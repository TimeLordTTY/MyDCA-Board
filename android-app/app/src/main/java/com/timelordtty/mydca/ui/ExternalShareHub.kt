package com.timelordtty.mydca.ui

import com.timelordtty.mydca.share.ExternalShareCapture
import com.timelordtty.mydca.share.ExternalSharePayload
import com.timelordtty.mydca.ui.screens.OcrEntryMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 外部分享落到的既有采集入口；不新增页面，也不引入新的导航框架。 */
data class ExternalShareDestination(
    val route: AppRoute,
    val entryMode: OcrEntryMode,
)

/**
 * 外部分享的导航与文案纯逻辑。
 *
 * 分享只把用户带进既有的手工文本 / 图片 OCR 采集页并预填内容：
 * 不自动解析、不自动 OCR、不自动生成草稿、不自动 preview / confirm。
 */
object ExternalShareHub {
    /** 分享预填页固定提示，明确当前只是预填。 */
    const val PREFILL_HINT = "来自系统分享，尚未解析/未生成草稿"

    const val TEXT_BANNER =
        "这段文字来自系统分享，只做了预填。你可以直接编辑或清空；只有你点击“解析记账候选”才会发给自己的 MyDCA 后端解析，草稿也必须由你再确认。"

    const val IMAGE_BANNER =
        "这张图片来自系统分享，只有你点击“使用此图片并识别”后才会在本机识别，图片不会上传；识别后仍需你手动解析并生成草稿。"

    fun destinationFor(payload: ExternalSharePayload): ExternalShareDestination = when (payload) {
        is ExternalSharePayload.Text -> ExternalShareDestination(AppRoute.Drafts, OcrEntryMode.ManualText)
        is ExternalSharePayload.Image -> ExternalShareDestination(AppRoute.Drafts, OcrEntryMode.Image)
    }

    fun bannerFor(payload: ExternalSharePayload): String = when (payload) {
        is ExternalSharePayload.Text -> TEXT_BANNER
        is ExternalSharePayload.Image -> IMAGE_BANNER
    }
}

/**
 * 采集侧的外部分享一次性状态。
 *
 * 任何离开当前采集流程的路径都必须回到空状态：返回 / 取消、主动切换底部导航、
 * 成功生成 DRAFT；避免 share target 残留到下一次采集，也避免与
 * selectedDraftId / NotificationNavigationTarget / QuickCaptureFocus 相互串扰。
 */
class ExternalShareCaptureSession {
    private val mutableActive = MutableStateFlow<ExternalShareCapture?>(null)
    val active: StateFlow<ExternalShareCapture?> = mutableActive.asStateFlow()

    /** 接纳刚刚消费到的 pending share；没有 pending 时保持现状，不误清当前分享。 */
    fun adopt(capture: ExternalShareCapture?) {
        if (capture != null) mutableActive.value = capture
    }

    fun onCaptureExit() {
        mutableActive.value = null
    }

    fun onManualNavigation() {
        mutableActive.value = null
    }

    fun onDraftCreated() {
        mutableActive.value = null
    }
}
