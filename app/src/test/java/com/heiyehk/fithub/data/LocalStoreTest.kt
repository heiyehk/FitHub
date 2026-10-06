package com.heiyehk.fithub.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * filesDir 持久化层。
 *
 * 与缓存层的区别是这里存用户数据，读不出来必须退回默认值而不是崩：
 * 绑定表损坏只该让用户重新绑定，不该让整个本机页起不来。
 */
class LocalStoreTest {

    @Serializable
    private data class Entry(val fullName: String, val note: String = "")

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun store() = LocalStore(
        File(System.getProperty("java.io.tmpdir"), "fithub-local-${System.nanoTime()}"),
        json,
    )

    @Test
    fun `写入后能读回`() {
        val s = store()
        s.write("k", mapOf("com.a" to "Org/Repo"))
        assertEquals(mapOf("com.a" to "Org/Repo"), s.read("k", emptyMap<String, String>()))
    }

    @Test
    fun `文件不存在时返回默认值`() {
        val s = store()
        assertEquals(emptyMap<String, String>(), s.read("missing", emptyMap<String, String>()))
    }

    @Test
    fun `内容损坏时返回默认值而不是抛异常`() {
        val s = store()
        s.file("k").writeText("{ 这不是合法 JSON")
        assertEquals(emptyMap<String, String>(), s.read("k", emptyMap<String, String>()))
    }

    @Test
    fun `字段缺失时按默认值补齐`() {
        val s = store()
        // 旧版本写的数据没有 note 字段
        s.file("k").writeText("""[{"fullName":"Org/Repo"}]""")
        val got = s.read<List<Entry>>("k", emptyList())
        assertEquals(1, got.size)
        assertEquals("Org/Repo", got[0].fullName)
        assertEquals("note 应取默认值", "", got[0].note)
    }

    @Test
    fun `不存在未知字段不影响解析`() {
        val s = store()
        s.file("k").writeText("""[{"fullName":"Org/Repo","futureField":123}]""")
        assertEquals(1, s.read<List<Entry>>("k", emptyList()).size)
    }

    @Test
    fun `写入不留下临时文件`() {
        val s = store()
        s.write("k", mapOf("a" to "b"))
        val leftovers = s.dir.listFiles()?.filter { it.name.endsWith(".tmp") }.orEmpty()
        assertTrue("临时文件应被改名或清理，实际残留 ${leftovers.map { it.name }}", leftovers.isEmpty())
    }

    @Test
    fun `remove 后读回默认值`() {
        val s = store()
        s.write("k", mapOf("a" to "b"))
        assertTrue(s.exists("k"))
        s.remove("k")
        assertFalse(s.exists("k"))
        assertEquals(emptyMap<String, String>(), s.read("k", emptyMap<String, String>()))
    }

    @Test
    fun `key 里的特殊字符被清洗且不逃出目录`() {
        val s = store()
        s.write("a/b c:d", mapOf("x" to "y"))
        val f = s.file("a/b c:d")
        assertEquals("清洗后应仍在存储目录内", s.dir, f.parentFile)
        assertEquals(mapOf("x" to "y"), s.read("a/b c:d", emptyMap<String, String>()))
    }

    @Test
    fun `写入前目录不存在会自动创建`() {
        val root = File(System.getProperty("java.io.tmpdir"), "fithub-mkdir-${System.nanoTime()}")
        val nested = File(root, "sub/dir")
        assertFalse(nested.exists())
        LocalStore(nested, json).write("k", mapOf("a" to "b"))
        assertTrue("目录应被自动创建", nested.isDirectory)
    }

    @Test
    fun `存储 key 常量互不冲突`() {
        val keys = listOf(
            LocalStore.KEY_SUBSCRIPTIONS,
            LocalStore.KEY_REPO_LINKS,
            LocalStore.KEY_HOME_SECTIONS,
            LocalStore.KEY_HISTORY,
        )
        assertEquals("key 数量", keys.size, keys.toSet().size)
    }

    @Test
    fun `读取形状不匹配时退回默认值`() {
        val s = store()
        s.write("k", mapOf("a" to "b"))
        // 同一个 key 换一种形状读，反序列化失败，必须退回传入的默认值
        assertEquals("fallback", s.read("k", Entry("fallback")).fullName)
    }
}
