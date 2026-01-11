<template>
  <div>
    <div class="card">
      <div class="row-between">
        <div>
          <h3>
            持仓管理
            <span class="tag blue tiny">实时计算</span>
          </h3>
          <div class="sub">买入/卖出来自"订单&结算"。持仓数据基于流水实时计算。</div>
        </div>
      </div>
      <div class="divider"></div>

      <div style="overflow: auto">
        <table>
          <thead>
            <tr>
              <th>标的</th>
              <th>代码</th>
              <th class="right">份额</th>
              <th class="right">均价</th>
              <th class="right">现价</th>
              <th class="right">市值</th>
              <th class="right">浮盈亏</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="7" class="td-muted" style="text-align: center">加载中...</td>
            </tr>
            <tr v-else-if="holdings.length === 0">
              <td colspan="7" class="td-muted" style="text-align: center">暂无持仓，去"订单&结算"买一笔试试。</td>
            </tr>
            <tr v-for="holding in holdings" :key="holding.productId">
              <td><b>{{ holding.productName || `产品${holding.productId}` }}</b></td>
              <td class="mono">{{ holding.productCode || '—' }}</td>
              <td class="right mono">{{ formatNumber(holding.totalShares || holding.shares || 0, 2) }}</td>
              <td class="right mono">{{ formatNumber(holding.averageCost || holding.avgCost || 0, 4) }}</td>
              <td class="right mono">{{ formatNumber(holding.marketValue && holding.totalShares ? holding.marketValue / holding.totalShares : 0, 4) }}</td>
              <td class="right mono">{{ formatCurrency(holding.marketValue) }}</td>
              <td class="right">
                <span class="chip" :class="(holding.unrealizedPnl || 0) >= 0 ? 'good' : 'bad'">
                  {{ (holding.unrealizedPnl || 0) >= 0 ? '+' : '' }}{{ formatCurrency(holding.unrealizedPnl) }}
                </span>
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
import { holdingApi, formatCurrency, formatNumber } from '@wealth-hub/shared'
import type { HoldingInfo } from '@wealth-hub/shared'

const loading = ref(false)
const holdings = ref<HoldingInfo[]>([])

async function loadHoldings() {
  loading.value = true
  try {
    holdings.value = await holdingApi.getHoldings()
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadHoldings()
})
</script>
