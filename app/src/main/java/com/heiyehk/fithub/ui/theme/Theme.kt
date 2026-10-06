package com.heiyehk.fithub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 一屏之内只允许一套颜色来源，所以自建 palette，不从 Material ColorScheme 取色 */
@Immutable
data class FitPalette(
    val isDark: Boolean,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val ink4: Color,
    val outlineSoft: Color,
    val canvas: Color,
    val wash: Color,
    val washDeep: Color,
    val hairline: Color,
    val hairlineSoft: Color,
    val accent: Color,
    val accentStrong: Color,
    val accentTint: Color,
    val accentLine: Color,
    val okTint: Color,
    val okLine: Color,
    val warnTint: Color,
    val warnLine: Color,
    val badTint: Color,
    val badLine: Color,
    val preTint: Color,
    val preLine: Color,
    val scrim: Color,
) {
    val surface: Color get() = if (isDark) Color(0xFF0E100E) else Color(0xFFFEFDFC)

    fun toneBg(tone: FitTone): Color = when (tone) {
        FitTone.Ok -> okTint
        FitTone.Warn -> warnTint
        FitTone.Bad -> badTint
        FitTone.Prerelease -> preTint
        FitTone.Muted -> washDeep
    }

    fun toneLine(tone: FitTone): Color = when (tone) {
        FitTone.Ok -> okLine
        FitTone.Warn -> warnLine
        FitTone.Bad -> badLine
        FitTone.Prerelease -> preLine
        FitTone.Muted -> hairline
    }

    fun toneFg(tone: FitTone): Color = when (tone) {
        FitTone.Ok -> if (isDark) Color(0xFF7ED3B7) else Color(0xFF14624F)
        FitTone.Warn -> if (isDark) Color(0xFFE0B978) else Color(0xFF8A5A12)
        FitTone.Bad -> if (isDark) Color(0xFFE79B94) else Color(0xFFA32B22)
        FitTone.Prerelease -> if (isDark) Color(0xFFA9AECD) else Color(0xFF5C5F7D)
        FitTone.Muted -> ink3
    }
}

private val LightPalette = FitPalette(
    isDark = false,
    ink = Ink.Primary, ink2 = Ink.Secondary, ink3 = Ink.Tertiary, ink4 = Ink.Quaternary,
    outlineSoft = Ink.OutlineSoft,
    canvas = Ink.Canvas, wash = Ink.Wash, washDeep = Ink.WashDeep,
    hairline = Ink.Hairline, hairlineSoft = Ink.HairlineSoft,
    accent = Ink.Accent, accentStrong = Ink.AccentStrong,
    accentTint = Ink.AccentTint, accentLine = Ink.AccentLine,
    okTint = Ink.OkTint, okLine = Ink.OkLine,
    warnTint = Ink.WarnTint, warnLine = Ink.WarnLine,
    badTint = Ink.BadTint, badLine = Ink.BadLine,
    preTint = Ink.PreTint, preLine = Ink.PreLine,
    scrim = Ink.Scrim,
)

private val DarkPalette = FitPalette(
    isDark = true,
    ink = InkDark.Primary, ink2 = InkDark.Secondary, ink3 = InkDark.Tertiary, ink4 = InkDark.Quaternary,
    outlineSoft = InkDark.OutlineSoft,
    canvas = InkDark.Canvas, wash = InkDark.Wash, washDeep = InkDark.WashDeep,
    hairline = InkDark.Hairline, hairlineSoft = InkDark.HairlineSoft,
    accent = InkDark.Accent, accentStrong = InkDark.AccentStrong,
    accentTint = InkDark.AccentTint, accentLine = InkDark.AccentLine,
    okTint = InkDark.OkTint, okLine = InkDark.OkLine,
    warnTint = InkDark.WarnTint, warnLine = InkDark.WarnLine,
    badTint = InkDark.BadTint, badLine = InkDark.BadLine,
    preTint = InkDark.PreTint, preLine = InkDark.PreLine,
    scrim = InkDark.Scrim,
)

val LocalFitPalette = staticCompositionLocalOf { LightPalette }

object FitTheme {
    val palette: FitPalette
        @Composable get() = LocalFitPalette.current
}

val FitShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun FitHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFF06130F),
            background = palette.surface,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.wash,
            onSurfaceVariant = palette.ink3,
            outline = palette.hairline,
            outlineVariant = palette.hairlineSoft,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            background = palette.surface,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.wash,
            onSurfaceVariant = palette.ink3,
            outline = palette.hairline,
            outlineVariant = palette.hairlineSoft,
        )
    }

    MaterialTheme(colorScheme = scheme, typography = FitTypography, shapes = FitShapes) {
        CompositionLocalProvider(LocalFitPalette provides palette, content = content)
    }
}
