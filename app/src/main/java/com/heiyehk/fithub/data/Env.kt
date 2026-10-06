package com.heiyehk.fithub.data

import com.heiyehk.fithub.R
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 运行时上下文与展示格式化。
 *
 * 只保留不产生数据的东西：
 *
 * - [device]：本机探测档案，已装列表由 [ScanEngine] 扫描、关联由 [LinkEngine] 回写
 * - [agoRes] / [formatStars] / [formatSize]：纯格式化函数
 * - [today]：当前日期
 */
object Env {

    val today: LocalDate get() = LocalDate.now()

    /**
     * 本机档案，探测自真实设备。
     *
     * `installed` 初始为空，由 [ScanEngine] 的扫描结果和 [LinkEngine] 的绑定回写。
     */
    var device: DeviceProfile = DeviceProfile.detect()
        internal set

    /**
     * 相对时间，返回**资源 ID**而不是文案。
     *
     * 「万」「个月前」这类写法随语言变，英文界面下必须显示 `12.3k` / `3 mo ago`。
     * data/ 层没有 [android.content.Context]，所以这里只给出 @StringRes，
     * 由 UI 层 `ageText(...)` 渲染。调用点见 `ui/Localized.kt`。
     */
    @androidx.annotation.StringRes
    fun agoRes(date: String): Int {
        val days = ChronoUnit.DAYS.between(LocalDate.parse(date), today).toInt()
        return when {
            days <= 0 -> R.string.age_today
            days == 1 -> R.string.age_yesterday
            days < 7 -> R.string.age_days
            days < 30 -> R.string.age_weeks
            else -> R.string.age_months
        }
    }

    /** [agoRes] 对应的天数参数，<= 0 / 1 的分支不需要 */
    fun agoDays(date: String): Int =
        ChronoUnit.DAYS.between(LocalDate.parse(date), today).toInt()

    /**
     * star 数走 GitHub 自己的记法：`12.3k` / `1.2M`。
     *
     * 中文习惯用「万」，但 star 数是 GitHub 生态的通用读法，英文界面显示
     * `12.3万` 会比 `12.3k` 更难和 GitHub 官网对上，所以两种语言统一用 k/M。
     */
    fun formatStars(n: Int): String = when {
        n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
        n >= 1000 -> String.format("%.1fk", n / 1000.0)
        else -> n.toString()
    }

    /**
     * 汇总值的版本。
     *
     * 「我所有仓库收到的 star 加起来」会超出 Int：单个仓库的上限在百万级，
     * 仓库数上千就轻松越过 2^31，用 Int 累加会静默溢出成一个负数 ——
     * 界面上就成了「-2147483648 star」。所以加总一路用 Long，只在显示时转回字符串。
     */
    fun formatStars(n: Long): String = when {
        n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
        n >= 1000 -> String.format("%.1fk", n / 1000.0)
        else -> n.toString()
    }

    fun formatSize(mb: Double): String =
        if (mb >= 100) "${mb.toInt()} MB" else String.format("%.1f MB", mb)
}
