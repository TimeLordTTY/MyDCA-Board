package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.dto.PendingSettlementOrderDto
import com.timelordtty.mydca.data.dto.SettlementConfirmDto
import com.timelordtty.mydca.data.dto.SettlementPreviewDto
import com.timelordtty.mydca.data.dto.SettlementPreviewRequestDto

/**
 * 人工结算数据仓库（v0.13.0）。
 *
 * 只封装「待结算列表 / 只读预览 / 主人二次确认后的正式结算」三个后端接口：
 * - listPendingSettlements 只读待结算订单；
 * - previewSettlement 只读计算结算影响，不写 settlement_confirm / ledger_txn；
 * - confirmSettlement 才会真正落账，且必须携带 preview 返回的 freshPreviewToken。
 *
 * 仓库不会自动 preview、不会自动 confirm，也不会连接数据库或券商接口。
 */
open class SettlementRepository(
    private val api: WealthHubApi,
) {
    open suspend fun listPendingSettlements(): NetworkResult<List<PendingSettlementOrderDto>> = safeNetworkCall {
        api.getPendingSettlements()
    }

    open suspend fun previewSettlement(
        request: SettlementPreviewRequestDto,
    ): NetworkResult<SettlementPreviewDto> = safeNetworkCall {
        api.previewSettlement(request)
    }

    open suspend fun confirmSettlement(
        request: SettlementPreviewRequestDto,
    ): NetworkResult<SettlementConfirmDto> = safeNetworkCall {
        api.confirmSettlement(request)
    }
}