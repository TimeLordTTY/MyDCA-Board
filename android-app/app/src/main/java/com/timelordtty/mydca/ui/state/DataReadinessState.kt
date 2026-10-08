package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.dto.DataReadinessDto
import java.time.Duration
import java.time.Instant

data class DataReadinessState(
    val data: DataReadinessDto? = null,
    val loading: Boolean = false,
    val error: String? = null
) {
    fun loaded(result: NetworkResult<DataReadinessDto>): DataReadinessState = when (result) {
        is NetworkResult.Success -> copy(data = result.data, loading = false, error = null)
        is NetworkResult.Failure -> copy(loading = false, error = result.message)
    }

    fun stale(now: Instant): Boolean = data?.checkedAt?.let {
        runCatching { Duration.between(Instant.parse(it), now).let { age -> age.isNegative || age > Duration.ofDays(1) } }.getOrDefault(true)
    } ?: true
}

fun readinessLabel(value: String?) = when (value) {
    "READY" -> "可读取 READY"
    "PARTIAL" -> "部分可用 PARTIAL"
    "UNAVAILABLE" -> "不可用 UNAVAILABLE"
    else -> "未知 UNKNOWN"
}

fun readinessArea(value: String?) = when (value) {
    "ASSETS" -> "资产"
    "MARKET" -> "行情"
    "EXCHANGE_RATES" -> "汇率"
    "GOALS" -> "目标"
    "BUDGETS" -> "本月预算"
    "RISK_RULES" -> "风险规则"
    "ALLOCATION" -> "配置观察"
    "RESEARCH" -> "研究证据"
    "FORECAST_INPUTS" -> "预测输入"
    "SCHEMA" -> "数据库部署"
    else -> "未识别项目"
}
