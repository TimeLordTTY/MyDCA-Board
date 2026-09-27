package com.timelordtty.mydca.outbox

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.core.security.AesGcmSecretCipher
import com.timelordtty.mydca.core.security.SecretCipher
import com.timelordtty.mydca.core.security.SecureKeyValueStore
import com.timelordtty.mydca.data.dto.AccountingIntentDto
import com.timelordtty.mydca.data.dto.DraftFromIntentResponseDto
import com.timelordtty.mydca.data.dto.DraftLedgerEntryDto
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.KeyGenerator

/** 固定时钟基准，避免单元测试依赖真实时间。 */
internal const val FIXED_NOW = 1_760_000_000_000L

internal fun accountingIntent(
    sourceType: String = "APP_FORM",
    sourceRef: String = "android-ocr-request-1",
    rawInput: String = "早餐 18 元 微信支付",
): AccountingIntentDto = AccountingIntentDto(
    sourceType = sourceType,
    sourceRef = sourceRef,
    rawInput = rawInput,
    txnType = "EXPENSE",
    amount = 18.0,
)

internal fun draftResponse(draftId: Long, status: String? = "DRAFT"): DraftFromIntentResponseDto =
    DraftFromIntentResponseDto(
        intent = null,
        draft = DraftLedgerEntryDto(id = draftId, status = status),
    )

internal fun networkFailure(cause: Throwable?, message: String = "网络连接失败，请检查网络后重试"): NetworkResult.Failure =
    NetworkResult.Failure(message, cause)

/** 只实现“创建 DRAFT”的网关测试替身；类型上没有任何 preview / confirm 入口。 */
internal class RecordingDraftGateway(
    private val respond: suspend (AccountingIntentDto) -> NetworkResult<DraftFromIntentResponseDto> =
        { NetworkResult.Success(draftResponse(draftId = 42L)) },
) : DraftCreationGateway {
    val intents = mutableListOf<AccountingIntentDto>()
    val calls: Int get() = intents.size

    override suspend fun createDraft(intent: AccountingIntentDto): NetworkResult<DraftFromIntentResponseDto> {
        intents += intent
        return respond(intent)
    }
}

internal fun incrementingIds(): () -> String {
    val counter = AtomicInteger(0)
    return { "entry-${counter.incrementAndGet()}" }
}

/** 内存键值存储替身，用于验证加密落盘内容。 */
internal class FakeSecureKeyValueStore : SecureKeyValueStore {
    val values = mutableMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(vararg keys: String) {
        keys.forEach { values.remove(it) }
    }
}

/**
 * 真实 AES-GCM 加解密（临时 JVM 密钥），用于验证加密往返与明文不落盘。
 * 同一个 cipher 实例必须在整个生命周期内使用同一把密钥，否则无法解密自己的密文。
 */
internal fun testSecretCipher(): SecretCipher {
    val key = newAesKey()
    return AesGcmSecretCipher { key }
}

internal fun newAesKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
