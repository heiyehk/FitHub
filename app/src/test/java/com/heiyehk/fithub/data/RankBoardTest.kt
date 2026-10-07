package com.heiyehk.fithub.data

import com.heiyehk.fithub.data.remote.DiscoverSort
import com.heiyehk.fithub.data.remote.GitHubApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * [RankBoard] 的查询串构造。
 *
 * 这里的判据都是**对着 GitHub 的真实行为**写的，不是对着实现写的：
 * 改了 [RankBoard] 的字段却没改期望值时，红的是「行为变了」，不是「测试过时了」。
 */
class RankBoardTest {

    private val today = LocalDate.of(2026, 10, 7)

    /** 不需要网络：缓存键只是拼字符串，不碰磁盘 */
    private fun api() = GitHubApi(File(System.getProperty("java.io.tmpdir"), "fithub-rank-${System.nanoTime()}"))

    @Test
    fun `窄池不加 created 窗口`() {
        // Harness 只有 133 个仓库，取前 20 本来就是前 15%。加了窗口反而把池子掏空
        assertFalse(
            "窄池不该有时间窗，否则榜会经常空",
            RankBoard.Harness.queryOn(today).contains("created:"),
        )
        assertFalse(RankBoard.Skills.queryOn(today).contains("created:"))
    }

    @Test
    fun `宽池必须加 created 窗口`() {
        // 3979 个仓库按 star 取前 20 三年不变 —— 那是墙不是榜
        for (b in listOf(RankBoard.Agents, RankBoard.Mcp)) {
            assertTrue("${b.id} 是宽池，必须加时间窗", b.queryOn(today).contains("created:>="))
        }
    }

    @Test
    fun `时间窗天数与声明一致`() {
        assertTrue(RankBoard.Agents.queryOn(today).contains("created:>=2026-07-09"))
        assertTrue(RankBoard.Mcp.queryOn(today).contains("created:>=2026-07-09"))
    }

    /**
     * ❗ 每个榜都**必须**有 topic 限定。
     *
     * 这条是被迫加上的：原来有个不带 topic 的「上升」榜（`created:>= stars:>100`，
     * 全站搜索），实测 1501 个仓库里前两名是「高性价比人生指南」和 Mac 的
     * Photoshop 替代 —— 榜单是按名次被评判的，榜首一塌糊涂整个 tab 就废了。
     *
     * 而给它补 topic 也不行：`topic:a OR topic:b` 返回 **HTTP 422**，
     * `(topic:a OR topic:b)` **静默返回 0**；`topic:claude-code` + 90 天窗口
     * 的前 6 名全不是 agent 工具（topic 被污染）；`topic:coding-agent` + 30 天
     * 窗口只剩 20 条。GitHub 搜索表达不出「上升」，所以那个 tab 被删了。
     *
     * 要再加全局榜之前，先想清楚它凭什么不是彩票榜。
     */
    @Test
    fun `所有榜都必须限定 topic`() {
        for (b in RankBoard.entries) {
            assertTrue(
                "${b.id} 没有 topic 限定，等于全站榜：前几名会被社交媒体带火的仓库占满",
                b.queryOn(today).contains("topic:"),
            )
        }
    }

    @Test
    fun `每条查询都排除归档仓库并设 star 下限`() {
        for (b in RankBoard.entries) {
            val q = b.queryOn(today)
            assertTrue("${b.id} 必须排除 archived", q.contains("archived:false"))
            assertTrue("${b.id} 必须有 star 下限", Regex("""stars:>\d+""").containsMatchIn(q))
        }
    }

    @Test
    fun `每个榜的 topic 与声明一致`() {
        assertTrue(RankBoard.Skills.queryOn(today).startsWith("topic:claude-skills "))
        assertTrue(RankBoard.Harness.queryOn(today).startsWith("topic:agent-harness "))
        assertTrue(RankBoard.Agents.queryOn(today).startsWith("topic:ai-agents "))
        assertTrue(RankBoard.Mcp.queryOn(today).startsWith("topic:mcp "))
    }

    @Test
    fun `limit 是有意义的值`() {
        for (b in RankBoard.entries) {
            assertTrue("LIMIT 必须在 1..100（GitHub 上限）", RankBoard.LIMIT in 1..100)
        }
    }

    /**
     * ❗ 核心守卫：GitHub 仓库搜索只认 4 个 sort 值。
     *
     * 传一个不存在的 sort **不会 422**，它被静默忽略并回退成 best match ——
     * 于是「加一个新枚举值」会得到一个 HTTP 200、界面完全正常、排序是错的榜。
     * 这条测试就是为了让那种改动当场变红。
     */
    @Test
    fun `所有榜的 sort 必须是 GitHub 认可的值`() {
        for (b in RankBoard.entries) {
            assertTrue(
                "${b.id} 的 sort=\"${b.sort.wire}\" 不是 GitHub 支持的值，" +
                    "会静默回退成 best match（结果看着对，排序是错的）",
                b.sort.wire in RankBoard.LEGAL_SORTS,
            )
        }
        // 顺带钉住 DiscoverSort 本身：枚举里多一个值这里就红
        for (s in DiscoverSort.entries) {
            assertTrue("DiscoverSort.${s.name} 的 wire 非法", s.wire in RankBoard.LEGAL_SORTS)
        }
    }

    /**
     * `has:release` **不是** GitHub 的仓库搜索限定符，实测是空操作：
     * `topic:android stars:>50 archived:false` 是 12366 条，加 `has:release` 还是
     * 12366 条且榜首同一个仓库（2026-10-07 实测）。
     *
     * 有人看到「只展示有产物的仓库」这句注释会顺手把它加回来 —— 加上之后过滤不生效，
     * 但没有任何症状能暴露它。这条就是拦住那次「顺手」。
     */
    @Test
    fun `查询串不得含 has 冒号限定符`() {
        for (b in RankBoard.entries) {
            val q = b.queryOn(today)
            assertFalse(
                "${b.id} 的查询里出现了 has: 限定符，GitHub 会静默忽略它",
                Regex("""\bhas:""").containsMatchIn(q),
            )
        }
    }

    /**
     * perPage 必须进缓存键。漏了的话：同一个 query 先用 20 取过一次，
     * 再用 50 取会读到那 20 条 —— 页面上只有 20 行，而且按刷新也不动。
     *
     * 变异验证：把 [GitHubApi.discoverCacheKey] 里的 `-$perPage` 删掉，这条立刻红。
     */
    @Test
    fun `perPage 进缓存键`() {
        val key20 = api().discoverCacheKey(DiscoverSort.Stars, "topic:mcp", 20)
        val key50 = api().discoverCacheKey(DiscoverSort.Stars, "topic:mcp", 50)
        assertTrue(
            "两个 perPage 共用了同一个缓存键，perPage=50 会读到 perPage=20 那次的结果",
            key20 != key50,
        )
    }

    /** 同样的 query 两次取必须共用一个键，否则缓存等于没有、白白烧配额 */
    @Test
    fun `同 query 同 perPage 复用同一个键`() {
        val a = api().discoverCacheKey(DiscoverSort.Stars, "topic:mcp", 30)
        val b = api().discoverCacheKey(DiscoverSort.Stars, "topic:mcp", 30)
        assertEquals(a, b)
    }

    /** 不同榜的键不能撞 —— 否则 A 榜的数据会显示在 B 榜上 */
    @Test
    fun `不同查询不共用键`() {
        val keys = RankBoard.entries.map { api().discoverCacheKey(it.sort, it.queryOn(today), RankBoard.LIMIT) }
        assertEquals("有 tab 的缓存键撞了", keys.size, keys.distinct().size)
    }

    /** tab id 是 UI 状态 map 的 key，重复会让两个 tab 共用一份数据 */
    @Test
    fun `tab id 不重复`() {
        val ids = RankBoard.entries.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }
}
