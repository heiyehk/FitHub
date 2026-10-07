package com.heiyehk.fithub.data.remote

import kotlinx.serialization.json.Json
import java.io.File

/**
 * 极简文件缓存：JSON 落在 cacheDir，6 小时内直接用。
 *
 * 不用 Room 是为了少一个依赖（Room 还要 KSP），当前缓存结构也只有一层。
 * 需要离线搜索或订阅列表时再换 Room。
 */
class RepoCache(@PublishedApi internal val dir: File, @PublishedApi internal val json: Json) {

    @PublishedApi
    internal fun file(key: String): File {
        val safe = sanitize(key)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$safe.json")
    }

    inline fun <reified T> read(key: String, maxAgeMs: Long = MAX_AGE): T? {
        val f = file(key)
        if (!f.exists()) return null
        if (System.currentTimeMillis() - f.lastModified() > maxAgeMs) return null
        return runCatching { json.decodeFromString<T>(f.readText()) }.getOrNull()
    }

    /**
     * 读缓存并同时返回数据年龄，忽略 TTL。
     *
     * 离线兜底必须走这条路径：数据在磁盘上就该先用上，过期与否交给 UI
     * 标注时间，而不是直接丢弃 —— 丢弃会让「离线可看」在缓存过期后变成空白。
     */
    inline fun <reified T> readIgnoringAge(key: String): Pair<T, Long>? {
        val f = file(key)
        if (!f.exists()) return null
        val age = (System.currentTimeMillis() - f.lastModified()).coerceAtLeast(0L)
        val value = runCatching { json.decodeFromString<T>(f.readText()) }.getOrNull() ?: return null
        return value to age
    }

    inline fun <reified T> write(key: String, value: T) {
        runCatching { file(key).writeText(json.encodeToString(value)) }
    }

    /** 手动刷新时把整条缓存清掉，保证用户拿到的是新的 */
    fun clearAll() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    /** 只丢一个条目。手动刷新用这个，避免连带作废其它接口的缓存 */
    fun remove(key: String) {
        runCatching { file(key).delete() }
    }

    /**
     * 按前缀删掉一批条目。
     *
     * 存在的理由是「同一个 key 家族由多处用不同后缀取同一份数据」这种情况 ——
     * 现在只有 releases：`perPage` 进了缓存 key，于是同一个仓库的 release 列表
     * 在盘上可能是 `releases-X-1`、`releases-X-5`、`releases-X-20` 三条。
     *
     * 作废方如果写死一份清单，那么**新增一个 perPage 就会漏掉那一条**，
     * 而症状是「刷新按了没反应」：不报错、不转圈、数据纹丝不动。
     * 写测试能挡住一次回归，但结构上的坑还在 —— 扫目录删前缀则不存在这个问题。
     *
     * **前缀匹配会多删**：仓库名本身以 `-` 开头的邻居会被连坐 ——
     * `releases-owner_repo-` 会命中 `owner/repo-2` 的 `releases-owner_repo-2-20`。
     * 这是刻意的取舍：缓存多删只是下次多发一次请求，漏删则是刷新假装成功却什么都没变。
     * 反过来 `owner/repo2`（没有那个 `-`）**不会**被误伤，误伤范围比看上去窄。
     */
    fun removeByPrefix(prefix: String) {
        val safe = sanitize(prefix)
        runCatching {
            dir.listFiles()?.forEach { f ->
                if (f.name.startsWith(safe) && f.name.endsWith(".json")) f.delete()
            }
        }
    }

    /**
     * 删掉 [maxAgeMs] 之前写下的条目，返回删了几条。
     *
     * ## 为什么需要它
     *
     * 缓存键里带着**会变的东西**（查询串、perPage、topic 改版），而键一旦变了，
     * 旧键写下的文件就成了**孤儿**：永远不会被读到，也永远不会被清掉。
     * 实测踩过的两处：
     * - 删掉「上升」榜后，`v2-discover-stars-created___2026-09-07_stars__100…` 留在盘上
     * - `has:release` 被证明是空操作后，`baseQuery` 变了，旧 query 的文件同样留了下来
     *
     * 这些文件不大（每个几 KB 到几十 KB），但它们只增不减，App 用一年目录就是垃圾场。
     * 而**没有清理的代价比想象中大**：用户改一次配置就多一份死数据，
     * 排查缓存问题时还得先分辨「哪些 key 还没人用了」。
     *
     * ## 为什么按「多久没碰过」而不是按「哪些 key」
     *
     * 没人知道现在到底有多少个 key 家族（搜索、发现、详情、releases…），
     * 也没有一张能穷举的清单。按时间删只需要一个数字，且**天然覆盖未来新增的键**。
     *
     * TTL 6 小时 + stale-while-revalidate 意味着**任何超过几天的文件都不可能被命中**，
     * 所以删掉它们不改变任何行为，只是回收空间。7 天给足余量。
     */
    fun pruneOlderThan(maxAgeMs: Long): Int {
        val cut = System.currentTimeMillis() - maxAgeMs
        var n = 0
        runCatching {
            dir.listFiles()?.forEach { f ->
                // 三个条件缺一不可：
                // `.json`  —— 目录里若还有别的文件，不该被这个方法顺手删掉
                // `isFile` —— **File.delete() 对空目录也返回 true**。一个叫 `x.json`
                //   的空目录（解压、备份、临时结构都可能造出来）会被真删掉。
                //   判据只按后缀写时，这条完全测不出来。
                // 够旧      —— 7 天前写下的不可能再被命中（TTL 6 小时）
                if (f.isFile && f.name.endsWith(".json") && f.lastModified() < cut && f.delete()) n++
            }
        }
        return n
    }

    /** key 落到文件名时的替换规则。抽出来是因为 [removeByPrefix] 必须用同一套 */
    private fun sanitize(key: String) = key.replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        const val MAX_AGE = 6 * 60 * 60 * 1000L   // 6 小时

        /** [pruneOlderThan] 的默认保留天数 */
        const val PRUNE_DAYS = 7L
    }
}
