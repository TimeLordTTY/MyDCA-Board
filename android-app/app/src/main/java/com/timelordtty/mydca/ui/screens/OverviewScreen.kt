package com.timelordtty.mydca.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timelordtty.mydca.data.repository.WealthRepository
import com.timelordtty.mydca.ui.state.WealthOverviewUiState
import com.timelordtty.mydca.ui.state.WealthStateHolder
import kotlinx.coroutines.launch

@Composable
fun OverviewScreen(
    wealthRepository: WealthRepository?,
    apiConfigError: String?,
) {
    var state by remember { mutableStateOf(WealthOverviewUiState()) }
    val scope = rememberCoroutineScope()
    val holder = remember(wealthRepository) { wealthRepository?.let { WealthStateHolder(it) } }

    fun refresh(showLoading: Boolean) {
        val loaded = state.overview != null
        if (holder == null) {
            state = state.copy(
                isLoading = false,
                isRefreshing = false,
                errorMessage = apiConfigError ?: "接口配置未就绪",
            )
            return
        }
        val previous = state.copy(
            isLoading = showLoading && !loaded,
            isRefreshing = !(showLoading && !loaded),
            errorMessage = null,
        )
        state = previous
        scope.launch { state = holder.loadOverview(previous) }
    }

    LaunchedEffect(wealthRepository, apiConfigError) {
        refresh(showLoading = true)
    }

    PageScaffold {
        SafetyBanner("总览只展示后端真实只读财富摘要；金额、持仓与待办口径以服务端返回为准，移动端不自行重算正式账本。")
        when {
            state.isLoading -> LoadingSection("正在加载总览")
            state.overview == null && !state.errorMessage.isNullOrBlank() ->
                ErrorSection("总览加载失败", state.errorMessage!!, { refresh(showLoading = true) })
            state.overview == null -> EmptySection("暂无财富数据", "当前账户还没有可展示的资产、持仓或活动数据。")
            else -> {
                RefreshBar(state.lastUpdatedAt, state.isRefreshing) { refresh(showLoading = false) }
                if (!state.errorMessage.isNullOrBlank()) {
                    NoticeBanner(
                        title = "本次刷新失败，以下保留上次成功数据",
                        message = state.errorMessage!!,
                        onRetry = { refresh(showLoading = false) },
                    )
                }
                OverviewContent(state)
            }
        }
    }
}

@Composable
private fun OverviewContent(state: WealthOverviewUiState) {
    val overview = requireNotNull(state.overview)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("总资产", formatMoney(overview.totalAssets), Modifier.weight(1f))
        MetricCard("净资产", formatMoney(overview.netWorth), Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("账户", overview.accountCount.toString(), Modifier.weight(1f))
        MetricCard("草稿/待结算", "${overview.draftCount}/${overview.settlementCount}", Modifier.weight(1f))
    }
    SectionCard(title = "资产摘要", description = "最近更新：${formatDateTime(overview.lastUpdatedAt)}") {
        KeyValueRow("现金余额", formatMoney(overview.cashBalance))
        KeyValueRow("持仓市值", formatMoney(overview.positionValue))
        KeyValueRow("总负债", formatMoney(overview.totalLiabilities))
        KeyValueRow("待办总数", overview.totalTodoCount.toString())
        if (overview.draftCount > 0) {
            StatusPill("还有 ${overview.draftCount} 条 DRAFT 草稿等待人工确认")
        }
    }
    SectionCard(title = "资金分区", description = "仅由后端汇总 REAL/CASH 叶子账户，父账户不重复计入。") {
        KeyValueRow("可支出 SPENDABLE", formatMoney(overview.spendableAmount))
        KeyValueRow("专款 RESERVED", formatMoney(overview.reservedFundAmount))
        KeyValueRow("可投资 INVESTABLE", formatMoney(overview.investableAmount))
        KeyValueRow("待分配", formatMoney(overview.unallocatedAmount))
    }
    SectionCard(title = "最近活动", description = "默认展示最近 5 条流水摘要。") {
        if (overview.recentActivities.isEmpty()) {
            StatusPill("暂无活动")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                overview.recentActivities.forEach { item ->
                    SectionCard(
                        title = item.note ?: item.txnType ?: item.txnId,
                        description = "${item.accountName ?: "未命名账户"} · ${formatDateTime(item.occurredAt)}",
                    ) {
                        KeyValueRow("金额", formatSignedMoney(item.amount))
                        KeyValueRow("状态", item.status ?: "暂无")
                    }
                }
            }
        }
    }
}
