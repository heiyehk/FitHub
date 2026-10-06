package com.heiyehk.fithub.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heiyehk.fithub.R
import com.heiyehk.fithub.data.HomeSection
import com.heiyehk.fithub.data.HomeSectionStore
import com.heiyehk.fithub.ui.components.FitBadge
import com.heiyehk.fithub.ui.components.GhostButton
import com.heiyehk.fithub.ui.components.HairLine
import com.heiyehk.fithub.ui.components.IconCircleButton
import com.heiyehk.fithub.ui.components.PrimaryButton
import com.heiyehk.fithub.ui.components.tap
import com.heiyehk.fithub.ui.icons.FiAlert
import com.heiyehk.fithub.ui.icons.FiArrowLeft
import com.heiyehk.fithub.ui.icons.FiCheck
import com.heiyehk.fithub.ui.icons.FiClose
import com.heiyehk.fithub.ui.icons.FiSliders
import com.heiyehk.fithub.ui.theme.Eyebrow
import com.heiyehk.fithub.ui.theme.FitTheme
import com.heiyehk.fithub.ui.theme.FitTone
import com.heiyehk.fithub.ui.theme.FitTypography
import com.heiyehk.fithub.ui.theme.MonoMeta

/**
 * 首页板块管理。
 *
 * **内置板块不可编辑，只能启停** —— 它们的内容是固定的（热门按 star、
 * 最近更新按 push 时间、精选是白名单、Agent 是预置 topic）。可编辑的只有
 * 自定义 topic 板块。
 *
 * 配额提示常驻：每个板块一次 search 请求，用户加板块前就该知道代价。
 */
@Composable
fun HomeSectionManager(
    sections: List<HomeSection>,
    onBack: () -> Unit,
    onChange: (List<HomeSection>) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState,
) {
    val p = FitTheme.palette
    var draft by remember { mutableStateOf(sections) }
    var adding by remember { mutableStateOf(false) }
    var newTopic by remember { mutableStateOf("") }

    val remaining = HomeSectionStore.remainingCustom(draft)
    val cost = draft.count { it.enabled }

    Column(modifier.fillMaxSize().background(p.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(FiArrowLeft, stringResource(R.string.action_back), onClick = onBack)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.home_sections_title), style = FitTypography.titleMedium, color = p.ink)
            Spacer(Modifier.weight(1f))
            if (draft != sections) {
                Text(
                    stringResource(R.string.home_section_save),
                    style = FitTypography.labelLarge,
                    color = p.accent,
                    modifier = Modifier.tap {
                        onChange(draft)
                        onBack()
                    }.padding(4.dp),
                )
            }
        }
        HairLine()

        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "cost") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(FiAlert, null, tint = p.ink4, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.home_section_cost, cost, cost),
                            style = FitTypography.bodySmall,
                            color = p.ink3,
                        )
                    }
                    if (cost >= 40) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.home_section_quota_warn),
                            style = FitTypography.bodySmall,
                            color = p.ink3,
                        )
                    }
                }
            }

            item(key = "list") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(stringResource(R.string.home_section_heading), style = Eyebrow, color = p.ink4)
                    Spacer(Modifier.height(10.dp))
                }
            }

            itemsIndexed(draft, key = { _, s -> s.id }) { index, section ->
                SectionRow(
                    section = section,
                    canMoveUp = index > 0 && !section.builtin,
                    canMoveDown = index < draft.lastIndex && !section.builtin,
                    onToggle = {
                        draft = draft.mapIndexed { i, s ->
                            if (i == index) s.copy(enabled = !s.enabled) else s
                        }
                    },
                    onUp = { draft = draft.moved(index, index - 1) },
                    onDown = { draft = draft.moved(index, index + 1) },
                    onDelete = {
                        draft = draft.filterIndexed { i, _ -> i != index }
                    },
                )
            }

            item(key = "add") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    if (adding) {
                        Text(stringResource(R.string.home_section_new), style = Eyebrow, color = p.ink4)
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(p.wash)
                                .border(1.dp, p.hairline, RoundedCornerShape(11.dp))
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BasicTextField(
                                value = newTopic,
                                onValueChange = { newTopic = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = FitTypography.bodyMedium.copy(color = p.ink),
                                cursorBrush = SolidColor(p.accent),
                                decorationBox = { inner ->
                                    if (newTopic.isEmpty()) {
                                        Text(stringResource(R.string.home_topic_hint), style = MonoMeta, color = p.ink4)
                                    }
                                    inner()
                                },
                            )
                            if (newTopic.isNotEmpty()) {
                                Icon(
                                    FiClose, stringResource(R.string.action_clear), tint = p.ink4,
                                    modifier = Modifier.size(22.dp).clip(CircleShape)
                                        .tap { newTopic = "" }.padding(4.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GhostButton(
                                stringResource(R.string.device_cancel),
                                { adding = false; newTopic = "" },
                                Modifier.weight(1f),
                            )
                            PrimaryButton(
                                text = stringResource(R.string.home_section_add),
                                icon = FiCheck,
                                height = 44.dp,
                                onClick = {
                                    val made = HomeSection.customFrom(newTopic)
                                    if (draft.none { it.id == made.id }) {
                                        draft = draft + made
                                    }
                                    newTopic = ""
                                    adding = false
                                },
                            )
                        }
                    } else {
                        PrimaryButton(
                            text = if (remaining > 0) {
                                stringResource(R.string.home_section_add_topic)
                            } else {
                                stringResource(R.string.home_section_limit)
                            },
                            icon = FiSliders,
                            height = 46.dp,
                            onClick = { adding = true },
                        )
                        if (remaining > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(
                                    R.string.home_section_remaining,
                                    remaining,
                                    HomeSection.MAX_CUSTOM,
                                ),
                                style = FitTypography.bodySmall,
                                color = p.ink4,
                            )
                        }
                    }
                }
            }

            item(key = "restore") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    GhostButton(
                        stringResource(R.string.home_section_restore),
                        onClick = { draft = HomeSection.defaults() },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.home_section_restore_note),
                        style = FitTypography.bodySmall,
                        color = p.ink4,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionRow(
    section: HomeSection,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = FitTheme.palette
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (section.enabled) p.accentTint else p.wash)
                    .tap { onToggle() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    FiCheck,
                    contentDescription = if (section.enabled) {
                        stringResource(R.string.home_section_toggle_off)
                    } else {
                        stringResource(R.string.home_section_toggle_on)
                    },
                    tint = if (section.enabled) p.accent else p.ink4,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        sectionTitle(section),
                        style = FitTypography.titleSmall,
                        color = if (section.enabled) p.ink else p.ink4,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(7.dp))
                    if (section.builtin) {
                        FitBadge(FitTone.Muted, stringResource(R.string.home_section_builtin))
                    }
                    if (!section.enabled) {
                        Spacer(Modifier.width(6.dp))
                        FitBadge(FitTone.Prerelease, stringResource(R.string.home_section_off))
                    }
                }
                if (section.subtitle.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        sectionSubtitle(section),
                        style = FitTypography.bodySmall,
                        color = p.ink4,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            if (section.builtin) {
                // 内置板块不可编辑也不可删除，只有开关
                Text(stringResource(R.string.home_section_fixed), style = MonoMeta, color = p.ink4, modifier = Modifier.padding(6.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("↑", style = FitTypography.bodyMedium, color = if (canMoveUp) p.ink3 else p.hairline,
                        modifier = Modifier.tap(enabled = canMoveUp) { onUp() }.padding(5.dp))
                    Text("↓", style = FitTypography.bodyMedium, color = if (canMoveDown) p.ink3 else p.hairline,
                        modifier = Modifier.tap(enabled = canMoveDown) { onDown() }.padding(5.dp))
                    Text(stringResource(R.string.home_section_delete), style = FitTypography.bodySmall, color = p.ink4,
                        modifier = Modifier.tap { onDelete() }.padding(5.dp))
                }
            }
        }
        HairLine(Modifier.padding(start = 62.dp))
    }
}

/** 上下移动。跨过内置板块没有意义，所以调用方会禁用越界的箭头 */
private fun List<HomeSection>.moved(from: Int, to: Int): List<HomeSection> {
    if (to !in indices) return this
    return toMutableList().also {
        it.add(to, it.removeAt(from))
    }
}
