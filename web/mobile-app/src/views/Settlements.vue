<template>
  <div class="settlements-page">
    <van-nav-bar title="待结算" fixed placeholder>
      <template #right>
        <van-icon name="refresh" @click="onRefresh" />
      </template>
    </van-nav-bar>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <div class="page-container">
        <van-empty v-if="!loading && pendingSettlements.length === 0" description="暂无待结算订单" />

        <div v-else class="settlement-list">
          <div
            v-for="item in pendingSettlements"
            :key="item.orderId"
            class="settlement-card"
            @click="showSettlementDetail(item)"
          >
            <div class="card-header">
              <van-tag :type="getOrderTypeTagType(item.orderType)" size="medium">
                {{ getOrderTypeLabel(item.orderType) }}
              </van-tag>
              <span class="order-id">{{ item.orderId }}</span>
            </div>

            <div class="card-body">
              <div class="product-info">
                <div class="product-name">{{ getProductName(item.productId) }}</div>
                <div class="product-code" v-if="getProductCode(item.productId)">
                  {{ getProductCode(item.productId) }}
                </div>
              </div>

              <div class="amount-info">
                <div class="amount-label">金额</div>
                <div class="amount-value">{{ formatCurrency(item.amount || 0) }}</div>
              </div>

              <div class="meta-info">
                <div class="meta-item">
                  <van-icon name="clock-o" />
                  <span>预期确认：{{ formatDate(item.expectedConfirmDate || '') }}</span>
                </div>
                <div class="meta-item" v-if="item.shares">
                  <van-icon name="chart-trending-o" />
                  <span>预期份额：{{ formatNumber(item.shares, 2) }}</span>
                </div>
              </div>
            </div>

            <div class="card-footer">
              <van-button
                size="small"
                type="primary"
                round
                @click.stop="handleConfirmSettlement(item)"
              >
                确认结算
              </van-button>
            </div>
          </div>
        </div>
      </div>
    </van-pull-refresh>

    <!-- 结算确认弹窗 -->
    <van-popup
      v-model:show="showConfirmDialog"
      position="bottom"
      :style="{ height: '80%' }"
      round
      closeable
    >
      <div class="confirm-popup" v-if="currentSettlement">
        <h3 class="popup-title">确认结算</h3>
        <van-form @submit="handleSubmitSettlement">
          <van-cell-group inset>
            <van-field
              v-model="settlementForm.confirmDate"
              name="confirmDate"
              label="确认日期"
              placeholder="选择确认日期"
              readonly
              is-link
              @click="showDatePicker = true"
              :rules="[{ required: true, message: '请选择确认日期' }]"
            />
            <van-field
              v-model="settlementForm.navDate"
              name="navDate"
              label="净值日期"
              placeholder="选择净值日期"
              readonly
              is-link
              @click="showNavDatePicker = true"
            />
            <van-field
              v-model="settlementForm.confirmNav"
              name="confirmNav"
              label="确认净值"
              placeholder="请输入净值"
              type="number"
              :rules="[{ required: true, message: '请输入净值' }]"
            />
            <van-field
              v-model="settlementForm.confirmShares"
              name="confirmShares"
              :label="isBuyOrder ? '确认份额' : '卖出份额'"
              placeholder="请输入份额"
              type="number"
              :rules="[{ required: true, message: '请输入份额' }]"
            />
            <van-field
              v-model="settlementForm.confirmAmount"
              name="confirmAmount"
              :label="isBuyOrder ? '确认金额' : '到账金额'"
              placeholder="请输入金额"
              type="number"
              :rules="[{ required: true, message: '请输入金额' }]"
            />
            <van-field
              v-model="settlementForm.confirmFee"
              name="confirmFee"
              label="手续费"
              placeholder="请输入手续费"
              type="number"
            />
            <van-field
              v-model="settlementForm.note"
              name="note"
              label="备注"
              placeholder="选填"
              type="textarea"
              rows="3"
            />
          </van-cell-group>
          <div class="form-actions">
            <van-button round block type="primary" native-type="submit" :loading="submitting">
              确认结算
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <!-- 日期选择器 -->
    <van-popup v-model:show="showDatePicker" position="bottom">
      <van-date-picker
        v-model="selectedDate"
        @confirm="handleDateConfirm"
        @cancel="showDatePicker = false"
      />
    </van-popup>

    <van-popup v-model:show="showNavDatePicker" position="bottom">
      <van-date-picker
        v-model="selectedNavDate"
        @confirm="handleNavDateConfirm"
        @cancel="showNavDatePicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { settlementApi, productApi } from '@wealth-hub/shared'
import { formatCurrency, formatDate, formatNumber, getOrderTypeLabel } from '@wealth-hub/shared'
import { showSuccessToast, showFailToast, showLoadingToast, closeToast } from 'vant'
import type { Order, ProductMaster } from '@wealth-hub/shared'

const refreshing = ref(false)
const loading = ref(false)
const submitting = ref(false)
const showConfirmDialog = ref(false)
const showDatePicker = ref(false)
const showNavDatePicker = ref(false)

const pendingSettlements = ref<Order[]>([])
const currentSettlement = ref<Order | null>(null)
const products = ref<ProductMaster[]>([])

const selectedDate = ref<Date[]>([])
const selectedNavDate = ref<Date[]>([])

const settlementForm = ref({
  confirmDate: '',
  navDate: '',
  confirmNav: '',
  confirmShares: '',
  confirmAmount: '',
  confirmFee: '0',
  note: '',
})

const isBuyOrder = computed(() => {
  if (!currentSettlement.value) return true
  return ['BUY', 'SUBSCRIPTION'].includes(currentSettlement.value.orderType)
})

async function loadData() {
  try {
    loading.value = true
    const [settlements, productList] = await Promise.all([
      settlementApi.getPendingSettlements(),
      productApi.getProducts(),
    ])
    pendingSettlements.value = settlements
    products.value = productList
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

function getProductName(productId?: number): string {
  if (!productId) return '—'
  const product = products.value.find(p => p.id === productId)
  return product?.productName || `产品${productId}`
}

function getProductCode(productId?: number): string {
  if (!productId) return ''
  const product = products.value.find(p => p.id === productId)
  return product?.productCode || ''
}

function showSettlementDetail(item: Order) {
  // TODO: 显示订单详情
}

function handleConfirmSettlement(item: Order) {
  currentSettlement.value = item
  settlementForm.value = {
    confirmDate: new Date().toISOString().split('T')[0],
    navDate: item.expectedNavDate || new Date().toISOString().split('T')[0],
    confirmNav: '',
    confirmShares: item.shares?.toString() || '',
    confirmAmount: item.amount?.toString() || '',
    confirmFee: '0',
    note: '',
  }
  showConfirmDialog.value = true
}

function handleDateConfirm({ selectedValues }: any) {
  settlementForm.value.confirmDate = selectedValues.join('-')
  showDatePicker.value = false
}

function handleNavDateConfirm({ selectedValues }: any) {
  settlementForm.value.navDate = selectedValues.join('-')
  showNavDatePicker.value = false
}

async function handleSubmitSettlement() {
  if (!currentSettlement.value) return

  try {
    submitting.value = true
    showLoadingToast({ message: '处理中...', forbidClick: true })

    await settlementApi.confirmSettlement({
      orderId: currentSettlement.value.orderId,
      confirmDate: settlementForm.value.confirmDate,
      navDate: settlementForm.value.navDate,
      confirmNav: parseFloat(settlementForm.value.confirmNav),
      confirmShares: parseFloat(settlementForm.value.confirmShares),
      confirmAmount: parseFloat(settlementForm.value.confirmAmount),
      confirmFee: parseFloat(settlementForm.value.confirmFee || '0'),
      isManualOverride: false,
      note: settlementForm.value.note,
    })

    closeToast()
    showSuccessToast('结算确认成功')
    showConfirmDialog.value = false
    await loadData()
    
    // 触发全局数据刷新
    window.dispatchEvent(new CustomEvent('data-refresh'))
  } catch (error: any) {
    closeToast()
    showFailToast(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.settlements-page {
  width: 100%;
  min-height: 100vh;
  background: var(--bg);
}

.page-container {
  padding: 16px;
  padding-bottom: calc(50px + var(--safe-area-inset-bottom) + 16px);
}

.settlement-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.settlement-card {
  background: var(--card);
  border-radius: var(--radius);
  padding: 16px;
  box-shadow: var(--shadow);
  transition: all 0.2s ease;
}

.settlement-card:active {
  transform: scale(0.98);
  box-shadow: var(--shadow2);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.order-id {
  font-size: 12px;
  color: var(--muted);
  font-family: monospace;
}

.card-body {
  margin-bottom: 16px;
}

.product-info {
  margin-bottom: 12px;
}

.product-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 4px;
}

.product-code {
  font-size: 12px;
  color: var(--muted);
  font-style: italic;
}

.amount-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  margin: 12px 0;
}

.amount-label {
  font-size: 14px;
  color: var(--muted);
}

.amount-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--text);
}

.meta-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--muted);
}

.card-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.confirm-popup {
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

.form-actions {
  margin-top: 24px;
  padding: 0 16px;
}

.form-actions .van-button {
  height: 48px;
  font-size: 16px;
  font-weight: 600;
}
</style>
