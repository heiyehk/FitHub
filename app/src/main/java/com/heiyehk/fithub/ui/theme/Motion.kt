package com.heiyehk.fithub.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * 动效令牌。
 *
 * 只跑 transform / alpha / color，不跑 layout 动画。
 */
object FitMotion {

    /** 详情面板滑入：轻微过冲的 spring，收得干脆 */
    val PanelIn: SpringSpec<Float> = spring(dampingRatio = 0.90f, stiffness = 420f)

    /** 滑出：更快一点，回到原页面时不要拖泥带水 */
    val PanelOut: SpringSpec<Float> = spring(dampingRatio = 0.94f, stiffness = 560f)

    /** 内容入场：比面板滑入再紧一点 */
    val ContentIn: SpringSpec<Float> = spring(dampingRatio = 0.88f, stiffness = 520f)

    /** 卡片按下回弹，阻尼比压得比面板低，回弹感更明显 */
    val Press: SpringSpec<Float> = spring(dampingRatio = 0.65f, stiffness = 950f)

    /** 透明度 / 颜色类过渡 */
    const val FADE_MS = 220
    const val FAST_MS = 140

    /** 内容入场错峰步长 */
    const val STAGGER_MS = 24L

    val Enter: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val Exit: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
}
