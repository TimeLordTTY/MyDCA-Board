<template>
  <div class="orders-page">
    <van-nav-bar title="订单管理" fixed placeholder />

    <van-pull-refresh v-model="refreshing" @refresh="reload">
      <div class="page-container">
        <div class="filter-bar">
          <van-tabs v-model:active="statusFilter" type="card" shrink @change="reload">
            <van-tab title="全部" name="ALL" />
            <van-tab title="待确认" name="PENDING" />
            <van-tab title="已确认" name="CONFIRMED" />
            <van-tab title="已取消" name="CANCELLED" />
            <van-tab title="失败" name="FAILED" />
          </van-tabs>
        </div>

        <van-empty
          v-if="!loading && orders.length === 0"
          description="暂无订单"
          image="search"
        />

        <div v-else class="order-list">
          <div
            v-for="o in orders"
            :key="o.orderId"
            class="order-card mobile-card"
            @click="openDetail(o)"
          >
            <div class="order-main">
              <div class="order-type">{{ getOrderTypeLabel(o.orderType) }}</div>
              <div class="order-amount">
                {{ formatAmount(o.amount) || formatShares(o.shares) }}
              </div>
            </div>
            <div class="order-meta">
              <span class="time">{{ formatDateTime(o.requestedAt) }}</span>
              <span class="status">{{ getStatusLabel(o.status) }}</span>
            </div>
            <div v-if="o.note" class="order-note">
              {{ o.note }}
            </div>
          </div>
        </div>
      </div>
    </van-pull-refresh>

    <van-popup
      v-model:show="showDetailDialog"
      position="bottom"
      :style="{ height: '75%' }"
      round
      closeable
    >
      <div v-if="currentDetail" class="detail-popup">
        <h3 class="popup-title">订单详情</h3>
        <van-cell-group inset>
          <van-cell title="订单号" :value="currentDetail.orderId" />
          <van-cell title="类型" :value="getOrderTypeLabel(currentDetail.orderType)" />
          <van-cell title="状态" :value="getStatusLabel(currentDetail.status)" />
          <van-cell title="发起时间" :value="formatDateTime(currentDetail.requestedAt)" />
          <van-cell v-if="currentDetail.tradeDate" title="交易日" :value="currentDetail.tradeDate" />
          <van-cell
            v-if="currentDetail.expectedConfirmDate"
            title="预计确认日"
            :value="currentDetail.expectedConfirmDate"
          />
          <van-cell v-if="currentDetail.amount" title="金额" :value="formatAmount(currentDetail.amount)" />
          <van-cell v-if="currentDetail.shares" title="份额" :value="formatShares(currentDetail.shares)" />
          <van-cell v-if="currentDetail.feeEstimate" title="预估手续费" :value="formatAmount(currentDetail.feeEstimate)" />
          <van-cell v-if="currentDetail.note" title="备注" :value="currentDetail.note" />
        </van-cell-group>

        <h4 class="section-title">资金明细</h4>
        <van-cell-group inset>
          <van-cell
            v-for="line in currentDetail.fundingLines"
            :key="line.id"
            :title="formatFundingTitle(line)"
            :label="formatFundingLabel(line)"
            :value="formatAmount(line.amount)"
          />
        </van-cell-group>

        <h4 v-if="currentDetail.settlement" class="section-title">结算信息</h4>
        <van-cell-group v-if="currentDetail.settlement" inset>
          <van-cell title="确认日期" :value="currentDetail.settlement.confirmDate" />
          <van-cell title="净值日期" :value="currentDetail.settlement.navDate" />
          <van-cell title="确认净值" :value="currentDetail.settlement.confirmNav.toFixed(4)" />
          <van-cell
            v-if="currentDetail.settlement.confirmShares != null"
            title="确认份额"
            :value="formatShares(currentDetail.settlement.confirmShares)"
          />
          <van-cell
            v-if="currentDetail.settlement.confirmAmount != null"
            title="确认金额"
            :value="formatAmount(currentDetail.settlement.confirmAmount)"
          />
          <van-cell
            v-if="currentDetail.settlement.confirmFee != null"
            title="确认费用"
            :value="formatAmount(currentDetail.settlement.confirmFee)"
          />
        </van-cell-group>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { showFailToast } from 'vant'
import { orderApi, type Order, type OrderDetail } from '@wealth-hub/shared'

const loading = ref(false)
const refreshing = ref(false)
const orders = ref<Order[]>([])

const statusFilter = ref<'ALL' | 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'FAILED'>('ALL')

const showDetailDialog = ref(false)
const currentDetail = ref<OrderDetail | null>(null)

function formatDateTime(s: string | undefined) {
  if (!s) return '-'
  return s.replace('T', ' ').substring(0, 19)
}

function formatAmount(v: number | undefined | null): string {
  if (v == null) return ''
  return v.toFixed(2)
}

function formatShares(v: number | undefined | null): string {
  if (v == null) return ''
  return v.toFixed(4)
}

function getStatusLabel(status: Order['status']) {
  switch (status) {
    case 'PENDING':
      return '待确认'
    case 'CONFIRMED':
      return '已确认'
    case 'CANCELLED':
      return '已取消'
    case 'FAILED':
      return '失败'
    default:
      return status
  }
}

function getOrderTypeLabel(t: Order['orderType']) {
  switch (t) {
    case 'BUY':
      return '买入'
    case 'SELL':
      return '卖出'
    case 'SUBSCRIPTION':
      return '申购'
    case 'REDEMPTION':
      return '赎回'
    default:
      return t
  }
}

async function reload() {
  try {
    loading.value = true
    const params: any = {}
    if (statusFilter.value !== 'ALL') {
      params.status = statusFilter.value
    }
    const list = await orderApi.getOrders(params)
    // 最近的排前面
    orders.value = [...list].sort((a, b) => (a.requestedAt < b.requestedAt ? 1 : -1))
  } catch (e: any) {
    showFailToast(e.message || '加载订单失败')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function formatFundingTitle(line: OrderDetail['fundingLines'][number]) {
  const prefix = line.lineType === 'SOURCE' ? '出金账户' : line.lineType === 'TARGET' ? '入金账户' : '账户'
  return `${prefix} #${line.accountId}`
}

function formatFundingLabel(line: OrderDetail['fundingLines'][number]) {
  const pieces: string[] = []
  if (line.amount != null) {
    pieces.push(`金额 ${formatAmount(line.amount)}`)
  }
  if (line.shares != null) {
    pieces.push(`份额 ${formatShares(line.shares)}`)
  }
  return pieces.join(' · ')
}

async function openDetail(o: Order) {
  try {
    const detail = await orderApi.getOrder(o.orderId)
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
}
.order-list {
  padding: 8px 12px 16px;
}
.order-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.order-main {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.order-type {
  font-size: var(--fs16);
  font-weight: 600;
}
.order-amount {
  font-size: var(--fs14);
  color: var(--primary);
}
.order-meta {
  display: flex;
  justify-content: space-between;
  font-size: var(--fs12);
  color: var(--muted);
}
.order-note {
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

