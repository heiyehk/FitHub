package com.heiyehk.fithub.data

import com.heiyehk.fithub.data.remote.ReleaseDto
import com.heiyehk.fithub.data.remote.ReleasePick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「最新版」选取规则。
 *
 * 这个测试是为了钉住一个真实 bug：原来的实现是
 * `releases.filter { !it.draft }.firstOrNull()` —— 只滤草稿、**不滤预发布**，
 * 于是 Flutter 那种把 `3.19.0-0.1.pre` 当成最新版的仓库，详情页版本号、
 * 分享链接、「可升级」判断全跟着错，而且很难被察觉（它看起来就是个正常版本号）。
 */
class ReleasePickTest {

    private fun rel(
        tag: String,
        prerelease: Boolean = false,
        draft: Boolean = false,
    ) = ReleaseDto(tagName = tag, prerelease = prerelease, draft = draft)

    @Test
    fun `预发布不会被当成最新版`() {
        val list = listOf(
            rel("3.19.0-0.1.pre", prerelease = true),
            rel("3.18.0"),
        )
        // GitHub 按发布时间倒序返回，所以第一个就是最新的
        assertEquals("3.19.0-0.1.pre", list.first().tagName)
        // 关掉预发布时必须退回 3.18.0，而不是那个 pre
        assertEquals("3.18.0", ReleasePick.latest(list, includePrerelease = false)?.tagName)
    }

    @Test
    fun `打开预发布后最新版就是预发布`() {
        val list = listOf(
            rel("3.19.0-0.1.pre", prerelease = true),
            rel("3.18.0"),
        )
        assertEquals("3.19.0-0.1.pre", ReleasePick.latest(list, includePrerelease = true)?.tagName)
    }

    @Test
    fun `草稿永远被排除`() {
        val list = listOf(
            rel("v9.9.9-draft", draft = true),
            rel("v1.0.0"),
        )
        // 即使打开预发布，草稿也不参与
        assertEquals("v1.0.0", ReleasePick.latest(list, includePrerelease = true)?.tagName)
    }

    @Test
    fun `空列表返回 null 而不是造一个版本号`() {
        assertNull(ReleasePick.latest(emptyList(), includePrerelease = false))
        assertNull(ReleasePick.latest(emptyList(), includePrerelease = true))
    }

    @Test
    fun `只有预发布时关掉开关就没有最新版`() {
        val list = listOf(rel("0.2.0-beta", prerelease = true))
        assertNull(ReleasePick.latest(list, includePrerelease = false))
        assertEquals("0.2.0-beta", ReleasePick.latest(list, includePrerelease = true)?.tagName)
    }

    @Test
    fun `visible 滤空时不能就此断言「这个仓库没有 release」`() {
        // 「一条都没滤出来」和「这个仓库没有 release」是两回事 ——
        // v2rayNG 就是被这里判死的：拉回来的几条恰好全是预发布，滤完是空的，
        // 于是页面显示「没有产物」，而真正的正式版排在请求范围之外。
        //
        // `visible` 本身就该返回空（它是纯筛选，不该替调用方兜底）；
        // 兜底发生在 GitHubMapper.applyReleases 那一层。两者都要有测试盯着。
        val onlyPre = listOf(rel("0.2.0-beta", prerelease = true))
        assertTrue(
            "筛选层如实返回空即可，不该在这里偷偷塞回预发布",
            ReleasePick.visible(onlyPre, includePrerelease = false).isEmpty(),
        )
        assertTrue(
            "草稿同样滤掉",
            ReleasePick.visible(listOf(rel("0.1.0", draft = true)), includePrerelease = false)
                .isEmpty(),
        )
    }

    @Test
    fun `visible 会滤掉不该参与适配判定的条目`() {
        val list = listOf(
            rel("0.3.0-beta", prerelease = true),
            rel("0.2.0"),
            rel("0.9.0-draft", draft = true),
        )
        assertEquals(
            listOf("0.2.0"),
            ReleasePick.visible(list, includePrerelease = false).map { it.tagName },
        )
        assertEquals(
            listOf("0.3.0-beta", "0.2.0"),
            ReleasePick.visible(list, includePrerelease = true).map { it.tagName },
        )
    }
}
