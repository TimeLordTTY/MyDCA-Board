package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.dto.MobileAccountDto
import com.timelordtty.mydca.data.dto.MobileHoldingDto
import com.timelordtty.mydca.data.dto.MobileTransactionDto
import com.timelordtty.mydca.data.repository.WealthRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 财富数据状态装配。
 *
 * 日常使用时一次网络抖动不应该清空页面：刷新失败只记录 errorMessage 并保留上一次成功数据，
 * 只有首次加载失败才进入空错误态。状态本身只读，不会写正式账本。
 */
class WealthStateHolder(
    private val repository: WealthRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun loadOverview(
        previous: WealthOverviewUiState = WealthOverviewUiState(isLoading = false),
    ): WealthOverviewUiState {
        return when (val result = repository.getOverview()) {
            is NetworkResult.Success -> {
                val overview = result.data
                val isEmpty = overview.totalAssets.isNullOrBlank() &&
                    overview.accountCount == 0 &&
                    overview.recentActivities.isEmpty()
                previous.copy(
                    isLoading = false,
                    isRefreshing = false,
                    overview = if (isEmpty) null else overview,
                    errorMessage = null,
                    lastUpdatedAt = clock(),
                )
            }
            is NetworkResult.Failure -> previous.copy(
                isLoading = false,
                isRefreshing = false,
                errorMessage = result.message,
            )
        }
    }

    suspend fun loadAssets(
        page: Int = 1,
        pageSize: Int = 20,
        previous: AssetsUiState = AssetsUiState(isLoading = false),
    ): AssetsUiState = coroutineScope {
        val accountsDeferred = async { repository.getAccounts(page, pageSize) }
        val transactionsDeferred = async { repository.getTransactions(page, pageSize) }
        val holdingsDeferred = async { repository.getHoldings(page, pageSize) }
        val cashFlowDeferred = async { repository.getCashFlow() }

        val accounts = accountsDeferred.await()
        val transactions = transactionsDeferred.await()
        val holdings = holdingsDeferred.await()
        val cashFlow = cashFlowDeferred.await()
        val results: List<NetworkResult<Any?>> = listOf(accounts, transactions, holdings, cashFlow)
        val firstError = results.filterIsInstance<NetworkResult.Failure>().firstOrNull()
        val anySuccess = results.any { it is NetworkResult.Success }

        AssetsUiState(
            isLoading = false,
            isRefreshing = false,
            accounts = (accounts as? NetworkResult.Success)?.data ?: previous.accounts,
            transactions = (transactions as? NetworkResult.Success)?.data ?: previous.transactions,
            holdings = (holdings as? NetworkResult.Success)?.data ?: previous.holdings,
            cashFlow = (cashFlow as? NetworkResult.Success)?.data ?: previous.cashFlow,
            errorMessage = firstError?.message,
            lastUpdatedAt = if (anySuccess) clock() else previous.lastUpdatedAt,
        )
    }

    suspend fun loadAccountDetail(accountId: Long): AccountDetailUiState {
        return when (val result = repository.getAccountDetail(accountId)) {
            is NetworkResult.Success -> AccountDetailUiState(
                accountId = accountId,
                isLoading = false,
                account = result.data,
                lastUpdatedAt = clock(),
            )
            is NetworkResult.Failure -> AccountDetailUiState(
                accountId = accountId,
                isLoading = false,
                errorMessage = result.message,
            )
        }
    }
}
