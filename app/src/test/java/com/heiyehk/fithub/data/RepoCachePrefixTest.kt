package com.heiyehk.fithub.data

import com.heiyehk.fithub.data.remote.RepoCache
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * 按前缀作废缓存条目。
 *
 * 这批测试盯的是已经发布出去的一个 bug：`invalidateRepo` 写死作废
 * `releases-X-3`，而详情早就改成一次拉 20 条了，于是那个 key 从来不存在，
 * **刷新时 releases 缓存一次都没被真正作废过** —— 界面表现为「刷新按了没反应」，
 * 不报错、不转圈、数据纹丝不动。
 *
 * 写死取值清单只能挡住「有人又改了那个数字」。按前缀删才是不用维护清单的做法，
 * 所以这里钉的是前缀这个行为本身。
 */
class RepoCachePrefixTest {

    @Serializable
    private data class Payload(val id: String, val n: Int)

    private val json = Json { ignoreUnknownKeys = true }

    private fun cache() = RepoCache(
        File(System.getProperty("java.io.tmpdir"), "fithub-prefix-${System.nanoTime()}"),
        json,
    )

    /**
     * 一个仓库在盘上可能有三种 perPage 的 releases 缓存。
     * 前缀作废必须**一次全清** —— 这正是原来写死 `-3` 时漏掉的那两条。
     */
    @Test
    fun `前缀作废清掉全部 perPage 取值`() {
        val c = cache()
        listOf(1, 5, 20).forEach { c.write("v2-releases-owner_repo-$it", Payload("r$it", it)) }
        assertNotNull(c.readIgnoringAge<Payload>("v2-releases-owner_repo-20"))

        c.removeByPrefix("v2-releases-owner_repo-")

        for (p in listOf(1, 5, 20)) {
            assertNull("perPage=$p 的缓存没有被清掉", c.readIgnoringAge<Payload>("v2-releases-owner_repo-$p"))
        }
    }

    /** 别人的仓库不能被连坐 */
    @Test
    fun `前缀作废不波及别的仓库`() {
        val c = cache()
        c.write("v2-releases-owner_repo-20", Payload("mine", 1))
        c.write("v2-releases-other_repo-20", Payload("theirs", 1))

        c.removeByPrefix("v2-releases-owner_repo-")

        assertNull(c.readIgnoringAge<Payload>("v2-releases-owner_repo-20"))
        assertNotNull(c.readIgnoringAge<Payload>("v2-releases-other_repo-20"))
    }

    /**
     * 非本家族的数据不能被误删。
     *
     * `readme-owner_repo` 和 `repo-owner_repo` 都以 owner_repo 结尾，
     * 前缀写窄了或写宽了都会出问题。
     */
    @Test
    fun `只删 releases 家族 不动同一仓库的其它缓存`() {
        val c = cache()
        c.write("v2-repo-owner_repo", Payload("meta", 1))
        c.write("v2-readme-owner_repo", Payload("readme", 1))
        c.write("v2-releases-owner_repo-20", Payload("rel", 1))

        c.removeByPrefix("v2-releases-owner_repo-")

        assertNotNull(c.readIgnoringAge<Payload>("v2-repo-owner_repo"))
        assertNotNull(c.readIgnoringAge<Payload>("v2-readme-owner_repo"))
        assertNull(c.readIgnoringAge<Payload>("v2-releases-owner_repo-20"))
    }

    /**
     * 前缀要按和写文件时同一套规则清洗。
     *
     * key 里仓库名带 `/`（`owner/repo`），落盘时被换成 `_`。作废方如果不做
     * 同样的替换，传进来的前缀就一个文件都匹配不到 —— 而症状和「写死错了 perPage」
     * 一模一样：刷新没反应。所以这条专门钉住清洗规则被复用了。
     */
    @Test
    fun `前缀同样按落盘规则清洗`() {
        val c = cache()
        c.write("v2-releases-owner/repo-20", Payload("r", 1))
        assertNotNull("带斜杠的 key 应当仍然能写出来", c.readIgnoringAge<Payload>("v2-releases-owner/repo-20"))

        c.removeByPrefix("v2-releases-owner/repo-")

        assertNull(c.readIgnoringAge<Payload>("v2-releases-owner/repo-20"))
    }

    /**
     * 多删比漏删安全，所以前缀**确实会顺带清掉某些邻居仓库**。
     *
     * 邻居长这样：仓库名本身以 `-数字` 结尾（`owner/repo-2`）。它在盘上是
     * `releases-owner_repo-2-20`，而被作废的仓库 `owner/repo` 前缀是
     * `releases-owner_repo-` —— 前缀正好在 `-2` 之前断开，于是被一起清掉。
     *
     * 写这个测试是因为我第一版把邻居写成了 `owner/repo2`，断言当场转红：
     * 那个 key 是 `releases-owner_repo2-20`，`-` 位置对不上，**并没有**被清掉。
     * 也就是说前缀比看上去更窄，少误伤；但上面那种形状的误伤是真实存在的。
     */
    @Test
    fun `仓库名以 -数字 结尾的邻居会被顺带清掉`() {
        val c = cache()
        c.write("v2-releases-owner_repo-20", Payload("a", 1))
        c.write("v2-releases-owner_repo-2-20", Payload("b", 1))
        c.write("v2-releases-owner_repo2-20", Payload("c", 1))

        c.removeByPrefix("v2-releases-owner_repo-")

        assertNull(c.readIgnoringAge<Payload>("v2-releases-owner_repo-20"))
        // 名字撞上前缀的会被连坐
        assertNull(c.readIgnoringAge<Payload>("v2-releases-owner_repo-2-20"))
        // 撞不上的（仓库名是 repo2，不是 repo-2）不受影响
        assertNotNull(c.readIgnoringAge<Payload>("v2-releases-owner_repo2-20"))
    }

    /** 空目录 / 前缀不存在时不能抛异常 —— 刷新是随手会按的动作 */
    @Test
    fun `空目录与不存在的前缀都不抛`() {
        val c = cache()
        c.removeByPrefix("v2-releases-nothing_here-")
        c.write("v2-releases-owner_repo-20", Payload("a", 1))
        val kept = c.readIgnoringAge<Payload>("v2-releases-owner_repo-20")
        assertNotNull(kept)
        // readIgnoringAge 返回的是「数据 + 年龄」，数据在 first 上
        assertEquals(1, kept!!.first.n)
    }
}