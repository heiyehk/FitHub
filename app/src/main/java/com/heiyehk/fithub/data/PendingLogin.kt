package com.heiyehk.fithub.data

import android.content.Context
import com.heiyehk.fithub.data.remote.DeviceCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 正在等授权的那张设备码，跨进程重启活下来。
 *
 * 为什么必须落盘：Device Flow 的关键动作发生在**别的应用里** —— 用户要切到浏览器
 * 去输这 8 位码。在那段时间里系统完全可能把我们的进程杀掉（真机上尤其明显，
 * 浏览器本身又不小），回来时内存里的登录状态全没了，用户只能从头再来，
 * 白白浪费一个 15 分钟才过期的设备码。
 *
 * 落盘的**不是机密**：device_code 只对拿着同一个 client_id 的一方才有用，
 * 而且过期后 GitHub 就不认了。拿到 token 或者码一过期都立刻删掉。
 *
 * 存 filesDir 而不是 cacheDir：这是「未完成的用户操作」，不是可重建的缓存，
 * 系统清缓存时不该顺手把它清掉（清了也只是让用户重输一遍码，不算 bug，但没理由丢）。
 */
object PendingLogin {

    private const val FILE = "pending_login"

    private val json = Json { ignoreUnknownKeys = true }

    private fun store(context: Context) = LocalStore(context.filesDir, json)

    /**
     * 记下当前正在等授权的码。落盘失败不影响登录本身，只是退回到「进程被杀就要重来」。
     */
    fun save(context: Context, code: DeviceCode, deadlineMs: Long) {
        runCatching {
            store(context).write(
                FILE,
                Pending(
                    deviceCode = code.deviceCode,
                    userCode = code.userCode,
                    verifyUrl = code.verificationUri,
                    intervalSec = code.intervalSec,
                    deadlineMs = deadlineMs,
                ),
            )
        }
    }

    /**
     * 取出还没过期的码。
     *
     * 过期的一律返回 null 并顺手删掉：留着它只会在下次进来时显示一个已经作废的码，
     * 用户照着输只会拿到 `expired_token`，那比一开始就没这个码更让人困惑。
     */
    fun take(context: Context): Pending? {
        val p = runCatching { store(context).read<Pending>(FILE, Pending()) }.getOrNull() ?: return null
        if (p.deviceCode.isBlank()) return null
        if (p.deadlineMs <= System.currentTimeMillis()) {
            clear(context)
            return null
        }
        return p
    }

    /** 拿到 token、被拒绝、码过期 —— 任何终局都要清，否则下次进来会接着轮询一张废码 */
    fun clear(context: Context) {
        runCatching { store(context).remove(FILE) }
    }

    @Serializable
    data class Pending(
        val deviceCode: String = "",
        val userCode: String = "",
        val verifyUrl: String = "",
        val intervalSec: Int = 5,
        val deadlineMs: Long = 0,
    ) {
        /** 恢复成一个 [DeviceCode] 喂回轮询循环；[expiresInSec] 由剩下的时间反推 */
        fun toDeviceCode(nowMs: Long = System.currentTimeMillis()): DeviceCode = DeviceCode(
            deviceCode = deviceCode,
            userCode = userCode,
            verificationUri = verifyUrl,
            expiresInSec = ((deadlineMs - nowMs) / 1000L).coerceAtLeast(0),
            intervalSec = intervalSec.coerceAtLeast(1),
        )
    }
}
