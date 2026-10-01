package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.dto.ResearchPlanDto
import com.timelordtty.mydca.data.dto.ResearchRunDto
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import retrofit2.HttpException

/** Only GET capabilities. Cancellation must not become a visible network failure. */
class ResearchPlanRepository(private val api: WealthHubApi) {
    suspend fun plans(page: Int): NetworkResult<List<ResearchPlanDto>> = read { api.researchPlans(page) }
    suspend fun detail(id: String): NetworkResult<ResearchPlanDto> = read { api.researchPlan(id) }
    suspend fun runs(page: Int): NetworkResult<List<ResearchRunDto>> = read { api.researchRuns(page) }

    private suspend fun <T> read(block: suspend () -> T): NetworkResult<T> = try {
        NetworkResult.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        val message = when (error) {
            is HttpException, is SocketTimeoutException -> error.toUserMessage()
            is IOException -> "网络连接失败或研究数据不可读取，请重试"
            else -> "研究数据读取失败，请稍后重试"
        }
        NetworkResult.Failure(message, error)
    }
}
