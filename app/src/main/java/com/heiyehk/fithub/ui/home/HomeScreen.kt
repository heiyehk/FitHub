package com.heiyehk.fithub.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heiyehk.fithub.data.DeviceState
import com.heiyehk.fithub.data.Async
import androidx.compose.ui.res.stringResource
import com.heiyehk.fithub.R
import com.heiyehk.fithub.ui.TabBarScrimHeight
import com.heiyehk.fithub.ui.ageText
import com.heiyehk.fithub.ui.explainText
import com.heiyehk.fithub.data.HomeSection
import com.heiyehk.fithub.data.Repo
import com.heiyehk.fithub.data.Env
import com.heiyehk.fithub.data.SigningRelation
import com.heiyehk.fithub.data.Verdict
import com.heiyehk.fithub.ui.components.AppTile
import com.heiyehk.fithub.ui.components.FitBadge
import com.heiyehk.fithub.ui.components.GhostButton
import com.heiyehk.fithub.ui.components.HairLine
import com.heiyehk.fithub.ui.components.IconCircleButton
import com.heiyehk.fithub.ui.components.LangBar
import com.heiyehk.fithub.ui.components.LangDot
import com.heiyehk.fithub.ui.components.MetaRow
import com.heiyehk.fithub.ui.components.MetaText
import com.heiyehk.fithub.ui.components.MonoText
import com.heiyehk.fithub.ui.components.OutlineBadge
import com.heiyehk.fithub.ui.components.RadarGraphic
import com.heiyehk.fithub.ui.components.SectionHead
import com.heiyehk.fithub.ui.components.Stars
import com.heiyehk.fithub.ui.components.StaggeredItem
import com.heiyehk.fithub.ui.components.StaleNotice
import com.heiyehk.fithub.ui.components.stalestOf
import com.heiyehk.fithub.ui.components.TopicChip
import com.heiyehk.fithub.ui.components.tap
import com.heiyehk.fithub.ui.icons.FiAlert
import com.heiyehk.fithub.ui.icons.FiCheck
import com.heiyehk.fithub.ui.icons.FiChevron
import com.heiyehk.fithub.ui.icons.FiDevice
import com.heiyehk.fithub.ui.icons.FiPackage
import com.heiyehk.fithub.ui.icons.FiSearch
import com.heiyehk.fithub.ui.icons.FiSliders
import com.heiyehk.fithub.ui.icons.FiSpark
import com.heiyehk.fithub.ui.theme.Eyebrow
import com.heiyehk.fithub.ui.theme.FitTheme
import com.heiyehk.fithub.ui.theme.FitTone
import com.heiyehk.fithub.ui.theme.FitTypography
import com.heiyehk.fithub.ui.theme.Fonts
import com.heiyehk.fithub.ui.theme.MonoMeta
import kotlinx.coroutines.launch

internal val ScreenPad = 20.dp

/**
 * 「最近更新」标题的 item key。
 *
 * 跳转按 key 而不是按下标 —— 板块可增删启停之后，前面每个 section 贡献几个
 * item 就不再固定，写死的 5 会指向错的位置。
 */
private const val KEY_RECENT_HEAD = "recent-head"

/** 语言筛选的「全部」选项。SectionRenderer 也要用它 */
internal const val ALL_LANGS = "全部"

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    langFilter: String,
    onLangFilter: (String) -> Unit,
    stars: Async<List<Repo>>,
    updated: Async<List<Repo>>,
    onRepoTap: (Repo) -> Unit,
    onScanTap: () -> Unit,
    onRateLimitTap: () -> Unit,
    onSearchTap: () -> Unit,
    onTileBounds: (String, Rect) -> Unit,
    onRetry: () -> Unit,
    /** GitHub API 配额余量 / 总额。顶栏那个胶囊显示的是真值，不是写死的字面量 */
    rateRemaining: Int,
    rateLimit: Int,
    /** 可升级数量，由宿主按已确认的绑定算好后传入 */
    upgradable: Int = 0,
    /** 精选区：按确切全名拉取 */
    featured: Async<List<Repo>> = Async.Loading,
    /** 下拉刷新：为 null 时不提供该手势 */
    onRefresh: (() -> Unit)? = null,
    /** 打开首页板块管理页 */
    onSectionTap: () -> Unit = {},
    /** 板块配置。停用的板块既不占位也不发请求 */
    sections: List<HomeSection> = HomeSection.defaults(),
    /** 按板块 id 查它的加载态。内置三段直接取现有 state，topic 段走 map */
    topicStates: Map<String, Async<List<Repo>>> = emptyMap(),
    /** 用户点了某个 topic 板块的查询按钮 */
    onSectionLoad: (HomeSection) -> Unit = {},
    /** 刷新进行中 */
    refreshing: Boolean = false,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val p = FitTheme.palette
    val scope = rememberCoroutineScope()

    fun pick(state: Async<List<Repo>>): List<Repo> =
        (state as? Async.Ok)?.value.orEmpty().filter {
            langFilter == ALL_LANGS || it.lang.equals(langFilter, true)
        }

    val starList = pick(stars)
    val updatedList = pick(updated)

    /** 板块 id → 它的加载态。内置三段用现有 state，topic 段查 map */
    fun stateOf(s: HomeSection): Async<List<Repo>>? = when (s.kind) {
        HomeSection.Kind.Stars -> stars
        HomeSection.Kind.Updated -> updated
        HomeSection.Kind.Featured -> featured
        HomeSection.Kind.Topic -> topicStates[s.id]
    }
    val langs = remember(stars, updated) {
        (starList.map { it.lang } + updatedList.map { it.lang })
            .filter { it != "—" }
            .groupingBy { it }.eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(7)
            .toList()
    }

    // 首页由两段请求拼成，任一段命中缓存就要提示，且取更旧的那个时间
    val cachedAge = stalestOf(stars, updated)

    // 状态栏 inset 放在 PullToRefreshBox 上，而不是列表的 contentPadding 上。
    //
    // 这里踩过一个坑：**stickyHeader 钉的是「视口」上沿，contentPadding 只移动内容起点，
    // 不影响吸顶位置**。配合 enableEdgeToEdge()，视口上沿就是屏幕顶部，所以把 inset
    // 写进 contentPadding 只能把顶栏推下来，筛选栏一吸顶照样钻进状态栏底下 ——
    // 而且只有滚动之后才露馅，不滚根本看不出来。
    //
    // background 排在 statusBarsPadding **之前**：这样底色仍铺满整屏（含状态栏区域），
    // 只有内容被内缩。写反的话状态栏后面会露出窗口底色。
    PullToRefreshBox(
        isRefreshing = onRefresh != null && refreshing,
        onRefresh = { onRefresh?.invoke() },
        modifier = modifier.fillMaxSize().background(p.surface).statusBarsPadding(),
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = TabBarScrimHeight + 40.dp),
    ) {
        item(key = "topbar") {
            HomeTopBar(onSearchTap, onRateLimitTap, onSectionTap, rateRemaining, rateLimit)
        }

        item(key = "hero") {
            Hero(
                upgradable = upgradable,
                onScan = onScanTap,
                onUpgrade = { scope.launch { listState.scrollToKey(KEY_RECENT_HEAD) } },
            )
        }

        if (cachedAge != null) {
            item(key = "stale") {
                StaleNotice(cachedAge, Modifier.padding(horizontal = ScreenPad, vertical = 4.dp))
            }
        }

        stickyHeader(key = "smartbar") {
            LangFilterBar(
                selected = langFilter,
                langs = langs,
                onSelect = onLangFilter,
            )
        }

        // 板块按配置逐个渲染。停用的不占位，也不发请求
        val enabled = sections.filter { it.enabled }
        enabled.forEachIndexed { i, section ->
            homeSection(
                section = section,
                index = i,
                state = stateOf(section),
                langFilter = langFilter,
                onRepoTap = onRepoTap,
                onTileBounds = onTileBounds,
                onLoad = { onSectionLoad(section) },
                onRetry = { onRetry() },
            )
        }

        item(key = "foot") { HomeFooter() }
    }
    }
}


// 顶栏

/**
 * 右侧胶囊是 GitHub API 的配额余量（未登录 60 次/小时），点一下看说明。
 */
@Composable
private fun HomeTopBar(
    onSearchTap: () -> Unit,
    onRateLimitTap: () -> Unit,
    onSectionTap: () -> Unit,
    rateRemaining: Int,
    rateLimit: Int,
) {
    val p = FitTheme.palette
    // 余量低于两成时改用警示色，让「快用完了」在扫一眼时就能看出来
    val low = rateLimit > 0 && rateRemaining * 5 < rateLimit
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 顶栏不放软件名，这块空间给搜索入口
            Row(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(CircleShape)
                    .background(p.wash)
                    .border(1.dp, p.hairline, CircleShape)
                    .tap { onSearchTap() }
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(FiSearch, null, tint = p.ink4, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(9.dp))
                Text(
                    stringResource(R.string.search_placeholder),
                    style = FitTypography.bodyMedium,
                    color = p.ink4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.width(9.dp))
            IconCircleButton(FiSliders, stringResource(R.string.home_sections_title), onClick = onSectionTap)
            Spacer(Modifier.width(7.dp))
            Row(
                Modifier
                    .clip(CircleShape)
                    .border(1.dp, p.hairline, CircleShape)
                    .tap { onRateLimitTap() }
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$rateRemaining",
                    style = MonoMeta.copy(
                        color = if (low) p.toneFg(FitTone.Warn) else p.ink,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text("/$rateLimit", style = MonoMeta, color = p.ink4)
            }
        }
        HairLine()
    }
}

// Hero

@Composable
private fun Hero(upgradable: Int, onScan: () -> Unit, onUpgrade: () -> Unit) {
    val p = FitTheme.palette
    val device = Env.device
    Column(Modifier.padding(horizontal = ScreenPad, vertical = 30.dp)) {
        MetaRow {
            Text(Env.today.toString(), style = Eyebrow, color = p.ink4)
            Text("/", style = Eyebrow, color = p.hairline)
            Text(
                // model 名里可能已含架构，重复追加会显示两遍
                if (device.name.contains(device.abi)) device.name else "${device.name} · ${device.abi}",
                style = Eyebrow,
                color = p.accent,
                modifier = Modifier.padding(start = 7.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        // buildAnnotatedString 的 lambda 不是 @Composable，取值要提到外面
        val headPlain = stringResource(R.string.home_hero_head)
        val headAccent = stringResource(R.string.home_hero_head_accent)
        Text(
            text = buildAnnotatedString {
                append(headPlain)
                withStyle(SpanStyle(color = FitTheme.palette.accent)) { append(headAccent) }
            },
            style = FitTypography.displaySmall,
            color = FitTheme.palette.ink,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.home_hero_desc),
            style = FitTypography.bodyMedium,
            color = FitTheme.palette.ink3,
        )

        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadarGraphic(size = 96.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                StatCell(stringResource(R.string.home_stat_abi), device.abi)
                HairLine()
                StatCell(stringResource(R.string.home_stat_sdk), "API ${device.sdk}")
                HairLine()
                StatCell(
                    stringResource(R.string.home_stat_linkable),
                    stringResource(R.string.profile_count_apps, device.installed.size),
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton(
                text = stringResource(R.string.home_scan_apps),
                onClick = onScan,
                icon = FiDevice,
                modifier = Modifier.weight(1f),
            )
            GhostButton(
                text = stringResource(R.string.home_count_upgradable, upgradable),
                onClick = onUpgrade,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    val p = FitTheme.palette
    Column(Modifier.padding(vertical = 9.dp)) {
        Text(label, style = FitTypography.labelSmall, color = p.ink4)
        Spacer(Modifier.height(2.dp))
        Text(value, style = FitTypography.titleSmall.copy(fontFamily = Fonts.Mono), color = p.ink)
    }
}

// 语言筛选

@Composable
private fun LangFilterBar(selected: String, langs: List<String>, onSelect: (String) -> Unit) {
    val p = FitTheme.palette
    Column(Modifier.background(p.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                // start 用 ScreenPad（20dp）而不是 0：这行是吸顶的，横向滚动的内容从它下面
                // 掠过，「筛选」标签直接贴左边缘会跟下面板块的内容左边界对不齐。
                // end 不用给 —— 里面的 LazyRow 用 weight(1f) 吃剩余宽度，
                // 右侧留白由它自己的 contentPadding 负责（滚动到底时也有 20dp）。
                .padding(start = ScreenPad, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(FiSpark, contentDescription = null, tint = p.accent, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.home_filter),
                style = FitTypography.labelSmall.copy(letterSpacing = 0.8.sp),
                color = p.accent,
            )
            Spacer(Modifier.width(12.dp))
            LazyRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = ScreenPad),
            ) {
                item {
                    LangChip(
                        // 筛选值仍是 ALL_LANGS 这个哨兵常量，只有显示的文字走资源
                        text = stringResource(R.string.home_filter_all),
                        active = selected == ALL_LANGS,
                        onClick = { onSelect(ALL_LANGS) },
                    )
                }
                items(langs, key = { it }) { lang ->
                    LangChip(lang, active = lang == selected) { onSelect(lang) }
                }
            }
        }
        HairLine()
    }
}

@Composable
private fun LangChip(text: String, active: Boolean, onClick: () -> Unit) {
    val p = FitTheme.palette
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (active) p.ink else p.surface)
            .border(BorderStroke(1.dp, if (active) p.ink else p.hairline), CircleShape)
            .tap { onClick() }
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = FitTypography.labelLarge,
            color = if (active) p.surface else p.ink2,
            maxLines = 1,
        )
    }
}

// 加载 / 失败 / 空

@Composable
internal fun SkeletonRail() {
    val p = FitTheme.palette
    LazyRow(
        contentPadding = PaddingValues(horizontal = ScreenPad),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(4) {
            Box(
                Modifier
                    .width(174.dp)
                    .height(190.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(p.washDeep),
            )
        }
    }
}

@Composable
internal fun SkeletonRows(count: Int = 4) {
    val p = FitTheme.palette
    Column(Modifier.padding(horizontal = ScreenPad)) {
        repeat(count) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.washDeep),
            )
        }
    }
}

@Composable
internal fun ErrorBlock(message: String, onRetry: () -> Unit) {
    val p = FitTheme.palette
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPad, vertical = 22.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(p.wash)
            .padding(18.dp),
    ) {
        Text(message, style = FitTypography.bodyMedium, color = p.ink2)
        Spacer(Modifier.height(12.dp))
        GhostButton(stringResource(R.string.home_retry), onRetry)
    }
}

@Composable
internal fun EmptyBlock(title: String, hint: String) {
    val p = FitTheme.palette
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPad, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = FitTypography.titleMedium, color = p.ink2)
        Spacer(Modifier.height(6.dp))
        Text(hint, style = FitTypography.bodySmall, color = p.ink4)
    }
}

@Composable
internal fun TrendCard(repo: Repo, rank: Int, onTileBounds: (String, Rect) -> Unit, onClick: () -> Unit) {
    val p = FitTheme.palette
    Column(
        Modifier
            .width(174.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(p.surface)
            .border(BorderStroke(1.dp, p.hairline), RoundedCornerShape(18.dp))
            .tap { onClick() }
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            AppTile(
                repo.monogram,
                repo.tileBg,
                repo.tileFg,
                modifier = Modifier.onGloballyPositioned { onTileBounds(repo.id, it.boundsInRoot()) },
                size = 42.dp,
                corner = 13.dp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                (rank + 1).toString().padStart(2, '0'),
                style = MonoMeta.copy(fontSize = 13.sp),
                color = p.ink4,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(repo.name, style = FitTypography.titleSmall, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(7.dp))
        Text(
            repo.desc,
            style = FitTypography.bodySmall,
            color = p.ink3,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(34.dp),
        )
        Spacer(Modifier.height(10.dp))
        HairLine(color = p.hairlineSoft)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetaRow(Modifier.weight(1f)) {
                LangDot(repo.langColor)
                // 没查到 release 时 version 是占位符「—」，画出来像是有个叫「—」的版本。
                // 热门里的 flutter / scrcpy 都是这种：宁可只留语言点，也不要那根杠。
                if (repo.hasVersion) MetaText(repo.version)
            }
            Stars(Env.formatStars(repo.stars))
        }
    }
}

// 仓库行

@Composable
internal fun RepoRow(repo: Repo, onTileBounds: (String, Rect) -> Unit, onClick: () -> Unit) {
    val p = FitTheme.palette
    val upgradable = repo.device is DeviceState.Upgrade
    val conflict = repo.device is DeviceState.SigningConflict
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .tap { onClick() }
                .padding(horizontal = ScreenPad, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTile(
                repo.monogram,
                repo.tileBg,
                repo.tileFg,
                modifier = Modifier.onGloballyPositioned { onTileBounds(repo.id, it.boundsInRoot()) },
            )
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                MetaRow {
                    Text(
                        repo.name,
                        style = FitTypography.titleSmall,
                        color = p.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (upgradable) {
                        Spacer(Modifier.width(7.dp))
                        FitBadge(FitTone.Ok, stringResource(R.string.device_upgradable), icon = FiCheck)
                    }
                    if (conflict) {
                        Spacer(Modifier.width(7.dp))
                        FitBadge(FitTone.Bad, stringResource(R.string.device_conflict), icon = FiAlert)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    repo.desc,
                    style = FitTypography.bodySmall,
                    color = p.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                // 同上：占位符「—」不是版本号，不画。
                // 这一列只有徽标时不必留 4.dp 的行距去撑一个空位。
                if (repo.hasVersion) {
                    MonoText(repo.version)
                    Spacer(Modifier.height(4.dp))
                }
                FitBadge(repo.verdict.tone, stringResource(repo.verdict.labelRes))
            }
        }
        HairLine(Modifier.padding(start = ScreenPad + 59.dp))
    }
}

// 精选

@Composable
internal fun FeaturedBento(
    big: Repo?,
    related: Repo?,
    desktop: Repo?,
    onTileBounds: (String, Rect) -> Unit,
    onRepoTap: (Repo) -> Unit,
) {
    Column(
        Modifier
            .padding(horizontal = ScreenPad)
            .clip(RoundedCornerShape(18.dp))
            .background(FitTheme.palette.wash)
            .border(BorderStroke(1.dp, FitTheme.palette.hairline), RoundedCornerShape(18.dp)),
    ) {
        if (big != null) {
            Column(Modifier.padding(18.dp).tap { onRepoTap(big) }) {
                Text(stringResource(R.string.home_featured_pick), style = Eyebrow, color = FitTheme.palette.accent)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppTile(
                        big.monogram,
                        big.tileBg,
                        big.tileFg,
                        modifier = Modifier.onGloballyPositioned { onTileBounds(big.id, it.boundsInRoot()) },
                        size = 48.dp,
                        corner = 15.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(big.name, style = FitTypography.titleMedium, color = FitTheme.palette.ink)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${big.owner} · ${ageText(big.date)}",
                            style = MonoMeta,
                            color = FitTheme.palette.ink4,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(big.desc, style = FitTypography.bodyMedium, color = FitTheme.palette.ink3, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(14.dp))
                LangBar(big.langShare, big.langColor)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    big.topics.take(3).forEach { TopicChip(it) }
                }
                Spacer(Modifier.height(14.dp))
                MetaRow {
                    FitBadge(big.verdict.tone, stringResource(big.verdict.labelRes))
                    Stars(Env.formatStars(big.stars))
                }
            }
            HairLine()
        }
        if (related != null) {
            Column(Modifier.padding(18.dp).tap { onRepoTap(related) }) {
                Text(stringResource(R.string.home_featured_related), style = Eyebrow, color = FitTheme.palette.accent)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppTile(related.monogram, related.tileBg, related.tileFg, size = 42.dp, corner = 13.dp)
                    Spacer(Modifier.width(11.dp))
                    Text(related.name, style = FitTypography.titleSmall, color = FitTheme.palette.ink, modifier = Modifier.weight(1f), maxLines = 1)
                    Icon(FiChevron, null, tint = FitTheme.palette.ink4, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.height(10.dp))
                val state = related.device
                when (state) {
                    is DeviceState.SigningConflict -> FitBadge(
                        FitTone.Bad,
                        stringResource(R.string.home_featured_conflict, state.installed),
                        icon = FiAlert,
                    )
                    is DeviceState.Upgrade -> FitBadge(
                        FitTone.Ok,
                        stringResource(R.string.home_featured_upgrade, state.from, state.to),
                        icon = FiCheck,
                    )
                    is DeviceState.Latest -> FitBadge(
                        FitTone.Muted,
                        stringResource(R.string.device_badge_latest, explainText(state.version)),
                    )
                    is DeviceState.VersionUnknown -> FitBadge(
                        FitTone.Muted,
                        stringResource(R.string.home_featured_version_unknown, state.installed),
                    )
                    DeviceState.NotInstalled -> FitBadge(FitTone.Muted, stringResource(related.verdict.labelRes))
                }
            }
            HairLine()
        }
        if (desktop != null) {
            Column(Modifier.padding(18.dp).tap { onRepoTap(desktop) }) {
                Text(stringResource(R.string.home_featured_no_fit), style = Eyebrow, color = FitTheme.palette.ink4)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppTile(desktop.monogram, desktop.tileBg, desktop.tileFg, size = 42.dp, corner = 13.dp)
                    Spacer(Modifier.width(11.dp))
                    Text(desktop.name, style = FitTypography.titleSmall, color = FitTheme.palette.ink, modifier = Modifier.weight(1f), maxLines = 1)
                    Icon(FiChevron, null, tint = FitTheme.palette.ink4, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.home_featured_desktop, desktop.dist.sdkLabel),
                    style = FitTypography.bodySmall,
                    color = FitTheme.palette.ink3,
                )
                Spacer(Modifier.height(10.dp))
                FitBadge(FitTone.Bad, stringResource(R.string.home_featured_platform_mismatch), icon = FiAlert)
            }
        }
    }
}

// 页脚

@Composable
private fun HomeFooter() {
    val p = FitTheme.palette
    Column(Modifier.navigationBarsPadding().padding(horizontal = ScreenPad, vertical = 34.dp)) {
        HairLine()
        Spacer(Modifier.height(20.dp))
        Text("FitHub", style = FitTypography.titleSmall, color = p.ink)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.home_footer_no_login),
            style = FitTypography.bodySmall,
            color = p.ink3,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.home_footer_data),
            style = MonoMeta,
            color = p.ink4,
        )
    }
}
