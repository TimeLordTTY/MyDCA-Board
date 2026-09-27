package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.dto.AccountingIntentDto
import com.timelordtty.mydca.data.dto.DraftFromIntentResponseDto
import com.timelordtty.mydca.data.dto.DraftLedgerEntryDto
import com.timelordtty.mydca.ocr.OcrDraftGateway
import com.timelordtty.mydca.outbox.DraftOutboxOrigin
import com.timelordtty.mydca.outbox.DraftOutboxQueue
import com.timelordtty.mydca.outbox.DraftOutboxStatus
import com.timelordtty.mydca.outbox.FIXED_NOW
import com.timelordtty.mydca.outbox.InMemoryDraftOutboxStorage
import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrDraftCoordinatorTest {
    @Test
    fun selectingNewImageClearsPreviousTextIntentAndDraft() = runBlocking {
        val coordinator = readyCoordinator("first", "支付 18 元")
        val gateway = FakeGateway()
        coordinator.parseIntent(gateway)
        coordinator.createDraft(gateway)

        coordinator.selectImage("second")

        assertEquals(OcrDraftStage.ImageSelected, coordinator.state.value.stage)
        assertEquals("second", coordinator.state.value.requestId)
        assertEquals("", coordinator.state.value.recognizedText)
        assertNull(coordinator.state.value.intent)
        assertNull(coordinator.state.value.draftId)
    }

    @Test
    fun staleRecognitionResultCannotOverwriteNewImage() {
        val coordinator = OcrDraftCoordinator()
        coordinator.selectImage("first")
        coordinator.beginRecognition("first")
        coordinator.selectImage("second")
        coordinator.beginRecognition("second")

        coordinator.recognitionSucceeded("first", "旧图片敏感文字")

        assertEquals("second", coordinator.state.value.requestId)
        assertEquals(OcrDraftStage.Recognizing, coordinator.state.value.stage)
        assertEquals("", coordinator.state.value.recognizedText)
    }

    @Test
    fun blankRecognitionCannotCallParseText() = runBlocking {
        val coordinator = OcrDraftCoordinator()
        val gateway = FakeGateway()
        coordinator.selectImage("image")
        coordinator.beginRecognition("image")
        coordinator.recognitionSucceeded("image", "   ")

        coordinator.parseIntent(gateway)

        assertEquals(0, gateway.parseCalls)
        assertEquals(OcrDraftStage.RecognitionFailed, coordinator.state.value.stage)
    }

    @Test
    fun editedTextIsOnlyPayloadSentToParser() = runBlocking {
        val coordinator = readyCoordinator("image", "原始识别文字")
        val gateway = FakeGateway()
        coordinator.editText("用户删除敏感字段后，支付 18 元")

        coordinator.parseIntent(gateway)

        assertEquals("用户删除敏感字段后，支付 18 元", gateway.lastParsedText)
        assertFalse(gateway.lastParsedText.orEmpty().contains("content://"))
        assertEquals("APP_FORM", coordinator.state.value.intent?.sourceType)
        assertNull(coordinator.state.value.intent?.parsedPayloadJson)
    }

    @Test
    fun createdDraftCannotBeCreatedTwice() = runBlocking {
        val coordinator = readyCoordinator("image", "支付 18 元")
        val gateway = FakeGateway()
        coordinator.parseIntent(gateway)

        coordinator.createDraft(gateway)
        coordinator.createDraft(gateway)

        assertEquals(1, gateway.createCalls)
        assertEquals(42L, coordinator.state.value.draftId)
        assertEquals(OcrDraftStage.DraftCreated, coordinator.state.value.stage)
    }

    @Test
    fun parseFailureNeverCallsDraftEndpoint() = runBlocking {
        val coordinator = readyCoordinator("image", "支付 18 元")
        val gateway = FakeGateway(parseFailure = true)

        coordinator.parseIntent(gateway)
        coordinator.createDraft(gateway)

        assertEquals(1, gateway.parseCalls)
        assertEquals(0, gateway.createCalls)
        assertNull(coordinator.state.value.draftId)
    }

    @Test
    fun draftFailureDoesNotInventDraftId() = runBlocking {
        val coordinator = readyCoordinator("image", "支付 18 元")
        val gateway = FakeGateway(draftFailure = true)
        coordinator.parseIntent(gateway)

        coordinator.createDraft(gateway)

        assertEquals(1, gateway.createCalls)
        assertNull(coordinator.state.value.draftId)
        assertEquals(OcrDraftStage.IntentReady, coordinator.state.value.stage)
        assertTrue(coordinator.state.value.message.orEmpty().contains("失败"))
    }

    @Test
    fun startTextEntryPreparesEditableTextWithoutCallingBackend() = runBlocking {
        val coordinator = OcrDraftCoordinator()
        val gateway = FakeGateway()

        coordinator.startTextEntry("manual-1", "  早餐 18 元 微信支付  ")

        assertEquals(OcrDraftStage.Recognized, coordinator.state.value.stage)
        assertEquals("manual-1", coordinator.state.value.requestId)
        assertEquals("早餐 18 元 微信支付", coordinator.state.value.recognizedText)
        assertNull(coordinator.state.value.intent)
        assertNull(coordinator.state.value.draftId)
        assertEquals(0, gateway.parseCalls)
        assertEquals(0, gateway.createCalls)
    }

    @Test
    fun manualTextEntryStillRequiresExplicitParseAndDraftSteps() = runBlocking {
        val coordinator = OcrDraftCoordinator()
        val gateway = FakeGateway()
        coordinator.startTextEntry("manual-2", "打车 32.5 元 支付宝")

        coordinator.parseIntent(gateway)
        assertEquals(OcrDraftStage.IntentReady, coordinator.state.value.stage)
        assertNull(coordinator.state.value.draftId)

        coordinator.createDraft(gateway)
        assertEquals(OcrDraftStage.DraftCreated, coordinator.state.value.stage)
        assertEquals(42L, coordinator.state.value.draftId)
    }

    @Test
    fun transientDraftFailureEntersOutboxWithStableSourceRef() = runBlocking {
        val coordinator = readyCoordinator("request-1", "支付 18 元")
        val gateway = FakeGateway(draftFailure = true, draftFailureCause = SocketTimeoutException("timeout"))
        val outbox = DraftOutboxQueue(InMemoryDraftOutboxStorage(), clock = { FIXED_NOW })

        coordinator.parseIntent(gateway)
        coordinator.createDraft(gateway, outbox, DraftOutboxOrigin.OCR)

        val entry = outbox.entries.value.single()
        assertEquals("android-ocr-request-1", entry.sourceRef)
        assertEquals(DraftOutboxOrigin.OCR, entry.origin)
        assertEquals(DraftOutboxStatus.PENDING, entry.status)
        assertNull(coordinator.state.value.draftId)
        assertTrue(coordinator.state.value.message.orEmpty().contains("待重试队列"))
    }

    @Test
    fun ocrParseFailureNeverEntersOutbox() = runBlocking {
        val coordinator = readyCoordinator("request-2", "支付 18 元")
        val gateway = FakeGateway(parseFailure = true)
        val outbox = DraftOutboxQueue(InMemoryDraftOutboxStorage(), clock = { FIXED_NOW })

        coordinator.parseIntent(gateway)
        coordinator.createDraft(gateway, outbox, DraftOutboxOrigin.OCR)

        assertTrue(outbox.entries.value.isEmpty())
        assertEquals(0, gateway.createCalls)
    }

    @Test
    fun queuedRetryOnlyRecreatesDraftAndReusesSourceRef() = runBlocking {
        val coordinator = readyCoordinator("request-3", "打车 32.5 元 支付宝")
        val failingGateway = FakeGateway(draftFailure = true, draftFailureCause = SocketTimeoutException("timeout"))
        val outbox = DraftOutboxQueue(InMemoryDraftOutboxStorage(), clock = { FIXED_NOW })
        coordinator.parseIntent(failingGateway)
        coordinator.createDraft(failingGateway, outbox, DraftOutboxOrigin.MANUAL_TEXT)

        val retryGateway = FakeGateway()
        assertTrue(outbox.retryEntry(outbox.entries.value.single().id, retryGateway))

        assertEquals(1, retryGateway.createCalls)
        assertEquals(0, retryGateway.parseCalls)
        assertEquals("android-ocr-request-3", retryGateway.createdIntents.single().sourceRef)
        assertEquals("APP_FORM", retryGateway.createdIntents.single().sourceType)
        assertTrue(outbox.entries.value.isEmpty())
        assertEquals(1, failingGateway.createCalls)
    }

    @Test
    fun repeatedCreateInsideOneAttemptReusesTheSameSourceRef() = runBlocking {
        val coordinator = readyCoordinator("attempt-9", "支付 18 元")
        val gateway = FakeGateway(draftFailure = true, draftFailureCause = SocketTimeoutException("timeout"))
        val outbox = DraftOutboxQueue(InMemoryDraftOutboxStorage(), clock = { FIXED_NOW })
        coordinator.parseIntent(gateway)

        coordinator.createDraft(gateway, outbox, DraftOutboxOrigin.OCR)
        coordinator.createDraft(gateway, outbox, DraftOutboxOrigin.OCR)

        assertEquals(2, gateway.createCalls)
        assertEquals(listOf("android-ocr-attempt-9", "android-ocr-attempt-9"), gateway.createdIntents.map { it.sourceRef })
        assertEquals(1, outbox.entries.value.size)
    }

    @Test
    fun eachNewCaptureAttemptGetsItsOwnSourceRef() = runBlocking {
        val gateway = FakeGateway(draftFailure = true, draftFailureCause = SocketTimeoutException("timeout"))
        val outbox = DraftOutboxQueue(InMemoryDraftOutboxStorage(), clock = { FIXED_NOW })
        listOf("attempt-a", "attempt-b").forEach { requestId ->
            val coordinator = readyCoordinator(requestId, "支付 18 元")
            coordinator.parseIntent(gateway)
            coordinator.createDraft(gateway, outbox, DraftOutboxOrigin.OCR)
        }

        assertEquals(2, outbox.entries.value.size)
        assertEquals(
            setOf("android-ocr-attempt-a", "android-ocr-attempt-b"),
            outbox.entries.value.map { it.sourceRef }.toSet(),
        )
    }

    private fun readyCoordinator(requestId: String, text: String): OcrDraftCoordinator {
        return OcrDraftCoordinator().apply {
            selectImage(requestId)
            beginRecognition(requestId)
            recognitionSucceeded(requestId, text)
        }
    }

    private class FakeGateway(
        private val parseFailure: Boolean = false,
        private val draftFailure: Boolean = false,
        private val draftFailureCause: Throwable? = null,
    ) : OcrDraftGateway {
        var parseCalls = 0
        var createCalls = 0
        var lastParsedText: String? = null
        val createdIntents = mutableListOf<AccountingIntentDto>()

        override suspend fun parseText(text: String, sourceRef: String): NetworkResult<AccountingIntentDto> {
            parseCalls += 1
            lastParsedText = text
            if (parseFailure) return NetworkResult.Failure("fixture parse failure")
            return NetworkResult.Success(
                AccountingIntentDto(
                    sourceType = "HERMES_TEXT",
                    sourceRef = sourceRef,
                    rawInput = text,
                    txnType = "EXPENSE",
                    amount = 18.0,
                    parsedPayloadJson = "fixture-json",
                ),
            )
        }

        override suspend fun createDraft(intent: AccountingIntentDto): NetworkResult<DraftFromIntentResponseDto> {
            createCalls += 1
            createdIntents += intent
            if (draftFailure) return NetworkResult.Failure("fixture draft failure", draftFailureCause)
            return NetworkResult.Success(
                DraftFromIntentResponseDto(
                    intent = intent,
                    draft = DraftLedgerEntryDto(id = 42L, status = "DRAFT"),
                ),
            )
        }
    }
}
