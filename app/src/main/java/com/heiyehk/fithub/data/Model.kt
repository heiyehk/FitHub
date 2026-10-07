package com.heiyehk.fithub.data

import android.os.Build
import androidx.annotation.StringRes
import com.heiyehk.fithub.R
import com.heiyehk.fithub.ui.theme.FitTone
import kotlinx.serialization.Serializable

/**
 * [Explain] 参数里的「另一个资源的 ID」。
 *
 * 场景：「签名为%5$s」里的签名方式本身是个枚举标签，也是要翻译的资源。
 * 直接把 [androidx.annotation.StringRes] 塞进 args 会被 `String.format` 打印成整数，
 * 所以用这个包装标一下，由 UI 层的 `explainText()` 先解析成文本再格式化。
 */
@JvmInline
value class ResArg(@param:StringRes @get:StringRes val res: Int)

/**
 * 领域层产出的「可本地化文案」：资源 ID + 格式化参数。
 *
 * data/ 这一层拿不到 [android.content.Context]，所以它不拼字符串，只持有
 * `@StringRes` 和参数；由 UI 层 `stringResource(x.res, *x.args)` 渲染
 * （见 `ui/Localized.kt` 的 `explainText`）。
 *
 * 不用「传 Context 进 data 层」的做法：那会让纯函数 [FitEngine] 变成有 Android 依赖的
 * 函数，JVM 单元测试跑不了，而这些文案恰恰是最需要被测试覆盖的部分。
 */
data class Explain(
    @param:StringRes val res: Int,
    val args: List<Any> = emptyList(),
) {
    companion object {
        /** 无参数文案 */
        fun of(@StringRes res: Int) = Explain(res)

        /** 参数里有嵌套资源 */
        fun of(@StringRes res: Int, vararg args: Any) = Explain(res, args.toList())
    }
}

/** 本机档案 —— 所有适配结论的唯一基准 */
data class DeviceProfile(
    val name: String,
    val abi: String,
    val sdk: Int,
    val sdkLabel: String,
    val installed: List<InstalledApp>,
    /** 支持的全部 ABI，用来判断「可降级的 universal 包」到底能不能装 */
    val supportedAbis: List<String> = listOf(abi),
) {
    companion object {
        /**
         * 探测本机档案。
         *
         * 适配结论以本机实测值为准。设备参数一旦写死，x86_64 设备上会把 arm64 包
         * 判成完全匹配。
         */
        fun detect(): DeviceProfile {
            // Build.* 是平台类型，真机上不会为 null，但 android.jar 桩里全是 null，
            // 少数定制 ROM 也会把 MANUFACTURER / MODEL 留空。detect() 在静态初始化
            // 里被调用，一个字段为 null 就 NPE，所以逐个兜住。
            val abis = Build.SUPPORTED_ABIS?.toList().orEmpty().ifEmpty { listOf("unknown") }
            val sdk = Build.VERSION.SDK_INT
            // 取不到品牌型号时留空串，UI 用 stringResource 兜底显示「本机」——
            // 这里写死中文的话，英文界面下会出现一个中文机型名。
            val name = listOf(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty())
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(" ")
            return DeviceProfile(
                name = name,
                abi = abis.first(),
                sdk = sdk,
                sdkLabel = "Android " + (Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: sdk.toString()),
                installed = emptyList(),
                supportedAbis = abis,
            )
        }
    }
}

data class InstalledApp(
    val repoId: String,
    val version: String,
    val versionCode: Int,
    val signing: SigningRelation,
    /** 真实包名，用来和 [ScannedApp] 对上 */
    val packageName: String? = null,
    /** 关联是怎么来的 —— UI 要如实写出来 */
    val source: LinkSource = LinkSource.Manual,
)

enum class LinkSource(@StringRes val labelRes: Int) {
    /** 通过 FitHub 装的，repoId 一定是真的 */
    InstalledViaUs(R.string.link_installed_via_us),
    /** 反查 GitHub 搜到的 */
    RepositorySearch(R.string.link_repo_search),
    /** 用户手动绑定 */
    Manual(R.string.link_manual),
}

enum class SigningRelation { Same, Different, Unknown }

/** 产物与本机比对后的结论 */
enum class FitState { Match, Degrade, Mismatch, Unknown, Checksum }

/**
 * [Repo.version] 拿不到 release 时用的占位符。
 *
 * 它只是「这里本来该有个版本号」的空位，**不是**一个版本 —— 界面不该把它画出来。
 */
const val NO_VERSION = "—"

enum class Verdict {
    Ok, Warn, Bad, Unknown;

    /**
     * [Unknown] 是**中性**的，不是错误。
     *
     * 它表示「还没读到 / 判定不了」—— 列表里的简略快照、请求失败、仓库压根没 release，
     * 都会落到这里。染成 `Bad` 会让首页每张卡都挂一个红色「无法解析」，
     * 用户读到的是一屏报错，而事实只是「信息不足」。
     *
     * 详情页早就为此在加载期把徽标藏掉了（见 DetailPanel 里 `detailLoading` 那段注释：
     * 「那份 Unknown 不是结论，只是还没读到，染成红色就成了『这个仓库无法解析』」），
     * 但**色调映射本身**没改，而首页没有那层保护 —— 错在这里，不在调用点。
     * 灰色（Muted -> ink3）说清了「信息不足」，又不冒充告警。
     */
    val tone: FitTone
        get() = when (this) {
            Ok -> FitTone.Ok
            Warn -> FitTone.Warn
            Bad -> FitTone.Bad
            Unknown -> FitTone.Muted
        }

    @get:StringRes
    val labelRes: Int
        get() = when (this) {
            Ok -> R.string.verdict_ok
            Warn -> R.string.verdict_warn
            Bad -> R.string.verdict_bad
            Unknown -> R.string.verdict_unknown
        }
}

/**
 * 一条 release 的更新日志。
 *
 * [body] 存的是 GitHub 返回的**原始 Markdown**，不是拆好的纯文本行。
 * 之前在 mapper 里按行 `take(6)` 摊平成 `List<String>`，Markdown 语义到 UI 之前
 * 就已经丢光了 —— 标题、列表、`code` 全变成一行行的字，渲染器再怎么做也回不来。
 * 拆行属于渲染时的事，渲染器（Markdown 组件）自己会处理。
 */
data class ReleaseNote(
    val tag: String,
    val date: String,
    val prerelease: Boolean = false,
    val body: String = "",
)

data class Asset(
    val name: String,
    val kind: String,
    val abi: String?,
    val sizeMb: Double,
    val sha: String?,
    val sdkLabel: String?,
    val fit: FitState,
    /** 不匹配/降级的原因，UI 直接显示，所以是可本地化的 [Explain] 而不是 String */
    val reason: Explain? = null,
    // 以下字段只有真实数据（GitHub Release / APK 解析）才有
    val downloadUrl: String? = null,
    val contentType: String? = null,
    val downloadCount: Int = 0,
    val publishedAt: String? = null,
    val tag: String? = null,
    val prerelease: Boolean = false,
    /** ABI 是从文件名推断的，还是真解析出来的 —— UI 上必须区分 */
    val inferred: Boolean = false,
    val parsed: Boolean = false,
    val realPackageName: String? = null,
    val realVersionName: String? = null,
    val realVersionCode: Long? = null,
    val realMinSdk: Int? = null,
    val realTargetSdk: Int? = null,
    val realSignerSha: String? = null,
    val realAbis: List<String> = emptyList(),
    val parseError: String? = null,
)

/**
 * 仓库 README 原文。
 *
 * 按需加载：只有用户点进「说明」tab 才请求，不随仓库详情一起拉。
 *
 * [truncated] 表示内容超过 [MAX_BYTES] 被截断，UI 必须如实标注 ——
 * 静默截断会让用户以为读到了完整文档。
 */
data class Readme(
    val markdown: String,
    val sizeBytes: Int,
    val truncated: Boolean = false,
) {
    val isEmpty: Boolean get() = markdown.isBlank()

    companion object {
        /** 单个 README 的字节上限。超过的按前 N KB 截断并标记 */
        const val MAX_BYTES = 512 * 1024

        fun from(raw: String, sizeBytes: Int): Readme {
            val bytes = raw.toByteArray(Charsets.UTF_8)
            if (bytes.size <= MAX_BYTES) {
                return Readme(raw, sizeBytes)
            }
            val clipped = String(bytes, 0, MAX_BYTES, Charsets.UTF_8)
                .substringBeforeLast('\n')
            return Readme(clipped, sizeBytes, truncated = true)
        }
    }
}

data class Dist(
    val ext: String,
    val stem: String,
    val abis: List<String>,
    val minSdk: Int,
    val sdkLabel: String,
    val signed: SignedBy,
    val sizeMb: Double,
    val desktop: Boolean = false,
)

enum class SignedBy(@StringRes val labelRes: Int) {
    Release(R.string.signed_release),
    CiSelfSigned(R.string.signed_ci),
    Unverified(R.string.signed_unverified),
}

/** 本机与已装应用的关系 */
sealed interface DeviceState {
    data object NotInstalled : DeviceState

    data class Upgrade(val from: String, val to: String) : DeviceState

    /**
     * 已是最新。
     *
     * [version] 是 [Explain] 而不是 String：「1.2.3（本机比 Release 还新）」这类
     * 括号补充语会随语序变化，只能由 UI 层在拿到资源后拼。
     */
    data class Latest(val version: Explain) : DeviceState

    data class SigningConflict(val installed: String, val detail: Explain) : DeviceState

    /**
     * 版本号没法比 —— 远端 APK 没解析过，拿不到真实 versionCode。
     *
     * versionCode 为 0 表示未知，不能拿来比大小。
     *
     * [released] 为 null 表示连版本号都没取到（不是 "—"），由 UI 层用
     * 「最新 Release」兜底，那也是要翻译的。
     */
    data class VersionUnknown(val installed: String, val released: String?) : DeviceState
}

/** 数据来源，UI 上需要区分 */
enum class DataSource { Sample, GitHub }

/**
 * 关注（订阅）的一项。
 *
 * 关注是本地行为，服务器上没有任何记录。元信息以快照形式存在本地，
 * 目的是让订阅页离线可读、少发请求；代价是快照会过期，
 * 所以 [metaFetchedAt] 必须一起存，UI 要据此说明数据多旧。
 *
 * 只存展示与判断需要的字段，不整份存 [Repo]：领域模型字段会随迭代增加，
 * 整份存会让旧关注项在升级后读不出来。
 */
@Serializable
data class Subscription(
    val fullName: String,
    val addedAt: Long,
    // 元信息快照
    val name: String = "",
    val owner: String = "",
    val desc: String = "",
    val stars: Int = 0,
    val lang: String = "",
    val topics: List<String> = emptyList(),
    val lastPush: String = "",
    val ownerAvatar: String = "",
    val ownerRepos: Int = 0,
    val latestTag: String = "",
    val latestReleaseDate: String = "",
    /** 快照的抓取时刻，用于显示「X 小时前」。0 表示从未刷新过 */
    val metaFetchedAt: Long = 0L,
    val note: String = "",
) {
    val displayName: String get() = name.ifBlank { fullName.substringAfterLast('/') }
    val isStale: Boolean
        get() = metaFetchedAt > 0 && System.currentTimeMillis() - metaFetchedAt > STALE_AFTER_MS

    companion object {
        /** 超过 6 小时的元信息在 UI 上标注为旧 */
        const val STALE_AFTER_MS = 6 * 60 * 60 * 1000L
    }
}

data class Repo(
    val id: String,
    val name: String,
    val owner: String,
    val monogram: String,
    val desc: String,
    val lang: String,
    val langColor: Long,
    val langShare: List<Int>,
    val stars: Int,
    val forks: Int,
    val watchers: Int,
    val issues: Int,
    val version: String,
    val versionCode: Int,
    val date: String,
    val topics: List<String>,
    val tileBg: Long,
    val tileFg: Long,
    val history: List<ReleaseNote>,
    val dist: Dist,
    // 真实数据字段
    val source: DataSource = DataSource.Sample,
    val htmlUrl: String = "",
    val downloads: Int = 0,
    val license: String = "",
    val archived: Boolean = false,
    /** 有没有真实的 release 产物 */
    val hasRealRelease: Boolean = false,
    /**
     * 这个仓库只发过预发布，而用户关掉了「包含预发布版本」。
     *
     * 和 `hasRealRelease == false` 是两回事：后者是「一个 release 都没有」，
     * 前者是「有，只是被开关滤掉了」。说成前者会让人以为得自己去发一个包。
     */
    val prereleaseOnly: Boolean = false,
    /**
     * 取 release 失败时的原因。
     *
     * null = 查到了（可能有也可能没有产物，看 [hasRealRelease]）
     * 非 null = 没查到，网络/限流/解析失败。
     *
     * 两者不能混：`releasesError != null` 时 UI 写「查不到」，
     * 不写「这个仓库没有 release」。
     */
    val releasesError: String? = null,
    // 下面这几个是派生数据，但必须放进构造参数：
    // 写成类体里的 var 时 copy() 不会带上，data class 的 equals 也只比构造参数，
    // 新旧实例会被判为相等，mutableStateOf 就会丢弃新值。
    val assets: List<Asset> = emptyList(),
    val best: Asset? = null,
    val verdict: Verdict = Verdict.Unknown,
    val device: DeviceState = DeviceState.NotInstalled,
) {
    /**
     * 有没有一个**真的**版本号可显示。
     *
     * [version] 在拿不到 release 时会退化成占位符「—」（见 GitHubMapper 的
     * `version = latest?.tagName ?: NO_VERSION`）。那根横杠不是版本，它说的是「没查到」，
     * 却和真正的版本号长得一样、还占着位置 —— 首页热门卡和仓库行都把它原样画了出来，
     * 读起来像是有个叫「—」的版本。
     *
     * 界面判断「要不要显示版本号」必须走这里，不要去比字面量：
     * 占位符长什么样是数据层的事，散在各个界面里比字符串迟早会漏一处。
     */
    val hasVersion: Boolean get() = version.isNotBlank() && version != NO_VERSION

    /** 用新的产物列表重算结论，返回新实例（不 mutate） */
    fun withAssets(
        list: List<Asset>,
        deviceState: DeviceState = this.device,
    ): Repo {
        val chosen = pickBest(list)
        val conclusion = when {
            chosen?.fit == FitState.Match -> Verdict.Ok
            chosen != null -> Verdict.Warn
            list.any { it.fit == FitState.Mismatch } -> Verdict.Bad
            else -> Verdict.Unknown
        }
        return copy(assets = list, best = chosen, verdict = conclusion, device = deviceState)
    }
}

/**
 * 挑「最该给用户的那一个」—— 全应用**唯一**的一份选择规则。
 *
 * 逐级降级：架构完全匹配 → 只是降级 → 没有架构信息但确实是 APK → 没有架构信息。
 * **每一档内部都先挑 release**，不只降级档。
 *
 * 为什么每一档都要：产物行的「完全匹配」是下载之后读真实清单得出的，
 * 所以一个仓库里 debug 包和 release 包**常常同时都是 Match**。
 * 若某一档只用 `firstOrNull`，谁在列表里靠前谁赢，而 debug 常常就排在前面。
 *
 * 为什么 release 优先：debug 包通常带 debug 签名、还可能带 applicationId 后缀
 * （`com.foo.debug`），装了要么和正式版冲突、要么装成一个用不上的分身；
 * unsigned 更是根本装不上。这两个都不是「更省事的选择」，是**装不出想要结果的选择**。
 *
 * ## 这段规则曾经存在过两份
 *
 * 原先 `GitHubMapper` 里另有一份同名实现，`Repo.withAssets()` 这里又自己抄了一遍**不带**
 * release 优先的版本。真正喂给主 CTA 的 `repo.best` 来自 `withAssets`，
 * 于是「下载给 release」的那次修复改的是**没人调的那份**，界面上照样把 debug
 * 当成「最该给用户的那一个」推给用户 —— 而单测只钉住了那份死代码，全绿。
 *
 * 所以规则必须和**用它的字段**写在同一个文件里，而不是留在抓数据的层里。
 */
internal fun pickBest(assets: List<Asset>): Asset? =
    preferRelease(assets) { it.fit == FitState.Match && it.kind == "APK" }
        ?: preferRelease(assets) { it.fit == FitState.Match }
        ?: preferRelease(assets) { it.fit == FitState.Degrade && it.kind == "APK" }
        ?: preferRelease(assets) { it.fit == FitState.Degrade }

/**
 * 在同一档候选里先挑 release 产物。
 *
 * `-unsigned` 排在 `-release` 之后而不是之前：文件名里同时出现两个词时
 * （`app-release-unsigned.apk`），它装不上，不能因为含 "release" 就被选中。
 * 两个都没有时保持传入顺序 —— GitHub 返回什么就是什么，不额外排序。
 */
private fun preferRelease(assets: List<Asset>, pred: (Asset) -> Boolean): Asset? {
    val candidates = assets.filter(pred)
    return candidates.firstOrNull { isReleaseBuild(it.name) } ?: candidates.firstOrNull()
}

/** 文件名像正式发布包，且不是未签名的那种 */
internal fun isReleaseBuild(name: String): Boolean {
    val lower = name.lowercase()
    return lower.contains("release") && !lower.contains("unsigned")
}

/**
 * 一组仓库的汇总数字。
 *
 * GitHub 没有「某人总共收到多少 star」这种接口，所以这三个数只能遍历自己的
 * 仓库自己加总 —— 也就意味着它**只覆盖实际拉到的那些仓库**。
 *
 * [watchUnknown] 是这个覆盖度的自证：有几个仓库的 `subscribers_count` 没在
 * 响应里，非 0 就说明 watch 的加总缺了一块，界面必须说出来，而不是照样
 * 显示一个看起来完整的数字。
 *
 * 加总用 Long：单个仓库 star 上限百万级，仓库数上千就越过 2^31，
 * Int 累加会静默溢出成负数。
 */
data class RepoTotals(
    val repos: Int = 0,
    val stars: Long = 0,
    val forks: Long = 0,
    val watchers: Long = 0,
    /** 有几个仓库的 subscribers_count 没返回。watch 因此偏低 */
    val watchUnknown: Int = 0,
)

/**
 * 「我的项目」的数据：仓库列表 + 汇总数字，一次拿全。
 *
 * 「我的」页要的 star/fork/watch 与项目列表页要的仓库是**同一批请求**。
 * 拆成两个接口的话，来回切页面会各拉一遍：配额翻倍，而且两个页面可能显示
 * 不同时间点的数字，用户会以为是两个不一致的真相。
 */
data class MyRepos(
    val repos: List<Repo> = emptyList(),
    val totals: RepoTotals = RepoTotals(),
    /**
     * 是否真的翻到了最后一页。
     *
     * false = 撞到页数上限，或中途某一页取失败 —— 只统计到了前面一部分。
     * 这种情况下汇总必须标注为「部分」，否则等于给出一个看起来完整的假数。
     */
    val complete: Boolean = true,
    val fromCache: Boolean = false,
    val ageMs: Long = 0L,
)

enum class DiscoveryMode(@StringRes val labelRes: Int) {
    Fit(R.string.discovery_fit),
    Recent(R.string.discovery_recent),
    Star(R.string.discovery_star),
    Upgradable(R.string.discovery_upgradable),
    Installed(R.string.discovery_installed),
}


/** 用户 / 组织 */
data class Person(
    val type: String,
    val name: String,
    val handle: String,
    val bio: String,
    val location: String,
    val followers: Int,
    val following: Int,
    val hireable: Boolean = false,
    val tileBg: Long,
    val tileFg: Long,
)
