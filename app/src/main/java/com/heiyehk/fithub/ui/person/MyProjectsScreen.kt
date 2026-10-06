package com.heiyehk.fithub.ui.person

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heiyehk.fithub.R
import com.heiyehk.fithub.data.Async
import com.heiyehk.fithub.data.Env
import com.heiyehk.fithub.data.MyRepos
import com.heiyehk.fithub.data.Repo
import com.heiyehk.fithub.data.RepoTotals
import com.heiyehk.fithub.data.remote.UserDto
import com.heiyehk.fithub.ui.InstallCheck
import com.heiyehk.fithub.ui.components.FitBadge
import com.heiyehk.fithub.ui.components.GhostButton
import com.heiyehk.fithub.ui.components.HairLine
import com.heiyehk.fithub.ui.components.IconCircleButton
import com.heiyehk.fithub.ui.components.MetaRow
import com.heiyehk.fithub.ui.components.RemoteAvatar
import com.heiyehk.fithub.ui.components.StaleNotice
import com.heiyehk.fithub.ui.components.StaggeredItem
import com.heiyehk.fithub.ui.components.stalestOf
import com.heiyehk.fithub.ui.icons.FiArrowLeft
import com.heiyehk.fithub.ui.theme.FitTheme
import com.heiyehk.fithub.ui.theme.FitTone
import com.heiyehk.fithub.ui.theme.FitTypography
import com.heiyehk.fithub.ui.theme.MonoMeta

/**
 * 「我的项目」页。
 *
 * 数据源是 `/user/repos?type=owner`，也就是**登录账号自己名下**的仓库 ——
 * 和公开的 [PersonScreen] 不是一回事，所以不能拿那一页顶替：
 * 那边读 `/users/{login}/repos`，既拿不到订阅数，也永远看不到私有仓库。
 *
 * 这一页同时负责 star / fork / watch 的汇总，因为汇总和列表是**同一批请求**：
 * 拆成两个接口的话，切来切去会各拉一遍，两个页面还可能显示不同时间点的数字。
 *
 * 汇总数字的边界写在头部下方（私有仓库没计入 / 只翻了一部分 / watch 缺了一块），
 * 因为这些数字是「遍历自己的仓库加总」算出来的，**不是** GitHub 直接给的总数。
 */
@Composable
fun MyProjectsScreen(
    me: UserDto?,
    data: Async<MyRepos>,
    installable: Map<String, InstallCheck>,
    /** 实际授予的 scope，用于如实显示「当前权限」 */
    grantedScopes: String?,
    /** 由调用方注入 `api::avatar`，免得 UI 自己建客户端 */
    avatarLoader: suspend (String) -> ByteArray?,
    onCheck: (Repo) -> Unit,
    onCheckAll: (List<Repo>) -> Unit,
    onRepoTap: (Repo) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val p = FitTheme.palette
    var sort by remember { mutableStateOf(RepoSort.Updated) }
    val sorted = remember(data, sort) {
        sortedRepos((data as? Async.Ok)?.value?.repos.orEmpty(), sort)
    }

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
            Text(
                stringResource(R.string.myprojects_title),
                style = FitTypography.titleMedium,
                color = p.ink,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            GhostButton(stringResource(R.string.myprojects_refresh), onRefresh, height = 32.dp)
        }
        HairLine()

        val cachedAge = stalestOf(data)
        when (val d = data) {
            is Async.Loading -> RepoListSkeleton()

            is Async.Err -> Column(Modifier.padding(24.dp)) {
                Text(d.message, style = FitTypography.bodyMedium, color = p.ink2)
                Spacer(Modifier.height(12.dp))
                GhostButton(stringResource(R.string.person_retry), onRefresh)
            }

            is Async.Ok -> LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                item(key = "head") {
                    MyHeader(
                        me = me,
                        totals = d.value.totals,
                        complete = d.value.complete,
                        grantedScopes = grantedScopes,
                        avatarLoader = avatarLoader,
                    )
                }

                if (cachedAge != null) {
                    item(key = "stale") {
                        StaleNotice(cachedAge, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    }
                }

                item(key = "sort") {
                    RepoListToolbar(sort, { sort = it }, onCheckAll = { onCheckAll(sorted) })
                }

                if (sorted.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.myprojects_empty),
                            style = FitTypography.bodyMedium,
                            color = p.ink4,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                } else {
                    itemsIndexed(sorted, key = { _, r -> "mr-${r.id}" }) { index, repo ->
                        StaggeredItem(index) {
                            RepoRow(
                                repo = repo,
                                installState = installable[repo.id],
                                onCheck = { onCheck(repo) },
                                onClick = { onRepoTap(repo) },
                                metaSuffix = stringResource(
                                    R.string.myprojects_repo_counts,
                                    repo.forks,
                                    Env.formatStars(repo.watchers),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 头部：头像 + 名字 + 四个汇总数 + 数据的边界说明。
 *
 * 边界说明不是可选的装饰：[RepoTotals] 是遍历加总出来的，
 * 少统计了什么必须写出来，否则这三个数字会被当成「GitHub 上的官方总数」。
 */
@Composable
private fun MyHeader(
    me: UserDto?,
    totals: RepoTotals,
    complete: Boolean,
    grantedScopes: String?,
    avatarLoader: suspend (String) -> ByteArray?,
) {
    val p = FitTheme.palette
    Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteAvatar(
                url = me?.avatarUrl.orEmpty(),
                monogram = me?.name?.take(2) ?: me?.login?.take(2) ?: "?",
                loader = avatarLoader,
                size = 58.dp,
                corner = 29.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    me?.name?.takeIf { it.isNotBlank() } ?: me?.login.orEmpty(),
                    style = FitTypography.headlineSmall,
                    color = p.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!me?.login.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("@${me?.login}", style = MonoMeta, color = p.ink4, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        CountTriple(
            cells = listOf(
                totals.repos.toString() to stringResource(R.string.ghstat_repos),
                Env.formatStars(totals.stars) to stringResource(R.string.ghstat_stars),
                Env.formatStars(totals.forks) to stringResource(R.string.ghstat_forks),
                Env.formatStars(totals.watchers) to stringResource(R.string.ghstat_watchers),
            ),
        )

        Spacer(Modifier.height(14.dp))
        // 每条都在说同一件事：这个数字覆盖了什么、没覆盖什么。
        // 分开成独立的一行而不是揉成一句，是为了让用户能对上自己知道的情况。
        if (!complete) {
            NoteLine(FitTone.Warn, stringResource(R.string.ghstat_partial, totals.repos))
        }
        // me 为 null 是「资料没查回来」，不是「权限不给」—— 两者要说不同的话。
        // 那种情况下资料缺失本身已经在标题位置显出来了，不再补一条归因。
        val private = me?.ownedPrivateRepos
        when {
            private == null && me != null ->
                NoteLine(FitTone.Muted, stringResource(R.string.ghstat_private_unknown))

            private != null && private > 0 ->
                NoteLine(FitTone.Muted, stringResource(R.string.ghstat_private_hidden, private))
        }
        if (totals.watchUnknown > 0) {
            NoteLine(
                FitTone.Muted,
                stringResource(R.string.ghstat_watch_partial, totals.watchUnknown),
            )
        }
        if (!grantedScopes.isNullOrBlank()) {
            NoteLine(FitTone.Muted, stringResource(R.string.myprojects_scope_granted, grantedScopes))
        }
    }
}

/**
 * 边界说明的一行。
 *
 * 用小圆点 + 可折行的正文，而不是 [FitBadge]：徽标里的文字是 `maxLines = 1`，
 * 而这些说明天生就长（「另有 3 个私有仓库未计入 —— FitHub 只申请了只读权限」），
 * 塞进徽标会被截掉后半句，恰好截掉最要紧的那半句。
 */
@Composable
private fun NoteLine(tone: FitTone, text: String) {
    val p = FitTheme.palette
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.Top) {
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