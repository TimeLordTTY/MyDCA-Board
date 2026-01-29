<template>
  <div>
    <!-- 新建订单模态框 -->
    <NewOrderModal v-model="newOrderVisible" @success="loadOrders" />

    <!-- 订单详情模态框 -->
    <el-dialog v-model="detailVisible" title="订单详情" width="700px" class="order-detail-dialog">
      <div v-if="selectedOrder" class="order-detail-content">
        <!-- 基本信息 -->
        <div class="detail-section">
          <div class="detail-row">
            <span class="detail-label">订单ID</span>
            <span class="detail-value mono">{{ selectedOrder.orderId }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">类型</span>
            <span class="detail-value">
              <span class="tag blue">{{ getOrderTypeLabel(selectedOrder.orderType) }}</span>
            </span>
          </div>
          <div class="detail-row">
            <span class="detail-label">产品</span>
            <span class="detail-value">
              {{ getProductDisplayName(selectedOrder.productId) }}
              <span class="sub-text">({{ getProductCode(selectedOrder.productId) }})</span>
            </span>
          </div>
          <div class="detail-row">
            <span class="detail-label">状态</span>
            <span class="detail-value">
              <span class="tag" :class="getOrderStatusTagClass(selectedOrder.status)">
                {{ getOrderStatusLabel(selectedOrder.status) }}
              </span>
            </span>
          </div>
        </div>

        <div class="divider"></div>

        <!-- 交易信息 -->
        <div class="detail-section">
          <div class="detail-row">
            <span class="detail-label">金额</span>
            <span class="detail-value mono highlight-amount">{{ formatCurrency(selectedOrder.amount || 0) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">份额</span>
            <span class="detail-value mono">{{ selectedOrder.shares ? selectedOrder.shares.toFixed(4) : '—' }}</span>
          </div>
          <div class="detail-row" v-if="selectedOrder.settlement?.confirmNav">
            <span class="detail-label">净值</span>
            <span class="detail-value mono">{{ selectedOrder.settlement.confirmNav.toFixed(6) }}</span>
          </div>
          <div class="detail-row" v-if="selectedOrder.settlement?.navDate">
            <span class="detail-label">净值日期</span>
            <span class="detail-value">{{ selectedOrder.settlement.navDate }}</span>
          </div>
          <div class="detail-row" v-if="selectedOrder.expectedConfirmDate && !selectedOrder.settlement?.confirmDate">
            <span class="detail-label">预期确认日期</span>
            <span class="detail-value">{{ selectedOrder.expectedConfirmDate }}</span>
          </div>
          <div class="detail-row" v-if="selectedOrder.settlement?.confirmDate">
            <span class="detail-label">确认日期</span>
            <span class="detail-value">{{ selectedOrder.settlement.confirmDate }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">创建时间</span>
            <span class="detail-value">{{ formatDateTime(selectedOrder.createdAt) }}</span>
          </div>
        </div>

        <div class="divider"></div>

        <!-- 出金账户 (SOURCE) - 对于赎回/卖出显示 -->
        <div class="detail-section" v-if="sourceFundingLines.length > 0">
          <h4 class="section-title">{{ isBuyOrderType(selectedOrder.orderType) ? '资金来源' : '出金账户' }}</h4>
          <div class="funding-list">
            <div v-for="line in sourceFundingLines" :key="line.id" class="funding-item">
              <span class="funding-account">{{ getAccountName(line.accountId) }}</span>
              <span class="funding-amount mono">
                {{ isBuyOrderType(selectedOrder.orderType) ? formatCurrency(line.amount || 0) : (line.shares ? line.shares.toFixed(4) + '份' : formatCurrency(line.amount || 0)) }}
              </span>
            </div>
          </div>
        </div>

        <!-- 到账账户 (TARGET) - 对于赎回/卖出显示 -->
        <div class="detail-section" v-if="targetFundingLines.length > 0 && !isBuyOrderType(selectedOrder.orderType)">
          <h4 class="section-title">到账账户</h4>
          <div class="funding-list">
            <div v-for="line in targetFundingLines" :key="line.id" class="funding-item">
              <span class="funding-account">{{ getAccountName(line.accountId) }}</span>
              <span class="funding-amount mono">
                {{ formatCurrency(line.amount || 0) }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <div style="text-align: right">
          <el-button @click="detailVisible = false">关闭</el-button>
          <el-button
            v-if="selectedOrder && canSettle(selectedOrder)"
            type="primary"
            @click="handleSettleFromDetail"
          >
            结算
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 结算确认模态框 -->
    <el-dialog v-model="settlementVisible" title="订单结算" width="600px">
      <div v-if="settlementOrder">
        <el-form :model="settlementForm" label-width="120px">
          <el-form-item label="订单ID">
            <span>{{ settlementOrder.orderId }}</span>
          </el-form-item>
          <el-form-item label="产品">
            <span>{{ getProductDisplayName(settlementOrder.productId) }}</span>
          </el-form-item>
          <el-form-item label="订单类型">
            <span>{{ getOrderTypeLabel(settlementOrder.orderType) }}</span>
          </el-form-item>
          <el-form-item label="确认日期" required>
            <el-date-picker
              v-model="settlementForm.confirmDate"
              type="date"
              placeholder="选择确认日期"
              style="width: 100%"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
          <el-form-item label="净值日期" required>
            <el-date-picker
              v-model="settlementForm.navDate"
              type="date"
              placeholder="选择净值日期"
              style="width: 100%"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              @change="handleNavDateChange"
            />
          </el-form-item>
          <el-form-item label="净值" required>
            <el-input-number
              v-model="settlementForm.confirmNav"
              :min="0.0001"
              :precision="4"
              style="width: 100%"
              @change="calculateSettlement"
            />
          </el-form-item>
          <template v-if="isBuyType">
            <el-form-item label="订单金额">
              <span>{{ formatCurrency(settlementOrder.amount || 0) }}</span>
            </el-form-item>
            <el-form-item label="预计份额" v-if="settlementForm.confirmNav">
              <span style="color: #4ea4ff; font-weight: 600">
                {{ calculateShares(settlementOrder.amount || 0, settlementForm.confirmNav) }} 份
              </span>
            </el-form-item>
            <el-form-item label="确认份额" required>
              <el-input-number
                v-model="settlementForm.confirmShares"
                :min="0.0001"
                :precision="6"
                style="width: 100%"
              />
            </el-form-item>
          </template>
          <template v-else>
            <el-form-item label="订单份额">
              <span>{{ settlementOrder.shares || 0 }} 份</span>
            </el-form-item>
            <el-form-item label="预计金额" v-if="settlementForm.confirmNav">
              <span style="color: #4ea4ff; font-weight: 600">
                {{ formatCurrency(calculateAmount(settlementOrder.shares || 0, settlementForm.confirmNav)) }}
              </span>
            </el-form-item>
            <el-form-item label="确认金额" required>
              <el-input-number
                v-model="settlementForm.confirmAmount"
                :min="0.01"
                :precision="2"
                style="width: 100%"
              />
            </el-form-item>
          </template>
          <el-form-item label="手续费">
            <el-input-number
              v-model="settlementForm.confirmFee"
              :min="0"
              :precision="2"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="settlementForm.note" type="textarea" />
          </el-form-item>
        </el-form>
        <div style="margin-top: 20px; text-align: right">
          <el-button @click="settlementVisible = false">取消</el-button>
          <el-button type="primary" @click="handleConfirmSettlement">确认结算</el-button>
        </div>
      </div>
    </el-dialog>

    <div class="card">
      <div class="row-between">
        <div>
          <h3>
            订单列表
            <span class="tag orange tiny">待结算会进"今日建议"</span>
          </h3>
          <div class="sub">下单会先占用 reserved；确认结算后才真正影响余额/持仓。</div>
        </div>
        <el-button type="primary" @click="handleNewOrder">▦ 新建订单</el-button>
      </div>
      <div class="divider"></div>

      <!-- 订单列表 -->
      <div class="ledger-table-container hide-scrollbar">
        <table class="ledger-table">
          <thead>
            <tr>
              <th>订单ID</th>
              <th>类型</th>
              <th>标的</th>
              <th>账户</th>
              <th class="right">
                <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 2px;">
                  <div>份额</div>
                  <div style="font-size: 11px; color: #999; font-weight: normal;">金额</div>
                </div>
              </th>
              <th class="right">净值</th>
              <th>确认日期</th>
              <th>状态</th>
              <th class="right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="9" class="td-muted" style="text-align: center; padding: 24px">加载中...</td>
            </tr>
            <tr v-else-if="orders.length === 0">
              <td colspan="9" class="td-muted" style="text-align: center; padding: 24px">暂无订单</td>
            </tr>
            <tr v-for="(order, index) in orders" :key="order.orderId" :class="{ 'row-even': index % 2 === 0 }">
              <td class="mono">{{ order.orderId.slice(-8) }}</td>
              <td>
                <span class="tag blue">{{ getOrderTypeLabel(order.orderType) }}</span>
              </td>
              <td>
                <div>{{ getProductDisplayName(order.productId) }}</div>
                <div style="font-size: 12px; color: #909399; margin-top: 2px">
                  {{ getChannelLabel(order.productId) }} · {{ getProductCode(order.productId) }}
                </div>
              </td>
              <td>{{ getFundingSourceDisplay(order) }}</td>
              <td class="right">
                <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 4px;">
                  <div class="mono">{{ order.shares != null ? order.shares.toFixed(4) : '—' }}</div>
                  <div class="mono td-muted">{{ order.amount != null ? formatCurrency(order.amount) : '—' }}</div>
                </div>
              </td>
              <td class="right mono">
                {{ getOrderNav(order).nav }}
                <span v-if="getOrderNav(order).navDate" class="nav-date">({{ getOrderNav(order).navDate }})</span>
              </td>
              <td>{{ getOrderConfirmDate(order) }}</td>
              <td>
                <span class="tag" :class="getOrderStatusTagClass(order.status)">
                  {{ getOrderStatusLabel(order.status) }}
                </span>
              </td>
              <td class="right">
                <div style="display: flex; gap: 8px; justify-content: flex-end;">
                  <button class="btn" @click="handleViewDetail(order)">详情</button>
                  <template v-if="order.status === 'PENDING'">
                    <button class="btn" @click="handleCancelOrder(order)">取消</button>
                    <button 
                      v-if="canSettle(order)" 
                      class="btn primary" 
                      @click="handleSettle(order)"
                    >
                      结算
                    </button>
                  </template>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi, navApi, formatCurrency, formatDateTime, formatDate, getOrderTypeLabel, getOrderStatusLabel, useAccountStore, useProductStore } from '@wealth-hub/shared'
import type { Order, OrderDetail, OrderFundingLine } from '@wealth-hub/shared'
import NewOrderModal from '../components/NewOrderModal.vue'

const route = useRoute()

const accountStore = useAccountStore()
const productStore = useProductStore()

const loading = ref(false)
const orders = ref<(Order & { fundingLines?: OrderFundingLine[], productName?: string, channel?: string, fee?: number, settlement?: any, navData?: any })[]>([])
const newOrderVisible = ref(false)
const detailVisible = ref(false)
const selectedOrder = ref<OrderDetail | null>(null)
const fundingLines = ref<OrderFundingLine[]>([])

const settlementVisible = ref(false)
const settlementOrder = ref<Order | null>(null)
const settlementForm = ref({
  confirmDate: '',
  navDate: '',
  confirmNav: undefined as number | undefined,
  confirmShares: undefined as number | undefined,
  confirmAmount: undefined as number | undefined,
  confirmFee: 0,
  note: ''
})

const isBuyType = computed(() => {
  if (!settlementOrder.value) return false
  return settlementOrder.value.orderType === 'BUY' || settlementOrder.value.orderType === 'SUBSCRIPTION'
})

// 区分出金账户（SOURCE）和到账账户（TARGET）
const sourceFundingLines = computed(() => {
  return fundingLines.value.filter(line => !line.lineType || line.lineType === 'SOURCE')
})

const targetFundingLines = computed(() => {
  return fundingLines.value.filter(line => line.lineType === 'TARGET')
})

function getOrderStatusTagClass(status: string): string {
  if (status === 'CONFIRMED') return 'green'
  if (status === 'PENDING') return 'orange'
  if (status === 'CANCELLED' || status === 'FAILED') return 'red'
  return 'gray'
}

async function loadOrders() {
  loading.value = true
  try {
    const orderList = await orderApi.getOrders()
    // 加载每个订单的详细信息（fundingLines、产品信息、费用、结算信息、净值）
    orders.value = await Promise.all(orderList.map(async (order) => {
      try {
        const detail = await orderApi.getOrder(order.orderId)
        const product = productStore.products.find(p => p.id === order.productId)
        // 费用优先级：settlement.confirmFee > detail.feeEstimate（实际费用） > order.feeEstimate
        const fee = detail.settlement?.confirmFee || (detail as any).feeEstimate || order.feeEstimate || 0
        
        // 如果有净值日期但没有结算信息，尝试获取净值
        let navData = null
        if (order.expectedNavDate && !detail.settlement?.confirmNav) {
          try {
            navData = await navApi.getNavByDate(order.productId, order.expectedNavDate)
          } catch (e) {
            // 获取失败则忽略
          }
        }
        
        return {
          ...order,
          fundingLines: detail.fundingLines || [],
          productName: product?.productName,
          channel: product?.channel,
          fee: fee,
          settlement: detail.settlement || null,
          navData: navData // 保存获取的净值数据
        }
      } catch (error) {
        // 如果获取详情失败，使用基本信息
        const product = productStore.products.find(p => p.id === order.productId)
        return {
          ...order,
          fundingLines: [],
          productName: product?.productName,
          channel: product?.channel,
          fee: order.feeEstimate || 0,
          settlement: null,
          navData: null
        }
      }
    }))
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function handleNewOrder() {
  newOrderVisible.value = true
}

async function handleViewDetail(order: Order) {
  try {
    const detail = await orderApi.getOrder(order.orderId)
    selectedOrder.value = detail
    fundingLines.value = detail.fundingLines || []
    detailVisible.value = true
  } catch (error: any) {
    ElMessage.error(error.message || '加载详情失败')
  }
}

async function handleSettleFromDetail() {
  if (!selectedOrder.value) return
  const baseOrder = orders.value.find(o => o.orderId === selectedOrder.value!.orderId)
  if (!baseOrder) {
    ElMessage.error('未找到该订单的列表数据，无法结算')
    return
  }
  if (!canSettle(baseOrder)) {
    ElMessage.warning('当前状态不允许结算')
    return
  }
  detailVisible.value = false
  await handleSettle(baseOrder)
}

function getAccountName(accountId: number): string {
  // 递归搜索账户树
  function findInTree(accounts: any[]): any | null {
    for (const acc of accounts) {
      if (acc.id === accountId) return acc
      if (acc.children && acc.children.length > 0) {
        const found = findInTree(acc.children)
        if (found) return found
      }
    }
    return null
  }

  // 先在账户树中查找
  const account = findInTree(accountStore.accountTree)
  if (account) {
    if (account.parentAccountId) {
      const parent = findInTree(accountStore.accountTree)
      // 重新搜索父账户
      const parentAccount = accountStore.accounts.find(a => a.id === account.parentAccountId)
      if (parentAccount) {
        return `${parentAccount.accountName}-${account.accountName}`
      }
    }
    return account.accountName
  }

  // 尝试从扁平列表查找
  const flatAccount = accountStore.accounts.find((a) => a.id === accountId)
  if (flatAccount) {
    if (flatAccount.parentAccountId) {
      const parent = accountStore.accounts.find((a) => a.id === flatAccount.parentAccountId)
      if (parent) {
        return `${parent.accountName}-${flatAccount.accountName}`
      }
    }
    return flatAccount.accountName
  }
  return `账户ID: ${accountId}`
}

function getProductDisplayName(productId: number): string {
  const product = productStore.products.find(p => p.id === productId)
  if (!product) return `产品ID: ${productId}`
  return product.productName
}

function getChannelLabel(productId: number): string {
  const product = productStore.products.find(p => p.id === productId)
  if (!product) return ''
  return product.channel === 'OTC' ? '场外' : '场内'
}

function getProductCode(productId: number): string {
  const product = productStore.products.find(p => p.id === productId)
  return product?.productCode || ''
}

function getOrderNav(order: Order & { settlement?: any, navData?: any }): { nav: string, navDate?: string } {
  // 优先从结算信息中获取净值
  if (order.settlement?.confirmNav) {
    return {
      nav: order.settlement.confirmNav.toFixed(4),
      navDate: order.settlement.navDate
    }
  }
  // 如果有预加载的净值数据
  if (order.navData?.nav) {
    return {
      nav: order.navData.nav.toFixed(4),
      navDate: order.expectedNavDate
    }
  }
  // 如果订单有预期净值日期但没有结算或净值数据，显示待确认
  if (order.expectedNavDate) {
    return { nav: '待确认', navDate: order.expectedNavDate }
  }
  return { nav: '—' }
}

function getOrderConfirmDate(order: Order & { settlement?: any }): string {
  const settledDate = order.settlement?.confirmDate
  const expectedDate = order.expectedConfirmDate
  const v = settledDate || expectedDate
  return v ? formatDate(v) : '—'
}

function isBuyOrderType(orderType: string): boolean {
  return orderType === 'BUY' || orderType === 'SUBSCRIPTION'
}

function getFundingSourceDisplay(order: Order & { fundingLines?: OrderFundingLine[] }): string {
  if (!order.fundingLines || order.fundingLines.length === 0) {
    return '—'
  }
  
  // 区分 SOURCE 和 TARGET
  const sourceLines = order.fundingLines.filter(line => !line.lineType || line.lineType === 'SOURCE')
  const targetLines = order.fundingLines.filter(line => line.lineType === 'TARGET')
  
  // 如果是买入类型，显示资金来源
  if (isBuyOrderType(order.orderType)) {
    if (sourceLines.length === 1) {
      return getAccountName(sourceLines[0].accountId)
    }
    return sourceLines.length > 1 ? `组合支付(${sourceLines.length}个账户)` : '—'
  }
  
  // 如果是卖出/赎回类型，优先显示到账账户
  if (targetLines.length === 1) {
    return getAccountName(targetLines[0].accountId)
  } else if (targetLines.length > 1) {
    return `多账户到账(${targetLines.length}个)`
  }
  
  // 没有 TARGET 账户，显示 SOURCE（可能是旧数据）
  if (sourceLines.length === 1) {
    return getAccountName(sourceLines[0].accountId)
  }
  return sourceLines.length > 1 ? `多账户(${sourceLines.length}个)` : '—'
}

function getOrderFee(order: Order & { fee?: number }): number {
  return order.fee || order.feeEstimate || 0
}

function canSettle(order: Order): boolean {
  if (order.status !== 'PENDING') return false
  if (!order.expectedConfirmDate) return true
  const today = new Date().toISOString().split('T')[0]
  return order.expectedConfirmDate <= today
}

async function handleSettle(order: Order) {
  settlementOrder.value = order
  const today = new Date().toISOString().split('T')[0]
  settlementForm.value = {
    confirmDate: order.expectedConfirmDate || today,
    navDate: order.expectedNavDate || today,
    confirmNav: undefined,
    confirmShares: undefined,
    confirmAmount: undefined,
    confirmFee: order.feeEstimate || 0,
    note: ''
  }
  settlementVisible.value = true
  
  // 自动获取净值（如果有净值日期）
  if (settlementForm.value.navDate) {
    await handleNavDateChange()
  }
}

async function handleNavDateChange() {
  if (!settlementForm.value.navDate || !settlementOrder.value) return
  // 自动获取净值
  try {
    const navData = await navApi.getNavByDate(settlementOrder.value.productId, settlementForm.value.navDate)
    if (navData && navData.nav) {
      settlementForm.value.confirmNav = navData.nav
      calculateSettlement()
    } else {
      ElMessage.warning('未找到该日期的净值数据，请手动输入')
    }
  } catch (error: any) {
    ElMessage.warning('获取净值失败，请手动输入')
  }
}

function calculateSettlement() {
  if (!settlementForm.value.confirmNav || !settlementOrder.value) return
  if (isBuyType.value && settlementOrder.value.amount) {
    settlementForm.value.confirmShares = calculateShares(settlementOrder.value.amount, settlementForm.value.confirmNav)
  } else if (!isBuyType.value && settlementOrder.value.shares) {
    settlementForm.value.confirmAmount = calculateAmount(settlementOrder.value.shares, settlementForm.value.confirmNav)
  }
}

function calculateShares(amount: number, nav: number): number {
  if (!nav || nav <= 0) return 0
  return amount / nav
}

function calculateAmount(shares: number, nav: number): number {
  return shares * nav
}

async function handleConfirmSettlement() {
  if (!settlementOrder.value) return
  if (!settlementForm.value.confirmDate || !settlementForm.value.navDate || !settlementForm.value.confirmNav) {
    ElMessage.error('请填写确认日期、净值日期和净值')
    return
  }
  if (isBuyType.value && !settlementForm.value.confirmShares) {
    ElMessage.error('请填写确认份额')
    return
  }
  if (!isBuyType.value && !settlementForm.value.confirmAmount) {
    ElMessage.error('请填写确认金额')
    return
  }

  try {
    await orderApi.confirmSettlement({
      orderId: settlementOrder.value.orderId,
      confirmDate: settlementForm.value.confirmDate,
      navDate: settlementForm.value.navDate,
      confirmNav: settlementForm.value.confirmNav,
      confirmShares: settlementForm.value.confirmShares,
      confirmAmount: settlementForm.value.confirmAmount,
      confirmFee: settlementForm.value.confirmFee || 0,
      note: settlementForm.value.note
    })
    ElMessage.success('结算成功')
    settlementVisible.value = false
    await loadOrders()
  } catch (error: any) {
    ElMessage.error(error.message || '结算失败')
  }
}

async function handleCancelOrder(order: Order) {
  ElMessageBox.confirm(`确定要取消订单"${order.orderId}"吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await orderApi.cancelOrder(order.orderId)
      ElMessage.success('取消成功')
      await loadOrders()
    } catch (error: any) {
      ElMessage.error(error.message || '取消失败')
    }
  })
}

onMounted(async () => {
  accountStore.fetchAccounts()
  productStore.fetchProducts()
  await loadOrders()
  
  // 检查URL参数，如果有settle参数，自动打开结算对话框
  const settleOrderId = route.query.settle as string
  if (settleOrderId) {
    const order = orders.value.find(o => o.orderId === settleOrderId)
    if (order && canSettle(order)) {
      handleSettle(order)
      // 清除URL参数
      window.history.replaceState({}, '', '/orders')
    }
  }
})
</script>

<style scoped>
/* 订单详情弹窗样式 */
.order-detail-content {
  padding: 8px 0;
}

.detail-section {
  margin-bottom: 16px;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.detail-row:last-child {
  border-bottom: none;
}

.detail-label {
  color: #606266;
  font-size: 14px;
  min-width: 80px;
}

.detail-value {
  color: #303133;
  font-size: 14px;
  text-align: right;
  flex: 1;
}

.detail-value .sub-text {
  color: #909399;
  font-size: 12px;
  margin-left: 8px;
}

.highlight-amount {
  color: #4ea4ff;
  font-weight: 600;
  font-size: 16px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.funding-list {
  background: #f8f9fa;
  border-radius: 8px;
  padding: 12px;
}

.funding-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px dashed rgba(0, 0, 0, 0.08);
}

.funding-item:last-child {
  border-bottom: none;
}

.funding-account {
  color: #606266;
  font-size: 13px;
}

.funding-amount {
  color: #303133;
  font-weight: 500;
}

.mono {
  font-family: 'SF Mono', Monaco, Consolas, 'Liberation Mono', monospace;
}

.nav-date {
  color: #909399;
  font-size: 12px;
  margin-left: 4px;
}

.divider {
  height: 1px;
  background: #ebeef5;
  margin: 16px 0;
}
</style>
