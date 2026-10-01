package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.api.WealthHubApi
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/** Observation reads never evaluate, acknowledge, mute or execute. */
class RiskWatchRepository(private val api: WealthHubApi) {
    suspend fun rules(page: Int) = read { api.riskRules(page) }
    suspend fun snapshots(id: String, page: Int) = read { api.riskSnapshots(id, page) }
    suspend fun events(id: String, page: Int) = read { api.riskEvents(id, page) }
    private suspend fun <T> read(block: suspend () -> T): NetworkResult<T> = try {
        NetworkResult.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        NetworkResult.Failure(if (error is HttpException) error.toUserMessage() else "风险数据读取失败，请检查网络后重试", error)
    }
}
