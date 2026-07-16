package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.dto.AccountingIntentDto
import com.timelordtty.mydca.ocr.OcrDraftGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OcrDraftStage {
    Idle,
    ImageSelected,
    Recognizing,
    Recognized,
    RecognitionFailed,
    ParsingIntent,
    IntentReady,
    CreatingDraft,
    DraftCreated,
}

/** OCR 页面短生命周期状态，不包含图片内容或 URI。 */
data class OcrDraftUiState(
    val stage: OcrDraftStage = OcrDraftStage.Idle,
    val requestId: String? = null,
    val recognizedText: String = "",
    val intent: AccountingIntentDto? = null,
    val draftId: Long? = null,
    val message: String? = null,
)

/** 绑定图片请求与异步结果，并串联“识别、解析候选、人工确认生成 DRAFT”三个阶段。 */
class OcrDraftCoordinator {
    private val mutableState = MutableStateFlow(OcrDraftUiState())
    val state: StateFlow<OcrDraftUiState> = mutableState.asStateFlow()

    @Synchronized
    fun selectImage(requestId: String) {
        mutableState.value = OcrDraftUiState(
            stage = OcrDraftStage.ImageSelected,
            requestId = requestId,
        )
    }

    @Synchronized
    fun beginRecognition(requestId: String): Boolean {
        if (mutableState.value.requestId != requestId) return false
        mutableState.value = mutableState.value.copy(stage = OcrDraftStage.Recognizing, message = null)
        return true
    }

    @Synchronized
    fun recognitionSucceeded(requestId: String, text: String) {
        if (mutableState.value.requestId != requestId || mutableState.value.stage != OcrDraftStage.Recognizing) return
        val cleaned = text.trim()
        mutableState.value = if (cleaned.isBlank()) {
            mutableState.value.copy(
                stage = OcrDraftStage.RecognitionFailed,
                recognizedText = "",
                message = "未识别到可用文字，请更换清晰图片后重试",
            )
        } else {
            mutableState.value.copy(
                stage = OcrDraftStage.Recognized,
                recognizedText = cleaned,
                message = null,
            )
        }
    }

    @Synchronized
    fun recognitionFailed(requestId: String) {
        if (mutableState.value.requestId != requestId || mutableState.value.stage != OcrDraftStage.Recognizing) return
        mutableState.value = mutableState.value.copy(
            stage = OcrDraftStage.RecognitionFailed,
            recognizedText = "",
            message = "图片识别失败，请检查图片格式或更换图片",
        )
    }

    @Synchronized
    fun editText(text: String) {
        val current = mutableState.value
        if (current.stage !in setOf(OcrDraftStage.Recognized, OcrDraftStage.IntentReady)) return
        mutableState.value = current.copy(
            stage = OcrDraftStage.Recognized,
            recognizedText = text,
            intent = null,
            draftId = null,
            message = null,
        )
    }

    suspend fun parseIntent(gateway: OcrDraftGateway) {
        val submission = synchronized(this) {
            val current = mutableState.value
            val text = current.recognizedText.trim()
            if (current.stage != OcrDraftStage.Recognized || text.isBlank()) return
            mutableState.value = current.copy(stage = OcrDraftStage.ParsingIntent, message = null)
            Triple(current.requestId ?: return, text, "android-ocr-${current.requestId}")
        }
        val result = gateway.parseText(submission.second, submission.third)
        synchronized(this) {
            val current = mutableState.value
            if (current.requestId != submission.first || current.stage != OcrDraftStage.ParsingIntent) return
            mutableState.value = when (result) {
                is NetworkResult.Failure -> current.copy(
                    stage = OcrDraftStage.Recognized,
                    message = "候选解析失败，请检查网络和服务状态后重试",
                )
                is NetworkResult.Success -> current.copy(
                    stage = OcrDraftStage.IntentReady,
                    intent = result.data.copy(
                        sourceType = "APP_FORM",
                        sourceRef = submission.third,
                        rawInput = submission.second,
                        parsedPayloadJson = null,
                    ),
                    message = null,
                )
            }
        }
    }

    suspend fun createDraft(gateway: OcrDraftGateway) {
        val submission = synchronized(this) {
            val current = mutableState.value
            val intent = current.intent ?: return
            if (current.stage != OcrDraftStage.IntentReady || current.draftId != null) return
            mutableState.value = current.copy(stage = OcrDraftStage.CreatingDraft, message = null)
            Pair(current.requestId ?: return, intent)
        }
        val result = gateway.createDraft(submission.second)
        synchronized(this) {
            val current = mutableState.value
            if (current.requestId != submission.first || current.stage != OcrDraftStage.CreatingDraft) return
            mutableState.value = when (result) {
                is NetworkResult.Failure -> current.copy(
                    stage = OcrDraftStage.IntentReady,
                    message = "草稿创建失败，请稍后重试",
                )
                is NetworkResult.Success -> current.copy(
                    stage = OcrDraftStage.DraftCreated,
                    draftId = result.data.draft.id,
                    message = "DRAFT 草稿已创建，尚未预览或正式入账",
                )
            }
        }
    }
}
