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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.repository.AiAccountingRepository
import com.timelordtty.mydca.notification.NotificationCandidate
import com.timelordtty.mydca.notification.NotificationCandidateStore
import com.timelordtty.mydca.notification.NotificationDraftInput
import com.timelordtty.mydca.notification.NotificationPermissionState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    baseUrl: String,
    displayName: String?,
    apiConfigError: String?,
    aiAccountingRepository: AiAccountingRepository?,
    onBaseUrlChange: (String) -> Unit,
    onLogout: () -> Unit,
    onOpenDraft: (Long) -> Unit,
) {
    PageScaffold {
        SafetyBanner("登录令牌由 Android Keystore 加密保护。设置页不会显示、复制或记录完整 Token。")
        SectionCard(
            title = "认证会话",
            description = "退出登录会立即清除内存和本地加密凭据，即使远端登出请求失败也不会保留本地会话。",
        ) {
            KeyValueRow("当前用户", displayName ?: "已认证用户")
            StatusPill("已安全登录")
            OutlinedButton(onClick = onLogout) {
                Text("退出登录")
            }
        }
        SectionCard(
            title = "接口配置",
            description = "BaseUrl 仅用于当前运行中的开发联调，不包含账号密码或 Token。",
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text("BaseUrl") },
            )
        }
        apiConfigError?.let { message ->
            SectionCard(
                title = "接口配置需要修正",
                description = message,
            ) {
                StatusPill("请检查 BaseUrl")
            }
        }
        NotificationListenerSettingsSection(
            aiAccountingRepository = aiAccountingRepository,
            apiConfigError = apiConfigError,
            onOpenDraft = onOpenDraft,
        )
        SectionCard(title = "当前未接入能力") {
            KeyValueRow("企业微信入口", "未接入")
            KeyValueRow("真实大模型", "未接入")
        }
    }
}

@Composable
private fun NotificationListenerSettingsSection(
    aiAccountingRepository: AiAccountingRepository?,
    apiConfigError: String?,
    onOpenDraft: (Long) -> Unit,
) {
    val context = LocalContext.current
    var permissionEnabled by remember {
        mutableStateOf(NotificationPermissionState.isNotificationListenerEnabled(context))
    }
    val candidates by NotificationCandidateStore.candidates.collectAsState()

    SectionCard(
        title = "通知监听基础壳",
        description = "只有主人手动在系统设置中授权后，Android 才能读取通知。当前只生成本地候选通知，必须由用户手动点击才能生成 DRAFT 草稿。",
    ) {
        KeyValueRow("授权状态", if (permissionEnabled) "已启用" else "未启用")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    context.startActivity(NotificationPermissionState.notificationListenerSettingsIntent())
                },
            ) {
                Text("打开系统授权页")
            }
            OutlinedButton(
                onClick = {
                    permissionEnabled = NotificationPermissionState.isNotificationListenerEnabled(context)
                },
            ) {
                Text("刷新状态")
            }
        }
        SafetyBanner("支付通知识别目前仍然是本地候选：不落库、不持久化完整通知原文、不打印通知原文。点击生成草稿后也只会进入后端 DRAFT 流程，不会自动 preview 或 confirm。")
        NotificationCandidateList(
            candidates = candidates,
            aiAccountingRepository = aiAccountingRepository,
            apiConfigError = apiConfigError,
            onOpenDraft = onOpenDraft,
        )
    }
}

@Composable
private fun NotificationCandidateList(
    candidates: List<NotificationCandidate>,
    aiAccountingRepository: AiAccountingRepository?,
    apiConfigError: String?,
    onOpenDraft: (Long) -> Unit,
) {
    SectionCard(
        title = "候选通知",
        description = "仅展示最近内存候选的脱敏摘要。应用重启或进程结束后候选会丢失。",
    ) {
        if (candidates.isEmpty()) {
            StatusPill("暂无候选通知")
            Text("授权通知监听后，新通知会先进入本地候选列表；当前不会自动创建草稿。")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                candidates.take(5).forEach { candidate ->
                    key(candidate.id) {
                        NotificationCandidateCard(
                            candidate = candidate,
                            aiAccountingRepository = aiAccountingRepository,
                            apiConfigError = apiConfigError,
                            onOpenDraft = onOpenDraft,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCandidateCard(
    candidate: NotificationCandidate,
    aiAccountingRepository: AiAccountingRepository?,
    apiConfigError: String?,
    onOpenDraft: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var confirmCandidate by remember { mutableStateOf(false) }
    var creatingDraft by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var createdDraftId by remember { mutableStateOf<Long?>(null) }
    val candidateAmount = NotificationDraftInput.amountValue(candidate)
    val canCreateDraft = NotificationDraftInput.canCreateDraft(
        candidate = candidate,
        apiAvailable = aiAccountingRepository != null && apiConfigError.isNullOrBlank(),
        createdDraftId = createdDraftId,
    )

    SectionCard(
        title = candidate.appLabel ?: candidate.packageName,
        description = candidate.titleSnippet ?: "无标题摘要",
    ) {
        KeyValueRow("时间", formatPostedAt(candidate.postedAt))
        KeyValueRow("疑似支付", if (candidate.isPaymentCandidate) "是" else "否")
        KeyValueRow("金额", candidate.amount ?: "未识别")
        KeyValueRow("来源", candidate.sourceHint ?: "未识别")
        KeyValueRow("正文摘要", candidate.textSnippet ?: "无正文摘要")
        StatusPill("本地候选，必须手动生成草稿，不会自动入账")
        if (!candidate.isPaymentCandidate) {
            Text("当前通知未被识别为支付候选，不能生成草稿。")
        } else if (candidateAmount == null) {
            Text("金额不明确，请改用手动记账入口。")
        } else if (!apiConfigError.isNullOrBlank()) {
            Text("接口配置未就绪：$apiConfigError")
        }
        resultMessage?.let { Text(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                enabled = canCreateDraft && !creatingDraft,
                onClick = { confirmCandidate = true },
            ) {
                Text(if (creatingDraft) "生成中" else "生成草稿")
            }
            createdDraftId?.let { draftId ->
                OutlinedButton(onClick = { onOpenDraft(draftId) }) {
                    Text("打开草稿箱")
                }
            }
        }
    }

    if (confirmCandidate) {
        AlertDialog(
            onDismissRequest = { if (!creatingDraft) confirmCandidate = false },
            title = { Text("确认生成 DRAFT 草稿") },
            text = {
                Text("本操作只会调用 parse-text 和 draft-from-intent 生成待确认草稿，不会自动 preview、不会自动 confirm，也不会写正式账本。")
            },
            confirmButton = {
                TextButton(
                    enabled = !creatingDraft,
                    onClick = {
                        val repository = aiAccountingRepository ?: return@TextButton
                        confirmCandidate = false
                        creatingDraft = true
                        resultMessage = null
                        createdDraftId = null
                        scope.launch {
                            val rawInput = NotificationDraftInput.buildRawInput(candidate)
                            when (val parsed = repository.parseText(rawInput, candidate.id)) {
                                is NetworkResult.Failure -> {
                                    resultMessage = "解析失败：${parsed.message}"
                                }
                                is NetworkResult.Success -> {
                                    val intent = parsed.data.copy(
                                        sourceType = "PAYMENT_NOTIFICATION",
                                        sourceRef = candidate.id,
                                        rawInput = rawInput,
                                        amount = parsed.data.amount ?: candidateAmount,
                                        note = parsed.data.note ?: candidate.textSnippet ?: candidate.titleSnippet,
                                    )
                                    when (val draftResult = repository.draftFromIntent(intent)) {
                                        is NetworkResult.Failure -> {
                                            resultMessage = "创建草稿失败：${draftResult.message}"
                                        }
                                        is NetworkResult.Success -> {
                                            val draftId = draftResult.data.draft.id
                                            createdDraftId = draftId
                                            resultMessage = "草稿 #$draftId 已创建，尚未正式入账。请进入草稿箱手动 preview 和 confirm。"
                                        }
                                    }
                                }
                            }
                            creatingDraft = false
                        }
                    },
                ) {
                    Text("确认生成")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !creatingDraft,
                    onClick = { confirmCandidate = false },
                ) {
                    Text("取消")
                }
            },
        )
    }
}

private fun formatPostedAt(postedAt: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(postedAt))
}
