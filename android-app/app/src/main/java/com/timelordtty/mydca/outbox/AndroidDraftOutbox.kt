package com.timelordtty.mydca.outbox

import android.content.Context
import com.timelordtty.mydca.core.security.AesGcmSecretCipher
import com.timelordtty.mydca.core.security.AndroidKeystoreAesKey
import com.timelordtty.mydca.core.security.SharedPreferencesKeyValueStore

/**
 * Android 侧 Outbox 装配入口。
 *
 * 复用与登录 Token 同一套 Android Keystore AES-GCM 能力，只是使用独立密钥别名；
 * 队列负载加密后写入独立偏好设置文件，明文不落盘。
 */
object AndroidDraftOutbox {
    private const val KEY_ALIAS = "mydca_android_draft_outbox"

    fun createQueue(
        context: Context,
        onDraftCreated: (DraftOutboxEntry, Long) -> Unit = { _, _ -> },
    ): DraftOutboxQueue = DraftOutboxQueue(
        storage = EncryptedDraftOutboxStorage(
            cipher = AesGcmSecretCipher { AndroidKeystoreAesKey.getOrCreate(KEY_ALIAS) },
            store = SharedPreferencesKeyValueStore.create(context, EncryptedDraftOutboxStorage.PREFERENCES_NAME),
        ),
        onDraftCreated = onDraftCreated,
    )
}
