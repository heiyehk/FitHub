package com.heiyehk.fithub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 关注列表的纯逻辑部分。
 *
 * [SubscriptionStore] 本身要 Context 落盘，这部分测不动；
 * 抽出不依赖 Android 的排序、时效判定、快照合并规则在这里验证。
 */
class SubscriptionTest {

    private fun sub(
        fullName: String,
        addedAt: Long = 1_000L,
        metaFetchedAt: Long = 0L,
        latestTag: String = "",
        name: String? = null,
    ) = Subscription(
        fullName = fullName,
        addedAt = addedAt,
        name = name ?: fullName.substringAfterLast('/'),
        metaFetchedAt = metaFetchedAt,
        latestTag = latestTag,
    )

    @Test
    fun `displayName 缺名时回退到仓库短名`() {
        assertEquals("NewPipe", sub("TeamNewPipe/NewPipe").displayName)
        assertEquals("NewPipe", sub("TeamNewPipe/NewPipe", name = "").displayName)
    }

    @Test
    fun `从未刷新过的元信息不算过期`() {
        // metaFetchedAt = 0 表示「还没刷新过」，不是「很久没刷新」
        assertFalse(sub("a/b", metaFetchedAt = 0L).isStale)
    }

    @Test
    fun `刚抓取的元信息不算过期`() {
        val now = System.currentTimeMillis()
        assertFalse(sub("a/b", metaFetchedAt = now).isStale)
    }

    @Test
    fun `超过 6 小时的元信息标记为过期`() {
        val old = System.currentTimeMillis() - Subscription.STALE_AFTER_MS - 60_000
        assertTrue(sub("a/b", metaFetchedAt = old).isStale)
    }

    @Test
    fun `关注判定忽略大小写`() {
        val list = listOf(sub("TeamNewPipe/NewPipe"))
        assertTrue(list.any { it.fullName.equals("teamnewpipe/newpipe", ignoreCase = true) })
        assertFalse(list.any { it.fullName.equals("Other/Repo", ignoreCase = true) })
    }

    @Test
    fun `按加入时间倒序排列`() {
        val list = listOf(sub("a/old", addedAt = 100), sub("a/new", addedAt = 300), sub("a/mid", addedAt = 200))
        assertEquals(listOf("a/new", "a/mid", "a/old"), list.sortedByDescending { it.addedAt }.map { it.fullName })
    }

    @Test
    fun `from Repo 转换保留展示字段`() {
        val repo = Repo(
            id = "TeamNewPipe/NewPipe",
            name = "NewPipe",
            owner = "TeamNewPipe",
            monogram = "NE",
            desc = "开源 YouTube 前端",
            lang = "Kotlin",
            langColor = 0L,
            langShare = listOf(100),
            stars = 26_000,
            forks = 2_900,
            watchers = 600,
            issues = 200,
            version = "v0.29.1",
            versionCode = 1015,
            date = "2026-10-01",
            topics = listOf("android", "youtube"),
            tileBg = 0L,
            tileFg = 0L,
            history = emptyList(),
            dist = Dist("apk", "NewPipe", listOf("arm64-v8a"), 23, "Android 6.0", SignedBy.Release, 20.0),
            source = DataSource.GitHub,
            hasRealRelease = true,
        )
        val addedAt = System.currentTimeMillis()
        val s = repo.toSubscription(addedAt = addedAt)
        assertEquals("TeamNewPipe/NewPipe", s.fullName)
        assertEquals(addedAt, s.addedAt)
        assertEquals("NewPipe", s.name)
        assertEquals(26_000, s.stars)
        assertEquals("Kotlin", s.lang)
        assertEquals(listOf("android", "youtube"), s.topics)
        assertEquals("v0.29.1", s.latestTag)
        assertEquals("2026-10-01", s.latestReleaseDate)
        // 关注那一刻手上的元信息就是新鲜的
        assertEquals(addedAt, s.metaFetchedAt)
        assertFalse(s.isStale)
    }

    @Test
    fun `没有 release 时 latestTag 为空而不是占位符`() {
        val repo = Repo(
            id = "a/b", name = "b", owner = "a", monogram = "AB", desc = "", lang = "", langColor = 0L,
            langShare = listOf(100), stars = 0, forks = 0, watchers = 0, issues = 0,
            version = "—", versionCode = 0, date = "2026-10-01", topics = emptyList(),
            tileBg = 0L, tileFg = 0L, history = emptyList(),
            dist = Dist("", "", emptyList(), 0, "—", SignedBy.Unverified, 0.0),
            source = DataSource.GitHub, hasRealRelease = false,
        )
        val s = repo.toSubscription()
        // 「—」是未知，不能当成真的版本号存进快照
        assertEquals("", s.latestTag)
        assertEquals("", s.latestReleaseDate)
    }

    @Test
    fun `note 默认为空字符串便于序列化往返`() {
        // 导出格式的稳定性靠字段默认值兜住：老文件缺 note 也要能读回来
        val s = sub("a/b", latestTag = "v1")
        assertEquals("v1", s.latestTag)
        assertEquals("", s.note)
    }
}
