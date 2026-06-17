package com.timelordtty.mydca.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TodayTodoScreen() {
    PageScaffold {
        SafetyBanner("今日待办只负责发现和导航。移动端不会自动确认草稿、不会执行结算、不会写正式账本。")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("草稿", "0", Modifier.weight(1f))
            MetricCard("结算", "0", Modifier.weight(1f))
            MetricCard("建议", "0", Modifier.weight(1f))
        }
        SectionCard(
            title = "暂无待办",
            description = "接入 GET /api/v2/todos/today 后，这里会展示 DRAFT 草稿、待结算和策略建议摘要。",
        ) {
            StatusPill("当前为安全空状态")
        }
    }
}
