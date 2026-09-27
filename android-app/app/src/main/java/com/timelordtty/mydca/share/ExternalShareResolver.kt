package com.timelordtty.mydca.share

/**
 * 系统分享内容的唯一解析规则（纯 Kotlin，无 Android 依赖，可直接单元测试）。
 *
 * 这里只回答“能不能收、收什么”：
 * - 只接受 `ACTION_SEND` 单条内容，`ACTION_SEND_MULTIPLE` 明确拒绝；
 * - 文本按 MIME 前缀 `text/` 接收，并做长度上限保护；
 * - 图片只接受系统授权的临时 `content://` URI，`file://` 与其他未知 scheme 一律拒绝。
 *
 * 本层不做任何解析为记账候选、创建草稿、预览或确认动作。
 */
object ExternalShareResolver {
    const val ACTION_SEND = "android.intent.action.SEND"
    const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"

    /** 分享文本长度上限；超过则安全截断，避免超长原文进入内存态采集流程。 */
    const val MAX_TEXT_LENGTH = 2000

    private const val TEXT_MIME_PREFIX = "text/"
    private const val IMAGE_MIME_PREFIX = "image/"
    private const val CONTENT_SCHEME_PREFIX = "content://"

    fun resolve(request: ExternalShareRequest): ExternalShareResolution {
        if (request.action != ACTION_SEND) {
            return ExternalShareResolution.Rejected(ExternalShareRejection.UnsupportedAction)
        }
        val mimeType = request.mimeType.orEmpty().trim().lowercase()
        return when {
            mimeType.startsWith(TEXT_MIME_PREFIX) -> resolveText(request)
            mimeType.startsWith(IMAGE_MIME_PREFIX) -> resolveImage(request)
            // 发送方未声明 type 时，只有纯文本分享才允许继续按文本处理。
            mimeType.isEmpty() && request.sharedText.orEmpty().isNotBlank() -> resolveText(request)
            else -> ExternalShareResolution.Rejected(ExternalShareRejection.UnsupportedMimeType)
        }
    }

    private fun resolveText(request: ExternalShareRequest): ExternalShareResolution {
        val text = request.sharedText.orEmpty().trim()
        if (text.isEmpty()) {
            return ExternalShareResolution.Rejected(ExternalShareRejection.MissingText)
        }
        if (text.length > MAX_TEXT_LENGTH) {
            return ExternalShareResolution.Accepted(
                payload = ExternalSharePayload.Text(text.take(MAX_TEXT_LENGTH)),
                truncated = true,
            )
        }
        return ExternalShareResolution.Accepted(ExternalSharePayload.Text(text))
    }

    private fun resolveImage(request: ExternalShareRequest): ExternalShareResolution {
        val uri = request.sharedUri.orEmpty().trim()
        if (uri.isEmpty()) {
            return ExternalShareResolution.Rejected(ExternalShareRejection.MissingImage)
        }
        if (!uri.startsWith(CONTENT_SCHEME_PREFIX, ignoreCase = true)) {
            return ExternalShareResolution.Rejected(ExternalShareRejection.UntrustedImageSource)
        }
        return ExternalShareResolution.Accepted(ExternalSharePayload.Image(uri))
    }
}

/**
 * 外部分享的 sourceRef 规则：只由固定前缀 + 随机 UUID 组成。
 *
 * 同一份分享最终创建 DRAFT（含 Outbox 重试重放）必须复用同一个 sourceRef，
 * 而不同分享事件必须不同；sourceRef 不包含分享文字、文件名、URI 或图片原文。
 */
object ExternalShareSourceRef {
    const val TEXT_PREFIX = "android-share-text-"
    const val IMAGE_PREFIX = "android-share-image-"

    fun forPayload(
        payload: ExternalSharePayload,
        uuid: String = java.util.UUID.randomUUID().toString(),
    ): String = when (payload) {
        is ExternalSharePayload.Text -> TEXT_PREFIX + uuid
        is ExternalSharePayload.Image -> IMAGE_PREFIX + uuid
    }
}
