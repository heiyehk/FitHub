package com.heiyehk.fithub.ui.person

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heiyehk.fithub.R
import com.heiyehk.fithub.data.Async
import com.heiyehk.fithub.data.Env
import com.heiyehk.fithub.data.Repo
import com.heiyehk.fithub.ui.InstallCheck
import com.heiyehk.fithub.ui.components.AppTile
import com.heiyehk.fithub.ui.components.CircularProgress
import com.heiyehk.fithub.ui.components.FitBadge
import com.heiyehk.fithub.ui.components.GhostButton
import com.heiyehk.fithub.ui.components.HairLine
import com.heiyehk.fithub.ui.components.MetaRow
import com.heiyehk.fithub.ui.components.StaggeredItem
import com.heiyehk.fithub.ui.components.Stars
import com.heiyehk.fithub.ui.components.tap
import com.heiyehk.fithub.ui.icons.FiAlert
import com.heiyehk.fithub.ui.icons.FiPackage
import com.heiyehk.fithub.ui.theme.FitTheme
import com.heiyehk.fithub.ui.theme.FitTone
import com.heiyehk.fithub.ui.theme.FitTypography
import com.heiyehk.fithub.ui.theme.MonoMeta

/**
 * 仓库列表的排序方式。
 *
 * **只有客户端排序，没有服务端排序**：`/user/repos` 和 `/users/{login}/repos`
 * 的 `sort` 只认 created / updated / pushed / full_name，没有 stars。
 * 想按 star 排只能拉回来自己排 —— 排序依据写在按钮上，别让用户以为
 * 「最近更新」是 GitHub 给的全局结果。
 */
enum class RepoSort(val labelRes: Int) {
    Updated(R.string.discovery_recent),
    Stars(R.string.discovery_star),
}

/**
 * 按当前排序排好。
 *
 * 抽成「排一个 List」的纯函数而不是只留一个「排 Async」的版本：
 * 「我的项目」页的数据是 `Async<MyRepos>`（列表在 `MyRepos.repos` 里），
 * 而公开页是 `Async<List<Repo>>`。排序规则只有一份，两种包装各调用一次。
 */
fun sortedRepos(list: List<Repo>, sort: RepoSort): List<Repo> = when (sort) {
    RepoSort.Updated -> list.sortedByDescending { it.date }
    RepoSort.Stars -> list.sortedByDescending { it.stars }
}

/** [repos] 可能还没加载完，空状态按空列表处理 */
fun sortRepos(repos: Async<List<Repo>>, sort: RepoSort): List<Repo> =
    sortedRepos((repos as? Async.Ok)?.value.orEmpty(), sort)

/**
 * 列表上方的排序切换 + 「检查安装包」。
 *
 * 抽出成公共件是因为「公开用户页」和「我的项目页」用的是同一套列表：
 * 两边各自写一遍的话，改了排序口径只改一处，另一处就会静默地不一样。
 */
@Composable
fun RepoListToolbar(
    sort: RepoSort,
    onSort: (RepoSort) -> Unit,
    onCheckAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = FitTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RepoSort.entries.forEach { s ->
            val active = s == sort
            Text(
                stringResource(s.labelRes),
                style = FitTypography.labelLarge,
                color = if (active) p.ink else p.ink4,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) p.washDeep else androidx.compose.ui.graphics.Color.Transparent)
                    .tap { onSort(s) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Spacer(Modifier.weight(1f))
        GhostButton(stringResource(R.string.person_check_assets), onCheckAll, height = 32.dp)
    }
}

/** 列表的加载中 / 出错 / 空 三种状态，占位高度保持和真的有内容时接近 */
@Composable
fun RepoListStateBlock(
    repos: Async<List<Repo>>,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    val p = FitTheme.palette
    when (val r = repos) {
        is Async.Loading -> RepoListSkeleton()
        is Async.Err -> Text(
            r.message,
            style = FitTypography.bodyMedium,
            color = p.ink4,
            modifier = modifier.padding(20.dp),
        )

        is Async.Ok -> if (r.value.isEmpty()) {
            Text(
                emptyText,
                style = FitTypography.bodyMedium,
                color = p.ink4,
                modifier = modifier.padding(20.dp),
            )
        }
    }
}

/**
 * 一行仓库。
 *
 * [metaSuffix] 追加到「语言 · 最后推送」那一行的尾巴。留成可选而不是把两种
 * 布局写成两个函数：两边的骨架完全一样，差别只有这一行文字。
 *
 * [trailingMeta] 是右侧那块由调用方决定的内容：公开页只放 star，
 * 「我的项目」页可以换成别的。做成插槽而不是再加几个布尔参数，
 * 是为了不要在同一个函数里长出两套布局。
 */
@Composable
fun RepoRow(
    repo: Repo,
    installState: InstallCheck?,
    onCheck: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    metaSuffix: String? = null,
    trailingMeta: @Composable () -> Unit = { Stars(Env.formatStars(repo.stars)) },
) {
    val p = FitTheme.palette
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .tap { onClick() }
                .padding(horizontal = 20.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTile(repo.monogram, repo.tileBg, repo.tileFg)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(repo.name, style = FitTypography.titleSmall, color = p.ink, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Text(
                    buildString {
                        append(repo.lang).append(" · ").append(repo.date)
                        if (!metaSuffix.isNullOrBlank()) append(" · ").append(metaSuffix)
                    },
                    style = MonoMeta,
                    color = p.ink4,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                InstallStateBadge(installState, onCheck)
            }
            Spacer(Modifier.width(10.dp))
            trailingMeta()
        }
        HairLine(Modifier.padding(start = 79.dp))
    }
}

/**
 * 「这个仓库有没有安装包」的状态。
 *
 * 未检查 / 检查中 / 有 / 没有 / 查失败是五件事，各自的动作也不同
 * （失败可点重试，没检查过也能点去查），所以不能压成一个「无产物」。
 */
@Composable
private fun InstallStateBadge(installState: InstallCheck?, onCheck: () -> Unit) {
    val p = FitTheme.palette
    when (installState) {
        InstallCheck.Yes -> FitBadge(FitTone.Ok, stringResource(R.string.person_has_assets), icon = FiPackage)
        InstallCheck.No -> FitBadge(FitTone.Muted, stringResource(R.string.person_no_release))

        InstallCheck.Checking -> MetaRow {
            CircularProgress(0.6f, size = 12.dp, color = p.ink4, stroke = 1.5.dp)
            Text(stringResource(R.string.person_checking), style = FitTypography.labelSmall, color = p.ink4)
        }

        InstallCheck.Failed -> Row(
            Modifier
                .clip(CircleShape)
                .border(BorderStroke(1.dp, p.toneLine(FitTone.Warn)), CircleShape)
                .tap { onCheck() }
                .padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(FiAlert, null, tint = p.toneFg(FitTone.Warn), modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.person_check_failed),
                style = FitTypography.labelSmall,
                color = p.toneFg(FitTone.Warn),
            )
        }

        null -> Row(
            Modifier
                .clip(CircleShape)
                .border(BorderStroke(1.dp, p.hairline), CircleShape)
                .tap { onCheck() }
                .padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.person_check_assets),
                style = FitTypography.labelSmall,
                color = p.ink3,
            )
        }
    }
}

@Composable
fun RepoListSkeleton() {
    val p = FitTheme.palette
    Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
        repeat(5) {
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

/** 「star / fork / watch」三个数字并排。三个格子宽度一致，不按数值长短伸缩 */
@Composable
fun CountTriple(
    cells: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    val p = FitTheme.palette
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        cells.forEach { (value, label) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    value,
                    style = FitTypography.headlineSmall,
                    color = p.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(label, style = FitTypography.labelSmall, color = p.ink4, maxLines = 1)
            }
        }
    }
}