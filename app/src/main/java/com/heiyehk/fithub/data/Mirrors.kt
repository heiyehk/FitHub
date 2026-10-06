package com.heiyehk.fithub.data

import java.net.URI

/**
 * GitHub 下载镜像。
 *
 * 国内直连 `objects.githubusercontent.com` 经常卡住或超时，镜像前缀是常见解法。
 * 代价是**流量经过第三方**：镜像能看到你下的是哪个文件。安装包会被系统安装、
 * 拿到相应权限，所以默认关闭，由用户自己在「我的 → 下载镜像」里选。
 *
 * 格式上只要求「原 URL 前面拼一段前缀」，所以新增镜像不用改代码，
 * 改 [MIRRORS] 就行，或者走 [AppPrefs] 里的自定义前缀。
 */
data class DownloadMirror(
    /** 界面上怎么称呼它 */
    val id: String,
    /** 前缀，最终 URL = prefix + 原始 browser_download_url */
    val prefix: String,
) {
    /** 这些前缀是第三方服务，不是 GitHub 官方 */
    val isThirdParty: Boolean get() = !id.startsWith("direct")
}

object Mirrors {

    /** 直连。不加任何前缀 */
    val DIRECT = DownloadMirror("direct", "")

    /**
     * 用户给的几个 `gh-proxy.org` 端点。
     *
     * 域名相同、子域不同（v4/v6/cdn/axisnow），行为一致只是调度不同，
     * 放在下拉里是为了「一个不通换另一个」——它们本来就是同一套服务的不同入口。
     */
    private val MIRRORS = listOf(
        DownloadMirror("gh-proxy", "https://gh-proxy.org/"),
        DownloadMirror("gh-proxy-v4", "https://v4.gh-proxy.org/"),
        DownloadMirror("gh-proxy-v6", "https://v6.gh-proxy.org/"),
        DownloadMirror("gh-proxy-cdn", "https://cdn.gh-proxy.org/"),
        DownloadMirror("gh-proxy-axisnow", "https://axisnow.gh-proxy.org/"),
    )

    val ALL: List<DownloadMirror> = listOf(DIRECT) + MIRRORS

    val BY_ID: Map<String, DownloadMirror> = ALL.associateBy { it.id }

    /**
     * 给下载 URL 套上镜像前缀。
     *
     * 幂等：已经带前缀的原样返回 —— 否则重试换镜像时会套成
     * `https://gh-proxy.org/https://gh-proxy.org/...`。
     *
     * 代理地址坏掉时不能把包下坏，所以这里只做字符串拼接，
     * 真正的「这个镜像能不能用」交给 [next] 逐个回退去试。
     */
    fun apply(originalUrl: String, mirror: DownloadMirror): String {
        if (mirror.prefix.isEmpty()) return originalUrl
        if (isAlreadyPrefixed(originalUrl)) return originalUrl
        // 前缀必须是 http(s):// 开头才合法，否则拼出来的 URL 根本不可用
        if (!mirror.prefix.startsWith("http://") && !mirror.prefix.startsWith("https://")) {
            return originalUrl
        }
        return mirror.prefix.trimEnd('/') + "/" + originalUrl.removePrefix("/")
    }

    private fun isAlreadyPrefixed(url: String): Boolean =
        runCatching { URI(url).host }.getOrNull()?.contains("gh-proxy") == true

    /**
     * 下一个要试的镜像 —— 下载失败时调用。
     *
     * 从当前这个往后顺延，**到末尾就绕回开头**：镜像全挂了就该直连再试一次，
     * 而不是直接判定下载失败。回退顺序因此是
     * 直连 → 5 个镜像 → 直连 → 5 个镜像 …
     *
     * [onlyMirrors] 为 true 时池子里没有直连，走到末尾就是真的没有了，返回 null。
     */
    fun next(currentId: String, onlyMirrors: Boolean): DownloadMirror? {
        val pool = if (onlyMirrors) MIRRORS else ALL
        if (pool.isEmpty()) return null
        val idx = pool.indexOfFirst { it.id == currentId }
        if (idx < 0) return pool.first()
        // onlyMirrors 的池子里没有直连：绕回开头等于在「只用镜像」里塞进直连，
        // 和参数语义相反，所以那里必须返回 null 让上层如实报错
        return pool.getOrNull(idx + 1) ?: if (onlyMirrors) null else pool.first()
    }
}
