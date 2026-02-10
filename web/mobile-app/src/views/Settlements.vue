<template>
  <div class="settlements-page">
    <van-nav-bar title="待结算" fixed placeholder>
      <template #right>
        <van-icon name="refresh" @click="onRefresh" />
      </template>
    </van-nav-bar>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <div class="page-container">
        <van-empty v-if="!loading && pendingSettlements.length === 0" description="暂无待结算订单" />

        <div v-else class="settlement-list">
          <div
            v-for="item in pendingSettlements"
            :key="item.orderId"
            class="settlement-card"
            @click="showSettlementDetail(item)"
          >
            <div class="card-header">
              <van-tag :type="getOrderTypeTagType(item.orderType)" size="medium">
                {{ getOrderTypeLabel(item.orderType) }}
              </van-tag>
              <span class="order-id">{{ item.orderId }}</span>
            </div>

            <div class="card-body">
              <div class="product-info">
                <div class="product-name">{{ getProductName(item.productId) }}</div>
                <div class="product-code" v-if="getProductCode(item.productId)">
                  {{ getProductCode(item.productId) }}
                </div>
              </div>

              <div class="amount-info">
                <div class="amount-label">金额</div>
                <div class="amount-value">{{ formatCurrency(getOrderAmount(item)) }}</div>
              </div>

              <div class="meta-info">
                <div class="meta-item">
                  <van-icon name="clock-o" />
                  <span>预期确认：{{ formatDate(item.expectedConfirmDate || '') }}</span>
                </div>
                <div class="meta-item" v-if="item.shares">
                  <van-icon name="chart-trending-o" />
                  <span>预期份额：{{ formatNumber(item.shares, 2) }}</span>
                </div>
              </div>
            </div>

            <div class="card-footer">
              <van-button
                size="small"
                type="default"
                round
                @click.stop="showSettlementDetail(item)"
              >
                查看详情
              </van-button>
            </div>
          </div>
        </div>
      </div>
    </van-pull-refresh>

    <!-- 订单详情弹窗（集成可编辑字段 + 结算功能，对齐PC端） -->
    <van-popup
      v-model:show="showDetailDialog"
      position="bottom"
      :style="{ height: '85%' }"
      round
      closeable
    >
      <div class="detail-popup" v-if="currentOrderDetail">
        <h3 class="popup-title">订单详情</h3>
        <van-cell-group inset>
          <van-cell title="订单号" :value="currentOrderDetail.orderId" />
          <van-cell title="类型" :value="getOrderTypeLabel(currentOrderDetail.orderType)" />
          <van-cell title="产品" :value="getProductName(currentOrderDetail.productId)" />
          <van-cell 
            v-if="getProductCode(currentOrderDetail.productId)" 
            title="产品代码" 
            :value="getProductCode(currentOrderDetail.productId)" 
          />
          <van-cell title="状态" :value="currentOrderDetail.status === 'PENDING' ? '待结算' : currentOrderDetail.status === 'CONFIRMED' ? '已确认' : '已取消'" />

          <!-- 金额：买入/申购可编辑，赎回/卖出只读 -->
          <van-field
            v-if="isBuyOrder && currentOrderDetail.status === 'PENDING'"
            v-model="detailForm.amount"
            label="金额"
            type="number"
            inputmode="decimal"
            placeholder="请输入金额"
            @update:model-value="handleAmountChange"
          />
          <van-cell v-else title="金额" :value="getOrderDetailAmount(currentOrderDetail)" />

          <!-- 份额：赎回/卖出可编辑，买入/申购只读 -->
          <van-field
            v-if="!isBuyOrder && currentOrderDetail.status === 'PENDING'"
            v-model="detailForm.shares"
            label="份额"
            type="number"
            inputmode="decimal"
            placeholder="请输入份额"
            @update:model-value="handleSharesChange"
          />
          <van-cell 
            v-else 
            title="份额" 
            :value="getDetailShares()" 
          />

          <!-- 净值：PENDING时可编辑 -->
          <van-field
            v-if="currentOrderDetail.status === 'PENDING'"
            v-model="detailForm.nav"
            label="净值"
            type="number"
            inputmode="decimal"
            placeholder="请输入净值"
            @update:model-value="handleNavChange"
          />
          <van-cell 
            v-else 
            title="净值" 
            :value="getOrderDetailNav(currentOrderDetail)?.nav ? formatNumber(getOrderDetailNav(currentOrderDetail)!.nav, 6) : '—'" 
          />

          <!-- 净值日期：PENDING时可编辑 -->
          <van-field
            v-if="currentOrderDetail.status === 'PENDING'"
            v-model="detailForm.navDate"
            label="净值日期"
            placeholder="选择净值日期"
            readonly
            is-link
            @click="showNavDatePicker = true"
          />
          <van-cell 
            v-else-if="getOrderDetailNav(currentOrderDetail)?.navDate" 
            title="净值日期" 
            :value="getOrderDetailNav(currentOrderDetail)?.navDate" 
          />

          <!-- 发起日期（只读） -->
          <van-cell 
            v-if="currentOrderDetail.requestedAt" 
            title="发起日期" 
            :value="formatDate(currentOrderDetail.requestedAt)" 
          />

          <!-- 确认日期：PENDING时可编辑 -->
          <van-field
            v-if="currentOrderDetail.status === 'PENDING'"
            v-model="detailForm.confirmDate"
            label="确认日期"
            placeholder="选择确认日期"
            readonly
            is-link
            @click="showDatePicker = true"
          />
          <van-cell 
            v-else-if="currentOrderDetail.settlement?.confirmDate" 
            title="确认日期" 
            :value="currentOrderDetail.settlement.confirmDate" 
          />

          <!-- 手续费：PENDING时可编辑 -->
          <van-field
            v-if="currentOrderDetail.status === 'PENDING'"
            v-model="detailForm.fee"
            label="手续费"
            type="number"
            inputmode="decimal"
            placeholder="0.00"
          />
          <van-cell 
            v-else-if="currentOrderDetail.settlement?.confirmFee != null" 
            title="手续费" 
            :value="formatCurrency(currentOrderDetail.settlement.confirmFee)" 
          />

          <!-- 实际到账（自动计算，只读） -->
          <van-cell 
            v-if="currentOrderDetail.status === 'PENDING'" 
            title="实际到账" 
            :value="computedNetAmount" 
          />
          <van-cell 
            v-else-if="currentOrderDetail.settlement?.confirmAmount" 
            title="实际到账" 
            :value="formatCurrency(currentOrderDetail.settlement.confirmAmount)" 
          />

          <!-- 已结算的额外信息 -->
          <van-cell 
            v-if="currentOrderDetail.settlement?.confirmShares" 
            title="确认份额" 
            :value="formatNumber(currentOrderDetail.settlement.confirmShares, 2) + ' 份'" 
          />
          <van-cell 
            v-if="currentOrderDetail.note" 
            title="备注" 
            :value="currentOrderDetail.note" 
          />
        </van-cell-group>

        <div class="detail-actions">
          <van-button 
            v-if="currentOrderDetail.status === 'PENDING'"
            type="danger" 
            size="large" 
            round 
            block 
            @click="handleCancelOrderFromDetail"
            style="margin-bottom: 12px;"
          >
            取消订单
          </van-button>
          <van-button 
            v-if="currentOrderDetail.status === 'PENDING'"
            type="primary" 
            size="large" 
            round 
            block 
            :loading="submitting"
            @click="handleSettleFromDetail"
          >
            确认结算
          </van-button>
        </div>
      </div>
    </van-popup>

    <!-- 确认日期选择器 -->
    <van-popup v-model:show="showDatePicker" position="bottom">
      <van-date-picker
        v-model="selectedDate"
        @confirm="handleDateConfirm"
        @cancel="showDatePicker = false"
      />
    </van-popup>

    <!-- 净值日期选择器 -->
    <van-popup v-model:show="showNavDatePicker" position="bottom">
      <van-date-picker
        v-model="selectedNavDate"
        @confirm="handleNavDateConfirm"
        @cancel="showNavDatePicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { settlementApi, productApi, navApi, orderApi } from '@wealth-hub/shared'
import { formatCurrency, formatDate, formatNumber, getOrderTypeLabel } from '@wealth-hub/shared'
import { showSuccessToast, showFailToast, showLoadingToast, closeToast, showConfirmDialog as showVantConfirmDialog } from 'vant'
import type { Order, ProductMaster, OrderDetail } from '@wealth-hub/shared'

const refreshing = ref(false)
const loading = ref(false)
const submitting = ref(false)
const showDetailDialog = ref(false)
const showDatePicker = ref(false)
const showNavDatePicker = ref(false)

const pendingSettlements = ref<Order[]>([])
const currentOrderDetail = ref<OrderDetail | null>(null)
const products = ref<ProductMaster[]>([])
const orderNavs = ref<Map<string, number>>(new Map())

const selectedDate = ref<Date[]>([])
const selectedNavDate = ref<Date[]>([])

// 详情内编辑表单（对齐PC端 detailEditForm）
const detailForm = ref({
  amount: '',
  shares: '',
  nav: '',
  navDate: '',
  confirmDate: '',
  fee: '0',
})

const isBuyOrder = computed(() => {
  if (!currentOrderDetail.value) return true
  return ['BUY', 'SUBSCRIPTION'].includes(currentOrderDetail.value.orderType)
})

// 计算实际到账金额
const computedNetAmount = computed(() => {
  const nav = parseFloat(detailForm.value.nav || '0')
  const fee = parseFloat(detailForm.value.fee || '0')
  if (isBuyOrder.value) {
    const amount = parseFloat(detailForm.value.amount || '0')
    if (amount > 0) return formatCurrency(amount)
    return '—'
  } else {
    const shares = parseFloat(detailForm.value.shares || '0')
    if (nav > 0 && shares > 0) {
      const amount = shares * nav - fee
      return formatCurrency(Number(amount.toFixed(2)))
    }
    return '—'
  }
})

async function loadData() {
  try {
    loading.value = true
    const [settlements, productList] = await Promise.all([
      settlementApi.getPendingSettlements(),
      productApi.getProducts(),
    ])
    pendingSettlements.value = settlements
    products.value = productList
    await loadNavsForOrders(settlements)
  } catch (error: any) {
    showFailToast(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadNavsForOrders(orders: Order[]) {
  const navPromises = orders
    .filter(order => ['SELL', 'REDEMPTION'].includes(order.orderType))
    .map(async (order) => {
      if (!order.productId || !order.expectedNavDate) return
      try {
        const navData = await navApi.getLatestNav(order.productId)
        if (navData && navData.nav) {
          orderNavs.value.set(order.orderId, navData.nav)
        }
      } catch (error) {
        console.warn(`获取订单 ${order.orderId} 的净值失败:`, error)
      }
    })
  await Promise.all(navPromises)
}

async function onRefresh() {
  refreshing.value = true
  await loadData()
  refreshing.value = false
}

function getOrderTypeTagType(orderType: string): 'primary' | 'success' | 'warning' | 'danger' {
  switch (orderType) {
    case 'BUY':
    case 'SUBSCRIPTION':
      return 'danger'
    case 'SELL':
    case 'REDEMPTION':
      return 'success'
    default:
      return 'primary'
  }
}

function getProductName(productId?: number): string {
  if (!productId) return '—'
  const product = products.value.find(p => p.id === productId)
  return product?.productName || `产品${productId}`
}

function getProductCode(productId?: number): string {
  if (!productId) return ''
  const product = products.value.find(p => p.id === productId)
  return product?.productCode || ''
}

function getOrderAmount(item: Order): number {
  const isBuy = ['BUY', 'SUBSCRIPTION'].includes(item.orderType)
  if (isBuy) {
    return item.amount || 0
  } else {
    if ((item as any).settlement?.confirmAmount) {
      return (item as any).settlement.confirmAmount
    }
    const nav = (item as any).settlement?.confirmNav 
      || (item as any).navData?.nav 
      || orderNavs.value.get(item.orderId)
    const shares = (item as any).settlement?.confirmShares ?? item.shares ?? 0
    if (nav && nav > 0 && shares > 0) {
      return Number((shares * nav).toFixed(2))
    }
    return item.amount || 0
  }
}

// 显示订单详情（加载数据 + 初始化编辑表单）
async function showSettlementDetail(item: Order) {
  try {
    showLoadingToast({ message: '加载中...', forbidClick: true })
    const orderDetail = await orderApi.getOrder(item.orderId)
    
    // 获取净值
    let navValue: number | undefined
    if (!orderDetail.settlement?.confirmNav && orderDetail.productId && orderDetail.expectedNavDate) {
      try {
        const navData = await navApi.getNavByDate(orderDetail.productId, orderDetail.expectedNavDate)
        if (navData && navData.nav) {
          ;(orderDetail as any).navData = navData
          navValue = navData.nav
        }
      } catch (error) {
        console.warn('获取净值失败:', error)
      }
    }
    
    currentOrderDetail.value = orderDetail
    
    // 初始化编辑表单（对齐PC端 handleViewDetail 逻辑）
    const isBuy = ['BUY', 'SUBSCRIPTION'].includes(orderDetail.orderType)
    const today = new Date().toISOString().split('T')[0]
    const nav = orderDetail.settlement?.confirmNav || navValue || 0
    
    detailForm.value = {
      amount: orderDetail.amount ? String(orderDetail.amount) : '',
      shares: orderDetail.shares ? String(orderDetail.shares) : '',
      nav: nav ? String(nav) : '',
      navDate: orderDetail.expectedNavDate || '',
      confirmDate: orderDetail.expectedConfirmDate || today,
      fee: orderDetail.settlement?.confirmFee?.toString() || (orderDetail as any).feeEstimate?.toString() || '0',
    }
    
    // 如果有净值，自动计算份额（买入）或金额（赎回）
    if (nav > 0) {
      autoCalculate()
    }
    
    showDetailDialog.value = true
    closeToast()
  } catch (error: any) {
    closeToast()
    showFailToast(error.message || '加载订单详情失败')
  }
}

// 自动计算：买入时按金额算份额，赎回时按份额算金额
function autoCalculate() {
  const nav = parseFloat(detailForm.value.nav || '0')
  if (nav <= 0) return
  const fee = parseFloat(detailForm.value.fee || '0')

  if (isBuyOrder.value) {
    const amount = parseFloat(detailForm.value.amount || '0')
    if (amount > 0) {
      const netAmount = amount - fee
      const shares = netAmount / nav
      detailForm.value.shares = shares.toFixed(6)
    }
  } else {
    const shares = parseFloat(detailForm.value.shares || '0')
    if (shares > 0) {
      const amount = shares * nav
      detailForm.value.amount = amount.toFixed(2)
    }
  }
}

function handleAmountChange() { autoCalculate() }
function handleSharesChange() { autoCalculate() }
function handleNavChange() { autoCalculate() }

// 获取详情中展示的份额
function getDetailShares(): string {
  if (currentOrderDetail.value?.settlement?.confirmShares) {
    return formatNumber(currentOrderDetail.value.settlement.confirmShares, 2) + ' 份'
  }
  // PENDING 买入时显示自动计算的份额
  if (isBuyOrder.value && currentOrderDetail.value?.status === 'PENDING') {
    const shares = parseFloat(detailForm.value.shares || '0')
    return shares > 0 ? formatNumber(shares, 2) + ' 份' : '—'
  }
  return currentOrderDetail.value?.shares ? formatNumber(currentOrderDetail.value.shares, 2) + ' 份' : '—'
}

function getOrderDetailAmount(order: OrderDetail): string {
  const isBuy = ['BUY', 'SUBSCRIPTION'].includes(order.orderType)
  if (isBuy) {
    return formatCurrency(order.amount || 0)
  } else {
    if (order.settlement?.confirmAmount) {
      return formatCurrency(order.settlement.confirmAmount)
    }
    const nav = order.settlement?.confirmNav 
      || (order as any).navData?.nav 
      || orderNavs.value.get(order.orderId)
    const shares = order.settlement?.confirmShares ?? order.shares ?? 0
    if (nav && nav > 0 && shares > 0) {
      return formatCurrency(Number((shares * nav).toFixed(2)))
    }
    return order.amount ? formatCurrency(order.amount) : '—'
  }
}

function getOrderDetailNav(order: OrderDetail): { nav: number, navDate?: string } | null {
  if (order.settlement?.confirmNav) {
    return { nav: order.settlement.confirmNav, navDate: order.settlement.navDate }
  }
  if ((order as any).navData?.nav) {
    return { nav: (order as any).navData.nav, navDate: (order as any).navData.navDate || order.expectedNavDate }
  }
  const cachedNav = orderNavs.value.get(order.orderId)
  if (cachedNav) {
    return { nav: cachedNav, navDate: order.expectedNavDate }
  }
  return null
}

function handleDateConfirm({ selectedValues }: any) {
  detailForm.value.confirmDate = selectedValues.join('-')
  showDatePicker.value = false
}

async function handleNavDateConfirm({ selectedValues }: any) {
  const newNavDate = selectedValues.join('-')
  detailForm.value.navDate = newNavDate
  showNavDatePicker.value = false
  
  // 净值日期变化时自动获取净值
  if (newNavDate && currentOrderDetail.value?.productId) {
    try {
      const navData = await navApi.getNavByDate(currentOrderDetail.value.productId, newNavDate)
      if (navData && navData.nav) {
        detailForm.value.nav = navData.nav.toString()
        autoCalculate()
      } else {
        showFailToast('未找到该日期的净值数据')
      }
    } catch (error: any) {
      showFailToast('获取净值失败：' + (error.message || '未知错误'))
    }
  }
}

// 直接从详情弹窗结算（对齐PC端 handleSettle 逻辑）
async function handleSettleFromDetail() {
  if (!currentOrderDetail.value) return
  
  const isBuy = isBuyOrder.value
  const nav = parseFloat(detailForm.value.nav || '0')
  
  // 校验
  if (!nav || nav <= 0) {
    showFailToast('请填写净值')
    return
  }
  if (isBuy) {
    const amount = parseFloat(detailForm.value.amount || '0')
    if (!amount || amount <= 0) {
      showFailToast('请填写金额')
      return
    }
  } else {
    const shares = parseFloat(detailForm.value.shares || '0')
    if (!shares || shares <= 0) {
      showFailToast('请填写份额')
      return
  }
  }
  if (!detailForm.value.confirmDate) {
    showFailToast('请选择确认日期')
    return
  }

  try {
    submitting.value = true
    showLoadingToast({ message: '处理中...', forbidClick: true })

    // 对齐PC端：买入只传confirmShares，赎回只传confirmAmount
    const fee = parseFloat(detailForm.value.fee || '0')
    await orderApi.confirmSettlement({
      orderId: currentOrderDetail.value.orderId,
      confirmDate: detailForm.value.confirmDate,
      navDate: detailForm.value.navDate,
      confirmNav: nav,
      confirmShares: isBuy ? parseFloat(detailForm.value.shares || '0') : undefined,
      confirmAmount: !isBuy ? parseFloat(detailForm.value.amount || '0') : undefined,
      confirmFee: fee,
      note: '',
    })

    closeToast()
    showSuccessToast('结算确认成功')
    showDetailDialog.value = false
    await loadData()
    window.dispatchEvent(new CustomEvent('data-refresh'))
  } catch (error: any) {
    closeToast()
    showFailToast(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

// 取消订单
async function handleCancelOrderFromDetail() {
  if (!currentOrderDetail.value) return
  try {
    await showVantConfirmDialog({
      title: '确认取消',
      message: `确定要取消订单"${currentOrderDetail.value.orderId}"吗？`,
    })
    showLoadingToast({ message: '处理中...', forbidClick: true })
    await orderApi.cancelOrder(currentOrderDetail.value.orderId)
    closeToast()
    showSuccessToast('取消成功')
    showDetailDialog.value = false
    await loadData()
    window.dispatchEvent(new CustomEvent('data-refresh'))
  } catch (error: any) {
    closeToast()
    if (error !== 'cancel') {
      showFailToast(error.message || '取消失败')
    }
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.settlements-page {
  width: 100%;
  min-height: 100vh;
  background: var(--bg);
}

.page-container {
  padding: 16px;
  padding-bottom: calc(50px + var(--safe-area-inset-bottom) + 16px);
}

.settlement-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.settlement-card {
  background: var(--card);
  border-radius: var(--radius);
  padding: 16px;
  box-shadow: var(--shadow);
  transition: all 0.2s ease;
}

.settlement-card:active {
  transform: scale(0.98);
  box-shadow: var(--shadow2);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.order-id {
  font-size: 12px;
  color: var(--muted);
  font-family: monospace;
}

.card-body {
  margin-bottom: 16px;
}

.product-info {
  margin-bottom: 12px;
}

.product-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 4px;
}

.product-code {
  font-size: 12px;
  color: var(--muted);
  font-style: italic;
}

.amount-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  margin: 12px 0;
}

.amount-label {
  font-size: 14px;
  color: var(--muted);
}

.amount-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--text);
}

.meta-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--muted);
}

.card-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.popup-title {
  font-size: 20px;
  font-weight: 600;
  text-align: center;
  margin: 0 0 24px 0;
  color: var(--text);
}

.detail-popup {
  padding: 24px;
  height: 100%;
  overflow-y: auto;
}

.detail-actions {
  margin-top: 24px;
  padding: 0 16px;
  padding-bottom: calc(24px + var(--safe-area-inset-bottom));
}

.detail-actions .van-button {
  height: 48px;
  font-size: 16px;
  font-weight: 600;
}
</style>
