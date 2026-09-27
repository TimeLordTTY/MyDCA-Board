package com.timelordtty.mydca.outbox

import com.timelordtty.mydca.data.dto.AccountingIntentDto
import com.timelordtty.mydca.notification.NotificationCandidate
import com.timelordtty.mydca.notification.NotificationDraftInput
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedDraftOutboxStorageTest {
    private val cipher = testSecretCipher()
    private val store = FakeSecureKeyValueStore()
    private val storage = EncryptedDraftOutboxStorage(cipher, store)

    @Test
    fun encryptedRoundTripRestoresQueueAfterRestart() {
        val before = DraftOutboxQueue(storage, clock = { FIXED_NOW }, idGenerator = { "entry-1" })
        before.enqueue(
            intent = accountingIntent(),
            origin = DraftOutboxOrigin.OCR,
            summary = "早餐 18 元 微信支付",
            failure = networkFailure(IOException("offline")),
        )

        assertTrue(store.values.isNotEmpty())
        assertTrue(store.values.values.none { it.contains("早餐 18 元") })
        assertTrue(store.values.values.none { it.contains("android-ocr-request-1") })

        val afterRestart = DraftOutboxQueue(
            storage = EncryptedDraftOutboxStorage(cipher, store),
            clock = { FIXED_NOW },
        )

        val restored = afterRestart.entries.value.single()
        assertEquals("entry-1", restored.id)
        assertEquals("android-ocr-request-1", restored.sourceRef)
        assertEquals("早餐 18 元 微信支付", restored.intent.rawInput)
        assertEquals(18.0, restored.intent.amount)
        assertEquals(DraftOutboxErrorCategory.RETRYABLE, restored.lastErrorCategory)
        assertEquals(DraftOutboxOrigin.OCR, restored.origin)
    }

    @Test
    fun encryptedPayloadKeepsOnlyAllowListedFields() {
        val json = DraftOutboxCodec.encode(listOf(fixtureEntry()))
        val keys = Regex("\"([A-Za-z]+)\":").findAll(json).map { it.groupValues[1] }.toSet()

        assertTrue(
            keys.containsAll(
                setOf(
                    "id", "sourceType", "sourceRef", "origin", "summary", "intent", "status",
                    "createdAt", "retryCount", "nextRetryAt", "lastAttemptAt",
                    "lastErrorCategory", "lastErrorMessage",
                ),
            ),
        )
        assertFalse(
            keys.any { key ->
                key.contains("token", ignoreCase = true) ||
                    key.contains("password", ignoreCase = true) ||
                    key.contains("cookie", ignoreCase = true) ||
                    key.contains("uri", ignoreCase = true) ||
                    key.contains("notification", ignoreCase = true)
            },
        )
    }

    @Test
    fun tokenPasswordImageUriAndFullNotificationBodyAreNeverPersisted() {
        val rawNotificationBody =
            "您的支付宝账户支出18.00元，收款方某某便利店，订单号2026092612345678，卡号6222021234567890123"
        val candidate = NotificationCandidate(
            id = "fingerprint-1",
            fingerprint = "fingerprint-1",
            packageName = "com.eg.android.AlipayGphone",
            appLabel = "支付宝",
            postedAt = FIXED_NOW,
            titleSnippet = "已支付 18.00 元",
            textSnippet = "已支付18.00元",
            amount = "18.0",
            sourceHint = "支付宝",
        )
        val queue = DraftOutboxQueue(storage, clock = { FIXED_NOW }, idGenerator = { "entry-notification" })
        val intent = AccountingIntentDto(
            sourceType = "PAYMENT_NOTIFICATION",
            sourceRef = candidate.fingerprint,
            rawInput = NotificationDraftInput.buildRawInput(candidate),
            txnType = "EXPENSE",
            amount = 18.0,
        )

        queue.enqueue(
            intent = intent,
            origin = DraftOutboxOrigin.PAYMENT_NOTIFICATION,
            summary = NotificationDraftInput.summary(candidate),
            failure = networkFailure(IOException("offline")),
        )

        val persisted = store.values.values.joinToString("|")
        assertFalse(persisted.contains(rawNotificationBody))
        assertFalse(persisted.contains("2026092612345678"))
        assertFalse(persisted.contains("6222021234567890123"))
        assertFalse(persisted.contains("content://"))
        assertFalse(persisted.contains("Bearer "))
        assertFalse(persisted.contains("password"))

        val restored = DraftOutboxQueue(storage, clock = { FIXED_NOW }).entries.value.single()
        assertEquals("fingerprint-1", restored.sourceRef)
        assertEquals(NotificationDraftInput.summary(candidate), restored.summary)
        assertTrue(restored.summary.length <= NotificationDraftInput.SUMMARY_MAX_LENGTH)
        assertFalse(restored.intent.rawInput.orEmpty().contains(rawNotificationBody))
    }

    @Test
    fun corruptedPersistedPayloadIsIgnoredInsteadOfCrashing() {
        storage.write(listOf(fixtureEntry()))
        store.values.keys.forEach { key -> store.putString(key, "broken-$$key") }

        assertTrue(storage.read().isEmpty())
    }

    @Test
    fun rotatedKeyCannotDecryptOldPayloadAndDoesNotCrash() {
        storage.write(listOf(fixtureEntry()))

        assertTrue(EncryptedDraftOutboxStorage(testSecretCipher(), store).read().isEmpty())
    }

    @Test
    fun clearingQueueAlsoClearsPersistedCiphertext() {
        val queue = DraftOutboxQueue(storage, clock = { FIXED_NOW }, idGenerator = { "entry-1" })
        queue.enqueue(
            intent = accountingIntent(),
            origin = DraftOutboxOrigin.MANUAL_TEXT,
            summary = "早餐 18 元",
            failure = networkFailure(IOException("offline")),
        )

        queue.discard(queue.entries.value.single().id)

        assertTrue(queue.entries.value.isEmpty())
        assertTrue(store.values.isEmpty())
        assertTrue(storage.read().isEmpty())
    }

    private fun fixtureEntry(): DraftOutboxEntry = DraftOutboxEntry(
        id = "entry-1",
        sourceType = "APP_FORM",
        sourceRef = "android-ocr-request-1",
        origin = DraftOutboxOrigin.OCR,
        summary = "早餐 18 元",
        intent = accountingIntent(),
        createdAt = FIXED_NOW,
        retryCount = 1,
        nextRetryAt = FIXED_NOW + 30_000L,
        lastErrorCategory = DraftOutboxErrorCategory.RETRYABLE,
        lastErrorMessage = "网络或服务端暂时不可用",
        lastAttemptAt = FIXED_NOW,
    )
}
