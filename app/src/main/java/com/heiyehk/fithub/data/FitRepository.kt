package com.heiyehk.fithub.data

import com.heiyehk.fithub.data.remote.ApiResult
import com.heiyehk.fithub.data.remote.DiscoverSort
import com.heiyehk.fithub.data.remote.GitHubApi
import com.heiyehk.fithub.data.remote.GitHubMapper
import com.heiyehk.fithub.data.remote.ReleasePick
import com.heiyehk.fithub.data.remote.RateDto
import com.heiyehk.fithub.data.remote.RepoDto
import com.heiyehk.fithub.data.remote.SearchResponse
import com.heiyehk.fithub.data.remote.UserDto
import com.heiyehk.fithub.data.remote.UserSearchResponse

/**
 * 异步三态，UI 直接映射到骨架 / 内容 / 错误
 *
 * [Ok.fromCache] 与 [Ok.ageMs] 沿用 [ApiResult.Ok] 的来源标记：
 * UI 需要区分「刚从网络拿到」与「读的是缓存」，否则会把旧数据当实时展示。
 */
sealed interface Async<out T> {
    data object Loading : Async<Nothing>
    data class Ok<T>(
        val value: T,
        val fromCache: Boolean = false,
        val ageMs: Long = 0L,
    ) : Async<T>

    data class Err(val message: String, val fromCache: Boolean = false) : Async<Nothing>
}

/**
 * UI 唯一的数据入口。
 *
 * 配额是 60 次/小时（未登录），所以策略很克制：
 * 发现两段各 1 次请求，详情 2 次，搜索 1 次，主体页 2 次。
 * 列表不逐个拉 release 填适配徽标，那要 30 次请求，配额就没了。
 * 适配结论只在详情页按需计算。
 */
class FitRepository(val api: GitHubApi) {

    /** FitHub 自己的仓库全名。改仓库地址时这里和 manifest 的 homepage 都要一起改 */
    private val selfRepo = "heiyehk/FitHub"

    /** 发现页的公共查询串：只展示有真实 release 的仓库 */
    private val baseQuery = "has:release topic:android archived:false"

    suspend fun stars(): Async<List<Repo>> =
        api.discover(DiscoverSort.Stars, baseQuery).toRepos()

    suspend fun updated(): Async<List<Repo>> =
        api.discover(DiscoverSort.Updated, baseQuery).toRepos()

    /**
     * 丢弃发现页两段的缓存。
     *
     * 手动刷新前必须调用，否则请求会直接命中未过期缓存，用户点了「刷新」
     * 看到的还是同一份数据。
     */
    fun invalidateDiscover() = api.invalidateDiscover(baseQuery)

    /** 搜索：GitHub 语法原样透传 */
    suspend fun search(query: String): Async<List<Repo>> =
        api.search(query).toRepos()

    /**
     * 详情：仓库元信息 + 最近 releases。
     *
     * releases 这一步失败不能当成空列表，原因挂在 `releasesError` 上，
     * UI 据此写「查不到」。
     */
    /**
     * 查 FitHub 自己最新的 release 版本号。
     *
     * 「我的 → 检查更新」用。本 App 没上任何商店，唯一的更新来源就是仓库 Release，
     * 所以就查自己的仓库 —— 不编造更新源，也不弹假结论。
     *
     * 走 [detail] 整条链会多花一次仓库元信息请求；这里直接打 releases 接口，
     * 因为检查更新只需要 tag。
     *
     * **必须穿缓存**：这是用户主动发起的「现在有没有新版」，而 releases 在 6 小时内
     * 是命中缓存的。不穿的话「刚查过 → 发了新版 → 再查」会拿到上一次那份并回答
     * 「已是最新」—— 用户明确求真的动作，缓存让它给出一个确定的错误答案。
     */
    suspend fun latestReleaseOfSelf(): Async<String> =
        when (val r = api.releases(selfRepo, 5, forceRefresh = true)) {
            is ApiResult.Ok ->
                // 用 visible() 而不是 filter(非 draft)：预发布不能被当成「最新版本」
                ReleasePick.latest(r.value, Prefs.state.value.includePrerelease)
                    ?.let { Async.Ok(it.tagName) }
                    ?: Async.Err("这个仓库还没有发布任何可用版本")

            is ApiResult.Err -> Async.Err(r.message)
        }

    suspend fun detail(fullName: String): Async<Repo> {
        val repoDto = api.repo(fullName)
        if (repoDto is ApiResult.Err) return Async.Err(repoDto.message)

        val base = GitHubMapper.toRepo((repoDto as ApiResult.Ok).value)
        return when (val releases = api.releases(fullName, RELEASES_PER_PAGE)) {
            is ApiResult.Ok -> Async.Ok(
                value = GitHubMapper.applyReleases(
                    base,
                    releases.value,
                    includePrerelease = Prefs.state.value.includePrerelease,
                ),
                fromCache = repoDto.fromCache || releases.fromCache,
                ageMs = maxOf(repoDto.ageMs, releases.ageMs),
            )

            // 元信息来自缓存、releases 来自网络时，也算部分过期
            is ApiResult.Err -> Async.Ok(
                value = base.copy(releasesError = releases.message),
                fromCache = repoDto.fromCache,
                ageMs = repoDto.ageMs,
            )
        }
    }

    /**
     * 「我的项目」：把全部页翻完，同时算出 star / fork / watch 汇总。
     *
     * GitHub 没有任何「某人总共收到多少 star」的接口，只能遍历仓库自己加总，
     * 所以这里的请求数 = 仓库数 / 100。绝大多数账号是 1 次请求；仓库超过 100 个
     * 才需要翻页，而翻不完（撞上限或中途断）时 [MyRepos.complete] 会是 false，
     * 界面据此标注「部分统计」而不是假装那是总数。
     *
     * 汇总从 **DTO** 上算而不是从 [Repo] 上算：`subscribers_count` 在 DTO 里可空，
     * 映射成 [Repo.watchers] 后 null 就变成了 0，「缺失」和「真的是 0」
     * 从此再也分不开 —— 那会让加总悄悄偏低而没人知道。
     *
     * 未登录时 GitHub 返回 404（`/user/repos` 需要 token），这里如实转成
     * 「需要登录」而不是「没有仓库」—— 两者含义完全不同。
     */
    suspend fun myRepos(sort: String = "updated"): Async<MyRepos> {
        val all = mutableListOf<RepoDto>()
        var fromCache = false
        var ageMs = 0L
        var complete = true

        for (page in 1..GitHubApi.MAX_MY_REPO_PAGES) {
            when (val r = api.myReposPage(sort, page)) {
                is ApiResult.Ok -> {
                    all += r.value
                    fromCache = fromCache || r.fromCache
                    ageMs = maxOf(ageMs, r.ageMs)
                    // 不足一整页 = 后面没有了
                    if (r.value.size < GitHubApi.MY_REPOS_PER_PAGE) break
                    // 满页且已到最后一页：说明可能还有下一页，但我们不再翻了
                    if (page == GitHubApi.MAX_MY_REPO_PAGES) complete = false
                }

                is ApiResult.Err -> {
                    // 首页就失败：如实报失败，不能返回空列表 —— 那会被界面
                    // 渲染成「你有 0 个仓库」，把一次网络故障说成了账号状态。
                    if (page == 1) {
                        return Async.Err(
                            if (r.kind == ApiResult.Err.Kind.NotFound) "需要登录后才能看自己的仓库" else r.message
                        )
                    }
                    // 中途断：前面几页是真实数据，继续用，但必须标成不完整
                    complete = false
                    break
                }
            }
        }

        return Async.Ok(
            value = MyRepos(
                repos = all.map { GitHubMapper.toRepo(it) },
                totals = RepoTotals(
                    repos = all.size,
                    stars = all.sumOf { it.stargazersCount.toLong() },
                    forks = all.sumOf { it.forksCount.toLong() },
                    watchers = all.sumOf { (it.subscribersCount ?: 0).toLong() },
                    watchUnknown = all.count { it.subscribersCount == null },
                ),
                complete = complete,
            ),
            fromCache = fromCache,
            ageMs = ageMs,
        )
    }

    /** 手动刷新「我的项目」前调用，否则刷新只是把同一份缓存再读一遍 */
    fun invalidateMyRepos(sort: String = "updated") = api.invalidateMyRepos(sort)

    suspend fun person(login: String): Async<UserDto> = when (val r = api.user(login)) {
        is ApiResult.Ok -> Async.Ok(r.value, r.fromCache, r.ageMs)
        is ApiResult.Err -> Async.Err(r.message)
    }

    /**
     * 仓库 README。
     *
     * 单独一个接口而不是挂在 [detail] 上：README 有 512 KB 上限且只在用户点开
     * 「说明」tab 时才有意义，跟详情一起拉会白白消耗配额。
     *
     * 仓库没有 README（GitHub 返回 404）时走 [Async.Err]，UI 显示「这个仓库没有 README」，
     * 不显示空白面板。
     */
    suspend fun readme(fullName: String): Async<Readme> = when (val r = api.readme(fullName)) {
        is ApiResult.Ok -> {
            val md = r.value
            if (md.isBlank()) {
                Async.Err("这个仓库没有 README 文件。")
            } else {
                Async.Ok(Readme.from(md, md.toByteArray(Charsets.UTF_8).size), r.fromCache, r.ageMs)
            }
        }

        is ApiResult.Err -> Async.Err(
            if (r.kind == ApiResult.Err.Kind.NotFound) "这个仓库没有 README 文件。" else r.message
        )
    }

    /** 主体名下的仓库，客户端过滤掉已归档的（列表接口不按 release 过滤） */
    suspend fun personRepos(login: String, org: Boolean): Async<List<Repo>> {
        val raw = if (org) api.orgRepos(login) else api.userRepos(login)
        return when (raw) {
            is ApiResult.Ok -> Async.Ok(
                value = raw.value.map { GitHubMapper.toRepo(it) }.filter { !it.archived },
                fromCache = raw.fromCache,
                ageMs = raw.ageMs,
            )
            is ApiResult.Err -> Async.Err(raw.message)
        }
    }

    suspend fun rate(): Pair<Int, Int> = when (val r = api.rateLimit()) {
        is ApiResult.Ok -> r.value.resources.core.let { it.remaining to it.limit }
        is ApiResult.Err -> api.lastRemaining to api.lastLimit
    }

    /**
     * 这个仓库到底有没有可下载的产物。
     *
     * 列表接口不返回 release 信息，只能逐个问，所以由用户点才查，
     * 不为一个页面用光 60 次/小时的配额。查过一次会进文件缓存。
     *
     * 返回 null 表示查询失败，调用方要区分「没有」和「不知道」，
     * 所以返回值是 Boolean? 而不是 Boolean。
     */
    suspend fun hasInstallable(fullName: String): Boolean? =
        when (val r = api.releases(fullName, 1)) {
            is ApiResult.Ok -> r.value.any { !it.draft && it.assets.isNotEmpty() }
            is ApiResult.Err -> null
        }

    /**
     * 精选区的项目。
     *
     * 按确切的全名拉真实元信息，每条 1 次请求，不拉 releases。
     * 拉到的条数可以少于传入的条数，不补位。
     */
    suspend fun featured(fullNames: List<String>): Async<List<Repo>> {
        val out = mutableListOf<Repo>()
        var fromCache = false
        var ageMs = 0L
        for (full in fullNames) {
            when (val r = api.repo(full)) {
                is ApiResult.Ok -> {
                    out += GitHubMapper.toRepo(r.value)
                    fromCache = fromCache || r.fromCache
                    ageMs = maxOf(ageMs, r.ageMs)
                }
                // 少一块就少一块
                is ApiResult.Err -> Unit
            }
        }
        return if (out.isEmpty()) Async.Err("精选项目一个都没拉到，检查网络或稍后再试")
        else Async.Ok(out, fromCache, ageMs)
    }

    /**
     * 按包名反查候选仓库。
     *
     * 先用完整包名搜（能命中 README 里写了包名的仓库），
     * 搜不到再退到「包名最后一段 + in:name」（`com.schabi.newpipe` → `newpipe in:name`）。
     * 返回的都只是候选，要用户确认后才写入绑定表。
     */
    suspend fun findRepoCandidates(packageName: String): Async<List<Repo>> {
        val seen = LinkedHashMap<String, Repo>()
        val queries = buildList {
            add(packageName)
            packageName.substringAfterLast('.').takeIf { it.length >= 3 }?.let { add("$it in:name") }
        }
        for (q in queries) {
            if (seen.size >= 8) break
            when (val r = api.search(q).toRepos()) {
                is Async.Err -> return r
                is Async.Ok -> r.value.forEach { repo ->
                    if (seen.size < 8) seen.putIfAbsent(repo.id, repo)
                }
                Async.Loading -> Unit
            }
        }
        return Async.Ok(seen.values.toList())
    }

    /** 用户 / 组织搜索，type 过滤在客户端做（GitHub 同一个接口） */
    suspend fun searchPeople(query: String, wantOrg: Boolean): Async<List<Person>> =
        when (val r = api.searchUsers(query)) {
            is ApiResult.Ok -> Async.Ok(
                r.value.items.mapNotNull { dto ->
                    val isOrg = dto.type == "Organization"
                    if (isOrg != wantOrg) return@mapNotNull null
                    Person(
                        type = if (isOrg) "org" else "user",
                        name = dto.name?.takeIf { it.isNotBlank() } ?: dto.login,
                        handle = dto.login,
                        bio = dto.bio?.takeIf { it.isNotBlank() } ?: "这个账号没有写 bio。",
                        location = dto.location.orEmpty(),
                        followers = dto.followers,
                        following = dto.following,
                        hireable = dto.hireable ?: false,
                        tileBg = if (isOrg) 0xFFEAEEEE else 0xFFE9EDF7,
                        tileFg = if (isOrg) 0xFF2B4B4B else 0xFF2F3E77,
                    )
                },
                fromCache = r.fromCache,
                ageMs = r.ageMs,
            )
            is ApiResult.Err -> Async.Err(r.message)
        }

    private fun ApiResult<SearchResponse>.toRepos(): Async<List<Repo>> = when (this) {
        is ApiResult.Ok -> Async.Ok(
            value = value.items.map { GitHubMapper.toRepo(it) },
            fromCache = fromCache,
            ageMs = ageMs,
        )

        is ApiResult.Err -> Async.Err(message)
    }

    /**
     * 只从缓存取一次搜索的结果，**不发请求**。盘上没有就返回 null。
     *
     * 给首页的 topic 板块冷启动预填用：这些板块的结果原来只活在 UI 的内存 map 里，
     * 进程一回收就归零，用户每次开 App 看到的都是一排「点我去查」的按钮，每点一次
     * 烧一次配额 —— 而那份数据其实早就下过。
     *
     * 和 [search] 的区别就是不发请求：宁可让用户还看到一个按钮，也不要在用户没
     * 表达任何意图的时候替他花掉配额（未登录只有 60 次/小时）。
     */
    fun searchFromCache(query: String): Async<List<Repo>>? =
        api.peekSearch(query)?.let { (dto, age) ->
            Async.Ok(
                value = dto.items.map { GitHubMapper.toRepo(it) },
                fromCache = true,
                ageMs = age,
            )
        }

    private companion object {
        /**
         * 详情页一次拉多少条 release。
         *
         * 之前是 3，而筛选是「先拉 3 条、再滤掉预发布」。两个一撞就出事：
         * 像 v2rayNG 那样连续发了好几个预发布的仓库，拉回来的 3 条**全是**预发布，
         * 滤完就是空列表，页面据此断言「这个仓库没有 release、没有产物」——
         * 而它第 10 位的 2.2.6 正式版正带着可安装的 APK，我们压根没去请求。
         *
         * 取 20 是权衡：GitHub 允许到 100，但 release 列表要全部展开成资产，
         * 拉太多会明显拖慢详情页首屏，也更费配额。20 条足以跨过任何一段连续的
         * 预发布（那种情况本来也不该当作正式版来推）。
         */
        const val RELEASES_PER_PAGE = 20
    }
}
