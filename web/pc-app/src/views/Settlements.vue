<template>
  <div>
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
              <th>订单ID</th>
              <th>类型</th>
              <th>标的</th>
              <th class="right">金额</th>
              <th>预期确认日期</th>
              <th class="right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="6" class="td-muted" style="text-align: center">加载中...</td>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { settlementApi, getOrderTypeLabel, formatCurrency, formatDate } from '@wealth-hub/shared'
import type { Order } from '@wealth-hub/shared'

const loading = ref(false)
const pendingSettlements = ref<Order[]>([])

async function loadSettlements() {
  loading.value = true
  try {
    pendingSettlements.value = await settlementApi.getPendingSettlements()
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function handleConfirmSettlement(settlement: Order) {
  ElMessage.info('结算确认功能开发中')
}

onMounted(() => {
  loadSettlements()
})
</script>
