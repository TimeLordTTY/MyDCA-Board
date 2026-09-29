package com.timelordtty.mydca.data.dto

/** Nullable server facts stay nullable: unknown is never silently converted to zero. */
data class FinanceRadarDto(
    val date: String?,
    val scope: String?,
    val assets: RadarAssetsDto?,
    val counts: RadarCountsDto?,
    val markets: List<RadarMarketDto> = emptyList(),
    val warnings: List<RadarWarningDto> = emptyList(),
)

data class RadarAssetsDto(
    val status: String?,
    val cashBalance: String?,
    val investmentCost: String?,
    val positionValue: String?,
    val liabilities: String?,
    val totalAssets: String?,
    val netWorth: String?,
)

data class RadarCountsDto(
    val drafts: Int?,
    val outbox: Int?,
    val pendingOrders: Int?,
    val awaitingSettlement: Int?,
    val reconciliationWarning: Int?,
    val reconciliationBroken: Int?,
)

data class RadarMarketDto(
    val productId: Long?,
    val status: String?,
    val priceDate: String?,
    val valuationDate: String?,
    val indicatorStatus: String?,
    val indicatorDate: String?,
)

data class RadarWarningDto(val code: String?, val status: String?, val message: String?)
