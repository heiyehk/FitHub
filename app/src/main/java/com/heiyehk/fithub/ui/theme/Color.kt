package com.heiyehk.fithub.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 纯白为底，发丝线分层，墨绿是唯一强调色。
 * 状态色只表达状态，不表达层级。
 */
object Ink {
    val Primary = Color(0xFF141614)
    val Secondary = Color(0xFF3D413D)
    val Tertiary = Color(0xFF6E736E)
    val Quaternary = Color(0xFF9BA09B)
    val OutlineSoft = Color(0xFFC2C6C1)

    // surface
    val Canvas = Color(0xFFF2F1ED)      // 页面缩小后露出的底
    val Wash = Color(0xFFFAF9F6)
    val WashDeep = Color(0xFFF4F3EF)
    val Hairline = Color(0xFFE8E6E1)
    val HairlineSoft = Color(0xFFF1F0EC)

    // 单一强调色：墨绿
    val Accent = Color(0xFF14624F)
    val AccentStrong = Color(0xFF0E4B3C)
    val AccentTint = Color(0xFFEAF2EF)
    val AccentLine = Color(0xFFCFE2DB)

    // 状态色的底与描边
    val OkTint = Color(0xFFE9F3EF)
    val OkLine = Color(0xFFC8E0D7)
    val WarnTint = Color(0xFFFCF4E4)
    val WarnLine = Color(0xFFEDDCB6)
    val BadTint = Color(0xFFFBECEB)
    val BadLine = Color(0xFFF0CFCB)
    val PreTint = Color(0xFFEEF0F6)
    val PreLine = Color(0xFFDCDFEA)

    val Scrim = Color(0xFF181A18)
}

object InkDark {
    val Primary = Color(0xFFF2F2EF)
    val Secondary = Color(0xFFC9CCC7)
    val Tertiary = Color(0xFF8E938E)
    val Quaternary = Color(0xFF636863)
    val OutlineSoft = Color(0xFF474B47)

    val Canvas = Color(0xFF080908)
    val Wash = Color(0xFF141614)
    val WashDeep = Color(0xFF1D1F1D)
    val Hairline = Color(0xFF262926)
    val HairlineSoft = Color(0xFF1E211E)

    val Accent = Color(0xFF6FC7AC)
    val AccentStrong = Color(0xFF8ED8C0)
    val AccentTint = Color(0xFF11241E)
    val AccentLine = Color(0xFF22453A)

    val OkTint = Color(0xFF10241E)
    val OkLine = Color(0xFF1F4539)
    val WarnTint = Color(0xFF241D10)
    val WarnLine = Color(0xFF453A1E)
    val BadTint = Color(0xFF241413)
    val BadLine = Color(0xFF4A2724)
    val PreTint = Color(0xFF191B24)
    val PreLine = Color(0xFF2C2F3C)

    val Scrim = Color(0xFF000000)
}

/** 状态色集中在这里定义，避免各处硬编码 */
enum class FitTone { Ok, Warn, Bad, Muted, Prerelease }
