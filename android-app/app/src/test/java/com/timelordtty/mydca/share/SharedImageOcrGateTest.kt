package com.timelordtty.mydca.share

import com.timelordtty.mydca.ui.state.OcrDraftCoordinator
import com.timelordtty.mydca.ui.state.OcrDraftStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分享图片的本地识别门：收到分享 Intent 只登记图片，识别必须由用户显式点击触发。
 */
class SharedImageOcrGateTest {
    private val shareUri = "content://com.example.provider/picked/payment-1"
    private val shareSourceRef = "android-share-image-11111111-2222-3333-4444-555555555555"

    @Test
    fun prefillAloneNeverStartsLocalRecognition() {
        val gate = SharedImageOcrGate(uri = shareUri, sourceRef = shareSourceRef)

        assertFalse("收到分享图片时不能自动开始本地 OCR", gate.recognitionStarted)
        assertEquals(shareUri, gate.uri)
        assertEquals(shareSourceRef, gate.sourceRef)
    }

    @Test
    fun onlyUserActionStartsRecognitionAndOnlyOnce() {
        val gate = SharedImageOcrGate(uri = shareUri, sourceRef = shareSourceRef)

        assertTrue("用户点击后应放行识别", gate.startRecognitionByUser())
        assertTrue(gate.recognitionStarted)
        assertFalse("重复点击不应再次触发本地 OCR", gate.startRecognitionByUser())
    }

    @Test
    fun coordinatorPrefillKeepsTheSharedImageWaitingForTheUser() {
        val coordinator = OcrDraftCoordinator()

        coordinator.selectImage("share-image-request", shareSourceRef)

        val state = coordinator.state.value
        assertEquals(OcrDraftStage.ImageSelected, state.stage)
        assertEquals("", state.recognizedText)
        assertNull("预填阶段不能有候选意图", state.intent)
        assertNull("预填阶段不能有草稿", state.draftId)
        assertEquals("分享图片必须沿用本次分享的 sourceRef", shareSourceRef, state.sourceRef)
    }

    @Test
    fun recognitionResultIsIgnoredUntilTheUserStartsRecognition() {
        val coordinator = OcrDraftCoordinator()
        coordinator.selectImage("share-image-request", shareSourceRef)

        coordinator.recognitionSucceeded("share-image-request", "支付 18 元")

        assertEquals("用户未点击前识别结果不能改写状态", OcrDraftStage.ImageSelected, coordinator.state.value.stage)
        assertEquals("", coordinator.state.value.recognizedText)

        assertTrue(coordinator.beginRecognition("share-image-request"))
        coordinator.recognitionSucceeded("share-image-request", "支付 18 元")

        assertEquals(OcrDraftStage.Recognized, coordinator.state.value.stage)
        assertEquals("支付 18 元", coordinator.state.value.recognizedText)
    }

    @Test
    fun recognitionIsStillFollowedByManualParseAndManualDraftOnly() {
        val coordinator = OcrDraftCoordinator()
        coordinator.selectImage("share-image-request", shareSourceRef)
        coordinator.beginRecognition("share-image-request")
        coordinator.recognitionSucceeded("share-image-request", "支付 18 元")

        assertEquals("识别完成后仍不能自动解析", OcrDraftStage.Recognized, coordinator.state.value.stage)
        assertNull(coordinator.state.value.intent)
        assertNull(coordinator.state.value.draftId)
    }
}
