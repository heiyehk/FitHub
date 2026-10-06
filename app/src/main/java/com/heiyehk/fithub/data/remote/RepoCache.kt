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

    /** key 落到文件名时的替换规则。抽出来是因为 [removeByPrefix] 必须用同一套 */
    private fun sanitize(key: String) = key.replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        const val MAX_AGE = 6 * 60 * 60 * 1000L   // 6 小时
    }
}
