package com.heiyehk.fithub.data.remote

import com.heiyehk.fithub.R
import com.heiyehk.fithub.data.Asset
import com.heiyehk.fithub.data.DataSource
import com.heiyehk.fithub.data.DeviceState
import com.heiyehk.fithub.data.Dist
import com.heiyehk.fithub.data.Explain
import com.heiyehk.fithub.data.FitEngine
import com.heiyehk.fithub.data.FitState
import com.heiyehk.fithub.data.Readme
import com.heiyehk.fithub.data.ReleaseNote
import com.heiyehk.fithub.data.Repo
import com.heiyehk.fithub.data.Env
import com.heiyehk.fithub.data.SignedBy
import com.heiyehk.fithub.data.Verdict

/**
 * DTO → 领域模型。
 *
 * 未解析的安装包，ABI 只能来自文件名推断，不能当作解析结果。
 * Asset.inferred 标记这一点，UI 照实显示。
 */
object GitHubMapper {

    /** 语言 → 展示色，没见过的给中性色 */
    private val LANG_COLORS = mapOf(
        "Kotlin" to 0xFFA97BFF, "Java" to 0xFFB07219, "Dart" to 0xFF0175C2,
        "C++" to 0xFF00599C, "C" to 0xFF555555, "Rust" to 0xFFDEA584,
        "Go" to 0xFF00ADD8, "Python" to 0xFF3572A5, "TypeScript" to 0xFF3178C6,
        "JavaScript" to 0xFFF1E05A, "Swift" to 0xFFF05138, "Ruby" to 0xFF701516,
        "Shell" to 0xFF89E051, "C#" to 0xFF178600, "PHP" to 0xFF4F5D95,
    )

    /** 从文件名里认 ABI —— 这是推断，不是解析 */
    private val ABI_PATTERNS = listOf(
        "arm64-v8a" to "arm64-v8a",
        "aarch64" to "arm64-v8a",
        "armeabi-v7a" to "armeabi-v7a",
        "armv7" to "armeabi-v7a",
        "x86_64" to "x86_64",
        "x86-64" to "x86_64",
        "universal" to "universal",
        "anydpi" to "universal",
        "x86" to "x86",
    )

    fun inferAbi(fileName: String): String? {
        val lower = fileName.lowercase()
        // 先匹配长模式，避免 x86 抢在 x86_64 前面
        return ABI_PATTERNS.sortedByDescending { it.first.length }
            .firstOrNull { lower.contains(it.first) }?.second
    }

    /** 语言 → 展示色，没见过的给中性色。订阅快照没有 DTO 可用，靠这个补回颜色 */
    fun langColorOf(lang: String): Long = LANG_COLORS[lang] ?: 0xFF8A908A

    fun toRepo(dto: RepoDto): Repo {
        val lang = dto.language ?: "—"
        val color = langColorOf(lang)
        return Repo(
            id = dto.fullName,
            name = dto.name.ifBlank { dto.slug },
            owner = dto.owner.login,
            monogram = dto.name.take(2).uppercase().ifBlank { "GH" },
            desc = dto.description?.takeIf { it.isNotBlank() }
                ?: "这个仓库没有写描述。GitHub 上有 release 才会出现在这里。",
            lang = lang,
            langColor = color,
            langShare = listOf(100),
            stars = dto.stargazersCount,
            forks = dto.forksCount,
            watchers = dto.subscribersCount ?: 0,
            issues = dto.openIssuesCount,
            version = "—",
            versionCode = 0,
            date = dto.lastPush,
            topics = dto.topics.take(4),
            tileBg = lighten(color),
            tileFg = color,
            history = emptyList(),
            dist = Dist("", "", emptyList(), 0, "—", SignedBy.Unverified, 0.0),
            source = DataSource.GitHub,
            htmlUrl = dto.htmlUrl,
            license = dto.license?.spdxId ?: "",
            archived = dto.archived,
            hasRealRelease = false,
        )
    }

    /** 调亮背景色，让图标块在白底上还能有层次 */
    private fun lighten(color: Long): Long {
        val a = ((color shr 24) and 0xFF).toLong()
        val r = ((color shr 16) and 0xFF).toInt()
        val g = ((color shr 8) and 0xFF).toInt()
        val b = (color and 0xFF).toInt()
        fun mix(c: Int, target: Int) = (c + (target - c) * 0.87).toInt().coerceIn(0, 255)
        return ((a.toInt() shl 24) or (mix(r, 247) shl 16) or (mix(g, 249) shl 8) or mix(b, 250)).toLong()
    }

    /**
     * 把 release 列表并进仓库。
     *
     * [includePrerelease] 来自「我的 → 默认包含预发布版本」开关。默认关：不勾时
     * 预发布既不参与「最新版」的判定，也不进适配产物的候选集。
     * 选取规则见 [ReleasePick]。
     */
    fun applyReleases(
        repo: Repo,
        releases: List<ReleaseDto>,
        includePrerelease: Boolean = false,
    ): Repo {
        val stable = ReleasePick.visible(releases, includePrerelease)

        // 正式版一条都取不到、而预发布是有东西的：宁可把预发布摆出来（并标清楚），
        // 也不要显示「这个仓库没有产物」—— 用户看到那句话会以为得自己发包，
        // 而实际上点开 GitHub 就是满屏的安装包。
        val shown = stable.ifEmpty { releases.filter { !it.draft } }
        val onlyPrerelease = stable.isEmpty() && shown.isNotEmpty()

        val latest = shown.firstOrNull()
        val assets = shown.flatMap { rel ->
            rel.assets.map { dto -> toAsset(dto, rel) }
        }
        // 更新日志保留**原始 Markdown**，不在这里拆行 —— 拆了渲染器就再也认不出来
        val notes = shown.take(RELEASES_IN_HISTORY).map { rel ->
            ReleaseNote(
                tag = rel.tagName,
                date = rel.date.ifBlank { repo.date },
                prerelease = rel.prerelease,
                body = rel.body.orEmpty(),
            )
        }

        return repo.copy(
            version = latest?.tagName ?: "—",
            date = latest?.date?.ifBlank { repo.date } ?: repo.date,
            history = notes,
            // 区别要分清：真的一个 release 都没有 vs 只发过预发布（开关打开就能用）
            hasRealRelease = shown.isNotEmpty(),
            prereleaseOnly = onlyPrerelease,
            releasesError = null,
            downloads = assets.sumOf { it.downloadCount },
        ).withAssets(assets, linkInstalled(repo))
    }

    /** 更新日志 tab 最多列这么多个版本，多了那一屏就没法看了 */
    private const val RELEASES_IN_HISTORY = 10

    fun toAsset(dto: AssetDto, rel: ReleaseDto): Asset {
        val device = Env.device
        val abi = inferAbi(dto.name)
        // 只有 APK / AAB 能装到本机，其它按目标平台判定
        val installable = dto.kind == "APK" || dto.kind == "AAB"
        val platform = detectPlatform(dto.name)

        val fit: FitState
        val reason: Explain?
        val inferred: Boolean

        when {
            dto.isChecksum || dto.kind == "SHA256" -> {
                fit = FitState.Checksum
                reason = null
                inferred = false
            }

            !installable -> {
                fit = FitState.Mismatch
                reason = if (platform != null) {
                    Explain(
                        R.string.reason_desktop_package,
                        listOf(platform, device.name, device.sdkLabel),
                    )
                } else {
                    Explain(R.string.reason_not_android, listOf(dto.kind))
                }
                inferred = false
            }

            abi == device.abi -> {
                fit = FitState.Match
                reason = Explain.of(R.string.reason_abi_inferred)
                inferred = true
            }

            abi == "universal" -> {
                fit = FitState.Degrade
                reason = Explain(R.string.reason_universal_only, listOf(device.abi))
                inferred = true
            }

            abi != null -> {
                fit = FitState.Mismatch
                reason = Explain(R.string.reason_abi_mismatch, listOf(device.abi, abi))
                inferred = true
            }

            // 文件名里没有 ABI，但**它确实是 APK/AAB**。
            //
            // 原来这里判 Unknown，于是 `pickBest` 挑不出它、`best` 为 null、
            // 主 CTA 退化成「打开原始安装包链接」—— 一个装得上的包被当成了装不上的。
            // 后果不只是文案难看：整个 App 对**所有没有 ABI 标记的仓库**都失效了，
            // 而那占了绝大多数（FitHub 自己的 release 就是这种）。
            //
            // 判 Degrade 而不是 Match，因为「文件名没写架构」和「确认是 universal」
            // 是两回事，前者要如实说（[reason_abi_unknown]），后者才敢说
            // 「没有单独分包、会多占体积」（[reason_universal_only]）。
            //
            // **这不算猜**：下载之后本来就会用 PackageManager 读真实清单，
            // 那套确认机制一直在（见 [com.heiyehk.fithub.data.FitEngine.withDownloadedFacts]
            // 与 Asset.realAbis）。原来的 Unknown 是在那次确认之前就把候选丢掉了。
            else -> {
                fit = FitState.Degrade
                reason = Explain.of(R.string.reason_abi_unknown)
                inferred = false
            }
        }

        return Asset(
            name = dto.name,
            kind = dto.kind,
            abi = if (installable) abi else null,
            sizeMb = (Math.round(dto.size / 1048576.0 * 10.0) / 10.0),
            sha = null,
            sdkLabel = null,
            fit = fit,
            reason = reason,
            downloadUrl = dto.browserDownloadUrl,
            contentType = dto.contentType,
            downloadCount = dto.downloadCount,
            publishedAt = rel.publishedAt?.take(10),
            tag = rel.tagName,
            prerelease = rel.prerelease,
            inferred = inferred,
        )
    }

    /** 从文件名认目标平台 —— 用于说明「这包是给谁用的」 */
    private fun detectPlatform(name: String): String? {
        val n = name.lowercase()
        return when {
            n.contains("windows") || n.contains("win64") || n.endsWith(".exe") -> "Windows"
            n.contains("macos") || n.contains("darwin") || n.contains("osx") || n.endsWith(".dmg") -> "macOS"
            n.contains("linux") || n.contains("gnu") || n.endsWith(".deb") || n.endsWith(".rpm") -> "Linux"
            else -> null
        }
    }

    /**
 * 挑「最该给用户的那一个」。
 *
 * 逐级降级：架构完全匹配 → 只是降级 → 没有架构信息但确实是 APK → 没有架构信息。
 * **每一档内部都先挑 release**，不只降级档。
 *
 * 为什么每一档都要：产物行的「完全匹配」是下载之后读真实清单得出的，
 * 所以一个仓库里 debug 包和 release 包**常常同时都是 Match**。
 * 之前只有降级档用了 [preferRelease]，匹配档还是 `firstOrNull` ——
 * 谁在列表里靠前谁赢，而 debug 常常就排在前面。
 *
 * 为什么 release 优先：debug 包通常带 debug 签名、还可能带 applicationId 后缀
 * （`com.foo.debug`），装了要么和正式版冲突、要么装成一个用不上的分身；
 * unsigned 更是根本装不上。这两个都不是「更省事的选择」，是**装不出想要结果的选择**。
 */
private fun pickBest(assets: List<Asset>): Asset? =
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

    /** 暴露给单测：[pickBest] 私有，但「同级里挑 release」这条规则必须钉住 */
    internal fun pickBestForTest(assets: List<Asset>): Asset? = pickBest(assets)

    fun verdictOf(assets: List<Asset>): Verdict {
        if (assets.isEmpty()) return Verdict.Unknown
        val best = pickBest(assets) ?: return Verdict.Unknown
        return if (best.fit == FitState.Match) Verdict.Ok else Verdict.Warn
    }

    /**
     * 本机是否装着这个仓库。判定统一交给 [FitEngine.deviceState]，
     * 依据 [LinkEngine] 写入的真实绑定；绑不上就是 [DeviceState.NotInstalled]。
     */
    private fun linkInstalled(repo: Repo): DeviceState = FitEngine.deviceState(repo)
}
