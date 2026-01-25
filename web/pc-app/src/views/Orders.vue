<template>
  <div>
    <!-- 新建订单模态框 -->
    <NewOrderModal v-model="newOrderVisible" @success="loadOrders" />

    <!-- 订单详情模态框 -->
    <el-dialog v-model="detailVisible" title="订单详情" width="800px">
      <div v-if="selectedOrder">
        <div style="margin-bottom: 16px">
          <div><strong>订单ID：</strong>{{ selectedOrder.orderId }}</div>
          <div><strong>类型：</strong>{{ getOrderTypeLabel(selectedOrder.orderType) }}</div>
          <div><strong>产品ID：</strong>{{ selectedOrder.productId }}</div>
          <div><strong>金额：</strong>{{ formatCurrency(selectedOrder.amount) }}</div>
          <div><strong>份额：</strong>{{ selectedOrder.shares || '—' }}</div>
          <div><strong>状态：</strong>{{ getOrderStatusLabel(selectedOrder.status) }}</div>
          <div><strong>创建时间：</strong>{{ formatDateTime(selectedOrder.createdAt) }}</div>
        </div>
        <div class="divider"></div>
        <div style="margin-top: 16px">
          <h4>资金来源</h4>
          <table style="margin-top: 12px">
            <thead>
              <tr>
                <th>账户</th>
                <th class="right">金额</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="line in fundingLines" :key="line.id">
                <td>{{ getAccountName(line.accountId) }}</td>
                <td class="right mono">{{ formatCurrency(line.amount) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
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
              <th>资金来源</th>
              <th class="right">金额</th>
              <th class="right">费用</th>
              <th>状态</th>
              <th class="right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="8" class="td-muted" style="text-align: center; padding: 24px">加载中...</td>
            </tr>
            <tr v-else-if="orders.length === 0">
              <td colspan="8" class="td-muted" style="text-align: center; padding: 24px">暂无订单</td>
            </tr>
            <tr v-for="(order, index) in orders" :key="order.orderId" :class="{ 'row-even': index % 2 === 0 }">
              <td class="mono">{{ order.orderId.slice(-8) }}</td>
              <td>
                <span class="tag blue">{{ getOrderTypeLabel(order.orderType) }}</span>
              </td>
              <td>
                <div>{{ getProductDisplayName(order.productId) }}</div>
                <div style="font-size: 12px; color: #909399; margin-top: 2px">
                  {{ getChannelLabel(order.productId) }}
                </div>
              </td>
              <td>{{ getFundingSourceDisplay(order) }}</td>
              <td class="right mono">{{ formatCurrency(order.amount) }}</td>
              <td class="right mono">{{ formatCurrency(getOrderFee(order)) }}</td>
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
import { orderApi, navApi, formatCurrency, formatDateTime, getOrderTypeLabel, getOrderStatusLabel, useAccountStore, useProductStore } from '@wealth-hub/shared'
import type { Order, OrderDetail, OrderFundingLine } from '@wealth-hub/shared'
import NewOrderModal from '../components/NewOrderModal.vue'

const route = useRoute()

const accountStore = useAccountStore()
const productStore = useProductStore()

const loading = ref(false)
const orders = ref<(Order & { fundingLines?: OrderFundingLine[], productName?: string, channel?: string, fee?: number })[]>([])
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
    // 加载每个订单的详细信息（fundingLines、产品信息、费用）
    orders.value = await Promise.all(orderList.map(async (order) => {
      try {
        const detail = await orderApi.getOrder(order.orderId)
        const product = productStore.products.find(p => p.id === order.productId)
        // 费用优先级：settlement.confirmFee > detail.feeEstimate（实际费用） > order.feeEstimate
        const fee = detail.settlement?.confirmFee || (detail as any).feeEstimate || order.feeEstimate || 0
        return {
          ...order,
          fundingLines: detail.fundingLines || [],
          productName: product?.productName,
          channel: product?.channel,
          fee: fee
        }
      } catch (error) {
        // 如果获取详情失败，使用基本信息
        const product = productStore.products.find(p => p.id === order.productId)
        return {
          ...order,
          fundingLines: [],
          productName: product?.productName,
          channel: product?.channel,
          fee: order.feeEstimate || 0
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

function getAccountName(accountId: number): string {
  const account = accountStore.accountTree.find((a) => a.id === accountId)
  if (!account) {
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
  const parent = account.parentAccountId
    ? accountStore.accountTree.find((a) => a.id === account.parentAccountId)
    : null
  return parent ? `${parent.accountName}-${account.accountName}` : account.accountName
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

function getFundingSourceDisplay(order: Order & { fundingLines?: OrderFundingLine[] }): string {
  if (!order.fundingLines || order.fundingLines.length === 0) {
    return '—'
  }
  if (order.fundingLines.length === 1) {
    return getAccountName(order.fundingLines[0].accountId)
  }
  return `组合支付(${order.fundingLines.length}个账户)`
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

function handleSettle(order: Order) {
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
