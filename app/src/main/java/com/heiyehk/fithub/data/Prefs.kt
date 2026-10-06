package com.heiyehk.fithub.data

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.heiyehk.fithub.R

/**
 * 用户偏好。
 *
 * 用 [android.content.SharedPreferences] 而不是 [LocalStore]：后者是整份 JSON 落盘，
 * 给它塞三个布尔开关意味着每改一次开关就重写整个文件，而且读的时候要反序列化。
 * 开关就该用开关该用的存储。
 *
 * 状态挂在 object 上而不是让调用方各自持有：`MainActivity` 要据此决定深浅色，
 * 改一次得立刻生效，散成几份 state 就会出现「设置页显示已切换、界面没变」。
 */
object Prefs {

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    const val KEY_THEME = "theme"
    const val KEY_INCLUDE_PRERELEASE = "includePrerelease"
    const val KEY_REQUIRE_SHA = "requireSha"
    const val KEY_LANG = "lang"
    const val KEY_MIRROR = "downloadMirror"
    const val KEY_MIRROR_FALLBACK = "downloadMirrorAutoFallback"

    data class Snapshot(
        /** [THEME_SYSTEM] / [THEME_LIGHT] / [THEME_DARK] */
        val theme: String = THEME_SYSTEM,
        /**
         * 默认关：预发布是给愿意尝鲜的人用的，混进「最新版」会让适配结论
         * 指向一个别人还没装的包。
         */
        val includePrerelease: Boolean = false,
        /**
         * 打开后，下载完成但拿不到权威校验和时**拒绝进入安装**。
         * 默认关：GitHub 的 Release 绝大多数不给产物提供 SHA-256，强制开会
         * 让大部分仓库都装不了 —— 所以它是一条「从严」的可选项，不是安全默认值。
         */
        val requireSha: Boolean = false,
    ) {
        val darkThemeOrNull: Boolean?
            get() = when (theme) {
                THEME_DARK -> true
                THEME_LIGHT -> false
                else -> null
            }

        @get:androidx.annotation.StringRes
        val themeLabelRes: Int
            get() = when (theme) {
                THEME_DARK -> R.string.theme_dark
                THEME_LIGHT -> R.string.theme_light
                else -> R.string.theme_system
            }
    }

    private val _state = mutableStateOf(Snapshot())
    val state: State<Snapshot> = _state

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences("fithub_prefs", Context.MODE_PRIVATE)

    /** 冷启动时读一次。SharedPreferences 首次 get 会把 xml 读进内存，耗时可忽略。 */
    fun init(context: Context) {
        val s = sp(context)
        _state.value = Snapshot(
            theme = s.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM,
            includePrerelease = s.getBoolean(KEY_INCLUDE_PRERELEASE, false),
            requireSha = s.getBoolean(KEY_REQUIRE_SHA, false),
        )
    }

    private fun update(context: Context, mutate: android.content.SharedPreferences.Editor.() -> Unit) {
        sp(context).edit().apply(mutate).apply()
        init(context) // 重读一次，保证写成功与内存状态一致
    }

    fun setTheme(context: Context, value: String) =
        update(context) { putString(KEY_THEME, value) }

    fun setIncludePrerelease(context: Context, value: Boolean) =
        update(context) { putBoolean(KEY_INCLUDE_PRERELEASE, value) }

    fun setRequireSha(context: Context, value: Boolean) =
        update(context) { putBoolean(KEY_REQUIRE_SHA, value) }

    /**
     * 语言偏好走独立的一组读写，不进 [Snapshot]。
     *
     * 原因：切换语言必须立刻 `recreate()` 生效，走 [Snapshot] 会经过 [init] 重读整个
     * 偏好文件，而这里只需要一个键。
     */
    fun lang(context: Context): String =
        sp(context).getString(KEY_LANG, AppLocale.SYSTEM) ?: AppLocale.SYSTEM

    fun setLang(context: Context, value: String) {
        sp(context).edit().putString(KEY_LANG, value).apply()
    }

    /**
     * 下载镜像。和语言一样走独立读写：改完要立刻影响下一次下载，
     * 而这一项是**下载路径的一部分**，[Prefs.state] 里的主题/开关跟它无关。
     *
     * 默认 [Mirrors.DIRECT] —— 走第三方镜像意味着安装包流量经过对方，
     * 该由用户自己决定，不该替他选。
     */
    fun mirrorId(context: Context): String =
        sp(context).getString(KEY_MIRROR, Mirrors.DIRECT.id) ?: Mirrors.DIRECT.id

    fun setMirrorId(context: Context, value: String) {
        sp(context).edit().putString(KEY_MIRROR, value).apply()
    }

    /**
     * 是否允许下载失败后自动换下一个镜像。
     *
     * 默认**开**：镜像挂了就静默卡住比换一个更糟，用户不会知道该点什么。
     */
    fun mirrorAutoFallback(context: Context): Boolean =
        sp(context).getBoolean(KEY_MIRROR_FALLBACK, true)

    fun setMirrorAutoFallback(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_MIRROR_FALLBACK, value).apply()
    }
}
