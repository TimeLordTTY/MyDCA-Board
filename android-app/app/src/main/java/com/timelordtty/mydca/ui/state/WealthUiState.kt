package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.data.dto.MobileAccountDto
import com.timelordtty.mydca.data.dto.MobileHoldingDto
import com.timelordtty.mydca.data.dto.MobileOverviewDto
import com.timelordtty.mydca.data.dto.MobilePageDto
import com.timelordtty.mydca.data.dto.MobileTransactionDto

data class WealthOverviewUiState(
    val isLoading: Boolean = true,
    val overview: MobileOverviewDto? = null,
    val errorMessage: String? = null,
)

data class AssetsUiState(
    val isLoading: Boolean = true,
    val accounts: MobilePageDto<MobileAccountDto> = MobilePageDto(),
    val transactions: MobilePageDto<MobileTransactionDto> = MobilePageDto(),
    val holdings: MobilePageDto<MobileHoldingDto> = MobilePageDto(),
    val errorMessage: String? = null,
)
