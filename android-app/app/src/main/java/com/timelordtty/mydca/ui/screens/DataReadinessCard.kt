package com.timelordtty.mydca.ui.screens

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.repository.DataReadinessRepository
import com.timelordtty.mydca.ui.state.*
import java.time.Instant

@Composable
fun DataReadinessCard(api: WealthHubApi, month: String) {
    val repository = remember(api) { DataReadinessRepository(api) }
    var state by remember(api, month) { mutableStateOf(DataReadinessState()) }
    var refresh by remember { mutableStateOf(0) }
    var details by remember { mutableStateOf(false) }
    LaunchedEffect(repository, month, refresh) {
        state = state.copy(loading = true)
        state = state.loaded(repository.read(month))
    }
    SectionCard("个人数据就绪 · $month", "只读诊断；可读取不代表达标或可以上线") {
        Text("数据库部署状态未知 UNKNOWN，需人工核验。缺失数据不代表数字 0。")
        if (state.loading) Text("正在读取诊断", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        state.error?.let { Text("读取失败：$it；当前状态未知 UNKNOWN，保留先前警示。", Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        val data = state.data
        if (data == null || data.evidence.isEmpty()) Text("暂无诊断证据：未知 UNKNOWN，需人工核验")
        if (data != null) {
            KeyValueRow("诊断时间", data.checkedAt ?: "未知")
            if (state.error != null || state.stale(Instant.now())) Text("旧快照或时间未核实，不代表当前数据就绪；请手动刷新。")
            data.evidence.forEach { item -> Text("${readinessArea(item.area)}：${readinessEvidenceLabel(item.area, item.state)}") }
            OutlinedButton(onClick = { details = !details }) { Text(if (details) "收起来源详情" else "查看来源详情") }
            if (details) data.evidence.forEach { item ->
                SectionCard(readinessArea(item.area), readinessEvidenceLabel(item.area, item.state)) {
                    Text(item.reason ?: "原因未知，需人工核验")
                    KeyValueRow("逻辑来源", item.source ?: "未知")
                    KeyValueRow("来源更新时间", item.dataTime ?: "未知")
                    Text(item.nextStep ?: "需人工核验")
                }
            }
        }
        OutlinedButton(onClick = { refresh++ }, enabled = !state.loading) { Text("手动刷新数据就绪") }
    }
}
