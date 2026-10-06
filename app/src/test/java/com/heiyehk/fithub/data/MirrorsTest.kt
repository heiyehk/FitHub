package com.heiyehk.fithub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下载镜像的 URL 拼接与回退顺序。
 *
 * 这两处都是「拼错了不会崩，只会悄悄下到别的东西」的代码，所以钉死。
 */
class MirrorsTest {

    private val original = "https://github.com/owner/repo/releases/download/v1.0.0/app.apk"

    @Test
    fun `直连不加分隔`() {
        assertEquals(original, Mirrors.apply(original, Mirrors.DIRECT))
    }

    @Test
    fun `镜像前缀拼在前面且不丢斜杠`() {
        val got = Mirrors.apply(original, Mirrors.BY_ID.getValue("gh-proxy"))
        assertEquals("https://gh-proxy.org/https://github.com/owner/repo/releases/download/v1.0.0/app.apk", got)
    }

    @Test
    fun `已经带过前缀的不再套一层`() {
        // 否则重试换镜像会拼成 gh-proxy.org/https://gh-proxy.org/...
        val once = Mirrors.apply(original, Mirrors.BY_ID.getValue("gh-proxy"))
        val twice = Mirrors.apply(once, Mirrors.BY_ID.getValue("gh-proxy-v4"))
        assertEquals(once, twice)
    }

    @Test
    fun `前缀不带 http 就不套用`() {
        // 坏配置不该让下载 URL 变成不可用的东西，退回原地址由上层报错
        val broken = DownloadMirror("broken", "gh-proxy.org/")
        assertEquals(original, Mirrors.apply(original, broken))
    }

    @Test
    fun `直连的下一个是第一个镜像`() {
        assertEquals("gh-proxy", Mirrors.next("direct", onlyMirrors = false)?.id)
    }

    @Test
    fun `镜像按给定顺序回退`() {
        assertEquals("gh-proxy-v6", Mirrors.next("gh-proxy-v4", onlyMirrors = false)?.id)
    }

    @Test
    fun `最后一个镜像回退到直连而不是结束`() {
        // 镜像全挂了也该再直连试一次，否则会误判成「下载失败」
        val last = Mirrors.ALL.last()
        assertEquals("direct", Mirrors.next(last.id, onlyMirrors = false)?.id)
    }

    @Test
    fun `onlyMirrors 模式在末尾返回 null`() {
        val lastMirror = Mirrors.ALL.last()
        assertNull(Mirrors.next(lastMirror.id, onlyMirrors = true))
    }

    @Test
    fun `未知 id 从头开始`() {
        assertEquals("direct", Mirrors.next("nope", onlyMirrors = false)?.id)
    }

    @Test
    fun `只有直连不是第三方`() {
        assertFalse(Mirrors.DIRECT.isThirdParty)
        // 走第三方时 UI 必须提示「流量经过对方」
        assertTrue(Mirrors.BY_ID.getValue("gh-proxy").isThirdParty)
    }

    @Test
    fun `每个 id 都能在 BY_ID 里找到`() {
        for (m in Mirrors.ALL) {
            assertSame(m, Mirrors.BY_ID[m.id])
        }
    }
}
