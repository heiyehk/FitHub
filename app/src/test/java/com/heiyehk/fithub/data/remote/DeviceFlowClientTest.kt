package com.heiyehk.fithub.data.remote

import com.heiyehk.fithub.R
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketException
import java.nio.channels.UnresolvedAddressException
import kotlin.coroutines.cancellation.CancellationException

/**
 * 登录错误映射的回归测试。
 *
 * 背景：用户报「浏览器授权后返回 App 就出错」。实测（AVD + release 包）拿到的是
 * `java.nio.channels.UnresolvedAddressException`，它的 `getMessage()` 返回 null，
 * 于是旧代码 `runCatching { }.getOrElse { }` 走进了「换取 token 失败」这句兜底，
 * 而且是**终局**——设备码在 GitHub 那边还有十几分钟，用户却再也回不来了。
 *
 * 这里把每一类失败都钉死，尤其是两类最容易被混淆的：
 * - 传输层失败（DNS/连接/5xx）必须**可重试**，不能是终局
 * - 取消必须原样抛出，不能被当成任何一种失败
 */
class DeviceFlowClientTest {

    /**
     * 生产环境的 [DeviceFlowClient.defaultClient] 开了 `expectSuccess = true`，
     * 所以 5xx/4xx 会变成异常而不是响应。测试里必须复刻这一条，否则
     * 「5xx 可重试 / 4xx 终局」这两条断言测的根本不是同一条代码路径。
     */
    private fun client(engine: MockEngine) = HttpClient(engine) { expectSuccess = true }

    private fun MockRequestHandleScope.respondOkJson(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun flow(engine: MockEngine) = DeviceFlowClient(CLIENT_ID, client(engine))

    @Test
    fun `DNS 解析失败是可重试的，不是终局`() = runBlocking<Unit> {
        // 这就是让用户看到「换取 token 失败」的那个异常
        val f = flow(MockEngine { throw UnresolvedAddressException() })
        val r = f.pollForToken("dc", 5)
        assertTrue(
            "DNS 失败必须归入 Unreachable，实际是 $r",
            r is PollResult.Unreachable,
        )
    }

    @Test
    fun `DNS 失败的原文是异常类名，因为 message 是 null`() = runBlocking<Unit> {
        val f = flow(MockEngine { throw UnresolvedAddressException() })
        val r = f.pollForToken("dc", 5) as PollResult.Unreachable
        assertEquals(R.string.login_err_raw, r.reason.res)
        // getMessage() == null，不补类名的话 UI 那边就只剩一句「未知错误」，没人能查
        assertEquals(listOf("UnresolvedAddressException"), r.reason.args)
    }

    @Test
    fun `连接被重置是可重试的`() = runBlocking<Unit> {
        val f = flow(MockEngine { throw SocketException("Connection reset") })
        assertTrue(f.pollForToken("dc", 5) is PollResult.Unreachable)
    }

    @Test
    fun `任意 IOException 都是可重试的`() = runBlocking<Unit> {
        val f = flow(MockEngine { throw IOException("boom") })
        assertTrue(f.pollForToken("dc", 5) is PollResult.Unreachable)
    }

    @Test
    fun `GitHub 返 5xx 是可重试的`() = runBlocking<Unit> {
        val f = flow(MockEngine { respondError(HttpStatusCode.InternalServerError) })
        assertTrue(f.pollForToken("dc", 5) is PollResult.Unreachable)
    }

    @Test
    fun `4xx 是终局，重试也没用`() = runBlocking<Unit> {
        val f = flow(MockEngine { respondError(HttpStatusCode.BadRequest) })
        val r = f.pollForToken("dc", 5)
        assertTrue("4xx 必须当场失败，实际是 $r", r is PollResult.Failed)
    }

    @Test
    fun `用户拒绝是终局`() = runBlocking<Unit> {
        val f = flow(MockEngine { respondOkJson("""{"error":"access_denied"}""") })
        assertEquals(PollResult.Denied, f.pollForToken("dc", 5))
    }

    @Test
    fun `码过期是终局`() = runBlocking<Unit> {
        val f = flow(MockEngine { respondOkJson("""{"error":"expired_token"}""") })
        assertEquals(PollResult.Expired, f.pollForToken("dc", 5))
    }

    @Test
    fun `还没授权是 Pending，不算错误`() = runBlocking<Unit> {
        val f = flow(MockEngine { respondOkJson("""{"error":"authorization_pending"}""") })
        assertEquals(PollResult.Pending(5), f.pollForToken("dc", 5))
    }

    @Test
    fun `slow_down 按 GitHub 给的间隔加长，不是固定重试`() = runBlocking<Unit> {
        val f = flow(MockEngine { respondOkJson("""{"error":"slow_down"}""") })
        assertEquals(PollResult.Pending(10), f.pollForToken("dc", 5))
    }

    @Test
    fun `拿到 token 就是成功`() = runBlocking<Unit> {
        val f = flow(
            MockEngine {
                respondOkJson("""{"access_token":"gho_x","token_type":"bearer","scope":"read:user"}""")
            }
        )
        val r = f.pollForToken("dc", 5)
        assertTrue(r is PollResult.Granted)
        assertEquals("gho_x", (r as PollResult.Granted).token)
    }

    @Test
    fun `响应里没有 access_token 是终局`() = runBlocking<Unit> {
        val f = flow(MockEngine { respondOkJson("""{"token_type":"bearer"}""") })
        val r = f.pollForToken("dc", 5)
        assertTrue(r is PollResult.Failed)
        assertEquals(R.string.login_err_no_token, (r as PollResult.Failed).reason.res)
    }

    @Test
    fun `响应根本不是合法 JSON 是终局`() = runBlocking<Unit> {
        val f = flow(MockEngine { respondOkJson("<html>502 Bad Gateway</html>") })
        assertTrue(f.pollForToken("dc", 5) is PollResult.Failed)
    }

    @Test(expected = CancellationException::class)
    fun `取消必须原样抛出，不能被当成失败`() = runBlocking<Unit> {
        val f = flow(MockEngine { throw CancellationException("scope closed") })
        f.pollForToken("dc", 5)
    }

    @Test(expected = CancellationException::class)
    fun `申请设备码时取消同样原样抛出`() = runBlocking<Unit> {
        val f = flow(MockEngine { throw CancellationException("left screen") })
        f.requestCode()
    }

    @Test
    fun `申请设备码时 DNS 失败是可重试的，不是终局`() = runBlocking<Unit> {
        // 同 pollForToken：一次解析失败不该逼用户手动点「Start sign-in」
        val f = flow(MockEngine { throw UnresolvedAddressException() })
        val r = f.requestCode()
        assertTrue(
            "申请码的传输层失败必须可重试，实际是 $r",
            r is DeviceResult.Unreachable,
        )
        assertEquals(listOf("UnresolvedAddressException"), (r as DeviceResult.Unreachable).reason.args)
    }

    @Test
    fun `申请设备码时连接超时是可重试的`() = runBlocking<Unit> {
        val f = flow(MockEngine { throw SocketException("Connect timeout has expired") })
        assertTrue(f.requestCode() is DeviceResult.Unreachable)
    }

    @Test
    fun `申请设备码时响应不是 JSON 是终局`() = runBlocking<Unit> {
        // 解析失败重试一万次也一样 —— 这类不属于「自己会好」
        val f = flow(MockEngine { respondOkJson("<html>502</html>") })
        assertTrue(f.requestCode() is DeviceResult.Failed)
    }

    @Test
    fun `授权链接带上跳过选账号`() = runBlocking<Unit> {
        // 浏览器明明已经登录了 GitHub，还要再弹一次选账号；手机上多 cookie 最容易挑错号
        val f = flow(
            MockEngine {
                respondOkJson(
                    """{"device_code":"dc","user_code":"ABCD-1234","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}"""
                )
            }
        )
        val r = f.requestCode() as DeviceResult.Ok
        assertEquals(
            "https://github.com/login/device?skip_account_picker=true",
            r.value.verificationUri,
        )
    }

    @Test
    fun `Enterprise 的授权页也照样拼参数，不能写死域名`() = runBlocking<Unit> {
        val f = flow(
            MockEngine {
                respondOkJson(
                    """{"device_code":"dc","user_code":"A","verification_uri":"https://ghe.corp.com/login/device","expires_in":900}"""
                )
            }
        )
        val r = f.requestCode() as DeviceResult.Ok
        assertEquals(
            "https://ghe.corp.com/login/device?skip_account_picker=true",
            r.value.verificationUri,
        )
    }

    @Test
    fun `已经有查询串就接上而不是再开一个问号`() {
        assertEquals(
            "https://github.com/login/device?a=1&skip_account_picker=true",
            DeviceFlowClient.withSkipAccountPicker("https://github.com/login/device?a=1"),
        )
    }

    @Test
    fun `已经带了同名参数就别拼第二次`() {
        val u = "https://github.com/login/device?skip_account_picker=false"
        assertEquals(u, DeviceFlowClient.withSkipAccountPicker(u))
    }

    @Test
    fun `空链接原样返回，不拼出光秃秃的问号`() {
        assertEquals("", DeviceFlowClient.withSkipAccountPicker(""))
    }

    @Test
    fun `没配 client_id 时不联网`() = runBlocking<Unit> {
        val f = DeviceFlowClient("", client(MockEngine { error("不该发请求") }))
        assertEquals(PollResult.NotConfigured, f.pollForToken("dc", 5))
    }

    @Test
    fun `申请设备码成功时字段都对`() = runBlocking<Unit> {
        val f = flow(
            MockEngine {
                respondOkJson(
                    """{"device_code":"dc","user_code":"ABCD-1234","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}"""
                )
            }
        )
        val r = f.requestCode() as DeviceResult.Ok
        assertEquals("ABCD-1234", r.value.userCode)
        assertEquals(900L, r.value.expiresInSec)
    }

    @Test
    fun `申请设备码时传输层失败会带上原文`() = runBlocking<Unit> {
        val f = flow(MockEngine { throw SocketException("Connection reset") })
        // 这条断言以前是 `as DeviceResult.Failed`，现在改成 Unreachable：
        // 传输层失败是**可重试**的，不该和「GitHub 拒绝了这个 client_id」共用终局。
        // 原文照样要带出来，否则失败时没人能查。
        val r = f.requestCode() as DeviceResult.Unreachable
        assertEquals(R.string.login_err_raw, r.reason.res)
        assertEquals(listOf("Connection reset"), r.reason.args)
    }

    companion object {
        // 假的。真实 client_id 不进版本库 —— 见 app/build.gradle.kts 里 githubClientId
        // 那段注释：它虽不是秘密，但公开一个可现成复制的 OAuth 授权页目标没有好处。
        // 这里只需要「非空」：为空的那条路径由上面 `没配 client_id 时不联网` 覆盖。
        private const val CLIENT_ID = "test-client-id"
    }
}
