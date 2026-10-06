package com.heiyehk.fithub.ui.history

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.heiyehk.fithub.R
import com.heiyehk.fithub.data.HistoryEntry
import com.heiyehk.fithub.ui.components.FitBadge
import com.heiyehk.fithub.ui.components.GhostButton
import com.heiyehk.fithub.ui.components.HairLine
import com.heiyehk.fithub.ui.components.IconCircleButton
import com.heiyehk.fithub.ui.components.tap
import com.heiyehk.fithub.ui.icons.FiArrowLeft
import com.heiyehk.fithub.ui.icons.FiBookmark
import com.heiyehk.fithub.ui.icons.FiClock
import com.heiyehk.fithub.ui.icons.FiPackage
import com.heiyehk.fithub.ui.theme.Eyebrow
import com.heiyehk.fithub.ui.theme.FitTheme
import com.heiyehk.fithub.ui.theme.FitTone
import com.heiyehk.fithub.ui.theme.FitTypography
import com.heiyehk.fithub.ui.theme.MonoMeta
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 历史足迹。
 *
 * 全部读本机记录，不发任何请求。足迹是设备状态不是账号数据，
 * 所以没有「同步」这个概念，也不提供导出 —— 见 [com.heiyehk.fithub.data.HistoryStore]。
 */
@Composable
fun HistoryScreen(
    entries: List<HistoryEntry>,
    onBack: () -> Unit,
    onClearOne: (HistoryEntry) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState,
) {
    val p = FitTheme.palette

    Column(modifier.fillMaxSize().background(p.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(FiArrowLeft, stringResource(R.string.action_back), onClick = onBack)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.history_title), style = FitTypography.titleMedium, color = p.ink)
            Spacer(Modifier.weight(1f))
            if (entries.isNotEmpty()) {
                Text(
                    stringResource(R.string.action_clear_all),
                    style = FitTypography.labelLarge,
                    color = p.ink3,
                    modifier = Modifier.tap { onClearAll() },
                )
            }
        }
        HairLine()

        if (entries.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(80.dp))
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(p.washDeep),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(FiClock, null, tint = p.ink4, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.history_empty_title), style = FitTypography.titleMedium, color = p.ink)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.history_empty_1) + "\n" +
                        stringResource(R.string.history_local_only),
                    style = FitTypography.bodyMedium,
                    color = p.ink3,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item(key = "note") {
                    Text(
                        stringResource(R.string.history_local_only_clear),
                        style = FitTypography.bodySmall,
                        color = p.ink4,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }

                // 按类型分节展示，一眼能看出哪一类多
                for ((kind, label) in KINDS) {
                    val group = entries.filter { it.kind == kind }
                    if (group.isEmpty()) continue

                    item(key = "h-$kind") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(label), style = Eyebrow, color = p.ink4)
                            Text(stringResource(R.string.history_count, group.size), style = MonoMeta, color = p.ink4)
                        }
                    }

                    items(group, key = { "${kind.name}-${it.ref}" }) { e ->
                        HistoryRow(e) { onClearOne(e) }
                    }
                }
            }
        }
    }
}

private val KINDS = listOf(
    HistoryEntry.Kind.RepoViewed to R.string.history_kind_viewed,
    HistoryEntry.Kind.AssetParsed to R.string.history_kind_parsed,
    HistoryEntry.Kind.InstalledViaUs to R.string.history_kind_installed,
)

@Composable
private fun HistoryRow(e: HistoryEntry, onClear: () -> Unit) {
    val p = FitTheme.palette
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(p.wash),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when (e.kind) {
                        HistoryEntry.Kind.RepoViewed -> FiBookmark
                        HistoryEntry.Kind.AssetParsed -> FiClock
                        HistoryEntry.Kind.InstalledViaUs -> FiPackage
                        // 浏览足迹里不该出现下载条目（那属于「下载与安装记录」页），
                        // 但枚举加了新值这里必须给个分支，给 FiClock 兜底而不是 else
                        HistoryEntry.Kind.Downloaded -> FiClock
                    },
                    null,
                    tint = p.ink3,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    e.title,
                    style = FitTypography.titleSmall,
                    color = p.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 描述单独一行：只有仓库名的话，一屏「flutter / scrcpy / zed」根本
                // 对不上号。老条目没有这个字段（空串），按「有才显示」处理。
                if (e.desc.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        e.desc,
                        style = FitTypography.bodySmall,
                        color = p.ink3,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (e.detail.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        e.detail,
                        style = FitTypography.bodySmall,
                        color = if (e.desc.isNotBlank()) p.ink4 else p.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(formatAt(e.at), style = MonoMeta, color = p.ink4)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.action_remove),
                style = FitTypography.labelSmall,
                color = p.ink4,
                modifier = Modifier.tap { onClear() }.padding(6.dp),
            )
        }
        HairLine(Modifier.padding(start = 62.dp))
    }
}

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

/** 本地时区的可读时间。足迹是给人看的，秒级精度没有意义 */
private fun formatAt(ms: Long): String =
    TIME_FMT.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
