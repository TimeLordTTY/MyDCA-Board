package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.dto.AccountingIntentDto
import com.timelordtty.mydca.ocr.OcrDraftGateway
import com.timelordtty.mydca.outbox.DraftCreationGateway
import com.timelordtty.mydca.outbox.DraftOutboxOrigin
import com.timelordtty.mydca.outbox.DraftOutboxQueue
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
    /** 本次采集固定的 sourceRef；为空时回落到既有 android-ocr-<requestId> 规则。 */
    val sourceRef: String? = null,
)

/**
 * 绑定请求与异步结果，并串联“识别/输入、解析候选、人工确认生成 DRAFT”三个阶段。
 *
 * 该状态机不会自动 preview、自动 confirm 或静默生成正式流水；每一步都必须由主人显式触发。
 */
class OcrDraftCoordinator {
    private val mutableState = MutableStateFlow(OcrDraftUiState())
    val state: StateFlow<OcrDraftUiState> = mutableState.asStateFlow()

    /**
     * 图片入口（系统 Photo Picker 或外部分享图片）：只登记请求与可选的稳定 sourceRef，
     * 不会自动开始识别；识别与后续解析、生成 DRAFT 仍必须由主人逐步点击。
     */
    @Synchronized
    fun selectImage(requestId: String, sourceRef: String? = null) {
        mutableState.value = OcrDraftUiState(
            stage = OcrDraftStage.ImageSelected,
            requestId = requestId,
            sourceRef = sourceRef,
        )
    }

    /**
     * 手工记账入口：跳过图片阶段直接进入“文字已就绪”，后续解析和生成 DRAFT 仍必须由主人逐步点击。
     * 本方法不调用任何接口，只准备可编辑文本。
     */
    @Synchronized
    fun startTextEntry(requestId: String, text: String, sourceRef: String? = null) {
        mutableState.value = OcrDraftUiState(
            stage = OcrDraftStage.Recognized,
            requestId = requestId,
            sourceRef = sourceRef,
            recognizedText = text.trim(),
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
            val sourceRef = current.sourceRef ?: "android-ocr-${current.requestId}"
            Triple(current.requestId ?: return, text, sourceRef)
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

    /**
     * 创建 DRAFT。
     *
     * 只有这一步失败才可能进入本地 outbox；解析失败不会走到这里。
     * 入队复用同一份 intent（因此 sourceRef 稳定），重试仍只会调用“创建 DRAFT”，不会 preview 或 confirm。
     */
    suspend fun createDraft(
        gateway: DraftCreationGateway,
        outbox: DraftOutboxQueue? = null,
        origin: DraftOutboxOrigin = DraftOutboxOrigin.OCR,
    ) {
        val submission = synchronized(this) {
            val current = mutableState.value
            val intent = current.intent ?: return
            if (current.stage != OcrDraftStage.IntentReady || current.draftId != null) return
            mutableState.value = current.copy(stage = OcrDraftStage.CreatingDraft, message = null)
            Pair(current.requestId ?: return, intent)
        }
        val result = gateway.createDraft(submission.second)
        val queued = if (result is NetworkResult.Failure) {
            outbox?.enqueue(
                intent = submission.second,
                origin = origin,
                summary = submission.second.rawInput.orEmpty(),
                failure = result,
            )
        } else {
            null
        }
        synchronized(this) {
            val current = mutableState.value
            if (current.requestId != submission.first || current.stage != OcrDraftStage.CreatingDraft) return
            mutableState.value = when (result) {
                is NetworkResult.Failure -> current.copy(
                    stage = OcrDraftStage.IntentReady,
                    message = if (queued != null) {
                        "草稿创建失败：${result.message}。已进入本地待重试队列，只会重试创建 DRAFT，不会自动入账。"
                    } else {
                        "草稿创建失败：${result.message}"
                    },
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
