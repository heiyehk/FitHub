package com.heiyehk.fithub.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heiyehk.fithub.R
import com.heiyehk.fithub.data.Async
import com.heiyehk.fithub.data.FitRepository
import com.heiyehk.fithub.data.FitRepository.FileBody
import com.heiyehk.fithub.data.remote.ContentEntryDto
import com.heiyehk.fithub.ui.components.GhostButton
import com.heiyehk.fithub.ui.components.HairLine
import com.heiyehk.fithub.ui.icons.FiArrowLeft
import com.heiyehk.fithub.ui.icons.FiExternal
import com.heiyehk.fithub.ui.icons.FiFolder
import com.heiyehk.fithub.ui.icons.FiFile
import com.heiyehk.fithub.ui.theme.FitTheme
import com.heiyehk.fithub.ui.theme.FitTypography
import com.heiyehk.fithub.ui.theme.MonoMeta
import kotlinx.coroutines.launch

/**
 * 仓库代码浏览。
 *
 * ## 为什么用面包屑而不是返回键退目录
 *
 * 详情面板的返回键是「关掉整个面板」，那是它的语义，不该被目录层级劫持 ——
 * 用户在子目录里按返回，本来想退出 App，结果被留在原地，或者反过来。
 * 所以退目录走面包屑：每一级都能点，直接跳上去，GitHub 上也是这么做的。
 *
 * ## 为什么一个目录一次请求
 *
 * 用 `contents/{path}` 而不是 `git/trees?recursive=1`：后者一次给整棵树，
 * 但超大仓库会 `truncated = true`，而**截断了用户看不出来** —— 建在一个残缺的
 * 列表上，点进某个目录发现是空的，没有任何提示能解释为什么。
 *
 * 代价是翻三层目录要三次请求。目录结果落 6 小时缓存（[FitRepository.contentsDir]），
 * 回退时不再花配额 —— 未登录只有 60 次/小时，这是整个 App 最紧的资源。
 */
@Composable
fun CodeBrowser(
    fullName: String,
    repo: FitRepository,
    modifier: Modifier = Modifier,
) {
    val p = FitTheme.palette
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    /** 当前目录，空串 = 仓库根 */
    var dir by remember(fullName) { mutableStateOf("") }
    var listing by remember(fullName) { mutableStateOf<Async<List<ContentEntryDto>>>(Async.Loading) }

    /** 正在看的文件路径；非 null 时整屏换成文件视图 */
    var openPath by remember(fullName) { mutableStateOf<String?>(null) }
    var fileBody by remember(fullName) { mutableStateOf<Async<FileBody>>(Async.Loading) }

    // 切目录才拉，留在同一目录不重复请求
    LaunchedEffect(fullName, dir) {
        listing = Async.Loading
        listing = repo.contentsDir(fullName, dir)
    }

    fun openFile(path: String) {
        openPath = path
        fileBody = Async.Loading
        scope.launch { fileBody = repo.contentsFile(fullName, path) }
    }

    Column(modifier.fillMaxSize().background(p.surface)) {
        // 面包屑。看文件时藏起来 —— 那时整屏都是那个文件，留着面包屑只会让人想去点别的目录。
        if (openPath == null) {
            Breadcrumb(
                fullName = fullName,
                path = dir,
                onPick = { target ->
                    dir = target
                },
            )
            HairLine()
        }

        val viewing = openPath
        if (viewing != null) {
            FileView(
                path = viewing,
                body = fileBody,
                onBack = { openPath = null },
                onOpenInGitHub = { url ->
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url),
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
        } else {
            DirectoryView(
                listing = listing,
                onOpenDir = { path ->
                    dir = path
                },
                onOpenFile = ::openFile,
                onRetry = {
                    scope.launch {
                        listing = Async.Loading
                        listing = repo.contentsDir(fullName, dir)
                    }
                },
            )
        }
    }
}

/**
 * 面包屑。每一级都可点，跳到那一级对应的目录。
 *
 * 横向滚动：深路径（`app/src/main/java/com/example/...`）在窄屏上放不下，
 * 挤成一行会让后面几级完全看不见。
 *
 * ## 仓库名那一级必须能点
 *
 * 仓库名**不是「它自己」，它就是仓库根目录** —— 那是每一层往上退的终点。
 * 之前把它标成不可点（注释写的是「点自己没有意义」），于是只有两层
 * （仓库名 + 一个子目录）时 `1 until lastIndex` 是空区间，**一个都点不了**，
 * 从任何第一层子目录都回不到根。
 *
 * 只有**当前所在的那一级**不可点 —— 你已经在那里了。
 */
@Composable
private fun Breadcrumb(fullName: String, path: String, onPick: (String) -> Unit) {
    val p = FitTheme.palette
    /**
     * 每项存的是**路径**而不是显示名。
     *
     * 存显示名的话，仓库名那一项就是 "flutter" 而不是 ""，点下去会把
     * `dir` 设成 `"flutter"` —— 于是去请求 `contents/flutter`，
     * 那个目录不存在。显示名和路径混在一份列表里是这类 bug 的来源。
     */
    val segments = remember(fullName, path) {
        buildList {
            add("")                                   // 仓库根
            if (path.isNotBlank()) {
                var acc = ""
                path.split('/').filter { it.isNotBlank() }.forEach {
                    acc = if (acc.isEmpty()) it else "$acc/$it"
                    add(acc)
                }
            }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { index, seg ->
            if (index > 0) {
                Text("/", style = MonoMeta, color = p.ink4)
                Spacer(Modifier.width(2.dp))
            }
            // 只有「当前所在的那一级」不可点 —— 你已经在那里了。
            // 其余每一级（含代表仓库根的仓库名）都能点，这才是能退回去的前提。
            val clickable = index < segments.lastIndex
            Text(
                // 第 0 项的 seg 是空串，显示仓库名
                text = if (index == 0) fullName.substringAfter('/') else seg.substringAfterLast('/'),
                style = FitTypography.labelLarge,
                color = if (clickable) p.accent else p.ink2,
                maxLines = 1,
                modifier = if (clickable) {
                    Modifier
                        .clip(CircleShape)
                        .clickable { onPick(seg) }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                } else {
                    Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                },
            )
        }
    }
}

@Composable
private fun DirectoryView(
    listing: Async<List<ContentEntryDto>>,
    onOpenDir: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val p = FitTheme.palette
    when (listing) {
        is Async.Loading -> Column(Modifier.padding(20.dp)) {
            repeat(6) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(p.washDeep),
                )
            }
        }

        is Async.Err -> Column(Modifier.padding(20.dp)) {
            Text(listing.message, style = FitTypography.bodyMedium, color = p.ink2)
            Spacer(Modifier.height(12.dp))
            GhostButton(stringResource(R.string.person_retry), onRetry)
        }

        is Async.Ok -> {
            val entries = remember(listing.value) {
                // 目录在前、文件在后，各自按名字排。
                // GitHub 返回的是 ASCII 序，大写全在小写前面（「Zebra」排在「android」前），
                // 那种顺序在中文界面上看着是乱的，所以自己按不区分大小写排。
                listing.value.sortedWith(
                    compareByDescending<ContentEntryDto> { it.type != "dir" }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
                )
            }
            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.code_empty_dir),
                    style = FitTypography.bodyMedium,
                    color = p.ink4,
                    modifier = Modifier.padding(20.dp),
                )
                return
            }
            // 用 Column 而不是 LazyColumn：这个组件是详情页那个 LazyColumn 里的**一个 item**，
            // 同方向再嵌一个 LazyColumn 是拿不到高度的（约束无界），而且滚动会打架。
            // 一个目录最多一千项（GitHub 的上限），普通源码目录只有几十条，
            // 一次性全组合出来不构成问题 —— 滚动交给外层。
            Column {
                entries.forEach { e ->
                    val isDir = e.type == "dir"
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { if (isDir) onOpenDir(e.path) else onOpenFile(e.path) }
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isDir) FiFolder else FiFile,
                            null,
                            tint = if (isDir) p.accent else p.ink4,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(11.dp))
                        Text(
                            e.name,
                            style = FitTypography.bodyMedium,
                            color = if (isDir) p.ink else p.ink2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (!isDir && e.size > 0) {
                            Text(formatBytes(e.size), style = MonoMeta, color = p.ink4)
                        }
                    }
                    HairLine(Modifier.padding(start = 44.dp))
                }
            }
        }
    }
}

@Composable
private fun FileView(
    path: String,
    body: Async<FileBody>,
    onBack: () -> Unit,
    onOpenInGitHub: (String) -> Unit,
) {
    val p = FitTheme.palette
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.heiyehk.fithub.ui.components.IconCircleButton(
                FiArrowLeft,
                stringResource(R.string.action_back),
                onBack,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                path.substringAfterLast('/'),
                style = FitTypography.titleSmall,
                color = p.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (body is Async.Ok && body.value.htmlUrl != null) {
                com.heiyehk.fithub.ui.components.IconCircleButton(
                    icon = FiExternal,
                    contentDescription = stringResource(R.string.code_open_in_github),
                    onClick = { onOpenInGitHub(body.value.htmlUrl) },
                )
            }
        }
        HairLine()

        when (body) {
            is Async.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.code_loading_file), style = FitTypography.bodySmall, color = p.ink4)
            }

            is Async.Err -> Text(
                body.message,
                style = FitTypography.bodyMedium,
                color = p.ink2,
                modifier = Modifier.padding(20.dp),
            )

            is Async.Ok -> {
                val text = body.value.text
                if (text == null) {
                    // 二进制、超大、GitHub 根本不给内容 —— 三种都归到「去 GitHub 看」。
                    // 如实说清楚是哪一种，而不是笼统说「打不开」。
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            stringResource(
                                if (body.value.sizeBytes > FileBody.MAX_TEXT_BYTES) {
                                    R.string.code_too_large
                                } else {
                                    R.string.code_not_text
                                },
                                formatBytes(body.value.sizeBytes),
                            ),
                            style = FitTypography.bodyMedium,
                            color = p.ink2,
                        )
                        Spacer(Modifier.height(12.dp))
                        body.value.htmlUrl?.let { url ->
                            GhostButton(
                                stringResource(R.string.code_open_in_github),
                                { onOpenInGitHub(url) },
                                icon = FiExternal,
                            )
                        }
                    }
                } else {
                    // 等宽字体 + **不自动折行**：源码的行结构本身就是信息，
                    // 折行会把缩进层级搞乱，读起来比不分行的还难。
                    // 横向滚动交给外层那个 LazyColumn 的兄弟方向 —— 这里给一个
                    // 宽出屏幕的固定宽度，靠外层横滑。
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text,
                            style = FitTypography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = p.ink2,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1048576 -> "${(bytes / 1048576.0 * 10).toInt() / 10.0} MB"
    bytes >= 1024 -> "${(bytes / 1024.0 * 10).toInt() / 10.0} KB"
    else -> "$bytes B"
}