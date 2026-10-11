<template>
  <div class="trade-page">
    <NewOrderModal v-model="newOrderVisible" @success="loadOrders" />
    <SettlementConfirmModal v-model="confirmVisible" :order="settlingOrder" @success="afterSettlement" />
    <header class="trade-hero"><div><span class="trade-eyebrow">ORDER BOOK / 订单中心</span><h1>每笔订单，都有来去。</h1><p>看清状态、资金路径和确认结果，再决定下一步。</p></div><el-button type="primary" :disabled="cancelling" @click="newOrderVisible = true">＋ 新建订单</el-button></header>
    <div class="trade-safety"><strong>人工确认，服务器计算</strong><span>内部订单不代表真实交易成交。买入付款 / 待结算应收与卖出份额占用分别核对。</span><router-link to="/settlements">去待结算 →</router-link></div>
    <div class="trade-glance"><article v-for="item in glance" :key="item.label"><span>{{ item.label }}</span><strong>{{ ready ? item.count : '—' }}</strong><small>{{ item.note }}</small></article></div>
    <section class="card">
      <div class="trade-toolbar"><div class="trade-tabs" role="group" aria-label="订单状态"><button v-for="tab in statusTabs" :key="tab.value" :aria-pressed="statusFilter === tab.value" :class="{ active: statusFilter === tab.value }" @click="statusFilter = tab.value">{{ tab.label }}</button></div><el-button :loading="loading" :disabled="cancelling" @click="loadOrders">刷新订单</el-button></div>
      <div class="trade-filters"><label><span>搜索已读取订单</span><el-input v-model="keyword" placeholder="订单编号、产品或账户" clearable /></label><label><span>订单方向</span><el-select v-model="typeFilter" clearable placeholder="全部方向"><el-option v-for="type in orderTypes" :key="type" :value="type" :label="getOrderTypeLabel(type)" /></el-select></label></div>
      <p class="trade-muted">筛选仅作用于当前接口返回的订单；笔数不代表资产、余额或实际成交。</p>
      <div v-if="loading" class="trade-state" role="status">正在读取订单与资金线…</div>
      <div v-else-if="listError" class="trade-state trade-error" role="alert"><p>{{ listError }}</p><el-button @click="loadOrders">重试读取</el-button></div>
      <div v-else-if="!orders.length" class="trade-state"><h3>还没有订单记录</h3><p>新建订单需要你明确操作，投资草稿确认后也可在这里复核。</p></div>
      <div v-else-if="!filteredOrders.length" class="trade-state"><h3>没有匹配的订单</h3><el-button @click="resetFilters">清除筛选</el-button></div>
      <div v-else class="trade-table-scroll"><table class="trade-table"><thead><tr><th>产品 / 订单</th><th>方向 / 状态</th><th>资金路径</th><th class="right">金额 / 份额</th><th>净值 / 日期</th><th>人工操作</th></tr></thead><tbody>
        <tr v-for="order in filteredOrders" :key="order.orderId">
          <td><b>{{ productName(order.productId) }}</b><small>{{ productCode(order.productId) }} · {{ channelName(order.productId) }}</small><small class="mono">{{ order.orderId }}</small><small v-if="order.detailError" class="trade-error">{{ order.detailError }}</small></td>
          <td><span class="tag blue">{{ getOrderTypeLabel(order.orderType) }}</span><small class="trade-status" :data-status="order.status">{{ getOrderStatusLabel(order.status) }}</small></td>
          <td><small v-for="line in order.fundingLines" :key="line.id">{{ line.lineType === 'TARGET' ? '→ 到账' : '来源' }} {{ accountName(line.accountId) }}</small><span v-if="!order.fundingLines?.length" class="trade-muted">资金线未读取</span></td>
          <td class="right mono">{{ displayNumber(order.settlement?.confirmAmount ?? order.amount) }}<small>{{ displayNumber(order.settlement?.confirmShares ?? order.shares, 6) }} 份</small></td>
          <td class="mono">{{ displayNumber(order.settlement?.confirmNav ?? order.navData?.nav, 6) }}<small>净值日 {{ order.settlement?.navDate || order.expectedNavDate || '未知' }}</small><small>确认日 {{ order.settlement?.confirmDate || order.expectedConfirmDate || '未知' }}</small></td>
          <td><div class="trade-actions"><button class="btn" :disabled="cancelling" @click="handleViewDetail(order)">详情</button><button v-if="order.status === 'PENDING'" class="btn" :disabled="cancelling" @click="handleCancelOrder(order)">取消订单</button><button v-if="canSettle(order)" class="btn primary" :disabled="cancelling" @click="handleSettle(order)">复核结算</button></div></td>
        </tr>
      </tbody></table></div>
      <p v-if="ready" class="trade-muted">当前显示 {{ filteredOrders.length }} / {{ orders.length }} 笔 · {{ loadedAt }} 读取</p>
      <p v-if="optionError" role="alert" class="trade-error">{{ optionError }}</p>
      <p v-if="cancelError" role="alert" class="trade-error">{{ cancelError }}</p>
    </section>
    <el-drawer v-model="detailVisible" title="订单详情 · 资金路径" size="min(720px, 96vw)" @closed="clearDetail">
      <p v-if="detailLoading" role="status">正在读取最新订单…</p><p v-if="detailError" role="alert" class="trade-error">{{ detailError }}</p>
      <template v-if="selectedOrder">
        <div class="trade-review-heading"><span class="trade-eyebrow">{{ getOrderTypeLabel(selectedOrder.orderType) }} / {{ getOrderStatusLabel(selectedOrder.status) }}</span><h2>{{ productName(selectedOrder.productId) }}</h2><p class="mono">{{ selectedOrder.orderId }}</p></div>
        <p class="trade-safety">{{ orderStageText(selectedOrder) }}</p>
        <div class="trade-facts"><div><small>下单金额</small><strong>{{ displayNumber(selectedOrder.amount) }}</strong></div><div><small>下单份额</small><strong>{{ displayNumber(selectedOrder.shares, 6) }}</strong></div><div><small>预计费用</small><strong>{{ displayNumber(selectedOrder.feeEstimate) }}</strong></div></div>
        <dl class="trade-details"><dt>发起时间</dt><dd>{{ formatDateTime(selectedOrder.requestedAt) }}</dd><dt>预计净值日</dt><dd>{{ selectedOrder.expectedNavDate || '未知' }}</dd><dt>预计确认日</dt><dd>{{ selectedOrder.expectedConfirmDate || '未知' }}</dd><dt>备注</dt><dd>{{ selectedOrder.note || '未提供' }}</dd></dl>
        <h3>来源 → 到账</h3><div class="funding-route"><div v-for="line in selectedOrder.fundingLines" :key="line.id"><span>{{ line.lineType === 'TARGET' ? '到账目标' : '来源 / 持仓' }}</span><b>{{ accountName(line.accountId) }}</b><span>{{ line.currency || '币种未知' }} · {{ displayNumber(line.amount) }} / {{ displayNumber(line.shares, 6) }} 份</span></div><p v-if="!selectedOrder.fundingLines.length">没有资金线记录，结算须由服务器核验。</p></div>
        <section v-if="selectedOrder.settlement" class="trade-preview"><h3>服务器结算记录</h3><dl class="trade-details"><dt>确认日期 / 净值日期</dt><dd>{{ selectedOrder.settlement.confirmDate }} / {{ selectedOrder.settlement.navDate }}</dd><dt>确认净值 / 份额</dt><dd>{{ displayNumber(selectedOrder.settlement.confirmNav, 6) }} / {{ displayNumber(selectedOrder.settlement.confirmShares, 6) }}</dd><dt>确认金额 / 手续费</dt><dd>{{ displayNumber(selectedOrder.settlement.confirmAmount) }} / {{ displayNumber(selectedOrder.settlement.confirmFee) }}</dd><dt>关联流水</dt><dd>{{ selectedOrder.settlement.ledgerTxnId || '历史关联缺失，请查看审计' }}</dd></dl><el-button @click="openAudit(selectedOrder.orderId)">查看结算审计</el-button></section>
        <div class="trade-actions"><el-button @click="openHoldings(selectedOrder.productId)">查看关联持仓</el-button><el-button v-if="canSettle(selectedOrder)" type="primary" @click="handleSettle(selectedOrder)">编辑结算输入与预览</el-button></div>
        <p class="trade-muted">订单详情只读。金额 / 份额 / 净值 / 日期 / 费用在结算复核中编辑，最终结果以服务器校验为准。</p>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, onActivated, onDeactivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElNotification } from 'element-plus'
import { orderApi, accountApi, productApi, navApi, useUserStore, getOrderTypeLabel, getOrderStatusLabel, formatDateTime } from '@wealth-hub/shared'
import type { Order, OrderDetail, Account, ProductMaster } from '@wealth-hub/shared'
import NewOrderModal from '../components/NewOrderModal.vue'
import SettlementConfirmModal from '../components/SettlementConfirmModal.vue'
import { createReadGate } from './managementViewModel'
import { displayNumber, localToday, orderTypes, orderStageText, validateOrders, settlementError } from '../components/orderSettlementModel'

type OrderRow = Order & Partial<Pick<OrderDetail, 'fundingLines' | 'settlement'>> & { detailError?: string; navData?: { nav: number } | null }
const route = useRoute(), router = useRouter(), user = useUserStore()
const identity = () => JSON.stringify([user.token, user.user?.id, user.user?.familyId])
const listGate = createReadGate(identity), detailGate = createReadGate(identity), cancelGate = createReadGate(identity)
const orders = ref<OrderRow[]>([]), accounts = ref<Account[]>([]), products = ref<ProductMaster[]>([])
const loading = ref(false), ready = ref(false), listError = ref(''), optionError = ref(''), loadedAt = ref('')
const keyword = ref(''), typeFilter = ref(''), statusFilter = ref('')
const newOrderVisible = ref(false), confirmVisible = ref(false), settlingOrder = ref<Order | null>(null)
const detailVisible = ref(false), selectedOrder = ref<OrderDetail | null>(null), detailLoading = ref(false), detailError = ref('')
const cancelling = ref(false), cancelError = ref('')
let initialReviewPending = true
const statusTabs = [{ value: '', label: '全部订单' }, { value: 'PENDING', label: '待结算' }, { value: 'CONFIRMED', label: '已结算' }, { value: 'CANCELLED', label: '已取消' }, { value: 'FAILED', label: '失败' }]
const productName = (id: number) => products.value.find(p => p.id === id)?.productName || '产品 #' + id
const productCode = (id: number) => products.value.find(p => p.id === id)?.productCode || '代码未提供'
const channelName = (id: number) => products.value.find(p => p.id === id)?.channel || '渠道未提供'
const accountName = (id: number) => accounts.value.find(a => a.id === id)?.accountName || '账户 #' + id
const filteredOrders = computed(() => orders.value.filter(order => (!statusFilter.value || order.status === statusFilter.value)
  && (!typeFilter.value || order.orderType === typeFilter.value)
  && (!keyword.value.trim() || [order.orderId, productName(order.productId), ...(order.fundingLines || []).map(l => accountName(l.accountId))].join(' ').toLocaleLowerCase().includes(keyword.value.trim().toLocaleLowerCase()))))
const glance = computed(() => [{ label: '待人工结算', count: orders.value.filter(o => o.status === 'PENDING').length, note: '继续核对，尚未结算' },
  { label: '已确认结算', count: orders.value.filter(o => o.status === 'CONFIRMED').length, note: '可查看服务器记录与审计' },
  { label: '取消 / 失败', count: orders.value.filter(o => ['CANCELLED', 'FAILED'].includes(o.status)).length, note: '不可继续结算，保留历史' }])
function canSettle(order: Order) { return order.status === 'PENDING' && (!order.expectedConfirmDate || order.expectedConfirmDate <= localToday()) }
function resetFilters() { keyword.value = ''; typeFilter.value = ''; statusFilter.value = '' }
function clearDetail() { detailGate.invalidate(); selectedOrder.value = null; detailLoading.value = false; detailError.value = '' }
async function loadOrders() {
  if (!listGate.active || cancelling.value) return
  const current = listGate.begin()
  confirmVisible.value = false; settlingOrder.value = null; detailVisible.value = false; clearDetail()
  orders.value = []; accounts.value = []; products.value = []; ready.value = false; loading.value = true; listError.value = ''; optionError.value = ''
  try {
    const [rows, accountResult, productResult] = await Promise.all([orderApi.getOrders(), accountApi.getAccounts().then(data => ({ data }), error => ({ error })), productApi.getProducts().then(data => ({ data }), error => ({ error }))])
    if (!current()) return
    validateOrders(rows)
    const hydrated = await Promise.all(rows.map(async row => {
      try {
        const detail = await orderApi.getOrder(row.orderId)
        validateOrders([detail])
        if (detail.orderId !== row.orderId || !Array.isArray(detail.fundingLines)) throw Error('详情或资金线格式异常')
        let navData = null
        if (detail.expectedNavDate && !detail.settlement) {
          try { navData = await navApi.getNavByDate(detail.productId, detail.expectedNavDate) } catch { /* Unknown is displayed without financial estimates. */ }
        }
        return { ...detail, navData }
      } catch (error) { return { ...row, detailError: settlementError(error, '详情读取失败，请重新打开详情') } }
    }))
    if (!current()) return
    accounts.value = 'data' in accountResult ? accountResult.data : []
    products.value = 'data' in productResult ? productResult.data : []
    const errors = [accountResult, productResult].flatMap(r => 'error' in r ? [settlementError(r.error, '名称读取失败，暂显示 ID')] : [])
    optionError.value = errors.join('；'); orders.value = hydrated; ready.value = true; loadedAt.value = new Date().toLocaleString('zh-CN')
    // Wait for user hydration so a late initial identity read cannot erase the deep link.
    if (initialReviewPending && user.user?.id) {
      initialReviewPending = false
      const id = typeof route.query.settle === 'string' ? route.query.settle : route.query.orderId
      const order = orders.value.find(o => o.orderId === id)
      if (order && typeof route.query.settle === 'string') handleSettle(order)
      else if (order) void handleViewDetail(order)
    }
  } catch (error) { if (current()) listError.value = settlementError(error, '订单读取失败，请重试') }
  finally { if (current()) loading.value = false }
}
async function handleViewDetail(order: Order) {
  if (cancelling.value || !detailGate.active) return
  const current = detailGate.begin()
  detailVisible.value = true; detailLoading.value = true; selectedOrder.value = null; detailError.value = ''
  try {
    const detail = await orderApi.getOrder(order.orderId)
    if (!current()) return
    validateOrders([detail])
    if (detail.orderId !== order.orderId || !Array.isArray(detail.fundingLines)) throw Error('服务器详情与当前订单不匹配')
    selectedOrder.value = detail
  } catch (error) { if (current()) detailError.value = settlementError(error, '订单详情读取失败，请重试') }
  finally { if (current()) detailLoading.value = false }
}
function handleSettle(order: Order) {
  if (!canSettle(order) || cancelling.value) return
  settlingOrder.value = order; confirmVisible.value = true
}
function afterSettlement() { void loadOrders() }
function openAudit(id: string) { void router.push({ name: 'Settlements', query: { audit: id } }) }
function openHoldings(id: number) { void router.push({ name: 'Holdings', query: { productId: id } }) }
async function handleCancelOrder(order: Order) {
  if (cancelling.value || order.status !== 'PENDING' || !cancelGate.active) return
  const current = cancelGate.begin()
  cancelling.value = true; cancelError.value = ''
  try {
    try { await ElMessageBox.confirm('取消内部订单 ' + order.orderId + '？资金与份额释放以服务器处理为准。', '明确取消订单', { confirmButtonText: '确认取消订单', cancelButtonText: '保留订单', type: 'warning' }) } catch { return }
    if (!current()) return
    await orderApi.cancelOrder(order.orderId)
    if (current()) { ElNotification.success({ title: '服务器已返回取消成功', message: '请核对最新订单状态。', position: 'bottom-right' }); cancelling.value = false; await loadOrders() }
  } catch (error) { if (current()) cancelError.value = settlementError(error, '取消结果未知，请刷新核对，不自动重试') }
  finally { cancelling.value = false }
}
function clearData() {
  listGate.invalidate(); cancelGate.invalidate(); clearDetail(); orders.value = []; accounts.value = []; products.value = []
  loading.value = false; ready.value = false; detailVisible.value = false; confirmVisible.value = false; newOrderVisible.value = false; settlingOrder.value = null
  listError.value = ''; optionError.value = ''; cancelError.value = ''
}
watch(identity, (_, previous) => { clearData(); if (JSON.parse(previous)[1] == null && user.user?.id && user.token) void loadOrders() }, { flush: 'sync' })
onMounted(async () => {
  await loadOrders()
  if (!listGate.active) return
  window.addEventListener('data-refresh', loadOrders)
})
onActivated(() => { listGate.activate(); detailGate.activate(); cancelGate.activate() })
function suspend() { clearData(); listGate.suspend(); detailGate.suspend(); cancelGate.suspend() }
onDeactivated(suspend)
onBeforeUnmount(() => { suspend(); window.removeEventListener('data-refresh', loadOrders) })
</script>
<style scoped src="../styles/orders-settlements.css"></style>
