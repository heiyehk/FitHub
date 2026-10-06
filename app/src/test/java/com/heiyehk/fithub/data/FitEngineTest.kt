package com.heiyehk.fithub.data

import com.heiyehk.fithub.R
import com.heiyehk.fithub.data.remote.AssetDto
import com.heiyehk.fithub.data.remote.GitHubMapper
import com.heiyehk.fithub.data.remote.ReleaseDto
import com.heiyehk.fithub.data.remote.RepoDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 真实数据路径的判定逻辑。
 *
 * 这里测的是 `GitHubMapper.toAsset` / `applyReleases`，线上真正会走的那条路。
 *
 * 为什么必须在 JVM 上测：
 * 手边唯一能跑的设备是 x86_64 模拟器，arm64 的分支在设备上跑不到。
 * 「arm64 包在 arm64 手机上判成完全匹配」只能靠这里证明，不能靠看截图点头。
 */
class FitEngineTest {

    // 测试夹具

    private fun device(abi: String, sdk: Int = 34) = DeviceProfile(
        name = "测试机 $abi",
        abi = abi,
        sdk = sdk,
        sdkLabel = "Android 14",
        installed = emptyList(),
        supportedAbis = listOf(abi),
    )

    private fun assetDto(name: String, sizeMb: Long = 20L) =
        AssetDto(name = name, size = sizeMb * 1048576L, downloadCount = 1234)

    private val release = ReleaseDto(tagName = "v1.0.0", publishedAt = "2026-10-01T00:00:00Z")

    /** 走真实路径：文件名 → Asset */
    private fun asset(name: String, sizeMb: Long = 20L): Asset =
        GitHubMapper.toAsset(assetDto(name, sizeMb), release)

    private fun repo(
        abis: List<String> = emptyList(),
        versionCode: Int = 0,
        assets: List<Asset> = emptyList(),
    ) = Repo(
        id = "owner/app", name = "App", owner = "owner", monogram = "AP", desc = "",
        lang = "Kotlin", langColor = 0L, langShare = emptyList(),
        stars = 0, forks = 0, watchers = 0, issues = 0,
        version = "1.0.0", versionCode = versionCode, date = "2026-10-04",
        topics = emptyList(), tileBg = 0L, tileFg = 0L,
        history = emptyList(),
        dist = Dist("apk", "App", abis, 24, "Android 7.0", SignedBy.Release, 20.0),
        assets = assets,
    )

    private fun scanned(versionName: String, versionCode: Long, signer: String? = null) = ScannedApp(
        packageName = "com.example.app",
        label = "App",
        versionName = versionName,
        versionCode = versionCode,
        systemApp = false,
        signerSha256 = signer,
    )

    // ABI 判定
    //
    // reason 现在是 Explain（资源 ID + 参数），所以断言「用了哪条资源」而不是
    // 「文案里有没有某个字」——后者会把测试焊死在具体措辞上，翻译一改就红。

    @Test
    fun `arm64 设备上 arm64 包判为完全匹配`() {
        Env.device = device("arm64-v8a")
        val a = asset("App-1.0.0-arm64-v8a.apk")
        assertEquals(FitState.Match, a.fit)
        // Match 仍然是「按文件名推断」，必须标明
        assertTrue("推断出来的 ABI 要写明", a.inferred)
        assertEquals(R.string.reason_abi_inferred, a.reason!!.res)
    }

    @Test
    fun `arm64 设备上 universal 包判为可降级并说明多占体积`() {
        Env.device = device("arm64-v8a")
        val a = asset("App-1.0.0-universal.apk")
        assertEquals(FitState.Degrade, a.fit)
        assertEquals(R.string.reason_universal_only, a.reason!!.res)
        assertTrue("降级原因要带上本机架构", a.reason!!.args.contains("arm64-v8a"))
    }

    @Test
    fun `arm64 设备上只有 32 位包判为不匹配并写明本机架构`() {
        Env.device = device("arm64-v8a")
        val a = asset("App-1.0.0-armeabi-v7a.apk")
        assertEquals(FitState.Mismatch, a.fit)
        assertEquals(R.string.reason_abi_mismatch, a.reason!!.res)
        assertTrue("原因里要写明本机架构", a.reason!!.args.contains("arm64-v8a"))
        assertTrue("原因里要写明包里有什么架构", a.reason!!.args.contains("armeabi-v7a"))
    }

    @Test
    fun `x86_64 设备上 arm64 包不能判为完全匹配`() {
        Env.device = device("x86_64")
        assertEquals(FitState.Mismatch, asset("App-1.0.0-arm64-v8a.apk").fit)
    }

    /**
     * 文件名里没有 ABI，但确实是 APK —— 判「可降级」而不是「未知」。
     *
     * 这条断言以前写的是 `FitState.Unknown`，理由是「不猜」。现在反过来：
     * 判 Unknown 会让 `pickBest` 挑不出它、`best` 成了 null、主 CTA 退化成
     * 「打开原始安装包链接」—— **一个装得上的包被当成装不上的**，而且是对
     * 所有没写 ABI 的仓库都失效（FitHub 自己的 release 就是这种）。
     *
     * 关键区别仍然要留着：**不能标 `inferred`**。文件名确实没给出架构，
     * 文案必须说「看不出 ABI」而不是「确认是 universal」，
     * 而且下载后仍然要靠 PackageManager 读真实清单来定论。
     */
    @Test
    fun `文件名里没有 ABI 的 APK 判为可降级并说明看不出架构`() {
        Env.device = device("arm64-v8a")
        val a = asset("App-1.0.0.apk")
        assertEquals(FitState.Degrade, a.fit)
        assertFalse("文件名没给出 ABI，就不能标 inferred", a.inferred)
        assertEquals("要说明看不出 ABI", R.string.reason_abi_unknown, a.reason!!.res)
    }

    /**
     * 和真·universal 包的文案必须分开。
     *
     * `App-1.0.0-universal.apk` 是作者**明说**一个包打天下，说「多占体积」成立；
     * `App-1.0.0.apk` 只是没写，说「多占体积」是在编。
     */
    @Test
    fun `没写 ABI 与写了 universal 用不同的文案`() {
        Env.device = device("arm64-v8a")
        assertEquals(R.string.reason_universal_only, asset("App-1.0.0-universal.apk").reason!!.res)
        assertEquals(R.string.reason_abi_unknown, asset("App-1.0.0.apk").reason!!.res)
    }

    /** 同级候选里优先 release：debug 包常带 debug 签名 / applicationId 后缀，装了会冲突 */
    @Test
    fun `没有架构信息时优先推荐 release 而不是 debug`() {
        Env.device = device("arm64-v8a")
        val best = GitHubMapper.pickBestForTest(
            listOf(
                asset("FitHub-1.0.0-debug.apk"),
                asset("FitHub-1.0.0-release.apk"),
            ),
        )
        assertEquals("FitHub-1.0.0-release.apk", best!!.name)
    }

    /** unsigned 装不上，哪怕名字里带 release 也不能被选中 */
    @Test
    fun `unsigned 产物即使带 release 字样也不被选中`() {
        Env.device = device("arm64-v8a")
        assertFalse(
            "app-release-unsigned 不算 release 产物",
            GitHubMapper.isReleaseBuild("App-1.0.0-release-unsigned.apk"),
        )
        assertTrue(GitHubMapper.isReleaseBuild("App-1.0.0-release.apk"))
    }

    /** 架构完全匹配的包仍然压过 release 偏好 —— release 只在同一档里起作用 */
    @Test
    fun `架构匹配的包仍然优先于 release 偏好`() {
        Env.device = device("arm64-v8a")
        val best = GitHubMapper.pickBestForTest(
            listOf(
                asset("App-1.0.0-release.apk"),
                asset("App-1.0.0-arm64-v8a.apk"),
            ),
        )
        assertEquals("App-1.0.0-arm64-v8a.apk", best!!.name)
    }

    /**
     * debug 和 release **同时都是架构匹配**时，必须选 release。
     *
     * 「完全匹配」是下载之后读真实清单判出来的，所以一个仓库里两个包常常同时 Match。
     * 之前只有降级档做了 release 偏好，匹配档还是 `firstOrNull` ——
     * 谁在列表里靠前谁赢，而 debug 常常就排在前面，于是点「下载」下到的是 debug 包：
     * 带 debug 签名、可能带 applicationId 后缀，装上也不是用户要的那个应用。
     */
    @Test
    fun `两个包都架构匹配时选 release 而不是 debug`() {
        Env.device = device("arm64-v8a")
        val debug = asset("App-1.0.0-debug.apk").copy(fit = FitState.Match)
        val release = asset("App-1.0.0-release.apk").copy(fit = FitState.Match)
        // debug 故意排在前面 —— 列表顺序是 GitHub 返回的，字母序 d < r
        val best = GitHubMapper.pickBestForTest(listOf(debug, release))
        assertEquals("App-1.0.0-release.apk", best!!.name)
    }

    @Test
    fun `macOS 桌面包判为装不上并写明本机`() {
        Env.device = device("arm64-v8a")
        val a = asset("scrcpy-macos-aarch64.dmg")
        assertEquals(FitState.Mismatch, a.fit)
        assertEquals(R.string.reason_desktop_package, a.reason!!.res)
        // 桌面包这条 reason 带的是本机机型名（不是 abi），别断言错对象
        assertTrue("原因里要写明本机", a.reason!!.args.contains(Env.device.name))
        assertTrue("原因里要写明本机 SDK", a.reason!!.args.contains(Env.device.sdkLabel))
    }

    @Test
    fun `SHA256SUMS 判为校验文件而不是安装包`() {
        Env.device = device("arm64-v8a")
        val a = asset("SHA256SUMS.txt")
        assertEquals(FitState.Checksum, a.fit)
        // 校验文件不该被当成能装的东西，就不该带 abi
        assertNull("非安装包不该带 abi", a.abi)
    }

    /**
     * 这些文件名是从真实 release 里抄的：
     * - termux/termux-app → `..._debug_sha256sums`（无扩展名）
     * - Genymobile/scrcpy  → `SHA256SUMS.txt`
     * - syncthing/syncthing → `sha256sum.txt.asc`
     *
     * 漏掉的话它们会被判成「不是安卓安装包，本机装不上」，
     * 那是对校验文件不成立的结论。
     */
    @Test
    fun `真实 release 里的校验文件名都要认出来`() {
        Env.device = device("arm64-v8a")
        val real = listOf(
            "termux-app_v0.119.0-github-debug_sha256sums",
            "SHA256SUMS.txt",
            "sha256sum.txt.asc",
            "sha1sum.txt.asc",
            "SHA256SUMS.txt.asc",
            "sha256sums",
            "checksums.txt",
            "app.sig",
        )
        for (name in real) {
            assertTrue("$name 没被认成校验文件", asset(name).kind == "SHA256")
            assertEquals("$name 被误判成安装包", FitState.Checksum, asset(name).fit)
        }
    }

    @Test
    fun `普通安装包不能被误认成校验文件`() {
        Env.device = device("arm64-v8a")
        val a = asset("App-1.0.0-arm64-v8a.apk")
        assertEquals("APK", a.kind)
        assertEquals(FitState.Match, a.fit)
    }

    @Test
    fun `AAB 也算安卓安装包`() {
        Env.device = device("arm64-v8a")
        assertEquals(FitState.Match, asset("App-1.0.0-arm64-v8a.aab").fit)
    }

    // 真实 release 映射

    @Test
    fun `applyReleases 拉出真实产物与版本`() {
        Env.device = device("arm64-v8a")
        val rel = ReleaseDto(
            tagName = "v0.29.1",
            publishedAt = "2026-10-01T00:00:00Z",
            assets = listOf(
                assetDto("NewPipe_v0.29.1.apk", 11_507_122 / 1048576),
                assetDto("NewPipe_v0.29.0.apk", 11_507_122 / 1048576),
            ),
        )
        val r = GitHubMapper.applyReleases(repo(), listOf(rel))
        assertEquals("v0.29.1", r.version)
        assertEquals(2, r.assets.size)
        assertTrue("有真实产物", r.hasRealRelease)
        assertNull("取到了就不该带错误", r.releasesError)
    }

    @Test
    fun `草稿 release 不计入`() {
        Env.device = device("arm64-v8a")
        val draft = ReleaseDto(
            tagName = "v9.9.9",
            draft = true,
            assets = listOf(assetDto("App-9.9.9-arm64-v8a.apk")),
        )
        val r = GitHubMapper.applyReleases(repo(), listOf(draft))
        assertFalse("草稿不算真实产物", r.hasRealRelease)
        assertTrue(r.assets.none { it.kind == "APK" })
    }

    // 版本结论

    @Test
    fun `远端 versionCode 未知时不得判定为已是最新`() {
        Env.device = device("arm64-v8a")
        val state = FitEngine.deviceStateFor(scanned("0.29.1", 1015), repo(versionCode = 0))
        assertTrue("versionCode 为 0 时必须是 VersionUnknown，实际是 $state", state is DeviceState.VersionUnknown)
    }

    @Test
    fun `本机 versionCode 更低时才判为可升级`() {
        Env.device = device("arm64-v8a")
        val state = FitEngine.deviceStateFor(scanned("1.5.0", 150), repo(versionCode = 182))
        assertTrue("实际是 $state", state is DeviceState.Upgrade)
        assertEquals("1.5.0", (state as DeviceState.Upgrade).from)
    }

    @Test
    fun `版本一致时判为已是最新`() {
        Env.device = device("arm64-v8a")
        assertTrue(FitEngine.deviceStateFor(scanned("1.0.0", 182), repo(versionCode = 182)) is DeviceState.Latest)
    }

    // 签名：只读到一侧就不许报冲突

    private fun parsedAsset(signerSha: String) = Asset(
        name = "App-1.0.0-arm64-v8a.apk", kind = "APK", abi = "arm64-v8a", sizeMb = 20.0,
        sha = null, sdkLabel = null, fit = FitState.Match, reason = null,
        parsed = true, realSignerSha = signerSha,
    )

    @Test
    fun `两侧证书指纹都读到且不同时才报签名冲突`() {
        Env.device = device("arm64-v8a")
        val state = FitEngine.deviceStateFor(
            scanned("1.0.0", 182, signer = "9988776655443322"),
            repo(versionCode = 182, assets = listOf(parsedAsset("aabbccdd11223344"))),
        )
        assertTrue("实际是 $state", state is DeviceState.SigningConflict)
    }

    @Test
    fun `只读到本机指纹时不得报签名冲突`() {
        Env.device = device("arm64-v8a")
        val state = FitEngine.deviceStateFor(
            scanned("1.0.0", 150, signer = "9988776655443322"),
            repo(versionCode = 182), // 没解析远端 APK
        )
        assertNotEquals(
            "只有一侧指纹就报冲突，等于拿猜测冒充事实",
            DeviceState.SigningConflict::class.java,
            state!!::class.java,
        )
        assertTrue("实际是 $state", state is DeviceState.Upgrade)
    }

    @Test
    fun `两侧指纹一致时不报冲突`() {
        Env.device = device("arm64-v8a")
        val state = FitEngine.deviceStateFor(
            scanned("1.0.0", 150, signer = "aabbccdd11223344"),
            repo(versionCode = 182, assets = listOf(parsedAsset("aabbccdd11223344"))),
        )
        assertTrue("实际是 $state", state is DeviceState.Upgrade)
    }

    // 关联：没绑就是没装

    @Test
    fun `没有绑定时判为未安装`() {
        Env.device = device("arm64-v8a")
        assertEquals(DeviceState.NotInstalled, FitEngine.deviceState(repo()))
    }

    // DTO 映射：snake_case 漏标不会报错

    @Test
    fun `RepoDto 的 snake_case 字段必须真的映射上`() {
        Env.device = device("arm64-v8a")
        val dto = RepoDto(
            fullName = "TeamNewPipe/NewPipe",
            name = "NewPipe",
            stargazersCount = 40_000,
            forksCount = 3_800,
            subscribersCount = 622,
            openIssuesCount = 1_500,
            pushedAt = "2026-10-01T12:00:00Z",
        )
        val r = GitHubMapper.toRepo(dto)
        // 这几个数一旦 @SerialName 漏标就会全变 0，而 UI 上看起来仍然正常
        assertEquals(40_000, r.stars)
        assertEquals(3_800, r.forks)
        assertEquals(622, r.watchers)
        assertEquals(1_500, r.issues)
        assertEquals("2026-10-01", r.date)
        assertEquals("TeamNewPipe/NewPipe", r.id)
    }

    @Test
    fun `AssetDto 的下载数与体积要真的映射上`() {
        Env.device = device("arm64-v8a")
        val dto = assetDto("NewPipe_v0.29.1.apk", 11L)
        val a = GitHubMapper.toAsset(dto, release)
        assertEquals(1234, a.downloadCount)
        assertEquals(11.0, a.sizeMb, 0.01)
        assertEquals("v1.0.0", a.tag)
        assertEquals("2026-10-01", a.publishedAt)
    }
}
