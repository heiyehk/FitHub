package com.heiyehk.fithub.data.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.IntentCompat
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
     * 跳到「安装未知来源应用」的授权页。
     *
     * 这个权限没有 API 能代用户授予，只能把设置页指给用户自己点。这是 Android 的设计，
     * 任何声称能静默绕过它的方案都不该进这个项目。
     *
     * 放在这里而不是各自实现，是因为**每条安装入口都得能走到它**：
     * 详情页、[MainActivity] 的通知栏入口、以前还有一个自己漏检的路径 ——
     * 少了权限的用户在详情页能装，从通知栏点却只弹一句 toast 然后死掉。
     * 抽成一处之后，新增入口只要记得调它就行。
     *
     * 已经授权时不跳：用户点了「安装」却被甩到设置页，而那页显示的恰恰是
     * 「已经允许」，是纯噪音。调用方自己先判 [canRequestInstall]。
     */
    fun openPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
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
                .apply {
                    setSize(apk.length())
                    // **必须显式表态「需要用户确认」。**
                    //
                    // Android 11+ 要求调用方写清楚要不要用户动作，不写就是 UNSPECIFIED，
                    // 由系统自决。实测在这台设备上自决的结果是：安装器进程起来了
                    // （会话里 `mBridges=1`），但既不弹界面也不回调，会话永远停在
                    // `mFinalStatus=PENDING` / `mSessionApplied=false`，
                    // 界面上则卡在「正在交给系统安装器…」直到天荒地老。
                    //
                    // 之前把这现象归咎于镜像的 PackageInstaller 有问题，是判错了 ——
                    // `markAsSealed` 里那条 `persistent_data_block` 的 ServiceNotFoundException
                    // 是框架内部吞掉的噪音，会话本身 seal 得干干净净。
                    //
                    // 31 才引入这两个常量，更早的版本不需要（11/12 上默认就是弹确认）。
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRequireUserAction(
                            PackageInstaller.SessionParams.USER_ACTION_REQUIRED,
                        )
                    }
                }
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

    /**
     * 设备上已装的那个包的 versionCode。没装 / 拿不到返回 0。
     *
     * [isInstalled] 回答的是「装没装」，而按钮真正要回答的是
     * 「**现在这个文件是装它，还是打开已经装的那个**」—— 后者要看版本。
     */
    fun installedVersionCode(context: Context, packageName: String): Long {
        if (packageName.isBlank()) return 0L
        return runCatching {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        }.getOrDefault(0L)
    }

    /**
     * 这个下载好的文件是不是**已装应用的更新**。
     *
     * 少这一条判断时，任何已安装应用的升级都会卡死：旧版正装着，
     * [isInstalled] 必然为 true，于是界面给出的是「打开」——
     * 点开是旧版，新下好的那个永远装不上。
     * 本项目自己的自更新就是第一个撞上这个的。
     *
     * 读不到已装版本时（0）**不**判成更新：宁可给「打开」——
     * 把一次已经装好的应用再装一遍，对用户是更糟的结果。
     */
    fun isUpdateOfInstalled(context: Context, entry: DownloadedApk): Boolean {
        val installed = installedVersionCode(context, entry.packageName)
        return installed > 0L && entry.versionCode > installed
    }
}

/**
 * 收系统安装结果的广播。
 *
 * ⚠️ **`STATUS_PENDING_USER_ACTION` 不是「可以忽略的中间态」，而是系统的点名。**
 *
 * commit 之后系统不会自己弹确认界面 —— 它把那个确认 Activity 的 Intent 通过
 * `Intent.EXTRA_INTENT` 交给**我们**，要我们 `startActivity` 把它弹出来。
 * 漏掉这一支的时候：会话 seal 成功、`mBridges=1`（安装器进程起来了），
 * 但没有任何界面出现，会话永远停在 `STATUS_PENDING`，
 * 既没有终局回调也没有 `InstallStep.Done`/`Failed`。
 * 表现为「点了确认安装，什么都没发生」，在真机和模拟器上一样。
 *
 * （原注释写的是「中间态别当成失败，成功/失败由站点状态通知」—— 这句话是对的，
 * 但结论错了：`PENDING_USER_ACTION` 不是「等一会儿就好」的中间态，
 * 它是**轮到你干活了**，不弹就永远不会有下一步。）
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        when (status) {
            // 系统让我们弹确认界面 —— 这一支漏了，安装器就永远唤不起来
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_INTENT,
                    Intent::class.java,
                )
                if (confirmation != null) {
                    // 从 BroadcastReceiver 起 Activity 必须带 NEW_TASK —— 我们没有自己的任务栈
                    context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    // 系统没给出确认 Intent，就没法继续了；如实说清楚，别让界面一直转
                    ApkInstaller.complete(
                        false,
                        context.getString(R.string.install_error_no_confirmation),
                    )
                }
            }

            PackageInstaller.STATUS_SUCCESS ->
                ApkInstaller.complete(true, context.getString(R.string.install_ok))

            PackageInstaller.STATUS_FAILURE ->
                ApkInstaller.complete(
                    false,
                    // 系统给的状态说明是原文，转述不翻译
                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        ?: context.getString(R.string.install_error_rejected),
                )

            // 其余（已提交、正在下载依赖等）确实不用管：等系统把终局发过来
            else -> Unit
        }
    }
}
