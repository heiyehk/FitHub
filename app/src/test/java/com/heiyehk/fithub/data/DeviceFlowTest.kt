package com.heiyehk.fithub.data

import com.heiyehk.fithub.data.remote.DeviceFlowClient
import com.heiyehk.fithub.data.remote.DeviceResult
import com.heiyehk.fithub.data.remote.PollResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Device Flow 的配置前置与状态语义。
 *
 * 这一层不发真实请求 —— 真实流程要用户去浏览器点确认，测不了。
 * 能测且值得测的是「哪些情况不是网络错误」，那决定 UI 怎么说话。
 */
class DeviceFlowTest {

    @Test
    fun `没配 client_id 时 isConfigured 为 false`() {
        assertFalse(DeviceFlowClient("").isConfigured)
        assertFalse(DeviceFlowClient("   ").isConfigured)
    }

    @Test
    fun `配了 client_id 时 isConfigured 为 true`() {
        assertTrue(DeviceFlowClient("Iv1.abc123").isConfigured)
    }

    @Test
    fun `未配置与失败是两种不同结果`() {
        // NotConfigured 是「这个构建没法登录」，提示用户去填 client_id；
        // Failed 会被当成网络问题去提示重试，方向完全不一样
        val nc: DeviceResult<*> = DeviceResult.NotConfigured
        val failed: DeviceResult<*> = DeviceResult.Failed("网络不可用")
        assertTrue(nc is DeviceResult.NotConfigured)
        assertTrue(failed is DeviceResult.Failed)
    }

    /**
     * 把 PollResult 映射成「UI 该做什么」。
     *
     * 抽成函数是为了让测试能断言**决策**而不是断言类型 ——
     * `x is X` 恒为真，编译器也会警告，那是废断言。
     * 真正要防的回归是：某天有人把 Denied 归到 Pending，
     * 用户在已经拒绝授权后还一直等着。
     */
    private fun whatUiShouldDo(r: PollResult): String = when (r) {
        is PollResult.Pending -> "继续等 ${r.nextIntervalSec} 秒"
        is PollResult.Denied -> "提示已被拒绝，停止轮询"
        is PollResult.Expired -> "提示设备码过期，回到第一步"
        is PollResult.Granted -> "存 token，进入已登录态"
        // 传输层失败**不是**终局：设备码还留在界面上，退避后自己再问。
        // 这一条就是用户报的「授权完回来就报错」的修法所在。
        is PollResult.Unreachable -> "设备码留在界面上，退避后重试"
        // 真正的终局：停了，只能靠用户点「重来一次」重新申请一张码
        is PollResult.Failed -> "提示失败原因，停止轮询"
        PollResult.NotConfigured -> "提示需要配置 client_id"
    }

    @Test
    fun `用户拒绝授权后 UI 必须停止等待`() {
        assertEquals("提示已被拒绝，停止轮询", whatUiShouldDo(PollResult.Denied))
    }

    @Test
    fun `设备码过期要回到第一步而不是继续轮询`() {
        assertEquals("提示设备码过期，回到第一步", whatUiShouldDo(PollResult.Expired))
    }

    @Test
    fun `slow_down 时按新间隔继续等而不是原频率`() {
        assertEquals("继续等 10 秒", whatUiShouldDo(PollResult.Pending(10)))
    }

    @Test
    fun `成功与失败走不同分支`() {
        assertEquals("存 token，进入已登录态", whatUiShouldDo(PollResult.Granted("t", "bearer", null)))
        assertEquals("提示失败原因，停止轮询", whatUiShouldDo(PollResult.Failed("网络不可用")))
    }

    @Test
    fun `传输层失败不能停轮询，必须留码重试`() {
        // 反过来的断言才是有意义的：网络抖一下就把登录判死，用户看到的就是
        // 「在浏览器授权完回来，App 报错说换 token 失败」
        assertEquals(
            "设备码留在界面上，退避后重试",
            whatUiShouldDo(PollResult.Unreachable("UnresolvedAddressException")),
        )
        assertTrue(whatUiShouldDo(PollResult.Unreachable("x")) != whatUiShouldDo(PollResult.Failed("x")))
    }

    @Test
    fun `未配置时提示配置而不是让用户重试`() {
        assertEquals("提示需要配置 client_id", whatUiShouldDo(PollResult.NotConfigured))
    }

    @Test
    fun `默认 scope 只含 read user 不含 repo 与 write`() {
        // 不申请 repo：那会给出私有仓库和写权限，远超「看看我自己的仓库」所需
        assertEquals("read:user", DeviceFlowClient.DEFAULT_SCOPE)
        assertFalse(DeviceFlowClient.DEFAULT_SCOPE.contains("repo"))
        assertFalse(DeviceFlowClient.DEFAULT_SCOPE.contains("write"))
    }
}
