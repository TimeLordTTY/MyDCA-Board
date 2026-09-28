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
 * 更新草稿请求体。
 * Android 只更新 DRAFT 草稿候选内容，不直接写正式账本；preview 会由用户保存后重新生成。
 */
data class UpdateDraftRequestDto(
    val sourceType: String? = null,
    val sourceRef: String? = null,
    val rawInput: String? = null,
    val parsedPayloadJson: String? = null,
    val confidence: Double? = null,
    val missingFieldsJson: String? = null,
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
    val targetAccountId: Long? = null,
    val targetAccountName: String? = null,
    val targetAccountType: String? = null,
    val targetFundUsage: String? = null,
    val targetAccountDelta: Double? = null,
    val impactDirection: String? = null,
    val accountDelta: Double? = null,
    /** 投资草稿的订单类型：BUY / SUBSCRIPTION；非投资草稿为空。 */
    val orderType: String? = null,
    /** 投资草稿选定的真实产品 ID；由主人明确选择，不由文本自动匹配。 */
    val productId: Long? = null,
    val productName: String? = null,
    val productCode: String? = null,
    val productAssetType: String? = null,
    val productCurrency: String? = null,
    /** 付款账户在本次确认前的可用余额（余额 - 已占用）。 */
    val availableBefore: Double? = null,
    /** 确认后待结算应收的变动金额，买入 / 申购为正数。 */
    val receivableDelta: Double? = null,
    val expectedNavDate: String? = null,
    val expectedConfirmDate: String? = null,
    /** 资金来源中文提示，便于主人复核付款账户与可用余额。 */
    val fundingMessage: String? = null,
    val willCreateLedgerTxn: Boolean = false,
    val willCreateOrder: Boolean = false,
    val willCreateSettlement: Boolean = false,
    val willAffectHolding: Boolean = false,
    val warnings: List<String>? = emptyList(),
    val missingFields: List<String>? = emptyList(),
)

/**
 * 产品主数据最小字段，用于投资草稿的真实产品选择（GET /api/v2/products）。
 * 移动端只读展示并让主人明确选择 productId，绝不会用产品名称自动匹配产品。
 */
data class ProductDto(
    val id: Long,
    val productCode: String? = null,
    val productName: String? = null,
    val assetType: String? = null,
    val currency: String? = null,
    val isActive: Boolean? = null,
)

/**
 * 忽略草稿请求体；首版使用固定说明，后续可由 UI 表单补充。
 */
data class IgnoreDraftRequestDto(
    val ignoreReason: String? = null,
)
