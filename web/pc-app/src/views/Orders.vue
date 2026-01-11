<template>
  <div>
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
                <template v-if="order.status === 'PENDING'">
                  <button class="btn" @click="handleCancelOrder(order)">取消</button>
                </template>
                <span v-else class="td-muted">—</span>
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
import { orderApi, formatCurrency, getOrderTypeLabel, getOrderStatusLabel } from '@wealth-hub/shared'
import type { Order } from '@wealth-hub/shared'

const loading = ref(false)
const orders = ref<Order[]>([])

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
  ElMessage.info('新建订单功能开发中')
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
  loadOrders()
})
</script>
