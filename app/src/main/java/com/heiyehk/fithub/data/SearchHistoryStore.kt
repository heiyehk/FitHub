package com.heiyehk.fithub.data

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * 搜索关键词历史。
 *
 * **和 [HistoryStore]（历史足迹）是两回事**，别合并：
 * - 足迹记的是「这台手机上发生过什么」（看过哪个仓库、装过哪个包），是浏览轨迹
 * - 这里记的是「用户敲过什么词」，是输入习惯
 *
 * 两者上限差一个数量级（200 vs 10），同步策略也不同 —— 足迹明确**不进** WebDAV，
 * 搜索历史同理：换设备不该把另一台机器敲过的词搬过来。
 *
 * 上限 [MAX_ENTRIES] 取 10：搜索历史的价值全在最近几条，超过这个数用户
 * 不会再回头找，落盘一份几百条的词表只是让「清空」这个动作变得没人敢点。
 */
object SearchHistoryStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun store(context: Context) = LocalStore(context.filesDir, json)

    /** 最近在前。读出来就是最终顺序，不再排序 —— 写的时候已经维护好了。 */
    fun list(context: Context): List<String> =
        store(context).read(LocalStore.KEY_SEARCH_HISTORY, emptyList<String>())

    /**
     * 记一次搜索。
     *
     * 重复的词**移到最前**而不是再追加一条：用户刚搜过的词多半正在改它
     * （`kotlin` → `kotlin compose`），追加会让列表里出现两行几乎一样的。
     * 比较忽略大小写 —— `Kotlin` 和 `kotlin` 对 GitHub 是同一个查询。
     *
     * 空白不入库：那不是一次搜索，只是用户清了输入框。
     */
    fun record(context: Context, query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val current = list(context)
        val next = (listOf(q) + current.filterNot { it.equals(q, ignoreCase = true) })
            .take(MAX_ENTRIES)
        store(context).write(LocalStore.KEY_SEARCH_HISTORY, next)
    }

    fun clear(context: Context) {
        store(context).write(LocalStore.KEY_SEARCH_HISTORY, emptyList<String>())
    }

    const val MAX_ENTRIES = 10
}