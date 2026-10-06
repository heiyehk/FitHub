package com.heiyehk.fithub.data

import android.content.Context
import android.util.Log

/**
 * GitHub token 的落盘。
 *
 * 加密由 [SecureStore] 完成，这里只管 GitHub 的键名与头格式。
 *
 * scope 是 `read:user`，能读到用户资料和仓库列表，所以必须加密 ——
 * 明文存等于把账号凭证放进备份和截图里。
 */
object TokenStore {

    private const val TAG = "TokenStore"
    private const val ALIAS = "fithub_github_token"
    private const val KEY = "token"

    private fun store(context: Context) = SecureStore(context, ALIAS)

    /** 读 token。没有或读不出来都返回 null，调用方按未登录处理 */
    fun load(context: Context): String? = store(context).get(KEY)

    fun save(context: Context, token: String) = store(context).put(KEY, token)

    fun clear(context: Context) = store(context).destroy()

    fun isLoggedIn(context: Context): Boolean = load(context) != null

    /** 给 Ktor 用的 `Authorization` 头值。GitHub 接受 `token` 与 `Bearer` 两种前缀 */
    fun headerValue(context: Context): String? = load(context)?.let { "Bearer $it" }

    /** 供「退出登录」用：清 token 但保留其它凭证 */
    fun logoutOnly(context: Context) {
        Log.i(TAG, "退出登录")
        store(context).remove(KEY)
    }
}
