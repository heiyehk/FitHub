package com.heiyehk.fithub.ui.detail

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.heiyehk.fithub.data.Asset
import com.heiyehk.fithub.data.Async
import com.heiyehk.fithub.data.DeviceState
import com.heiyehk.fithub.data.FitEngine
import com.heiyehk.fithub.data.FitState
import com.heiyehk.fithub.data.Readme
import com.heiyehk.fithub.data.Prefs
import com.heiyehk.fithub.data.Repo
import com.heiyehk.fithub.data.Env
import com.heiyehk.fithub.data.Explain
import com.heiyehk.fithub.data.Verdict
import com.heiyehk.fithub.data.install.ApkAction
import com.heiyehk.fithub.data.install.ApkInstaller
import com.heiyehk.fithub.data.install.ApkLibrary
import com.heiyehk.fithub.data.install.DownloadCenter
import com.heiyehk.fithub.data.install.DownloadedApk
import com.heiyehk.fithub.data.install.actionFor
import com.heiyehk.fithub.data.install.assetNameOrNull
import com.heiyehk.fithub.data.install.DownloadService
import com.heiyehk.fithub.R
import com.heiyehk.fithub.ui.components.AppTile
import com.heiyehk.fithub.ui.components.CircularProgress
import com.heiyehk.fithub.ui.components.FitBadge
import com.heiyehk.fithub.ui.components.HairLine
import com.heiyehk.fithub.ui.components.IconCircleButton
import com.heiyehk.fithub.ui.components.LangDot
import com.heiyehk.fithub.ui.components.MetaRow
import com.heiyehk.fithub.ui.components.MetaText
import com.heiyehk.fithub.ui.components.MonoText
import com.heiyehk.fithub.ui.components.OutlineBadge
import com.heiyehk.fithub.ui.components.PrimaryButton
import com.heiyehk.fithub.ui.components.StaleNotice
import com.heiyehk.fithub.ui.components.boldMarkup
import com.heiyehk.fithub.ui.components.stalestOf
import com.heiyehk.fithub.ui.components.tap
import com.heiyehk.fithub.ui.ageText
import com.heiyehk.fithub.ui.explainText
import com.heiyehk.fithub.ui.icons.FiAlert
import com.heiyehk.fithub.ui.icons.FiArrowLeft
import com.heiyehk.fithub.ui.icons.FiBookmark
import com.heiyehk.fithub.ui.icons.FiCheck
import com.heiyehk.fithub.ui.icons.FiChevron
import com.heiyehk.fithub.ui.icons.FiDownload
import com.heiyehk.fithub.ui.icons.FiPackage
import com.heiyehk.fithub.ui.icons.FiRefresh
import com.heiyehk.fithub.ui.icons.FiExternal
import com.heiyehk.fithub.ui.icons.FiShare
import com.heiyehk.fithub.ui.icons.FiShield
import com.heiyehk.fithub.ui.theme.Eyebrow
import com.heiyehk.fithub.ui.theme.FitMotion
import com.heiyehk.fithub.ui.theme.FitTheme
import com.heiyehk.fithub.ui.theme.FitTone
import com.heiyehk.fithub.ui.theme.FitTypography
import com.heiyehk.fithub.ui.theme.Fonts
import com.heiyehk.fithub.ui.theme.MonoMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 面板几何常量。抽屉图标飞行动画的目标位置也用它算，一处定义 */
object DetailMetrics {
    val sidePad: Dp = 20.dp
    val iconSize: Dp = 56.dp
    val barHeight: Dp = 56.dp
    val heroTop: Dp = 14.dp

    /** 面板头部图标的左上角（不含状态栏 inset） */
    fun iconTop(inset: Dp): Dp = inset + barHeight + heroTop
}

/**
 * 详情页的三个 tab。
 *
 * 第三个用 README 而不是「说明」：GitHub 上那个文件就叫 README，页面上又已经有
 * 适配产物和更新日志两个中文 tab，叫「说明」会让人以为这是 FitHub 自己写的介绍。
 */
enum class DetailTab(@StringRes val labelRes: Int) {
    Assets(R.string.detail_tab_assets),
    Changelog(R.string.detail_tab_changelog),
    Readme(R.string.detail_tab_readme),
}

/**
 * 安装闭环状态机。
 *
 * 下载 → SHA-256 校验 → 签名预检 →（冲突则拦截）→ 安装 → 回写本机；
 * 冲突时拦截并改写 CTA 文案。
 */
sealed interface InstallStep {
    data object Idle : InstallStep
    data class Downloading(val progress: Float, val speed: String) : InstallStep

    /**
     * 用户按了暂停。
     *
     * 刻意留着 [saved] / [total]：只写「已暂停」的话，用户既不知道停在百分之几，
     * 也不知道继续完还剩多少 —— 半路被叫停的人最关心的就是这两个数。
     */
    data class Paused(val progress: Float, val saved: Long, val total: Long) : InstallStep
    data object Verifying : InstallStep
    data object CheckingSignature : InstallStep
    data class Blocked(val reason: String) : InstallStep
    /**
     * 文件已就绪，等用户点确认。
     *
     * [verified] = 摘要和发布方给的校验和比对过。false 时 UI 只能说「已计算」，
     * 不能说「校验通过」—— GitHub 的 Release 并不给每个产物提供 SHA-256，
     * 拿不到权威值时谎报校验成功是最糟的一种错。
     */
    data class ReadyToInstall(val sha: String = "", val verified: Boolean = false) : InstallStep
    data object Installing : InstallStep
    data class Done(val label: String) : InstallStep
    data class Failed(val reason: String) : InstallStep
}

val InstallStep.progressFraction: Float
    get() = when (this) {
        is InstallStep.Downloading -> progress
        is InstallStep.Paused -> progress
        is InstallStep.Blocked -> 1f
        is InstallStep.Failed -> 0f
        InstallStep.Idle -> 0f
        else -> 1f
    }

fun InstallStep.stageLabel(context: Context): String =
    when (this) {
        InstallStep.Idle -> ""
        is InstallStep.Downloading ->
            context.getString(R.string.install_stage_downloading, (progress * 100).toInt(), speed)
        is InstallStep.Paused ->
            context.getString(
                R.string.download_paused,
                formatBytes(saved),
                formatBytes(total),
            )
        InstallStep.Verifying -> context.getString(R.string.install_stage_verifying)
        InstallStep.CheckingSignature -> context.getString(R.string.install_stage_check_signature)
        is InstallStep.Blocked -> context.getString(R.string.install_stage_blocked, reason)
        is InstallStep.ReadyToInstall ->
            if (verified) context.getString(R.string.install_stage_ready_verified)
            else context.getString(R.string.install_stage_ready_computed)
        InstallStep.Installing -> context.getString(R.string.install_stage_installing)
        is InstallStep.Done -> label
        is InstallStep.Failed -> reason
    }

sealed interface DownloadState {
    data object Idle : DownloadState

    /** [speed] 是给界面直接显示的字符串，格式换算放在服务里，不在 UI 里做 */
    data class Running(val progress: Float, val speed: String = "") : DownloadState

    /**
     * 用户按了暂停。
     *
     * 单独一个分支而不是给 [Running] 加个 flag：这两个状态下按钮的意思正好相反
     * （跑着 = 暂停，停着 = 继续），用一个布尔去记的话每处渲染都得再判一次
     * 「到底是哪一种」，漏判就是「点下载没反应」。
     *
     * 只存比例不存字节：已下多少从 [DownloadCenter] 读 —— 中枢是唯一事实源，
     * 本地再存一份必然会和服务那边对不上（见 reconcileDownloads）。
     */
    data class Paused(val progress: Float) : DownloadState

    data object Done : DownloadState

    /** 下载或校验真的失败了。失败也要有声音 —— 之前这里只有 Running/Done，失败被吞成「转一下就没了」 */
    data class Failed(val reason: String) : DownloadState
}

@Composable
fun DetailPanel(
    repo: Repo,
    contentAlpha: Float,
    topInset: Dp,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onOpenRelease: () -> Unit,
    onInstallDone: () -> Unit,
    /** 某个安装包真的下到手了。用来补「下载与安装记录」——之前根本没记过 */
    onDownloaded: (repoId: String, assetName: String, sha: String) -> Unit = { _, _, _ -> },
    /**
     * 「打开」失败要有个出口。
     *
     * 拉起一个已装应用失败是真实存在的失败模式（包被卸载了 / 没有启动入口），
     * 而它不经过任何我们自己的下载状态机 —— 不给回调的话就是静默无反应。
     */
    onInstallFailed: (String) -> Unit = {},
    /**
     * 已下载清单（落盘的，跨进程）。
     *
     * 按钮显示「下载 / 安装 / 打开」全靠它。之前这里只有组件内的 `downloads`，
     * 进程一回收就清零，于是隔天回来已下过的包又显示成「没下过」。
     *
     * **由宿主持有并往下传**，不在这里自己读一遍：下载记录页也会读同一份清单，
     * 两边各读各的迟早会分叉（那边装完刷新了，这边还停在「安装」）。
     */
    library: List<DownloadedApk>,
    /** 清单变了（刚下完 / 刚装完）。宿主重新读盘并把新值传回来 */
    onLibraryChanged: () -> Unit,
    /** README 按需加载状态；null 表示尚未触发加载 */
    readme: Async<Readme>? = null,
    onReadmeLoad: () -> Unit = {},
    /** 该仓库是否已被关注（来自本地关注列表，不是组件内临时状态） */
    following: Boolean = false,
    onToggleFollow: () -> Unit = {},
    /** 手动刷新当前仓库。不为 null 时顶栏显示刷新按钮 */
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    /**
     * 详情那次请求的结果，只用来读缓存来源与年龄。
     *
     * 面板渲染的是 [repo] 而不是这个状态 —— 宿主会用列表里的简略数据先占位，
     * 详情回来后再覆盖。这个字段只承载「这份数据有多旧」这一个信息。
     */
    detailState: Async<Repo>? = null,
    modifier: Modifier = Modifier,
) {
    val p = FitTheme.palette
    var tab by remember(repo.id) { mutableStateOf(DetailTab.Assets) }
    val downloads = remember(repo.id) { mutableStateMapOf<String, DownloadState>() }
    // 初始值必须从进程级的 DownloadCenter 还原，不能一律 Idle —— 详见 restoreStep。
    // 两次打开同一个仓库时 DownloadCenter 可能还留着上一轮的状态，面板却从零开始，
    // 于是「点下载没反应」或者「白下一遍」。
    val install = remember(repo.id) {
        mutableStateOf(restoreStep(DownloadCenter.state.value, repo.best?.name))
    }
    val installStep = install.value
    val scope = rememberCoroutineScope()

    /**
     * 「适配产物」是否展开全部。
     *
     * 默认只列适配本机的安卓安装包 —— 一次 Release 常常混着源码包、Windows 桌面包、
     * 校验文件之类，对「这台手机能不能装」这个问题它们全是噪音。但不匹配的也不能删掉，
     * 折叠着并且能一键展开，原因写在各自的行里。
     *
     * 按 repo.id 记住：换仓库时重置回折叠，免得把上一个仓库的展开状态带过来。
     */
    var showAllAssets by remember(repo.id) { mutableStateOf(false) }

    /**
     * 当前只看哪个版本的产物，null = 全部。
     *
     * 详情页一次拉 20 条 release（RELEASES_PER_PAGE），产物会横跨多个 tag。
     * 平铺在一列里的话用户得逐行找「我要的那个版本」，chip 把这件事变成一次点选。
     */
    var selectedTag by remember(repo.id) { mutableStateOf<String?>(null) }

    /**
     * 进面板先和中枢对一次账。
     *
     * 行内状态存在组件里（remember(repo.id)），下载进度存在进程里的前台服务里，
     * 两者会分家：进程重启过、服务被杀过、或者上一次下载的终局没人接。
     * 本地一旦停在 Running，按钮就永远画成转圈的环 —— 上一版还把它 disabled 掉，
     * 那三个症状（点下载没反应、切完下载源回来点不动、没法暂停取消）是同一个死锁。
     */
    LaunchedEffect(repo.id) { reconcileDownloads(downloads) }

    /**
     * 进详情页先刷新一次已下载清单。
     *
     * 两个理由，缺一不可：
     * - 上一次在别的页面装过包（通知栏、下载记录页），这个面板的 [library] 是打开时
     *   那一刻的快照，不刷新就一直停在装之前的状态
     * - 早期版本记下的条目没有清单事实，趁文件还在盘上补读一次
     *
     * 放在 [LaunchedEffect] 而不是 `remember`：这里要的是「每次进入都做一次」，
     * `remember` 只在首次组合跑，之后这个面板被关掉再打开（mounted 翻转）就再也不会刷新。
     */
    LaunchedEffect(repo.id) { onLibraryChanged() }

    /**
     * 主 CTA 用的 [install] 也必须跟着中枢走 —— 它和 [downloads] 是同一件事的两份状态。
     *
     * 上一版只对账了行内 map，结果是：**从通知栏点「继续」**（服务被直接驱动，完全绕过
     * 这个组件）之后，通知已经在下、界面还停在「已暂停」，用户以为卡住了，
     * 在 App 里再点一次「继续」就会**起第二个下载**。
     *
     * 只在**下载阶段的几态之间**纠正（Downloading / Paused ↔ 中枢），
     * 绝不碰 Verifying / ReadyToInstall / Installing / Done / Blocked / Failed ——
     * 那几个是界面自己在推进，拿中枢去覆盖会把「校验完了、等你确认安装」冲掉。
     */
    LaunchedEffect(repo.id, install) {
        val best = repo.best ?: return@LaunchedEffect
        DownloadCenter.state.collect { p ->
            val cur = install.value
            if (cur !is InstallStep.Downloading && cur !is InstallStep.Paused) return@collect
            if (p.assetNameOrNull != best.name) return@collect
            install.value = when (p) {
                is DownloadCenter.Progress.Running -> InstallStep.Downloading(
                    if (p.total > 0) p.bytes.toFloat() / p.total else 0f,
                    p.speed,
                )

                is DownloadCenter.Progress.Paused -> InstallStep.Paused(
                    if (p.total > 0) p.saved.toFloat() / p.total else 0f,
                    p.saved,
                    p.total,
                )

                is DownloadCenter.Progress.Ready -> InstallStep.ReadyToInstall(
                    sha = p.sha,
                    verified = p.verified,
                )

                is DownloadCenter.Progress.Failed -> InstallStep.Failed(p.reason)
                // Idle 说明这一轮已经结束（取消 / 正常收尾）。退回 Idle 才能再点一次下载；
                // 停在这个态就又是一个「点不动」。
                else -> InstallStep.Idle
            }
        }
    }

    /**
     * 详情请求还在飞。
     *
     * 面板渲染的 [repo] 此刻可能只是列表里的简略快照 —— assets / history 都还是空的。
     * 这时候必须说「在加载」，不能拿空快照当事实去报「共 0 个产物」，
     * 那句话会被读成「这个仓库没有发布包」。
     */
    val detailLoading = detailState is Async.Loading
    val detailError = (detailState as? Async.Err)?.message

    // 下载服务要 Context 落盘、PackageInstaller 要 Context 提交，面板本身不接收参数：
    // 让调用方把 Activity 传进来只会多一路可以传错的入口。
    val context = LocalContext.current

    BackHandler { onClose() }

    val blocker = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxSize()
            .background(p.surface)
            // 面板必须吃掉落在空白/内边距上的点击，否则会穿透到下面的遮罩，
            // 表现为「点 CTA 附近却把详情关了」。
            .clickable(interactionSource = blocker, indication = null) { }
            .alpha(contentAlpha),
    ) {
        // 头部
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = topInset)
                .height(DetailMetrics.barHeight)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(FiArrowLeft, stringResource(R.string.detail_action_back), onClose)
            Spacer(Modifier.weight(1f))
            IconCircleButton(
                FiBookmark,
                stringResource(
                    if (following) R.string.detail_action_unfollow else R.string.detail_action_follow,
                ),
                onClick = onToggleFollow,
                active = following,
            )
            Spacer(Modifier.width(2.dp))
            if (onRefresh != null) {
                // 刷新中不能把按钮换成裸 spinner：IconCircleButton 是 40dp，18dp 的裸环
                // 会让顶栏在每次刷新时位移 22dp，整行跟着抖一下。
                // 外面套一个固定 40dp 的槽位，槽内二选一，尺寸就恒定了。
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    if (refreshing) {
                        CircularProgress(0f, size = 18.dp, color = p.ink4, indeterminate = true)
                    } else {
                        // 用刷新图标而不是 FiClock：FiClock 在别处表示「历史 / 时间」，
                        // 放在这里会让人以为点开是看时间线。
                        IconCircleButton(FiRefresh, stringResource(R.string.detail_action_refresh), onClick = onRefresh)
                    }
                }
                Spacer(Modifier.width(2.dp))
            }
            IconCircleButton(FiShare, stringResource(R.string.detail_action_share), onShare)
            Spacer(Modifier.width(2.dp))
            IconCircleButton(FiExternal, stringResource(R.string.detail_action_open_release), onOpenRelease)
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // 标题区
            item(key = "hero") {
                Column(Modifier.padding(start = DetailMetrics.sidePad, end = DetailMetrics.sidePad, top = DetailMetrics.heroTop)) {
                    // 详情是两次请求拼的（元信息 + releases），任一命中缓存就提示
                    val cachedAge = stalestOf(detailState)
                    if (cachedAge != null) {
                        StaleNotice(cachedAge, Modifier.padding(bottom = 12.dp))
                    }
                    // 拉取中 / 拉取失败的提示不在这里。
                    // 它原来插在标题上方，detailLoading 一变就把标题往下顶约 42dp、
                    // 加载完再弹回来 —— 进详情必然看到一次跳动。已挪到 tab 栏下面的产物区
                    // 顶部（见下方 key = "assets-loading"）：那里才是真正缺数据的区域，
                    // 标题、徽章、统计这些已经在屏幕上的东西不会因为拉取而位移。
                    if (detailError != null) {
                        Text(
                            stringResource(R.string.detail_error_banner, detailError),
                            style = FitTypography.bodySmall,
                            color = p.toneFg(FitTone.Warn),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        AppTile(repo.monogram, repo.tileBg, repo.tileFg, size = DetailMetrics.iconSize, corner = 17.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                repo.name,
                                style = FitTypography.headlineMedium,
                                color = p.ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${repo.owner} / ${repo.name}",
                                style = MonoMeta,
                                color = p.ink4,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    MetaRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // 加载中不挂判定徽章。此刻的 repo 很可能还只是列表里的简略快照：
                        // assets 空、verdict 是默认的 Unknown。那份 Unknown 不是结论，
                        // 只是「还没读到」，染成红色挂在标题下就成了「这个仓库无法解析」。
                        // 徽章的位置留给 VerdictCard 那块居中加载提示（见下方 key = "verdict"）。
                        if (!detailLoading) {
                            FitBadge(repo.verdict.tone, stringResource(repo.verdict.labelRes))
                        }
                        OutlineBadge(repo.lang) { LangDot(repo.langColor) }
                        if (repo.device !is DeviceState.NotInstalled) {
                            OutlineBadge(stringResource(R.string.detail_badge_local, installedVersion(repo)))
                        }
                        OutlineBadge(stringResource(R.string.detail_badge_no_login))
                    }
                    Spacer(Modifier.height(16.dp))
                    HairLine()
                    Spacer(Modifier.height(14.dp))
                    // 四个数各占四分之一。star/fork 最容易到六位数，固定宽度会挤掉后两个
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("star", Env.formatStars(repo.stars), Modifier.weight(1f))
                        Stat("fork", Env.formatStars(repo.forks), Modifier.weight(1f))
                        Stat("watcher", Env.formatStars(repo.watchers), Modifier.weight(1f))
                        Stat("issue", Env.formatStars(repo.issues), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }

            /**
             * 三个 tab 的吸顶栏。
             *
             * 面板顶栏（上面那个 Column）已经用 statusBarsPadding() 吃掉了状态栏 inset，
             * 而这个 LazyColumn 排在顶栏**下面** —— 所以它的视口上沿本来就在状态栏之下，
             * 吸顶时自然不会压上去，这里不用再处理一次。
             *
             * 真正的规律是「视口上沿在哪，stickyHeader 就钉在哪」：contentPadding 不参与。
             * 首页踩过这个坑 —— 那边 LazyColumn 是铺满全屏的（视口上沿 = 屏幕顶部），
             * 却把 inset 写进了 contentPadding，结果筛选栏一吸顶就钻到状态栏底下。
             */
            stickyHeader(key = "tabs") {
                TabBar(tab, onSelect = {
                    tab = it
                    // 切到「README」才发请求。README 不随详情预取
                    if (it == DetailTab.Readme) onReadmeLoad()
                })
            }

            // 三个 tab 的内容
            when (tab) {
                DetailTab.Assets -> {
                    // 折叠的是**非安装包**（源码包、桌面包、校验文件），不是「没猜中 ABI 的 apk」。
                    //
                    // 之前按 fit 过滤（Match/Degrade 才显示），于是 arm64 设备上所有
                    // *_x86_64.apk 都是 Mismatch，全被收进「不匹配」那一堆 —— 而那恰恰
                    // 是用户最想看、想点解析的产物。fit 是**按文件名猜**的，拿它决定
                    // 「要不要展示」等于把猜测当事实。
                    //
                    // 现在：kind 决定收不收起（apk/aab 永远显示），fit 只决定徽章颜色。
                    //
                    // 版本分组：产物可能横跨多个 release。只取带 tag 的 —— 没有 tag 的产物在
                    //「全部」里照常可见，但不该独占一格 chip，否则空 tag 会变成一排长得一样
                    // 的按钮。distinct 保持原有顺序，不排序：GitHub 返回的顺序就是版本从新到旧。
                    val tags = repo.assets.mapNotNull { it.tag }.distinct()
                    val prereleaseTags = repo.assets.filter { it.prerelease }.mapNotNull { it.tag }.toSet()
                    // 选中的 tag 可能在刷新后已经不存在（作者把 release 删了）：这时回到「全部」。
                    // 停在空列表上会被读成「这个版本一个产物都没有」。
                    val activeTag = selectedTag?.takeIf { it in tags }

                    // 计数和折叠都跟着当前 tag 走。否则选中一个旧版本时头上还写着
                    // 「共 12 个 / 3 个安装包」—— 那是全仓库的数，和下面列出来的对不上。
                    val scoped = if (activeTag == null) repo.assets else repo.assets.filter { it.tag == activeTag }
                    val scopedInstallable = scoped.filter { it.kind == "APK" || it.kind == "AAB" }
                    val nonInstallable = scoped.size - scopedInstallable.size
                    val shown = if (showAllAssets) scoped else scopedInstallable

                    // 版本切换放在产物区顶部。加载提示不在这里另起一处了 ——
                    // 下面那块居中的加载块已经覆盖同一个时间段，同屏两个 spinner 只会让人
                    // 以为有两个东西在慢慢来。
                    if (tags.size > 1) {
                        item(key = "asset-tags") {
                            AssetVersionChips(
                                tags = tags,
                                prereleaseTags = prereleaseTags,
                                selected = activeTag,
                                onSelect = { selectedTag = it },
                            )
                        }
                    }

                    item(key = "verdict") {
                        // top 间距不能省：LazyColumn 的 contentPadding 只有 bottom，
                        // stickyHeader 的 HairLine 之下就是列表首项，不给 top 的话
                        // 判定卡会直接贴住 tab 栏的下划线。16dp 与更新日志各行一致。
                        // 注意用 start/end 而不是 horizontal —— Modifier.padding 没有
                        // 「horizontal + top」这种混搭重载。
                        Column(
                            Modifier.padding(
                                start = DetailMetrics.sidePad,
                                top = 16.dp,
                                end = DetailMetrics.sidePad,
                            ),
                        ) {
                            // 加载中不给任何判定结论。面板渲染的 repo 此刻可能只是列表快照
                            // （assets 空、verdict 默认 Unknown），照着渲染就会在进详情的第一眼
                            // 弹出一张红色的「无法解析」并断言「这个仓库还没有 release」——
                            // 那不是事实，只是还没读到。
                            if (detailLoading) {
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 26.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    CircularProgress(0f, size = 22.dp, color = p.accent, indeterminate = true)
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        stringResource(R.string.detail_assets_loading),
                                        style = FitTypography.bodyMedium,
                                        color = p.ink4,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            } else {
                                VerdictCard(repo)
                                //「只发过预发布」不是判定结论，是对上面那张卡的补充说明：
                                // 预发布的产物照样能装、ABI 适配照样成立，所以不能塞进
                                // verdictExplain 改红，也不能并进卡里冒充同一个结论。单独一行。
                                FitEngine.prereleaseNotice(repo)?.let { notice ->
                                    Spacer(Modifier.height(12.dp))
                                    PrereleaseNotice(explainText(notice))
                                }
                                Spacer(Modifier.height(12.dp))
                                DeviceStateCard(repo)
                            }
                        }
                    }
                    item(key = "assets-head") {
                        // 加载中整块不渲染：空快照数出来的「共 0 个」不是事实，
                        // 写出来会被读成「这个仓库没有发布包」。
                        if (!detailLoading) {
                            Column(Modifier.padding(start = DetailMetrics.sidePad, end = DetailMetrics.sidePad, top = 18.dp, bottom = 10.dp)) {
                                Text(
                                    when {
                                        // 一个产物都没有：别写「都在上面了」，上面什么都没有
                                        scoped.isEmpty() -> stringResource(R.string.detail_assets_count_empty)
                                        // 折叠时只报「安装包有几个 + 非安装包折起来几个」，不报全量构成，
                                        // 否则用户会以为漏显示了
                                        !showAllAssets ->
                                            stringResource(R.string.detail_assets_count_fitting, scopedInstallable.size) +
                                                if (nonInstallable > 0) {
                                                    stringResource(R.string.detail_assets_count_folded, nonInstallable)
                                                } else {
                                                    stringResource(R.string.detail_assets_count_all)
                                                }
                                        else -> stringResource(
                                            R.string.detail_assets_count_full,
                                            scoped.size,
                                            scopedInstallable.size,
                                            nonInstallable,
                                        )
                                    },
                                    style = FitTypography.bodySmall,
                                    color = p.ink4,
                                )
                                // 一个安装包都没有时把话说死，别让用户对着空列表猜
                                if (!showAllAssets && scopedInstallable.isEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        stringResource(R.string.detail_assets_no_match_hint),
                                        style = FitTypography.bodySmall,
                                        color = p.toneFg(FitTone.Warn),
                                    )
                                }
                            }
                        }
                    }
                    items(count = shown.size, key = { shown[it].name }) { index ->
                        val asset = shown[index]
                        // 未开始 / 失败 / 暂停 都从这里重新开始：前台服务带着已下载的字节续传，
                        // 所以「继续」和「首次下载」是同一条路，不需要另写一份分支。
                        val start = {
                            startDownload(
                                context = context,
                                downloads = downloads,
                                asset = asset,
                                repoName = repo.id,
                                scope = scope,
                                onNoUrl = onOpenRelease,
                                onDownloaded = onDownloaded,
                                onPublished = onLibraryChanged,
                            )
                        }
                        // 这一行现在该显示什么。清单是跨进程的落盘事实源，所以
                        // App 重启之后回来，按钮照样是「安装 / 打开」而不是回到「下载」。
                        val entry = library.firstOrNull {
                            it.repoName == repo.id && it.assetName == asset.name
                        }
                        val action = actionFor(context, entry)
                        AssetRow(
                            asset = asset,
                            primary = repo.best?.name == asset.name,
                            state = downloads[asset.name] ?: DownloadState.Idle,
                            action = action,
                            onClick = {
                                val live = downloads[asset.name] is DownloadState.Running
                                when {
                                    // 跑着 = 暂停；停着 = 继续
                                    live -> DownloadCenter.pause(context, asset.name)
                                    downloads[asset.name] is DownloadState.Paused -> start()
                                    action == ApkAction.OPEN -> {
                                        if (!ApkInstaller.launch(context, entry?.packageName.orEmpty())) {
                                            onInstallFailed(
                                                context.getString(R.string.action_launch_failed),
                                            )
                                        }
                                    }

                                    action == ApkAction.INSTALL && entry != null -> {
                                        installFromFile(
                                            context = context,
                                            install = install,
                                            entry = entry,
                                            repo = repo,
                                            scope = scope,
                                            onDownloaded = onDownloaded,
                                            onInstalled = onLibraryChanged,
                                            onFailed = onInstallFailed,
                                        )
                                    }

                                    else -> start()
                                }
                            },
                            onCancel = {
                                DownloadCenter.cancel(context, asset.name)
                                // 终局不一定还回来（服务可能正被杀），本地先清掉。留着转圈的
                                // 按钮正是死锁的成因 —— 它看起来就不可点，用户只能杀进程。
                                downloads.remove(asset.name)
                            },
                        )
                    }
                    if (nonInstallable > 0 || showAllAssets) {
                        item(key = "assets-toggle") {
                            AssetToggle(
                                expanded = showAllAssets,
                                hiddenCount = nonInstallable,
                                onToggle = { showAllAssets = !showAllAssets },
                            )
                        }
                    }
                }

                DetailTab.Changelog -> {
                    if (detailLoading) {
                        item(key = "cl-loading") {
                            Text(
                                stringResource(R.string.detail_changelog_loading),
                                style = FitTypography.bodySmall,
                                color = p.ink4,
                                modifier = Modifier.padding(horizontal = DetailMetrics.sidePad, vertical = 18.dp),
                            )
                        }
                    }
                    items(count = repo.history.size, key = { i -> "rel-${repo.history[i].tag}" }) { index ->
                        val release = repo.history[index]
                        Column(Modifier.padding(horizontal = DetailMetrics.sidePad)) {
                            Column(Modifier.padding(vertical = 16.dp)) {
                                MetaRow {
                                    Text(release.tag, style = FitTypography.titleSmall.copy(fontFamily = com.heiyehk.fithub.ui.theme.Fonts.Mono), color = p.ink)
                                    if (release.prerelease) {
                                        Spacer(Modifier.width(7.dp))
                                        FitBadge(FitTone.Prerelease, stringResource(R.string.detail_badge_prerelease))
                                    }
                                    if (index == 0) {
                                        Spacer(Modifier.width(7.dp))
                                        FitBadge(FitTone.Ok, stringResource(R.string.detail_badge_latest))
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Text(ageText(release.date), style = MonoMeta, color = p.ink4)
                                }
                                Spacer(Modifier.height(10.dp))
                                // body 是 GitHub 返回的原始 Markdown。交给渲染器，别再按行拆：
                                // 拆行那一步已经从数据层拿掉了，这里再拆一次等于把语义永久丢掉 ——
                                // 标题、列表、`code` 全塌成同一种普通文字（和 README 用同一个渲染器）。
                                if (release.body.isBlank()) {
                                    Text(
                                        stringResource(R.string.detail_changelog_empty),
                                        style = FitTypography.bodyMedium,
                                        color = p.ink4,
                                    )
                                } else {
                                    com.mikepenz.markdown.m3.Markdown(
                                        content = release.body,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            HairLine()
                        }
                    }
                }

                DetailTab.Readme -> {
                    item(key = "readme") {
                        Column(Modifier.padding(horizontal = DetailMetrics.sidePad)) {
                            Spacer(Modifier.height(18.dp))

                            when (val r = readme) {
                                // 尚未点开过这个 tab：不发请求，给一个明确的入口
                                null -> Column(
                                    Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        stringResource(R.string.detail_readme_lazy_hint),
                                        style = FitTypography.bodyMedium,
                                        color = p.ink3,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    com.heiyehk.fithub.ui.components.GhostButton(
                                        stringResource(R.string.detail_readme_load),
                                        onReadmeLoad,
                                    )
                                }

                                Async.Loading -> Column(
                                    Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Spacer(Modifier.height(40.dp))
                                    Text(
                                        stringResource(R.string.detail_readme_loading),
                                        style = FitTypography.bodyMedium,
                                        color = p.ink4,
                                    )
                                }

                                is Async.Err -> Column(
                                    Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Spacer(Modifier.height(24.dp))
                                    Text(
                                        r.message,
                                        style = FitTypography.bodyMedium,
                                        color = p.ink3,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    com.heiyehk.fithub.ui.components.GhostButton(
                                        stringResource(R.string.detail_retry),
                                        onReadmeLoad,
                                    )
                                }

                                is Async.Ok -> {
                                    // 缓存命中时说明数据年龄，别把旧内容当刚拉取的
                                    if (r.fromCache) {
                                        StaleNotice(r.ageMs)
                                        Spacer(Modifier.height(14.dp))
                                    }
                                    // 截断必须告知，否则用户以为读完了
                                    if (r.value.truncated) {
                                        Text(
                                            stringResource(
                                                R.string.detail_readme_truncated,
                                                Readme.MAX_BYTES / 1024,
                                            ),
                                            style = FitTypography.bodySmall,
                                            color = p.ink3,
                                        )
                                        Spacer(Modifier.height(12.dp))
                                    }
                                    com.mikepenz.markdown.m3.Markdown(
                                        content = r.value.markdown,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }

                            Spacer(Modifier.height(18.dp))
                            Text(
                                stringResource(R.string.detail_readme_package_info),
                                style = FitTypography.titleSmall,
                                color = p.ink,
                            )
                            Spacer(Modifier.height(8.dp))
                            MonoBlock(
                                buildString {
                                    appendLine("minSdk   ${repo.dist.minSdk}   (${repo.dist.sdkLabel})")
                                    appendLine("abis     ${repo.dist.abis.joinToString(", ")}")
                                    appendLine("signed   ${stringResource(repo.dist.signed.labelRes)}")
                                    appendLine("device   ${Env.device.abi} / API ${Env.device.sdk}")
                                },
                            )
                            Spacer(Modifier.height(16.dp))
                            HairLine()
                            Spacer(Modifier.height(14.dp))
                            Text(
                                stringResource(R.string.detail_readme_disclaimer),
                                style = FitTypography.bodySmall,
                                color = p.ink4,
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }

        // 底部：唯一的主按钮 + 安装状态
        Column(
            Modifier
                .fillMaxWidth()
                .background(p.surface)
                .padding(bottom = 14.dp),
        ) {
            AnimatedVisibility(
                visible = installStep !is InstallStep.Idle,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(160)),
            ) {
                InstallProgress(
                    step = installStep,
                    onResume = {
                        startInstall(
                            context, install, downloads, repo, scope, onOpenRelease, onDownloaded,
                            onPublished = onLibraryChanged,
                        )
                    },
                    onCancel = {
                        repo.best?.let { best ->
                            DownloadCenter.cancel(context, best.name)
                            downloads.remove(best.name)
                        }
                        DownloadCenter.reset()
                        install.value = InstallStep.Idle
                    },
                )
            }
            HairLine()
            Column(Modifier.padding(horizontal = DetailMetrics.sidePad, vertical = 14.dp)) {
                PrimaryButton(
                    text = when {
                        installStep is InstallStep.Blocked -> stringResource(R.string.detail_cta_blocked)
                        installStep is InstallStep.Done -> stringResource(R.string.detail_cta_done)
                        // 下载中和暂停态都把主按钮让给这一轮下载：写「下载最佳适配」会让人
                        // 以为要重新从 0 下一遍，实际语义只是暂停 / 继续
                        installStep is InstallStep.Downloading ->
                            stringResource(R.string.download_action_pause)
                        installStep is InstallStep.Paused ->
                            stringResource(R.string.download_action_resume)
                        installStep is InstallStep.ReadyToInstall ->
                            stringResource(
                                R.string.detail_cta_confirm,
                                Env.formatSize(repo.best?.sizeMb ?: 0.0),
                            )
                        else -> ctaLabel(context, repo)
                    },
                    icon = when {
                        installStep is InstallStep.Blocked -> FiAlert
                        installStep is InstallStep.Done -> FiCheck
                        installStep is InstallStep.Idle ->
                            if (repo.dist.desktop || repo.verdict == Verdict.Unknown) FiExternal else FiDownload
                        // 下载中 / 暂停态这个按钮管的是这一轮下载，别再挂盾牌
                        installStep is InstallStep.Downloading || installStep is InstallStep.Paused -> FiDownload
                        else -> FiShield
                    },
                    // 下载中**不能**禁用：这个状态下点它就是暂停。之前连它一起锁死，
                    // 前台服务一旦被杀（清缓存 / 被系统回收）用户就既不能取消也不能重来，
                    // 只能杀进程。真正不能打断的只有校验和提交安装这两段 ——
                    // 那时打断会留下半截状态。
                    enabled = installStep !is InstallStep.Verifying &&
                        installStep !is InstallStep.CheckingSignature &&
                        installStep !is InstallStep.Installing,
                    onClick = {
                        when (val step = installStep) {
                            is InstallStep.Downloading -> {
                                repo.best?.let { DownloadCenter.pause(context, it.name) }
                            }

                            // 暂停 = 继续下载。走的是同一条前台服务链路，续传由服务负责
                            is InstallStep.Paused -> {
                                startInstall(
                                    context, install, downloads, repo, scope, onOpenRelease, onDownloaded,
                                    onPublished = onLibraryChanged,
                                )
                            }

                            is InstallStep.ReadyToInstall -> {
                                val current = DownloadCenter.state.value
                                val file = (current as? DownloadCenter.Progress.Ready)?.file
                                android.util.Log.i(
                                    "FitHubInstall",
                                    "点击确认安装，当前 DownloadCenter=" +
                                        current::class.simpleName + " file=" + file,
                                )
                                if (file == null) {
                                    // 上一次下载结果没了（进程被杀、缓存被清）。不能顺着
                                    // 上一状态假装能装，退回 Idle 让用户重新走一次。
                                    install.value = InstallStep.Failed(
                                        context.getString(R.string.install_error_result_lost),
                                    )
                                    DownloadCenter.reset()
                                } else {
                                    install.value = InstallStep.Installing
                                    ApkInstaller.install(context, file) { ok, msg ->
                                        // 回调在 binder 线程上，状态必须切回主线程改
                                        scope.launch {
                                            when {
                                                ok -> {
                                                    install.value = InstallStep.Done(
                                                        context.getString(
                                                            R.string.install_done_label,
                                                            repo.name,
                                                            repo.version,
                                                        ),
                                                    )
                                                    // 装完必须记进已下载清单，否则这一行永远停在
                                                    // 「安装」：清单是按钮三态的唯一数据源，而它
                                                    // 是落盘的，不写就等于这次安装对界面不存在。
                                                    // 装完把清单刷回来，按钮当场就变成「打开」。
                                                    library.firstOrNull {
                                                        it.repoName == repo.id &&
                                                            it.assetName == repo.best?.name
                                                    }?.let { ApkLibrary.setInstalled(context, it.key, true) }
                                                    onInstallDone()
                                                    onLibraryChanged()
                                                }

                                                msg == ApkInstaller.NEEDS_PERMISSION -> {
                                                    install.value = InstallStep.Blocked(
                                                        context.getString(R.string.install_error_no_permission),
                                                    )
                                                    openInstallPermissionSettings(context)
                                                }

                                                else -> install.value = InstallStep.Failed(
                                                    context.getString(R.string.install_error_failed, msg),
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            is InstallStep.Blocked -> {
                                if (ApkInstaller.canRequestInstall(context)) {
                                    // 用户已经去设置里开好了，回来再点一次就能装
                                    install.value = InstallStep.Idle
                                } else {
                                    openInstallPermissionSettings(context)
                                }
                            }

                            is InstallStep.Done -> onClose()

                            is InstallStep.Failed -> {
                                install.value = InstallStep.Idle
                                DownloadCenter.reset()
                            }

                            is InstallStep.Idle ->
                                startInstall(
                                    context, install, downloads, repo, scope, onOpenRelease, onDownloaded,
                                    onPublished = onLibraryChanged,
                                )

                            else -> Unit
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
                MetaRow(horizontalArrangement = Arrangement.Center) {
                    val best = repo.best
                    when {
                        installStep !is InstallStep.Idle ->
                            Text(installStep.stageLabel(context), style = MonoMeta, color = p.ink3)

                        best != null && repo.device !is DeviceState.SigningConflict -> {
                            Icon(FiShield, null, tint = p.accent, modifier = Modifier.size(12.dp))
                            // 原来写「下载后强校验」是句空话：GitHub 的 Release 不给产物
                            // 提供权威 SHA-256，App 算出来的是自己的指纹，没有比对对象。
                            Text(
                                if (best.sha != null) {
                                    stringResource(R.string.detail_sha_partial, best.sha)
                                } else {
                                    stringResource(R.string.detail_sha_pending)
                                },
                                style = MonoMeta,
                                color = p.ink4,
                            )
                        }

                        repo.device is DeviceState.SigningConflict -> {
                            Icon(FiAlert, null, tint = p.toneFg(FitTone.Bad), modifier = Modifier.size(12.dp))
                            Text(
                                stringResource(R.string.detail_conflict_hint),
                                style = MonoMeta,
                                color = p.toneFg(FitTone.Bad),
                            )
                        }

                        else ->
                            Text(
                                stringResource(R.string.detail_official_note),
                                style = MonoMeta,
                                color = p.ink4,
                            )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallProgress(
    step: InstallStep,
    onResume: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    val p = FitTheme.palette
    val bad = step is InstallStep.Blocked || step is InstallStep.Failed
    // 暂停用中性色：绿色是「在推进」，红色是「出错了」，停下来的下载两者都不是，
    // 套其中任何一个都会让用户以为事情还在往前走 / 已经完蛋了。
    val tone = when {
        bad -> FitTone.Bad
        step is InstallStep.Paused -> FitTone.Muted
        else -> FitTone.Ok
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = DetailMetrics.sidePad, vertical = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(p.toneBg(tone))
            .padding(14.dp),
    ) {
        // 这里只留图标和进度条，文字一律交给 CTA 下方那行 stageLabel。
        // 之前两个地方渲染的是同一个 stageLabel，于是底部同时出现
        // 「下载中 15% · 5.0 MB/s · 15%」—— 同一个数字说两遍，还拼成一句读不通的话。
        MetaRow {
            when {
                bad -> Icon(FiAlert, null, tint = p.toneFg(FitTone.Bad), modifier = Modifier.size(14.dp))
                step is InstallStep.Done -> Icon(FiCheck, null, tint = p.toneFg(FitTone.Ok), modifier = Modifier.size(14.dp))
                // 停住了就别再转：转着的环会被读成「还在下」
                step is InstallStep.Paused -> Icon(
                    FiDownload,
                    null,
                    tint = p.ink3,
                    modifier = Modifier.size(14.dp),
                )
                else -> CircularProgress(progress = step.progressFraction, size = 14.dp, color = p.accent)
            }
        }
        Spacer(Modifier.height(9.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(p.surface.copy(alpha = 0.7f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(step.progressFraction.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (bad) p.toneFg(FitTone.Bad) else if (step is InstallStep.Paused) p.ink4 else p.accent),
            )
        }
        if (step is InstallStep.Blocked) {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.detail_install_blocked_explain),
                style = FitTypography.bodySmall,
                color = p.ink3,
            )
        }
        // 暂停态把两个出口都摆在眼前。主按钮虽然也写着「继续」，但一个按钮只给得出
        // 一个动作；没有取消的话，半路停下来的下载就没地方了结。
        if (step is InstallStep.Paused) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillAction(stringResource(R.string.download_action_resume), p.accent, onResume)
                PillAction(stringResource(R.string.download_action_cancel), p.ink4, onCancel)
            }
        }
    }
}

/**
 * 跳到「安装未知来源应用」的授权页。
 *
 * 实现挪到了 [ApkInstaller.openPermissionSettings]：通知栏那条安装入口
 * （[com.heiyehk.fithub.MainActivity]）也得跳同一页，两边各写一份的话
 * 一定会有一边漏掉 —— 漏掉的那边只弹一句 toast，用户没有能解决的入口。
 */
private fun openInstallPermissionSettings(context: Context) =
    ApkInstaller.openPermissionSettings(context)

/**
 * 下载 → 校验 → 交给系统安装器。
 *
 * 之前这里是 `Animatable` 播 1800ms 假进度、700ms 假校验、900ms 假签名检查，
 * 然后 `delay(900)` 直接宣布「已安装」—— **全程不碰网络也不碰文件系统**，
 * 还会往历史里写一条根本没发生过的安装记录。
 *
 * 现在接的是真的链路：
 *   [DownloadService]  在前台服务里下文件，边下边算 SHA-256，通知栏同步进度；
 *   SHA-256 对不上直接失败（Release 页没给 sha 时不能假装校验过）；
 *   校验过了才进 [InstallStep.ReadyToInstall]，等用户点确认才提交给系统安装器。
 *
 * 进度观察和终局等待拆成两个协程：StateFlow 不会自己结束，用 `first {}` 等终局
 * 的话又拿不到中途的进度。
 */
private fun startInstall(
    context: Context,
    state: MutableState<InstallStep>,
    downloads: MutableMap<String, DownloadState>,
    repo: Repo,
    scope: CoroutineScope,
    onNoDownloadUrl: () -> Unit,
    /** 记一条「下载过这个包」的历史。持久化在宿主那边，这里只给数据 */
    onDownloaded: (String, String, String) -> Unit = { _, _, _ -> },
    /**
     * 成品已落到公共下载目录。
     *
     * 主 CTA 这条路**必须**也回调它：清单是按钮三态的唯一数据源，而这条路径和
     * 行内按钮是两条独立的入口。漏了它就会出现「包已经下到下载目录、行内那个
     * 按钮却还是下载箭头」—— 界面上看着像没下过。
     */
    onPublished: () -> Unit = {},
) {
    val asset = repo.best
    if (asset == null) { onNoDownloadUrl(); return }
    val url = asset.downloadUrl
    if (url.isNullOrBlank()) {
        // 没下载地址的（示例数据、桌面端包）不该在这里假装能下
        onNoDownloadUrl()
        return
    }
    if (DownloadCenter.isBusyFor(asset.name)) {
        // 不能静默 return：那正是「点了完全没反应」的成因 —— 按钮写着「下载」，
        // 点下去什么也不发生，用户只会以为 App 卡了。把已经在跑的那一次接到界面上，
        // 按钮当场变成可暂停的进度态。
        state.value = restoreStep(DownloadCenter.state.value, asset.name)
        return
    }

    scope.launch {
        state.value = InstallStep.Downloading(0f, context.getString(R.string.install_stage_preparing))
        // 用 repo.id 而不是 repo.name：通知栏点「安装」要拿这份标识回查已下载清单，
        // 两处必须一致。name 只是裸仓库名（flutter），不同 owner 会撞车
        DownloadCenter.enqueue(context, url, asset, repo.id)

        val watcher = launch {
            DownloadCenter.state.collect { p ->
                when (p) {
                    is DownloadCenter.Progress.Running -> {
                        val frac = if (p.total > 0) p.bytes.toFloat() / p.total else 0f
                        state.value = InstallStep.Downloading(
                            frac,
                            "${p.speed} · ${(frac * 100).toInt()}%",
                        )
                        downloads[asset.name] = DownloadState.Running(frac, p.speed)
                    }

                    is DownloadCenter.Progress.Paused -> {
                        val frac = if (p.total > 0) p.saved.toFloat() / p.total else 0f
                        state.value = InstallStep.Paused(frac, p.saved, p.total)
                        downloads[asset.name] = DownloadState.Paused(frac)
                    }

                    is DownloadCenter.Progress.Verifying -> state.value = InstallStep.Verifying
                    else -> Unit
                }
            }
        }

        val outcome = DownloadCenter.state.first {
            it is DownloadCenter.Progress.Ready || it is DownloadCenter.Progress.Failed
        }
        watcher.cancel()

        when (outcome) {
            is DownloadCenter.Progress.Failed -> {
                downloads[asset.name] = DownloadState.Failed(outcome.reason)
                state.value = InstallStep.Failed(outcome.reason)
                DownloadCenter.reset()
            }

            is DownloadCenter.Progress.Ready -> {
                downloads[asset.name] = DownloadState.Done
                // 文件确实到手了才记 —— 记在 Ready 而不是点下载那一刻，
                // 否则失败和中途退出的记录会和真正下好的混在一起
                onDownloaded(repo.id, asset.name, outcome.sha)
                onPublished()
                state.value = when {
                    repo.device is DeviceState.SigningConflict -> InstallStep.Blocked(
                        context.getString(R.string.install_blocked_signature_differs),
                    )

                    // 「安装前强制校验 SHA-256」：拿不到权威校验和就不放行。
                    // 这个开关默认关 —— GitHub 的 Release 绝大多数不给产物提供
                    // SHA-256，默认开会变成「大部分仓库都装不了」。
                    Prefs.state.value.requireSha && !outcome.verified ->
                        InstallStep.Failed(
                            context.getString(R.string.install_error_require_sha),
                        )

                    else -> InstallStep.ReadyToInstall(sha = outcome.sha, verified = outcome.verified)
                }
            }

            else -> Unit
        }
    }
}

/**
 * 把**进程级**的 [DownloadCenter.Progress] 还原成这个面板该显示的 [InstallStep]。
 *
 * 存在的理由是「点了完全没反应」那个 bug：面板打开时 [install] 原来一律从
 * `InstallStep.Idle` 起步，而 [DownloadCenter] 是 `object` —— **进程活多久它活多久，
 * 比面板活得久**。于是只要下载是在面板关着的时候跑的（前台服务继续下载，用户
 * 切出去再回来 / 转屏 / 面板被重建），两边就对不上了：
 *
 * - 服务还在跑 → 全局是 `Running`，面板是 `Idle` → 按钮写着「下载」，
 *   点下去被 `isBusyFor` 静默 return，**什么都没发生**
 * - 服务已经下完 → 全局是 `Ready`，面板是 `Idle` → 点下去会**重新下几十 MB**
 *
 * 面板打开时先按全局状态还原一次，两边就重新对齐了。
 *
 * [assetName] 对不上的一律当 [InstallStep.Idle]：全局那一份是别的仓库的下载，
 * 拿它冒充本仓库的进度只会显示一个假的百分比。
 *
 * [DownloadCenter.Progress.Verifying] 是唯一不带资产名的状态，而 DownloadCenter
 * 是**单槽**的 —— 同一时刻只有一次下载/校验在进行，所以直接认领给当前面板。
 */
private fun restoreStep(
    progress: DownloadCenter.Progress,
    assetName: String?,
): InstallStep {
    if (assetName == null) return InstallStep.Idle
    return when (progress) {
        is DownloadCenter.Progress.Running ->
            if (progress.assetName == assetName) {
                InstallStep.Downloading(
                    if (progress.total > 0) progress.bytes.toFloat() / progress.total else 0f,
                    progress.speed,
                )
            } else {
                InstallStep.Idle
            }

        is DownloadCenter.Progress.Paused ->
            if (progress.assetName == assetName) {
                InstallStep.Paused(
                    if (progress.total > 0) progress.saved.toFloat() / progress.total else 0f,
                    progress.saved,
                    progress.total,
                )
            } else {
                InstallStep.Idle
            }

        DownloadCenter.Progress.Verifying -> InstallStep.Verifying

        is DownloadCenter.Progress.Ready ->
            if (progress.assetName == assetName) {
                InstallStep.ReadyToInstall(progress.sha, progress.verified)
            } else {
                InstallStep.Idle
            }

        // 失败态回到 Idle：重新打开面板就该给一次干净的重试机会，
        // 而不是把上一轮的失败原因一直挂在按钮上。
        DownloadCenter.Progress.Idle,
        is DownloadCenter.Progress.Failed,
        -> InstallStep.Idle
    }
}

/** 单个产物的行内下载按钮：走同一条前台服务链路，和详情页主 CTA 共用进度。 */
private fun startDownload(
    context: Context,
    downloads: MutableMap<String, DownloadState>,
    asset: Asset,
    repoName: String,
    scope: CoroutineScope,
    onNoUrl: () -> Unit,
    onDownloaded: (repoId: String, assetName: String, sha: String) -> Unit = { _, _, _ -> },
    /** 成品已经落到公共下载目录、清单也记上了 —— 宿主用它把按钮切成「安装 / 打开」 */
    onPublished: () -> Unit = {},
) {
    val url = asset.downloadUrl
    if (url.isNullOrBlank()) { onNoUrl(); return }
    if (DownloadCenter.isBusyFor(asset.name)) {
        // 同 startInstall：不能静默 return，把已经在跑的进度接到这一行上，
        // 否则行内按钮点了不动，而界面上连「正在下」都看不出来。
        val p = DownloadCenter.state.value
        if (p is DownloadCenter.Progress.Running) {
            downloads[asset.name] = DownloadState.Running(
                if (p.total > 0) p.bytes.toFloat() / p.total else 0f,
                p.speed,
            )
        }
        return
    }
    scope.launch {
        downloads[asset.name] = DownloadState.Running(0f, context.getString(R.string.install_stage_preparing_short))
        DownloadCenter.enqueue(context, url, asset, repoName)

        // 只等**自己这个产物**的终局。之前把 `Running && assetName == 本人` 也算进
        // 终局条件里，结果第一个进度回调就把协程结束了，后面再读 state.value 拿到的
        // 还是 Running —— 行内状态永远停在「转圈」，也不会显示失败。
        //
        // 中途的进度 / 暂停要另开一条 collect：first{} 一旦满足就返回，拿不到中途的值。
        // 暂停不是终局，不能加进下面的条件里，否则协程会带着 Paused 结束，
        // 之后重新下载就没人接终局了。
        val watcher = launch {
            DownloadCenter.state.collect { p ->
                when (p) {
                    is DownloadCenter.Progress.Running ->
                        if (p.assetName == asset.name) {
                            downloads[asset.name] = DownloadState.Running(
                                if (p.total > 0) p.bytes.toFloat() / p.total else 0f,
                                p.speed,
                            )
                        }

                    is DownloadCenter.Progress.Paused ->
                        if (p.assetName == asset.name) {
                            downloads[asset.name] = DownloadState.Paused(
                                if (p.total > 0) p.saved.toFloat() / p.total else 0f,
                            )
                        }

                    else -> Unit
                }
            }
        }

        val outcome = DownloadCenter.state.first {
            (it is DownloadCenter.Progress.Ready && it.assetName == asset.name) ||
                (it is DownloadCenter.Progress.Failed && it.assetName == asset.name)
        }
        watcher.cancel()
        downloads[asset.name] = when (outcome) {
            is DownloadCenter.Progress.Failed -> DownloadState.Failed(outcome.reason)
            is DownloadCenter.Progress.Ready -> {
                onDownloaded(repoName, asset.name, outcome.sha)
                // 文件已经落到公共下载目录、清单也记上了，通知宿主把按钮切成
                // 「安装 / 打开」—— 不刷新的话这一行还要等进程重启才对
                onPublished()
                DownloadState.Done
            }
            else -> DownloadState.Idle
        }
    }
}

/**
 * 装一个**已经在下载目录里**的包。不重新下载。
 *
 * 走 [ApkInstaller] 而不是复用 [startInstall]：那条路的第一件事就是发起下载，
 * 而这里文件已经在了 —— 再下一遍几十 MB 是纯粹的浪费。
 */
private fun installFromFile(
    context: Context,
    install: MutableState<InstallStep>,
    entry: DownloadedApk,
    repo: Repo,
    scope: CoroutineScope,
    onDownloaded: (String, String, String) -> Unit,
    onInstalled: () -> Unit,
    onFailed: (String) -> Unit,
) {
    val file = entry.file()
    if (!file.exists() || file.length() <= 0L) {
        // 用户把文件从下载目录里删了。清单会在下一次 list() 时自动剔除这条，
        // 这里只说清楚发生了什么，不报成安装失败
        onFailed(context.getString(R.string.action_file_missing))
        return
    }
    if (!ApkInstaller.canRequestInstall(context)) {
        onFailed(context.getString(R.string.install_error_no_permission))
        return
    }
    install.value = InstallStep.Installing
    ApkInstaller.install(context, file) { ok, message ->
        // 回调在 binder 线程上，状态必须切回主线程改
        scope.launch {
            if (ok) {
                ApkLibrary.setInstalled(context, entry.key, true)
                onDownloaded(repo.id, entry.assetName, entry.sha256)
                onInstalled()
                install.value = InstallStep.Done(
                    context.getString(
                        R.string.install_done_label,
                        entry.versionName.ifBlank { entry.displayName },
                        "",
                    ).trim(),
                )
            } else {
                install.value = InstallStep.Failed(message)
                onFailed(context.getString(R.string.install_error_failed, message))
            }
        }
    }
}

/**
 * 用中枢的状态纠正行内状态。
 *
 * [DownloadState] 活在组件里（remember(repo.id)），下载进度活在前台服务里（进程级）。
 * 两者一定会分家：进程重启过、服务被杀过、或者上一次的终局没人接。本地一旦停在
 * Running，那一行的按钮就永远画成转圈的环 —— 上一版还把它 disabled 掉，于是「点下载
 * 没反应」「切完下载源回来还是点不动」「没法暂停取消」是同一个死锁的三个症状。
 *
 * 中枢是唯一事实源：它说停就是停，它说终局就照抄，连它说 Idle 却在转的也一并清掉 ——
 * 那正是「等不到的终局」。本地状态永远只做缓存，不做判断。
 */
private fun reconcileDownloads(downloads: MutableMap<String, DownloadState>) {
    when (val p = DownloadCenter.state.value) {
        is DownloadCenter.Progress.Running ->
            downloads[p.assetName] = DownloadState.Running(
                if (p.total > 0) p.bytes.toFloat() / p.total else 0f,
                p.speed,
            )

        is DownloadCenter.Progress.Paused ->
            downloads[p.assetName] = DownloadState.Paused(
                if (p.total > 0) p.saved.toFloat() / p.total else 0f,
            )

        is DownloadCenter.Progress.Ready -> downloads[p.assetName] = DownloadState.Done

        is DownloadCenter.Progress.Failed -> downloads[p.assetName] = DownloadState.Failed(p.reason)

        DownloadCenter.Progress.Idle -> downloads.keys
            .filter { downloads[it] is DownloadState.Running }
            .forEach { downloads.remove(it) }

        // 校验中不动行内状态：进度条停在 100% 比显示「校验」更贴近用户看到的东西
        DownloadCenter.Progress.Verifying -> Unit
    }
}

private fun installedVersion(repo: Repo): String =    Env.device.installed.firstOrNull { it.repoId == repo.id }?.version ?: "—"

private fun ctaLabel(context: Context, repo: Repo): String {
    val best = repo.best
    return when {
        repo.dist.desktop -> context.getString(R.string.detail_cta_desktop)
        repo.verdict == Verdict.Unknown -> context.getString(R.string.detail_cta_raw_link)
        repo.device is DeviceState.SigningConflict -> context.getString(R.string.detail_cta_conflict)
        best != null ->
            context.getString(R.string.detail_cta_best, Env.formatSize(best.sizeMb))
        else -> context.getString(R.string.detail_cta_all, repo.assets.size)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    val p = FitTheme.palette
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = FitTypography.labelSmall, color = p.ink4, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        // 单行 + 省略：数字再长也不换行把这一格撑高，四个格子保持同一基线
        Text(
            value,
            style = FitTypography.titleSmall.copy(fontFamily = com.heiyehk.fithub.ui.theme.Fonts.Mono),
            color = p.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 产物列表底部的展开/收起行。
 *
 * [hiddenCount] 是当前状态下「看不到几个」：折叠时是折起来的数量，展开时是藏起来的数量。
 * 两种状态都要说清楚，否则用户会以为这就是全部。
 */
@Composable
private fun AssetToggle(expanded: Boolean, hiddenCount: Int, onToggle: () -> Unit) {
    val p = FitTheme.palette
    val angle by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 180f else 90f,
        animationSpec = tween(180),
        label = "assetsToggle",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .tap { onToggle() }
            .padding(horizontal = DetailMetrics.sidePad, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) {
                stringResource(R.string.detail_assets_collapse, hiddenCount)
            } else {
                stringResource(R.string.detail_assets_expand, hiddenCount)
            },
            style = FitTypography.labelLarge,
            color = p.ink3,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            FiChevron,
            contentDescription = null,
            tint = p.ink4,
            modifier = Modifier
                .size(13.dp)
                .graphicsLayer { rotationZ = angle },
        )
    }
    HairLine(Modifier.padding(horizontal = DetailMetrics.sidePad))
}

/**
 * 产物区的版本切换（一排横向可滚动的 chip）。
 *
 * 详情页一次拉 20 条 release，产物会横跨多个 tag。平铺在一列里，用户要自己逐行找
 * 「我要的那个版本」；chip 把这件事变成一次点选。
 *
 * 首位的「全部」是默认项（selected == null）。只有 tag 多于一个时才渲染整排：
 * 只有一个版本时这排 chip 里只有一个按钮，点它和不点它看到的东西完全一样，纯占地方。
 *
 * 顺序跟着 repo.assets 走，不排序 —— GitHub 返回的顺序就是版本从新到旧，
 * 另排一次只会让「第一个」不再等于最新那个。
 */
@Composable
private fun AssetVersionChips(
    tags: List<String>,
    prereleaseTags: Set<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = DetailMetrics.sidePad, top = 14.dp, end = DetailMetrics.sidePad),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VersionChip(
            label = stringResource(R.string.home_filter_all),
            active = selected == null,
            onClick = { onSelect(null) },
        )
        tags.forEach { tag ->
            VersionChip(
                label = tag,
                prerelease = tag in prereleaseTags,
                active = tag == selected,
                onClick = { onSelect(tag) },
            )
        }
    }
}

@Composable
private fun VersionChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    prerelease: Boolean = false,
) {
    val p = FitTheme.palette
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (active) p.accent else Color.Transparent)
            .border(BorderStroke(1.dp, if (active) p.accent else p.hairline), CircleShape)
            .tap { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            label,
            style = FitTypography.titleSmall,
            color = if (active) p.surface else p.ink3,
            maxLines = 1,
        )
        // 预发布标在 chip 上而不是靠名字猜：tag 完全可能是 "v1.2" 这种看不出性质的写法。
        // 用 Prerelease 色调而不是 Warn —— 它不是警告，只是版本性质。
        if (prerelease) {
            Text(
                stringResource(R.string.detail_badge_prerelease),
                style = FitTypography.labelSmall,
                color = if (active) p.surface.copy(alpha = 0.78f) else p.toneFg(FitTone.Prerelease),
                maxLines = 1,
            )
        }
    }
}

/**
 * 「这个仓库只发过预发布」。
 *
 * 刻意不用 Warn / Bad 色：预发布的产物照样能装、ABI 适配结论也照样成立，染成警告色
 * 等于替用户下一个他没下的判断。语气和更新日志里那枚「预发布」徽标保持一致。
 *
 * 也刻意放在判定卡**外面**：它是补充说明，不是判定结论。塞进 VerdictCard 里
 * 会和红色结论挤在同一张牌子上，用户会把两件事当成同一件。
 */
@Composable
private fun PrereleaseNotice(text: String) {
    val p = FitTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.toneBg(FitTone.Prerelease))
            .border(BorderStroke(1.dp, p.toneLine(FitTone.Prerelease)), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        FitBadge(FitTone.Prerelease, stringResource(R.string.detail_badge_prerelease))
        Spacer(Modifier.width(10.dp))
        Text(
            boldMarkup(text, p.ink2),
            style = FitTypography.bodySmall,
            color = p.ink3,
        )
    }
}

/** 暂停态下的文字动作。圆形按钮里只有一条语义链，取消另起一个出口。 */
@Composable
private fun PillAction(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .border(BorderStroke(1.dp, color.copy(alpha = 0.45f)), CircleShape)
            .tap { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, style = FitTypography.labelSmall, color = color, maxLines = 1)
    }
}

/** 字节 → 显示文本。Env.formatSize 收的是 MB，转一下，别让暂停态里冒出第二套单位。 */
private fun formatBytes(bytes: Long): String = Env.formatSize(bytes / 1024.0 / 1024.0)

/**
 * 三个 tab 的横排选择器，吸在面板顶部。
 *
 * 底下那层不透明底色是必须的：吸顶之后内容会从它下面滚过去，透明的话文字会叠在一起。
 * 补一条发丝线是为了让「被挡住」这件事有明确边界，而不是内容凭空消失。
 *
 * 整行的 start 用 sidePad（20dp），和下面每张卡片的左边界对齐。item 自己只管 end，
 * 那 22dp 是「这一格到下一格」的间距，不是「屏幕到这一格」的距离 —— 两者混在一起的话
 * 第一个 tab 会贴到 x=0。
 */
@Composable
private fun TabBar(selected: DetailTab, onSelect: (DetailTab) -> Unit) {
    val p = FitTheme.palette
    Column(Modifier.fillMaxWidth().background(p.surface)) {
        Row(Modifier.fillMaxWidth().padding(start = DetailMetrics.sidePad, bottom = 2.dp)) {
        DetailTab.entries.forEach { entry ->
            val active = entry == selected
            val indicator by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (active) 1f else 0f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.9f,
                    stiffness = 700f,
                ),
                label = "tabIndicator",
            )
            Column(
                Modifier
                    .clickable { onSelect(entry) }
                    .padding(end = 22.dp, top = 8.dp, bottom = 10.dp),
                // 指示条是固定 22dp 的短横，而 tab 文字（「适配产物」约 56dp）比它宽。
                // 不设这个对齐的话短横会贴文字左缘，看起来就是「没居中」。
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(entry.labelRes),
                    style = if (active) FitTypography.titleSmall else FitTypography.titleSmall.copy(fontWeight = FontWeight.Normal),
                    color = if (active) p.ink else p.ink4,
                )
                Spacer(Modifier.height(7.dp))
                Box(
                    Modifier
                        .height(1.5.dp)
                        .width(22.dp)
                        .clip(CircleShape)
                        .background(p.accent.copy(alpha = indicator)),
                )
            }
        }
        }
        HairLine()
    }
}

@Composable
private fun VerdictCard(repo: Repo) {
    val p = FitTheme.palette
    val tone = repo.verdict.tone
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(p.toneBg(tone))
            .border(BorderStroke(1.dp, p.toneLine(tone)), RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        val icon = when (repo.verdict) {
            Verdict.Ok -> FiCheck
            Verdict.Warn -> FiAlert
            Verdict.Bad, Verdict.Unknown -> FiAlert
        }
        FitBadge(tone, stringResource(verdictTitle(repo.verdict)), icon = icon)
        Spacer(Modifier.height(10.dp))
        Text(
            boldMarkup(explainText(FitEngine.verdictExplain(repo)), p.ink),
            style = FitTypography.bodyMedium,
            color = p.ink2,
        )
        repo.best?.let { best ->
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (best.abi != null) FactChip(stringResource(R.string.detail_fact_abi, best.abi))
                if (best.parsed) FactChip(stringResource(R.string.detail_fact_min_sdk, best.realMinSdk ?: 0))
                if (best.parsed) {
                    FactChip(
                        stringResource(
                            R.string.detail_fact_signer,
                            best.realSignerSha?.take(8) ?: stringResource(R.string.detail_value_none),
                        ),
                    )
                }
                if (!best.parsed) {
                    FactChip(
                        stringResource(
                            if (best.inferred) {
                                R.string.detail_fact_abi_inferred
                            } else {
                                R.string.detail_fact_unparsed
                            },
                        ),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FactChip(Env.formatSize(best.sizeMb))
                FactChip(stringResource(R.string.detail_fact_downloads, best.downloadCount))
                best.sha?.let { FactChip("sha ${it.take(8)}") }
            }
        }
    }
}

@StringRes
private fun verdictTitle(verdict: Verdict): Int = when (verdict) {
    Verdict.Ok -> R.string.verdict_title_ok
    Verdict.Warn -> R.string.verdict_title_warn
    Verdict.Bad -> R.string.verdict_title_bad
    Verdict.Unknown -> R.string.verdict_title_unknown
}

@Composable
private fun FactChip(text: String) {
    Text(
        text = text,
        style = MonoMeta,
        color = FitTheme.palette.ink2,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(FitTheme.palette.surface.copy(alpha = 0.72f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@Composable
private fun DeviceStateCard(repo: Repo) {
    val p = FitTheme.palette
    val state = repo.device
    if (state is DeviceState.NotInstalled) return
    val tone = when (state) {
        is DeviceState.SigningConflict -> FitTone.Bad
        is DeviceState.Latest -> FitTone.Ok
        else -> FitTone.Muted
    }
    val title = when (state) {
        is DeviceState.SigningConflict -> stringResource(R.string.device_conflict_title)
        is DeviceState.Upgrade -> stringResource(R.string.device_upgrade_title, state.from, state.to)
        is DeviceState.Latest -> stringResource(R.string.device_latest_title, explainText(state.version))
        is DeviceState.VersionUnknown -> stringResource(R.string.device_unknown_title)
        DeviceState.NotInstalled -> return
    }
    val detail: Explain = when (state) {
        is DeviceState.SigningConflict -> state.detail
        is DeviceState.Upgrade -> Explain(R.string.device_upgrade_detail)
        is DeviceState.Latest -> Explain(R.string.device_latest_detail)
        is DeviceState.VersionUnknown -> Explain(
            R.string.device_version_unknown_detail,
            listOf(
                state.installed,
                state.released?.takeIf { it.isNotBlank() } ?: stringResource(R.string.latest_release),
            ),
        )
        DeviceState.NotInstalled -> return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.toneBg(tone))
            .border(BorderStroke(1.dp, p.toneLine(tone)), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Icon(
            if (tone == FitTone.Bad) FiAlert else FiShield,
            contentDescription = null,
            tint = p.toneFg(tone),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = FitTypography.titleSmall, color = p.toneFg(tone))
            Spacer(Modifier.height(4.dp))
            Text(explainText(detail), style = FitTypography.bodySmall, color = p.ink3)
        }
    }
}


@Composable
private fun AssetRow(
    asset: Asset,
    primary: Boolean,
    state: DownloadState,
    /**
     * 这一行现在该显示什么：下载 / 安装 / 打开。
     *
     * 和 [state] 分开是刻意的：[state] 说的是「这一次传输进行到哪」，
     * [action] 说的是「这个包现在处在生命周期的哪一步」。前者会随进度变，
     * 后者只随「有没有下过 / 装没装」变 —— 混在一起就会出现进度条转完了
     * 图标还是下载箭头。
     */
    action: ApkAction,
    /** 开始 / 继续 / 暂停，随 [state] 变义，绝不因为在跑就锁死 */
    onClick: () -> Unit,
    /** 只在跑着或暂停着时出现 */
    onCancel: () -> Unit,
) {
    val p = FitTheme.palette
    val dimmed = asset.fit == FitState.Mismatch || asset.fit == FitState.Unknown
    val tone = when (asset.fit) {
        FitState.Match -> FitTone.Ok
        FitState.Degrade -> FitTone.Warn
        FitState.Mismatch -> FitTone.Muted
        FitState.Unknown -> FitTone.Muted
        FitState.Checksum -> FitTone.Muted
    }
    val stateLabel = when (asset.fit) {
        FitState.Match -> stringResource(R.string.detail_fit_match, asset.abi ?: "")
        FitState.Degrade -> stringResource(R.string.detail_fit_degrade)
        FitState.Mismatch -> stringResource(R.string.detail_fit_mismatch)
        FitState.Unknown -> stringResource(R.string.detail_fit_unknown)
        FitState.Checksum -> stringResource(R.string.detail_fit_checksum)
    }

    Column(Modifier.fillMaxWidth().then(if (dimmed) Modifier.alpha(0.58f) else Modifier)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (primary) p.toneBg(FitTone.Ok).copy(alpha = 0.5f) else Color.Transparent)
                .padding(horizontal = DetailMetrics.sidePad, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when (asset.fit) {
                            FitState.Match -> p.accent
                            FitState.Degrade -> p.toneFg(FitTone.Warn)
                            else -> Color.Transparent
                        },
                    )
                    .then(
                        if (asset.fit == FitState.Checksum) Modifier.border(1.dp, p.ink4, CircleShape) else Modifier,
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    asset.name,
                    style = MonoMeta.copy(fontSize = 12.sp),
                    color = p.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                MetaRow {
                    Text(stateLabel, style = FitTypography.labelSmall, color = p.toneFg(tone))
                    if (asset.inferred && asset.fit != FitState.Checksum) {
                        Box(Modifier.size(3.dp).clip(CircleShape).background(p.hairline))
                        Text(
                            stringResource(R.string.detail_inferred_short),
                            style = MonoMeta,
                            color = p.ink4,
                        )
                    }
                    if (asset.fit != FitState.Checksum && asset.sdkLabel != null) {
                        Box(Modifier.size(3.dp).clip(CircleShape).background(p.hairline))
                        Text(asset.sdkLabel, style = MonoMeta, color = p.ink4)
                    }
                }

                // 暂停说明放在 fit / sdk 这一行下面，而不是塞进上面那行 MetaRow：
                // 那行是产物自身的属性，混进一句会读状态的话会让它像是在讲这个包。
                // 已下多少从中枢读（见 DownloadState.Paused），不在本地留第二份。
                if (state is DownloadState.Paused) {
                    val hub = DownloadCenter.state.value as? DownloadCenter.Progress.Paused
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (hub != null && hub.assetName == asset.name) {
                            stringResource(
                                R.string.download_paused,
                                formatBytes(hub.saved),
                                formatBytes(hub.total),
                            )
                        } else {
                            // 中枢已经换人了（取消 / 别的产物接管）：宁可只说暂停，
                            // 也不报一个属于另一个下载的字节数
                            stringResource(R.string.download_paused_unknown)
                        },
                        style = MonoMeta,
                        color = p.ink4,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 解析出的清单信息全部来自系统 API
                if (asset.parsed) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(
                            R.string.detail_asset_manifest,
                            asset.realPackageName ?: "",
                            asset.realVersionName ?: "",
                            asset.realVersionCode ?: 0L,
                        ),
                        style = MonoMeta, color = p.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        stringResource(
                            R.string.detail_asset_manifest_abi,
                            asset.realMinSdk ?: 0,
                            asset.realAbis.joinToString().ifEmpty {
                                stringResource(R.string.detail_value_no_native)
                            },
                            asset.realSignerSha?.take(8) ?: stringResource(R.string.detail_value_none),
                        ),
                        style = MonoMeta, color = p.ink4, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (asset.sha != null) {
                    Spacer(Modifier.height(3.dp))
                    Text("sha256 ${asset.sha}", style = MonoMeta, color = p.ink4, maxLines = 1)
                }
                asset.reason?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(explainText(it), style = FitTypography.bodySmall, color = p.toneFg(FitTone.Bad))
                }
                asset.parseError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = FitTypography.bodySmall, color = p.toneFg(FitTone.Bad))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (asset.fit != FitState.Checksum) {
                    MonoText(Env.formatSize(asset.sizeMb), color = p.ink3)
                    Spacer(Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 以前这里还有一个「解析」按钮：点它会下载并读清单。
                    // 现在下载完成时就读了（见 DownloadService），真实事实直接显示在
                    // 下面的 manifest 块里，所以没有第二个入口要占位置。
                    DownloadButton(
                        state = state,
                        action = action,
                        primary = primary,
                        onClick = onClick,
                    )
                }
                // 取消只在「真的有一个下载在跑 / 停着」时才出现。
                // 没有东西可取消的时候摆一个灰色「取消」，用户点下去只会发现什么也没发生。
                if (state is DownloadState.Running || state is DownloadState.Paused) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.download_action_cancel),
                        style = FitTypography.labelSmall,
                        color = p.ink4,
                        modifier = Modifier.tap { onCancel() }.padding(horizontal = 4.dp),
                    )
                }
            }
        }
        HairLine(Modifier.padding(start = DetailMetrics.sidePad + 20.dp))
    }
}

/**
 * 产物行右边的圆形下载按钮。
 *
 * 刻意**不**在任何状态下禁用。上一版是 `tap(enabled = state !is Running)`，而 Running
 * 会因为等不到终局而永久留在本地（见 reconcileDownloads）—— 于是这一行看起来「点不动」，
 * 用户唯一的出路是杀进程。现在跑着 = 暂停、停着 = 继续，其余 = 重新开始，任何一态都有出路。
 *
 * 图标由 [action] 决定，而不是由 [state] 决定：[state] 答的是「这次传输到哪了」，
 * [action] 答的是「这个包现在该干什么」。之前这里只画一个对勾，于是下完的包
 * 看上去「完事了」，用户想装得自己另找入口 —— 现在是 下载 → 安装 → 打开 三态。
 *
 * 取消不塞在这个圆里：圆里只有「开始 / 暂停 / 继续」一条语义链，混进取消会让每种状态
 * 都要单独定义一次点击含义。它放在圆下面，只在有东西可取消时出现。
 */
@Composable
private fun DownloadButton(
    state: DownloadState,
    action: ApkAction,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val p = FitTheme.palette
    val size = if (primary) 44.dp else 38.dp
    val live = state is DownloadState.Running || state is DownloadState.Paused
    // 「下一步」才有实心底：NONE（不是安装包、装不了）画成实心主色会诱使人去按
    val solid = action == ApkAction.INSTALL || action == ApkAction.OPEN
    val bg by animateColorAsState(
        targetValue = when {
            solid -> p.accent
            primary -> p.ink
            else -> Color.Transparent
        },
        animationSpec = tween(FitMotion.FADE_MS),
        label = "dlBg",
    )
    val actionLabel = stringResource(
        when {
            state is DownloadState.Running -> R.string.download_action_pause
            state is DownloadState.Paused -> R.string.download_action_resume
            action == ApkAction.OPEN -> R.string.action_open
            action == ApkAction.INSTALL -> R.string.action_install
            action == ApkAction.NONE -> R.string.action_not_installable
            else -> R.string.detail_download_action
        },
    )
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .then(
                when {
                    solid || primary -> Modifier
                    // 跑着 / 停着时这一圈是描边，不是禁用：它按得动（暂停 / 继续），
                    // 只是含义随状态变。用发丝色画会和不可点的按钮长得一模一样。
                    live -> Modifier.border(
                        BorderStroke(1.5.dp, p.accent.copy(alpha = 0.45f)),
                        CircleShape,
                    )
                    // NONE 是个死路：文件下好了，但它不是能装的东西，没有下一步。
                    // 画成发丝色描边，和「可以按」的长得不一样
                    action == ApkAction.NONE -> Modifier.border(
                        BorderStroke(1.dp, p.hairline),
                        CircleShape,
                    )
                    else -> Modifier.border(BorderStroke(1.dp, p.hairline), CircleShape)
                },
            )
            // 唯一不可点的是 NONE：文件下好了，但它不是能装的东西，没有下一步。
            // 其余三态都有出路（下载 / 安装 / 打开），跑着和停着更是必须能按
            .tap(enabled = action != ApkAction.NONE) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        when {
            state is DownloadState.Running || state is DownloadState.Paused -> {
                val frac = when (state) {
                    is DownloadState.Running -> state.progress
                    is DownloadState.Paused -> state.progress
                    else -> 0f
                }
                // Running / Paused 共用同一个环：位置照实留着，用户才知道停在百分之几。
                // 区别只在颜色 —— 停住的用灰，一眼能看出它不再往前走了。
                val ring = if (state is DownloadState.Paused) p.ink4 else if (primary) p.surface else p.accent
                Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                    drawArc(
                        color = p.hairline,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = ring,
                        startAngle = -90f,
                        sweepAngle = 360f * frac,
                        useCenter = false,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            // 内容描述必须说清楚这一下按下去是干什么 —— 三个图标长得都像，
            // 读屏用户没有别的方式知道按下之后会发生什么
            else -> Icon(
                when (action) {
                    ApkAction.OPEN -> FiExternal
                    ApkAction.INSTALL -> FiPackage
                    // 下好了但不是安装包：画对勾表示「这件事到此为止」，
                    // 并且下面 tap(enabled=false) 让它真的按不动
                    ApkAction.NONE -> FiCheck
                    ApkAction.DOWNLOAD -> FiDownload
                },
                contentDescription = actionLabel,
                // 底色是实心的（主色）时图标必须用浅色，否则深色图标压在主色上看不清。
                // 之前只判 primary，于是非主行的「安装 / 打开」是深图标压主色底
                tint = if (solid || primary) p.surface else if (action == ApkAction.NONE) p.ink4 else p.ink2,
                modifier = Modifier.size(if (primary) 19.dp else 17.dp),
            )
        }
    }
}

@Composable
private fun MonoBlock(text: String) {
    Text(
        text = text.trimEnd(),
        style = MonoMeta.copy(fontSize = 12.sp, lineHeight = 19.sp),
        color = FitTheme.palette.ink2,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FitTheme.palette.ink)
            .padding(14.dp),
    )
}
