package com.heiyehk.fithub.data.remote

import android.util.Log
import com.heiyehk.fithub.R
import com.heiyehk.fithub.data.Explain
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.util.encodeBase64

/**
 * WebDAV 客户端，只做订阅同步需要的那几个动作。
 *
 * 不用 Ktor 的 dav 插件 —— 那是给通用 DAV 客户端用的，而这个 App 只读写
 * 一个已知路径下的单个文件，自己发四个动词更省依赖也更好排查。
 *
 * 认证一律 HTTP Basic。注意 WebDAV 服务商的密码习惯：
 * 坚果云要求用「应用密码」而不是账号密码，否则会被拒绝。
 */
class WebDavClient(
    private val config: WebDavConfig,
    private val http: HttpClient = defaultClient(),
) {

    /** 服务商的连通性只有这三种结果，别的一律算配置问题 */
    fun validate(): WebDavValidation = when {
        config.baseUrl.isBlank() -> WebDavValidation.NotConfigured(Explain.of(R.string.webdav_not_set_url))
        !config.baseUrl.startsWith("http") ->
            WebDavValidation.NotConfigured(Explain.of(R.string.webdav_url_scheme))

        config.username.isBlank() -> WebDavValidation.NotConfigured(Explain.of(R.string.webdav_not_set_user))
        config.password.isBlank() -> WebDavValidation.NotConfigured(Explain.of(R.string.webdav_not_set_pass))
        else -> WebDavValidation.Ok
    }

    private fun fileUrl(): String =
        config.baseUrl.trimEnd('/') + "/" + config.path.trim().trim('/')

    /**
     * 写一个文件。目录不存在时先建。
     *
     * 顺序是 PUT 前先 MKCOL：直接 PUT 到不存在的目录，很多服务端（坚果云就是）
     * 会返回 409 而不是替你建目录。
     */
    suspend fun put(relativePath: String, content: String): WebDavResult =
        guarded {
            ensureParentDir(relativePath)
            val resp = send(HttpMethod.Put, fileUrl() + "/" + relativePath.trimStart('/')) {
                contentType(ContentType.Application.Json)
                setBody(content)
            }
            if (resp.status.isSuccess() || resp.status == HttpStatusCode.Created) {
                WebDavResult.Ok()
            } else {
                WebDavResult.Failed(Explain(R.string.webdav_write_failed, listOf(resp.status.value)))
            }
        }

    /** 读一个文件。不存在返回 [WebDavResult.NotFound]，与「读失败」要区分 */
    suspend fun get(relativePath: String): WebDavResult = guarded {
        val resp = send(HttpMethod.Get, fileUrl() + "/" + relativePath.trimStart('/')) {}
        when {
            resp.status.isSuccess() -> WebDavResult.Ok(resp.bodyAsText())
            resp.status == HttpStatusCode.NotFound -> WebDavResult.NotFound
            resp.status == HttpStatusCode.Unauthorized ->
                WebDavResult.Failed(Explain.of(R.string.webdav_auth_failed))

            else -> WebDavResult.Failed(Explain(R.string.webdav_read_failed, listOf(resp.status.value)))
        }
    }

    /** 用 HEAD 探活。服务端不一定实现，404 也算「通了」 */
    suspend fun probe(): WebDavResult = guarded {
        val resp = send(HttpMethod.Head, fileUrl()) {}
        if (resp.status.isSuccess() || resp.status == HttpStatusCode.NotFound) {
            WebDavResult.Ok()
        } else if (resp.status == HttpStatusCode.Unauthorized) {
            WebDavResult.Failed(Explain.of(R.string.webdav_auth_failed_short))
        } else {
            WebDavResult.Failed(Explain(R.string.webdav_unreachable, listOf(resp.status.value)))
        }
    }

    /** 建目录。已存在或服务端不允许 MKCOL 都不算错 —— 有些服务端禁用该动词 */
    private suspend fun ensureParentDir(relativePath: String) {
        val segments = relativePath.trimStart('/').split('/')
        if (segments.size <= 1) return
        var acc = fileUrl()
        for (segment in segments.dropLast(1)) {
            acc += "/$segment"
            send(MKCOL, acc) {}
        }
    }

    private suspend fun guarded(block: suspend () -> WebDavResult): WebDavResult {
        when (val v = validate()) {
            is WebDavValidation.NotConfigured -> return WebDavResult.NotConfigured(v.reason)
            WebDavValidation.Ok -> Unit
        }
        return runCatching { block() }.getOrElse {
            Log.w(TAG, "WebDAV 请求异常", it)
            // 原文是 Ktor 抛的，不翻译也不改写：出问题时只有它能定位。
            // 拿不到 message 才退到我们自己那句「网络不可用」。
            val raw = it.message
            WebDavResult.Failed(
                if (raw == null) Explain.of(R.string.webdav_network_unavailable)
                else Explain(R.string.webdav_request_error, listOf(raw)),
            )
        }
    }

    private suspend fun send(
        method: HttpMethod,
        url: String,
        block: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ) = http.request(url) {
        this.method = method
        header(HttpHeaders.UserAgent, "FitHub")
        // Basic：base64(user:pass)。用户名密码里有中文时也必须是 UTF-8 编码后再 base64
        val creds = "${config.username}:${config.password}".toByteArray(Charsets.UTF_8).encodeBase64()
        header(HttpHeaders.Authorization, "Basic $creds")
        block()
    }

    companion object {
        private const val TAG = "WebDavClient"

        /**
         * `MKCOL` 是 WebDAV 私有方法，Ktor 的 [HttpMethod] 没收录。
         * 目录建不出来不该让整个上传失败 —— 有些服务端在父目录已存在时
         * 直接忽略或禁用该动词，所以调用方不检查它的返回值。
         */
        private val MKCOL = HttpMethod.parse("MKCOL")

        fun defaultClient() = HttpClient(CIO) {
            expectSuccess = false
            install(HttpTimeout) {
                // WebDAV 服务商响应普遍偏慢，比 GitHub API 宽松一些
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
        }
    }
}

sealed interface WebDavResult {
    /** [value] 是响应体；纯状态类操作用空串占位 */
    data class Ok(val value: String = "") : WebDavResult

    /** [reason] 会在同步页原样显示，所以是可本地化的 [Explain] 而不是 String */
    data class Failed(val reason: Explain) : WebDavResult

    /** 文件不存在。这是「还没同步过」而不是错误 */
    data object NotFound : WebDavResult

    data class NotConfigured(val reason: Explain) : WebDavResult
}

sealed interface WebDavValidation {
    data object Ok : WebDavValidation
    data class NotConfigured(val reason: Explain) : WebDavValidation
}
