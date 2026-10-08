package com.timelordtty.mydca.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.dto.GoalDto
import com.timelordtty.mydca.data.repository.GoalBudgetRepository
import com.timelordtty.mydca.ui.state.*

@Composable
fun GoalForecastScreen(api: WealthHubApi, goal: GoalDto, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val repository = remember(api) { GoalBudgetRepository(api) }
    var refresh by remember { mutableStateOf(0) }
    PageScaffold {
        OutlinedButton(onClick = onClose) { Text("返回目标与预算") }
        SafetyBanner("目标预测仅为零收益只读情景，不保存计划、不调拨资金、不执行交易。")
        Text("${goal.config.name} · 目标预测")
        Text("从来源统计月的下月起查看12个月。仅关联同作用域唯一预算；无预算或同月多个预算时不选择。预算覆盖未经人工确认，不推断自由现金或达成日期。")
        OutlinedButton(onClick = { refresh++ }) { Text("刷新预测") }
        ReadObservation(goal.id, refresh, { repository.forecast(goal) }) { result ->
            KeyValueRow("来源日期", result.asOfDate ?: "未知")
            Text(forecastFreshness(result.asOfDate))
            Text(dataQuality(result.actualProgress.quality))
            result.actualProgress.reason?.let { Text(it) }
            KeyValueRow("实际完成率", goalRate(result.actualProgress))
            KeyValueRow("实际缺口", goalRemaining(goal, result.actualProgress))
            Text(dataQuality(result.baseline.quality))
            KeyValueRow("假设达成时间", forecastOutcome(result.baseline))
            result.baseline.assumption?.let { Text(it) }
            if (result.baseline.months.isEmpty()) EmptySection("暂无逐月预测", "后端未返回月份，请人工重试。")
            result.baseline.months.forEach { row ->
                SectionCard(row.month, dataQuality(row.quality)) {
                    row.reason?.let { Text(it) }
                    KeyValueRow("已知计划收入", observedMoney(row.plannedIncome, "OK", result.currency))
                    KeyValueRow("已知计划支出", observedMoney(row.plannedExpenses, "OK", result.currency))
                    KeyValueRow("计划预留（非划转）", observedMoney(row.plannedReserve, "OK", result.currency))
                    KeyValueRow("假设新增投入", observedMoney(row.contribution, row.quality, result.currency))
                    KeyValueRow("假设累计进度", observedMoney(row.cumulativeProgress, row.quality, result.currency))
                    KeyValueRow("假设剩余缺口", observedMoney(row.remainingGap, row.quality, result.currency))
                }
            }
        }
    }
}
