package com.timelordtty.mydca.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.timelordtty.mydca.data.repository.WealthRepository
import com.timelordtty.mydca.ui.state.AssetsUiState
import com.timelordtty.mydca.ui.state.WealthStateHolder
import kotlinx.coroutines.launch

@Composable
fun AssetsScreen(
    wealthRepository: WealthRepository?,
    apiConfigError: String?,
) {
    var state by remember { mutableStateOf(AssetsUiState()) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        val repository = wealthRepository ?: run {
            state = AssetsUiState(isLoading = false, errorMessage = apiConfigError ?: "接口配置未就绪")
            return
        }
        val holder = WealthStateHolder(repository)
        scope.launch {
            state = AssetsUiState(isLoading = true)
            state = holder.loadAssets()
        }
    }

    LaunchedEffect(wealthRepository, apiConfigError) {
        refresh()
    }

    PageScaffold {
        SafetyBanner("资产页为只读浏览：可查看账户、流水、持仓，不提供改余额、确认、撤销或人工改账入口。")
        val errorMessage = state.errorMessage
        when {
            state.isLoading -> SectionCard("正在加载资产数据") { CircularProgressIndicator() }
            !errorMessage.isNullOrBlank() &&
                state.accounts.items.isEmpty() &&
                state.transactions.items.isEmpty() &&
                state.holdings.items.isEmpty() -> RetrySection("资产数据加载失败", errorMessage, ::refresh)
            else -> AssetsContent(state, ::refresh)
        }
    }
}

@Composable
private fun AssetsContent(state: AssetsUiState, onRefresh: () -> Unit) {
    SectionCard(title = "账户", description = "共 ${state.accounts.total} 个，只展示当前登录用户可读账户。") {
        OutlinedButton(onClick = onRefresh) { Text("刷新") }
        if (state.accounts.items.isEmpty()) {
            StatusPill("暂无账户")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.accounts.items.forEach { account ->
                    SectionCard(
                        title = account.accountName,
                        description = listOfNotNull(account.parentAccountName, account.accountType).joinToString(" · "),
                    ) {
                        KeyValueRow("余额/市值", formatMoney(account.balance))
                        KeyValueRow("可用", formatMoney(account.availableAmount))
                        KeyValueRow("保留", formatMoney(account.reservedAmount))
                        KeyValueRow("更新时间", formatDateTime(account.updatedAt))
                    }
                }
            }
        }
    }
    SectionCard(title = "流水", description = "默认按后端返回顺序读取第一页，只读查看。") {
        if (state.transactions.items.isEmpty()) {
            StatusPill("暂无流水")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.transactions.items.forEach { txn ->
                    SectionCard(
                        title = txn.note ?: txn.txnType ?: txn.txnId,
                        description = "${txn.accountName ?: "未命名账户"} · ${formatDateTime(txn.requestedAt)}",
                    ) {
                        KeyValueRow("金额", formatSignedMoney(txn.amount))
                        KeyValueRow("状态", txn.status ?: "暂无")
                        KeyValueRow("类型", txn.txnType ?: "暂无")
                    }
                }
            }
        }
    }
    SectionCard(title = "持仓", description = "只展示后端已有的份额、成本、市值与盈亏字段。") {
        if (state.holdings.items.isEmpty()) {
            StatusPill("暂无持仓")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.holdings.items.forEach { holding ->
                    SectionCard(
                        title = holding.productName ?: holding.productCode ?: "未命名标的",
                        description = holding.accountName ?: "未命名账户",
                    ) {
                        KeyValueRow("数量", formatShares(holding.totalShares))
                        KeyValueRow("成本", formatMoney(holding.totalCost))
                        KeyValueRow("均价", formatMoney(holding.avgCost))
                        KeyValueRow("市值", formatMoney(holding.marketValue))
                        KeyValueRow("盈亏", formatSignedMoney(holding.unrealizedPnl))
                    }
                }
            }
        }
    }
    if (!state.errorMessage.isNullOrBlank()) {
        RetrySection("部分数据加载不完整", state.errorMessage, onRefresh)
    }
}
