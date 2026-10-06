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

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * 绑定表落在 filesDir，丢了就是用户数据丢了，所以用 [LocalStore] 而不是缓存层。
     * [LocalStore] 本身无状态，只持有目录与 Json，按需构造即可。
     */
    private fun store(context: Context) = LocalStore(context.filesDir, json)

    /** 读出所有已确认的绑定：packageName -> owner/repo */
    fun bindings(context: Context): Map<String, String> = read(context)

    fun bind(context: Context, packageName: String, fullName: String) {
        val next = bindings(context).toMutableMap().apply { this[packageName] = fullName }
        store(context).write(LocalStore.KEY_REPO_LINKS, next)
        bindingsStore = next
    }

    fun unbind(context: Context, packageName: String) {
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
        bindingsStore = read(context)
    }

    private fun read(context: Context): Map<String, String> =
        store(context).read(LocalStore.KEY_REPO_LINKS, emptyMap())
}
