package com.heiyehk.fithub.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heiyehk.fithub.data.SyncOutcome
import com.heiyehk.fithub.data.remote.WebDavConfig
import com.heiyehk.fithub.data.remote.WebDavPreset
import androidx.compose.ui.res.stringResource
import com.heiyehk.fithub.R
import com.heiyehk.fithub.ui.components.FitBadge
import com.heiyehk.fithub.ui.components.GhostButton
import com.heiyehk.fithub.ui.components.HairLine
import com.heiyehk.fithub.ui.components.IconCircleButton
import com.heiyehk.fithub.ui.components.PrimaryButton
import com.heiyehk.fithub.ui.components.tap
import com.heiyehk.fithub.ui.explainText
import com.heiyehk.fithub.ui.icons.FiArrowLeft
import com.heiyehk.fithub.ui.icons.FiBell
import com.heiyehk.fithub.ui.icons.FiShield
import com.heiyehk.fithub.ui.theme.Eyebrow
import com.heiyehk.fithub.ui.theme.FitTheme
import com.heiyehk.fithub.ui.theme.FitTone
import com.heiyehk.fithub.ui.theme.FitTypography
import com.heiyehk.fithub.ui.theme.MonoMeta

/**
 * WebDAV 订阅同步的配置页。
 *
 * 密码用密码模式显示，且**不写入日志、不回显到 URL**。
 * 选「坚果云」时会把「要用应用密码不是登录密码」直接写在页面上 ——
 * 这是最常见的第一次配置失败原因，写在文档里没人会看。
 */
@Composable
fun WebDavScreen(
    saved: WebDavConfig,
    subscriptionCount: Int,
    lastOutcome: SyncOutcome?,
    syncing: Boolean,
    onBack: () -> Unit,
    onSave: (WebDavConfig) -> Unit,
    onSync: () -> Unit,
    onForcePush: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState,
) {
    val p = FitTheme.palette
    var conf by remember { mutableStateOf(saved) }
    var showPassword by remember { mutableStateOf(false) }
    val preset = WebDavPreset.byId(conf.presetId)

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
            Text(stringResource(R.string.webdav_title), style = FitTypography.titleMedium, color = p.ink)
        }
        HairLine()

        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item(key = "intro") {
                Text(
                    stringResource(R.string.webdav_intro_1),
                    style = FitTypography.bodySmall,
                    color = p.ink4,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }

            // 服务商选择
            item(key = "presets") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text(stringResource(R.string.webdav_presets), style = Eyebrow, color = p.ink4)
                    Spacer(Modifier.height(10.dp))
                    WebDavPreset.BUILT_IN.forEach { option ->
                        val active = conf.presetId == option.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) p.wash else p.surface)
                                .border(1.dp, if (active) p.accentLine else p.hairline, RoundedCornerShape(12.dp))
                                .tap {
                                    conf = conf.copy(
                                        presetId = option.id,
                                        // 换服务商时把地址带过去，但密码要用户重填 ——
                                        // 不同服务商的密码语义不同（坚果云要应用密码）
                                        baseUrl = option.baseUrl,
                                        password = if (conf.presetId == option.id) conf.password else "",
                                    )
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(option.nameRes),
                                    style = FitTypography.titleSmall,
                                    color = if (active) p.accent else p.ink,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    stringResource(option.noteRes),
                                    style = FitTypography.bodySmall,
                                    color = p.ink4,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "fields") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.webdav_conn), style = Eyebrow, color = p.ink4)
                    Spacer(Modifier.height(10.dp))

                    Field(
                        label = stringResource(R.string.webdav_field_url),
                        value = conf.baseUrl,
                        placeholder = "https://dav.jianguoyun.com/dav/",
                        onChange = { conf = conf.copy(baseUrl = it, presetId = "") },
                    )
                    Spacer(Modifier.height(10.dp))
                    Field(
                        label = stringResource(
                            if (preset?.appPassword == true) R.string.webdav_field_user_email else R.string.webdav_field_user,
                        ),
                        value = conf.username,
                        placeholder = "you@example.com",
                        onChange = { conf = conf.copy(username = it) },
                    )
                    Spacer(Modifier.height(10.dp))
                    Field(
                        label = stringResource(
                            if (preset?.appPassword == true) R.string.webdav_field_pass_app else R.string.webdav_field_pass,
                        ),
                        value = conf.password,
                        placeholder = "••••••••",
                        onChange = { conf = conf.copy(password = it) },
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailing = {
                            Text(
                                stringResource(if (showPassword) R.string.webdav_hide else R.string.webdav_show),
                                style = FitTypography.labelSmall,
                                color = p.ink4,
                                modifier = Modifier.tap { showPassword = !showPassword }.padding(4.dp),
                            )
                        },
                    )
                    if (preset?.appPassword == true) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(FiShield, null, tint = p.ink4, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.webdav_jianguo_tip),
                                style = FitTypography.bodySmall,
                                color = p.ink4,
                            )
                        }
                    }
                }
            }

            item(key = "actions") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    PrimaryButton(
                        text = stringResource(if (conf.enabled) R.string.webdav_save else R.string.webdav_save_enable),
                        icon = FiShield,
                        height = 46.dp,
                        onClick = { onSave(conf.copy(enabled = true)) },
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton(stringResource(R.string.webdav_test), onTest, Modifier.weight(1f))
                        GhostButton(stringResource(R.string.webdav_sync_now), onSync, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    GhostButton(stringResource(R.string.webdav_force_push), onForcePush)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.webdav_merge_note_1) + "\n" +
                            stringResource(R.string.webdav_merge_note_2),
                        style = FitTypography.bodySmall,
                        color = p.ink4,
                    )
                }
            }

            // 上次同步结果：成功与失败都要说清楚，失败尤其要说本地没被动
            item(key = "last") {
                val o = lastOutcome
                if (o != null) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text(stringResource(R.string.webdav_last_result), style = Eyebrow, color = p.ink4)
                        Spacer(Modifier.height(8.dp))
                        when (o) {
                            is SyncOutcome.Pushed -> Row(verticalAlignment = Alignment.CenterVertically) {
                                FitBadge(FitTone.Ok, stringResource(R.string.webdav_pushed))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.webdav_pushed_detail, o.count),
                                    style = FitTypography.bodySmall,
                                    color = p.ink3,
                                )
                            }

                            is SyncOutcome.Pulled -> Row(verticalAlignment = Alignment.CenterVertically) {
                                FitBadge(FitTone.Ok, stringResource(R.string.webdav_pulled))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.webdav_pulled_detail, o.added),
                                    style = FitTypography.bodySmall,
                                    color = p.ink3,
                                )
                            }

                            is SyncOutcome.Failed -> Column {
                                FitBadge(FitTone.Warn, stringResource(R.string.webdav_failed))
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    explainText(o.reason),
                                    style = FitTypography.bodySmall,
                                    color = p.ink3,
                                )
                            }

                            is SyncOutcome.NotConfigured -> Column {
                                FitBadge(FitTone.Muted, stringResource(R.string.webdav_not_configured))
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    explainText(o.reason),
                                    style = FitTypography.bodySmall,
                                    color = p.ink3,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "scope") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text(stringResource(R.string.webdav_scope), style = Eyebrow, color = p.ink4)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(FiBell, null, tint = p.ink4, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.webdav_scope_1, subscriptionCount) + "\n" +
                                stringResource(R.string.webdav_scope_2),
                            style = FitTypography.bodySmall,
                            color = p.ink4,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = FitTheme.palette
    Column {
        Text(label, style = FitTypography.labelSmall, color = p.ink4)
        Spacer(Modifier.height(6.dp))
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
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = FitTypography.bodyMedium.copy(color = p.ink),
                cursorBrush = SolidColor(p.accent),
                visualTransformation = visualTransformation,
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(placeholder, style = MonoMeta, color = p.ink4)
                    }
                    inner()
                },
            )
            if (trailing != null) {
                Spacer(Modifier.width(6.dp))
                trailing()
            }
        }
    }
}
