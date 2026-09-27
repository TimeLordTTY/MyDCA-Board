package com.timelordtty.mydca.core.security

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 加密结果：密文与随机 IV 分开保存，不把 IV 混入密文。 */
data class EncryptedPayload(
    val ciphertext: String,
    val iv: String,
)

/**
 * 对称加解密边界。
 * 生产实现的密钥必须由 Android Keystore 管理；禁止把密钥或明文写入普通偏好设置、日志或源码。
 */
interface SecretCipher {
    fun encrypt(plainText: String): EncryptedPayload

    /** 解密失败（数据损坏、密钥变更或负载非法）时返回 null，不抛出异常。 */
    fun decrypt(payload: EncryptedPayload): String?
}

/**
 * AES-GCM 加解密实现，密钥由外部注入。
 * 因此既能挂 Android Keystore 密钥，也能在 JVM 单元测试里用临时密钥做真实加解密往返验证。
 */
class AesGcmSecretCipher(
    private val keyProvider: () -> SecretKey,
) : SecretCipher {
    override fun encrypt(plainText: String): EncryptedPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return EncryptedPayload(ciphertext = encode(encrypted), iv = encode(cipher.iv))
    }

    override fun decrypt(payload: EncryptedPayload): String? {
        val ciphertext = decode(payload.ciphertext) ?: return null
        val iv = decode(payload.iv) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }

    private fun encode(value: ByteArray): String = Base64.getEncoder().encodeToString(value)

    private fun decode(value: String): ByteArray? = try {
        Base64.getDecoder().decode(value)
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
