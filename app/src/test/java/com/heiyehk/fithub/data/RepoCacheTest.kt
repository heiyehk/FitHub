package com.heiyehk.fithub.data

import com.heiyehk.fithub.data.remote.RepoCache
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 缓存读取的 TTL 行为。
 *
 * 钉的是两件事：
 * 1) 未过期时按 TTL 判定，超期后 [RepoCache.read] 返回 null
 * 2) [RepoCache.readIgnoringAge] 永远读得到，并把数据年龄一并返回
 *
 * 第 2 条是离线可看的前提 —— 缓存超过 TTL 后如果直接丢弃，
 * 断网打开就会是空白，而不是那份旧数据。
 */
class RepoCacheTest {

    @Serializable
    private data class Payload(val id: String, val n: Int)

    private val json = Json { ignoreUnknownKeys = true }

    private fun cache() = RepoCache(File(System.getProperty("java.io.tmpdir"), "fithub-test-${System.nanoTime()}"), json)

    /** 把文件的修改时间往前推，模拟「这份缓存已经放了 N 毫秒」 */
    private fun age(file: File, ms: Long) {
        file.setLastModified(System.currentTimeMillis() - ms)
    }

    @Test
    fun `未过期时 read 返回数据`() {
        val c = cache()
        c.write("k", Payload("a", 1))
        val got = c.read<Payload>("k", maxAgeMs = 60_000)
        assertNotNull(got)
        assertEquals("a", got!!.id)
    }

    @Test
    fun `超过 TTL 后 read 返回 null`() {
        val c = cache()
        c.write("k", Payload("a", 1))
        val f = c.file("k")
        age(f, 120_000)
        assertNull(c.read<Payload>("k", maxAgeMs = 60_000))
    }

    @Test
    fun `readIgnoringAge 忽略 TTL 仍能读到数据`() {
        val c = cache()
        c.write("k", Payload("a", 1))
        val f = c.file("k")
        age(f, 10 * 60_000)  // 10 分钟前

        val got = c.readIgnoringAge<Payload>("k")
        assertNotNull("过期缓存必须读得到，否则离线打开是空白", got)
        assertEquals("a", got!!.first.id)
    }

    @Test
    fun `readIgnoringAge 返回的年龄与文件时间一致`() {
        val c = cache()
        c.write("k", Payload("a", 1))
        val f = c.file("k")
        age(f, 5 * 60_000)

        val got = c.readIgnoringAge<Payload>("k")
        assertNotNull(got)
        // 文件时间被回拨了 5 分钟，年龄应接近 5 分钟
        assertTrue(
            "年龄应在 5 分钟左右，实际 ${got!!.second}",
            got.second in (4 * 60_000)..(6 * 60_000),
        )
    }

    @Test
    fun `readIgnoringAge 对不存在的 key 返回 null`() {
        val c = cache()
        assertNull(c.readIgnoringAge<Payload>("missing"))
    }

    @Test
    fun `内容损坏时两个读取都返回 null 而不是抛异常`() {
        val c = cache()
        c.file("k").writeText("{ 不是合法 JSON")
        assertNull(c.read<Payload>("k"))
        assertNull(c.readIgnoringAge<Payload>("k"))
    }

    @Test
    fun `key 里的特殊字符被清洗成安全文件名`() {
        val c = cache()
        c.write("a/b c:d", Payload("x", 1))
        assertTrue("不应逃出缓存目录", c.file("a/b c:d").parentFile == c.dir)
        assertNotNull(c.readIgnoringAge<Payload>("a/b c:d"))
    }
}
