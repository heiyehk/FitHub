package com.heiyehk.fithub.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * 「刷新」必须真的作废掉 releases 缓存。
 *
 * 这是已经发布出去的一个 bug：`invalidateRepo` 里写死了 `releases-X-3`，
 * 而详情早就改成一次拉 20 条（BUG-02 为了修 v2rayNG 被判成空仓库），
 * 于是那个 key 从来不存在 —— **详情页的 refresh 按下去，releases 一次都没被作废过**。
 * stars、描述、最后推送会更新（那部分 key 是对的），release 列表纹丝不动，
 * 而那恰恰是详情页的主体。症状是不报错、不转圈、看起来像没刷新。
 *
 * **为什么这批测试不能只测 [RepoCache.removeByPrefix]**
 *
 * 第一版只测了前缀删除本身，结果做变异验证时把 `invalidateRepo` 改回写死 `-3`，
 * 203 条测试**一条都没红** —— 因为根本没有一条断言穿过
 * 「`invalidateRepo` → releases 的真实 key」这条路径。
 * 测一个用对了就永远对的纯函数，挡不住接线接错。所以下面直接用 [GitHubApi]
 * 自己的 key 构造函数造缓存，再断言 `invalidateRepo` 把它清掉。
 */
class GitHubApiInvalidateTest {

    @kotlinx.serialization.Serializable
    private data class Rel(val tag: String)

    private val json = Json { ignoreUnknownKeys = true }

    /** 不需要网络：作废只碰磁盘 */
    private fun api() = GitHubApi(
        File(System.getProperty("java.io.tmpdir"), "fithub-inv-${System.nanoTime()}"),
    )

    private fun put(api: GitHubApi, key: String) {
        api.cache.write(GitHubApi.CACHE_VERSION + key, Rel("v1"))
    }

    private fun has(api: GitHubApi, key: String) =
        api.cache.readIgnoringAge<Rel>(GitHubApi.CACHE_VERSION + key) != null

    /**
     * 本项目用到的全部 perPage。
     *
     * 列在这里不是为了枚举它们好去作废（作废走前缀，不用清单），
     * 而是为了让「新增一个 perPage 后刷新还有效」这件事**在测试里显形**。
     */
    private val perPageInUse = listOf(1, 5, 20)

    /** 回归本体：刷新之后，详情页那份 releases 缓存必须没了 */
    @Test
    fun `invalidateRepo 清掉全部 perPage 的 releases 缓存`() {
        val api = api()
        val repo = "owner/repo"
        perPageInUse.forEach { put(api, api.releasesCacheKey(repo, it)) }
        // 详情页实际用的那条（RELEASES_PER_PAGE = 20）
        assertNotNull(
            "前置条件没成立：详情页那条缓存根本没写进去",
            api.cache.readIgnoringAge<Rel>(
                GitHubApi.CACHE_VERSION + api.releasesCacheKey(repo, 20),
            ),
        )

        api.invalidateRepo(repo)

        for (p in perPageInUse) {
            assertNull(
                "perPage=$p 的 releases 缓存没被作废 —— 刷新会看起来没反应",
                api.cache.readIgnoringAge<Rel>(
                    GitHubApi.CACHE_VERSION + api.releasesCacheKey(repo, p),
                ),
            )
        }
    }

    /**
 * 详情页那份数据由元信息和 releases 两份组成，**两份都要清**。
     *
     * 少了 releases 那份就是当前这个 bug；少了元信息那份则会让 stars / 描述 /
     * 最后推送停在旧值 —— 症状同样是「刷新没反应」，只是页面更靠下的部分。
     */
    @Test
    fun `invalidateRepo 清掉元信息与 releases 两份`() {
        val api = api()
        val repo = "owner/repo"
        put(api, "repo-$repo")
        put(api, api.releasesCacheKey(repo, 20))

        api.invalidateRepo(repo)

        assertNull(
            "元信息缓存没被作废 —— stars / 描述会停在旧值",
            api.cache.readIgnoringAge<Rel>(GitHubApi.CACHE_VERSION + "repo-$repo"),
        )
        assertNull(
            "releases 缓存没被作废 —— release 列表会停在旧值",
            api.cache.readIgnoringAge<Rel>(GitHubApi.CACHE_VERSION + api.releasesCacheKey(repo, 20)),
        )
    }

    /** 别的仓库不能被连坐 */
    @Test
    fun `invalidateRepo 不波及别的仓库`() {
        val api = api()
        put(api, api.releasesCacheKey("owner/repo", 20))
        put(api, api.releasesCacheKey("other/repo", 20))

        api.invalidateRepo("owner/repo")

        assertNull(api.cache.readIgnoringAge<Rel>(GitHubApi.CACHE_VERSION + api.releasesCacheKey("owner/repo", 20)))
        assertNotNull(api.cache.readIgnoringAge<Rel>(GitHubApi.CACHE_VERSION + api.releasesCacheKey("other/repo", 20)))
    }
}