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
    onBaseUrlChange: (String) -> Unit,
    onLogout: () -> Unit,
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
        NotificationListenerSettingsSection()
        SectionCard(title = "当前未接入能力") {
            KeyValueRow("企业微信入口", "未接入")
            KeyValueRow("真实大模型", "未接入")
        }
    }
}

@Composable
private fun NotificationListenerSettingsSection(
) {
    val context = LocalContext.current
    var permissionEnabled by remember {
        mutableStateOf(NotificationPermissionState.isNotificationListenerEnabled(context))
    }
    var postPermissionEnabled by remember { mutableStateOf(NotificationPermissionState.canPostNotifications(context)) }

    SectionCard(
        title = "通知监听基础壳",
        description = "只有主人手动在系统设置中授权后，Android 才能读取通知。当前只生成本地候选通知，必须由用户手动点击才能生成 DRAFT 草稿。",
    ) {
        KeyValueRow("授权状态", if (permissionEnabled) "已启用" else "未启用")
        KeyValueRow("本地提醒权限", if (postPermissionEnabled) "已启用" else "未启用")
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
                    postPermissionEnabled = NotificationPermissionState.canPostNotifications(context)
                },
            ) {
                Text("刷新状态")
            }
            OutlinedButton(onClick = { context.startActivity(NotificationPermissionState.appNotificationSettingsIntent(context)) }) {
                Text("本地提醒设置")
            }
        }
        SafetyBanner("仅持久化脱敏后的最小结构化字段，默认保留 7 天且最多 50 条。候选处理入口已移至今日待办；不会自动生成草稿、preview 或 confirm。")
    }
}
