package com.timelordtty.mydca.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.timelordtty.mydca.notification.NotificationCandidate
import com.timelordtty.mydca.notification.NotificationCandidateStore
import com.timelordtty.mydca.notification.NotificationPermissionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    baseUrl: String,
    token: String,
    apiConfigError: String?,
    onBaseUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
) {
    PageScaffold {
        SafetyBanner("设置页首版只管理开发联调用的 BaseUrl、Token 输入和通知监听授权入口。配置保存在当前内存状态中，不写入源码、不持久化、不记录明文 Token。")
        LoginPlaceholderContent(
            baseUrl = baseUrl,
            token = token,
            onBaseUrlChange = onBaseUrlChange,
            onTokenChange = onTokenChange,
        )
        apiConfigError?.let { message ->
            SectionCard(
                title = "接口配置需要修正",
                description = message,
            ) {
                StatusPill("请检查 BaseUrl")
            }
        }
        NotificationListenerSettingsSection()
        SectionCard(title = "当前未接入能力") {
            KeyValueRow("真实登录", "未接入")
            KeyValueRow("安全 Token 持久化", "未接入")
            KeyValueRow("OCR", "未接入")
            KeyValueRow("支付通知生成草稿", "未接入")
            KeyValueRow("企业微信入口", "未接入")
            KeyValueRow("真实大模型", "未接入")
        }
    }
}

@Composable
private fun NotificationListenerSettingsSection() {
    val context = LocalContext.current
    var permissionEnabled by remember {
        mutableStateOf(NotificationPermissionState.isNotificationListenerEnabled(context))
    }
    val candidates by NotificationCandidateStore.candidates.collectAsState()

    SectionCard(
        title = "通知监听基础壳",
        description = "只有主人手动在系统设置中授权后，Android 才能读取通知。当前只生成本地候选通知，不会自动生成草稿、不会自动预览、不会自动确认入账。",
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
        SafetyBanner("支付通知识别目前只是本地候选：不落库、不持久化完整通知原文、不打印通知原文。后续必须由用户点击生成草稿后，才允许进入后端草稿流程。")
        NotificationCandidateList(candidates = candidates)
    }
}

@Composable
private fun NotificationCandidateList(candidates: List<NotificationCandidate>) {
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
                    NotificationCandidateCard(candidate)
                }
            }
        }
    }
}

@Composable
private fun NotificationCandidateCard(candidate: NotificationCandidate) {
    SectionCard(
        title = candidate.appLabel ?: candidate.packageName,
        description = candidate.titleSnippet ?: "无标题摘要",
    ) {
        KeyValueRow("时间", formatPostedAt(candidate.postedAt))
        KeyValueRow("疑似支付", if (candidate.isPaymentCandidate) "是" else "否")
        KeyValueRow("金额", candidate.amount ?: "未识别")
        KeyValueRow("来源", candidate.sourceHint ?: "未识别")
        KeyValueRow("正文摘要", candidate.textSnippet ?: "无正文摘要")
        StatusPill("本地候选，不会自动入账")
    }
}

private fun formatPostedAt(postedAt: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(postedAt))
}
