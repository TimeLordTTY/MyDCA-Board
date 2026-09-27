package com.timelordtty.mydca.outbox

import com.timelordtty.mydca.core.network.ApiConfig
import com.timelordtty.mydca.core.network.NetworkModule
import com.timelordtty.mydca.data.repository.AiAccountingRepository
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 端到端网络边界验证：outbox 重试只允许打到“创建 DRAFT”接口。
 * 这是“重试绝不越过 preview / confirm 边界”的机器可验证证据。
 */
class DraftOutboxNetworkBoundaryTest {
    @Test
    fun retryOnlyCallsDraftFromIntentEndpoint() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"draft":{"id":77,"status":"DRAFT"}}"""),
            )
            val repository = AiAccountingRepository(
                NetworkModule.createApi(ApiConfig(server.url("/").toString())),
            )
            val queue = DraftOutboxQueue(InMemoryDraftOutboxStorage(), clock = { FIXED_NOW })
            queue.enqueue(
                intent = accountingIntent(),
                origin = DraftOutboxOrigin.MANUAL_TEXT,
                summary = "早餐 18 元",
                failure = networkFailure(IOException("offline")),
            )

            val entryId = queue.entries.value.single().id
            assertTrue(queue.retryEntry(entryId, repository))

            val requests = drainRequests(server)
            assertEquals(1, requests.size)
            assertEquals("POST", requests.single().method)
            assertEquals("/api/v2/ai/accounting/draft-from-intent", requests.single().path)
            requests.forEach { request -> assertNoConfirmBoundaryRequest(request) }
            assertTrue(requests.single().body.readUtf8().contains("android-ocr-request-1"))
            assertTrue(queue.entries.value.isEmpty())
            assertEquals(77L, queue.lastOutcome.value?.draftId)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun existingConfirmedDraftRemovesEntryWithoutAnyConfirmRequest() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"draft":{"id":91,"status":"CONFIRMED"}}"""),
            )
            val repository = AiAccountingRepository(
                NetworkModule.createApi(ApiConfig(server.url("/").toString())),
            )
            val queue = DraftOutboxQueue(InMemoryDraftOutboxStorage(), clock = { FIXED_NOW })
            queue.enqueue(
                intent = accountingIntent(sourceType = "PAYMENT_NOTIFICATION", sourceRef = "fingerprint-abc"),
                origin = DraftOutboxOrigin.PAYMENT_NOTIFICATION,
                summary = "支付宝 · 18.0 元",
                failure = networkFailure(IOException("offline")),
            )

            queue.retryEntry(queue.entries.value.single().id, repository)

            val requests = drainRequests(server)
            assertEquals(1, requests.size)
            requests.forEach { request -> assertNoConfirmBoundaryRequest(request) }
            assertTrue(queue.entries.value.isEmpty())
            assertEquals(91L, queue.lastOutcome.value?.draftId)
            assertFalse(queue.lastOutcome.value?.canOpenDraft == true)
            assertTrue(queue.lastOutcome.value?.showsExistingDecidedDraft == true)
        } finally {
            server.shutdown()
        }
    }

    private fun drainRequests(server: MockWebServer): List<RecordedRequest> =
        generateSequence { server.takeRequest(1, TimeUnit.SECONDS) }.toList()

    private fun assertNoConfirmBoundaryRequest(request: RecordedRequest) {
        val path = request.path.orEmpty()
        assertFalse(path.contains("preview"))
        assertFalse(path.contains("confirm"))
        assertFalse(path.contains("ignore"))
        assertFalse(path.startsWith("/api/v2/drafts"))
        assertFalse(path.contains("/quick-entry"))
        assertFalse(path.contains("settlement"))
        assertFalse(path.contains("order"))
    }
}
