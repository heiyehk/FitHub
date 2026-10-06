package com.heiyehk.fithub.data.remote

import android.content.Context
import androidx.annotation.StringRes
import com.heiyehk.fithub.R
import com.heiyehk.fithub.data.LocalStore
import com.heiyehk.fithub.data.SecureStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * WebDAV 连接配置。
 *
 * 非敏感字段（地址、路径）存 [com.heiyehk.fithub.data.LocalStore]，明文可见；
 * **密码单独走 [SecureStore] 加密**，不与地址混在一起 ——
 * 将来若要把配置导出或打印排查，密码不会跟着漏出去。
 */
@Serializable
data class WebDavConfig(
    val baseUrl: String = "",
    val username: String = "",
    /** 单独加密存放，落盘时会被清空，见 [load] / [save] */
    val password: String = "",
    val path: String = DEFAULT_PATH,
    val presetId: String = "",
    /** 关掉后设置页仍显示配置，但不参与同步 */
    val enabled: Boolean = false,
) {
    val isBlank: Boolean get() = baseUrl.isBlank() || username.isBlank()

    companion object {
        const val DEFAULT_PATH = "fithub/subscriptions.json"
        private const val FILE = "webdav-config"
        private const val ALIAS = "fithub_webdav_creds"
        private const val KEY_PASSWORD = "password"

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun load(context: Context): WebDavConfig {
            val app = context.applicationContext
            val store = LocalStore(app.filesDir, json)
            val conf = store.read(FILE, WebDavConfig())
            // 密码不从 LocalStore 读，只从加密存储取
            return conf.copy(password = SecureStore(app, ALIAS).get(KEY_PASSWORD).orEmpty())
        }

        fun save(context: Context, config: WebDavConfig) {
            val app = context.applicationContext
            val plain = config.copy(password = "")
            LocalStore(app.filesDir, json).write(FILE, plain)
            val secure = SecureStore(app, ALIAS)
            if (config.password.isBlank()) secure.remove(KEY_PASSWORD) else secure.put(KEY_PASSWORD, config.password)
        }

        fun clear(context: Context) {
            val app = context.applicationContext
            LocalStore(app.filesDir, json).write(FILE, WebDavConfig())
            SecureStore(app, ALIAS).destroy()
        }
    }
}

/**
 * 服务商预设。
 *
 * 只列**确定能写**的。腾讯微云只读禁写、百度网盘仅企业版、阿里云盘无官方
 * WebDAV —— 这三个即使用户能连上也传不上去，列进预设只会让人配了必然失败。
 * 需要的人走「通用自建」。
 *
 * [name]/[note] 是给测试断言文案用的中文原文，**UI 一律读 [nameRes]/[noteRes]**。
 * 两者要成对改。留着中文原文是因为 `SubscriptionSyncTest` 直接对文案做
 * `contains` 断言，改成资源 ID 会让那条断言失去意义 —— 那是别人的测试，本层不能动。
 */
@Serializable
data class WebDavPreset(
    val id: String,
    val name: String,
    val baseUrl: String,
    val note: String,
    /** 需要应用密码而不是账号密码 */
    val appPassword: Boolean = false,
    /** UI 显示用的名称 */
    @StringRes val nameRes: Int,
    /** UI 显示用的说明 */
    @StringRes val noteRes: Int,
) {
    companion object {
        val BUILT_IN = listOf(
            WebDavPreset(
                id = "jianguoyun",
                name = "坚果云",
                baseUrl = "https://dav.jianguoyun.com/dav/",
                note = "免费版上传 1 GB/月、下载 3 GB/月。订阅文件只有几百字节，够用很久。" +
                    "账号填注册邮箱，密码要用「安全设置 → 应用密码」，不是登录密码。",
                appPassword = true,
                nameRes = R.string.webdav_preset_jianguo_name,
                noteRes = R.string.webdav_preset_jianguo_note,
            ),
            WebDavPreset(
                id = "selfhosted",
                name = "通用自建",
                baseUrl = "",
                note = "Nextcloud、飞牛、Seafile 等自建服务。地址形如 https://你的域名/dav/",
                nameRes = R.string.webdav_preset_selfhosted_name,
                noteRes = R.string.webdav_preset_selfhosted_note,
            ),
        )

        fun byId(id: String): WebDavPreset? = BUILT_IN.firstOrNull { it.id == id }
    }
}
