package com.heiyehk.fithub.data

import com.heiyehk.fithub.data.parse.ApkInfo
import com.heiyehk.fithub.data.parse.ApkParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [ApkParser.enrich] 里「按真实清单重算适配结论」的判据。
 *
 * 这些用例针对的是**实测踩到的那一类**：包名 / minSdk / ABI 都读出来了，
 * 结论却和事实相反，于是用户下完了才发现这东西「本机装不上」。
 */
class ApkAbiJudgeTest {

    private fun device(abi: String, sdk: Int = 34) = DeviceProfile(
        name = "test $abi",
        abi = abi,
        sdk = sdk,
        sdkLabel = "Android 14",
        installed = emptyList(),
        supportedAbis = listOf(abi),
    )

    /** 一条还没读过真实清单的产物 —— enrich 的入参 */
    private fun asset() = Asset(
        name = "App-1.0.0.apk",
        kind = "APK",
        abi = null,
        sizeMb = 20.0,
        sha = null,
        sdkLabel = null,
        fit = FitState.Degrade,
    )

    private fun info(
        abis: List<String>,
        minSdk: Int = 21,
    ) = ApkInfo(
        packageName = "com.example.app",
        versionName = "1.0.0",
        versionCode = 1,
        minSdk = minSdk,
        targetSdk = 34,
        signerSha256 = "a".repeat(64),
        abis = abis,
        isDebuggable = false,
        splitNames = emptyList(),
        sizeBytes = 1024L,
    )

    /**
     * 清单里**一个原生库都没有**的包 = 纯 Java / Kotlin 代码，在任何 ABI 上都跑得起来。
     *
     * 原来没有这一支：空列表既不含本机 ABI、个数也不超过 2，于是落进 `judge` 的
     * `else` 被判成 `Mismatch`。实测受害者是 Markor（纯 Java 的 Markdown 编辑器）：
     * 被判「本机装不上」→ 又因为 `pickBest` 的四档只认 Match / Degrade 而整个被跳过
     * → `repo.best` 退到上一个 tag → 主 CTA 改推一个**更旧**的版本，
     * 而刚下好的那个变成一行点不动的死条目。
     */
    @Test
    fun `不含任何原生库的包在本机是匹配的`() {
        Env.device = device("x86_64")
        val a = ApkParser.enrich(asset(), info(abis = emptyList()))
        assertEquals(FitState.Match, a.fit)
        assertNull(a.reason)
    }

    /** 空 ABI 也一样要算匹配 —— 平台无关的应用在 x86_64 模拟器上同样能装 */
    @Test
    fun `不含原生库的包在 arm64 设备上也是匹配的`() {
        Env.device = device("arm64-v8a")
        val a = ApkParser.enrich(asset(), info(abis = emptyList()))
        assertEquals(FitState.Match, a.fit)
    }

    /** minSdk 比本机还高时，不匹配仍然是唯一能盖过「无原生库」的结论 */
    @Test
    fun `无原生库但 minSdk 超了仍然判不匹配`() {
        Env.device = device("x86_64", sdk = 34)
        val a = ApkParser.enrich(asset(), info(abis = emptyList(), minSdk = 99))
        assertEquals(FitState.Mismatch, a.fit)
    }

    /** 单个 ABI 且不是本机 —— 真正的 ABI 不匹配，这条不能被上面的修复带偏 */
    @Test
    fun `单个不匹配的 ABI 仍然判不匹配`() {
        Env.device = device("x86_64")
        val a = ApkParser.enrich(asset(), info(abis = listOf("arm64-v8a")))
        assertEquals(FitState.Mismatch, a.fit)
    }

    /** 三个以上 ABI 一律当 universal 包，匹配 */
    @Test
    fun `多个 ABI 的包按 universal 处理`() {
        Env.device = device("x86_64")
        val a = ApkParser.enrich(asset(), info(abis = listOf("armeabi-v7a", "arm64-v8a", "x86_64")))
        assertEquals(FitState.Match, a.fit)
    }
}