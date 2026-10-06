package com.heiyehk.fithub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页板块配置。
 *
 * 重点钉两件事：
 * 1) 上限必须生效 —— 每个板块一次 search 请求，越界就是配额被打光
 * 2) id 必须稳定且可去重 —— 改标题不该让用户对不上已有缓存
 */
class HomeSectionTest {

    private fun custom(topic: String, title: String = "") = HomeSection.customFrom(topic, title)

    // ---- 默认配置 ----

    @Test
    fun `默认四个板块全部启用且都是内置`() {
        val d = HomeSection.defaults()
        assertEquals(4, d.size)
        assertTrue(d.all { it.enabled })
        assertTrue("默认板块都应是内置不可编辑", d.all { it.builtin })
    }

    @Test
    fun `Agent 板块本质是 topic 板块`() {
        val agent = HomeSection.defaults().first { it.id == HomeSection.ID_AGENT }
        assertEquals(HomeSection.Kind.Topic, agent.kind)
        assertEquals("ai-agent", agent.topic)
    }

    @Test
    fun `内置板块不可编辑，配置页只给开关`() {
        assertTrue(HomeSection.defaults().all { it.builtin })
        assertFalse("自定义板块不该是内置", custom("kotlin").builtin)
    }

    // ---- kind 判定 ----

    @Test
    fun `四个内置板块的 kind 各自正确`() {
        val by = HomeSection.defaults().associateBy { it.id }
        assertEquals(HomeSection.Kind.Stars, by.getValue(HomeSection.ID_STARS).kind)
        assertEquals(HomeSection.Kind.Updated, by.getValue(HomeSection.ID_UPDATED).kind)
        assertEquals(HomeSection.Kind.Topic, by.getValue(HomeSection.ID_AGENT).kind)
    }

    @Test
    fun `只有 topic 板块带查询串`() {
        assertEquals(
            "topic:kotlin stars:>50 archived:false",
            custom("kotlin").query,
        )
        // 非 topic 板块不该凭空造查询，否则会白发配额
        assertEquals(null, HomeSection.defaults().first { it.id == HomeSection.ID_STARS }.query)
        assertEquals(null, HomeSection.defaults().first { it.id == HomeSection.ID_UPDATED }.query)
    }

    // ---- id / query 稳定性 ----

    @Test
    fun `改标题不改变 id`() {
        // id 变了用户就配不上已有缓存
        val a = custom("kotlin")
        val b = a.copy(title = "Kotlin 项目")
        assertEquals(a.id, b.id)
    }

    @Test
    fun `改标题不改变搜索查询`() {
        /**
         * 这条比「缓存 key 稳定」更靠底层，也才是真正的保证。
         *
         * topic 板块的缓存 key 是由**查询串**派生的（`GitHubApi.searchCacheKey`），
         * 冷启动预填和真正去查用的是同一个查询串。所以只要用户改个标题就换了查询串，
         * 预填就找不到上次那份数据 —— 症状是「明明点过，每次开 App 还是那个按钮」。
         * query 由 topic 派生、不碰 title，这条守住它。
         */
        val a = custom("kotlin")
        val b = a.copy(title = "Kotlin 项目", subtitle = "随便改")
        assertEquals(a.query, b.query)
    }

    @Test
    fun `不同 topic 的搜索查询互不相同`() {
        // 查询串相同 = 共用一份缓存 = 两个板块显示一模一样的内容
        val queries = HomeSection.defaults()
            .mapNotNull { it.query } + custom("kotlin").query + custom("java").query
        assertEquals("查询串不应重复", queries.size, queries.toSet().size)
    }

    @Test
    fun `同一 topic 派生同一个 id`() {
        // 否则用户重复添加会堆出两条一样的板块
        assertEquals(custom("kotlin").id, custom("KOTLIN").id)
        assertNotEquals(custom("kotlin").id, custom("java").id)
    }

    @Test
    fun `topic 标题留空时回退到 topic 本身`() {
        assertEquals("kotlin", custom("kotlin").title)
        assertEquals("我的板块", custom("kotlin", "我的板块").title)
    }

    // ---- 上限 ----

    @Test
    fun `自定义板块上限是 6`() {
        assertEquals(6, HomeSection.MAX_CUSTOM)
        assertEquals(10, HomeSection.MAX_TOTAL)
    }

    @Test
    fun `超出上限的配置被裁掉`() {
        val input = HomeSection.defaults() + (1..20).map { custom("topic$it") }
        val result = sanitizeSections(input)

        assertEquals("自定义应被裁到 6", 6, result.count { !it.builtin })
        assertEquals("内置全部保留", 4, result.count { it.builtin })
        assertTrue("总数不超过 10", result.size <= HomeSection.MAX_TOTAL)
    }

    @Test
    fun `id 重复的配置只留一条`() {
        val input = listOf(custom("kotlin"), custom("KOTLIN"), custom("java"))
        assertEquals(2, sanitizeSections(input).size)
    }

    @Test
    fun `内置板块始终排在自定义之前`() {
        val input = listOf(custom("a"), custom("b")) + HomeSection.defaults()
        val result = sanitizeSections(input)
        assertTrue("内置应在前", result.take(4).all { it.builtin })
    }

    @Test
    fun `还能再加几个自定义板块`() {
        assertEquals(6, HomeSectionStore.remainingCustom(emptyList()))
        assertEquals(4, HomeSectionStore.remainingCustom(HomeSection.defaults() + listOf(custom("a"), custom("b"))))
        assertEquals("超了就是 0，不能为负", 0, HomeSectionStore.remainingCustom(HomeSection.defaults() + (1..9).map { custom("t$it") }))
    }

    // ---- 停用 ----

    @Test
    fun `停用只是 enabled 变 false，配置本身还在`() {
        val off = HomeSection.defaults().first { it.id == HomeSection.ID_STARS }.copy(enabled = false)
        assertFalse(off.enabled)
        assertTrue("停用后仍应留在配置里以便再次开启", off.builtin)
        assertEquals("停用不改变 kind 判定", HomeSection.Kind.Stars, off.kind)
    }
}
