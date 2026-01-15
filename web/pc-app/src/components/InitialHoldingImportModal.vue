<template>
  <el-dialog
    v-model="visible"
    title="初始持仓导入"
    width="900px"
    @close="handleClose"
  >
    <div style="margin-bottom: 16px; padding: 12px; background: #f5f7fa; border-radius: 4px;">
      <div style="font-weight: 600; margin-bottom: 4px;">使用说明</div>
      <div style="font-size: 12px; color: #666; line-height: 1.6;">
        <p>1. 此功能用于一次性导入当前的实际持仓，对齐系统数据与实际数据</p>
        <p>2. 导入后，系统会为每个产品创建持仓账户和对应的流水记录</p>
        <p>3. 之后的新交易通过"订单&结算"正常录入，系统会自动计算持仓</p>
        <p>4. 如果产品不存在，系统会自动创建产品（需要填写完整信息）</p>
      </div>
    </div>

    <div style="margin-bottom: 12px;">
      <el-button type="primary" size="small" @click="handleAddRow">+ 添加持仓</el-button>
      <el-button type="default" size="small" @click="handleImportFromCSV" style="margin-left: 8px;">从CSV导入</el-button>
      <el-button type="danger" size="small" @click="handleClearAll" style="margin-left: 8px;">清空</el-button>
    </div>

    <div style="max-height: 400px; overflow-y: auto; border: 1px solid #e4e7ed; border-radius: 4px;">
      <el-table :data="holdings" border size="small" style="width: 100%">
        <el-table-column label="序号" width="60" type="index" />
        <el-table-column label="渠道" width="100">
          <template #default="{ row, $index }">
            <el-select 
              v-model="row.channel" 
              size="small" 
              style="width: 100%"
              @change="handleChannelChange($index)"
            >
              <el-option label="场内" value="EXCHANGE" />
              <el-option label="场外" value="OTC" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="产品名称" width="200">
          <template #default="{ row, $index }">
            <el-select
              v-model="row.productId"
              size="small"
              style="width: 100%"
              filterable
              remote
              :remote-method="(query) => handleSearchProduct(query, $index)"
              :loading="productLoading"
              placeholder="请选择产品"
              @change="handleProductChange($index)"
            >
              <el-option
                v-for="product in filteredProducts[$index] || []"
                :key="product.id"
                :label="`${product.productName} (${product.productCode})`"
                :value="product.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="产品代码" width="120">
          <template #default="{ row }">
            <el-input
              v-model="row.productCode"
              size="small"
              placeholder="自动回显"
              disabled
            />
          </template>
        </el-table-column>
        <el-table-column label="持仓份额" width="120">
          <template #default="{ row }">
            <el-input-number
              v-model="row.shares"
              size="small"
              :precision="6"
              :min="0"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="成本价" width="120">
          <template #default="{ row }">
            <el-input-number
              v-model="row.costPrice"
              size="small"
              :precision="6"
              :min="0"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="持仓成本" width="120">
          <template #default="{ row }">
            <span class="mono">{{ formatCurrency((row.shares || 0) * (row.costPrice || 0)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="150">
          <template #default="{ row }">
            <el-input
              v-model="row.note"
              size="small"
              placeholder="可选"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ $index }">
            <el-button
              type="danger"
              size="small"
              link
              @click="handleRemoveRow($index)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div style="margin-top: 16px; padding: 12px; background: #fff7e6; border: 1px solid #ffe58f; border-radius: 4px;">
      <div style="font-weight: 600; margin-bottom: 4px; color: #d46b08;">⚠️ 注意事项</div>
      <div style="font-size: 12px; color: #666; line-height: 1.6;">
        <p>• 导入前请确保产品代码正确，系统会根据代码查找或创建产品</p>
        <p>• 如果产品已存在持仓，导入会累加到现有持仓上</p>
        <p>• 成本价用于计算平均成本，请填写准确的买入均价</p>
        <p>• 导入后无法撤销，请仔细核对数据</p>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">确认导入</el-button>
    </template>

    <!-- CSV导入对话框 -->
    <el-dialog
      v-model="csvDialogVisible"
      title="从CSV导入"
      width="600px"
      append-to-body
    >
      <div style="margin-bottom: 12px;">
        <div style="font-size: 12px; color: #666; margin-bottom: 8px;">
          CSV格式：产品代码,产品名称,渠道(EXCHANGE/OTC),持仓份额,成本价,备注
        </div>
        <el-input
          v-model="csvContent"
          type="textarea"
          :rows="10"
          placeholder="请粘贴CSV内容，每行一条持仓记录&#10;示例：&#10;159919,沪深300ETF,EXCHANGE,1000,1.25,场内ETF&#10;000001,华夏成长,OTC,5000,1.15,场外基金"
        />
      </div>
      <template #footer>
        <el-button @click="csvDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleParseCSV">解析并导入</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatCurrency } from '@wealth-hub/shared'
import { holdingApi, productApi } from '@wealth-hub/shared'
import type { ProductMaster } from '@wealth-hub/shared'

interface InitialHolding {
  productCode: string
  productName: string
  channel: 'EXCHANGE' | 'OTC'
  shares: number
  costPrice: number
  note?: string
  productId?: number // 如果产品已存在，这里会填充产品ID
}

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'success': []
}>()

const visible = ref(false)
const saving = ref(false)
const holdings = ref<InitialHolding[]>([])
const csvDialogVisible = ref(false)
const csvContent = ref('')
const productLoading = ref(false)
const filteredProducts = ref<Record<number, ProductMaster[]>>({})
const allProducts = ref<ProductMaster[]>([])

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val) {
    // 打开对话框时，如果没有数据，添加一行
    if (holdings.value.length === 0) {
      handleAddRow()
    }
    // 加载所有产品列表
    loadAllProducts()
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

function handleAddRow() {
  const index = holdings.value.length
  holdings.value.push({
    productCode: '',
    productName: '',
    channel: 'EXCHANGE',
    shares: 0,
    costPrice: 0,
    note: '',
    productId: undefined,
  })
  // 初始化该行的产品列表（根据渠道过滤）
  updateFilteredProducts(index)
}

function updateFilteredProducts(index: number) {
  const holding = holdings.value[index]
  if (!holding.channel) {
    filteredProducts.value[index] = []
    return
  }
  // 根据渠道过滤产品
  filteredProducts.value[index] = allProducts.value.filter(
    p => p.channel === holding.channel
  )
}

function handleRemoveRow(index: number) {
  holdings.value.splice(index, 1)
}

function handleClearAll() {
  ElMessageBox.confirm('确定要清空所有持仓数据吗？', '确认清空', {
    type: 'warning',
  }).then(() => {
    holdings.value = []
    handleAddRow()
  }).catch(() => {})
}

async function loadAllProducts() {
  try {
    productLoading.value = true
    const products = await productApi.getProducts()
    allProducts.value = products
  } catch (error: any) {
    console.error('加载产品列表失败:', error)
    ElMessage.error('加载产品列表失败')
  } finally {
    productLoading.value = false
  }
}

function handleChannelChange(index: number) {
  const holding = holdings.value[index]
  // 渠道改变时，清空产品选择
  holding.productId = undefined
  holding.productName = ''
  holding.productCode = ''
  filteredProducts.value[index] = []
}

async function handleSearchProduct(query: string, index: number) {
  const holding = holdings.value[index]
  if (!holding.channel) {
    ElMessage.warning('请先选择渠道')
    return
  }

  productLoading.value = true
  try {
    // 根据渠道和关键词搜索产品
    const products = await productApi.getProducts({
      channel: holding.channel,
      keyword: query,
    })
    filteredProducts.value[index] = products
  } catch (error: any) {
    console.error('搜索产品失败:', error)
    ElMessage.error('搜索产品失败')
  } finally {
    productLoading.value = false
  }
}

function handleProductChange(index: number) {
  const holding = holdings.value[index]
  if (!holding.productId) return

  // 查找选中的产品
  const product = allProducts.value.find(p => p.id === holding.productId)
  if (product) {
    holding.productName = product.productName
    holding.productCode = product.productCode
    holding.channel = product.channel as 'EXCHANGE' | 'OTC'
  } else {
    // 如果不在全量列表中，从过滤结果中查找
    const filtered = filteredProducts.value[index]?.find(p => p.id === holding.productId)
    if (filtered) {
      holding.productName = filtered.productName
      holding.productCode = filtered.productCode
      holding.channel = filtered.channel as 'EXCHANGE' | 'OTC'
    }
  }
}

function handleImportFromCSV() {
  csvDialogVisible.value = true
  csvContent.value = ''
}

function handleParseCSV() {
  if (!csvContent.value.trim()) {
    ElMessage.warning('请输入CSV内容')
    return
  }

  const lines = csvContent.value.trim().split('\n')
  const newHoldings: InitialHolding[] = []

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim()
    if (!line) continue

    const parts = line.split(',').map(p => p.trim())
    if (parts.length < 4) {
      ElMessage.warning(`第${i + 1}行格式不正确，已跳过`)
      continue
    }

    const [productCode, productName, channel, sharesStr, costPriceStr, note] = parts

    if (!productCode || !productName || !channel || !sharesStr || !costPriceStr) {
      ElMessage.warning(`第${i + 1}行数据不完整，已跳过`)
      continue
    }

    const shares = parseFloat(sharesStr)
    const costPrice = parseFloat(costPriceStr)

    if (isNaN(shares) || isNaN(costPrice) || shares <= 0 || costPrice <= 0) {
      ElMessage.warning(`第${i + 1}行份额或成本价无效，已跳过`)
      continue
    }

    if (channel !== 'EXCHANGE' && channel !== 'OTC') {
      ElMessage.warning(`第${i + 1}行渠道必须是EXCHANGE或OTC，已跳过`)
      continue
    }

    newHoldings.push({
      productCode,
      productName,
      channel: channel as 'EXCHANGE' | 'OTC',
      shares,
      costPrice,
      note: note || '',
    })
  }

  if (newHoldings.length === 0) {
    ElMessage.warning('没有解析到有效数据')
    return
  }

  // 合并到现有持仓列表
  holdings.value = [...holdings.value, ...newHoldings]
  csvDialogVisible.value = false
  csvContent.value = ''
  ElMessage.success(`成功导入${newHoldings.length}条持仓记录`)
}

async function handleSave() {
  // 验证数据
  const validHoldings = holdings.value.filter(h => 
    h.productId &&
    h.productCode && 
    h.productName && 
    h.shares > 0 && 
    h.costPrice > 0
  )

  if (validHoldings.length === 0) {
    ElMessage.warning('请至少填写一条有效的持仓记录')
    return
  }

  // 检查是否有无效数据
  const invalidCount = holdings.value.length - validHoldings.length
  if (invalidCount > 0) {
    await ElMessageBox.confirm(
      `有${invalidCount}条无效记录将被跳过，是否继续导入${validHoldings.length}条有效记录？`,
      '确认导入',
      {
        type: 'warning',
      }
    )
  }

  saving.value = true
  try {
    await holdingApi.importInitialHoldings(validHoldings)
    ElMessage.success(`成功导入${validHoldings.length}条持仓记录`)
    emit('success')
    visible.value = false
    holdings.value = []
  } catch (error: any) {
    ElMessage.error(error.message || '导入失败')
  } finally {
    saving.value = false
  }
}

function handleClose() {
  holdings.value = []
  csvContent.value = ''
  csvDialogVisible.value = false
}
</script>

<style scoped>
.mono {
  font-family: 'Courier New', monospace;
}
</style>
