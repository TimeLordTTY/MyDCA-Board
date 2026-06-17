package com.timelordtty.mydca.ui.screens

import androidx.compose.runtime.Composable

@Composable
fun SettingsScreen() {
    PageScaffold {
        SafetyBanner("设置页首版只展示配置边界。真实通知监听、OCR、支付通知解析留给后续阶段。")
        SectionCard(title = "当前未接入能力") {
            KeyValueRow("真实登录", "未接入")
            KeyValueRow("通知监听", "未接入")
            KeyValueRow("OCR", "未接入")
            KeyValueRow("支付通知解析", "未接入")
        }
    }
}
