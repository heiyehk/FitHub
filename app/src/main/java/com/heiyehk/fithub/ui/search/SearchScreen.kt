package com.heiyehk.fithub.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heiyehk.fithub.data.Async
import com.heiyehk.fithub.data.Person
import com.heiyehk.fithub.data.Repo
import com.heiyehk.fithub.data.SearchHistoryStore
import com.heiyehk.fithub.data.Env
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.heiyehk.fithub.R
import com.heiyehk.fithub.ui.components.AppTile
import com.heiyehk.fithub.ui.components.FitBadge
import com.heiyehk.fithub.ui.components.HairLine
import com.heiyehk.fithub.ui.components.MetaRow
import com.heiyehk.fithub.ui.components.Stars
import com.heiyehk.fithub.ui.components.StaleNotice
import com.heiyehk.fithub.ui.components.stalestOf
import com.heiyehk.fithub.ui.components.tap
import com.heiyehk.fithub.ui.icons.FiArrowRight
import com.heiyehk.fithub.ui.icons.FiClose
import com.heiyehk.fithub.ui.icons.FiSearch
import com.heiyehk.fithub.ui.theme.FitTheme
import com.heiyehk.fithub.ui.theme.FitTone
import com.heiyehk.fithub.ui.theme.FitTypography
import com.heiyehk.fithub.ui.theme.MonoMeta
import kotlinx.coroutines.delay

enum class SearchType(@StringRes val labelRes: Int) {
    Repo(R.string.search_type_repo),
    User(R.string.search_type_user),
    Org(R.string.search_type_org),
}

/**
 * 全屏搜索。
 *
 * 一个输入框，三类结果 Tab，300ms 防抖。
 * has: / language: / topic: 等语法原样透传给 GitHub。
 * 结果只展示有真实可下载产物的仓库。
 */
@Composable
fun SearchScreen(
    onClose: () -> Unit,
    onRepoTap: (Repo) -> Unit,
    onPersonTap: (String) -> Unit,
    onSearch: suspend (String) -> Async<List<Repo>>,
    onSearchPeople: suspend (String, Boolean) -> Async<List<Person>>,
) {
    val p = FitTheme.palette
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(SearchType.Repo) }
    var cur by remember { mutableStateOf(0) }
    var results by remember { mutableStateOf<Async<List<Repo>>>(Async.Loading) }
    var people by remember { mutableStateOf<Async<List<Person>>>(Async.Loading) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var lastQuery by remember { mutableStateOf("") }
    var lastPeople by remember { mutableStateOf("") }

    /**
     * 最近搜索。
     *
     * 记的是**真正发出去的那一次请求**，不是每次按键：300ms 防抖之后才落一条，
     * 所以「敲到一半停手」不会被记成一个用户没打算搜的词。
     */
    var history by remember { mutableStateOf(SearchHistoryStore.list(context)) }

    fun rememberQuery(q: String) {
        SearchHistoryStore.record(context, q)
        history = SearchHistoryStore.list(context)
    }

    // 300ms 防抖，只在查询词真的变了时才发请求
    LaunchedEffect(query, type) {
        if (query.isBlank()) return@LaunchedEffect
        delay(300)
        if (type == SearchType.Repo) {
            if (query != lastQuery) {
                lastQuery = query
                results = onSearch(query)
                rememberQuery(query)
            }
        } else if (query != lastPeople || people is Async.Loading) {
            lastPeople = query
            people = onSearchPeople(query, type == SearchType.Org)
            rememberQuery(query)
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.surface)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(p.wash)
                    .border(1.dp, p.hairline, CircleShape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(FiSearch, null, tint = p.ink4, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        cur = 0
                    },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    singleLine = true,
                    textStyle = FitTypography.titleMedium.copy(color = p.ink),
                    cursorBrush = SolidColor(p.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.search_placeholder),
                                style = FitTypography.titleMedium,
                                color = p.ink4,
                            )
                        }
                        inner()
                    },
                )
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn(tween(120)),
                    exit = fadeOut(tween(120)),
                ) {
                    Icon(
                        FiClose,
                        stringResource(R.string.action_clear),
                        tint = p.ink4,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .tap { query = "" }
                            .padding(5.dp),
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .tap { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(FiClose, stringResource(R.string.search_close), tint = p.ink2, modifier = Modifier.size(19.dp))
            }
        }

        /* 结果类型 Tab */
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchType.entries.forEach { t ->
                val active = t == type
                // 角标只数服务端返回的结果，不在本地过滤
                val count = when {
                    query.isBlank() -> 0
                    t != SearchType.Repo -> 0
                    else -> (results as? Async.Ok)?.value?.size ?: 0
                }
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(if (active) p.ink else p.surface)
                        .border(1.dp, if (active) p.ink else p.hairline, CircleShape)
                        .tap { type = t; cur = 0 }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(t.labelRes), style = FitTypography.labelLarge, color = if (active) p.surface else p.ink2)
                    if (count > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            count.toString(),
                            style = MonoMeta,
                            color = if (active) p.surface.copy(alpha = 0.6f) else p.ink4,
                        )
                    }
                }
            }
        }

        HairLine()

        // 结果命中缓存时写明时间。这一屏是 Column 而非 LazyListScope，可以直接调
        val cachedAge = stalestOf(if (type == SearchType.Repo) results else people)
        if (cachedAge != null) {
            StaleNotice(cachedAge, Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
        }

        if (query.isBlank()) {
            // 最近搜索排在语法引导前面：搜过的人回来是要接着搜上一次那个词，
            // 不是来学 GitHub 搜索语法的。
            //
            // 两块合成**一个** LazyColumn：语法引导那部分自己就是 LazyColumn，
            // 把它单独放进 Column 会拿到无限高约束直接崩。
            SearchEmptyState(
                history = history,
                onPick = { query = it },
                onClear = {
                    SearchHistoryStore.clear(context)
                    history = emptyList()
                },
            )
        } else if (type != SearchType.Repo) {
            when (val ps = people) {
                is Async.Loading -> LoadingBlock()
                is Async.Err -> EmptyResult(ps.message, stringResource(R.string.search_hint_keyword))
                is Async.Ok -> if (ps.value.isEmpty()) {
                    EmptyResult(
                        stringResource(R.string.search_empty_people, stringResource(type.labelRes)),
                        stringResource(R.string.search_hint_keyword),
                    )
                } else {
                    PeopleResults(ps.value, onPersonTap)
                }
            }
        } else {
            when (val r = results) {
                is Async.Loading -> LoadingBlock()
                is Async.Err -> EmptyResult(r.message, stringResource(R.string.search_hint_drop_syntax))
                is Async.Ok -> if (r.value.isEmpty()) {
                    EmptyResult(
                        stringResource(R.string.search_empty_repos),
                        stringResource(R.string.search_hint_drop_syntax_alt),
                    )
                } else {
                    RepoResults(r.value, onRepoTap)
                }
            }
        }
    }
}

@Composable
private fun PeopleResults(list: List<Person>, onPersonTap: (String) -> Unit) {
    val p = FitTheme.palette
    LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
        items(list, key = { "p-${it.handle}" }) { person ->
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .tap { onPersonTap(person.handle) }
                        .padding(horizontal = 20.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppTile(
                        monogram = person.name.take(2),
                        background = person.tileBg,
                        foreground = person.tileFg,
                        size = 44.dp,
                        corner = 22.dp,
                    )
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                person.name,
                                style = FitTypography.titleSmall,
                                color = p.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (person.hireable) {
                                Spacer(Modifier.width(7.dp))
                                FitBadge(FitTone.Ok, stringResource(R.string.search_hireable))
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "@${person.handle} · ${person.bio}",
                            style = FitTypography.bodySmall,
                            color = p.ink3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    // /search/users 不返回 followers，「未返回」不等于 0，所以这里不显示；
                    // 真值要进主体页拿完整 profile 才有。
                    Text(
                        stringResource(R.string.search_view),
                        style = MonoMeta,
                        color = p.ink4,
                    )
                }
                HairLine(Modifier.padding(start = 77.dp))
            }
        }
    }
}

@Composable
private fun LoadingBlock() {
    val p = FitTheme.palette
    Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
        repeat(4) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.washDeep),
            )
        }
    }
}

@Composable
private fun RepoResults(list: List<Repo>, onRepoTap: (Repo) -> Unit) {
    val p = FitTheme.palette
    LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
        items(list, key = { "sr-${it.id}" }) { repo ->
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .tap { onRepoTap(repo) }
                        .padding(horizontal = 20.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppTile(repo.monogram, repo.tileBg, repo.tileFg)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(repo.name, style = FitTypography.titleSmall, color = p.ink, maxLines = 1)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${repo.owner} · ${repo.lang} · ${repo.date}",
                            style = MonoMeta,
                            color = p.ink4,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Stars(Env.formatStars(repo.stars))
                }
                HairLine(Modifier.padding(start = 79.dp))
            }
        }
    }
}


@Composable
private fun EmptyResult(title: String, hint: String) {
    val p = FitTheme.palette
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = FitTypography.titleMedium, color = p.ink2)
        Spacer(Modifier.height(8.dp))
        Text(hint, style = FitTypography.bodySmall, color = p.ink4)
    }
}

/**
 * 空查询时的引导。
 *
 * 只列 GitHub 搜索语法，点一下原样透传给 search API，
 * 展示的每一条都是接口返回的结果。
 *
 * 原本第一条是 `has:release`，已删。**GitHub 仓库搜索没有这个限定符** ——
 * 输入它不会报错、不过滤、也不提示（实测：加与不加结果数完全相同），
 * 等于在教用户一个假语法。真正的「有安装包」只能逐仓库查 /releases，
 * 搜索框给不了。理由与实测数据见 `FitRepository.baseQuery` 的注释。
 */
private val SYNTAX_HINTS = listOf(
    "language:kotlin" to R.string.search_syntax_language,
    "topic:compose" to R.string.search_syntax_topic,
    "stars:>5000" to R.string.search_syntax_stars,
    "archived:false" to R.string.search_syntax_archived,
)

/**
 * 空查询时的整屏：最近搜索 + GitHub 搜索语法引导。
 *
 * 合成**一个** LazyColumn 而不是两个竖着摞：下面那块自己就是 LazyColumn，
 * 放进 Column 会拿到无限高约束直接崩。两块必须是同一个 LazyColumn 的两个 item。
 */
@Composable
private fun SearchEmptyState(
    history: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    val p = FitTheme.palette
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        if (history.isNotEmpty()) {
            item(key = "history-head") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.search_history_title),
                        style = FitTypography.titleSmall,
                        color = p.ink,
                        modifier = Modifier.weight(1f),
                    )
                    // 词是可能被设备上别人看到的东西（应用列表、剪贴板建议），
                    // 所以清空必须一眼找得到，而不是塞进设置里。
                    Text(
                        stringResource(R.string.search_history_clear),
                        style = FitTypography.labelLarge,
                        color = p.ink4,
                        modifier = Modifier
                            .clip(CircleShape)
                            .tap { onClear() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            items(history, key = { "h-$it" }) { q ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .tap { onPick(q) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(FiSearch, null, tint = p.ink4, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(11.dp))
                    Text(
                        q,
                        style = FitTypography.bodyMedium,
                        color = p.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                HairLine(Modifier.padding(start = 46.dp))
            }
            item(key = "history-gap") { Spacer(Modifier.height(10.dp)) }
        }

        item(key = "syntax") {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                Text(stringResource(R.string.search_syntax_title), style = FitTypography.titleSmall, color = p.ink)
                Spacer(Modifier.height(12.dp))
                SYNTAX_HINTS.forEach { (code, desc) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .tap { onPick(code) }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(p.washDeep)
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                        ) {
                            Text(code, style = MonoMeta, color = p.ink2)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(desc), style = FitTypography.bodySmall, color = p.ink4, modifier = Modifier.weight(1f))
                        Icon(FiArrowRight, null, tint = p.ink4, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.search_syntax_note),
                    style = FitTypography.bodySmall,
                    color = p.ink4,
                )
            }
        }
    }
}
