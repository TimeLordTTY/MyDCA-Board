<template>
  <div class="ledger-page">
    <van-nav-bar title="流水查询" fixed placeholder />

    <van-pull-refresh v-model="refreshing" @refresh="reload">
      <div class="page-container">
        <div class="filter-bar">
          <van-field
            v-model="keyword"
            label="备注"
            placeholder="按备注搜索"
            clearable
            @clear="reload"
            @blur="reload"
          />
          <div class="date-row">
            <van-field
              v-model="startDate"
              is-link
              readonly
              label="起始"
              placeholder="开始日期"
              @click="showStartPicker = true"
            />
            <span class="date-sep">至</span>
            <van-field
              v-model="endDate"
              is-link
              readonly
              label="结束"
              placeholder="结束日期"
              @click="showEndPicker = true"
            />
          </div>
        </div>

        <van-empty
          v-if="!loading && txns.length === 0"
          description="暂无流水记录"
          image="search"
        />

        <div v-else class="txn-list">
          <div
            v-for="txn in txns"
            :key="txn.txnId"
            class="txn-card mobile-card"
            @click="openDetail(txn)"
          >
            <div class="txn-main">
              <div class="txn-type">{{ txn.txnType }}</div>
              <div class="txn-amount">{{ getAmountLabel(txn) }}</div>
            </div>
            <div class="txn-meta">
              <span class="time">{{ formatDateTime(txn.requestedAt) }}</span>
              <span class="status">{{ getStatusLabel(txn.status) }}</span>
            </div>
            <div v-if="txn.note" class="txn-note">
              {{ txn.note }}
            </div>
          </div>
        </div>
      </div>
    </van-pull-refresh>

    <van-popup
      v-model:show="showDetailDialog"
      position="bottom"
      :style="{ height: '70%' }"
      round
      closeable
    >
      <div v-if="currentDetail" class="detail-popup">
        <h3 class="popup-title">流水详情</h3>
        <van-cell-group inset>
          <van-cell title="交易ID" :value="currentDetail.txnId" />
          <van-cell title="类型" :value="currentDetail.txnType" />
          <van-cell title="状态" :value="getStatusLabel(currentDetail.status)" />
          <van-cell title="发起时间" :value="formatDateTime(currentDetail.requestedAt)" />
          <van-cell v-if="currentDetail.tradeDate" title="交易归属日" :value="currentDetail.tradeDate" />
          <van-cell v-if="currentDetail.confirmDate" title="确认日期" :value="currentDetail.confirmDate" />
          <van-cell v-if="currentDetail.productId" title="关联产品ID" :value="String(currentDetail.productId)" />
          <van-cell v-if="currentDetail.orderId" title="关联订单号" :value="currentDetail.orderId" />
          <van-cell v-if="currentDetail.note" title="备注" :value="currentDetail.note" />
        </van-cell-group>

        <h4 class="section-title">分录明细</h4>
        <van-cell-group inset>
          <van-cell
            v-for="p in currentDetail.postings"
            :key="p.id"
            :title="formatPostingTitle(p)"
            :label="formatPostingLabel(p)"
            :value="formatAmount(p.amount)"
          />
        </van-cell-group>
      </div>
    </van-popup>

    <van-popup v-model:show="showStartPicker" position="bottom" round>
      <van-date-picker
        v-model="startDateParts"
        title="选择开始日期"
        :min-date="minDate"
        :max-date="maxDate"
        @confirm="onConfirmStart"
        @cancel="showStartPicker = false"
      />
    </van-popup>
    <van-popup v-model:show="showEndPicker" position="bottom" round>
      <van-date-picker
        v-model="endDateParts"
        title="选择结束日期"
        :min-date="minDate"
        :max-date="maxDate"
        @confirm="onConfirmEnd"
        @cancel="showEndPicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { showFailToast } from 'vant'
import { ledgerApi, type LedgerTxn, type LedgerTxnDetail } from '@wealth-hub/shared'

const loading = ref(false)
const refreshing = ref(false)
const txns = ref<LedgerTxn[]>([])

// 最近 30 天默认区间
const today = new Date()
const thirtyDaysAgo = new Date(today.getTime() - 29 * 24 * 60 * 60 * 1000)
const startDate = ref(formatDateInput(thirtyDaysAgo))
const endDate = ref(formatDateInput(today))
const keyword = ref('')

const showStartPicker = ref(false)
const showEndPicker = ref(false)
const minDate = new Date(today.getFullYear() - 2, 0, 1)
const maxDate = today
const startDateParts = ref<[string, string, string]>(toPickerParts(thirtyDaysAgo))
const endDateParts = ref<[string, string, string]>(toPickerParts(today))

const showDetailDialog = ref(false)
const currentDetail = ref<LedgerTxnDetail | null>(null)

function formatDateInput(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function toPickerParts(d: Date): [string, string, string] {
  return [
    String(d.getFullYear()),
    String(d.getMonth() + 1).padStart(2, '0'),
    String(d.getDate()).padStart(2, '0'),
  ]
}

function onConfirmStart({ selectedValues }: { selectedValues: string[] }) {
  startDate.value = selectedValues.join('-')
  showStartPicker.value = false
  reload()
}

function onConfirmEnd({ selectedValues }: { selectedValues: string[] }) {
  endDate.value = selectedValues.join('-')
  showEndPicker.value = false
  reload()
}

async function reload() {
  try {
    loading.value = true
    const params: any = {
      startDate: startDate.value,
      endDate: endDate.value,
      page: 1,
      pageSize: 50,
    }
    if (keyword.value.trim()) {
      params.note = keyword.value.trim()
    }
    const resp = await ledgerApi.getTransactions(params)
    txns.value = resp.list
  } catch (e: any) {
    showFailToast(e.message || '加载流水失败')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function formatDateTime(s: string | undefined) {
  if (!s) return '-'
  return s.replace('T', ' ').substring(0, 19)
}

function formatAmount(v: number | undefined | null): string {
  if (v == null) return '-'
  return (v >= 0 ? '' : '-') + Math.abs(v).toFixed(2)
}

function getStatusLabel(status: LedgerTxn['status']) {
  switch (status) {
    case 'PENDING':
      return '待确认'
    case 'CONFIRMED':
      return '已确认'
    case 'CANCELLED':
      return '已取消'
    case 'REVERSED':
      return '已撤销'
    default:
      return status
  }
}

function getAmountLabel(txn: LedgerTxn) {
  // 这里只能展示“交易方向/金额概要”，当前后端没直接给金额，先用 txnType 做占位
  return txn.txnType
}

function formatPostingTitle(p: LedgerTxnDetail['postings'][number]) {
  return `${p.postingType === 'DEBIT' ? '借' : '贷'} · 账户ID ${p.accountId}`
}

function formatPostingLabel(p: LedgerTxnDetail['postings'][number]) {
  return `${p.accountType} · ${p.currency}${p.shares != null ? ` · 份额 ${p.shares}` : ''}`
}

async function openDetail(txn: LedgerTxn) {
  try {
    const detail = await ledgerApi.getTransactionDetail(txn.txnId)
    currentDetail.value = detail
    showDetailDialog.value = true
  } catch (e: any) {
    showFailToast(e.message || '加载详情失败')
  }
}

onMounted(() => {
  reload()
})
</script>

<style scoped>
.filter-bar {
  padding: 12px 12px 4px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.date-row {
  display: flex;
  align-items: center;
}
.date-row .van-field {
  flex: 1;
}
.date-sep {
  padding: 0 4px;
  font-size: var(--fs12);
  color: var(--muted);
}
.txn-list {
  padding: 8px 12px 16px;
}
.txn-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.txn-main {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.txn-type {
  font-size: var(--fs16);
  font-weight: 600;
}
.txn-amount {
  font-size: var(--fs14);
  color: var(--primary);
}
.txn-meta {
  display: flex;
  justify-content: space-between;
  font-size: var(--fs12);
  color: var(--muted);
}
.txn-note {
  font-size: var(--fs12);
  color: var(--muted);
}
.detail-popup {
  padding: 16px;
}
.popup-title {
  margin: 0 0 8px 0;
  font-size: var(--fs18);
}
.section-title {
  margin: 12px 0 4px;
  font-size: var(--fs14);
}
</style>

