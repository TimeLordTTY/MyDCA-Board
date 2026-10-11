<template>
  <div class="products-page-container management-page holdings-page">
    <HoldingDetailModal v-model="detailVisible" :holding="selectedHolding" />
    <InitialHoldingImportModal v-if="importDialogVisible" v-model="importDialogVisible" @success="loadHoldings" />
    <header class="management-hero">
      <div><span class="management-eyebrow">HOLDINGS / 投资观察</span><h1>看清持仓，也看清收益的来处。</h1><p>来源账户、持仓成本与净值证据放在一起。买入 / 卖出仍通过原有订单与人工结算流程完成。</p></div>
      <div class="row-gap"><el-button :loading="loading" @click="loadHoldings">重新读取</el-button><el-button type="primary" @click="importDialogVisible = true">导入初始持仓</el-button></div>
    </header>
    <div class="holdings-glance" aria-live="polite">
      <article><span>筛选后持仓</span><strong>{{ ready ? visibleHoldings.length : '—' }}</strong><small>按来源账户分别展示</small></article>
      <article><span>估值待补全</span><strong>{{ ready ? visibleHoldings.filter(h => h.marketValue === null).length : '—' }}</strong><small>未知金额不纳入资产判断</small></article>
      <article><span>已实现盈亏</span><strong class="holdings-label">暂未提供</strong><small>当前接口未返回，不从买卖记录推算</small></article>
    </div>
    <div class="management-guide"><span><b>浮动盈亏</b> · 服务器市值与剩余成本的差额，不等于已实现收益</span><span><b>价格时间 ≠ 估值时间</b> · 实时报价与净值分别标注，历史日期不代表今日行情</span><span><b>读取时间</b> · {{ loadedAt || '尚未读取' }}；金额币种沿用原接口口径</span></div>
    <section class="card holdings-content" aria-label="持仓明细" :aria-busy="loading">
      <div class="management-filter">
        <el-input v-model="filters.keyword" aria-label="搜索持仓名称或代码" placeholder="搜索名称 / 代码" clearable />
        <el-select v-model="filters.broker" aria-label="筛选来源账户" placeholder="全部来源账户" clearable><el-option v-for="b in brokerOptions" :key="b.id" :label="b.name" :value="b.id" /></el-select>
        <el-select v-model="filters.channel" aria-label="筛选交易渠道" placeholder="全部渠道" clearable><el-option label="场内" value="EXCHANGE" /><el-option label="场外" value="OTC" /></el-select>
        <el-select v-model="filters.assetType" aria-label="筛选产品类型" placeholder="全部类型" clearable><el-option v-for="type in assetTypes" :key="type" :label="type" :value="type" /></el-select>
        <el-checkbox v-model="filters.showCleared">显示已清仓</el-checkbox>
        <el-button @click="resetFilters">重置筛选</el-button>
      </div>
      <p v-if="filters.productId" class="td-muted">仅查看产品 #{{ filters.productId }} 的来源持仓。<button class="management-link" @click="clearProductFilter">查看全部产品</button></p>
      <div v-if="loadError" class="management-state" role="alert">{{ loadError }} <el-button @click="loadHoldings">重试</el-button></div>
      <div v-else-if="loading" class="management-state" role="status">正在读取持仓与价格证据…</div>
      <div v-else-if="!ready" class="management-state">访问身份已变化，请重新读取当前持仓。</div>
      <template v-else>
        <p v-if="evidenceError" role="alert" class="holdings-warning">{{ evidenceError }} <el-button @click="loadHoldings">重新读取</el-button></p>
        <div v-if="!visibleHoldings.length" class="management-state" role="status">{{ holdings.length ? '没有符合筛选条件的持仓，可重置筛选或显示已清仓。' : '当前访问范围暂无持仓。初始持仓可通过原导入流程录入。' }}</div>
        <div v-else class="holdings-table-wrap">
          <table class="holdings-table"><caption class="td-muted">持仓金额由服务器提供；未知与真实零分开展示，盈亏保留正负号。</caption>
            <thead><tr><th>产品 / 来源</th><th class="right">份额 / 市值</th><th class="right">剩余成本 / 平均成本</th><th>报价或净值 / 时间</th><th class="right">浮动盈亏</th><th>查看</th></tr></thead>
            <tbody><tr v-for="holding in visibleHoldings" :key="`${holding.brokerAccountId ?? 'unknown'}:${holding.productId}`">
              <td><b>{{ holding.productName || `产品${holding.productId}` }}</b><small>{{ holding.productCode || '代码未提供' }} · {{ holding.channel === 'EXCHANGE' ? '场内' : holding.channel === 'OTC' ? '场外' : '渠道未知' }} · {{ holding.assetType || '类型未知' }}</small><small>{{ holding.brokerAccountName || (holding.brokerAccountId == null ? '来源账户未提供' : `账户${holding.brokerAccountId}`) }}</small></td>
              <td class="right mono">{{ numeric(holding.sharesCalc, 2) }}<small>{{ money(holding.marketValue) }}</small><small class="holdings-quality">{{ holding.quality }}</small></td>
              <td class="right mono">{{ money(holding.totalCostCalc) }}<small>{{ numeric(holding.avgCostCalc, 4) }}</small></td>
              <td class="mono">{{ numeric(holding.currentPrice, 4) }}<small>{{ holding.channel === 'EXCHANGE' ? '报价时间' : '净值日期' }}：{{ holding.priceTime || '未知' }}</small><small>估值净值证据：{{ holding.navDate || '缺失' }}</small><small>{{ holding.priceSource || '价格来源未提供' }}</small></td>
              <td class="right mono" :class="`pnl-${pnlTone(holding.unrealizedPnl)}`">{{ signedMoney(holding.unrealizedPnl) }}<small>{{ pnlRateText(holding) }}</small><small>已实现：暂未提供</small></td>
              <td><button class="management-link" @click="handleViewDetail(holding)" :aria-label="`查看${holding.productName || holding.productId}的持仓详情`">详情</button><br /><button class="management-link" @click="viewProduct(holding)" :aria-label="`查看${holding.productName || holding.productId}的产品档案`">产品档案 ↗</button></td>
            </tr></tbody>
          </table>
        </div>
      </template>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, onActivated, onDeactivated } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { holdingApi, marketApi, navApi, useUserStore, formatCurrency, formatNumber } from '@wealth-hub/shared'
import type { HoldingInfo, MarketQuoteRealtime, Nav } from '@wealth-hub/shared'
import HoldingDetailModal from '../components/HoldingDetailModal.vue'
import InitialHoldingImportModal from '../components/InitialHoldingImportModal.vue'
import { holdingView, normalizeHoldings, filterHoldings, pnlTone } from './holdingsViewModel'
import type { HoldingView } from './holdingsViewModel'
import { createReadGate, readError } from './managementViewModel'

const router = useRouter(), route = useRoute(), user = useUserStore()
const identity = () => JSON.stringify([user.token, user.user?.id, user.user?.familyId])
const gate = createReadGate(identity)
const loading = ref(false), ready = ref(false), loadError = ref(''), evidenceError = ref(''), loadedAt = ref('')
const holdings = ref<HoldingInfo[]>([])
const quotes = ref(new Map<number, MarketQuoteRealtime>()), navs = ref(new Map<number, Nav>())
const detailVisible = ref(false), selectedHolding = ref<HoldingView | null>(null), importDialogVisible = ref(false)
const filters = reactive({ keyword: '', broker: '', assetType: '', channel: '', productId: '', showCleared: false })
watch(() => route.query.productId, value => { filters.productId = typeof value === 'string' ? value : '' }, { immediate: true })
const rows = computed(() => holdings.value.map(h => holdingView(h, quotes.value.get(h.productId), navs.value.get(h.productId))))
const visibleHoldings = computed(() => filterHoldings(rows.value, filters).sort((a, b) =>
  a.unrealizedPnl === null ? b.unrealizedPnl === null ? 0 : 1 : b.unrealizedPnl === null ? -1 : b.unrealizedPnl - a.unrealizedPnl))
const brokerOptions = computed(() => [...new Map(holdings.value.filter(h => h.brokerAccountId != null).map(h => [String(h.brokerAccountId), h.brokerAccountName || `账户${h.brokerAccountId}`])).entries()].map(([id, name]) => ({ id, name })))
const assetTypes = computed(() => [...new Set(holdings.value.map(h => h.assetType).filter((v): v is string => !!v))])
const numeric = (value: number | null, digits: number) => value === null ? '未知' : formatNumber(value, digits)
const money = (value: number | null) => value === null ? '未知' : formatCurrency(value)
const signedMoney = (value: number | null) => value === null ? '未知' : `${value > 0 ? '+' : value < 0 ? '-' : ''}${formatCurrency(Math.abs(value))}`
function pnlRateText(holding: HoldingView) {
  if (holding.unrealizedPnl === null || holding.totalCostCalc === null || holding.totalCostCalc <= 0) return '盈亏率：未知'
  const rate = holding.unrealizedPnl / holding.totalCostCalc * 100
  return `盈亏率：${rate > 0 ? '+' : ''}${formatNumber(rate, 2)}%`
}
function clearData() {
  gate.invalidate(); holdings.value = []; quotes.value = new Map(); navs.value = new Map()
  ready.value = false; loading.value = false; loadedAt.value = ''; loadError.value = ''; evidenceError.value = ''
  detailVisible.value = false; selectedHolding.value = null; importDialogVisible.value = false
}
async function loadHoldings() {
  if (!gate.active) return
  clearData()
  const current = gate.begin()
  loading.value = true
  try {
    const data = normalizeHoldings(await holdingApi.getHoldings())
    if (!current()) return
    const ids = [...new Set(data.map(h => h.productId))]
    const exchangeIds = [...new Set(data.filter(h => h.channel === 'EXCHANGE').map(h => h.productId))]
    // NAV is the actual server valuation source, including exchange holdings. A 404 is missing evidence.
    const [quoteResult, ...navResults] = await Promise.allSettled([
      exchangeIds.length ? marketApi.getRealtimeQuotes(exchangeIds) : Promise.resolve([]),
      ...ids.map(id => navApi.getLatestNav(id)),
    ])
    if (!current()) return
    if (quoteResult.status === 'fulfilled') quotes.value = new Map((quoteResult.value as MarketQuoteRealtime[]).map(q => [q.productId, q]))
    navResults.forEach((result, i) => { if (result.status === 'fulfilled' && result.value) navs.value.set(ids[i], result.value as Nav) })
    if (quoteResult.status === 'rejected' || navResults.some(r => r.status === 'rejected')) evidenceError.value = '部分报价或净值读取失败；受影响的价格或估值标为未知，请重试。'
    holdings.value = data; ready.value = true; loadedAt.value = new Date().toLocaleString('zh-CN')
  } catch (error) { if (current()) loadError.value = readError(error, '持仓') }
  finally { if (current()) loading.value = false }
}
function handleViewDetail(holding: HoldingView) { selectedHolding.value = holding; detailVisible.value = true }
function viewProduct(holding: HoldingView) { router.push({ name: 'Products', query: { productId: holding.productId } }) }
function clearProductFilter() { filters.productId = ''; router.replace({ query: { ...route.query, productId: undefined } }) }
function resetFilters() { Object.assign(filters, { keyword: '', broker: '', assetType: '', channel: '', showCleared: false }); clearProductFilter() }
watch(identity, (_, previous) => {
  clearData()
  if (JSON.parse(previous)[1] == null && user.user?.id && user.token) void loadHoldings()
}, { flush: 'sync' })
onMounted(() => { void loadHoldings(); window.addEventListener('data-refresh', loadHoldings) })
onActivated(() => gate.activate())
onDeactivated(() => { clearData(); gate.suspend() })
onBeforeUnmount(() => { clearData(); gate.suspend(); window.removeEventListener('data-refresh', loadHoldings) })
</script>

<style scoped src="../styles/management-pages.css"></style>
<style scoped>
.holdings-page { gap: 0; }
.holdings-glance { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; margin-bottom: 20px; flex-shrink: 0; }
.holdings-glance article { padding: 22px; border: 1px solid var(--line); border-radius: var(--wh-card-radius, 20px); background: var(--card); }
.holdings-glance span, .holdings-glance small { display: block; color: var(--muted); }
.holdings-glance strong { display: block; font-size: 36px; margin: 10px 0; font-variant-numeric: tabular-nums; }
.holdings-glance .holdings-label { font-size: 24px; line-height: 1.5; }
.holdings-content { flex-shrink: 0; }
.management-filter .el-input, .management-filter .el-select { width: 180px; }
.holdings-table-wrap { overflow: auto; }
.holdings-table { width: 100%; min-width: 1000px; }
.holdings-table caption { text-align: left; padding: 0 0 16px; }
.holdings-table td { vertical-align: top; padding: 18px 12px; }
.holdings-table small { display: block; margin-top: 6px; color: var(--muted); line-height: 1.5; }
.holdings-table .holdings-quality { display: inline-block; padding: 2px 8px; background: var(--primarySoft); border-radius: 6px; }
.holdings-warning { color: var(--bad); }
.holdings-page .holdings-table .pnl-profit { color: var(--good) !important; }
.holdings-page .holdings-table .pnl-loss { color: var(--bad) !important; }
.holdings-page .holdings-table .pnl-neutral { color: var(--muted) !important; }
@media (max-width: 760px) { .holdings-glance { grid-template-columns: 1fr; } }
</style>
