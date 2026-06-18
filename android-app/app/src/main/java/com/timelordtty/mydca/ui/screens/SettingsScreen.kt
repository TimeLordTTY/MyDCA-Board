package com.timelordtty.mydca.ui.screens

import androidx.compose.runtime.Composable

@Composable
fun SettingsScreen(
    baseUrl: String,
    token: String,
    apiConfigError: String?,
    onBaseUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
) {
    PageScaffold {
        SafetyBanner("设置页首版只管理开发联调用的 BaseUrl 与 Token 输入。配置保存在当前内存状态中，不写入源码、不持久化、不记录明文 Token。")
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
        SectionCard(title = "当前未接入能力") {
            KeyValueRow("真实登录", "未接入")
            KeyValueRow("通知监听", "未接入")
            KeyValueRow("OCR", "未接入")
            KeyValueRow("支付通知解析", "未接入")
        }
    }
}
