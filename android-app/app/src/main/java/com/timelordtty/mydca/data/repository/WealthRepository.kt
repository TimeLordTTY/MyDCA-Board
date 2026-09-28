package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.dto.MobileAccountDto
import com.timelordtty.mydca.data.dto.MobileCashFlowDto
import com.timelordtty.mydca.data.dto.MobileHoldingByAccountDto
import com.timelordtty.mydca.data.dto.MobileHoldingDto
import com.timelordtty.mydca.data.dto.MobileOverviewDto
import com.timelordtty.mydca.data.dto.MobilePageDto
import com.timelordtty.mydca.data.dto.MobileTransactionDto
import com.timelordtty.mydca.data.dto.ProductDto

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

    open suspend fun getCashFlow(): NetworkResult<MobileCashFlowDto> = safeNetworkCall { api.getMobileCashFlow() }

    open suspend fun getTransactions(page: Int, pageSize: Int): NetworkResult<MobilePageDto<MobileTransactionDto>> = safeNetworkCall {
        api.getMobileTransactions(page, pageSize)
    }

    open suspend fun getHoldings(page: Int, pageSize: Int): NetworkResult<MobilePageDto<MobileHoldingDto>> = safeNetworkCall {
        api.getMobileHoldings(page, pageSize)
    }

    /** 只读拉取产品主数据列表，供投资草稿选择真实产品；不做任何自动匹配或下单。 */
    open suspend fun getProducts(keyword: String? = null, assetType: String? = null): NetworkResult<List<ProductDto>> =
        safeNetworkCall {
            api.getProducts(keyword, assetType, null)
        }

    /** 只读拉取指定产品在各账户的真实持仓来源，供卖出 / 赎回草稿让主人明确选择持仓来源账户。 */
    open suspend fun getProductHoldingsByAccount(productId: Long): NetworkResult<List<MobileHoldingByAccountDto>> =
        safeNetworkCall {
            api.getProductHoldingsByAccount(productId)
        }
}
