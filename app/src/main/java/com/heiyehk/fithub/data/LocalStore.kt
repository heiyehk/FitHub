package com.heiyehk.fithub.data

import kotlinx.serialization.json.Json
import java.io.File

/**
 * filesDir 下的 JSON 持久化，放用户数据。
 *
 * 与 [com.heiyehk.fithub.data.remote.RepoCache] 的分工：
 * - [com.heiyehk.fithub.data.remote.RepoCache] 在 cacheDir，无 TTL，系统清理时丢弃，属于可重建的缓存
 * - 本类在 filesDir，**不设 TTL** —— 关注列表、绑定表、板块配置丢了就是用户数据丢了
 *
 * 结构改动时递增 [VERSION]。key 会带上它，旧结构的文件读不出来时返回默认值，
 * 而不是抛异常或写出错乱的数据。
 */
class LocalStore(@PublishedApi internal val dir: File, @PublishedApi internal val json: Json) {

    @PublishedApi
    internal fun file(key: String): File {
        if (!dir.exists()) dir.mkdirs()
        val safe = key.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(dir, "$safe.json")
    }

    /**
     * 读。文件缺失或内容损坏都返回 [default]，不抛 —— 用户数据读不出来时
     * 表现为「还没有这类数据」，而不是整个界面崩掉。
     */
    inline fun <reified T> read(key: String, default: T): T {
        val f = file(key)
        if (!f.exists()) return default
        return runCatching { json.decodeFromString<T>(f.readText()) }.getOrDefault(default)
    }

    /**
     * 写。先写临时文件再改名，避免写一半被系统杀掉导致文件截断 ——
     * 截断的 JSON 下次就读不出来了。
     */
    inline fun <reified T> write(key: String, value: T) {
        val target = file(key)
        val tmp = File(target.parentFile, "${target.name}.tmp")
        runCatching {
            tmp.writeText(json.encodeToString(value))
            if (!tmp.renameTo(target)) {
                // 部分文件系统上 renameTo 跨覆盖会失败，退化为直写
                target.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    fun exists(key: String): Boolean = file(key).exists()

    fun remove(key: String) {
        runCatching { file(key).delete() }
    }

    companion object {
        /** 数据结构一变就 +1，旧 key 自然失效 */
        const val VERSION = 1

        /** 关注列表的存储 key */
        const val KEY_SUBSCRIPTIONS = "subscriptions"

        /** 包名到仓库的绑定表 */
        const val KEY_REPO_LINKS = "repo-links"

        /** 首页板块配置 */
        const val KEY_HOME_SECTIONS = "home-sections"

        /** 历史足迹 */
        const val KEY_HISTORY = "history"

        /** 搜索历史（关键词），上限见 [com.heiyehk.fithub.data.SearchHistoryStore.MAX_ENTRIES] */
        const val KEY_SEARCH_HISTORY = "search-history"
    }
}
