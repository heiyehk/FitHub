package com.heiyehk.fithub.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** 真实扫描到的一条已装应用 */
@Serializable
data class ScannedApp(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val systemApp: Boolean,
    /** 签名证书 SHA-256 指纹，用来和 Release 包做冲突检测 */
    val signerSha256: String?,
)

/** 扫描结果统计 */
@Serializable
data class ScanResult(
    val total: Int,
    val userInstalled: Int,
    val system: Int,
    val apps: List<ScannedApp>,
    val elapsedMs: Long,
    /**
     * 扫描结果是不是完整的。
     *
     * Android 11+ 有包可见性过滤：没拿到 `QUERY_ALL_PACKAGES` 时，
     * `getInstalledPackages` 会静默地少返回一批应用，而且不报错。
     * 这时候 total 是个下限，UI 不能说成「共安装 N 个」。
     *
     * **默认值是 false 而不是 true**：这份数据现在会落盘（见 [ScanStore]），
     * 以后给 [ScanResult] 加字段时老用户盘上的文件会少几个键。缺字段必须能被
     * 读出来，否则用户一升级 App 本机页就空了。而「读不出来」时诚实的方向是
     * 「不确定完整」—— 宁可少说一句「共 N 个」，也不能把一个下限说成总数。
     */
    val complete: Boolean = false,
)

/**
 * 本机已装扫描。
 *
 * 这里跑的是真代码：分批读取 PackageManager，拿到 versionCode 与签名指纹。
 * 「按包名反查 GitHub 仓库」在 [LinkEngine]，需要服务端配合才能全自动，
 * 所以关联关系由绑定表提供。
 */
object ScanEngine {

    fun scan(context: Context): ScanResult {
        val start = System.currentTimeMillis()
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES.toLong()
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES.toLong()
        }

        val installed = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(flags.toInt())
            }
        }.getOrDefault(emptyList())

        val apps = installed.mapNotNull { it.toScanned(context) }
        val system = apps.count { it.systemApp }

        // 拿不到 QUERY_ALL_PACKAGES 时，上面的列表是被系统静默过滤过的
        val complete = context.checkSelfPermission("android.permission.QUERY_ALL_PACKAGES") ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

        return ScanResult(
            total = apps.size,
            userInstalled = apps.size - system,
            system = system,
            apps = apps.sortedWith(compareBy({ it.systemApp }, { it.label.lowercase() })),
            elapsedMs = System.currentTimeMillis() - start,
            complete = complete,
        )
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.toScanned(context: Context): ScannedApp? {
        val appInfo = applicationInfo ?: return null
        val pkg = packageName ?: return null
        return ScannedApp(
            packageName = pkg,
            label = runCatching { appInfo.loadLabel(context.packageManager).toString() }.getOrDefault(pkg),
            versionName = versionName ?: "—",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                longVersionCode
            } else {
                versionCode.toLong()
            },
            systemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            signerSha256 = signerSha256(),
        )
    }

    /** 取签名证书的 SHA-256 指纹 —— 与下载包比对，判断能不能覆盖安装 */
    private fun PackageInfo.signerSha256(): String? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            signatures
        }
        val first = signatures?.firstOrNull() ?: return null
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(first.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
    }
}

/**
 * 扫描结果的落盘。
 *
 * [ScanEngine.scan] 本身是纯计算，但它不便宜：`getInstalledPackages` 一次拉回全量
 * 列表，再**逐个**取签名证书算 SHA-256。装了两三百个包的用户每次冷启动都要走一遍，
 * 而结果在这中间根本不会变。
 *
 * 和 GitHub 缓存同一个判断：放 filesDir 而不是 cacheDir —— cacheDir 系统在存储
 * 紧张时直接清，而「本机装了哪些 App」正是这个 App 自己的立身之本，清掉等于把
 * 「本机」页变空。数据只有几百 KB 量级。
 *
 * 没有 TTL 概念：[read] 永远返回盘上那份（连同年龄），**要不要重扫由调用方决定**。
 * 这样「冷启动先用旧的顶上、后台再扫一遍」和「用户按了重新扫描就直接扫」
 * 能共用同一个入口，而不会把「什么时候该更新」这件事写死在存储层。
 */
@Serializable
data class ScanSnapshot(
    val result: ScanResult,
    /**
     * 落盘时间。
     *
     * 默认 0 = 「这份记录没有有效的时间」，[ScanStore.read] 会据此返回 null。
     * 宁可当成没有缓存（退回全量扫描），也不要拿一份不知道多旧的结果当现状 ——
     * 用户看到「本机共 0 个应用」会以为 App 坏了。
     */
    val at: Long = 0L,
)

object ScanStore {

    private const val KEY = "scan-result"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun store(context: Context) = LocalStore(context.filesDir, json)

    /** 读盘上那份和它的年龄。读不出来（首次运行 / 文件损坏 / 结构变了）返回 null。 */
    fun read(context: Context): Pair<ScanResult, Long>? {
        val snap = runCatching { store(context).read<ScanSnapshot>(KEY, ScanSnapshot(ScanResult(0, 0, 0, emptyList(), 0L, complete = false), 0L)) }
            .getOrNull() ?: return null
        if (snap.at <= 0L) return null
        return snap.result to (System.currentTimeMillis() - snap.at).coerceAtLeast(0L)
    }

    fun write(context: Context, result: ScanResult) {
        store(context).write(KEY, ScanSnapshot(result, System.currentTimeMillis()))
    }

    fun clear(context: Context) {
        store(context).remove(KEY)
    }
}
