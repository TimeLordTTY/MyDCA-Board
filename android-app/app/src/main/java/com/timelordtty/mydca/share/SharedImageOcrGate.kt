package com.timelordtty.mydca.share

/**
 * 分享图片的本地 OCR 门。
 *
 * 收到分享 Intent 时只登记待识别图片（URI 只在进程内短暂保留），
 * 识别必须由用户显式点击“使用此图片并识别”才放行，且同一次分享只会放行一次。
 */
class SharedImageOcrGate(
    val uri: String,
    val sourceRef: String,
) {
    var recognitionStarted: Boolean = false
        private set

    /** 返回 true 表示本次是用户显式发起的识别；重复点击不会再次触发本地 OCR。 */
    fun startRecognitionByUser(): Boolean {
        if (recognitionStarted) return false
        recognitionStarted = true
        return true
    }
}
