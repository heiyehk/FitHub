package com.heiyehk.fithub.ui.history

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.heiyehk.fithub.R
import com.heiyehk.fithub.data.HistoryEntry
import com.heiyehk.fithub.data.install.ApkAction
import com.heiyehk.fithub.data.install.ApkInstaller
import com.heiyehk.fithub.data.install.ApkLibrary
import com.heiyehk.fithub.data.install.DownloadedApk
import com.heiyehk.fithub.data.install.actionFor
import com.heiyehk.fithub.ui.TabBarScrimHeight
import com.heiyehk.fithub.ui.components.HairLine
import com.heiyehk.fithub.ui.icons.FiArrowLeft
import com.heiyehk.fithub.ui.icons.FiCheck
import com.heiyehk.fithub.ui.icons.FiDownload
import com.heiyehk.fithub.ui.icons.FiPackage
import com.heiyehk.fithub.ui.icons.FiRefresh
import com.heiyehk.fithub.ui.theme.Eyebrow
import com.heiyehk.fithub.ui.theme.FitTheme
import com.heiyehk.fithub.ui.theme.FitTypography
import com.heiyehk.fithub.ui.theme.MonoMeta
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 下载与安装记录。
 *
 * 和 [com.heiyehk.fithub.ui.history.HistoryScreen] 的区别：那边记的是**看过什么**
 * （浏览足迹），这里记的是**动过文件什么**（下载、校验、装上）。两者都不参与 WebDAV
 * 同步 —— 同步过去会让另一台设备出现本机根本没发生过的记录。
 *
 * [focus] 决定哪一节排最前。「从 FitHub 安装的应用」和「下载与安装记录」指向同一个
 * 数据集，区别只是用户从哪边进来的，没必要做两套页面。
 */
@Composable
fun RecordsScreen(
    entries: List<HistoryEntry>,
    focus: HistoryEntry.Kind?,
    /**
     * 已下载清单，用来给「下载过安装包」那一节接上真正的动作。
     *
     * 之前这一页只有文字记录：下好的包躺在下载目录里，用户从这儿既装不了也开不了，
     * 只能记住文件名、切到文件管理器、再或者回详情页重新点一遍。清单是唯一知道
     * 「这个包在哪、装没装」的地方，所以必须传进来。
     */
    library: List<DownloadedApk>,
    onBack: () -> Unit,
    onClearOne: (HistoryEntry) -> Unit,
    onClearAll: () -> Unit,
    /** 装上之后清单变了，宿主刷新一下，列表里的按钮会当场从安装变成打开 */
    onNotifyChanged: () -> Unit = {},
) {
    val p = FitTheme.palette

    Column(
        Modifier
            .fillMaxSize()
            .background(p.surface)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 8.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(FiArrowLeft, stringResource(R.string.action_back), tint = p.ink, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.records_title),
                style = FitTypography.titleMedium,
                color = p.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.action_clear_all),
                style = FitTypography.labelSmall,
                color = p.ink4,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onClearAll() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        HairLine()

        val groups = buildList {
            // 排最前的那节先加，其余按固定顺序，保证「从安装进来」和「从下载进来」
            // 看到的第一屏不同但内容一致
            focus?.let { f -> add(f to SECTIONS.first { it.kind == f }) }
            SECTIONS.forEach { if (it.kind != focus) add(it.kind to it) }
        }

        if (entries.none { it.kind != HistoryEntry.Kind.RepoViewed }) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.records_empty_title), style = FitTypography.titleMedium, color = p.ink)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.records_empty_1) + "\n" +
                        stringResource(R.string.history_local_only),
                    style = FitTypography.bodyMedium,
                    color = p.ink3,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = TabBarScrimHeight + 24.dp),
        ) {
            item(key = "note") {
                Text(
                    stringResource(R.string.history_local_only_clear),
                    style = FitTypography.bodySmall,
                    color = p.ink4,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            for ((kind, meta) in groups) {
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
                        Text(stringResource(meta.label), style = Eyebrow, color = p.ink4)
                        Text(stringResource(R.string.history_count, group.size), style = MonoMeta, color = p.ink4)
                    }
                }
                items(group.size, key = { "$kind-${group[it].ref}-${group[it].at}" }) { i ->
                    val e = group[i]
                    // 「下载过」这一节能对上清单的才给动作。
                    //
                    // 用**整串**匹配而不是只比 assetName：记录的 ref 存的就是
                    // `repoId/assetName`，而不同仓库的产物重名很常见（几乎每家
                    // 都发 `app-release.apk`）。只比名字会把另一个仓库的包挂上来，
                    // 用户点「安装」装到的东西和这一行显示的对不上。
                    val entry = if (e.kind == HistoryEntry.Kind.Downloaded) {
                        library.firstOrNull { it.key == e.ref }
                    } else {
                        null
                    }
                    RecordRow(
                        e,
                        meta.icon,
                        entry = entry,
                        onClear = { onClearOne(e) },
                        onNotifyChanged = onNotifyChanged,
                    )
                }
            }
        }
    }
}

private class SectionMeta(val kind: HistoryEntry.Kind, @StringRes val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val SECTIONS = listOf(
    SectionMeta(HistoryEntry.Kind.Downloaded, R.string.records_kind_downloaded, FiDownload),
    SectionMeta(HistoryEntry.Kind.InstalledViaUs, R.string.history_kind_installed, FiPackage),
    SectionMeta(HistoryEntry.Kind.AssetParsed, R.string.history_kind_parsed, FiCheck),
)

@Composable
private fun RecordRow(
    e: HistoryEntry,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    /** 对得上的已下载条目。为 null = 这一行只是文字，不可点 */
    entry: DownloadedApk? = null,
    onClear: () -> Unit,
    onNotifyChanged: () -> Unit = {},
) {
    val p = FitTheme.palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val action = actionFor(context, entry)

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = entry != null) { onRunRecordAction(context, entry, action, onNotifyChanged) }
                .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(p.wash),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = p.ink3, modifier = Modifier.size(15.dp))
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
                if (e.detail.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        e.detail,
                        style = FitTypography.bodySmall,
                        color = p.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(formatAt(e.at), style = MonoMeta, color = p.ink4)
            }
            Spacer(Modifier.width(6.dp))
            // 这一行的下一步动作。有清单才画：画一个点不动的按钮比不画更糟。
            // NONE（不是安装包）连字都不画 —— 它没有下一步
            if (entry != null && action != ApkAction.NONE) {
                Text(
                    stringResource(
                        when (action) {
                            ApkAction.OPEN -> R.string.action_open
                            ApkAction.INSTALL -> R.string.action_install
                            ApkAction.DOWNLOAD, ApkAction.NONE -> R.string.action_remove
                        },
                    ),
                    style = FitTypography.labelSmall,
                    color = p.accent,
                    modifier = Modifier.padding(6.dp),
                )
            }
            Text(
                stringResource(R.string.action_remove),
                style = FitTypography.labelSmall,
                color = p.ink4,
                modifier = Modifier.clickable { onClear() }.padding(6.dp),
            )
        }
        HairLine(Modifier.padding(start = 62.dp))
    }
}

/**
 * 「下载与安装记录」里点一行会发生什么。
 *
 * 三态和详情页那枚按钮完全一致（见 `actionFor`）：没下过就没什么可按的、
 * 下过没装就装、装上了就打开。文件被用户从下载目录里删掉时说清楚是文件没了，
 * 而不是报成「安装失败」—— 那两个是完全不同的问题。
 */
private fun onRunRecordAction(
    context: Context,
    entry: DownloadedApk?,
    action: ApkAction,
    onNotifyChanged: () -> Unit,
) {
    if (entry == null) return
    when (action) {
        ApkAction.OPEN -> {
            if (!ApkInstaller.launch(context, entry.packageName)) {
                Toast.makeText(context, R.string.action_launch_failed, Toast.LENGTH_LONG).show()
            }
        }

        ApkAction.INSTALL -> {
            val file = entry.file()
            if (!file.exists() || file.length() <= 0L) {
                Toast.makeText(context, R.string.action_file_missing, Toast.LENGTH_LONG).show()
                return
            }
            if (!ApkInstaller.canRequestInstall(context)) {
                Toast.makeText(context, R.string.install_error_no_permission, Toast.LENGTH_LONG).show()
                return
            }
            ApkInstaller.install(context, file) { ok, message ->
                val text = if (ok) {
                    ApkLibrary.setInstalled(context, entry.key, true)
                    onNotifyChanged()
                    context.getString(R.string.install_ok)
                } else {
                    context.getString(R.string.install_error_failed, message)
                }
                // PackageInstaller 的回调在 binder 线程上，Toast 得回主线程
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                }
            }
        }

        ApkAction.DOWNLOAD, ApkAction.NONE -> Unit
    }
}

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

private fun formatAt(ms: Long): String =
    TIME_FMT.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
