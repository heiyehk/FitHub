package com.heiyehk.fithub.ui.subscribe

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heiyehk.fithub.R
import com.heiyehk.fithub.data.Async
import com.heiyehk.fithub.data.Env
import com.heiyehk.fithub.data.RankBoard
import com.heiyehk.fithub.data.Repo
import com.heiyehk.fithub.data.Subscription
import com.heiyehk.fithub.ui.TabBarScrimHeight
import com.heiyehk.fithub.ui.components.AppTile
import com.heiyehk.fithub.ui.components.CircularProgress
import com.heiyehk.fithub.ui.components.FitBadge
import com.heiyehk.fithub.ui.components.GhostButton
import com.heiyehk.fithub.ui.components.HairLine
import com.heiyehk.fithub.ui.components.IconCircleButton
import com.heiyehk.fithub.ui.components.MetaRow
import com.heiyehk.fithub.ui.components.tap
import com.heiyehk.fithub.ui.icons.FiBell
import com.heiyehk.fithub.ui.icons.FiBookmark
import com.heiyehk.fithub.ui.icons.FiCheck
import com.heiyehk.fithub.ui.icons.FiRefresh
import com.heiyehk.fithub.ui.theme.Eyebrow
import com.heiyehk.fithub.ui.theme.FitTheme
import com.heiyehk.fithub.ui.theme.FitTone
import com.heiyehk.fithub.ui.theme.FitTypography
import com.heiyehk.fithub.ui.theme.MonoMeta
import kotlinx.coroutines.launch

/**
 * 订阅页。
 *
 * 第一个 tab 是本地订阅列表，其余是排行榜。**订阅 tab 打开不发任何请求**：
 * 数据来自 [Subscription] 快照，所以离线可读。
 *
 * 排行榜相反：**停在某个 tab 上才发那一个请求**，1 小时内滑走再滑回来命中缓存。
 * 触发条件用 [PagerState.settledPage] 而不是页面 composition —— pager 会预组合相邻页，
 * 照 composition 触发会在一次滑动里把 5 个榜全打一遍，而未登录 search 只有 10 次/分钟。
 *
 * 榜单**不显示产物状态**：GitHub 的仓库搜索没有 `has:release` 限定符（实测是空操作，
 * 见 [RankBoard]），真要筛就得每个仓库再打一次 /releases。所以行上给 star 与最近 push，
 * 订阅的价值是追踪更新 —— 对 AI 工具类仓库本来也不在下载。
 */
@Composable
fun SubscribeScreen(
    subs: List<Subscription>,
    /** 按 [RankBoard.id] 取。缺 key = 还没加载过 */
    ranks: Map<String, Async<List<Repo>>>,
    onRankLoad: (RankBoard) -> Unit,
    /** 用户在榜里点了「重试」。与 [onRankLoad] 分开，因为前者只在没加载过时触发 */
    onRankRetry: (RankBoard) -> Unit,
    /** 下拉刷新。必须作废缓存后重发，否则什么也不会发生 */
    onRankRefresh: (RankBoard) -> Unit,
    /** 正在下拉刷新的榜 id。与「加载中」分开：刷新时旧榜留在屏幕上，不该闪成骨架屏 */
    refreshingRanks: Set<String>,
    /**
     * 打开仓库详情。[placeholder] 是调用方手上已有的那份数据，有就传。
     *
     * 榜行**必须**传：详情面板靠 `placeholder` 才能立刻出标题 / star / 描述，
     * 拿不到就得起一个空面板等网络 —— 那正是「第一次打开卡一下」的成因。
     * 订阅列表那边手上只有 [Subscription]，传 null，走 byId 反查。
     */
    onRepoTap: (String, Repo?) -> Unit,
    onBrowse: () -> Unit,
    onRefreshOne: (String) -> Unit,
    onRefreshAll: () -> Unit,
    onToggleFollow: (Repo) -> Unit,
    isFollowing: (String) -> Boolean,
    refreshing: Set<String>,
    modifier: Modifier = Modifier,
    listState: LazyListState,
) {
    val p = FitTheme.palette
    val pagerState = rememberPagerState(pageCount = { RankBoard.entries.size + 1 })

    /**
     * 每个 tab 一份滚动位置。
     *
     * 共用一个 [LazyListState] 的话，从榜 A 切到榜 B 再切回来会停在 B 的位置 ——
     * 两个列表内容不同，滚动位置对不上号，看起来就是「随机跳到中间」。
     */
    val rankStates = remember { RankBoard.entries.associateWith { LazyListState() } }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page > 0) onRankLoad(RankBoard.entries[page - 1])
        }
    }

    Column(modifier.fillMaxSize().background(p.surface)) {
        Column(Modifier.statusBarsPadding()) {
            Text(
                stringResource(R.string.tab_subscribe),
                style = FitTypography.headlineSmall,
                color = p.ink,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp),
            )
            RankTabStrip(pagerState)
        }
        HairLine()

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            if (page == 0) {
                SubscriptionsPage(
                    subs = subs,
                    onRepoTap = onRepoTap,
                    onBrowse = onBrowse,
                    onRefreshOne = onRefreshOne,
                    onRefreshAll = onRefreshAll,
                    refreshing = refreshing,
                    listState = listState,
                )
            } else {
                val board = RankBoard.entries[page - 1]
                RankPage(
                    board = board,
                    state = ranks[board.id],
                    refreshing = board.id in refreshingRanks,
                    listState = rankStates.getValue(board),
                    onRetry = { onRankRetry(board) },
                    onRefresh = { onRankRefresh(board) },
                    onRepoTap = onRepoTap,
                    onToggleFollow = onToggleFollow,
                    isFollowing = isFollowing,
                )
            }
        }
    }
}

@Composable
private fun RankTabStrip(pagerState: PagerState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 指示条跟着选中项滚。用可滚动的 Row + manual scroll 也能做，但要自己算
    // 每个 tab 的 offset；LazyRow 直接给 animateScrollToItem，顺带把超宽屏也一起解决了。
    LaunchedEffect(pagerState.currentPage) {
        listState.animateScrollToItem(pagerState.currentPage)
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        val count = RankBoard.entries.size + 1
        items(count, key = { it }) { page ->
            val label = if (page == 0) {
                stringResource(R.string.rank_tab_subs)
            } else {
                stringResource(RankBoard.entries[page - 1].labelRes)
            }
            TabItem(label, pagerState.currentPage == page) {
                scope.launch { pagerState.animateScrollToPage(page) }
            }
        }
    }
}

@Composable
private fun TabItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = FitTheme.palette
    Column(
        Modifier
            .tap { onClick() }
            .padding(top = 8.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = if (selected) FitTypography.titleSmall else FitTypography.bodyMedium,
            color = if (selected) p.ink else p.ink4,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .width(if (selected) 16.dp else 0.dp)
                .height(2.dp)
                .background(if (selected) p.ink else Color.Transparent, RoundedCornerShape(1.dp))
        )
    }
}

@Composable
private fun SubscriptionsPage(
    subs: List<Subscription>,
    /**
     * 打开仓库详情。[placeholder] 是调用方手上已有的那份数据，有就传。
     *
     * 榜行**必须**传：详情面板靠 `placeholder` 才能立刻出标题 / star / 描述，
     * 拿不到就得起一个空面板等网络 —— 那正是「第一次打开卡一下」的成因。
     * 订阅列表那边手上只有 [Subscription]，传 null，走 byId 反查。
     */
    onRepoTap: (String, Repo?) -> Unit,
    onBrowse: () -> Unit,
    onRefreshOne: (String) -> Unit,
    onRefreshAll: () -> Unit,
    refreshing: Set<String>,
    listState: LazyListState,
) {
    val p = FitTheme.palette
    val refreshingAll = refreshing.isNotEmpty() && refreshing.size == subs.size

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(p.surface),
        state = listState,
        // 底部留出 tab bar 再加 28.dp，保证最后一项不被悬浮条盖住
        contentPadding = PaddingValues(bottom = TabBarScrimHeight + 28.dp),
    ) {
        item(key = "top") {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 标题移到了固定顶栏里，所以这一行只剩刷新按钮 —— 别给它单独占一行，
                    // 否则「N 个」上面会多出一条空带，看起来像少了点什么。
                    Text(
                        if (subs.isEmpty()) stringResource(R.string.subscribe_count_zero)
                        else stringResource(R.string.subscribe_quota_note, subs.size),
                        style = FitTypography.bodySmall,
                        color = p.ink4,
                        modifier = Modifier.weight(1f),
                    )
                    if (subs.isNotEmpty()) {
                        if (refreshingAll) {
                            CircularProgress(0f, size = 18.dp, color = p.ink4, indeterminate = true)
                        } else {
                            IconCircleButton(FiRefresh, stringResource(R.string.subscribe_refresh_all), onRefreshAll)
                        }
                    }
                }
                ListDivider()
            }
        }

        if (subs.isEmpty()) {
            item(key = "empty") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(p.washDeep),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(FiBell, null, tint = p.ink4, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(stringResource(R.string.subscribe_empty_title), style = FitTypography.titleSmall, color = p.ink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.subscribe_empty_1) + "\n" +
                            stringResource(R.string.subscribe_empty_2),
                        style = FitTypography.bodyMedium,
                        color = p.ink3,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(22.dp))
                    GhostButton(stringResource(R.string.subscribe_empty_cta), onBrowse)
                }
            }
        }

        items(subs, key = { "s-${it.fullName}" }) { sub ->
            SubscriptionRow(
                sub = sub,
                refreshing = sub.fullName in refreshing,
                onTap = { onRepoTap(sub.fullName, null) },
                onRefresh = { onRefreshOne(sub.fullName) },
            )
        }

        if (subs.isNotEmpty()) {
            item(key = "foot") {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    /**
                     * 这里**没有**分割线，是故意的。
                     *
                     * 最后一个订阅行的末尾已经画了一条 [ListDivider]，两条 1.dp 中间
                     * 没有间隔，叠起来就是 2.dp —— 看着像一条粗线。
                     * （这个叠线是统一缩进之后才显形的：改之前页脚那条在 20.dp、
                     * 行那条在 79.dp，横错开所以看不出来；一对齐就糊成一条。）
                     *
                     * 列表为空时同样不需要：上面是「这个榜是空的」文案，不是行。
                     */
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.subscribe_about), style = Eyebrow, color = p.ink4)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.subscribe_about_1) + "\n" +
                            stringResource(R.string.subscribe_about_2),
                        style = FitTypography.bodySmall,
                        color = p.ink4,
                    )
                }
            }
        }
    }
}

/** [PullToRefreshBox] 仍是实验 API，与首页 / 本机 / 详情页的处理一致 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RankPage(
    board: RankBoard,
    state: Async<List<Repo>>?,
    refreshing: Boolean,
    listState: LazyListState,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    /**
     * 打开仓库详情。[placeholder] 是调用方手上已有的那份数据，有就传。
     *
     * 榜行**必须**传：详情面板靠 `placeholder` 才能立刻出标题 / star / 描述，
     * 拿不到就得起一个空面板等网络 —— 那正是「第一次打开卡一下」的成因。
     * 订阅列表那边手上只有 [Subscription]，传 null，走 byId 反查。
     */
    onRepoTap: (String, Repo?) -> Unit,
    onToggleFollow: (Repo) -> Unit,
    isFollowing: (String) -> Boolean,
) {
    val p = FitTheme.palette

    // 还没加载过（用户没停在这个 tab 上）与正在加载，两种都用同一个占位：
    // 未加载的 tab 不该显示成「加载失败」
    if (state == null || state is Async.Loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgress(0f, size = 22.dp, color = p.ink4, indeterminate = true)
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.rank_loading),
                    style = FitTypography.bodySmall,
                    color = p.ink4,
                )
            }
        }
        return
    }

    if (state is Async.Err) {
        Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.rank_failed, (state as Async.Err).message),
                    style = FitTypography.bodyMedium,
                    color = p.ink3,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                GhostButton(stringResource(R.string.rank_retry), onRetry)
            }
        }
        return
    }

    // 上面已经排掉 null / Loading / Err，剩下的只可能是 Ok。
    // 用 as? 而不是 as：泛型 sealed 子类的显式 cast 必须写全类型参数，
    // 写成裸 `as Async.Ok` 编译不过；而这里再兜一层也不会掩盖任何分支。
    val ok = state as? Async.Ok<List<Repo>> ?: return
    val repos = ok.value

    // 下拉刷新。和首页 / 本机 / 详情同一套手势，用户不用学第二遍。
    // 只包这一支：加载中与错误态是居中卡片，没有列表可拉。
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().background(p.surface),
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = TabBarScrimHeight + 28.dp),
    ) {
        item(key = "head") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.rank_count, repos.size), style = MonoMeta, color = p.ink4)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(board.noteRes), style = FitTypography.bodySmall, color = p.ink4)
                }
                // 读的是缓存就写明年龄，别把旧数据当实时展示
                if (ok.fromCache && ok.ageMs > 0) {
                    Spacer(Modifier.height(8.dp))
                    FitBadge(FitTone.Prerelease, stringResource(R.string.rank_cached, ageTextOf(ok.ageMs)))
                }
            }
            ListDivider()
        }

        if (repos.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.rank_empty),
                    style = FitTypography.bodyMedium,
                    color = p.ink4,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 48.dp),
                )
            }
        }

        items(repos, key = { "r-${it.id}" }) { repo ->
            RankRow(
                repo = repo,
                following = isFollowing(repo.id),
                onTap = { onRepoTap(repo.id, repo) },
                onToggleFollow = { onToggleFollow(repo) },
            )
        }

        /**
         * 配额与过滤口径说明放**列表末尾**，不是顶部。
         *
         * 放顶部时是四行灰色小字，占掉首屏约四分之一，第一条数据要划过一次才看得见 ——
         * 而这两句（"首次停在这个 tab 发 1 次请求"、"不做产物过滤"）是解释，不是内容。
         * 放到底部既保住了「不确定性被显式表达」，又不挡路。
         */
        item(key = "foot") {
            // 同样不画分割线：最后一个排行行的末尾已经有一条，两条会叠成 2.dp
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.rank_cost_note), style = Eyebrow, color = p.ink4)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.rank_no_release_note), style = FitTypography.bodySmall, color = p.ink4)
            }
        }
    }
    }
}

@Composable
private fun RankRow(
    repo: Repo,
    following: Boolean,
    onTap: () -> Unit,
    onToggleFollow: () -> Unit,
) {
    val p = FitTheme.palette
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .tap { onTap() }
                .padding(start = 20.dp, end = 12.dp, top = 13.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTile(repo.name.take(2).uppercase(), repo.langColor, 0xFF1A1C1A)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                MetaRow {
                    Text(repo.name, style = FitTypography.titleSmall, color = p.ink, maxLines = 1)
                    if (repo.stars > 0) {
                        Spacer(Modifier.width(7.dp))
                        Text(Env.formatStars(repo.stars), style = MonoMeta, color = p.ink4)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    repo.desc,
                    style = FitTypography.bodySmall,
                    color = p.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (repo.date.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.rank_pushed_at, repo.date),
                        style = MonoMeta,
                        color = p.ink4,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            IconCircleButton(
                if (following) FiCheck else FiBookmark,
                stringResource(if (following) R.string.rank_unfollow else R.string.rank_follow),
                onToggleFollow,
            )
        }
        ListDivider()
    }
}

@Composable
private fun SubscriptionRow(
    sub: Subscription,
    refreshing: Boolean,
    onTap: () -> Unit,
    onRefresh: () -> Unit,
) {
    val p = FitTheme.palette
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .tap { onTap() }
                .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTile(
                sub.displayName.take(2).uppercase(),
                com.heiyehk.fithub.data.remote.GitHubMapper.langColorOf(sub.lang),
                0xFF1A1C1A,
            )
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                MetaRow {
                    Text(sub.displayName, style = FitTypography.titleSmall, color = p.ink, maxLines = 1)
                    if (sub.stars > 0) {
                        Spacer(Modifier.width(7.dp))
                        Text(Env.formatStars(sub.stars), style = MonoMeta, color = p.ink4)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    sub.desc,
                    style = FitTypography.bodySmall,
                    color = p.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 快照过期要写明，否则等于把旧数据当实时展示
                    if (sub.isStale) {
                        FitBadge(FitTone.Prerelease, stringResource(R.string.subscribe_meta_age, agoTextOf(sub.metaFetchedAt)))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        if (sub.latestTag.isBlank()) stringResource(R.string.subscribe_no_tag) else sub.latestTag,
                        style = MonoMeta,
                        color = p.ink4,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            if (refreshing) {
                CircularProgress(0f, size = 16.dp, color = p.ink4, indeterminate = true)
            } else {
                IconCircleButton(FiRefresh, stringResource(R.string.subscribe_refresh_one), onRefresh)
            }
        }
        ListDivider()
    }
}

/**
 * 绝对时刻 → 「N 小时前」。
 *
 * 只给**时刻戳**用（[Subscription.metaFetchedAt]）。
 */
@Composable
private fun agoTextOf(epochMs: Long): String =
    if (epochMs <= 0L) stringResource(R.string.subscribe_age_unknown)
    else ageTextOf(System.currentTimeMillis() - epochMs)

/**
 * 时长 → 「N 小时前」。
 *
 * **给「已经过去多久」用的**（`Async.Ok.ageMs`）。它是个 duration，不是时刻戳 ——
 * 两种语义混进一个函数时，缓存年龄会变成「20718 天前」这种一眼假的数字，
 * 而它只是长得不对、不报错，正是最难被发现的那一类。
 */
@Composable
private fun ageTextOf(ageMs: Long): String {
    if (ageMs < 0) return stringResource(R.string.subscribe_age_unknown)
    val hours = ageMs / 3_600_000
    return when {
        hours < 1 -> stringResource(R.string.subscribe_age_under_hour)
        hours < 24 -> stringResource(R.string.subscribe_age_hours, hours.toInt())
        else -> stringResource(R.string.subscribe_age_days, (hours / 24).toInt())
    }
}

private val RankBoard.labelRes: Int
    get() = when (this) {
        RankBoard.Skills -> R.string.rank_tab_skills
        RankBoard.Harness -> R.string.rank_tab_harness
        RankBoard.Agents -> R.string.rank_tab_agents
        RankBoard.Mcp -> R.string.rank_tab_mcp
    }

private val RankBoard.noteRes: Int
    get() = when (this) {
        RankBoard.Skills -> R.string.rank_note_skills
        RankBoard.Harness -> R.string.rank_note_harness
        RankBoard.Agents -> R.string.rank_note_agents
        RankBoard.Mcp -> R.string.rank_note_mcp
    }

/**
 * 列表内分割线的左边距 = 行边距 20 + 头像块 + 间距 13，对齐到文字列起点。
 *
 * 抽成常量是因为这一页有 6 处分割线，而它们曾经是 **0 / 20 / 79 三种**左边距：
 * 行间那条是 79，页脚那条写在 `padding(horizontal = 20.dp)` 的 Column 里就成了 20，
 * 页头那条又是满宽的 0。三条线并排出现、起点各不相同，看上去像渲染错位。
 *
 * 满宽的那条（tab 条正下方）**刻意保持满宽** —— 它分的是「顶栏 / 列表」两个大区块，
 * 不是列表内部，用同一个缩进反而会和上面的 tab 底边撞在一起。
 */
private val RowDividerInset = 79.dp

/** 列表内部统一用这一条：[RowDividerInset] 的唯一出口，别再手写数字 */
@Composable
private fun ListDivider() = HairLine(Modifier.padding(start = RowDividerInset))
