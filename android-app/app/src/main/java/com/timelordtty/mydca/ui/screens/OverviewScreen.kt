package com.timelordtty.mydca.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement

@Composable
fun OverviewScreen() {
    PageScaffold {
        SafetyBanner("移动端总览首版只展示入口与状态，不计算账本影响，不替代后端资产口径。")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("待确认草稿", "0", Modifier.weight(1f))
            MetricCard("待结算", "0", Modifier.weight(1f))
        }
        SectionCard(
            title = "移动端承接范围",
            description = "先承接今日待办、草稿箱和确认前预览；账户、流水、持仓以只读查看为主。",
        )
    }
}
