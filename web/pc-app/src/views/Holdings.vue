<template>
  <div class="products-page-container">
    <!-- 持仓详情模态框 -->
    <HoldingDetailModal v-model="detailVisible" :holding="selectedHolding" />

    <!-- 初始持仓导入对话框 -->
    <InitialHoldingImportModal v-model="importDialogVisible" @success="handleImportSuccess" />

    <div class="card" style="flex: 1; min-height: 0; display: flex; flex-direction: column;">
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

      <!-- 左右分栏：左=场内，右=场外（结构/风格对齐“产品管理”页面） -->
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; flex: 1; min-height: 0; overflow: hidden;">
        <!-- 左侧：场内产品 -->
        <div class="card" style="padding: 12px; display: flex; flex-direction: column; min-height: 0;">
          <div class="row-gap" style="margin-bottom: 8px; flex-shrink: 0;">
            <h3 style="margin: 0; font-size: 14px; font-weight: 600;">场内产品</h3>
          </div>
          <div class="hide-scrollbar holdings-scroll" style="flex: 1; overflow: auto; min-height: 0;">
            <table>
              <thead>
                <tr>
                  <th>名称</th>
                  <th class="right">份额 / 市值</th>
                  <th class="right">成本价 / 最新价</th>
                  <th class="right">盈亏率 / 浮盈亏</th>
                  <th class="right" style="min-width: 80px; white-space: nowrap;">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="loading">
                  <td colspan="5" class="td-muted" style="text-align: center">加载中...</td>
                </tr>
                <tr v-else-if="exchangeHoldings.length === 0">
                  <td colspan="5" class="td-muted" style="text-align: center">暂无场内持仓</td>
                </tr>
                <tr v-for="holding in exchangeHoldings" :key="holding.productId">
                  <!-- 名称 + 代码（灰显） -->
                  <td>
                    <div class="holding-name-cell">
                      <b class="holding-name">{{ holding.productName || `产品${holding.productId}` }}</b>
                      <div style="font-size: 12px; color: #999; font-style: italic; margin-top: 4px;" class="mono">
                        {{ holding.productCode || '—' }}
                      </div>
                    </div>
                  </td>

                  <!-- 份额 + 市值（上下） -->
                  <td class="right">
                    <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 4px;">
                      <div class="mono">{{ formatNumber(holding.sharesCalc, 2) }}</div>
                      <div class="mono td-muted">{{ formatCurrency(holding.marketValue) }}</div>
                    </div>
                  </td>

                  <!-- 成本价（黑）+ 最新价（红/绿） -->
                  <td class="right">
                    <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 4px;">
                      <div class="mono" style="color: #0f172a;">{{ formatNumber(holding.avgCostCalc, 4) }}</div>
                      <div class="mono" :style="{ color: priceColor(holding.currentPrice, holding.avgCostCalc) }">
                        <span v-if="holding.currentPrice > 0">{{ formatNumber(holding.currentPrice, 4) }}</span>
                        <span v-else class="td-muted">—</span>
                      </div>
                    </div>
                  </td>

                  <!-- 盈亏率（红/绿）+ 浮盈亏（红/绿） -->
                  <td class="right">
                    <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 6px;">
                      <div class="mono" :style="{ color: pnlColor(holding.unrealizedPnl) }">
                        {{ pnlRateText(holding.unrealizedPnl, holding.totalCostCalc) }}
                      </div>
                      <div :style="{ color: pnlColor(holding.unrealizedPnl), fontWeight: 600 }">
                        {{ formatSignedCurrency(holding.unrealizedPnl) }}
                      </div>
                    </div>
                  </td>

                  <td class="right" style="white-space: nowrap;">
                    <button class="btn-small" @click="handleViewDetail(holding)">详情</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- 右侧：场外产品 -->
        <div class="card" style="padding: 12px; display: flex; flex-direction: column; min-height: 0;">
          <div class="row-gap" style="margin-bottom: 8px; flex-shrink: 0;">
            <h3 style="margin: 0; font-size: 14px; font-weight: 600;">场外产品</h3>
          </div>
          <div class="hide-scrollbar holdings-scroll" style="flex: 1; overflow: auto; min-height: 0;">
            <table>
              <thead>
                <tr>
                  <th>名称</th>
                  <th class="right">份额 / 市值</th>
                  <th class="right">成本价 / 最新价</th>
                  <th class="right">盈亏率 / 浮盈亏</th>
                  <th class="right" style="min-width: 80px; white-space: nowrap;">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="loading">
                  <td colspan="5" class="td-muted" style="text-align: center">加载中...</td>
                </tr>
                <tr v-else-if="otcHoldings.length === 0">
                  <td colspan="5" class="td-muted" style="text-align: center">暂无场外持仓</td>
                </tr>
                <tr v-for="holding in otcHoldings" :key="holding.productId">
                  <td>
                    <div class="holding-name-cell">
                      <b class="holding-name">{{ holding.productName || `产品${holding.productId}` }}</b>
                      <div style="font-size: 12px; color: #999; font-style: italic; margin-top: 4px;" class="mono">
                        {{ holding.productCode || '—' }}
                      </div>
                    </div>
                  </td>

                  <td class="right">
                    <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 4px;">
                      <div class="mono">{{ formatNumber(holding.sharesCalc, 2) }}</div>
                      <div class="mono td-muted">{{ formatCurrency(holding.marketValue) }}</div>
                    </div>
                  </td>

                  <td class="right">
                    <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 4px;">
                      <div class="mono" style="color: #0f172a;">{{ formatNumber(holding.avgCostCalc, 4) }}</div>
                      <div class="mono" :style="{ color: priceColor(holding.currentPrice, holding.avgCostCalc) }">
                        <span v-if="holding.currentPrice > 0">{{ formatNumber(holding.currentPrice, 4) }}</span>
                        <span v-else class="td-muted">—</span>
                      </div>
                    </div>
                  </td>

                  <td class="right">
                    <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 6px;">
                      <div class="mono" :style="{ color: pnlColor(holding.unrealizedPnl) }">
                        {{ pnlRateText(holding.unrealizedPnl, holding.totalCostCalc) }}
                      </div>
                      <div :style="{ color: pnlColor(holding.unrealizedPnl), fontWeight: 600 }">
                        {{ formatSignedCurrency(holding.unrealizedPnl) }}
                      </div>
                    </div>
                  </td>

                  <td class="right" style="white-space: nowrap;">
                    <button class="btn-small" @click="handleViewDetail(holding)">详情</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
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
    const avgCost = holding.averageCost || holding.avgCost || 0
    const totalCost = shares * avgCost
    const marketValue = shares * currentPrice
    const unrealizedPnl = marketValue - totalCost
    const pctChg = quote?.pctChg
    
    return {
      ...holding,
      sharesCalc: shares,
      avgCostCalc: avgCost,
      totalCostCalc: totalCost,
      currentPrice,
      marketValue,
      unrealizedPnl,
      pctChg,
      quote,
      nav,
    }
  })
})

const exchangeHoldings = computed(() =>
  holdingsWithQuote.value
    .filter(h => h.channel === 'EXCHANGE')
    .slice()
    .sort((a, b) => (Number(b.unrealizedPnl) || 0) - (Number(a.unrealizedPnl) || 0))
)
const otcHoldings = computed(() =>
  holdingsWithQuote.value
    .filter(h => h.channel !== 'EXCHANGE')
    .slice()
    .sort((a, b) => (Number(b.unrealizedPnl) || 0) - (Number(a.unrealizedPnl) || 0))
)

function pnlColor(pnl: number) {
  // 中国股市习惯：红涨绿跌（盈利红色、亏损绿色）
  return (pnl || 0) >= 0 ? 'var(--good)' : 'var(--bad)'
}

function priceColor(currentPrice: number, costPrice: number) {
  if (!currentPrice || currentPrice <= 0) return 'var(--muted)'
  if (!costPrice || costPrice <= 0) return '#0f172a'
  // 中国股市习惯：红涨绿跌
  return currentPrice >= costPrice ? 'var(--good)' : 'var(--bad)'
}

function pnlRateText(pnl: number, totalCost: number) {
  const cost = totalCost || 0
  if (!cost || cost <= 0) return '—'
  const rate = (pnl || 0) / cost
  return `${rate >= 0 ? '+' : ''}${formatNumber(rate * 100, 2)}%`
}

function formatSignedCurrency(amount: number) {
  const v = Number(amount) || 0
  const sign = v >= 0 ? '+' : '-'
  return `${sign}${formatCurrency(Math.abs(v))}`
}

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
      // 后端可能返回 { [productId]: HoldingInfo } 的对象结构，Object.values 会丢失 key
      holdings.value = Object.entries(holdingsData as Record<string, any>).map(([productId, v]) => ({
        ...v,
        productId: v?.productId ?? Number(productId),
      }))
    } else {
      holdings.value = []
    }
    
    // 加载实时行情和净值（场内优先实时行情；场外使用净值）
    if (holdings.value.length > 0) {
      const exchangeIds = holdings.value
        .filter(h => h.channel === 'EXCHANGE')
        .map(h => Number(h.productId))
        .filter(id => Number.isFinite(id) && id > 0)
      const otcIds = holdings.value
        .filter(h => h.channel !== 'EXCHANGE')
        .map(h => Number(h.productId))
        .filter(id => Number.isFinite(id) && id > 0)
      
      if (exchangeIds.length === 0 && otcIds.length === 0) {
        console.warn('持仓数据缺少productId，跳过行情/净值加载', holdings.value)
        return
      }
      
      // 场内：批量获取实时行情
      if (exchangeIds.length > 0) {
        try {
          const quotesData = await marketApi.getRealtimeQuotes(exchangeIds)
          quotes.value = new Map(quotesData.map(q => [q.productId, q]))
        } catch (error: any) {
          console.warn('加载实时行情失败:', error)
        }
      }
      
      // 场外：批量获取最新净值（场内去调 nav/latest 会 404，避免无意义请求）
      if (otcIds.length > 0) {
        try {
          const navPromises = otcIds.map(id => navApi.getLatestNav(id))
          const navsData = await Promise.all(navPromises)
          navs.value = new Map(
            navsData.filter(n => n !== null).map(n => [n!.productId, n!])
          )
        } catch (error: any) {
          console.warn('加载净值失败:', error)
        }
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

<style scoped>
.holdings-scroll table {
  /* 避免被压缩得太窄导致内容“奇怪换行/截断” */
  min-width: 520px;
}

.holding-name-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.holding-name {
  white-space: normal;
  word-break: break-word;
  line-height: 1.3;
}
</style>
