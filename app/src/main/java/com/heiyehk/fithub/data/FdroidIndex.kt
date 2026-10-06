package com.heiyehk.fithub.data

import android.content.Context
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 随包分发的「包名 → GitHub 仓库」映射表（取自 F-Droid 官方索引）。
 *
 * **这是包名反查的第一条路，也是唯一不消耗 GitHub 配额的一条。**
 *
 * 产物 `assets/fdroid-repo-index.json` 由 `tools/build-fdroid-index.mjs` 生成
 * （实测 2026-10：源索引 54.6 MB / 4549 个包，筛出 3698 条 GitHub 映射、186 KB）。
 *
 * ### 为什么需要它
 *
 * 反查的另一条路是「拿包名去 GitHub 搜 → 取候选 → 去候选仓库的 build.gradle 里验
 * `applicationId`」（Obtainium 的做法，那才是精确度真正的来源）。那条路准，但贵：
 * 8 个候选 × 2 个常见路径 = 16 次请求，而未登录只有 60 次/小时 —— 一次反查吃掉 27%。
 *
 * 先查这张表，F-Droid 上的应用直接命中，不花任何配额；剩下查不到的才走那条路。
 *
 * ### 它的边界
 *
 * - **只覆盖 F-Droid 收录的应用。** 而 FitHub 的存在意义恰恰有一半是「只发 GitHub
 *   Release、没上 F-Droid 的应用」，所以查不到是**常态**，不是异常。
 * - **会过期。** 上架 / 搬家不会通知我们。重新跑一次生成脚本更新即可。
 * - **只收 GitHub。** 索引里 GitLab / Codeberg 的条目在生成时就剔掉了 ——
 *   FitHub 只跟 GitHub 打交道，留着会给出「定位到了但不是 GitHub 仓库」的半截答案。
 *
 * 因此这张表**不能单独当作结论**：命中要按「用户确认的候选」走（现有
 * `LinkEngine` 那套确认流程），没命中也不要报「查不到」以外的结论。
 */
object FdroidIndex {

    private const val ASSET = "fdroid-repo-index.json"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 解析一次就留着。
     *
     * 不是因为它大（186 KB），而是因为它在**每一次包名反查**时都会被读到，
     * 而反查是用户点一下就该出结果的动作。`@Volatile` + 双重检查：解析要跑
     * 几十毫秒，不该在主线程上重复发生。
     */
    @Volatile
    private var cached: Map<String, String>? = null

    /**
     * 包名 → `owner/repo`，查不到返回 null。
     *
     * 大小写不敏感：包名理论上大小写敏感，但 F-Droid 的索引和
     * `PackageManager` 给出来的写法在实践中并不总是一致，按小写查更稳。
     */
    fun lookup(context: Context, packageName: String): String? {
        if (packageName.isBlank()) return null
        val map = cached ?: synchronized(this) {
            cached ?: load(context).also { cached = it }
        }
        return map[packageName.lowercase()]
    }

    /** 条目数。设置页/调试用，不参与业务判断。 */
    fun size(context: Context): Int {
        val map = cached ?: synchronized(this) {
            cached ?: load(context).also { cached = it }
        }
        return map.size
    }

    /**
     * 读 assets。
     *
     * 读不出来（文件没打进包、被裁剪、JSON 损坏）就退化成**空表**，于是所有包名
     * 都退回名称搜索那条路 —— 功能降级但不会崩。反查本来就有一条不依赖这张表的
     * 退路，为它抛异常不值得。
     */
    private fun load(context: Context): Map<String, String> = runCatching {
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), text)
            .mapKeys { it.key.lowercase() }
    }.getOrElse { emptyMap() }
}