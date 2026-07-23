package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.dto.MobileAccountDto
import com.timelordtty.mydca.data.dto.MobileHoldingDto
import com.timelordtty.mydca.data.dto.MobileOverviewDto
import com.timelordtty.mydca.data.dto.MobilePageDto
import com.timelordtty.mydca.data.dto.MobileTransactionDto
import com.timelordtty.mydca.data.repository.WealthRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class WealthStateHolder(
    private val repository: WealthRepository,
) {
    suspend fun loadOverview(): WealthOverviewUiState {
        return when (val result = repository.getOverview()) {
            is NetworkResult.Success -> {
                val overview = result.data
                val isEmpty = overview.totalAssets.isNullOrBlank() &&
                    overview.accountCount == 0 &&
                    overview.recentActivities.isEmpty()
                WealthOverviewUiState(
                    isLoading = false,
                    overview = if (isEmpty) null else overview,
                )
            }
            is NetworkResult.Failure -> WealthOverviewUiState(
                isLoading = false,
                errorMessage = result.message,
            )
        }
    }

    suspend fun loadAssets(page: Int = 1, pageSize: Int = 20): AssetsUiState = coroutineScope {
        val accountsDeferred = async { repository.getAccounts(page, pageSize) }
        val transactionsDeferred = async { repository.getTransactions(page, pageSize) }
        val holdingsDeferred = async { repository.getHoldings(page, pageSize) }
        val cashFlowDeferred = async { repository.getCashFlow() }

        val accounts = accountsDeferred.await()
        val transactions = transactionsDeferred.await()
        val holdings = holdingsDeferred.await()
        val cashFlow = cashFlowDeferred.await()
        val firstError = listOf(accounts, transactions, holdings, cashFlow).filterIsInstance<NetworkResult.Failure>().firstOrNull()

        AssetsUiState(
            isLoading = false,
            accounts = (accounts as? NetworkResult.Success)?.data ?: MobilePageDto<MobileAccountDto>(),
            transactions = (transactions as? NetworkResult.Success)?.data ?: MobilePageDto<MobileTransactionDto>(),
            holdings = (holdings as? NetworkResult.Success)?.data ?: MobilePageDto<MobileHoldingDto>(),
            cashFlow = (cashFlow as? NetworkResult.Success)?.data,
            errorMessage = firstError?.message,
        )
    }
}
