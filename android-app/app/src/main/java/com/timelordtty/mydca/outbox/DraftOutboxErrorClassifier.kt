package com.timelordtty.mydca.outbox

import java.io.IOException
import retrofit2.HttpException

/**
 * 把“创建 DRAFT”的失败映射为队列可执行的分类。
 *
 * 只处理草稿创建链路的网络/服务端结果，不引入任何模型或 provider 概念：
 * 解析失败、字段业务校验失败不会走到这里，因此不可能伪装成可自动重试。
 */
object DraftOutboxErrorClassifier {
    fun classify(error: Throwable?): DraftOutboxErrorCategory = when (error) {
        is HttpException -> classifyStatusCode(error.code())
        is IOException -> DraftOutboxErrorCategory.RETRYABLE
        else -> DraftOutboxErrorCategory.UNKNOWN
    }

    /** 网络中断、超时、连接失败与明确 5xx 可重试；401/403 需重新登录；其余 4xx 交给用户处理。 */
    fun classifyStatusCode(statusCode: Int): DraftOutboxErrorCategory = when (statusCode) {
        401, 403 -> DraftOutboxErrorCategory.AUTH_REQUIRED
        in 500..599 -> DraftOutboxErrorCategory.RETRYABLE
        in 400..499 -> DraftOutboxErrorCategory.BUSINESS
        else -> DraftOutboxErrorCategory.UNKNOWN
    }

    fun isAutoRetryEligible(category: DraftOutboxErrorCategory): Boolean =
        category == DraftOutboxErrorCategory.RETRYABLE

    /** 展示给用户的失败原因；不包含 Token、密码或通知原文。 */
    fun messageFor(category: DraftOutboxErrorCategory, serverMessage: String?): String {
        val detail = serverMessage?.takeIf { it.isNotBlank() }
        return if (detail == null) category.label else "${category.label}：$detail"
    }
}
