package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.data.dto.ForecastScenarioDto
import java.time.LocalDate

fun forecastOutcome(scenario: ForecastScenarioDto): String = when {
    scenario.quality != "OK" -> "无法确定：数据未知或覆盖不完整，请查看逐月原因"
    scenario.outcome == "ACHIEVED" && scenario.achievedMonth != null -> "${scenario.achievedMonth}（假设，非承诺）"
    scenario.outcome == "UNREACHABLE_WITHIN_RANGE" -> "当前预算条件下，预测范围内无法达成"
    else -> "无法确定：后端未提供达成结果"
}
fun forecastFreshness(date: String?, today: LocalDate = LocalDate.now()): String {
    val source = runCatching { LocalDate.parse(date) }.getOrNull()
    return when {
        source == null -> "来源时间未知，请刷新后核对"
        source.isBefore(today) -> "来源数据已过期，请人工刷新；以下仅为该日期的情景"
        source.isAfter(today) -> "来源日期晚于设备日期，请核对时间"
        else -> "来源为今日统计读数；情景不是市场预测"
    }
}
