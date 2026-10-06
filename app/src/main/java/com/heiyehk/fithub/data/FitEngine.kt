package com.heiyehk.fithub.data

import com.heiyehk.fithub.R

/**
 * 适配推荐引擎。
 *
 * 结论要能解释原因：解析不了就明说解析不了，不做推测。
 *
 * 这一层不拼文案，只产出 [Explain]（资源 ID + 参数）。原因有两个：
 * data/ 拿不到 `Context`，而且 [verdictExplain] 之类的纯函数必须能跑在 JVM
 * 单元测试上 —— 一旦依赖 Android 资源，这层最重要的测试就没法写了。
 */
object FitEngine {

    /**
     * 绑定变化后重算本机状态。
     *
     * `device` 是构造参数里的派生字段，copy() 不会自动重算，
     * 绑定写进 [Env.device] 之后需要显式调用一次。
     */
    fun withDeviceState(repo: Repo): Repo = repo.copy(device = deviceState(repo))

    /** 本机已装应用与最新 release 的关系（按 [DeviceProfile.installed] 里已确认的绑定算） */
    fun deviceState(repo: Repo): DeviceState {
        val inst = Env.device.installed.firstOrNull { it.repoId == repo.id }
            ?: return DeviceState.NotInstalled

        if (inst.signing == SigningRelation.Different) {
            return DeviceState.SigningConflict(
                installed = inst.version,
                detail = Explain(
                    R.string.conflict_store_signed,
                    listOf(inst.version, ResArg(repo.dist.signed.labelRes)),
                ),
            )
        }
        // 与 deviceStateFor 相同：远端 APK 未解析时 versionCode 为 0，表示未知
        if (repo.versionCode <= 0) {
            return DeviceState.VersionUnknown(
                installed = inst.version,
                released = repo.version.takeIf { it.isNotBlank() && it != "—" },
            )
        }
        return when {
            inst.versionCode < repo.versionCode -> DeviceState.Upgrade(inst.version, repo.version)
            else -> DeviceState.Latest(Explain(R.string.latest_plain, listOf(inst.version)))
        }
    }

    /**
     * 拿本机已扫描到的包与最新 release 比对。
     *
     * 报签名冲突要求两侧证书指纹都已读取。缺远端指纹时只给版本结论，不声明签名。
     */
    fun deviceStateFor(app: ScannedApp, repo: Repo): DeviceState {
        val remote = repo.assets.firstOrNull { it.parsed && !it.realSignerSha.isNullOrBlank() }
        val localFp = app.signerSha256
        val remoteFp = remote?.realSignerSha

        if (localFp != null && remoteFp != null && !sameCertificate(localFp, remoteFp)) {
            return DeviceState.SigningConflict(
                installed = app.versionName,
                detail = Explain(
                    R.string.conflict_cert_mismatch,
                    listOf(
                        app.label,
                        localFp.take(8),
                        repo.name,
                        remote?.name.orEmpty(),
                        remoteFp.take(8),
                    ),
                ),
            )
        }

        // 远端 APK 未解析时 versionCode 恒为 0，表示未知，不能参与大小比较，
        // 否则会把「本机比 Release 还新」当成结论。
        if (repo.versionCode <= 0) {
            return DeviceState.VersionUnknown(
                installed = app.versionName,
                released = repo.version.takeIf { it.isNotBlank() && it != "—" },
            )
        }

        return when {
            app.versionCode < repo.versionCode -> DeviceState.Upgrade(app.versionName, repo.version)
            app.versionCode > repo.versionCode -> DeviceState.Latest(
                Explain(
                    R.string.latest_newer_local,
                    listOf(app.versionName),
                )
            )
            else -> DeviceState.Latest(Explain(R.string.latest_plain, listOf(app.versionName)))
        }
    }

    /** 比对签名证书指纹：两边都取前 8 位十六进制比对 */
    private fun sameCertificate(a: String, b: String): Boolean {
        val x = a.replace(":", "").lowercase().take(8)
        val y = b.replace(":", "").lowercase().take(8)
        return x.isNotEmpty() && x == y
    }

    /**
     * 适配结论的一句话解释 —— 详情页顶部那张卡。
     *
     * 统一用 `**` 标加粗，由 UI 层的 `boldMarkup()` 渲染。不要在这里用 `<b>`：
     * Android 资源里 `<` 需要转义，翻译很容易漏。
     */
    fun verdictExplain(repo: Repo): Explain {
        val device = Env.device
        val d = repo.dist

        // 真实 GitHub 数据：没解析过就按未解析处理
        if (repo.source == DataSource.GitHub) {
            val best = repo.best
            val apkCount = repo.assets.count { it.kind == "APK" || it.kind == "AAB" }
            return when {
                repo.releasesError != null ->
                    Explain(R.string.explain_releases_failed, listOf(repo.releasesError))

                !repo.hasRealRelease -> Explain.of(R.string.explain_no_release)

                apkCount == 0 -> Explain(
                    R.string.explain_no_android_assets,
                    listOf(apkCount, device.name, device.sdkLabel),
                )

                best == null -> Explain.of(R.string.explain_no_matching)

                best.parsed -> Explain(
                    R.string.explain_parsed,
                    listOf(
                        best.realPackageName.orEmpty(),
                        best.realMinSdk ?: 0,
                        best.realTargetSdk ?: 0,
                        best.realAbis.joinToString(),
                        best.realSignerSha.orEmpty(),
                    ),
                )

                best.inferred -> Explain(
                    R.string.explain_inferred,
                    listOf(best.abi.orEmpty(), device.abi),
                )

                else -> best.reason ?: Explain.of(R.string.explain_not_installable)
            }
        }
        return when (repo.verdict) {
            Verdict.Ok -> if (d.signed == SignedBy.CiSelfSigned) {
                Explain(
                    R.string.explain_ok_ci,
                    listOf(device.abi, device.sdkLabel, d.minSdk, d.sdkLabel),
                )
            } else {
                Explain(
                    R.string.explain_ok,
                    listOf(
                        device.abi,
                        device.sdkLabel,
                        d.minSdk,
                        d.sdkLabel,
                        ResArg(d.signed.labelRes),
                    ),
                )
            }

            Verdict.Warn -> Explain(
                R.string.explain_warn,
                listOf(device.abi, (d.sizeMb * 0.42).toInt()),
            )

            Verdict.Bad -> Explain(R.string.explain_bad, listOf(device.abi))

            Verdict.Unknown -> Explain.of(R.string.explain_unknown)
        }
    }

    /**
     * 「这个仓库只发过预发布」的补充说明，不是判定。
     *
     * 刻意**不**并进 [verdictExplain]：预发布的产物照样能装、ABI 适配结论也照样成立，
     * 把它们藏起来只会在仓库明明摆着安装包时让用户以为没有（v2rayNG 就是这样）。
     * 现在预发布的产物照常列出来，只在这里把「它们是预发布」这件事讲清楚。
     *
     * 不需要时返回 null，UI 直接不渲染这一条。
     */
    fun prereleaseNotice(repo: Repo): Explain? =
        if (repo.prereleaseOnly) Explain.of(R.string.explain_prerelease_only) else null
}
