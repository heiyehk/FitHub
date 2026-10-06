package com.heiyehk.fithub.data.remote

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 一次请求的结果：有数据，或带可展示原因的错误
 *
 * [Ok.fromCache] 与 [Ok.ageMs] 用来区分「刚从网络拿到」和「读的是本地缓存」。
 * 缺了这两个字段，离线时 UI 无法告诉用户数据是什么时候的，
 * 缓存就会被当成实时数据展示。
 */
sealed interface ApiResult<out T> {
    data class Ok<T>(
        val value: T,
        val remaining: Int,
        val fromCache: Boolean = false,
        val ageMs: Long = 0L,
    ) : ApiResult<T>

    data class Err(val message: String, val kind: Kind = Kind.Network) : ApiResult<Nothing> {
        enum class Kind { Network, RateLimited, NotFound, Parse, Empty }
    }
}

/**
 * GitHub REST 客户端。
 *
 * 用 Ktor CIO 而不是 OkHttp：纯 Kotlin 实现，R8 后体积更小。
 *
 * 登录是可选项：[authProvider] 返回 null 时请求不带用户身份，全部功能照常可用。
 * 这是产品定位决定的 —— 登录只是把配额从 60/h 提到 5000/h，不该成为任何
 * 浏览或下载功能的前置条件。
 *
 * @param cacheDir 缓存目录。**必须是 filesDir 下面的**，不能是 cacheDir ——
 *   cacheDir 系统随时可以清空（存储紧张时不需要用户确认），而未登录只有
 *   60 次/小时配额，清空一次的代价是用户要重新把每个仓库、README、更新日志
 *   都刷回来。详细理由见调用点。
 */
class GitHubApi(cacheDir: File, private val authProvider: () -> String? = { null }) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val client = HttpClient(CIO) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 20_000
        }
    }

    /**
     * 只用来跑「后台刷新缓存」。挂在 client 上而不是某个界面：缓存是 App 级共享的，
     * 界面的生命周期不该决定缓存什么时候更新。用 [SupervisorJob] 是因为一次刷新
     * 失败不该连累其它还没跑的刷新。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 正在后台刷新的缓存 key。
     *
     * 去重不是为了省流量，是为了省**配额** —— 未登录 60 次/小时，而且用户来回切
     * 仓库会反复命中同一条过期缓存。并发集合而不是锁：这里只在
     * 「要不要发起」这一个判断上用到，串行化的代价大于收益。
     */
    private val refreshing = ConcurrentHashMap.newKeySet<String>()

    fun close() {
        scope.cancel()
        client.close()
    }

    /** 服务端返回的配额，UI 直接读 */
    @Volatile
    var lastRemaining: Int = 60
        private set

    @Volatile
    var lastLimit: Int = 60
        private set

    /**
     * 这个 token **实际**被授予的 scope，来自响应头 `X-OAuth-Scopes`。
     *
     * 不能拿「我们申请了什么」当答案：GitHub 允许用户在授权页改 scope
     * （官方文档原话是 users can edit their scopes, effectively granting your
     * application less access than you originally requested），所以申请 `read:user`
     * 并不保证拿到 `read:user`。要显示「当前权限」，只能读这个头。
     *
     * null = 最近的响应没带这个头（未登录的请求就没有）。
     */
    @Volatile
    var grantedScopes: String? = null
        private set

    val cache = RepoCache(cacheDir, json)

    /**
     * 丢掉某几个条目的缓存，让下一次请求真的走网络。
     *
     * 手动刷新前调用，否则「刷新」只是把同一份未过期缓存再读一遍。
     * 用 [RepoCache.remove] 而不是 [clearAll]，避免连带作废 README 等其它缓存。
     */
    fun invalidate(vararg cacheKeys: String) {
        cacheKeys.forEach { cache.remove(CACHE_VERSION + it) }
    }

    /**
     * **stale-while-revalidate**：盘上有数据就立刻返回，同时后台去刷新。
     *
     * 之前是「TTL 内用缓存，TTL 过期就纯等网络，只有网络**抛异常**才回退旧缓存」。
     * 那个行为在 60 次/小时的配额下很糟：
     * - 缓存一过期，每次打开 App 都要转圈等网络，慢得像没有缓存
     * - 配额撞顶返回 403/429 时走的是「非 2xx」分支，**根本不会碰缓存**，
     *   于是明明盘上有数据，界面却是一片「配额用完了」—— 而那些数据是几分钟前
     *   自己刚拉下来的
     *
     * 现在只要磁盘上有就先用上，并把年龄如实报给 UI（界面已经会标「N 分钟前」）。
     * 后台刷新成功会覆盖缓存，下次进入就是新的；用户手动刷新仍走
     * [invalidate] 强制穿透。
     */
    private suspend inline fun <reified T> get(
        path: String,
        cacheKey: String,
        params: Map<String, String> = emptyMap(),
        maxAgeMs: Long = RepoCache.MAX_AGE,
        cacheable: Boolean = true,
        /**
         * 跳过缓存读取，直接联网取；取到后**照常写回缓存**。
         *
         * 只给一种场景用：用户明确表达了「现在给我真相」——目前只有「检查更新」。
         *
         * 浏览类请求用缓存是对的（快、省配额），但检查更新不是：它问的是
         * 「此刻远端有没有比我新的版本」，而 releases 在 6 小时内是命中缓存的。
         * 于是「1 小时前查过一次，5 分钟前发了新版，再查」会拿到**上一次那份**，
         * 回答「已是最新」—— 一个用户主动发起的检查，却给了他一个确定的错误答案。
         *
         * 请求失败时仍然回退旧缓存：给不出新答案时如实说拿不到，好过说「没有新版」。
         */
        forceRefresh: Boolean = false,
    ): ApiResult<T> {
        // key 前缀带 CACHE_VERSION，字段映射一改旧缓存整体作废，不会一直读到脏数据
        val key = CACHE_VERSION + cacheKey

        if (cacheable && !forceRefresh) {
            cache.readIgnoringAge<T>(key)?.let { (value, age) ->
                if (age <= maxAgeMs) {
                    // 新鲜数据直接用，不发请求
                    return ApiResult.Ok(value, lastRemaining, fromCache = true, ageMs = age)
                }
                // 过期数据也先用：它比一个转圈或者一句「配额用完了」有用得多。
                // 后台这次刷新可能失败（配额/断网），失败就保留这份旧的。
                Log.i(TAG, "缓存已过期，先用旧的再后台刷新: $key age=${age}ms")
                // 类型实参必须**显式**写：reified 的 T 在 scope.launch 的 lambda 里
                // 已经被擦除，不写就推不出来，报 "Cannot infer type for T"
                //
                // **同一份数据同时只刷一次**：未登录只有 60 次/小时配额，而用户来回
                // 切仓库、切 tab 会反复命中同一条过期缓存。没有这道去重，一次会话里
                // 几十个仓库就能把配额烧光 —— 而且全花在刷新**已经看得到**的内容上。
                if (refreshing.add(key)) {
                    scope.launch {
                        try {
                            refreshInBackground<T>(path, key, params)
                        } finally {
                            refreshing.remove(key)
                        }
                    }
                }
                return ApiResult.Ok(value, lastRemaining, fromCache = true, ageMs = age)
            }
        }

        val fresh: ApiResult<T> = fetch<T>(path, key, params, cacheable)
        // 网络这一趟白跑了（配额/404/解析失败）但盘上有旧数据：仍然交出旧数据，
        // 并把 fromCache 标出来，而不是让 UI 显示一个它本可以避免的错误
        if (cacheable && fresh is ApiResult.Err) {
            cache.readIgnoringAge<T>(key)?.let { (value, age) ->
                Log.w(TAG, "请求失败但有缓存兜底: $key age=${age}ms err=${fresh.message}")
                return ApiResult.Ok(value, lastRemaining, fromCache = true, ageMs = age)
            }
        }
        return fresh
    }

    /**
     * 后台刷新：只写缓存，不改任何状态。
     *
     * 故意**不**回调 UI：这类静默刷新拿到的数据，用户当下看到的还是那份旧的
     * （界面上标着年龄），下次进页面或手动刷新才换成新的。为此引入一套事件总线
     * 换来的只是一次「界面自己跳了一下」，不值得。失败的静默处理也是刻意的 ——
     * 用户没在等这个结果，弹一个错只会莫名其妙。
     */
    private suspend inline fun <reified T> refreshInBackground(
        path: String,
        key: String,
        params: Map<String, String>,
    ) {
        // 显式写死返回类型：放进 runCatching 的话 T 会在 Result 的类型参数里丢掉，
        // Kotlin 推不出来，只能报 "Cannot infer type for type parameter 'T'"。
        // fetch 自己已经吃掉了网络异常，这里的 catch 只是兜住意外。
        val result: ApiResult<T> = try {
            fetch(path, key, params, cacheable = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "后台刷新失败: $key", e)
            return
        }
        when (result) {
            is ApiResult.Ok -> Log.i(TAG, "后台刷新完成: $key")
            is ApiResult.Err -> Log.w(TAG, "后台刷新未成功，保留旧缓存: $key ${result.message}")
        }
    }

    /** 真正发一次请求并落缓存。[get] 和 [refreshInBackground] 共用。 */
    private suspend inline fun <reified T> fetch(
        path: String,
        key: String,
        params: Map<String, String>,
        cacheable: Boolean,
    ): ApiResult<T> = try {
        val response = client.get(path) {
            accept(ContentType.Application.Json)
            header(HttpHeaders.UserAgent, "FitHub")
            authProvider()?.let { header(HttpHeaders.Authorization, it) }
            params.forEach { (k, v) -> parameter(k, v) }
        }
        response.headers["X-RateLimit-Remaining"]?.toIntOrNull()?.let { lastRemaining = it }
        response.headers["X-RateLimit-Limit"]?.toIntOrNull()?.let { lastLimit = it }
        // 带 token 的响应才有这个头；没带就当没拿到，不覆盖上一次的值
        response.headers["X-OAuth-Scopes"]?.let { grantedScopes = it.ifBlank { null } }

        when (response.status.value) {
            in 200..299 -> {
                val value = json.decodeFromString<T>(response.body<String>())
                if (cacheable) cache.write(key, value)
                ApiResult.Ok(value, lastRemaining)
            }

            403, 429 -> ApiResult.Err(
                "GitHub API 配额用完了，约一小时后恢复。登录可提到 5000/小时。",
                ApiResult.Err.Kind.RateLimited,
            )

            404 -> ApiResult.Err("GitHub 上没有这个内容", ApiResult.Err.Kind.NotFound)

            else -> ApiResult.Err("GitHub 返回 ${response.status.value}", ApiResult.Err.Kind.Parse)
        }
    } catch (e: Exception) {
        Log.w(TAG, "请求失败 $path", e)
        ApiResult.Err(e.message ?: "网络不可用")
    }

    /**
     * **只**读缓存，绝不发请求。返回 null = 盘上没有这一条。
     *
     * 存在的意义是区分「先看看有没有」和「去拿」：[get] 在缓存过期时会去发请求，
     * 而有些场景要的是「有就用，没有就算了」—— 比如冷启动先把首页的 topic 板块
     * 填上：盘上有就显示，没有就还是那个「点我去查」的按钮，**不能**因为顺手
     * 填个缓存就替用户花掉一次配额。
     *
     * 忽略 TTL：过期数据在这里也照样返回（调用方拿 [Pair.second] 的年龄自己决定要不要
     * 标「N 分钟前」）。这是缓存能兜住配额耗尽的前提。
     */
    inline fun <reified T> peek(cacheKey: String): Pair<T, Long>? =
        cache.readIgnoringAge<T>(CACHE_VERSION + cacheKey)

    private val base = "https://api.github.com"

    suspend fun rateLimit(): ApiResult<RateLimitDto> = try {
        val response = client.get("$base/rate_limit") {
            accept(ContentType.Application.Json)
            header(HttpHeaders.UserAgent, "FitHub")
            // rate_limit 也要带 token：不带的话查到的是未登录的 60/h，
            // 登录后 UI 会把配额显示成 60，数字就错了
            authProvider()?.let { header(HttpHeaders.Authorization, it) }
        }
        val dto = response.body<RateLimitDto>()
        lastRemaining = dto.resources.core.remaining
        lastLimit = dto.resources.core.limit
        ApiResult.Ok(dto, lastRemaining)
    } catch (e: Exception) {
        ApiResult.Err(e.message ?: "取配额失败")
    }

    /**
     * 发现页数据源。GitHub 没有 Trending API，用 search 的 sort 代替，UI 上标注排序依据。
     *
     * TTL 1 小时：发现页是最常打开的页面，单次也只是 1 次 search 请求，
     * 缓存久一点对配额友好，而数据本身变化不快。
     */
    suspend fun discover(sort: DiscoverSort, query: String): ApiResult<SearchResponse> =
        get(
            "$base/search/repositories",
            discoverCacheKey(sort, query),
            mapOf(
                "q" to query,
                "sort" to sort.wire,
                "order" to "desc",
                "per_page" to "30",
            ),
            maxAgeMs = HOME_TTL,
        )

    /**
     * 丢弃一个仓库的详情缓存。
     *
     * 详情由元信息和 releases 两份组成，两个 key 都要作废，
     * 否则刷新后 releases 仍读旧的，看起来像没刷新。
     */
    fun invalidateRepo(fullName: String) {
        invalidate("repo-$fullName", "releases-$fullName-3")
    }

    /** 丢弃发现页两段的缓存。key 在这里拼一次，避免两处写错 */
    fun invalidateDiscover(query: String) {
        invalidate(*DiscoverSort.entries.map { discoverCacheKey(it, query) }.toTypedArray())
    }

    private fun discoverCacheKey(sort: DiscoverSort, query: String) = "discover-${sort.wire}-$query"

    suspend fun repo(fullName: String): ApiResult<RepoDto> =
        get("$base/repos/$fullName", "repo-$fullName")

    suspend fun releases(
        fullName: String,
        perPage: Int = 3,
        /** 「检查更新」传 true：穿缓存去问一次远端此刻的版本 */
        forceRefresh: Boolean = false,
    ): ApiResult<List<ReleaseDto>> =
        get(
            "$base/repos/$fullName/releases",
            "releases-$fullName-$perPage",
            mapOf("per_page" to perPage.toString()),
            forceRefresh = forceRefresh,
        )

    /** 统一搜索：GitHub 搜索语法直接透传，App 不做二次解析 */
    suspend fun search(query: String): ApiResult<SearchResponse> =
        get(
            "$base/search/repositories",
            searchCacheKey(query),
            mapOf("q" to query, "per_page" to "30"),
        )

    /** [search] 的缓存 key。[peekSearch] 复用它，两处各拼一次必然对不上 */
    private fun searchCacheKey(query: String) = "search-$query"

    /**
     * 只读缓存里的搜索结果，不发请求。
     *
     * 首页的 topic 板块靠它冷启动预填：之前这些板块的结果只活在一个内存 map 里，
     * 进程一回收就归零，于是用户每次开 App 看到的都是一排「点我去查」的按钮，
     * 每点一次就烧一次配额 —— 而那份数据其实早就下过、好好地躺在缓存里。
     */
    fun peekSearch(query: String): Pair<SearchResponse, Long>? =
        peek<SearchResponse>(searchCacheKey(query))

    /**
     * 拉取仓库 README 原文。
     *
     * 与其它接口不同，这里返回解码后的 Markdown 而不是 DTO：
     * `content` 字段是 base64，解码只在这里做一次。
     *
     * 只在用户点进「说明」tab 时调用，不随仓库详情一起预取。
     */
    suspend fun readme(fullName: String): ApiResult<String> =
        when (val r = get<ReadmeDto>("$base/repos/$fullName/readme", "readme-$fullName")) {
            is ApiResult.Err -> r
            is ApiResult.Ok -> {
                val decoded = decodeReadme(r.value)
                if (decoded == null) {
                    ApiResult.Err("README 解码失败（编码是 ${r.value.encoding.ifBlank { "空" }}）", ApiResult.Err.Kind.Parse)
                } else {
                    ApiResult.Ok(decoded, r.remaining, r.fromCache, r.ageMs)
                }
            }
        }

    /**
     * GitHub 的 readme 接口把内容按 base64 编码返回，字段里还带换行。
     *
     * 返回 null 表示拿不到可用文本（编码不是 base64、或解码抛错），
     * 调用方据此区分「README 是空的」和「没读出来」。
     */
    private fun decodeReadme(dto: ReadmeDto): String? {
        if (dto.encoding != "base64") return null
        return runCatching {
            dto.content.replace("\n", "").let { java.util.Base64.getDecoder().decode(it) }
                .toString(Charsets.UTF_8)
        }.getOrNull()
    }

    suspend fun user(login: String): ApiResult<UserDto> =
        get("$base/users/$login", "user-$login")

    /**
     * 登录用户的资料。`/user` 不接受用户名参数，读的就是 token 所属账号。
     *
     * 不走缓存：这是「我」而不是「某个公开仓库」，缓存会在退出登录后留下
     * 上一个账号的资料。
     */
    suspend fun me(): ApiResult<UserDto> =
        get("$base/user", "me", cacheable = false)

    /**
     * 拉一张头像图的原始字节。
     *
     * 单独一个方法而不是复用 [get]：那是 JSON 通道，这里要的是裸字节，
     * 而且不能把响应写进 [cache] —— 缓存是按 JSON 编码的，图片塞进去
     * 下次就会被 decodeFromString 炸掉。
     *
     * 刻意**不带** Authorization：头像在 `avatars.githubusercontent.com`，
     * 不是 `api.github.com`，没必要把用户令牌发到另一个域名上。
     *
     * 返回 null 表示没取到（网络失败 / 非 2xx）。调用方要区分
     * 「没有头像」和「头像暂时拿不到」，所以是 null 而不是空字节数组。
     */
    suspend fun avatar(url: String): ByteArray? {
        if (url.isBlank()) return null
        return try {
            val response = client.get(url) { header(HttpHeaders.UserAgent, "FitHub") }
            if (response.status.value in 200..299) response.body<ByteArray>() else null
        } catch (e: Exception) {
            Log.w(TAG, "头像拉取失败 $url", e)
            null
        }
    }

    /**
     * 登录用户自己的仓库，**一页**。
     *
     * `per_page` 用接口允许的上限 100，而不是惯例的 30/50：翻页在
     * [com.heiyehk.fithub.data.FitRepository.myRepos] 里做，页数越少请求越少，
     * 而每次请求都花配额。原来的 50 是个更贵的默认值 —— 51 个仓库的用户会
     * 静默少统计 1 个，而 GitHub 不会为此报错。
     *
     * `type=owner` 只取自己**创建**的仓库（fork 别人的不算）。
     * `type` 与 `visibility` 不能同时传，GitHub 会回 422，所以这里只给 type。
     */
    suspend fun myReposPage(sort: String = "updated", page: Int = 1): ApiResult<List<RepoDto>> =
        get(
            "$base/user/repos",
            "myrepos-$sort-p$page",
            mapOf(
                "sort" to sort,
                "per_page" to MY_REPOS_PER_PAGE.toString(),
                "type" to "owner",
                "page" to page.toString(),
            ),
        )

    /**
     * 丢掉「我的仓库」**全部页**的缓存。
     *
     * 逐页作废而不是只清第 1 页：只清第一页的话，刷新后首页是新的、后面几页
     * 还是旧的，仓库数会突然变少，用户会以为是仓库被删了。
     */
    fun invalidateMyRepos(sort: String = "updated") {
        for (p in 1..MAX_MY_REPO_PAGES) invalidate("myrepos-$sort-p$p")
    }

    /** 用户 / 组织搜索 —— GitHub 同一个接口，结果里带 type 区分个人与组织 */
    suspend fun searchUsers(query: String): ApiResult<UserSearchResponse> =
        get(
            "$base/search/users",
            "searchusers-$query",
            mapOf("q" to query, "per_page" to "30"),
        )

    suspend fun orgRepos(org: String, sort: String = "updated"): ApiResult<List<RepoDto>> =
        get("$base/orgs/$org/repos", "orgrepos-$org-$sort", mapOf("sort" to sort, "per_page" to "50"))

    suspend fun userRepos(user: String, sort: String = "updated"): ApiResult<List<RepoDto>> =
        get("$base/users/$user/repos", "userrepos-$user-$sort", mapOf("sort" to sort, "per_page" to "50"))

    companion object {
        private const val TAG = "GitHubApi"

        /** DTO 字段映射一改就 +1，旧缓存整体作废 */
        const val CACHE_VERSION = "v2-"

        /** 发现页缓存 1 小时 */
        const val HOME_TTL = 60 * 60 * 1000L

        /** 「我的仓库」每页条数。100 就是接口上限，再大无效 */
        const val MY_REPOS_PER_PAGE = 100

        /**
         * 「我的仓库」最多翻几页。
         *
         * 正常的终止条件是「某页返回条数不足一页」，但那等于把正确性押在
         * 服务端一定如实返回短页上。这里留一个硬上限当刹车 —— 万一它一直
         * 给满页，循环会永远跑下去，而每次循环都在烧用户的配额。
         * 100 页 = 10000 个仓库，真实账号第 2 页就结束了。
         */
        const val MAX_MY_REPO_PAGES = 100
    }
}

enum class DiscoverSort(val wire: String, val label: String, val note: String) {
    Stars("stars", "最多 star", "GitHub 没有 Trending 接口，这里用 search 按 star 排序"),
    Updated("updated", "最近更新", "按最近 push 时间排序"),
}
