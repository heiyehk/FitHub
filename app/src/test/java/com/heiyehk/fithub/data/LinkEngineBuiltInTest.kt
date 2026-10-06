package com.heiyehk.fithub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置绑定（FitHub ↔ FitHub 自己）的不变量。
 *
 * 这条关系是**固定**的：不可解绑、不可改绑，加载时还会把落盘表里可能存在的
 * 别的值覆盖回正确值。这批断言盯的就是这三条 —— 它们一旦破了，本机上那个 FitHub
 * 就会指向一个不是自己的仓库，而界面上还没有任何入口能改回来。
 */
class LinkEngineBuiltInTest {

    private val selfPkg = "com.heiyehk.fithub"

    /** 只有 FitHub 自己是内置项，普通应用仍然可以被解绑 / 改绑 */
    @Test
    fun `only the app itself is a built-in link`() {
        assertTrue(LinkEngine.isBuiltIn(selfPkg))
        assertFalse(LinkEngine.isBuiltIn("com.schabi.newpipe"))
        assertFalse(LinkEngine.isBuiltIn(""))
        assertFalse(LinkEngine.isBuiltIn("com.heiyehk.fithub.fdroid"))
    }

    /** 空表上加载：内置绑定被种进去 */
    @Test
    fun `built-in link is seeded into an empty table`() {
        val merged = LinkEngine.mergeBuiltIns(emptyMap())
        assertEquals(merged[selfPkg], "heiyehk/FitHub")
    }

    /**
     * 内置项**覆盖**而不是「仅在缺失时补」。
     *
     * 这是最容易写错的一条：写成「缺失才补」的话，一台装过早期版本
     * （那时这条还能改绑）的机器升级上来，会永远指着一个错的仓库，
     * 而且界面上没有入口能改回来 —— 数据错了但没有任何自愈路径。
     */
    @Test
    fun `built-in link overwrites a stale value`() {
        val stale = mapOf(selfPkg to "someone/fork-of-fithub", "com.other.app" to "other/repo")
        val merged = LinkEngine.mergeBuiltIns(stale)
        assertEquals(merged[selfPkg], "heiyehk/FitHub")
        // 别人的绑定一个字都不能动
        assertEquals(merged["com.other.app"], "other/repo")
        assertEquals(merged.size, 2)
    }

    /**
     * 已经正确时合出来的表和原来**内容相等**，`seedBuiltIns` 据此跳过落盘。
     *
     * 这里断言的是相等而不是同一个对象 —— `Map.plus` 总是新建一个 map，
     * 拿 `===` 去断言只会得到一个和实现细节绑死的测试。
     */
    @Test
    fun `merging an already-correct table changes nothing`() {
        val correct = mapOf(selfPkg to "heiyehk/FitHub", "com.other.app" to "other/repo")
        assertEquals(LinkEngine.mergeBuiltIns(correct), correct)
    }
}