package com.heiyehk.fithub

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import com.heiyehk.fithub.data.AppLocale
import com.heiyehk.fithub.data.Prefs
import com.heiyehk.fithub.data.install.ApkInstaller
import com.heiyehk.fithub.data.install.ApkLibrary
import com.heiyehk.fithub.data.install.ApkStore
import com.heiyehk.fithub.data.install.DownloadCenter
import com.heiyehk.fithub.ui.FitHubApp
import com.heiyehk.fithub.ui.theme.FitHubTheme

class MainActivity : ComponentActivity() {

    /**
     * 必须在这里换 locale，而不是等 `onCreate`：`setContent` 之后的第一次资源读取
     * 就已经定型了，晚了只能看到旧语言。
     *
     * API 33+ 系统已经在 applicationLocales 上应用过了，[AppLocale.wrap] 会原样放行。
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 先读偏好再 setContent：深浅色由「我的 → 外观」决定，读 Prefs.state 即可，
        // 改设置时 object 里的 state 变化会触发重组，主题当场跟着切。
        Prefs.init(this)
        enableEdgeToEdge()
        setContent {
            val override = Prefs.state.value.darkThemeOrNull
            FitHubTheme(darkTheme = override ?: isSystemInDarkTheme()) {
                FitHubApp()
            }
        }
        // 推到首帧之后再弹：宁可让首屏先出来，也不要拿一个系统弹窗盖住它。
        // post 出来的 runnable 是在 onCreate/onResume 派发完之后才执行的，
        // 这时候弹窗能正常显示。
        window.decorView.post {
            ensureNotificationPermission()
            ensureStoragePermission()
            handleAction(intent)
        }
    }

    /**
     * 通知栏点「下载完成」时拉起的就是这个 Activity（`DownloadService.buildNotification`
     * 里的 contentIntent）。App 已经在前台时不会重建，而是走这里。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAction(intent)
    }

    /**
     * 消化通知栏带过来的动作。
     *
     * 处理完**必须把 action 抹掉**：屏幕旋转 / 主题切换 / 低内存重建都会重新走
     * onCreate 并带上同一个 intent，不抹掉的话用户转一下屏幕就被再拉起一次系统安装器。
     */
    private fun handleAction(intent: Intent?) {
        val action = intent?.getStringExtra(EXTRA_ACTION) ?: return
        intent.removeExtra(EXTRA_ACTION)
        when (action) {
            ACTION_INSTALL -> {
                val asset = intent.getStringExtra(EXTRA_ASSET) ?: return
                val repo = intent.getStringExtra(EXTRA_REPO).orEmpty()
                installFromLibrary(repo, asset)
            }

            ACTION_LAUNCH -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return
                if (!ApkInstaller.launch(this, pkg)) {
                    toast(getString(R.string.action_launch_failed))
                }
            }
        }
    }

    /**
     * 从已下载清单里取出那个包并交给系统安装器。
     *
     * 清单是唯一事实源，不去猜文件名 —— 公共目录里的实际名字可能被 MediaStore 改过，
     * 拿资产名硬拼会指向一个不存在的文件。
     */
    private fun installFromLibrary(repoId: String, assetName: String) {
        val entry = ApkLibrary.get(this, repoId, assetName)
        if (entry == null) {
            // 文件被用户从下载目录里删了是常事，清单会在 list() 里自动剔除，
            // 于是这里拿到的就是 null。直说「文件没了」，别报成安装失败
            toast(getString(R.string.action_file_missing))
            return
        }
        if (!ApkInstaller.canRequestInstall(this)) {
            // 没给「安装未知来源应用」授权时必须把设置页指给用户，否则系统安装器
            // 会静默失败。原来这里只 toast 一句就 return —— 从通知栏点安装的人
            // 被告知「没权限」，却拿不到任何能解决的入口，只能自己去系统设置里找。
            toast(getString(R.string.install_error_no_permission))
            ApkInstaller.openPermissionSettings(this)
            return
        }
        ApkInstaller.install(this, entry.file()) { ok, message ->
            runOnUiThread {
                if (ok) {
                    ApkLibrary.setInstalled(this, entry.key, true)
                    // 通知还在的话把它变成「打开」：装完之后用户想跑一下这个应用，
                    // 不该还要自己切回 App 去找
                    DownloadCenter.postLaunchNotification(
                        this,
                        entry.displayName.ifBlank { entry.assetName },
                        entry.packageName,
                    )
                } else {
                    toast(getString(R.string.install_error_failed, message))
                }
            }
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    /**
     * Android 13+ 的通知要运行时授权，manifest 里声明了也不作数。
     *
     * 不申请的后果不是「少一条通知」，而是 `importance=NONE`：前台服务的进度通知
     * 用户**完全看不到**（实测 dumpsys notification 就是这个状态）。下载了几十 MB、
     * 中途是停是续、失败了没有，全都没有任何提示。
     *
     * 只用框架 API：minSdk 26 早就过了 23，`checkSelfPermission` /
     * `requestPermissions` 都不需要兼容分支，也就不必为它引 `ActivityCompat`。
     *
     * 弹过一次就不再主动弹第二次。`shouldShowRequestPermissionRationale` 在用户
     * 拒绝过一次之后会变 true，用它当闸门就不会被反复骚扰；用户误点拒绝只能去
     * 系统设置里再打开，这是一次性提示该承担的成本。
     *
     * 这里**不能**再用 `NotificationManager.areNotificationsEnabled()` 当「用户已关掉」
     * 的判据：Android 13+ 上「从未授权」和「用户在系统设置里明确关掉」返回的
     * 都是 `false`，用它守门会把「还没申请过」一并挡掉，于是永远走不到
     * `requestPermissions` —— 实测装完新包 `granted=false` 且 `importance=NONE`，
     * 通知权限从来没被申请过。这正是那条守卫原本想避免的失败模式。
     */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val perm = Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) return
        if (shouldShowRequestPermissionRationale(perm)) return
        requestPermissions(arrayOf(perm), REQ_NOTIFICATIONS)
    }

    /**
     * Android 8/9（API 26–28）上写公共下载目录要的运行时权限。
     *
     * manifest 里那条声明了 `maxSdkVersion="28"`：API 29 起分区存储接管，
     * 我们走 MediaStore 根本不需要它（系统也会忽略这条权限），继续申请只会
     * 多弹一个用户看不懂的框。
     *
     * 漏掉这一步的后果不是「少存一份文件」而是**Android 8/9 上整个下载链路
     * 直接写不进去**（`mkdirs` / `openOutputStream` 抛 SecurityException）。
     * minSdk 就是 26，所以这不是为老版本兜底，是主线功能的前置条件。
     *
     * 用户拒绝不重试也不提示：这个权限只影响「装在哪」这一个选择，下载和安装
     * 本身照常，只是文件退回私有目录。等他真去下东西时报一句更合适。
     */
    private fun ensureStoragePermission() {
        if (!ApkStore.needsLegacyWritePermission()) return
        val perm = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(perm), REQ_STORAGE)
    }

    companion object {
        const val EXTRA_ACTION = "fithub.action"
        const val ACTION_INSTALL = "install"
        const val ACTION_LAUNCH = "launch"
        const val EXTRA_ASSET = "fithub.asset"
        const val EXTRA_REPO = "fithub.repo"
        const val EXTRA_PACKAGE = "fithub.package"
        private const val REQ_NOTIFICATIONS = 1001
        private const val REQ_STORAGE = 1002
    }
}
