package com.timelordtty.mydca.auth

import android.content.Context
import com.timelordtty.mydca.core.security.AesGcmSecretCipher
import com.timelordtty.mydca.core.security.AndroidKeystoreAesKey
import com.timelordtty.mydca.core.security.EncryptedPayload

/**
 * 使用 Android Keystore AES-GCM 密钥加密 Token，SharedPreferences 仅保存密文和随机 IV。
 * 加解密本身复用 `core/security` 的通用 AES-GCM 实现，避免多处重复实现加密细节。
 */
class KeystoreTokenStore(context: Context) : TokenStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val cipher = AesGcmSecretCipher { AndroidKeystoreAesKey.getOrCreate(KEY_ALIAS) }

    override fun read(): String? {
        val encrypted = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        val iv = preferences.getString(KEY_IV, null) ?: return null
        return cipher.decrypt(EncryptedPayload(ciphertext = encrypted, iv = iv))?.takeIf { it.isNotBlank() }
    }

    override fun write(token: String) {
        require(token.isNotBlank()) { "Token 不能为空" }
        val payload = cipher.encrypt(token)
        preferences.edit()
            .putString(KEY_CIPHERTEXT, payload.ciphertext)
            .putString(KEY_IV, payload.iv)
            .apply()
    }

    override fun clear() {
        preferences.edit().remove(KEY_CIPHERTEXT).remove(KEY_IV).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "secure_auth_session"
        const val KEY_CIPHERTEXT = "access_token_ciphertext"
        const val KEY_IV = "access_token_iv"
        const val KEY_ALIAS = "mydca_android_access_token"
    }
}
