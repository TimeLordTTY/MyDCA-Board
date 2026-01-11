<template>
  <div>
    <div class="card">
      <div class="row-between">
        <div>
          <h3>
            产品管理
            <span class="tag blue tiny">可新增/编辑/停用</span>
          </h3>
          <div class="sub">
            产品是全局字典：ETF / 基金 / 货基 / 逆回购。后续接行情服务时，code 用于查询。
          </div>
        </div>
        <div class="row-gap">
          <el-button type="primary" @click="handleAddProduct">＋ 新增产品</el-button>
        </div>
      </div>
      <div class="divider"></div>

      <!-- 筛选 -->
      <div class="row-gap" style="margin-bottom: 16px">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索产品名称或代码"
          style="width: 200px"
          clearable
          @clear="loadProducts"
          @keyup.enter="loadProducts"
        />
        <el-select v-model="filters.assetType" placeholder="资产类型" style="width: 150px" clearable @change="loadProducts">
          <el-option
            v-for="(label, value) in assetTypeMap"
            :key="value"
            :label="label"
            :value="value"
          />
        </el-select>
        <el-select v-model="filters.channel" placeholder="渠道" style="width: 120px" clearable @change="loadProducts">
          <el-option
            v-for="(label, value) in channelMap"
            :key="value"
            :label="label"
            :value="value"
          />
        </el-select>
        <el-button @click="loadProducts">搜索</el-button>
      </div>

      <!-- 产品列表 -->
      <div style="overflow: auto">
        <table>
          <thead>
            <tr>
              <th>名称</th>
              <th>代码</th>
              <th>类型</th>
              <th>渠道</th>
              <th>市场</th>
              <th>币种</th>
              <th>状态</th>
              <th class="right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="productStore.loading">
              <td colspan="8" class="td-muted" style="text-align: center">加载中...</td>
            </tr>
            <tr v-else-if="productStore.products.length === 0">
              <td colspan="8" class="td-muted" style="text-align: center">暂无产品</td>
            </tr>
            <tr v-for="product in productStore.products" :key="product.id">
              <td><b>{{ product.productName }}</b></td>
              <td class="mono">{{ product.productCode }}</td>
              <td>
                <span class="tag blue">{{ getAssetTypeLabel(product.assetType) }}</span>
              </td>
              <td>{{ getChannelLabel(product.channel) }}</td>
              <td>{{ getMarketLabel(product.market) }}</td>
              <td>{{ getCurrencyLabel(product.currency) }}</td>
              <td>
                <span class="tag" :class="product.isActive ? 'green' : 'red'">
                  {{ product.isActive ? '启用' : '停用' }}
                </span>
              </td>
              <td class="right">
                <button class="btn" @click="handleEdit(product)">编辑</button>
                <button class="btn" @click="handleToggle(product)">
                  {{ product.isActive ? '停用' : '启用' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  useProductStore,
  assetTypeMap,
  channelMap,
  marketMap,
  currencyMap,
  getAssetTypeLabel,
  getChannelLabel,
  getMarketLabel,
  getCurrencyLabel,
} from '@wealth-hub/shared'
import type { ProductMaster } from '@wealth-hub/shared'

const productStore = useProductStore()

const filters = reactive({
  keyword: '',
  assetType: '',
  channel: '',
})

const dialogVisible = ref(false)
const editingProduct = ref<ProductMaster | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

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

async function loadProducts() {
  await productStore.fetchProducts({
    keyword: filters.keyword || undefined,
    assetType: filters.assetType || undefined,
    channel: filters.channel || undefined,
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

function handleToggle(product: ProductMaster) {
  ElMessageBox.confirm(
    `确定要${product.isActive ? '停用' : '启用'}产品"${product.productName}"吗？`,
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(async () => {
    try {
      await productStore.updateProduct(product.id, { isActive: !product.isActive })
      ElMessage.success('操作成功')
      await loadProducts()
    } catch (error: any) {
      ElMessage.error(error.message || '操作失败')
    }
  })
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
      await loadProducts()
    } catch (error: any) {
      ElMessage.error(error.message || '保存失败')
    } finally {
      saving.value = false
    }
  })
}

onMounted(() => {
  loadProducts()
})
</script>
