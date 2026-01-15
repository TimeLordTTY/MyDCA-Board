<template>
  <div>
    <!-- 持仓详情模态框 -->
    <HoldingDetailModal v-model="detailVisible" :holding="selectedHolding" />

    <!-- 初始持仓导入对话框 -->
    <InitialHoldingImportModal v-model="importDialogVisible" @success="handleImportSuccess" />

    <div class="card">
      <div class="row-between">
        <div>
          <h3>
            持仓管理
            <span class="tag blue tiny">实时计算</span>
          </h3>
          <div class="sub">买入/卖出来自"订单&结算"。持仓数据基于流水实时计算。</div>
        </div>
        <div>
          <el-button type="primary" @click="handleOpenImport">导入初始持仓</el-button>
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
              <th class="right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="8" class="td-muted" style="text-align: center">加载中...</td>
            </tr>
            <tr v-else-if="holdings.length === 0">
              <td colspan="8" class="td-muted" style="text-align: center">暂无持仓，去"订单&结算"买一笔试试。</td>
            </tr>
            <tr v-for="holding in holdingsWithQuote" :key="holding.productId">
              <td><b>{{ holding.productName || `产品${holding.productId}` }}</b></td>
              <td class="mono">{{ holding.productCode || '—' }}</td>
              <td class="right mono">{{ formatNumber(holding.totalShares || holding.shares || 0, 2) }}</td>
              <td class="right mono">{{ formatNumber(holding.averageCost || holding.avgCost || 0, 4) }}</td>
              <td class="right mono">
                <span v-if="holding.currentPrice > 0">{{ formatNumber(holding.currentPrice, 4) }}</span>
                <span v-else class="td-muted">—</span>
                <span v-if="holding.pctChg !== undefined && holding.pctChg !== null" 
                      :class="holding.pctChg >= 0 ? 'text-green' : 'text-red'"
                      style="margin-left: 8px; font-size: 0.9em;">
                  {{ holding.pctChg >= 0 ? '+' : '' }}{{ formatNumber(holding.pctChg, 2) }}%
                </span>
              </td>
              <td class="right mono">{{ formatCurrency(holding.marketValue) }}</td>
              <td class="right">
                <span class="chip" :class="(holding.unrealizedPnl || 0) >= 0 ? 'good' : 'bad'">
                  {{ (holding.unrealizedPnl || 0) >= 0 ? '+' : '' }}{{ formatCurrency(holding.unrealizedPnl) }}
                </span>
              </td>
              <td class="right">
                <button class="btn" @click="handleViewDetail(holding)">详情</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { holdingApi, marketApi, navApi, formatCurrency, formatNumber } from '@wealth-hub/shared'
import type { HoldingInfo, MarketQuoteRealtime, Nav } from '@wealth-hub/shared'
import HoldingDetailModal from '../components/HoldingDetailModal.vue'
import InitialHoldingImportModal from '../components/InitialHoldingImportModal.vue'

const loading = ref(false)
const holdings = ref<HoldingInfo[]>([])
const quotes = ref<Map<number, MarketQuoteRealtime>>(new Map())
const navs = ref<Map<number, Nav>>(new Map())
const detailVisible = ref(false)
const selectedHolding = ref<any>(null)
const importDialogVisible = ref(false)

// 合并持仓和行情数据
const holdingsWithQuote = computed(() => {
  return holdings.value.map(holding => {
    const quote = quotes.value.get(holding.productId)
    const nav = navs.value.get(holding.productId)
    
    // 优先使用实时行情价格，其次使用净值
    let currentPrice = 0
    if (quote) {
      currentPrice = quote.price
    } else if (nav) {
      currentPrice = nav.nav
    }
    
    const shares = holding.totalShares || holding.shares || 0
    const marketValue = shares * currentPrice
    const avgCost = holding.averageCost || holding.avgCost || 0
    const totalCost = shares * avgCost
    const unrealizedPnl = marketValue - totalCost
    const pctChg = quote?.pctChg
    
    return {
      ...holding,
      currentPrice,
      marketValue,
      unrealizedPnl,
      pctChg,
      quote,
      nav,
    }
  })
})

async function loadHoldings() {
  loading.value = true
  try {
    const holdingsData = await holdingApi.getHoldings()
    
    // 处理返回的数据（可能是Map或数组）
    if (holdingsData instanceof Map) {
      holdings.value = Array.from(holdingsData.values())
    } else if (Array.isArray(holdingsData)) {
      holdings.value = holdingsData
    } else if (typeof holdingsData === 'object' && holdingsData !== null) {
      holdings.value = Object.values(holdingsData)
    } else {
      holdings.value = []
    }
    
    // 加载实时行情和净值
    if (holdings.value.length > 0) {
      const productIds = holdings.value.map(h => h.productId)
      
      // 批量获取实时行情
      try {
        const quotesData = await marketApi.getRealtimeQuotes(productIds)
        quotes.value = new Map(quotesData.map(q => [q.productId, q]))
      } catch (error: any) {
        console.warn('加载实时行情失败:', error)
      }
      
      // 批量获取最新净值（作为备用）
      try {
        const navPromises = productIds.map(id => navApi.getLatestNav(id))
        const navsData = await Promise.all(navPromises)
        navs.value = new Map(
          navsData.filter(n => n !== null).map(n => [n!.productId, n!])
        )
      } catch (error: any) {
        console.warn('加载净值失败:', error)
      }
    }
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function handleViewDetail(holding: any) {
  selectedHolding.value = holding
  detailVisible.value = true
}

function handleOpenImport() {
  importDialogVisible.value = true
}

function handleImportSuccess() {
  // 导入成功后重新加载持仓
  loadHoldings()
}

onMounted(() => {
  loadHoldings()
})
</script>
