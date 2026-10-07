package com.heiyehk.fithub.data.install

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.heiyehk.fithub.MainActivity
import com.heiyehk.fithub.R
import com.heiyehk.fithub.data.Asset
import com.heiyehk.fithub.data.Env
import com.heiyehk.fithub.data.HistoryEntry
import com.heiyehk.fithub.data.HistoryStore
import com.heiyehk.fithub.data.Mirrors
import com.heiyehk.fithub.data.Prefs
import com.heiyehk.fithub.data.parse.ApkParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 下载进度中枢。
 *
 * 单独一个 object 而不是塞进服务里：UI（详情页的行内进度）和通知栏需要看的是**同一份**
 * 状态。分成两处各报一次就会出现「通知显示 80%、界面显示 20%」这种对不上的情况。
 *
 * 状态是进程级的 —— 服务被系统杀掉、或者 App 冷启动后重新进详情，读到的都是这份。
 */
object DownloadCenter {

    private const val CHANNEL = "download"
    private const val TAG = "FitHubDownload"

    private val _state = MutableStateFlow<Progress>(Progress.Idle)
    val state: StateFlow<Progress> = _state.asStateFlow()

    /** 同一个资产已经在跑就别再起一个，服务是单例的 */
    fun isBusyFor(assetName: String): Boolean =
        (_state.value as? Progress.Running)?.assetName == assetName

    /**
     * 同一个资产是「暂停中」。
     *
     * 必须和 [isBusyFor] 分开：暂停之后按钮还得能点（去继续），要是把它并回
     * Running，UI 那两处 `if (isBusyFor) return` 就会把「继续」也一起吞掉，
     * 暂停就成了一个只能靠杀进程退出的死路。
     */
    fun isPausedFor(assetName: String): Boolean =
        (_state.value as? Progress.Paused)?.assetName == assetName

    /**
     * 暂停 / 取消是**发给服务**的命令，而不是在这里改状态。
     *
     * 真正停下来的是服务里那个下载协程，只有它知道 `.part` 写到哪、要不要删；
     * 这里抢先改状态的话，服务还没停就会又发一次 Running 覆盖掉。
     */
    fun pause(context: Context, assetName: String) =
        sendAction(context, assetName, ACTION_PAUSE)

    fun cancel(context: Context, assetName: String) =
        sendAction(context, assetName, ACTION_CANCEL)

    /**
     * 继续下载 —— 语义上就是**重新入队**。
     *
     * 真正的「接着下」由 [ApkParser.downloadForService] 的 Range 请求完成：`.part`
     * 还在盘上，重启的下载会接着它往下写。所以这里跟 [enqueue] 是同一件事，
     * 转发过去而不是复制一份。
     *
     * 不额外先发一次 [Progress.Paused]：调用方只在 [isPausedFor] 为真时才会调它，
     * 状态本来就已经是 Paused，再发一遍只会用「只有名字、没有字节数」的版本
     * 把它覆盖掉，把进度条抹成 0。
     */
    fun resume(context: Context, url: String, asset: Asset, repoName: String) {
        enqueue(context, url, asset, repoName)
    }

    /**
     * 命令发不出去就只记一笔日志，不弹错。
     *
     * 没有正在跑的东西时直接忽略：这时点「暂停」在语义上就是空操作，给个错误
     * 只会让用户以为 App 坏了。
     */
    private fun sendAction(context: Context, assetName: String, action: String) {
        val st = _state.value
        if (st !is Progress.Running && st !is Progress.Paused) return
        val i = Intent(context, DownloadService::class.java).apply {
            this.action = action
            putExtra(EXTRA_ASSET, assetName)
        }
        // startService 而不是 startForegroundService：服务此刻已经在前台跑着，
        // 这只是一次「再调一次 onStartCommand」，不需要重新走前台服务的 5 秒期限。
        // 万一它其实没在跑（比如进程刚被回收），runCatching 兜住 IllegalStateException。
        runCatching { context.startService(i) }
            .onFailure { Log.w(TAG, "命令发送失败 action=$action asset=$assetName", it) }
    }

    internal fun emit(p: Progress) {
        _state.value = p
    }

    internal fun reset() {
        _state.value = Progress.Idle
    }

    /**
     * 通知 id 和 [DownloadService] 用同一条，否则装完之后通知栏里会同时躺着
     * 「下载完成」和「打开」两条，点哪条都不对。
     */
    const val NOTIF_ID = 4201

    /**
     * 装好之后把通知栏那条从「下载完成 · 点我去装」换成「已安装 · 点我去打开」。
     *
     * 用户装完通常就是想跑一下，不该还要切回 App 重新找一遍那个包。
     * 清单被移除或包名读不到时把通知撤掉 —— 留一条点不动的通知比没有更糟。
     */
    fun postLaunchNotification(context: Context, displayName: String, packageName: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (packageName.isBlank()) {
            nm.cancel(NOTIF_ID)
            return
        }
        createChannel(context)
        val launch = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_LAUNCH)
                putExtra(MainActivity.EXTRA_PACKAGE, packageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        nm.notify(
            NOTIF_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setContentTitle(context.getString(R.string.download_notif_title, displayName))
                .setContentText(context.getString(R.string.download_notif_installed))
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(launch)
                .build(),
        )
    }

    /**
     * 启动一次下载。
     *
     * 放在这里而不是服务的 companion：调用方只应该跟「下载中枢」打交道，
     * 不该知道底下是个 Service。通知通道也在这里建 —— Android 8 上前台服务
     * 启动前通道不存在会直接崩。
     */
    fun enqueue(context: Context, url: String, asset: Asset, repoName: String) {
        createChannel(context)
        val intent = Intent(context, DownloadService::class.java).apply {
            putExtra(DownloadCenter.EXTRA_URL, url)
            putExtra(DownloadCenter.EXTRA_ASSET, asset.name)
            putExtra(DownloadCenter.EXTRA_REPO, repoName)
            putExtra(DownloadCenter.EXTRA_SHA, asset.sha)
        }
        context.startForegroundService(intent)
    }

    /** 一次下载 / 校验的状态。刻意做成 sealed：UI 不该拿到「既没跑完也没失败」的中间值。 */
    sealed interface Progress {
        data object Idle : Progress

        data class Running(
            val assetName: String,
            val repoName: String,
            val bytes: Long,
            val total: Long,
            /** 瞬时速度，字符串是为了让 UI 直接显示、不必自己换算单位；取不到可信速率时是空串 */
            val speed: String,
        ) : Progress

        /**
         * 已暂停。`.part` 还在盘上，[saved] 是真实落盘的字节数，所以进度能停在原处。
         *
         * 单独一个状态而不是复用 [Running]：UI 靠 [isPausedFor] 判断「这个产物还能点
         * 继续」，塞回 Running 就分不出「正在跑」和「可以继续」了。
         */
        data class Paused(
            val assetName: String,
            val repoName: String,
            val saved: Long,
            val total: Long,
        ) : Progress

        data object Verifying : Progress

        /**
         * 下载完了，[file] 在手边，等用户确认安装。
         *
         * [sha] 是我们**自己算**的完整摘要。[verified] 表示它是不是和某个**权威来源**
         *（Release 页发布的 .sha256 / checksums.txt）比对过 —— 绝大多数 Release 根本不
         * 提供这种文件，那种情况下 verified=false，UI 必须照实说「已计算，可自行核对」，
         * 不能写成「校验通过」。
         *
         * 之前这里拿 `ApkParser.sha256()` 的 12 位截断值当「期望值」去比 64 位摘要，
         * 永远对不上；更糟的是那个值本来就是自己刚算的，拿自己校验自己等于演戏。
         *
         * [file] 指向**公共下载目录**里那份（`Download/FitHub/`），不是 cache 里的
         * 中途工作副本 —— 用户在文件管理器里看到的就是它，装上的也是它。
         * [packageName] 是顺手从清单里读出来的：装上之后可以直接拉起它，
         * 详情页的按钮也才能从「安装」切成「打开」。
         */
        data class Ready(
            val file: File,
            val assetName: String,
            val repoName: String,
            val sha: String,
            val verified: Boolean,
            val packageName: String = "",
            /** 盘上实际的文件名，未必等于 [assetName]（MediaStore 会按 MIME 改扩展名） */
            val displayName: String = "",
        ) : Progress

        /**
         * 失败。
         *
         * [assetName] 必须在：详情页可能同时盯着多个产物的行内状态，没有它就无法判断
         * 「这次失败是不是我关心的那个」。之前就是漏了它，导致行内一直是「无法解析」，
         * 看上去像什么都没发生。
         */
        data class Failed(val reason: String, val assetName: String = "") : Progress
    }

    // internal 而不是 private：DownloadService 要读这些 key
    internal const val EXTRA_URL = "url"
    internal const val EXTRA_ASSET = "asset"
    internal const val EXTRA_REPO = "repo"
    internal const val EXTRA_SHA = "sha"

    /**
     * 通知栏按钮走 action 而不是新开一个 Activity。
     *
     * 进度是服务自己的事；用户点一下「暂停」要是还得回 App 点按钮，那这个通知栏
     * 按钮就没有存在意义了。动作直接回到 [DownloadService]。
     */
    internal const val ACTION_PAUSE = "com.heiyehk.fithub.download.PAUSE"
    internal const val ACTION_RESUME = "com.heiyehk.fithub.download.RESUME"
    internal const val ACTION_CANCEL = "com.heiyehk.fithub.download.CANCEL"

    /** 通道要先建好，否则 Android 8 上前台服务启动时会直接崩 */
    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                // 通道名与说明会显示在系统「通知」设置里，所以要跟着语言走
                context.getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.download_channel_desc)
                setShowBadge(false)
            },
        )
    }
}

/**
 * 中枢当前记的产物名；不属于任何一次具体下载时是 null。
 *
 * 「拿不到归属」和「归属于某个包」必须能分开：拿 null 去比一个大小写不同的名字
 * 会永远不等。UI 靠它判断「这个状态说的是不是我关心的那个产物」—— 详情页可能同时
 * 盯着十几个产物，不先按名字过滤就会张冠李戴。
 *
 * 放在 `DownloadCenter` 外面而不是里面：UI 层要 import 它，放进去就成了成员扩展，
 * 用的时候还得先拿到一个 `DownloadCenter` 实例。
 */
val DownloadCenter.Progress.assetNameOrNull: String?
    get() = when (this) {
        is DownloadCenter.Progress.Running -> assetName
        is DownloadCenter.Progress.Paused -> assetName
        is DownloadCenter.Progress.Ready -> assetName
        // Failed 的 assetName 有默认空串：空串不能当成「说的是某个包」
        is DownloadCenter.Progress.Failed -> assetName.ifBlank { null }
        DownloadCenter.Progress.Idle,
        DownloadCenter.Progress.Verifying,
        -> null
    }

/**
 * 前台下载服务。
 *
 * 为什么要前台服务而不是后台协程：APK 动辄几十 MB，用户不会盯着详情页等；进程一旦进后台
 * 被系统回收，下载就断了，而且**没有任何地方告诉用户断了**。前台服务给通知栏一条常驻的
 * 进度，既是「还在下」的凭证，也是用户中途想取消时的出口。
 *
 * 只做「下载 + SHA-256 校验」。安装要拉起系统安装器（需要 Activity 上下文和用户交互），
 * 留在 UI 层做，服务只把文件准备好。
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 只装**当前这一次**下载。
     *
     * 暂停/取消要停的是它，不能用 `scope.cancel()`：那会把收尾用的那个 20 秒计时器
     * 一起干掉，而且之后再点「继续」就没得启动了。
     */
    private var job: Job? = null

    /**
     * 协程取消**不会**打断阻塞中的 `InputStream.read()` —— 它不是挂起点，
     * 真正生效要等到下一次挂起，那就是最多一整个 30 秒的 readTimeout。
     * 所以「马上停」靠这个标志：进度回调每读满 64 KB 看一眼，命中就抛，
     * `use` 随即把流关掉、半截字节留在 `.part` 里。
     */
    @Volatile
    private var stopRequested = false

    /**
     * 通知栏「继续」只能用一条不带 URL 的 action intent 重建下载，那几个字段就是
     * 它的凭据。暂停期间服务一直留在前台，所以它们不会丢。
     */
    private var lastUrl: String = ""
    private var lastName: String = ""
    private var lastRepo: String = ""
    private var lastSha: String? = null

    /** 真实速率的采样器。传的是「已下载总字节」的历史教训就在下面那个类里 */
    private val speed = SpeedMeter()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 通知栏按钮：这三个分支不下载任何东西，只是把正在跑的那个协程停掉或重启
        when (intent?.action) {
            DownloadCenter.ACTION_PAUSE -> {
                onPauseRequested()
                return START_NOT_STICKY
            }

            DownloadCenter.ACTION_CANCEL -> {
                onCancelRequested()
                return START_NOT_STICKY
            }

            DownloadCenter.ACTION_RESUME -> {
                // 继续时命令里没有 URL，只能用服务自己记下的那一份
                if (lastUrl.isBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startDownload(lastUrl, lastName, lastRepo, lastSha)
                return START_NOT_STICKY
            }
        }

        val url = intent?.getStringExtra(DownloadCenter.EXTRA_URL)
        // 极端情况下 intent 没带 asset 名。这是个兜底值、不是正常路径，
        // 所以不占一个正式资源位，用一个通用词顶上去。
        val name = intent?.getStringExtra(DownloadCenter.EXTRA_ASSET)
            ?: getString(R.string.download_generic_asset)
        val repo = intent?.getStringExtra(DownloadCenter.EXTRA_REPO) ?: ""
        if (url.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startDownload(url, name, repo, intent.getStringExtra(DownloadCenter.EXTRA_SHA))
        // 不重启：被杀就是被杀，凭空再下一次用户会看到两次通知
        return START_NOT_STICKY
    }

    /**
     * 真正的下载流程。首次启动和通知栏「继续」共用这一条路。
     *
     * 续传不靠这里记进度，靠 `.part` 还在盘上（见 [ApkParser.downloadForService]），
     * 所以「继续」和「重新开始」的区别只有盘上那份文件。
     */
    private fun startDownload(url: String, name: String, repo: String, sha: String?) {
        lastUrl = url
        lastName = name
        lastRepo = repo
        lastSha = sha
        // 同一时刻只准有一个下载：状态是全局的，第二个协程会跟第一个抢同一个 .part。
        // 顺序有讲究 —— 要先让旧的那份看见 stopRequested 立刻停，再把标志清给新下载用；
        // 反过来清的话，旧协程的读循环会继续往同一个 .part 里写。
        val previous = job
        if (previous != null) {
            stopRequested = true
            previous.cancel()
        }
        stopRequested = false
        speed.reset()

        val preparing = getString(R.string.install_stage_preparing)
        val verifyingText = getString(R.string.install_stage_verifying)
        val doneText = getString(R.string.download_done)
        val verifyFailedText = getString(R.string.download_verify_failed)
        val shaMismatch = getString(R.string.download_sha_mismatch)

        // Android 14 (API 34) 起前台服务必须声明类型，漏了会直接 SecurityException
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                buildNotification(name, 0L, 0L, preparing),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIF_ID, buildNotification(name, 0L, 0L, preparing))
        }

        job = scope.launch {
            // 公共下载目录里已经有成品就不要再下一遍。用户下完没装，隔天回来点
            // 「下载」应该直接拿到那份，而不是把流量和几十 MB 再花一遍。
            //
            // **只查一次**：existing() 自己在文件消失时会删行并返回 null，所以查第二遍
            // 有可能拿到 null —— 之前那个 `!!` 就真的会 NPE。而且 onSuccess 里抛出的
            // 异常不会被同一条链的 onFailure 接住，会一路冒到协程。
            val published = ApkStore.existing(this@DownloadService, name)
            runCatching {
                if (published != null) return@runCatching null
                val dir = File(cacheDir, "apk").apply { mkdirs() }
                val target = File(dir, name)
                if (target.exists() && target.length() > 0L) {
                    target
                } else {
                    downloadWithFallback(url, target, name, repo, preparing)
                }
            }.onSuccess { downloaded ->
                DownloadCenter.emit(DownloadCenter.Progress.Verifying)

                val staged = downloaded?.takeIf { it.length() > 0L } ?: published?.file
                if (staged == null || !staged.exists() || staged.length() <= 0L) {
                    // 通知栏也得换掉：只改内存状态的话，用户看到的还是上一条
                    // 「下载完成 · 点我安装」，点下去必然失败
                    val goneText = getString(R.string.download_failed)
                    DownloadCenter.emit(
                        DownloadCenter.Progress.Failed(goneText, name),
                    )
                    notify(buildNotification(name, 0L, 0L, goneText))
                    return@onSuccess
                }
                notify(buildNotification(name, staged.length(), staged.length(), verifyingText))
                val actual = withContext(Dispatchers.IO) { ApkParser.sha256ForService(staged) }

                // 只有**足够长**的期望值才配当权威来源。Asset.sha 是界面用的 12 位短指纹
                // （ApkParser.sha256 里 take(12) 截的），拿它比 64 位摘要必然失败；
                // 而且它是我们自己刚算的，比对等于自己验自己。
                val expected = sha
                val authoritative = expected?.takeIf { it.length >= 32 }
                if (authoritative != null && !authoritative.equals(actual, ignoreCase = true)) {
                    Log.w(TAG, "SHA-256 与发布方不一致 asset=$name 期望=$authoritative 实际=$actual")
                    // 校验不过的包不能留在用户能看见的下载目录里
                    if (published != null) ApkStore.remove(this@DownloadService, name)
                    else staged.delete()
                    DownloadCenter.emit(
                        DownloadCenter.Progress.Failed(shaMismatch, name),
                    )
                    notify(buildNotification(name, 0L, 0L, verifyFailedText))
                    return@onSuccess
                }

                // 校验通过（或压根没有权威值可比）之后才发布。发布之前这一份只活在
                // cache 里，用户看不到 —— 这正是我们想要的：一个没验完的包不该出现在
                // 用户的下载目录里。
                val dest = published ?: withContext(Dispatchers.IO) {
                    ApkStore.publish(this@DownloadService, staged, name)
                }

                // 读一次清单。这里读的是**公共下载目录里那份**的真实路径（分区存储下
                // 自己经 MediaStore 建的文件仍可直读，已实测），所以清单事实和用户
                // 手上那个文件必然是同一份，不会出现「校验的是 A、显示的是 B」。
                //
                // 这一次读取取代了原来那个独立的「解析」动作：以前用户要点一下解析
                // 才会读清单，而解析还会自己再下一遍到 cacheDir。
                val manifest = withContext(Dispatchers.IO) {
                    runCatching { ApkParser.parseArchive(this@DownloadService, dest.file) }.getOrNull()
                }

                ApkLibrary.record(
                    this@DownloadService,
                    DownloadedApk(
                        repoName = repo,
                        assetName = name,
                        displayName = dest.file.name,
                        sizeBytes = dest.sizeBytes,
                        sha256 = actual,
                        verified = authoritative != null,
                        packageName = manifest?.packageName.orEmpty(),
                        versionName = manifest?.versionName.orEmpty(),
                        versionCode = manifest?.versionCode ?: 0L,
                        minSdk = manifest?.minSdk ?: 0,
                        targetSdk = manifest?.targetSdk ?: 0,
                        signerSha256 = manifest?.signerSha256.orEmpty(),
                        abis = manifest?.abis.orEmpty(),
                        debuggable = manifest?.isDebuggable ?: false,
                        manifestRead = manifest != null,
                        at = System.currentTimeMillis(),
                    ),
                )

                // 「下载与安装记录」记在这里，和 [ApkLibrary.record] 挨着 ——
                // 也就是**每一个**下载的入口都会记到。
                //
                // 之前这条记录写在详情面板的 `onDownloaded` 回调里，也就是只有
                // 「从详情页点下载」这一条路会记。于是「我的 → 检查更新」下的那个包
                // 明明已经落到下载目录、`ApkLibrary` 也有它，界面上却查不到 ——
                // 而自更新恰恰是这个 App 里最该被看见、也最该能被管理的一次下载。
                //
                // 事实只有一处会发生（文件落到公共目录、摘要算完），所以记录也只写
                // 在这一处。写在 UI 层就意味着「每多一个下载入口就得多记得一次」，
                // 而漏掉的那一次不会报错，只是安静地少一条记录。
                HistoryStore.record(
                    this@DownloadService,
                    HistoryEntry(
                        kind = HistoryEntry.Kind.Downloaded,
                        // 与 DownloadedApk.key 同一种拼法：记录页就是拿整串去对清单的
                        // （只比 assetName 会把别的仓库的同名产物挂上来）
                        ref = "$repo/$name",
                        title = name,
                        detail = listOfNotNull(
                            repo.substringAfterLast('/').takeIf { it.isNotBlank() },
                            actual.take(8).takeIf { it.isNotBlank() }?.let { "sha $it" },
                        ).joinToString(" · "),
                        at = System.currentTimeMillis(),
                    ),
                )

                // 中途工作副本已经没用了，删掉免得白占一份几十 MB —— 公共目录那份才是权威
                if (published == null) staged.delete()

                Log.i(
                    TAG,
                    "下载完成 published=${dest.file.absolutePath} asset=$name sha=$actual " +
                        "verified=${authoritative != null} pkg=${manifest?.packageName}",
                )
                DownloadCenter.emit(
                    DownloadCenter.Progress.Ready(
                        file = dest.file,
                        assetName = name,
                        repoName = repo,
                        sha = actual,
                        verified = authoritative != null,
                        packageName = manifest?.packageName.orEmpty(),
                        displayName = dest.file.name,
                    )
                )
                notify(buildNotification(name, dest.sizeBytes, dest.sizeBytes, doneText, actionable = true))
            }.onFailure { e ->
                // 暂停/取消**就是靠取消协程**实现的：runCatching 捕的是 Throwable，
                // 会把 CancellationException 一并吞掉。不原样抛出去的话，用户刚点完
                // 暂停就被告知「下载失败」，而且末尾那个 20 秒收尾计时器也会跟着跑起来
                // 把服务停掉 —— 暂停就成了假的。
                if (e is CancellationException) throw e
                // 失败必须能定位到原因。之前这里只有一句通知文案，服务一停就没了，
                // 排查时完全看不出是连接失败、HTTP 错误还是校验不过。
                Log.w(TAG, "下载失败 asset=$name url=$url", e)
                // 通知和 Progress.Failed 面向用户，所以只给一句可本地化的话；
                // 底层异常原文（可能是中文的「下载失败 HTTP 404」）留在上面的 Log 里。
                // DownloadCenter 是进程级 StateFlow，这里存的已经是渲染好的文本，
                // 切换语言后不会跟着变 —— 要跟着变得让 UI 侧存 Explain，那是另一件事。
                val text = getString(R.string.download_failed)
                DownloadCenter.emit(DownloadCenter.Progress.Failed(text, name))
                notify(buildNotification(name, 0L, 0L, text))
            }
            // 不立刻 stopSelf：通知里那条「下载完成」要让用户看见并点它进 App。
            // 停留 20 秒后由系统回收，用户点通知照样能拉起 MainActivity。
            // 这个计时器挂在 scope 上、而不是 job 上：暂停取消的是 job，
            // 它不该把已经下完的那条通知也一起带走。
            scope.launch {
                kotlinx.coroutines.delay(20_000)
                stopSelf()
            }
        }
    }

    /**
     * 套上用户选的镜像下载；失败时按 [Prefs.mirrorAutoFallback] 逐个换下一个。
     *
     * **走镜像时必须把「这个包来自第三方」写进通知**，不能悄悄下载：安装包会被
     * 系统安装并拿到权限，流量经过谁的服务器是用户该知道的事。
     *
     * `.part` 的去留分两种情况，这里以前搞反了：
     * - **第一次**尝试：要留着。暂停后「继续」走的就是这条路，续传靠它
     *   （见 [ApkParser.downloadForService]）
     * - **换**镜像：要删掉。镜像是另一台服务器上的另一份拷贝，同步进度不保证
     *   跟官方一致；接着上一个来源的字节往下写，拿到的是两段拼起来的坏包。
     *   之前这里只删了 target、漏了 `.part`，于是「换镜像重试」实际上是在拿
     *   旧镜像的半截文件续传。
     */
    private suspend fun downloadWithFallback(
        originalUrl: String,
        target: File,
        name: String,
        repo: String,
        preparing: String,
    ): File {
        val firstId = Prefs.mirrorId(this)
        val auto = Prefs.mirrorAutoFallback(this)
        var mirrorId = firstId
        var lastError: Throwable? = null
        var firstAttempt = true

        while (true) {
            val mirror = Mirrors.BY_ID[mirrorId] ?: Mirrors.DIRECT
            val effUrl = Mirrors.apply(originalUrl, mirror)
            if (target.exists()) target.delete()
            if (!firstAttempt) partOf(target).delete()
            firstAttempt = false

            if (mirror.isThirdParty) {
                val note = getString(R.string.download_via_mirror, mirror.id)
                DownloadCenter.emit(DownloadCenter.Progress.Running(name, repo, 0L, 0L, note))
                notify(buildNotification(name, 0L, 0L, note))
            } else {
                startForeground(NOTIF_ID, buildNotification(name, 0L, 0L, preparing))
            }

            val r = runCatching {
                ApkParser.downloadForService(effUrl, target) { saved, total ->
                    // 暂停/取消要能**马上**断掉读循环：协程取消打断不了阻塞中的
                    // read()，只能在这里抛（见 stopRequested 的说明）
                    if (stopRequested) throw CancellationException("用户暂停/取消")
                    // 拿不到可信速率就留空串。宁可只显示「18.3 MB / 60.9 MB」，
                    // 也别给一个假的 0.0 MB/s —— 那比没有更让人以为卡死了。
                    val speedText = speed.sample(saved)?.let { humanSpeed(it) } ?: ""
                    DownloadCenter.emit(
                        DownloadCenter.Progress.Running(name, repo, saved, total, speedText)
                    )
                    notify(
                        buildNotification(
                            name, saved, total, progressText(speedText, saved, total)
                        )
                    )
                }
                target
            }
            if (r.isSuccess) return r.getOrThrow()

            // 暂停/取消导致的抛出不是「这个镜像不行」，别去换下一个接着下
            r.exceptionOrNull()?.let { if (it is CancellationException) throw it }

            lastError = r.exceptionOrNull()
            Log.w(TAG, "镜像下载失败 mirror=$mirrorId url=$effUrl", lastError)
            val nextMirror = if (auto) Mirrors.next(mirrorId, onlyMirrors = false) else null
            if (nextMirror == null) throw lastError ?: IllegalStateException("download failed")
            mirrorId = nextMirror.id
        }
    }

    /**
     * 暂停：停掉协程、**保留** `.part`、状态落到 [DownloadCenter.Progress.Paused]。
     *
     * 服务和通知都留着。停掉服务的话系统会顺手把这条前台通知收走，用户既看不到
     * 「已经下了 40%」，也就没有地方点「继续」—— 暂停就成了一个只能杀进程退出的
     * 死路。这条通知就是暂停期间唯一的出口。
     */
    private fun onPauseRequested() {
        val st = DownloadCenter.state.value
        // 校验阶段没有「暂停」可言；Idle 是通知比状态先到一瞬的情况，同样忽略
        if (st !is DownloadCenter.Progress.Running && st !is DownloadCenter.Progress.Paused) return
        stopRequested = true
        job?.cancel()
        job = null
        DownloadCenter.emit(
            DownloadCenter.Progress.Paused(lastName, lastRepo, st.savedOf(), st.totalOf()),
        )
        notify(
            buildNotification(
                lastName,
                st.savedOf(),
                st.totalOf(),
                // 必须把两个占位符实参传进去：只写 getString(id) 的话通知里
                // 会原样显示「Paused · %1$s of %2$s」—— 实测截图抓到的就是这个。
                getString(
                    R.string.download_paused,
                    Env.formatSize(st.savedOf() / 1_048_576.0),
                    Env.formatSize(st.totalOf() / 1_048_576.0),
                ),
                paused = true,
            ),
        )
    }

    /**
     * 取消：停协程、删掉半截文件、状态落到 Failed、前台身份和通知一起撤。
     */
    private fun onCancelRequested() {
        val st = DownloadCenter.state.value
        // 已经出结果（Ready / Failed）时按「取消」不能改状态：UI 正在等 Ready 去拉起
        // 系统安装器，这里换成 Failed 就变成了「结果丢了」。
        if (st is DownloadCenter.Progress.Ready || st is DownloadCenter.Progress.Failed) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        stopRequested = true
        job?.cancel()
        job = null
        val dir = File(cacheDir, "apk").apply { mkdirs() }
        partOf(File(dir, lastName)).delete()
        // 字节已经收齐、正在算摘要时取消，等于白下一次：把成品也删掉，
        // 否则下一次点下载会直接跳过下载、拿这份文件去校验
        if (st is DownloadCenter.Progress.Verifying) File(dir, lastName).delete()
        DownloadCenter.emit(
            DownloadCenter.Progress.Failed(getString(R.string.download_cancelled), lastName),
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * 暂停/取消要报的字节数。Running 和 Paused 各存了一份，取不到就当 0 ——
     * 通知上的进度条宁可从 0 画起，也不要拿一个错的数停在半路。
     */
    private fun DownloadCenter.Progress.savedOf(): Long = when (this) {
        is DownloadCenter.Progress.Running -> bytes
        is DownloadCenter.Progress.Paused -> saved
        else -> 0L
    }

    private fun DownloadCenter.Progress.totalOf(): Long = when (this) {
        is DownloadCenter.Progress.Running -> total
        is DownloadCenter.Progress.Paused -> total
        else -> 0L
    }

    /** 半截文件的名字必须和 ApkParser 那边一致，否则「保留 .part」等于没保留 */
    private fun partOf(target: File): File = File(target.parentFile, "${target.name}.part")

    override fun onDestroy() {
        // 僵尸状态：服务被系统杀掉、或协程在写盘时抛出没接住的异常，状态就永远停在
        // Running / Verifying。此后 isBusyFor 恒为 true，而 UI 那两处是
        // `if (isBusyFor) return` 的静默 return —— 用户看到的就是「点了没反应」，
        // 除非有人把它落回终局。Paused 不用动：那是用户自己停的，可以继续。
        val st = DownloadCenter.state.value
        if (st is DownloadCenter.Progress.Running || st is DownloadCenter.Progress.Verifying) {
            Log.w(TAG, "服务结束但状态仍在进行中，落回失败 asset=$lastName state=$st")
            DownloadCenter.emit(
                DownloadCenter.Progress.Failed(
                    getString(R.string.download_interrupted),
                    lastName,
                ),
            )
        }
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(n: Notification) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIF_ID, n)
    }

    private fun buildNotification(
        name: String,
        bytes: Long,
        total: Long,
        text: String,
        paused: Boolean = false,
        /**
         * 成品已就绪、点通知就是去装它。
         *
         * 只有这一种情况才往 intent 里塞资产上下文。之前那个 contentIntent 是硬编码
         * 的裸 MainActivity：用户看到「下载完成」点下去，得到的只是一个不知道是哪个包
         * 的首页，还得自己重新找一遍。
         */
        actionable: Boolean = false,
    ): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                if (actionable) {
                    putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_INSTALL)
                    putExtra(MainActivity.EXTRA_ASSET, name)
                    putExtra(MainActivity.EXTRA_REPO, lastRepo)
                }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.download_notif_title, name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .apply { if (total > 0) setProgress(100, pct(bytes, total).toInt(), false) }
        // 之前这里只有一条「打开 App」，用户想中止只能去杀进程，而且没有任何地方
        // 告诉他「已经停了」—— 前台服务那条常驻通知本来就是给人一个出口的。
        if (paused) {
            builder.addAction(
                android.R.drawable.ic_media_play,
                getString(R.string.download_resume),
                actionIntent(DownloadCenter.ACTION_RESUME, name, REQUEST_RESUME),
            )
            builder.addAction(
                android.R.drawable.ic_menu_delete,
                getString(R.string.download_cancel),
                actionIntent(DownloadCenter.ACTION_CANCEL, name, REQUEST_CANCEL),
            )
        } else {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.download_pause),
                actionIntent(DownloadCenter.ACTION_PAUSE, name, REQUEST_PAUSE),
            )
            builder.addAction(
                android.R.drawable.ic_menu_delete,
                getString(R.string.download_cancel),
                actionIntent(DownloadCenter.ACTION_CANCEL, name, REQUEST_CANCEL),
            )
        }
        return builder.build()
    }

    /**
     * 通知按钮 -> 服务自己的 PendingIntent。
     *
     * 必须 IMMUTABLE（Android 12+ 强制），requestCode 要按 action 分开：系统按
     * requestCode 缓存 PendingIntent，共用一个会让后建的把先建的覆盖掉，表现为
     * 「暂停按钮按下去变成了取消」。
     */
    private fun actionIntent(action: String, name: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, DownloadService::class.java).apply {
                this.action = action
                putExtra(DownloadCenter.EXTRA_ASSET, name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * 通知正文：「3.2 MB/s · 42% · 18.3 MB / 60.9 MB」。
     *
     * 速度还没有可信值时就不给速度那一段，只留字节数 —— 用户拿它跟自家 Wi-Fi
     * 下的速度对一下，比光看个百分比有用。服务端没给 Content-Length 时就只给
     * 「已下多少」。
     */
    private fun progressText(speed: String, saved: Long, total: Long): String {
        val size = Env.formatSize(saved / 1_048_576.0) +
            if (total > 0L) " / ${Env.formatSize(total / 1_048_576.0)}" else ""
        return listOfNotNull(
            speed.ifBlank { null },
            if (total > 0L) "${pct(saved, total)}%" else null,
            size,
        ).joinToString(" · ")
    }

    /**
     * 速率采样器。
     *
     * 按「两次取样之间的字节差 ÷ 时间差」算。之前那个 `humanSpeed` 直接把**已下载
     * 总字节**除以 1 MB 写成了「MB/s」，于是「才下了 5 MB」显示成「5.0 MB/s」，
     * 数字还在一路往上涨，看着像越下越快。
     *
     * 两个细节：
     * - 攒到最短窗口才出值。进度回调每读满 64 KB 触发一次，网卡快的时候一秒能
     *   回调上百次，拿这种间隔算出来的全是噪声。
     * - 用 EWMA 而不是滑动窗口。滑动窗口要留一串样本，这里只需要一个数。
     *
     * 返回 null = 还没有可信速率，调用方这时**不要**编一个 0 出来。
     */
    private class SpeedMeter {
        private var markAt = 0L
        private var markBytes = 0L
        private var rate = -1.0
        private var primed = false

        fun sample(saved: Long, now: Long = SystemClock.elapsedRealtime()): Double? {
            if (markAt == 0L) {
                markAt = now
                markBytes = saved
                return null
            }
            val dt = (now - markAt) / 1000.0
            if (dt < MIN_WINDOW_S) return null
            val delta = saved - markBytes
            markAt = now
            markBytes = saved
            if (delta < 0L) {
                // 进度被重置了（换镜像从零开始）：上一个速率立刻作废，
                // 不然会把一次负增长当成速率继续报
                rate = -1.0
                primed = false
                return null
            }
            val inst = delta / dt
            rate = if (primed) rate + ALPHA * (inst - rate) else inst
            primed = true
            return rate
        }

        fun reset() {
            markAt = 0L
            markBytes = 0L
            rate = -1.0
            primed = false
        }

        private companion object {
            /** 最短取样窗口 0.4 秒：比这更密的间隔量出来的是抖动，不是速率 */
            const val MIN_WINDOW_S = 0.4
            const val ALPHA = 0.35
        }
    }

    private companion object {
        const val TAG = "FitHubDownload"
        const val CHANNEL = "download"

        /** 与 [DownloadCenter.NOTIF_ID] 同一条通知，两边必须一致 */
        const val NOTIF_ID = DownloadCenter.NOTIF_ID

        // 三个按钮各自的 requestCode，原因见 actionIntent 的说明
        const val REQUEST_PAUSE = 1
        const val REQUEST_RESUME = 2
        const val REQUEST_CANCEL = 3

        fun pct(saved: Long, total: Long): Int =
            if (total > 0) ((saved.toDouble() / total) * 100).toInt().coerceIn(0, 100) else 0

        /**
         * [bytesPerSec] 才是速率（字节/秒）。传已下载总量进来是以前那个 bug 的根因：
         * 那不是速度，那叫「下了多少」。
         */
        fun humanSpeed(bytesPerSec: Double): String =
            String.format(Locale.US, "%.1f MB/s", bytesPerSec / 1_048_576.0)
    }
}
