package com.heiyehk.fithub.data

import android.content.Context
import com.heiyehk.fithub.R
import com.heiyehk.fithub.data.remote.WebDavClient
import com.heiyehk.fithub.data.remote.WebDavResult

/**
 * 订阅的 WebDAV 同步。
 *
 * **不并发**：手动触发，一台设备一次只做一次同步。
 *
 * **失败绝不清本地**。同步失败的正确表现是「本地保持原样并告知用户」，
 * 而不是把用户本机的关注项清空去迁就云端 —— 那是数据丢失。
 */
object SubscriptionSync {

    /**
     * 跑一次同步。
     *
     * 冲突判定：比较本地和远端的 [SubscriptionFile.exportedAt]，
     * **新的覆盖旧的**，相等时以本地为准（本地刚改过就以本地为准更符合直觉）。
     * 数据量只有几百字节，不需要增量合并，整份覆盖即可。
     */
    suspend fun run(
        context: Context,
        client: WebDavClient,
        local: List<Subscription>,
        now: Long = System.currentTimeMillis(),
    ): SyncOutcome {
        // 导出一次，两处共用：payload 用于上传，exportedAt 用于判新旧
        val payload = SubscriptionTransfer.export(local, now)
        val localAt = SubscriptionTransfer.exportedAtOf(payload).orEmpty()

        return when (val remote = client.get(REMOTE_FILE)) {
            is WebDavResult.NotConfigured -> SyncOutcome.NotConfigured(remote.reason)

            is WebDavResult.Failed -> SyncOutcome.Failed(remote.reason)

            // 云端还没有这个文件 —— 第一次同步，直接推上去
            WebDavResult.NotFound -> push(client, payload, local.size)

            is WebDavResult.Ok -> {
                val remoteAt = SubscriptionTransfer.exportedAtOf(remote.value).orEmpty()
                // 远端时间拿不到时按「本地较新」处理：宁可多传一次，
                // 也不能因为读不懂一个字段就把远端整个覆盖掉
                if (remoteAt.isNotBlank() && remoteAt > localAt) {
                    pullInto(context, remote.value, local, now)
                } else {
                    push(client, payload, local.size)
                }
            }
        }
    }

    private suspend fun pullInto(
        context: Context,
        remoteJson: String,
        local: List<Subscription>,
        now: Long,
    ): SyncOutcome = when (val parsed = SubscriptionTransfer.import(remoteJson, local, now)) {
        is ImportOutcome.Ok -> {
            SubscriptionStore.replaceAll(context, parsed.merged)
            SyncOutcome.Pulled(parsed.added, parsed.skipped, parsed.invalid)
        }

        is ImportOutcome.UnsupportedSchema -> SyncOutcome.Failed(
            Explain(
                R.string.webdav_schema_unsupported,
                listOf(parsed.found, parsed.supported),
            ),
        )

        is ImportOutcome.Malformed -> SyncOutcome.Failed(unreadable(parsed.reason))

        ImportOutcome.Empty -> SyncOutcome.Failed(Explain.of(R.string.webdav_cloud_empty))
    }

    private suspend fun push(client: WebDavClient, payload: String, count: Int): SyncOutcome =
        when (val put = client.put(REMOTE_FILE, payload)) {
            is WebDavResult.Ok -> SyncOutcome.Pushed(count)
            is WebDavResult.NotConfigured -> SyncOutcome.NotConfigured(put.reason)
            // put.reason 已经是 Explain，不是纯字符串，所以不能再套一层格式化模板：
            // 直接透传，由 UI 决定怎么排版
            is WebDavResult.Failed -> SyncOutcome.Failed(put.reason)
            WebDavResult.NotFound -> SyncOutcome.Failed(Explain.of(R.string.webdav_upload_no_dir))
        }

    /**
     * 把 [ImportOutcome.Malformed] 的原因套进「已保留本地数据」那句话。
     *
     * `explainText()` 只把 [ResArg] 解成文本、其余原样传给 `String.format`，所以
     * 一个模板够用：带第三方原文的那一支传 String，纯我们自己的那一支传 ResArg。
     */
    private fun unreadable(reason: Explain): Explain = Explain(
        R.string.webdav_cloud_unreadable,
        listOf(
            if (reason.args.isEmpty()) ResArg(reason.res)
            else reason.args.first(),
        ),
    )

    /** 同步到云端，不看远端版本。用于「强制覆盖」 */
    suspend fun forcePush(
        client: WebDavClient,
        local: List<Subscription>,
        now: Long = System.currentTimeMillis(),
    ): SyncOutcome = push(client, SubscriptionTransfer.export(local, now), local.size)

    /** 云端文件名。放在配置路径的父目录下，与其它 App 的文件不会撞名 */
    const val REMOTE_FILE = "subscriptions.json"
}

sealed interface SyncOutcome {
    /** 本地较新，已推上去 */
    data class Pushed(val count: Int) : SyncOutcome

    /** 云端较新，已拉下来并合并。参数与 ImportOutcome.Ok 同义 */
    data class Pulled(val added: Int, val skipped: Int, val invalid: Int) : SyncOutcome

    /**
     * [reason] 会显示在同步页的「上次结果」里，所以是可本地化的 [Explain]。
     * UI 侧用 `explainText()` 渲染。
     */
    data class Failed(val reason: Explain) : SyncOutcome

    data class NotConfigured(val reason: Explain) : SyncOutcome
}
