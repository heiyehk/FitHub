package com.heiyehk.fithub.data

import com.heiyehk.fithub.data.remote.ReadmeDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * README 的解码与截断。
 *
 * 截断这一条要单独钉住：静默截断会让用户以为读完了整份文档，
 * 所以超限时必须带 [Readme.truncated] 标记交给 UI 显式说明。
 */
class ReadmeTest {

    private fun decode(content: String, encoding: String = "base64") =
        ReadmeTest.decodeForTest(ReadmeDto(content = content, encoding = encoding))

    @Test
    fun `base64 内容被还原为原文`() {
        val md = "# 标题\n\n正文"
        val b64 = Base64.getEncoder().encodeToString(md.toByteArray())
        assertEquals(md, decode(b64))
    }

    @Test
    fun `内容里的换行符不影响解码`() {
        val md = "# a\n\n## b\n\n```kotlin\nval x = 1\n```"
        // GitHub 返回的 base64 每 60 字符一个换行，解码前必须先去掉
        val b64 = Base64.getEncoder().encodeToString(md.toByteArray())
        val wrapped = b64.chunked(60).joinToString("\n")
        assertEquals(md, decode(wrapped))
    }

    @Test
    fun `中文与 emoji 能正确还原`() {
        val md = "安装方式\n\n先下载 **APK**，然后 `adb install`。🎉"
        val b64 = Base64.getEncoder().encodeToString(md.toByteArray())
        assertEquals(md, decode(b64))
    }

    @Test
    fun `编码不是 base64 时返回 null`() {
        assertEquals(null, decode("whatever", encoding = "utf-8"))
    }

    @Test
    fun `内容不是合法 base64 时返回 null 而不是抛异常`() {
        assertEquals(null, decode("!!! not base64 !!!"))
    }

    @Test
    fun `未超限的 README 不标记截断`() {
        val r = Readme.from("短内容", "短内容".toByteArray().size)
        assertFalse(r.truncated)
        assertEquals("短内容", r.markdown)
    }

    @Test
    fun `超过 512 KB 时截断并标记`() {
        val big = "x".repeat(Readme.MAX_BYTES + 4096)
        val r = Readme.from(big, big.toByteArray().size)
        assertTrue("超限必须标记截断", r.truncated)
        assertTrue(
            "截断后应不超过上限，实际 ${r.markdown.toByteArray().size}",
            r.markdown.toByteArray().size <= Readme.MAX_BYTES,
        )
        assertTrue("原始大小应被如实记录", r.sizeBytes > Readme.MAX_BYTES)
    }

    @Test
    fun `截断按整行切分，不产生半个代码块`() {
        val line = "y".repeat(100)
        val big = (1..8000).joinToString("\n") { line }
        val r = Readme.from(big, big.toByteArray().size)
        assertTrue(r.truncated)
        // 截断点落在换行符上，所以最后一行仍是完整的原始行，不该被腰斩
        assertTrue("末尾应是完整的一行", r.markdown.endsWith(line))
    }

    @Test
    fun `isEmpty 反映内容是否为空`() {
        assertTrue(Readme("", 0).isEmpty)
        assertTrue(Readme("   \n  ", 5).isEmpty)
        assertFalse(Readme("# x", 3).isEmpty)
    }

    companion object {
        /** 复刻 GitHubApi 的解码逻辑，避免测试去碰网络层 */
        fun decodeForTest(dto: ReadmeDto): String? {
            if (dto.encoding != "base64") return null
            return runCatching {
                dto.content.replace("\n", "").let { Base64.getDecoder().decode(it) }
                    .toString(Charsets.UTF_8)
            }.getOrNull()
        }
    }
}
