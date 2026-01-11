<template>
  <el-dialog
    v-model="visible"
    title="统一记账中心"
    width="800px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <!-- 第一步：选择业务类型 -->
    <div v-if="step === 1">
      <div class="sub" style="margin-bottom: 16px">第一步：选择业务类型</div>
      <div class="divider"></div>
      <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-top: 16px">
        <button
          v-for="(label, type) in txnTypeOptions"
          :key="type"
          class="btn"
          style="flex-direction: column; padding: 16px; text-align: center"
          @click="selectType(type)"
        >
          <div style="font-size: 20px; margin-bottom: 4px">{{ label.icon }}</div>
          <div style="font-weight: 600">{{ label.name }}</div>
          <div class="tiny muted" style="margin-top: 4px">{{ type }}</div>
        </button>
      </div>
    </div>

    <!-- 第二步：填写详情 -->
    <div v-else>
      <div class="sub" style="margin-bottom: 16px">第二步：填写{{ txnTypeOptions[selectedType]?.name }}详情</div>
      <div class="divider"></div>
      <el-form :model="form" label-width="120px">
        <!-- 支出/收入 -->
        <template v-if="selectedType === 'EXPENSE' || selectedType === 'INCOME'">
          <el-form-item label="账户" required>
            <el-select v-model="form.accountId" placeholder="选择账户" style="width: 100%">
              <el-option
                v-for="acc in cashLeafAccounts"
                :key="acc.id"
                :label="`${getAccountDisplayName(acc)} (${getFundUsageLabel(acc.fundUsage)})`"
                :value="acc.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="金额（元）" required>
            <el-input-number v-model="form.amount" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.note" placeholder="比如：工资 / 咖啡 / 午餐" />
          </el-form-item>
        </template>

        <!-- 转账 -->
        <template v-else-if="selectedType === 'TRANSFER_OUT' || selectedType === 'TRANSFER_IN'">
          <el-form-item label="转出账户" required>
            <el-select v-model="form.fromAccountId" placeholder="选择转出账户" style="width: 100%">
              <el-option
                v-for="acc in cashLeafAccounts"
                :key="acc.id"
                :label="getAccountDisplayName(acc)"
                :value="acc.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="转入账户" required>
            <el-select v-model="form.toAccountId" placeholder="选择转入账户" style="width: 100%">
              <el-option
                v-for="acc in cashLeafAccounts"
                :key="acc.id"
                :label="getAccountDisplayName(acc)"
                :value="acc.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="金额（元）" required>
            <el-input-number v-model="form.amount" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.note" placeholder="比如：分配到房租预备金" />
          </el-form-item>
        </template>

        <!-- 买入/卖出 -->
        <template v-else-if="selectedType === 'BUY' || selectedType === 'SELL'">
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
          <el-form-item label="资金来源账户" required>
            <el-select v-model="form.accountId" placeholder="选择账户" style="width: 100%">
              <el-option
                v-for="acc in cashLeafAccounts"
                :key="acc.id"
                :label="getAccountDisplayName(acc)"
                :value="acc.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="金额（元）" required>
            <el-input-number v-model="form.amount" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="费用（元）">
            <el-input-number v-model="form.fee" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.note" placeholder="比如：周定投 / 赎回" />
          </el-form-item>
        </template>

        <!-- 调整 -->
        <template v-else-if="selectedType === 'ADJUST'">
          <el-form-item label="账户" required>
            <el-select v-model="form.accountId" placeholder="选择账户" style="width: 100%">
              <el-option
                v-for="acc in cashLeafAccounts"
                :key="acc.id"
                :label="getAccountDisplayName(acc)"
                :value="acc.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="调整后余额（元）" required>
            <el-input-number v-model="form.amount" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="调整原因">
            <el-input
              v-model="form.note"
              type="textarea"
              placeholder="请说明调整原因（如：对账差异修正）"
            />
          </el-form-item>
        </template>
      </el-form>
      <div style="display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px">
        <el-button @click="step = 1">← 返回</el-button>
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">提交</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAccountStore, useProductStore } from '@wealth-hub/shared'
import { ledgerApi, getFundUsageLabel, txnTypeMap } from '@wealth-hub/shared'
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

const step = ref(1)
const selectedType = ref('')
const submitting = ref(false)

const form = ref({
  accountId: undefined as number | undefined,
  fromAccountId: undefined as number | undefined,
  toAccountId: undefined as number | undefined,
  productId: undefined as number | undefined,
  amount: undefined as number | undefined,
  fee: 0,
  note: '',
})

const txnTypeOptions: Record<string, { name: string; icon: string }> = {
  EXPENSE: { name: '支出', icon: '💸' },
  INCOME: { name: '收入', icon: '💵' },
  TRANSFER_OUT: { name: '转账/分配', icon: '⇄' },
  BUY: { name: '买入/申购', icon: '📈' },
  SELL: { name: '卖出/赎回', icon: '📉' },
  ADJUST: { name: '调整', icon: '⚙️' },
}

const cashLeafAccounts = computed(() => accountStore.cashLeafAccounts)

const products = computed(() => productStore.products.filter((p) => p.isActive))

watch(visible, (val) => {
  if (val) {
    step.value = 1
    selectedType.value = ''
    form.value = {
      accountId: undefined,
      fromAccountId: undefined,
      toAccountId: undefined,
      productId: undefined,
      amount: undefined,
      fee: 0,
      note: '',
    }
  }
})

function selectType(type: string) {
  selectedType.value = type
  step.value = 2
}

function getAccountDisplayName(acc: Account): string {
  const parent = accountStore.accountTree.find((a) => a.id === acc.parentAccountId)
  return parent ? `${parent.accountName} / ${acc.accountName}` : acc.accountName
}

function handleClose() {
  visible.value = false
}

async function handleSubmit() {
  if (!selectedType.value) {
    ElMessage.error('请选择业务类型')
    return
  }

  try {
    submitting.value = true

    // 根据不同的业务类型构建postings
    const postings: any[] = []

    if (selectedType.value === 'EXPENSE') {
      if (!form.value.accountId || !form.value.amount) {
        ElMessage.error('请填写完整信息')
        return
      }
      // CASH CREDIT + EXPENSE DEBIT
      postings.push({
        postingType: 'CREDIT',
        accountId: form.value.accountId,
        accountType: 'CASH',
        amount: form.value.amount,
        currency: 'CNY',
      })
      // EXPENSE账户由后端自动创建
      postings.push({
        postingType: 'DEBIT',
        accountId: form.value.accountId, // 后端会替换为虚拟账户
        accountType: 'EXPENSE',
        amount: form.value.amount,
        currency: 'CNY',
      })
    } else if (selectedType.value === 'INCOME') {
      if (!form.value.accountId || !form.value.amount) {
        ElMessage.error('请填写完整信息')
        return
      }
      // CASH DEBIT + INCOME CREDIT
      postings.push({
        postingType: 'DEBIT',
        accountId: form.value.accountId,
        accountType: 'CASH',
        amount: form.value.amount,
        currency: 'CNY',
      })
      postings.push({
        postingType: 'CREDIT',
        accountId: form.value.accountId, // 后端会替换为虚拟账户
        accountType: 'INCOME',
        amount: form.value.amount,
        currency: 'CNY',
      })
    } else if (selectedType.value === 'TRANSFER_OUT' || selectedType.value === 'TRANSFER_IN') {
      if (!form.value.fromAccountId || !form.value.toAccountId || !form.value.amount) {
        ElMessage.error('请填写完整信息')
        return
      }
      // FROM CREDIT + TO DEBIT
      postings.push({
        postingType: 'CREDIT',
        accountId: form.value.fromAccountId,
        accountType: 'CASH',
        amount: form.value.amount,
        currency: 'CNY',
      })
      postings.push({
        postingType: 'DEBIT',
        accountId: form.value.toAccountId,
        accountType: 'CASH',
        amount: form.value.amount,
        currency: 'CNY',
      })
    } else if (selectedType.value === 'BUY' || selectedType.value === 'SELL') {
      if (!form.value.productId || !form.value.accountId || !form.value.amount) {
        ElMessage.error('请填写完整信息')
        return
      }
      // 买入：CASH CREDIT + POSITION DEBIT
      // 卖出：POSITION CREDIT + CASH DEBIT
      // 这里简化处理，实际应该根据产品类型和订单类型生成正确的分录
      ElMessage.warning('买入/卖出功能需要更复杂的逻辑，建议使用订单功能')
      return
    } else if (selectedType.value === 'ADJUST') {
      if (!form.value.accountId || form.value.amount === undefined) {
        ElMessage.error('请填写完整信息')
        return
      }
      // 调整：需要计算差额，生成ADJUST分录
      ElMessage.warning('余额调整功能建议使用账户管理页面的余额调整功能')
      return
    }

    await ledgerApi.createTransaction({
      txnType: selectedType.value,
      postings,
      note: form.value.note || undefined,
    })

    ElMessage.success('提交成功')
    emit('success')
    handleClose()
  } catch (error: any) {
    ElMessage.error(error.message || '提交失败')
  } finally {
    submitting.value = false
  }
}
</script>
