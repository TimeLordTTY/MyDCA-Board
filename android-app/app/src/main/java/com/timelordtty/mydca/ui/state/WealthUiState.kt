package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.data.dto.MobileAccountDto
import com.timelordtty.mydca.data.dto.MobileCashFlowDto
import com.timelordtty.mydca.data.dto.MobileHoldingDto
import com.timelordtty.mydca.data.dto.MobileOverviewDto
import com.timelordtty.mydca.data.dto.MobilePageDto
import com.timelordtty.mydca.data.dto.MobileTransactionDto

/**
 * 总览页状态。
 *
 * isLoading 只在首次加载、页面还没有任何数据时为 true；刷新已有数据时使用 isRefreshing，
 * 避免刷新过程中清空主人正在查看的内容。
 */
data class WealthOverviewUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val overview: MobileOverviewDto? = null,
    val errorMessage: String? = null,
    val lastUpdatedAt: Long? = null,
)

/** 资产页状态，字段与后端 mobile 分页接口一一对应。 */
data class AssetsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val accounts: MobilePageDto<MobileAccountDto> = MobilePageDto(),
    val transactions: MobilePageDto<MobileTransactionDto> = MobilePageDto(),
    val holdings: MobilePageDto<MobileHoldingDto> = MobilePageDto(),
    val cashFlow: MobileCashFlowDto? = null,
    val errorMessage: String? = null,
    val lastUpdatedAt: Long? = null,
)

/** 单个账户详情状态，只在主人主动查看时按需加载，不参与列表批量刷新。 */
data class AccountDetailUiState(
    val accountId: Long,
    val isLoading: Boolean = false,
    val account: MobileAccountDto? = null,
    val errorMessage: String? = null,
    val lastUpdatedAt: Long? = null,
)
