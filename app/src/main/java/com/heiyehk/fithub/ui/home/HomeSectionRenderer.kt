package com.heiyehk.fithub.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.heiyehk.fithub.R
import com.heiyehk.fithub.data.Async
import com.heiyehk.fithub.data.HomeSection
import com.heiyehk.fithub.data.Repo
import com.heiyehk.fithub.ui.components.GhostButton
import com.heiyehk.fithub.ui.components.SectionHead
import com.heiyehk.fithub.ui.components.StaggeredItem
import com.heiyehk.fithub.ui.components.StaleNotice
import com.heiyehk.fithub.ui.components.tap
import com.heiyehk.fithub.ui.theme.FitTheme
import com.heiyehk.fithub.ui.theme.FitTone
import com.heiyehk.fithub.ui.theme.FitTypography

/**
 * 把一个 [HomeSection] 渲染成若干 item。
 *
 * 四种形态：
 * - [HomeSection.Kind.Stars] 横向卡片条
 * - [HomeSection.Kind.Updated] 竖排列表
 * - [HomeSection.Kind.Featured] 三格 Bento
 * - [HomeSection.Kind.Topic] 按需查询的列表 —— 只在**缓存里没有**时才是按钮；
 *   缓存里有的（上次点过）冷启动会直接预填上，并标出数据来自多久之前
 *
 * 抽出来的原因：四个板块的标题、序号、错误处理、空态文案全是重复的，
 * 各自写一遍意味着「加个自定义板块」要改四个地方。
 */
fun LazyListScope.homeSection(
    section: HomeSection,
    index: Int,
    state: Async<List<Repo>>?,
    langFilter: String,
    onRepoTap: (Repo) -> Unit,
    onTileBounds: (String, Rect) -> Unit,
    onLoad: () -> Unit,
    onRetry: () -> Unit,
) {
    val prefix = "${section.id}-"
    val no = "%02d".format(index + 1)

    item(key = "${prefix}head") {
        Column(Modifier.padding(start = ScreenPad, end = ScreenPad, top = 38.dp, bottom = 14.dp)) {
            SectionHead(no, sectionTitle(section), sectionSubtitle(section))
        }
    }

    when (section.kind) {
        HomeSection.Kind.Stars -> {
            when (val s = state) {
                is Async.Loading -> item(key = "${prefix}skel") { SkeletonRail() }
                is Async.Err -> item(key = "${prefix}err") { ErrorBlock(s.message, onRetry) }
                is Async.Ok -> {
                    val list = s.value.filterByLang(langFilter).take(12)
                    item(key = "${prefix}rail") {
                        if (list.isEmpty()) {
                            EmptyBlock(
                                stringResource(R.string.home_empty_lang_title),
                                stringResource(R.string.home_empty_lang_hint),
                            )
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = ScreenPad),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                itemsIndexed(list, key = { i, r -> "${prefix}t-$i-${r.id}" }) { i, repo ->
                                    StaggeredItem(i) {
                                        TrendCard(repo, i, onTileBounds) { onRepoTap(repo) }
                                    }
                                }
                            }
                        }
                    }
                }
                null -> Unit
            }
        }

        HomeSection.Kind.Updated -> {
            when (val s = state) {
                is Async.Loading -> item(key = "${prefix}skel") { SkeletonRows() }
                is Async.Err -> item(key = "${prefix}err") { ErrorBlock(s.message, onRetry) }
                is Async.Ok -> {
                    val list = s.value.filterByLang(langFilter).take(12)
                    if (list.isEmpty()) {
                        item(key = "${prefix}empty") {
                            EmptyBlock(
                                stringResource(R.string.home_empty_lang_title),
                                stringResource(R.string.home_empty_lang_hint),
                            )
                        }
                    } else {
                        itemsIndexed(list, key = { i, r -> "${prefix}r-$i-${r.id}" }) { i, repo ->
                            StaggeredItem(i) { RepoRow(repo, onTileBounds) { onRepoTap(repo) } }
                        }
                    }
                }
                null -> Unit
            }
        }

        HomeSection.Kind.Featured -> {
            item(key = "${prefix}bento") {
                when (val f = state) {
                    is Async.Loading -> Text(
                        stringResource(R.string.home_featured_loading),
                        style = FitTypography.bodySmall,
                        color = FitTheme.palette.ink4,
                        modifier = Modifier.padding(horizontal = ScreenPad, vertical = 20.dp),
                    )

                    is Async.Err -> Text(
                        stringResource(R.string.home_featured_err, f.message),
                        style = FitTypography.bodySmall,
                        color = FitTheme.palette.toneFg(FitTone.Warn),
                        modifier = Modifier.padding(horizontal = ScreenPad, vertical = 20.dp),
                    )

                    is Async.Ok -> {
                        val b = f.value
                        if (b.size >= 3) {
                            FeaturedBento(b[0], b[1], b[2], onTileBounds, onRepoTap)
                        } else {
                            // 未拉到的条目不占位
                            Text(
                                stringResource(R.string.home_featured_partial, b.size),
                                style = FitTypography.bodySmall,
                                color = FitTheme.palette.ink4,
                                modifier = Modifier.padding(horizontal = ScreenPad, vertical = 20.dp),
                            )
                        }
                    }

                    null -> Unit
                }
            }
        }

        HomeSection.Kind.Topic -> {
            // 懒加载：查一次就花一次请求，不预取。用户点按钮才发。
            item(key = "${prefix}body") {
                when (val s = state) {
                    null -> Column(Modifier.padding(horizontal = ScreenPad, vertical = 14.dp)) {
                        Text(
                            stringResource(R.string.home_topic_idle, section.topic),
                            style = FitTypography.bodySmall,
                            color = FitTheme.palette.ink4,
                        )
                        Spacer(Modifier.height(12.dp))
                        GhostButton(stringResource(R.string.home_topic_query, sectionTitle(section)), onLoad)
                    }

                    is Async.Loading -> Text(
                        stringResource(R.string.home_topic_loading),
                        style = FitTypography.bodySmall,
                        color = FitTheme.palette.ink4,
                        modifier = Modifier.padding(horizontal = ScreenPad, vertical = 18.dp),
                    )

                    is Async.Err -> Column(Modifier.padding(horizontal = ScreenPad, vertical = 14.dp)) {
                        Text(
                            stringResource(R.string.home_topic_err, s.message),
                            style = FitTypography.bodySmall,
                            color = FitTheme.palette.toneFg(FitTone.Warn),
                        )
                        Spacer(Modifier.height(10.dp))
                        GhostButton(stringResource(R.string.home_topic_retry), onLoad)
                    }

                    is Async.Ok -> if (s.value.isEmpty()) {
                        Column(Modifier.padding(horizontal = ScreenPad, vertical = 14.dp)) {
                            Text(
                                stringResource(R.string.home_topic_empty),
                                style = FitTypography.bodySmall,
                                color = FitTheme.palette.ink4,
                            )
                            Spacer(Modifier.height(10.dp))
                            GhostButton(stringResource(R.string.home_topic_query_again), onLoad)
                        }
                    } else {
                        Column {
                            // 内容是预填的缓存而不是刚查的，就照实标出来。
                            // 不标的话用户会以为这一屏是实时的，而「点过一次之后
                            // 每次开 App 都在」恰恰是最容易让人误解成实时的形态。
                            if (s.fromCache) {
                                StaleNotice(
                                    ageMs = s.ageMs,
                                    modifier = Modifier.padding(
                                        horizontal = ScreenPad,
                                        vertical = 10.dp,
                                    ),
                                )
                            }
                            s.value.filterByLang(langFilter).take(6).forEachIndexed { i, r ->
                                StaggeredItem(i) { RepoRow(r, onTileBounds) { onRepoTap(r) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun List<Repo>.filterByLang(lang: String): List<Repo> =
    if (lang == ALL_LANGS) this else filter { it.lang.equals(lang, true) }

/**
 * 板块标题。
 *
 * 内置四个的标题写死在 `HomeSection.defaults()` 里（data 层不带资源 id），
 * 所以在这里按 id 换成资源；自定义 topic 板块的标题是用户自己敲的，原样显示。
 */
@Composable
internal fun sectionTitle(s: HomeSection): String = when (s.id) {
    HomeSection.ID_STARS -> stringResource(R.string.home_section_stars)
    HomeSection.ID_UPDATED -> stringResource(R.string.home_section_updated)
    HomeSection.ID_FEATURED -> stringResource(R.string.home_section_featured)
    HomeSection.ID_AGENT -> stringResource(R.string.home_section_agent)
    else -> s.title
}

/** 板块副标题。同 [sectionTitle]：内置的走资源，自定义的用原值 */
@Composable
internal fun sectionSubtitle(s: HomeSection): String = when (s.id) {
    HomeSection.ID_STARS -> stringResource(R.string.home_section_stars_sub)
    HomeSection.ID_UPDATED -> stringResource(R.string.home_section_updated_sub)
    HomeSection.ID_FEATURED -> stringResource(R.string.home_section_featured_sub)
    HomeSection.ID_AGENT -> stringResource(R.string.home_section_agent_sub)
    else -> s.subtitle
}
