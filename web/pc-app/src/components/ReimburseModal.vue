<template>
  <el-dialog
    v-model="visible"
    title="报销"
    width="600px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form :model="form" label-width="120px">
      <el-form-item label="发生时间" required>
        <el-date-picker
          v-model="form.occurredAt"
          type="datetime"
          placeholder="选择发生时间"
          style="width: 100%"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </el-form-item>
      <el-form-item label="分类" required>
        <el-cascader
          v-model="form.category"
          :options="categoryOptions"
          :props="cascaderProps"
          placeholder="选择分类"
          style="width: 100%"
          clearable
        />
      </el-form-item>
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
      <el-form-item label="报销金额（元）" required>
        <el-input-number v-model="form.amount" :min="0.01" :precision="2" style="width: 100%" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.note" placeholder="报销说明" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="submitting">确认报销</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAccountStore } from '@wealth-hub/shared'
import { ledgerApi, getFundUsageLabel, incomeCategories, getCategoryGroups } from '@wealth-hub/shared'
import type { Account, LedgerTxn } from '@wealth-hub/shared'

const props = defineProps<{
  modelValue: boolean
  expenseTxn?: LedgerTxn | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const accountStore = useAccountStore()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const submitting = ref(false)

const form = ref({
  occurredAt: new Date().toISOString().slice(0, 19).replace('T', ' '),
  category: [] as number[],
  accountId: undefined as number | undefined,
  amount: undefined as number | undefined,
  note: '',
})

const cashLeafAccounts = computed(() => accountStore.cashLeafAccounts)

const categories = computed(() => incomeCategories)

const categoryOptions = computed(() => {
  if (categories.value.length === 0) return []
  const groups = getCategoryGroups(categories.value)
  // 默认选择"报销"分类
  const reimburseCategory = categories.value.find(cat => cat.categoryL1 === '报销')
  return groups.map((group: any) => {
    if (group.categories.length === 1 && !group.categories[0].categoryL2) {
      return {
        value: group.categories[0].id,
        label: group.categoryL1,
      }
    }
    return {
      value: group.categoryL1,
      label: group.categoryL1,
      children: group.categories.map((cat: any) => ({
        value: cat.id,
        label: cat.categoryL2 || cat.categoryL1,
      })),
    }
  })
})

const cascaderProps = {
  value: 'value',
  label: 'label',
  children: 'children',
  emitPath: true,
  checkStrictly: false,
  expandTrigger: 'hover' as const,
}

watch(visible, (val) => {
  if (val && props.expenseTxn) {
    // 回显支出信息
    const expense = props.expenseTxn
    form.value = {
      occurredAt: expense.requestedAt ? new Date(expense.requestedAt).toISOString().slice(0, 19).replace('T', ' ') : new Date().toISOString().slice(0, 19).replace('T', ' '),
      category: [], // 默认选择报销分类
      accountId: undefined, // 需要从postings中获取
      amount: undefined, // 需要从postings中获取
      note: expense.note || '',
    }
    
    // 尝试从postings中获取账户和金额信息
    // 这里假设后端会返回postings，如果没有则需要单独获取
    if (expense && 'postings' in expense && (expense as any).postings) {
      const postings = (expense as any).postings
      const cashPosting = postings.find((p: any) => p.accountType === 'CASH' && p.postingType === 'CREDIT')
      if (cashPosting) {
        form.value.accountId = cashPosting.accountId
        form.value.amount = cashPosting.amount
      }
    }
    
    // 设置默认分类为"报销"
    const reimburseCategory = categories.value.find(cat => cat.categoryL1 === '报销')
    if (reimburseCategory) {
      form.value.category = [reimburseCategory.id]
    }
  } else if (val) {
    form.value = {
      occurredAt: new Date().toISOString().slice(0, 19).replace('T', ' '),
      category: [],
      accountId: undefined,
      amount: undefined,
      note: '',
    }
    const reimburseCategory = categories.value.find(cat => cat.categoryL1 === '报销')
    if (reimburseCategory) {
      form.value.category = [reimburseCategory.id]
    }
  }
})

function getAccountDisplayName(acc: Account): string {
  const parent = accountStore.accountTree.find((a) => a.id === acc.parentAccountId)
  return parent ? `${parent.accountName} / ${acc.accountName}` : acc.accountName
}

function handleClose() {
  visible.value = false
}

async function handleSubmit() {
  if (!props.expenseTxn) {
    ElMessage.error('缺少原支出信息')
    return
  }
  
  if (!form.value.accountId || !form.value.amount || !form.value.occurredAt || !form.value.category || (Array.isArray(form.value.category) && form.value.category.length === 0)) {
    ElMessage.error('请填写完整信息')
    return
  }

  try {
    submitting.value = true
    const categoryId = Array.isArray(form.value.category) 
      ? (form.value.category[form.value.category.length - 1] as number)
      : (form.value.category as number)
    
    await ledgerApi.reimburse(props.expenseTxn.txnId, {
      reimburseAmount: form.value.amount,
      accountId: form.value.accountId,
      occurredAt: form.value.occurredAt,
      note: form.value.note || undefined,
    })
    ElMessage.success('报销成功')
    emit('success')
    handleClose()
  } catch (error: any) {
    ElMessage.error(error.message || '报销失败')
  } finally {
    submitting.value = false
  }
}
</script>
