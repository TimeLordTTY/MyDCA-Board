package com.timelordtty.mydca.data.dto

/**
 * 草稿 DTO 最小字段，对齐后端 DraftLedgerEntryDTO 的移动端首版展示需要。
 */
data class DraftLedgerEntryDto(
    val id: Long,
    val sourceType: String? = null,
    val sourceRef: String? = null,
    val rawInput: String? = null,
    val parsedPayloadJson: String? = null,
    val previewPayloadJson: String? = null,
    val status: String? = null,
    val confidence: Double? = null,
    val missingFieldsJson: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/**
 * 草稿影响预览 DTO 最小字段。
 * confirmSupported 是移动端确认按钮是否可用的唯一依据之一。
 */
data class DraftPreviewDto(
    val draftId: Long,
    val confirmSupported: Boolean = false,
    val message: String? = null,
    val txnType: String? = null,
    val amount: Double? = null,
    val accountId: Long? = null,
    val accountName: String? = null,
    val accountType: String? = null,
    val fundUsage: String? = null,
    val impactDirection: String? = null,
    val accountDelta: Double? = null,
    val willCreateLedgerTxn: Boolean = false,
    val willCreateOrder: Boolean = false,
    val willCreateSettlement: Boolean = false,
    val willAffectHolding: Boolean = false,
    val warnings: List<String>? = emptyList(),
    val missingFields: List<String>? = emptyList(),
)

/**
 * 忽略草稿请求体；首版使用固定说明，后续可由 UI 表单补充。
 */
data class IgnoreDraftRequestDto(
    val ignoreReason: String? = null,
)
