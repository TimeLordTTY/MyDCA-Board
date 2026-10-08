package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.api.WealthHubApi
import kotlinx.coroutines.CancellationException
import java.io.IOException
import retrofit2.HttpException
import java.net.SocketTimeoutException

/** GET observations and explicitly read-only forecast; never persisted locally. */
class GoalBudgetRepository(private val api: WealthHubApi) {
    suspend fun goals(page: Int) = read { api.goals(page) }
    suspend fun progress(id: String) = read { api.goalProgress(id) }
    suspend fun budgets(page: Int) = read { api.monthlyBudgets(page) }
    suspend fun comparison(id: String) = read { api.budgetComparison(id) }

    // No budget coverage assertion, extra funds, or positive return is inferred on mobile.
    suspend fun forecast(goal: com.timelordtty.mydca.data.dto.GoalDto) = read {
        val progress = api.goalProgress(goal.id)
        val start = java.time.YearMonth.from(java.time.LocalDate.parse(progress.asOfDate)).plusMonths(1)
        val budgets = mutableListOf<com.timelordtty.mydca.data.dto.BudgetDto>()
        var page = 0
        do {
            val rows = api.monthlyBudgets(page++)
            budgets.addAll(rows)
            if (page >= 100 && rows.size == 20) throw IOException("预算过多，无法完整读取")
        } while (rows.size == 20)
        val months = (0L..11L).map { offset ->
            val month = start.plusMonths(offset).toString()
            val matches = budgets.filter { it.config.month == month && it.config.scope == goal.config.scope }
                .distinctBy { it.id }
            com.timelordtty.mydca.data.dto.ForecastMonthInputDto(month, matches.singleOrNull()?.id)
        }
        api.goalForecast(goal.id, com.timelordtty.mydca.data.dto.GoalForecastRequestDto(
            start.toString(), start.plusMonths(11).toString(), months))
    }

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
