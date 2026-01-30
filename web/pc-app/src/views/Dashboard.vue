<template>
  <div>
    <!-- 今日建议 - 订单详情弹窗（直接在总览里操作，与订单管理界面完全一致） -->
    <el-dialog v-model="settlementVisible" title="订单详情" width="700px" class="order-detail-dialog">
      <div v-if="settlementOrder" class="order-detail-content">
        <!-- 标题：订单详情 + 订单ID（灰色） -->
        <div class="detail-header">
          <span>订单详情</span>
          <span class="sub-text" style="margin-left: 8px;">{{ settlementOrder.orderId }}</span>
        </div>

        <div class="divider"></div>

        <!-- 详细信息 -->
        <div class="detail-section">
          <!-- 类型 -->
          <div class="detail-row">
            <span class="detail-label">类型</span>
            <span class="detail-value">
              <span class="tag blue">{{ getOrderTypeLabel(settlementOrder.orderType) }}</span>
            </span>
          </div>

          <!-- 产品和标的代码 -->
          <div class="detail-row">
            <span class="detail-label">产品</span>
            <span class="detail-value">
              <div>{{ getProductDisplayName(settlementOrder.productId) }}</div>
              <div class="sub-text" style="margin-top: 4px;">{{ getProductCode(settlementOrder.productId) }}</div>
            </span>
          </div>

          <!-- 状态 -->
          <div class="detail-row">
            <span class="detail-label">状态</span>
            <span class="detail-value">
              <span class="tag" :class="getOrderStatusTagClass(settlementOrder.status)">
                {{ getOrderStatusLabel(settlementOrder.status) }}
              </span>
            </span>
          </div>

          <!-- 金额（买入/申购可编辑，赎回/卖出只读） -->
          <div class="detail-row">
            <span class="detail-label">金额</span>
            <span class="detail-value">
              <el-input-number
                v-if="isBuyOrderType(settlementOrder.orderType) && settlementOrder.status === 'PENDING'"
                v-model="settlementForm.amount"
                :min="0.01"
                :precision="2"
                style="width: 100%"
                @change="handleDashboardAmountChange"
              />
              <span v-else class="mono highlight-amount">{{ getOrderDetailAmount(settlementOrder) }}</span>
            </span>
          </div>

          <!-- 份额（赎回/卖出可编辑，买入/申购只读） -->
          <div class="detail-row">
            <span class="detail-label">份额</span>
            <span class="detail-value">
              <el-input-number
                v-if="!isBuyOrderType(settlementOrder.orderType) && settlementOrder.status === 'PENDING'"
                v-model="settlementForm.shares"
                :min="0.01"
                :precision="2"
                style="width: 100%"
                @change="handleDashboardSharesChange"
              />
              <span v-else class="mono">{{ getOrderDetailShares(settlementOrder) }}</span>
            </span>
          </div>

          <!-- 净值（可编辑） -->
          <div class="detail-row">
            <span class="detail-label">净值</span>
            <span class="detail-value">
              <el-input-number
                v-if="settlementOrder.status === 'PENDING'"
                v-model="settlementForm.confirmNav"
                :min="0.000001"
                :precision="6"
                style="width: 100%"
                @change="handleDashboardNavChange"
              />
              <template v-else>
                <div class="mono">{{ getOrderDetailNav(settlementOrder)?.nav?.toFixed(6) || '—' }}</div>
                <div v-if="getOrderDetailNav(settlementOrder)?.navDate" class="sub-text" style="margin-top: 4px;">
                  {{ getOrderDetailNav(settlementOrder)?.navDate }}
                </div>
              </template>
            </span>
          </div>

          <!-- 净值日期（可编辑） -->
          <div class="detail-row" v-if="settlementOrder.status === 'PENDING'">
            <span class="detail-label">净值日期</span>
            <span class="detail-value">
              <el-date-picker
                v-model="settlementForm.navDate"
                type="date"
                placeholder="选择净值日期"
                style="width: 100%"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                @change="handleSettlementNavDateChange"
              />
            </span>
          </div>
          <div class="detail-row" v-else-if="getOrderDetailNav(settlementOrder)?.navDate">
            <span class="detail-label">净值日期</span>
            <span class="detail-value">{{ getOrderDetailNav(settlementOrder)?.navDate }}</span>
          </div>

          <!-- 订单的发起日期 -->
          <div class="detail-row" v-if="settlementOrder.requestedAt">
            <span class="detail-label">订单的发起日期</span>
            <span class="detail-value">{{ formatDateTime(settlementOrder.requestedAt) }}</span>
          </div>

          <!-- 确认日期（可编辑） -->
          <div class="detail-row" v-if="settlementOrder.status === 'PENDING'">
            <span class="detail-label">确认日期</span>
            <span class="detail-value">
              <el-date-picker
                v-model="settlementForm.confirmDate"
                type="date"
                placeholder="选择确认日期"
                style="width: 100%"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
              />
            </span>
          </div>
          <div class="detail-row" v-else-if="settlementOrder.settlement?.confirmDate">
            <span class="detail-label">订单的确认日期</span>
            <span class="detail-value">{{ settlementOrder.settlement.confirmDate }}</span>
          </div>

          <!-- 手续费 -->
          <div class="detail-row" v-if="getOrderDetailFee(settlementOrder) > 0 || settlementOrder.status === 'PENDING'">
            <span class="detail-label">手续费</span>
            <span class="detail-value">
              <el-input-number
                v-if="settlementOrder.status === 'PENDING'"
                v-model="settlementForm.confirmFee"
                :min="0"
                :precision="2"
                style="width: 100%"
              />
              <span v-else class="mono">{{ formatCurrency(getOrderDetailFee(settlementOrder)) }}</span>
            </span>
          </div>

          <!-- 资金来源 -->
          <div class="detail-row" v-if="settlementOrder.fundingLines && settlementOrder.fundingLines.filter((l: any) => !l.lineType || l.lineType === 'SOURCE').length > 0">
            <span class="detail-label">资金来源</span>
            <span class="detail-value">
              <div v-for="line in settlementOrder.fundingLines.filter((l: any) => !l.lineType || l.lineType === 'SOURCE')" :key="line.id">
                {{ getAccountName(line.accountId) }}
              </div>
            </span>
          </div>

          <!-- 资金到账 -->
          <div class="detail-row" v-if="settlementOrder.fundingLines && settlementOrder.fundingLines.filter((l: any) => l.lineType === 'TARGET').length > 0">
            <span class="detail-label">资金到账</span>
            <span class="detail-value">
              <div v-for="line in settlementOrder.fundingLines.filter((l: any) => l.lineType === 'TARGET')" :key="line.id">
                {{ getAccountName(line.accountId) }}
              </div>
            </span>
          </div>
        </div>
      </div>

      <template #footer>
        <div style="text-align: right">
          <el-button @click="settlementVisible = false">关闭</el-button>
          <el-button
            v-if="settlementOrder && settlementOrder.status === 'PENDING'"
            type="primary"
            @click="handleConfirmSettlementFromDashboard"
          >
            结算
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 资产概览KPI -->
    <div class="kpis">
      <div class="kpi primary">
        <div class="label">✨ 净资产（Net Worth）</div>
        <div class="value">{{ formatCurrency(overview.netWorth) }}</div>
        <div class="mini">= 现金 + 持仓市值 - 负债</div>
        <div class="row">
          <span class="chip" :class="overview.todayPnl && overview.todayPnl >= 0 ? 'good' : 'bad'">
            今日 {{ overview.todayPnl && overview.todayPnl >= 0 ? '+' : '' }}{{ formatCurrency(overview.todayPnl) }}
          </span>
          <span class="chip">
            本月净流入 {{ overview.monthInflow && overview.monthInflow >= 0 ? '+' : '' }}{{ formatCurrency(overview.monthInflow) }}
          </span>
        </div>
      </div>

      <div class="kpi">
        <div class="label">💧 可用资金</div>
        <div class="value">{{ formatCurrency(availableFunds) }}</div>
        <div class="mini">= Σ(现金叶子余额) - Σ(占用)</div>
        <div class="row">
          <span class="chip warn">占用 {{ formatCurrency(reservedAmount) }}</span>
          <span class="chip">生活费可支出 {{ formatCurrency(spendableAmount) }}</span>
        </div>
      </div>

      <div class="kpi">
        <div class="label">📈 持仓市值</div>
        <div class="value">{{ formatCurrency(positionValue) }}</div>
        <div class="mini">= Σ(持仓 shares × 价格)</div>
        <div class="row" style="flex-direction: column; align-items: flex-start; gap: 4px;">
          <span class="chip" :class="totalPnl >= 0 ? 'good' : 'bad'">
            总盈亏 {{ totalPnl >= 0 ? '+' : '' }}{{ formatCurrency(totalPnl) }}
          </span>
          <span class="chip" :class="exchangePnl >= 0 ? 'good' : 'bad'" style="font-size: 11px;">
            场内盈亏 {{ exchangePnl >= 0 ? '+' : '' }}{{ formatCurrency(exchangePnl) }}
          </span>
          <span class="chip" :class="otcPnl >= 0 ? 'good' : 'bad'" style="font-size: 11px;">
            场外盈亏 {{ otcPnl >= 0 ? '+' : '' }}{{ formatCurrency(otcPnl) }}
          </span>
        </div>
      </div>

      <div class="kpi">
        <div class="label">💳 负债</div>
        <div class="value">{{ formatCurrency(overview.liability) }}</div>
        <div class="mini">= Σ(信贷账户余额：花呗/信用卡/白条/贷款)</div>
      </div>
    </div>

    <div class="grid">
      <!-- 资产配比图表 -->
      <div class="card">
        <h3>
          资产配比（Allocation）
          <span class="row-gap">
            <span class="tag gray tiny">实时</span>
          </span>
        </h3>
        <div id="allocationChart" style="width: 100%; height: 300px"></div>
      </div>

      <!-- 今日建议和核心持仓 -->
      <div class="card">
        <h3>
          今天建议
          <span class="tag orange tiny" v-if="todayActions.length > 0">待办 {{ todayActions.length }}</span>
        </h3>
        <div class="sub">建议来自：待结算订单 / 待分配资金 / 本月应还 / 逆回购到期</div>

        <div class="divider"></div>

        <div v-if="todayActions.length === 0" class="td-muted" style="padding: 20px; text-align: center">
          暂无待办事项
        </div>
        <div v-else class="row-gap" style="flex-direction: column; align-items: stretch">
          <div
            v-for="action in todayActions"
            :key="action.id"
            class="card today-action-card"
            style="padding: 12px; margin-bottom: 8px; cursor: pointer"
            @click="handleTodayActionClick(action)"
          >
            <div style="font-weight: 600">{{ action.title }}</div>
            <div v-if="action.description" class="td-muted" style="font-size: 12px; margin-top: 4px">
              {{ action.description }}
            </div>
          </div>
        </div>

        <div class="divider"></div>

        <h3>
          核心持仓（Top 5）
          <span class="row-gap">
            <span class="tag blue tiny">联动</span>
            <button class="btn" @click="$router.push({ name: 'Holdings' })" style="padding: 6px 10px; font-size: 12px">
              查看全部 →
            </button>
          </span>
        </h3>
        <div class="sub">来自持仓数据的实时计算</div>
        <div style="margin-top: 10px; overflow: auto">
            <table>
              <thead>
                <tr>
                  <th>名称</th>
                  <th class="right">
                    <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 2px;">
                      <div>份额</div>
                      <div style="font-size: 11px; color: #999; font-weight: normal;">市值</div>
                    </div>
                  </th>
                  <th class="right">
                    <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 2px;">
                      <div>成本价</div>
                      <div style="font-size: 11px; color: #999; font-weight: normal;">最新价</div>
                    </div>
                  </th>
                  <th class="right">
                    <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 2px;">
                      <div>盈亏率</div>
                      <div style="font-size: 11px; color: #999; font-weight: normal;">浮盈亏</div>
                    </div>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="topHoldings.length === 0">
                  <td colspan="4" class="td-muted">暂无持仓，去"订单&结算"买一笔试试。</td>
                </tr>
                <tr v-for="holding in topHoldings" :key="holding.productId">
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
                      <div class="mono">{{ formatNumber(holding.sharesCalc || 0, 2) }}</div>
                      <div class="mono td-muted">{{ formatCurrency(holding.marketValue || 0) }}</div>
                    </div>
                  </td>
                  <td class="right">
                    <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 4px;">
                      <div class="mono" style="color: #0f172a;">{{ formatNumber(holding.avgCostCalc || 0, 4) }}</div>
                      <div class="mono" :style="{ color: priceColor(holding.currentPrice || 0, holding.avgCostCalc || 0) }">
                        <span v-if="holding.currentPrice > 0">{{ formatNumber(holding.currentPrice, 4) }}</span>
                        <span v-else class="td-muted">—</span>
                      </div>
                    </div>
                  </td>
                  <td class="right">
                    <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 6px;">
                      <div class="mono" :style="{ color: pnlColor(holding.unrealizedPnl || 0) }">
                        {{ pnlRateText(holding.unrealizedPnl || 0, holding.totalCostCalc || 0) }}
                      </div>
                      <div :style="{ color: pnlColor(holding.unrealizedPnl || 0), fontWeight: 600 }">
                        {{ formatSignedCurrency(holding.unrealizedPnl || 0) }}
                      </div>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
        </div>
      </div>
    </div>

    <!-- 待结算清单 -->
    <div class="card" style="margin-top: var(--gap)">
      <h3>
        待结算清单
        <span class="tag orange tiny" v-if="pendingSettlements.length > 0">{{ pendingSettlements.length }} 笔</span>
      </h3>
      <div class="sub">需要确认结算的订单</div>
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
            <tr v-if="pendingSettlements.length === 0">
              <td colspan="6" class="td-muted">暂无待结算订单</td>
            </tr>
            <tr v-for="settlement in pendingSettlements" :key="settlement.orderId">
              <td class="mono">{{ settlement.orderId.slice(-8) }}</td>
              <td>
                <span class="tag blue">{{ getOrderTypeLabel(settlement.orderType) }}</span>
              </td>
              <td><b>{{ settlement.productName || '未知' }}</b></td>
              <td class="right mono">{{ formatCurrency(settlement.amount) }}</td>
              <td>{{ formatDate(settlement.expectedConfirmDate) }}</td>
              <td class="right">
                <button class="btn" @click="handleConfirmSettlement(settlement.orderId)">确认结算</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { dashboardApi, holdingApi, marketApi, navApi, orderApi, useAccountStore, useProductStore, getOrderStatusLabel } from '@wealth-hub/shared'
import { formatCurrency, formatNumber, formatDate, formatDateTime, getOrderTypeLabel } from '@wealth-hub/shared'
import type { MarketQuoteRealtime, Nav, AssetOverview, Account } from '@wealth-hub/shared'

const accountStore = useAccountStore()
const productStore = useProductStore()

const overview = ref<AssetOverview>({
  totalAssets: 0,
  netWorth: 0,
  cashBalance: 0,
  positionValue: 0,
  liability: 0,
  todayPnl: 0,
  monthInflow: 0,
})

const todayActions = ref<any[]>([])
const pendingSettlements = ref<any[]>([])
const holdings = ref<any[]>([])
const quotes = ref<Map<number, MarketQuoteRealtime>>(new Map())
const navs = ref<Map<number, Nav>>(new Map())

// 今日建议 - 订单结算弹窗状态
const settlementVisible = ref(false)
const settlementOrder = ref<any | null>(null)
const settlementForm = ref({
  confirmDate: '',
  navDate: '',
  confirmNav: undefined as number | undefined,
  confirmShares: undefined as number | undefined,
  confirmAmount: undefined as number | undefined,
  confirmFee: 0,
  note: '',
})

const isBuyType = computed(() => {
  if (!settlementOrder.value) return false
  return settlementOrder.value.orderType === 'BUY' || settlementOrder.value.orderType === 'SUBSCRIPTION'
})

const availableFunds = computed(() => {
  return accountStore.cashLeafAccounts.reduce((sum, acc) => sum + (acc.balance || 0) - (acc.reservedAmount || 0), 0)
})

const reservedAmount = computed(() => {
  return accountStore.cashLeafAccounts.reduce((sum, acc) => sum + (acc.reservedAmount || 0), 0)
})

const spendableAmount = computed(() => {
  return accountStore.cashLeafAccounts
    .filter((acc) => acc.fundUsage === 'SPENDABLE')
    .reduce((sum, acc) => sum + (acc.balance || 0) - (acc.reservedAmount || 0), 0)
})

// 合并持仓和行情数据
const holdingsWithQuote = computed(() => {
  if (!holdings.value || !Array.isArray(holdings.value)) {
    return []
  }
  
  return holdings.value.map(holding => {
    const quote = quotes.value.get(holding.productId)
    const nav = navs.value.get(holding.productId)
    
    // 优先使用实时行情价格，其次使用净值
    let currentPrice = 0
    if (quote && quote.price > 0) {
      currentPrice = quote.price
    } else if (nav && nav.nav > 0) {
      currentPrice = nav.nav
    }
    
    // 使用后端返回的份额和成本（更准确）
    const shares = Number(holding.totalShares || holding.shares || 0)
    const avgCost = Number(holding.averageCost || holding.avgCost || 0)
    const totalCost = shares * avgCost
    const marketValue = currentPrice > 0 ? shares * currentPrice : 0
    const unrealizedPnl = marketValue - totalCost
    
    return {
      ...holding,
      currentPrice,
      marketValue,
      unrealizedPnl,
      quote,
      nav,
      // 确保这些字段是数字类型
      totalShares: shares,
      averageCost: avgCost,
      // 保留 assetType 用于资产配比分类
      assetType: holding.assetType,
    }
  })
})

// 计算总盈亏、场内盈亏、场外盈亏
const totalPnl = computed(() => {
  return holdingsWithQuote.value.reduce((sum, h) => {
    // 确保计算正确：市值 - 成本
    const shares = h.totalShares || h.shares || 0
    const avgCost = h.averageCost || h.avgCost || 0
    const totalCost = shares * avgCost
    const marketValue = h.marketValue || 0
    const pnl = marketValue - totalCost
    return sum + pnl
  }, 0)
})

const exchangePnl = computed(() => {
  return holdingsWithQuote.value
    .filter(h => h.channel === 'EXCHANGE')
    .reduce((sum, h) => {
      const shares = h.totalShares || h.shares || 0
      const avgCost = h.averageCost || h.avgCost || 0
      const totalCost = shares * avgCost
      const marketValue = h.marketValue || 0
      const pnl = marketValue - totalCost
      return sum + pnl
    }, 0)
})

const otcPnl = computed(() => {
  return holdingsWithQuote.value
    .filter(h => h.channel === 'OTC')
    .reduce((sum, h) => {
      const shares = h.totalShares || h.shares || 0
      const avgCost = h.averageCost || h.avgCost || 0
      const totalCost = shares * avgCost
      const marketValue = h.marketValue || 0
      const pnl = marketValue - totalCost
      return sum + pnl
    }, 0)
})

const positionValue = computed(() => {
  return holdingsWithQuote.value.reduce((sum, h) => sum + (h.marketValue || 0), 0)
})

// 按盈亏排序，取绝对值最大的前5个
const topHoldings = computed(() => {
  return holdingsWithQuote.value
    .map(h => {
      const shares = h.totalShares || h.shares || 0
      const avgCost = h.averageCost || h.avgCost || 0
      const totalCost = shares * avgCost
      const marketValue = h.marketValue || 0
      const unrealizedPnl = marketValue - totalCost
      return {
        ...h,
        sharesCalc: shares,
        avgCostCalc: avgCost,
        totalCostCalc: totalCost,
        unrealizedPnl,
      }
    })
    .sort((a, b) => Math.abs(b.unrealizedPnl || 0) - Math.abs(a.unrealizedPnl || 0))
    .slice(0, 5)
})

let allocationChart: echarts.ECharts | null = null

async function loadData() {
  try {
    // 确保账户数据已加载
    if (accountStore.accounts.length === 0) {
      await accountStore.fetchAccounts()
    }
    
    // 加载资产概览
    const overviewData = await dashboardApi.getAssetOverview()
    console.log('资产概览数据:', overviewData)
    console.log('账户数据:', accountStore.accounts)
    console.log('账户树:', accountStore.accountTree)
    overview.value = overviewData

    // 加载今日建议
    todayActions.value = await dashboardApi.getTodayActions()

    // 加载待结算清单（后端返回Order列表）
    const orders = await dashboardApi.getPendingSettlements()
    // 转换为PendingSettlement格式
    pendingSettlements.value = (orders as any[]).map((order: any) => ({
      orderId: order.orderId,
      orderType: order.orderType,
      productId: order.productId,
      productName: undefined, // 需要从product获取
      amount: order.amount || 0,
      expectedConfirmDate: order.expectedConfirmDate || '',
      fundingLines: [], // 需要从order_funding_line获取
    }))

    // 加载持仓
    try {
      const holdingsData = await holdingApi.getHoldings()
      // holdingsData 可能是 Map 或对象，需要转换为数组
      if (holdingsData instanceof Map) {
        holdings.value = Array.from(holdingsData.values())
      } else if (Array.isArray(holdingsData)) {
        holdings.value = holdingsData
      } else if (typeof holdingsData === 'object' && holdingsData !== null) {
        holdings.value = Object.values(holdingsData)
      } else {
        holdings.value = []
      }
      
      // 加载实时行情和净值（用于计算持仓市值）
      // 场内产品用实时行情，场外产品用净值
      if (holdings.value.length > 0) {
        // 区分场内和场外产品
        const exchangeIds = holdings.value
          .filter(h => h.channel === 'EXCHANGE')
          .map(h => h.productId)
          .filter(id => id)
        const otcIds = holdings.value
          .filter(h => h.channel !== 'EXCHANGE')
          .map(h => h.productId)
          .filter(id => id)
        
        // 场内：批量获取实时行情
        if (exchangeIds.length > 0) {
          try {
            const quotesData = await marketApi.getRealtimeQuotes(exchangeIds)
            quotes.value = new Map(quotesData.map(q => [q.productId, q]))
          } catch (error: any) {
            console.warn('加载实时行情失败:', error)
          }
        }
        
        // 场外：批量获取最新净值（每个请求独立处理，避免单个失败影响全部）
        if (otcIds.length > 0) {
          const navPromises = otcIds.map(id => 
            navApi.getLatestNav(id).catch(err => {
              console.warn(`获取产品${id}净值失败:`, err)
              return null
            })
          )
          const navsData = await Promise.all(navPromises)
          navs.value = new Map(
            navsData.filter(n => n !== null).map(n => [n!.productId, n!])
          )
        }
      }
    } catch (error: any) {
      console.error('Failed to load holdings:', error)
      holdings.value = []
    }

    // 加载账户数据
    await accountStore.fetchAccounts()

    // 更新图表
    updateAllocationChart()
  } catch (error: any) {
    ElMessage.error(error.message || '加载数据失败')
  }
}

// 辅助函数：盈亏颜色
function pnlColor(pnl: number) {
  return (pnl || 0) >= 0 ? 'var(--good)' : 'var(--bad)'
}

// 辅助函数：价格颜色
function priceColor(currentPrice: number, costPrice: number) {
  if (!currentPrice || currentPrice <= 0) return 'var(--muted)'
  if (!costPrice || costPrice <= 0) return '#0f172a'
  return currentPrice >= costPrice ? 'var(--good)' : 'var(--bad)'
}

// 辅助函数：盈亏率文本
function pnlRateText(pnl: number, totalCost: number) {
  const cost = totalCost || 0
  if (!cost || cost <= 0) return '—'
  const rate = (pnl || 0) / cost
  return `${rate >= 0 ? '+' : ''}${formatNumber(rate * 100, 2)}%`
}

// 辅助函数：带符号的货币格式
function formatSignedCurrency(amount: number) {
  const v = Number(amount) || 0
  const sign = v >= 0 ? '+' : '-'
  return `${sign}${formatCurrency(Math.abs(v))}`
}

// ---------- 今日建议 - 订单结算相关逻辑 ----------

async function openOrderSettlement(orderId: string) {
  try {
    const detail = await orderApi.getOrder(orderId)
    settlementOrder.value = detail
    
    // 初始化编辑表单
    const isBuy = isBuyOrderType(detail.orderType)
    const today = new Date().toISOString().split('T')[0]
    
    settlementForm.value.amount = detail.amount ? Number(detail.amount) : undefined
    settlementForm.value.shares = detail.shares ? Number(detail.shares) : undefined
    settlementForm.value.navDate = detail.expectedNavDate || today
    settlementForm.value.confirmDate = detail.expectedConfirmDate || today
    settlementForm.value.confirmFee = (detail.settlement?.confirmFee || (detail as any).feeEstimate || 0) as number
    settlementForm.value.note = ''
    
    // 如果未结算，尝试获取净值
    let navValue = undefined
    if (!detail.settlement?.confirmNav) {
      // 优先使用预期净值日期
      if (detail.expectedNavDate) {
        try {
          const navData = await navApi.getNavByDate(detail.productId, detail.expectedNavDate)
          if (navData && navData.nav) {
            navValue = navData.nav
            if (!settlementOrder.value.navData) {
              settlementOrder.value.navData = navData
            }
          }
        } catch (e) {
          // 获取失败，尝试获取最新净值
          try {
            const latestNav = await navApi.getLatestNav(detail.productId)
            if (latestNav && latestNav.nav) {
              navValue = latestNav.nav
              if (!settlementOrder.value.navData) {
                settlementOrder.value.navData = latestNav
              }
            }
          } catch (e2) {
            // 获取失败则忽略
          }
        }
      } else {
        // 没有预期净值日期，尝试获取最新净值
        try {
          const latestNav = await navApi.getLatestNav(detail.productId)
          if (latestNav && latestNav.nav) {
            navValue = latestNav.nav
            if (!settlementOrder.value.navData) {
              settlementOrder.value.navData = latestNav
            }
          }
        } catch (e) {
          // 获取失败则忽略
        }
      }
    } else {
      // 已结算，使用确认净值
      navValue = detail.settlement.confirmNav ? Number(detail.settlement.confirmNav) : undefined
      settlementForm.value.navDate = detail.settlement.navDate || detail.expectedNavDate || today
    }
    
    settlementForm.value.confirmNav = navValue
    
    // 根据净值自动计算份额或金额
    if (navValue && navValue > 0) {
      if (isBuy && settlementForm.value.amount) {
        // 买入：按金额和净值计算份额
        settlementForm.value.confirmShares = Number((settlementForm.value.amount / navValue).toFixed(2))
      } else if (!isBuy && settlementForm.value.shares) {
        // 赎回：按份额和净值计算金额
        settlementForm.value.confirmAmount = Number((settlementForm.value.shares * navValue).toFixed(2))
      }
    }
    
    settlementVisible.value = true
  } catch (error: any) {
    ElMessage.error(error.message || '加载订单失败')
  }
}

// 总览页面：金额变化时自动计算份额（买入/申购）
function handleDashboardAmountChange() {
  if (!settlementOrder.value || !isBuyOrderType(settlementOrder.value.orderType)) return
  if (settlementForm.value.amount && settlementForm.value.confirmNav && settlementForm.value.confirmNav > 0) {
    settlementForm.value.confirmShares = Number((settlementForm.value.amount / settlementForm.value.confirmNav).toFixed(2))
  }
}

// 总览页面：份额变化时自动计算金额（赎回/卖出）
function handleDashboardSharesChange() {
  if (!settlementOrder.value || isBuyOrderType(settlementOrder.value.orderType)) return
  if (settlementForm.value.shares && settlementForm.value.confirmNav && settlementForm.value.confirmNav > 0) {
    settlementForm.value.confirmAmount = Number((settlementForm.value.shares * settlementForm.value.confirmNav).toFixed(2))
  }
}

// 总览页面：净值变化时自动计算份额或金额
function handleDashboardNavChange() {
  if (!settlementOrder.value) return
  const isBuy = isBuyOrderType(settlementOrder.value.orderType)
  
  if (settlementForm.value.confirmNav && settlementForm.value.confirmNav > 0) {
    if (isBuy && settlementForm.value.amount) {
      // 买入：按金额和净值计算份额
      settlementForm.value.confirmShares = Number((settlementForm.value.amount / settlementForm.value.confirmNav).toFixed(2))
    } else if (!isBuy && settlementForm.value.shares) {
      // 赎回：按份额和净值计算金额
      settlementForm.value.confirmAmount = Number((settlementForm.value.shares * settlementForm.value.confirmNav).toFixed(2))
    }
  }
}

async function handleSettlementNavDateChange() {
  if (!settlementForm.value.navDate || !settlementOrder.value) return
  try {
    const navData = await navApi.getNavByDate(settlementOrder.value.productId, settlementForm.value.navDate)
    if (navData && navData.nav) {
      settlementForm.value.confirmNav = navData.nav
      handleDashboardNavChange() // 重新计算份额或金额
    } else {
      ElMessage.warning('未找到该日期的净值数据，请手动输入')
    }
  } catch (error: any) {
    ElMessage.warning('获取净值失败，请手动输入')
  }
}

function calculateShares(amount: number, nav: number): number {
  if (!nav || nav <= 0) return 0
  return amount / nav
}

function calculateAmount(shares: number, nav: number): number {
  return shares * nav
}

function calculateSettlementFromForm() {
  if (!settlementForm.value.confirmNav || !settlementOrder.value) return
  if (isBuyType.value && settlementOrder.value.amount) {
    settlementForm.value.confirmShares = calculateShares(settlementOrder.value.amount, settlementForm.value.confirmNav)
  } else if (!isBuyType.value && settlementOrder.value.shares) {
    settlementForm.value.confirmAmount = calculateAmount(settlementOrder.value.shares, settlementForm.value.confirmNav)
  }
}

async function handleConfirmSettlementFromDashboard() {
  if (!settlementOrder.value) return

  // 验证必填字段
  if (!settlementForm.value.confirmDate || !settlementForm.value.navDate || !settlementForm.value.confirmNav) {
    ElMessage.error('请填写确认日期、净值日期和净值')
    return
  }
  
  const isBuy = isBuyOrderType(settlementOrder.value.orderType)
  
  if (isBuy) {
    if (!settlementForm.value.amount || settlementForm.value.amount <= 0) {
      ElMessage.error('请填写金额')
      return
    }
    if (!settlementForm.value.confirmShares || settlementForm.value.confirmShares <= 0) {
      ElMessage.error('请填写确认份额')
      return
    }
  } else {
    if (!settlementForm.value.shares || settlementForm.value.shares <= 0) {
      ElMessage.error('请填写份额')
      return
    }
    if (!settlementForm.value.confirmAmount || settlementForm.value.confirmAmount <= 0) {
      ElMessage.error('请填写确认金额')
      return
    }
  }

  try {
    await orderApi.confirmSettlement({
      orderId: settlementOrder.value.orderId,
      confirmDate: settlementForm.value.confirmDate,
      navDate: settlementForm.value.navDate,
      confirmNav: settlementForm.value.confirmNav!,
      confirmShares: isBuy ? settlementForm.value.confirmShares : undefined,
      confirmAmount: !isBuy ? settlementForm.value.confirmAmount : undefined,
      confirmFee: settlementForm.value.confirmFee || 0,
      note: settlementForm.value.note || '',
    })
    ElMessage.success('结算成功')
    settlementVisible.value = false
    // 重新加载数据，刷新今日建议/待结算清单等
    await loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '结算失败')
  }
}

function updateAllocationChart() {
  if (!allocationChart) return

  // 计算现金：包括CASH、PAYMENT、MMF账户余额，以及BROKER账户余额
  // 注意：MMF账户余额是通过份额*净值计算的，已经包含在账户余额中
  // 从账户树中获取所有叶子账户
  let cashValue = 0
  
  function traverseAccounts(accounts: Account[]) {
    accounts.forEach(acc => {
      // 如果是叶子账户（没有子账户）
      if (!acc.children || acc.children.length === 0) {
        // 只统计REAL账户
        if (acc.accountKind === 'REAL') {
          // 现金类账户：CASH、PAYMENT、MMF、BROKER（余额）
          if (acc.accountType === 'CASH' || 
              acc.accountType === 'PAYMENT' || 
              acc.accountType === 'MMF' ||
              acc.accountType === 'BROKER') {
            cashValue += (acc.balance || 0)
          }
        }
      } else {
        // 递归处理子账户
        traverseAccounts(acc.children)
      }
    })
  }
  
  traverseAccounts(accountStore.accountTree)
  
  // 计算持仓市值，按产品类型分类
  // 注意：持仓市值只包括通过持仓表（holdings）计算的产品持仓
  let bankWmValue = 0  // 银行理财产品（BANK_WM_NAV/BANK_WM_BOX）
  let fundValue = 0    // 基金（ETF/LOF/FUND等，不包括MMF和银行理财）
  let stockValue = 0   // 股票
  
  holdingsWithQuote.value.forEach(h => {
    const marketValue = h.marketValue || 0
    const assetType = h.assetType || ''
    
    // 银行理财产品
    if (assetType === 'BANK_WM_NAV' || assetType === 'BANK_WM_BOX') {
      bankWmValue += marketValue
    } 
    // 股票
    else if (assetType === 'STOCK') {
      stockValue += marketValue
    }
    // 基金类：ETF/LOF/FUND等（不包括MMF，MMF已通过账户余额计入现金）
    else if (assetType === 'ETF' || assetType === 'LOF' || assetType === 'FUND') {
      fundValue += marketValue
    }
    // MMF 不计入持仓市值，因为MMF账户余额已经通过份额*净值计算并包含在现金中
  })

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)',
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        data: [
          { value: cashValue, name: '现金' },
          { value: fundValue, name: '基金' },
          { value: bankWmValue, name: '银行理财' },
          { value: stockValue, name: '股票' },
        ].filter(item => item.value > 0), // 只显示有值的项
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)',
          },
        },
      },
    ],
  }

  allocationChart.setOption(option)
}

function handleTodayActionClick(action: any) {
  console.log('handleTodayActionClick called:', action)
  // 预留多类型扩展，这里先支持订单结算
  if (action.type === 'SETTLE_ORDER') {
    if (action.id) {
      console.log('Opening settlement for order:', action.id)
      openOrderSettlement(String(action.id))
      return
    }
    if (action.actionUrl) {
      console.log('Using actionUrl:', action.actionUrl)
      window.location.href = action.actionUrl
      return
    }
  }

  // 兜底：如果有跳转链接，直接跳转
  if (action.actionUrl) {
    console.log('Fallback: using actionUrl:', action.actionUrl)
    window.location.href = action.actionUrl
  } else {
    console.warn('No action handler found for:', action)
  }
}

// 辅助函数：获取产品名称
function getProductDisplayName(productId: number): string {
  const product = productStore.products.find(p => p.id === productId)
  if (!product) return `产品ID: ${productId}`
  return product.productName
}

// 辅助函数：获取产品代码
function getProductCode(productId: number): string {
  const product = productStore.products.find(p => p.id === productId)
  return product?.productCode || ''
}

// 辅助函数：获取订单状态标签
function getOrderStatusTagClass(status: string): string {
  if (status === 'CONFIRMED') return 'green'
  if (status === 'PENDING') return 'orange'
  if (status === 'CANCELLED' || status === 'FAILED') return 'red'
  return 'gray'
}

// 辅助函数：判断是否为买入类型
function isBuyOrderType(orderType: string): boolean {
  return orderType === 'BUY' || orderType === 'SUBSCRIPTION'
}

// 订单详情：计算金额（保留2位小数）
function getOrderDetailAmount(order: any): string {
  // 如果正在编辑，使用编辑表单的值
  if (settlementForm.value.amount !== undefined && order.status === 'PENDING') {
    return formatCurrency(Number(settlementForm.value.amount.toFixed(2)))
  }
  
  const isBuy = isBuyOrderType(order.orderType)
  
  if (isBuy) {
    // 买入：显示输入的金额
    return formatCurrency(order.amount || 0)
  } else {
    // 赎回：按净值计算金额
    const nav = order.settlement?.confirmNav || order.navData?.nav
    const shares = order.shares || 0
    
    if (nav && nav > 0 && shares > 0) {
      const amount = shares * nav
      return formatCurrency(Number(amount.toFixed(2)))
    }
    
    // 如果没有净值，显示订单金额（如果有）
    return order.amount ? formatCurrency(order.amount) : '—'
  }
}

// 订单详情：获取净值
function getOrderDetailNav(order: any): { nav: number, navDate?: string } | null {
  // 如果正在编辑，使用编辑表单的值
  if (settlementForm.value.confirmNav !== undefined && order.status === 'PENDING') {
    return {
      nav: settlementForm.value.confirmNav,
      navDate: settlementForm.value.navDate
    }
  }
  
  // 优先使用结算确认的净值
  if (order.settlement?.confirmNav) {
    return {
      nav: order.settlement.confirmNav,
      navDate: order.settlement.navDate
    }
  }
  
  // 其次使用获取到的净值数据
  if (order.navData?.nav) {
    return {
      nav: order.navData.nav,
      navDate: order.navData.navDate || order.expectedNavDate
    }
  }
  
  return null
}

// 订单详情：获取份额（保留2位小数）
function getOrderDetailShares(order: any): string {
  // 如果正在编辑，使用编辑表单的值
  if (settlementForm.value.shares !== undefined && order.status === 'PENDING') {
    return settlementForm.value.shares.toFixed(2) + ' 份'
  }
  if (settlementForm.value.confirmShares !== undefined && order.status === 'PENDING' && isBuyOrderType(order.orderType)) {
    return settlementForm.value.confirmShares.toFixed(2) + ' 份'
  }
  
  const isBuy = isBuyOrderType(order.orderType)
  
  if (isBuy) {
    // 买入：按净值计算份额
    const nav = order.settlement?.confirmNav || order.navData?.nav
    const amount = order.amount || 0
    
    if (nav && nav > 0 && amount > 0) {
      const shares = amount / nav
      return shares.toFixed(2) + ' 份'
    }
    
    return '—'
  } else {
    // 赎回：显示订单份额
    return order.shares ? order.shares.toFixed(2) + ' 份' : '—'
  }
}

// 辅助函数：获取账户名称
function getAccountName(accountId: number): string {
  // 递归搜索账户树
  function findInTree(accounts: any[]): any | null {
    for (const acc of accounts) {
      if (acc.id === accountId) return acc
      if (acc.children && acc.children.length > 0) {
        const found = findInTree(acc.children)
        if (found) return found
      }
    }
    return null
  }

  // 先在账户树中查找
  const account = findInTree(accountStore.accountTree)
  if (account) {
    if (account.parentAccountId) {
      const parentAccount = accountStore.accounts.find(a => a.id === account.parentAccountId)
      if (parentAccount) {
        return `${parentAccount.accountName}-${account.accountName}`
      }
    }
    return account.accountName
  }

  // 如果树中找不到，在平铺列表中查找
  const flatAccount = accountStore.accounts.find(a => a.id === accountId)
  return flatAccount ? flatAccount.accountName : `账户ID: ${accountId}`
}

// 订单详情：获取手续费
function getOrderDetailFee(order: any): number {
  return order.settlement?.confirmFee || order.feeEstimate || order.fee || 0
}

onMounted(() => {
  // 确保产品数据已加载
  productStore.fetchProducts()
  loadData()

  // 初始化图表
  const chartDom = document.getElementById('allocationChart')
  if (chartDom) {
    allocationChart = echarts.init(chartDom)
    updateAllocationChart()
  }

  // 监听窗口大小变化
  window.addEventListener('resize', () => {
    allocationChart?.resize()
  })
})
</script>

<style scoped>
.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
}

.detail-label {
  color: #606266;
  font-size: 13px;
  min-width: 80px;
}

.detail-value {
  color: #303133;
  font-size: 13px;
  text-align: right;
  flex: 1;
  margin-left: 16px;
}

.mono {
  font-family: 'SF Mono', Monaco, Consolas, 'Liberation Mono', monospace;
}

.order-detail-content {
  padding: 8px 0;
}

.detail-header {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.detail-section {
  margin-bottom: 16px;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.detail-row:last-child {
  border-bottom: none;
}

.detail-value .sub-text {
  color: #909399;
  font-size: 12px;
  margin-left: 8px;
}

.highlight-amount {
  color: #4ea4ff;
  font-weight: 600;
  font-size: 16px;
}

.divider {
  height: 1px;
  background: #ebeef5;
  margin: 16px 0;
}
</style>
