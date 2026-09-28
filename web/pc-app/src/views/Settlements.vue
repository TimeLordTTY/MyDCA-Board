<template>
  <div>
    <!-- 结算确认模态框 -->
    <SettlementConfirmModal
      v-model="confirmVisible"
      :order="selectedOrder"
      @success="loadSettlements"
    />

    <div class="card">
      <div class="row-between">
        <div>
          <h3>
            待结算清单
            <span class="tag orange tiny" v-if="pendingSettlements.length > 0">{{ pendingSettlements.length }} 笔</span>
          </h3>
          <div class="sub">需要确认结算的订单</div>
        </div>
      </div>
      <div class="divider"></div>

      <div style="overflow: auto">
        <table>
          <thead>
            <tr>
              <th style="width: 100px;">订单ID</th>
              <th style="width: 60px;">类型</th>
              <th style="width: 200px;">标的</th>
              <th class="right" style="width: 100px;">金额</th>
              <th style="width: 120px;">预期确认日期</th>
              <th class="right" style="width: 100px;">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="6" class="td-muted" style="text-align: center">加载中...</td>
            </tr>
            <tr v-else-if="listError">
              <td colspan="6" class="td-muted" style="text-align: center" role="alert">{{ listError }} <button class="btn" @click="loadSettlements">重试</button></td>
            </tr>
            <tr v-else-if="pendingSettlements.length === 0">
              <td colspan="6" class="td-muted" style="text-align: center">暂无待结算订单</td>
            </tr>
            <tr v-for="settlement in pendingSettlements" :key="settlement.orderId">
              <td class="mono">{{ settlement.orderId.slice(-8) }}</td>
              <td>
                <span class="tag blue">{{ getOrderTypeLabel(settlement.orderType) }}</span>
              </td>
              <td><b>产品ID: {{ settlement.productId }}</b></td>
              <td class="right mono">{{ formatCurrency(settlement.amount) }}</td>
              <td>{{ formatDate(settlement.expectedConfirmDate) }}</td>
              <td class="right">
                <button class="btn" @click="handleConfirmSettlement(settlement)">确认结算</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <div class="card" style="margin-top: 20px">
      <div class="row-between"><h3>结算历史与只读对账</h3><button class="btn" @click="loadHistory">刷新历史</button></div>
      <div v-if="historyError" class="sub">{{ historyError }}</div>
      <div v-else-if="historyLoading" class="sub">正在加载结算历史...</div>
      <div v-else-if="history.length === 0" class="sub">暂无结算历史</div>
      <table v-else><thead><tr><th>订单</th><th>产品 / 类型</th><th>状态</th><th>对账</th><th>操作</th></tr></thead>
        <tbody><tr v-for="item in history" :key="item.orderId">
          <td class="mono">{{ item.orderId }}</td><td>{{ item.productName || item.productId }} / {{ getOrderTypeLabel(item.orderType) }}</td>
          <td>{{ item.orderStatus }}</td><td>{{ auditLabel(item.reconciliationStatus) }}</td>
          <td><button class="btn" @click="loadAudit(item.orderId)">查看详情</button></td>
        </tr></tbody></table>
      <div v-if="auditError" class="sub">{{ auditError }}</div>
      <div v-if="selectedAudit" class="card" style="margin-top: 16px">
        <h3>结算审计：{{ selectedAudit.orderId }}</h3>
        <p>确认时间：{{ selectedAudit.settlement.confirmedAt || '未记录' }}；确认日期：{{ selectedAudit.settlement.confirmDate }}</p>
        <p>净值：{{ selectedAudit.settlement.confirmNav ?? '未记录' }}；份额：{{ selectedAudit.settlement.confirmShares ?? '未记录' }}；金额：{{ selectedAudit.settlement.confirmAmount ?? '未记录' }}</p>
        <p>预览摘要：{{ selectedAudit.settlement.previewDigest || '历史记录未保存' }}（仅审计展示，不可用于确认）</p>
        <p>现金影响：{{ selectedAudit.cashDelta }}；持仓份额影响：{{ selectedAudit.positionSharesDelta }}；手续费：{{ selectedAudit.feeAmount }}</p>
        <p>来源 / 目标账户：{{ selectedAudit.fundingLines.map(line => `${line.lineType || 'SOURCE'} #${line.accountId}`).join('；') || '无' }}</p>
        <p>对账：{{ auditLabel(selectedAudit.reconciliationStatus) }}；{{ selectedAudit.reasons.join('；') || '未发现不一致' }}</p>
        <p>结算流水：{{ selectedAudit.ledgerTxnId || '缺失' }}</p>
        <p v-for="posting in selectedAudit.postings" :key="`${posting.txnId}-${posting.accountId}-${posting.accountType}`">
          {{ posting.accountType }} {{ posting.postingType }} 账户 #{{ posting.accountId }} 金额 {{ posting.amount }} 份额 {{ posting.shares ?? '—' }}
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { settlementApi, getOrderTypeLabel, formatCurrency, formatDate } from '@wealth-hub/shared'
import type { Order, SettlementAudit } from '@wealth-hub/shared'
import SettlementConfirmModal from '../components/SettlementConfirmModal.vue'

const loading = ref(false)
const listError = ref<string | null>(null)
const pendingSettlements = ref<Order[]>([])
const route = useRoute()
const history = ref<SettlementAudit[]>([])
const selectedAudit = ref<SettlementAudit | null>(null)
const historyLoading = ref(false)
const historyError = ref<string | null>(null)
const auditError = ref<string | null>(null)
const auditLabel = (status: SettlementAudit['reconciliationStatus']) =>
  ({ OK: '一致', WARNING: '需核对', BROKEN: '不一致' })[status]

async function loadHistory() {
  historyLoading.value = true
  historyError.value = null
  try { history.value = await settlementApi.getHistory() }
  catch { historyError.value = '结算历史加载失败，请重试' }
  finally { historyLoading.value = false }
}

async function loadAudit(orderId: string) {
  selectedAudit.value = null
  auditError.value = null
  try { selectedAudit.value = await settlementApi.getAudit(orderId) }
  catch { auditError.value = '结算详情加载失败，请重试' }
}

async function loadSettlements() {
  loading.value = true
  listError.value = null
  try {
    pendingSettlements.value = await settlementApi.getPendingSettlements()
  } catch (error: any) {
    pendingSettlements.value = []
    listError.value = error?.response?.status === 403 ? '没有查看待结算订单的权限，请联系管理员。' : '待结算订单加载失败，请重试。'
  } finally {
    loading.value = false
  }
}

const confirmVisible = ref(false)
const selectedOrder = ref<Order | null>(null)

function handleConfirmSettlement(settlement: Order) {
  selectedOrder.value = settlement
  confirmVisible.value = true
}

onMounted(() => {
  loadSettlements()
  loadHistory()
  if (typeof route.query.audit === 'string') loadAudit(route.query.audit)
})
</script>
