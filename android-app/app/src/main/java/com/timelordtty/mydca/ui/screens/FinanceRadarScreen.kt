package com.timelordtty.mydca.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timelordtty.mydca.data.dto.FinanceRadarDto
import com.timelordtty.mydca.data.repository.FinanceRadarRepository
import com.timelordtty.mydca.ui.FinanceRadarDestination
import com.timelordtty.mydca.ui.state.FinanceRadarState
import com.timelordtty.mydca.ui.state.FinanceRadarStateHolder
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun FinanceRadarScreen(
    repository: FinanceRadarRepository,
    outboxCount: Int,
    onClose: () -> Unit,
    onNavigate: (FinanceRadarDestination) -> Unit,
) {
    var state by remember(repository) { mutableStateOf(FinanceRadarState()) }
    val holder = remember(repository) { FinanceRadarStateHolder(repository) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        if (state.loading) return
        state = state.copy(loading = true, error = null)
        scope.launch { state = holder.refresh(state) }
    }

    // The snapshot lives only in this composition. Process restoration always starts with a GET.
    LaunchedEffect(repository) { refresh() }

    PageScaffold {
        OutlinedButton(onClick = onClose) { Text("返回总览") }
        SafetyBanner("每日财富雷达只读展示服务端事实；行情和指标状态是数据质量提醒，不是交易建议。")
        when {
            state.snapshot == null && state.loading -> LoadingSection("正在读取财富雷达")
            state.snapshot == null && state.error != null ->
                ErrorSection("雷达读取失败", state.error!!, ::refresh)
            state.snapshot == null -> EmptySection("暂无雷达数据", "请手动刷新后再查看。")
            else -> {
                RefreshBar(state.lastSuccessAt, state.loading) { refresh() }
                if (state.isStale || state.snapshot?.date != LocalDate.now().toString()) {
                    NoticeBanner(
                        if (state.isStale) "旧快照 · 尚未刷新成功" else "历史日期快照",
                        state.error?.let { "$it。以下为上次成功结果，请手动重试。" }
                            ?: if (state.loading) "正在刷新，以下仍为上次成功结果。"
                            else "快照日期不是今天，请手动刷新确认当前状态。",
                        if (state.loading) null else ::refresh,
                    )
                }
                RadarContent(requireNotNull(state.snapshot), outboxCount, onNavigate)
            }
        }
    }
}

@Composable
private fun RadarContent(
    radar: FinanceRadarDto,
    outboxCount: Int,
    onNavigate: (FinanceRadarDestination) -> Unit,
) {
    val assets = radar.assets
    val counts = radar.counts
    SectionCard("核心资产摘要", "数据日期：${radar.date ?: "未知"} · 状态：${statusText(assets?.status)}") {
        KeyValueRow("现金余额", radarMoney(assets?.cashBalance))
        KeyValueRow("投资成本", radarMoney(assets?.investmentCost))
        KeyValueRow("持仓市值", radarMoney(assets?.positionValue))
        KeyValueRow("负债", radarMoney(assets?.liabilities))
        KeyValueRow("总资产", radarMoney(assets?.totalAssets))
        KeyValueRow("净资产", radarMoney(assets?.netWorth))
        RadarLink("查看资产", FinanceRadarDestination.ASSETS, onNavigate)
    }
    SectionCard("待处理事实", "未知表示服务端无法确定，不能视为零。Outbox 数量来自本机加密队列。") {
        KeyValueRow("DRAFT 草稿", countText(counts?.drafts))
        RadarLink("打开草稿箱", FinanceRadarDestination.DRAFTS, onNavigate)
        KeyValueRow("本机 Outbox", outboxCount.toString())
        RadarLink("打开 Outbox 恢复", FinanceRadarDestination.OUTBOX, onNavigate)
        KeyValueRow("PENDING 订单", countText(counts?.pendingOrders))
        KeyValueRow("待结算", countText(counts?.awaitingSettlement))
        RadarLink("查看待结算", FinanceRadarDestination.SETTLEMENTS, onNavigate)
        KeyValueRow("对账 WARNING", countText(counts?.reconciliationWarning))
        KeyValueRow("对账 BROKEN", countText(counts?.reconciliationBroken))
    }
    SectionCard("行情与指标", "过期与未知仅提示数据质量，日期均由服务端提供。") {
        if (radar.markets.isEmpty()) Text("暂无持仓行情数据")
        radar.markets.forEach { market ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("产品 ${market.productId ?: "未知"}")
                KeyValueRow("行情", "${statusText(market.status)} · ${market.priceDate ?: "日期未知"}")
                KeyValueRow("估值日期", market.valuationDate ?: "未知")
                KeyValueRow("指标", "${statusText(market.indicatorStatus)} · ${market.indicatorDate ?: "日期未知"}")
            }
        }
    }
    SectionCard("数据提醒") {
        val warnings = radar.warnings.filterNot { it.code == "OUTBOX_UNKNOWN" }
        if (warnings.isEmpty()) Text("暂无数据质量提醒")
        warnings.forEach { warning ->
            Text("${statusText(warning.status)}：${warning.message ?: "详情未知"}")
        }
    }
}

@Composable
private fun RadarLink(label: String, target: FinanceRadarDestination, onNavigate: (FinanceRadarDestination) -> Unit) {
    OutlinedButton(onClick = { onNavigate(target) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

internal fun countText(value: Int?): String = value?.toString() ?: "未知"
internal fun radarMoney(value: String?): String = if (value == null) "未知" else formatMoney(value)
internal fun statusText(value: String?): String = when (value) {
    "OK" -> "正常"
    "WARNING" -> "过期或需关注"
    "BROKEN" -> "异常"
    else -> "未知"
}
