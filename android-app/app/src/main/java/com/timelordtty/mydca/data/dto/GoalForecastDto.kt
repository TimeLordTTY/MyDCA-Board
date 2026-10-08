package com.timelordtty.mydca.data.dto

import java.math.BigDecimal

data class ForecastMonthInputDto(val month: String, val budgetId: String?, val cashflowCovered: Boolean = false)
data class GoalForecastRequestDto(val startMonth: String, val endMonth: String,
    val months: List<ForecastMonthInputDto>, val mode: String = "PLANNED",
    val monthlyExtraSavings: BigDecimal = BigDecimal.ZERO)
data class ForecastMonthDto(val month: String, val quality: String?, val reason: String?,
    val plannedIncome: BigDecimal?, val plannedExpenses: BigDecimal?, val plannedReserve: BigDecimal?,
    val contribution: BigDecimal?, val cumulativeProgress: BigDecimal?, val remainingGap: BigDecimal?)
data class ForecastScenarioDto(val annualRate: BigDecimal?, val assumption: String?, val quality: String?,
    val outcome: String?, val achievedMonth: String?, val months: List<ForecastMonthDto>)
data class GoalForecastDto(val goalId: String, val currency: String, val asOfDate: String?,
    val actualProgress: GoalProgressDto, val baseline: ForecastScenarioDto)
