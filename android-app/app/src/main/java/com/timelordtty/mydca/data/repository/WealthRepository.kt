package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.dto.MobileAccountDto
import com.timelordtty.mydca.data.dto.MobileHoldingDto
import com.timelordtty.mydca.data.dto.MobileOverviewDto
import com.timelordtty.mydca.data.dto.MobilePageDto
import com.timelordtty.mydca.data.dto.MobileTransactionDto

open class WealthRepository(
    private val api: WealthHubApi,
) {
    open suspend fun getOverview(): NetworkResult<MobileOverviewDto> = safeNetworkCall {
        api.getMobileOverview()
    }

    open suspend fun getAccounts(page: Int, pageSize: Int): NetworkResult<MobilePageDto<MobileAccountDto>> = safeNetworkCall {
        api.getMobileAccounts(page, pageSize)
    }

    open suspend fun getAccountDetail(accountId: Long): NetworkResult<MobileAccountDto> = safeNetworkCall {
        api.getMobileAccountDetail(accountId)
    }

    open suspend fun getTransactions(page: Int, pageSize: Int): NetworkResult<MobilePageDto<MobileTransactionDto>> = safeNetworkCall {
        api.getMobileTransactions(page, pageSize)
    }

    open suspend fun getHoldings(page: Int, pageSize: Int): NetworkResult<MobilePageDto<MobileHoldingDto>> = safeNetworkCall {
        api.getMobileHoldings(page, pageSize)
    }
}
