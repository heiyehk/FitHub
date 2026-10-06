package com.heiyehk.fithub.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * 中文走系统字体（MiSans / HarmonyOS Sans / 思源黑体），不打包字体文件，没有下载体积。
 * 版本号、体积、sha256 走等宽，保证 1.10 → 1.9 这类变化数字宽度不抖动。
 */
object Fonts {
    val Sans = FontFamily.SansSerif
    val Mono = FontFamily.Monospace
    val Serif = FontFamily.Serif
}

/**
 * 移动端尺度：字号比桌面版整体收一档。
 * 正文 15sp，层级靠字重与留白拉开，不靠颜色。
 */
val FitTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Fonts.Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Fonts.Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Fonts.Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Fonts.Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Fonts.Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Fonts.Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Fonts.Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Fonts.Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Fonts.Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Fonts.Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.1.sp,
    ),
)

/** 版本号 / 体积 / sha256 统一用它 */
val MonoValue = TextStyle(
    fontFamily = Fonts.Mono,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    textAlign = TextAlign.Start,
)

val MonoMeta = TextStyle(
    fontFamily = Fonts.Mono,
    fontWeight = FontWeight.Normal,
    fontSize = 10.5.sp,
    lineHeight = 14.sp,
)

/** 段首小标签：字距拉开，全大写 */
val Eyebrow = TextStyle(
    fontFamily = Fonts.Mono,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    letterSpacing = 1.sp,
)
