package com.heiyehk.fithub.data.remote

import com.heiyehk.fithub.R
import com.heiyehk.fithub.data.Explain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.accept
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import java.io.IOException
import java.nio.channels.UnresolvedAddressException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * GitHub Device Flow。
 *
 * 选它而不是 Web + PKCE 的原因：Device Flow 是 GitHub 原生支持的，
 * **不需要回调 URI**，移动端不用配 intent-filter 也不用处理自定义 scheme。
 * 代价是用户要去浏览器输入一串设备码。
 *
 * 前提：必须先在 https://github.com/settings/developers 注册一个 OAuth App
 * 拿到 client_id。拿不到就整个流程不可用 —— [isConfigured] 会如实反映。
 */
class DeviceFlowClient(
    private val clientId: String,
    private val http: HttpClient = defaultClient(),
) {

    val isConfigured: Boolean get() = clientId.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 第一步：申请设备码。
     *
     * 用户拿 [DeviceCode.userCode] 去 [DeviceCode.verificationUri] 输入，
     * App 之后按 [DeviceCode.interval] 秒轮询第二步。
     */
    suspend fun requestCode(scope: String = DEFAULT_SCOPE): DeviceResult<DeviceCode> {
        if (!isConfigured) return DeviceResult.NotConfigured
        return try {
            val resp = http.submitForm(
                url = "$DEVICE_BASE/login/device/code",
                formParameters = Parameters.build {
                    append("client_id", clientId)
                    append("scope", scope)
                },
            ) {
                accept(ContentType.Application.Json)
                header(HttpHeaders.UserAgent, "FitHub")
            }
            val dto = json.decodeFromString(DeviceCodeDto.serializer(), resp.body<String>())
            DeviceResult.Ok(
                DeviceCode(
                    userCode = dto.userCode,
                    verificationUri = withSkipAccountPicker(dto.verificationUri),
                    expiresInSec = dto.expiresIn,
                    intervalSec = dto.interval.coerceAtLeast(1),
                    deviceCode = dto.deviceCode,
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            // 申请码这步同样不能把瞬时失败当终局：设备码 15 分钟才过期，
            // 一次 DNS 抖动 / 连接超时不该逼用户手动点「重来一次」。
            if (t.isTransientTransport()) DeviceResult.Unreachable(describe(t))
            else DeviceResult.Failed(describe(t))
        }
    }

    /**
     * 第二步：轮询换 token。
     *
     * `authorization_pending` 不是错误 —— 那就是「用户还没在浏览器里确认」，
     * 等一个 interval 再问一次。`slow_down` 是 GitHub 要求我们放慢节奏，
     * 按它给的秒数加长间隔，别自作主张固定重试。
     */
    suspend fun pollForToken(
        deviceCode: String,
        currentIntervalSec: Int,
    ): PollResult {
        if (!isConfigured) return PollResult.NotConfigured
        return try {
            val resp = http.submitForm(
                url = "$DEVICE_BASE/login/oauth/access_token",
                formParameters = Parameters.build {
                    append("client_id", clientId)
                    append("device_code", deviceCode)
                    append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                },
            ) {
                accept(ContentType.Application.Json)
                header(HttpHeaders.UserAgent, "FitHub")
                contentType(ContentType.Application.Json)
            }
            val dto = json.decodeFromString(TokenDto.serializer(), resp.body<String>())

            when {
                !dto.error.isNullOrBlank() -> when (dto.error) {
                    "authorization_pending" -> PollResult.Pending(currentIntervalSec)
                    "slow_down" -> PollResult.Pending(currentIntervalSec + 5)
                    "expired_token" -> PollResult.Expired
                    "access_denied" -> PollResult.Denied
                    // GitHub 的 wire error 原样传下去，UI 那边按原文判类别
                    else -> PollResult.Failed(dto.errorDescription ?: dto.error)
                }

                dto.accessToken.isNullOrBlank() -> PollResult.Failed(Explain.of(R.string.login_err_no_token))
                else -> PollResult.Granted(dto.accessToken, dto.tokenType ?: "bearer", dto.scope)
            }
        } catch (ce: CancellationException) {
            // 取消不是失败，必须原样抛出。
            //
            // 之前这里写的是 `runCatching { }.getOrElse { }` —— runCatching 捕获的是
            // **所有** Throwable，包括 CancellationException。协程被取消（页面离开、
            // 进程被杀、作用域关闭）时它会被当成「换取 token 失败」报给用户，
            // 而那一次失败根本没有发生。
            throw ce
        } catch (t: Throwable) {
            // 传输层抖一下就判死是这个 bug 的另一半：设备码在 GitHub 那边还有十几分钟
            // 才过期，中间 DNS 解析失败一次、连接被重置一次，登录就再也回不来了。
            // 所以能自己好的错误交给上层重试，只有「重试也没用」的那些才当场报出去。
            if (t.isTransientTransport()) PollResult.Unreachable(describe(t))
            else PollResult.Failed(describe(t))
        }
    }

    /**
     * 这个异常会不会自己好。
     *
     * 判据不是「看起来严不严重」，而是「下一轮还有没有可能成功」：
     * - DNS 解析不了、连接被重置、请求超时、GitHub 返 5xx —— 这四类下一轮都可能成，
     *   给 [PollResult.Unreachable] 让轮询循环退避重试。
     * - 4xx 和 JSON 解析失败重试一万次也一样，必须立刻报出去。
     *
     * 注意 [java.nio.channels.UnresolvedAddressException] 继承的是
     * `IllegalArgumentException` 而**不是** `IOException` —— 只按 IOException 抓的话，
     * 恰好会漏掉「DNS 解析不了」这个最常见的那一个。
     */
    private fun Throwable.isTransientTransport(): Boolean = when (this) {
        is UnresolvedAddressException -> true
        is IOException -> true
        is ServerResponseException -> true
        else -> false
    }

    /**
     * 把异常变成一句能看懂的话。
     *
     * 不少网络异常的 `getMessage()` 返回 null（`UnresolvedAddressException` 就是），
     * 只在 message 为空时补上类名 —— 宁可给一句生硬的原文，也不要给「未知错误」，
     * 因为 `login_err_raw` 会把原文附在后面，出问题时没人能查。
     */
    private fun describe(t: Throwable): Explain =
        Explain(R.string.login_err_raw, listOf(t.message?.takeIf { it.isNotBlank() } ?: t::class.java.simpleName))

    companion object {
        private const val DEVICE_BASE = "https://github.com"

        /** GitHub 设备授权页的「跳过选账号」参数 */
        const val SKIP_ACCOUNT_PICKER = "skip_account_picker"

        /**
         * 给授权页加上 `skip_account_picker=true`。
         *
         * 不加的话，浏览器明明已经登录了 GitHub 还会再弹一次「选账号」——
         * 手机上通常存着多个账号的 cookie，最容易在这一步挑错号，或者干脆被它挡住。
         *
         * 以 GitHub 返回的 `verification_uri` 为准再拼参数，而不是写死一个 URL：
         * GitHub Enterprise 的设备授权页在各自的域名下，写死会让 Enterprise 用不了。
         * 已经带了查询串就接 `&`，带了同名参数就原样返回 —— 免得拼出
         * `?a=1?skip_account_picker=true` 这种非法 URL。
         */
        internal fun withSkipAccountPicker(uri: String): String {
            if (uri.isBlank()) return uri
            if (uri.contains("$SKIP_ACCOUNT_PICKER=")) return uri
            val sep = if (uri.contains('?')) "&" else "?"
            return "$uri$sep$SKIP_ACCOUNT_PICKER=true"
        }

        /**
         * 只申请 `read:user`。
         *
         * 不申请 `repo`：那会给出私有仓库和写权限，远超「看看我自己的仓库」所需。
         * 不申请任何 write scope。
         */
        const val DEFAULT_SCOPE = "read:user"

        private fun defaultClient() = HttpClient(CIO) {
            expectSuccess = true
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
            }
        }
    }
}

/** 第一步拿到的设备码信息 */
data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSec: Long,
    val intervalSec: Int,
)

sealed interface DeviceResult<out T> {
    data class Ok<T>(val value: T) : DeviceResult<T>

    /**
     * 失败。
     *
     * [reason] 是可本地化的 [Explain]：我们自己写的兜底句给资源 ID，第三方原文
     * （Ktor 的英文异常、GitHub 的 wire error）当参数原样传下去。
     */
    data class Failed(val reason: Explain) : DeviceResult<Nothing> {
        /**
         * 第三方原文入口。
         *
         * 用 `login_err_raw` 包一层，语义是「这就是原文，不翻译」——
         * 与 [Explain] 直接给资源 ID 的那一支区分开。
         */
        constructor(raw: String) : this(Explain(R.string.login_err_raw, listOf(raw)))
    }

    /** 没配 client_id。这是配置问题，不是网络问题，UI 要分开说 */
    data object NotConfigured : DeviceResult<Nothing>

    /**
     * 传输层失败，**可以重试** —— 和 [PollResult.Unreachable] 同一个道理。
     *
     * 之前这里直接落到 [Failed]，于是「连不上 GitHub」和「GitHub 拒绝了这个
     * client_id」共用一条终局路径：前者要用户手动点重来，后者点了也没用。
     */
    data class Unreachable(val reason: Explain) : DeviceResult<Nothing> {
        constructor(raw: String) : this(Explain(R.string.login_err_raw, listOf(raw)))
    }
}

sealed interface PollResult {
    /** 还没确认，等 interval 秒再问 */
    data class Pending(val nextIntervalSec: Int) : PollResult

    data class Granted(val token: String, val tokenType: String, val scope: String?) : PollResult

    /** 设备码过期，要重新申请第一步 */
    data object Expired : PollResult

    /** 用户在 GitHub 那边点了拒绝 */
    data object Denied : PollResult

    data class Failed(val reason: Explain) : PollResult {
        /** 同 [DeviceResult.Failed]：第三方原文入口，不翻译 */
        constructor(raw: String) : this(Explain(R.string.login_err_raw, listOf(raw)))
    }

    /**
     * 传输层失败，**可以重试**。
     *
     * 和 [Failed] 分开就是这个 bug 的要害：GitHub 那边设备码还有十几分钟才过期，
     * 中间 DNS 抖一下、连接被重置一次、GitHub 返个 5xx，都只该等几秒再问一次，
     * 不该把整个登录判死。用户视角就是「切到浏览器授权完回来一看报错了」。
     */
    data class Unreachable(val reason: Explain) : PollResult {
        constructor(raw: String) : this(Explain(R.string.login_err_raw, listOf(raw)))
    }

    data object NotConfigured : PollResult
}

@Serializable
private data class DeviceCodeDto(
    @SerialName("device_code") val deviceCode: String = "",
    @SerialName("user_code") val userCode: String = "",
    @SerialName("verification_uri") val verificationUri: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0,
    val interval: Int = 5,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

@Serializable
private data class TokenDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)
