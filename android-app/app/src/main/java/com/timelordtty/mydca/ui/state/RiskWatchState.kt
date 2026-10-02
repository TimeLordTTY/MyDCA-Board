package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.data.dto.RiskEventDto
import com.timelordtty.mydca.data.dto.RiskSnapshotDto
import java.time.LocalDate

const val RISK_DISCLAIMER = "仅供观察，不构成交易建议"
fun riskGroups(events: List<RiskEventDto>, filter: String) = events
    .filter { filter == "ALL" || it.state == filter }
    .groupBy { it.evidence.severity ?: "UNKNOWN" }
    .toSortedMap(compareBy<String> { listOf("CRITICAL", "WARNING", "INFO", "UNKNOWN").indexOf(it).let { index -> if (index < 0) 4 else index } }.thenBy { it })
fun riskDataWarning(snapshot: RiskSnapshotDto, today: LocalDate = LocalDate.now()): String {
    val date = runCatching { LocalDate.parse(snapshot.sourceDataTimestamp?.take(10)) }.getOrNull()
    return when {
        snapshot.status != "OK" || (snapshot.threshold != null && snapshot.observedValue?.isFinite() != true) -> "数据未知 UNKNOWN，不能视为风险已解除"
        date == null || date.isAfter(today) -> "来源时间未知或异常，无法确认数据新鲜度"
        date.isBefore(today.minusDays(3)) -> "数据陈旧（超过 3 天），请谨慎观察"
        else -> "来源日期在 3 天内；这是已保存的观察证据，不代表实时数据"
    }
}
fun riskLabel(value: String?) = when(value) {
    "RETURN" -> "收益率"
    "DRAWDOWN" -> "回撤"
    "ALLOCATION_DEVIATION" -> "类别偏离"
    "STALE" -> "行情陈旧"
    "CONCENTRATION" -> "集中度"
    "NOTE" -> "观察备注"
    "PERSONAL" -> "个人"
    "FAMILY" -> "家庭"
    "ABOVE" -> "大于等于"
    "BELOW" -> "小于等于"
    "CRITICAL" -> "严重 CRITICAL"
    "WARNING" -> "警告 WARNING"
    "INFO" -> "提示 INFO"
    "OPEN" -> "未解除 OPEN"
    "RESOLVED" -> "已解除 RESOLVED"
    "ACKNOWLEDGED" -> "已读 ACKNOWLEDGED"
    "MUTED" -> "静默 MUTED"
    else -> value ?: "未知"
}
