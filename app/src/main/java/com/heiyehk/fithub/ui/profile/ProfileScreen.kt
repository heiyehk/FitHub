package com.heiyehk.fithub.ui.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heiyehk.fithub.R
import com.heiyehk.fithub.data.Async
import com.heiyehk.fithub.data.Env
import com.heiyehk.fithub.data.MyRepos
import com.heiyehk.fithub.data.Prefs
import com.heiyehk.fithub.data.remote.UserDto
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.heiyehk.fithub.data.AppLocale
import com.heiyehk.fithub.data.Mirrors
import com.heiyehk.fithub.ui.TabBarScrimHeight
import com.heiyehk.fithub.ui.components.FitBadge
import com.heiyehk.fithub.ui.components.HairLine
import com.heiyehk.fithub.ui.components.IconCircleButton
import com.heiyehk.fithub.ui.components.MetaRow
import com.heiyehk.fithub.ui.components.PrimaryButton
import com.heiyehk.fithub.ui.components.RemoteAvatar
import com.heiyehk.fithub.ui.components.tap
import com.heiyehk.fithub.ui.icons.FiAlert
import com.heiyehk.fithub.ui.icons.FiArrowRight
import com.heiyehk.fithub.ui.icons.FiBookmark
import com.heiyehk.fithub.ui.icons.FiCheck
import com.heiyehk.fithub.ui.icons.FiClose
import com.heiyehk.fithub.ui.icons.FiClock
import com.heiyehk.fithub.ui.icons.FiDownload
import com.heiyehk.fithub.ui.icons.FiGlobe
import com.heiyehk.fithub.ui.icons.FiPackage
import com.heiyehk.fithub.ui.icons.FiRefresh
import com.heiyehk.fithub.ui.icons.FiShield
import com.heiyehk.fithub.ui.icons.FiSliders
import com.heiyehk.fithub.ui.icons.FiUser
import com.heiyehk.fithub.ui.theme.Eyebrow
import com.heiyehk.fithub.ui.theme.FitTheme
import com.heiyehk.fithub.ui.theme.FitTone
import com.heiyehk.fithub.ui.theme.FitTypography
import com.heiyehk.fithub.ui.theme.MonoMeta

/**
 * 我的：账号状态与设置。
 *
 * 登录已接入 GitHub Device Flow 并在真机跑通（见 [LoginScreen]）。
 */
@Composable
fun ProfileScreen(
    /**
     * 配额**余量**，不是已用量。
     *
     * 传进来的一直是 GitHubApi.lastRemaining。进度条按「还剩多少」画：随消耗变短，
     * 和顶栏那个胶囊同一口径。
     */
    quotaRemaining: Int,
    quotaTotal: Int,
    quotaLoggedIn: Int,
    downloadCount: Int,
    installedViaUs: Int,
    subscriptionCount: Int,
    historyCount: Int,
    /** WebDAV 同步状态摘要。关着就不要写成「已连接」—— 那是在编 */
    syncLabel: String,
    versionName: String,
    onLogin: () -> Unit,
    /** 已登录。决定这一块显示「未登录」还是「谁登录了 + 退出」 */
    loggedIn: Boolean = false,
    /** 登录的用户名 / 昵称。查不到就写 null，不要拿「未知用户」糊弄 */
    loginName: String? = null,
    /**
     * 登录用户的资料。
     *
     * 和 [loggedIn] 分开是有意义的：token 在但 [loggedIn] 为真时 `me()` 可能失败，
     * 于是 [loggedIn] 真而这里为 null。这是个真实存在的中间态，
     * 头像退回文字色块即可，但不能因此改写成「未登录」。
     */
    me: UserDto? = null,
    /** 自己的仓库 + star/fork/watch 汇总。未登录或还没拉时给 null */
    myRepos: Async<MyRepos>? = null,
    /** `api::avatar`。传进来而不是 UI 自己建客户端 */
    avatarLoader: suspend (String) -> ByteArray? = { null },
    /** 进入「我的项目」页 */
    onOpenMyProjects: () -> Unit = {},
    onLogout: () -> Unit = {},
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState,
) {
    val p = FitTheme.palette
    // 直接读 Prefs 里的 state 而不是收参数：开关一改就重组，
    // 不会出现「设置页显示已切换、深浅色没变」这种两边不同步的中间态。
    val prefs = Prefs.state.value
    // 余量占比：配额没动时是满的，用得越多条越短
    val ratio = if (quotaTotal > 0) (quotaRemaining.toFloat() / quotaTotal).coerceIn(0f, 1f) else 0f
    val quotaLow = quotaTotal > 0 && quotaRemaining * 5 < quotaTotal

    LazyColumn(
        modifier = modifier.fillMaxSize().background(p.surface),
        state = listState,
        // 底部留出 tab bar 再加 28.dp，保证最后一项不被悬浮条盖住
        contentPadding = PaddingValues(bottom = TabBarScrimHeight + 28.dp),
    ) {
        item(key = "top") {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.tab_profile),
                        style = FitTypography.headlineSmall,
                        color = p.ink,
                        modifier = Modifier.weight(1f),
                    )
                    // 这里原来有个「设置」齿轮。偏好（外观 / 预发布 / 校验）本来就在
                    // 同一页下面，齿轮点进去要么跳到同一页、要么就得再做一个内容完全
                    // 重合的设置页 —— 两个入口管同一组开关只会更容易走样。所以删掉。
                    // 要恢复：把下面这行 IconCircleButton(FiSliders, "设置") 加回来即可。
                }
                HairLine()
            }
        }

        // 账号与配额
        item(key = "account") {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (loggedIn) {
                        // 登录了就显示真头像（GitHub 的 Gravatar）。
                        // 没登录时保持那个人形图标：那是「这里还没有账号」的意思，
                        // 换成文字色块会让人以为已经登录了。
                        RemoteAvatar(
                            url = me?.avatarUrl.orEmpty(),
                            monogram = me?.name?.take(2) ?: me?.login?.take(2) ?: "?",
                            loader = avatarLoader,
                            size = 52.dp,
                            corner = 26.dp,
                        )
                    } else {
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(p.washDeep),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(FiUser, null, tint = p.ink4, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        // 三态而不是两种：登录了但还没查到昵称（me() 失败）是真实存在的
                        // 中间态，写成「未登录」会骗人，写成「某某已登录」又拿不出名字。
                        Text(
                            when {
                                !loggedIn -> stringResource(R.string.profile_logged_out)
                                loginName != null ->
                                    stringResource(R.string.profile_logged_in_as, loginName)

                                else -> stringResource(R.string.profile_logged_in)
                            },
                            style = FitTypography.titleMedium,
                            color = p.ink,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (loggedIn) {
                                stringResource(R.string.profile_token_local)
                            } else {
                                stringResource(R.string.profile_no_login_needed)
                            },
                            style = FitTypography.bodySmall,
                            color = p.ink4,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(p.wash)
                        .padding(16.dp),
                ) {
                    MetaRow {
                        Text(
                            stringResource(R.string.profile_quota_title),
                            style = FitTypography.titleSmall,
                            color = p.ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.profile_quota_remain, quotaRemaining, quotaTotal),
                            style = MonoMeta,
                            color = if (quotaLow) p.toneFg(FitTone.Warn) else p.ink3,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(p.hairline),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(ratio)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(if (quotaLow) p.toneFg(FitTone.Warn) else p.accent),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (loggedIn) {
                            stringResource(R.string.profile_scope_logged_in)
                        } else {
                            stringResource(R.string.profile_scope_logged_out, quotaLoggedIn)
                        },
                        style = FitTypography.bodySmall,
                        color = p.ink3,
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = if (loggedIn) {
                            stringResource(R.string.profile_logout)
                        } else {
                            stringResource(R.string.login_title)
                        },
                        icon = if (loggedIn) FiClose else FiShield,
                        height = 46.dp,
                        onClick = if (loggedIn) onLogout else onLogin,
                    )
                }
            }
        }

        // 登录后才有的 GitHub 账号数据。
        //
        // 和下面那行本机统计分成两块：star / fork / watch 是 GitHub 上的云端数字，
        // 下载 / 经我安装是这台设备上的记录。挤在同一行会让用户以为它们同一口径，
        // 也会让人分不清「换了台手机数字为什么变了」。
        if (loggedIn) {
            item(key = "gh") {
                val ok = myRepos as? Async.Ok
                Column(Modifier.padding(horizontal = 20.dp)) {
                    GroupHead(stringResource(R.string.profile_gh_group))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(p.wash)
                            .padding(vertical = 16.dp),
                    ) {
                        when (myRepos) {
                            null, is Async.Loading -> Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                StatCell("—", stringResource(R.string.ghstat_stars))
                                StatCell("—", stringResource(R.string.ghstat_forks))
                                StatCell("—", stringResource(R.string.ghstat_watchers))
                            }

                            is Async.Err -> Text(
                                myRepos.message,
                                style = FitTypography.bodySmall,
                                color = p.ink4,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )

                            is Async.Ok -> Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                StatCell(
                                    Env.formatStars(myRepos.value.totals.stars),
                                    stringResource(R.string.ghstat_stars),
                                )
                                StatCell(
                                    Env.formatStars(myRepos.value.totals.forks),
                                    stringResource(R.string.ghstat_forks),
                                )
                                StatCell(
                                    Env.formatStars(myRepos.value.totals.watchers),
                                    stringResource(R.string.ghstat_watchers),
                                )
                            }
                        }

                        // 汇总的覆盖边界。GitHub 没有「我总共收到多少 star」的接口，
                        // 这三个数是遍历自己的仓库加总出来的，所以少算了什么必须写出来。
                        val totals = ok?.value?.totals
                        if (totals != null && !ok.value.complete) {
                            GhNote(FitTone.Warn, stringResource(R.string.ghstat_partial, totals.repos))
                        }
                        // me 为 null 说明连资料都没查回来，此时说「权限拿不到私有仓库数量」
                        // 是把一次网络失败硬说成权限问题 —— 那属于编原因。
                        // 资料缺失本身已经由上面那行「已登录」而不是姓名如实呈现了。
                        val private = me?.ownedPrivateRepos
                        when {
                            private == null && me != null ->
                                GhNote(FitTone.Muted, stringResource(R.string.ghstat_private_unknown))

                            private != null && private > 0 ->
                                GhNote(FitTone.Muted, stringResource(R.string.ghstat_private_hidden, private))
                        }
                        if (totals != null && totals.watchUnknown > 0) {
                            GhNote(FitTone.Muted, stringResource(R.string.ghstat_watch_partial, totals.watchUnknown))
                        }

                        HairLine(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                        // 「我的项目」入口。放在这张卡里而不是另起一个分组，
                        // 是因为它就是上面那三个数字的明细，点进去看的是同一批数据。
                        SettingRow(
                            icon = FiArrowRight,
                            title = stringResource(R.string.profile_action_my_projects),
                            value = totals?.repos?.let {
                                stringResource(R.string.profile_gh_count_repos, it)
                            }.orEmpty(),
                            onClick = onOpenMyProjects,
                            contentPadding = 16.dp,
                        )
                    }
                }
            }
        }

        item(key = "stats") {
            Row(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(p.surface)
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatCell(downloadCount.toString(), stringResource(R.string.profile_stat_downloads))
                StatCell(installedViaUs.toString(), stringResource(R.string.profile_stat_installed))
                StatCell("0", stringResource(R.string.profile_stat_shared))
            }
        }

        item(key = "group1") {
            GroupHead(stringResource(R.string.profile_group_usage))
            SettingRow(
                FiClock,
                stringResource(R.string.profile_action_records),
                stringResource(R.string.profile_count_items, downloadCount),
            ) { onAction("下载与安装记录") }
            SettingRow(
                FiBookmark,
                stringResource(R.string.profile_action_subs),
                stringResource(R.string.profile_count_subs, subscriptionCount),
            ) { onAction("我的订阅") }
            SettingRow(
                FiPackage,
                stringResource(R.string.profile_action_installed_by_us),
                stringResource(R.string.profile_count_apps, installedViaUs),
            ) { onAction("从 FitHub 安装的应用") }
            SettingRow(
                FiClock,
                stringResource(R.string.profile_action_history),
                stringResource(R.string.profile_count_items, historyCount),
            ) { onAction("历史足迹") }
        }

        item(key = "group2") {
            GroupHead(stringResource(R.string.profile_group_subs))
            SettingRow(
                FiArrowRight,
                stringResource(R.string.profile_action_export),
                stringResource(R.string.profile_count_items, subscriptionCount),
            ) { onAction("导出订阅") }
            SettingRow(
                FiArrowRight,
                stringResource(R.string.profile_action_import),
                stringResource(R.string.profile_import_source),
            ) { onAction("导入订阅") }
            SettingRow(
                FiRefresh,
                stringResource(R.string.profile_action_sync),
                syncLabel,
            ) { onAction("订阅同步") }
        }

        item(key = "group3") {
            GroupHead(stringResource(R.string.profile_group_prefs))
            SettingRow(
                FiSliders,
                stringResource(R.string.pref_appearance),
                stringResource(prefs.themeLabelRes),
            ) { onAction("外观") }
            SettingRow(
                FiGlobe,
                stringResource(R.string.pref_language),
                // 三个选项的名字是「跟随系统 / 简体中文 / English」，各语言通用，
                // 所以走 AppLocale.choiceLabel() 而不是 stringResource
                AppLocale.choiceLabel(AppLocale.current(LocalContext.current)),
            ) { onAction("语言") }
            SettingRow(
                FiDownload,
                stringResource(R.string.pref_mirror),
                Mirrors.BY_ID[Prefs.mirrorId(LocalContext.current)]?.id
                    ?: stringResource(R.string.pref_mirror_direct),
            ) { onAction("下载源") }
            SettingToggle(
                icon = FiCheck,
                title = stringResource(R.string.pref_include_prerelease),
                checked = prefs.includePrerelease,
                hint = stringResource(R.string.pref_include_prerelease_hint),
            ) { onAction("预发布") }
            SettingToggle(
                icon = FiShield,
                title = stringResource(R.string.pref_require_sha),
                checked = prefs.requireSha,
                hint = stringResource(R.string.pref_require_sha_hint),
            ) { onAction("校验") }
        }

        item(key = "group4") {
            GroupHead(stringResource(R.string.profile_group_about))
            SettingRow(
                FiAlert,
                stringResource(R.string.info_privacy_title),
                stringResource(R.string.pref_privacy_value),
            ) { onAction("隐私") }
            SettingRow(
                FiArrowRight,
                stringResource(R.string.info_license_title),
                "",
            ) { onAction("开源协议") }
            SettingRow(
                FiArrowRight,
                stringResource(R.string.info_update_title),
                "v$versionName",
            ) { onAction("检查更新") }
        }

        item(key = "foot") {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 30.dp)) {
                HairLine()
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.profile_footer),
                    style = FitTypography.bodySmall,
                    color = p.ink4,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    val p = FitTheme.palette
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = FitTypography.headlineSmall, color = p.ink)
        Spacer(Modifier.height(3.dp))
        Text(label, style = FitTypography.labelSmall, color = p.ink4)
    }
}

@Composable
private fun GroupHead(title: String) {
    val p = FitTheme.palette
    Column {
        Text(
            title,
            style = Eyebrow,
            color = p.ink4,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 8.dp),
        )
    }
}

/**
 * 带开关的设置行。
 *
 * 不用 Material 的 [androidx.compose.material3.Switch]：它自带的尺寸和配色
 * 和这一屏的行高、墨色体系对不上，看起来像另一个 App 塞进来的。画一个小圆点 +
 * 轨道更省事，也更贴合这一屏的克制。
 */
@Composable
private fun SettingToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    checked: Boolean,
    /** 关闭时的原因。开关下面必须说清「关着会怎样」，否则用户只能靠猜 */
    hint: String,
    onChange: (Boolean) -> Unit,
) {
    val p = FitTheme.palette
    val knob by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "knob",
    )
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .tap { onChange(!checked) }
                .padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = p.ink3, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                title,
                style = FitTypography.titleSmall,
                color = p.ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(10.dp))
            // 轨道
            Box(
                Modifier
                    .width(38.dp)
                    .height(22.dp)
                    .clip(CircleShape)
                    .background(if (checked) p.accent else p.hairline),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        // 写成 (knob * 16f).dp 而不是 knob * 16.dp：
                        // 后者要解析 Float.times(Dp)，那是 androidx.compose.ui.unit 里的
                        // 顶层运算符，得单独 import；这里只用 Float.dp，不用多带一个 import。
                        .offset(x = (knob * 16f).dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (checked) p.surface else p.ink4.copy(alpha = 0.5f)),
                )
            }
        }
        Text(
            hint,
            style = FitTypography.bodySmall,
            color = p.ink4,
            modifier = Modifier.padding(start = 52.dp, end = 20.dp, bottom = 12.dp),
        )
        HairLine(Modifier.padding(start = 52.dp))
    }
}

/**
 * 一行设置。
 *
 * [contentPadding] 是横向内边距：页面上的行贴屏幕边（20.dp），
 * 而「GitHub 账号」那张卡里的行是嵌在卡内的（16.dp）—— 两种位置靠一个参数区分，
 * 而不是复制一份几乎一样的行出来。
 *
 * 行尾固定带一个向右箭头：这页的行全部可点，没有「只读的展示行」，
 * 所以不需要按可点性改变外观。
 */
@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    /**
     * 横向内边距：页面上的行贴屏幕边（20.dp），而「GitHub 账号」那张卡里的行
     * 是嵌在卡内的（16.dp）。两种位置靠一个参数区分，而不是复制一份几乎一样的行。
     *
     * 放在 [onClick] **前面**是必须的：这一页所有调用点都写成尾随 lambda
     * （`SettingRow(...) { … }`），而尾随 lambda 只会绑定到最后一个参数。
     * 一旦把默认值参数放到最后，全页的点击回调都会静默地变成「内边距」，
     * 编译器报的是类型不匹配，而不是「这个行点不动了」。
     */
    contentPadding: androidx.compose.ui.unit.Dp = 20.dp,
    onClick: () -> Unit,
) {
    val p = FitTheme.palette
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .tap { onClick() }
                .padding(horizontal = contentPadding, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = p.ink3, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                title,
                style = FitTypography.titleSmall,
                color = p.ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (value.isNotEmpty()) {
                Text(value, style = FitTypography.bodySmall, color = p.ink4, maxLines = 1)
            }
            Spacer(Modifier.width(6.dp))
            Icon(FiArrowRight, null, tint = p.ink4.copy(alpha = 0.6f), modifier = Modifier.size(15.dp))
        }
        HairLine(Modifier.padding(start = contentPadding + 32.dp))
    }
}

/**
 * GitHub 账号区块里的一行「数据边界」说明。
 *
 * 这些数字是遍历仓库加总出来的，不是 GitHub 直接给的总数，
 * 所以「少算了什么」必须摆在数字底下 —— 否则用户会把它们当成官方口径。
 * 文案可折行：这些都是长句，塞进单行徽标会被截掉后半句。
 */
@Composable
private fun GhNote(tone: FitTone, text: String) {
    val p = FitTheme.palette
    Spacer(Modifier.height(6.dp))
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(p.toneFg(tone)),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = FitTypography.bodySmall, color = p.toneFg(tone))
    }
}
