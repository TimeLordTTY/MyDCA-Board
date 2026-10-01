package com.timelordtty.mydca.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.dto.ResearchPlanDto
import com.timelordtty.mydca.data.dto.ResearchRunDto
import com.timelordtty.mydca.data.repository.ResearchPlanRepository
import com.timelordtty.mydca.ui.state.*
import java.util.Locale

/** Foreground, manually refreshed research viewing. No create/edit/run action exists. */
@Composable
fun ResearchPlansScreen(api: WealthHubApi) {
    val repository = remember(api) { ResearchPlanRepository(api) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var recentOpen by remember { mutableStateOf(false) }
    if (recentOpen) {
        BackHandler { recentOpen = false }
        Column {
            OutlinedButton(onClick = { recentOpen = false }) { Text("返回研究方案") }
            BacktestRecentScreen(api)
        }
        return
    }
    var page by remember { mutableStateOf(0) }
    var runPage by remember { mutableStateOf(0) }
    var attempt by remember { mutableStateOf(0) }
    var plans by remember(repository) { mutableStateOf(ResearchReadState<List<ResearchPlanDto>>()) }
    var detail by remember(repository) { mutableStateOf(ResearchReadState<ResearchPlanDto>()) }
    var history by remember(repository) { mutableStateOf(ResearchReadState<List<ResearchRunDto>>()) }
    var hasMoreRuns by remember { mutableStateOf(false) }
    BackHandler(enabled = selectedId != null) { selectedId = null }
    LaunchedEffect(repository, page, attempt) {
        plans = ResearchReadState()
        plans = plans.loaded(repository.plans(page))
    }
    LaunchedEffect(repository, selectedId, attempt) {
        detail = ResearchReadState()
        selectedId?.let { detail = detail.loaded(repository.detail(it)) }
    }
    LaunchedEffect(repository, runPage, attempt) {
        val previous = if (runPage == 0) emptyList() else history.data.orEmpty()
        history = ResearchReadState(loading = true)
        val loaded = history.loaded(repository.runs(runPage))
        hasMoreRuns = loaded.data?.size == 50 && runPage < 2000
        history = if (loaded.data != null) loaded.copy(data = (previous + loaded.data).distinctBy { it.historyRunId }) else loaded
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("研究方案 · 只读")
            Text("研究结果不代表未来表现；不构成投资建议。")
            OutlinedButton(onClick = { if (selectedId != null) selectedId = null else recentOpen = true }) {
                Text(if (selectedId != null) "返回方案列表" else "查看最近成功回测")
            }
            OutlinedButton(onClick = { runPage = 0; attempt++ }, enabled = !plans.loading && !history.loading && (selectedId == null || !detail.loading)) { Text("刷新 / 重试") }
        }
        item {
            if (history.loading) Text("正在加载回测历史…")
            history.error?.let { Text("回测历史读取失败：$it；运行状态与指标未知。") }
            if (history.data != null) Text("已读取最近 ${history.data!!.size} 条运行（含其他方案）；可继续读取更早历史。")
        }
        if (selectedId == null) {
            item {
                if (plans.loading) Text("正在加载研究方案…")
                plans.error?.let { Text("研究方案读取失败：$it") }
                if (plans.data != null && visibleResearchPlans(plans.data!!).isEmpty()) Text("本页暂无 DRAFT / ACTIVE 研究方案。可翻页查看或在 PC 管理方案。")
            }
            items(visibleResearchPlans(plans.data.orEmpty()), key = { it.id }) { plan ->
                ResearchCard(plan) {
                    val latest = planRuns(plan, history.data.orEmpty()).firstOrNull()
                    Text("最近已读取运行：${if (history.error != null || history.loading) "未知" else researchStatus(latest?.status)}")
                    if (latest?.status == "SUCCESS") ResearchMetrics(latest.metrics)
                    if (latest?.status != null && latest.status != "SUCCESS") Text("运行未成功：${latest.failureCode ?: "无失败代码"}；无可用成功指标")
                    OutlinedButton(onClick = { selectedId = plan.id }) { Text("查看详情与证据") }
                }
            }
            item {
                Text("方案第 ${page + 1} 页")
                if (page > 0) OutlinedButton(onClick = { page-- }, enabled = !plans.loading) { Text("上一页") }
                if (plans.data?.size == 20 && page < 5000) OutlinedButton(onClick = { page++ }, enabled = !plans.loading) { Text("下一页") }
            }
        } else {
            item {
                if (detail.loading) Text("正在加载方案详情…")
                detail.error?.let { Text("方案详情读取失败：$it；方案可能不存在或无权访问。") }
            }
            detail.data?.let { plan ->
                item {
                    ResearchCard(plan) {
                        Text("备注：${plan.description ?: "无"}")
                        Text("来源候选：${plan.sourceCandidateId ?: "未知"}")
                        Text("来源运行：${plan.sourceRunIds.joinToString().ifEmpty { "无" }}")
                        Text("创建时参数快照：${plan.canonicalParamsSnapshot}")
                        Text("研究参数草稿（仅查看）：${plan.paramsDraft}")
                        Text("数据集 hashes：${plan.datasetHashes.joinToString().ifEmpty { "未知" }}")
                        Text("证据引用：${plan.evidenceBundleRef ?: "未知"}")
                    }
                    if (plan.evidenceSnapshot?.runs.isNullOrEmpty()) Text("无可读取的来源证据快照；证据状态未知。")
                }
                items(plan.evidenceSnapshot?.runs.orEmpty(), key = { "source-${it.run_id}" }) { source ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("创建时来源证据：${source.run_id}")
                            Text("dataset hash：${source.dataset_hash ?: "未知"}")
                            Text("engine：${source.engine_version ?: "未知"}")
                            ResearchMetrics(source.metrics)
                        }
                    }
                }
                item { Text("方案历史运行（以 researchPlanId 关联，不混入来源运行）") }
                val runs = planRuns(plan, history.data.orEmpty())
                if (runs.isEmpty() && history.data != null) item { Text("已读取历史中暂无此方案运行；可继续读取更早历史。") }
                items(runs, key = { "history-${it.historyRunId}" }) { run ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${run.startedAt ?: "时间未知"} · ${researchStatus(run.status)}")
                            Text("run：${run.historyRunId}")
                            Text("dataset：${run.dataset ?: "未知"} / ${run.datasetHash ?: "未知"}")
                            Text("engine：${run.engineVersion ?: "未知"}")
                            if (run.status == "SUCCESS") ResearchMetrics(run.metrics)
                            else Text("运行未成功：${run.failureCode ?: "无失败代码"}；无可用成功指标")
                        }
                    }
                }
            }
        }
        if (hasMoreRuns && !history.loading && history.error == null) item {
            OutlinedButton(onClick = { runPage++ }) { Text("读取更早回测历史") }
        }
    }
}

@Composable
private fun ResearchCard(plan: ResearchPlanDto, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${plan.name} · ${researchStatus(plan.status)}")
            Text("${plan.strategy} / version ${plan.strategyVersion}")
            if (plan.warnings.isEmpty()) Text("服务端未报告证据警告")
            else plan.warnings.forEach { Text("证据警告：$it") }
            content()
        }
    }
}

@Composable
private fun ResearchMetrics(metrics: Map<String, Double?>?) {
    fun percent(key: String) = metrics?.get(key)?.takeIf { it.isFinite() }?.let { String.format(Locale.CHINA, "%.2f%%", it * 100) } ?: "未知"
    Text("收益 ${percent("total_return")} · 年化 ${percent("annualized_return")} · 最大回撤 ${percent("max_drawdown")}")
}
