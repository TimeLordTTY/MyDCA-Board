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

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="买入费率" prop="buyFeeRate">
              <el-input-number
                v-model="form.buyFeeRate"
                :precision="6"
                :step="0.0001"
                :min="0"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="卖出费率" prop="sellFeeRate">
              <el-input-number
                v-model="form.sellFeeRate"
                :precision="6"
                :step="0.0001"
                :min="0"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

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
          <el-input v-model="form.dataSource" placeholder="如：AKSHARE、FUND" />
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
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
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

const rules: FormRules = {
  productCode: [{ required: true, message: '请输入产品代码', trigger: 'blur' }],
  productName: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
  assetType: [{ required: true, message: '请选择资产类型', trigger: 'change' }],
  channel: [{ required: true, message: '请选择渠道', trigger: 'change' }],
  market: [{ required: true, message: '请选择市场', trigger: 'change' }],
  currency: [{ required: true, message: '请选择币种', trigger: 'change' }],
  buyFeeRate: [{ required: true, message: '请输入买入费率', trigger: 'blur' }],
  sellFeeRate: [{ required: true, message: '请输入卖出费率', trigger: 'blur' }],
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
    ElMessage.error(error.message || '加载失败')
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
    
    ElMessage.success('产品排序已保存')
  } catch (error: any) {
    console.error('Failed to save product order:', error)
    ElMessage.error(error.message || '保存排序失败')
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

function handleEdit(product: ProductMaster) {
  editingProduct.value = product
  Object.assign(form, product)
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
}

function handleDialogClose() {
  editingProduct.value = null
  resetForm()
  formRef.value?.resetFields()
}

async function handleSave() {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    saving.value = true
    try {
      if (editingProduct.value) {
        await productStore.updateProduct(editingProduct.value.id, form)
        ElMessage.success('更新成功')
      } else {
        await productStore.createProduct(form)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      await loadAllProducts()
    } catch (error: any) {
      ElMessage.error(error.message || '保存失败')
    } finally {
      saving.value = false
    }
  })
}

onMounted(async () => {
  await loadAllProducts()
})

onBeforeUnmount(() => {
  // 清理时停止自动滚动
  stopAutoScroll()
})
</script>
