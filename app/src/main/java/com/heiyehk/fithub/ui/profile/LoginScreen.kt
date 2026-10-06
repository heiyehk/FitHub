package com.heiyehk.fithub.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heiyehk.fithub.R
import com.heiyehk.fithub.data.Explain
import com.heiyehk.fithub.data.PendingLogin
import com.heiyehk.fithub.data.remote.DeviceFlowClient
import com.heiyehk.fithub.data.remote.DeviceResult
import com.heiyehk.fithub.data.remote.PollResult
import com.heiyehk.fithub.ui.components.CircularProgress
import com.heiyehk.fithub.ui.components.PrimaryButton
import com.heiyehk.fithub.ui.icons.FiAlert
import com.heiyehk.fithub.ui.icons.FiCheck
import com.heiyehk.fithub.ui.icons.FiExternal
import com.heiyehk.fithub.ui.icons.FiShield
import com.heiyehk.fithub.ui.theme.FitTheme
import com.heiyehk.fithub.ui.theme.FitTone
import com.heiyehk.fithub.ui.theme.FitTypography
import com.heiyehk.fithub.ui.theme.MonoMeta
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 登录流程的状态。
 *
 * 每一种终局都有自己独立的状态，**没有「成功/失败」两个值**：
 * 用户在 GitHub 上点取消、码过期、拿不到令牌、网络出错 —— 这四件事要给的
 * 提示和下一步都不一样。混成一个 error 就只能靠猜。
 */
sealed interface LoginState {
    /** 还没开始。client_id 没编进包里时也停在这里，由 [LoginScreen] 自己说明 */
    data object Idle : LoginState

    /** 正在向 GitHub 申请设备码 */
    data object Requesting : LoginState

    /**
     * 拿到了设备码，等用户在浏览器里输入。
     *
     * 存的是**截止时刻**而不是「剩余秒数」—— 存剩余秒数的话它会被冻结在申请
     * 那一刻，界面上一直显示同一个倒计时，看起来还在计时其实早就不准了。
     *
     * [polling] 只影响副标题，**不换掉整个 state**：换掉的话设备码就从界面上消失，
     * 而用户在这期间正要切到浏览器照着输，码一没就只能靠记忆补。
     */
    data class ShowCode(
        val userCode: String,
        val verifyUrl: String,
        val deadlineMs: Long,
        val polling: Boolean = false,
        /**
         * 连续几次传输层失败（DNS/连接/超时）。0 表示网络正常。
         *
         * 单独记一路而不是直接进 [Failed]：这些失败**自己会好**，用户此刻多半正
         * 在浏览器里输码，我们把这张码从界面上撤走只会逼他重头来一次。
         */
        val offlineAttempts: Int = 0,
    ) : LoginState {
        val remainSec: Long get() = ((deadlineMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
    }

    /** 用户已在浏览器里授权，正在轮询换 token */
    data object Waiting : LoginState

    /**
     * 失败。[reason] 是领域层给的 [Explain]：我们自己写的那句是纯资源 ID，
     * 带第三方原文（Ktor / GitHub）的则把原文塞在 [Explain.args] 里。
     */
    data class Failed(val reason: Explain) : LoginState

    /** 用户在 GitHub 的授权页点了取消 */
    data object Denied : LoginState

    /** 设备码过期（GitHub 那边 15 分钟） */
    data object Expired : LoginState

    /** 换到 token 了 */
    data class Granted(val login: String?) : LoginState
}

/**
 * 用 GitHub Device Flow 登录。
 *
 * 选它而不是网页授权 + 回调：Device Flow 是 GitHub 原生支持的，不需要在
 * manifest 里配 intent-filter、不用处理自定义 scheme 被别的应用抢注的问题。
 * 代价就是这一步要用户去浏览器里手输一串码。
 *
 * 整个流程是**真的**在轮询 —— [DeviceFlowClient] 每次都对
 * `POST /login/oauth/access_token` 发请求，没有本地假状态。
 */
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    client: DeviceFlowClient,
    /** 换到 token 后落盘 */
    onToken: (String) -> Unit,
    /** 落盘后再去查一下是谁登录了，失败也不影响登录本身 */
    onResolveLogin: suspend (String) -> String?,
    onOpenUrl: (String) -> Unit,
) {
    val p = FitTheme.palette
    val scope = rememberCoroutineScope()
    // explain() 不是 @Composable，拿 Context 而不是资源 ID 来翻译
    val context = LocalContext.current
    // 保留 MutableState 本身而不只用 `by` 取值：Compose 编译器对 `::state` 这种
    // 可调用引用还不支持（报 "References to variables aren't supported yet"），
    // 轮询要写状态时得拿得到它。
    // 必须是 `var ... by`：委托同时提供读写，`val ... by` 是不可重新赋值的。
    val stateHolder = remember { mutableStateOf<LoginState>(LoginState.Idle) }
    var state by stateHolder

    // 倒计时每秒走一次。到点就停 —— 真正的过期由轮询循环判定，这里只管显示
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(state) {
        if (state is LoginState.ShowCode) {
            while (true) {
                delay(1000)
                tick++
            }
        }
    }
    val code = state as? LoginState.ShowCode

    // 「已复制」提示，2 秒后自己退回「复制代码」。用状态而不是 toast：
    // 复制之后用户马上要切到浏览器，toast 一闪而过看不到。
    var copied by remember { mutableStateOf(false) }
    val onCopied: () -> Unit = {
        copied = true
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    // 用户去浏览器输码的这段时间里，进程被杀是很常见的（浏览器自己就不小）。
    // 落盘的设备码让这里能把轮询接上，而不是让他对着刚输完的码被告知「从头再来」。
    //
    // 只在进入这个页面时做一次 —— 挂 Unit 而不是 state，否则每次重组都会重启一个轮询循环。
    LaunchedEffect(Unit) {
        val pending = PendingLogin.take(context)
        if (pending != null) {
            val c = pending.toDeviceCode()
            state = LoginState.ShowCode(
                userCode = c.userCode,
                verifyUrl = c.verificationUri,
                deadlineMs = pending.deadlineMs,
                polling = true,
            )
            copyCode(context, c.userCode) { onCopied() }
            pollForToken(client, c, stateHolder, onToken, onResolveLogin) {
                PendingLogin.clear(context)
            }
        }
    }

    InfoScreen(stringResource(R.string.login_title), onBack) {
        // client_id 是构建期注入的。拿不到就不是「登录失败」，而是这个包根本没编进去 ——
        // 两者必须分开说，否则用户会以为自己输错了码或者网络有问题。
        //
        // 用 if/else 而不是提前 return：InfoScreen 的 content 是普通（可空）lambda，
        // 里面 return 会编译不过。
        if (!client.isConfigured) {
            InfoHeading(stringResource(R.string.login_nocfg_h))
            InfoPara(stringResource(R.string.login_nocfg_desc))
            InfoPara(stringResource(R.string.login_nocfg_howto))
            InfoNote(stringResource(R.string.login_nocfg_note))
        } else {

        when (val s = state) {
            is LoginState.Idle -> {
                InfoHeading(stringResource(R.string.login_how_h))
                InfoPara(stringResource(R.string.login_how_steps))
                InfoPara(stringResource(R.string.login_how_scope))
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = stringResource(R.string.login_start),
                    icon = FiShield,
                    height = 48.dp,
                    onClick = {
                        state = LoginState.Requesting
                        scope.launch {
                            when (val r = requestCodeWithRetry(client)) {
                                is DeviceResult.Ok -> {
                                    val c = r.value
                                    state = LoginState.ShowCode(
                                        userCode = c.userCode,
                                        verifyUrl = c.verificationUri,
                                        deadlineMs = System.currentTimeMillis() + c.expiresInSec * 1000L,
                                    )
                                    // 自动进剪贴板：用户下一步就是切到浏览器粘贴，
                                    // 让他再回来长按选中 8 位代码是白添一步
                                    copyCode(context, c.userCode) { onCopied() }
                                    // 落盘：切去浏览器那段时间进程最容易被杀，回来要能接上
                                    PendingLogin.save(context, c, System.currentTimeMillis() + c.expiresInSec * 1000L)
                                    pollForToken(client, c, stateHolder, onToken, onResolveLogin) {
                                        PendingLogin.clear(context)
                                    }
                                }
                                is DeviceResult.Failed -> state = LoginState.Failed(r.reason)
                                DeviceResult.NotConfigured -> state = LoginState.Failed(
                                    Explain.of(R.string.login_err_not_configured),
                                )
                                // 走不到这里：requestCodeWithRetry 会把可重试的那类
                                // 自己在内部消化掉，交出来的只可能是终局。留这一支
                                // 是为了让编译器看到穷尽，真到了也照实显示原因。
                                is DeviceResult.Unreachable -> state = LoginState.Failed(r.reason)
                            }
                        }
                    },
                )
            }

            is LoginState.Requesting -> Busy(stringResource(R.string.login_busy_request))

            is LoginState.ShowCode -> {
                InfoHeading(stringResource(R.string.login_code_h))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(p.wash)
                        // 整块可点：这是要用户照着敲的 8 位代码，敲不进去就只能手抄，
                        // 而这块在按钮上方，页面一滚就没了
                        .clickable { copyCode(context, s.userCode, onCopied) }
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // 等宽大字：字距和对比度不够会敲错
                    Text(
                        s.userCode,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 32.sp,
                        letterSpacing = 4.sp,
                        color = p.ink,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(p.accentTint)
                        .clickable { onOpenUrl(s.verifyUrl) }
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(
                            R.string.login_open_url,
                            // 显示时去掉查询串、打开时用完整链接：`skip_account_picker`
                            // 只是让浏览器别再弹一次选账号，把它摊在按钮上会变成 55 个字符，
                            // 手机上必然换行把按钮撑高。地址本身没变，说的还是同一个地方。
                            s.verifyUrl.substringBefore('?').removePrefix("https://"),
                        ),
                        style = FitTypography.titleSmall,
                        color = p.ink,
                        // Enterprise 的域名可能很长：宁可省略号截断，也不要把布局撑坏
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(FiExternal, null, tint = p.ink, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.height(10.dp))
                // 独立复制按钮：不点上面那块大字也能复制
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(p.wash)
                        .clickable { copyCode(context, s.userCode, onCopied) }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (copied) {
                            stringResource(R.string.login_code_copied)
                        } else {
                            stringResource(R.string.login_code_copy)
                        },
                        style = FitTypography.titleSmall,
                        color = if (copied) p.accent else p.ink2,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(FiCheck, null, tint = if (copied) p.accent else p.ink2, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.height(12.dp))
                InfoNote(
                    stringResource(
                        R.string.login_code_countdown,
                        s.remainSec / 60,
                        s.remainSec % 60,
                    ),
                )

                Spacer(Modifier.height(18.dp))
                // 轮询中也要留着码：用户可能还在浏览器里没输完，或者输错了要重看
                Busy(
                    stringResource(
                        if (s.polling) R.string.login_busy_polling else R.string.login_busy_prompt,
                    )
                )
                // 网络在抖，明确告诉用户在重试、第几次，而不是让转圈停在那里让人以为卡死
                if (s.offlineAttempts > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(
                            R.string.login_offline_retry,
                            s.offlineAttempts,
                            MAX_OFFLINE_ATTEMPTS,
                        ),
                        style = FitTypography.bodySmall,
                        color = p.toneFg(FitTone.Warn),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.login_cancel_hint),
                    style = FitTypography.bodySmall,
                    color = p.ink4,
                )
            }

            is LoginState.Waiting -> Busy(stringResource(R.string.login_busy_waiting))

            is LoginState.Granted -> {
                InfoHeading(stringResource(R.string.login_granted_h))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(FiCheck, null, tint = p.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        s.login?.let { stringResource(R.string.login_granted_as, it) }
                            ?: stringResource(R.string.login_granted),
                        style = FitTypography.titleSmall,
                        color = p.ink,
                    )
                }
                InfoPara(stringResource(R.string.login_granted_quota))
            }

            is LoginState.Denied -> Terminal(
                tone = FitTone.Warn,
                title = stringResource(R.string.login_denied_h),
                body = stringResource(R.string.login_denied_b),
            )

            is LoginState.Expired -> Terminal(
                tone = FitTone.Warn,
                title = stringResource(R.string.login_expired_h),
                body = stringResource(R.string.login_expired_b),
            )

            is LoginState.Failed -> Terminal(
                tone = FitTone.Bad,
                title = stringResource(R.string.login_failed_h),
                body = explain(context, s.reason),
            )
        }

        Spacer(Modifier.height(20.dp))
        // 只在「已经结束、可以重来」时给这个入口。请求还在飞或者正在等授权时
        // 给「重来一次」毫无意义 —— 用户点了只会多发一串设备码。
        if (state is LoginState.Denied ||
            state is LoginState.Expired ||
            state is LoginState.Failed
        ) {
            Text(
                stringResource(R.string.login_retry),
                style = FitTypography.titleSmall,
                color = p.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { state = LoginState.Idle }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        } // end else(client.isConfigured)
    }
}

/**
 * 连续多少次传输层失败之后才认输。
 *
 * 5 次 × 默认 5 秒间隔 ≈ 25 秒，够扛住一次切后台、一次网络切换、DNS 重新解析。
 * 无限重试会让真正的故障永远停在转圈；一次就放弃又会重演这个 bug。
 */
private const val MAX_OFFLINE_ATTEMPTS = 5

/**
 * 申请设备码，传输层失败自动退避重试。
 *
 * 为什么这一步也要重试：连不上 GitHub 和「GitHub 拒绝了这个 client_id」
 * 本来就该分开，但之前它们共用一条终局路径 —— 一次 DNS 抖动就把用户推到
 * 「失败」页，要他自己再点一次「Start sign-in」。移动网络上这种抖动很常见，
 * 让程序自己扛过去比让用户重按一次更靠谱。
 *
 * 重试用完仍失败就返回最后一个原因，不再无限重试 —— 真的配错了（比如
 * client_id 没编进去）时无限重试只会让人对着转圈干等。
 */
private suspend fun requestCodeWithRetry(
    client: DeviceFlowClient,
): DeviceResult<com.heiyehk.fithub.data.remote.DeviceCode> {
    var last: DeviceResult.Unreachable? = null
    repeat(MAX_CODE_ATTEMPTS) { attempt ->
        when (val r = client.requestCode()) {
            is DeviceResult.Unreachable -> {
                last = r
                if (attempt < MAX_CODE_ATTEMPTS - 1) delay(CODE_RETRY_DELAY_MS * (attempt + 1))
            }
            // 成功 / 终局失败 / 没配置：都没有再试的余地，直接交出去
            else -> return r
        }
    }
    return last ?: DeviceResult.Failed(Explain.of(R.string.login_err_generic))
}

/** 申请设备码最多试 3 次（首次 + 2 次重试），约 6 秒内覆盖一次短暂的网络抖动 */
private const val MAX_CODE_ATTEMPTS = 3
private const val CODE_RETRY_DELAY_MS = 2_000L

/** 轮询直到拿到令牌或出终局。
 *
 * `slow_down` 是在告诉我们「你问得太快了」，必须**按它给的数字加长间隔**，
 * 不能自作聪明固定重试 —— 那是 GitHub 明确要求配合的限流信号。
 *
 * 三类失败要分开，这是这个 bug 的全部要害：
 * - **传输层失败**（[PollResult.Unreachable]）自己会好，退避后重试，码留在界面上
 * - **GitHub 的 wire error**（拒绝/过期/配置错）重试也没用，当场出终局
 * - **取消**根本不是失败，由 [DeviceFlowClient] 原样抛出，不会走到这里
 */
private suspend fun pollForToken(
    client: DeviceFlowClient,
    code: com.heiyehk.fithub.data.remote.DeviceCode,
    setState: MutableState<LoginState>,
    onToken: (String) -> Unit,
    onResolveLogin: suspend (String) -> String?,
    onFinished: () -> Unit,
) {
    var interval = code.intervalSec
    val deadline = System.currentTimeMillis() + code.expiresInSec * 1000L
    // 记住当前 ShowCode，只改 polling / offline 标志 —— 换整个 state 会让设备码从界面上消失
    fun showCode(polling: Boolean, offline: Int) = LoginState.ShowCode(
        userCode = code.userCode,
        verifyUrl = code.verificationUri,
        deadlineMs = deadline,
        polling = polling,
        offlineAttempts = offline,
    )
    var offline = 0
    while (System.currentTimeMillis() < deadline) {
        delay(interval * 1000L)
        when (val r = client.pollForToken(code.deviceCode, interval)) {
            is PollResult.Pending -> {
                interval = r.nextIntervalSec
                offline = 0
                setState.value = showCode(polling = true, offline = 0)
            }

            is PollResult.Unreachable -> {
                offline++
                if (offline >= MAX_OFFLINE_ATTEMPTS) {
                    setState.value = LoginState.Failed(r.reason)
                    onFinished()
                    return
                }
                // 网络没通，但用户可能正在浏览器里输码 —— 码必须留在屏幕上，
                // 他输完回来时这边应该已经自己接上了，而不是让他重新申请一张。
                setState.value = showCode(polling = true, offline = offline)
            }

            is PollResult.Granted -> {
                onToken(r.token)
                onFinished()
                // 查用户名只为显示；查不到不影响「已登录」这个事实，不该因此报失败
                setState.value = LoginState.Granted(onResolveLogin(r.token))
                return
            }

            PollResult.Denied -> {
                setState.value = LoginState.Denied
                onFinished()
                return
            }

            PollResult.Expired -> {
                setState.value = LoginState.Expired
                onFinished()
                return
            }

            is PollResult.Failed -> {
                setState.value = LoginState.Failed(r.reason)
                onFinished()
                return
            }

            PollResult.NotConfigured -> {
                setState.value = LoginState.Failed(Explain.of(R.string.login_err_not_configured))
                onFinished()
                return
            }
        }
    }
    // 轮询循环自己走完 = 码在等待期间过期了
    setState.value = LoginState.Expired
    onFinished()
}

/**
 * 把设备码写进系统剪贴板。
 *
 * 为什么必须有这个：设备码显示在页面上方，往下滚去看倒计时和轮询状态时它就出屏了，
 * 用户只能来回滚着抄 8 个字符。而且下一步就是切到浏览器粘贴，回 App 长按选中是白添一步。
 * 申请到码就自动复制一次，之后仍然可以点。
 *
 * Android 13+ 系统会弹「已粘贴」的系统提示，那个由系统负责，这里不重复提示。
 */
private fun copyCode(context: Context, code: String, onDone: () -> Unit = {}) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("FitHub device code", code))
    onDone()
}

/**
 * 把领域层给出的失败原因翻成人话。
 *
 * [x.args] 里带第三方原文时（Ktor 抛的 `Connection reset by peer`、GitHub 的
 * `incorrect_client`），按原文判类别 —— 只有拿到完整原文才能分清是超时、DNS 还是
 * 证书问题。但**不能只留人话**：出问题时没人能查，所以原始信息照样附在后面。
 *
 * 不带原文时就是我们自己写的一句，直接显示，别再套一层「请求失败」。
 *
 * 这个函数因此保持非 @Composable，由调用方从 `LocalContext.current` 把 Context 传进来。
 */
private fun explain(context: Context, x: Explain): String {
    val raw = x.args.filterIsInstance<String>().firstOrNull()
        ?: return context.getString(x.res)
    val t = raw.lowercase()
    val whyRes = when {
        "incorrect_client" in t ->
            R.string.login_err_incorrect_client

        "connection reset" in t || "timeout" in t ->
            R.string.login_err_network

        // "unresolvedaddressexception" 这条是补上去的：DNS 解析失败时
        // UnresolvedAddressException.getMessage() 返回 null，所以传上来的原文只有类名，
        // 之前两条判据永远匹配不上，用户看到的是「换取 token 失败」而不是「连不上」。
        "unable to resolve" in t || "unknownhost" in t || "unresolvedaddress" in t ->
            R.string.login_err_dns

        "ssl" in t || "certificate" in t ->
            R.string.login_err_ssl

        "device_flow_disabled" in t ->
            R.string.login_err_device_flow_disabled

        else -> R.string.login_err_generic
    }
    return context.getString(whyRes) + "\n\n" + context.getString(R.string.login_err_raw, raw)
}

@Composable
private fun Busy(text: String) {
    val p = FitTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgress(0f, size = 15.dp, color = p.accent, indeterminate = true)
        Spacer(Modifier.width(10.dp))
        Text(text, style = FitTypography.bodyMedium, color = p.ink3)
    }
}

@Composable
private fun Terminal(tone: FitTone, title: String, body: String) {
    val p = FitTheme.palette
    InfoHeading(title)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.toneBg(tone)).padding(14.dp)) {
        Icon(FiAlert, null, tint = p.toneFg(tone), modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            body,
            style = FitTypography.bodyMedium,
            color = p.ink2,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
        )
    }
}
