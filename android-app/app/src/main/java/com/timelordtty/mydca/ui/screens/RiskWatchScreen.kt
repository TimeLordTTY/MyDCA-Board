package com.timelordtty.mydca.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.dto.*
import com.timelordtty.mydca.data.repository.RiskWatchRepository
import com.timelordtty.mydca.ui.state.*

@Composable
fun RiskWatchScreen(api: WealthHubApi, onClose: () -> Unit) {
    val repository = remember(api) { RiskWatchRepository(api) }
    var rule by remember { mutableStateOf<RiskRuleDto?>(null) }
    var page by remember { mutableStateOf(0) }
    var attempt by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf("OPEN") }
    var rules by remember(repository) { mutableStateOf(ResearchReadState<List<RiskRuleDto>>()) }
    var events by remember(repository) { mutableStateOf(ResearchReadState<List<RiskEventDto>>()) }
    var snapshots by remember(repository) { mutableStateOf(ResearchReadState<List<RiskSnapshotDto>>()) }
    fun back() { if (rule != null) { rule = null; page = 0 } else onClose() }
    BackHandler { back() }
    LaunchedEffect(repository, rule?.id, page, attempt) {
        rules = ResearchReadState(); events = ResearchReadState(); snapshots = ResearchReadState()
        val selected = rule
        if (selected == null) {
            rules = rules.loaded(repository.rules(page))
            val collected = mutableListOf<RiskEventDto>()
            var failure: NetworkResult.Failure? = null
            for (entry in rules.data.orEmpty()) {
                when (val result = repository.events(entry.id, 0)) {
                    is NetworkResult.Success -> collected.addAll(result.data)
                    is NetworkResult.Failure -> { failure = result; break }
                }
            }
            events = if (failure != null) events.loaded(failure) else events.loaded(NetworkResult.Success(collected))
        }
        else {
            events = events.loaded(repository.events(selected.id, page))
            snapshots = snapshots.loaded(repository.snapshots(selected.id, page))
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("风险观察 · 只读", style = MaterialTheme.typography.titleLarge)
        Text(RISK_DISCLAIMER)
        Text("仅查看已保存证据；刷新不会重新评估。")
        OutlinedButton(onClick = { back() }) { Text(if (rule == null) "返回总览" else "返回规则列表") }
        val loading = if (rule == null) rules.loading || events.loading else events.loading || snapshots.loading
        OutlinedButton(onClick = { attempt++ }, enabled = !loading) { Text("刷新 / 重试") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) item { Text("正在读取风险数据…") }
            if (rule == null) {
                rules.error?.let { item { Text("读取失败：$it") } }
                item {
                    Text("当前提醒（本页规则各最近 20 条；完整记录请打开规则历史）")
                    listOf("OPEN", "RESOLVED", "ALL").forEach { value ->
                        OutlinedButton(onClick = { filter = value }, enabled = filter != value) { Text(if (value == "ALL") "全部（含已读 / 静默）" else riskLabel(value)) }
                    }
                }
                events.error?.let { item { Text("提醒读取失败：$it；当前状态未知") } }
                val currentGroups = riskGroups(events.data.orEmpty(), filter)
                if (events.data != null && rules.data != null && currentGroups.isEmpty()) item { Text("已读取记录中暂无符合条件的提醒；不代表没有风险。") }
                currentGroups.forEach { (severity, entries) ->
                    item { Text(riskLabel(severity)) }
                    items(entries, key = { "current-${it.evidence.ruleId}-${it.fingerprint}" }) { event ->
                        Column {
                            Text("规则：${event.evidence.ruleId} · ${riskLabel(event.state)}")
                            RiskEvidence(event.evidence)
                            OutlinedButton(onClick = { rule = rules.data?.firstOrNull { it.id == event.evidence.ruleId }; page = 0 }) { Text("查看规则与历史") }
                        }
                    }
                }
                if (rules.data?.isEmpty() == true) item { Text("暂无风险观察规则。") }
                items(rules.data.orEmpty(), key = { it.id }) { entry ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text("${entry.config.type ?: "未知规则"} · ${riskLabel(entry.config.severity)}")
                        Text(entry.config.note ?: "无备注")
                        OutlinedButton(onClick = { rule = entry; page = 0 }) { Text("查看当前状态与历史") }
                    } }
                }
            } else {
                item {
                    Text("规则：${rule!!.id} / ${rule!!.config.type}")
                    Text("作用域：${rule!!.config.scope}；产品：${rule!!.config.productId ?: "无"}；类别：${rule!!.config.assetType ?: "无"}")
                    Text("阈值：${rule!!.config.threshold ?: "未知"}；目标：${rule!!.config.target ?: "无"}；方向：${rule!!.config.direction ?: "无"}")
                    Text("备注：${rule!!.config.note ?: "无"}")
                    Text("提醒状态（本页按严重级别分组，过滤仅针对已读取页）")
                    listOf("OPEN", "RESOLVED", "ALL").forEach { value ->
                        OutlinedButton(onClick = { filter = value }, enabled = filter != value) { Text(if (value == "ALL") "全部（含已读 / 静默）" else riskLabel(value)) }
                    }
                }
                events.error?.let { item { Text("提醒读取失败：$it；当前状态未知") } }
                val groups = riskGroups(events.data.orEmpty(), filter)
                if (events.data != null && groups.isEmpty()) item { Text("本页无符合过滤条件的提醒；不代表没有风险。") }
                groups.forEach { (severity, entries) ->
                    item { Text(riskLabel(severity)) }
                    items(entries, key = { it.fingerprint }) { event ->
                        Column { Text(riskLabel(event.state)); RiskEvidence(event.evidence) }
                    }
                }
                item { Text("评估历史（最近记录在前；包含未知及未命中证据）") }
                snapshots.error?.let { item { Text("历史读取失败：$it") } }
                if (snapshots.data?.isEmpty() == true) item { Text("尚无评估历史；当前风险未知。") }
                items(snapshots.data.orEmpty(), key = { "snapshot-${it.id}" }) { RiskEvidence(it) }
            }
            item {
                Text("第 ${page + 1} 页")
                if (page > 0) OutlinedButton(onClick = { page-- }, enabled = !loading) { Text("上一页") }
                val more = if (rule == null) rules.data?.size == 20 else events.data?.size == 20 || snapshots.data?.size == 20
                if (more) OutlinedButton(onClick = { page++ }, enabled = !loading) { Text("下一页") }
            }
        }
    }
}

@Composable
private fun RiskEvidence(evidence: RiskSnapshotDto) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
        Text("${riskLabel(evidence.severity)} · ${evidence.status ?: "UNKNOWN"} · ${if (evidence.matched) "命中" else "未命中 / 未知"}")
        Text("原因：${evidence.reason ?: "未知"}")
        Text("观察值：${evidence.observedValue ?: "未知"}；阈值：${evidence.threshold ?: "未知"}（比例 0.1 = 10%；陈旧阈值单位为天）")
        Text("来源时间：${evidence.sourceDataTimestamp ?: "未知"}")
        Text("评估时间：${evidence.createdAt ?: "未知"}")
        Text(riskDataWarning(evidence))
    } }
}
