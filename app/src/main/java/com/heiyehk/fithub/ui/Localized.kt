package com.heiyehk.fithub.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.heiyehk.fithub.R
import com.heiyehk.fithub.data.Env
import com.heiyehk.fithub.data.Explain
import com.heiyehk.fithub.data.ResArg

/**
 * 渲染领域层产出的 [Explain]。
 *
 * 嵌套的 [ResArg] 先解析成文本再交给 `String.format`。只支持一层，但领域层目前的
 * 用法（「签名为%5$s」里嵌一个签名标签）就这一层，够用。
 */
@Composable
fun explainText(x: Explain): String {
    if (x.args.isEmpty()) return stringResource(x.res)
    val args: List<Any> = x.args.map { a -> if (a is ResArg) stringResource(a.res) else a }
    return stringResource(x.res, *args.toTypedArray())
}

/**
 * 非 Composable 场合的同名能力，给 [android.content.Context] 已经拿到的地方用
 * （分享文案、前台服务通知、PackageInstaller 结果回调）。
 */
fun explainText(context: Context, x: Explain): String {
    if (x.args.isEmpty()) return context.getString(x.res)
    val args: List<Any> = x.args.map { a ->
        if (a is ResArg) context.getString(a.res) else a
    }
    return context.getString(x.res, *args.toTypedArray())
}

/** 去掉富文本标记，供分享、复制这类不接受标签的场合使用 */
fun stripMarkup(s: String): String =
    s.replace("**", "").replace(Regex("<[^>]+>"), "")

/**
 * 本机名。[DeviceProfile.name] 拿不到品牌型号时是空串（见 `detect()`），
 * 显示时在这里兜底成可翻译的「本机」。
 */
fun deviceName(context: Context): String =
    Env.device.name.ifBlank { context.getString(R.string.this_device) }

@Composable
fun deviceName(): String = deviceName(LocalContext.current)

/**
 * 相对时间。资源 ID 与天数参数都来自 [Env]，这里只负责按当前语言拼出来。
 */
@Composable
fun ageText(date: String): String {
    val res = Env.agoRes(date)
    val days = Env.agoDays(date)
    return if (days > 1) stringResource(res, days) else stringResource(res)
}

fun ageText(context: Context, date: String): String {
    val res = Env.agoRes(date)
    val days = Env.agoDays(date)
    return if (days > 1) context.getString(res, days) else context.getString(res)
}

/**
 * 分享用的结论摘要：只取第一句，并剥掉加粗标记。
 *
 * 句号按当前语言取：中文是「。」，英文是「.」。写死「。」的话，英文下
 * `substringBefore` 找不到分隔符，会把整段结论原样塞进分享文本。
 */
fun shareSummary(context: Context, x: Explain): String {
    val full = stripMarkup(explainText(context, x))
    val lang = context.resources.configuration.locales[0].language
    val sep = if (lang == "zh") '。' else '.'
    return full.substringBefore(sep).trim().ifBlank { context.getString(R.string.share_no_verdict) }}
