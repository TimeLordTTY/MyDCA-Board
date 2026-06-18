package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.dto.TodayTodoDto

/**
 * 今日待办数据仓库。
 * 仅封装只读查询，移动端不会在待办页自动执行确认、入账或交易动作。
 */
class TodoRepository(
    private val api: WealthHubApi,
) {
    suspend fun getTodayTodos(): NetworkResult<TodayTodoDto> = safeCall {
        api.getTodayTodos()
    }

    private suspend fun <T> safeCall(block: suspend () -> T): NetworkResult<T> {
        return try {
            NetworkResult.Success(block())
        } catch (error: Throwable) {
            NetworkResult.Failure(error.message ?: "移动端接口调用失败", error)
        }
    }
}
