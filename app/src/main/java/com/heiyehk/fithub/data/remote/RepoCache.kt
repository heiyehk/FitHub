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
        val safe = key.replace(Regex("[^A-Za-z0-9._-]"), "_")
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

    companion object {
        const val MAX_AGE = 6 * 60 * 60 * 1000L   // 6 小时
    }
}
