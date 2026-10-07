package com.heiyehk.fithub.data.parse

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.heiyehk.fithub.R
import com.heiyehk.fithub.data.Asset
import com.heiyehk.fithub.data.Explain
import com.heiyehk.fithub.data.FitState
import com.heiyehk.fithub.data.Env
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipFile

/** 解析结果，字段均来自系统 API 和 APK 内部结构 */
data class ApkInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val signerSha256: String?,
    val abis: List<String>,
    val isDebuggable: Boolean,
    val splitNames: List<String>,
    val sizeBytes: Long,
)

/**
 * APK 解析。
 *
 * 清单走系统 API `PackageManager.getPackageArchiveInfo()`，零第三方依赖；
 * ABI / split 从 APK 的 zip 条目里读（APK 本身就是 zip）。
 *
 * **这里只读文件，不下载。** 下载是 [ApkParser.downloadForService] 的事，
 * 而且只有它一条路：以前这个类里还有一份自己的 `download`，于是「点解析」会
 * 把同一个包再下一遍到 `cacheDir/apk/`，和下载链路各存一份、互不认领。
 * 现在下载完成时读一次清单、把事实记进 [ApkLibrary]，
 * 界面用 [enrich] 合回产物 —— 解析不再是一个用户要单独点的动作。
 */
object ApkParser {

    @Suppress("DEPRECATION")
    fun parseArchive(context: Context, file: File): ApkInfo? {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES.toLong()
        val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags))
        } else {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, flags.toInt())
        } ?: return null

        val (abis, splits) = readZipStructure(file)

        return ApkInfo(
            packageName = pi.packageName ?: return null,
            versionName = pi.versionName ?: "—",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else pi.versionCode.toLong(),
            minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) pi.applicationInfo?.minSdkVersion ?: 0 else 0,
            targetSdk = pi.applicationInfo?.targetSdkVersion ?: 0,
            signerSha256 = pi.signerSha256(),
            abis = abis,
            isDebuggable = (pi.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0,
            splitNames = splits,
            sizeBytes = file.length(),
        )
    }

    /** APK 就是 zip：读 lib/<abi>/ 和 split_*.apk 条目，不用第三方库 */
    private fun readZipStructure(file: File): Pair<List<String>, List<String>> {
        val abis = linkedSetOf<String>()
        val splits = linkedSetOf<String>()
        runCatching {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement().name
                    when {
                        name.startsWith("lib/") -> {
                            val abi = name.removePrefix("lib/").substringBefore('/')
                            if (abi.isNotEmpty()) abis += abi
                        }

                        name.startsWith("split_") && name.endsWith(".apk") -> {
                            splits += name.removePrefix("split_").removeSuffix(".apk")
                        }

                        name == "AndroidManifest.xml" -> Unit
                    }
                }
            }
        }
        return abis.toList() to splits.toList()
    }

    /** 取签名证书 SHA-256 —— 和本机已装包比对，判断能不能覆盖安装 */
    @Suppress("DEPRECATION")
    private fun PackageInfo.signerSha256(): String? = try {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners
        } else {
            this.signatures
        }
        val first = signatures?.firstOrNull()
        if (first == null) {
            null
        } else {
            MessageDigest.getInstance("SHA-256")
                .digest(first.toByteArray())
                .joinToString("") { b -> "%02x".format(b) }
                .take(16)
        }
    } catch (e: Throwable) {
        null
    }

    /** 归一成设备 ABI：abi 超过 2 个按 universal 处理 */
    private fun abiOf(info: ApkInfo): String? = when {
        info.abis.isEmpty() -> null
        info.abis.contains(Env.device.abi) -> Env.device.abi
        info.abis.size > 2 -> "universal"
        else -> info.abis.first()
    }

    /**
     * 把一份**真实清单**合进 [asset]，并按真实值重算适配结论。
     *
     * 这是原来「解析」按钮唯一的作用。解析不再是独立动作 —— 下载完成时
     * `DownloadService` 已经读过一遍清单（见 `ApkLibrary`），界面把那份事实
     * 合回来即可，所以用户不需要「先下载、再点解析」两个动作。
     *
     * [inferred] 必须翻成 false：ABI 从这里开始是真的，不再是从文件名猜的，
     * 界面靠这个标记区分「推断」和「实测」，留着会一直误导。
     */
    fun enrich(asset: Asset, info: ApkInfo): Asset = asset.copy(
        parsed = true,
        inferred = false,
        parseError = null,
        realPackageName = info.packageName,
        realVersionName = info.versionName,
        realVersionCode = info.versionCode,
        realMinSdk = info.minSdk,
        realTargetSdk = info.targetSdk,
        realSignerSha = info.signerSha256,
        realAbis = info.abis,
        abi = abiOf(info),
        sdkLabel = "minSdk ${info.minSdk} · target ${info.targetSdk}",
        fit = judge(info, asset),
        reason = reasonOf(info, asset),
    )

    private fun judge(info: ApkInfo, asset: Asset): FitState {
        val device = Env.device
        if (info.minSdk > device.sdk) return FitState.Mismatch
        // 空 ABI 列表 = 这个包**根本没有原生库**，纯 Java/Kotlin 代码，在任何 ABI 上都跑得起来。
        //
        // 原先没有这一支，空列表落到下面的 else 被判成 Mismatch，后果是一整条连锁：
        // 纯 Java 应用（实测是 Markor，一个 Markdown 编辑器）被判「本机装不上」，
        // 还因为 `pickBest` 的四档只认 Match / Degrade 而被整个跳过 ——
        // `repo.best` 于是退到上一个 tag，主 CTA 转去推一个**更旧**的版本，
        // 而刚下好的那个变成一行点不动的死条目。
        // 界面上写出来的话也是自相矛盾的：「真实清单里只有（空），本机是 x86_64」。
        if (info.abis.isEmpty()) return FitState.Match
        return if (info.abis.contains(device.abi) || info.abis.size > 2) FitState.Match else FitState.Mismatch
    }

    private fun reasonOf(info: ApkInfo, asset: Asset): Explain? {
        val device = Env.device
        return when {
            info.minSdk > device.sdk ->
                Explain(
                    R.string.reason_min_sdk,
                    listOf(info.minSdk, device.sdk, device.sdkLabel),
                )

            // 和 [judge] 同一件事：没有原生库就没有 ABI 可对，空列表不构成不匹配。
            info.abis.isNotEmpty() &&
                !info.abis.contains(device.abi) && info.abis.size <= 2 ->
                Explain(
                    R.string.reason_parsed_abis,
                    listOf(info.abis.joinToString(), device.abi),
                )

            info.isDebuggable -> Explain.of(R.string.reason_debug_build)

            else -> null
        }
    }

    /**
     * 给下载服务用的下载：回调**字节数**而不是百分比。
     *
     * 百分比回调在两个场景下没法用：响应头没有 Content-Length 时分母是 0；通知栏
     * 想显示「已下 18.3 MB / 60.9 MB」和瞬时速度，光有 0..1 的比例算不出来。
     *
     * **真断点续传**：`.part` 非空就带 `Range: bytes=<size>-`。
     * - `206`：接着往 `.part` 后面写，总大小 = 已有长度 + 本次 Content-Length
     * - `200`：服务端**无视**了 Range（或者压根没有可续的那一段）。必须清空重写 ——
     *   append 会把两段首尾拼起来，那必然是个坏包
     * - `416`：本地 `.part` 比远端还大（换了版本、或上次被外部截断），删掉重来一次
     *
     * 落盘策略与 [download] 一致（.part 临时文件 + 原子 rename），中断不会留下半个
     * 文件被当成完整的用。**额外加一道长度检查**：读到的字节比 Content-Length 少
     * 时直接抛错、不 rename —— 否则一个截断的包会被当成完整包交给 SHA-256，
     * 而发布方没给权威校验和时它会被直接送去安装。
     */
    fun downloadForService(url: String, target: File, onBytes: (saved: Long, total: Long) -> Unit) {
        val part = File(target.parentFile, "${target.name}.part")
        if (!transfer(url, part, part.length(), onBytes)) {
            part.delete()
            if (!transfer(url, part, 0L, onBytes)) {
                throw IllegalStateException("下载失败 HTTP $HTTP_RANGE_NOT_SATISFIABLE")
            }
        }
        if (!part.renameTo(target)) {
            // 跨分区 / 目标已存在时 renameTo 会失败，退化成拷贝+删临时文件
            part.copyTo(target, overwrite = true)
            part.delete()
        }
    }

    /**
     * 往 [part] 里传一段。返回 false = 本地半截文件不该留（服务端回 416），
     * 调用方删掉从零重来；其他失败一律抛出去，让上层换镜像。
     */
    private fun transfer(
        url: String,
        part: File,
        resumeFrom: Long,
        onBytes: (saved: Long, total: Long) -> Unit,
    ): Boolean {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "FitHub")
            if (resumeFrom > 0L) setRequestProperty("Range", "bytes=$resumeFrom-")
        }
        try {
            val code = conn.responseCode
            if (code == HTTP_RANGE_NOT_SATISFIABLE) {
                // 没发过 Range 还收到 416 就没法解释了，按普通失败处理
                if (resumeFrom > 0L) return false
                throw IllegalStateException("下载失败 HTTP $code")
            }
            if (code !in 200..299) throw IllegalStateException("下载失败 HTTP $code")
            // 只有「真的回了 206」才敢 append。回 200 就是完整响应，从头覆盖写
            val appending = code == HTTP_PARTIAL && resumeFrom > 0L
            if (!appending && resumeFrom > 0L) part.delete()
            val base = if (appending) resumeFrom else 0L
            val remain = conn.contentLengthLong
            // 206 时 Content-Length 是**剩余**长度，得加上已有的
            val total = if (remain > 0L) base + remain else 0L
            var saved = base
            conn.inputStream.use { input ->
                val out = if (appending) FileOutputStream(part, true) else part.outputStream()
                out.use { os ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        os.write(buf, 0, read)
                        saved += read
                        // 回调里可能抛（暂停/取消就是靠抛来停的），use 会把流关好，
                        // `.part` 里已经写进去的字节也就留住了
                        onBytes(saved, total)
                    }
                }
            }
            if (total > 0L && saved < total) {
                throw IOException("下载中断：$saved / $total 字节")
            }
            return true
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 完整的 64 位小写十六进制摘要，用于和 Release 给的 SHA-256 比对。
     *
     * 不能复用 [sha256]：它 `take(12)` 截断过，那是给 UI 显示用的短指纹，
     * 拿它去校验等于只比了前 12 个字符。
     */
    fun sha256ForService(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 续传要用到的两个响应码。写死而不用 `HttpURLConnection` 的常量：那两个名字
     * 在不同 SDK 里的可读性时好时坏，自己定义一个含义明确的名字更省心。
     */
    private const val HTTP_PARTIAL = 206
    private const val HTTP_RANGE_NOT_SATISFIABLE = 416
}
