package com.heiyehk.fithub.ui.device

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heiyehk.fithub.data.DeviceState
import com.heiyehk.fithub.data.Env
import com.heiyehk.fithub.data.FitEngine
import com.heiyehk.fithub.data.Repo
import com.heiyehk.fithub.data.ScanResult
import com.heiyehk.fithub.data.ScannedApp
import androidx.compose.ui.res.stringResource
import com.heiyehk.fithub.R
import com.heiyehk.fithub.ui.TabBarScrimHeight
import com.heiyehk.fithub.ui.explainText
import com.heiyehk.fithub.ui.components.AppTile
import com.heiyehk.fithub.ui.components.FitBadge
import com.heiyehk.fithub.ui.components.GhostButton
import com.heiyehk.fithub.ui.components.HairLine
import com.heiyehk.fithub.ui.components.IconCircleButton
import com.heiyehk.fithub.ui.components.MetaRow
import com.heiyehk.fithub.ui.components.MonoText
import com.heiyehk.fithub.ui.components.RadarGraphic
import com.heiyehk.fithub.ui.components.StaggeredItem
import com.heiyehk.fithub.ui.components.tap
import com.heiyehk.fithub.ui.icons.FiAlert
import com.heiyehk.fithub.ui.icons.FiArrowRight
import com.heiyehk.fithub.ui.icons.FiCheck
import com.heiyehk.fithub.ui.icons.FiClock
import com.heiyehk.fithub.ui.icons.FiClose
import com.heiyehk.fithub.ui.icons.FiRefresh
import com.heiyehk.fithub.ui.icons.FiSearch
import com.heiyehk.fithub.ui.icons.FiSliders
import com.heiyehk.fithub.ui.theme.Eyebrow
import com.heiyehk.fithub.ui.theme.FitTheme
import com.heiyehk.fithub.ui.theme.FitTone
import com.heiyehk.fithub.ui.theme.FitTypography
import com.heiyehk.fithub.ui.theme.MonoMeta
import kotlinx.coroutines.delay

/**
 * 本机：扫描本机已安装应用，按用户确认过的绑定给出可升级 / 签名冲突结论。
 *
 * 关联不上的应用留在「未关联」分组里等待用户绑定。
 */
@Composable
fun DeviceScreen(
    scan: ScanResult?,
    bindings: Map<String, Repo>,
    lookupPkg: String?,
    candidates: List<Repo>,
    onLookup: (ScannedApp) -> Unit,
    onBind: (ScannedApp, Repo) -> Unit,
    onRepoTap: (Repo) -> Unit,
    /** 解除关联。点了不可撤销，所以 UI 侧先做一次行内确认 */
    onUnbind: (ScannedApp) -> Unit,
    /** 换绑到另一个仓库：内部先解除再进反查 */
    onRelink: (ScannedApp) -> Unit,
    onRescan: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState,
) {
    val p = FitTheme.palette
    var scanning by remember { mutableStateOf(scan == null) }
    val radar = remember { Animatable(0f) }

    /** 本机应用搜索词。纯本地过滤，不发请求 —— 包名和应用名都在扫描结果里 */
    var query by remember { mutableStateOf("") }
    /** 正在等二次确认的解绑目标 */
    var unbindArmed by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(scanning) {
        if (scanning) {
            radar.animateTo(1f, tween(1200))
            delay(700)
            scanning = false
            radar.animateTo(0f, tween(400))
        }
    }

    // 系统应用不参与升级判断
    val userApps = scan?.apps.orEmpty().filter { !it.systemApp }

    /**
     * 搜索命中的应用集合。
     *
     * 应用名和包名都参与匹配：用户记得的往往只是图标或半个名字，拿到应用名反而
     * 要去设置里翻包名。反过来也一样。两个都匹配，命中任一即可。
     */
    val q = query.trim()
    val filtering = q.isNotEmpty()
    val matched = if (filtering) {
        userApps.filter {
            it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
        }
    } else {
        userApps
    }

    val unlinked = matched.filter { it.packageName !in bindings }

    /** 已绑定应用及其设备状态 */
    val linkedRows = matched.mapNotNull { app ->
        bindings[app.packageName]?.let { app to FitEngine.deviceStateFor(app, it) }
    }
    val conflicts = linkedRows.filter { it.second is DeviceState.SigningConflict }
    val upgrades = linkedRows.filter { it.second is DeviceState.Upgrade }
    val latest = linkedRows.filter { it.second is DeviceState.Latest }
    // 远端 APK 未解析时版本号不可比，单独分组，不并入「已是最新」
    val versionUnknown = linkedRows.filter { it.second is DeviceState.VersionUnknown }

    /**
     * 解绑的二次确认。
     *
     * 绑定是用户逐条确认过才写进本机档案的，一次误触就清掉、而且界面上再也找不回来
     * 入口，所以第一次点只「上膛」，行内换成「确认解除 / 取消」，第二次才真删。
     * 同时只允许一个目标处于待确认态。
     */
    val onUnbindClick: (ScannedApp) -> Unit = { app ->
        if (unbindArmed == app.packageName) {
            onUnbind(app)
            unbindArmed = null
        } else {
            unbindArmed = app.packageName
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(p.surface),
        state = listState,
        contentPadding = PaddingValues(bottom = TabBarScrimHeight + 28.dp),
    ) {
        item(key = "top") {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.tab_device), style = FitTypography.headlineSmall, color = p.ink, modifier = Modifier.weight(1f))
                    IconCircleButton(FiRefresh, stringResource(R.string.device_rescan), onClick = {
                        scanning = true
                        onRescan()
                    })
                    IconCircleButton(
                        FiSliders,
                        stringResource(R.string.device_settings),
                        onClick = onOpenSettings,
                    )
                }
                HairLine()
            }
        }

        item(key = "search") {
            // 搜索放在扫描概览之前：用户找应用时不需要先读完一整屏统计。
            // 过滤是纯本地的，输入即出结果，不消耗任何 GitHub 配额。
            DeviceSearchField(
                query = query,
                onQueryChange = { query = it },
                onClear = { query = "" },
            )
        }

        item(key = "scan") {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 26.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadarGraphic(
                        modifier = Modifier.graphicsLayer { alpha = 0.75f + 0.25f * radar.value },
                        size = 92.dp,
                    )
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        if (scanning) {
                            Text(stringResource(R.string.device_scanning), style = FitTypography.titleSmall, color = p.ink)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.device_scanning_detail),
                                style = FitTypography.bodySmall,
                                color = p.ink4,
                            )
                        } else {
                            Text(
                                // complete 为 false 时 total 只是可见数量，不能写成「共安装」
                                if (scan?.complete != false) {
                                    stringResource(R.string.device_scan_total, scan?.total ?: 0)
                                } else {
                                    stringResource(R.string.device_scan_visible, scan?.total ?: 0)
                                },
                                style = FitTypography.titleSmall,
                                color = p.ink,
                            )
                            Spacer(Modifier.height(6.dp))
                            if (scan?.complete == false) {
                                Text(
                                    stringResource(R.string.device_scan_partial),
                                    style = FitTypography.bodySmall,
                                    color = p.toneFg(FitTone.Warn),
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                            MetaRow {
                                Text(
                                    stringResource(R.string.device_user_apps, scan?.userInstalled ?: 0),
                                    style = FitTypography.bodySmall,
                                    color = p.ink3,
                                )
                                Box(Modifier.size(3.dp).clip(CircleShape).background(p.hairline))
                                Text(
                                    stringResource(R.string.device_sys_apps, scan?.system ?: 0),
                                    style = FitTypography.bodySmall,
                                    color = p.ink3,
                                )
                            }
                            if ((scan?.elapsedMs ?: 0) > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.device_elapsed, scan?.elapsedMs ?: 0),
                                    style = MonoMeta,
                                    color = p.ink4,
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "summary") {
            Column(Modifier.padding(horizontal = 20.dp)) {
                // 搜索时这四个数字只统计命中项，不加一句说明会被读成全机总数
                if (filtering) {
                    Text(
                        stringResource(R.string.device_filter_hit, matched.size),
                        style = FitTypography.bodySmall,
                        color = p.ink4,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(p.wash)
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    SummaryCell(stringResource(R.string.device_upgradable), upgrades.size.toString(), if (upgrades.isEmpty()) FitTone.Muted else FitTone.Ok)
                    SummaryCell(stringResource(R.string.device_conflict), conflicts.size.toString(), if (conflicts.isEmpty()) FitTone.Muted else FitTone.Bad)
                    SummaryCell(stringResource(R.string.device_latest), latest.size.toString(), FitTone.Muted)
                    SummaryCell(stringResource(R.string.device_unlinked), unlinked.size.toString(), FitTone.Muted)
                }
            }
        }

        if (conflicts.isNotEmpty()) {
            item(key = "conflict-head") {
                GroupHead(
                    stringResource(R.string.device_action_needed),
                    stringResource(R.string.device_action_needed_note),
                    FitTone.Bad,
                )
            }
            items(conflicts, key = { "c-${it.first.packageName}" }) { (app, state) ->
                StaggeredItem(0) {
                    LinkedRow(
                        app = app,
                        repo = bindings.getValue(app.packageName),
                        state = state,
                        highlight = FitTone.Bad,
                        onClick = { onRepoTap(it) },
                        unbindArmed = unbindArmed == app.packageName,
                        onUnbindClick = { onUnbindClick(app) },
                        onUnbindCancel = { unbindArmed = null },
                        onRelink = { onRelink(app) },
                    )
                }
            }
        }

        if (upgrades.isNotEmpty()) {
            item(key = "up-head") {
                GroupHead(
                    stringResource(R.string.device_upgradable),
                    stringResource(R.string.device_upgradable_note),
                    FitTone.Ok,
                )
            }
            items(upgrades, key = { "u-${it.first.packageName}" }) { (app, state) ->
                StaggeredItem(0) {
                    LinkedRow(
                        app = app,
                        repo = bindings.getValue(app.packageName),
                        state = state,
                        highlight = FitTone.Ok,
                        onClick = { onRepoTap(it) },
                        unbindArmed = unbindArmed == app.packageName,
                        onUnbindClick = { onUnbindClick(app) },
                        onUnbindCancel = { unbindArmed = null },
                        onRelink = { onRelink(app) },
                    )
                }
            }
        }

        if (latest.isNotEmpty()) {
            item(key = "latest-head") {
                GroupHead(
                    stringResource(R.string.device_latest),
                    stringResource(R.string.device_no_action),
                    FitTone.Muted,
                )
            }
            items(latest, key = { "l-${it.first.packageName}" }) { (app, state) ->
                StaggeredItem(0) {
                    LinkedRow(
                        app = app,
                        repo = bindings.getValue(app.packageName),
                        state = state,
                        highlight = FitTone.Muted,
                        onClick = { onRepoTap(it) },
                        unbindArmed = unbindArmed == app.packageName,
                        onUnbindClick = { onUnbindClick(app) },
                        onUnbindCancel = { unbindArmed = null },
                        onRelink = { onRelink(app) },
                    )
                }
            }
        }

        if (versionUnknown.isNotEmpty()) {
            item(key = "vunk-head") {
                GroupHead(
                    stringResource(R.string.device_unknown_title),
                    stringResource(R.string.device_version_unknown_note),
                    FitTone.Muted,
                )
            }
            items(versionUnknown, key = { "v-${it.first.packageName}" }) { (app, state) ->
                StaggeredItem(0) {
                    LinkedRow(
                        app = app,
                        repo = bindings.getValue(app.packageName),
                        state = state,
                        highlight = FitTone.Muted,
                        onClick = { onRepoTap(it) },
                        unbindArmed = unbindArmed == app.packageName,
                        onUnbindClick = { onUnbindClick(app) },
                        onUnbindCancel = { unbindArmed = null },
                        onRelink = { onRelink(app) },
                    )
                }
            }
        }

        if (unlinked.isNotEmpty()) {
            item(key = "unlinked-head") {
                GroupHead(
                    stringResource(R.string.device_unlinked_group, unlinked.size),
                    stringResource(R.string.device_unlinked_note),
                    FitTone.Muted,
                )
            }
            items(unlinked, key = { "u2-${it.packageName}" }) { app ->
                StaggeredItem(0) {
                    UnlinkedRow(
                        app = app,
                        looking = app.packageName == lookupPkg,
                        candidates = if (app.packageName == lookupPkg) candidates else emptyList(),
                        onLookup = { onLookup(app) },
                        onBind = { onBind(app, it) },
                        onRepoTap = onRepoTap,
                    )
                }
            }
        } else if (scan != null && !filtering) {
            item(key = "all-linked") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 26.dp)) {
                    HairLine()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.device_all_linked), style = Eyebrow, color = p.ink4)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.device_all_linked_detail, userApps.size),
                        style = FitTypography.bodySmall,
                        color = p.ink4,
                    )
                }
            }
        }

        // 搜索命中 0 个时要明说「没搜到」，不能什么都不显示 —— 分组全空时页面看着像加载失败
        if (filtering && matched.isEmpty()) {
            item(key = "no-hit") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 26.dp)) {
                    HairLine()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.device_no_match), style = Eyebrow, color = p.ink4)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.device_no_match_query, q) + "\n" +
                            stringResource(R.string.device_no_match_scope),
                        style = FitTypography.bodySmall,
                        color = p.ink4,
                    )
                }
            }
        }

        if (scan == null || userApps.isEmpty()) {
            item(key = "none") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 26.dp)) {
                    HairLine()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.device_none), style = Eyebrow, color = p.ink4)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.device_none_detail),
                        style = FitTypography.bodySmall,
                        color = p.ink4,
                    )
                }
            }
        }
    }
}

/**
 * 本机应用搜索框。
 *
 * 纯本地过滤，不发请求 —— 一屏几百个应用靠滚动找不现实，而「应用名 / 包名」这两个
 * 字段扫描时就都有了，搜本机根本不需要网络，也不该消耗 GitHub 配额。
 *
 * 外观沿用发现页顶栏那个搜索胶囊（同一个 CircleShape + wash 底 + 发丝描边），
 * 两处搜索看起来是一套东西。
 */
@Composable
private fun DeviceSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val p = FitTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp)
            .height(38.dp)
            .clip(CircleShape)
            .background(p.wash)
            .border(1.dp, p.hairline, CircleShape)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(FiSearch, contentDescription = null, tint = p.ink4, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(9.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = FitTypography.bodyMedium.copy(color = p.ink),
            cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(stringResource(R.string.device_search_hint), style = FitTypography.bodyMedium, color = p.ink4)
                }
                inner()
            },
        )
        if (query.isNotEmpty()) {
            Icon(
                FiClose,
                stringResource(R.string.action_clear),
                tint = p.ink4,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .tap { onClear() }
                    .padding(5.dp),
            )
        }
    }
}

/**
 * 行内的文字操作。
 *
 * 不用 GhostButton：每个已绑定行都挂两个带描边的药丸太重，会把状态徽标挤下去。
 * 纯文字 + 按下缩放，视觉层级低于徽标，但入口常驻 —— 解绑藏进长按或更多菜单的话，
 * 就重演一次「绑了之后找不到出口」。
 */
@Composable
private fun RowAction(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        style = FitTypography.labelLarge,
        color = color,
        modifier = Modifier
            .tap { onClick() }
            .padding(vertical = 6.dp),
    )
}

@Composable
private fun SummaryCell(label: String, value: String, tone: FitTone) {
    val p = FitTheme.palette
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = FitTypography.headlineSmall, color = p.toneFg(tone))
        Spacer(Modifier.height(3.dp))
        Text(label, style = FitTypography.labelSmall, color = p.ink4)
    }
}

@Composable
private fun GroupHead(title: String, note: String, tone: FitTone) {
    val p = FitTheme.palette
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 10.dp)) {
        MetaRow {
            Text(title, style = FitTypography.titleSmall, color = p.toneFg(tone))
            Spacer(Modifier.width(8.dp))
            Text(note, style = FitTypography.bodySmall, color = p.ink4)
        }
    }
}

/**
 * 已绑定的应用：版本号取自扫描与最新 Release 的比对结果
 *
 * 行尾常驻「换仓库 / 解除关联」两个操作。绑定一旦建立就再也改不了、也删不掉，
 * 所以入口必须常驻可见，不能藏进长按或更多菜单。
 */
@Composable
private fun LinkedRow(
    app: ScannedApp,
    repo: Repo,
    state: DeviceState,
    highlight: FitTone,
    onClick: (Repo) -> Unit,
    /** 本行是否处于"再点一次就真解绑"的待确认态 */
    unbindArmed: Boolean,
    onUnbindClick: () -> Unit,
    onUnbindCancel: () -> Unit,
    onRelink: () -> Unit,
) {
    val p = FitTheme.palette
    val (badgeText, badgeTone, badgeIcon) = when (state) {
        is DeviceState.SigningConflict -> Triple(stringResource(R.string.device_badge_uninstall), FitTone.Bad, FiAlert)
        is DeviceState.Upgrade -> Triple("${state.from} → ${state.to}", FitTone.Ok, FiCheck)
        is DeviceState.Latest -> Triple(
            stringResource(R.string.device_badge_latest, explainText(state.version)),
            FitTone.Muted,
            FiCheck,
        )
        is DeviceState.VersionUnknown -> Triple(
            stringResource(
                R.string.device_badge_version_unknown,
                state.installed,
                // released 为 null 表示连版本号都没取到，用可翻译的「最新 Release」兜底
                state.released ?: stringResource(R.string.latest_release),
            ),
            FitTone.Muted,
            FiClock,
        )
        DeviceState.NotInstalled -> Triple("—", FitTone.Muted, FiCheck)
    }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .tap { onClick(repo) }
                .padding(horizontal = 20.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTile(app.label.take(2), 0xFFF1F0EC, 0xFF4A4F4A)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                MetaRow {
                    Text(app.label, style = FitTypography.titleSmall, color = p.ink, maxLines = 1)
                    Spacer(Modifier.width(7.dp))
                    FitBadge(FitTone.Prerelease, repo.owner)
                    // 展示绑定来源，来源不同的结论可信度也不同
                    Env.device.installed
                        .firstOrNull { it.packageName == app.packageName }
                        ?.source
                        ?.let { src ->
                            Spacer(Modifier.width(6.dp))
                            FitBadge(FitTone.Muted, stringResource(src.labelRes))
                        }
                }
                Spacer(Modifier.height(5.dp))
                FitBadge(badgeTone, badgeText, icon = badgeIcon)
            }
            Icon(FiArrowRight, null, tint = p.ink4, modifier = Modifier.size(16.dp))
        }
        // 操作行：默认「换仓库 / 解除关联」，待确认时原地换成「确认解除 / 取消」
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 79.dp, end = 20.dp, top = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (unbindArmed) {
                RowAction(stringResource(R.string.device_unbind_confirm), p.toneFg(FitTone.Bad), onUnbindClick)
                RowAction(stringResource(R.string.device_cancel), p.ink4, onUnbindCancel)
            } else {
                RowAction(stringResource(R.string.device_relink), p.ink2, onRelink)
                RowAction(stringResource(R.string.device_unbind), p.toneFg(FitTone.Bad), onUnbindClick)
            }
        }
        // 指纹为空时不展示
        app.signerSha256?.let {
            Text(
                stringResource(R.string.device_local_signer, it.take(8)),
                style = MonoMeta,
                color = p.ink4,
                modifier = Modifier.padding(start = 79.dp, bottom = 10.dp),
            )
        }
        HairLine(Modifier.padding(start = 79.dp))
    }
}

/**
 * 未关联的应用。
 * 点「查仓库」才发一次搜索请求，结果是候选，需用户确认后绑定。
 */
@Composable
private fun UnlinkedRow(
    app: ScannedApp,
    looking: Boolean,
    candidates: List<Repo>,
    onLookup: () -> Unit,
    onBind: (Repo) -> Unit,
    onRepoTap: (Repo) -> Unit,
) {
    val p = FitTheme.palette
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTile(app.label.take(2), 0xFFF1F0EC, 0xFF6E736E)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = FitTypography.titleSmall, color = p.ink, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${app.packageName} · v${app.versionName}",
                    style = MonoMeta,
                    color = p.ink4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!looking) {
                GhostButton(stringResource(R.string.device_find_repo), onClick = onLookup, icon = FiSearch)
            }
        }

        if (looking) {
            if (candidates.isEmpty()) {
                Text(
                    stringResource(R.string.device_lookup_busy),
                    style = FitTypography.bodySmall,
                    color = p.ink4,
                    modifier = Modifier.padding(start = 79.dp, end = 20.dp, bottom = 10.dp),
                )
            } else {
                Text(
                    stringResource(R.string.device_lookup_candidates),
                    style = FitTypography.bodySmall,
                    color = p.ink4,
                    modifier = Modifier.padding(start = 79.dp, end = 20.dp, top = 2.dp, bottom = 6.dp),
                )
                candidates.take(4).forEach { cand ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .tap {
                                onBind(cand)
                                onRepoTap(cand)
                            }
                            .padding(start = 79.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppTile(cand.monogram, cand.tileBg, cand.tileFg, size = 26.dp, corner = 8.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${cand.owner}/${cand.name}",
                                style = FitTypography.bodySmall,
                                color = p.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(cand.desc, style = MonoMeta, color = p.ink4, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(stringResource(R.string.device_bind), style = FitTypography.labelSmall, color = p.accent)
                    }
                }
            }
        }
        HairLine(Modifier.padding(start = 79.dp))
    }
}
