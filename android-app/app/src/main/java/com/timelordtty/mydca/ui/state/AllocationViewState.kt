package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.data.dto.AllocationPreviewDto
import java.math.BigDecimal
import java.time.LocalDate

const val ALLOCATION_DISCLAIMER = "规则来自主人配置；情景预览不构成交易建议"
fun allocationReturnWatch(threshold: String?, rate: String?): String {
    if (threshold == null) return "未配置"
    val limit = threshold.toBigDecimalOrNull() ?: return "未知 UNKNOWN"
    val observed = rate?.toBigDecimalOrNull() ?: return "未知 UNKNOWN"
    return if (observed >= limit) "已触发（仅观察）" else "未触发"
}
fun allocationPercent(value: String?): String = value?.toBigDecimalOrNull()?.multiply(BigDecimal("100"))?.stripTrailingZeros()?.toPlainString()?.let { "$it%" } ?: "未知 UNKNOWN"
fun allocationLabel(value: String?) = when (value) {
    "IN_RANGE" -> "区间内 IN_RANGE"
    "BELOW_BAND" -> "低于下限 BELOW_BAND"
    "ABOVE_BAND" -> "高于上限 ABOVE_BAND"
    "TAKE_PROFIT_WATCH" -> "收益阈值已触发 TAKE_PROFIT_WATCH（仅观察）"
    "OK" -> "可读取 OK"
    "STALE" -> "行情陈旧 STALE"
    "NOT_MODELED" -> "未建模 NOT_MODELED"
    else -> "未知 ${value ?: "UNKNOWN"}"
}
fun allocationFreshness(preview: AllocationPreviewDto, today: LocalDate = LocalDate.now()): String {
    val date = runCatching { LocalDate.parse(preview.dataDate) }.getOrNull()
    return when {
        preview.status == "UNKNOWN" -> "数据缺失或陈旧 UNKNOWN；假设金额不可确定"
        date == null || date.isAfter(today) -> "数据时间未知或异常 UNKNOWN"
        date.isBefore(today.minusDays(3)) -> "快照陈旧（超过 3 天），不代表当前配置"
        else -> "这是数据日期的只读快照，不代表实时行情"
    }
}
