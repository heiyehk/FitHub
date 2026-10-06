package com.heiyehk.fithub.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.heiyehk.fithub.ui.theme.FitTheme
import com.heiyehk.fithub.ui.theme.FitTypography

/**
 * 悬浮胶囊 tab bar，不通栏、不贴底，浮在内容之上。
 *
 *  - 高 56dp，左右贴边各 16dp，底部 = navigationBars inset + 16dp
 *  - 白底 + 发丝描边 + 一层不脏的柔光投影
 *  - 选中态是内嵌药丸（淡墨绿）而不是整块变色，药丸在格子里水平垂直都居中，
 *    圆角取高/2，用 spring 平移
 *
 * @param hidden 详情面板打开时整条淡出：面板是全屏的，留着会在边缘露一条
 */
@Composable
fun FloatingTabBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
) {
    val p = FitTheme.palette
    val alpha by animateFloatAsState(
        targetValue = if (hidden) 0f else 1f,
        animationSpec = tween(180),
        label = "tabBarAlpha",
    )
    if (alpha <= 0.01f) return

    val shadowAlpha by animateFloatAsState(
        targetValue = if (hidden) 0f else 1f,
        animationSpec = tween(180),
        label = "tabBarShadow",
    )

    Box(
        modifier.graphicsLayer { this.alpha = alpha },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(30.dp),
                    ambientColor = Color(0x14141614).copy(alpha = 0.35f * shadowAlpha),
                    spotColor = Color(0x1A141614).copy(alpha = 0.30f * shadowAlpha),
                )
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = 0.96f + 0.04f * alpha
                    scaleY = 0.96f + 0.04f * alpha
                },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(TAB_BAR_HEIGHT)
                    .clip(RoundedCornerShape(30.dp))
                    .background(p.surface.copy(alpha = 0.985f))
                    .border(BorderStroke(1.dp, p.hairline), RoundedCornerShape(30.dp))
                    .padding(horizontal = TAB_BAR_INSET),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxSize()) {
                    val itemWidth: Dp = maxWidth / AppTab.entries.size                    // 药丸的水平位移：从盒子左沿起算的"格内居中"位置。
                    //
                    // 横向必须用 offset(x) 从左沿起算，不能换成 align(Center)：
                    // align(Center) 会把药丸放在**整个盒子**的中心（也就是第 1、2 格的
                    // 分界），而不是第 0 格的中心。实测过 —— 那样第 0 格会偏 136dp。
                    // 纵向反过来：BoxWithConstraints 默认 TopStart，药丸不写对齐就会
                    // 贴在 56dp 高的上沿、比居中的图标+文字整体高 9dp，文字被挤出胶囊。
                    //
                    // 所以两个方向各管各的：横向 offset 算，纵向 CenterVertically。
                    val pillX by animateDpAsState(
                        targetValue = itemWidth * selected.ordinal + (itemWidth - TAB_PILL_WIDTH) / 2,
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f),
                        label = "pillX",
                    )
                    val pillAlpha by animateFloatAsState(
                        targetValue = if (hidden) 0f else 1f,
                        animationSpec = tween(160),
                        label = "pillAlpha",
                    )

                    // align(Center) 是必须的：BoxWithConstraints 默认 TopStart，
                    // 只写 offset(x) 会让药丸顶在上沿、比图标+文字高一截，文字被挤出胶囊。
                    // 药丸平移而不是瞬移：damping 0.78 / stiffness 620 手感更干脆
                    Box(
                        Modifier
                            // 纵向：BoxWithConstraints 默认 TopStart，不写对齐药丸就贴在
                            // 56dp 的上沿、比居中的图标+文字整体高 9dp，文字被挤出胶囊。
                            // 这里直接用两个常量算出居中量，省掉 align 的重载歧义
                            // （Alignment.CenterVertically 静态类型是 Alignment.Vertical，
                            // 传给 Modifier.align 会解析到 ColumnScope 的另一个重载）。
                            .offset(
                                x = pillX,
                                y = (TAB_BAR_HEIGHT - TAB_PILL_HEIGHT) / 2,
                            )
                            .width(TAB_PILL_WIDTH)
                            .height(TAB_PILL_HEIGHT)
                            .clip(RoundedCornerShape(TAB_PILL_HEIGHT / 2))
                            .background(p.accentTint.copy(alpha = 0.92f * pillAlpha)),
                    )

                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        AppTab.entries.forEach { tab ->
                            TabItem(
                                tab = tab,
                                active = tab == selected,
                                onClick = { onSelect(tab) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: AppTab,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = FitTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 900f),
        label = "tabPress",
    )
    val tint by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(200),
        label = "tabTint",
    )
    val color = lerp(p.ink4, p.accent, tint)

    // 外层 Box 的 contentAlignment 只保证「图标+文字这一整块」在格子里居中；
    // 块**内部**图标和文字的对齐是 Column 自己管的，两件事互不替代。
    Box(
        modifier
            .fillMaxWidth()
            .height(TAB_BAR_HEIGHT)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // horizontalAlignment 必须显式给 CenterHorizontally。
        //
        // Column 的横向对齐**默认是 Start**，而列宽 = 最宽的子元素 = 那行文字。
        // 于是图标被贴在文字块的左边缘而不是它的正上方：中文标签是 2 个字、接近
        // 正方形，图标几乎占满列宽，偏移小到看不出来；换成英文标签一下就露馅 ——
        // 实测图标中心相对标签中心 Discover -30px / Device -17px /
        // Subscriptions -63.5px / Profile -16px，标签越宽偏得越多。
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp))
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(tab.labelRes),
                style = FitTypography.labelSmall.copy(letterSpacing = 0.2.sp),
                color = color,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

val TAB_BAR_HEIGHT: Dp = 56.dp

/**
 * 药丸尺寸。
 *
 * 横向：格宽 = (屏宽 - bar 两侧 16dp - 内部 2×8dp) / 4，实测 411dp 宽的机器上约
 * 90.9dp。取 80dp（约 88%），两侧各还留约 5.4dp —— 够宽到一眼看出选中态，又没到
 * 贴上格子边界的程度。贴满了就不再是「一枚药丸」，而是四分之一块底色。
 *
 * 再往上就要看标签宽度了：英文 `Subscriptions` 实测约 69dp，80dp 的药丸里两侧只剩
 * 5.5dp。中文标签只有两个字符（远窄于英文），所以这一档在两种语言下都成立。
 *
 * 纵向比内容高 6dp（21dp 图标 + 3dp 间隙 + 14dp 文字 = 38dp），上下各留 3dp，
 * 文字和图标不会贴到胶囊边上。
 */
private val TAB_PILL_WIDTH: Dp = 80.dp
private val TAB_PILL_HEIGHT: Dp = 44.dp

/** 胶囊内部左右留白，图标离边更远一点 */
private val TAB_BAR_INSET: Dp = 8.dp

/** 列表底部要避让的高度 = tab bar 高 + 悬浮间隙，列表再各自加一段留白 */
val TabBarScrimHeight: Dp = TAB_BAR_HEIGHT + 16.dp
