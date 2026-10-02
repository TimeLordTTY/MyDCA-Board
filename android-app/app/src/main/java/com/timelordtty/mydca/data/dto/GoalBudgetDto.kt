package com.timelordtty.mydca.data.dto

import java.math.BigDecimal

data class GoalConfigDto(val name: String, val targetValue: BigDecimal, val targetDate: String,
    val currency: String, val scope: String, val measure: String, val state: String)
data class GoalDto(val id: String, val config: GoalConfigDto)
data class GoalProgressDto(val goalId: String, val quality: String?, val reason: String?,
    val currentValue: BigDecimal?, val knownValue: BigDecimal?, val completionRate: BigDecimal?,
    val completed: Boolean?, val asOfDate: String?, val daysRemaining: Long, val overdue: Boolean)
data class BudgetItemDto(val name: String, val kind: String, val planned: BigDecimal)
data class BudgetConfigDto(val name: String, val month: String, val currency: String,
    val scope: String, val items: List<BudgetItemDto>)
data class BudgetDto(val id: String, val config: BudgetConfigDto)
data class BudgetItemComparisonDto(val item: BudgetItemDto, val quality: String?,
    val actual: BigDecimal?, val knownActual: BigDecimal?, val remaining: BigDecimal?, val overspent: Boolean?)
data class BudgetComparisonDto(val budgetId: String, val quality: String?,
    val plannedIncome: BigDecimal?, val plannedExpenses: BigDecimal?, val plannedReserve: BigDecimal?,
    val plannedSurplus: BigDecimal?, val actualIncome: BigDecimal?, val actualExpenses: BigDecimal?,
    val actualSurplus: BigDecimal?, val remainingBudget: BigDecimal?, val overspent: Boolean?,
    val unmatchedPostings: Int, val items: List<BudgetItemComparisonDto>, val warnings: List<String>)
