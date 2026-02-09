<template>
  <div class="dashboard-page">
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <div class="page-container">
        <!-- 资产概览KPI卡片 -->
        <div class="kpi-section">
          <div class="kpi-row">
            <div class="kpi-card" @click="showAssetDetail = true">
              <div class="kpi-label">净资产</div>
              <div class="kpi-value" :class="{ 'text-good': assetOverview.netWorth >= 0, 'text-bad': assetOverview.netWorth < 0 }">
                {{ formatCurrency(assetOverview.netWorth) }}
              </div>
              <div class="kpi-change" v-if="assetOverview.todayChange !== undefined">
                <van-icon :name="assetOverview.todayChange >= 0 ? 'arrow-up' : 'arrow-down'" />
                <span :class="{ 'text-good': assetOverview.todayChange >= 0, 'text-bad': assetOverview.todayChange < 0 }">
                  {{ formatCurrency(Math.abs(assetOverview.todayChange)) }}
                </span>
              </div>
            </div>
            <div class="kpi-card" @click="showAssetDetail = true">
              <div class="kpi-label">可用资金</div>
              <div class="kpi-value">{{ formatCurrency(assetOverview.availableCash || assetOverview.cashBalance) }}</div>
            </div>
          </div>
          <div class="kpi-row">
            <div class="kpi-card" @click="showAssetDetail = true">
              <div class="kpi-label">持仓市值</div>
              <div class="kpi-value">{{ formatCurrency(assetOverview.positionValue) }}</div>
            </div>
            <div class="kpi-card" @click="showAssetDetail = true">
              <div class="kpi-label">浮动盈亏</div>
              <div class="kpi-value" :class="{ 'text-good': (assetOverview.unrealizedPnl || 0) >= 0, 'text-bad': (assetOverview.unrealizedPnl || 0) < 0 }">
                {{ formatCurrency(assetOverview.unrealizedPnl || 0) }}
              </div>
            </div>
          </div>
        </div>

        <!-- 资产配置图表（简化版） -->
        <div class="card-section">
          <div class="section-header">
            <h3>资产配置</h3>
            <van-icon name="arrow" @click="showAllocationDetail = true" />
          </div>
          <div class="allocation-preview">
            <div 
              v-for="item in (assetAllocation?.items || [])" 
              :key="item.label"
              class="allocation-item"
            >
              <div class="allocation-info">
                <div class="allocation-name">{{ item.label }}</div>
                <div class="allocation-value">{{ formatCurrency(item.value) }}</div>
              </div>
              <van-progress 
                :percentage="item.weight * 100" 
                :color="getAllocationColor(item.label)"
                stroke-width="8"
                :show-pivot="false"
              />
            </div>
          </div>
        </div>

        <!-- 待结算清单 -->
        <div class="card-section" v-if="pendingSettlements.length > 0">
          <div class="section-header">
            <h3>待结算 <van-badge :content="pendingSettlements.length" /></h3>
            <span class="link-text" @click="$router.push('/settlements')">查看全部</span>
          </div>
          <div class="settlement-list">
            <div 
              v-for="item in pendingSettlements.slice(0, 3)" 
              :key="item.orderId"
              class="settlement-item"
              @click="handleSettlementClick(item)"
            >
              <div class="settlement-info">
                <div class="settlement-title">
                  <van-tag :type="getOrderTypeTagType(item.orderType)" size="small">
                    {{ getOrderTypeLabel(item.orderType) }}
                  </van-tag>
                  <span class="product-name">{{ getProductName(item.productId) }}</span>
                </div>
                <div class="settlement-meta">
                  <span class="amount">{{ formatCurrency(item.amount || 0) }}</span>
                  <span class="date">{{ formatDate(item.expectedConfirmDate || '') }}</span>
                </div>
              </div>
              <van-icon name="arrow" />
            </div>
          </div>
        </div>

        <!-- 核心持仓Top 5 -->
        <div class="card-section" v-if="topHoldings.length > 0">
          <div class="section-header">
            <h3>核心持仓</h3>
            <span class="link-text" @click="$router.push('/holdings')">查看全部</span>
          </div>
          <div class="holding-list">
            <div 
              v-for="holding in topHoldings.slice(0, 5)" 
              :key="holding.productId"
              class="holding-item"
              @click="$router.push({ name: 'Holdings', query: { productId: holding.productId } })"
            >
              <div class="holding-info">
                <div class="holding-name">{{ holding.productName }}</div>
                <div class="holding-meta">
                  <span class="shares">{{ formatNumber(holding.totalShares, 2) }} 份</span>
                  <span class="value">{{ formatCurrency(holding.marketValue) }}</span>
                </div>
              </div>
              <div class="holding-pnl" :class="{ 'text-good': holding.unrealizedPnl >= 0, 'text-bad': holding.unrealizedPnl < 0 }">
                {{ formatCurrency(holding.unrealizedPnl) }}
              </div>
            </div>
          </div>
        </div>

        <!-- 今日建议（Phase 3实现，当前显示空状态） -->
        <div class="card-section">
          <div class="section-header">
            <h3>今日建议</h3>
          </div>
          <van-empty description="暂无建议" />
        </div>
      </div>
    </van-pull-refresh>

    <!-- 资产详情弹窗 -->
    <van-popup
      v-model:show="showAssetDetail"
      position="bottom"
      :style="{ height: '60%' }"
      round
      closeable
    >
      <div class="asset-detail-popup">
        <h3 class="popup-title">资产详情</h3>
        <div class="detail-list">
          <van-cell title="净资产" :value="formatCurrency(assetOverview.netWorth)" />
          <van-cell title="可用资金" :value="formatCurrency(assetOverview.availableCash || assetOverview.cashBalance)" />
          <van-cell title="持仓市值" :value="formatCurrency(assetOverview.positionValue)" />
          <van-cell title="浮动盈亏" :value="formatCurrency(assetOverview.unrealizedPnl || 0)" />
          <van-cell title="负债" :value="formatCurrency(assetOverview.liability)" />
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { dashboardApi, holdingApi, settlementApi, marketApi, navApi } from '@wealth-hub/shared'
import { formatCurrency, formatNumber, formatDate } from '@wealth-hub/shared'
import { getOrderTypeLabel, getAssetTypeLabel } from '@wealth-hub/shared'
import { showLoadingToast, closeToast } from 'vant'
import type { AssetOverview, AssetAllocation, HoldingInfo, Order, MarketQuoteRealtime, Nav } from '@wealth-hub/shared'

const router = useRouter()
const refreshing = ref(false)
const showAssetDetail = ref(false)
const showAllocationDetail = ref(false)

const assetOverview = ref<AssetOverview & { availableCash?: number; unrealizedPnl?: number; todayChange?: number }>({
  totalAssets: 0,
  netWorth: 0,
  cashBalance: 0,
  positionValue: 0,
  liability: 0,
  availableCash: 0,
  unrealizedPnl: 0,
  todayChange: 0,
})

const assetAllocation = ref<AssetAllocation>({
  groupBy: 'assetType',
  items: [],
})

const pendingSettlements = ref<Order[]>([])
const topHoldings = ref<any[]>([])
const quotes = ref<Map<number, MarketQuoteRealtime>>(new Map())
const navs = ref<Map<number, Nav>>(new Map())

async function loadData() {
  try {
    showLoadingToast({ message: '加载中...', forbidClick: true })

    // 独立加载每个接口，一个失败不影响其他
    // 1. 资产概览
    try {
      const overview = await dashboardApi.getAssetOverview()
      assetOverview.value = overview
    } catch (e: any) {
      console.warn('加载资产概览失败:', e.message)
    }

    // 2. 待结算
    try {
      const settlements = await settlementApi.getPendingSettlements()
      pendingSettlements.value = settlements
    } catch (e: any) {
      console.warn('加载待结算失败:', e.message)
    }

    // 3. 持仓 + 行情/净值
    try {
      let holdingsData = await holdingApi.getHoldings()
      
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
      
      // 合并持仓和行情数据
      const holdingsWithQuote = holdingsList.map(holding => {
        const quote = quotes.value.get(holding.productId)
        const nav = navs.value.get(holding.productId)
        
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
      
      topHoldings.value = holdingsWithQuote.sort((a, b) => Math.abs(b.unrealizedPnl || 0) - Math.abs(a.unrealizedPnl || 0))
      
      // 更新资产概览中的持仓市值和浮动盈亏
      const totalMarketValue = holdingsWithQuote.reduce((sum, h) => sum + (h.marketValue || 0), 0)
      const totalPnl = holdingsWithQuote.reduce((sum, h) => sum + (h.unrealizedPnl || 0), 0)
      assetOverview.value = {
        ...assetOverview.value,
        positionValue: totalMarketValue || assetOverview.value.positionValue,
        unrealizedPnl: totalPnl,
      }
    } catch (e: any) {
      console.warn('加载持仓失败:', e.message)
    }

    closeToast()
  } catch (error: any) {
    closeToast()
    console.error('加载数据失败:', error)
  }
}

async function onRefresh() {
  refreshing.value = true
  await loadData()
  refreshing.value = false
}

function getProductName(productId?: number): string {
  if (!productId) return '—'
  const holding = topHoldings.value.find(h => h.productId === productId)
  return holding?.productName || `产品${productId}`
}

function getOrderTypeTagType(orderType: string): 'primary' | 'success' | 'warning' | 'danger' {
  switch (orderType) {
    case 'BUY':
    case 'SUBSCRIPTION':
      return 'danger'
    case 'SELL':
    case 'REDEMPTION':
      return 'success'
    default:
      return 'primary'
  }
}

function getAllocationColor(name: string): string {
  const colors: Record<string, string> = {
    '现金': '#4ea4ff',
    'ETF': '#7cc7ff',
    '基金': '#67c23a',
    '股票': '#e6a23c',
    '债券': '#f56c6c',
  }
  return colors[name] || '#4ea4ff'
}

function handleSettlementClick(item: Order) {
  // 跳转到结算确认页面
  router.push({ name: 'Settlements' })
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.dashboard-page {
  width: 100%;
  min-height: 100vh;
  background: var(--bg);
}

.page-container {
  padding: 16px;
  padding-bottom: calc(50px + var(--safe-area-inset-bottom) + 16px);
}

/* KPI卡片区域 */
.kpi-section {
  margin-bottom: 16px;
}

.kpi-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
}

.kpi-card {
  background: var(--card);
  border-radius: var(--radius);
  padding: 20px 16px;
  text-align: center;
  box-shadow: var(--shadow);
  position: relative;
  overflow: hidden;
  transition: all 0.2s ease;
}

.kpi-card:active {
  transform: scale(0.98);
  box-shadow: var(--shadow2);
}

.kpi-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--primary), var(--primary2));
}

.kpi-label {
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 8px;
}

.kpi-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text);
  line-height: 1.2;
  margin-bottom: 4px;
}

.kpi-change {
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  margin-top: 4px;
}

/* 卡片区域 */
.card-section {
  background: var(--card);
  border-radius: var(--radius);
  padding: 16px;
  margin-bottom: 16px;
  box-shadow: var(--shadow);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.link-text {
  font-size: 13px;
  color: var(--primary);
  font-weight: 500;
}

/* 资产配置预览 */
.allocation-preview {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.allocation-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.allocation-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.allocation-name {
  font-size: 14px;
  color: var(--text);
  font-weight: 500;
}

.allocation-value {
  font-size: 14px;
  color: var(--muted);
  font-weight: 500;
}

/* 待结算列表 */
.settlement-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.settlement-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background: var(--bg);
  border-radius: var(--radiusSmall);
  transition: all 0.2s ease;
}

.settlement-item:active {
  background: var(--primarySoft);
}

.settlement-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.settlement-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.product-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text);
}

.settlement-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--muted);
}

.amount {
  font-weight: 600;
  color: var(--text);
}

/* 持仓列表 */
.holding-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.holding-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background: var(--bg);
  border-radius: var(--radiusSmall);
  transition: all 0.2s ease;
}

.holding-item:active {
  background: var(--primarySoft);
}

.holding-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.holding-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text);
}

.holding-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--muted);
}

.holding-pnl {
  font-size: 16px;
  font-weight: 600;
}

/* 弹窗样式 */
.asset-detail-popup {
  padding: 24px;
}

.popup-title {
  font-size: 18px;
  font-weight: 600;
  text-align: center;
  margin: 0 0 20px 0;
  color: var(--text);
}

.detail-list {
  margin-top: 16px;
}
</style>
