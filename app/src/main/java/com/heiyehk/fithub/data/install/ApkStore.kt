package com.heiyehk.fithub.data.install

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * 安装包的落盘位置：**手机的公共下载目录** `Download/FitHub/`。
 *
 * 之前落在 `cacheDir/apk/`，有两个实打实的问题：系统随时可以清空 cache（用户下完
 * 没装，隔一天文件就没了），而且外部完全看不见 —— 文件管理器里没有这一项。
 *
 * ## 为什么 API 29+ 是 MediaStore 而不是直接 File
 *
 * 分区存储下 App 不能直接往公共目录建文件，必须先在 MediaStore 插一行拿到
 * `content://` Uri 才能写。**但反过来读是可以的**：我们自己经 MediaStore 建出来的
 * 文件，真实路径 `/storage/emulated/0/Download/FitHub/x.apk` 上
 * `PackageManager.getPackageArchiveInfo()` 和 `ZipFile` 都能正常工作（API 36 实测）。
 *
 * 这条性质是整套设计的地基 —— 它意味着解析和安装这两条链**一行都不用改成流式的**，
 * 仍然吃 `File`。反之若只拿到 content:// Uri，`getPackageArchiveInfo(content://…)`
 * 返回 null（实测），就只能整条重写。
 *
 * API 26–28 还没有分区存储（targetSdk 高也没用，那三个系统版本按老规矩来），
 * 直接写公共目录，但要 `WRITE_EXTERNAL_STORAGE` 运行时权限。
 *
 * ## 为什么 `.part` 仍然留在 cacheDir
 *
 * 续传靠的是「读 `.part` 现在的长度，然后发 `Range: bytes=N-`」，而且换镜像时必须
 * **删掉**半截文件。半截文件放在公共目录里有两个问题：MediaStore 会给未提交的
 * `IS_PENDING=1` 行按 MIME 改名字（实测 `x.bin` 变成 `x.bin.apk`），以及用户中止
 * 下载后公共目录会留下一堆看不见也清不掉的僵尸行。所以中途文件留在私有 cache，
 * **只有成品**才发布到公共目录 —— 多一次顺序拷贝（50 MB 约 1–3 秒），换来续传
 * 逻辑一行不动、用户的下载目录干净。
 */
object ApkStore {

    private const val TAG = "FitHubApkStore"

    /** 公共下载目录下的子目录名。留一层子目录是为了不和用户自己下的东西混在一起。 */
    const val SUBDIR = "FitHub"

    private const val APK_MIME = "application/vnd.android.package-archive"

    /** 已发布到公共目录的一个安装包。 [uri] 在 API 29+ 才有值。 */
    data class Published(
        val file: File,
        val uri: String,
        val sizeBytes: Long,
    )

    /** 分区存储分界线：这一版起必须走 MediaStore。 */
    private fun useMediaStore(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * API 26–28 写公共目录要的运行时权限。
     *
     * API 29+ 这条权限被系统忽略，所以 manifest 上要配 `maxSdkVersion=28`，
     * 这里也只在真的需要它的版本上问用户要。
     */
    fun needsLegacyWritePermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** 公共下载目录本体，含我们的子目录。用于建目录和拼真实路径。 */
    fun publicDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SUBDIR)

    /**
     * 把 [source] 发布到公共下载目录，返回落好盘的位置。
     *
     * 同名文件**先删掉再插**：MediaStore 允许重名行存在（实测同名插入拿到的是另一个
     * Uri，不去重就会在用户目录里堆出一串 `x.apk`、`x (1).apk`），而我们靠名字定位，
     * 留着旧的只会让「重新下载」变成找不到文件。
     */
    fun publish(context: Context, source: File, displayName: String): Published {
        require(source.exists() && source.length() > 0L) { "源文件不存在: $source" }
        val safeName = sanitize(displayName)
        val dir = publicDir()
        if (dir.exists() && !dir.canWrite()) {
            // 常见于用户插了 SD 卡并把下载目录挪走；报清楚，别让上层只看到一个
            // 后面含义不明的 IO 异常
            throw IllegalStateException("下载目录不可写: ${dir.absolutePath}")
        }
        if (!useMediaStore()) {
            if (!dir.exists()) dir.mkdirs()
            val target = File(dir, safeName)
            source.copyTo(target, overwrite = true)
            return Published(target, "", target.length())
        }

        remove(context, safeName)

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safeName)
            put(MediaStore.Downloads.MIME_TYPE, mimeOf(safeName))
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR/")
            // 必须先 PENDING：IS_PENDING=1 的行用户看不到，写完提交才出现，
            // 免得用户看着一个几百 KB 的半成品躺在下载目录里
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver
            .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore 拒绝写入下载目录")
        try {
            context.contentResolver.openOutputStream(uri, "w")!!.use { out ->
                source.inputStream().use { it.copyTo(out) }
            }
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (e: Throwable) {
            // 提交失败就不能留一行僵尸在用户目录里。这里吞掉原始异常是因为
            // 上层拿到的是一个已经清理干净的现场，删不掉行的话错误会很难读
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw e
        }

        // 拿回**系统实际用的名字**再拼路径：MediaStore 会按 MIME_TYPE 补/改扩展名
        // （实测 DISPLAY_NAME=x.bin + APK MIME → 落盘 x.bin.apk），不查一次就会
        // 拿着我们以为的名字去找一个不存在的文件
        val actual = queryOne(context, uri)
        val file = File(publicDir(), actual.name)
        return Published(file, uri.toString(), actual.size)
    }

    /**
     * 查这个名字在公共目录里是不是已经有成品了。
     *
     * 「重新下载 / 复用已下载」全靠它。必须按**实际落盘名**找，因为发布时
     * MediaStore 可能改过名字。
     */
    fun existing(context: Context, displayName: String): Published? {
        val safeName = sanitize(displayName)
        if (!useMediaStore()) {
            val f = File(publicDir(), safeName)
            return if (f.exists() && f.length() > 0L) Published(f, "", f.length()) else null
        }
        val hit = context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
            ),
            "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf("${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR/", safeName),
            null,
        )?.use { c ->
            if (!c.moveToFirst()) return null
            Row(
                id = c.getLong(0),
                name = c.getString(1) ?: return null,
                size = c.getLong(2),
            )
        } ?: return null

        val file = File(publicDir(), hit.name)
        // 行还在但文件没了（用户手动删过 / 被清理工具干掉）：这种行必须当没有，
        // 否则上层会拿着一个不存在的 File 去装，失败信息还很难懂
        if (!file.exists() || file.length() <= 0L) {
            remove(context, safeName)
            return null
        }
        val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, hit.id)
        return Published(file, uri.toString(), hit.size)
    }

    /** 删掉公共目录里的成品。找不到就算了 —— 清理路径不该自己变成失败源。 */
    fun remove(context: Context, displayName: String) {
        val safeName = sanitize(displayName)
        runCatching {
            if (!useMediaStore()) {
                File(publicDir(), safeName).delete()
            } else {
                context.contentResolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.DISPLAY_NAME} = ?",
                    arrayOf("${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR/", safeName),
                )
            }
        }.onFailure { Log.w(TAG, "删除失败 name=$safeName", it) }
    }

    private data class Row(val id: Long, val name: String, val size: Long)

    private fun queryOne(context: Context, uri: Uri): Row =
        context.contentResolver.query(
            uri,
            arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
            ),
            null,
            null,
            null,
        )?.use { c ->
            if (c.moveToFirst()) {
                Row(c.getLong(0), c.getString(1).orEmpty(), c.getLong(2))
            } else {
                null
            }
        } ?: throw IllegalStateException("刚插入的行查不回来: $uri")

    /**
     * 资产名来自 GitHub Releases，可能带 `/`、空格和中文。
     *
     * `/` 会让 `File(dir, name)` 跑到子目录里去（那层目录并不存在，mkdirs 也没建），
     * 落盘直接失败；中文名在部分 ROM 上会变成乱码。只保留文件名里真正需要的字符。
     */
    fun sanitize(raw: String): String {
        val base = raw.substringAfterLast('/').trim().ifBlank { "package.apk" }
        return base.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
    }

    /**
     * 按扩展名给 MIME。给不出就当二进制。
     *
     * 固定写 APK 的 MIME 会被系统按 APK 扩展名**改正文件名**（实测 `x.bin` → `x.bin.apk`），
     * 而 FitHub 下载的不只有 APK：Release 上的 `checksums.txt`、`.sha256` 同样走这条
     * 落盘路径，被改名之后清单里存的名字和盘上的就对不上了。
     */
    private fun mimeOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isBlank()) return "application/octet-stream"
        return when (ext) {
            "apk" -> APK_MIME
            else -> android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(ext)
                ?: "application/octet-stream"
        }
    }
}
