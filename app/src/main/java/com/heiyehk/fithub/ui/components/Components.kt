package com.heiyehk.fithub.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heiyehk.fithub.R
import com.heiyehk.fithub.data.Async
import com.heiyehk.fithub.ui.icons.FiClock
import com.heiyehk.fithub.ui.icons.FiStar
import com.heiyehk.fithub.ui.theme.Eyebrow
import com.heiyehk.fithub.ui.theme.FitMotion
import com.heiyehk.fithub.ui.theme.FitTheme
import com.heiyehk.fithub.ui.theme.FitTone
import com.heiyehk.fithub.ui.theme.FitTypography
import com.heiyehk.fithub.ui.theme.Fonts
import com.heiyehk.fithub.ui.theme.MonoValue

// 排版辅助

/**
 * 把 `**x**` 渲染成加粗片段：正文里局部强调不必拆成多个 Text。
 *
 * 用 `**` 而不是 `<b>`：标记要能待在 Android 字符串资源里，`<` 在 XML 中需要
 * 转义，翻译漏一个 `&lt;` 就会在运行时露原文。`%` 才需要转义（写 `%%`），
 * 而且 `verifyTranslations` 会检查。
 *
 * `<b>` 仍被接受：领域层早期产出的部分 `Explain` 资源沿用了它，两种一起处理，
 * 遇到未闭合的标签按纯文本输出，不吞内容。
 */
@Composable
fun boldMarkup(text: String, color: Color = FitTheme.palette.ink): AnnotatedString {
    val out = buildAnnotatedString {
        var rest = text
        while (true) {
            val star = rest.indexOf("**")
            val angle = rest.indexOf("<b>")
            // 取更靠前的那种标记；都没有就整段输出
            val useStar = when {
                star < 0 -> false
                angle < 0 -> true
                else -> star < angle
            }
            if (!useStar && angle < 0) {
                append(rest)
                break
            }
            val open = if (useStar) star else angle
            val closer = if (useStar) "**" else "</b>"
            val close = rest.indexOf(closer, open + closer.length)
            if (close < 0) {
                append(rest)
                break
            }
            append(rest.substring(0, open))
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = color)) {
                append(rest.substring(open + closer.length, close))
            }
            rest = rest.substring(close + closer.length)
        }
    }
    return out
}

// 交互

/** 无水波纹的点击 + 按下缩放。动画只跑 scale，不跑 layout。 */
fun Modifier.tap(enabled: Boolean = true, pressedScale: Float = 0.97f, onClick: () -> Unit): Modifier =
    composed {
        if (!enabled) return@composed this
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) pressedScale else 1f,
            animationSpec = FitMotion.Press,
            label = "tapScale",
        )
        Modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
    }

// 基础零件

@Composable
fun HairLine(modifier: Modifier = Modifier, color: Color = FitTheme.palette.hairline) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun AppTile(
    monogram: String,
    background: Long,
    foreground: Long,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    corner: Dp = 14.dp,
    inkTile: Boolean = false,
) {
    val p = FitTheme.palette
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(if (inkTile) p.ink else Color(background))
            .then(
                if (inkTile) Modifier
                else Modifier.border(0.5.dp, p.ink.copy(alpha = 0.05f), RoundedCornerShape(corner)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = monogram,
            color = if (inkTile) p.surface else Color(foreground),
            fontSize = (size.value * 0.34f).sp,
            fontWeight = if (inkTile) FontWeight.Medium else FontWeight.SemiBold,
            letterSpacing = (-0.5).sp,
            maxLines = 1,
        )
    }
}

@Composable
fun FitBadge(
    tone: FitTone,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val p = FitTheme.palette
    Row(
        modifier
            .clip(CircleShape)
            .background(p.toneBg(tone))
            .border(BorderStroke(1.dp, p.toneLine(tone)), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = p.toneFg(tone), modifier = Modifier.size(12.dp))
        }
        Text(text, style = FitTypography.labelSmall, color = p.toneFg(tone), maxLines = 1)
    }
}

@Composable
fun OutlineBadge(text: String, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    val p = FitTheme.palette
    Row(
        modifier
            .clip(CircleShape)
            .border(BorderStroke(1.dp, p.hairline), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        leading?.invoke()
        Text(text, style = FitTypography.labelSmall, color = p.ink3, maxLines = 1)
    }
}

@Composable
fun MetaRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(7.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement,
        content = content,
    )
}

@Composable
fun MetaText(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(
        text = text,
        style = FitTypography.bodySmall,
        color = color ?: FitTheme.palette.ink4,
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * 缓存时效提示。
 *
 * 内容来自缓存时必须显示这个条 —— 用户分不清「刚拉的」和「两小时前的」，
 * 就等于把旧数据当实时数据展示。
 */
@Composable
fun StaleNotice(ageMs: Long, modifier: Modifier = Modifier) {
    val p = FitTheme.palette
    val minutes = ageMs / 60_000
    val label = when {
        minutes < 1 -> stringResource(R.string.cache_just_now)
        minutes < 60 -> stringResource(R.string.cache_minutes_ago, minutes)
        else -> stringResource(R.string.cache_hours_ago, minutes / 60)
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(p.warnTint)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(FiClock, null, tint = p.ink3, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.cache_offline, label),
            style = FitTypography.bodySmall,
            color = p.ink3,
        )
    }
}

/**
 * 多个 [Async] 结果里最旧那个缓存的年龄，都不是缓存时返回 null。
 *
 * 一个页面常常由多个请求拼成（发现页两段、详情页元信息 + releases），
 * 只要有一项命中缓存就该提示，取最旧的时间才不会把部分旧说成全新。
 */
@Composable
fun stalestOf(vararg states: Async<*>?): Long? {
    val ages = states.mapNotNull { s ->
        (s as? Async.Ok)?.takeIf { it.fromCache }?.ageMs
    }
    return if (ages.isEmpty()) null else ages.max()
}

@Composable
fun MonoText(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(
        text = text,
        style = MonoValue,
        color = color ?: FitTheme.palette.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun LangDot(color: Long, modifier: Modifier = Modifier, size: Dp = 7.dp) {
    Box(modifier.size(size).clip(CircleShape).background(Color(color)))
}

@Composable
fun Stars(count: String, modifier: Modifier = Modifier) {
    MetaRow(modifier) {
        Icon(FiStar, contentDescription = null, tint = FitTheme.palette.ink4, modifier = Modifier.size(13.dp))
        Text(count, style = FitTypography.bodySmall, color = FitTheme.palette.ink4, maxLines = 1)
    }
}

@Composable
fun LangBar(share: List<Int>, primary: Long, modifier: Modifier = Modifier) {
    val p = FitTheme.palette
    val rest = listOf(p.ink.copy(alpha = 0.22f), p.ink.copy(alpha = 0.13f), p.ink.copy(alpha = 0.07f), p.ink.copy(alpha = 0.04f))
    Row(
        modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        share.forEachIndexed { index, percent ->
            Box(
                Modifier
                    .weight(percent.toFloat())
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(if (index == 0) Color(primary) else rest.getOrElse(index) { p.hairline }),
            )
        }
    }
}

@Composable
fun TopicChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = FitTypography.labelSmall.copy(fontFamily = Fonts.Mono, letterSpacing = 0.sp),
        color = FitTheme.palette.ink3,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(FitTheme.palette.washDeep)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

@Composable
fun SectionHead(index: String, title: String, note: String, modifier: Modifier = Modifier) {
    val p = FitTheme.palette
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(index, style = Eyebrow, color = p.ink4, modifier = Modifier.padding(bottom = 7.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                style = FitTypography.headlineSmall,
                color = p.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = note,
            style = FitTypography.bodyMedium,
            color = p.ink4,
            modifier = Modifier.padding(start = 26.dp),
        )
    }
}

// 按钮

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 50.dp,
    enabled: Boolean = true,
) {
    val p = FitTheme.palette
    val bg by animateColorAsState(
        targetValue = if (enabled) p.ink else p.ink.copy(alpha = 0.32f),
        animationSpec = tween(FitMotion.FADE_MS),
        label = "primaryBg",
    )
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .tap(enabled = enabled) { onClick() }
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = p.surface, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = FitTypography.titleMedium, color = p.surface, maxLines = 1)
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 44.dp,
    /**
     * 禁用态。
     *
     * 「正在做」和「不能做」要用同一种视觉：正在下载的按钮按下去没反应，
     * 跟卡住了没区别。所以 disabled 就做成淡出，而不是换个文案。
     */
    enabled: Boolean = true,
) {
    val p = FitTheme.palette
    val dimmed = p.ink.copy(alpha = 0.38f)
    Row(
        modifier
            .height(height)
            .clip(CircleShape)
            .border(
                BorderStroke(1.dp, if (enabled) p.hairline else p.hairline.copy(alpha = 0.5f)),
                CircleShape,
            )
            .tap(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) p.ink2 else dimmed, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = FitTypography.labelLarge, color = if (enabled) p.ink else dimmed, maxLines = 1)
    }
}

@Composable
fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    active: Boolean = false,
) {
    val p = FitTheme.palette
    val bg by animateColorAsState(
        targetValue = if (active) p.accentTint else Color.Transparent,
        animationSpec = tween(FitMotion.FAST_MS),
        label = "iconBtnBg",
    )
    val fg by animateColorAsState(
        targetValue = if (active) p.accent else p.ink2,
        animationSpec = tween(FitMotion.FAST_MS),
        label = "iconBtnFg",
    )
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .tap { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = fg, modifier = Modifier.size(19.dp))
    }
}

// 扫描雷达

/** 三圈向外扩散的 ping，代替旋转扫针：更安静，也不占 CPU */
@Composable
fun RadarGraphic(
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    accent: Color = FitTheme.palette.accent,
) {
    val transition = rememberInfiniteTransition(label = "radar")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
        ),
        label = "radarPhase",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val maxR = this.size.minDimension / 2f
            val core = maxR * 0.16f
            listOf(0.44f, 0.72f, 1f).forEach { ratio ->
                drawCircle(
                    color = accent.copy(alpha = 0.18f),
                    radius = maxR * ratio,
                    style = Stroke(width = 1f),
                )
            }
            repeat(3) { index ->
                val t = (phase * 1.5f - index * 0.22f).coerceIn(0f, 1f)
                if (t > 0f && t < 1f) {
                    val r = core + t * maxR
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.15f * (1f - t)), Color.Transparent),
                            center = center,
                            radius = r,
                        ),
                        radius = r,
                    )
                }
            }
            listOf(
                Offset(center.x, center.y - maxR),
                Offset(center.x, center.y + maxR),
                Offset(center.x - maxR, center.y),
                Offset(center.x + maxR, center.y),
            ).forEach { drawCircle(color = accent.copy(alpha = 0.3f), radius = 1.6f, center = it) }
            drawCircle(color = accent.copy(alpha = 0.2f), radius = core * 1.95f, style = Stroke(width = 1.4f))
            drawCircle(color = accent, radius = core)
        }
    }
}

/**
 * 细环进度 —— 安装流程里回答「现在到哪一步了」
 *
 * [indeterminate] 用于「在跑但不知道到哪一步」的场景，比如串行刷新中的一条。
 * 那种情况下画 0% 的静止环会让人误以为卡住了，所以走转圈动画。
 */
@Composable
fun CircularProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = FitTheme.palette.accent,
    trackColor: Color = FitTheme.palette.hairline,
    stroke: Dp = 2.dp,
    indeterminate: Boolean = false,
) {
    if (indeterminate) {
        SpinningRing(modifier.size(size), color, stroke)
        return
    }
    val clamped = progress.coerceIn(0f, 1f)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw)
            val topLeft = androidx.compose.ui.geometry.Offset(sw / 2f, sw / 2f)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
            if (clamped > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * clamped,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
private fun SpinningRing(modifier: Modifier, color: Color, stroke: Dp) {
    val spin by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw)
            val topLeft = androidx.compose.ui.geometry.Offset(sw / 2f, sw / 2f)
            drawArc(
                color = color,
                startAngle = spin,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
        }
    }
}

// 进场错峰

/** 进场错峰：位移 + 淡入，逐项间隔由 FitMotion.STAGGER_MS 控制 */
@Composable
fun StaggeredItem(
    index: Int,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 240,
                    delayMillis = (index * FitMotion.STAGGER_MS).toInt(),
                    easing = FitMotion.Enter,
                ),
            )
        } else {
            progress.snapTo(0f)
        }
    }
    Box(
        modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 16.dp.toPx()
        },
    ) { content() }
}
