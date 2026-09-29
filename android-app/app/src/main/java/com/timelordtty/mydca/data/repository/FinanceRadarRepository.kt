package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.dto.FinanceRadarDto

/** Radar only fetches server facts; it has no draft, order or settlement write methods. */
class FinanceRadarRepository(private val api: WealthHubApi) {
    suspend fun load(): NetworkResult<FinanceRadarDto> = safeNetworkCall { api.getFinanceRadar() }
}
