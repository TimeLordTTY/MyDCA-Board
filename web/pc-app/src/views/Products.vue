<template>
  <div class="products-page-container">
    <div class="card" style="flex-shrink: 0; margin-bottom: 12px; padding: 12px 16px;">
      <div class="row-between">
        <div>
          <h3 style="margin: 0 0 4px 0; font-size: 16px;">
            产品管理
            <span class="tag blue tiny">可新增/编辑/停用</span>
          </h3>
          <div class="sub" style="margin: 0; font-size: 12px;">
            产品是全局字典：ETF / 基金 / 货基 / 逆回购。后续接行情服务时，code 用于查询。
          </div>
        </div>
        <div class="row-gap">
          <el-button type="primary" size="small" @click="handleAddProduct">＋ 新增产品</el-button>
        </div>
      </div>
    </div>

    <!-- 产品列表 - 左右分栏 -->
    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; flex: 1; min-height: 0; overflow: hidden;">
        <!-- 左侧：场内产品 -->
        <div class="card" style="padding: 12px; display: flex; flex-direction: column; min-height: 0;">
          <div class="row-gap" style="margin-bottom: 8px; flex-shrink: 0;">
            <h3 style="margin: 0; font-size: 14px; font-weight: 600;">场内产品</h3>
          </div>
          <!-- 左侧筛选条件 -->
          <div class="row-gap" style="margin-bottom: 8px; flex-shrink: 0;">
            <el-input
              v-model="exchangeFilters.keyword"
              placeholder="搜索产品名称或代码"
              style="width: 180px"
              clearable
              @clear="loadExchangeProducts"
              @keyup.enter="loadExchangeProducts"
            />
            <el-select
              v-model="exchangeFilters.assetType"
              placeholder="资产类型"
              style="width: 130px"
              clearable
              @change="loadExchangeProducts"
            >
              <el-option
                v-for="(label, value) in assetTypeMap"
                :key="value"
                :label="label"
                :value="value"
              />
            </el-select>
            <el-select
              v-model="exchangeFilters.isActive"
              placeholder="是否启用"
              style="width: 100px"
              @change="loadExchangeProducts"
            >
              <el-option label="启用" :value="true" />
              <el-option label="停用" :value="false" />
              <el-option label="全部" :value="undefined" />
            </el-select>
          </div>
          <div style="flex: 1; overflow: auto; min-height: 0;" class="hide-scrollbar">
            <table>
              <thead>
                <tr>
                  <th>名称</th>
                  <th>类型</th>
                  <th>市场</th>
                  <th class="right" style="min-width: 80px; white-space: nowrap;">操作</th>
                </tr>
              </thead>
              <tbody>
                <template v-if="loading">
                  <tr>
                    <td colspan="4" class="td-muted" style="text-align: center">加载中...</td>
                  </tr>
                </template>
                <template v-else-if="exchangeProducts.length === 0">
                  <tr>
                    <td colspan="4" class="td-muted" style="text-align: center">暂无场内产品</td>
                  </tr>
                </template>
                <template v-else>
                  <tr
                    v-for="(product, index) in exchangeProducts"
                    :key="product.id"
                    draggable="true"
                    :data-index="index"
                    @dragstart="handleDragStart($event, index, 'exchange')"
                    @dragover.prevent="handleDragOver($event, index, 'exchange')"
                    @drop="handleDrop($event, index, 'exchange')"
                    @dragend="handleDragEnd"
                    style="cursor: move;"
                    :class="{ 'drag-over': dragOverIndex === index && dragOverType === 'exchange' }"
                  >
                    <td>
                      <div style="display: flex; align-items: center; gap: 8px;">
                        <span style="color: #999; font-size: 12px; user-select: none;">⋮⋮</span>
                        <div>
                          <b style="cursor: pointer; color: #4ea4ff;" @click="handleViewHolding(product)">
                            {{ product.productName }}
                          </b>
                          <div style="font-size: 12px; color: #999; font-style: italic; margin-top: 4px;" class="mono">
                            {{ product.productCode }}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td>
                      <span class="tag blue">{{ getAssetTypeLabel(product.assetType) }}</span>
                    </td>
                    <td>{{ getMarketLabel(product.market) }}</td>
                    <td class="right" style="white-space: nowrap; padding: 8px;">
                      <button class="btn-small" @click="handleEdit(product)" title="编辑产品">✏️ 编辑</button>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
          </div>
        </div>

        <!-- 右侧：场外产品 -->
        <div class="card" style="padding: 12px; display: flex; flex-direction: column; min-height: 0;">
          <div class="row-gap" style="margin-bottom: 8px; flex-shrink: 0;">
            <h3 style="margin: 0; font-size: 14px; font-weight: 600;">场外产品</h3>
          </div>
          <!-- 右侧筛选条件 -->
          <div class="row-gap" style="margin-bottom: 8px; flex-shrink: 0;">
            <el-input
              v-model="otcFilters.keyword"
              placeholder="搜索产品名称或代码"
              style="width: 180px"
              clearable
              @clear="loadOtcProducts"
              @keyup.enter="loadOtcProducts"
            />
            <el-select
              v-model="otcFilters.assetType"
              placeholder="资产类型"
              style="width: 130px"
              clearable
              @change="loadOtcProducts"
            >
              <el-option
                v-for="(label, value) in assetTypeMap"
                :key="value"
                :label="label"
                :value="value"
              />
            </el-select>
            <el-select
              v-model="otcFilters.isActive"
              placeholder="是否启用"
              style="width: 100px"
              @change="loadOtcProducts"
            >
              <el-option label="启用" :value="true" />
              <el-option label="停用" :value="false" />
              <el-option label="全部" :value="undefined" />
            </el-select>
          </div>
          <div ref="otcTableContainer" style="flex: 1; overflow: auto; min-height: 0;" class="hide-scrollbar">
            <table>
              <thead>
                <tr>
                  <th>名称</th>
                  <th>类型</th>
                  <th class="right" style="min-width: 80px; white-space: nowrap;">操作</th>
                </tr>
              </thead>
              <tbody>
                <template v-if="loading">
                  <tr>
                    <td colspan="3" class="td-muted" style="text-align: center">加载中...</td>
                  </tr>
                </template>
                <template v-else-if="otcProducts.length === 0">
                  <tr>
                    <td colspan="3" class="td-muted" style="text-align: center">暂无场外产品</td>
                  </tr>
                </template>
                <template v-else>
                  <tr
                    v-for="(product, index) in otcProducts"
                    :key="product.id"
                    draggable="true"
                    :data-index="index"
                    @dragstart="handleDragStart($event, index, 'otc')"
                    @dragover.prevent="handleDragOver($event, index, 'otc')"
                    @drop="handleDrop($event, index, 'otc')"
                    @dragend="handleDragEnd"
                    @dragleave="stopAutoScroll"
                    style="cursor: move;"
                    :class="{ 'drag-over': dragOverIndex === index && dragOverType === 'otc' }"
                  >
                    <td>
                      <div style="display: flex; align-items: center; gap: 8px;">
                        <span style="color: #999; font-size: 12px; user-select: none;">⋮⋮</span>
                        <div>
                          <b style="cursor: pointer; color: #4ea4ff;" @click="handleViewHolding(product)">
                            {{ product.productName }}
                          </b>
                          <div style="font-size: 12px; color: #999; font-style: italic; margin-top: 4px;" class="mono">
                            {{ product.productCode }}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td>
                      <span class="tag blue">{{ getAssetTypeLabel(product.assetType) }}</span>
                    </td>
                    <td class="right" style="white-space: nowrap; padding: 8px;">
                      <button class="btn-small" @click="handleEdit(product)" title="编辑产品">✏️ 编辑</button>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
          </div>
        </div>
      </div>

    <!-- 新增/编辑产品对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingProduct ? '编辑产品' : '新增产品'"
      width="800px"
      @close="handleDialogClose"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="产品代码" prop="productCode">
              <el-input v-model="form.productCode" placeholder="如：000001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产品名称" prop="productName">
              <el-input v-model="form.productName" placeholder="产品名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="资产类型" prop="assetType">
              <el-select v-model="form.assetType" placeholder="选择资产类型" style="width: 100%">
                <el-option
                  v-for="(label, value) in assetTypeMap"
                  :key="value"
                  :label="label"
                  :value="value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="渠道" prop="channel">
              <el-select v-model="form.channel" placeholder="选择渠道" style="width: 100%">
                <el-option
                  v-for="(label, value) in channelMap"
                  :key="value"
                  :label="label"
                  :value="value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="市场" prop="market">
              <el-select v-model="form.market" placeholder="选择市场" style="width: 100%">
                <el-option
                  v-for="(label, value) in marketMap"
                  :key="value"
                  :label="label"
                  :value="value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="币种" prop="currency">
              <el-select v-model="form.currency" placeholder="选择币种" style="width: 100%">
                <el-option
                  v-for="(label, value) in currencyMap"
                  :key="value"
                  :label="label"
                  :value="value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否QDII">
              <el-switch v-model="form.isQdii" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="跟踪指数">
          <el-input v-model="form.trackIndex" placeholder="如：沪深300" />
        </el-form-item>

        <!-- 场外产品显示费率配置，场内产品不显示（使用券商账户费率） -->
        <template v-if="form.channel === 'OTC' && form.assetType !== 'BANK_WM_NAV' && form.assetType !== 'MMF'">
          <el-form-item label="买入费率" prop="buyFeeRate">
            <el-input-number
              v-model="buyFeeRatePercent"
              :precision="6"
              :step="0.0001"
              :min="0"
              :max="100"
              style="width: 100%"
            >
              <template #suffix>%</template>
            </el-input-number>
            <div class="sub">场外基金买入费率（0-100万档）</div>
          </el-form-item>
          
          <!-- 卖出费率分段配置 -->
          <el-form-item label="卖出费率分段" required>
            <div style="margin-bottom: 12px;">
              <el-button size="small" type="primary" @click="handleAddFeeTier">+ 添加分段</el-button>
            </div>
            <el-table 
              :data="sellFeeTiers" 
              border 
              style="width: 100%"
              :cell-style="{ padding: '8px 6px' }"
            >
              <el-table-column prop="minDays" label="最小持有天数" width="120" align="center">
                <template #default="scope">
                  <el-input
                    v-if="scope.row"
                    v-model="scope.row.minDays"
                    type="number"
                    placeholder="0"
                    style="width: 100%"
                    @blur="scope.row.minDays = scope.row.minDays === '' ? 0 : Number(scope.row.minDays) || 0"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="maxDays" label="最大持有天数" width="140" align="center">
                <template #default="scope">
                  <el-input
                    v-if="scope.row"
                    v-model="scope.row.maxDaysInput"
                    type="number"
                    placeholder="留空表示无上限"
                    style="width: 100%"
                    @blur="handleMaxDaysBlur(scope.row)"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="sellFeeRatePercent" label="卖出费率(%)" width="130" align="center">
                <template #default="scope">
                  <el-input
                    v-if="scope.row"
                    v-model="scope.row.sellFeeRatePercent"
                    type="number"
                    placeholder="0.000000"
                    style="width: 100%"
                    @blur="scope.row.sellFeeRatePercent = scope.row.sellFeeRatePercent === '' ? 0 : Number(scope.row.sellFeeRatePercent) || 0"
                  />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70" fixed="right" align="center">
                <template #default="scope">
                  <el-button
                    v-if="scope.$index !== undefined"
                    type="danger"
                    link
                    size="small"
                    @click="handleRemoveFeeTier(scope.$index)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="sub" style="margin-top: 12px; color: #999; line-height: 1.6;">
              提示：普通基金只使用分段费率，不使用默认卖出费率。持有天数使用左闭右开区间（如0-7表示[0, 7)，7-30表示[7, 30)）。最大持有天数留空表示无上限。
            </div>
          </el-form-item>
        </template>
        <template v-else-if="form.channel === 'EXCHANGE'">
          <el-form-item>
            <div class="sub" style="color: #999;">
              场内产品费率由券商账户配置决定，无需在此设置
            </div>
          </el-form-item>
        </template>
        <template v-else-if="form.assetType === 'BANK_WM_NAV' || form.assetType === 'MMF'">
          <el-form-item>
            <div class="sub" style="color: #999;">
              银行理财净值型和货币基金无买入卖出费率
            </div>
          </el-form-item>
        </template>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="买入确认延迟" prop="buyConfirmOffset">
              <el-input-number v-model="form.buyConfirmOffset" :min="1" style="width: 100%" />
              <div class="sub">T+N中的N</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="卖出确认延迟" prop="sellConfirmOffset">
              <el-input-number v-model="form.sellConfirmOffset" :min="1" style="width: 100%" />
              <div class="sub">T+N中的N</div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="交易截单时间" prop="cutoffTime">
          <el-input v-model="form.cutoffTime" placeholder="如：15:00" />
        </el-form-item>

        <el-form-item label="数据来源">
          <el-select v-model="form.dataSource" placeholder="选择数据来源" style="width: 100%" clearable>
            <el-option label="基金净值 (fund)" value="fund" />
            <el-option label="AKShare (akshare)" value="akshare" />
            <el-option label="招商银行 (cmbc)" value="cmbc" />
            <el-option label="手动录入 (manual)" value="manual" />
          </el-select>
        </el-form-item>

        <el-form-item label="是否启用">
          <el-switch v-model="form.isActive" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.note" type="textarea" :rows="3" placeholder="备注信息" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElNotification, type FormInstance, type FormRules } from 'element-plus'
import {
  useProductStore,
  assetTypeMap,
  channelMap,
  marketMap,
  currencyMap,
  getAssetTypeLabel,
  getMarketLabel,
  productApi,
} from '@wealth-hub/shared'
import type { ProductMaster } from '@wealth-hub/shared'
import type { FundSellFeeTier } from '@wealth-hub/shared/src/api/product'

const productStore = useProductStore()
const router = useRouter()

// 使用本地ref来存储产品列表，确保响应式
const allProducts = ref<ProductMaster[]>([])
const exchangeProducts = ref<ProductMaster[]>([])
const otcProducts = ref<ProductMaster[]>([])
const loading = ref(false)

// 左侧场内产品筛选条件
const exchangeFilters = reactive({
  keyword: '',
  assetType: '',
  isActive: true as boolean | undefined,
})

// 右侧场外产品筛选条件
const otcFilters = reactive({
  keyword: '',
  assetType: '',
  isActive: true as boolean | undefined,
})

const dialogVisible = ref(false)
const editingProduct = ref<ProductMaster | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

// 拖拽排序相关
const draggedIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)
const dragOverType = ref<'exchange' | 'otc' | null>(null)
const draggedType = ref<'exchange' | 'otc' | null>(null)
const scrollInterval = ref<number | null>(null)
const exchangeTableContainer = ref<HTMLElement | null>(null)
const otcTableContainer = ref<HTMLElement | null>(null)

const form = reactive<Partial<ProductMaster>>({
  productCode: '',
  productName: '',
  assetType: 'FUND',
  channel: 'OTC',
  market: 'NA',
  currency: 'CNY',
  isQdii: false,
  trackIndex: '',
  buyFeeRate: 0,
  sellFeeRate: 0,
  buyConfirmOffset: 1,
  sellConfirmOffset: 1,
  cutoffTime: '15:00',
  dataSource: '',
  isActive: true,
  note: '',
})

// 费率百分比显示（用于前端输入）
const buyFeeRatePercent = computed({
  get: () => form.buyFeeRate != null ? form.buyFeeRate * 100 : 0,
  set: (val) => { form.buyFeeRate = val != null ? val / 100 : 0 }
})

const sellFeeRatePercent = computed({
  get: () => form.sellFeeRate != null ? form.sellFeeRate * 100 : 0,
  set: (val) => { form.sellFeeRate = val != null ? val / 100 : 0 }
})

// 卖出费率分段配置（前端扩展字段，包含百分比显示）
interface FeeTier extends Omit<FundSellFeeTier, 'sellFeeRate'> {
  sellFeeRatePercent: number
  maxDaysInput?: string | number  // 用于输入的临时字段
}

const sellFeeTiers = ref<FeeTier[]>([])

const rules: FormRules = {
  productCode: [{ required: true, message: '请输入产品代码', trigger: 'blur' }],
  productName: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
  assetType: [{ required: true, message: '请选择资产类型', trigger: 'change' }],
  channel: [{ required: true, message: '请选择渠道', trigger: 'change' }],
  market: [{ required: true, message: '请选择市场', trigger: 'change' }],
  currency: [{ required: true, message: '请选择币种', trigger: 'change' }],
  buyFeeRate: [{ required: true, message: '请输入买入费率', trigger: 'blur' }],
  // 普通基金不再需要默认卖出费率，只使用分段费率
  // sellFeeRate: [{ required: true, message: '请输入卖出费率', trigger: 'blur' }],
  buyConfirmOffset: [{ required: true, message: '请输入买入确认延迟', trigger: 'blur' }],
  sellConfirmOffset: [{ required: true, message: '请输入卖出确认延迟', trigger: 'blur' }],
  cutoffTime: [{ required: true, message: '请输入交易截单时间', trigger: 'blur' }],
}

// 加载所有产品（调用同一个接口）
async function loadAllProducts() {
  loading.value = true
  try {
    await productStore.fetchProducts()
    // 从store中获取所有产品
    allProducts.value = productStore.products || []
    // 分别加载场内和场外产品
    loadExchangeProducts()
    loadOtcProducts()
  } catch (error: any) {
    ElNotification.error({ title: '错误', message: error.message || '加载失败', position: 'bottom-right' })
    allProducts.value = []
    exchangeProducts.value = []
    otcProducts.value = []
  } finally {
    loading.value = false
  }
}

// 加载场内产品（基于左侧筛选条件）
function loadExchangeProducts() {
  let filtered = allProducts.value.filter((p) => p.channel === 'EXCHANGE')

  // 应用筛选条件
  if (exchangeFilters.keyword) {
    const keyword = exchangeFilters.keyword.toLowerCase()
    filtered = filtered.filter(
      (p) =>
        p.productName.toLowerCase().includes(keyword) ||
        p.productCode.toLowerCase().includes(keyword)
    )
  }

  if (exchangeFilters.assetType) {
    filtered = filtered.filter((p) => p.assetType === exchangeFilters.assetType)
  }

  if (exchangeFilters.isActive !== undefined) {
    filtered = filtered.filter((p) => p.isActive === exchangeFilters.isActive)
  }

  // 应用保存的排序
  exchangeProducts.value = loadSavedOrder(filtered, 'EXCHANGE')
}

// 加载场外产品（基于右侧筛选条件）
function loadOtcProducts() {
  let filtered = allProducts.value.filter((p) => p.channel === 'OTC')

  // 应用筛选条件
  if (otcFilters.keyword) {
    const keyword = otcFilters.keyword.toLowerCase()
    filtered = filtered.filter(
      (p) =>
        p.productName.toLowerCase().includes(keyword) ||
        p.productCode.toLowerCase().includes(keyword)
    )
  }

  if (otcFilters.assetType) {
    filtered = filtered.filter((p) => p.assetType === otcFilters.assetType)
  }

  if (otcFilters.isActive !== undefined) {
    filtered = filtered.filter((p) => p.isActive === otcFilters.isActive)
  }

  // 应用保存的排序
  otcProducts.value = loadSavedOrder(filtered, 'OTC')
}

function handleViewHolding(product: ProductMaster) {
  router.push({ name: 'Holdings', query: { productId: product.id } })
}

// 拖拽排序处理
function handleDragStart(event: DragEvent, index: number, type: 'exchange' | 'otc') {
  draggedIndex.value = index
  draggedType.value = type
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', '')
  }
  
  // 开始边缘自动滚动检测
  startAutoScroll(type)
}

function handleDragOver(event: DragEvent, index: number, type: 'exchange' | 'otc') {
  if (draggedIndex.value === null || draggedType.value !== type) return
  if (index !== draggedIndex.value) {
    dragOverIndex.value = index
    dragOverType.value = type
  }
  
  // 检测是否需要自动滚动
  checkAutoScroll(event, type)
}

function handleDrop(event: DragEvent, dropIndex: number, type: 'exchange' | 'otc') {
  event.preventDefault()
  if (draggedIndex.value === null || draggedType.value !== type) return
  
  const sourceIndex = draggedIndex.value
  if (sourceIndex === dropIndex) return

  // 重新排序
  if (type === 'exchange') {
    const newList = [...exchangeProducts.value]
    const [removed] = newList.splice(sourceIndex, 1)
    newList.splice(dropIndex, 0, removed)
    exchangeProducts.value = newList
    // 保存排序
    saveProductOrder(newList, 'EXCHANGE')
  } else {
    const newList = [...otcProducts.value]
    const [removed] = newList.splice(sourceIndex, 1)
    newList.splice(dropIndex, 0, removed)
    otcProducts.value = newList
    // 保存排序
    saveProductOrder(newList, 'OTC')
  }

  handleDragEnd()
}

function handleDragEnd() {
  draggedIndex.value = null
  dragOverIndex.value = null
  dragOverType.value = null
  draggedType.value = null
  
  // 停止自动滚动
  stopAutoScroll()
}

// 边缘自动滚动功能
function startAutoScroll(type: 'exchange' | 'otc') {
  // 拖拽开始时，允许滚轮滚动
  const container = type === 'exchange' ? exchangeTableContainer.value : otcTableContainer.value
  if (container) {
    // 允许滚轮事件 - 在拖拽过程中，滚轮可以正常滚动表格
    const handleWheel = (e: WheelEvent) => {
      e.stopPropagation()
      container!.scrollTop += e.deltaY
    }
    container.addEventListener('wheel', handleWheel, { passive: true })
    // 存储事件处理器以便清理
    ;(container as any)._dragWheelHandler = handleWheel
  }
}

function checkAutoScroll(event: DragEvent, type: 'exchange' | 'otc') {
  const container = type === 'exchange' ? exchangeTableContainer.value : otcTableContainer.value
  if (!container) return
  
  const rect = container.getBoundingClientRect()
  const mouseY = event.clientY
  const scrollThreshold = 50 // 距离边缘50px时开始滚动
  const scrollSpeed = 10 // 滚动速度
  
  // 停止之前的滚动
  stopAutoScroll()
  
  // 检查是否接近顶部边缘
  if (mouseY - rect.top < scrollThreshold && container.scrollTop > 0) {
    scrollInterval.value = window.setInterval(() => {
      if (container.scrollTop > 0) {
        container.scrollTop = Math.max(0, container.scrollTop - scrollSpeed)
      } else {
        stopAutoScroll()
      }
    }, 16) // 约60fps
  }
  // 检查是否接近底部边缘
  else if (rect.bottom - mouseY < scrollThreshold && 
           container.scrollTop < container.scrollHeight - container.clientHeight) {
    scrollInterval.value = window.setInterval(() => {
      const maxScroll = container.scrollHeight - container.clientHeight
      if (container.scrollTop < maxScroll) {
        container.scrollTop = Math.min(maxScroll, container.scrollTop + scrollSpeed)
      } else {
        stopAutoScroll()
      }
    }, 16) // 约60fps
  }
}

function stopAutoScroll() {
  if (scrollInterval.value !== null) {
    clearInterval(scrollInterval.value)
    scrollInterval.value = null
  }
  
  // 移除滚轮事件监听器
  if (exchangeTableContainer.value && (exchangeTableContainer.value as any)._dragWheelHandler) {
    exchangeTableContainer.value.removeEventListener('wheel', (exchangeTableContainer.value as any)._dragWheelHandler)
    delete (exchangeTableContainer.value as any)._dragWheelHandler
  }
  if (otcTableContainer.value && (otcTableContainer.value as any)._dragWheelHandler) {
    otcTableContainer.value.removeEventListener('wheel', (otcTableContainer.value as any)._dragWheelHandler)
    delete (otcTableContainer.value as any)._dragWheelHandler
  }
}

// 保存产品排序（调用后端接口）
async function saveProductOrder(orderedProducts: ProductMaster[], _channel: 'EXCHANGE' | 'OTC') {
  try {
    // 构建更新请求
    const updates = orderedProducts.map((product, index) => ({
      id: product.id,
      sortOrder: index + 1, // 从1开始
    }))
    
    // 调用后端接口保存排序
    await (productApi as any).updateProductSortOrder(updates)
    
    // 更新本地数据中的sortOrder
    orderedProducts.forEach((product, index) => {
      ;(product as any).sortOrder = index + 1
      const allProduct = allProducts.value.find((p) => p.id === product.id)
      if (allProduct) {
        ;(allProduct as any).sortOrder = index + 1
      }
    })
    
    ElNotification.success({ title: '成功', message: '产品排序已保存', position: 'bottom-right' })
  } catch (error: any) {
    console.error('Failed to save product order:', error)
    ElNotification.error({ title: '错误', message: error.message || '保存排序失败', position: 'bottom-right' })
  }
}

// 加载保存的排序顺序（从后端返回的sortOrder字段排序）
function loadSavedOrder(products: ProductMaster[], _channel: 'EXCHANGE' | 'OTC'): ProductMaster[] {
  // 按照sortOrder排序，如果sortOrder为null或undefined，则按id排序
  return [...products].sort((a, b) => {
    const aSort = (a as any).sortOrder
    const bSort = (b as any).sortOrder
    if (aSort != null && bSort != null) {
      return aSort - bSort
    }
    if (aSort != null) {
      return -1
    }
    if (bSort != null) {
      return 1
    }
    return a.id - b.id
  })
}

function handleAddProduct() {
  editingProduct.value = null
  resetForm()
  dialogVisible.value = true
}

async function handleEdit(product: ProductMaster) {
  editingProduct.value = product
  Object.assign(form, product)
  
  // 数据来源转成小写（兼容旧数据）
  if (form.dataSource) {
    form.dataSource = form.dataSource.toLowerCase()
  }
  
  // 如果是场外产品，加载费率分段配置
  if (product.channel === 'OTC' && product.assetType !== 'BANK_WM_NAV' && product.assetType !== 'MMF') {
    await loadFeeTiers(product.id)
  } else {
    sellFeeTiers.value = []
  }
  
  dialogVisible.value = true
}


function resetForm() {
  Object.assign(form, {
    productCode: '',
    productName: '',
    assetType: 'FUND',
    channel: 'OTC',
    market: 'NA',
    currency: 'CNY',
    isQdii: false,
    trackIndex: '',
    buyFeeRate: 0,
    sellFeeRate: 0,
    buyConfirmOffset: 1,
    sellConfirmOffset: 1,
    cutoffTime: '15:00',
    dataSource: '',
    isActive: true,
    note: '',
  })
  sellFeeTiers.value = []
}

function handleAddFeeTier() {
  // 使用索引作为排序值，保持后端兼容性
  sellFeeTiers.value.push({
    minDays: 0,
    maxDays: null,
    maxDaysInput: '',
    sellFeeRatePercent: 0,
    sellFeeRate: 0,
    sortOrder: sellFeeTiers.value.length,
    isActive: true,
    note: '',
  })
}

// 处理最大持有天数输入框失焦事件
function handleMaxDaysBlur(row: FeeTier) {
  const value = row.maxDaysInput
  if (value === '' || value === null || value === undefined) {
    row.maxDays = null
  } else {
    const numValue = Number(value)
    row.maxDays = isNaN(numValue) ? null : numValue
  }
}

function handleRemoveFeeTier(index: number) {
  sellFeeTiers.value.splice(index, 1)
}

// 加载费率分段配置
async function loadFeeTiers(productId: number) {
  try {
    const tiers = await productApi.getSellFeeTiers(productId)
    sellFeeTiers.value = tiers.map(t => ({
      ...t,
      sellFeeRatePercent: (t.sellFeeRate || 0) * 100,
      maxDaysInput: t.maxDays === null || t.maxDays === undefined ? '' : t.maxDays
    }))
  } catch (error: any) {
    console.error('加载费率分段失败:', error)
    sellFeeTiers.value = []
  }
}

function handleDialogClose() {
  editingProduct.value = null
  resetForm()
  sellFeeTiers.value = []
  formRef.value?.resetFields()
}

async function handleSave() {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    // 如果是场外产品，验证分段费率配置
    if (form.channel === 'OTC' && form.assetType !== 'BANK_WM_NAV' && form.assetType !== 'MMF') {
      if (!sellFeeTiers.value || sellFeeTiers.value.length === 0) {
        ElNotification.warning({ title: '警告', message: '请至少配置一个卖出费率分段', position: 'bottom-right' })
        return
      }
      // 验证分段配置是否完整
      for (const tier of sellFeeTiers.value) {
        if (tier.minDays == null || tier.sellFeeRatePercent == null) {
          ElNotification.warning({ title: '警告', message: '请完善卖出费率分段配置（最小持有天数和费率必填）', position: 'bottom-right' })
          return
        }
      }
    }

    saving.value = true
    try {
      let productId: number
      
      if (editingProduct.value) {
        const updated = await productStore.updateProduct(editingProduct.value.id, form)
        productId = updated.id
        ElNotification.success({ title: '成功', message: '更新成功', position: 'bottom-right' })
      } else {
        const created = await productStore.createProduct(form)
        productId = created.id
        ElNotification.success({ title: '成功', message: '创建成功', position: 'bottom-right' })
      }
      
      // 如果是场外产品，保存费率分段配置
      if (form.channel === 'OTC' && form.assetType !== 'BANK_WM_NAV' && form.assetType !== 'MMF') {
        await saveFeeTiers(productId)
      }
      
      dialogVisible.value = false
      await loadAllProducts()
      
      // 通知其他页面（例如“记一笔”中的产品列表）刷新数据
      window.dispatchEvent(new CustomEvent('data-refresh'))
    } catch (error: any) {
      ElNotification.error({ title: '错误', message: error.message || '保存失败', position: 'bottom-right' })
    } finally {
      saving.value = false
    }
  })
}

// 保存费率分段配置
async function saveFeeTiers(productId: number) {
  try {
    // 转换百分比为小数，移除前端扩展字段，处理最大持有天数
    const tiersToSave: FundSellFeeTier[] = sellFeeTiers.value.map((tier, index) => {
      const { sellFeeRatePercent, maxDaysInput, ...rest } = tier
      // 处理最大持有天数：如果 maxDaysInput 为空，则 maxDays 为 null
      let maxDays = tier.maxDays
      if (maxDaysInput === '' || maxDaysInput === null || maxDaysInput === undefined) {
        maxDays = null
      } else if (typeof maxDaysInput === 'string') {
        maxDays = maxDaysInput.trim() === '' ? null : Number(maxDaysInput) || null
      }
      
      return {
        ...rest,
        maxDays: maxDays,
        sellFeeRate: sellFeeRatePercent / 100,
        sortOrder: index, // 使用索引作为排序值
        productId: productId,
      }
    })
    
    await productApi.saveSellFeeTiers(productId, tiersToSave)
  } catch (error: any) {
    console.error('保存费率分段失败:', error)
    ElNotification.warning({ title: '警告', message: '产品已保存，但费率分段保存失败', position: 'bottom-right' })
  }
}

onMounted(async () => {
  await loadAllProducts()
})

onBeforeUnmount(() => {
  // 清理时停止自动滚动
  stopAutoScroll()
})
</script>
