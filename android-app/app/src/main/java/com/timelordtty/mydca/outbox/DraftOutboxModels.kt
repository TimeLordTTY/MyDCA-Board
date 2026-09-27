package com.timelordtty.mydca.outbox

import com.timelordtty.mydca.data.dto.AccountingIntentDto

/** 三类采集入口；只用于队列归类与展示，不改变任何入账语义。 */
enum class DraftOutboxOrigin(val label: String) {
    MANUAL_TEXT("手工文本"),
    OCR("图片 OCR"),
    PAYMENT_NOTIFICATION("支付通知候选"),
}

/** 创建 DRAFT 失败的分类；只有 RETRYABLE 允许自动重试。 */
enum class DraftOutboxErrorCategory(val label: String) {
    RETRYABLE("网络或服务端暂时不可用"),
    AUTH_REQUIRED("登录已失效，需要重新登录"),
    BUSINESS("服务端拒绝当前草稿内容"),
    UNKNOWN("未识别的失败原因"),
}

/** 队列项状态。 */
enum class DraftOutboxStatus(val label: String) {
    PENDING("等待重试"),
    AUTH_PAUSED("暂停：等待重新登录"),
    BLOCKED("需人工修改或丢弃"),
    EXHAUSTED("自动重试次数已用尽"),
}

/**
 * 一条“待创建草稿”记录。
 *
 * 可持久化内容仅限：用户已确认/编辑过的记账意图、稳定的 sourceType/sourceRef，
 * 以及队列自身的恢复元数据。类型上不存在 Token、密码、Cookie、图片/URI
 * 或通知完整原文等字段，因此这些敏感内容无法进入队列。
 */
data class DraftOutboxEntry(
    val id: String,
    val sourceType: String,
    val sourceRef: String,
    val origin: DraftOutboxOrigin,
    val summary: String,
    val intent: AccountingIntentDto,
    val createdAt: Long,
    val retryCount: Int = 0,
    val nextRetryAt: Long = 0L,
    val lastErrorCategory: DraftOutboxErrorCategory? = null,
    val lastErrorMessage: String? = null,
    val lastAttemptAt: Long? = null,
    val status: DraftOutboxStatus = DraftOutboxStatus.PENDING,
)

/**
 * 重试成功后的结果。
 * 服务端可能返回新草稿，也可能返回同一 sourceRef 已存在的既有草稿。
 */
data class DraftOutboxOutcome(
    val entryId: String,
    val origin: DraftOutboxOrigin,
    val draftId: Long,
    val draftStatus: String?,
) {
    /** 只有服务端明确返回 DRAFT 时才引导用户打开草稿。 */
    val canOpenDraft: Boolean
        get() = draftStatus?.equals("DRAFT", ignoreCase = true) == true

    /** 既有草稿已 CONFIRMED / IGNORED 时只展示状态，不再发起任何确认动作。 */
    val showsExistingDecidedDraft: Boolean
        get() = !canOpenDraft
}
