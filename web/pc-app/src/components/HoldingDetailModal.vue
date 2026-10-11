<template>
  <el-dialog v-model="visible" :title="`持仓详情 - ${holding?.productName || `产品${holding?.productId}`}`" class="holdings-detail" width="min(1200px, 94vw)" @close="handleClose">
    <div v-if="holding" style="max-height: 70vh; overflow-y: auto">
      <!-- 持仓基本信息 -->
      <div class="card" style="margin-bottom: 16px">
        <h4>持仓信息 · {{ holding.brokerAccountName || "来源账户未提供" }}</h4>
        <p class="td-muted">{{ holding.valuationNote }} 净值证据日期：{{ holding.navDate || "未知" }}；价格时间：{{ holding.priceTime || "未知" }}。</p>
        <div class="row" style="gap: 24px; margin-top: 12px">
          <div>
            <div class="td-muted" style="font-size: 12px">持仓份额</div>
            <div style="font-size: 18px; font-weight: 600">{{ displayNumber(holding.sharesCalc, 2) }}</div>
          </div>
          <div>
            <div class="td-muted" style="font-size: 12px">平均成本</div>
            <div style="font-size: 18px; font-weight: 600">{{ displayNumber(holding.avgCostCalc, 4) }}</div>
          </div>
          <div>
            <div class="td-muted" style="font-size: 12px">{{ isExchange ? "实时报价" : "单位净值" }}</div>
            <div style="font-size: 18px; font-weight: 600">{{ displayNumber(holding.currentPrice, 4) }}</div>
          </div>
          <div>
            <div class="td-muted" style="font-size: 12px">持仓市值</div>
            <div style="font-size: 18px; font-weight: 600">{{ displayMoney(holding.marketValue) }}</div>
          </div>
          <div>
            <div class="td-muted" style="font-size: 12px">浮动盈亏</div>
            <div style="font-size: 18px; font-weight: 600" :class="`pnl-${pnlTone(holding.unrealizedPnl)}`">
              {{ holding.unrealizedPnl !== null && holding.unrealizedPnl > 0 ? '+' : '' }}{{ displayMoney(holding.unrealizedPnl) }}
            </div>
          </div>
        </div>
      </div>

      <div class="detail-cost"><span>剩余成本：{{ displayMoney(holding.totalCostCalc) }}</span><span>已实现盈亏：暂未提供（当前接口未返回）</span></div>
      <p class="td-muted">浮盈来自服务器持仓口径，不代表累计投资收益。</p>
      <p v-if="detailError" role="alert" class="detail-error">{{ detailError }} <el-button @click="loadActiveTab">重试</el-button></p>
      <p v-if="detailLoading" role="status">正在读取详情证据…</p>
      <!-- 图表标签页（按场内/场外差异化展示） -->
      <el-tabs v-model="activeTab">
        <!-- 场外：净值曲线 -->
        <el-tab-pane v-if="isOtc" label="净值曲线" name="nav">
          <div id="navChart" style="width: 100%; height: 400px"></div>
        </el-tab-pane>

        <!-- 场内：IOPV/估值与溢价；BOND_REPO 使用同一行情接口展示债券行情 -->
        <el-tab-pane v-if="isExchange" :label="isBondRepo ? '债券行情' : 'IOPV/估值'" name="iopv">
          <div v-if="isBondRepo" class="bond-quote-summary">
            <div>
              <span class="td-muted">最新价/年化收益率</span>
              <strong>{{ displayNumber(latestQuote?.price, 4) }}</strong>
            </div>
            <div>
              <span class="td-muted">涨跌幅</span>
              <strong>{{ formatRatioPercent(latestQuote?.pctChg) }}</strong>
            </div>
            <div>
              <span class="td-muted">成交量</span>
              <strong>{{ latestQuote?.volume != null ? formatNumber(latestQuote.volume, 2) : '-' }}</strong>
            </div>
            <div>
              <span class="td-muted">成交额</span>
              <strong>{{ latestQuote?.amount != null ? formatCurrency(latestQuote.amount) : '-' }}</strong>
            </div>
          </div>
          <div id="iopvChart" style="width: 100%; height: 400px"></div>
          <div v-if="!isBondRepo" class="td-muted" style="margin-top: 8px; font-size: 12px">
            注：IOPV来自实时行情采集；历史范围仅覆盖系统开始采集后的数据。
          </div>
          <div v-else class="td-muted" style="margin-top: 8px; font-size: 12px">
            注：债券/国债逆回购行情来自实时行情采集；价格字段按行情源返回值展示，不作为基金净值使用。
          </div>
        </el-tab-pane>

        <!-- 场内：K线图 -->
        <el-tab-pane v-if="isExchange" label="K线图" name="kline">
          <div id="klineChart" style="width: 100%; height: 400px"></div>
        </el-tab-pane>

        <!-- 技术指标：场内/场外都可展示（场外基于净值派生） -->
        <el-tab-pane label="技术指标" name="indicator">
          <div style="margin-bottom: 16px">
            <label style="margin-right: 8px">窗口天数：</label>
            <el-select v-model="windowDays" style="width: 120px" @change="loadActiveTab">
              <el-option label="20日" :value="20" />
              <el-option label="60日" :value="60" />
            </el-select>
          </div>
          <div v-if="latestIndicator" class="indicator-summary">
            <div class="indicator-summary-group">
              <div class="indicator-summary-title">BOLL（{{ latestIndicator.bollWindow || windowDays }}日）</div>
              <div class="indicator-summary-grid">
                <div>
                  <span class="td-muted">中轨</span>
                  <strong>{{ formatIndicatorValue(latestIndicator.bollMiddle) }}</strong>
                </div>
                <div>
                  <span class="td-muted">上轨</span>
                  <strong>{{ formatIndicatorValue(latestIndicator.bollUpper) }}</strong>
                </div>
                <div>
                  <span class="td-muted">下轨</span>
                  <strong>{{ formatIndicatorValue(latestIndicator.bollLower) }}</strong>
                </div>
                <div>
                  <span class="td-muted">标准差</span>
                  <strong>{{ formatIndicatorValue(latestIndicator.bollStd) }}</strong>
                </div>
              </div>
            </div>
            <div class="indicator-summary-group">
              <div class="indicator-summary-title">KDJ（{{ latestIndicator.kdjWindow || 9 }}日）</div>
              <div class="indicator-summary-grid">
                <div>
                  <span class="td-muted">K</span>
                  <strong>{{ formatIndicatorValue(latestIndicator.kdjK) }}</strong>
                </div>
                <div>
                  <span class="td-muted">D</span>
                  <strong>{{ formatIndicatorValue(latestIndicator.kdjD) }}</strong>
                </div>
                <div>
                  <span class="td-muted">J</span>
                  <strong>{{ formatIndicatorValue(latestIndicator.kdjJ) }}</strong>
                </div>
                <div>
                  <span class="td-muted">RSV</span>
                  <strong>{{ formatIndicatorValue(latestIndicator.kdjRsv) }}</strong>
                </div>
              </div>
            </div>
          </div>
          <div id="indicatorChart" style="width: 100%; height: 400px"></div>
        </el-tab-pane>

        <!-- 交易记录：显示该产品的持仓变化历史 -->
        <el-tab-pane label="交易记录" name="transactions">
          <p class="td-muted">当前访问范围该产品最近100条交易，未按来源账户拆分；不作为已实现盈亏。</p>
          <div v-if="transactionLoading" style="text-align: center; padding: 40px">
            <span class="td-muted">加载中...</span>
          </div>
          <div v-else-if="!detailError && transactionHistory.length === 0" style="text-align: center; padding: 40px">
            <span class="td-muted">暂无交易记录</span>
          </div>
          <table v-else-if="transactionHistory.length > 0" class="txn-table">
            <thead>
              <tr>
                <th>交易时间</th>
                <th style="text-align: center">类型</th>
                <th style="text-align: right">金额</th>
                <th style="text-align: right">份额</th>
                <th>备注</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(txn, index) in transactionHistory" :key="txn.txnId" :class="{ 'row-even': index % 2 === 0 }">
                <td class="mono">{{ formatDate(txn.requestedAt) }}</td>
                <td style="text-align: center">
                  <span class="txn-type-tag" :class="getTxnTypeClass(txn.txnType)">
                    {{ formatTxnType(txn.txnType) }}
                  </span>
                </td>
                <td style="text-align: right" class="mono">
                  {{ (txn as any).summaryAmount == null ? '未知' : formatAmountWithSign(txn.txnType, (txn as any).summaryAmount) }}
                </td>
                <td style="text-align: right" class="mono">{{ displayNumber((txn as any).summaryShares, 2) }}</td>
                <td class="note-cell">{{ txn.note || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </el-tab-pane>
      </el-tabs>
    </div>
    <div v-else style="text-align: center; padding: 40px" class="td-muted">加载中...</div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { navApi, marketApi, indicatorApi, ledgerApi, useUserStore, formatCurrency, formatNumber } from '@wealth-hub/shared'
import type { Nav, MarketBarDaily, IndicatorDaily, LedgerTxn } from '@wealth-hub/shared'

import type { HoldingView } from '../views/holdingsViewModel'
import { numberOrNull, pnlTone } from '../views/holdingsViewModel'
import { createReadGate, readError } from '../views/managementViewModel'

const props = defineProps<{
  modelValue: boolean
  holding: HoldingView | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const activeTab = ref('nav')
const windowDays = ref(20)

let navChart: echarts.ECharts | null = null
let klineChart: echarts.ECharts | null = null
let indicatorChart: echarts.ECharts | null = null
let iopvChart: echarts.ECharts | null = null

const navData = ref<Nav[]>([])
const klineData = ref<MarketBarDaily[]>([])
const indicatorData = ref<IndicatorDaily[]>([])
const quoteHistory = ref<any[]>([])
const transactionHistory = ref<LedgerTxn[]>([])
const transactionLoading = ref(false)

const isExchange = computed(() => props.holding?.channel === 'EXCHANGE')
const isOtc = computed(() => props.holding?.channel === 'OTC')
const isBondRepo = computed(() => props.holding?.assetType === 'BOND_REPO')
const latestIndicator = computed(() => indicatorData.value.length > 0 ? indicatorData.value[indicatorData.value.length - 1] : null)
const latestQuote = computed(() => quoteHistory.value.length > 0 ? quoteHistory.value[quoteHistory.value.length - 1] : null)

const user = useUserStore()
const identity = () => JSON.stringify([user.token, user.user?.id, user.user?.familyId, props.holding?.productId, props.holding?.brokerAccountId, visible.value, activeTab.value, windowDays.value])
const gate = createReadGate(identity)
const detailError = ref(''), detailLoading = ref(false)
const displayNumber = (value: unknown, digits: number) => numberOrNull(value) === null ? '未知' : formatNumber(value as number, digits)
const displayMoney = (value: number | null) => value === null ? '未知' : formatCurrency(value)

watch(() => [visible.value, props.holding?.productId, props.holding?.brokerAccountId], () => {
  handleClose()
  activeTab.value = props.holding?.channel === 'EXCHANGE' ? 'iopv' : 'nav'
}, { flush: 'sync' })
watch(identity, () => { void loadActiveTab() }, { immediate: true, flush: 'sync' })
async function loadActiveTab() {
  handleClose()
  navData.value = []; klineData.value = []; indicatorData.value = []; quoteHistory.value = []; transactionHistory.value = []
  detailError.value = ''; detailLoading.value = false; transactionLoading.value = false
  if (!visible.value || !props.holding || !gate.active) return
  const current = gate.begin()
  detailLoading.value = true
  await nextTick()
  if (!current()) return
  // The request and its chart render belong to the same identity/product/tab.
  try {
    if (activeTab.value === 'nav') { await loadNavData(current); if (current()) renderNavChart() }
    else if (activeTab.value === 'iopv') { await loadIopvData(current); if (current()) renderIopvChart() }
    else if (activeTab.value === 'kline') { await loadKlineData(current); if (current()) renderKlineChart() }
    else if (activeTab.value === 'indicator') { await loadIndicators(current); if (current()) renderIndicatorChart() }
    else if (activeTab.value === 'transactions') await loadTransactionHistory(current)
    if (current() && ![navData.value, klineData.value, indicatorData.value, quoteHistory.value, transactionHistory.value].some(data => data.length)) detailError.value = '当前范围暂无详情证据；不表示行情或收益为零。'
  } catch (error) { if (current()) detailError.value = readError(error, '详情证据') }
  finally { if (current()) { detailLoading.value = false; transactionLoading.value = false } }
}

async function loadNavData(current: () => boolean) {
  if (!props.holding) return
  const endDate = new Date().toISOString().split('T')[0]
  const startDate = new Date(Date.now() - 90 * 86400000).toISOString().split('T')[0]
  const data = await navApi.getHistoryNav(props.holding.productId, startDate, endDate)
  if (current()) navData.value = data.slice().reverse()
}
async function loadKlineData(current: () => boolean) {
  if (!props.holding) return
  const endDate = new Date().toISOString().split('T')[0]
  const startDate = new Date(Date.now() - 90 * 86400000).toISOString().split('T')[0]
  const data = await marketApi.getHistoryBars(props.holding.productId, startDate, endDate)
  if (current()) klineData.value = data.slice().reverse()
}
async function loadIopvData(current: () => boolean) {
  if (!props.holding) return
  const data = await marketApi.getQuoteHistory(props.holding.productId, new Date(Date.now() - 7 * 86400000).toISOString(), new Date().toISOString())
  if (current()) quoteHistory.value = data
}
async function loadIndicators(current: () => boolean) {
  if (!props.holding) return
  const endDate = new Date().toISOString().split('T')[0]
  const startDate = new Date(Date.now() - 90 * 86400000).toISOString().split('T')[0]
  const data = await indicatorApi.getHistoryIndicators(props.holding.productId, startDate, endDate, windowDays.value)
  if (current()) indicatorData.value = data.slice().reverse()
}
async function loadTransactionHistory(current: () => boolean) {
  if (!props.holding) return
  transactionLoading.value = true
  const response = await ledgerApi.getTransactions({ productId: props.holding.productId, pageSize: 100 })
  if (current()) transactionHistory.value = response.list || []
}

function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

function formatTxnType(type: string): string {
  const typeMap: Record<string, string> = {
    BUY: '买入',
    SELL: '卖出',
    SUBSCRIPTION: '申购',
    REDEMPTION: '赎回',
    TRANSFER_OUT: '转出',
    TRANSFER_IN: '转入',
    INCOME: '收入',
    EXPENSE: '支出',
    BOND_REPO: '逆回购',
    CUSTODY_TRANSFER: '转托管',
    ADJUST: '调整',
  }
  return typeMap[type] || type
}

function formatIndicatorValue(value?: number): string {
  return value == null ? '-' : formatNumber(value, 4)
}

function formatRatioPercent(value?: number | null): string {
  if (value == null) return '-'
  return `${value >= 0 ? '+' : ''}${formatNumber(value * 100, 2)}%`
}

/**
 * 格式化金额，根据交易类型添加正负号
 * 买入/申购应该显示为正数（表示投入的金额）
 */
function formatAmountWithSign(txnType: string, amount: number): string {
  if (amount === 0) {
    return formatCurrency(0)
  }
  
  // 买入/申购/调整（初始持仓）：显示为正数（投入金额）
  if (['BUY', 'SUBSCRIPTION', 'ADJUST'].includes(txnType)) {
    return formatCurrency(Math.abs(amount))
  }
  
  // 卖出/赎回：显示为正数（到账金额）
  if (['SELL', 'REDEMPTION'].includes(txnType)) {
    return formatCurrency(Math.abs(amount))
  }
  
  // 其他类型：保持原样
  return formatCurrency(amount)
}

function getTxnTypeClass(type: string): string {
  if (['BUY', 'SUBSCRIPTION'].includes(type)) return 'type-buy'
  if (['SELL', 'REDEMPTION'].includes(type)) return 'type-sell'
  if (['REDEMPTION_OUT'].includes(type)) return 'type-sell-out'
  if (['REDEMPTION_IN'].includes(type)) return 'type-sell-in'
  if (['TRANSFER_OUT'].includes(type)) return 'type-transfer-out'
  if (['TRANSFER_IN'].includes(type)) return 'type-transfer-in'
  if (['INCOME', 'DIVIDEND_CASH', 'DIVIDEND_REINVEST'].includes(type)) return 'type-income'
  if (['EXPENSE'].includes(type)) return 'type-expense'
  return 'type-default'
}

function renderNavChart() {
  if (!navChart) {
    const chartDom = document.getElementById('navChart')
    if (!chartDom) return
    navChart = echarts.init(chartDom)
  }

  const dates = navData.value.map(n => n.navDate)
  const navs = navData.value.map(n => n.nav)

  navChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const param = params[0]
        return `${param.axisValue}<br/>${param.seriesName}: ${formatNumber(param.value, 4)}`
      }
    },
    xAxis: {
      type: 'category',
      data: dates,
    },
    yAxis: {
      type: 'value',
      scale: true,
    },
    series: [
      {
        name: '净值',
        type: 'line',
        data: navs,
        smooth: true,
        itemStyle: { color: '#409EFF' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
              { offset: 1, color: 'rgba(64, 158, 255, 0.1)' }
            ]
          }
        }
      }
    ]
  })
}

function renderIopvChart() {
  if (!iopvChart) {
    const chartDom = document.getElementById('iopvChart')
    if (!chartDom) return
    iopvChart = echarts.init(chartDom)
  }

  const dates = quoteHistory.value.map((q: any) => q.quoteTime)
  const price = quoteHistory.value.map((q: any) => q.price ?? null)
  const iopv = quoteHistory.value.map((q: any) => q.iopv ?? null)
  const premium = quoteHistory.value.map((q: any) => q.premiumRate ?? null)
  const pctChg = quoteHistory.value.map((q: any) => q.pctChg != null ? q.pctChg * 100 : null)

  if (isBondRepo.value) {
    iopvChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['最新价/年化收益率', '涨跌幅%'] },
      xAxis: { type: 'category', data: dates },
      yAxis: [
        { type: 'value', name: '价格/收益率', scale: true },
        { type: 'value', name: '涨跌幅%', scale: true, position: 'right' },
      ],
      series: [
        { name: '最新价/年化收益率', type: 'line', data: price, smooth: true },
        { name: '涨跌幅%', type: 'line', yAxisIndex: 1, data: pctChg, smooth: true },
      ],
    })
    return
  }

  iopvChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['价格', 'IOPV', '溢价率'] },
    xAxis: { type: 'category', data: dates },
    yAxis: [
      { type: 'value', name: '价格', scale: true },
      { type: 'value', name: '溢价率', scale: true, position: 'right' },
    ],
    series: [
      { name: '价格', type: 'line', data: price, smooth: true },
      { name: 'IOPV', type: 'line', data: iopv, smooth: true },
      { name: '溢价率', type: 'line', yAxisIndex: 1, data: premium, smooth: true },
    ],
  })
}

function renderKlineChart() {
  if (!klineChart) {
    const chartDom = document.getElementById('klineChart')
    if (!chartDom) return
    klineChart = echarts.init(chartDom)
  }

  const dates = klineData.value.map(k => k.tradeDate)
  const volumes = klineData.value.map(k => k.volume || 0)
  const ohlc = klineData.value.map(k => [k.openPrice || 0, k.closePrice, k.lowPrice || 0, k.highPrice || 0])

  klineChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: {
      data: ['K线', '成交量']
    },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
    },
    yAxis: [
      {
        type: 'value',
        scale: true,
        name: '价格',
      },
      {
        type: 'value',
        scale: true,
        name: '成交量',
        position: 'right',
      }
    ],
    series: [
      {
        name: 'K线',
        type: 'candlestick',
        data: ohlc,
        itemStyle: {
          color: '#26a69a',
          color0: '#ef5350',
          borderColor: '#26a69a',
          borderColor0: '#ef5350'
        }
      },
      {
        name: '成交量',
        type: 'bar',
        yAxisIndex: 1,
        data: volumes,
        itemStyle: { color: '#90caf9' }
      }
    ]
  })
}

function renderIndicatorChart() {
  if (!indicatorChart) {
    const chartDom = document.getElementById('indicatorChart')
    if (!chartDom) return
    indicatorChart = echarts.init(chartDom)
  }

  const dates = indicatorData.value.map(i => i.tradeDate)
  const ma20 = indicatorData.value.map(i => i.ma20 ?? null)
  const ma60 = indicatorData.value.map(i => i.ma60 ?? null)
  const bollMiddle = indicatorData.value.map(i => i.bollMiddle ?? null)
  const bollUpper = indicatorData.value.map(i => i.bollUpper ?? null)
  const bollLower = indicatorData.value.map(i => i.bollLower ?? null)
  const pctRank = indicatorData.value.map(i => i.pctRank != null ? i.pctRank * 100 : null)
  const kdjK = indicatorData.value.map(i => i.kdjK ?? null)
  const kdjD = indicatorData.value.map(i => i.kdjD ?? null)
  const kdjJ = indicatorData.value.map(i => i.kdjJ ?? null)

  indicatorChart.setOption({
    tooltip: {
      trigger: 'axis',
    },
    legend: {
      data: ['MA20', 'MA60', 'BOLL中轨', 'BOLL上轨', 'BOLL下轨', '分位%', 'K', 'D', 'J']
    },
    xAxis: {
      type: 'category',
      data: dates,
    },
    yAxis: [
      {
        type: 'value',
        name: '价格',
        scale: true,
      },
      {
        type: 'value',
        name: '分位/KDJ',
        min: 0,
        max: 100,
        position: 'right',
      }
    ],
    series: [
      {
        name: 'MA20',
        type: 'line',
        data: ma20,
        smooth: true,
        itemStyle: { color: '#409EFF' }
      },
      {
        name: 'MA60',
        type: 'line',
        data: ma60,
        smooth: true,
        itemStyle: { color: '#67C23A' }
      },
      {
        name: 'BOLL中轨',
        type: 'line',
        data: bollMiddle,
        smooth: true,
        symbol: 'none',
        itemStyle: { color: '#909399' },
        lineStyle: { type: 'dashed' }
      },
      {
        name: 'BOLL上轨',
        type: 'line',
        data: bollUpper,
        smooth: true,
        symbol: 'none',
        itemStyle: { color: '#F56C6C' },
        lineStyle: { type: 'dotted' }
      },
      {
        name: 'BOLL下轨',
        type: 'line',
        data: bollLower,
        smooth: true,
        symbol: 'none',
        itemStyle: { color: '#67C23A' },
        lineStyle: { type: 'dotted' }
      },
      {
        name: '分位%',
        type: 'line',
        yAxisIndex: 1,
        data: pctRank,
        smooth: true,
        itemStyle: { color: '#E6A23C' }
      },
      {
        name: 'K',
        type: 'line',
        yAxisIndex: 1,
        data: kdjK,
        smooth: true,
        symbol: 'none',
        itemStyle: { color: '#9B59B6' }
      },
      {
        name: 'D',
        type: 'line',
        yAxisIndex: 1,
        data: kdjD,
        smooth: true,
        symbol: 'none',
        itemStyle: { color: '#16A085' }
      },
      {
        name: 'J',
        type: 'line',
        yAxisIndex: 1,
        data: kdjJ,
        smooth: true,
        symbol: 'none',
        itemStyle: { color: '#D35400' }
      }
    ]
  })
}

function handleClose() {
  gate.invalidate()
  navData.value = []; klineData.value = []; indicatorData.value = []; quoteHistory.value = []; transactionHistory.value = []
  detailError.value = ''; detailLoading.value = false; transactionLoading.value = false
  navChart?.dispose()
  klineChart?.dispose()
  indicatorChart?.dispose()
  iopvChart?.dispose()
  navChart = null
  klineChart = null
  indicatorChart = null
  iopvChart = null
}

function resizeCharts() { navChart?.resize(); klineChart?.resize(); indicatorChart?.resize(); iopvChart?.resize() }
window.addEventListener('resize', resizeCharts)
onUnmounted(() => { handleClose(); gate.suspend(); window.removeEventListener('resize', resizeCharts) })
</script>

<style scoped>
.holdings-detail .card { border-radius: var(--wh-card-radius, 20px); }
.holdings-detail .row { flex-wrap: wrap; }
.detail-cost { display: flex; flex-wrap: wrap; gap: 24px; padding: 16px; background: var(--primarySoft); border-radius: 12px; }
.detail-error { color: var(--bad); }
.holdings-detail .pnl-profit { color: var(--good); }
.holdings-detail .pnl-loss { color: var(--bad); }
.holdings-detail .pnl-neutral { color: var(--muted); }
.holdings-detail .indicator-summary-title, .holdings-detail .indicator-summary-grid strong { color: var(--text); }

/* 交易记录表格样式 */
.txn-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  border-radius: 8px;
  overflow: hidden;
  font-size: 14px;
}

.txn-table thead th {
  background: linear-gradient(135deg, rgba(78, 164, 255, 0.12), rgba(124, 199, 255, 0.08));
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 600;
  text-align: left;
  border-bottom: 2px solid rgba(78, 164, 255, 0.2);
  white-space: nowrap;
}

.txn-table tbody td {
  padding: 12px 16px;
  border-bottom: 1px solid rgba(230, 238, 247, 0.6);
  vertical-align: middle;
}

.txn-table tbody tr.row-even {
  background: rgba(78, 164, 255, 0.04);
}

.txn-table tbody tr:hover {
  background: rgba(78, 164, 255, 0.1);
}

.txn-table tbody tr:last-child td {
  border-bottom: none;
}

.note-cell {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #909399;
}

.indicator-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.indicator-summary-group {
  padding: 12px;
  border: 1px solid rgba(78, 164, 255, 0.16);
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(78, 164, 255, 0.08), rgba(124, 199, 255, 0.04));
}

.indicator-summary-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.indicator-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.indicator-summary-grid div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.indicator-summary-grid strong {
  font-family: 'JetBrains Mono', 'Fira Code', 'SF Mono', monospace;
  font-size: 13px;
  color: #0f172a;
}

.bond-quote-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
  padding: 12px;
  border: 1px solid rgba(230, 162, 60, 0.2);
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(230, 162, 60, 0.1), rgba(245, 158, 11, 0.04));
}

.bond-quote-summary div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.bond-quote-summary strong {
  font-family: 'JetBrains Mono', 'Fira Code', 'SF Mono', monospace;
  font-size: 14px;
  color: #92400e;
}

@media (max-width: 768px) {
  .indicator-summary {
    grid-template-columns: 1fr;
  }

  .indicator-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .bond-quote-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

/* 交易类型标签样式 */
.txn-type-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.type-buy {
  background: rgba(59, 130, 246, 0.15);
  color: #3b82f6;
}

.type-sell {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
}

.type-sell-out {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
}

.type-sell-in {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
}

.type-transfer-out {
  background: rgba(100, 116, 139, 0.15);
  color: #64748b;
}

.type-transfer-in {
  background: rgba(100, 116, 139, 0.15);
  color: #64748b;
}

.type-income {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
}

.type-expense {
  background: rgba(22, 163, 74, 0.15);
  color: #16a34a;
}

.type-default {
  background: rgba(100, 116, 139, 0.1);
  color: #64748b;
}

.mono {
  font-family: 'JetBrains Mono', 'Fira Code', 'SF Mono', monospace;
}

.text-red {
  color: #ef4444;
}

.text-green {
  color: #16a34a;
}
</style>
