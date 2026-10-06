package com.heiyehk.fithub.data

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * App 内语言。
 *
 * 分两段实现，原因是 minSdk 26 而平台的 per-app locale 要 API 33：
 *
 * - **API 33+** 交给系统。[LocaleManager.applicationLocales] 是原生能力，系统设置里
 *   会自动出现「应用语言」这一项，用户从那边改也生效。这里只负责**写入**。
 * - **API 26–32** 系统没有这能力，只能自己包装 context（见 `MainActivity.attachBaseContext`）
 *   并在切换后 `recreate()`。偏好存在 [Prefs] 里。
 *
 * 两条路**不能同时生效**，否则会互相覆盖：API 33+ 上如果 attachBaseContext 还在按
 * [Prefs] 包装，用户在系统设置里改了语言，回到 App 会被旧偏好盖回去。所以
 * [wrap] 在 API 33+ 且系统已有 applicationLocales 时直接放行。
 *
 * 不引 androidx.appcompat 的 AppCompatDelegate：它要求 Activity 继承 AppCompatActivity
 * 且主题继承 AppCompat 主题，而本项目的主题 parent 是平台主题
 * `@android:style/Theme.Material.Light.NoActionBar`，为语言切换把 Material Components
 * 拖进来不划算。平台 API 已经能覆盖同样的需求。
 */
object AppLocale {

    const val SYSTEM = "system"
    const val ZH = "zh"
    const val EN = "en"

    /** 供语言选择 UI 用的三选项，顺序即展示顺序 */
    val CHOICES = listOf(SYSTEM, ZH, EN)

    /** 界面上怎么称呼自己：跟随系统 / 简体中文 / English —— 这些名字各语言通用，不资源化 */
    fun choiceLabel(pref: String): String = when (pref) {
        ZH -> "简体中文"
        EN -> "English"
        else -> "Follow system"
    }

    /**
     * 偏好值 -> BCP 47 语言标记。`SYSTEM` 返回 null，表示「不指定，交给系统」。
     *
     * 简中用 `zh-CN` 而不是裸 `zh`：裸 `zh` 会被解析成 `zh-Hans-CN`，在部分设备上
     * 拿到的是繁体字形资源。
     */
    fun tagOf(pref: String): String? = when (pref) {
        ZH -> "zh-CN"
        EN -> "en"
        else -> null
    }

    private fun isTiramisu() = Build.VERSION.SDK_INT >= 33

    @Suppress("DEPRECATION") // 低版本用 applicationContext.resources，反射式取 LocaleManager 没有收益
    private fun localeManager(context: Context) =
        if (isTiramisu()) context.getSystemService(android.app.LocaleManager::class.java) else null

    /**
     * 当前生效的语言偏好。
     *
     * API 33+ 上以**系统**为准而不是 [Prefs]：用户在系统设置里改了应用语言时，
     * 这里必须跟着变，否则设置页会显示一个不生效的旧值。
     */
    fun current(context: Context): String {
        if (isTiramisu()) {
            val tag = localeManager(context)?.applicationLocales?.toLanguageTags().orEmpty()
            return when {
                tag.isEmpty() -> SYSTEM
                tag.startsWith("zh") -> ZH
                tag.startsWith("en") -> EN
                else -> SYSTEM
            }
        }
        return Prefs.lang(context)
    }

    /**
     * 切换语言。调用方负责随后 `recreate()`。
     *
     * 偏好始终写进 [Prefs]：一是为了设置页在低版本上能读到当前值，二是留个可读的痕迹。
     */
    fun set(context: Context, pref: String) {
        Prefs.setLang(context, pref)
        if (!isTiramisu()) return
        val lm = localeManager(context) ?: return
        val tag = tagOf(pref)
        lm.applicationLocales =
            if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }

    /**
     * 按当前偏好包装 context，供 `MainActivity.attachBaseContext` 调用。
     *
     * API 33+ 且系统已经设了 applicationLocales 时**原样返回**：系统会在重建 Activity 时
     * 自己应用，再按 [Prefs] 包一层会让系统设置里的改动被旧偏好盖掉。
     */
    fun wrap(base: Context): Context {
        if (isTiramisu()) {
            val sys = localeManager(base)?.applicationLocales
            if (sys != null && !sys.isEmpty) return base
        }
        val tag = tagOf(Prefs.lang(base)) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }
}
