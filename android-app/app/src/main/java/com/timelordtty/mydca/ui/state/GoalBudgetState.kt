package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.data.dto.*
import java.math.BigDecimal
import java.math.RoundingMode

fun dataQuality(value: String?) = when (value) {
    "OK" -> "完整 OK"
    "PARTIAL" -> "部分数据 PARTIAL，不能确定最终结果"
    else -> "未知 UNKNOWN，数据不足"
}
fun observedMoney(value: BigDecimal?, quality: String?, currency: String): String =
    if (quality != "OK" || value == null) "未知" else "${value.setScale(2, RoundingMode.HALF_UP).toPlainString()} $currency"
fun goalRate(progress: GoalProgressDto): String =
    if (progress.quality != "OK" || progress.completionRate == null) "未知"
    else "${progress.completionRate.multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP).toPlainString()}%"
// A remaining target is a display calculation, never a ledger or an inferred partial result.
fun goalRemaining(goal: GoalDto, progress: GoalProgressDto): String = observedMoney(
    progress.currentValue?.let { goal.config.targetValue.subtract(it).max(BigDecimal.ZERO) },
    progress.quality, goal.config.currency)
fun overspendStatus(value: Boolean?, quality: String?) = when {
    quality != "OK" || value == null -> "超支状态未知"
    value -> "已超支"
    else -> "未超支"
}
fun currentMonthBudgets(budgets: List<BudgetDto>, month: String) = budgets.filter { it.config.month == month }

fun planningScope(value: String) = when (value) {
    "PERSONAL" -> "个人"
    "FAMILY" -> "家庭"
    else -> "未知作用域"
}
fun budgetItemStatus(kind: String, overspent: Boolean?, quality: String?) = when (kind) {
    "RESERVE" -> "仅计划，不对应实际资金划转"
    "INCOME" -> "收入计划，不适用超支判断"
    else -> overspendStatus(overspent, quality)
}
