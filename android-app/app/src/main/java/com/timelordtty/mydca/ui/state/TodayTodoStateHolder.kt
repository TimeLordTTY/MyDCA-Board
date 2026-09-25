package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.dto.TodayTodoDto
import com.timelordtty.mydca.data.repository.TodoRepository

/**
 * 今日待办页状态。
 *
 * 与总览/资产页一致：只在首次加载时占位，刷新失败保留上一次成功待办并单独提示错误。
 */
data class TodayTodoUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val todos: TodayTodoDto? = null,
    val errorMessage: String? = null,
    val lastUpdatedAt: Long? = null,
)

/** 只读装载今日待办，不触发任何确认、入账或交易动作。 */
class TodayTodoStateHolder(
    private val repository: TodoRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun load(
        previous: TodayTodoUiState = TodayTodoUiState(isLoading = false),
    ): TodayTodoUiState {
        return when (val result = repository.getTodayTodos()) {
            is NetworkResult.Success -> previous.copy(
                isLoading = false,
                isRefreshing = false,
                todos = result.data,
                errorMessage = null,
                lastUpdatedAt = clock(),
            )
            is NetworkResult.Failure -> previous.copy(
                isLoading = false,
                isRefreshing = false,
                errorMessage = result.message,
            )
        }
    }
}
