package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.api.WealthHubApi
import kotlinx.coroutines.CancellationException
import java.io.IOException
import retrofit2.HttpException
import java.net.SocketTimeoutException

/** This feature exposes only GET requests; observations are never persisted locally. */
class GoalBudgetRepository(private val api: WealthHubApi) {
    suspend fun goals(page: Int) = read { api.goals(page) }
    suspend fun progress(id: String) = read { api.goalProgress(id) }
    suspend fun budgets(page: Int) = read { api.monthlyBudgets(page) }
    suspend fun comparison(id: String) = read { api.budgetComparison(id) }

    private suspend fun <T> read(block: suspend () -> T): NetworkResult<T> = try {
        NetworkResult.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        NetworkResult.Failure(when (error) {
            is HttpException, is SocketTimeoutException -> error.toUserMessage()
            is IOException -> "网络连接失败或目标预算数据不可读取，请重试"
            else -> "目标预算数据读取失败，请稍后重试"
        }, error)
    }
}
