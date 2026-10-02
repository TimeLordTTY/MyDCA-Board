package com.timelordtty.mydca.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.repository.GoalBudgetRepository
import com.timelordtty.mydca.ui.state.*
import java.time.YearMonth

@Composable
fun GoalBudgetScreen(api: WealthHubApi, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val repository = remember(api) { GoalBudgetRepository(api) }
    var budgetTab by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(0) }
    var refresh by remember { mutableStateOf(0) }
    val month = YearMonth.now().toString()
    PageScaffold {
        OutlinedButton(onClick = onClose) { Text("返回总览") }
        SafetyBanner("目标与预算仅供只读查看，不创建或编辑，不执行交易或正式入账。")
        OutlinedButton(onClick = { budgetTab = !budgetTab; page = 0 }) {
            Text(if (budgetTab) "查看目标进度" else "查看本月预算")
        }
        OutlinedButton(onClick = { refresh++ }) { Text("刷新") }
        if (budgetTab) {
            ReadPage("本月预算 $month", page, refresh, { repository.budgets(page) },
                onPrevious = { page-- }, onNext = { page++ }) { budgets ->
                val current = currentMonthBudgets(budgets, month)
                if (current.isEmpty()) EmptySection("本页无本月预算", "预算按分页读取，可继续查看下一页。")
                current.forEach { budget ->
                    SectionCard(budget.config.name, "${budget.config.month} · ${budget.config.currency} · ${budget.config.scope}") {
                        ReadObservation(budget.id, refresh, { repository.comparison(budget.id) }) { result ->
                            Text(dataQuality(result.quality))
                            KeyValueRow("计划收入", observedMoney(result.plannedIncome, "OK", budget.config.currency))
                            KeyValueRow("计划支出", observedMoney(result.plannedExpenses, "OK", budget.config.currency))
                            KeyValueRow("计划储备", observedMoney(result.plannedReserve, "OK", budget.config.currency))
                            KeyValueRow("实际收入", observedMoney(result.actualIncome, result.quality, budget.config.currency))
                            KeyValueRow("实际支出", observedMoney(result.actualExpenses, result.quality, budget.config.currency))
                            KeyValueRow("剩余预算", observedMoney(result.remainingBudget, result.quality, budget.config.currency))
                            Text(overspendStatus(result.overspent, result.quality))
                            result.warnings.forEach { Text(it) }
                            KeyValueRow("未匹配流水", result.unmatchedPostings.toString())
                            result.items.forEach { row ->
                                SectionCard(row.item.name, dataQuality(row.quality)) {
                                    KeyValueRow("计划", observedMoney(row.item.planned, "OK", budget.config.currency))
                                    KeyValueRow("实际", observedMoney(row.actual, row.quality, budget.config.currency))
                                    KeyValueRow("剩余", observedMoney(row.remaining, row.quality, budget.config.currency))
                                    Text(overspendStatus(row.overspent, row.quality))
                                    if (row.quality == "PARTIAL") KeyValueRow("已知部分（非最终实际）", observedMoney(row.knownActual, "OK", budget.config.currency))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            ReadPage("目标进度", page, refresh, { repository.goals(page) },
                onPrevious = { page-- }, onNext = { page++ }) { goals ->
                goals.forEach { goal ->
                    SectionCard(goal.config.name, "${goal.config.currency} · ${goal.config.scope}") {
                        KeyValueRow("目标日期", goal.config.targetDate)
                        KeyValueRow("目标金额", observedMoney(goal.config.targetValue, "OK", goal.config.currency))
                        KeyValueRow("状态", when (goal.config.state) { "ACTIVE" -> "进行中"; "PAUSED" -> "已暂停"; "ARCHIVED" -> "已归档"; else -> "未知" })
                        ReadObservation(goal.id, refresh, { repository.progress(goal.id) }) { result ->
                            Text(dataQuality(result.quality))
                            result.reason?.let { Text(it) }
                            KeyValueRow("完成率", goalRate(result))
                            KeyValueRow("当前值", observedMoney(result.currentValue, result.quality, goal.config.currency))
                            KeyValueRow("剩余值", goalRemaining(goal, result))
                            KeyValueRow("数据日期", result.asOfDate ?: "未知")
                            KeyValueRow("距离目标日期", "${result.daysRemaining} 天${if (result.overdue) "（已逾期）" else ""}")
                            if (result.quality == "PARTIAL") KeyValueRow("已知部分（非最终当前值）", observedMoney(result.knownValue, "OK", goal.config.currency))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> ReadPage(title: String, page: Int, refresh: Int,
    load: suspend () -> com.timelordtty.mydca.core.network.NetworkResult<List<T>>,
    onPrevious: () -> Unit, onNext: () -> Unit, content: @Composable (List<T>) -> Unit) {
    var state by remember(page, refresh) { mutableStateOf(ResearchReadState<List<T>>()) }
    var retry by remember(page, refresh) { mutableStateOf(0) }
    LaunchedEffect(page, refresh, retry) { state = ResearchReadState(); state = state.loaded(load()) }
    Text("$title · 第 ${page + 1} 页")
    ReadContent(state, { retry++ }) { rows ->
        if (rows.isEmpty()) EmptySection("暂无数据", "当前页没有可查看的记录。") else content(rows)
    }
    OutlinedButton(onClick = onPrevious, enabled = page > 0 && !state.loading) { Text("上一页") }
    OutlinedButton(onClick = onNext, enabled = !state.loading && state.error == null && state.data?.size == 20) { Text("下一页") }
}

@Composable
private fun <T> ReadObservation(id: String, refresh: Int,
    load: suspend () -> com.timelordtty.mydca.core.network.NetworkResult<T>, content: @Composable (T) -> Unit) {
    var state by remember(id, refresh) { mutableStateOf(ResearchReadState<T>()) }
    var retry by remember(id, refresh) { mutableStateOf(0) }
    LaunchedEffect(id, refresh, retry) { state = ResearchReadState(); state = state.loaded(load()) }
    ReadContent(state, { retry++ }, content)
}

@Composable
private fun <T> ReadContent(state: ResearchReadState<T>, retry: () -> Unit, content: @Composable (T) -> Unit) {
    when {
        state.loading -> LoadingSection("正在读取")
        state.error != null -> ErrorSection("读取失败", state.error, retry)
        else -> state.data?.let { content(it) }
    }
}
