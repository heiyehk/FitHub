package com.heiyehk.fithub.ui.profile

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import com.heiyehk.fithub.data.install.DownloadCenter
import com.heiyehk.fithub.data.install.assetNameOrNull
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.heiyehk.fithub.BuildConfig
import com.heiyehk.fithub.R
import com.heiyehk.fithub.data.AppLocale
import com.heiyehk.fithub.data.Async
import com.heiyehk.fithub.data.FitRepository
import com.heiyehk.fithub.data.Mirrors
import com.heiyehk.fithub.data.Prefs
import com.heiyehk.fithub.ui.icons.FiArrowLeft
import com.heiyehk.fithub.ui.icons.FiCheck
import com.heiyehk.fithub.ui.icons.FiRefresh
import com.heiyehk.fithub.ui.theme.FitTheme
import com.heiyehk.fithub.ui.theme.FitTypography
import com.heiyehk.fithub.ui.theme.MonoMeta
import kotlinx.coroutines.launch

/**
 * 「我的」里的三个说明页共用的骨架：标题栏 + 可滚动正文 + 底部空当。
 *
 * 单独抽出来是因为三页的交互完全一样，只有正文不同。原来的做法是每页写一遍
 * 顶栏和滚动容器，那是复制三份最容易走样的代码。
 *
 * [title] 是普通 String 而不是 @StringRes：调用方已经在 Composable 里，用
 * `stringResource(...)` 解析好传进来就行，这里再包一层 @StringRes 只会让
 * 每个调用点多写一个包装函数。
 */
@Composable
fun InfoScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val p = FitTheme.palette
    Column(
        Modifier
            .fillMaxSize()
            .background(p.surface)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 8.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    FiArrowLeft,
                    stringResource(R.string.info_back),
                    tint = p.ink,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(title, style = FitTypography.titleMedium, color = p.ink)
        }
        HairDivider()

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            content()
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun HairDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(FitTheme.palette.hairline),
    )
}

/** 正文小标题 */
@Composable
internal fun InfoHeading(text: String) {
    Spacer(Modifier.height(18.dp))
    Text(text, style = FitTypography.titleSmall, color = FitTheme.palette.ink)
    Spacer(Modifier.height(8.dp))
}

@Composable
internal fun InfoPara(text: String) {
    Text(text, style = FitTypography.bodyMedium, color = FitTheme.palette.ink2)
    Spacer(Modifier.height(10.dp))
}

@Composable
internal fun InfoBullet(text: String) {
    Row(Modifier.padding(bottom = 7.dp)) {
        // 「·」是排版用的分隔符，不是词，各语言一致，不资源化
        Text("·", style = FitTypography.bodyMedium, color = FitTheme.palette.ink4)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = FitTypography.bodyMedium,
            color = FitTheme.palette.ink2,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun InfoNote(text: String) {
    Text(text, style = MonoMeta, color = FitTheme.palette.ink4)
}

/**
 * 三选一里的单选行：标题 + 选中打勾 + 底线。
 *
 * 外观与语言两页的交互完全一样（见 [ThemeScreen] 与 [LanguageScreen]），
 * 复制一遍只会在以后改视觉时让两页走样，所以抽出来共用。
 */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, onPick: () -> Unit) {
    val p = FitTheme.palette
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onPick() }
                .padding(vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = FitTypography.titleSmall,
                color = p.ink,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(FiCheck, null, tint = p.accent, modifier = Modifier.size(18.dp))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(p.hairline),
        )
    }
}

/* ────────────────────────── 隐私 ────────────────────────── */

@Composable
fun PrivacyScreen(onBack: () -> Unit) = InfoScreen(stringResource(R.string.info_privacy_title), onBack) {
    InfoHeading(stringResource(R.string.info_privacy_h_inone))
    InfoPara(stringResource(R.string.info_privacy_b_none))

    InfoHeading(stringResource(R.string.info_privacy_h_requests))
    InfoPara(stringResource(R.string.info_privacy_requests_intro))
    InfoBullet(stringResource(R.string.info_privacy_req_api))
    InfoBullet(stringResource(R.string.info_privacy_req_release))
    InfoBullet(stringResource(R.string.info_privacy_req_device))
    InfoBullet(stringResource(R.string.info_privacy_req_webdav))
    InfoPara(stringResource(R.string.info_privacy_anon_quota))

    InfoHeading(stringResource(R.string.info_privacy_h_local))
    InfoBullet(stringResource(R.string.info_privacy_local_history))
    InfoBullet(stringResource(R.string.info_privacy_local_scan))
    InfoBullet(stringResource(R.string.info_privacy_local_subs))
    InfoBullet(stringResource(R.string.info_privacy_local_token))
    InfoPara(stringResource(R.string.info_privacy_local_note))

    InfoHeading(stringResource(R.string.info_privacy_h_apk))
    InfoPara(stringResource(R.string.info_privacy_apk_note))
}

/* ────────────────────── 开源协议与致谢 ────────────────────── */

@Composable
fun LicenseScreen(onBack: () -> Unit) = InfoScreen(stringResource(R.string.info_license_title), onBack) {
    InfoHeading(stringResource(R.string.info_license_h_project))
    InfoPara(stringResource(R.string.info_license_mit))
    // 版权行是法务原文，中英文一致，不资源化
    InfoPara("Copyright (c) 2026 heiyehk")
    InfoPara(stringResource(R.string.info_license_terms))

    InfoHeading(stringResource(R.string.info_license_h_runtime))
    InfoPara(stringResource(R.string.info_license_runtime_intro))
    InfoBullet(stringResource(R.string.info_license_dep_compose))
    InfoBullet(stringResource(R.string.info_license_dep_ktor))
    InfoBullet(stringResource(R.string.info_license_dep_serialization))
    InfoBullet(stringResource(R.string.info_license_dep_markdown))
    InfoPara(stringResource(R.string.info_license_runtime_note))

    InfoHeading(stringResource(R.string.info_license_h_data))
    InfoPara(stringResource(R.string.info_license_data_note))
}

/* ────────────────────────── 外观 ────────────────────────── */

/**
 * 三选一而不是开关：深浅色不是一个布尔量，「跟随系统」必须是第三种状态，
 * 硬塞进开关就会出现「开关关着但界面是深的」这种自相矛盾。
 */
@Composable
fun ThemeScreen(onBack: () -> Unit, onPick: (String) -> Unit) {
    val current = Prefs.state.value.theme
    InfoScreen(stringResource(R.string.pref_appearance), onBack) {
        InfoHeading(stringResource(R.string.pref_appearance_scheme))
        for (value in listOf(Prefs.THEME_SYSTEM, Prefs.THEME_LIGHT, Prefs.THEME_DARK)) {
            // 标签复用 Prefs.Snapshot.themeLabelRes 指向的那三个 key，
            // 设置页那一行显示的就是它们，两处不该各写一份
            val label = stringResource(
                when (value) {
                    Prefs.THEME_SYSTEM -> R.string.theme_system
                    Prefs.THEME_LIGHT -> R.string.theme_light
                    else -> R.string.theme_dark
                },
            )
            ChoiceRow(label = label, selected = value == current) { onPick(value) }
        }
        Spacer(Modifier.height(14.dp))
        InfoNote(stringResource(R.string.pref_appearance_note))
    }
}

/* ────────────────────────── 语言 ────────────────────────── */

/**
 * App 内语言的三选一。
 *
 * 和外观页同样的交互，原因也一样：语言不是一个布尔量，「跟随系统」必须是
 * 第三种状态。选项名走 [AppLocale.choiceLabel] —— 「简体中文 / English」
 * 这些名字各语言通用，资源化反而会把「English」翻译成「英文」。
 *
 * 切换后必须 `recreate()`：低版本是自己包装 context 的（见
 * `MainActivity.attachBaseContext`），不重建就不会重新读一遍字符串资源。
 */
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // 与 FitHubApp 里的返回键用同一个取法：这个 App 是单 Activity + overlay，
    // Compose 拿到的 LocalContext 就是 Activity 本身
    val activity = context as? Activity
    // 不 remember：AppLocale.current 是去读偏好，切完语言重建后要立刻读到新值
    val current = AppLocale.current(context)

    InfoScreen(stringResource(R.string.pref_language), onBack) {
        InfoHeading(stringResource(R.string.pref_language_scheme))
        for (pref in AppLocale.CHOICES) {
            ChoiceRow(
                label = AppLocale.choiceLabel(pref),
                selected = pref == current,
            ) {
                AppLocale.set(context, pref)
                activity?.recreate()
            }
        }
        Spacer(Modifier.height(14.dp))
        InfoNote(stringResource(R.string.pref_language_note))
    }
}

/**
 * 下载镜像。
 *
 * 选「直连」之外任意一项都意味着**安装包流量经过第三方服务器** —— 安装包会被
 * 系统安装并拿到权限，这件事必须让用户自己决定，所以默认直连，且每次都在界面上写明。
 *
 * 「自动换下一个」默认开：镜像挂了就静默停在那里，用户根本不知道该点什么；
 * 关掉则严格只用一个源，失败就如实报错。
 */
@Composable
fun MirrorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(Prefs.mirrorId(context)) }
    var auto by remember { mutableStateOf(Prefs.mirrorAutoFallback(context)) }

    // 正在下东西的时候切源，**对这一轮没有影响**：服务是在开始时就把镜像读走的
    // （DownloadService.downloadWithFallback 开头取 Prefs.mirrorId），中途改设置
    // 不会把正在传的数据改道。不说清楚的话，用户会以为换源能救活卡住的那次下载，
    // 或者反过来——以为这一轮已经走了新源。
    //
    // 用 produceState 而不是 collectAsState：StateFlow 的无参重载在
    // androidx.lifecycle.compose 里（本项目没这个依赖），Flow 重载的 R 只能由初值
    // 推出来而这里初值就是 null —— 裸 collectAsState(null) 在 HEAD 上根本编译不过。
    val busyWith by produceState<String?>(null) {
        DownloadCenter.state.collect { value = it.assetNameOrNull }
    }

    InfoScreen(stringResource(R.string.pref_mirror), onBack) {
        if (busyWith != null) {
            InfoNote(stringResource(R.string.pref_mirror_busy, busyWith!!))
            Spacer(Modifier.height(10.dp))
        }
        InfoHeading(stringResource(R.string.pref_mirror_pick))
        for (m in Mirrors.ALL) {
            ChoiceRow(
                label = if (m.id == Mirrors.DIRECT.id) {
                    stringResource(R.string.pref_mirror_direct)
                } else {
                    "${stringResource(R.string.pref_mirror_option)} · ${m.id}"
                },
                selected = m.id == current,
            ) {
                current = m.id
                Prefs.setMirrorId(context, m.id)
            }
        }
        Spacer(Modifier.height(16.dp))
        InfoHeading(stringResource(R.string.pref_mirror_fallback))
        // 这一页是纯文本布局，没有开关组件；用同一套 ChoiceRow 表达二选一，
        // 免得为了一个开关再往 InfoScreen 塞一个视觉上不属于这页的控件
        ChoiceRow(
            label = stringResource(R.string.pref_mirror_fallback_on),
            selected = auto,
        ) {
            auto = true
            Prefs.setMirrorAutoFallback(context, true)
        }
        ChoiceRow(
            label = stringResource(R.string.pref_mirror_fallback_off),
            selected = !auto,
        ) {
            auto = false
            Prefs.setMirrorAutoFallback(context, false)
        }
        Spacer(Modifier.height(12.dp))
        // 当前选中的是第三方时才提示；选了直连就别啰嗦
        if (Mirrors.BY_ID[current]?.isThirdParty == true) {
            InfoNote(stringResource(R.string.pref_mirror_warn))
        } else {
            InfoNote(stringResource(R.string.pref_mirror_note))
        }
    }
}

/* ────────────────────────── 检查更新 ────────────────────────── */

/**
 * 检查 FitHub 自身的更新。
 *
 * 诚实的做法是查本项目在 GitHub 上的 Release 并和 [BuildConfig.VERSION_NAME] 比，
 * 而不是弹一个「已是最新」的假结论 —— 本 App 还没发布到任何商店，
 * 唯一的更新来源就是仓库 Release。
 *
 * 会消耗一次未认证配额（60 次/小时里的一次），所以只在用户点下按钮时才请求。
 */
@Composable
fun UpdateScreen(onBack: () -> Unit, repo: FitRepository, onToast: (String, String?) -> Unit) {
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Async<String>?>(null) }
    val scope = rememberCoroutineScope()
    val p = FitTheme.palette
    // 点击回调不是 @Composable，stringResource 在里面调不动，
    // 轮询结果里的那两条 toast 因此走 context.getString
    val context = LocalContext.current

    InfoScreen(stringResource(R.string.info_update_title), onBack) {
        InfoHeading(stringResource(R.string.info_update_h_current))
        Text(BuildConfig.VERSION_NAME, style = FitTypography.titleSmall, color = p.ink)
        Spacer(Modifier.height(4.dp))
        InfoNote(stringResource(R.string.info_update_quota_note))

        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (checking) p.wash else p.accentTint)
                .clickable(enabled = !checking) {
                    checking = true
                    result = null
                    scope.launch {
                        result = repo.latestReleaseOfSelf()
                        checking = false
                        val r = result
                        when (r) {
                            is Async.Ok -> {
                                val latest = r.value
                                onToast(
                                    if (latest == BuildConfig.VERSION_NAME) {
                                        context.getString(R.string.info_update_up_to_date)
                                    } else {
                                        context.getString(R.string.info_update_new_version, latest)
                                    },
                                    null,
                                )
                            }
                            is Async.Err -> onToast(
                                context.getString(R.string.info_update_check_failed),
                                r.message,
                            )
                            Async.Loading -> Unit
                            null -> Unit
                        }
                    }
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(FiRefresh, null, tint = p.ink, modifier = Modifier.size(16.dp))
                Text(
                    if (checking) {
                        stringResource(R.string.info_update_checking)
                    } else {
                        stringResource(R.string.info_update_title)
                    },
                    style = FitTypography.titleSmall,
                    color = p.ink,
                )
            }
        }

        val r = result
        when (r) {
            is Async.Ok -> {
                Spacer(Modifier.height(18.dp))
                InfoHeading(stringResource(R.string.info_update_h_result))
                if (r.value == BuildConfig.VERSION_NAME) {
                    InfoPara(stringResource(R.string.info_update_same, BuildConfig.VERSION_NAME))
                } else {
                    InfoPara(
                        stringResource(R.string.info_update_compare, r.value, BuildConfig.VERSION_NAME),
                    )
                    InfoPara(stringResource(R.string.info_update_source_only))
                }
            }

            is Async.Err -> {
                Spacer(Modifier.height(18.dp))
                InfoHeading(stringResource(R.string.info_update_h_result))
                InfoPara(stringResource(R.string.info_update_not_found, r.message))
            }

            else -> Unit
        }
    }
}
