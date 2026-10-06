package com.heiyehk.fithub.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 足迹条目。
 *
 * 足迹是**设备状态**而不是用户内容：它记的是「这台手机上发生过什么」，
 * 不是「这个账号拥有什么」。所以它不进 WebDAV 同步 —— 同步过去会让另一台
 * 设备出现本机没发生过的浏览记录。
 */
@Serializable
data class HistoryEntry(
    val kind: Kind,
    /** 稳定标识：仓库全名 / 包名 / 资产名。用于去重与单条清除 */
    val ref: String,
    val title: String,
    val detail: String = "",
    val at: Long,
    /**
     * 仓库描述。
     *
     * 只写仓库名的话，一屏「看过的仓库」全是 flutter / scrcpy / zed 这种裸名字，
     * 根本想不起来是哪个东西 —— 描述才是让人认出来的那一行。
     *
     * 后加的字段都带默认值：老条目反序列化出来是空串，UI 侧按「有才显示」处理，
     * 不需要迁移落盘数据。
     */
    val desc: String = "",
    /** 最后提交日期（GitHub 的 pushed_at），用来判断这个仓库还活不活跃 */
    val lastPush: String = "",
    /** 记下这一条时看到的 release 版本 */
    val release: String = "",
) {
    @Serializable
    enum class Kind {
        RepoViewed,
        AssetParsed,
        InstalledViaUs,

        /**
         * 下载过安装包。
         *
         * 之前**没有**这一类，于是「下载与安装记录」页永远是空的 —— 不是没记录，
         * 是从来没往里写。下载完成（校验通过、文件就绪）时记一条。
         */
        Downloaded,
    }

    /** 同一目标再次发生时并入旧条目并更新时间，而不是追加一条 */
    val dedupeKey: String get() = "${kind.name}:$ref"
}

/**
 * 足迹的落盘与内存状态。
 *
 * 环形上限 [MAX_ENTRIES]：超了丢最旧的。足迹的价值在近期，
 * 无限增长只会在几个月后变成一个没人看的占位文件。
 */
object HistoryStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun store(context: Context) = LocalStore(context.filesDir, json)

    fun list(context: Context): List<HistoryEntry> =
        store(context).read(LocalStore.KEY_HISTORY, emptyList<HistoryEntry>())
            .sortedByDescending { it.at }

    /**
     * 记一条。同 [HistoryEntry.dedupeKey] 已存在时并入旧条目并更新时间，
     * 避免「同一个仓库看十次就占满十条」。
     */
    fun record(context: Context, entry: HistoryEntry) {
        val current = list(context)
        val idx = current.indexOfFirst { it.dedupeKey == entry.dedupeKey }
        val next = if (idx >= 0) {
            current.toMutableList().also { it[idx] = entry }
        } else {
            current + entry
        }
        val trimmed = next
            .sortedByDescending { it.at }
            .take(MAX_ENTRIES)
        store(context).write(LocalStore.KEY_HISTORY, trimmed)
    }

    /** 只清某一类，或某一类里的某一条 */
    fun clear(context: Context, kind: HistoryEntry.Kind, ref: String? = null) {
        val next = list(context).filterNot {
            it.kind == kind && (ref == null || it.ref == ref)
        }
        store(context).write(LocalStore.KEY_HISTORY, next)
    }

    fun clearAll(context: Context) {
        store(context).write(LocalStore.KEY_HISTORY, emptyList<HistoryEntry>())
    }

    fun count(context: Context): Int = list(context).size

    fun countOf(context: Context, kind: HistoryEntry.Kind): Int =
        list(context).count { it.kind == kind }

    const val MAX_ENTRIES = 200
}
