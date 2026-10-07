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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import com.heiyehk.fithub.data.install.ApkInstaller
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
import com.heiyehk.fithub.ui.components.GhostButton
import com.heiyehk.fithub.data.Asset
import com.heiyehk.fithub.data.Async
import com.heiyehk.fithub.data.Env
import com.heiyehk.fithub.data.FitRepository
import com.heiyehk.fithub.data.Mirrors
import com.heiyehk.fithub.data.Prefs
import com.heiyehk.fithub.data.remote.ReleaseDto
import com.heiyehk.fithub.data.remote.VersionTag
import com.heiyehk.fithub.ui.icons.FiArrowLeft
import com.heiyehk.fithub.ui.icons.FiDownload
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
 *
 * ## 有新版时干什么
 *
 * 按下按钮**直接下载并安装**，不去仓库详情页、不让用户自己挑产物。
 * 下载走 [DownloadCenter]（前台服务 + 通知栏进度 + SHA-256），装走 [ApkInstaller]
 * —— 和装任何一个别的仓库是同两条路，所以下载进度、校验、覆盖安装的判断
 * 都不必再写一遍。
 *
 * 这里曾经改成「跳进自己的仓库详情页」，那对用户等于「点更新 → 自己去找到那个包
 * → 再点一次下载」，多两步；更糟的是详情页里那个包能不能直接装，还取决于文件名
 * 有没有带 ABI（见 GitHubMapper 的 toAsset）。这条不再绕路。
 */
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    repo: FitRepository,
    onToast: (String, String?) -> Unit,
    /** 下载完成、宿主刷新「下载与安装记录」和计数 */
    onLibraryChanged: () -> Unit = {},
) {
    var checking by remember { mutableStateOf(false) }
    // 存整条 release 而不是 tag：下载要用**同一个**对象去挑包。
    // 存 tag 的话下载那一步就得再查一次 releases，而那一次读的是另一条缓存键。
    var result by remember { mutableStateOf<Async<ReleaseDto>?>(null) }

    /** 已经点下「下载并安装」的那个包名。只认自己的那一次进度，别人的下载不管。 */
    var installing by remember { mutableStateOf<String?>(null) }
    var installError by remember { mutableStateOf<String?>(null) }

    /**
     * 取消是用户自己的动作，**不是**失败。
     *
     * 和 [installError] 分开存：塞进去的话界面会写「安装没成功：已取消」——
     * 用户明明是自己按的取消，却被告知自己搞砸了安装，而且这句话还挂在页面上
     * 不走。「已取消」只是一次性的回执，说完就该消失。
     */
    var cancelled by remember { mutableStateOf(false) }
    var installed by remember { mutableStateOf(false) }

    /**
     * 挑包**没有**再发请求。
     *
     * 原来这里是 `repo.latestInstallableOfSelf()`，它走 `detail()` 重新查一遍
     * releases（perPage=20、不穿缓存），于是拿到的可能是上次打开自己仓库详情页时
     * 的旧列表 —— 界面提示 v0.0.3，装上去却是 v0.0.2，且全程无报错。
     * 现在从检查结果那一条 release 直接挑，比较用的和下载用的必然是同一个版本。
     */
    val picked: Asset? = remember(result) {
        (result as? Async.Ok)?.value?.let { repo.installableOf(it) }
    }

    val scope = rememberCoroutineScope()
    val p = FitTheme.palette
    // 点击回调不是 @Composable，stringResource 在里面调不动，
    // 轮询结果里的那两条 toast 因此走 context.getString
    val context = LocalContext.current
    val download by DownloadCenter.state.collectAsState()

    // 认领自己的那一次下载。下完了就交给系统安装器 —— 走的是详情面板里
    // 同一个 ApkInstaller，所以覆盖安装、签名冲突那些判断行为完全一致。
    LaunchedEffect(download, installing) {
        val mine = installing ?: return@LaunchedEffect
        when (val st = download) {
            is DownloadCenter.Progress.Ready -> if (st.assetName == mine) {
                installing = null
                // 文件到位了，宿主那边刷新一下记录页和计数
                onLibraryChanged()
                ApkInstaller.install(context, st.file) { ok, message ->
                    scope.launch {
                        if (ok) {
                            installed = true
                        } else {
                            installError = if (message == ApkInstaller.NEEDS_PERMISSION) {
                                context.getString(R.string.install_error_no_permission)
                            } else {
                                // 直接用原因本身，**不要**再套 install_error_failed。
                                // 渲染处已经用 info_update_install_failed 带了「安装没成功：」，
                                // 两层都这么写的话，界面上会是
                                // 「安装没成功：没装上：这个包签名不同」——同一句话说三遍。
                                message
                            }
                            // 系统安装器已经接管，把通知栏那条撤掉 ——
                            // 否则会同时留着「下载完成」和「安装失败」两条
                            DownloadCenter.reset()
                        }
                    }
                }
            }

            is DownloadCenter.Progress.Failed -> if (st.assetName == mine) {
                installing = null
                // 用户自己按的取消也走 Failed（服务只有这一个终局出口），
                // 但它不是失败 —— 说成「安装没成功：已取消」是在指责用户搞砸了安装。
                // 这里已经由取消按钮置位了，别让服务的回执把它盖回去。
                if (cancelled) {
                    installError = null
                } else {
                    installError = st.reason
                }
            }

            else -> Unit
        }
    }

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
                                val latest = r.value.tagName
                                // 按版本号比，不是字符串相等 —— 见 VersionTag 的注释：
                                // tag 带 v 前缀时字符串相等恒为 false，会永远报「新版本」。
                                val cmp = VersionTag.compare(latest, BuildConfig.VERSION_NAME)
                                onToast(
                                    when {
                                        cmp == 0 -> context.getString(R.string.info_update_up_to_date)
                                        // 远端比本机旧：装的是开发版或更晚的构建。
                                        // 照实说，不能让用户去「更新」到一个更旧的包。
                                        cmp < 0 -> context.getString(
                                            R.string.info_update_local_newer,
                                            BuildConfig.VERSION_NAME,
                                            latest,
                                        )

                                        else -> context.getString(R.string.info_update_new_version, latest)
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
                val latestTag = r.value.tagName
                val cmp = VersionTag.compare(latestTag, BuildConfig.VERSION_NAME)
                when {
                    cmp == 0 -> InfoPara(stringResource(R.string.info_update_same, BuildConfig.VERSION_NAME))

                    // 本机比线上新。这不是「检查失败」，是用户装了个更新的构建，
                    // 如实写出来比含糊其辞有用。
                    cmp < 0 -> {
                        InfoPara(
                            stringResource(
                                R.string.info_update_local_newer_para,
                                BuildConfig.VERSION_NAME,
                                latestTag,
                            ),
                        )
                        InfoPara(stringResource(R.string.info_update_source_only))
                    }

                    else -> {
                        InfoPara(
                            stringResource(R.string.info_update_compare, latestTag, BuildConfig.VERSION_NAME),
                        )
                        InfoPara(stringResource(R.string.info_update_source_only))

                        // 走哪个下载源要写出来，而不是让用户自己记得。
                        //
                        // 代理是**用户自己选的**（我的 → 下载源），但下载时通知栏只写
                        // 「via mirror gh-proxy」那一瞬；这一页是用户按下按钮前最后能看到
                        // 说明的地方，不写就等于要他去设置页里回忆自己选了什么。
                        val mirrorId = remember { Prefs.mirrorId(context) }
                        val mirror = Mirrors.BY_ID[mirrorId]
                        InfoPara(
                            if (mirror != null && mirror.isThirdParty) {
                                context.getString(
                                    R.string.info_update_via_mirror,
                                    mirror.id,
                                    if (Prefs.mirrorAutoFallback(context)) {
                                        context.getString(R.string.info_update_mirror_auto)
                                    } else {
                                        context.getString(R.string.info_update_mirror_single)
                                    },
                                )
                            } else {
                                stringResource(R.string.info_update_via_direct)
                            },
                        )
                        Spacer(Modifier.height(14.dp))
                        /*
                         * 只在这一档给按钮。
                         *
                         * 「已是最新」给了没处可去，「本机更新」给了是骗人 ——
                         * 那两个版本号说明用户装的就是更新的构建，让他去装个更旧的
                         * 没有任何道理。空状态给下一步动作，但不等于每一档都给。
                         *
                         * 按钮要能在下载途中切换成暂停/继续/取消：自更新是几十 MB 的
                         * 文件，用户按下之后唯一能做的事就是盯着它下完或者停掉它。
                         * 原来这里只有 `enabled = !busy`，于是整个下载过程在界面上
                         * 是一个按不动的按钮，只能退回通知栏去停 —— 而这一页自己
                         * 触发的下载，用户没理由还要再跳出去找那个出口。
                         */
                        val mine = installing
                        val running = download.assetNameOrNull == mine
                        val paused = (download as? DownloadCenter.Progress.Paused)?.assetName == mine
                        val busy = mine != null

                        if (!busy) {
                            GhostButton(
                                text = stringResource(R.string.info_update_go),
                                onClick = {
                                    // 包已经在检查结果里了，这里不再发任何请求：
                                    // 再查一次就是给「提示的版本」和「下载的版本」
                                    // 制造分岔的机会（见 picked 的注释）。
                                    val asset = picked
                                    if (asset?.downloadUrl.isNullOrBlank()) {
                                        installError = context.getString(R.string.info_update_no_asset)
                                        return@GhostButton
                                    }
                                    installError = null
                                    installed = false
                                    // 重新开始就是否定了上一轮的取消 ——
                                    // 否则「已取消」会一直挂在结果区，看着像刚失败过
                                    cancelled = false
                                    installing = asset.name
                                    DownloadCenter.enqueue(
                                        context,
                                        asset.downloadUrl,
                                        asset,
                                        repo.selfRepo,
                                    )
                                },
                                icon = FiDownload,
                            )
                        } else {
                            // 进行中：暂停/继续 + 取消。两者都要，因为「暂停」保住
                            // .part 可以续传，而「取消」要把半截文件删掉、真的停下来。
                            val st = download
                            val pctText = when (st) {
                                is DownloadCenter.Progress.Running ->
                                    if (st.total > 0) {
                                        "${(st.bytes * 100 / st.total).toInt()}% · " +
                                            "${Env.formatSize(st.bytes / 1_048_576.0)} / " +
                                            Env.formatSize(st.total / 1_048_576.0) +
                                            (if (st.speed.isNotBlank()) " · ${st.speed}" else "")
                                    } else {
                                        Env.formatSize(st.bytes / 1_048_576.0)
                                    }

                                is DownloadCenter.Progress.Paused ->
                                    context.getString(
                                        R.string.info_update_paused_at,
                                        Env.formatSize(st.saved / 1_048_576.0),
                                    )

                                DownloadCenter.Progress.Verifying ->
                                    stringResource(R.string.install_stage_verifying)

                                else -> ""
                            }
                            if (pctText.isNotBlank()) {
                                Text(pctText, style = MonoMeta, color = p.ink4)
                                Spacer(Modifier.height(10.dp))
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                GhostButton(
                                    text = stringResource(
                                        if (paused) R.string.download_resume else R.string.download_pause,
                                    ),
                                    onClick = {
                                        val name = mine ?: return@GhostButton
                                        if (paused) {
                                            // 继续 = 重新入队：`.part` 还在盘上，
                                            // 服务会用 Range 请求接着往下写。
                                            val asset = picked
                                            if (asset?.downloadUrl.isNullOrBlank()) {
                                                installError = context.getString(R.string.info_update_no_asset)
                                                return@GhostButton
                                            }
                                            DownloadCenter.resume(context, asset.downloadUrl, asset, repo.selfRepo)
                                        } else {
                                            DownloadCenter.pause(context, name)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                GhostButton(
                                    text = stringResource(R.string.download_cancel),
                                    onClick = {
                                        val name = mine ?: return@GhostButton
                                        DownloadCenter.cancel(context, name)
                                        // 终局不一定还回来（服务可能正被杀），
                                        // 本地先放手，否则按钮永远卡在「下载中」。
                                        installing = null
                                        cancelled = true
                                        installError = null
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (installError != null) {
                            Spacer(Modifier.height(10.dp))
                            InfoPara(stringResource(R.string.info_update_install_failed, installError!!))
                        }
                        if (cancelled) {
                            Spacer(Modifier.height(10.dp))
                            InfoPara(stringResource(R.string.download_cancelled))
                        }
                        if (installed) {
                            Spacer(Modifier.height(10.dp))
                            InfoPara(stringResource(R.string.info_update_installed))
                        }
                    }
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
