package com.heiyehk.fithub.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 凭证加密存储。
 *
 * 密钥在 **Android Keystore** 里生成且不可导出，SharedPreferences 里只存
 * `IV + 密文` 的 base64。明文存 GitHub token 或 WebDAV 密码，等于把账号凭证
 * 放进备份和截图里。
 *
 * 没有用 `androidx.security:security-crypto` 的 `EncryptedSharedPreferences`：
 * 那套 API 在 1.1.0 已整体废弃，而 minSdk 26 的平台 Keystore 直接可用，
 * 少一个依赖也少一份 R8 负担 —— 与本项目「依赖集尽量小」的一贯取舍一致。
 *
 * Keystore 在设备重置、锁屏凭据变更或应用数据被清后可能失效。此时读出来是
 * null 而不是抛异常：调用方按未配置处理，比崩掉合理。
 */
class SecureStore(context: Context, private val alias: String) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("fithub-secrets", Context.MODE_PRIVATE)

    /** Keystore 里的 AES 密钥。不存在时现生成一把 */
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val gen = KeyGenerator.getInstance(TRANSFORMATION, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(BLOCK_MODE)
                .setEncryptionPaddings(PADDING)
                // 不要求用户解锁：凭证是登录时拿到的，每次开 App 都要求解锁
                // 会让「已登录」形同虚设
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return gen.generateKey()
    }

    fun put(name: String, value: String) {
        runCatching {
            val cipher = Cipher.getInstance("$TRANSFORMATION/$BLOCK_MODE/$PADDING")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            prefs.edit()
                .putString(name, Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP))
                .apply()
        }.onFailure { Log.w(TAG, "写 $name 失败", it) }
    }

    fun get(name: String): String? = runCatching {
        val stored = prefs.getString(name, null) ?: return null
        val raw = Base64.decode(stored, Base64.NO_WRAP)
        // IV 固定 12 字节，剩下的才是密文
        if (raw.size <= IV_LEN) return null
        val cipher = Cipher.getInstance("$TRANSFORMATION/$BLOCK_MODE/$PADDING")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, raw, 0, IV_LEN))
        cipher.doFinal(raw, IV_LEN, raw.size - IV_LEN).toString(Charsets.UTF_8).takeIf { it.isNotBlank() }
    }.getOrElse {
        Log.w(TAG, "读 $name 失败，按未配置处理", it)
        null
    }

    fun remove(name: String) {
        runCatching { prefs.edit().remove(name).apply() }
            .onFailure { Log.w(TAG, "清 $name 失败", it) }
    }

    /** 连密钥一起删。重新授权时用新密钥更干净 */
    fun destroy() {
        runCatching {
            prefs.edit().clear().apply()
            KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(alias)
        }.onFailure { Log.w(TAG, "销毁 $alias 失败", it) }
    }

    companion object {
        private const val TAG = "SecureStore"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val GCM_TAG_BITS = 128
        private const val IV_LEN = 12
    }
}
