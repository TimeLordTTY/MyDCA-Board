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

    <!-- 订单详情弹窗 -->
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
          <van-cell 
            title="份额" 
            :value="currentOrderDetail.shares ? formatNumber(currentOrderDetail.shares, 2) + ' 份' : '—'" 
          />
          <van-cell 
            title="金额" 
            :value="getOrderDetailAmount(currentOrderDetail)" 
          />
          <van-cell 
            title="净值" 
            :value="getOrderDetailNav(currentOrderDetail)?.nav ? formatNumber(getOrderDetailNav(currentOrderDetail)!.nav, 6) : '—'" 
          />
          <van-cell 
            v-if="getOrderDetailNav(currentOrderDetail)?.navDate" 
            title="净值日期" 
            :value="getOrderDetailNav(currentOrderDetail)?.navDate" 
          />
          <van-cell 
            title="预期净值日期" 
            :value="currentOrderDetail.expectedNavDate || '—'" 
          />
          <van-cell 
            title="预期确认日期" 
            :value="currentOrderDetail.expectedConfirmDate || '—'" 
          />
          <van-cell 
            v-if="currentOrderDetail.settlement" 
            title="确认日期" 
            :value="currentOrderDetail.settlement.confirmDate || '—'" 
          />
          <van-cell 
            v-if="currentOrderDetail.settlement?.confirmNav" 
            title="确认净值" 
            :value="formatNumber(currentOrderDetail.settlement.confirmNav, 6)" 
          />
          <van-cell 
            v-if="currentOrderDetail.settlement?.confirmShares" 
            title="确认份额" 
            :value="formatNumber(currentOrderDetail.settlement.confirmShares, 2) + ' 份'" 
          />
          <van-cell 
            v-if="currentOrderDetail.settlement?.confirmAmount" 
            title="确认金额" 
            :value="formatCurrency(currentOrderDetail.settlement.confirmAmount)" 
          />
          <van-cell 
            v-if="currentOrderDetail.settlement?.confirmFee" 
            title="手续费" 
            :value="formatCurrency(currentOrderDetail.settlement.confirmFee)" 
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
            @click="handleSettleFromDetail"
          >
            确认结算
          </van-button>
        </div>
      </div>
    </van-popup>

    <!-- 结算确认弹窗 -->
    <van-popup
      v-model:show="showConfirmDialog"
      position="bottom"
      :style="{ height: '80%' }"
      round
      closeable
    >
      <div class="confirm-popup" v-if="currentSettlement">
        <h3 class="popup-title">确认结算</h3>
        <van-form @submit="handleSubmitSettlement">
          <van-cell-group inset>
            <van-field
              v-model="settlementForm.confirmDate"
              name="confirmDate"
              label="确认日期"
              placeholder="选择确认日期"
              readonly
              is-link
              @click="showDatePicker = true"
              :rules="[{ required: true, message: '请选择确认日期' }]"
            />
            <van-field
              v-model="settlementForm.navDate"
              name="navDate"
              label="净值日期"
              placeholder="选择净值日期"
              readonly
              is-link
              @click="showNavDatePicker = true"
            />
            <van-field
              v-model="settlementForm.confirmNav"
              name="confirmNav"
              label="确认净值"
              placeholder="请输入净值"
              type="digit"
              inputmode="decimal"
              :rules="[{ required: true, message: '请输入净值' }]"
            />
            <van-field
              v-model="settlementForm.confirmShares"
              name="confirmShares"
              :label="isBuyOrder ? '确认份额' : '卖出份额'"
              placeholder="请输入份额"
              type="digit"
              inputmode="decimal"
              :rules="[{ required: true, message: '请输入份额' }]"
            />
            <van-field
              v-model="settlementForm.confirmAmount"
              name="confirmAmount"
              :label="isBuyOrder ? '确认金额' : '到账金额'"
              placeholder="请输入金额"
              type="digit"
              inputmode="decimal"
              :rules="[{ required: true, message: '请输入金额' }]"
            />
            <van-field
              v-model="settlementForm.confirmFee"
              name="confirmFee"
              label="手续费"
              placeholder="请输入手续费"
              type="digit"
              inputmode="decimal"
            />
            <van-field
              v-model="settlementForm.note"
              name="note"
              label="备注"
              placeholder="选填"
              type="textarea"
              rows="3"
            />
          </van-cell-group>
          <div class="form-actions">
            <van-button round block type="primary" native-type="submit" :loading="submitting">
              确认结算
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <!-- 日期选择器 -->
    <van-popup v-model:show="showDatePicker" position="bottom">
      <van-date-picker
        v-model="selectedDate"
        @confirm="handleDateConfirm"
        @cancel="showDatePicker = false"
      />
    </van-popup>

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
import { ref, computed, onMounted, watch } from 'vue'
import { settlementApi, productApi, navApi, orderApi } from '@wealth-hub/shared'
import { formatCurrency, formatDate, formatNumber, getOrderTypeLabel } from '@wealth-hub/shared'
import { showSuccessToast, showFailToast, showLoadingToast, closeToast, showConfirmDialog as showVantConfirmDialog } from 'vant'
import type { Order, ProductMaster, OrderDetail } from '@wealth-hub/shared'

const refreshing = ref(false)
const loading = ref(false)
const submitting = ref(false)
const showDetailDialog = ref(false)
const showConfirmDialog = ref(false)
const showDatePicker = ref(false)
const showNavDatePicker = ref(false)

const pendingSettlements = ref<Order[]>([])
const currentSettlement = ref<Order | null>(null)
const currentOrderDetail = ref<OrderDetail | null>(null)
const products = ref<ProductMaster[]>([])
const orderNavs = ref<Map<string, number>>(new Map()) // 存储订单的净值数据

const selectedDate = ref<Date[]>([])
const selectedNavDate = ref<Date[]>([])

const settlementForm = ref({
  confirmDate: '',
  navDate: '',
  confirmNav: '',
  confirmShares: '',
  confirmAmount: '',
  confirmFee: '0',
  note: '',
})

const isBuyOrder = computed(() => {
  if (!currentSettlement.value) return true
  return ['BUY', 'SUBSCRIPTION'].includes(currentSettlement.value.orderType)
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
    
    // 为赎回订单加载净值数据
    await loadNavsForOrders(settlements)
  } catch (error: any) {
    showFailToast(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

// 为订单加载净值数据（用于计算金额）
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
        // 忽略单个订单的净值获取失败
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

// 计算订单金额（优先使用结算确认的金额，其次使用份额×净值）
function getOrderAmount(item: Order): number {
  const isBuy = ['BUY', 'SUBSCRIPTION'].includes(item.orderType)
  
  if (isBuy) {
    // 买入：使用订单金额
    return item.amount || 0
  } else {
    // 赎回：优先使用结算确认的金额，其次使用份额×净值计算
    if ((item as any).settlement?.confirmAmount) {
      return (item as any).settlement.confirmAmount
    }
    
    // 尝试从多个来源获取净值
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

// 显示订单详情
async function showSettlementDetail(item: Order) {
  try {
    showLoadingToast({ message: '加载中...', forbidClick: true })
    const orderDetail = await orderApi.getOrder(item.orderId)
    
    // 获取净值数据（如果还没有结算确认的净值）
    if (!orderDetail.settlement?.confirmNav && orderDetail.productId && orderDetail.expectedNavDate) {
      try {
        const navData = await navApi.getNavByDate(orderDetail.productId, orderDetail.expectedNavDate)
        if (navData && navData.nav) {
          // 将净值数据附加到订单详情对象上
          ;(orderDetail as any).navData = navData
        }
      } catch (error) {
        // 获取净值失败不影响详情显示
        console.warn('获取净值失败:', error)
      }
    }
    
    currentOrderDetail.value = orderDetail
    currentSettlement.value = item
    showDetailDialog.value = true
    closeToast()
  } catch (error: any) {
    closeToast()
    showFailToast(error.message || '加载订单详情失败')
  }
}

// 从详情页进入结算
function handleSettleFromDetail() {
  if (!currentOrderDetail.value) return
  showDetailDialog.value = false
  handleConfirmSettlement(currentOrderDetail.value as any)
}

// 从详情页取消订单
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

async function handleConfirmSettlement(item: Order | OrderDetail) {
  currentSettlement.value = item as Order
  const navDate = item.expectedNavDate || new Date().toISOString().split('T')[0]
  
  settlementForm.value = {
    confirmDate: new Date().toISOString().split('T')[0],
    navDate: navDate,
    confirmNav: '',
    confirmShares: item.shares?.toString() || '',
    confirmAmount: item.amount?.toString() || '',
    confirmFee: '0',
    note: '',
  }
  
  // 自动获取净值
  if (item.productId && navDate) {
    try {
      const navData = await navApi.getNavByDate(item.productId, navDate)
      if (navData && navData.nav) {
        settlementForm.value.confirmNav = navData.nav.toString()
        // 自动计算份额或金额
        calculateSettlementFields()
      }
    } catch (error: any) {
      // 获取净值失败，用户需要手动输入
      console.warn('获取净值失败:', error)
    }
  }
  
  showConfirmDialog.value = true
}

// 获取订单详情金额
function getOrderDetailAmount(order: OrderDetail): string {
  const isBuy = ['BUY', 'SUBSCRIPTION'].includes(order.orderType)
  
  if (isBuy) {
    return formatCurrency(order.amount || 0)
  } else {
    // 赎回：优先使用结算确认的金额，其次使用份额×净值计算
    if (order.settlement?.confirmAmount) {
      return formatCurrency(order.settlement.confirmAmount)
    }
    
    // 尝试从多个来源获取净值
    const nav = order.settlement?.confirmNav 
      || (order as any).navData?.nav 
      || orderNavs.value.get(order.orderId)
    const shares = order.settlement?.confirmShares ?? order.shares ?? 0
    
    if (nav && nav > 0 && shares > 0) {
      const amount = shares * nav
      return formatCurrency(Number(amount.toFixed(2)))
    }
    
    return order.amount ? formatCurrency(order.amount) : '—'
  }
}

// 获取订单详情净值
function getOrderDetailNav(order: OrderDetail): { nav: number, navDate?: string } | null {
  if (order.settlement?.confirmNav) {
    return {
      nav: order.settlement.confirmNav,
      navDate: order.settlement.navDate
    }
  }
  
  // 尝试从navData获取（如果已加载）
  if ((order as any).navData?.nav) {
    return {
      nav: (order as any).navData.nav,
      navDate: (order as any).navData.navDate || order.expectedNavDate
    }
  }
  
  // 尝试从orderNavs缓存获取
  const cachedNav = orderNavs.value.get(order.orderId)
  if (cachedNav) {
    return {
      nav: cachedNav,
      navDate: order.expectedNavDate
    }
  }
  
  return null
}

function handleDateConfirm({ selectedValues }: any) {
  settlementForm.value.confirmDate = selectedValues.join('-')
  showDatePicker.value = false
}

async function handleNavDateConfirm({ selectedValues }: any) {
  settlementForm.value.navDate = selectedValues.join('-')
  showNavDatePicker.value = false
  
  // 净值日期变化时，自动获取净值
  if (settlementForm.value.navDate && currentSettlement.value?.productId) {
    try {
      const navData = await navApi.getNavByDate(
        currentSettlement.value.productId,
        settlementForm.value.navDate
      )
      if (navData && navData.nav) {
        settlementForm.value.confirmNav = navData.nav.toString()
        // 重新计算份额或金额
        calculateSettlementFields()
      } else {
        showFailToast('未找到该日期的净值数据')
      }
    } catch (error: any) {
      showFailToast('获取净值失败：' + (error.message || '未知错误'))
    }
  }
}

// 自动计算结算字段（净值、份额、金额之间的联动）
function calculateSettlementFields() {
  if (!currentSettlement.value) return
  
  const isBuy = isBuyOrder.value
  const nav = parseFloat(settlementForm.value.confirmNav || '0')
  
  if (nav <= 0) return
  
  if (isBuy) {
    // 买入：按金额和净值计算份额（需要扣除手续费）
    const amount = parseFloat(settlementForm.value.confirmAmount || '0')
    const fee = parseFloat(settlementForm.value.confirmFee || '0')
    if (amount > 0) {
      const netAmount = amount - fee
      const shares = netAmount / nav
      settlementForm.value.confirmShares = shares.toFixed(6)
    }
  } else {
    // 赎回：按份额和净值计算金额
    const shares = parseFloat(settlementForm.value.confirmShares || '0')
    if (shares > 0) {
      const amount = shares * nav
      settlementForm.value.confirmAmount = amount.toFixed(2)
    }
  }
}

// 监听净值变化，自动计算
watch(() => settlementForm.value.confirmNav, () => {
  calculateSettlementFields()
})

// 监听份额变化（赎回时），自动计算金额
watch(() => settlementForm.value.confirmShares, () => {
  if (!isBuyOrder.value) {
    calculateSettlementFields()
  }
})

// 监听金额变化（买入时），自动计算份额
watch(() => settlementForm.value.confirmAmount, () => {
  if (isBuyOrder.value) {
    calculateSettlementFields()
  }
})

// 监听手续费变化（买入时），自动计算份额
watch(() => settlementForm.value.confirmFee, () => {
  if (isBuyOrder.value) {
    calculateSettlementFields()
  }
})

async function handleSubmitSettlement() {
  if (!currentSettlement.value) return

  try {
    submitting.value = true
    showLoadingToast({ message: '处理中...', forbidClick: true })

    await settlementApi.confirmSettlement({
      orderId: currentSettlement.value.orderId,
      confirmDate: settlementForm.value.confirmDate,
      navDate: settlementForm.value.navDate,
      confirmNav: parseFloat(settlementForm.value.confirmNav),
      confirmShares: parseFloat(settlementForm.value.confirmShares),
      confirmAmount: parseFloat(settlementForm.value.confirmAmount),
      confirmFee: parseFloat(settlementForm.value.confirmFee || '0'),
      isManualOverride: false,
      note: settlementForm.value.note,
    })

    closeToast()
    showSuccessToast('结算确认成功')
    showConfirmDialog.value = false
    await loadData()
    
    // 触发全局数据刷新
    window.dispatchEvent(new CustomEvent('data-refresh'))
  } catch (error: any) {
    closeToast()
    showFailToast(error.message || '操作失败')
  } finally {
    submitting.value = false
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

.confirm-popup {
  padding: 24px;
  height: 100%;
  overflow-y: auto;
}

.popup-title {
  font-size: 20px;
  font-weight: 600;
  text-align: center;
  margin: 0 0 24px 0;
  color: var(--text);
}

.form-actions {
  margin-top: 24px;
  padding: 0 16px;
}

.form-actions .van-button {
  height: 48px;
  font-size: 16px;
  font-weight: 600;
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
