package com.heiyehk.fithub.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 远程头像。GitHub 的 `avatar_url` 指向 Gravatar，所以拿到的就是普通图片。
 *
 * **不引 Coil / Glide**：整个 App 只有这一个地方要显示远程图，而
 * `build.gradle.kts` 里刻意没放图片库（注释写着「保持 release 包体积」）。
 * 为一张几十 KB 的图加一个依赖不划算，走「读字节 → 解码 → 内存 + 磁盘缓存」
 * 这条最短的路径就够了。
 *
 * 兜底不是空白框，而是 [AppTile] 的文字色块：没给头像、或者这次没拉到，
 * 界面仍然完整，而不是留一个让用户猜的洞。
 *
 * [loader] 由调用方注入（传 `api::avatar`）而不是这里自己建客户端 ——
 * 少一个 HttpClient，也免得两处各配一套超时。
 */
@Composable
fun RemoteAvatar(
    url: String,
    monogram: String,
    loader: suspend (String) -> ByteArray?,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    corner: Dp = size / 2,
    background: Long = 0xFFE9EDF7,
    foreground: Long = 0xFF2F3E77,
) {
    // 必须在组合期取，不能在 LaunchedEffect 的协程里读：
    // LocalContext.current 是 @Composable，协程里调它就绕过了重组。
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect
        AvatarCache.read(context, url)?.let {
            bitmap = it
            return@LaunchedEffect
        }
        val bytes = loader(url) ?: return@LaunchedEffect
        AvatarCache.store(context, url, bytes)
        // 解码必须离开主线程：decodeByteArray 一张几十 KB 的图足以让
        // 「我的」页首帧掉一帧，而这一屏本来就是打开就要看的。
        val decoded = withContext(Dispatchers.IO) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } ?: return@LaunchedEffect
        val image = decoded.asImageBitmap()
        AvatarCache.rememberBitmap(url, image)
        bitmap = image
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(RoundedCornerShape(corner)),
        )
    } else {
        AppTile(
            monogram = monogram,
            background = background,
            foreground = foreground,
            modifier = modifier,
            size = size,
            corner = corner,
        )
    }
}

/**
 * 头像缓存。
 *
 * 磁盘那份不是洁癖：「我的」页在冷启动就要渲染头像，没有磁盘缓存的话每次
 * 开 App 都得为一张图发一次请求，而登录用户的配额是有上限的。
 *
 * 放 **filesDir** 而不是 cacheDir：cacheDir 系统在存储紧张时会直接清掉、
 * 不需要用户确认，头像缓存随机失效就等于这个文件白写。头像一共就几十 KB，
 * 占不了多少地方，不值得为了省那点空间去赌系统不清。
 *
 * 文件名用 URL 的哈希而不是 URL 本身：地址末尾带 `?v=4`，不适合直接当文件名。
 */
private object AvatarCache {

    /** 头像这种资源天然只有个位数，满了整批清掉比维护 LRU 省事 */
    private const val MEMORY_LIMIT = 32

    private val memory = ConcurrentHashMap<String, ImageBitmap>()

    fun rememberBitmap(url: String, bitmap: ImageBitmap) {
        if (memory.size >= MEMORY_LIMIT) memory.clear()
        memory[url] = bitmap
    }

    suspend fun read(context: Context, url: String): ImageBitmap? {
        memory[url]?.let { return it }
        val bytes = withContext(Dispatchers.IO) {
            runCatching { file(context, url).takeIf { it.exists() }?.readBytes() }.getOrNull()
        } ?: return null
        val bmp = withContext(Dispatchers.IO) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } ?: return null
        return bmp.asImageBitmap().also { rememberBitmap(url, it) }
    }

    suspend fun store(context: Context, url: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            runCatching {
                file(context, url).apply {
                    parentFile?.mkdirs()
                    writeBytes(bytes)
                }
            }
        }
    }

    private fun file(context: Context, url: String) =
        File(File(context.filesDir, "avatars"), Integer.toHexString(url.hashCode()) + ".img")
}