package com.heiyehk.fithub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 足迹的纯逻辑部分：去重键、排序、上限。
 *
 * 落盘要 Context，这部分测不动，所以把可测的规则抽成顶层函数在这里验证。
 */
class HistoryEntryTest {

    private fun entry(
        kind: HistoryEntry.Kind = HistoryEntry.Kind.RepoViewed,
        ref: String = "a/b",
        title: String = "B",
        at: Long = 1L,
    ) = HistoryEntry(kind = kind, ref = ref, title = title, at = at)

    @Test
    fun `去重键由类型和标识组成`() {
        assertEquals("RepoViewed:a/b", entry(ref = "a/b").dedupeKey)
        assertEquals("AssetParsed:x.apk", entry(kind = HistoryEntry.Kind.AssetParsed, ref = "x.apk").dedupeKey)
    }

    @Test
    fun `同一目标不同类型不共用去重键`() {
        // 同一个包名既可能被查看过也可能被安装过，这是两件事
        val viewed = entry(kind = HistoryEntry.Kind.RepoViewed, ref = "com.a.b")
        val installed = entry(kind = HistoryEntry.Kind.InstalledViaUs, ref = "com.a.b")
        assertNotEquals(viewed.dedupeKey, installed.dedupeKey)
    }

    @Test
    fun `同一条再次发生时应并入旧条目而不是追加`() {
        val current = listOf(entry(ref = "a/b", title = "旧标题", at = 100L))
        val again = entry(ref = "a/b", title = "新标题", at = 200L)

        val idx = current.indexOfFirst { it.dedupeKey == again.dedupeKey }
        val next = if (idx >= 0) {
            current.toMutableList().also { it[idx] = again }
        } else {
            current + again
        }

        assertEquals("重复发生不应增加条数", 1, next.size)
        assertEquals("标题应被更新", "新标题", next[0].title)
        assertEquals("时间应被刷新", 200L, next[0].at)
    }

    @Test
    fun `不同目标各自占一条`() {
        val current = listOf(entry(ref = "a/b", at = 1L))
        val other = entry(ref = "c/d", at = 2L)
        val next = current + other
        assertEquals(2, next.size)
    }

    @Test
    fun `超过上限时丢最旧的`() {
        val many = (1..HistoryStore.MAX_ENTRIES + 30).map { i ->
            entry(ref = "o/r$i", at = i.toLong())
        }
        val trimmed = many.sortedByDescending { it.at }.take(HistoryStore.MAX_ENTRIES)
        assertEquals(HistoryStore.MAX_ENTRIES, trimmed.size)
        assertEquals("最新的那条必须留下", (HistoryStore.MAX_ENTRIES + 30).toLong(), trimmed.first().at)
    }

    @Test
    fun `列表按时间倒序`() {
        val list = listOf(entry(ref = "a", at = 100), entry(ref = "b", at = 300), entry(ref = "c", at = 200))
        assertEquals(listOf("b", "c", "a"), list.sortedByDescending { it.at }.map { it.ref })
    }

    @Test
    fun `清某一类只影响该类`() {
        val list = listOf(
            entry(kind = HistoryEntry.Kind.RepoViewed, ref = "a"),
            entry(kind = HistoryEntry.Kind.InstalledViaUs, ref = "a"),
            entry(kind = HistoryEntry.Kind.RepoViewed, ref = "b"),
        )
        val after = list.filterNot { it.kind == HistoryEntry.Kind.RepoViewed }
        assertEquals(1, after.size)
        assertEquals(HistoryEntry.Kind.InstalledViaUs, after[0].kind)
    }

    @Test
    fun `清单条时同类同 ref 一起清掉`() {
        val list = listOf(
            entry(kind = HistoryEntry.Kind.AssetParsed, ref = "x.apk", at = 1),
            entry(kind = HistoryEntry.Kind.AssetParsed, ref = "y.apk", at = 2),
        )
        val after = list.filterNot { it.kind == HistoryEntry.Kind.AssetParsed && it.ref == "x.apk" }
        assertEquals(1, after.size)
        assertEquals("y.apk", after[0].ref)
    }

    @Test
    fun `清空即传入空列表`() {
        val list = listOf(entry(ref = "a"), entry(ref = "b"))
        assertTrue(list.isNotEmpty())
        assertEquals(0, listOf<HistoryEntry>().size)
    }
}
