package com.timelordtty.mydca.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.dto.*
import com.timelordtty.mydca.data.repository.AllocationRepository
import com.timelordtty.mydca.ui.state.*

@Composable
fun AllocationScreen(api: WealthHubApi, onClose: () -> Unit) {
    val repository = remember(api) { AllocationRepository(api) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf(0) }
    var retry by remember { mutableStateOf(0) }
    var previewRequested by remember { mutableStateOf(false) }
    var previewRetry by remember { mutableStateOf(0) }
    var policies by remember(repository) { mutableStateOf(ResearchReadState<List<AllocationPolicyDto>>()) }
    var detail by remember(repository) { mutableStateOf(ResearchReadState<AllocationPolicyDto>()) }
    var observation by remember(repository) { mutableStateOf(ResearchReadState<AllocationEvaluationDto>()) }
    var preview by remember(repository) { mutableStateOf(ResearchReadState<AllocationPreviewDto>(loading = false)) }
    fun back() { if (selectedId != null) { selectedId = null; previewRequested = false } else onClose() }
    BackHandler { back() }
    LaunchedEffect(repository, selectedId, page, retry) {
        policies = ResearchReadState(); detail = ResearchReadState(); observation = ResearchReadState()
        previewRequested = false; preview = ResearchReadState(loading = false)
        val id = selectedId
        if (id == null) policies = policies.loaded(repository.policies(page))
        else {
            detail = detail.loaded(repository.detail(id))
            if (detail.data != null) observation = observation.loaded(repository.observation(id))
            else observation = ResearchReadState(loading = false)
        }
    }
    LaunchedEffect(repository, selectedId, retry, previewRequested, previewRetry) {
        preview = ResearchReadState(loading = false)
        val id = selectedId
        if (id != null && previewRequested && detail.data != null) {
            preview = ResearchReadState()
            preview = preview.loaded(repository.preview(id))
        }
    }
    val loading = if (selectedId == null) policies.loading else detail.loading || observation.loading || preview.loading
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("配置偏离与止盈观察 · 只读", style = MaterialTheme.typography.titleLarge)
            Text(ALLOCATION_DISCLAIMER)
            OutlinedButton(onClick = { back() }) { Text(if (selectedId == null) "返回总览" else "返回配置列表") }
            OutlinedButton(onClick = { retry++ }, enabled = !loading) { Text("刷新 / 重试") }
        }
        if (loading) item { AllocationNotice("正在读取配置观察…") }
        if (selectedId == null) {
            policies.error?.let { item { AllocationNotice("读取失败：$it；当前状态未知 UNKNOWN") } }
            if (policies.data?.isEmpty() == true) item { Text("暂无主人配置的观察规则。") }
            items(policies.data.orEmpty(), key = { it.id }) { policy ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    AllocationSummary(policy)
                    OutlinedButton(onClick = { selectedId = policy.id; previewRequested = false }) { Text("查看偏离与收益观察") }
                } }
            }
            item {
                Text("第 ${page + 1} 页")
                if (page > 0) OutlinedButton(onClick = { page-- }, enabled = !loading) { Text("上一页") }
                if (policies.data?.size == 20) OutlinedButton(onClick = { page++ }, enabled = !loading) { Text("下一页") }
            }
        } else {
            detail.error?.let { item { AllocationNotice("规则读取失败：$it；当前状态未知 UNKNOWN") } }
            detail.data?.let { policy ->
                item { AllocationSummary(policy) }
                observation.error?.let { item { AllocationNotice("观察读取失败：$it；收益与偏离未知 UNKNOWN") } }
                observation.data?.let { evidence -> item {
                    Text("观察状态：${allocationLabel(evidence.status)}")
                    Text(evidence.reason ?: "原因未知 UNKNOWN")
                    Text("当前权重：${allocationPercent(evidence.allocation)}")
                    Text("收益率：${allocationPercent(evidence.returnRate)}")
                    Text("已触发分段阈值：${evidence.reachedTakeProfitThresholds.joinToString { allocationPercent(it) }.ifEmpty { if (evidence.returnRate == null) "未知 UNKNOWN" else "无" }}")
                    Text("收益阈值：${allocationReturnWatch(policy.config.returnThreshold, evidence.returnRate)}")
                    Text("评估时间：${evidence.evaluatedAt ?: "未知 UNKNOWN"}；数据日期见主动读取的情景快照，二者可能不同。")
                    Text("止盈观察可能优先于偏离状态；独立区间状态以情景快照为准。")
                } }
                item {
                    OutlinedButton(onClick = { previewRequested = true; previewRetry++ }, enabled = !loading) { Text("查看 / 重试只读情景预览") }
                }
            }
            preview.error?.let { item { AllocationNotice("情景读取失败：$it；假设金额未知 UNKNOWN") } }
            preview.data?.let { evidence ->
                item {
                    Text("配置偏离：${allocationLabel(evidence.status)}")
                    Text(evidence.description ?: "说明未知 UNKNOWN")
                    Text("数据日期：${evidence.dataDate ?: "未知 UNKNOWN"}")
                    Text(allocationFreshness(evidence))
                    Text("当前权重：${allocationPercent(evidence.currentWeight)}；目标：${allocationPercent(evidence.targetWeight)}")
                    Text("目标区间：${allocationPercent(evidence.lowerBound)} 至 ${allocationPercent(evidence.upperBound)}")
                    Text("偏离目标：${evidence.deviationPercentagePoints ?: "未知 UNKNOWN"} 个百分点")
                    Text("组合总资产（人民币现金 + 持仓，不扣负债）：${evidence.totalAssets ?: "未知 UNKNOWN"}")
                    AllocationScenario("到目标点", evidence.targetScenario)
                    AllocationScenario("到最近区间边界", evidence.bandScenario)
                    Text("正数为假设增加，负数为假设减少；其余组合等额反向。金额不是可执行金额，不校验资金可用性，不分配到其他产品。")
                }
                items(evidence.prices) { price -> Text("产品 ${price.productId ?: "未知"}：${allocationLabel(price.status)}；行情日 ${price.priceDate ?: "UNKNOWN"}；估值日 ${price.valuationDate ?: "UNKNOWN"}；价格来源 ${price.priceSource ?: "UNKNOWN"}") }
                items(evidence.warnings) { warning -> Text("${warning.code ?: "未知"} · ${allocationLabel(warning.status)}：${warning.message ?: "说明未知"}") }
                item { Text("税、费用、滑点、交易限制、外汇：未建模 NOT_MODELED；金额舍入可能保留亚分偏离。") }
            }
        }
    }
}

@Composable
private fun AllocationNotice(text: String) { Text(text, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }

@Composable
private fun AllocationSummary(policy: AllocationPolicyDto) {
    val config = policy.config
    Text("规则 ${policy.id} · ${if (config.enabled == true) "启用" else "停用 / 未知"}")
    Text("${riskLabel(config.scope)} · 产品 ${config.productId ?: "无"} · 类别 ${config.assetType ?: "无"}")
    Text("目标 ${allocationPercent(config.target)}；区间 ${allocationPercent(config.lowerBound)} 至 ${allocationPercent(config.upperBound)}")
    Text("收益观察阈值：${config.returnThreshold?.let { allocationPercent(it) } ?: "未配置"}")
    Text("分段止盈观察：${config.takeProfitThresholds.joinToString { allocationPercent(it) }.ifEmpty { "未配置" }}")
    Text("备注：${config.note ?: "无"}")
}

@Composable
private fun AllocationScenario(label: String, scenario: AllocationScenarioDto?) {
    Text("$label · 假设选中资产调整：${scenario?.selectedAdjustment ?: "未知 UNKNOWN"} 元；其余组合：${scenario?.remainderAdjustment ?: "未知 UNKNOWN"} 元")
}
