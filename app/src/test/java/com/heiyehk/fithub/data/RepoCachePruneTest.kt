package com.heiyehk.fithub.data

import com.heiyehk.fithub.data.remote.RepoCache
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * [RepoCache.pruneOlderThan]。
 *
 * 这函数会**真删文件**，所以判据必须覆盖「删对了」和「不该删的还在」两侧 ——
 * 只断言「老文件没了」的话，把整个目录清空也能过。
 */
class RepoCachePruneTest {

    private fun cache(dir: File) = RepoCache(dir, Json { ignoreUnknownKeys = true })

    /**
     * 新建一个**已存在**的目录。
     *
     * `File.writeText` 不会建父目录，而生产路径是先 [RepoCache.file] 的 `mkdirs()`
     * 再写；测试直接 `writeText` 就必须自己建 —— 这一点本身也说明清理函数
     * 面对的目录永远是「已存在」的。
     */
    private fun newDir(tag: String): File =
        File(System.getProperty("java.io.tmpdir"), "fithub-prune-$tag-${System.nanoTime()}").apply { mkdirs() }

    private fun File.ageTo(days: Long) {
        setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days))
    }

    @Test
    fun `过期的删掉 新的留下`() {
        val dir = newDir("basic")
        val c = cache(dir)
        val old = File(dir, "old.json").apply { writeText("{}"); ageTo(30) }
        val older = File(dir, "older.json").apply { writeText("{}"); ageTo(400) }
        val fresh = File(dir, "fresh.json").apply { writeText("{}"); ageTo(1) }

        val n = c.pruneOlderThan(TimeUnit.DAYS.toMillis(7))

        assertEquals("应删两条", 2, n)
        assertFalse("30 天前的该删", old.exists())
        assertFalse("400 天前的该删", older.exists())
        assertTrue("1 天前的必须留下 —— 它仍可能被命中", fresh.exists())
    }

    /** 刚好卡在边界上的：6 天前的不该被 7 天的保留期删掉 */
    @Test
    fun `边界值不算过期`() {
        val dir = newDir("edge")
        val c = cache(dir)
        val edge = File(dir, "edge.json").apply { writeText("{}"); ageTo(6) }
        c.pruneOlderThan(TimeUnit.DAYS.toMillis(7))
        assertTrue("6 天前的不该被 7 天的保留期删掉", edge.exists())
    }

    /**
     * 只动 `.json` **文件**。
     *
     * 两个坑各对应一个断言：
     * - 非 `.json` 的文件不该删 —— 目录里将来可能有索引、锁文件
     * - 叫 `x.json` 的**目录**也不该删 —— `File.delete()` 对空目录同样返回 true，
     *   只按后缀写的实现会把它真删掉。
     *
     * ⚠️ 写这条时踩过一次**空洞断言**：只 `mkdirs()` 没 `ageTo(...)`，
     * 目录的 mtime 是当下，压根没到清理条件，有没有 `isFile` 都会留下 ——
     * 拆掉守卫跑，测试照样全绿。**「过期的」和「被删的」两个条件一个都不能省。**
     */
    @Test
    fun `不动非 json 文件 也不动目录`() {
        val dir = newDir("other")
        val c = cache(dir)
        val keep = File(dir, "notes.txt").apply { writeText("x"); ageTo(400) }
        val sub = File(dir, "sub.json").apply { mkdirs(); ageTo(400) }
        File(dir, "inside.json").apply { writeText("{}"); ageTo(400) }

        c.pruneOlderThan(TimeUnit.DAYS.toMillis(7))

        assertTrue("非 .json 文件不该被删", keep.exists())
        assertTrue("名字以 .json 结尾的目录不该被删", sub.exists())
        assertFalse("目录里面的过期文件照常清理", File(dir, "inside.json").exists())
    }

    /**
     * 变异验证：`isFile` 去掉后，这一条必须变红。
     *
     * 上一版因为没给目录设过期时间，拆掉守卫也照样绿 —— 那条断言当时是空的。
     * 现在的前提是真的：目录 400 天前建的。
     */
    @Test
    fun `守卫是有效的`() {
        val dir = newDir("guard")
        val sub = File(dir, "x.json").apply { mkdirs(); ageTo(400) }
        cache(dir).pruneOlderThan(TimeUnit.DAYS.toMillis(7))
        assertTrue("这个目录既过期、名字又是 .json，唯一的防线就是 isFile", sub.exists())
    }

    /** 目录还不存在时不能抛 —— 冷启动时缓存目录常常是空的 */
    @Test
    fun `目录不存在时安全返回`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "fithub-prune-none-${System.nanoTime()}")
        assertEquals(0, cache(dir).pruneOlderThan(TimeUnit.DAYS.toMillis(7)))
        assertFalse("不该顺手把目录建出来", dir.exists())
    }

    /** 连续调两次：第二次不该再删任何东西（说明上一次的删除真的生效了） */
    @Test
    fun `重复调用是幂等的`() {
        val dir = newDir("idem")
        val c = cache(dir)
        File(dir, "a.json").apply { writeText("{}"); ageTo(30) }
        File(dir, "b.json").apply { writeText("{}"); ageTo(30) }
        assertEquals(2, c.pruneOlderThan(TimeUnit.DAYS.toMillis(7)))
        assertEquals("第二次应该一条都删不到", 0, c.pruneOlderThan(TimeUnit.DAYS.toMillis(7)))
    }
}
