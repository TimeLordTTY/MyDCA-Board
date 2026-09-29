package com.timelordtty.mydca.ui.screens

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.dto.BacktestResultDto
import retrofit2.HttpException
import java.util.Locale

/** Read only recent runs; this screen has no run or trade action. */
@Composable
fun BacktestRecentScreen(api: WealthHubApi) {
    var results by remember { mutableStateOf<List<BacktestResultDto>>(emptyList()) }
    var message by remember { mutableStateOf("加载最近回测中…") }
    var loadAttempt by remember { mutableStateOf(0) }
    var loadFailed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(api, loadAttempt) {
        loading = true
        message = if (results.isEmpty()) "加载最近回测中…" else "正在刷新，以下为上次成功读取的结果。"
        loadFailed = false
        try {
            results = api.recentBacktests().reversed()
            message = if (results.isEmpty()) "暂无回测结果，请在 PC 策略实验室运行历史回测。" else ""
        } catch (error: Exception) {
            loadFailed = true
            val reason = when ((error as? HttpException)?.code()) {
                401 -> "登录已失效，请重新登录。"
                403 -> "当前账号没有回测历史访问权限。"
                else -> "最近回测加载失败，请检查网络后重试。"
            }
            message = if (results.isEmpty()) reason else "$reason 以下为上次成功读取的旧结果。"
        } finally {
            loading = false
        }
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("历史回测不代表未来表现；此处仅展示结果。") }
        if (message.isNotEmpty()) item { Text(message) }
        if (!loading) item { OutlinedButton(onClick = { loadAttempt++ }) { Text(if (loadFailed) "重试加载" else "刷新结果") } }
        items(results, key = { it.run_id }) { result ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${result.strategy.name} v${result.strategy.version} · ${result.data_range.start} 至 ${result.data_range.end}")
                    Text("收益 ${pct(result.metrics.total_return)} · 年化 ${pct(result.metrics.annualized_return)}")
                    Text("最大回撤 ${pct(result.metrics.max_drawdown)} · 期末资产 ${num(result.metrics.final_assets)}")
                    Text("相对基准资产差 ${num(result.baseline?.final_assets_delta)} · 年化差 ${pct(result.baseline?.annualized_return_delta)}")
                }
            }
        }
    }
}

private fun pct(value: Double?): String = value?.let { String.format(Locale.CHINA, "%.2f%%", it * 100) } ?: "未知"
private fun num(value: Double?): String = value?.let { String.format(Locale.CHINA, "%.2f", it) } ?: "未知"
