<template>
  <div class="holdings-page">
    <van-nav-bar title="持仓" fixed placeholder>
      <template #right>
        <van-icon name="refresh" @click="onRefresh" />
      </template>
    </van-nav-bar>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <div class="page-container">
        <!-- 持仓汇总卡片 -->
        <div class="summary-card" v-if="holdings.length > 0">
          <div class="summary-item">
            <div class="summary-label">持仓市值</div>
            <div class="summary-value">{{ formatCurrency(totalMarketValue) }}</div>
          </div>
          <div class="summary-item">
            <div class="summary-label">浮动盈亏</div>
            <div class="summary-value" :class="{ 'text-good': totalUnrealizedPnl >= 0, 'text-bad': totalUnrealizedPnl < 0 }">
              {{ formatCurrency(totalUnrealizedPnl) }}
            </div>
          </div>
        </div>

        <van-empty v-if="!loading && holdings.length === 0" description="暂无持仓" />

        <div v-else class="holding-list">
          <div
            v-for="holding in holdings"
            :key="holding.productId"
            class="holding-card"
            @click="showHoldingDetail(holding)"
          >
            <div class="card-header">
              <div class="product-info">
                <div class="product-name">{{ holding.productName }}</div>
                <div class="product-code" v-if="holding.productCode">
                  {{ holding.productCode }}
                </div>
              </div>
              <van-icon name="arrow" />
            </div>

            <div class="card-body">
              <div class="holding-row">
                <div class="row-item">
                  <div class="row-label">持仓份额</div>
                  <div class="row-value">{{ formatNumber(holding.totalShares || 0, 2) }}</div>
                </div>
                <div class="row-item">
                  <div class="row-label">平均成本</div>
                  <div class="row-value">{{ formatNumber(holding.averageCost || 0, 4) }}</div>
                </div>
              </div>

              <div class="holding-row">
                <div class="row-item">
                  <div class="row-label">当前价格</div>
                  <div class="row-value">{{ formatNumber(holding.currentPrice || 0, 4) }}</div>
                </div>
                <div class="row-item">
                  <div class="row-label">持仓市值</div>
                  <div class="row-value">{{ formatCurrency(holding.marketValue || 0) }}</div>
                </div>
              </div>

              <div class="pnl-section">
                <div class="pnl-label">浮动盈亏</div>
                <div class="pnl-value" :class="{ 'text-good': (holding.unrealizedPnl || 0) >= 0, 'text-bad': (holding.unrealizedPnl || 0) < 0 }">
                  {{ formatCurrency(holding.unrealizedPnl || 0) }}
                </div>
                <div class="pnl-percent" :class="{ 'text-good': getPnlPercent(holding) >= 0, 'text-bad': getPnlPercent(holding) < 0 }">
                  {{ getPnlPercent(holding) >= 0 ? '+' : '' }}{{ formatNumber(getPnlPercent(holding), 2) }}%
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </van-pull-refresh>

    <!-- 持仓详情弹窗 -->
    <van-popup
      v-model:show="showDetailDialog"
      position="bottom"
      :style="{ height: '85%' }"
      round
      closeable
    >
      <div class="detail-popup" v-if="currentHolding">
        <h3 class="popup-title">持仓详情</h3>
        <div class="detail-content">
          <van-cell-group inset>
            <van-cell title="产品名称" :value="currentHolding.productName" />
            <van-cell title="产品代码" :value="currentHolding.productCode || '—'" />
            <van-cell title="持仓份额" :value="formatNumber(currentHolding.totalShares || 0, 2)" />
            <van-cell title="平均成本" :value="formatNumber(currentHolding.averageCost || currentHolding.avgCost || 0, 4)" />
            <van-cell title="当前价格" :value="formatNumber(currentHolding.currentPrice || 0, 4)" />
            <van-cell title="持仓市值" :value="formatCurrency(currentHolding.marketValue || 0)" />
            <van-cell 
              title="浮动盈亏" 
              :value="formatCurrency(currentHolding.unrealizedPnl || 0)"
              :value-class="(currentHolding.unrealizedPnl || 0) >= 0 ? 'text-good' : 'text-bad'"
            />
            <van-cell 
              title="盈亏比例" 
              :value="`${getPnlPercent(currentHolding) >= 0 ? '+' : ''}${formatNumber(getPnlPercent(currentHolding), 2)}%`"
              :value-class="getPnlPercent(currentHolding) >= 0 ? 'text-good' : 'text-bad'"
            />
          </van-cell-group>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { holdingApi, marketApi, navApi } from '@wealth-hub/shared'
import { formatCurrency, formatNumber } from '@wealth-hub/shared'
import { showFailToast } from 'vant'
import type { HoldingInfo, MarketQuoteRealtime, Nav } from '@wealth-hub/shared'

const refreshing = ref(false)
const loading = ref(false)
const showDetailDialog = ref(false)

const rawHoldings = ref<HoldingInfo[]>([])
const quotes = ref<Map<number, MarketQuoteRealtime>>(new Map())
const navs = ref<Map<number, Nav>>(new Map())
const currentHolding = ref<any>(null)

// 合并持仓和行情数据
const holdings = computed(() => {
  return rawHoldings.value.map(holding => {
    const quote = quotes.value.get(holding.productId)
    const nav = navs.value.get(holding.productId)
    
    // 优先使用实时行情价格，其次使用净值
    let currentPrice = 0
    if (quote && quote.price > 0) {
      currentPrice = quote.price
    } else if (nav && nav.nav > 0) {
      currentPrice = nav.nav
    }
    
    const shares = Number(holding.totalShares || holding.shares || 0)
    const avgCost = Number(holding.averageCost || holding.avgCost || 0)
    const totalCost = shares * avgCost
    const marketValue = currentPrice > 0 ? shares * currentPrice : 0
    const unrealizedPnl = marketValue - totalCost
    
    return {
      ...holding,
      currentPrice,
      totalShares: shares,
      averageCost: avgCost,
      totalCost,
      marketValue,
      unrealizedPnl,
    }
  })
})

const totalMarketValue = computed(() => {
  return holdings.value.reduce((sum, h) => sum + (h.marketValue || 0), 0)
})

const totalUnrealizedPnl = computed(() => {
  return holdings.value.reduce((sum, h) => sum + (h.unrealizedPnl || 0), 0)
})

async function loadData() {
  try {
    loading.value = true
    const holdingsData = await holdingApi.getHoldings()
    
    // 处理返回的数据（可能是Map或数组）
    let holdingsList: HoldingInfo[] = []
    if (Array.isArray(holdingsData)) {
      holdingsList = holdingsData
    } else if (typeof holdingsData === 'object' && holdingsData !== null) {
      holdingsList = Object.entries(holdingsData as Record<string, any>).map(([productId, v]) => ({
        ...v,
        productId: v?.productId ?? Number(productId),
      }))
    }
    
    rawHoldings.value = holdingsList
    
    // 加载行情和净值数据
    if (holdingsList.length > 0) {
      const exchangeIds = holdingsList
        .filter(h => h.channel === 'EXCHANGE')
        .map(h => Number(h.productId))
        .filter(id => Number.isFinite(id) && id > 0)
      const otcIds = holdingsList
        .filter(h => h.channel !== 'EXCHANGE')
        .map(h => Number(h.productId))
        .filter(id => Number.isFinite(id) && id > 0)
      
      // 场内：批量获取实时行情
      if (exchangeIds.length > 0) {
        try {
          const quotesData = await marketApi.getRealtimeQuotes(exchangeIds)
          quotes.value = new Map(quotesData.map(q => [q.productId, q]))
        } catch (e: any) {
          console.warn('加载实时行情失败:', e.message)
        }
      }
      
      // 场外：批量获取最新净值
      if (otcIds.length > 0) {
        try {
          const navPromises = otcIds.map(id => navApi.getLatestNav(id))
          const navsData = await Promise.all(navPromises)
          navs.value = new Map(
            navsData.filter(n => n !== null).map(n => [n!.productId, n!])
          )
        } catch (e: any) {
          console.warn('加载净值失败:', e.message)
        }
      }
    }
  } catch (error: any) {
    showFailToast(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function onRefresh() {
  refreshing.value = true
  await loadData()
  refreshing.value = false
}

function getPnlPercent(holding: HoldingInfo): number {
  if (!holding.totalCost || holding.totalCost === 0) return 0
  const pnl = holding.unrealizedPnl || 0
  return (pnl / holding.totalCost) * 100
}

function showHoldingDetail(holding: HoldingInfo) {
  currentHolding.value = holding
  showDetailDialog.value = true
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.holdings-page {
  width: 100%;
  min-height: 100vh;
  background: var(--bg);
}

.page-container {
  padding: 16px;
  padding-bottom: calc(50px + var(--safe-area-inset-bottom) + 16px);
}

/* 汇总卡片 */
.summary-card {
  background: var(--card);
  border-radius: var(--radius);
  padding: 20px;
  margin-bottom: 16px;
  box-shadow: var(--shadow);
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.summary-item {
  text-align: center;
}

.summary-label {
  font-size: 13px;
  color: var(--muted);
  margin-bottom: 8px;
}

.summary-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text);
}

/* 持仓列表 */
.holding-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.holding-card {
  background: var(--card);
  border-radius: var(--radius);
  padding: 16px;
  box-shadow: var(--shadow);
  transition: all 0.2s ease;
}

.holding-card:active {
  transform: scale(0.98);
  box-shadow: var(--shadow2);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.product-info {
  flex: 1;
}

.product-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 4px;
}

.product-code {
  font-size: 12px;
  color: var(--muted);
  font-style: italic;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.holding-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  padding: 12px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.row-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.row-label {
  font-size: 12px;
  color: var(--muted);
}

.row-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
}

.pnl-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background: var(--bg);
  border-radius: var(--radiusSmall);
  margin-top: 8px;
}

.pnl-label {
  font-size: 14px;
  color: var(--muted);
}

.pnl-value {
  font-size: 20px;
  font-weight: 700;
}

.pnl-percent {
  font-size: 16px;
  font-weight: 600;
}

/* 详情弹窗 */
.detail-popup {
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

.detail-content {
  margin-top: 16px;
}
</style>
