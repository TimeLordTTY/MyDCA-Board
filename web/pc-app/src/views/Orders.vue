<template>
  <div>
    <!-- 新建订单模态框 -->
    <NewOrderModal v-model="newOrderVisible" @success="loadOrders" />

    <!-- 订单详情模态框 -->
    <el-dialog v-model="detailVisible" title="订单详情" width="700px" class="order-detail-dialog">
      <div v-if="selectedOrder" class="order-detail-content">
        <!-- 标题：订单详情 + 订单ID（灰色） -->
        <div class="detail-header">
          <span>订单详情</span>
          <span class="sub-text" style="margin-left: 8px;">{{ selectedOrder.orderId }}</span>
        </div>

        <div class="divider"></div>

        <!-- 详细信息 -->
        <div class="detail-section">
          <!-- 类型 -->
      <div class="detail-row">
            <span class="detail-label">类型</span>
            <span class="detail-value">
              <span class="tag blue">{{ getOrderTypeLabel(selectedOrder.orderType) }}</span>
            </span>
          </div>

          <!-- 产品和标的代码 -->
          <div class="detail-row">
            <span class="detail-label">产品</span>
            <span class="detail-value">
              <div>{{ getProductDisplayName(selectedOrder.productId) }}</div>
              <div class="sub-text" style="margin-top: 4px;">{{ getProductCode(selectedOrder.productId) }}</div>
            </span>
          </div>

          <!-- 状态 -->
          <div class="detail-row">
            <span class="detail-label">状态</span>
            <span class="detail-value">
              <span class="tag" :class="getOrderStatusTagClass(selectedOrder.status)">
                {{ getOrderStatusLabel(selectedOrder.status) }}
              </span>
            </span>
        </div>

          <!-- 金额（买入/申购可编辑，赎回/卖出只读） -->
          <div class="detail-row">
            <span class="detail-label">金额</span>
            <span class="detail-value">
              <el-input-number
                v-if="isBuyOrderType(selectedOrder.orderType) && selectedOrder.status === 'PENDING'"
                v-model="detailEditForm.amount"
                :min="0.01"
                :precision="2"
                style="width: 100%"
                @change="handleDetailAmountChange"
              />
              <span v-else class="mono highlight-amount">{{ getOrderDetailAmount(selectedOrder) }}</span>
            </span>
          </div>

          <!-- 份额（赎回/卖出可编辑，买入/申购只读） -->
          <div class="detail-row">
            <span class="detail-label">份额</span>
            <span class="detail-value">
              <el-input-number
                v-if="!isBuyOrderType(selectedOrder.orderType) && selectedOrder.status === 'PENDING'"
                v-model="detailEditForm.shares"
                :min="0.01"
                :precision="2"
                style="width: 100%"
                @change="handleDetailSharesChange"
              />
              <span v-else class="mono">{{ getOrderDetailShares(selectedOrder) }}</span>
            </span>
          </div>

          <!-- 净值（可编辑） -->
          <div class="detail-row">
            <span class="detail-label">净值</span>
            <span class="detail-value">
              <el-input-number
                v-if="selectedOrder.status === 'PENDING'"
                v-model="detailEditForm.nav"
                :min="0.000001"
                :precision="6"
                style="width: 100%"
                @change="handleDetailNavChange"
              />
              <template v-else>
                <div class="mono">{{ getOrderDetailNav(selectedOrder)?.nav?.toFixed(6) || '—' }}</div>
                <div v-if="getOrderDetailNav(selectedOrder)?.navDate" class="sub-text" style="margin-top: 4px;">
                  {{ getOrderDetailNav(selectedOrder)?.navDate }}
          </div>
              </template>
            </span>
        </div>

          <!-- 净值日期（可编辑） -->
          <div class="detail-row" v-if="selectedOrder.status === 'PENDING'">
            <span class="detail-label">净值日期</span>
            <span class="detail-value">
              <el-date-picker
                v-model="detailEditForm.navDate"
                type="date"
                placeholder="选择净值日期"
                style="width: 100%"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                @change="handleDetailNavDateChange"
              />
              </span>
            </div>
          <div class="detail-row" v-else-if="getOrderDetailNav(selectedOrder)?.navDate">
            <span class="detail-label">净值日期</span>
            <span class="detail-value">{{ getOrderDetailNav(selectedOrder)?.navDate }}</span>
        </div>

          <!-- 订单的发起日期 -->
          <div class="detail-row" v-if="selectedOrder.requestedAt">
            <span class="detail-label">订单的发起日期</span>
            <span class="detail-value">{{ formatDateTime(selectedOrder.requestedAt) }}</span>
            </div>

          <!-- 确认日期（可编辑） -->
          <div class="detail-row" v-if="selectedOrder.status === 'PENDING'">
            <span class="detail-label">确认日期</span>
            <span class="detail-value">
            <el-date-picker
                v-model="detailEditForm.confirmDate"
              type="date"
              placeholder="选择确认日期"
              style="width: 100%"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
            />
              </span>
          </div>
          <div class="detail-row" v-else-if="selectedOrder.settlement?.confirmDate">
            <span class="detail-label">订单的确认日期</span>
            <span class="detail-value">{{ selectedOrder.settlement.confirmDate }}</span>
          </div>

          <!-- 手续费 -->
          <div class="detail-row">
            <span class="detail-label">手续费</span>
            <span class="detail-value">
              <el-input-number
                v-if="selectedOrder.status === 'PENDING'"
                v-model="detailEditForm.fee"
                :min="0"
                :precision="2"
                style="width: 100%"
                @change="handleDetailFeeChange"
              />
              <span v-else class="mono">{{ formatCurrency(getOrderDetailFee(selectedOrder)) }}</span>
            </span>
        </div>

          <!-- 实际到账金额（只读） -->
          <div class="detail-row">
            <span class="detail-label">实际到账</span>
            <span class="detail-value">
              <span class="mono">{{ getOrderNetAmount(selectedOrder) }}</span>
            </span>
          </div>

          <!-- 资金来源 -->
          <div class="detail-row" v-if="sourceFundingLines.length > 0">
            <span class="detail-label">资金来源</span>
            <span class="detail-value">
              <div v-for="line in sourceFundingLines" :key="line.id">
                {{ getAccountName(line.accountId) }}
      </div>
            </span>
          </div>

          <!-- 资金到账 -->
          <div class="detail-row" v-if="targetFundingLines.length > 0">
            <span class="detail-label">资金到账</span>
            <span class="detail-value">
              <div v-for="line in targetFundingLines" :key="line.id">
                {{ getAccountName(line.accountId) }}
              </div>
            </span>
          </div>
        </div>
      </div>

      <template #footer>
        <div style="text-align: right">
          <el-button @click="detailVisible = false">关闭</el-button>
          <el-button
            v-if="selectedOrder && canSettle(selectedOrder)"
            type="primary"
            @click="handleSettleFromDetail"
          >
            结算
          </el-button>
        </div>
      </template>
    </el-dialog>


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
      <div class="ledger-table-container hide-scrollbar">
        <table class="ledger-table">
          <thead>
            <tr>
              <th style="width: 60px;">类型</th>
              <th style="width: 220px;">标的</th>
              <th style="width: 140px;">账户</th>
              <th class="right" style="width: 100px;">
                <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 2px;">
                  <div>份额</div>
                  <div style="font-size: 11px; color: #999; font-weight: normal;">金额</div>
                </div>
              </th>
              <th class="right" style="width: 120px;">净值</th>
              <th class="right" style="width: 140px;">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="6" class="td-muted" style="text-align: center; padding: 24px">加载中...</td>
            </tr>
            <tr v-else-if="orders.length === 0">
              <td colspan="6" class="td-muted" style="text-align: center; padding: 24px">暂无订单</td>
            </tr>
            <tr 
              v-for="(order, index) in orders" 
              :key="order.orderId" 
              :class="{ 'row-even': index % 2 === 0 }"
              :style="getOrderRowStyle(order)"
            >
              <td>
                <span class="tag blue">{{ getOrderTypeLabel(order.orderType) }}</span>
              </td>
              <td>
                <div>{{ getProductDisplayName(order.productId) }}</div>
                <div style="font-size: 12px; color: #909399; margin-top: 2px">
                  {{ getChannelLabel(order.productId) }} · {{ getProductCode(order.productId) }}
                </div>
              </td>
              <td style="white-space: nowrap;">{{ getFundingSourceDisplay(order) }}</td>
              <td class="right" style="white-space: nowrap;">
                <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 4px;">
                  <div class="mono">{{ getOrderListShares(order) }}</div>
                  <div class="mono td-muted">{{ getOrderListAmount(order) }}</div>
                </div>
              </td>
              <td class="right mono" style="white-space: nowrap;">
                <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 2px;">
                  <div>{{ getOrderNav(order).nav }}</div>
                  <div style="font-size: 12px; color: #909399;">
                    {{ getOrderNavDateAndConfirmDate(order) }}
                  </div>
                </div>
              </td>
              <td class="right" style="white-space: nowrap;">
                <div style="display: flex; gap: 8px; justify-content: flex-end;">
                  <button class="btn" @click="handleViewDetail(order)">详情</button>
                  <template v-if="order.status === 'PENDING'">
                    <button class="btn" @click="handleCancelOrder(order)">取消</button>
                    <button 
                      v-if="canSettle(order)" 
                      class="btn primary" 
                      @click="handleSettle(order)"
                    >
                      结算
                    </button>
                  </template>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox, ElNotification } from 'element-plus'
import { orderApi, navApi, formatCurrency, formatDateTime, formatDate, getOrderTypeLabel, getOrderStatusLabel, useAccountStore, useProductStore } from '@wealth-hub/shared'
import type { Order, OrderDetail, OrderFundingLine } from '@wealth-hub/shared'
import NewOrderModal from '../components/NewOrderModal.vue'

const route = useRoute()

const accountStore = useAccountStore()
const productStore = useProductStore()

const loading = ref(false)
const orders = ref<(Order & { fundingLines?: OrderFundingLine[], productName?: string, channel?: string, fee?: number, settlement?: any, navData?: any })[]>([])
const newOrderVisible = ref(false)
const detailVisible = ref(false)
const selectedOrder = ref<OrderDetail | null>(null)
const fundingLines = ref<OrderFundingLine[]>([])
const detailEditForm = ref({
  amount: undefined as number | undefined,
  shares: undefined as number | undefined,
  nav: undefined as number | undefined,
  navDate: undefined as string | undefined,
  confirmDate: undefined as string | undefined,
  fee: undefined as number | undefined,
})


// 区分出金账户（SOURCE）和到账账户（TARGET）
const sourceFundingLines = computed(() => {
  return fundingLines.value.filter(line => !line.lineType || line.lineType === 'SOURCE')
})

const targetFundingLines = computed(() => {
  return fundingLines.value.filter(line => line.lineType === 'TARGET')
})

function getOrderStatusTagClass(status: string): string {
  if (status === 'CONFIRMED') return 'green'
  if (status === 'PENDING') return 'orange'
  if (status === 'CANCELLED' || status === 'FAILED') return 'red'
  return 'gray'
}

async function loadOrders() {
  loading.value = true
  try {
    const orderList = await orderApi.getOrders()
    // 加载每个订单的详细信息（fundingLines、产品信息、费用、结算信息、净值）
    orders.value = await Promise.all(orderList.map(async (order) => {
      try {
        const detail = await orderApi.getOrder(order.orderId)
        const product = productStore.products.find(p => p.id === order.productId)
        // 费用优先级：settlement.confirmFee > detail.feeEstimate（实际费用） > order.feeEstimate
        const fee = detail.settlement?.confirmFee || (detail as any).feeEstimate || order.feeEstimate || 0
        
        // 如果有净值日期但没有结算信息，尝试获取净值
        let navData = null
        if (order.expectedNavDate && !detail.settlement?.confirmNav) {
          try {
            navData = await navApi.getNavByDate(order.productId, order.expectedNavDate)
          } catch (e) {
            // 获取失败则忽略
          }
        }
        
        return {
          ...order,
          fundingLines: detail.fundingLines || [],
          productName: product?.productName,
          channel: product?.channel,
          fee: fee,
          settlement: detail.settlement || null,
          navData: navData // 保存获取的净值数据
        }
      } catch (error) {
        // 如果获取详情失败，使用基本信息
        const product = productStore.products.find(p => p.id === order.productId)
        return {
          ...order,
          fundingLines: [],
          productName: product?.productName,
          channel: product?.channel,
          fee: order.feeEstimate || 0,
          settlement: null,
          navData: null
        }
      }
    }))
  } catch (error: any) {
    ElNotification.error({ title: '错误', message: error.message || '加载失败', position: 'bottom-right' })
  } finally {
    loading.value = false
  }
}

function handleNewOrder() {
  newOrderVisible.value = true
}

async function handleViewDetail(order: Order) {
  try {
    // 确保账户数据已加载，用于“资金来源/到账账户”的账户名称展示
    if (!accountStore.accounts || accountStore.accounts.length === 0) {
      await accountStore.fetchAccounts()
    }
    
    const detail = await orderApi.getOrder(order.orderId)
    selectedOrder.value = detail
    fundingLines.value = detail.fundingLines || []
    
    // 初始化编辑表单
    const isBuy = isBuyOrderType(detail.orderType)
    detailEditForm.value.amount = detail.amount ? Number(detail.amount) : undefined
    detailEditForm.value.shares = detail.shares ? Number(detail.shares) : undefined
    detailEditForm.value.navDate = detail.expectedNavDate || undefined
    const today = new Date().toISOString().split('T')[0]
    detailEditForm.value.confirmDate = detail.expectedConfirmDate || today
    // 手续费：优先使用结算确认的手续费，其次使用订单的feeEstimate
    detailEditForm.value.fee = detail.settlement?.confirmFee || (detail as any).feeEstimate || 0
    
    // 如果未结算，尝试获取净值
    let navValue = undefined
    if (!detail.settlement?.confirmNav) {
      // 优先使用预期净值日期
      if (detail.expectedNavDate) {
        try {
          const navData = await navApi.getNavByDate(detail.productId, detail.expectedNavDate)
          if (navData && navData.nav) {
            navValue = navData.nav
            if (!selectedOrder.value.navData) {
              selectedOrder.value.navData = navData
            }
          }
        } catch (e) {
          // 获取失败，尝试获取最新净值
          try {
            const latestNav = await navApi.getLatestNav(detail.productId)
            if (latestNav && latestNav.nav) {
              navValue = latestNav.nav
              if (!selectedOrder.value.navData) {
                selectedOrder.value.navData = latestNav
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
            if (!selectedOrder.value.navData) {
              selectedOrder.value.navData = latestNav
            }
          }
        } catch (e) {
          // 获取失败则忽略
        }
      }
    } else {
      // 已结算，使用确认净值
      navValue = detail.settlement.confirmNav ? Number(detail.settlement.confirmNav) : undefined
      detailEditForm.value.navDate = detail.settlement.navDate || detail.expectedNavDate || undefined
    }
    
    detailEditForm.value.nav = navValue
    
    // 根据净值自动计算份额或金额
    if (navValue && navValue > 0) {
      if (isBuy && detailEditForm.value.amount) {
        // 买入：按金额和净值计算份额（需要扣除手续费）
        const fee = detailEditForm.value.fee || 0
        const netAmount = detailEditForm.value.amount - fee
        detailEditForm.value.shares = Number((netAmount / navValue).toFixed(2))
      } else if (!isBuy && detailEditForm.value.shares) {
        // 赎回：按份额和净值计算金额
        detailEditForm.value.amount = Number((detailEditForm.value.shares * navValue).toFixed(2))
      }
    }
    
    detailVisible.value = true
  } catch (error: any) {
    ElNotification.error({ title: '错误', message: error.message || '加载详情失败', position: 'bottom-right' })
  }
}

// 订单详情：金额变化时自动计算份额（买入/申购）
function handleDetailAmountChange() {
  if (!selectedOrder.value || !isBuyOrderType(selectedOrder.value.orderType)) return
  if (detailEditForm.value.amount && detailEditForm.value.nav && detailEditForm.value.nav > 0) {
    // 买入：把金额视为“净金额”（已扣除手续费），反推份额：份额 = (金额 + 手续费) / 净值
    const fee = detailEditForm.value.fee || 0
    const grossAmount = detailEditForm.value.amount + fee
    detailEditForm.value.shares = Number((grossAmount / detailEditForm.value.nav).toFixed(2))
  }
}

// 订单详情：份额变化时自动计算金额（赎回/卖出）
function handleDetailSharesChange() {
  if (!selectedOrder.value || isBuyOrderType(selectedOrder.value.orderType)) return
  if (detailEditForm.value.shares && detailEditForm.value.nav && detailEditForm.value.nav > 0) {
    detailEditForm.value.amount = Number((detailEditForm.value.shares * detailEditForm.value.nav).toFixed(2))
  }
}

// 订单详情：净值变化时自动计算份额或金额
function handleDetailNavChange() {
  if (!selectedOrder.value) return
  const isBuy = isBuyOrderType(selectedOrder.value.orderType)
  
  if (detailEditForm.value.nav && detailEditForm.value.nav > 0) {
    if (isBuy && detailEditForm.value.amount) {
      // 买入：金额视为“净金额”，反推份额：份额 = (金额 + 手续费) / 净值
      const fee = detailEditForm.value.fee || 0
      const grossAmount = detailEditForm.value.amount + fee
      detailEditForm.value.shares = Number((grossAmount / detailEditForm.value.nav).toFixed(2))
    } else if (!isBuy && detailEditForm.value.shares) {
      // 赎回：按份额和净值计算金额
      detailEditForm.value.amount = Number((detailEditForm.value.shares * detailEditForm.value.nav).toFixed(2))
    }
  }
}

// 订单详情：手续费变化时联动金额/份额（买入）
function handleDetailFeeChange() {
  if (!selectedOrder.value || !isBuyOrderType(selectedOrder.value.orderType)) return
  // 手续费变化时，优先保持份额不变，重新计算“净金额”
  if (detailEditForm.value.shares && detailEditForm.value.nav && detailEditForm.value.nav > 0) {
    const fee = detailEditForm.value.fee || 0
    const grossAmount = detailEditForm.value.shares * detailEditForm.value.nav
    detailEditForm.value.amount = Number((grossAmount - fee).toFixed(2))
  } else if (detailEditForm.value.amount && detailEditForm.value.nav && detailEditForm.value.nav > 0) {
    // 如果没有份额但有金额，则反推份额
    handleDetailAmountChange()
  }
}

// 订单详情：净值日期变化时自动获取净值
async function handleDetailNavDateChange() {
  if (!detailEditForm.value.navDate || !selectedOrder.value) return
  try {
    const navData = await navApi.getNavByDate(selectedOrder.value.productId, detailEditForm.value.navDate)
    if (navData && navData.nav) {
      detailEditForm.value.nav = navData.nav
      handleDetailNavChange() // 重新计算份额或金额
    } else {
      ElNotification.warning({ title: '警告', message: '未找到该日期的净值数据，请手动输入', position: 'bottom-right' })
    }
  } catch (error: any) {
    ElNotification.warning({ title: '警告', message: '获取净值失败，请手动输入', position: 'bottom-right' })
  }
}

async function handleSettleFromDetail() {
  if (!selectedOrder.value) return
  
  // 验证必填字段
  if (!detailEditForm.value.nav || detailEditForm.value.nav <= 0) {
    ElNotification.error({ title: '错误', message: '请填写净值', position: 'bottom-right' })
    return
  }
  
  if (!detailEditForm.value.navDate) {
    ElNotification.error({ title: '错误', message: '请填写净值日期', position: 'bottom-right' })
    return
  }
  
  const isBuy = isBuyOrderType(selectedOrder.value.orderType)
  
  if (isBuy) {
    if (!detailEditForm.value.amount || detailEditForm.value.amount <= 0) {
      ElNotification.error({ title: '错误', message: '请填写金额', position: 'bottom-right' })
      return
    }
    if (!detailEditForm.value.shares || detailEditForm.value.shares <= 0) {
      ElNotification.error({ title: '错误', message: '请填写份额', position: 'bottom-right' })
      return
    }
  } else {
    if (!detailEditForm.value.shares || detailEditForm.value.shares <= 0) {
      ElNotification.error({ title: '错误', message: '请填写份额', position: 'bottom-right' })
      return
    }
    if (!detailEditForm.value.amount || detailEditForm.value.amount <= 0) {
      ElNotification.error({ title: '错误', message: '请填写金额', position: 'bottom-right' })
      return
    }
  }
  
  if (!detailEditForm.value.confirmDate) {
    ElNotification.error({ title: '错误', message: '请填写确认日期', position: 'bottom-right' })
    return
  }
  
  try {
    await orderApi.confirmSettlement({
      orderId: selectedOrder.value.orderId,
      confirmDate: detailEditForm.value.confirmDate,
      navDate: detailEditForm.value.navDate!,
      confirmNav: detailEditForm.value.nav!,
      confirmShares: isBuy ? detailEditForm.value.shares : undefined,
      confirmAmount: !isBuy ? detailEditForm.value.amount : undefined,
      // 如果用户输入了手续费（包括0），传递用户输入的值；如果未输入（undefined/null），传递undefined让后端自动计算
      confirmFee: detailEditForm.value.fee !== undefined && detailEditForm.value.fee !== null ? detailEditForm.value.fee : undefined,
      note: ''
    })
    
    ElNotification({
      title: '结算成功',
      message: '订单已确认结算',
      type: 'success',
      position: 'bottom-right',
      duration: 3000,
    })
    
    // 结算成功后：关闭详情弹窗并刷新订单列表
    detailVisible.value = false
    await loadOrders()
    
    // 通知其他页面（流水、持仓等）刷新数据
    window.dispatchEvent(new CustomEvent('data-refresh'))
  } catch (error: any) {
    ElNotification.error({ title: '错误', message: error.message || '结算失败', position: 'bottom-right' })
  }
}

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
    // 虚拟账户（如持仓虚拟账户）在“资金来源/到账”里不展示
    if (account.accountKind === 'VIRTUAL') {
      return ''
    }
    if (account.parentAccountId) {
      const parentAccount = accountStore.accounts.find(a => a.id === account.parentAccountId)
      if (parentAccount) {
        return `${parentAccount.accountName}-${account.accountName}`
      }
    }
    return account.accountName
  }

  // 尝试从扁平列表查找
  const flatAccount = accountStore.accounts.find((a) => a.id === accountId)
  if (flatAccount) {
    if (flatAccount.accountKind === 'VIRTUAL') {
      return ''
    }
    if (flatAccount.parentAccountId) {
      const parent = accountStore.accounts.find((a) => a.id === flatAccount.parentAccountId)
      if (parent) {
        return `${parent.accountName}-${flatAccount.accountName}`
      }
    }
    return flatAccount.accountName
  }
  // 找不到时，给出更友好的占位文本
  return `未知账户（ID: ${accountId}）`
}

function getProductDisplayName(productId: number): string {
  const product = productStore.products.find(p => p.id === productId)
  if (!product) return `产品ID: ${productId}`
  return product.productName
}

function getChannelLabel(productId: number): string {
  const product = productStore.products.find(p => p.id === productId)
  if (!product) return ''
  return product.channel === 'OTC' ? '场外' : '场内'
}

function getProductCode(productId: number): string {
  const product = productStore.products.find(p => p.id === productId)
  return product?.productCode || ''
}

function getOrderNav(order: Order & { settlement?: any, navData?: any }): { nav: string, navDate?: string } {
  // 优先从结算信息中获取净值
  if (order.settlement?.confirmNav) {
    return {
      nav: order.settlement.confirmNav.toFixed(6),
      navDate: order.settlement.navDate
    }
  }
  // 如果有预加载的净值数据
  if (order.navData?.nav) {
    return {
      nav: order.navData.nav.toFixed(6),
      navDate: order.expectedNavDate
    }
  }
  // 如果订单有预期净值日期但没有结算或净值数据，显示待确认
  if (order.expectedNavDate) {
    return { nav: '待确认', navDate: order.expectedNavDate }
  }
  return { nav: '—' }
}

function getOrderConfirmDate(order: Order & { settlement?: any }): string {
  const settledDate = order.settlement?.confirmDate
  const expectedDate = order.expectedConfirmDate
  const v = settledDate || expectedDate
  return v ? formatDate(v) : '—'
}

// 订单列表：计算金额显示（保留2位小数）
function getOrderListAmount(order: Order & { settlement?: any, navData?: any }): string {
  const isBuy = isBuyOrderType(order.orderType)
  
  if (isBuy) {
    // 买入：显示订单金额
    return order.amount ? formatCurrency(Number(order.amount.toFixed(2))) : '—'
  } else {
    // 赎回：按净值计算金额
    const nav = order.settlement?.confirmNav || order.navData?.nav
    const shares = order.shares || 0
    
    if (nav && nav > 0 && shares > 0) {
      const amount = shares * nav
      return formatCurrency(Number(amount.toFixed(2)))
    }
    
    // 如果没有净值，显示订单金额（如果有）
    return order.amount ? formatCurrency(Number(order.amount.toFixed(2))) : '—'
  }
}

// 订单列表：计算份额显示（保留2位小数）
function getOrderListShares(order: Order & { settlement?: any, navData?: any, fee?: number }): string {
  const isBuy = isBuyOrderType(order.orderType)
  
  if (isBuy) {
    // 买入：按净值计算份额（需要扣除手续费）
    const nav = order.settlement?.confirmNav || order.navData?.nav
    const amount = order.amount || 0
    const fee = order.settlement?.confirmFee ?? order.fee ?? 0
    
    if (nav && nav > 0 && amount > 0) {
      const netAmount = amount - fee
      const shares = netAmount / nav
      return shares.toFixed(2)
    }
    
    return '—'
  } else {
    // 赎回：显示订单份额
    return order.shares ? order.shares.toFixed(2) : '—'
  }
}

// 订单列表：显示净值日期和确认日期
function getOrderNavDateAndConfirmDate(order: Order & { settlement?: any, navData?: any }): string {
  const navDate = order.settlement?.navDate || order.navData?.navDate || order.expectedNavDate
  const confirmDate = order.settlement?.confirmDate || order.expectedConfirmDate
  
  const parts: string[] = []
  if (navDate) parts.push(navDate)
  if (confirmDate) parts.push(confirmDate)
  
  return parts.length > 0 ? parts.join(' / ') : '—'
}

// 订单列表：获取行样式（用颜色区分状态）
function getOrderRowStyle(order: Order): any {
  if (order.status === 'CONFIRMED') {
    return { backgroundColor: 'rgba(245, 101, 101, 0.15)' } // 已确认：红色
  }
  if (order.status === 'CANCELLED' || order.status === 'FAILED') {
    return { backgroundColor: 'rgba(103, 194, 58, 0.15)' } // 已取消：绿色
  }
  if (order.status === 'PENDING') {
    return { backgroundColor: 'rgba(230, 162, 60, 0.15)' } // 待结算：黄色
  }
  return {}
}

function isBuyOrderType(orderType: string): boolean {
  return orderType === 'BUY' || orderType === 'SUBSCRIPTION'
}

function getFundingSourceDisplay(order: Order & { fundingLines?: OrderFundingLine[] }): string {
  if (!order.fundingLines || order.fundingLines.length === 0) {
    return '—'
  }
  
  // 区分 SOURCE 和 TARGET
  const sourceLines = order.fundingLines.filter(line => !line.lineType || line.lineType === 'SOURCE')
  const targetLines = order.fundingLines.filter(line => line.lineType === 'TARGET')
  
  // 如果是买入类型，显示资金来源
  if (isBuyOrderType(order.orderType)) {
    if (sourceLines.length === 1) {
      return getAccountName(sourceLines[0].accountId)
    }
    return sourceLines.length > 1 ? `组合支付(${sourceLines.length}个账户)` : '—'
  }
  
  // 如果是卖出/赎回类型，优先显示到账账户
  if (targetLines.length === 1) {
    return getAccountName(targetLines[0].accountId)
  } else if (targetLines.length > 1) {
    return `多账户到账(${targetLines.length}个)`
  }
  
  // 没有 TARGET 账户，显示 SOURCE（可能是旧数据）
  if (sourceLines.length === 1) {
    return getAccountName(sourceLines[0].accountId)
  }
  return sourceLines.length > 1 ? `多账户(${sourceLines.length}个)` : '—'
}

function getOrderFee(order: Order & { fee?: number }): number {
  return order.fee || order.feeEstimate || 0
}

// 订单详情：计算金额（保留2位小数）
function getOrderDetailAmount(order: OrderDetail & { navData?: any }): string {
  // 如果正在编辑，使用编辑表单的值
  if (detailEditForm.value.amount !== undefined) {
    return formatCurrency(Number(detailEditForm.value.amount.toFixed(2)))
  }
  
  const isBuy = isBuyOrderType(order.orderType)
  
  if (isBuy) {
    // 买入：金额使用订单金额（下单时的总扣款），不在这里再次推导，避免与下单金额不一致
    return order.amount ? formatCurrency(Number(order.amount.toFixed(2))) : '—'
  } else {
    // 赎回：按净值计算金额
    const nav = order.settlement?.confirmNav || (order as any).navData?.nav
    const shares = order.shares || 0
    
    if (nav && nav > 0 && shares > 0) {
      const amount = shares * nav
      return formatCurrency(Number(amount.toFixed(2)))
    }
    
    // 如果没有净值，显示订单金额（如果有）
    return order.amount ? formatCurrency(order.amount) : '—'
  }
}

// 订单详情：实际到账金额（金额 - 手续费）
function getOrderNetAmount(order: OrderDetail & { navData?: any }): string {
  const isBuy = isBuyOrderType(order.orderType)

  // 编辑状态下优先使用表单里的值
  const editingAmount = detailEditForm.value.amount
  const editingShares = detailEditForm.value.shares
  const editingNav = detailEditForm.value.nav
  const editingFee = detailEditForm.value.fee

  // 统一获取手续费：编辑表单 > 结算确认 > 预估费
  const baseFee =
    editingFee ??
    order.settlement?.confirmFee ??
    ((order as any).feeEstimate ?? order.feeEstimate ?? 0)

  if (isBuy) {
    // 买入：净支出 = 金额（下单金额）或编辑金额
    const grossAmount =
      editingAmount !== undefined
        ? editingAmount
        : order.amount !== undefined
          ? Number(order.amount)
          : 0
    return formatCurrency(Number(grossAmount.toFixed(2)))
  }

  // 卖出/赎回：净到账 = 份额 * 净值 - 手续费
  let shares =
    editingShares !== undefined
      ? editingShares
      : order.shares !== undefined
        ? Number(order.shares)
        : undefined

  let nav =
    editingNav !== undefined
      ? editingNav
      : order.settlement?.confirmNav ??
        ((order as any).navData?.nav as number | undefined)

  if (shares && nav) {
    const grossAmount = shares * nav
    const netAmount = grossAmount - (baseFee || 0)
    return formatCurrency(Number(netAmount.toFixed(2)))
  }

  // 回退：如果缺少份额或净值，则用「金额 - 手续费」
  const fallbackAmount =
    editingAmount !== undefined
      ? editingAmount
      : order.amount !== undefined
        ? Number(order.amount)
        : 0
  const netFallback = fallbackAmount - (baseFee || 0)
  return formatCurrency(Number(netFallback.toFixed(2)))
}

// 订单详情：获取净值
function getOrderDetailNav(order: OrderDetail & { navData?: any }): { nav: number, navDate?: string } | null {
  // 如果正在编辑，使用编辑表单的值
  if (detailEditForm.value.nav !== undefined) {
    return {
      nav: detailEditForm.value.nav,
      navDate: detailEditForm.value.navDate
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
  if ((order as any).navData?.nav) {
    return {
      nav: (order as any).navData.nav,
      navDate: (order as any).navData.navDate || order.expectedNavDate
    }
  }
  
  return null
}

// 订单详情：获取份额
function getOrderDetailShares(order: OrderDetail): string {
  // 如果正在编辑，使用编辑表单的值
  if (detailEditForm.value.shares !== undefined) {
    return detailEditForm.value.shares.toFixed(2) + ' 份'
  }
  
  const isBuy = isBuyOrderType(order.orderType)
  
  if (isBuy) {
    // 买入：按净值计算份额（需要扣除手续费）
    const nav = order.settlement?.confirmNav || (order as any).navData?.nav
    const amount = order.amount || 0
    const fee = order.settlement?.confirmFee ?? (order as any).feeEstimate ?? 0
    
    if (nav && nav > 0 && amount > 0) {
      const netAmount = amount - fee
      const shares = netAmount / nav
      return shares.toFixed(2) + ' 份'
    }
    
    return '—'
  } else {
    // 赎回：显示订单份额
    return order.shares ? order.shares.toFixed(2) + ' 份' : '—'
  }
}

// 订单详情：获取手续费
function getOrderDetailFee(order: OrderDetail & { fee?: number, feeEstimate?: number }): number {
  // 优先使用结算确认的手续费（即使是0也要使用，因为0是有效值）
  if (order.settlement && order.settlement.confirmFee !== undefined && order.settlement.confirmFee !== null) {
    return order.settlement.confirmFee
  }
  // 其次使用订单的feeEstimate
  if (order.feeEstimate !== undefined && order.feeEstimate !== null) {
    return order.feeEstimate
  }
  // 最后使用order.fee（如果存在）
  if ((order as any).fee !== undefined && (order as any).fee !== null) {
    return (order as any).fee
  }
  // 如果都没有，返回0
  return 0
}

function canSettle(order: Order): boolean {
  if (order.status !== 'PENDING') return false
  if (!order.expectedConfirmDate) return true
  const today = new Date().toISOString().split('T')[0]
  return order.expectedConfirmDate <= today
}

async function handleSettle(order: Order) {
  // 直接进行结算，不弹窗
  const today = new Date().toISOString().split('T')[0]
  const confirmDate = order.expectedConfirmDate || today
  const navDate = order.expectedNavDate || today
  
  // 自动获取净值
  let confirmNav: number | undefined = undefined
  if (navDate) {
    try {
      const navData = await navApi.getNavByDate(order.productId, navDate)
      if (navData && navData.nav) {
        confirmNav = navData.nav
      }
    } catch (error: any) {
      // 获取净值失败，需要用户手动输入，打开详情页面
      ElNotification.warning({ title: '警告', message: '无法自动获取净值，请点击"详情"手动输入净值后结算', position: 'bottom-right' })
      await handleViewDetail(order)
    return
  }
  }
  
  if (!confirmNav) {
    // 没有净值，需要用户手动输入，打开详情页面
    ElNotification.warning({ title: '警告', message: '请点击"详情"输入净值后结算', position: 'bottom-right' })
    await handleViewDetail(order)
    return
  }

  // 计算份额或金额
  const isBuy = isBuyOrderType(order.orderType)
  let confirmShares: number | undefined = undefined
  let confirmAmount: number | undefined = undefined
  
  if (isBuy && order.amount) {
    // 买入/申购：计算份额
    const fee = order.feeEstimate || 0
    const netAmount = order.amount - fee
    confirmShares = Number((netAmount / confirmNav).toFixed(6))
  } else if (!isBuy && order.shares) {
    // 卖出/赎回：计算金额
    confirmAmount = Number((order.shares * confirmNav).toFixed(2))
  }
  
  // 直接调用结算接口
  try {
    await orderApi.confirmSettlement({
      orderId: order.orderId,
      confirmDate: confirmDate,
      navDate: navDate,
      confirmNav: confirmNav,
      confirmShares: confirmShares,
      confirmAmount: confirmAmount,
      confirmFee: order.feeEstimate || 0,
      note: ''
    })
    ElNotification({
      title: '结算成功',
      message: '订单已确认结算',
      type: 'success',
      position: 'bottom-right',
      duration: 3000,
    })
    await loadOrders()
  } catch (error: any) {
    ElNotification.error({ title: '错误', message: error.message || '结算失败', position: 'bottom-right' })
  }
}

function calculateShares(amount: number, nav: number, fee: number = 0): number {
  if (!nav || nav <= 0) return 0
  const netAmount = amount - fee
  return netAmount / nav
}

function calculateAmount(shares: number, nav: number): number {
  return shares * nav
}

async function handleCancelOrder(order: Order) {
  ElMessageBox.confirm(`确定要取消订单"${order.orderId}"吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await orderApi.cancelOrder(order.orderId)
      ElNotification.success({ title: '成功', message: '取消成功', position: 'bottom-right' })
      await loadOrders()
    } catch (error: any) {
      ElNotification.error({ title: '错误', message: error.message || '取消失败', position: 'bottom-right' })
    }
  })
}

onMounted(async () => {
  accountStore.fetchAccounts()
  productStore.fetchProducts()
  await loadOrders()
  
  // 检查URL参数，如果有settle参数，自动进行结算
  const settleOrderId = route.query.settle as string
  if (settleOrderId) {
    const order = orders.value.find(o => o.orderId === settleOrderId)
    if (order && canSettle(order)) {
      await handleSettle(order)
      // 清除URL参数
      window.history.replaceState({}, '', '/orders')
    }
  }
  
  // 监听全局数据刷新事件
  window.addEventListener('data-refresh', loadOrders)
})
</script>

<style scoped>
/* 订单详情弹窗样式 */
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

.detail-label {
  color: #606266;
  font-size: 14px;
  min-width: 80px;
}

.detail-value {
  color: #303133;
  font-size: 14px;
  text-align: right;
  flex: 1;
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

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.funding-list {
  background: #f8f9fa;
  border-radius: 8px;
  padding: 12px;
}

.funding-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px dashed rgba(0, 0, 0, 0.08);
}

.funding-item:last-child {
  border-bottom: none;
}

.funding-account {
  color: #606266;
  font-size: 13px;
}

.funding-amount {
  color: #303133;
  font-weight: 500;
}

.mono {
  font-family: 'SF Mono', Monaco, Consolas, 'Liberation Mono', monospace;
}

.nav-date {
  color: #909399;
  font-size: 12px;
  margin-left: 4px;
}

.divider {
  height: 1px;
  background: #ebeef5;
  margin: 16px 0;
}
</style>
