package com.heiyehk.fithub.data.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.heiyehk.fithub.R
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * 用系统 [PackageInstaller] 真正把 APK 装上。
 *
 * 为什么不用 `ACTION_INSTALL_PACKAGE` + `file://` Uri：那条路要配 FileProvider 的
 * authority、在 paths.xml 里放行 cacheDir，而且从 Android 7 起基本已经走不通。
 * PackageInstaller 是官方路径，commit 之后由系统安装器接管 —— 我们不碰安装过程本身，
 * 只负责把字节写进 session 并等一个结果回调。
 */
object ApkInstaller {

    private const val TAG = "FitHubInstall"

    /** 用户没给「安装未知来源应用」授权，或被系统收回 */
    const val NEEDS_PERMISSION = "needs-permission"

    /**
     * 正在等系统回传结果的那一次安装。
     *
     * 一次只允许一个：详情页的状态机本来就是单条的，而且两个 install 同时提交会让
     * 「谁装完了」变得说不清。这里用 AtomicReference 而不是 synchronized —— 写入方
     * 只有主线程和 BroadcastReceiver（binder 线程），读方同源。
     */
    private val pending = AtomicReference<((Boolean, String) -> Unit)?>(null)

    /**
     * 用户有没有给「安装未知来源应用」授权。
     *
     * 这个方法在 **PackageManager** 上，不在 PackageInstaller 上 —— 记错了会一直
     * "Unresolved reference"，而且 PackageInstaller 上确实有一个名字很接近的
     * `canRequestPackageInstalls` 容易被想当然。
     */
    fun canRequestInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /**
     * 提交一个已经落到本地的 APK 给系统安装器。
     *
     * [onResult] 在系统安装完成 / 失败后回调。**这不是**「装上了」的最终依据 ——
     * 用户在系统安装器里点「取消」同样会回调 false，UI 要照实说。
     *
     * [message] 在 [ok] 为 true 时无意义；失败时是**已经本地化好的**原因文本，
     * 或 [NEEDS_PERMISSION] 标记。UI 那侧用 `getString(R.string.install_error_failed, message)`
     * 套一层，所以这里给的是短句而不是整段说明。
     */
    fun install(context: Context, apk: File, onResult: (Boolean, String) -> Unit) {
        if (!canRequestInstall(context)) {
            onResult(false, NEEDS_PERMISSION)
            return
        }
        if (!apk.exists() || apk.length() == 0L) {
            onResult(false, context.getString(R.string.install_error_result_lost))
            return
        }

        val installer = context.packageManager.packageInstaller
        pending.set(onResult)
        try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                .apply { setSize(apk.length()) }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                // fsync 必须在 out **还开着**的时候调。
                // 先 close 再 fsync 会拿到 EBADF (Bad file descriptor)，
                // 而且这个错是 commit 之后才由系统回传的，排查起来很绕。
                val out = session.openWrite("package", 0, apk.length())
                try {
                    apk.inputStream().use { input -> input.copyTo(out) }
                    session.fsync(out)
                } finally {
                    out.close()
                }
                val intent = Intent(context, InstallResultReceiver::class.java)
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // M 及以上 PendingIntent 默认不可变，系统要往里塞结果码，必须显式声明可变
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                // commit 是 Session 上的方法，只收 IntentSender；带 sessionId 的那个
                // 在当前 android.jar 里已经没有了，别照着老文章写。
                //
                // 还要多走一步 getIntentSender()：当前 android.jar 的公开 stub 里
                // PendingIntent 只 implements Parcelable，没声明 extends IntentSender
                // （运行时框架类其实是继承的），所以直接传 PendingIntent 编译不过。
                // getIntentSender() 返回的就是标准的 android.content.IntentSender。
                session.commit(
                    PendingIntent.getBroadcast(context, id, intent, flags).intentSender
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "提交安装失败 apk=${apk.name}", e)
            pending.set(null)
            // e.message 是框架抛的英文，属于第三方原文，不翻译也不改写
            onResult(false, e.message ?: context.getString(R.string.install_error_installer_failed))
        }
    }

    /** 由 [InstallResultReceiver] 调用。放在这里而不是 receiver 里，方便单测直接驱动。 */
    internal fun complete(ok: Boolean, message: String) {
        pending.getAndSet(null)?.invoke(ok, message)
    }

    /**
     * 这个包是不是已经装在本机上了。
     *
     * 详情页的按钮状态全靠它：装上了就显示「打开」，没装就显示「安装」。
     * 必须查**真实的启动 Activity**而不是只看 `getPackageInfo` 能不能拿到 ——
     * 拿得到包名不等于能启动（有些包没有 LAUNCHER 入口，比如纯后台服务）。
     */
    fun isInstalled(context: Context, packageName: String): Boolean =
        packageName.isNotBlank() && launchIntentOf(context, packageName) != null

    /**
     * 拉起一个已安装的应用。返回 false = 拉不起来（没装 / 没有启动入口）。
     *
     * 状态不明的包名一律当「拉不起来」，不在这里编一个原因 —— 调用方拿 false 自己
     * 决定怎么提示，它比一个错的「没有权限」有用。
     */
    fun launch(context: Context, packageName: String): Boolean {
        val i = launchIntentOf(context, packageName) ?: return false
        // 从通知栏点过来时我们只有 Application 上下文，没有自己的任务栈
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(i) }.isSuccess
    }

    private fun launchIntentOf(context: Context, packageName: String): Intent? =
        runCatching { context.packageManager.getLaunchIntentForPackage(packageName) }
            .getOrNull()
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * 收系统安装结果的广播。
 *
 * 只关心「这次 install 结束了没有、结果是什么」：
 * [PackageInstaller.EXTRA_STATUS] 是 `STATUS_SUCCESS` 才算成功；
 * 其余的按 [PackageInstaller.EXTRA_STATUS_MESSAGE] 如实转述。
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        when (status) {
            PackageInstaller.STATUS_SUCCESS ->
                ApkInstaller.complete(true, context.getString(R.string.install_ok))

            PackageInstaller.STATUS_FAILURE ->
                ApkInstaller.complete(
                    false,
                    // 系统给的状态说明是原文，转述不翻译
                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        ?: context.getString(R.string.install_error_rejected),
                )

            else -> Unit // 中间态（已提交、正在下载依赖等），不发终局回调
        }
    }
}
