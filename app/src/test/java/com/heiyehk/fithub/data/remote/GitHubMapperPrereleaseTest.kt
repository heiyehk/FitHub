package com.heiyehk.fithub.data.remote

import com.heiyehk.fithub.data.DataSource
import com.heiyehk.fithub.data.Dist
import com.heiyehk.fithub.data.Repo
import com.heiyehk.fithub.data.SignedBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「全是预发布」的仓库不能被显示成一个空仓库。
 *
 * 用户报的原始现象：v2rayNG 详情页显示「这个仓库没有发布任何产物」，
 * 而它 GitHub 上明明挂着一堆 APK。两处原因叠在一起：
 *
 * 1. 详情页只拉 **3** 条 release，而 v2rayNG 最新的 3 条恰好全是预发布；
 *    预发布一滤，`stable` 就是空列表，于是页面断言「没有 release」。
 *    真正的正式版 2.2.6 在第 10 位，**压根没被请求**。
 * 2. 即使拉够了，只要正式版一条都没有，原实现仍然直接显示空 —— 而不是把
 *    预发布的产物摆出来。
 *
 * 第 1 条的「拉多少条」在 [com.heiyehk.fithub.data.FitRepository] 里，
 * 第 2 条在这里。
 */
class GitHubMapperPrereleaseTest {

    private fun base() = Repo(
        id = "2dust/v2rayNG", name = "v2rayNG", owner = "2dust", monogram = "V2",
        desc = "", lang = "Kotlin", langColor = 0L, langShare = emptyList(),
        stars = 0, forks = 0, watchers = 0, issues = 0,
        version = "—", versionCode = 0, date = "2026-10-05",
        topics = emptyList(), tileBg = 0L, tileFg = 0L,
        history = emptyList(), dist = Dist("", "", emptyList(), 0, "—", SignedBy.Unverified, 0.0),
        source = DataSource.GitHub,
    )

    private fun asset(name: String) =
        AssetDto(name = name, size = 32L * 1048576L, downloadCount = 100)

    private fun rel(tag: String, prerelease: Boolean, apkName: String) = ReleaseDto(
        tagName = tag,
        prerelease = prerelease,
        publishedAt = "2026-10-01T00:00:00Z",
        assets = listOf(asset(apkName)),
    )

    @Test
    fun `拉到的全是预发布时，产物照样要列出来而不是显示空仓库`() {
        val r = GitHubMapper.applyReleases(
            base(),
            listOf(
                rel("2.3.9", true, "v2rayNG_2.3.9-fdroid_x86_64.apk"),
                rel("2.3.8", true, "v2rayNG_2.3.8-fdroid_x86_64.apk"),
            ),
            includePrerelease = false,
        )
        assertEquals("预发布的 APK 必须看得见", 2, r.assets.size)
        assertEquals("2.3.9", r.version)
    }

    @Test
    fun `这种情况要标成 prereleaseOnly，好让 UI 说明白但不当成失败`() {
        val r = GitHubMapper.applyReleases(
            base(),
            listOf(rel("2.3.9", true, "v2rayNG_2.3.9-fdroid_x86_64.apk")),
            includePrerelease = false,
        )
        assertTrue(r.prereleaseOnly)
        // 有东西可展示 —— 这一位现在和「一个 release 都没有」同义，
        // 正是旧实现把两者混为一谈、导致空仓库误报的那一处
        assertTrue(r.hasRealRelease)
    }

    @Test
    fun `有正式版时只列正式版的产物`() {
        val r = GitHubMapper.applyReleases(
            base(),
            listOf(
                rel("2.3.9", true, "v2rayNG_2.3.9-fdroid_x86_64.apk"),
                rel("2.2.6", false, "v2rayNG_2.2.6-fdroid_x86_64.apk"),
            ),
            includePrerelease = false,
        )
        assertEquals(1, r.assets.size)
        assertEquals("2.2.6", r.version)
        assertFalse(r.prereleaseOnly)
    }

    @Test
    fun `打开预发布后新旧版本都在，且不再标成只有预发布`() {
        val r = GitHubMapper.applyReleases(
            base(),
            listOf(
                rel("2.3.9", true, "v2rayNG_2.3.9-fdroid_x86_64.apk"),
                rel("2.2.6", false, "v2rayNG_2.2.6-fdroid_x86_64.apk"),
            ),
            includePrerelease = true,
        )
        assertEquals(2, r.assets.size)
        assertFalse(r.prereleaseOnly)
    }

    @Test
    fun `真的一个 release 都没有时才当空仓库`() {
        val r = GitHubMapper.applyReleases(base(), emptyList(), includePrerelease = false)
        assertFalse(r.hasRealRelease)
        assertFalse(r.prereleaseOnly)
        assertTrue(r.assets.isEmpty())
    }

    @Test
    fun `草稿不算 release，不能因为有草稿就显示成有东西`() {
        val r = GitHubMapper.applyReleases(
            base(),
            listOf(
                ReleaseDto(tagName = "3.0.0", draft = true, assets = listOf(asset("a_x86_64.apk"))),
            ),
            includePrerelease = false,
        )
        assertTrue("草稿是作者还没打算发的东西", r.assets.isEmpty())
        assertFalse(r.hasRealRelease)
    }

    @Test
    fun `更新日志保留原始 Markdown，不能在数据层就摊平成几行`() {
        val md = "## 修复\n\n- 修好了 A\n- 修好了 B\n\n```kotlin\nval x = 1\n```"
        val r = GitHubMapper.applyReleases(
            base(),
            listOf(
                ReleaseDto(
                    tagName = "2.2.6",
                    publishedAt = "2026-10-01T00:00:00Z",
                    body = md,
                    assets = listOf(asset("a_x86_64.apk")),
                ),
            ),
            includePrerelease = false,
        )
        // 之前是 lineSequence().filter{}.take(6)：标题、代码块围栏全被拆散，
        // 等于把 Markdown 语义在渲染之前就毁了
        assertEquals(md, r.history.first().body)
        assertTrue("代码围栏必须还在", r.history.first().body.contains("```"))
    }

    @Test
    fun `空 body 也不会炸`() {
        val r = GitHubMapper.applyReleases(
            base(),
            listOf(
                ReleaseDto(
                    tagName = "2.2.6",
                    body = null,
                    assets = listOf(asset("a_x86_64.apk")),
                ),
            ),
            includePrerelease = false,
        )
        assertEquals("", r.history.first().body)
    }

    @Test
    fun `更新日志条数上限别缩回 3`() {
        val many = (1..25).map {
            ReleaseDto(
                tagName = "2.0.$it",
                publishedAt = "2026-10-01T00:00:00Z",
                body = "n$it",
                assets = listOf(asset("a_x86_64.apk")),
            )
        }
        val r = GitHubMapper.applyReleases(base(), many, includePrerelease = false)
        assertTrue("25 条 release 至少要给出多个版本的历史", r.history.size > 3)
        assertNotEquals(0, r.history.size)
    }
}
