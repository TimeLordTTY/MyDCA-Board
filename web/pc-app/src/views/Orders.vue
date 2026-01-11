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
      <div style="overflow: auto">
        <table>
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
              <td colspan="8" class="td-muted" style="text-align: center">加载中...</td>
            </tr>
            <tr v-else-if="orders.length === 0">
              <td colspan="8" class="td-muted" style="text-align: center">暂无订单</td>
            </tr>
            <tr v-for="order in orders" :key="order.orderId">
              <td class="mono">{{ order.orderId.slice(-8) }}</td>
              <td>
                <span class="tag blue">{{ getOrderTypeLabel(order.orderType) }}</span>
              </td>
              <td><b>{{ order.productId }}</b></td>
              <td>组合支付</td>
              <td class="right mono">{{ formatCurrency(order.amount) }}</td>
              <td class="right mono">{{ formatCurrency(order.feeEstimate) }}</td>
              <td>
                <span class="tag" :class="getOrderStatusTagClass(order.status)">
                  {{ getOrderStatusLabel(order.status) }}
                </span>
              </td>
              <td class="right">
                <button class="btn" @click="handleViewDetail(order)" style="margin-right: 8px">详情</button>
                <template v-if="order.status === 'PENDING'">
                  <button class="btn" @click="handleCancelOrder(order)">取消</button>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi, formatCurrency, formatDateTime, getOrderTypeLabel, getOrderStatusLabel, useAccountStore } from '@wealth-hub/shared'
import type { Order, OrderDetail, OrderFundingLine } from '@wealth-hub/shared'
import NewOrderModal from '../components/NewOrderModal.vue'

const accountStore = useAccountStore()

const loading = ref(false)
const orders = ref<Order[]>([])
const newOrderVisible = ref(false)
const detailVisible = ref(false)
const selectedOrder = ref<OrderDetail | null>(null)
const fundingLines = ref<OrderFundingLine[]>([])

function getOrderStatusTagClass(status: string): string {
  if (status === 'CONFIRMED') return 'green'
  if (status === 'PENDING') return 'orange'
  if (status === 'CANCELLED' || status === 'FAILED') return 'red'
  return 'gray'
}

async function loadOrders() {
  loading.value = true
  try {
    orders.value = await orderApi.getOrders()
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
  if (!account) return `账户ID: ${accountId}`
  const parent = account.parentAccountId
    ? accountStore.accountTree.find((a) => a.id === account.parentAccountId)
    : null
  return parent ? `${parent.accountName} / ${account.accountName}` : account.accountName
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

onMounted(() => {
  accountStore.fetchAccounts()
  loadOrders()
})
</script>
