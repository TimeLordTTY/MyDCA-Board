package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.dto.AccountingIntentDto
import com.timelordtty.mydca.data.dto.DraftFromIntentResponseDto
import com.timelordtty.mydca.data.dto.DraftLedgerEntryDto
import com.timelordtty.mydca.ocr.OcrDraftGateway
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
    ) : OcrDraftGateway {
        var parseCalls = 0
        var createCalls = 0
        var lastParsedText: String? = null

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
            if (draftFailure) return NetworkResult.Failure("fixture draft failure")
            return NetworkResult.Success(
                DraftFromIntentResponseDto(
                    intent = intent,
                    draft = DraftLedgerEntryDto(id = 42L, status = "DRAFT"),
                ),
            )
        }
    }
}
