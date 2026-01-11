<template>
  <div>
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
        <div class="value">{{ formatCurrency(overview.positionValue) }}</div>
        <div class="mini">= Σ(持仓 shares × 价格)</div>
        <div class="row">
          <span class="chip" :class="unrealizedPnl >= 0 ? 'good' : 'bad'">
            浮动盈亏 {{ unrealizedPnl >= 0 ? '+' : '' }}{{ formatCurrency(unrealizedPnl) }}
          </span>
        </div>
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
            class="card"
            style="padding: 12px; margin-bottom: 8px"
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
                <th>标的</th>
                <th class="right">份额</th>
                <th class="right">成本</th>
                <th class="right">现价</th>
                <th class="right">市值</th>
                <th class="right">盈亏</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="topHoldings.length === 0">
                <td colspan="6" class="td-muted">暂无持仓，去"订单&结算"买一笔试试。</td>
              </tr>
              <tr v-for="holding in topHoldings" :key="holding.productId">
                <td><b>{{ holding.productName || `产品${holding.productId}` }}</b></td>
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
import { dashboardApi, holdingApi, settlementApi, useAccountStore } from '@wealth-hub/shared'
import { formatCurrency, formatNumber, formatDate, getOrderTypeLabel } from '@wealth-hub/shared'

const accountStore = useAccountStore()

const overview = ref({
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

const unrealizedPnl = computed(() => {
  return holdings.value.reduce((sum, h) => sum + (h.unrealizedPnl || 0), 0)
})

const topHoldings = computed(() => {
  return holdings.value
    .sort((a, b) => (b.marketValue || 0) - (a.marketValue || 0))
    .slice(0, 5)
})

let allocationChart: echarts.ECharts | null = null

async function loadData() {
  try {
    // 加载资产概览
    overview.value = await dashboardApi.getAssetOverview()

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
    holdings.value = await holdingApi.getHoldings()

    // 加载账户数据
    await accountStore.fetchAccounts()

    // 更新图表
    updateAllocationChart()
  } catch (error: any) {
    ElMessage.error(error.message || '加载数据失败')
  }
}

function updateAllocationChart() {
  if (!allocationChart) return

  const cashValue = overview.value.cashBalance
  const positionValue = overview.value.positionValue
  const total = cashValue + positionValue

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
          { value: positionValue, name: '持仓' },
          { value: cashValue, name: '现金' },
        ],
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

function handleConfirmSettlement(orderId: string) {
  // TODO: 打开结算确认对话框
  ElMessage.info('结算确认功能开发中')
}

onMounted(() => {
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
