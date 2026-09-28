package com.timelordtty.mydca.data.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MobilePageDto<T>(
    val items: List<T> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 20,
    val total: Long = 0,
    val totalPages: Int = 0,
    val hasNext: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class MobileOverviewDto(
    val totalAssets: String? = null,
    val cashBalance: String? = null,
    val positionValue: String? = null,
    val totalLiabilities: String? = null,
    val netWorth: String? = null,
    val accountCount: Int = 0,
    val spendableAmount: String? = null,
    val reservedFundAmount: String? = null,
    val investableAmount: String? = null,
    val unallocatedAmount: String? = null,
    val draftCount: Int = 0,
    val settlementCount: Int = 0,
    val suggestionCount: Int = 0,
    val totalTodoCount: Int = 0,
    val lastUpdatedAt: String? = null,
    val recentActivities: List<MobileActivityDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MobileActivityDto(
    val txnId: String,
    val txnType: String? = null,
    val status: String? = null,
    val note: String? = null,
    val accountName: String? = null,
    val amount: String? = null,
    val currency: String? = null,
    val occurredAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class MobileAccountDto(
    val id: Long,
    val parentAccountId: Long? = null,
    val accountName: String,
    val parentAccountName: String? = null,
    val accountType: String? = null,
    val fundUsage: String? = null,
    val leaf: Boolean = true,
    val selectableForDraft: Boolean = false,
    val selectableForExpense: Boolean = false,
    val safetyMessage: String? = null,
    val currency: String? = null,
    val balance: String? = null,
    val reservedAmount: String? = null,
    val availableAmount: String? = null,
    val active: Boolean? = null,
    val updatedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class MobileCashFlowDto(
    val monthStart: String? = null,
    val monthEnd: String? = null,
    val income: String? = null,
    val expense: String? = null,
    val netCashFlow: String? = null,
    val investmentInflow: String? = null,
    val investmentOutflow: String? = null,
    val recentActivities: List<MobileActivityDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MobileHoldingDto(
    val productId: Long? = null,
    val productCode: String? = null,
    val productName: String? = null,
    val accountName: String? = null,
    val totalShares: String? = null,
    val totalCost: String? = null,
    val avgCost: String? = null,
    val marketValue: String? = null,
    val unrealizedPnl: String? = null,
)

/**
 * 指定产品在各账户的持仓明细，对齐后端 GET /api/v2/holdings/product/{productId}/by-account。
 * 用于卖出 / 赎回草稿让主人明确选择真实持仓来源；只读展示，不参与任何自动匹配。
 */
@JsonClass(generateAdapter = true)
data class MobileHoldingByAccountDto(
    val accountId: Long? = null,
    val accountName: String? = null,
    val parentAccountName: String? = null,
    val shares: Double? = null,
    val marketValue: Double? = null,
)

@JsonClass(generateAdapter = true)
data class MobileTransactionDto(
    val txnId: String,
    val txnType: String? = null,
    val status: String? = null,
    val amount: String? = null,
    val currency: String? = null,
    val accountName: String? = null,
    val note: String? = null,
    val requestedAt: String? = null,
    val tradeDate: String? = null,
)
