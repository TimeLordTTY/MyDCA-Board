package com.timelordtty.mydca.data.dto

/**
 * 待结算订单最小字段（GET /api/v2/settlements/pending 返回 orders 表实体）。
 *
 * 移动端只读展示「哪笔订单正在等待人工结算」。列表本身不会触发 preview，
 * 更不会触发 confirm；真正的结算影响必须由主人手动生成预览后再二次确认。
 */
data class PendingSettlementOrderDto(
    val id: Long = 0L,
    val orderId: String = "",
    val productId: Long? = null,
    val orderType: String? = null,
    val amount: Double? = null,
    val shares: Double? = null,
    val tradeDate: String? = null,
    val expectedNavDate: String? = null,
    val expectedConfirmDate: String? = null,
    val status: String? = null,
    val feeEstimate: Double? = null,
    val note: String? = null,
)

/**
 * 结算影响预览中的单条分录（只读，不代表已经落账）。
 *
 * accountId 为空表示该账户会在确认结算时自动创建（例如券商维度持仓账户或虚拟子账户）。
 */
data class SettlementPostingPreviewDto(
    val accountId: Long? = null,
    val accountName: String? = null,
    val accountType: String? = null,
    val postingType: String? = null,
    val amount: Double? = null,
    val shares: Double? = null,
    val currency: String? = null,
    val description: String? = null,
)

/**
 * 人工结算只读预览结果（POST /api/v2/settlements/preview）。
 *
 * confirmSupported=false 时移动端必须禁用「确认结算」按钮，并展示 blockingReasons。
 * freshPreviewToken 是 confirm 的唯一通行证：任何关键输入 / 订单 / 资金来源 / 账户快照变化都会使其失效。
 */
data class SettlementPreviewDto(
    val orderId: String = "",
    val orderType: String? = null,
    val orderTypeLabel: String? = null,
    val orderStatus: String? = null,
    val productId: Long? = null,
    val productName: String? = null,
    val productCode: String? = null,
    val currency: String? = null,
    val confirmDate: String? = null,
    val navDate: String? = null,
    val confirmNav: Double? = null,
    val confirmShares: Double? = null,
    val confirmAmount: Double? = null,
    val confirmFee: Double? = null,
    val computedShares: Double? = null,
    val computedAmount: Double? = null,
    val totalFundingAmount: Double? = null,
    val warnings: List<String>? = emptyList(),
    val confirmSupported: Boolean = false,
    val blockingReasons: List<String>? = emptyList(),
    val postingsPreview: List<SettlementPostingPreviewDto>? = emptyList(),
    val summaryLines: List<String>? = emptyList(),
    val willCreateSettlementConfirm: Boolean = false,
    val willCreateLedgerTxn: Boolean = false,
    val willChangeHolding: Boolean = false,
    val willChangeCash: Boolean = false,
    val freshPreviewToken: String? = null,
    val previewFingerprint: String? = null,
)

/**
 * 人工结算预览 / 确认请求体。
 *
 * preview 与 confirm 共用同一份输入，避免「预览时看到的输入」与「确认时真正使用的输入」不一致；
 * confirm 必须原样携带 preview 返回的 freshPreviewToken。confirmFee 为 null 表示由后端按
 * BrokerFeeService 估算，明确传 0 表示使用 0。
 */
data class SettlementPreviewRequestDto(
    val orderId: String,
    val confirmDate: String? = null,
    val navDate: String? = null,
    val confirmNav: Double? = null,
    val confirmShares: Double? = null,
    val confirmAmount: Double? = null,
    val confirmFee: Double? = null,
    val freshPreviewToken: String? = null,
    val note: String? = null,
)

/** 结算确认结果最小字段（POST /api/v2/settlements/confirm 返回 settlement_confirm 实体）。 */
data class SettlementConfirmDto(
    val id: Long = 0L,
    val orderId: String = "",
    val confirmDate: String? = null,
    val navDate: String? = null,
    val confirmNav: Double? = null,
    val confirmShares: Double? = null,
    val confirmAmount: Double? = null,
    val confirmFee: Double? = null,
    val confirmedAt: String? = null,
    val note: String? = null,
    val previewDigest: String? = null,
)

/** Read-only settlement history; previewDigest is never a confirm credential. */
data class SettlementAuditDto(
    val orderId: String = "",
    val orderType: String? = null,
    val orderStatus: String? = null,
    val productName: String? = null,
    val settlement: SettlementConfirmDto = SettlementConfirmDto(),
    val fundingLines: List<SettlementAuditFundingDto> = emptyList(),
    val ledgerTxnId: String? = null,
    val postings: List<SettlementAuditPostingDto> = emptyList(),
    val cashDelta: Double? = null,
    val positionSharesDelta: Double? = null,
    val feeAmount: Double? = null,
    val reconciliationStatus: String = "WARNING",
    val reasons: List<String> = emptyList(),
)

data class SettlementAuditFundingDto(val accountId: Long = 0L, val lineType: String? = null)
data class SettlementAuditPostingDto(
    val txnId: String = "", val accountId: Long = 0L, val accountType: String = "",
    val postingType: String = "", val amount: Double? = null, val shares: Double? = null,
)
