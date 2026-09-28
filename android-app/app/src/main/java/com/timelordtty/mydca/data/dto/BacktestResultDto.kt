package com.timelordtty.mydca.data.dto

data class BacktestResultDto(
    val run_id: String,
    val strategy: BacktestStrategyDto,
    val data_range: BacktestRangeDto,
    val metrics: BacktestMetricsDto,
    val baseline: BacktestBaselineDto? = null,
)
data class BacktestStrategyDto(val name: String, val version: String)
data class BacktestRangeDto(val start: String, val end: String)
data class BacktestMetricsDto(
    val total_return: Double,
    val annualized_return: Double?,
    val max_drawdown: Double,
    val invested: Double,
    val final_assets: Double,
    val cash: Double,
    val trade_count: Int,
)
data class BacktestBaselineDto(val final_assets_delta: Double, val annualized_return_delta: Double?)
