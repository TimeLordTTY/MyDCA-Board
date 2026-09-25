package com.timelordtty.mydca.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.timelordtty.mydca.data.repository.AiAccountingRepository
import com.timelordtty.mydca.data.repository.TodoRepository
import com.timelordtty.mydca.notification.DraftCreationGate
import com.timelordtty.mydca.notification.NotificationCandidate
import com.timelordtty.mydca.notification.NotificationCandidateStatus
import com.timelordtty.mydca.notification.NotificationCandidateStore
import com.timelordtty.mydca.notification.NotificationDraftInput
import com.timelordtty.mydca.notification.NotificationNavigationTarget
import com.timelordtty.mydca.ui.state.TodayTodoStateHolder
import com.timelordtty.mydca.ui.state.TodayTodoUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun TodayTodoScreen(
    todoRepository: TodoRepository?,
    aiAccountingRepository: AiAccountingRepository?,
    apiConfigError: String?,
    selectedCandidateId: String?,
    onOpenDraft: (Long) -> Unit,
) {
    var state by remember { mutableStateOf(TodayTodoUiState()) }
    val scope = rememberCoroutineScope()
    val holder = remember(todoRepository) { todoRepository?.let { TodayTodoStateHolder(it) } }

    fun refresh(showLoading: Boolean) {
        if (holder == null) {
            state = state.copy(
                isLoading = false,
                isRefreshing = false,
                errorMessage = apiConfigError ?: "接口配置未就绪",
            )
            return
        }
        val loaded = state.todos != null
        val previous = state.copy(
            isLoading = showLoading && !loaded,
            isRefreshing = !(showLoading && !loaded),
            errorMessage = null,
        )
        state = previous
        scope.launch { state = holder.load(previous) }
    }

    LaunchedEffect(todoRepository, apiConfigError) {
        refresh(showLoading = true)
    }

    PageScaffold {
        SafetyBanner("今日待办只负责发现和导航。移动端不会自动确认草稿、不会执行结算、不会写正式账本。")
        PaymentCandidateSection(
            repository = aiAccountingRepository,
            apiConfigError = apiConfigError,
            selectedCandidateId = selectedCandidateId,
            onOpenDraft = onOpenDraft,
        )

        when {
            state.isLoading -> LoadingSection("正在加载今日待办")
            state.todos == null && !state.errorMessage.isNullOrBlank() ->
                ErrorSection("今日待办加载失败", state.errorMessage!!, { refresh(showLoading = true) })
            else -> {
                RefreshBar(state.lastUpdatedAt, state.isRefreshing) { refresh(showLoading = false) }
                if (!state.errorMessage.isNullOrBlank()) {
                    NoticeBanner(
                        title = "本次刷新失败，以下保留上次成功待办",
                        message = state.errorMessage!!,
                        onRetry = { refresh(showLoading = false) },
                    )
                }
                state.todos?.let { todos -> TodayTodoContent(todos, onOpenDraft) }
            }
        }
    }
}

@Composable
private fun PaymentCandidateSection(
    repository: AiAccountingRepository?,
    apiConfigError: String?,
    selectedCandidateId: String?,
    onOpenDraft: (Long) -> Unit,
) {
    val candidates by NotificationCandidateStore.candidates.collectAsState()
    val visible = candidates.filter { it.status != NotificationCandidateStatus.DISMISSED }
        .sortedByDescending { if (it.id == selectedCandidateId) Long.MAX_VALUE else it.postedAt }
    SectionCard("支付通知候选", "${visible.size} 条。候选只会在你手动确认后生成 DRAFT，不会自动 preview、confirm 或正式入账。") {
        if (visible.isEmpty()) StatusPill("暂无候选")
        visible.forEach { candidate ->
            PaymentCandidateCard(candidate, repository, apiConfigError, onOpenDraft)
        }
    }
    LaunchedEffect(selectedCandidateId, visible) {
        if (selectedCandidateId != null && visible.any { it.id == selectedCandidateId }) {
            NotificationNavigationTarget.consume(selectedCandidateId)
        }
    }
}

@Composable
private fun PaymentCandidateCard(
    candidate: NotificationCandidate,
    repository: AiAccountingRepository?,
    apiConfigError: String?,
    onOpenDraft: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var confirm by remember(candidate.id) { mutableStateOf(false) }
    var creating by remember(candidate.id) { mutableStateOf(false) }
    var message by remember(candidate.id) { mutableStateOf<String?>(null) }
    val canCreate = NotificationDraftInput.canCreateDraft(candidate, repository != null && apiConfigError.isNullOrBlank(), candidate.createdDraftId)
    SectionCard(candidate.sourceHint ?: candidate.appLabel ?: "支付应用", candidate.textSnippet ?: candidate.titleSnippet ?: "已脱敏通知") {
        KeyValueRow("时间", SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(candidate.postedAt)))
        KeyValueRow("金额", "${candidate.amount ?: "未识别"} 元")
        KeyValueRow("状态", candidate.status.name)
        message?.let { Text(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(enabled = canCreate && !creating, onClick = { confirm = true }) { Text(if (creating) "生成中" else "生成草稿") }
            candidate.createdDraftId?.let { id -> OutlinedButton(onClick = { onOpenDraft(id) }) { Text("打开草稿") } }
            OutlinedButton(enabled = !creating, onClick = { NotificationCandidateStore.dismiss(candidate.id) }) { Text("忽略") }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { if (!creating) confirm = false },
        title = { Text("确认生成草稿") },
        text = { Text("仅生成 DRAFT。后续 preview 和二次 confirm 必须由你手动完成。") },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } },
        confirmButton = { TextButton(enabled = !creating, onClick = {
            val api = repository ?: return@TextButton
            if (candidate.createdDraftId != null || creating || !DraftCreationGate.tryAcquire(candidate.id)) return@TextButton
            confirm = false; creating = true
            scope.launch {
                val raw = NotificationDraftInput.buildRawInput(candidate)
                when (val parsed = api.parseText(raw, candidate.fingerprint)) {
                    is NetworkResult.Failure -> message = "解析失败：${parsed.message}"
                    is NetworkResult.Success -> {
                        val intent = parsed.data.copy(sourceType = "PAYMENT_NOTIFICATION", sourceRef = candidate.fingerprint, rawInput = raw, amount = parsed.data.amount ?: candidate.amount?.toDoubleOrNull())
                        when (val result = api.draftFromIntent(intent)) {
                            is NetworkResult.Failure -> message = "创建失败：${result.message}"
                            is NetworkResult.Success -> {
                                val draftId = result.data.draft.id
                                NotificationCandidateStore.markDraftCreated(candidate.id, draftId)
                                message = "草稿 #$draftId 已创建，尚未正式入账。"
                            }
                        }
                    }
                }
                creating = false
                DraftCreationGate.release(candidate.id)
            }
        }) { Text("确认生成") } },
    )
}

@Composable
private fun TodayTodoContent(
    todos: TodayTodoDto,
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
        val items = todos.items.orEmpty()
        if (items.isEmpty()) {
            StatusPill("暂无待办")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { item ->
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
