<template>
  <el-dialog
    v-model="visible"
    title="新建订单"
    width="800px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form :model="form" label-width="120px">
      <el-form-item label="产品" required>
        <el-select v-model="form.productId" placeholder="选择产品" style="width: 100%">
          <el-option
            v-for="prod in products"
            :key="prod.id"
            :label="`${prod.productName} (${prod.productCode})`"
            :value="prod.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="订单类型" required>
        <el-select v-model="form.orderType" placeholder="选择订单类型" style="width: 100%">
          <el-option
            v-for="(label, value) in orderTypeMap"
            :key="value"
            :label="label"
            :value="value"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        :label="form.orderType === 'BUY' || form.orderType === 'SUBSCRIPTION' ? '总金额（元）' : '总份额'"
        required
      >
        <el-input-number
          v-if="form.orderType === 'BUY' || form.orderType === 'SUBSCRIPTION'"
          v-model="form.amount"
          :min="0.01"
          :precision="2"
          style="width: 100%"
        />
        <el-input-number
          v-else
          v-model="form.shares"
          :min="0.01"
          :precision="4"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="资金来源账户" required>
        <el-select 
          v-model="form.accountId" 
          placeholder="选择账户" 
          style="width: 100%"
          @change="handleAccountChange"
        >
          <el-option
            v-for="acc in cashLeafAccounts"
            :key="acc.id"
            :label="getAccountDisplayName(acc)"
            :value="acc.id"
          />
        </el-select>
        <div v-if="selectedAccount?.linkedProductId" class="form-help-text" style="color: #f59e0b; margin-top: 4px;">
          此账户已关联产品，可以按子账户分别设置买入金额/卖出份额
        </div>
      </el-form-item>

      <!-- 多子账户配置（当选择的账户关联了产品且有子账户时显示） -->
      <el-form-item 
        v-if="selectedAccount?.linkedProductId && childAccounts.length > 0"
        :label="form.orderType === 'BUY' || form.orderType === 'SUBSCRIPTION' ? '子账户买入金额分配' : '子账户卖出份额分配'"
      >
        <div style="border: 1px solid #e5e7eb; border-radius: 4px; padding: 12px; background: #f9fafb;">
          <div style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center;">
            <span style="font-size: 13px; color: #6b7280;">
              {{ form.orderType === 'BUY' || form.orderType === 'SUBSCRIPTION' 
                ? `总金额：${form.amount || 0} 元，已分配：${totalAllocatedAmount.toFixed(2)} 元` 
                : `总份额：${form.shares || 0}，已分配：${totalAllocatedShares.toFixed(4)}` }}
            </span>
            <el-button 
              size="small" 
              type="primary" 
              text 
              @click="handleAddFundingLine"
            >
              + 添加子账户
            </el-button>
          </div>
          <div v-if="fundingLines.length === 0" style="text-align: center; color: #9ca3af; padding: 20px;">
            点击"添加子账户"开始分配
          </div>
          <div v-else>
            <div 
              v-for="(line, index) in fundingLines" 
              :key="index"
              style="display: flex; gap: 8px; margin-bottom: 8px; align-items: center;"
            >
              <el-select
                v-model="line.accountId"
                placeholder="选择子账户"
                style="flex: 1"
                @change="handleFundingLineChange"
              >
                <el-option
                  v-for="acc in availableChildAccounts"
                  :key="acc.id"
                  :label="acc.accountName"
                  :value="acc.id"
                  :disabled="fundingLines.some((fl, i) => i !== index && fl.accountId === acc.id)"
                />
              </el-select>
              <el-input-number
                v-if="form.orderType === 'BUY' || form.orderType === 'SUBSCRIPTION'"
                v-model="line.amount"
                :min="0.01"
                :precision="2"
                placeholder="金额"
                style="width: 150px"
                @change="handleFundingLineChange"
              />
              <el-input-number
                v-else
                v-model="line.shares"
                :min="0.01"
                :precision="4"
                placeholder="份额"
                style="width: 150px"
                @change="handleFundingLineChange"
              />
              <el-button 
                size="small" 
                type="danger" 
                text 
                @click="handleRemoveFundingLine(index)"
              >
                删除
              </el-button>
            </div>
            <div v-if="allocationError" style="color: #ef4444; font-size: 12px; margin-top: 8px;">
              {{ allocationError }}
            </div>
          </div>
        </div>
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model="form.note" placeholder="比如：周定投" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="submitting">提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAccountStore, useProductStore } from '@wealth-hub/shared'
import { orderApi, orderTypeMap } from '@wealth-hub/shared'
import type { Account } from '@wealth-hub/shared'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const accountStore = useAccountStore()
const productStore = useProductStore()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const submitting = ref(false)

const form = ref({
  productId: undefined as number | undefined,
  orderType: 'BUY' as 'BUY' | 'SELL' | 'SUBSCRIPTION' | 'REDEMPTION',
  amount: undefined as number | undefined,
  shares: undefined as number | undefined,
  accountId: undefined as number | undefined,
  note: '',
})

// 资金来源行列表（用于多子账户配置）
const fundingLines = ref<Array<{
  accountId: number | undefined
  amount?: number
  shares?: number
}>>([])

const cashLeafAccounts = computed(() => accountStore.cashLeafAccounts)

const products = computed(() => productStore.products.filter((p) => p.isActive))

// 获取选中的账户（从扁平列表中查找，因为accountTree是树形结构）
const selectedAccount = computed(() => {
  if (!form.value.accountId) return null
  // 从扁平列表中查找
  return accountStore.accounts.find((a) => a.id === form.value.accountId) || null
})

// 获取选中账户的子账户列表（从扁平列表中查找）
const childAccounts = computed(() => {
  if (!form.value.accountId) return []
  return accountStore.accounts.filter((a) => a.parentAccountId === form.value.accountId)
})

// 获取可用的子账户（排除已选择的）
const availableChildAccounts = computed(() => {
  return childAccounts.value.filter((acc) => {
    // 如果该账户已经在fundingLines中被选择（除了当前编辑的行），则不可选
    return true // 在el-option中通过disabled属性控制
  })
})

// 计算已分配的总金额/总份额
const totalAllocatedAmount = computed(() => {
  return fundingLines.value
    .filter((fl) => fl.amount != null)
    .reduce((sum, fl) => sum + (fl.amount || 0), 0)
})

const totalAllocatedShares = computed(() => {
  return fundingLines.value
    .filter((fl) => fl.shares != null)
    .reduce((sum, fl) => sum + (fl.shares || 0), 0)
})

// 分配错误提示
const allocationError = computed(() => {
  if (form.value.orderType === 'BUY' || form.value.orderType === 'SUBSCRIPTION') {
    if (form.value.amount == null) return null
    const diff = Math.abs(totalAllocatedAmount.value - form.value.amount)
    if (diff > 0.01) {
      return `已分配金额 ${totalAllocatedAmount.value.toFixed(2)} 元，与总金额 ${form.value.amount.toFixed(2)} 元不一致（差额：${diff.toFixed(2)} 元）`
    }
  } else {
    if (form.value.shares == null) return null
    const diff = Math.abs(totalAllocatedShares.value - form.value.shares)
    if (diff > 0.0001) {
      return `已分配份额 ${totalAllocatedShares.value.toFixed(4)}，与总份额 ${form.value.shares.toFixed(4)} 不一致（差额：${diff.toFixed(4)}）`
    }
  }
  return null
})

watch(visible, (val) => {
  if (val) {
    form.value = {
      productId: undefined,
      orderType: 'BUY',
      amount: undefined,
      shares: undefined,
      accountId: undefined,
      note: '',
    }
    fundingLines.value = []
  }
})

// 监听订单类型变化，清空fundingLines
watch(() => form.value.orderType, () => {
  fundingLines.value = []
})

function getAccountDisplayName(acc: Account): string {
  const parent = accountStore.accountTree.find((a) => a.id === acc.parentAccountId)
  return parent ? `${parent.accountName} / ${acc.accountName}` : acc.accountName
}

function handleAccountChange() {
  // 账户变化时，清空fundingLines
  fundingLines.value = []
}

function handleAddFundingLine() {
  fundingLines.value.push({
    accountId: undefined,
    amount: undefined,
    shares: undefined,
  })
}

function handleRemoveFundingLine(index: number) {
  fundingLines.value.splice(index, 1)
}

function handleFundingLineChange() {
  // 触发重新计算
}

function handleClose() {
  visible.value = false
}

async function handleSubmit() {
  if (!form.value.productId) {
    ElMessage.error('请选择产品')
    return
  }

  const isBuyOrder = form.value.orderType === 'BUY' || form.value.orderType === 'SUBSCRIPTION'
  const isSellOrder = form.value.orderType === 'SELL' || form.value.orderType === 'REDEMPTION'

  if (isBuyOrder && !form.value.amount) {
    ElMessage.error('请填写总金额')
    return
  }

  if (isSellOrder && !form.value.shares) {
    ElMessage.error('请填写总份额')
    return
  }

  // 如果选择了关联产品的账户且有子账户，必须配置fundingLines
  if (selectedAccount.value?.linkedProductId && childAccounts.value.length > 0) {
    if (fundingLines.value.length === 0) {
      ElMessage.error('请至少添加一个子账户并分配金额/份额')
      return
    }

    // 校验所有行都填写完整
    for (let i = 0; i < fundingLines.value.length; i++) {
      const line = fundingLines.value[i]
      if (!line.accountId) {
        ElMessage.error(`第 ${i + 1} 行请选择子账户`)
        return
      }
      if (isBuyOrder && (line.amount == null || line.amount <= 0)) {
        ElMessage.error(`第 ${i + 1} 行请填写买入金额`)
        return
      }
      if (isSellOrder && (line.shares == null || line.shares <= 0)) {
        ElMessage.error(`第 ${i + 1} 行请填写卖出份额`)
        return
      }
    }

    // 校验总金额/总份额是否匹配
    if (allocationError.value) {
      ElMessage.error(allocationError.value)
      return
    }
  }

  try {
    submitting.value = true

    // 构建fundingLines
    let finalFundingLines: Array<{ accountId: number; amount?: number; shares?: number }> = []

    if (selectedAccount.value?.linkedProductId && childAccounts.value.length > 0 && fundingLines.value.length > 0) {
      // 使用多子账户配置
      finalFundingLines = fundingLines.value
        .filter((fl) => fl.accountId != null)
        .map((fl) => ({
          accountId: fl.accountId!,
          amount: fl.amount,
          shares: fl.shares,
        }))
    } else {
      // 使用单个账户（兼容旧逻辑）
      if (!form.value.accountId) {
        ElMessage.error('请选择资金来源账户')
        return
      }
      finalFundingLines = [
        {
          accountId: form.value.accountId,
          amount: isBuyOrder ? form.value.amount : undefined,
          shares: isSellOrder ? form.value.shares : undefined,
        },
      ]
    }

    await orderApi.createOrder({
      productId: form.value.productId,
      orderType: form.value.orderType,
      amount: form.value.amount,
      shares: form.value.shares,
      fundingLines: finalFundingLines,
    })
    ElMessage.success('订单创建成功')
    emit('success')
    handleClose()
  } catch (error: any) {
    ElMessage.error(error.message || '创建失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.form-help-text {
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
}
</style>
