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
import { ElNotification } from 'element-plus'
import { settlementApi, getOrderTypeLabel, formatCurrency, formatDate } from '@wealth-hub/shared'
import type { Order } from '@wealth-hub/shared'
import SettlementConfirmModal from '../components/SettlementConfirmModal.vue'

const loading = ref(false)
const pendingSettlements = ref<Order[]>([])

async function loadSettlements() {
  loading.value = true
  try {
    pendingSettlements.value = await settlementApi.getPendingSettlements()
  } catch (error: any) {
    ElNotification.error({ title: '错误', message: error.message || '加载失败', position: 'bottom-right' })
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
})
</script>
