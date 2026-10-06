package com.heiyehk.fithub.data

import com.heiyehk.fithub.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 订阅的导入导出格式。
 *
 * 用自家 JSON 而不是 OPML：OPML 是 RSS 订阅清单的标准格式，而这里「订阅」指
 * 关注仓库以获取 release 更新，不是 RSS 订阅，属于类型错配。
 *
 * 条目带完整快照而不是只存 `owner/repo`，是为了让导入后立刻能离线看到
 * 内容。`metaFetchedAt` 一并保留，所以旧快照在导入后仍会被标为过期。
 */
@Serializable
data class SubscriptionFile(
    val schema: Int = CURRENT_SCHEMA,
    val app: String = "FitHub",
    val exportedAt: String = "",
    val items: List<Subscription> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA = 1
    }
}

/** 导入结果。UI 要按类型给不同的说法，不能一律显示「导入失败」 */
sealed interface ImportOutcome {
    /**
     * @param merged 合并后的完整列表，调用方直接落盘
     * @param added 新增条数
     * @param skipped 已在列表里、因而跳过的条数
     * @param invalid 因缺少合法 `owner/repo` 而丢弃的条数
     */
    data class Ok(
        val merged: List<Subscription>,
        val added: Int,
        val skipped: Int,
        val invalid: Int,
    ) : ImportOutcome

    /** 版本不认识。整个文件拒绝，不做部分导入 —— 半份数据比没导入更难收拾 */
    data class UnsupportedSchema(val found: Int, val supported: Int) : ImportOutcome

    /**
     * 内容不是合法 JSON，或结构对不上。
     *
     * [reason] 会被 UI 原样显示，所以是 [Explain] 而不是 String。第三方（kotlinx.serialization）
     * 的英文报错原样作为参数传下去，不翻译 —— 那是别人的话。
     */
    data class Malformed(val reason: Explain) : ImportOutcome

    data object Empty : ImportOutcome
}

/**
 * 订阅的导入导出。
 *
 * 导入默认是**合并**而不是覆盖：覆盖会静默丢掉用户本机已有的关注项，
 * 而合并最多只是多几条重复（`SubscriptionStore` 按 fullName 去重）。
 * 需要覆盖时先清空再导入。
 */
object SubscriptionTransfer {

    private val json = Json {
        ignoreUnknownKeys = true      // 旧版/新版多出的字段直接忽略
        encodeDefaults = true
        isLenient = true
    }

    fun export(subs: List<Subscription>, now: Long = System.currentTimeMillis()): String =
        json.encodeToString(
            SubscriptionFile.serializer(),
            SubscriptionFile(
                exportedAt = isoOf(now),
                items = subs.sortedBy { it.fullName.lowercase() },
            ),
        )

    /** 建议的导出文件名：fithub-subscriptions-2026-10-05.json */
    fun suggestedFileName(now: Long = System.currentTimeMillis()): String =
        "fithub-subscriptions-${java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(now), java.time.ZoneOffset.UTC)}.json"

    /**
     * 只取导出时间，不碰条目。
     *
     * 同步用它在本地与云端之间判新旧。手搓字符串找 `"exportedAt"` 那种写法
     * 一旦字段名或格式变了就静默返回 null，结果就是「每次都上传」——
     * 多设备场景下会把另一台设备的修改覆盖掉。
     */
    fun exportedAtOf(text: String): String? = runCatching {
        json.decodeFromString(SubscriptionFile.serializer(), text).exportedAt.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun import(
        text: String,
        into: List<Subscription>,
        now: Long = System.currentTimeMillis(),
    ): ImportOutcome {
        val file = runCatching { json.decodeFromString(SubscriptionFile.serializer(), text) }
            .getOrElse {
                val raw = it.message
                return ImportOutcome.Malformed(
                    if (raw == null) Explain.of(R.string.import_not_json)
                    else Explain(R.string.import_malformed_raw, listOf(raw)),
                )
            }

        if (file.schema != SubscriptionFile.CURRENT_SCHEMA) {
            // 明确拒绝整个文件。不认识的版本只导一半，用户根本看不出来少了什么
            return ImportOutcome.UnsupportedSchema(file.schema, SubscriptionFile.CURRENT_SCHEMA)
        }

        if (file.items.isEmpty()) return ImportOutcome.Empty

        val existing = into.map { it.fullName.lowercase() }.toMutableSet()
        val merged = into.toMutableList()
        var added = 0
        var skipped = 0
        var invalid = 0

        for (raw in file.items) {
            val full = raw.fullName.trim()
            if (!isValidFullName(full)) {
                invalid++
                continue
            }
            val key = full.lowercase()
            if (key in existing) {
                skipped++
                continue
            }
            existing += key
            // addedAt 沿用文件里的值，保留「什么时候关注的」；缺失时用当前时间
            merged += raw.copy(
                fullName = full,
                addedAt = raw.addedAt.takeIf { it > 0 } ?: now,
            )
            added++
        }

        return ImportOutcome.Ok(merged, added, skipped, invalid)
    }

    /** GitHub 仓库全名：`owner/repo`，两段都不能为空，也不允许路径穿越 */
    fun isValidFullName(name: String): Boolean {
        val parts = name.split('/')
        if (parts.size != 2) return false
        val (owner, repo) = parts
        if (owner.isBlank() || repo.isBlank()) return false
        return parts.none { it == "." || it == ".." || it.contains('\\') }
    }

    private fun isoOf(ms: Long): String =
        java.time.Instant.ofEpochMilli(ms).toString()
}
