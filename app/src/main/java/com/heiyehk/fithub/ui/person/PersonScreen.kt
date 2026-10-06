package com.heiyehk.fithub.ui.person

import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heiyehk.fithub.data.Async
import com.heiyehk.fithub.data.Env
import com.heiyehk.fithub.data.Repo
import com.heiyehk.fithub.data.remote.UserDto
import com.heiyehk.fithub.R
import com.heiyehk.fithub.ui.InstallCheck
import com.heiyehk.fithub.ui.components.AppTile
import com.heiyehk.fithub.ui.components.FitBadge
import com.heiyehk.fithub.ui.components.GhostButton
import com.heiyehk.fithub.ui.components.HairLine
import com.heiyehk.fithub.ui.components.IconCircleButton
import com.heiyehk.fithub.ui.components.MetaRow
import com.heiyehk.fithub.ui.components.StaleNotice
import com.heiyehk.fithub.ui.components.StaggeredItem
import com.heiyehk.fithub.ui.components.stalestOf
import com.heiyehk.fithub.ui.icons.FiArrowLeft
import com.heiyehk.fithub.ui.theme.FitTheme
import com.heiyehk.fithub.ui.theme.FitTone
import com.heiyehk.fithub.ui.theme.FitTypography
import com.heiyehk.fithub.ui.theme.MonoMeta

/**
 * 公开的用户 / 组织页。
 *
 * 数据源是 `/users/{login}/repos` 和 `/users/{login}`，所以这里**只可能有
 * 公开仓库**，也拿不到订阅数一类的私有计数。要看自己的（含汇总）走
 * `MyProjectsScreen`，两者共用 [RepoRow] / [RepoListToolbar] 这套列表件。
 *
 * 列表接口不返回 release 信息，可安装状态只能逐个仓库查，所以按需发请求：
 * 用户点「检查安装包」才查，不在一进页面时把配额打满。未检查的显示「未检查」。
 */
@Composable
fun PersonScreen(
    login: String,
    profile: Async<UserDto>,
    repos: Async<List<Repo>>,
    installable: Map<String, InstallCheck>,
    onCheck: (Repo) -> Unit,
    onCheckAll: (List<Repo>) -> Unit,
    onRepoTap: (Repo) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val p = FitTheme.palette
    var sort by remember { mutableStateOf(RepoSort.Updated) }
    val sorted = remember(repos, sort) { sortRepos(repos, sort) }

    Column(modifier.fillMaxSize().background(p.surface)) {
        // 顶栏：只有返回，不放软件名
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(FiArrowLeft, stringResource(R.string.action_back), onClick = onBack)
            Spacer(Modifier.width(6.dp))
            Text("@$login", style = FitTypography.titleMedium, color = p.ink, maxLines = 1)
        }
        HairLine()

        // 主体信息与仓库列表任一命中缓存就要说明时间。
        // 算在 LazyColumn 之外：LazyListScope 不是 @Composable 上下文。
        val cachedAge = stalestOf(profile, repos)

        when (val prof = profile) {
            is Async.Loading -> RepoListSkeleton()

            is Async.Err -> Column(Modifier.padding(24.dp)) {
                Text(prof.message, style = FitTypography.bodyMedium, color = p.ink2)
                Spacer(Modifier.height(12.dp))
                GhostButton(stringResource(R.string.person_retry), onBack)
            }

            is Async.Ok -> LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                item(key = "head") { ProfileHeader(prof.value) }

                if (cachedAge != null) {
                    item(key = "stale") {
                        StaleNotice(cachedAge, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    }
                }

                item(key = "sort") {
                    RepoListToolbar(sort, { sort = it }, onCheckAll = { onCheckAll(sorted) })
                }

                when (repos) {
                    is Async.Loading -> item(key = "rl") { RepoListSkeleton() }

                    is Async.Err -> item(key = "re") {
                        Text(
                            repos.message,
                            style = FitTypography.bodyMedium,
                            color = p.ink4,
                            modifier = Modifier.padding(20.dp),
                        )
                    }

                    is Async.Ok -> if (sorted.isEmpty()) {
                        item(key = "rempty") {
                            Text(
                                stringResource(R.string.person_no_repos),
                                style = FitTypography.bodyMedium,
                                color = p.ink4,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    } else {
                        itemsIndexed(sorted, key = { _, r -> "pr-${r.id}" }) { index, repo ->
                            StaggeredItem(index) {
                                RepoRow(
                                    repo = repo,
                                    installState = installable[repo.id],
                                    onCheck = { onCheck(repo) },
                                    onClick = { onRepoTap(repo) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(user: UserDto) {
    val p = FitTheme.palette
    val isOrg = user.type == "Organization"
    Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppTile(
                monogram = user.name?.take(2) ?: user.login.take(2),
                background = if (isOrg) 0xFFEAEEEE else 0xFFE9EDF7,
                foreground = if (isOrg) 0xFF2B4B4B else 0xFF2F3E77,
                size = 58.dp,
                corner = 29.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    user.name?.takeIf { it.isNotBlank() } ?: user.login,
                    style = FitTypography.headlineSmall,
                    color = p.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                MetaRow {
                    Text("@${user.login}", style = MonoMeta, color = p.ink4)
                    FitBadge(
                        if (isOrg) FitTone.Prerelease else FitTone.Muted,
                        stringResource(if (isOrg) R.string.person_kind_org else R.string.person_kind_user),
                    )
                    if (user.hireable == true) {
                        FitBadge(FitTone.Ok, stringResource(R.string.person_hireable))
                    }
                }
            }
        }
        if (!user.bio.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(user.bio, style = FitTypography.bodyMedium, color = p.ink2)
        }
        Spacer(Modifier.height(14.dp))
        MetaRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp)) {
            Stat(stringResource(R.string.person_stat_repos), user.publicRepos.toString())
            Stat(stringResource(R.string.person_stat_followers), Env.formatStars(user.followers))
            Stat(stringResource(R.string.person_stat_following), user.following.toString())
            if (!user.location.isNullOrBlank()) {
                Stat(stringResource(R.string.person_stat_location), user.location)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    val p = FitTheme.palette
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = FitTypography.titleSmall, color = p.ink)
        Spacer(Modifier.width(4.dp))
        Text(label, style = FitTypography.labelSmall, color = p.ink4)
    }
}