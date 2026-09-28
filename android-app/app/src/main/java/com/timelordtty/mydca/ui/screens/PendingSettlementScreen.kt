package com.timelordtty.mydca.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.timelordtty.mydca.data.dto.PendingSettlementOrderDto
import com.timelordtty.mydca.data.dto.SettlementPreviewDto
import com.timelordtty.mydca.data.repository.SettlementRepository
import com.timelordtty.mydca.data.repository.WealthRepository
import com.timelordtty.mydca.ui.state.PendingSettlementUiState
import com.timelordtty.mydca.ui.state.SettlementEditForm
import com.timelordtty.mydca.ui.state.SettlementEditState
import com.timelordtty.mydca.ui.state.SettlementPreviewUiState
import com.timelordtty.mydca.ui.state.SettlementStateHolder
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * 人工结算安全链（v0.13.0）：待结算列表 -> 手工填写真实结算结果 -> 只读预览 -> 主人二次确认 -> 内部落账。
 *
 * 页面边界：
 * - 进入页面只加载待结算列表，不会自动 preview，更不会自动 confirm；
 * - 「生成结算预览」是唯一的只读预览入口，预览不会写 settlement_confirm / ledger_txn；
 * - 「确认结算」必须先有 confirmSupported=true 的预览与 freshPreviewToken，并且要再经过一次中文二次确认；
 * - 任何字段修改都会立刻失效旧预览，防止拿旧预览直接结算。
 */
@Composable
fun PendingSettlementScreen(
    settlementRepository: SettlementRepository?,
    wealthRepository: WealthRepository?,
    apiConfigError: String?,
    focusOrderId: String? = null,
    onSettlementConfirmed: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    var listState by remember { mutableStateOf(PendingSettlementUiState()) }
    var selectedOrder by remember { mutableStateOf<PendingSettlementOrderDto?>(null) }
    var form by remember { mutableStateOf(SettlementEditForm()) }
    var previewState by remember { mutableStateOf(SettlementPreviewUiState()) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var isConfirming by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val holder = remember(settlementRepository, wealthRepository) {
        settlementRepository?.let { SettlementStateHolder(it, wealthRepository) }
    }
    val today = remember { LocalDate.now().toString() }

    fun selectOrder(order: PendingSettlementOrderDto, clearMessage: Boolean = true) {
        selectedOrder = order
        form = SettlementEditState.formFromOrder(order, today)
        previewState = SettlementPreviewUiState()
        showConfirmDialog = false
        isConfirming = false
        if (clearMessage) {
            actionMessage = null
        }
    }

    fun onFormChange(updated: SettlementEditForm) {
        if (updated == form) {
            return
        }
        val hadPreview = previewState.hasPreview
        form = updated
        // 任何字段修改都会使旧预览失效：直接清空，避免主人拿旧预览直接确认结算。
        previewState = SettlementPreviewUiState()
        if (hadPreview) {
            actionMessage = SettlementEditState.staleNotice()
        }
    }

    fun refresh(showLoading: Boolean, preferredOrderId: String? = selectedOrder?.orderId) {
        val stateHolder = holder ?: run {
            listState = listState.copy(
                isLoading = false,
                isRefreshing = false,
                errorMessage = apiConfigError ?: "接口配置未就绪",
            )
            return
        }
        val loaded = listState.orders.isNotEmpty()
        val previous = listState.copy(
            isLoading = showLoading && !loaded,
            isRefreshing = !(showLoading && !loaded),
            errorMessage = null,
        )
        listState = previous
        scope.launch {
            val next = stateHolder.loadPending(previous)
            listState = next
            if (next.errorMessage == null) {
                // 列表刷新后必须重新建立预览：旧预览对应的是刷新前的订单 / 账户快照。
                previewState = SettlementPreviewUiState()
                val wanted = preferredOrderId ?: focusOrderId
                val matched = next.orders.firstOrNull { it.orderId == wanted }
                    ?: next.orders.firstOrNull()
                if (matched == null) {
                    selectedOrder = null
                    form = SettlementEditForm()
                } else {
                    selectOrder(matched, clearMessage = false)
                }
            }
        }
    }

    fun generatePreview() {
        val order = selectedOrder ?: run {
            actionMessage = "请先选择一笔待结算订单。"
            return
        }
        val requestResult = SettlementEditState.buildRequest(form)
        val request = requestResult.request
        if (request == null) {
            previewState = SettlementPreviewUiState(errorMessage = requestResult.error)
            actionMessage = null
            return
        }
        val repository = settlementRepository ?: run {
            previewState = SettlementPreviewUiState(errorMessage = apiConfigError ?: "接口配置未就绪")
            return
        }
        scope.launch {
            val requestedOrderId = order.orderId
            val requestedFingerprint = SettlementEditState.inputFingerprint(form)
            previewState = SettlementPreviewUiState(isGenerating = true)
            actionMessage = "正在生成只读结算预览"
            when (val result = repository.previewSettlement(request)) {
                is NetworkResult.Success -> {
                    if (selectedOrder?.orderId == requestedOrderId) {
                        previewState = SettlementPreviewUiState(
                            preview = result.data,
                            inputFingerprint = requestedFingerprint,
                            lastGeneratedAt = System.currentTimeMillis(),
                        )
                        actionMessage = if (result.data.confirmSupported) {
                            "预览已生成，请确认结算影响后再二次确认。"
                        } else {
                            "预览未通过校验：${SettlementEditState.blockingText(result.data)}"
                        }
                    }
                }
                is NetworkResult.Failure -> {
                    if (selectedOrder?.orderId == requestedOrderId) {
                        // 失败绝不显示假成功：保持没有可确认的预览，确认按钮保持禁用。
                        previewState = SettlementPreviewUiState(errorMessage = result.message)
                        actionMessage = "结算预览失败：${result.message}"
                    }
                }
            }
        }
    }

    fun confirmSettlement() {
        val order = selectedOrder ?: return
        val preview = previewState.preview ?: return
        val canConfirm = SettlementEditState.canConfirm(
            form = form,
            preview = preview,
            previewFingerprint = previewState.inputFingerprint,
            confirming = isConfirming,
        )
        if (!canConfirm) {
            showConfirmDialog = false
            actionMessage = "当前结算没有可确认的预览，移动端已阻止本次确认。"
            return
        }
        val requestResult = SettlementEditState.buildRequest(form)
        val request = requestResult.request
        if (request == null) {
            showConfirmDialog = false
            actionMessage = requestResult.error
            return
        }
        val repository = settlementRepository ?: run {
            showConfirmDialog = false
            actionMessage = apiConfigError ?: "接口配置未就绪"
            return
        }
        scope.launch {
            val requestedOrderId = order.orderId
            isConfirming = true
            actionMessage = "正在提交结算确认"
            val confirmedRequest = request.copy(freshPreviewToken = preview.freshPreviewToken)
            when (val result = repository.confirmSettlement(confirmedRequest)) {
                is NetworkResult.Success -> {
                    showConfirmDialog = false
                    isConfirming = false
                    actionMessage = "订单 ${result.data.orderId.ifBlank { requestedOrderId }} 已完成人工结算，" +
                        "结算确认与内部账本已生成。"
                    previewState = SettlementPreviewUiState()
                    // 结算成功后刷新待结算列表，并通知持仓 / 资产相关页面重新加载。
                    refresh(showLoading = false, preferredOrderId = null)
                    onSettlementConfirmed()
                }
                is NetworkResult.Failure -> {
                    showConfirmDialog = false
                    isConfirming = false
                    actionMessage = "结算失败：${result.message}。本次未生成结算确认与内部账本。"
                    previewState = SettlementPreviewUiState()
                }
            }
        }
    }

    LaunchedEffect(settlementRepository, wealthRepository, apiConfigError) {
        refresh(showLoading = true)
    }

    val canConfirm = SettlementEditState.canConfirm(
        form = form,
        preview = previewState.preview,
        previewFingerprint = previewState.inputFingerprint,
        confirming = isConfirming,
    )

    PageScaffold {
        SafetyBanner(
            "待结算只做人工结算：先生成只读预览，看懂现金 / 持仓 / 手续费影响并二次确认后，" +
                "才会真正生成财富中枢内部账本。不会连接券商、基金公司或交易所，也不会自动结算。",
        )
        PendingSettlementListSection(
            state = listState,
            selectedOrderId = selectedOrder?.orderId,
            onRefresh = { refresh(showLoading = false) },
            onSelectOrder = ::selectOrder,
            onClose = onClose,
        )
        SettlementEditSection(
            selectedOrder = selectedOrder,
            form = form,
            previewState = previewState,
            canConfirm = canConfirm,
            isConfirming = isConfirming,
            actionMessage = actionMessage,
            onFormChange = ::onFormChange,
            onGeneratePreview = ::generatePreview,
            onRequestConfirm = { showConfirmDialog = true },
        )
    }

    if (showConfirmDialog) {
        ConfirmSettlementDialog(
            preview = previewState.preview,
            canConfirm = canConfirm,
            isConfirming = isConfirming,
            onConfirm = ::confirmSettlement,
            onDismiss = { if (!isConfirming) showConfirmDialog = false },
        )
    }
}

@Composable
private fun PendingSettlementListSection(
    state: PendingSettlementUiState,
    selectedOrderId: String?,
    onRefresh: () -> Unit,
    onSelectOrder: (PendingSettlementOrderDto) -> Unit,
    onClose: () -> Unit,
) {
    SectionCard(
        title = "待结算订单",
        description = "来自 GET /api/v2/settlements/pending。列表只展示 PENDING 订单，不会自动结算。",
    ) {
        RefreshBar(
            lastUpdatedAt = state.lastUpdatedAt,
            isRefreshing = state.isRefreshing,
            refreshLabel = "刷新待结算",
            onRefresh = onRefresh,
        )
        state.errorMessage?.let { message ->
            NoticeBanner(
                title = "本次刷新失败，以下保留上次成功待结算清单",
                message = message,
                onRetry = onRefresh,
            )
        }
        TextButton(onClick = onClose) {
            Text("返回今日待办")
        }
        when {
            state.isLoading -> LoadingSection("正在加载待结算订单")
            state.orders.isEmpty() -> StatusPill("暂无待结算订单")
            else -> {
                StatusPill("待人工结算 ${SettlementEditState.pendingCount(state.orders)} 笔")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.orders.forEach { order ->
                        val name = order.productId?.let { state.productNames[it] }
                        val prefix = if (order.orderId == selectedOrderId) "已选 " else ""
                        OutlinedButton(
                            onClick = { onSelectOrder(order) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(prefix + SettlementEditState.orderSummary(order, name))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettlementEditSection(
    selectedOrder: PendingSettlementOrderDto?,
    form: SettlementEditForm,
    previewState: SettlementPreviewUiState,
    canConfirm: Boolean,
    isConfirming: Boolean,
    actionMessage: String?,
    onFormChange: (SettlementEditForm) -> Unit,
    onGeneratePreview: () -> Unit,
    onRequestConfirm: () -> Unit,
) {
    SectionCard(
        title = "结算输入与影响预览",
        description = "「生成结算预览」只读计算现金 / 持仓 / 手续费影响；「确认结算」需要二次确认，" +
            "并且必须携带 fresh preview 令牌。",
    ) {
        if (selectedOrder == null) {
            StatusPill("请选择一笔待结算订单")
            return@SectionCard
        }

        KeyValueRow("订单号", selectedOrder.orderId)
        KeyValueRow("订单类型", SettlementEditState.orderTypeLabel(selectedOrder.orderType))
        KeyValueRow("订单状态", selectedOrder.status ?: "未知")
        KeyValueRow("预计确认日期", selectedOrder.expectedConfirmDate ?: "未提供")
        KeyValueRow("预计净值日期", selectedOrder.expectedNavDate ?: "未提供")
        KeyValueRow("本类型字段", SettlementEditState.fieldLabels(form.orderType).joinToString("、"))

        if (SettlementEditState.normalizeOrderType(form.orderType) == null) {
            StatusPill("该订单类型暂不支持人工结算，移动端已阻止 preview 与 confirm")
            return@SectionCard
        }

        OutlinedTextField(
            value = form.confirmDate,
            onValueChange = { onFormChange(form.copy(confirmDate = it)) },
            enabled = !isConfirming,
            label = { Text("确认日期 confirmDate（YYYY-MM-DD，必填）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.navDate,
            onValueChange = { onFormChange(form.copy(navDate = it)) },
            enabled = !isConfirming,
            label = { Text("净值日期 navDate（YYYY-MM-DD，必填）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.confirmNav,
            onValueChange = { onFormChange(form.copy(confirmNav = it)) },
            enabled = !isConfirming,
            label = { Text("实际净值 confirmNav（必填，必须大于 0）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.confirmShares,
            onValueChange = { onFormChange(form.copy(confirmShares = it)) },
            enabled = !isConfirming,
            label = {
                Text(
                    if (SettlementEditState.isBuyLike(form.orderType)) {
                        "实际份额 confirmShares（可留空，按金额 / 净值计算）"
                    } else {
                        "实际份额 confirmShares（可留空）"
                    },
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        if (SettlementEditState.isSellLike(form.orderType)) {
            OutlinedTextField(
                value = form.confirmAmount,
                onValueChange = { onFormChange(form.copy(confirmAmount = it)) },
                enabled = !isConfirming,
                label = { Text("实际到账金额 confirmAmount（卖出 / 赎回必填，必须大于 0）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = form.confirmFee,
            onValueChange = { onFormChange(form.copy(confirmFee = it)) },
            enabled = !isConfirming,
            label = { Text("手续费 confirmFee（可留空按费率估算，输入 0 则使用 0）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.note,
            onValueChange = { onFormChange(form.copy(note = it)) },
            enabled = !isConfirming,
            label = { Text("备注 note（可选）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                enabled = !previewState.isGenerating && !isConfirming,
                onClick = onGeneratePreview,
            ) {
                Text(if (previewState.isGenerating) "生成中" else "生成结算预览")
            }
            Button(
                enabled = canConfirm,
                onClick = onRequestConfirm,
            ) {
                Text("确认结算")
            }
        }

        actionMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(message, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }

        when {
            previewState.isGenerating -> LoadingSection("正在生成结算预览")
            previewState.errorMessage != null -> NoticeBanner(
                title = "结算预览失败",
                message = previewState.errorMessage,
            )
            previewState.preview != null -> SettlementPreviewContent(previewState.preview)
            else -> StatusPill("尚未生成结算预览，确认结算保持禁用")
        }
    }
}

@Composable
private fun SettlementPreviewContent(preview: SettlementPreviewDto) {
    SectionCard(
        title = "结算影响预览（只读）",
        description = "预览不会写 settlement_confirm / ledger_txn，也不会改动订单状态与占用资金。",
    ) {
        KeyValueRow("订单号", preview.orderId)
        KeyValueRow("订单类型", preview.orderTypeLabel ?: SettlementEditState.orderTypeLabel(preview.orderType))
        KeyValueRow("订单状态", preview.orderStatus ?: "未知")
        KeyValueRow("产品", preview.productName ?: "未提供")
        KeyValueRow("确认日期", preview.confirmDate ?: "未提供")
        KeyValueRow("净值日期", preview.navDate ?: "未提供")
        KeyValueRow("实际净值", SettlementEditState.formatNumber(preview.confirmNav))
        KeyValueRow("实际份额", preview.confirmShares?.let(SettlementEditState::formatNumber) ?: "未填写")
        KeyValueRow("实际到账金额", preview.confirmAmount?.let(SettlementEditState::formatNumber) ?: "未填写")
        KeyValueRow("计算份额", preview.computedShares?.let(SettlementEditState::formatNumber) ?: "本类型不适用")
        KeyValueRow("计算金额", preview.computedAmount?.let(SettlementEditState::formatNumber) ?: "本类型不适用")
        KeyValueRow("手续费", preview.confirmFee?.let(SettlementEditState::formatNumber) ?: "按费率估算")
        KeyValueRow("结算影响开关", SettlementEditState.impactFlagsText(preview))
        StatusPill(if (preview.confirmSupported) "可以确认结算" else "不可确认")

        val summary = preview.summaryLines.orEmpty().filter { it.isNotBlank() }
        if (summary.isNotEmpty()) {
            Text("结算影响：", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            summary.forEach { line -> Text("· $line", style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
        }
        preview.postingsPreview.orEmpty().forEach { posting ->
            KeyValueRow(
                posting.accountName ?: posting.accountType ?: "账户",
                "${posting.postingType ?: ""} ${posting.amount?.let(SettlementEditState::formatNumber) ?: ""}".trim(),
            )
        }
        preview.warnings.orEmpty().filter { it.isNotBlank() }.forEach { warning ->
            Text("提示：$warning", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
        if (!preview.confirmSupported) {
            Text(
                "阻断原因：${SettlementEditState.blockingText(preview)}",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ConfirmSettlementDialog(
    preview: SettlementPreviewDto?,
    canConfirm: Boolean,
    isConfirming: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(SettlementEditState.confirmDialogTitle(preview)) },
        text = { Text(SettlementEditState.confirmDialogMessage(preview, canConfirm)) },
        confirmButton = {
            Button(enabled = canConfirm && !isConfirming, onClick = onConfirm) {
                Text(if (isConfirming) "结算中" else "二次确认并结算")
            }
        },
        dismissButton = {
            OutlinedButton(enabled = !isConfirming, onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}