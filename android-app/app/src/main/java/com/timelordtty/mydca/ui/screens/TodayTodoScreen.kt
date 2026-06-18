package com.timelordtty.mydca.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.dto.TodayTodoDto
import com.timelordtty.mydca.data.dto.TodoItemDto
import com.timelordtty.mydca.data.repository.TodoRepository
import com.timelordtty.mydca.ui.state.AsyncState
import kotlinx.coroutines.launch

@Composable
fun TodayTodoScreen(
    todoRepository: TodoRepository,
    onOpenDraft: (Long) -> Unit,
) {
    var todoState by remember { mutableStateOf<AsyncState<TodayTodoDto>>(AsyncState.Loading) }
    val scope = rememberCoroutineScope()

    fun refreshTodos() {
        scope.launch {
            todoState = AsyncState.Loading
            todoState = when (val result = todoRepository.getTodayTodos()) {
                is NetworkResult.Success -> AsyncState.Success(result.data)
                is NetworkResult.Failure -> AsyncState.Error(result.message)
            }
        }
    }

    LaunchedEffect(todoRepository) {
        refreshTodos()
    }

    PageScaffold {
        SafetyBanner("今日待办只负责发现和导航。移动端不会自动确认草稿、不会执行结算、不会写正式账本。")

        when (val state = todoState) {
            AsyncState.Loading -> LoadingSection("正在加载今日待办")
            is AsyncState.Error -> ErrorSection(
                title = "今日待办加载失败",
                message = state.message,
                onRetry = ::refreshTodos,
            )
            is AsyncState.Success -> TodayTodoContent(
                todos = state.data,
                onRefresh = ::refreshTodos,
                onOpenDraft = onOpenDraft,
            )
        }
    }
}

@Composable
private fun TodayTodoContent(
    todos: TodayTodoDto,
    onRefresh: () -> Unit,
    onOpenDraft: (Long) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("草稿", todos.draftCount.toString(), Modifier.weight(1f))
        MetricCard("结算", todos.settlementCount.toString(), Modifier.weight(1f))
        MetricCard("建议", todos.suggestionCount.toString(), Modifier.weight(1f))
    }

    SectionCard(
        title = "今日待办",
        description = "${todos.date ?: "今日"} 共 ${todos.totalCount} 项。点击草稿待办只会打开草稿详情，不会直接入账。",
    ) {
        OutlinedButton(onClick = onRefresh) {
            Text("刷新")
        }
        if (todos.items.isEmpty()) {
            StatusPill("暂无待办")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                todos.items.forEach { item ->
                    TodoItemCard(item = item, onOpenDraft = onOpenDraft)
                }
            }
        }
    }
}

@Composable
private fun TodoItemCard(
    item: TodoItemDto,
    onOpenDraft: (Long) -> Unit,
) {
    val draftId = item.refId.toLongOrNull()
    SectionCard(
        title = item.title,
        description = item.description,
    ) {
        KeyValueRow("类型", item.type)
        KeyValueRow("状态", item.status ?: "待处理")
        KeyValueRow("引用", item.refId)
        if (item.type.equals("DRAFT", ignoreCase = true) && draftId != null) {
            Button(onClick = { onOpenDraft(draftId) }) {
                Text("打开草稿")
            }
        } else {
            StatusPill("当前移动端仅支持草稿导航")
        }
    }
}

@Composable
private fun LoadingSection(title: String) {
    SectionCard(title = title) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorSection(
    title: String,
    message: String,
    onRetry: () -> Unit,
) {
    SectionCard(title = title, description = message) {
        OutlinedButton(onClick = onRetry) {
            Text("重试")
        }
    }
}
