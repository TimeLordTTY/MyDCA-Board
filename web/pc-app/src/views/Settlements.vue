<template>
  <div class="trade-page">
    <SettlementConfirmModal v-model="confirmVisible" :order="selectedOrder" @success="afterSettlement" />
    <header class="trade-hero"><div><span class="trade-eyebrow">SETTLEMENT DESK / 人工结算</span><h1>先看影响，再落下确认。</h1><p>待结算是一个复核队列。每笔都要由你核对并再次确认。</p></div><router-link class="btn" to="/orders">返回订单中心 →</router-link></header>
    <div class="trade-safety"><strong>预览只读</strong><span>01 核对输入 → 02 服务器影响预览 → 03 明确二次确认。没有批量或自动结算。</span></div>
    <div class="trade-glance"><article><span>待人工复核</span><strong>{{ pendingReady ? pendingSettlements.length : '—' }}</strong><small>当前接口返回笔数，不汇总为资产</small></article><article><span>历史需核对</span><strong>{{ historyReady ? history.filter(h => h.reconciliationStatus !== 'OK').length : '—' }}</strong><small>只读对账，不自动修复</small></article><article class="trade-glance-note"><span>确认前的最后一站</span><strong>你来确认</strong><small>金额、持仓与账户快照由服务器核验</small></article></div>
    <section class="card">
      <div class="trade-toolbar"><div><span class="trade-eyebrow">01 / 待结算</span><h2>逐笔核对</h2></div><el-button :loading="loading" @click="loadSettlements">刷新待结算</el-button></div>
      <div class="trade-filters"><label><span>搜索已读取订单</span><el-input v-model="keyword" placeholder="产品或订单编号" clearable /></label><label><span>订单方向</span><el-select v-model="typeFilter" clearable placeholder="全部方向"><el-option v-for="type in orderTypes" :key="type" :value="type" :label="getOrderTypeLabel(type)" /></el-select></label></div>
      <p v-if="optionError" class="trade-error" role="alert">{{ optionError }}</p>
      <div v-if="loading" class="trade-state" role="status">正在读取待结算订单…</div>
      <div v-else-if="listError" class="trade-state trade-error" role="alert"><p>{{ listError }}</p><el-button @click="loadSettlements">重试读取</el-button></div>
      <div v-else-if="!pendingSettlements.length" class="trade-state"><h3>目前没有待结算订单</h3><p>这是服务器本次读取结果。已结算记录可在下方查看。</p></div>
      <div v-else-if="!filteredPending.length" class="trade-state"><h3>当前筛选没有匹配订单</h3><el-button @click="keyword = ''; typeFilter = ''">清除筛选</el-button></div>
      <div v-else class="settlement-queue">
        <article v-for="order in filteredPending" :key="order.orderId" class="settlement-ticket">
          <div class="trade-toolbar"><span class="tag orange">待结算 · {{ getOrderTypeLabel(order.orderType) }}</span><span class="mono trade-muted">{{ order.orderId }}</span></div>
          <h3>{{ productName(order.productId) }}</h3>
          <div class="trade-facts"><div><small>{{ isBuyOrder(order.orderType) ? '下单金额' : '预计金额 · 可能尚未提供' }}</small><strong>{{ displayNumber(order.amount) }}</strong></div><div><small>下单份额</small><strong>{{ displayNumber(order.shares, 6) }}</strong></div><div><small>预计确认日</small><strong>{{ order.expectedConfirmDate || '未知' }}</strong></div></div>
          <p class="trade-muted">{{ orderStageText(order) }}</p>
          <p v-if="!knownNumber(isBuyOrder(order.orderType) ? order.amount : order.shares)" class="trade-error">关键下单数值缺失，请打开复核并查看服务器校验。</p>
          <div class="trade-actions"><button class="btn primary" @click="handleConfirmSettlement(order)">打开结算复核 →</button><el-button @click="openOrder(order.orderId)">查看订单详情</el-button><el-button @click="openHoldings(order.productId)">关联持仓</el-button></div>
        </article>
      </div>
      <p v-if="pendingReady" class="trade-muted">已读取 {{ pendingSettlements.length }} 笔，筛选显示 {{ filteredPending.length }} 笔。不代表真实交易成交。</p>
    </section>
    <section class="card">
      <div class="trade-toolbar"><div><span class="trade-eyebrow">02 / 结算历史</span><h2>结果与审计，留在这里。</h2></div><el-button :loading="historyLoading" @click="loadHistory">刷新历史</el-button></div>
      <div class="trade-tabs" role="group" aria-label="对账状态"><button v-for="tab in auditTabs" :key="tab.value" :aria-pressed="auditFilter === tab.value" :class="{ active: auditFilter === tab.value }" @click="auditFilter = tab.value">{{ tab.label }}</button></div>
      <p class="trade-muted">对账只读取服务器记录；WARNING / BROKEN 不当作成功，不自动补账或修复。</p>
      <div v-if="historyLoading" class="trade-state" role="status">正在读取结算历史…</div>
      <div v-else-if="historyError" class="trade-state trade-error" role="alert"><p>{{ historyError }}</p><el-button @click="loadHistory">重试历史</el-button></div>
      <div v-else-if="!history.length" class="trade-state">服务器本次未返回结算历史</div>
      <div v-else-if="!filteredHistory.length" class="trade-state">当前对账筛选没有匹配记录</div>
      <div v-else class="trade-table-scroll"><table><thead><tr><th>产品 / 订单</th><th>方向 / 订单状态</th><th>确认日</th><th>只读对账</th><th>审计</th></tr></thead><tbody><tr v-for="item in filteredHistory" :key="item.orderId"><td><b>{{ item.productName || productName(item.productId) }}</b><small class="mono">{{ item.orderId }}</small></td><td>{{ getOrderTypeLabel(item.orderType) }}<small>{{ getOrderStatusLabel(item.orderStatus) || item.orderStatus }}</small></td><td>{{ item.settlement.confirmDate || '未知' }}</td><td><span class="audit-badge" :data-status="item.reconciliationStatus">{{ auditLabel(item.reconciliationStatus) }}</span></td><td><button class="btn" @click="loadAudit(item.orderId)">查看审计</button></td></tr></tbody></table></div>
    </section>
    <el-drawer v-model="auditVisible" title="结算审计 · 只读" size="min(800px, 96vw)" @closed="clearAudit">
      <p v-if="auditLoading" role="status">正在读取结算审计…</p><p v-if="auditError" role="alert" class="trade-error">{{ auditError }}</p>
      <el-button v-if="auditError" @click="loadAudit(auditOrderId)">重试审计</el-button>
      <template v-if="selectedAudit">
        <div class="trade-review-heading"><span class="trade-eyebrow">{{ getOrderTypeLabel(selectedAudit.orderType) }} / {{ auditLabel(selectedAudit.reconciliationStatus) }}</span><h2>{{ selectedAudit.productName || productName(selectedAudit.productId) }}</h2><p class="mono">{{ selectedAudit.orderId }}</p></div>
        <el-alert v-if="selectedAudit.reconciliationStatus !== 'OK'" type="warning" :closable="false" :title="auditLabel(selectedAudit.reconciliationStatus)" :description="selectedAudit.reasons.join('；') || '服务器未提供具体原因，请人工核对。'" />
        <p v-else class="trade-muted">{{ selectedAudit.reasons.join('；') || '服务器对账未发现不一致；不代表真实交易渠道已验收。' }}</p>
        <div class="trade-facts"><div><small>现金影响</small><strong>{{ displayNumber(selectedAudit.cashDelta) }}</strong></div><div><small>持仓份额影响</small><strong>{{ displayNumber(selectedAudit.positionSharesDelta, 6) }}</strong></div><div><small>手续费</small><strong>{{ displayNumber(selectedAudit.feeAmount) }}</strong></div></div>
        <dl class="trade-details"><dt>确认时间 / 操作人</dt><dd>{{ selectedAudit.settlement.confirmedAt || '未记录' }} / {{ selectedAudit.settlement.confirmedByUserId ?? '未记录' }}</dd><dt>确认日 / 净值日</dt><dd>{{ selectedAudit.settlement.confirmDate }} / {{ selectedAudit.settlement.navDate }}</dd><dt>净值 / 份额 / 金额</dt><dd>{{ displayNumber(selectedAudit.settlement.confirmNav, 6) }} / {{ displayNumber(selectedAudit.settlement.confirmShares, 6) }} / {{ displayNumber(selectedAudit.settlement.confirmAmount) }}</dd><dt>预览摘要</dt><dd>{{ selectedAudit.settlement.previewDigest || '历史记录未保存' }}<small>仅审计展示，不能用于结算确认。</small></dd><dt>结算流水</dt><dd>{{ selectedAudit.ledgerTxnId || '关联缺失，请人工核对' }}</dd></dl>
        <h3>来源 → 到账</h3><div class="funding-route"><div v-for="line in selectedAudit.fundingLines" :key="line.id"><span>{{ line.lineType === 'TARGET' ? '到账目标' : '来源 / 持仓' }}</span><b>账户 #{{ line.accountId }}</b><span>{{ displayNumber(line.amount) }} {{ line.currency || '币种未知' }} / {{ displayNumber(line.shares, 6) }} 份</span></div></div>
        <h3>服务器结算分录</h3><div class="trade-table-scroll"><table><thead><tr><th>账户 / 流水</th><th>类型 / 方向</th><th>金额 / 份额</th></tr></thead><tbody><tr v-for="(posting, index) in selectedAudit.postings" :key="index"><td>#{{ posting.accountId }}<small class="mono">{{ posting.txnId }}</small></td><td>{{ posting.accountType }} / {{ posting.postingType }}</td><td>{{ displayNumber(posting.amount) }}<small>{{ displayNumber(posting.shares, 6) }} 份</small></td></tr></tbody></table></div>
        <p v-if="!selectedAudit.postings.length" class="trade-error">未返回关联分录，不能推断影响为零。</p>
        <el-button @click="openHoldings(selectedAudit.productId)">查看关联持仓</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, onActivated, onDeactivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { settlementApi, productApi, useUserStore, getOrderTypeLabel, getOrderStatusLabel } from '@wealth-hub/shared'
import type { Order, ProductMaster, SettlementAudit } from '@wealth-hub/shared'
import SettlementConfirmModal from '../components/SettlementConfirmModal.vue'
import { createReadGate } from './managementViewModel'
import { displayNumber, knownNumber, orderTypes, isBuyOrder, orderStageText, validateOrders, settlementError } from '../components/orderSettlementModel'

const route = useRoute(), router = useRouter(), user = useUserStore()
const identity = () => JSON.stringify([user.token, user.user?.id, user.user?.familyId])
const pendingGate = createReadGate(identity), historyGate = createReadGate(identity), auditGate = createReadGate(identity)
const loading = ref(false), listError = ref(''), pendingReady = ref(false), optionError = ref('')
const pendingSettlements = ref<Order[]>([]), products = ref<ProductMaster[]>([]), keyword = ref(''), typeFilter = ref('')
const confirmVisible = ref(false), selectedOrder = ref<Order | null>(null)
const history = ref<SettlementAudit[]>([]), historyLoading = ref(false), historyError = ref(''), historyReady = ref(false), auditFilter = ref('')
const selectedAudit = ref<SettlementAudit | null>(null), auditVisible = ref(false), auditLoading = ref(false), auditError = ref(''), auditOrderId = ref('')
let initialAuditPending = true
const auditTabs = [{ value: '', label: '全部记录' }, { value: 'OK', label: '一致' }, { value: 'WARNING', label: '需核对' }, { value: 'BROKEN', label: '不一致' }]
const auditLabel = (status: string) => ({ OK: '一致', WARNING: '需核对', BROKEN: '不一致' }[status] || '未知对账状态')
const productName = (id: number) => products.value.find(p => p.id === id)?.productName || '产品 #' + id
const filteredPending = computed(() => pendingSettlements.value.filter(o => (!typeFilter.value || typeFilter.value === o.orderType) && (!keyword.value.trim() || (o.orderId + ' ' + productName(o.productId)).toLocaleLowerCase().includes(keyword.value.trim().toLocaleLowerCase()))))
const filteredHistory = computed(() => history.value.filter(h => !auditFilter.value || h.reconciliationStatus === auditFilter.value))
function clearAudit() { auditGate.invalidate(); selectedAudit.value = null; auditLoading.value = false; auditError.value = '' }
async function loadSettlements() {
  if (!pendingGate.active) return
  const current = pendingGate.begin()
  confirmVisible.value = false; selectedOrder.value = null; pendingSettlements.value = []; products.value = []
  loading.value = true; listError.value = ''; optionError.value = ''; pendingReady.value = false
  try {
    const [rows, names] = await Promise.all([settlementApi.getPendingSettlements(), productApi.getProducts().then(data => ({ data }), error => ({ error }))])
    if (!current()) return
    pendingSettlements.value = validateOrders(rows, true)
    if ('data' in names) products.value = names.data
    else optionError.value = settlementError(names.error, '产品名称读取失败，暂显示产品 ID')
    pendingReady.value = true
  } catch (error) { if (current()) listError.value = settlementError(error, '待结算读取失败，请重试') }
  finally { if (current()) loading.value = false }
}
function validateAudit(value: SettlementAudit) {
  if (!value || !value.settlement || !Array.isArray(value.fundingLines) || !Array.isArray(value.postings) || !Array.isArray(value.reasons)) throw Error('结算审计数据格式异常')
}
async function loadHistory() {
  if (!historyGate.active) return
  const current = historyGate.begin()
  history.value = []; historyReady.value = false; historyLoading.value = true; historyError.value = ''; auditVisible.value = false; clearAudit()
  try {
    const rows = await settlementApi.getHistory()
    if (!current()) return
    if (!Array.isArray(rows)) throw Error('结算历史列表格式异常')
    rows.forEach(validateAudit); history.value = rows; historyReady.value = true
    if (initialAuditPending && user.user?.id) {
      initialAuditPending = false
      if (typeof route.query.audit === 'string') void loadAudit(route.query.audit)
    }
  } catch (error) { if (current()) historyError.value = settlementError(error, '结算历史读取失败，请重试') }
  finally { if (current()) historyLoading.value = false }
}
async function loadAudit(orderId: string) {
  if (!auditGate.active || !/^[A-Za-z0-9_-]{1,128}$/.test(orderId)) return
  const current = auditGate.begin()
  auditOrderId.value = orderId; auditVisible.value = true; selectedAudit.value = null; auditLoading.value = true; auditError.value = ''
  try {
    const value = await settlementApi.getAudit(orderId)
    if (!current()) return
    validateAudit(value)
    if (value.orderId !== orderId) throw Error('服务器审计与当前订单不匹配')
    selectedAudit.value = value
  } catch (error) { if (current()) auditError.value = settlementError(error, '结算审计读取失败，请重试') }
  finally { if (current()) auditLoading.value = false }
}
function handleConfirmSettlement(order: Order) { if (order.status === 'PENDING' && pendingGate.active) { selectedOrder.value = order; confirmVisible.value = true } }
function afterSettlement() { void loadSettlements(); void loadHistory() }
function openOrder(orderId: string) { void router.push({ name: 'Orders', query: { orderId } }) }
function openHoldings(productId: number) { void router.push({ name: 'Holdings', query: { productId } }) }
function clearData() {
  pendingGate.invalidate(); historyGate.invalidate(); clearAudit(); pendingSettlements.value = []; products.value = []; history.value = []
  confirmVisible.value = false; selectedOrder.value = null; auditVisible.value = false; loading.value = false; historyLoading.value = false
  pendingReady.value = false; historyReady.value = false; listError.value = ''; optionError.value = ''; historyError.value = ''
}
watch(identity, (_, previous) => { clearData(); if (JSON.parse(previous)[1] == null && user.user?.id && user.token) { void loadSettlements(); void loadHistory() } }, { flush: 'sync' })
function refresh() { void loadSettlements(); void loadHistory() }
onMounted(() => { refresh(); window.addEventListener('data-refresh', refresh) })
onActivated(() => { pendingGate.activate(); historyGate.activate(); auditGate.activate() })
function suspend() { clearData(); pendingGate.suspend(); historyGate.suspend(); auditGate.suspend() }
onDeactivated(suspend)
onBeforeUnmount(() => { suspend(); window.removeEventListener('data-refresh', refresh) })
</script>
<style scoped src="../styles/orders-settlements.css"></style>
