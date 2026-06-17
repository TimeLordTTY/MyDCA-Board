package com.timelordtty.mydca.data.dto

/**
 * 今日待办摘要 DTO，对齐后端 GET /api/v2/todos/today。
 */
data class TodayTodoDto(
    val date: String? = null,
    val totalCount: Int = 0,
    val draftCount: Int = 0,
    val settlementCount: Int = 0,
    val suggestionCount: Int = 0,
    val items: List<TodoItemDto> = emptyList(),
)

/**
 * 待办单项 DTO。
 *
 * actionPath 只用于移动端导航，不代表自动执行。
 */
data class TodoItemDto(
    val type: String,
    val refId: String,
    val title: String,
    val description: String? = null,
    val status: String? = null,
    val actionPath: String? = null,
)
