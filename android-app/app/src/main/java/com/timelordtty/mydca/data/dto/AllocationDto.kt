package com.timelordtty.mydca.data.dto

/** Ratios are fractions; decimal strings preserve server precision. */
data class AllocationConfigDto(
    val scope: String?, val productId: Long?, val assetType: String?,
    val target: String?, val lowerBound: String?, val upperBound: String?,
    val returnThreshold: String?, val takeProfitThresholds: List<String> = emptyList(),
    val enabled: Boolean?, val note: String?,
)
data class AllocationPolicyDto(val id: String, val config: AllocationConfigDto, val createdAt: String?)
data class AllocationEvaluationDto(
    val policyId: String, val status: String?, val reason: String?, val allocation: String?,
    val returnRate: String?, val reachedTakeProfitThresholds: List<String> = emptyList(), val evaluatedAt: String?,
)
data class AllocationScenarioDto(val selectedAdjustment: String?, val remainderAdjustment: String?)
data class AllocationPriceDto(val productId: Long?, val status: String?, val priceDate: String?, val valuationDate: String?, val priceSource: String?)
data class AllocationPreviewDto(
    val policyId: String, val status: String?, val description: String?, val totalAssets: String?,
    val currentWeight: String?, val targetWeight: String?, val lowerBound: String?, val upperBound: String?,
    val deviationPercentagePoints: String?, val targetScenario: AllocationScenarioDto?, val bandScenario: AllocationScenarioDto?,
    val dataDate: String?, val prices: List<AllocationPriceDto> = emptyList(), val warnings: List<RadarWarningDto> = emptyList(),
)
