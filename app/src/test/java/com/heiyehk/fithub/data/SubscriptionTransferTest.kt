package com.heiyehk.fithub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 订阅导入导出。
 *
 * 重点钉两件事：
 * 1) 不认识的 schema **整体拒绝**，不做部分导入 —— 半份数据比没导入更难收拾
 * 2) 导入是合并不是覆盖 —— 覆盖会静默丢掉用户本机已有的关注
 */
class SubscriptionTransferTest {

    private fun sub(fullName: String, addedAt: Long = 1_700_000_000_000L) = Subscription(
        fullName = fullName,
        addedAt = addedAt,
        name = fullName.substringAfterLast('/'),
        metaFetchedAt = 1_700_000_000_000L,
    )

    // ---- 导出 ----

    @Test
    fun `导出的 JSON 能被自己读回且条目不丢`() {
        val subs = listOf(sub("TeamNewPipe/NewPipe"), sub("zed-industries/zed"))
        val outcome = SubscriptionTransfer.import(SubscriptionTransfer.export(subs), emptyList())
        assertTrue(outcome is ImportOutcome.Ok)
        outcome as ImportOutcome.Ok
        assertEquals(2, outcome.added)
        assertEquals(0, outcome.skipped)
    }

    @Test
    fun `导出带 schema 与 app 标识`() {
        val json = SubscriptionTransfer.export(listOf(sub("a/b")))
        assertTrue(json.contains("\"schema\""))
        assertTrue(json.contains("\"app\":\"FitHub\"") || json.contains("\"app\": \"FitHub\""))
        assertTrue(json.contains("\"exportedAt\""))
    }

    @Test
    fun `导出按全名排序，结果与传入顺序无关`() {
        // 固定 exportedAt，否则两次导出的时间戳不同，比不出排序是否稳定
        val t = 1_757_000_000_000L
        val a = SubscriptionTransfer.export(listOf(sub("Z/z"), sub("A/a"), sub("M/m")), t)
        val b = SubscriptionTransfer.export(listOf(sub("M/m"), sub("A/a"), sub("Z/z")), t)
        assertEquals(a, b)
        // 排序按全名小写：A/a < M/m < Z/z
        assertTrue(a.indexOf("A/a") < a.indexOf("M/m"))
        assertTrue(a.indexOf("M/m") < a.indexOf("Z/z"))
    }

    @Test
    fun `空列表导出是合法 JSON 且能读回 Empty`() {
        val outcome = SubscriptionTransfer.import(SubscriptionTransfer.export(emptyList()), emptyList())
        assertTrue(outcome is ImportOutcome.Empty)
    }

    // ---- 导入：合并语义 ----

    @Test
    fun `导入是合并：已有的保留，新的追加`() {
        val existing = listOf(sub("A/keep"))
        val text = SubscriptionTransfer.export(listOf(sub("A/keep"), sub("B/new")))
        val outcome = SubscriptionTransfer.import(text, existing)
        outcome as ImportOutcome.Ok
        assertEquals("只应新增 1 条", 1, outcome.added)
        assertEquals("已存在的应计为跳过", 1, outcome.skipped)
    }

    @Test
    fun `重复导入同一份文件不会产生重复项`() {
        val subs = listOf(sub("A/a"), sub("B/b"))
        val text = SubscriptionTransfer.export(subs)
        val first = SubscriptionTransfer.import(text, emptyList()) as ImportOutcome.Ok
        val mergedOnce = subs.take(first.added)
        val second = SubscriptionTransfer.import(text, mergedOnce) as ImportOutcome.Ok
        assertEquals(0, second.added)
        assertEquals(2, second.skipped)
    }

    @Test
    fun `全名比对忽略大小写`() {
        val existing = listOf(sub("TeamNewPipe/NewPipe"))
        val text = SubscriptionTransfer.export(listOf(sub("teamnewpipe/newpipe")))
        val outcome = SubscriptionTransfer.import(text, existing) as ImportOutcome.Ok
        assertEquals(0, outcome.added)
        assertEquals(1, outcome.skipped)
    }

    @Test
    fun `导出时排序不改变关注时间`() {
        val older = sub("A/old", addedAt = 1L)
        val newer = sub("B/new", addedAt = 9_999L)
        val text = SubscriptionTransfer.export(listOf(newer, older))
        val outcome = SubscriptionTransfer.import(text, emptyList()) as ImportOutcome.Ok
        assertEquals(0, outcome.skipped)
        // addedAt 沿用文件里的值，不被导入时刻覆盖
        assertTrue(text.contains("\"addedAt\":1"))
        assertTrue(text.contains("\"addedAt\":9999"))
        assertEquals(2, outcome.added)
    }

    // ---- 导入：拒绝与异常 ----

    @Test
    fun `不认识的 schema 整体拒绝`() {
        val text = """{"schema":99,"app":"FitHub","items":[{"fullName":"A/b","addedAt":1}]}"""
        val outcome = SubscriptionTransfer.import(text, emptyList())
        assertTrue(outcome is ImportOutcome.UnsupportedSchema)
        outcome as ImportOutcome.UnsupportedSchema
        assertEquals(99, outcome.found)
        assertEquals(SubscriptionFile.CURRENT_SCHEMA, outcome.supported)
    }

    @Test
    fun `内容不是 JSON 时报 Malformed 而不是抛异常`() {
        val outcome = SubscriptionTransfer.import("这不是 JSON{{{", emptyList())
        assertTrue(outcome is ImportOutcome.Malformed)
    }

    @Test
    fun `结构对不上时报 Malformed`() {
        val outcome = SubscriptionTransfer.import("""{"schema":1,"items":"不是数组"}""", emptyList())
        assertTrue(outcome is ImportOutcome.Malformed)
    }

    @Test
    fun `items 为空数组报 Empty`() {
        val outcome = SubscriptionTransfer.import("""{"schema":1,"items":[]}""", emptyList())
        assertTrue(outcome is ImportOutcome.Empty)
    }

    @Test
    fun `非法全名被丢弃并计入 invalid，其余照常导入`() {
        val text = """
            {"schema":1,"items":[
              {"fullName":"A/good","addedAt":1},
              {"fullName":"没有斜杠","addedAt":1},
              {"fullName":"a/b/c","addedAt":1},
              {"fullName":"/empty-owner","addedAt":1}
            ]}
        """.trimIndent()
        val outcome = SubscriptionTransfer.import(text, emptyList()) as ImportOutcome.Ok
        assertEquals(1, outcome.added)
        assertEquals(3, outcome.invalid)
    }

    // ---- 全名校验 ----

    @Test
    fun `全名校验接受标准形态`() {
        assertTrue(SubscriptionTransfer.isValidFullName("TeamNewPipe/NewPipe"))
        assertTrue(SubscriptionTransfer.isValidFullName("a/b"))
    }

    @Test
    fun `全名校验拒绝路径穿越与空段`() {
        assertFalse(SubscriptionTransfer.isValidFullName("../etc"))
        assertFalse(SubscriptionTransfer.isValidFullName("a/../b"))
        assertFalse(SubscriptionTransfer.isValidFullName("a/./b"))
        assertFalse(SubscriptionTransfer.isValidFullName("a//b"))
        assertFalse(SubscriptionTransfer.isValidFullName("a/"))
        assertFalse(SubscriptionTransfer.isValidFullName("/b"))
        assertFalse(SubscriptionTransfer.isValidFullName(""))
        assertFalse(SubscriptionTransfer.isValidFullName("a\\b"))
    }

    @Test
    fun `导入时全名两端空白被裁掉`() {
        val text = """{"schema":1,"items":[{"fullName":"  A/b  ","addedAt":1}]}"""
        val outcome = SubscriptionTransfer.import(text, emptyList()) as ImportOutcome.Ok
        assertEquals(1, outcome.added)
    }

    @Test
    fun `addedAt 为 0 时用当前时间兜底`() {
        val now = 1_234_567_890L
        val text = """{"schema":1,"items":[{"fullName":"A/b","addedAt":0}]}"""
        val outcome = SubscriptionTransfer.import(text, emptyList(), now) as ImportOutcome.Ok
        assertEquals(1, outcome.added)
    }

    @Test
    fun `缺少 schema 字段时按当前版本处理`() {
        // 老文件没写 schema 字段时不能直接当成不兼容拒绝
        val text = """{"items":[{"fullName":"A/b","addedAt":1}]}"""
        val outcome = SubscriptionTransfer.import(text, emptyList())
        assertTrue(outcome is ImportOutcome.Ok)
    }

    @Test
    fun `未知字段不影响导入`() {
        val text = """{"schema":1,"futureField":"x","items":[{"fullName":"A/b","addedAt":1,"extra":true}]}"""
        val outcome = SubscriptionTransfer.import(text, emptyList()) as ImportOutcome.Ok
        assertEquals(1, outcome.added)
    }

    @Test
    fun `建议文件名带日期且是 json`() {
        val name = SubscriptionTransfer.suggestedFileName(1_757_000_000_000L)
        assertTrue(name.endsWith(".json"))
        assertTrue(name.contains("fithub-subscriptions"))
    }
}
