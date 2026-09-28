package com.timelordtty.mydca.outbox

import com.timelordtty.mydca.core.network.NetworkResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.net.SocketTimeoutException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class DraftOutboxQueueTest {
    private val storage = InMemoryDraftOutboxStorage()
    private var now = FIXED_NOW

    private fun newQueue(policy: DraftOutboxRetryPolicy = DraftOutboxRetryPolicy()): DraftOutboxQueue =
        DraftOutboxQueue(
            storage = storage,
            policy = policy,
            clock = { now },
            idGenerator = incrementingIds(),
        )

    @Test
    fun transientCreationFailureIsQueuedWithStableSourceRef() {
        val queue = newQueue()

        val entry = queue.enqueue(
            intent = accountingIntent(),
            origin = DraftOutboxOrigin.OCR,
            summary = "早餐 18 元 微信支付",
            failure = networkFailure(SocketTimeoutException("timeout")),
        )

        assertNotNull(entry)
        assertEquals(DraftOutboxStatus.PENDING, entry?.status)
        assertEquals(DraftOutboxErrorCategory.RETRYABLE, entry?.lastErrorCategory)
        assertEquals("android-ocr-request-1", entry?.sourceRef)
        assertEquals(FIXED_NOW + 30_000L, entry?.nextRetryAt)
        assertEquals(1, queue.entries.value.size)
    }

    @Test
    fun blankSourceRefIsNotQueued() {
        val queue = newQueue()

        val entry = queue.enqueue(
            intent = accountingIntent(sourceRef = "   "),
            origin = DraftOutboxOrigin.MANUAL_TEXT,
            summary = "早餐 18 元",
            failure = networkFailure(IOException("offline")),
        )

        assertNull(entry)
        assertTrue(queue.entries.value.isEmpty())
    }

    @Test
    fun sameSourceRefIsOnlyQueuedOnce() {
        val queue = newQueue()
        val intent = accountingIntent()

        val first = queue.enqueue(intent, DraftOutboxOrigin.OCR, "早餐 18 元", networkFailure(IOException("offline")))
        val second = queue.enqueue(intent, DraftOutboxOrigin.OCR, "早餐 18 元", networkFailure(IOException("offline")))

        assertEquals(first?.id, second?.id)
        assertEquals(1, queue.entries.value.size)
    }

    @Test
    fun editSurvivesRestartAndKeepsTheOriginalSourceRef() = runTest {
        val queue = newQueue()
        val entry = queue.enqueue(accountingIntent(), DraftOutboxOrigin.OCR, "姓名和卡号 123456", networkFailure(IOException("private")))!!
        queue.pauseForEdit(entry.id)
        val edited = entry.intent.copy(rawInput = "早餐 20 元", amount = 20.0)
        assertTrue(queue.updateEditedIntent(entry.id, edited))

        val restored = newQueue()
        assertEquals(DraftOutboxStatus.BLOCKED, restored.entries.value.single().status)
        assertEquals("android-ocr-request-1", restored.entries.value.single().intent.sourceRef)
        assertEquals("早餐 20 元", restored.entries.value.single().intent.rawInput)
        assertFalse(restored.entries.value.single().summary.contains("123456"))
        assertFalse(restored.entries.value.single().lastErrorMessage.orEmpty().contains("private"))
        assertEquals(0, restored.retryDueEntries(RecordingDraftGateway(), FIXED_NOW + 86_400_000L))
    }

    @Test
    fun editedCreationCompletesTheOriginalQueueItem() {
        val queue = newQueue()
        val entry = queue.enqueue(accountingIntent(), DraftOutboxOrigin.PAYMENT_NOTIFICATION, "敏感通知", networkFailure(IOException("offline")))!!
        queue.pauseForEdit(entry.id)
        queue.completeEditedCreation(entry.id, 91L, "DRAFT")
        assertTrue(queue.entries.value.isEmpty())
        assertEquals(91L, queue.lastOutcome.value?.draftId)
        assertEquals(DraftOutboxOrigin.PAYMENT_NOTIFICATION, queue.lastOutcome.value?.origin)
    }

    @Test
    fun duplicateManualRetryWhileRequestIsRunningOnlyCallsGatewayOnce() = runTest {
        val queue = newQueue()
        val entry = queue.enqueue(accountingIntent(), DraftOutboxOrigin.OCR, "摘要", networkFailure(IOException("offline")))!!
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val gateway = RecordingDraftGateway {
            started.complete(Unit)
            release.await()
            NetworkResult.Success(draftResponse(draftId = 9L))
        }
        val first = async { queue.retryEntry(entry.id, gateway) }
        started.await()
        assertFalse(queue.retryEntry(entry.id, gateway))
        release.complete(Unit)
        assertTrue(first.await())
        assertEquals(1, gateway.calls)
    }

    @Test
    fun retryRespectsBackoffThenSucceedsAndDequeues() = runTest {
        val policy = DraftOutboxRetryPolicy(backoffSeconds = listOf(30L))
        val queue = newQueue(policy)
        queue.enqueue(accountingIntent(), DraftOutboxOrigin.OCR, "早餐 18 元", networkFailure(IOException("offline")))
        val gateway = RecordingDraftGateway()

        assertEquals(0, queue.retryDueEntries(gateway))
        assertEquals(0, gateway.calls)

        now += 30_000L
        assertEquals(1, queue.retryDueEntries(gateway))

        assertTrue(queue.entries.value.isEmpty())
        assertEquals(1, gateway.calls)
        assertEquals("android-ocr-request-1", gateway.intents.single().sourceRef)
        assertEquals(42L, queue.lastOutcome.value?.draftId)
        assertEquals("DRAFT", queue.lastOutcome.value?.draftStatus)
        assertTrue(queue.lastOutcome.value?.canOpenDraft == true)
    }

    @Test
    fun retryReusesSameSourceRefAcrossTransientFailures() = runTest {
        val policy = DraftOutboxRetryPolicy(backoffSeconds = listOf(0L))
        val queue = newQueue(policy)
        queue.enqueue(accountingIntent(), DraftOutboxOrigin.MANUAL_TEXT, "早餐 18 元", networkFailure(IOException("offline")))
        val gateway = RecordingDraftGateway { NetworkResult.Failure("网络连接失败，请检查网络后重试", IOException("offline")) }

        queue.retryDueEntries(gateway)
        queue.retryDueEntries(gateway)
        val stillQueued = queue.entries.value.single()

        assertEquals(2, gateway.calls)
        assertEquals(setOf("android-ocr-request-1"), gateway.intents.mapNotNull { it.sourceRef }.toSet())
        assertEquals(2, stillQueued.retryCount)
        assertEquals(DraftOutboxStatus.PENDING, stillQueued.status)

        queue.retryEntry(stillQueued.id, RecordingDraftGateway())
        assertTrue(queue.entries.value.isEmpty())
    }

    @Test
    fun unauthorizedFailurePausesUntilUserRetries() = runTest {
        val policy = DraftOutboxRetryPolicy(backoffSeconds = listOf(0L))
        val queue = newQueue(policy)
        queue.enqueue(
            accountingIntent(),
            DraftOutboxOrigin.OCR,
            "早餐 18 元",
            networkFailure(httpError(401), "登录已失效，请重新登录"),
        )

        val entry = queue.entries.value.single()
        assertEquals(DraftOutboxStatus.AUTH_PAUSED, entry.status)
        assertEquals(DraftOutboxErrorCategory.AUTH_REQUIRED, entry.lastErrorCategory)

        now += 24 * 60 * 60 * 1000L
        val gateway = RecordingDraftGateway()
        assertEquals(0, queue.retryDueEntries(gateway))
        assertEquals(0, gateway.calls)
        assertEquals(1, queue.entries.value.size)

        // 重新登录后用户显式重试，仍复用同一 sourceRef
        assertTrue(queue.retryEntry(entry.id, gateway))
        assertTrue(queue.entries.value.isEmpty())
        assertEquals("android-ocr-request-1", gateway.intents.single().sourceRef)
    }

    @Test
    fun businessFailureIsNeverRetriedAutomaticallyButCanBeDiscarded() = runTest {
        val policy = DraftOutboxRetryPolicy(backoffSeconds = listOf(0L))
        val queue = newQueue(policy)
        queue.enqueue(
            accountingIntent(),
            DraftOutboxOrigin.MANUAL_TEXT,
            "早餐 18 元",
            networkFailure(httpError(400), "接口请求失败(400)"),
        )

        val entry = queue.entries.value.single()
        assertEquals(DraftOutboxStatus.BLOCKED, entry.status)
        assertEquals(DraftOutboxErrorCategory.BUSINESS, entry.lastErrorCategory)
        assertEquals(DraftOutboxErrorCategory.BUSINESS.label, entry.lastErrorMessage)

        now += 24 * 60 * 60 * 1000L
        val gateway = RecordingDraftGateway()
        assertEquals(0, queue.retryDueEntries(gateway))
        assertEquals(0, gateway.calls)

        assertTrue(queue.discard(entry.id))
        assertTrue(queue.entries.value.isEmpty())
        assertEquals(0, gateway.calls)
    }

    @Test
    fun automaticRetryStopsAfterTheAttemptLimit() = runTest {
        val policy = DraftOutboxRetryPolicy(maxAutoRetryAttempts = 2, backoffSeconds = listOf(0L))
        val queue = newQueue(policy)
        queue.enqueue(accountingIntent(), DraftOutboxOrigin.OCR, "早餐 18 元", networkFailure(IOException("offline")))
        val gateway = RecordingDraftGateway { NetworkResult.Failure("服务暂时不可用，请稍后重试", IOException("offline")) }

        assertEquals(1, queue.retryDueEntries(gateway))
        assertEquals(1, queue.retryDueEntries(gateway))
        assertEquals(DraftOutboxStatus.EXHAUSTED, queue.entries.value.single().status)

        now += 24 * 60 * 60 * 1000L
        assertEquals(0, queue.retryDueEntries(gateway))
        assertEquals(2, gateway.calls)
    }

    @Test
    fun existingDraftWithSameSourceRefIsTreatedAsCreatedAndDequeued() = runTest {
        val policy = DraftOutboxRetryPolicy(backoffSeconds = listOf(0L))
        val queue = newQueue(policy)
        queue.enqueue(accountingIntent(), DraftOutboxOrigin.OCR, "早餐 18 元", networkFailure(IOException("offline")))
        val gateway = RecordingDraftGateway { NetworkResult.Success(draftResponse(draftId = 88L, status = "CONFIRMED")) }

        assertEquals(1, queue.retryDueEntries(gateway))

        assertTrue(queue.entries.value.isEmpty())
        val outcome = queue.lastOutcome.value
        assertEquals(88L, outcome?.draftId)
        assertFalse(outcome?.canOpenDraft == true)
        assertTrue(outcome?.showsExistingDecidedDraft == true)
    }

    @Test
    fun queueIsRestoredAfterProcessRestart() {
        val before = newQueue()
        before.enqueue(accountingIntent(), DraftOutboxOrigin.OCR, "早餐 18 元", networkFailure(IOException("offline")))

        // 模拟进程重启：同一份已加密/已持久化的存储上重新构建队列
        val afterRestart = DraftOutboxQueue(
            storage = storage,
            policy = DraftOutboxRetryPolicy(),
            clock = { now },
            idGenerator = incrementingIds(),
        )

        val restored = afterRestart.entries.value.single()
        assertEquals("android-ocr-request-1", restored.sourceRef)
        assertEquals(DraftOutboxStatus.PENDING, restored.status)
        assertEquals(1, afterRestart.entries.value.size)
    }

    @Test
    fun paymentNotificationSourceRefIsPreservedForRetries() = runTest {
        val policy = DraftOutboxRetryPolicy(backoffSeconds = listOf(0L))
        val queue = newQueue(policy)
        val intent = accountingIntent(
            sourceType = "PAYMENT_NOTIFICATION",
            sourceRef = "fingerprint-abc",
            rawInput = "来源：支付宝；金额：18.0 元；摘要：已支付 18.00 元",
        )
        queue.enqueue(intent, DraftOutboxOrigin.PAYMENT_NOTIFICATION, "支付宝 · 18.0 元", networkFailure(IOException("offline")))
        val gateway = RecordingDraftGateway()

        queue.retryDueEntries(gateway)

        assertEquals("PAYMENT_NOTIFICATION", gateway.intents.single().sourceType)
        assertEquals("fingerprint-abc", gateway.intents.single().sourceRef)
        assertEquals(DraftOutboxOrigin.PAYMENT_NOTIFICATION, queue.lastOutcome.value?.origin)
    }

    @Test
    fun controlledRetryIsSingleInstanceEvenWhenCalledConcurrently() = runTest {
        val policy = DraftOutboxRetryPolicy(backoffSeconds = listOf(0L))
        val queue = newQueue(policy)
        queue.enqueue(accountingIntent(), DraftOutboxOrigin.OCR, "早餐 18 元", networkFailure(IOException("offline")))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val gateway = RecordingDraftGateway {
            started.complete(Unit)
            release.await()
            NetworkResult.Success(draftResponse(draftId = 9L))
        }

        val first = async { queue.retryDueEntries(gateway) }
        started.await()

        assertEquals(0, queue.retryDueEntries(gateway))
        assertEquals(1, gateway.calls)

        release.complete(Unit)
        assertEquals(1, first.await())
        assertEquals(1, gateway.calls)
        assertTrue(queue.entries.value.isEmpty())
    }

    private fun httpError(code: Int): Throwable =
        HttpException(Response.error<Any>(code, "".toResponseBody("text/plain".toMediaType())))
}
