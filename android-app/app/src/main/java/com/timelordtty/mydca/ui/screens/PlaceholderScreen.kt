package com.timelordtty.mydca.ui.screens

import androidx.compose.runtime.Composable

@Composable
fun PlaceholderScreen(title: String, description: String) {
    PageScaffold {
        SafetyBanner("此页面当前为只读入口占位，不会修改账本、账户、持仓或订单数据。")
        SectionCard(title = title, description = description) {
            StatusPill("Phase3 Android 壳首版")
        }
    }
}
