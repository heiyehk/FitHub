package com.heiyehk.fithub.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heiyehk.fithub.BuildConfig
import com.heiyehk.fithub.data.AppLocale
import com.heiyehk.fithub.data.Asset
import com.heiyehk.fithub.data.Async
import com.heiyehk.fithub.data.DeviceState
import com.heiyehk.fithub.data.install.ApkLibrary
import com.heiyehk.fithub.data.install.withDownloadedFacts
import com.heiyehk.fithub.data.Explain
import com.heiyehk.fithub.data.FitEngine
import com.heiyehk.fithub.data.FitRepository
import com.heiyehk.fithub.data.LinkEngine
import com.heiyehk.fithub.data.parse.ApkParser
import com.heiyehk.fithub.data.remote.GitHubApi
import com.heiyehk.fithub.data.remote.UserDto
import com.heiyehk.fithub.data.Readme
import com.heiyehk.fithub.data.HistoryEntry
import com.heiyehk.fithub.data.HomeSection
import com.heiyehk.fithub.data.HomeSectionStore
import com.heiyehk.fithub.data.HistoryStore
import com.heiyehk.fithub.data.ImportOutcome
import com.heiyehk.fithub.data.MyRepos
import com.heiyehk.fithub.data.Repo
import com.heiyehk.fithub.data.RankBoard
import com.heiyehk.fithub.data.Env
import com.heiyehk.fithub.data.ScanEngine
import com.heiyehk.fithub.data.ScanResult
import com.heiyehk.fithub.data.ScanStore
import com.heiyehk.fithub.data.ScannedApp
import com.heiyehk.fithub.data.SigningRelation
import com.heiyehk.fithub.data.Prefs
import com.heiyehk.fithub.data.SubscriptionStore
import com.heiyehk.fithub.data.TokenStore
import com.heiyehk.fithub.data.remote.ApiResult
import com.heiyehk.fithub.data.remote.DeviceFlowClient
import com.heiyehk.fithub.data.SubscriptionSync
import com.heiyehk.fithub.data.SubscriptionTransfer
import com.heiyehk.fithub.data.SyncOutcome
import com.heiyehk.fithub.data.remote.WebDavClient
import com.heiyehk.fithub.data.remote.WebDavConfig
import com.heiyehk.fithub.data.remote.WebDavPreset
import com.heiyehk.fithub.data.toSubscription
import com.heiyehk.fithub.R
import com.heiyehk.fithub.ui.components.AppTile
import com.heiyehk.fithub.ui.components.tap
import com.heiyehk.fithub.ui.detail.DetailMetrics
import com.heiyehk.fithub.ui.detail.DetailPanel
import com.heiyehk.fithub.ui.device.DeviceScreen
import com.heiyehk.fithub.ui.history.HistoryScreen
import com.heiyehk.fithub.ui.home.ALL_LANGS
import com.heiyehk.fithub.ui.home.HomeScreen
import com.heiyehk.fithub.ui.home.HomeSectionManager
import com.heiyehk.fithub.ui.icons.FiCheck
import com.heiyehk.fithub.ui.profile.InfoHeading
import com.heiyehk.fithub.ui.profile.InfoNote
import com.heiyehk.fithub.ui.profile.InfoScreen
import com.heiyehk.fithub.ui.profile.ProfileScreen
import com.heiyehk.fithub.ui.person.PersonScreen
import com.heiyehk.fithub.ui.person.MyProjectsScreen
import com.heiyehk.fithub.ui.history.RecordsScreen
import com.heiyehk.fithub.ui.profile.LicenseScreen
import com.heiyehk.fithub.ui.profile.LoginScreen
import com.heiyehk.fithub.ui.profile.MirrorScreen
import com.heiyehk.fithub.ui.profile.PrivacyScreen
import com.heiyehk.fithub.ui.profile.ThemeScreen
import com.heiyehk.fithub.ui.profile.UpdateScreen
import com.heiyehk.fithub.ui.search.SearchScreen
import com.heiyehk.fithub.ui.subscribe.SubscribeScreen
import com.heiyehk.fithub.ui.sync.WebDavScreen
import com.heiyehk.fithub.ui.theme.FitMotion
import com.heiyehk.fithub.ui.theme.FitTheme
import com.heiyehk.fithub.ui.theme.FitTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.Dispatchers

/**
 * 「这个仓库有没有安装包」的检查状态。
 *
 * 「没有」和「查失败」分开：网络异常时显示查失败，不当成没有产物。
 */
enum class InstallCheck { Checking, Yes, No, Failed }

/** 记录每张卡片图标在根坐标系里的位置，供飞行动画取起点；用普通 Map 避免写入触发重组 */
@Stable
class TileBoundsRegistry {    private val map = HashMap<String, Rect>()

    fun record(id: String, rect: Rect) {
        map[id] = rect
    }

    fun get(id: String): Rect? = map[id]
}

/**
 * 首页连按两次返回的确认窗口。
 *
 * 2 秒：够短到不像在等，长到用户「想退出」的意图不会因为手抖一次就作废。
 * 超过这个间隔就重新计一次，第一次的提示也跟着失效。
 */
private const val EXIT_CONFIRM_MS = 2000L

/**
 * 根宿主。
 *
 * 遮罩、内容层缩小、面板滑入和图标飞行共用同一个 Animatable 进度，
 * 中途反向点击能连续衔接。
 */
@Composable
fun FitHubApp() {
    val p = FitTheme.palette
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val progress = remember { Animatable(0f) }
    var tab by remember { mutableStateOf(AppTab.Discover) }
    var active by remember { mutableStateOf<Repo?>(null) }

    /**
     * README 按需加载：null = 尚未触发，Loading = 请求中。
     *
     * 不放进 [active]（Repo）里，因为它只在用户点开「说明」tab 时才需要，
     * 跟仓库元信息一起拉会白耗配额。关闭详情时清空。
     */
    var readme by remember { mutableStateOf<Async<Readme>?>(null) }

    /**
     * 详情那次请求的完整结果。
     *
     * 面板渲染的是 [active]（可能是列表里的简略占位数据），
     * 而缓存来源与年龄只有这里有，所以单独传一份给面板读。
     */
    var detailState by remember { mutableStateOf<Async<Repo>?>(null) }

    /** 手动刷新当前详情 */
    var refreshingDetail by remember { mutableStateOf(false) }

    /**
     * 历史足迹。纯本机记录，不进 WebDAV 同步。
     *
     * 存在 state 里而不是每次读文件：清除单条后列表要立刻反映。
     */
    var history by remember { mutableStateOf(HistoryStore.list(context)) }

    /**
     * 已下载清单。装完 / 打开之后要刷新，让「下载与安装记录」那一页的按钮
     * 立刻从「安装」变成「打开」，不用等下次进页面。
     */
    var libraryState by remember { mutableStateOf(ApkLibrary.list(context)) }

    /**
     * 全屏页面栈。顺序即叠放顺序，最后一项在最上面。
     *
     * 取代原来每页一个布尔量的写法，理由见 [Page]：布尔量只说「现在开着哪几页」，
     * 说不清「是怎么走到这儿的」，于是打开仓库详情时顺手把来源页关掉，
     * 返回时底下什么都不剩，只能落回首页。详见 docs/bugs.md 的 BUG-08。
     */
    val pages = remember { mutableStateListOf<Page>() }

    /** 打开一页。已经是最上面那一页就不重复压栈 */
    fun openPage(page: Page) {
        if (pages.lastOrNull() != page) pages.add(page)
    }

    /** 返回：弹掉最上面那一页 */
    fun closePage() {
        if (pages.isNotEmpty()) pages.removeAt(pages.lastIndex)
    }

    /**
     * 这一页是不是当前最上面的那一页。
     *
     * 渲染时**只画栈顶那一页**，而不是「栈里每一页都画、靠代码里的先后顺序决定谁盖谁」。
     * 后者要求「压栈顺序」和「声明顺序」永远一致 —— 两者一旦对不上，
     * 就会出现「页面明明在栈底下，却盖住了栈顶那页」，而且这种错没有任何提示。
     * 让叠放顺序直接从栈里读出来，就不存在对不上的可能。
     */
    fun isTop(page: Page): Boolean = pages.lastOrNull() == page

    /** 首页板块配置与它的管理页 */
    val sectionStore = remember { HomeSectionStore(context) }
    var homeSections by remember { mutableStateOf(sectionStore.load()) }
    val sectionManagerState = rememberLazyListState()
    var webDavConfig by remember { mutableStateOf(WebDavConfig.load(context)) }
    var webDavSyncing by remember { mutableStateOf(false) }
    var lastSyncOutcome by remember { mutableStateOf<SyncOutcome?>(null) }

    val webDavState = rememberLazyListState()

    val historyState = rememberLazyListState()

    fun recordHistory(e: HistoryEntry) {
        scope.launch {
            withContext(Dispatchers.IO) { HistoryStore.record(context, e) }
            history = HistoryStore.list(context)
        }
    }

    /**
     * 记一条「看过这个仓库」。
     *
     * 描述 / 最后提交 / release 版本都在这里带上：一屏历史全是裸仓库名的话，
     * 用户根本想不起自己看过什么。`—`（没有 release）和空串都按「没有」处理，不写进
     * detail 里占位。
     */
    fun recordRepoViewed(repo: Repo) {
        val meta = listOfNotNull(
            repo.owner.takeIf { it.isNotBlank() },
            repo.version.takeIf { it.isNotBlank() && it != "—" },
            repo.date.takeIf { it.isNotBlank() && it != "—" }
                ?.let { context.getString(R.string.history_meta_last_push, it) },
        ).joinToString(" · ")
        recordHistory(
            HistoryEntry(
                kind = HistoryEntry.Kind.RepoViewed,
                ref = repo.id,
                title = repo.name,
                detail = meta,
                at = System.currentTimeMillis(),
                desc = repo.desc.trim(),
                lastPush = repo.date,
                release = repo.version,
            )
        )
    }

    val clipboard = LocalClipboard.current

    var mounted by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf<Rect?>(null) }
    /** 语言筛选的初始值。用 [ALL_LANGS] 常量而不是字面量：它是筛选的哨兵值，
     * 一旦资源化成随语言变化的文案，比较就会和 HomeScreen 里的常量对不上。 */
    var langFilter by remember { mutableStateOf(ALL_LANGS) }
    var message by remember { mutableStateOf<String?>(null) }
    /**
     * 主体页的资料与仓库列表，**按 login 分桶**。
     *
     * 之前是两个全局变量，于是「打开 A 的主体页 → 进仓库详情 → 返回」这条路走不通，
     * 而且切到另一个人时前一个人的数据会闪一下。现在一份数据跟着 [Page.Person.login] 走，
     * 返回时还是同一个人那份。
     */
    val personProfile = remember { mutableStateMapOf<String, Async<UserDto>>() }
    val personRepos = remember { mutableStateMapOf<String, Async<List<Repo>>>() }
    val installable = remember { mutableStateMapOf<String, InstallCheck>() }
    var scan by remember { mutableStateOf<ScanResult?>(null) }
    /** 扫描进行中（后台那次也算）。用来把「重新扫描」按成不可连点 */
    var scanning by remember { mutableStateOf(false) }

    /** 用户确认过的「已装应用 → 仓库」绑定：packageName -> 仓库 */
    val linkBindings = remember { mutableStateMapOf<String, Repo>() }
    var lookupPkg by remember { mutableStateOf<String?>(null) }
    var lookupCandidates by remember { mutableStateOf<List<Repo>>(emptyList()) }
    var lookupFailed by remember { mutableStateOf(false) }

    // authProvider 在每次请求时现读，所以登录/退出立刻对后续请求生效，
    // 不需要重建 GitHubApi（重建会把 HttpClient 和缓存一起丢掉）。
    //
    // 缓存放 **filesDir** 而不是 cacheDir：cacheDir 系统在存储紧张时会直接清掉、
    // 不需要用户确认，而未登录只有 60 次/小时配额。清一次意味着用户要把每个看过的
    // 仓库、README、更新日志重新刷回来 —— 那是配额，不是网络带宽的问题。
    // filesDir 同样随卸载删除，对这份「随时可重建、但重建很贵」的数据刚好合适。
    val api = remember {
        GitHubApi(File(context.filesDir, "gh-cache")) { TokenStore.headerValue(context) }
    }
    val repo = remember { FitRepository(api) }
    //
    // 别名：下面 DetailPanel 那个 ctive?.let { repo -> ... } 的 lambda 参数
    // **同名**，那个作用域里的 repo 是仓库模型而不是这一份。在那里面写 repo
    // 会安静地拿到错的那个 —— 编译不报错，只是 codeRepo 传进去一个 DTO。
    val gh = repo

    /** 登录态。token 本身在 TokenStore（Keystore 加密），这里只缓存给人看的资料 */
    var me by remember { mutableStateOf<UserDto?>(null) }

    /**
     * 自己的仓库 + star / fork / watch 汇总。
     *
     * 「我的」页那三个数字与「我的项目」页的列表是**同一批请求**，所以只存一份。
     * 分开拉的话来回切页面会各花一次配额，而且两个页面可能显示不同时间点的数字，
     * 用户会以为是两个不一致的真相。
     *
     * null = 未登录或还没开始拉。这两种情况界面上的处理不一样，所以不合并：
     * 未登录时压根不显示这一块（`/user/repos` 没有 token 会 404）。
     */
    var myRepos by remember { mutableStateOf<Async<MyRepos>?>(null) }

    /**
     * 关注列表。落盘在 filesDir，服务器上没有对应记录。
     *
     * 启动时读一次，关注/取消时整体替换 —— 列表规模是几十条，
     * 整份换掉比维护增量状态更不容易出错。
     */
    var subscriptions by remember { mutableStateOf(SubscriptionStore.all(context)) }

    /** 正在刷新的仓库，UI 用它显示行内的进度态 */
    var refreshingSubs by remember { mutableStateOf(emptySet<String>()) }

    fun toast(text: String, note: String? = null) {
        message = if (note.isNullOrBlank()) text else "$text · $note"
    }

    fun isFollowing(fullName: String) =
        subscriptions.any { it.fullName.equals(fullName, ignoreCase = true) }

    /**
     * 跑一次 WebDAV 同步。手动触发，不轮询。
     *
     * 结果写进 [lastSyncOutcome] 给配置页显示 —— 同步失败必须让用户看见
     * 「本机数据没被动」，否则他会以为同步成了。
     */
    fun runSync(force: Boolean = false) {
        val conf = webDavConfig
        if (!conf.enabled || conf.isBlank) {
            lastSyncOutcome = SyncOutcome.NotConfigured(Explain.of(R.string.sync_not_configured_reason))
            openPage(Page.WebDav)
            return
        }
        if (webDavSyncing) return
        webDavSyncing = true
        scope.launch {
            val client = WebDavClient(conf)
            val outcome = if (force) {
                SubscriptionSync.forcePush(client, subscriptions)
            } else {
                SubscriptionSync.run(context, client, subscriptions)
            }
            lastSyncOutcome = outcome
            webDavSyncing = false
            if (outcome is SyncOutcome.Pulled) {
                subscriptions = SubscriptionStore.all(context)
            }
            toast(
                when (outcome) {
                    is SyncOutcome.Pushed -> context.getString(R.string.toast_sync_pushed, outcome.count)
                    is SyncOutcome.Pulled -> context.getString(R.string.toast_sync_pulled, outcome.added)
                    is SyncOutcome.Failed -> context.getString(R.string.toast_sync_failed)
                    is SyncOutcome.NotConfigured -> context.getString(R.string.toast_sync_not_run)
                },
                when (outcome) {
                    is SyncOutcome.Failed -> explainText(context, outcome.reason)
                    is SyncOutcome.NotConfigured -> explainText(context, outcome.reason)
                    is SyncOutcome.Pulled -> context.getString(R.string.toast_sync_pulled_note, outcome.skipped)
                    is SyncOutcome.Pushed -> null
                },
            )
        }
    }
    var stars by remember { mutableStateOf<Async<List<Repo>>>(Async.Loading) }
    var updated by remember { mutableStateOf<Async<List<Repo>>>(Async.Loading) }
    /**
     * topic 类板块的加载态，按板块 id 索引。
     *
     * 默认未加载：用「key 不存在」表达，和 [Async.Loading] 区分开 ——
     * 没触发过和正在查是两回事，UI 要给不同的入口。
     *
     * Agent 板块只是这个 map 里的一个条目（id = builtin-agent），不是特例。
     */
    var topicStates by remember { mutableStateOf<Map<String, Async<List<Repo>>>>(emptyMap()) }
    /** 订阅页的排行榜，按 [RankBoard.id] 存。与 topicStates 同一套 Async 三态 */
    var rankStates by remember { mutableStateOf<Map<String, Async<List<Repo>>>>(emptyMap()) }
    /**
     * 正在下拉刷新的榜。
     *
     * **故意与 [rankStates] 分开**：首次加载没数据看，要转圈；
     * 刷新时旧榜还留在屏幕上，置成 Loading 会让整页闪成骨架屏。
     */
    var refreshingRanks by remember { mutableStateOf<Set<String>>(emptySet()) }
    /** 精选区：按确切全名拉取 */
    var featured by remember { mutableStateOf<Async<List<Repo>>>(Async.Loading) }

    /** 下拉刷新进行中。期间保留旧内容，不把状态置成 Loading */
    var refreshingDiscover by remember { mutableStateOf(false) }
    var rate by remember { mutableStateOf(api.lastRemaining to api.lastLimit) }

    // 「经我安装」「下载记录」这两个计数**从 history 派生**，不再各自维护内存计数。
    // 之前是两个内存里的 mutable 状态，App 一重启就归零 —— 于是「经我安装 0 个」
    // 这种假数字会长期摆在那里。history 本身是落盘的，派生出来就跟着进程活多久都对。
    val installedViaUsCount = remember(history) {
        history.count { it.kind == HistoryEntry.Kind.InstalledViaUs }
    }
    val downloadCount = remember(history) {
        history.count { it.kind == HistoryEntry.Kind.Downloaded }
    }

    val bounds = remember { TileBoundsRegistry() }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val discoverState = rememberLazyListState()
    val deviceState = rememberLazyListState()
    val subscribeState = rememberLazyListState()
    val profileState = rememberLazyListState()

    LaunchedEffect(message) {
        if (message != null) {
            delay(2600)
            message = null
        }
    }

    /** 逐条把绑定换成完整仓库信息（串行请求），再回写本机档案 */
    fun resolveLinks(s: ScanResult) {
        scope.launch {
            val links = LinkEngine.bindings(context)
            if (links.isEmpty()) {
                linkBindings.clear()
                LinkEngine.applyBindings(s, emptyMap())
                return@launch
            }
            val byPkg = s.apps.associateBy { it.packageName }
            for ((pkg, fullName) in links) {
                if (pkg !in byPkg) { linkBindings.remove(pkg); continue }
                if (linkBindings[pkg]?.id == fullName) continue
                when (val d = repo.detail(fullName)) {
                    is Async.Ok -> linkBindings[pkg] = d.value
                    else -> linkBindings.remove(pkg)
                }
            }
            // device 是派生状态：先写绑定再重算，顺序反了拿到的还是旧值
            LinkEngine.applyBindings(s, linkBindings)
            linkBindings.keys.toList().forEach { pkg ->
                linkBindings[pkg]?.let { linkBindings[pkg] = FitEngine.withDeviceState(it) }
            }
        }
    }

    /**
     * 重扫本机已装应用，并落盘。
     *
     * [useCache] 只给冷启动用：先把盘上那份显示出来（重活是逐个算签名指纹，
     * 装得多的话要跑几百毫秒），再在后台重扫一遍覆盖。用户按「重新扫描」时
     * 传 false —— 那是他明确说了「现在扫」，给一份旧的等于没按。
     */
    fun refreshScan(force: Boolean = false) {
        if (scanning) return
        scanning = true
        scope.launch {
            try {
                if (!force) {
                    ScanStore.read(context)?.first?.let { cached ->
                        scan = cached
                        resolveLinks(cached)
                    }
                }
                val s = withContext(Dispatchers.IO) { ScanEngine.scan(context) }
                scan = s
                // 无条件调用：绑定表为空时 resolveLinks 会清掉 linkBindings 并
                // 重新 applyBindings，漏掉这一步会让「解绑了但列表里还在」。
                // 加个 `if (bindings.isNotEmpty())` 的门反而会跳过这次清理。
                resolveLinks(s)
                withContext(Dispatchers.IO) { ScanStore.write(context, s) }
            } finally {
                scanning = false
            }
        }
    }

    LaunchedEffect(Unit) {
        LinkEngine.load(context)
        // 冷启动清掉 7 天前的缓存。键里的查询串 / perPage 一改，旧键就成了永远没人读
        // 也没人删的孤儿；7 天远超 6 小时 TTL，删掉不影响任何一次命中。
        // 放在这里而不是每次进榜：清理是全局的，跟着某个 tab 走反而会漏。
        api.pruneCache()
        refreshScan()
    }

    val targetLeft = with(density) { DetailMetrics.sidePad.toPx() }
    val targetTop = with(density) { DetailMetrics.iconTop(topInset).toPx() }

    /** 发现页 Hero 用的「N 个可升级」：只统计已确认绑定且判定为 Upgrade 的项 */
    val upgradableCount = remember(linkBindings, scan) {
        val s = scan ?: return@remember 0
        val byPkg = s.apps.associateBy { it.packageName }
        linkBindings.count { (pkg, linked) ->
            val app = byPkg[pkg] ?: return@count false
            FitEngine.deviceStateFor(app, linked) is DeviceState.Upgrade
        }
    }
    val targetSize = with(density) { DetailMetrics.iconSize.toPx() }

    fun open(repo: Repo) {
        if (active?.id == repo.id) return
        source = bounds.get(repo.id)
        active = repo
        // 这条路径不补拉详情：调用方只有本机页，传进来的都是 confirmLink / resolveLinks
        // 已经按确切全名解析过、并算过本机状态的完整数据（assets、releases 都在）。
        // 所以这里不是 Loading —— 面板显示的就是最终数据，直接标成 Ok。
        //
        // 别的来源（发现页、搜索、主体页、订阅页）走 open(fullName)，那份要补拉。
        detailState = Async.Ok(repo, fromCache = false)
        readme = null
        mounted = true
        recordRepoViewed(repo)
        scope.launch { progress.animateTo(1f, animationSpec = FitMotion.PanelIn) }
    }

    /**
     * 精选区按精确全名拉取，见 [FitRepository.featured]。
     *
     * 全名差一个字符就是 404，拉不到就只显示已拉到的数量，不用别的条目补位。
     */
    val featuredNames = listOf("organicmaps/organicmaps", "beemdevelopment/Aegis", "zed-industries/zed")

    fun loadDiscover() {
        scope.launch {
            stars = Async.Loading
            updated = Async.Loading
            featured = Async.Loading
            stars = repo.stars()
            updated = repo.updated()
            featured = repo.featured(featuredNames)
            rate = repo.rate()
        }
    }

    /**
     * 下拉刷新。
     *
     * 期间不把状态置成 Loading —— 那样整页会闪成骨架屏，而旧数据其实还能看。
     * 保留旧内容直到新数据回来，期间由 [refreshingDiscover] 驱动下拉指示器。
     */
    fun refreshDiscover() {
        if (refreshingDiscover) return
        refreshingDiscover = true
        repo.invalidateDiscover()
        scope.launch {
            stars = repo.stars()
            updated = repo.updated()
            featured = repo.featured(featuredNames)
            rate = repo.rate()
            refreshingDiscover = false
        }
    }

    /**
     * 加载一个 topic 板块。由用户点击触发，不预先消耗配额。
     *
     * 查一次就花一次请求，所以：正在查的直接返回（防连点）、
     * 已经成功过的也不重查（用户想刷新用下拉）。
     */
    fun loadTopicSection(section: HomeSection) {
        val q = section.query ?: return
        if (topicStates[section.id] is Async.Loading) return
        topicStates = topicStates + (section.id to Async.Loading)
        scope.launch {
            val r = repo.search(q)
            topicStates = topicStates + (section.id to r)
            rate = api.lastRemaining to api.lastLimit
        }
    }

    /**
     * 真正发一次请求。**必须声明在调用者之前** —— 局部函数是���声明后使用。
     *
     * [isRefresh] 决定要不要先置成 [Async.Loading]：
     * - 首次加载（false）：盘上没数据，非转圈不可，所以置 Loading。
     * - 下拉刷新（true）：**不置**。旧榜还在屏幕上，置了 Loading 整页会闪成骨架屏，
     *   而用户下拉恰恰说明他想继续看这份数据。理由与 [refreshDiscover] 相同，
     *   它也是「期间不把状态置成 Loading」。
     */
    fun fetchRank(board: RankBoard, isRefresh: Boolean) {
        if (isRefresh) {
            if (board.id in refreshingRanks) return
            refreshingRanks = refreshingRanks + board.id
        } else {
            rankStates = rankStates + (board.id to Async.Loading)
        }
        scope.launch {
            try {
                val next = repo.rank(board)
                // 下拉刷新失败**不覆盖**已有的数据。
                //
                // 用户可能 3 秒前还在看这 30 条，一次网络抖动不该让整页变成错误页 ——
                // 而这里连兜底都没有：上面 [refreshRank] 已经把磁盘缓存删了，
                // 所以 GitHubApi 的 stale-while-revalidate（过期数据也先返回）也救不了。
                // 保留旧数据 + 一句提示，是这里唯一说得通的处理。
                if (isRefresh && next is Async.Err) {
                    toast(context.getString(R.string.toast_rank_refresh_failed), next.message)
                } else {
                    rankStates = rankStates + (board.id to next)
                }
                rate = api.lastRemaining to api.lastLimit
            } finally {
                // 刷新失败也要摘掉标记，否则下拉指示器永远转下去、且再也拉不动第二次
                if (isRefresh) refreshingRanks = refreshingRanks - board.id
            }
        }
    }

    /**
     * 停在某个榜上时拉一次，key = [RankBoard.id]。
     *
     * **只对「从没加载过」的榜发请求。** Err 也直接返回：触发条件是滑动，
     * 用户来回滑两下就会连着打好几次请求；而失败往往正是配额或网络出问题的时候，
     * 那时自动重试最没用也最烧配额（未登录 search 只有 10 次/分钟）。
     * 要重来请下拉刷新，或按错误页里的「重试」。
     */
    fun loadRank(board: RankBoard) {
        if (rankStates[board.id] != null) return
        fetchRank(board, isRefresh = false)
    }

    /** 用户在错误页点了「重试」，无条件重来一次。盘上没数据，转圈是对的 */
    fun retryRank(board: RankBoard) = fetchRank(board, isRefresh = false)

    /**
     * 下拉刷新一个榜。
     *
     * **必须先作废缓存** —— [loadRank] 只认「没加载过」，不清缓存的话下拉会
     * 安静地什么也不做（和 `refreshDiscover`、订阅刷新踩的是同一个坑）。
     * 只作废这一个榜，其它榜和首页那两段不受影响。
     */
    fun refreshRank(board: RankBoard) {
        repo.invalidateRank(board)
        fetchRank(board, isRefresh = true)
    }

    /**
     * 冷启动用**缓存**把 topic 板块先填上。
     *
     * 这些板块的结果原来只活在一个内存 map（[topicStates]）里，进程一回收就归零 ——
     * 于是用户每次开 App 看到的都是一排「点我去查」的按钮，每点一次烧一次配额，
     * 而那份数据其实早就下过、好好躺在缓存文件里。这就是「板块没缓存」的全部症状。
     *
     * **只读缓存，绝不发请求**（[GitHubApi.peekSearch]）：盘上有就显示并照实标出
     * 「N 分钟前」，没有就还是那个按钮 —— 不能为了填个缓存就替用户花掉配额。
     * 真正要联网的路径仍然是用户点一下，或者下拉刷新。
     */
    fun hydrateTopicSectionsFromCache(sections: List<HomeSection>) {
        val next = topicStates.toMutableMap()
        var filled = 0
        sections.filter { it.enabled && it.kind == HomeSection.Kind.Topic }.forEach { s ->
            // 已经有的不覆盖：可能是用户刚点出来的新鲜结果
            if (next[s.id] != null) return@forEach
            val hit = repo.searchFromCache(s.query ?: return@forEach) ?: return@forEach
            next[s.id] = hit
            filled++
        }
        if (filled > 0) topicStates = next
    }

    /**
     * 拉「我的项目」：仓库列表 + star/fork/watch 汇总。
     *
     * 登录后才拉，而且只在需要时拉：打开 App 不进「我的」页就不该为这几个数字
     * 烧掉 1~N 次请求（仓库超过 100 个还要翻页）。
     *
     * [force] 是手动刷新：必须先作废**全部页**的缓存，否则点「刷新」只是把同一份
     * 缓存再读一遍，用户会以为刷新没生效。
     */
    fun loadMyRepos(force: Boolean = false) {
        // 未登录直接清掉：换了账号（或退出）之后，旧账号的汇总留着比没有更糟
        if (!TokenStore.isLoggedIn(context)) {
            myRepos = null
            return
        }
        scope.launch {
            if (force) repo.invalidateMyRepos()
            if (myRepos == null || force) myRepos = Async.Loading
            myRepos = repo.myRepos()
            rate = api.lastRemaining to api.lastLimit
        }
    }

    LaunchedEffect(Unit) {
        // 冷启动先看有没有存着的 token：有就把「我是谁」补上（失败不影响别的功能）
        if (TokenStore.isLoggedIn(context)) {
            when (val r = api.me()) {
                is ApiResult.Ok -> me = r.value
                else -> Unit
            }
            loadMyRepos()
        }
        loadDiscover()
        // topic 板块先用缓存填上（只读盘、不发请求）。不调这一下的话，这些板块的
        // 结果只活在内存里，进程一回收就归零，用户每次冷启动看到的都是一排按钮。
        hydrateTopicSectionsFromCache(homeSections)
    }

    /**
     * 从已加载的列表里找简略数据，供详情打开时先占位。
     *
     * 必须覆盖**全部**来源。漏掉任何一个的后果不是「少一个占位」而是「面板根本不出现」：
     * 面板是 `active?.let` 渲染的，byId 返回 null 时 active 为 null，用户点了卡片只能
     * 干等补拉的网络回来 —— 表现就是「点一下卡住，过一会儿详情才出来」。
     * 搜索结果存在 SearchScreen 内部、够不着，那条路径靠调用方传 placeholder 兜底。
     */
    fun byId(fullName: String): Repo? {
        // personRepos 现在是 Map<login, 列表>（一个主体页的数据跟着 login 走，
        // 主体页在栈上待着期间不能丢），所以这里要把所有桶都看一遍。
        val sources: List<Async<List<Repo>>> =
            listOf(stars, updated, featured) + personRepos.values.toList()
        for (s in sources) {
            val list = (s as? Async.Ok<List<Repo>>)?.value ?: continue
            list.firstOrNull { it.id == fullName }?.let { return it }
        }
        return null
    }

    /**
     * 拉详情。
     *
     * 结果同时写进 [detailState]（供面板读缓存来源与年龄）和 [active]（供渲染）。
     * 失败只 toast，不清空 [active] —— 列表占位数据还能继续看。
     */
    val loadDetail: suspend (String) -> Unit = { fullName ->
        when (val d = repo.detail(fullName)) {
            is Async.Ok -> {
                detailState = d
                // 换新的详情数据要重算本机状态：device 是 copy() 不会自动重算的派生字段。
                // 少了这一步，从发现页/搜索进详情时绑过的仓库会丢掉「本机 x.y.z」那枚徽标。
                //
                // withDownloadedFacts 在这之后：已下载过的产物要按**真实清单**重算适配
                // 结论，GitHub 那边只给文件名推断的 ABI，那是两回事。
                active = FitEngine.withDeviceState(d.value).withDownloadedFacts(libraryState)
            }
            is Async.Err -> {
                detailState = d
                toast(d.message)
            }
            Async.Loading -> Unit
        }
    }

    /**
     * 手动刷新当前详情。
     *
     * 先作废这条的缓存，否则请求会直接命中未过期缓存，按钮点了跟没点一样。
     */
    fun refreshDetail() {
        val full = active?.id ?: return
        if (refreshingDetail) return
        refreshingDetail = true
        scope.launch {
            api.invalidateRepo(full)
            loadDetail(full)
            refreshingDetail = false
        }
    }

    /**
     * 打开详情：先用列表里的简略数据占位，再按 id 拉完整详情覆盖。
     *
     * [placeholder] 是调用方手上已经有的那个 Repo。给上它就不用再靠 [byId] 反查 ——
     * 搜索结果那类 byId 够不着的来源靠它才能立刻出面板。
     */
    fun open(
        fullName: String,
        fromEl: androidx.compose.ui.layout.LayoutCoordinates? = null,
        placeholder: Repo? = null,
    ) {
        if (active?.id == fullName) return
        source = fromEl?.boundsInRoot()
        active = placeholder ?: byId(fullName)
        readme = null
        // 必须在发请求前就置成 Loading：loadDetail 只在结果回来时才写状态，
        // 期间面板渲染的是列表快照，assets / history 都还是空的
        detailState = Async.Loading
        mounted = true
        // 占位数据有就记，没有就算了 —— 别因为记一条足迹把面板卡住
        active?.let { recordRepoViewed(it) }
        scope.launch { progress.animateTo(1f, animationSpec = FitMotion.PanelIn) }
        scope.launch { loadDetail(fullName) }
    }

    /**
     * 关注 / 取消关注当前详情页的仓库。
     *
     * 元信息取手上这份 [Repo] 作为初始快照，所以关注完立刻能离线看到，
     * 不用等下一次请求。
     */
    fun toggleFollow(target: Repo) {
        if (isFollowing(target.id)) {
            SubscriptionStore.unfollow(context, target.id)
            subscriptions = SubscriptionStore.all(context)
            toast(context.getString(R.string.toast_unfollowed, target.name))
            return
        }
        SubscriptionStore.follow(context, target.toSubscription())
        subscriptions = SubscriptionStore.all(context)
        toast(
            context.getString(R.string.toast_followed, target.name),
            context.getString(R.string.toast_followed_note),
        )
    }

    /**
     * 刷新一条关注的元信息。串行调用，避免并发撞限流。
     *
     * 拉失败不动本地快照 —— 用空值覆盖会让用户丢掉已存的描述和 star 数。
     *
     * ⚠️ 这里**必须**写 [refreshingSubs]��原来只有 [refreshAllSubscriptions] 会写它，
     * 于是行内的那个转圈只在「全部刷新」时才可能亮 —— 单条刷新点下去界面纹丝不动，
     * 看起来像按钮坏了。真机实测确认过：点完 500ms 截图与点之前完全一致。
     *
     * 用 try/finally 收尾：请求抛异常时不能把这一项永远留在「刷新中」，
     * 那会让用户再也点不动它，而且没有任何东西告诉他为什么。
     */
    fun refreshSubscription(fullName: String, onDone: (Boolean) -> Unit = {}) {
        // 已在刷就别再发一次，未登录配额 60/h 经不起连点
        if (fullName in refreshingSubs) return
        refreshingSubs = refreshingSubs + fullName
        // 手动刷新必须先作废缓存。api.repo / api.releases 都会先读磁盘缓存，
        // 缓存没过期就直接返回 —— 那「刷新」读的还是同一份数据，
        // 真机实测表现为：按下去 90ms 就提示「成功」，转圈根本没机会出现。
        // 与 [refreshDiscover] 里的 invalidateDiscover 是同一个理由。
        api.invalidateRepo(fullName)
        scope.launch {
            var ok = false
            try {
                ok = when (val d = repo.detail(fullName)) {
                    is Async.Ok -> {
                        SubscriptionStore.refreshMeta(context, fullName) { it.copy(
                            name = d.value.name,
                            owner = d.value.owner,
                            desc = d.value.desc,
                            stars = d.value.stars,
                            lang = d.value.lang,
                            topics = d.value.topics,
                            lastPush = d.value.date,
                            latestTag = d.value.version.takeIf { it != "—" }.orEmpty(),
                            latestReleaseDate = if (d.value.hasRealRelease) d.value.date else "",
                        ) }
                        true
                    }
                    is Async.Err -> {
                        toast(context.getString(R.string.toast_subs_refresh_failed, fullName), d.message)
                        false
                    }
                    Async.Loading -> false
                }
                if (ok) subscriptions = SubscriptionStore.all(context)
            } finally {
                refreshingSubs = refreshingSubs - fullName
            }
            onDone(ok)
        }
    }

    /**
     * 串行刷新全部关注的元信息。
     *
     * 串行是必须的：并发请求会直接撞上 GitHub 限流，而未登录配额只有 60 次/小时。
     * 一次全量刷新的代价是 N 次请求，N 就是关注数。
     */
    fun refreshAllSubscriptions() {
        val targets = subscriptions.map { it.fullName }
        if (targets.isEmpty() || refreshingSubs.isNotEmpty()) return
        refreshingSubs = targets.toSet()
        scope.launch {
            var ok = 0
            var failed = 0
            for (full in targets) {
                // 同 [refreshSubscription]：先作废缓存，否则「全部刷新」只是把磁盘上
                // 那份再读一遍。用户按了刷新却什么都没变，还提示「成功 N 条」。
                api.invalidateRepo(full)
                when (val d = repo.detail(full)) {
                    is Async.Ok -> {
                        SubscriptionStore.refreshMeta(context, full) { it.copy(
                            name = d.value.name,
                            owner = d.value.owner,
                            desc = d.value.desc,
                            stars = d.value.stars,
                            lang = d.value.lang,
                            topics = d.value.topics,
                            lastPush = d.value.date,
                            latestTag = d.value.version.takeIf { it != "—" }.orEmpty(),
                            latestReleaseDate = if (d.value.hasRealRelease) d.value.date else "",
                        ) }
                        ok++
                    }
                    is Async.Err -> failed++
                    Async.Loading -> Unit
                }
                refreshingSubs = refreshingSubs - full
            }
            subscriptions = SubscriptionStore.all(context)
            refreshingSubs = emptySet()
            toast(
                context.getString(R.string.toast_subs_refresh_done, ok),
                if (failed > 0) context.getString(R.string.toast_subs_refresh_partial, failed) else null,
            )
        }
    }

    /**
     * 解析并落盘导入结果。剪贴板与文件两条路径共用，保证判定一致。
     *
     * 放在最前面是因为 Kotlin 的局部函数不能前向引用：
     * 下面 importSubscriptions 和 importFromClipboardOrPicker 都要调它。
     */
    fun applyImport(text: String) {
        when (val outcome = SubscriptionTransfer.import(text, subscriptions)) {
            is ImportOutcome.Ok -> {
                if (outcome.added > 0) {
                    SubscriptionStore.replaceAll(context, outcome.merged)
                    subscriptions = SubscriptionStore.all(context)
                }
                val notes = buildString {
                    if (outcome.skipped > 0) {
                        append(context.getString(R.string.import_note_skipped, outcome.skipped))
                    }
                    if (outcome.invalid > 0) {
                        if (isNotEmpty()) append(" · ")
                        append(context.getString(R.string.import_note_invalid, outcome.invalid))
                    }
                }
                toast(context.getString(R.string.toast_import_done, outcome.added), notes.ifBlank { null })
            }

            is ImportOutcome.UnsupportedSchema -> toast(
                context.getString(R.string.toast_import_failed),
                context.getString(R.string.import_schema_mismatch, outcome.found, outcome.supported),
            )

            is ImportOutcome.Malformed -> toast(
                context.getString(R.string.toast_import_failed),
                context.getString(
                    R.string.import_malformed,
                    explainText(context, outcome.reason),
                ),
            )

            ImportOutcome.Empty -> toast(context.getString(R.string.toast_import_empty))
        }
    }

    /**
     * 导出订阅到用户选定的文件。
     *
     * SAF 的 ContentResolver 直接写，不需要存储权限。
     */
    fun exportSubscriptions(uri: android.net.Uri) {
        val subs = subscriptions
        if (subs.isEmpty()) {
            toast(context.getString(R.string.toast_export_empty))
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val out = context.contentResolver.openOutputStream(uri)
                        ?: error(context.getString(R.string.export_open_failed))
                    out.use { it.write(SubscriptionTransfer.export(subs).toByteArray(Charsets.UTF_8)) }
                }
            }
            result.fold(
                onSuccess = { toast(context.getString(R.string.toast_export_done, subs.size)) },
                onFailure = {
                    toast(
                        context.getString(R.string.toast_export_failed),
                        it.message ?: context.getString(R.string.export_write_denied),
                    )
                },
            )
        }
    }

    /** 从选定的文件导入。读失败只提示，不动本地数据 */
    fun importSubscriptions(uri: android.net.Uri) {
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                toast(
                    context.getString(R.string.toast_import_failed),
                    context.getString(R.string.import_read_failed),
                )
            } else {
                applyImport(text)
            }
        }
    }

    // SAF：用户自己选保存位置，不需要存储权限，也不需要在 manifest 里配 FileProvider。
    // 这两个 launcher 必须排在 exportSubscriptions / importSubscriptions 之后 ——
    // 它们的回调要调那两个函数，而 Kotlin 局部函数同样不能前向引用。
    val openExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { exportSubscriptions(it) } }

    val openImportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importSubscriptions(it) } }

    /**
     * 剪贴板优先。
     *
     * 从聊天里直接复制一段 JSON 是常见路径，比弹文件选择器快得多。
     * 剪贴板里不是 JSON 才退回选文件；导入完清掉剪贴板，
     * 免得下次点导入又命中同一段。
     */
    fun importFromClipboardOrPicker() {
        // LocalClipboard 只给 ClipEntry，文本要从 clipData 里取 —— 老的
        // ClipboardManager.getText() 已经没有对应物了。
        // 读写都是 suspend，而调用点在 onClick 里不是协程上下文，所以整个分支搬进 scope.launch。
        scope.launch {
            val text = clipboard.getClipEntry()?.clipData
                ?.getItemAt(0)?.coerceToText(context)?.toString()
            if (!text.isNullOrBlank() && text.trimStart().startsWith("{")) {
                applyImport(text)
                // setClipEntry(null) 是真清空。老 API 没有 clear()，只能 setText("")
                // 去骗下一轮的 startsWith 判断。
                clipboard.setClipEntry(null)
            } else {
                openImportPicker.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
        }
    }

    /**
     * 拉 README。只在用户点进「说明」tab 时触发，已经加载过的不重复请求。
     */
    fun loadReadme(fullName: String) {
        if (readme != null) return
        readme = Async.Loading
        scope.launch { readme = repo.readme(fullName) }
    }

    /**
     * 下载完成 / 装完之后重新读一次已下载清单。
     *
     * 清单是**落盘的**，所以每次进详情页都先拿它把产物的真实清单事实合上去
     * （见 [withDownloadedFacts]）—— 真实 minSdk / ABI / 签名 / 包名，以及据此
     * 重算出来的适配结论。解析不再是用户要单独点的动作：下载时就读过一次了。
     *
     * 顺带把「早期版本记的、没读过清单」的条目补读一次。那些字段是空值而不是
     * 0，而空值和「清单里真的没有」同形，不补就会一直停在「只知道文件名」。
     * 文件还在盘上时补读是几十毫秒；文件没了（用户删过）就跳过，那条记录会由
     * [ApkLibrary.list] 自己剔除。
     */
    fun refreshLibrary() {
        val pending = ApkLibrary.list(context).filter { it.installable && !it.manifestRead && it.file().exists() }
        if (pending.isNotEmpty()) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    pending.forEach { e ->
                        runCatching { ApkParser.parseArchive(context, e.file()) }.getOrNull()
                            ?.let { ApkLibrary.setManifest(context, e.key, it) }
                    }
                }
                // 补完重新合一次：这一次清单里才有真实事实可显示
                libraryState = ApkLibrary.list(context)
                active = active?.withDownloadedFacts(libraryState)
            }
        }
        libraryState = ApkLibrary.list(context)
        active = active?.withDownloadedFacts(libraryState)
    }

    /**
     * 打开用户 / 组织页：先拿 profile（决定是 user 还是 org），再按类型取仓库列表。
     *
     * 页面本身压进 [pages] 栈 —— 从这里点进仓库详情时**不再把这一页关掉**，
     * 返回才回得来（这正是 BUG-08）。
     */
    fun openPerson(login: String) {
        openPage(Page.Person(login))
        // 已经开着这一页就别从头再拉一遍 —— **除非上次拉失败了**。
        // 「重试」按的就是这里，静默 return 会让那个按钮彻底没用：点一下什么也不发生。
        if (personProfile[login] != null && personProfile[login] !is Async.Err) return
        personProfile[login] = Async.Loading
        personRepos[login] = Async.Loading
        scope.launch {
            when (val u = repo.person(login)) {
                is Async.Ok -> {
                    personProfile[login] = u
                    personRepos[login] = repo.personRepos(login, u.value.type == "Organization")
                }
                is Async.Err -> {
                    personProfile[login] = u
                    personRepos[login] = Async.Err(u.message)
                }
                Async.Loading -> Unit
            }
        }
    }

    /** 由用户点击触发安装包检查 */
    fun checkInstallable(target: Repo) {
        val cur = installable[target.id]
        if (cur != null && cur != InstallCheck.Failed) return
        installable[target.id] = InstallCheck.Checking
        scope.launch {
            installable[target.id] =
                repo.hasInstallable(target.id)?.let { if (it) InstallCheck.Yes else InstallCheck.No }
                    ?: InstallCheck.Failed
        }
    }

    /** 串行执行，并发请求会触发 GitHub 限流 */
    fun checkAllInstallable(targets: List<Repo>) {
        val list = targets.take(10)
        scope.launch {
            for (item in list) {
                if (installable[item.id] == null || installable[item.id] == InstallCheck.Failed) {
                    installable[item.id] = InstallCheck.Checking
                    installable[item.id] =
                        repo.hasInstallable(item.id)?.let { if (it) InstallCheck.Yes else InstallCheck.No }
                            ?: InstallCheck.Failed
                }
            }
        }
    }

    /**
     * 按包名反查候选仓库。
     *
     * 只给候选、不自动绑定：未经比对的绑定会直接产出「可升级」结论。
     */
    fun lookupRepo(app: ScannedApp) {
        if (lookupPkg == app.packageName) return
        lookupPkg = app.packageName
        lookupCandidates = emptyList()
        lookupFailed = false
        scope.launch {
            when (val r = repo.findRepoCandidates(app.packageName, context)) {
                is Async.Ok -> {
                    lookupCandidates = r.value
                    if (r.value.isEmpty()) {
                        toast(
                            context.getString(R.string.toast_lookup_none, app.label),
                            context.getString(R.string.toast_lookup_none_note, app.packageName),
                        )
                    }
                }
                is Async.Err -> {
                    lookupFailed = true
                    toast(context.getString(R.string.toast_lookup_failed, app.label), r.message)
                }
                Async.Loading -> Unit
            }
        }
    }

    /** 用户选定候选后才写入绑定 */
    fun confirmLink(app: ScannedApp, candidate: Repo) {
        LinkEngine.bind(context, app.packageName, candidate.id)
        lookupPkg = null
        lookupCandidates = emptyList()
        scope.launch {
            // search 结果只有元信息、没有 release，按 id 补拉一次完整详情
            val resolved = when (val d = repo.detail(candidate.id)) {
                is Async.Ok -> d.value
                is Async.Err -> {
                    toast(context.getString(R.string.toast_bind_detail_failed), d.message)
                    candidate
                }
                Async.Loading -> candidate
            }
            linkBindings[app.packageName] = resolved
            scan?.let { LinkEngine.applyBindings(it, linkBindings) }
            val fresh = FitEngine.withDeviceState(resolved)
            linkBindings[app.packageName] = fresh
            open(fresh)
            toast(
                context.getString(R.string.toast_bound, app.label),
                context.getString(R.string.toast_bound_note, "${resolved.owner}/${resolved.name}"),
            )
        }
    }

    /**
     * 解除「已装应用 → 仓库」的绑定。
     *
     * 顺序不能反：先让 LinkEngine 把落盘表和内存表都去掉，再重算本机档案 ——
     * applyBindings 读的是 LinkEngine 自己的 bindingsStore。
     *
     * [quiet] 给「换仓库」用：那条链路紧接着就要弹反查结果，再补一句 toast 是噪音。
     */
    fun unbind(app: ScannedApp, quiet: Boolean = false) {
        // 内置绑定不可拆。必须在**这里**就返回，不能只靠 LinkEngine 拒写 ——
        // 那样的话下面这些照样执行：内存绑定被摘掉、界面上弹出「已解除」，
        // 而落盘表里那条还在。用户看到的是一个并没有真的解除的状态。
        if (LinkEngine.isBuiltIn(app.packageName)) return
        LinkEngine.unbind(context, app.packageName)
        linkBindings.remove(app.packageName)
        // 正在反查的同一个包要收尾，否则解除后还挂着上一轮的候选
        if (lookupPkg == app.packageName) {
            lookupPkg = null
            lookupCandidates = emptyList()
        }
        scan?.let { LinkEngine.applyBindings(it, linkBindings) }
        if (!quiet) {
            toast(
                context.getString(R.string.toast_unbound, app.label),
                context.getString(R.string.toast_unbound_note),
            )
        }
    }

    /**
     * 换绑到另一个仓库。
     *
     * 不新增一套流程：解除后这个包就回到「未关联」分组，接着走原有的按包名反查，
     * 用户确认候选后覆盖绑定。用户不需要先解绑、再回列表点一次「查仓库」。
     */
    fun relink(app: ScannedApp) {
        unbind(app, quiet = true)
        lookupRepo(app)
    }

    fun close() {
        if (!mounted) return
        scope.launch {
            progress.animateTo(0f, animationSpec = FitMotion.PanelOut)
            mounted = false
            active = null
            readme = null
            detailState = null
            refreshingDetail = false
            source = null
        }
    }

    /**
     * 全局返回栈。
     *
     * 这个 App 是单 Activity + 一叠 overlay，没有 NavHost，返回键只能自己按
     * 「谁在最上面」逐层退。
     *
     * 顺序 = 视觉叠放顺序的逆序：
     *
     * 1. **仓库详情**（它渲染在所有页面之上，不在 [pages] 栈里）
     * 2. **页面栈** [pages] 的栈顶
     * 3. 主 tab 回发现页
     * 4. 退出确认
     *
     * 第 1 条必须在第 2 条之前 —— 从搜索结果点进仓库详情时，搜索页**没有被关掉**，
     * 它就压在栈里等着。于是「返回」先关详情、再露回搜索页，而不是一步跨到首页。
     *
     * DetailPanel 自己也有 BackHandler（组合更晚、会先命中），这里的 `mounted -> close()`
     * 是兜底：面板淡出到 alpha≈0 时它会提前 return，那时就没注册了，返回键会漏下来。
     */
    var lastBackAt by remember { mutableStateOf(0L) }
    val backActivity = LocalContext.current as? Activity
    BackHandler {
        when {
            mounted -> close()
            pages.isNotEmpty() -> closePage()
            tab != AppTab.Discover -> tab = AppTab.Discover
            else -> {
                // 已经在首页。直接退出会「啪」一下回桌面，很像闪退，
                // 所以第一次按只提示，短时间内再按才真退。
                val now = System.currentTimeMillis()
                if (now - lastBackAt < EXIT_CONFIRM_MS) {
                    backActivity?.finish()
                } else {
                    lastBackAt = now
                    toast(context.getString(R.string.toast_exit_confirm))
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(p.canvas),
    ) {
        /* ① 内容层：详情打开时整体缩小后退。
         *
         * 仅在详情打开时挂载，避免平时多一层 RenderNode。
         */
        val contentLayer = if (mounted) {
            Modifier.graphicsLayer {
                val v = progress.value
                val shrink = 1f - 0.09f * v
                scaleX = shrink
                scaleY = shrink
                translationX = -8.dp.toPx() * v
                shape = RoundedCornerShape(28.dp.toPx() * v)
                clip = true
            }
        } else {
            Modifier
        }
        Box(
            contentLayer
                .fillMaxSize(),
        ) {
            when (tab) {
                AppTab.Discover -> HomeScreen(
                    langFilter = langFilter,
                    onLangFilter = { langFilter = it },
                    stars = stars,
                    updated = updated,
                    onRepoTap = { open(it.id, placeholder = it) },
                    onScanTap = {
                        // 手动扫描 = 用户明确说了「现在扫」，给旧的等于没按
                        refreshScan(force = true)
                        toast(context.getString(R.string.toast_rescan_done))
                    },
                    onRateLimitTap = {
                        toast(
                            context.getString(
                                R.string.toast_rate_limit,
                                rate.first.toString(),
                                rate.second.toString(),
                            ),
                        )
                    },
                    rateRemaining = rate.first,
                    rateLimit = rate.second,
                    onSearchTap = { openPage(Page.Search) },
                    onTileBounds = { id, rect -> bounds.record(id, rect) },
                    onRetry = { loadDiscover() },
                    upgradable = upgradableCount,
                    featured = featured,
                    onRefresh = { refreshDiscover() },
                    onSectionTap = { openPage(Page.SectionManager) },
                    sections = homeSections,
                    topicStates = topicStates,
                    onSectionLoad = { loadTopicSection(it) },
                    refreshing = refreshingDiscover,
                    listState = discoverState,
                )

                AppTab.Device -> DeviceScreen(
                    scan = scan,
                    bindings = linkBindings,
                    lookupPkg = lookupPkg,
                    candidates = lookupCandidates,
                    onLookup = { lookupRepo(it) },
                    onBind = { app, candidate -> confirmLink(app, candidate) },
                    onRepoTap = { open(it) },
                    onUnbind = { unbind(it) },
                    onRelink = { relink(it) },
                    onRescan = {
                        // 同上：这是「重新扫描」，必须真扫。绑定关系由 refreshScan 里的
                        // resolveLinks 统一处理，不再在这里重复调一遍
                        refreshScan(force = true)
                    },
                    onOpenSettings = { tab = AppTab.Profile },
                    listState = deviceState,
                )

                AppTab.Subscribe -> SubscribeScreen(
                    subs = subscriptions,
                    ranks = rankStates,
                    onRankLoad = { loadRank(it) },
                    onRankRetry = { retryRank(it) },
                    onRankRefresh = { refreshRank(it) },
                    refreshingRanks = refreshingRanks,
                    onRepoTap = { open(it) },
                    onBrowse = { tab = AppTab.Discover },
                    onRefreshOne = { refreshSubscription(it) },
                    onRefreshAll = { refreshAllSubscriptions() },
                    onToggleFollow = { toggleFollow(it) },
                    isFollowing = { isFollowing(it) },
                    refreshing = refreshingSubs,
                    listState = subscribeState,
                )

                AppTab.Profile -> ProfileScreen(
                    quotaRemaining = rate.first,
                    quotaTotal = rate.second,
                    quotaLoggedIn = 5000,
                    downloadCount = downloadCount,
                    installedViaUs = installedViaUsCount,
                    subscriptionCount = subscriptions.size,
                    historyCount = history.size,
                    syncLabel = when {
                        !webDavConfig.enabled -> stringResource(R.string.sync_state_off)
                        webDavConfig.isBlank -> stringResource(R.string.sync_state_incomplete)
                        else -> WebDavPreset.byId(webDavConfig.presetId)
                            ?.let { stringResource(it.nameRes) }
                            ?: stringResource(R.string.sync_state_custom)
                    },
                    versionName = BuildConfig.VERSION_NAME,
                    onLogin = { openPage(Page.Login) },
                    loggedIn = TokenStore.isLoggedIn(context),
                    loginName = me?.name?.takeIf { it.isNotBlank() } ?: me?.login,
                    me = me,
                    myRepos = myRepos,
                    avatarLoader = { url -> api.avatar(url) },
                    onOpenMyProjects = {
                        // 进页面前先确保有数据：直接开的话会先看到骨架屏，
                        // 而多数账号是 1 次请求就能拿到，没必要让用户多等一轮。
                        loadMyRepos()
                        // 必须开这一页。只 loadMyRepos() 的话整个回调只刷新了数据、
                        // 没有任何一层被打开，「我的项目」那 265 行就是不可达代码 ——
                        // 表现正是「点了没反应」。
                        openPage(Page.MyProjects)
                    },
                    onLogout = {
                        TokenStore.logoutOnly(context)
                        me = null
                        // 汇总数字必须一起清：留着上一个账号的 star 数，
                        // 会在「未登录」的状态下显示别人的数据
                        myRepos = null
                        pages.removeAll { it == Page.MyProjects }
                        // 配额立刻重读：请求头已经不带 token 了，读回来就是 60/h 的真实值
                        scope.launch { rate = repo.rate() }
                        toast(
                            context.getString(R.string.toast_logged_out),
                            context.getString(R.string.toast_logged_out_note),
                        )
                    },
                    onAction = { label ->
                        when (label) {
                            "我的订阅" -> tab = AppTab.Subscribe
                            "导出订阅" -> openExport.launch(SubscriptionTransfer.suggestedFileName())
                            "导入订阅" -> importFromClipboardOrPicker()
                            "历史足迹" -> openPage(Page.History)
                            "订阅同步" -> openPage(Page.WebDav)
                            "下载与安装记录" -> openPage(Page.Records(HistoryEntry.Kind.Downloaded))
                            "从 FitHub 安装的应用" -> openPage(Page.Records(HistoryEntry.Kind.InstalledViaUs))
                            "外观" -> openPage(Page.Theme)
                            "语言" -> openPage(Page.Language)
                            "下载源" -> openPage(Page.Mirror)
                            "隐私" -> openPage(Page.Privacy)
                            "开源协议" -> openPage(Page.License)
                            "检查更新" -> openPage(Page.Update)

                            "预发布" -> {
                                val next = !Prefs.state.value.includePrerelease
                                Prefs.setIncludePrerelease(context, next)
                                // 预发布会改变「哪些 release 参与适配判定」，所以发现页
                                // 和详情的缓存都得作废，否则用户切了开关看到的还是旧结论。
                                repo.invalidateDiscover()
                                loadDiscover()
                                toast(
                                    context.getString(
                                        if (next) R.string.toast_prerelease_on else R.string.toast_prerelease_off,
                                    ),
                                    if (next) context.getString(R.string.toast_prerelease_on_note) else null,
                                )
                            }

                            "校验" -> {
                                val next = !Prefs.state.value.requireSha
                                Prefs.setRequireSha(context, next)
                                toast(
                                    context.getString(
                                        if (next) R.string.toast_sha_on else R.string.toast_sha_off,
                                    ),
                                    if (next) context.getString(R.string.toast_sha_on_note) else null,
                                )
                            }

                            else -> toast(context.getString(R.string.toast_not_open_yet))
                        }
                    },
                    listState = profileState,
                )
            }
        }

        /* ⑤ 悬浮 tab bar：详情打开时整条淡出 */
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset + 16.dp)
                .padding(horizontal = 16.dp),
        ) {
            FloatingTabBar(
                selected = tab,
                onSelect = { tab = it },
                hidden = mounted,
            )
        }

        /* ⑥ 全屏搜索 */
        AnimatedVisibility(
            visible = isTop(Page.Search),
            enter = slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize(),
        ) {
            SearchScreen(
                onClose = { closePage() },
                // 进详情时**不关搜索页**：它留在栈里，返回时才回得来，
                // 而且搜索结果和滚动位置都原样保留（页面没被销毁重建）。
                onRepoTap = { open(it.id, placeholder = it) },
                onPersonTap = { handle ->
                    closePage()
                    if (handle.startsWith("agent:")) {
                        toast(context.getString(R.string.toast_not_github_account))
                    } else {
                        openPerson(handle)
                    }
                },
                onSearch = { q -> repo.search(q) },
                onSearchPeople = { q, wantOrg -> repo.searchPeople(q, wantOrg) },
            )
        }

        /* ⑦ 用户 / 组织页 */
        val person = (pages.lastOrNull() as? Page.Person)?.login
        if (person != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(p.surface),
            ) {
                PersonScreen(
                    login = person,
                    profile = personProfile[person] ?: Async.Loading,
                    repos = personRepos[person] ?: Async.Loading,
                    installable = installable,
                    onCheck = { checkInstallable(it) },
                    onCheckAll = { checkAllInstallable(it) },
                    // 同上：不关这一页，返回才回得来
                    onRepoTap = { open(it.id, placeholder = it) },
                    onBack = { closePage() },
                    onRetry = { openPerson(person) },
                )
            }
        }

        /* ⑦a 我的项目页 */
        if (isTop(Page.MyProjects)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(p.surface),
            ) {
                MyProjectsScreen(
                    me = me,
                    data = myRepos ?: Async.Loading,
                    installable = installable,
                    grantedScopes = api.grantedScopes,
                    avatarLoader = { url -> api.avatar(url) },
                    onCheck = { checkInstallable(it) },
                    onCheckAll = { checkAllInstallable(it) },
                    // 同上：不关这一页，「我的 → 我的项目 → 仓库 → 返回」才回得来
                    onRepoTap = { open(it.id, placeholder = it) },
                    onRefresh = { loadMyRepos(force = true) },
                    onBack = { closePage() },
                )
            }
        }

        /* ⑧ 历史足迹 */
        if (isTop(Page.History)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(p.surface),
            ) {
                HistoryScreen(
                    entries = history,
                    onBack = { closePage() },
                    onClearOne = { e ->
                        scope.launch {
                            withContext(Dispatchers.IO) { HistoryStore.clear(context, e.kind, e.ref) }
                            history = HistoryStore.list(context)
                        }
                    },
                    onClearAll = {
                        scope.launch {
                            withContext(Dispatchers.IO) { HistoryStore.clearAll(context) }
                            history = emptyList()
                            toast(context.getString(R.string.toast_history_cleared))
                        }
                    },
                    listState = historyState,
                )
            }
        }

        /* ⑧a 下载与安装记录。焦点跟着这一页走：两个入口指向同一份数据但起点不同 */
        val recordsPage = pages.lastOrNull() as? Page.Records
        if (recordsPage != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(p.surface),
            ) {
                RecordsScreen(
                    entries = history,
                    focus = recordsPage.focus,
                    /**
                     * 已下载清单在这一页不只是给人看的，还要**能用**。
                     *
                     * 之前「下载与安装记录」里的每一行都只是文字：下好的包躺在
                     * 下载目录里，用户要么自己切出去找文件，要么回详情页重新点一遍。
                     */
                    library = libraryState,
                    onBack = { closePage() },
                    onClearOne = { e ->
                        scope.launch {
                            withContext(Dispatchers.IO) { HistoryStore.clear(context, e.kind, e.ref) }
                            history = HistoryStore.list(context)
                        }
                    },
                    onClearAll = {
                        scope.launch {
                            withContext(Dispatchers.IO) { HistoryStore.clearAll(context) }
                            history = emptyList()
                            toast(context.getString(R.string.toast_records_cleared))
                        }
                    },
                    onNotifyChanged = { libraryState = ApkLibrary.list(context) },
                )
            }
        }

        /* ⑧b 外观 */
        if (isTop(Page.Theme)) {            Box(
                Modifier
                    .fillMaxSize()
                    .background(p.surface),
            ) {
                ThemeScreen(
                    onBack = { closePage() },
                    onPick = {
                        Prefs.setTheme(context, it)
                        closePage()
                    },
                )
            }
        }

        /* ⑧b1b 语言 */
        if (isTop(Page.Language)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(p.surface),
            ) {
                LanguageScreen(
                    onBack = { closePage() },
                    onPick = { pref ->
                        // 写完偏好必须重建：资源由 attachBaseContext 包装的 context 提供，
                        // 不 recreate() 的话这一层还拿着旧 locale。
                        AppLocale.set(context, pref)
                        backActivity?.recreate()
                    },
                )
            }
        }

        /* ⑧b1c 下载源 */
        if (isTop(Page.Mirror)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(p.surface),
            ) {
                MirrorScreen(onBack = { closePage() })
            }
        }

        /* ⑧b2 GitHub 登录 */
        if (isTop(Page.Login)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(p.surface),
            ) {
                LoginScreen(
                    onBack = { closePage() },
                    client = remember { DeviceFlowClient(BuildConfig.GITHUB_CLIENT_ID) },
                    onToken = { token ->
                        TokenStore.save(context, token)
                        // 登录改变了配额和可见数据，作废发现页缓存重拉
                        repo.invalidateDiscover()
                        scope.launch {
                            rate = repo.rate()
                            loadDiscover()
                        }
                    },
                    onResolveLogin = {
                        // 查用户名只为显示。查不到就返回 null，**不要**因此报「登录失败」——
                        // 令牌已经换到手了，那就是登录成功。
                        when (val r = api.me()) {
                            is ApiResult.Ok -> {
                                me = r.value
                                // 登录那一刻就把 star/fork/watch 拉上：
                                // 登录完回到「我的」页就要看到数字，
                                // 而不是先看到三个破折号再等它自己冒出来。
                                loadMyRepos()
                                r.value.name?.takeIf { it.isNotBlank() } ?: r.value.login
                            }
                            else -> null
                        }
                    },
                    onOpenUrl = { url -> openExternal(context, url) },
                )
            }
        }

        /* ⑧c 隐私 / 开源协议 / 检查更新 —— 三个都是平级页面，栈顶是谁就渲染谁 */
        when (pages.lastOrNull()) {
            Page.Privacy -> Box(Modifier.fillMaxSize().background(p.surface)) {
                PrivacyScreen(onBack = { closePage() })
            }

            Page.License -> Box(Modifier.fillMaxSize().background(p.surface)) {
                LicenseScreen(onBack = { closePage() })
            }

            Page.Update -> Box(Modifier.fillMaxSize().background(p.surface)) {
                UpdateScreen(
                    onBack = { closePage() },
                    repo = repo,
                    onToast = { msg, note -> toast(msg, note) },
                )
            }

            else -> Unit
        }

        /* ⑨ WebDAV 配置 */
        if (isTop(Page.WebDav)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(p.surface),
            ) {
                WebDavScreen(
                    saved = webDavConfig,
                    subscriptionCount = subscriptions.size,
                    lastOutcome = lastSyncOutcome,
                    syncing = webDavSyncing,
                    onBack = { closePage() },
                    onSave = {
                        webDavConfig = it
                        WebDavConfig.save(context, it)
                        toast(
                            context.getString(R.string.toast_webdav_saved),
                            context.getString(R.string.toast_webdav_saved_note, it.baseUrl),
                        )
                    },
                    onSync = { runSync(false) },
                    onForcePush = { runSync(true) },
                    onTest = {
                        scope.launch {
                            val r = WebDavClient(webDavConfig).probe()
                            toast(
                                when (r) {
                                    is com.heiyehk.fithub.data.remote.WebDavResult.Ok ->
                                        context.getString(R.string.webdav_probe_ok)
                                    is com.heiyehk.fithub.data.remote.WebDavResult.Failed ->
                                        context.getString(R.string.webdav_probe_failed)
                                    is com.heiyehk.fithub.data.remote.WebDavResult.NotConfigured ->
                                        context.getString(R.string.webdav_probe_not_configured)
                                    com.heiyehk.fithub.data.remote.WebDavResult.NotFound ->
                                        context.getString(R.string.webdav_probe_not_found)
                                },
                                when (r) {
                                    is com.heiyehk.fithub.data.remote.WebDavResult.Failed ->
                                        explainText(context, r.reason)
                                    is com.heiyehk.fithub.data.remote.WebDavResult.NotConfigured ->
                                        explainText(context, r.reason)
                                    else -> null
                                },
                            )
                        }
                    },
                    listState = webDavState,
                )
            }
        }

        /* ⑩ 首页板块管理 */
        if (isTop(Page.SectionManager)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(p.surface),
            ) {
                HomeSectionManager(
                    sections = homeSections,
                    onBack = { closePage() },
                    onChange = {
                        homeSections = it
                        sectionStore.save(it)
                        // 新加的板块立刻用已有缓存填上：用户加完板块回首页，
                        // 看到的是内容而不是一个还得再点一次的按钮（不花配额）
                        hydrateTopicSectionsFromCache(it)
                        toast(
                            context.getString(R.string.toast_saved),
                            context.getString(R.string.toast_saved_note),
                        )
                    },
                    listState = sectionManagerState,
                )
            }
        }

        /*
         * ↓↓↓ 详情遮罩 / 详情面板 / 共享图标 ↓↓↓
         *
         * 这三块刻意排在**所有页面之下**（也就是视觉上的最上层）。
         *
         * 从搜索结果或主体页进仓库详情时，那些页面现在**不会被关掉**（见 [Page]），
         * 它们还留在栈里等着被返回。而这个 Box 里后面的兄弟节点总是画在更上面 ——
         * 详情要是还留在原来那个位置，就会被搜索页整块盖住，用户点了仓库之后
         * 看到的还是上一页。
         */

        /* 详情遮罩 */
        if (mounted) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.32f * progress.value }
                    .background(p.scrim)
                    .tap { close() },
            )
        }

        /* 详情面板：从右侧滑入 */
        if (mounted) {
            active?.let { repo ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = (1f - progress.value) * size.width
                        },
                ) {
                    DetailPanel(
                        repo = repo,
                        codeRepo = gh,
                        contentAlpha = ((progress.value - 0.35f) / 0.5f).coerceIn(0f, 1f),
                        topInset = topInset,
                        onClose = { close() },
                        onShare = { shareRepo(context, repo) { msg, note -> toast(msg, note) } },
                        onOpenRelease = {
                            openReleasePage(context, repo) { url ->
                                toast(context.getString(R.string.toast_no_browser), url)
                            }
                        },
                        // 文件真的下到手才记，失败和中途退出的不会混进「下载记录」
                        onDownloaded = { repoId, assetName, sha ->
                            recordHistory(
                                HistoryEntry(
                                    kind = HistoryEntry.Kind.Downloaded,
                                    ref = "$repoId/$assetName",
                                    title = assetName,
                                    detail = listOfNotNull(
                                        repoId.substringAfterLast('/').takeIf { it.isNotBlank() },
                                        sha.take(8).takeIf { it.isNotBlank() }?.let { "sha $it" },
                                    ).joinToString(" · "),
                                    at = System.currentTimeMillis(),
                                )
                            )
                        },
                        readme = readme,
                        library = libraryState,
                        onLibraryChanged = { refreshLibrary() },
                        onReadmeLoad = { loadReadme(repo.id) },
                        following = isFollowing(repo.id),
                        onToggleFollow = { toggleFollow(repo) },
                        onRefresh = { refreshDetail() },
                        refreshing = refreshingDetail,
                        detailState = detailState,
                        onInstallDone = {
                            // 计数从 history 派生，这里只需写记录，不用再动内存里的计数器
                            recordHistory(
                                HistoryEntry(
                                    kind = HistoryEntry.Kind.InstalledViaUs,
                                    ref = repo.id,
                                    title = repo.name,
                                    detail = listOfNotNull(
                                        repo.owner.takeIf { it.isNotBlank() },
                                        repo.version.takeIf { it.isNotBlank() && it != "—" },
                                    ).joinToString(" · "),
                                    at = System.currentTimeMillis(),
                                    desc = repo.desc.trim(),
                                    lastPush = repo.date,
                                    release = repo.version,
                                )
                            )
                            toast(context.getString(R.string.toast_install_done))
                        },
                        // 「从已下载清单直接装」和「打开已装应用」这两条路都不经过
                        // startInstall 的状态机，它们失败了必须自己出声 ——
                        // 漏掉的话就是按钮按下去一点反应都没有
                        onInstallFailed = { reason -> toast(reason) },
                        onBusyDownload = { reason -> toast(reason) },
                    )
                }
            }
        }

        /* 共享图标：从列表卡片飞进详情页的那一下 */
        val from = source
        if (mounted && from != null && from.width > 0f && active != null) {
            val repo = active!!
            val baseSize = with(density) { from.width.toDp() }
            Box(
                Modifier
                    .size(baseSize)
                    .graphicsLayer {
                        val v = progress.value
                        alpha = if (v <= 0.002f || v >= 0.998f) 0f else 1f
                        translationX = from.left + (targetLeft - from.left) * v
                        translationY = from.top + (targetTop - from.top) * v
                        val size = from.width + (targetSize - from.width) * v
                        scaleX = size / from.width
                        scaleY = size / from.width
                    },
            ) {
                AppTile(
                    monogram = repo.monogram,
                    background = repo.tileBg,
                    foreground = repo.tileFg,
                    size = baseSize,
                    corner = with(density) { (from.width * 0.3f).toDp() },
                )
            }
        }

        /* ⑪ 轻提示 */
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset + 16.dp + 56.dp + 14.dp)
                .padding(horizontal = 24.dp),
        ) {
            message?.let { ToastBar(it) }
        }
    }
}

@Composable
private fun ToastBar(text: String) {
    val p = FitTheme.palette
    Row(
        Modifier
            .background(p.ink, CircleShape)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(FiCheck, contentDescription = null, tint = p.accent, modifier = Modifier.size(15.dp))
        Text(
            text = text,
            style = FitTypography.bodyMedium,
            color = p.surface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 语言选择。
 *
 * 三选一而不是开关，理由和外观一样：「跟随系统」是一种独立状态，硬塞进开关会出现
 * 「开关关着但界面确实是系统语言」这种自相矛盾。选项名用 [AppLocale.choiceLabel]，
 * 它给的是各语言通用的自称（简体中文 / English）。
 *
 * 选完由调用方 `AppLocale.set` + `recreate()`：资源挂在 `attachBaseContext` 包装出来的
 * context 上，进程不重建就没有新 locale。
 */
@Composable
private fun LanguageScreen(onBack: () -> Unit, onPick: (String) -> Unit) {
    val p = FitTheme.palette
    val current = AppLocale.current(LocalContext.current)
    InfoScreen(stringResource(R.string.language_title), onBack) {
        InfoHeading(stringResource(R.string.language_heading))
        for (value in AppLocale.CHOICES) {
            val selected = value == current
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(value) }
                        .padding(vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        AppLocale.choiceLabel(value),
                        style = FitTypography.titleSmall,
                        color = p.ink,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(FiCheck, null, tint = p.accent, modifier = Modifier.size(18.dp))
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(p.hairline),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        InfoNote(stringResource(R.string.language_note))
    }
}

/**
 * Release 页地址：有 tag 就精确到那一条，没有就退回仓库的 releases 列表页。
 *
 * 退回而不是编一个产物列表 —— 本 App 的立场就是「直取官方 Release」，
 * 拿不到精确地址时把 GitHub 上真实存在的东西指给用户，比在 App 里摆一份
 * 可能已经过期的快照诚实。
 */
private fun releaseUrl(repo: Repo): String {
    val base = repo.htmlUrl.ifBlank { "https://github.com/${repo.owner}/${repo.name}" }
    val tag = repo.history.firstOrNull()?.tag?.takeIf { it.isNotBlank() }
    return if (tag != null) "$base/releases/tag/$tag" else "$base/releases"
}

/** 交给系统浏览器打开。设备上一个浏览器都没有时必须明确说，不能静默失败 */
private fun openExternal(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/** 详情页右上角的 FiExternal 与底部黑色主 CTA 共用。 */
private fun openReleasePage(context: Context, repo: Repo, onFail: (String) -> Unit) {
    val url = releaseUrl(repo)
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { onFail(url) }
}

/**
 * 详情页「分享卡片」。
 *
 * 这一版发的是文字，不是图片卡片。带图要 FileProvider + 把 Compose 渲成 Bitmap
 * 再授 URI 权限，代价和收益不成比例，绝大多数接收方（聊天工具、邮件）本来就能
 * 正确呈现纯链接。
 *
 * 文字里刻意带上「本机 + 适配结论」—— 一个不带自己结论的 GitHub 链接，
 * 任何浏览器复制都能给，这里值得存在的理由是那条结论。
 */
private fun shareRepo(context: Context, repo: Repo, onFail: (String, String?) -> Unit) {
    val full = "${repo.owner}/${repo.name}"
    // 结论是领域层的 Explain（富文本），分享出去要剥掉 ** 强调标记，并只取第一句。
    val verdict = shareSummary(context, FitEngine.verdictExplain(repo))
    val text = buildString {
        append(full)
        if (repo.version.isNotBlank() && repo.version != "—") append("  ${repo.version}")
        append("  ★${Env.formatStars(repo.stars)}\n")
        if (repo.desc.isNotBlank()) {
            append(repo.desc.trim()); append('\n')
        }
        append("\n")
        append(context.getString(R.string.share_device_prefix, deviceName(context), Env.device.sdkLabel))
        append(verdict); append('\n')
        append('\n')
        append(releaseUrl(repo))
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, full)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    // 设备上可能一个能收 ACTION_SEND 的应用都没有（刚开机的模拟器就是这样），
    // 那就明确告诉用户，不要静默失败。
    runCatching {
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.share_chooser_title, full)),
        )
    }.onFailure { onFail(context.getString(R.string.share_no_app), it.message) }
}
