package com.heiyehk.fithub.data

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * 已装应用 → GitHub 仓库的关联。
 *
 * 不做静默自动关联。包名反查出来的是候选，是不是它得用户点一下确认：
 * 把本机装的 `com.example.foo` 悄悄认成某个同名仓库，
 * 就会给用户报一个并不存在的「可升级」。
 *
 * 关联的三个来源，UI 上都会写明：
 * - [LinkSource.InstalledViaUs] 自己通过 FitHub 装的，一定真
 * - [LinkSource.RepositorySearch] 反查候选后用户确认的
 * - [LinkSource.Manual] 用户手动绑的
 */
object LinkEngine {

    /**
     * 内置绑定：包名 -> 仓库全名，**编译期已知正确**，不需要反查也不消耗配额。
     *
     * 只放「这个 App 就是这个仓库」这种恒等关系。反查是启发式的、会出错，
     * 而把 FitHub 绑到 FitHub 上没有任何猜测成分 —— 每个装了这个包的人
     * 都不该再走一遍「搜包名 → 看候选 → 点确认」。
     *
     * **这条关系是固定的**：既不能解绑，也不能改绑到别的仓库。
     * [bind] / [unbind] 都会对这里的包名直接返回，界面上也不给这两个入口 ——
     * 一个装在设备上的 FitHub 指向的不是 FitHub 自己的仓库，那就是数据错了，
     * 让用户点两下就能改掉并不能让数据变对。
     *
     * 改了仓库地址，这里和 [com.heiyehk.fithub.data.FitRepository] 的 selfRepo 要一起改。
     */
    private val BUILT_IN_LINKS: Map<String, String> = mapOf(
        "com.heiyehk.fithub" to "heiyehk/FitHub",
    )

    /** 这个包名是不是内置绑定（UI 据此隐藏「换绑 / 解除关联」两个入口） */
    fun isBuiltIn(packageName: String): Boolean = packageName in BUILT_IN_LINKS

    /**
     * 把内置绑定合进已有绑定表。纯函数，便于单测。
     *
     * 内置项是**覆盖**而不是「仅在缺失时补」：这条关系是固定的，而落盘文件里
     * 可能留着别处写进去的别的值（那时这条还没做成不可改的）。不覆盖的话，
     * 一台装过旧版本又升上来的机器会永远指着一个错的仓库，而界面上还没有任何
     * 入口能把它改回来。
     */
    internal fun mergeBuiltIns(current: Map<String, String>): Map<String, String> =
        current + BUILT_IN_LINKS

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * 绑定表落在 filesDir，丢了就是用户数据丢了，所以用 [LocalStore] 而不是缓存层。
     * [LocalStore] 本身无状态，只持有目录与 Json，按需构造即可。
     */
    private fun store(context: Context) = LocalStore(context.filesDir, json)

    /** 读出所有已确认的绑定：packageName -> owner/repo */
    fun bindings(context: Context): Map<String, String> = read(context)

    fun bind(context: Context, packageName: String, fullName: String) {
        // 内置绑定是固定的。静默返回而不是抛异常：调用方是 UI 回调，
        // 抛出去只会在点击处理里变成崩溃，而界面上本来就不该有这个入口。
        if (isBuiltIn(packageName)) return
        val next = bindings(context).toMutableMap().apply { this[packageName] = fullName }
        store(context).write(LocalStore.KEY_REPO_LINKS, next)
        bindingsStore = next
    }

    fun unbind(context: Context, packageName: String) {
        if (isBuiltIn(packageName)) return
        val next = bindings(context).toMutableMap().apply { remove(packageName) }
        store(context).write(LocalStore.KEY_REPO_LINKS, next)
        bindingsStore = next
    }

    /**
     * 把反查结果并进 [Env.device.installed]，
     * 于是详情页的 `FitEngine.deviceState(repo)` 拿到已绑定的真实结论。
     *
     * [repos] 是按包名索引的已解析仓库（packageName -> Repo）。
     * 只写三项都成立的：既在绑定表里、本机扫得到这个包、而且仓库也真的解析出来了。
     */
    fun applyBindings(scan: ScanResult, repos: Map<String, Repo>) {
        val links = bindingsStore
        if (links.isEmpty()) {
            Env.device = Env.device.copy(installed = emptyList())
            return
        }
        val byPkg = scan.apps.associateBy { it.packageName }
        val out = links.mapNotNull { (pkg, fullName) ->
            val app = byPkg[pkg] ?: return@mapNotNull null
            val repo = repos[pkg]?.takeIf { it.id == fullName } ?: return@mapNotNull null
            InstalledApp(
                repoId = repo.id,
                version = app.versionName,
                versionCode = app.versionCode.toInt(),
                signing = SigningRelation.Unknown, // 远端证书没读到之前不下结论
                packageName = pkg,
                source = LinkSource.RepositorySearch,
            )
        }
        Env.device = Env.device.copy(installed = out)
    }

    /**
     * 绑定表来自文件，读一次缓存在内存里。
     * [bind] / [unbind] 之后调用 [load] 刷新。
     */
    private var bindingsStore: Map<String, String> = emptyMap()
        private set

    fun load(context: Context) {
        bindingsStore = seedBuiltIns(context, read(context))
    }

    /**
     * 把 [BUILT_IN_LINKS] 写进绑定表。规则见 [mergeBuiltIns]。
     *
     * 其余条目一律不动：那些是用户自己确认过的绑定。
     */
    private fun seedBuiltIns(
        context: Context,
        current: Map<String, String>,
    ): Map<String, String> {
        val merged = mergeBuiltIns(current)
        if (merged == current) return current
        store(context).write(LocalStore.KEY_REPO_LINKS, merged)
        return merged
    }

    private fun read(context: Context): Map<String, String> =
        store(context).read(LocalStore.KEY_REPO_LINKS, emptyMap())
}
