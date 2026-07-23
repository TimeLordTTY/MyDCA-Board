package com.timelordtty.mydca.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.dto.DraftLedgerEntryDto
import com.timelordtty.mydca.data.dto.DraftPreviewDto
import com.timelordtty.mydca.data.dto.MobileAccountDto
import com.timelordtty.mydca.data.repository.DraftRepository
import com.timelordtty.mydca.data.repository.WealthRepository
import com.timelordtty.mydca.ui.state.AsyncState
import com.timelordtty.mydca.ui.state.DraftEditForm
import com.timelordtty.mydca.ui.state.DraftEditState
import kotlinx.coroutines.launch

@Composable
fun DraftInboxScreen(
    draftRepository: DraftRepository?,
    wealthRepository: WealthRepository?,
    apiConfigError: String?,
    selectedDraftId: Long?,
    onDraftHandled: () -> Unit,
    onOpenImageOcr: () -> Unit,
) {
    var draftsState by remember { mutableStateOf<AsyncState<List<DraftLedgerEntryDto>>>(AsyncState.Loading) }
    var selectedDraft by remember { mutableStateOf<DraftLedgerEntryDto?>(null) }
    var previewState by remember { mutableStateOf<AsyncState<DraftPreviewDto>?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var editForm by remember { mutableStateOf(DraftEditForm()) }
    var editError by remember { mutableStateOf<String?>(null) }
    var isEditDirty by remember { mutableStateOf(false) }
    var isSavingDraft by remember { mutableStateOf(false) }
    var isPreviewingDraft by remember { mutableStateOf(false) }
    var isConfirmingDraft by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showIgnoreDialog by remember { mutableStateOf(false) }
    var selectableAccounts by remember { mutableStateOf<List<MobileAccountDto>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun setCurrentDraft(draft: DraftLedgerEntryDto?) {
        selectedDraft = draft
        editForm = draft?.let(DraftEditState::formFromDraft) ?: DraftEditForm()
        editError = null
        isEditDirty = false
        isSavingDraft = false
        isPreviewingDraft = false
        isConfirmingDraft = false
    }

    fun selectDraft(draft: DraftLedgerEntryDto) {
        setCurrentDraft(draft)
        previewState = null
        actionMessage = null
    }

    fun refreshDrafts(preferredDraftId: Long? = selectedDraft?.id) {
        val repository = draftRepository ?: run {
            draftsState = AsyncState.Error(apiConfigError ?: "接口配置未就绪")
            return
        }
        scope.launch {
            draftsState = AsyncState.Loading
            when (val result = repository.listDrafts()) {
                is NetworkResult.Success -> {
                    val drafts = result.data
                    draftsState = AsyncState.Success(drafts)
                    setCurrentDraft(if (preferredDraftId != null) {
                        drafts.firstOrNull { it.id == preferredDraftId }
                            ?: selectedDraft?.takeIf { it.id == preferredDraftId }
                    } else {
                        drafts.firstOrNull()
                    })
                    previewState = null
                }
                is NetworkResult.Failure -> {
                    draftsState = AsyncState.Error(result.message)
                }
            }
        }
    }

    fun loadDraftDetail(draftId: Long) {
        val repository = draftRepository ?: run {
            actionMessage = apiConfigError ?: "接口配置未就绪"
            return
        }
        scope.launch {
            actionMessage = "正在加载草稿详情"
            when (val result = repository.getDraft(draftId)) {
                is NetworkResult.Success -> {
                    if (selectedDraft == null || selectedDraft?.id == draftId || selectedDraftId == draftId) {
                        setCurrentDraft(result.data)
                        previewState = null
                        actionMessage = null
                    }
                }
                is NetworkResult.Failure -> {
                    if (selectedDraft == null || selectedDraft?.id == draftId || selectedDraftId == draftId) {
                        actionMessage = "草稿详情加载失败：${result.message}"
                    }
                }
            }
        }
    }

    fun previewSelectedDraft() {
        val repository = draftRepository ?: run {
            previewState = AsyncState.Error(apiConfigError ?: "接口配置未就绪")
            return
        }
        val draft = selectedDraft ?: return
        if (isEditDirty) {
            previewState = AsyncState.Error("表单已修改，请先保存草稿，再基于最新内容生成预览。")
            return
        }
        scope.launch {
            val requestedDraftId = draft.id
            isPreviewingDraft = true
            previewState = AsyncState.Loading
            when (val result = repository.previewDraft(requestedDraftId)) {
                is NetworkResult.Success -> {
                    if (selectedDraft?.id == requestedDraftId) {
                        previewState = AsyncState.Success(result.data)
                    }
                }
                is NetworkResult.Failure -> {
                    if (selectedDraft?.id == requestedDraftId) {
                        previewState = AsyncState.Error(result.message)
                    }
                }
            }
            if (selectedDraft?.id == requestedDraftId) {
                isPreviewingDraft = false
            }
        }
    }

    fun saveSelectedDraft(previewAfterSave: Boolean) {
        val draft = selectedDraft ?: return
        if (draft.status?.equals("DRAFT", ignoreCase = true) != true) {
            editError = "只有 DRAFT 状态草稿可以编辑。"
            return
        }
        val requestResult = DraftEditState.buildUpdateRequest(draft, editForm)
        val request = requestResult.request
        if (request == null) {
            editError = requestResult.error
            return
        }
        val repository = draftRepository ?: run {
            editError = apiConfigError ?: "接口配置未就绪"
            return
        }
        scope.launch {
            val requestedDraftId = draft.id
            isSavingDraft = true
            editError = null
            previewState = null
            actionMessage = if (previewAfterSave) "正在保存草稿并重新生成预览" else "正在保存草稿"
            when (val updateResult = repository.updateDraft(requestedDraftId, request)) {
                is NetworkResult.Success -> {
                    if (selectedDraft?.id != requestedDraftId) {
                        isSavingDraft = false
                        return@launch
                    }
                    setCurrentDraft(updateResult.data)
                    isEditDirty = false
                    actionMessage = "草稿已保存：${updateResult.data.id}。旧预览已清空，请基于最新草稿重新预览。"
                    if (previewAfterSave) {
                        isPreviewingDraft = true
                        previewState = AsyncState.Loading
                        previewState = when (val previewResult = repository.previewDraft(updateResult.data.id)) {
                            is NetworkResult.Success -> {
                                if (selectedDraft?.id == updateResult.data.id) {
                                    actionMessage = "草稿已保存，并已基于最新内容生成预览。"
                                    AsyncState.Success(previewResult.data)
                                } else {
                                    null
                                }
                            }
                            is NetworkResult.Failure -> {
                                if (selectedDraft?.id == updateResult.data.id) {
                                    actionMessage = "草稿已保存，但重新预览失败：${previewResult.message}"
                                    AsyncState.Error(previewResult.message)
                                } else {
                                    null
                                }
                            }
                        }
                        isPreviewingDraft = false
                    }
                }
                is NetworkResult.Failure -> {
                    if (selectedDraft?.id == requestedDraftId) {
                        editError = "保存失败：${updateResult.message}"
                        actionMessage = null
                    }
                }
            }
            if (selectedDraft?.id == requestedDraftId) {
                isSavingDraft = false
            }
        }
    }

    fun confirmSelectedDraft() {
        val draft = selectedDraft ?: return
        val preview = when (val state = previewState) {
            is AsyncState.Success -> state.data
            else -> null
        } ?: return
        val canConfirm = DraftEditState.canConfirm(
            draft = draft,
            preview = preview,
            editDirty = isEditDirty,
            saving = isSavingDraft,
            previewing = isPreviewingDraft,
            confirming = isConfirmingDraft,
        )
        if (!canConfirm) {
            actionMessage = "当前草稿没有可确认预览，已阻止确认。"
            return
        }

        scope.launch {
            isConfirmingDraft = true
            actionMessage = "正在提交确认请求"
            val repository = draftRepository ?: run {
                actionMessage = apiConfigError ?: "接口配置未就绪"
                isConfirmingDraft = false
                return@launch
            }
            when (val result = repository.confirmDraft(draft.id)) {
                is NetworkResult.Success -> {
                    showConfirmDialog = false
                    actionMessage = "草稿已确认：${result.data.id}"
                    refreshDrafts(null)
                    onDraftHandled()
                }
                is NetworkResult.Failure -> {
                    showConfirmDialog = false
                    actionMessage = "确认失败：${result.message}"
                }
            }
            isConfirmingDraft = false
        }
    }

    fun ignoreSelectedDraft() {
        val draft = selectedDraft ?: return
        scope.launch {
            actionMessage = "正在忽略草稿"
            val repository = draftRepository ?: run {
                actionMessage = apiConfigError ?: "接口配置未就绪"
                return@launch
            }
            when (val result = repository.ignoreDraft(draft.id, "Android 手动忽略")) {
                is NetworkResult.Success -> {
                    showIgnoreDialog = false
                    actionMessage = "草稿已忽略：${result.data.id}"
                    refreshDrafts(null)
                    onDraftHandled()
                }
                is NetworkResult.Failure -> {
                    showIgnoreDialog = false
                    actionMessage = "忽略失败：${result.message}"
                }
            }
        }
    }

    LaunchedEffect(draftRepository, apiConfigError, selectedDraftId) {
        refreshDrafts(selectedDraftId)
        if (selectedDraftId != null) {
            loadDraftDetail(selectedDraftId)
        }
    }
    LaunchedEffect(wealthRepository) {
        if (wealthRepository != null) {
            when (val result = wealthRepository.getAccounts(1, 100)) {
                is NetworkResult.Success -> selectableAccounts = result.data.items.filter { it.leaf }
                is NetworkResult.Failure -> actionMessage = "账户选择器加载失败：${result.message}"
            }
        }
    }

    val preview = when (val state = previewState) {
        is AsyncState.Success -> state.data
        else -> null
    }
    val canConfirm = selectedDraft?.status?.equals("DRAFT", ignoreCase = true) == true &&
        DraftEditState.canConfirm(
            draft = selectedDraft,
            preview = preview,
            editDirty = isEditDirty,
            saving = isSavingDraft,
            previewing = isPreviewingDraft,
            confirming = isConfirmingDraft,
        )

    PageScaffold {
        SafetyBanner("草稿箱只承接查看、预览和用户手动确认。AI/文本解析只生成草稿，不会直接入账；只有二次确认后才会调用确认接口。")
        SectionCard(
            title = "图片识别记账",
            description = "手动选择支付截图，在本机识别并复核文字后生成 DRAFT；图片不会上传。",
        ) {
            Button(onClick = onOpenImageOcr) { Text("选择图片开始识别") }
        }

        actionMessage?.let { message ->
            SectionCard(title = "操作状态", description = message)
        }

        when (val state = draftsState) {
            AsyncState.Loading -> LoadingCard("正在加载草稿箱")
            is AsyncState.Error -> ErrorCard(
                title = "草稿箱加载失败",
                message = state.message,
                onRetry = { refreshDrafts() },
            )
            is AsyncState.Success -> DraftListSection(
                drafts = state.data,
                selectedDraftId = selectedDraft?.id,
                onRefresh = { refreshDrafts() },
                onSelectDraft = ::selectDraft,
            )
        }

        DraftDetailSection(
            selectedDraft = selectedDraft,
            previewState = previewState,
            canConfirm = canConfirm,
            editForm = editForm,
            selectableAccounts = selectableAccounts,
            editError = editError,
            isEditDirty = isEditDirty,
            isSavingDraft = isSavingDraft,
            isPreviewingDraft = isPreviewingDraft,
            onEditFormChange = {
                editForm = it
                editError = null
                isEditDirty = true
                previewState = null
                actionMessage = "表单已修改：请先保存草稿，再基于最新内容重新预览。"
            },
            onSave = { saveSelectedDraft(previewAfterSave = false) },
            onSaveAndPreview = { saveSelectedDraft(previewAfterSave = true) },
            onPreview = ::previewSelectedDraft,
            onIgnore = { showIgnoreDialog = true },
            onConfirm = { showConfirmDialog = true },
        )
    }

    if (showConfirmDialog) {
        ConfirmDraftDialog(
            canConfirm = canConfirm,
            onConfirm = ::confirmSelectedDraft,
            onDismiss = { showConfirmDialog = false },
        )
    }

    if (showIgnoreDialog) {
        IgnoreDraftDialog(
            onConfirm = ::ignoreSelectedDraft,
            onDismiss = { showIgnoreDialog = false },
        )
    }
}

@Composable
private fun DraftListSection(
    drafts: List<DraftLedgerEntryDto>,
    selectedDraftId: Long?,
    onRefresh: () -> Unit,
    onSelectDraft: (DraftLedgerEntryDto) -> Unit,
) {
    SectionCard(
        title = "待确认草稿",
        description = "来自 GET /api/v2/drafts?status=DRAFT。列表只展示草稿，不会自动确认或入账。",
    ) {
        OutlinedButton(onClick = onRefresh) {
            Text("刷新草稿")
        }
        if (drafts.isEmpty()) {
            StatusPill("暂无 DRAFT 草稿")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                drafts.forEach { draft ->
                    val rawInput = draft.rawInput ?: "无原文"
                    val label = if (draft.id == selectedDraftId) {
                        "已选 #${draft.id}：$rawInput"
                    } else {
                        "#${draft.id}：$rawInput"
                    }
                    OutlinedButton(
                        onClick = { onSelectDraft(draft) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftDetailSection(
    selectedDraft: DraftLedgerEntryDto?,
    previewState: AsyncState<DraftPreviewDto>?,
    canConfirm: Boolean,
    editForm: DraftEditForm,
    selectableAccounts: List<MobileAccountDto>,
    editError: String?,
    isEditDirty: Boolean,
    isSavingDraft: Boolean,
    isPreviewingDraft: Boolean,
    onEditFormChange: (DraftEditForm) -> Unit,
    onSave: () -> Unit,
    onSaveAndPreview: () -> Unit,
    onPreview: () -> Unit,
    onIgnore: () -> Unit,
    onConfirm: () -> Unit,
) {
    SectionCard(
        title = "草稿预览与确认",
        description = "确认按钮必须等待当前草稿的 preview.confirmSupported=true，且需要主人二次确认。",
    ) {
        if (selectedDraft == null) {
            StatusPill("请选择一个草稿")
            return@SectionCard
        }

        KeyValueRow("草稿 ID", selectedDraft.id.toString())
        KeyValueRow("状态", selectedDraft.status ?: "未知")
        KeyValueRow("来源", selectedDraft.sourceType ?: "未知")
        KeyValueRow("原始输入", selectedDraft.rawInput ?: "无")
        KeyValueRow("缺失字段", selectedDraft.missingFieldsJson ?: "无")

        DraftEditSection(
            selectedDraft = selectedDraft,
            editForm = editForm,
            selectableAccounts = selectableAccounts,
            editError = editError,
            isEditDirty = isEditDirty,
            isSavingDraft = isSavingDraft,
            isPreviewingDraft = isPreviewingDraft,
            onEditFormChange = onEditFormChange,
            onSave = onSave,
            onSaveAndPreview = onSaveAndPreview,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                enabled = selectedDraft.status?.equals("DRAFT", ignoreCase = true) == true &&
                    !isEditDirty &&
                    !isSavingDraft &&
                    !isPreviewingDraft,
                onClick = onPreview,
            ) {
                Text(if (isPreviewingDraft) "预览中" else "生成预览")
            }
            OutlinedButton(
                enabled = selectedDraft.status?.equals("DRAFT", ignoreCase = true) == true &&
                    !isSavingDraft &&
                    !isPreviewingDraft,
                onClick = onIgnore,
            ) {
                Text("忽略")
            }
            Button(
                enabled = canConfirm,
                onClick = onConfirm,
            ) {
                Text("确认记账")
            }
        }

        when (previewState) {
            null -> StatusPill("尚未生成预览")
            AsyncState.Loading -> CircularProgressIndicator()
            is AsyncState.Error -> Text("预览失败：${previewState.message}")
            is AsyncState.Success -> PreviewContent(previewState.data)
        }
    }
}

@Composable
private fun DraftEditSection(
    selectedDraft: DraftLedgerEntryDto,
    editForm: DraftEditForm,
    selectableAccounts: List<MobileAccountDto>,
    editError: String?,
    isEditDirty: Boolean,
    isSavingDraft: Boolean,
    isPreviewingDraft: Boolean,
    onEditFormChange: (DraftEditForm) -> Unit,
    onSave: () -> Unit,
    onSaveAndPreview: () -> Unit,
) {
    val editable = selectedDraft.status?.equals("DRAFT", ignoreCase = true) == true
    SectionCard(
        title = "草稿编辑与账户补全",
        description = "保存只调用 PUT /api/v2/drafts/{draftId} 更新 DRAFT 草稿，不会直接写正式账本；保存后必须重新 preview。",
    ) {
        if (!editable) {
            StatusPill("非 DRAFT 草稿不可编辑，只能查看历史状态。")
            return@SectionCard
        }

        Text("accountId 是后端真实账户 ID，必须是正整数；accountNameHint 只是人工提示，不会替代 accountId。")
        if (isEditDirty) {
            Text("当前表单有未保存修改：旧预览已失效，保存并重新预览后才能确认。")
        }
        val requestResult = DraftEditState.buildUpdateRequest(selectedDraft, editForm)
        requestResult.error?.let { Text("表单校验：$it") }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                enabled = !isSavingDraft && !isPreviewingDraft,
                onClick = { onEditFormChange(editForm.copy(txnType = "EXPENSE")) },
            ) {
                Text(if (editForm.txnType == "EXPENSE") "支出 EXPENSE ✓" else "支出 EXPENSE")
            }
            OutlinedButton(
                enabled = !isSavingDraft && !isPreviewingDraft,
                onClick = { onEditFormChange(editForm.copy(txnType = "INCOME")) },
            ) {
                Text(if (editForm.txnType == "INCOME") "收入 INCOME ✓" else "收入 INCOME")
            }
        }

        OutlinedTextField(
            value = editForm.amount,
            onValueChange = { onEditFormChange(editForm.copy(amount = it)) },
            enabled = !isSavingDraft && !isPreviewingDraft,
            label = { Text("金额 amount") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        SectionCard(
            title = "选择叶子账户",
            description = "普通消费只允许 SPENDABLE；RESERVED、INVESTABLE、待分配和父账户不可作为默认消费来源。",
        ) {
            val candidates = selectableAccounts.filter { account ->
                editForm.txnType != "EXPENSE" || account.selectableForExpense
            }
            if (candidates.isEmpty()) {
                StatusPill("暂无符合当前交易类型的可选账户")
            } else {
                candidates.forEach { account ->
                    OutlinedButton(
                        enabled = !isSavingDraft && !isPreviewingDraft,
                        onClick = {
                            onEditFormChange(
                                editForm.copy(
                                    accountId = account.id.toString(),
                                    accountNameHint = account.accountName,
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${account.accountName} · ${account.fundUsage ?: "待分配"} · 可用 ${formatMoney(account.availableAmount)}")
                    }
                }
            }
        }
        OutlinedTextField(
            value = editForm.accountId,
            onValueChange = { onEditFormChange(editForm.copy(accountId = it)) },
            enabled = !isSavingDraft && !isPreviewingDraft,
            label = { Text("真实账户 ID accountId") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = editForm.accountNameHint,
            onValueChange = { onEditFormChange(editForm.copy(accountNameHint = it)) },
            enabled = !isSavingDraft && !isPreviewingDraft,
            label = { Text("账户提示 accountNameHint") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = editForm.note,
            onValueChange = { onEditFormChange(editForm.copy(note = it)) },
            enabled = !isSavingDraft && !isPreviewingDraft,
            label = { Text("备注 note") },
            modifier = Modifier.fillMaxWidth(),
        )

        editError?.let { Text(it) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                enabled = !isSavingDraft && !isPreviewingDraft && requestResult.isValid,
                onClick = onSave,
            ) {
                Text(if (isSavingDraft) "保存中" else "保存草稿")
            }
            OutlinedButton(
                enabled = !isSavingDraft && !isPreviewingDraft && requestResult.isValid,
                onClick = onSaveAndPreview,
            ) {
                Text(if (isPreviewingDraft) "预览中" else "保存并预览")
            }
        }
    }
}

@Composable
private fun PreviewContent(preview: DraftPreviewDto) {
    SectionCard(
        title = "本次影响预览",
        description = preview.message,
    ) {
        KeyValueRow("预览草稿", preview.draftId.toString())
        KeyValueRow("确认支持", if (preview.confirmSupported) "可确认" else "不可确认")
        KeyValueRow("账户", preview.accountName ?: "未匹配")
        KeyValueRow("账户类型", preview.accountType ?: "未知")
        KeyValueRow("交易类型", preview.txnType ?: "未知")
        KeyValueRow("金额", preview.amount?.toString() ?: "未知")
        KeyValueRow("资金用途", preview.fundUsage ?: "未知")
        KeyValueRow("影响方向", preview.impactDirection ?: "未知")
        KeyValueRow("账户变动", preview.accountDelta?.toString() ?: "未知")
        KeyValueRow("会生成流水", if (preview.willCreateLedgerTxn) "是" else "否")
        KeyValueRow("会生成订单", if (preview.willCreateOrder) "是" else "否")
        KeyValueRow("会生成结算", if (preview.willCreateSettlement) "是" else "否")
        KeyValueRow("会影响持仓", if (preview.willAffectHolding) "是" else "否")
        val missingFields = preview.missingFields.orEmpty()
        val warnings = preview.warnings.orEmpty()
        if (missingFields.isNotEmpty()) {
            Text("缺失字段：${missingFields.joinToString()}")
        }
        if (warnings.isNotEmpty()) {
            Text("风险提示：${warnings.joinToString()}")
        }
    }
}

@Composable
private fun LoadingCard(title: String) {
    SectionCard(title = title) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorCard(
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

@Composable
private fun ConfirmDraftDialog(
    canConfirm: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认正式记账？") },
        text = {
            Text(
                if (canConfirm) {
                    "本操作会调用后端 confirm 接口。请确认预览内容无误后再继续。"
                } else {
                    "当前草稿没有可确认预览，移动端已阻止本次确认。"
                }
            )
        },
        confirmButton = {
            Button(enabled = canConfirm, onClick = onConfirm) {
                Text("二次确认")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun IgnoreDraftDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("忽略草稿？") },
        text = { Text("本操作会调用 ignore 接口，并写入固定原因：Android 手动忽略。") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("确认忽略")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
