<template>
  <el-dialog v-model="visible" title="人工结算 · 逐笔复核" width="min(840px, 94vw)" top="6vh" :close-on-click-modal="false" :close-on-press-escape="!submitting" :before-close="beforeClose" @closed="resetSession">
    <div class="settlement-room">
      <div class="flow-steps" aria-label="结算步骤"><span :class="{ active: !preview }">01 核对输入</span><span :class="{ active: !!preview }">02 服务器预览</span><span>03 明确二次确认</span></div>
      <p v-if="loadingDetail" role="status">正在读取最新订单与资金线…</p>
      <p v-if="actionError" class="trade-error" role="alert">{{ actionError }}</p>
      <el-button v-if="!detail && !loadingDetail" @click="loadDetail">重试读取订单</el-button>
      <template v-if="detail">
        <div class="trade-review-heading"><span class="trade-eyebrow">{{ getOrderTypeLabel(detail.orderType) }} / {{ getOrderStatusLabel(detail.status) }}</span><h2>{{ detail.orderId }}</h2><p>{{ orderStageText(detail) }}</p></div>
        <div class="trade-facts"><div><small>下单金额</small><strong>{{ displayNumber(detail.amount) }}</strong></div><div><small>下单份额</small><strong>{{ displayNumber(detail.shares, 6) }}</strong></div><div><small>产品</small><strong>#{{ detail.productId }}</strong></div></div>
        <div class="funding-route"><div v-for="line in detail.fundingLines" :key="line.id"><span>{{ line.lineType === 'TARGET' ? '到账目标' : '来源 / 持仓账户' }}</span><b>账户 #{{ line.accountId }}</b><span>{{ line.currency || '币种未知' }} · {{ displayNumber(line.amount) }} / {{ displayNumber(line.shares, 6) }} 份</span></div><p v-if="!detail.fundingLines.length">资金线缺失，须由服务器预览核验。</p></div>
        <p v-if="uncertain" role="alert" class="trade-error">上次确认结果未知，请关闭后刷新订单并核对结算历史。本页禁止重提，不自动重试。</p>
        <p v-if="detail.status !== 'PENDING'" role="alert" class="trade-error">服务器最新状态不是待结算，不能继续确认。</p>
        <el-form :model="form" label-position="top" :disabled="submitting || uncertain || detail.status !== 'PENDING'">
          <div class="trade-form-grid">
            <el-form-item label="确认日期" required><el-date-picker v-model="form.confirmDate" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" /></el-form-item>
            <el-form-item label="净值日期" required><el-date-picker v-model="form.navDate" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" /></el-form-item>
            <el-form-item label="确认净值" required><el-input-number v-model="form.confirmNav" :min="0.000001" :precision="6" /><el-button :loading="loadingNav" :disabled="busy || uncertain" @click="loadNav">读取该日净值</el-button></el-form-item>
            <el-form-item label="确认份额 · 可留空由服务器核验"><el-input-number v-model="form.confirmShares" :min="0" :precision="6" /></el-form-item>
            <el-form-item :label="isBuyOrder(detail.orderType) ? '确认金额 · 可选' : '实际确认金额 · 必填'" :required="!isBuyOrder(detail.orderType)"><el-input-number v-model="form.confirmAmount" :min="0" :precision="2" /></el-form-item>
            <el-form-item label="确认费用 · 留空按服务器费率估算"><el-input-number v-model="form.confirmFee" :min="0" :precision="2" /></el-form-item>
          </div>
          <el-form-item label="备注"><el-input v-model="form.note" type="textarea" :rows="2" /></el-form-item>
          <el-form-item label="手动覆盖"><el-switch :model-value="false" disabled /><span class="trade-muted">现有后端固定关闭，不支持绕过校验。</span></el-form-item>
        </el-form>
        <p class="trade-muted">金额、份额与手续费影响由服务器计算；这里不推算持仓或到账余额。修改输入后须重新预览。</p>
      </template>
      <section v-if="preview" class="trade-preview" aria-label="结算影响预览">
        <div class="trade-toolbar"><h3>如果确认，会发生什么？</h3><span class="tag" :class="canConfirm ? 'green' : 'orange'">{{ canConfirm ? '可进入人工确认' : '暂不可确认' }}</span></div>
        <p class="trade-muted">只读预览，尚未改变余额、持仓或订单状态。</p>
        <el-alert v-if="!preview.confirmSupported" type="error" :closable="false" title="服务器阻止结算" :description="buildSettlementBlockingText(preview)" />
        <el-alert v-else-if="!preview.freshPreviewToken" type="error" :closable="false" title="服务器未返回有效预览令牌，请重新预览" />
        <div class="trade-facts"><div><small>服务器计算份额</small><strong>{{ displayNumber(preview.computedShares, 6) }}</strong></div><div><small>服务器计算金额</small><strong>{{ displayNumber(preview.computedAmount) }}</strong></div><div><small>服务器确认费用</small><strong>{{ displayNumber(preview.confirmFee) }}</strong></div></div>
        <ul><li v-for="(line, index) in preview.summaryLines" :key="index">{{ line }}</li></ul>
        <div class="trade-table-scroll"><table><thead><tr><th>受影响账户</th><th>分录类型 / 方向</th><th>金额 / 份额</th><th>服务器说明</th></tr></thead><tbody><tr v-for="(posting, index) in preview.postingsPreview" :key="index"><td>{{ posting.accountName || (posting.accountId ? '账户 #' + posting.accountId : '结算时由服务器创建') }}</td><td>{{ posting.accountType }} / {{ posting.postingType }}</td><td>{{ displayNumber(posting.amount) }} {{ posting.currency || '币种未知' }}<br>{{ displayNumber(posting.shares, 6) }} 份</td><td>{{ posting.description || '未提供' }}</td></tr></tbody></table></div>
        <el-alert v-for="(warning, index) in preview.warnings" :key="index" type="warning" :closable="false" :title="warning" />
      </section>
      <p v-else-if="detail" class="trade-preview-empty">先生成服务器预览，再核对现金 / 应收 / 持仓 / 手续费影响。</p>
    </div>
    <template #footer><el-button :disabled="submitting" @click="handleClose">关闭</el-button><el-button :loading="previewing" :disabled="busy || !detail || detail.status !== 'PENDING' || uncertain" @click="handlePreview">生成只读预览</el-button><el-button type="primary" :loading="submitting" :disabled="busy || !canConfirm" @click="handleSubmit">核对后确认结算</el-button></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount, onDeactivated, onActivated } from 'vue'
import { ElMessageBox, ElNotification } from 'element-plus'
import { settlementApi, orderApi, navApi, useUserStore, getOrderTypeLabel, getOrderStatusLabel, buildSettlementPreviewText, buildSettlementBlockingText, buildSettlementConfirmTitle } from '@wealth-hub/shared'
import type { Order, OrderDetail, SettlementPreview, SettlementPreviewRequest } from '@wealth-hub/shared'
import { createReadGate } from '../views/managementViewModel'
import { displayNumber, knownNumber, validDate, localToday, isBuyOrder, orderStageText, validateOrders, settlementError } from './orderSettlementModel'

const props = defineProps<{ modelValue: boolean; order: Order | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; success: [] }>()
const user = useUserStore()
const identity = () => JSON.stringify([user.token, user.user?.id, user.user?.familyId])
const session = createReadGate(identity), navGate = createReadGate(identity)
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const detail = ref<OrderDetail | null>(null), preview = ref<SettlementPreview | null>(null)
const loadingDetail = ref(false), previewing = ref(false), submitting = ref(false), loadingNav = ref(false)
const actionError = ref('')
const form = ref(emptyForm())
// Memory-only guards: a lost financial response must be reconciled, never auto-retried.
const uncertainOrders = ref(new Set<string>()), completedOrders = ref(new Set<string>())
const orderKey = () => JSON.stringify([identity(), props.order?.orderId])
const uncertain = computed(() => uncertainOrders.value.has(orderKey()))
const busy = computed(() => loadingDetail.value || previewing.value || submitting.value || loadingNav.value)
let revision = 0
const canConfirm = computed(() => !!detail.value && detail.value.status === 'PENDING' && !uncertain.value
  && !completedOrders.value.has(orderKey()) && preview.value?.confirmSupported === true
  && preview.value.orderId === detail.value.orderId && preview.value.orderType === detail.value.orderType
  && preview.value.orderStatus === 'PENDING' && typeof preview.value.freshPreviewToken === 'string'
  && !!preview.value.freshPreviewToken.trim())
function emptyForm() {
  return { confirmDate: '', navDate: '', confirmNav: undefined as number | undefined,
    confirmShares: undefined as number | undefined, confirmAmount: undefined as number | undefined,
    confirmFee: undefined as number | undefined, note: '' }
}
function invalidatePreview() { revision++; preview.value = null }
function resetSession() {
  session.invalidate(); navGate.invalidate(); invalidatePreview(); detail.value = null
  loadingDetail.value = false; previewing.value = false; loadingNav.value = false; actionError.value = ''
}
watch(form, invalidatePreview, { deep: true, flush: 'sync' })
watch(() => form.value.navDate, () => { navGate.invalidate(); loadingNav.value = false; form.value.confirmNav = undefined }, { flush: 'sync' })
watch(() => [props.modelValue, props.order] as const, () => {
  resetSession()
  if (props.modelValue && props.order) void loadDetail()
}, { deep: true, immediate: true, flush: 'sync' })
watch(identity, resetSession, { flush: 'sync' })

async function loadDetail() {
  if (!visible.value || !props.order || submitting.value || !session.active) return
  const id = props.order.orderId, current = session.begin()
  invalidatePreview(); detail.value = null; actionError.value = ''; loadingDetail.value = true
  try {
    const value = await orderApi.getOrder(id)
    if (!current() || !visible.value) return
    validateOrders([value])
    if (value.orderId !== id || !Array.isArray(value.fundingLines)) throw Error('服务器订单详情不匹配或资金线格式异常')
    detail.value = value
    form.value = { ...emptyForm(), confirmDate: value.expectedConfirmDate || localToday(), navDate: value.expectedNavDate || localToday(),
      confirmShares: isBuyOrder(value.orderType) ? undefined : value.shares, confirmFee: value.feeEstimate, note: value.note || '' }
  } catch (error) { if (current()) actionError.value = settlementError(error, '最新订单读取失败，请重试') }
  finally { if (current()) loadingDetail.value = false }
}
async function loadNav() {
  if (busy.value || !detail.value || detail.value.status !== 'PENDING' || uncertain.value) return
  if (!validDate(form.value.navDate)) { actionError.value = '请先选择有效净值日期'; return }
  const current = navGate.begin(), date = form.value.navDate, id = detail.value.productId
  loadingNav.value = true; actionError.value = ''; form.value.confirmNav = undefined
  const expectedRevision = revision
  try {
    const nav = await navApi.getNavByDate(id, date)
    if (!current() || !visible.value || revision !== expectedRevision) return
    if (!nav || nav.navDate !== date || !knownNumber(nav.nav) || nav.nav <= 0) throw Error('该日净值未知，请核实后手动输入')
    form.value.confirmNav = nav.nav
  } catch (error) { if (current() && revision === expectedRevision) actionError.value = settlementError(error, '净值读取失败，请核实后手动输入') }
  finally { if (current()) loadingNav.value = false }
}
function buildRequest(): SettlementPreviewRequest | null {
  if (!detail.value) return null
  return { orderId: detail.value.orderId, ...form.value, note: form.value.note || undefined }
}
function validateInput(request: SettlementPreviewRequest): string | null {
  if (!validDate(request.confirmDate) || !validDate(request.navDate)) return '请填写有效的确认日期和净值日期'
  if (!knownNumber(request.confirmNav) || request.confirmNav <= 0) return '确认净值未知或无效，请核实后填写正数'
  for (const value of [request.confirmShares, request.confirmAmount, request.confirmFee]) {
    if (value != null && (!knownNumber(value) || value < 0)) return '金额、份额和费用须为有效非负数'
  }
  if (detail.value && !isBuyOrder(detail.value.orderType) && (!knownNumber(request.confirmAmount) || request.confirmAmount <= 0)) return '卖出 / 赎回须填写正数实际确认金额'
  return null
}
async function handlePreview() {
  if (busy.value || !visible.value || !session.active || detail.value?.status !== 'PENDING' || uncertain.value || completedOrders.value.has(orderKey())) return
  invalidatePreview(); actionError.value = ''
  const request = buildRequest()
  if (!request) return
  const invalid = validateInput(request)
  if (invalid) { actionError.value = invalid; return }
  const current = session.begin(), inputRevision = revision
  previewing.value = true
  try {
    const value = await settlementApi.previewSettlement(request)
    if (!current() || !visible.value || revision !== inputRevision) return
    if (value.orderId !== request.orderId || value.orderType !== detail.value?.orderType) throw Error('服务器预览与当前订单不匹配')
    preview.value = value
  } catch (error) { if (current() && revision === inputRevision) actionError.value = settlementError(error, '结算预览失败，请重试') }
  finally { if (current()) previewing.value = false }
}
async function handleSubmit() {
  if (busy.value || !visible.value || !session.active || !canConfirm.value) return
  const request = buildRequest(), fresh = preview.value, key = orderKey(), inputRevision = revision
  if (!request || !fresh) return
  const current = session.begin()
  // Lock before awaiting the owner's dialog, preventing concurrent clicks and writes.
  submitting.value = true; actionError.value = ''
  try {
    try {
      await ElMessageBox.confirm(buildSettlementPreviewText(fresh), buildSettlementConfirmTitle(fresh), {
        confirmButtonText: '我已核对，确认结算', cancelButtonText: '再检查一下', type: 'warning', closeOnClickModal: false,
      })
    } catch { return }
    if (!current() || !visible.value || revision !== inputRevision || preview.value !== fresh || !canConfirm.value || key !== orderKey()) return
    try {
      await orderApi.confirmSettlement({ ...request, freshPreviewToken: fresh.freshPreviewToken })
      completedOrders.value.add(key)
      if (!current()) return
      ElNotification.success({ title: '服务器已返回结算成功', message: '请以最新订单和结算历史核对结果。', position: 'bottom-right' })
      emit('success'); visible.value = false
    } catch (error) {
      const status = (error as { response?: { status?: number } })?.response?.status
      if (!status || status >= 500 || status === 408) uncertainOrders.value.add(key)
      if (current()) actionError.value = settlementError(error, '确认失败，请核对订单与结算历史')
    }
  } finally { submitting.value = false; invalidatePreview() }
}
function handleClose() { if (!submitting.value) { resetSession(); visible.value = false } }
function beforeClose(done: () => void) { if (!submitting.value) { resetSession(); done() } }
onActivated(() => { session.activate(); navGate.activate() })
function suspend() { resetSession(); session.suspend(); navGate.suspend() }
onDeactivated(suspend)
onBeforeUnmount(suspend)
</script>

<style scoped src="../styles/orders-settlements.css"></style>
