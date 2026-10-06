package com.heiyehk.fithub.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 自带图标集：24x24 视口，1.6 描边、圆头圆角。
 *
 * 不引 material-icons-extended，那一包有几千个图标，debug 包体积会直接上兆；
 * 这 24 个手写向量 R8 后几乎不占体积，风格也统一。
 */
private fun fitIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.6f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero,
    ).build()

/** 用四段三次贝塞尔近似正圆，省得手算 arcTo */
private fun circle(cx: Float, cy: Float, r: Float): String {
    val k = r * 0.5523f
    return "M${cx - r},$cy " +
        "C${cx - r},${cy - k} ${cx - k},${cy - r} $cx,${cy - r} " +
        "C${cx + k},${cy - r} ${cx + r},${cy - k} ${cx + r},$cy " +
        "C${cx + r},${cy + k} ${cx + k},${cy + r} $cx,${cy + r} " +
        "C${cx - k},${cy + r} ${cx - r},${cy + k} ${cx - r},$cy Z"
}

val FiSearch: ImageVector by lazy {
    fitIcon(
        "Search",
        circle(11f, 11f, 6.2f) + " M15.8,15.8 L20,20",
    )
}

val FiArrowLeft: ImageVector by lazy {
    fitIcon("ArrowLeft", "M15,5 L8,12 L15,19")
}

val FiChevron: ImageVector by lazy {
    fitIcon("Chevron", "M9,5 L16,12 L9,19")
}

val FiStar: ImageVector by lazy {
    fitIcon(
        "Star",
        "M12,3.6 L14.47,8.8 L20,9.57 L15.97,13.47 L16.95,19.1 " +
            "L12,16.4 L7.05,19.1 L8.03,13.47 L4,9.57 L9.53,8.8 Z",
    )
}

val FiDownload: ImageVector by lazy {
    fitIcon("Download", "M12,3.8 L12,14.8 M8,10.8 L12,14.8 L16,10.8 M4.6,19.4 L19.4,19.4")
}

val FiShare: ImageVector by lazy {
    fitIcon(
        "Share",
        "M12,3.6 L12,14.6 M8.2,7.4 L12,3.6 L15.8,7.4 " +
            "M5,13.4 L5,18.4 C5,19.5 5.7,20.2 6.6,20.2 L17.4,20.2 " +
            "C18.3,20.2 19,19.5 19,18.4 L19,13.4",
    )
}

val FiBookmark: ImageVector by lazy {
    fitIcon("Bookmark", "M6.6,4.6 L17.4,4.6 L17.4,19.8 L12,15.9 L6.6,19.8 Z")
}

val FiCheck: ImageVector by lazy {
    fitIcon("Check", "M5,12.6 L9.4,17 L19,7.4")
}

val FiAlert: ImageVector by lazy {
    fitIcon("Alert", circle(12f, 12f, 8.2f) + " M12,7.6 L12,12.8 M12,15.9 L12,16")
}

val FiDevice: ImageVector by lazy {
    fitIcon(
        "Device",
        "M9.6,3.4 L14.4,3.4 C15.7,3.4 16.8,4.5 16.8,5.8 L16.8,18.2 " +
            "C16.8,19.5 15.7,20.6 14.4,20.6 L9.6,20.6 C8.3,20.6 7.2,19.5 7.2,18.2 " +
            "L7.2,5.8 C7.2,4.5 8.3,3.4 9.6,3.4 Z M10.6,17.4 L13.4,17.4",
    )
}

val FiPackage: ImageVector by lazy {
    fitIcon(
        "Package",
        "M12,3.6 L19.2,7.5 L19.2,16.4 L12,20.4 L4.8,16.4 L4.8,7.5 Z " +
            "M4.9,7.6 L12,11.5 L19.1,7.6 M12,11.5 L12,20.3",
    )
}

val FiClock: ImageVector by lazy {
    fitIcon("Clock", circle(12f, 12f, 8.2f) + " M12,7.6 L12,12 L15,13.8")
}

val FiShield: ImageVector by lazy {
    fitIcon(
        "Shield",
        "M12,3.6 L18.6,5.9 L18.6,11.4 C18.6,15.5 15.8,18.6 12,20 " +
            "C8.2,18.6 5.4,15.5 5.4,11.4 L5.4,5.9 Z M9.4,12.2 L11.3,14.1 L14.7,10.5",
    )
}

val FiSpark: ImageVector by lazy {
    fitIcon(
        "Spark",
        "M12,3.4 L13.7,8.3 L18.6,10 L13.7,11.7 L12,16.6 L10.3,11.7 L5.4,10 L10.3,8.3 Z",
    )
}

val FiExternal: ImageVector by lazy {
    fitIcon(
        "External",
        "M14,5 L19,5 L19,10 M19,5 L11.6,12.4 " +
            "M17.4,14.2 L17.4,17.8 C17.4,18.6 16.8,19.2 16,19.2 " +
            "L6.2,19.2 C5.4,19.2 4.8,18.6 4.8,17.8 L4.8,8.2 " +
            "C4.8,7.4 5.4,6.8 6.2,6.8 L9.8,6.8",
    )
}

val FiLogo: ImageVector by lazy {
    fitIcon(
        "Logo",
        "M8.4,5.6 L19.6,5.6 C21.4,5.6 22.8,7 22.8,8.8 L22.8,20 " +
            "C22.8,21.8 21.4,23.2 19.6,23.2 L8.4,23.2 C6.6,23.2 5.2,21.8 5.2,20 " +
            "L5.2,8.8 C5.2,7 6.6,5.6 8.4,5.6 Z M9.6,18.4 L9.6,9.6 M18.4,9.6 L18.4,18.4 M9.6,14 L18.4,14",
    )
}

// tab bar 用图

/** 发现：四角罗盘 */
val FiCompass: ImageVector by lazy {
    fitIcon(
        "Compass",
        circle(12f, 12f, 8.4f) + " M15.4,8.6 L13.6,13.6 L8.6,15.4 L10.4,10.4 Z",
    )
}

/** 我的：头肩 */
val FiUser: ImageVector by lazy {
    fitIcon(
        "User",
        circle(12f, 8.4f, 3.9f) + " M4.8,20 C4.8,16.6 8,14.4 12,14.4 " +
            "C16,14.4 19.2,16.6 19.2,20",
    )
}

/** 订阅：铃 */
val FiBell: ImageVector by lazy {
    fitIcon(
        "Bell",
        "M12,3.4 C14.5,3.4 16.4,5.3 16.4,7.8 L16.4,12 L18.4,15 L5.6,15 " +
            "L7.6,12 L7.6,7.8 C7.6,5.3 9.5,3.4 12,3.4 Z M10.2,17.4 L13.8,17.4",
    )
}

/** 语言：地球 */
val FiGlobe: ImageVector by lazy {
    fitIcon(
        "Globe",
        // 外圆 + 赤道 + 两条对称经线弧（用三次贝塞尔近似椭圆弧，和 circle() 同一套近似法）
        circle(12f, 12f, 9.4f) +
            " M2.6,12 L21.4,12" +
            " M12,2.6 C9.3,6.9 9.3,17.1 12,21.4" +
            " M12,2.6 C14.7,6.9 14.7,17.1 12,21.4",
    )
}

/** 设置：滑块 */
val FiSliders: ImageVector by lazy {
    fitIcon(
        "Sliders",
        "M4,8 L10,8 M14,8 L20,8 M4,16 L14,16 M18,16 L20,16 M12,5.6 L12,10.4 M16,13.6 L16,18.4",
    )
}

/** 搜索返回：关闭 */
val FiClose: ImageVector by lazy {
    fitIcon("Close", "M6.6,6.6 L17.4,17.4 M17.4,6.6 L6.6,17.4")
}

/** 刷新 */
val FiRefresh: ImageVector by lazy {
    fitIcon(
        "Refresh",
        "M19.4,12 C19.4,16.1 16,19.4 12,19.4 C8,19.4 4.6,16.1 4.6,12 " +
            "C4.6,7.9 8,4.6 12,4.6 M19.4,4.6 L19.4,9 L15,9",
    )
}

/** 右箭头（列表进入） */
val FiArrowRight: ImageVector by lazy {
    fitIcon("ArrowRight", "M4.6,12 L18,12 M13.6,7.2 L18.8,12 L13.6,16.8")
}

