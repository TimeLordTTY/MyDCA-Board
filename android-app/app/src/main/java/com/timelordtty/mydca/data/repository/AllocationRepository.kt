package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.api.WealthHubApi
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/** No configuration writes. The server's bodyless POST evaluate is strictly read-only. */
class AllocationRepository(private val api: WealthHubApi) {
    suspend fun policies(page: Int) = read { api.allocationPolicies(page) }
    suspend fun detail(id: String) = read { api.allocationPolicy(id) }
    suspend fun observation(id: String) = read { api.allocationObservation(id) }
    suspend fun preview(id: String) = read { api.allocationPreview(id) }
    private suspend fun <T> read(block: suspend () -> T): NetworkResult<T> = try {
        NetworkResult.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        NetworkResult.Failure(if (error is HttpException) error.toUserMessage() else "配置观察读取失败，请检查网络后重试", error)
    }
}
