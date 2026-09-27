package com.timelordtty.mydca.share

/**
 * 系统 Share Sheet 送进来的内容。
 *
 * 只有两种形态允许进入 App：单条文本、单张图片。
 * 类型上不存在图片字节、多文件集合或任何持久化字段，
 * 因此分享内容无法在采集流程之外被落盘或上传。
 */
sealed interface ExternalSharePayload {
    data class Text(val text: String) : ExternalSharePayload

    /** 只承载系统授权的临时 content:// URI 字符串；图片字节不进入本层。 */
    data class Image(val uri: String) : ExternalSharePayload
}

/**
 * 从 `android.content.Intent` 抽离出的纯数据视图，便于在 JVM 单元测试里固定分享解析规则。
 *
 * 只保留 action / type / EXTRA_TEXT / EXTRA_STREAM 四个字段，不携带 Intent 本身。
 */
data class ExternalShareRequest(
    val action: String?,
    val mimeType: String? = null,
    val sharedText: String? = null,
    val sharedUri: String? = null,
)

/** 分享内容被安全拒绝的原因；[message] 直接面向用户，为中文提示。 */
enum class ExternalShareRejection(val message: String) {
    UnsupportedAction("一次只能分享一条文本或一张图片，多选内容不会被导入。"),
    UnsupportedMimeType("暂不支持这种分享内容，可以复制文字后用“记一笔”手工录入。"),
    MissingText("这次分享里没有可用的文字。"),
    MissingImage("这次分享里没有可用的图片。"),
    UntrustedImageSource("出于安全考虑，只接受系统授权的临时图片，其他来源已拒绝。"),
}

/**
 * 解析结果：要么是可进入采集流程的 payload，要么是安全拒绝 + 中文提示。
 *
 * [ExternalShareResolution.Accepted.truncated] 表示文本超长已被安全截断，需要提示用户复核。
 */
sealed interface ExternalShareResolution {
    data class Accepted(
        val payload: ExternalSharePayload,
        val truncated: Boolean = false,
    ) : ExternalShareResolution

    data class Rejected(val rejection: ExternalShareRejection) : ExternalShareResolution
}
