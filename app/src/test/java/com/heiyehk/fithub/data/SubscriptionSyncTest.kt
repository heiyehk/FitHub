package com.heiyehk.fithub.data

import com.heiyehk.fithub.data.remote.WebDavConfig
import com.heiyehk.fithub.data.remote.WebDavPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WebDAV 同步的判定规则。
 *
 * 两条不能错的：
 * 1) 远端时间拿不到时按「本地较新」处理 —— 反过来会在多设备下覆盖掉别人的修改
 * 2) 任何失败都不许碰本地数据
 */
class SubscriptionSyncTest {

    private fun sub(fullName: String) = Subscription(
        fullName = fullName,
        addedAt = 1_700_000_000_000L,
        name = fullName.substringAfterLast('/'),
    )

    // ---- 导出时间判定 ----

    @Test
    fun `能读出导出时间`() {
        val json = SubscriptionTransfer.export(listOf(sub("a/b")), now = 1_700_000_000_000L)
        val at = SubscriptionTransfer.exportedAtOf(json)
        assertTrue("应能读出 exportedAt，实际 $at", !at.isNullOrBlank())
    }

    @Test
    fun `内容损坏时读不出时间而不是抛异常`() {
        assertNull(SubscriptionTransfer.exportedAtOf("不是 JSON{{{"))
    }

    @Test
    fun `空文件读不出时间`() {
        assertNull(SubscriptionTransfer.exportedAtOf(""))
    }

    @Test
    fun `缺 exportedAt 字段时读出 null`() {
        // 读不出时间必须走「本地较新」，不能抛 —— 抛了整次同步就废了
        val json = """{"schema":1,"items":[]}"""
        assertNull(SubscriptionTransfer.exportedAtOf(json))
    }

    @Test
    fun `时间可比：晚的更新`() {
        val early = SubscriptionTransfer.export(listOf(sub("a/b")), now = 1_700_000_000_000L)
        val late = SubscriptionTransfer.export(listOf(sub("c/d")), now = 1_800_000_000_000L)
        val a = SubscriptionTransfer.exportedAtOf(early)!!
        val b = SubscriptionTransfer.exportedAtOf(late)!!
        assertTrue("后导出的应更新", b > a)
    }

    // ---- provider 预设 ----

    @Test
    fun `预设里只有确定能写的服务商`() {
        val ids = WebDavPreset.BUILT_IN.map { it.id }
        assertTrue("坚果云应在内", ids.contains("jianguoyun"))
        assertTrue("应留一个自建入口", ids.contains("selfhosted"))
    }

    @Test
    fun `不支持的服务商不出现在任何界面文案里`() {
        // 之前这里会列一份「不支持」清单（腾讯/百度/阿里）。删掉了：
        // 一屏别人家产品的差评既没人看得到好处，也把「这是别人家的服务商」
        // 摆在用户面前像在替谁说话。预设里只留确实能写的。
        val ids = WebDavPreset.BUILT_IN.map { it.id }
        listOf("tencent", "weiyun", "baidu", "aliyun", "ali").forEach { bad ->
            assertFalse("不该有 $bad 预设", ids.contains(bad))
        }
    }

    @Test
    fun `坚果云标注了要用应用密码`() {
        val nutstore = WebDavPreset.byId("jianguoyun")
        assertTrue("坚果云必须提示用应用密码", nutstore?.appPassword == true)
        assertTrue(nutstore?.note?.contains("应用密码") == true)
    }

    @Test
    fun `坚果云地址是官方 dav 端点`() {
        assertEquals("https://dav.jianguoyun.com/dav/", WebDavPreset.byId("jianguoyun")?.baseUrl)
    }

    @Test
    fun `自建预设地址留空等用户填`() {
        assertEquals("", WebDavPreset.byId("selfhosted")?.baseUrl)
    }

    @Test
    fun `按 id 能取回预设 取不到时为 null`() {
        assertTrue(WebDavPreset.byId("jianguoyun") != null)
        assertNull(WebDavPreset.byId("不存在的服务商"))
    }

    // ---- 配置完整性 ----

    @Test
    fun `配置为空时不算已填`() {
        assertTrue(WebDavConfig().isBlank)
    }

    @Test
    fun `地址或账号缺一个都算未填`() {
        assertTrue(WebDavConfig(baseUrl = "https://dav.example.com/", username = "", password = "pw").isBlank)
        assertTrue(WebDavConfig(baseUrl = "", username = "me@example.com", password = "pw").isBlank)
        assertFalse(WebDavConfig(baseUrl = "https://dav.example.com/", username = "me@example.com", password = "pw").isBlank)
    }

    @Test
    fun `关掉开关时即使填全也不算启用`() {
        val off = WebDavConfig(
            baseUrl = "https://dav.example.com/",
            username = "me@example.com",
            password = "pw",
            enabled = false,
        )
        assertFalse(off.enabled)
    }
}
