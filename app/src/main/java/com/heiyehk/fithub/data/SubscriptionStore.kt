package com.heiyehk.fithub.data

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * 关注列表的落盘与内存状态。
 *
 * 关注是纯本地行为，服务器上没有对应记录，因此这里就是唯一真相。
 *
 * 元信息以快照存在本地，换来的是订阅页离线可读、少发请求；代价是快照会旧，
 * 所以 [Subscription.metaFetchedAt] 一并保存，由 UI 决定要不要提示。
 */
object SubscriptionStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun store(context: Context) = LocalStore(context.filesDir, json)

    /** 按加入时间倒序 —— 最近关注的排前面 */
    fun all(context: Context): List<Subscription> =
        store(context)
            .read(LocalStore.KEY_SUBSCRIPTIONS, emptyList<Subscription>())
            .sortedByDescending { it.addedAt }

    fun isFollowing(context: Context, fullName: String): Boolean =
        all(context).any { it.fullName.equals(fullName, ignoreCase = true) }

    fun get(context: Context, fullName: String): Subscription? =
        all(context).firstOrNull { it.fullName.equals(fullName, ignoreCase = true) }

    /**
     * 关注。已存在时不覆盖 —— 重复关注应保持原有 [Subscription.addedAt]，
     * 否则用户会看到「刚刚关注」而其实早就关注了。
     */
    fun follow(context: Context, sub: Subscription) {
        val current = all(context)
        if (current.any { it.fullName.equals(sub.fullName, ignoreCase = true) }) return
        val next = current + sub.copy(fullName = sub.fullName.trim())
        save(context, next)
        cache = next
    }

    fun unfollow(context: Context, fullName: String) {
        val current = all(context)
        val next = current.filterNot { it.fullName.equals(fullName, ignoreCase = true) }
        if (next.size == current.size) return
        save(context, next)
        cache = next
    }

    /**
     * 整份替换，用于导入。
     *
     * 覆盖而不是合并在这里是刻意的：合并的判定已经在
     * [SubscriptionTransfer.import] 里做完了，到这里传入的已经是最终列表。
     */
    fun replaceAll(context: Context, subs: List<Subscription>) {
        save(context, subs.sortedByDescending { it.addedAt })
        cache = all(context)
    }

    /**
     * 用最新拉到的仓库元信息更新快照，保留 [Subscription.addedAt] 与 [Subscription.note]。
     *
     * 拉取失败时不要调用这个方法 —— 那会让旧数据被空值覆盖。
     */
    fun refreshMeta(context: Context, fullName: String, block: (Subscription) -> Subscription) {
        val current = all(context)
        val idx = current.indexOfFirst { it.fullName.equals(fullName, ignoreCase = true) }
        if (idx < 0) return
        val next = current.toMutableList()
        next[idx] = block(current[idx]).copy(metaFetchedAt = System.currentTimeMillis())
        save(context, next)
        cache = next
    }

    private fun save(context: Context, list: List<Subscription>) =
        store(context).write(LocalStore.KEY_SUBSCRIPTIONS, list)

    /**
     * 已读的关注列表，供同步查询用。
     *
     * [load] 之后有效；关注数变化时用 [follow] / [unfollow] 顺带刷新。
     */
    private var cache: List<Subscription> = emptyList()
        private set

    fun load(context: Context) {
        cache = all(context)
    }

    fun snapshot(): List<Subscription> = cache

    fun followingCount(): Int = cache.size
}

/**
 * 从 [Repo] 构造一条关注记录。
 *
 * 关注时拿到的 [Repo] 可能来自列表接口（没有 release），所以 [latestTag] 允许为空 ——
 * 那是「还不知道」，不是「没有 release」。
 */
fun Repo.toSubscription(addedAt: Long = System.currentTimeMillis()): Subscription = Subscription(
    fullName = id,
    addedAt = addedAt,
    name = name,
    owner = owner,
    desc = desc,
    stars = stars,
    lang = lang,
    topics = topics,
    lastPush = date,
    latestTag = version.takeIf { it != "—" }.orEmpty(),
    latestReleaseDate = if (hasRealRelease) date else "",
    // 关注那一刻手上就是刚拉到的元信息
    metaFetchedAt = addedAt,
)
