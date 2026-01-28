<template>
  <el-dialog v-model="visible" :title="`持仓详情 - ${holding?.productName || `产品${holding?.productId}`}`" width="1200px" @close="handleClose">
    <div v-if="holding" style="max-height: 70vh; overflow-y: auto">
      <!-- 持仓基本信息 -->
      <div class="card" style="margin-bottom: 16px">
        <h4>持仓信息</h4>
        <div class="row" style="gap: 24px; margin-top: 12px">
          <div>
            <div class="td-muted" style="font-size: 12px">持仓份额</div>
            <div style="font-size: 18px; font-weight: 600">{{ formatNumber(holding.totalShares || holding.shares || 0, 2) }}</div>
          </div>
          <div>
            <div class="td-muted" style="font-size: 12px">平均成本</div>
            <div style="font-size: 18px; font-weight: 600">{{ formatNumber(holding.averageCost || holding.avgCost || 0, 4) }}</div>
          </div>
          <div>
            <div class="td-muted" style="font-size: 12px">当前价格</div>
            <div style="font-size: 18px; font-weight: 600">{{ formatNumber(holding.currentPrice || 0, 4) }}</div>
          </div>
          <div>
            <div class="td-muted" style="font-size: 12px">持仓市值</div>
            <div style="font-size: 18px; font-weight: 600">{{ formatCurrency(holding.marketValue || 0) }}</div>
          </div>
          <div>
            <div class="td-muted" style="font-size: 12px">浮动盈亏</div>
            <div style="font-size: 18px; font-weight: 600" :class="(holding.unrealizedPnl || 0) >= 0 ? 'text-red' : 'text-green'">
              {{ (holding.unrealizedPnl || 0) >= 0 ? '+' : '' }}{{ formatCurrency(holding.unrealizedPnl || 0) }}
            </div>
          </div>
        </div>
      </div>

      <!-- 图表标签页（按场内/场外差异化展示） -->
      <el-tabs v-model="activeTab">
        <!-- 场外：净值曲线 -->
        <el-tab-pane v-if="isOtc" label="净值曲线" name="nav">
          <div id="navChart" style="width: 100%; height: 400px"></div>
        </el-tab-pane>

        <!-- 场内：IOPV/估值与溢价 -->
        <el-tab-pane v-if="isExchange" label="IOPV/估值" name="iopv">
          <div id="iopvChart" style="width: 100%; height: 400px"></div>
          <div class="td-muted" style="margin-top: 8px; font-size: 12px">
            注：IOPV来自实时行情采集；历史范围仅覆盖系统开始采集后的数据。
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
            <el-select v-model="windowDays" style="width: 120px" @change="loadIndicators">
              <el-option label="20日" :value="20" />
              <el-option label="60日" :value="60" />
            </el-select>
          </div>
          <div id="indicatorChart" style="width: 100%; height: 400px"></div>
        </el-tab-pane>

        <!-- 交易记录：显示该产品的持仓变化历史 -->
        <el-tab-pane label="交易记录" name="transactions">
          <div v-if="transactionLoading" style="text-align: center; padding: 40px">
            <span class="td-muted">加载中...</span>
          </div>
          <div v-else-if="transactionHistory.length === 0" style="text-align: center; padding: 40px">
            <span class="td-muted">暂无交易记录</span>
          </div>
          <table v-else class="txn-table">
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
                <td style="text-align: right" class="mono">{{ formatCurrency((txn as any).summaryAmount || 0) }}</td>
                <td style="text-align: right" class="mono">{{ formatNumber((txn as any).summaryShares || 0, 2) }}</td>
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
import { navApi, marketApi, indicatorApi, ledgerApi, formatCurrency, formatNumber } from '@wealth-hub/shared'
import type { Nav, MarketBarDaily, IndicatorDaily, LedgerTxn } from '@wealth-hub/shared'

interface HoldingWithQuote {
  productId: number
  productName?: string
  channel?: 'EXCHANGE' | 'OTC'
  assetType?: string
  totalShares?: number
  shares?: number
  averageCost?: number
  avgCost?: number
  currentPrice?: number
  marketValue?: number
  unrealizedPnl?: number
}

const props = defineProps<{
  modelValue: boolean
  holding: HoldingWithQuote | null
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

watch([visible, activeTab], async ([newVisible, newTab]) => {
  if (newVisible && props.holding) {
    await nextTick()
    if (newTab === 'nav') {
      await loadNavData()
      renderNavChart()
    } else if (newTab === 'iopv') {
      await loadIopvData()
      renderIopvChart()
    } else if (newTab === 'kline') {
      await loadKlineData()
      renderKlineChart()
    } else if (newTab === 'indicator') {
      await loadIndicators()
      renderIndicatorChart()
    } else if (newTab === 'transactions') {
      await loadTransactionHistory()
    }
  }
}, { immediate: true })

watch(visible, (v) => {
  if (v && props.holding) {
    activeTab.value = props.holding.channel === 'EXCHANGE' ? 'iopv' : 'nav'
  }
})

async function loadNavData() {
  if (!props.holding) return
  try {
    const endDate = new Date().toISOString().split('T')[0]
    const startDate = new Date(Date.now() - 90 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]
    navData.value = await navApi.getHistoryNav(props.holding.productId, startDate, endDate)
    navData.value.reverse() // 按时间正序
  } catch (error: any) {
    console.error('加载净值数据失败:', error)
    navData.value = []
  }
}

async function loadKlineData() {
  if (!props.holding) return
  try {
    const endDate = new Date().toISOString().split('T')[0]
    const startDate = new Date(Date.now() - 90 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]
    klineData.value = await marketApi.getHistoryBars(props.holding.productId, startDate, endDate)
    klineData.value.reverse() // 按时间正序
  } catch (error: any) {
    console.error('加载K线数据失败:', error)
    klineData.value = []
  }
}

async function loadIopvData() {
  if (!props.holding) return
  try {
    const end = new Date()
    const start = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000)
    quoteHistory.value = await marketApi.getQuoteHistory(
      props.holding.productId,
      start.toISOString(),
      end.toISOString()
    )
  } catch (error: any) {
    console.error('加载IOPV/估值数据失败:', error)
    quoteHistory.value = []
  }
}

async function loadIndicators() {
  if (!props.holding) return
  try {
    const endDate = new Date().toISOString().split('T')[0]
    const startDate = new Date(Date.now() - 90 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]
    indicatorData.value = await indicatorApi.getHistoryIndicators(
      props.holding.productId,
      startDate,
      endDate,
      windowDays.value
    )
    indicatorData.value.reverse() // 按时间正序
    await nextTick()
    renderIndicatorChart()
  } catch (error: any) {
    console.error('加载指标数据失败:', error)
    indicatorData.value = []
  }
}

async function loadTransactionHistory() {
  if (!props.holding) return
  transactionLoading.value = true
  try {
    const response = await ledgerApi.getTransactions({
      productId: props.holding.productId,
      pageSize: 100,
    })
    transactionHistory.value = response.list || []
  } catch (error: any) {
    console.error('加载交易记录失败:', error)
    transactionHistory.value = []
  } finally {
    transactionLoading.value = false
  }
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

function getTxnTypeColor(type: string): string {
  if (['BUY', 'SUBSCRIPTION', 'TRANSFER_IN', 'INCOME'].includes(type)) {
    return '#ef4444' // 红色 - 买入/收入
  } else if (['SELL', 'REDEMPTION', 'TRANSFER_OUT', 'EXPENSE'].includes(type)) {
    return '#16a34a' // 绿色 - 卖出/支出
  }
  return '#64748b'
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
  const ma20 = indicatorData.value.map(i => i.ma20 || null)
  const ma60 = indicatorData.value.map(i => i.ma60 || null)
  const pctRank = indicatorData.value.map(i => i.pctRank || null)

  indicatorChart.setOption({
    tooltip: {
      trigger: 'axis',
    },
    legend: {
      data: ['MA20', 'MA60', '分位']
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
        name: '分位',
        min: 0,
        max: 1,
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
        name: '分位',
        type: 'line',
        yAxisIndex: 1,
        data: pctRank,
        smooth: true,
        itemStyle: { color: '#E6A23C' }
      }
    ]
  })
}

function handleClose() {
  navChart?.dispose()
  klineChart?.dispose()
  indicatorChart?.dispose()
  iopvChart?.dispose()
  navChart = null
  klineChart = null
  indicatorChart = null
  iopvChart = null
}

onUnmounted(() => {
  handleClose()
})

// 监听窗口大小变化
if (typeof window !== 'undefined') {
  window.addEventListener('resize', () => {
    navChart?.resize()
    klineChart?.resize()
    indicatorChart?.resize()
  })
}
</script>

<style scoped>
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
