package com.heiyehk.fithub.data

import com.heiyehk.fithub.data.remote.DiscoverSort
import java.time.LocalDate

/**
 * 订阅页的排行榜 tab。
 *
 * 每个 board = 1 次 search 请求，所以 tab 数量本身就是配额成本：
 * 未登录 search 只有 10 次/分钟，滑一遍打满 6 个就吃掉 60% 的预算。
 * 剩下的品类不进 tab，走首页的自定义板块（见 [HomeSectionStore.customFrom]）。
 *
 * ## 为什么不用 `has:release` 过滤
 *
 * GitHub 的仓库搜索**没有** `has:release` 限定符。实测（2026-10-07）：
 * `topic:android stars:>50 archived:false` 与再加 `has:release` 的结果数完全相同
 * （12366 : 12366，榜首同一个仓库），也就是这个限定符被静默忽略、不过滤任何东西。
 * 加引号 `"has:release"` 会变成全文匹配，结果是 0 —— 说明它是被当成未知限定符丢掉的。
 *
 * 所以**「只展示有产物的仓库」在一次 search 里做不到**：真要筛就得每个仓库再打一次
 * `/releases`。因此排行行**不显示产物状态**，改用 star + 最近 push —— 对 AI 工具类
 * 仓库而言订阅的价值是追踪更新，本来也不在下载。
 *
 * ## 为什么宽池要加 created 时间窗
 *
 * `topic:android` 有 12366 个带 star 的仓库，按 star 取前 20 三年不变 —— 那不是榜，
 * 是一堵墙。加 `created:>=` 之后榜才会动，对刚入局的人也才有意义。
 * 窄池（如 [Harness] 只有 133 个）取前 20 是前 15%，本身就是好榜，不加窗口。
 */
enum class RankBoard(
    /** 稳定 id，用于缓存键与状态 map 的 key */
    val id: String,
    val sort: DiscoverSort,
    /** topic 限定符。**不允许为空** —— 空串等于全站榜，见下面「上升」那段 */
    private val topic: String,
    /**
     * `created:>=` 时间窗天数，0 = 不加。
     *
     * 加了之后查询串每天不同，缓存键也每天不同 —— 这是对的（数据本来就该每天更新），
     * 但意味着跨零点后同一次滑动会重新联网，别当成缓存穿透的 bug。
     */
    private val freshDays: Int,
) {
    /**
     * 不带 topic 的「上升」榜已经删掉了。
     *
     * 原来的 `Rising` 查询是 `created:>=<30天前> stars:>100`，**没有 topic 限定** ——
     * 等于全站搜索按 star 倒序，实测 1501 个仓库里前两名是「高性价比人生指南」
     * 和 Mac 的 Photoshop 替代。30 天能冲上四万星的是被社交媒体推上热搜的，不是
     * 被工具链选出来的，而榜单是按名次被评判的。
     *
     * 给它加 topic 限定同样不行，实测三组：
     * - 多 topic 取并集：`topic:a OR topic:b` → **HTTP 422**；`(topic:a OR topic:b)` → **静默 0**
     * - `topic:claude-code` + 90 天窗口 → 624 个，但前 6 名全不是 agent 工具（topic 被污染）
     * - `topic:coding-agent` + 30 天窗口 → 只剩 **20** 条，等于没筛
     *
     * 结论：不是配置没调好，是 **GitHub 搜索表达不出「上升」**。留着就是名不副实。
     * 真正的增速榜需要本地存 star 快照做差值，那只对自己订阅过的仓库成立。
     */

    /** 窄池 579 —— 专门挑 agent skill 仓库，不是泛 AI */
    Skills(id = "skills", sort = DiscoverSort.Stars, topic = "claude-skills", freshDays = 0),

    /** 窄池 133 —— 最窄的一个，前 20 就是前 15%，是最像榜的一个 */
    Harness(id = "harness", sort = DiscoverSort.Stars, topic = "agent-harness", freshDays = 0),

    /** 宽池 3979 —— 必须加时间窗，否则榜永远是那批老仓库 */
    Agents(id = "agents", sort = DiscoverSort.Stars, topic = "ai-agents", freshDays = 90),

    /** 宽池 4069 —— 同上 */
    Mcp(id = "mcp", sort = DiscoverSort.Stars, topic = "mcp", freshDays = 90),
    ;

    /**
     * 拼出这个榜的查询串。
     *
     * 日期由参数传入而不是内部取 [LocalDate.now]：查询串要进缓存键，
     * 「今天是哪天」必须是调用方的决定，否则单测里没法固定输入。
     * 调用方（UI 层）传本地日期，避免跨时区把窗口算偏一天。
     */
    fun queryOn(today: LocalDate): String = buildString {
        append("topic:").append(topic).append(' ')
        if (freshDays > 0) {
            append("created:>=").append(today.minusDays(freshDays.toLong())).append(' ')
        }
        append("stars:>").append(MIN_STARS).append(" archived:false")
    }

    /** 今天该用的查询串 */
    fun query(): String = queryOn(LocalDate.now())

    companion object {
        /**
         * 取前几条。
         *
         * 一次 search 调用返回 20 条和 50 条一样只花 **1 次请求**，所以这个值
         * **不影响配额**，纯粹是屏幕上放多少行。原先它是每个 board 的构造参数，
         * 但四个榜写的都是 30 —— 一个永远等于自己默认值的旋钮不是配置，是摆设，
         * 所以收回成常量。真要调就改这一处。
         */
        const val LIMIT = 30

        /**
         * star 下限。
         *
         * 太高会把刚起步的项目挡在门外，太低会灌进一堆玩具仓库。50 是实测下来
         * 各 topic 都还有货的档位（最窄的 agent-harness 也有 133 个）。
         *
         * 同 [LIMIT]：四个榜原本都传 50，没有一个用别的值。
         */
        const val MIN_STARS = 50

        /**
         * GitHub 仓库搜索**只认这四个** sort 值，多传别的会被静默忽略并回退成
         * best match（HTTP 200，不报错）。这里逐个断言是为了让「加个新枚举值」
         * 变成编译期/测试期可见的失败，而不是线上一个看起来正常的错榜。
         */
        val LEGAL_SORTS = setOf("stars", "forks", "help-wanted-issues", "updated")
    }
}
