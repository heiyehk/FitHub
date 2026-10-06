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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heiyehk.fithub.data.Env
import com.heiyehk.fithub.data.Subscription
import androidx.compose.ui.res.stringResource
import com.heiyehk.fithub.R
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
import com.heiyehk.fithub.ui.icons.FiClock
import com.heiyehk.fithub.ui.theme.Eyebrow
import com.heiyehk.fithub.ui.theme.FitTheme
import com.heiyehk.fithub.ui.theme.FitTone
import com.heiyehk.fithub.ui.theme.FitTypography
import com.heiyehk.fithub.ui.theme.MonoMeta

/**
 * 订阅页。
 *
 * 关注是纯本地行为，数据来自 [Subscription] 快照，因此这页在离线时也能读。
 * 代价是快照可能旧，所以超过 6 小时的行会标注「X 小时前」而不是当成实时。
 *
 * 打开本页不发任何请求；刷新由用户主动触发（行内单个刷新 / 顶部全部刷新），
 * 串行执行以免撞上 GitHub 限流。
 */
@Composable
fun SubscribeScreen(
    subs: List<Subscription>,
    onRepoTap: (String) -> Unit,
    onBrowse: () -> Unit,
    onRefreshOne: (String) -> Unit,
    onRefreshAll: () -> Unit,
    refreshing: Set<String>,
    modifier: Modifier = Modifier,
    listState: LazyListState,
) {
    val p = FitTheme.palette
    val refreshingAll = refreshing.isNotEmpty() && refreshing.size == subs.size

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
                    Text(stringResource(R.string.tab_subscribe), style = FitTypography.headlineSmall, color = p.ink, modifier = Modifier.weight(1f))
                    if (subs.isNotEmpty()) {
                        if (refreshingAll) {
                            CircularProgress(0f, size = 18.dp, color = p.ink4, indeterminate = true)
                        } else {
                            IconCircleButton(FiClock, stringResource(R.string.subscribe_refresh_all), onRefreshAll)
                        }
                    } else {
                        Text(stringResource(R.string.subscribe_count_zero), style = MonoMeta, color = p.ink4)
                    }
                }
                if (subs.isNotEmpty()) {
                    Text(
                        stringResource(R.string.subscribe_quota_note, subs.size),
                        style = FitTypography.bodySmall,
                        color = p.ink4,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
                    )
                }
                HairLine()
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
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                onTap = { onRepoTap(sub.fullName) },
                onRefresh = { onRefreshOne(sub.fullName) },
            )
        }

        if (subs.isNotEmpty()) {
            item(key = "foot") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 26.dp)) {
                    HairLine()
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
                        FitBadge(FitTone.Prerelease, stringResource(R.string.subscribe_meta_age, agoOf(sub.metaFetchedAt)))
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
                IconCircleButton(FiClock, stringResource(R.string.subscribe_refresh_one), onRefresh)
            }
        }
        HairLine(Modifier.padding(start = 79.dp))
    }
}

/** 快照抓取时刻的相对表述 */
@Composable
private fun agoOf(ts: Long): String {
    if (ts <= 0L) return stringResource(R.string.subscribe_age_unknown)
    val hours = (System.currentTimeMillis() - ts) / 3_600_000
    return when {
        hours < 1 -> stringResource(R.string.subscribe_age_under_hour)
        hours < 24 -> stringResource(R.string.subscribe_age_hours, hours.toInt())
        else -> stringResource(R.string.subscribe_age_days, (hours / 24).toInt())
    }
}
