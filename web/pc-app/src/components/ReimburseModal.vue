<template>
  <el-dialog
    v-model="visible"
    title="报销"
    width="min(600px, 94vw)"
    :close-on-click-modal="false"
    :show-close="!submitting"
    :close-on-press-escape="!submitting"
    @close="handleClose"
  >
    <p v-if="serverError" role="alert">{{ serverError }}</p><p v-if="entryWriteState.uncertain" role="alert">{{ uncertainWriteMessage }}</p>
    <el-form :disabled="submitting" :model="form" label-width="120px">
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
import { ElNotification, ElMessageBox } from 'element-plus'
import { useAccountStore, useUserStore } from '@wealth-hub/shared'
import { ledgerApi as rawLedgerApi, getFundUsageLabel, incomeCategories, getCategoryGroups } from '@wealth-hub/shared'
import type { Account, LedgerTxn } from '@wealth-hub/shared'

import { guardWrites, entryWriteState, uncertainWriteMessage } from './entrySafety'
import { localDateTime } from '../views/ledgerViewModel'
const ledgerApi = guardWrites(rawLedgerApi, ['reimburse'])
const serverError = ref('')

const props = defineProps<{
  modelValue: boolean
  expenseTxn?: LedgerTxn | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const accountStore = useAccountStore(), user = useUserStore()
const identity = () => JSON.stringify([user.token, user.user?.id, user.user?.familyId])
watch(identity, () => { visible.value = false }, { flush: 'sync' })

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const submitting = ref(false)

const form = ref({
  occurredAt: localDateTime(),
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
      occurredAt: expense.requestedAt ? expense.requestedAt : localDateTime(),
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
    serverError.value = ''
    form.value = {
      occurredAt: localDateTime(),
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
  if (submitting.value) return
  visible.value = false
}

async function handleSubmit() {
  const owner = identity(), snapshot = JSON.stringify([form.value, props])
  if (submitting.value || entryWriteState.pending || entryWriteState.uncertain) return
  serverError.value = ""
  if (!props.expenseTxn) {
    ElNotification.error({ title: '错误', message: '缺少原支出信息', position: 'bottom-right' })
    return
  }
  
  if (!form.value.accountId || !form.value.amount || !form.value.occurredAt || !form.value.category || (Array.isArray(form.value.category) && form.value.category.length === 0)) {
    ElNotification.error({ title: '错误', message: '请填写完整信息', position: 'bottom-right' })
    return
  }

  try {
    submitting.value = true
    await ElMessageBox.confirm(`请核对金额 ${form.value.amount} 元、账户 ${form.value.accountId} 及时间 ${form.value.occurredAt}。确认后提交正式接口。`, '确认这一笔', { confirmButtonText: '确认提交', cancelButtonText: '返回修改', type: 'warning' })
    if (owner !== identity() || !visible.value || snapshot !== JSON.stringify([form.value, props])) { serverError.value = '身份或填写内容已变化，请重新核对。'; return }
    const categoryId = Array.isArray(form.value.category) 
      ? (form.value.category[form.value.category.length - 1] as number)
      : (form.value.category as number)
    
    await ledgerApi.reimburse(props.expenseTxn.txnId, {
      reimburseAmount: form.value.amount,
      accountId: form.value.accountId,
      occurredAt: form.value.occurredAt,
      note: form.value.note || undefined,
    })
    ElNotification.success({ title: '成功', message: '报销成功', position: 'bottom-right' })
    emit('success')
    visible.value = false
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    serverError.value = error.message || '提交失败'
    ElNotification.error({ title: '错误', message: error.message || '报销失败', position: 'bottom-right' })
  } finally {
    submitting.value = false
  }
}
</script>
