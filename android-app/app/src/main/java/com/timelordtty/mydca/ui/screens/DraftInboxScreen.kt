package com.timelordtty.mydca.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.dp
import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.dto.DraftLedgerEntryDto
import com.timelordtty.mydca.data.dto.DraftLifecycleEventDto
import com.timelordtty.mydca.data.dto.DraftPreviewDto
import com.timelordtty.mydca.data.dto.MobileAccountDto
import com.timelordtty.mydca.data.dto.MobileHoldingByAccountDto
import com.timelordtty.mydca.data.dto.ProductDto
import com.timelordtty.mydca.data.repository.DraftRepository
import com.timelordtty.mydca.data.repository.WealthRepository
import com.timelordtty.mydca.outbox.DraftCreationGateway
import com.timelordtty.mydca.outbox.DraftOutboxQueue
import com.timelordtty.mydca.outbox.DraftOutboxEntry
import com.timelordtty.mydca.ui.state.AsyncState
import com.timelordtty.mydca.ui.state.DraftAccountSelection
import com.timelordtty.mydca.ui.state.DraftEditForm
import com.timelordtty.mydca.ui.state.DraftEditState
import com.timelordtty.mydca.ui.state.DraftReview
import kotlinx.coroutines.launch

@Composable
fun DraftInboxScreen(
    draftRepository: DraftRepository?,
    wealthRepository: WealthRepository?,
    apiConfigError: String?,
    selectedDraftId: Long?,
    onDraftHandled: () -> Unit,
    onOpenImageOcr: () -> Unit,
    onOpenManualEntry: () -> Unit,
    onEditOutboxEntry: (DraftOutboxEntry) -> Unit = {},
    onOpenDraft: (Long) -> Unit,
    onOpenSettlementAudit: (String) -> Unit = {},
    draftOutbox: DraftOutboxQueue? = null,
    draftCreationGateway: DraftCreationGateway? = null,
    focusOutbox: Boolean = false,
) {
    var draftsState by remember { mutableStateOf<AsyncState<List<DraftLedgerEntryDto>>>(AsyncState.Loading) }
    var listError by remember { mutableStateOf<String?>(null) }
    var isRefreshingDrafts by remember { mutableStateOf(false) }
    var draftsUpdatedAt by remember { mutableStateOf<Long?>(null) }
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
    var showReopenDialog by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var historyState by remember { mutableStateOf<AsyncState<List<DraftLifecycleEventDto>>?>(null) }
    var selectableAccounts by remember { mutableStateOf<List<MobileAccountDto>>(emptyList()) }
    var selectableProducts by remember { mutableStateOf<List<ProductDto>>(emptyList()) }
    var productHoldings by remember { mutableStateOf<List<MobileHoldingByAccountDto>>(emptyList()) }
    var isLoadingHoldings by remember { mutableStateOf(false) }
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
        historyState = null
        previewState = null
        actionMessage = null
    }

    fun loadHistory() {
        val draftId = selectedDraft?.id ?: return
        val repository = draftRepository ?: return
        historyState = AsyncState.Loading
        scope.launch {
            val result = repository.history(draftId)
            if (selectedDraft?.id == draftId) historyState = when (result) {
                is NetworkResult.Success -> AsyncState.Success(result.data)
                is NetworkResult.Failure -> AsyncState.Error(result.message)
            }
        }
    }

    fun reopenSelected() {
        val draftId = selectedDraft?.takeIf { it.status == "IGNORED" }?.id ?: return
        showReopenDialog = false
        val repository = draftRepository ?: return
        scope.launch {
            when (val result = repository.reopen(draftId)) {
                is NetworkResult.Success -> {
                    setCurrentDraft(result.data)
                    previewState = null
                    historyState = null
                    actionMessage = "草稿已恢复，请重新预览并二次确认"
                    val current = (draftsState as? AsyncState.Success)?.data.orEmpty()
                    draftsState = AsyncState.Success(current.map { if (it.id == draftId) result.data else it })
                }
                is NetworkResult.Failure -> actionMessage = "恢复失败：${result.message}"
            }
        }
    }

    fun copySelected() {
        val draftId = selectedDraft?.takeIf { it.status == "CONFIRMED" }?.id ?: return
        showCopyDialog = false
        val repository = draftRepository ?: return
        scope.launch {
            when (val result = repository.copyConfirmed(draftId)) {
                is NetworkResult.Success -> {
                    setCurrentDraft(result.data)
                    previewState = null
                    historyState = null
                    actionMessage = "已复制为新草稿，请预览并二次确认"
                    val current = (draftsState as? AsyncState.Success)?.data.orEmpty()
                    draftsState = AsyncState.Success(listOf(result.data) + current)
                }
                is NetworkResult.Failure -> actionMessage = "复制失败：${result.message}"
            }
        }
    }

    fun refreshDrafts(preferredDraftId: Long? = selectedDraft?.id) {
        val repository = draftRepository ?: run {
            draftsState = AsyncState.Error(apiConfigError ?: "接口配置未就绪")
            return
        }
        scope.launch {
            val hasLoaded = draftsState is AsyncState.Success
            isRefreshingDrafts = hasLoaded
            if (!hasLoaded) draftsState = AsyncState.Loading
            when (val result = repository.listDrafts()) {
                is NetworkResult.Success -> {
                    val drafts = result.data
                    draftsState = AsyncState.Success(drafts)
                    listError = null
                    draftsUpdatedAt = System.currentTimeMillis()
                    setCurrentDraft(if (preferredDraftId != null) {
                        drafts.firstOrNull { it.id == preferredDraftId }
                            ?: selectedDraft?.takeIf { it.id == preferredDraftId }
                    } else {
                        drafts.firstOrNull()
                    })
                    previewState = null
                }
                is NetworkResult.Failure -> {
                    if (hasLoaded) {
                        listError = result.message
                    } else {
                        draftsState = AsyncState.Error(result.message)
                    }
                }
            }
            isRefreshingDrafts = false
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
        if (!DraftReview.isDraft(draft)) {
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
    LaunchedEffect(draftOutbox, draftCreationGateway) {
        val outbox = draftOutbox ?: return@LaunchedEffect
        val gateway = draftCreationGateway ?: return@LaunchedEffect
        if (outbox.retryDueEntries(gateway) > 0) {
            refreshDrafts(null)
        }
    }
    LaunchedEffect(wealthRepository) {
        if (wealthRepository != null) {
            when (val result = wealthRepository.getAccounts(1, 100)) {
                is NetworkResult.Success -> selectableAccounts = result.data.items.filter { it.leaf }
                is NetworkResult.Failure -> actionMessage = "账户选择器加载失败：${result.message}"
            }
            // 只读拉取产品主数据，供主人明确选择真实产品；移动端不会用产品名称自动匹配 productId。
            when (val productResult = wealthRepository.getProducts()) {
                is NetworkResult.Success -> selectableProducts = productResult.data.filter { it.isActive != false }
                is NetworkResult.Failure -> actionMessage = "产品选择器加载失败：${productResult.message}"
            }
        }
    }
    // 卖出 / 赎回草稿：产品选定后只读拉取该产品在各账户的真实持仓来源，供主人明确选择。
    LaunchedEffect(wealthRepository, editForm.txnType, editForm.productId) {
        val repository = wealthRepository
        val isSellRedeem = DraftEditState.isSellRedeemType(editForm.txnType)
        val productId = editForm.productId.trim().toLongOrNull()
        if (repository == null || !isSellRedeem || productId == null) {
            productHoldings = emptyList()
            isLoadingHoldings = false
            return@LaunchedEffect
        }
        isLoadingHoldings = true
        productHoldings = when (val result = repository.getProductHoldingsByAccount(productId)) {
            is NetworkResult.Success -> result.data
            is NetworkResult.Failure -> {
                actionMessage = "持仓来源加载失败：${result.message}"
                emptyList()
            }
        }
        isLoadingHoldings = false
    }

    val preview = when (val state = previewState) {
        is AsyncState.Success -> state.data
        else -> null
    }
    val canConfirm = DraftReview.isDraft(selectedDraft) &&
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
            title = "记账入口",
            description = "手工记一笔只解析文本并生成 DRAFT；图片识别只在本机识别，图片不会上传。两条入口都不会自动 preview、confirm 或正式入账。",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenManualEntry) { Text("手工记一笔") }
                OutlinedButton(onClick = onOpenImageOcr) { Text("选择图片开始识别") }
            }
        }

        DraftOutboxSection(
            outbox = draftOutbox,
            gateway = draftCreationGateway,
            onOpenDraft = onOpenDraft,
            onDraftCreated = { refreshDrafts() },
            onEdit = onEditOutboxEntry,
            highlighted = focusOutbox,
        )

        actionMessage?.let { message ->
            SectionCard(title = "操作状态", description = message)
        }

        when (val state = draftsState) {
            AsyncState.Loading -> LoadingSection("正在加载草稿箱")
            is AsyncState.Error -> ErrorSection(
                title = "草稿箱加载失败",
                message = state.message,
                onRetry = { refreshDrafts() },
            )
            is AsyncState.Success -> DraftListSection(
                drafts = state.data,
                selectedDraftId = selectedDraft?.id,
                isRefreshing = isRefreshingDrafts,
                lastUpdatedAt = draftsUpdatedAt,
                listError = listError,
                onRefresh = { refreshDrafts() },
                onSelectDraft = ::selectDraft,
            )
        }

        DraftDetailSection(
            selectedDraft = selectedDraft,
            onOpenSettlementAudit = onOpenSettlementAudit,
            previewState = previewState,
            canConfirm = canConfirm,
            editForm = editForm,
            selectableAccounts = selectableAccounts,
            selectableProducts = selectableProducts,
            productHoldings = productHoldings,
            isLoadingHoldings = isLoadingHoldings,
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
            historyState = historyState,
            onHistory = ::loadHistory,
            onReopen = { showReopenDialog = true },
            onCopy = { showCopyDialog = true },
        )
    }

    if (showConfirmDialog) {
        ConfirmDraftDialog(
            title = DraftReview.confirmDialogTitle(preview),
            message = DraftReview.confirmDialogMessage(preview, canConfirm),
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
    if (showReopenDialog) {
        AlertDialog(
            onDismissRequest = { showReopenDialog = false },
            title = { Text("恢复草稿") },
            text = { Text("恢复后仍需重新预览并二次确认，是否继续？") },
            confirmButton = { Button(onClick = ::reopenSelected) { Text("确认恢复") } },
            dismissButton = { OutlinedButton(onClick = { showReopenDialog = false }) { Text("取消") } },
        )
    }
    if (showCopyDialog) {
        AlertDialog(
            onDismissRequest = { showCopyDialog = false },
            title = { Text("复制为新草稿") },
            text = { Text("将生成新的草稿和来源标识，仍需预览并二次确认。") },
            confirmButton = { Button(onClick = ::copySelected) { Text("确认复制") } },
            dismissButton = { OutlinedButton(onClick = { showCopyDialog = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun DraftListSection(
    drafts: List<DraftLedgerEntryDto>,
    selectedDraftId: Long?,
    isRefreshing: Boolean,
    lastUpdatedAt: Long?,
    listError: String?,
    onRefresh: () -> Unit,
    onSelectDraft: (DraftLedgerEntryDto) -> Unit,
) {
    SectionCard(
        title = "草稿箱与历史",
        description = "可查看待确认、已忽略和已确认草稿；任何操作都不会自动入账。",
    ) {
        RefreshBar(
            lastUpdatedAt = lastUpdatedAt,
            isRefreshing = isRefreshing,
            refreshLabel = "刷新草稿",
            onRefresh = onRefresh,
        )
        if (listError != null) {
            NoticeBanner(
                title = "本次刷新失败，以下保留上次成功草稿列表",
                message = listError,
                onRetry = onRefresh,
            )
        }
        if (drafts.isEmpty()) {
            StatusPill("暂无草稿")
        } else {
            StatusPill("待人工确认 ${DraftReview.pendingDraftCount(drafts)} 条")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                drafts.forEach { draft ->
                    val selected = draft.id == selectedDraftId
                    val label = (if (selected) "已选 " else "") + DraftReview.listLabel(draft)
                    OutlinedButton(
                        onClick = { onSelectDraft(draft) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label)
                    }
                    val rawInput = draft.rawInput
                    if (!rawInput.isNullOrBlank()) {
                        Text(
                            text = "原文：$rawInput",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftDetailSection(
    selectedDraft: DraftLedgerEntryDto?,
    onOpenSettlementAudit: (String) -> Unit,
    previewState: AsyncState<DraftPreviewDto>?,
    canConfirm: Boolean,
    editForm: DraftEditForm,
    selectableAccounts: List<MobileAccountDto>,
    selectableProducts: List<ProductDto>,
    productHoldings: List<MobileHoldingByAccountDto>,
    isLoadingHoldings: Boolean,
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
    historyState: AsyncState<List<DraftLifecycleEventDto>>?,
    onHistory: () -> Unit,
    onReopen: () -> Unit,
    onCopy: () -> Unit,
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
        KeyValueRow("状态", DraftReview.statusLabel(selectedDraft.status))
        KeyValueRow("来源", selectedDraft.sourceType ?: "未知")
        KeyValueRow("原始输入", selectedDraft.rawInput ?: "无")
        KeyValueRow("创建时间", formatDateTime(selectedDraft.createdAt))
        KeyValueRow("更新时间", formatDateTime(selectedDraft.updatedAt))
        StatusPill(
            if (DraftReview.isDraft(selectedDraft)) {
                "仅 DRAFT，尚未入账"
            } else {
                "历史状态，只读查看"
            }
        )
        ParsedInfoSection(selectedDraft)
        selectedDraft.confirmOrderId?.let { orderId ->
            OutlinedButton(onClick = { onOpenSettlementAudit(orderId) }) {
                Text("查看关联订单与结算审计")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onHistory) { Text("查看历史") }
            if (selectedDraft.status == "IGNORED") {
                OutlinedButton(onClick = onReopen) { Text("恢复草稿") }
            }
            if (selectedDraft.status == "CONFIRMED") {
                OutlinedButton(onClick = onCopy) { Text("复制为新草稿") }
            }
        }
        when (historyState) {
            null -> Unit
            AsyncState.Loading -> Text("正在加载历史")
            is AsyncState.Error -> Text("历史加载失败：${historyState.message}")
            is AsyncState.Success -> {
                if (historyState.data.isEmpty()) Text("暂无历史记录")
                historyState.data.forEach { event ->
                    Text("${formatDateTime(event.createdAt)} · ${draftEventLabel(event.eventType)} · ${event.summary ?: ""}")
                }
            }
        }

        if (DraftReview.isDraft(selectedDraft)) {
            DraftEditSection(
                selectedDraft = selectedDraft,
                editForm = editForm,
                selectableAccounts = selectableAccounts,
                selectableProducts = selectableProducts,
                productHoldings = productHoldings,
                isLoadingHoldings = isLoadingHoldings,
                editError = editError,
                isEditDirty = isEditDirty,
                isSavingDraft = isSavingDraft,
                isPreviewingDraft = isPreviewingDraft,
                onEditFormChange = onEditFormChange,
                onSave = onSave,
                onSaveAndPreview = onSaveAndPreview,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                enabled = DraftReview.isDraft(selectedDraft) &&
                    !isEditDirty &&
                    !isSavingDraft &&
                    !isPreviewingDraft,
                onClick = onPreview,
            ) {
                Text(if (isPreviewingDraft) "预览中" else "生成预览")
            }
            OutlinedButton(
                enabled = DraftReview.isDraft(selectedDraft) &&
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

private fun draftEventLabel(type: String): String = when (type) {
    "create" -> "创建"
    "edit" -> "编辑"
    "preview" -> "预览"
    "ignored" -> "忽略"
    "reopen" -> "恢复"
    "confirm_attempt" -> "尝试确认"
    "confirmed" -> "确认成功"
    "confirm_failed" -> "确认失败"
    "copy" -> "复制"
    else -> type
}

@Composable
private fun ParsedInfoSection(draft: DraftLedgerEntryDto) {
    val info = DraftReview.parsedInfo(draft)
    SectionCard(
        title = "解析信息复核",
        description = "只展示后端已返回的候选字段；补齐字段仍需保存草稿后重新生成预览。",
    ) {
        KeyValueRow("交易类型", DraftReview.txnTypeLabel(info.txnType))
        KeyValueRow("金额", info.amount ?: "待补充")
        KeyValueRow("账户 ID", info.accountId ?: "待补充")
        KeyValueRow("账户提示", info.accountNameHint ?: "无")
        KeyValueRow("备注", info.note ?: "无")
        KeyValueRow("解析置信度", info.confidence ?: "未提供")
        KeyValueRow("缺失字段", info.missingFieldText)
        if (!info.hasParsedField) {
            StatusPill("没有可用候选字段，请在下方表单补齐")
        }
    }
}

@Composable
private fun DraftEditSection(
    selectedDraft: DraftLedgerEntryDto,
    editForm: DraftEditForm,
    selectableAccounts: List<MobileAccountDto>,
    selectableProducts: List<ProductDto>,
    productHoldings: List<MobileHoldingByAccountDto>,
    isLoadingHoldings: Boolean,
    editError: String?,
    isEditDirty: Boolean,
    isSavingDraft: Boolean,
    isPreviewingDraft: Boolean,
    onEditFormChange: (DraftEditForm) -> Unit,
    onSave: () -> Unit,
    onSaveAndPreview: () -> Unit,
) {
    val editable = DraftReview.isDraft(selectedDraft)
    SectionCard(
        title = "草稿编辑与账户补全",
        description = "保存只调用 PUT /api/v2/drafts/{draftId} 更新 DRAFT 草稿，不会直接写正式账本；保存后必须重新 preview。",
    ) {
        if (!editable) {
            StatusPill("非 DRAFT 草稿不可编辑，只能查看历史状态。")
            return@SectionCard
        }

        Text("accountId 是后端真实账户 ID，必须是正整数；accountNameHint 只是人工提示，不会替代 accountId。")
        Text("卖出 SELL / 赎回 REDEMPTION 只填写真实产品、持仓来源、本次份额与到账账户；确认后只创建内部 PENDING 记录，不立即改动持仓或余额。")
        if (isEditDirty) {
            Text("当前表单有未保存修改：旧预览已失效，保存并重新预览后才能确认。")
        }
        val requestResult = DraftEditState.buildUpdateRequest(selectedDraft, editForm)
        requestResult.error?.let { Text("表单校验：$it") }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                enabled = !isSavingDraft && !isPreviewingDraft,
                onClick = { onEditFormChange(DraftEditState.switchTxnType(editForm, DraftAccountSelection.EXPENSE)) },
            ) {
                Text(if (editForm.txnType == DraftAccountSelection.EXPENSE) "支出 EXPENSE ✓" else "支出 EXPENSE")
            }
            OutlinedButton(
                enabled = !isSavingDraft && !isPreviewingDraft,
                onClick = { onEditFormChange(DraftEditState.switchTxnType(editForm, DraftAccountSelection.INCOME)) },
            ) {
                Text(if (editForm.txnType == DraftAccountSelection.INCOME) "收入 INCOME ✓" else "收入 INCOME")
            }
            OutlinedButton(
                enabled = !isSavingDraft && !isPreviewingDraft,
                onClick = { onEditFormChange(DraftEditState.switchTxnType(editForm, DraftAccountSelection.TRANSFER)) },
            ) {
                Text(if (editForm.txnType == DraftAccountSelection.TRANSFER) "转账 TRANSFER ✓" else "转账 TRANSFER")
            }
            OutlinedButton(
                enabled = !isSavingDraft && !isPreviewingDraft,
                onClick = { onEditFormChange(DraftEditState.switchTxnType(editForm, DraftAccountSelection.BUY)) },
            ) {
                Text(if (editForm.txnType == DraftAccountSelection.BUY) "买入 BUY ✓" else "买入 BUY")
            }
            OutlinedButton(
                enabled = !isSavingDraft && !isPreviewingDraft,
                onClick = { onEditFormChange(DraftEditState.switchTxnType(editForm, DraftAccountSelection.SUBSCRIPTION)) },
            ) {
                Text(if (editForm.txnType == DraftAccountSelection.SUBSCRIPTION) "申购 SUBSCRIPTION ✓" else "申购 SUBSCRIPTION")
            }
            OutlinedButton(
                enabled = !isSavingDraft && !isPreviewingDraft,
                onClick = { onEditFormChange(DraftEditState.switchTxnType(editForm, DraftAccountSelection.SELL)) },
            ) {
                Text(if (editForm.txnType == DraftAccountSelection.SELL) "卖出 SELL ✓" else "卖出 SELL")
            }
            OutlinedButton(
                enabled = !isSavingDraft && !isPreviewingDraft,
                onClick = { onEditFormChange(DraftEditState.switchTxnType(editForm, DraftAccountSelection.REDEMPTION)) },
            ) {
                Text(if (editForm.txnType == DraftAccountSelection.REDEMPTION) "赎回 REDEMPTION ✓" else "赎回 REDEMPTION")
            }
        }

        val isTransferForm = editForm.txnType == DraftAccountSelection.TRANSFER
        val isInvestmentForm = DraftEditState.isInvestmentType(editForm.txnType)
        val isSellRedeemForm = DraftEditState.isSellRedeemType(editForm.txnType)
        val editEnabled = !isSavingDraft && !isPreviewingDraft
        val selectedProduct = selectableProducts.firstOrNull { it.id.toString() == editForm.productId.trim() }
        if (!isSellRedeemForm) {
            OutlinedTextField(
                value = editForm.amount,
                onValueChange = { onEditFormChange(editForm.copy(amount = it)) },
                enabled = editEnabled,
                label = { Text("金额 amount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (isInvestmentForm || isSellRedeemForm) {
            ProductPickerSection(
                selectedProductId = editForm.productId,
                products = selectableProducts,
                enabled = editEnabled,
                onPick = { product ->
                    onEditFormChange(
                        editForm.copy(
                            productId = product.id.toString(),
                            productNameHint = product.productName ?: editForm.productNameHint,
                        ),
                    )
                },
            )
        }
        if (isSellRedeemForm) {
            HoldingSourcePickerSection(
                selectedAccountId = editForm.sourceAccountId,
                holdings = productHoldings,
                isLoading = isLoadingHoldings,
                enabled = editEnabled,
                onPick = { holding ->
                    onEditFormChange(
                        editForm.copy(
                            sourceAccountId = holding.accountId?.toString().orEmpty(),
                            sourceAccountNameHint = holding.accountName ?: editForm.sourceAccountNameHint,
                        ),
                    )
                },
            )
            OutlinedTextField(
                value = editForm.shares,
                onValueChange = { onEditFormChange(editForm.copy(shares = it)) },
                enabled = editEnabled,
                label = { Text("本次份额 shares（必填）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            AccountPickerSection(
                title = when {
                    isTransferForm -> "选择转出账户"
                    isInvestmentForm -> "选择付款账户"
                    else -> "选择叶子账户"
                },
                description = when {
                    isTransferForm ->
                        "转账可能是主人主动调整资金分区：SPENDABLE / RESERVED / INVESTABLE 之间都可以转移，只需选择真实叶子账户。"
                    isInvestmentForm ->
                        "投资买入 / 申购只允许 INVESTABLE 真实叶子账户；国债逆回购 BOND_REPO 才允许使用 RESERVED 专款。确认后会立即扣减该账户并增加待结算应收。"
                    else ->
                        "普通消费只允许 SPENDABLE；RESERVED、INVESTABLE、待分配和父账户不可作为默认消费来源。"
                },
                txnType = editForm.txnType,
                accounts = selectableAccounts,
                productAssetType = selectedProduct?.assetType,
                enabled = editEnabled,
                onPick = { account ->
                    onEditFormChange(
                        editForm.copy(accountId = account.id.toString(), accountNameHint = account.accountName),
                    )
                },
            )
        }
        if (isTransferForm) {
            AccountPickerSection(
                title = "选择转入账户",
                description = "转入账户同样必须是真实叶子账户；转出与转入不能相同，币种必须一致，最终以后端 preview 为准。",
                txnType = editForm.txnType,
                accounts = selectableAccounts,
                enabled = editEnabled,
                onPick = { account ->
                    onEditFormChange(editForm.copy(targetAccountId = account.id.toString()))
                },
            )
            val sourceAccount = selectableAccounts.firstOrNull { it.id.toString() == editForm.accountId.trim() }
            val targetAccount = selectableAccounts.firstOrNull { it.id.toString() == editForm.targetAccountId.trim() }
            DraftAccountSelection.transferValidationMessage(sourceAccount, targetAccount)?.let { message ->
                Text(message)
            }
        }
        if (isSellRedeemForm) {
            AccountPickerSection(
                title = "选择到账账户",
                description = "到账账户必须是当前可见的 REAL 叶子账户，不能是 VIRTUAL / POSITION / 父账户，币种需与产品一致；最终以后端 preview 为准。",
                txnType = editForm.txnType,
                accounts = selectableAccounts,
                productCurrency = selectedProduct?.currency,
                enabled = editEnabled,
                onPick = { account ->
                    onEditFormChange(editForm.copy(targetAccountId = account.id.toString()))
                },
            )
        }
        if (!isSellRedeemForm) {
            OutlinedTextField(
                value = editForm.accountId,
                onValueChange = { onEditFormChange(editForm.copy(accountId = it)) },
                enabled = !isSavingDraft && !isPreviewingDraft,
                label = {
                    Text(
                        when {
                            isTransferForm -> "转出账户 ID accountId"
                            isInvestmentForm -> "付款账户 ID accountId"
                            else -> "真实账户 ID accountId"
                        },
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (isTransferForm) {
            OutlinedTextField(
                value = editForm.targetAccountId,
                onValueChange = { onEditFormChange(editForm.copy(targetAccountId = it)) },
                enabled = !isSavingDraft && !isPreviewingDraft,
                label = { Text("转入账户 ID targetAccountId") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (isSellRedeemForm) {
            OutlinedTextField(
                value = editForm.sourceAccountId,
                onValueChange = { onEditFormChange(editForm.copy(sourceAccountId = it)) },
                enabled = !isSavingDraft && !isPreviewingDraft,
                label = { Text("持仓来源 ID sourceAccountId（必填）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = editForm.targetAccountId,
                onValueChange = { onEditFormChange(editForm.copy(targetAccountId = it)) },
                enabled = !isSavingDraft && !isPreviewingDraft,
                label = { Text("到账账户 ID targetAccountId（必填）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = editForm.sourceAccountNameHint,
                onValueChange = { onEditFormChange(editForm.copy(sourceAccountNameHint = it)) },
                enabled = !isSavingDraft && !isPreviewingDraft,
                label = { Text("持仓来源提示 sourceAccountNameHint（仅提示）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = editForm.accountNameHint,
                onValueChange = { onEditFormChange(editForm.copy(accountNameHint = it)) },
                enabled = !isSavingDraft && !isPreviewingDraft,
                label = { Text("账户提示 accountNameHint") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (isInvestmentForm || isSellRedeemForm) {
            OutlinedTextField(
                value = editForm.productId,
                onValueChange = { onEditFormChange(editForm.copy(productId = it)) },
                enabled = !isSavingDraft && !isPreviewingDraft,
                label = { Text("真实产品 ID productId（必填）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = editForm.productNameHint,
                onValueChange = { onEditFormChange(editForm.copy(productNameHint = it)) },
                enabled = !isSavingDraft && !isPreviewingDraft,
                label = { Text("产品名称提示 productNameHint（仅提示）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = editForm.expectedNavDate,
                onValueChange = { onEditFormChange(editForm.copy(expectedNavDate = it)) },
                enabled = !isSavingDraft && !isPreviewingDraft,
                label = { Text("预计净值日 expectedNavDate（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = editForm.expectedConfirmDate,
                onValueChange = { onEditFormChange(editForm.copy(expectedConfirmDate = it)) },
                enabled = !isSavingDraft && !isPreviewingDraft,
                label = { Text("预计确认日 expectedConfirmDate（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
        if (preview.txnType?.trim()?.uppercase() in setOf("BUY", "SUBSCRIPTION")) {
            KeyValueRow("订单类型", preview.orderType ?: preview.txnType ?: "未知")
            KeyValueRow("产品", preview.productName ?: "未选择")
            KeyValueRow("产品代码", preview.productCode ?: "无")
            KeyValueRow("产品类型", preview.productAssetType ?: "未知")
            KeyValueRow("产品币种", preview.productCurrency ?: "未知")
            KeyValueRow("可用余额", preview.availableBefore?.toString() ?: "未知")
            KeyValueRow("待结算应收变动", preview.receivableDelta?.toString() ?: "未知")
            KeyValueRow("预计净值日", preview.expectedNavDate ?: "未设置")
            KeyValueRow("预计确认日", preview.expectedConfirmDate ?: "未设置")
        }
        if (preview.txnType?.trim()?.uppercase() in setOf("SELL", "REDEMPTION")) {
            KeyValueRow("订单类型", preview.orderType ?: preview.txnType ?: "未知")
            KeyValueRow("产品", preview.productName ?: "未选择")
            KeyValueRow("产品代码", preview.productCode ?: "无")
            KeyValueRow("产品类型", preview.productAssetType ?: "未知")
            KeyValueRow("产品币种", preview.productCurrency ?: "未知")
            KeyValueRow("持仓来源", preview.accountName ?: "未匹配")
            KeyValueRow("当前可用份额", preview.availableShares?.toString() ?: "未知")
            KeyValueRow("本次份额", preview.shares?.toString() ?: "未知")
            KeyValueRow("预计剩余份额", preview.remainingShares?.toString() ?: "未知")
            KeyValueRow("到账账户", preview.targetAccountName ?: "未匹配")
            KeyValueRow("预计净值日", preview.expectedNavDate ?: "未设置")
            KeyValueRow("预计确认日", preview.expectedConfirmDate ?: "未设置")
        }
        if (preview.txnType?.equals("TRANSFER", ignoreCase = true) == true) {
            KeyValueRow("转入账户", preview.targetAccountName ?: "未匹配")
            KeyValueRow("转入账户类型", preview.targetAccountType ?: "未知")
            KeyValueRow("转入资金用途", preview.targetFundUsage ?: "未知")
            KeyValueRow("转入账户变动", preview.targetAccountDelta?.toString() ?: "未知")
        }
        KeyValueRow("会生成流水", if (preview.willCreateLedgerTxn) "是" else "否")
        KeyValueRow("会生成订单", if (preview.willCreateOrder) "是" else "否")
        KeyValueRow("会生成结算", if (preview.willCreateSettlement) "是" else "否")
        KeyValueRow("会影响持仓", if (preview.willAffectHolding) "是" else "否")
        val investmentLines = DraftReview.investmentImpactLines(preview)
        if (investmentLines.isNotEmpty()) {
            Text("投资付款账本影响：")
            investmentLines.forEach { line -> Text(line) }
        }
        val sellRedeemLines = DraftReview.sellRedeemImpactLines(preview)
        if (sellRedeemLines.isNotEmpty()) {
            Text("卖出 / 赎回份额占用影响：")
            sellRedeemLines.forEach { line -> Text(line) }
        }
        val transferLines = DraftReview.transferImpactLines(preview)
        if (transferLines.isNotEmpty()) {
            Text("转账双账户影响：")
            transferLines.forEach { line -> Text(line) }
        }
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
private fun ConfirmDraftDialog(
    title: String,
    message: String,
    canConfirm: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
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

@Composable
private fun AccountPickerSection(
    title: String,
    description: String,
    txnType: String?,
    accounts: List<MobileAccountDto>,
    enabled: Boolean,
    onPick: (MobileAccountDto) -> Unit,
    productAssetType: String? = null,
    productCurrency: String? = null,
) {
    SectionCard(title = title, description = description) {
        val candidates = accounts.filter { account ->
            DraftAccountSelection.isSelectable(txnType, account, productAssetType, productCurrency)
        }
        val blocked = accounts.filterNot { account ->
            DraftAccountSelection.isSelectable(txnType, account, productAssetType, productCurrency)
        }
        if (candidates.isEmpty()) {
            StatusPill("暂无符合当前交易类型的可选账户")
        } else {
            candidates.forEach { account ->
                OutlinedButton(
                    enabled = enabled,
                    onClick = { onPick(account) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "${account.accountName} · ${account.fundUsage ?: "待分配"} · 可用 ${formatMoney(account.availableAmount)}",
                    )
                }
            }
        }
        if (blocked.isNotEmpty()) {
            Text("以下账户受保护，当前交易类型不可选择：")
            blocked.forEach { account ->
                Text(
                    text = "${account.accountName}：${DraftAccountSelection.rejectionReason(txnType, account, productAssetType, productCurrency)}",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ProductPickerSection(
    selectedProductId: String,
    products: List<ProductDto>,
    enabled: Boolean,
    onPick: (ProductDto) -> Unit,
) {
    SectionCard(
        title = "选择真实产品",
        description = "产品来自 GET /api/v2/products，只展示启用中的产品；必须由主人明确选择，绝不用产品名称自动匹配 productId。",
    ) {
        if (products.isEmpty()) {
            StatusPill("暂无可用产品，请先在 PC 端维护产品主数据")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                products.forEach { product ->
                    val selected = product.id.toString() == selectedProductId.trim()
                    OutlinedButton(
                        enabled = enabled,
                        onClick = { onPick(product) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            (if (selected) "已选 " else "") +
                                "${product.productName ?: "未命名产品"} · ${product.productCode ?: "无代码"} · " +
                                "${product.assetType ?: "未知类型"} · ${product.currency ?: "未知币种"}",
                        )
                    }
                }
            }
        }
    }
}
/**
 * 卖出 / 赎回草稿的持仓来源选择：只展示 GET /api/v2/holdings/product/{productId}/by-account
 * 返回的真实持仓账户，必须由主人明确选择，移动端不会按账户名或历史订单自动匹配。
 */
@Composable
private fun HoldingSourcePickerSection(
    selectedAccountId: String,
    holdings: List<MobileHoldingByAccountDto>,
    isLoading: Boolean,
    enabled: Boolean,
    onPick: (MobileHoldingByAccountDto) -> Unit,
) {
    SectionCard(
        title = "选择持仓来源",
        description = "持仓来源来自 GET /api/v2/holdings/product/{productId}/by-account，只展示该产品当前真实持仓账户；可用份额还需扣除仍在 PENDING 的卖出 / 赎回占用。",
    ) {
        when {
            isLoading -> CircularProgressIndicator()
            holdings.isEmpty() -> StatusPill("该产品暂无可见持仓来源，请先在 PC 端确认持仓或更改产品")
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                holdings.forEach { holding ->
                    val selected = holding.accountId?.toString() == selectedAccountId.trim()
                    OutlinedButton(
                        enabled = enabled,
                        onClick = { onPick(holding) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            (if (selected) "已选 " else "") +
                                "${holding.accountName ?: "未知账户"} · 份额 ${formatShares(holding.shares?.toString())}" +
                                (holding.parentAccountName?.takeIf { it.isNotBlank() }?.let { " · 父账户 $it" } ?: ""),
                        )
                    }
                }
            }
        }
    }
}
