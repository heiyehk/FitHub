package com.heiyehk.fithub.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 首页板块。
 *
 * [id] 稳定不变，用于排序、启停与缓存 key —— 改标题或 topic 都不要动它，
 * 否则用户的配置会对不上已有缓存。
 */
@Serializable
data class HomeSection(
    val id: String,
    val title: String,
    val subtitle: String = "",
    /** [Kind.Topic] 用这个构造搜索查询 */
    val topic: String = "",
    /** 关闭后配置仍在，只是不显示 */
    val enabled: Boolean = true,
    val builtin: Boolean = false,
) {
    @Serializable
    enum class Kind {
        /** 按 star 排序 */
        Stars,
        /** 按最近 push 排序 */
        Updated,
        /** 按确切全名拉取 */
        Featured,
        /** 按 topic 搜索 —— 自定义板块走这条 */
        Topic,
    }

    val kind: Kind
        get() = when {
            topic.isNotBlank() -> Kind.Topic
            id == ID_STARS -> Kind.Stars
            id == ID_UPDATED -> Kind.Updated
            else -> Kind.Featured
        }

    /** 该板块的搜索查询。非 topic 板块返回 null */
    val query: String?
        get() = if (kind == Kind.Topic) "topic:$topic stars:>50 archived:false" else null

    /**
     * 板块总上限等常量在 companion。
     *
     * 这里**没有** cacheKey()：缓存 key 由 [com.heiyehk.fithub.data.remote.GitHubApi]
     * 在真正发请求的那一处拼（`search-<query>` / `discover-<sort>-<query>`），
     * 在这里另拼一份必然会和它对不上 —— 而对不上的后果是「预填时找得到、失效时
     * 清不掉」。要读缓存用 `GitHubApi.peekSearch`。
     */

    companion object {
        const val ID_STARS = "builtin-stars"
        const val ID_UPDATED = "builtin-updated"
        const val ID_FEATURED = "builtin-featured"
        const val ID_AGENT = "builtin-agent"

        /**
         * 自定义板块上限。
         *
         * **每个板块 = 1 次 search 请求**，未登录配额只有 60/h。
         * 不设上限的话，用户加 30 个板块就在一次打开首页时打光配额。
         */
        const val MAX_CUSTOM = 6

        /** 板块总数上限 */
        const val MAX_TOTAL = 10

        /** 默认的四个板块。builtin = true 表示不可编辑，只能启停 */
        fun defaults(): List<HomeSection> = listOf(
            HomeSection(
                id = ID_STARS,
                title = "热门",
                subtitle = "GitHub 没有 Trending 接口 · 这里按 star 排序",
                builtin = true,
            ),
            HomeSection(
                id = ID_UPDATED,
                title = "最近更新",
                subtitle = "按最近 push 时间排序 · 只含有 release 的仓库",
                builtin = true,
            ),
            HomeSection(
                id = ID_FEATURED,
                title = "精选",
                subtitle = "按确切全名从 GitHub 拉的实时元信息",
                builtin = true,
            ),
            HomeSection(
                id = ID_AGENT,
                title = "Agent 工具",
                subtitle = "按 topic 搜索 GitHub 上的 AI Agent 与 CLI 项目",
                topic = "ai-agent",
                builtin = true,
            ),
        )

        /**
         * 新建一个自定义 topic 板块。
         *
         * id 用 topic 派生而不是随机串：同一 topic 重复添加时能被认出来，
         * 而不会在配置里堆出两条一样的。
         */
        fun customFrom(topic: String, title: String = ""): HomeSection {
            val t = topic.trim()
            return HomeSection(
                id = "topic-${t.lowercase()}",
                title = title.ifBlank { t },
                subtitle = "按 topic「$t」搜索",
                topic = t,
            )
        }
    }
}

/**
 * 校正配置，保证任何来源都满足约束。
 *
 * 宁可在这里把不合规的裁掉，也不要让越界的配置进到 UI 层 ——
 * 那样每个调用点都要各自判断一遍，漏一处就是配额被打光的 bug。
 *
 * 抽成顶层函数是为了让 JVM 测试能直接调它：[HomeSectionStore] 的构造需要
 * Context 落盘，测不了。把规则抄进测试再测那份副本是自欺欺人 ——
 * 真实现改了，测试照样绿。
 */
fun sanitizeSections(input: List<HomeSection>): List<HomeSection> {
    val seen = mutableSetOf<String>()
    val deduped = input.filter { seen.add(it.id.lowercase()) }
    val customs = deduped.filterNot { it.builtin }.take(HomeSection.MAX_CUSTOM)
    val builtins = deduped.filter { it.builtin }
    // 内置板块固定在前：挪到用户板块后面会让默认布局变形
    return (builtins + customs).take(HomeSection.MAX_TOTAL)
}

/**
 * 板块配置的落盘。
 *
 * 只存用户可改的部分，位置决定顺序。数据各归各的板块（各自独立缓存），
 * 配置不参与缓存失效。
 */
class HomeSectionStore(context: Context) {

    private val store = LocalStore(
        context.applicationContext.filesDir,
        Json { ignoreUnknownKeys = true; encodeDefaults = true },
    )

    fun load(): List<HomeSection> =
        sanitizeSections(store.read(LocalStore.KEY_HOME_SECTIONS, HomeSection.defaults()))

    fun save(sections: List<HomeSection>) =
        store.write(LocalStore.KEY_HOME_SECTIONS, sanitizeSections(sections))

    companion object {
        /** 还能再加几个自定义板块。UI 用它禁用「添加」按钮 */
        fun remainingCustom(sections: List<HomeSection>): Int =
            (HomeSection.MAX_CUSTOM - sections.count { !it.builtin }).coerceAtLeast(0)
    }
}
