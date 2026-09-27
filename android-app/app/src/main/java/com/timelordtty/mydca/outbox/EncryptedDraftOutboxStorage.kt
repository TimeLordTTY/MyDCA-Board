package com.timelordtty.mydca.outbox

import com.timelordtty.mydca.core.security.EncryptedPayload
import com.timelordtty.mydca.core.security.SecretCipher
import com.timelordtty.mydca.core.security.SecureKeyValueStore

/**
 * 只把密文和随机 IV 写入本地键值存储的 Outbox 存储。
 * 明文负载仅存在于内存；负载缺失或无法解密时返回空队列，不抛出异常。
 */
class EncryptedDraftOutboxStorage(
    private val cipher: SecretCipher,
    private val store: SecureKeyValueStore,
) : DraftOutboxStorage {
    override fun read(): List<DraftOutboxEntry> {
        val ciphertext = store.getString(KEY_CIPHERTEXT) ?: return emptyList()
        val iv = store.getString(KEY_IV) ?: return emptyList()
        val json = cipher.decrypt(EncryptedPayload(ciphertext = ciphertext, iv = iv)) ?: return emptyList()
        return DraftOutboxCodec.decode(json)
    }

    override fun write(entries: List<DraftOutboxEntry>) {
        if (entries.isEmpty()) {
            store.remove(KEY_CIPHERTEXT, KEY_IV)
            return
        }
        val payload = cipher.encrypt(DraftOutboxCodec.encode(entries))
        store.putString(KEY_CIPHERTEXT, payload.ciphertext)
        store.putString(KEY_IV, payload.iv)
    }

    companion object {
        const val PREFERENCES_NAME = "secure_draft_outbox"
        private const val KEY_CIPHERTEXT = "draft_outbox_ciphertext"
        private const val KEY_IV = "draft_outbox_iv"
    }
}
