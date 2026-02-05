<template>
  <el-dialog
    v-model="visible"
    title="退款"
    width="600px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form :model="form" label-width="120px">
      <el-form-item label="退款时间" required>
        <el-date-picker
          v-model="form.occurredAt"
          type="datetime"
          placeholder="选择退款时间"
          style="width: 100%"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </el-form-item>
      <el-form-item label="退款账户" required>
        <el-select v-model="form.accountId" placeholder="选择退款到账账户" style="width: 100%">
          <el-option
            v-for="acc in cashLeafAccounts"
            :key="acc.id"
            :label="`${getAccountDisplayName(acc)} (${getFundUsageLabel(acc.fundUsage)})`"
            :value="acc.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="退款金额（元）" required>
        <el-input-number 
          v-model="form.amount" 
          :min="0.01" 
          :precision="2" 
          :max="maxRefundAmount > 0.01 ? maxRefundAmount : undefined" 
          style="width: 100%" 
        />
        <div style="margin-top: 4px; color: #909399; font-size: 12px">
          原交易金额：{{ formatCurrency(originalAmount) }}，最多可退款：{{ formatCurrency(maxRefundAmount) }}
          <span v-if="maxRefundAmount > 0" style="color: #67c23a">（支持部分退款）</span>
        </div>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.note" placeholder="退款说明" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="submitting">确认退款</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElNotification } from 'element-plus'
import { useAccountStore } from '@wealth-hub/shared'
import { ledgerApi, getFundUsageLabel, formatCurrency } from '@wealth-hub/shared'
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
  accountId: undefined as number | undefined,
  amount: undefined as number | undefined,
  note: '',
})

const cashLeafAccounts = computed(() => accountStore.cashLeafAccounts)

// 计算原交易金额和最大退款金额
const originalAmount = computed(() => {
  if (!props.expenseTxn) return 0
  const txnAny = props.expenseTxn as any
  
  // 优先使用 summaryAmount（如果存在）
  if (txnAny.summaryAmount) {
    return Number(txnAny.summaryAmount) || 0
  }
  
  // 如果没有 summaryAmount，从 postings 中计算
  // 对于支出交易，应该找 CASH CREDIT 的金额（现金减少）
  if (txnAny.postings && Array.isArray(txnAny.postings)) {
    const cashPosting = txnAny.postings.find((p: any) => 
      p.accountType === 'CASH' && p.postingType === 'CREDIT'
    )
    if (cashPosting && cashPosting.amount) {
      return Number(cashPosting.amount) || 0
    }
    
    // 如果没有找到 CASH CREDIT，尝试找 EXPENSE DEBIT 的金额
    const expensePosting = txnAny.postings.find((p: any) => 
      p.accountType === 'EXPENSE' && p.postingType === 'DEBIT'
    )
    if (expensePosting && expensePosting.amount) {
      return Number(expensePosting.amount) || 0
    }
  }
  
  return 0
})

const maxRefundAmount = computed(() => {
  const amount = originalAmount.value
  // 确保返回值至少为 0，避免负数
  return amount > 0 ? amount : 0
})

watch(visible, (val) => {
  if (val && props.expenseTxn) {
    // 回显支出信息
    const expense = props.expenseTxn
    const txnAny = expense as any
    
    // 默认使用原流水的发生时间
    const defaultOccurredAt = expense.occurredAt || new Date().toISOString().slice(0, 19).replace('T', ' ')
    
    form.value = {
      occurredAt: defaultOccurredAt,
      accountId: undefined, // 需要从postings中获取
      amount: originalAmount.value, // 默认退款全额
      note: expense.note ? `退款：${expense.note}` : '退款',
    }
    
    // 尝试从postings中获取账户信息
    if (expense && 'postings' in expense && (expense as any).postings) {
      const postings = (expense as any).postings
      const cashPosting = postings.find((p: any) => p.accountType === 'CASH' && p.postingType === 'CREDIT')
      if (cashPosting) {
        form.value.accountId = cashPosting.accountId
      }
    }
  } else if (val) {
    form.value = {
      occurredAt: new Date().toISOString().slice(0, 19).replace('T', ' '),
      accountId: undefined,
      amount: undefined,
      note: '',
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
    ElNotification.error({ title: '错误', message: '缺少原支出信息', position: 'bottom-right' })
    return
  }
  
  if (!form.value.occurredAt || !form.value.accountId || !form.value.amount) {
    ElNotification.error({ title: '错误', message: '请填写完整信息', position: 'bottom-right' })
    return
  }

  if (form.value.amount < 0.01) {
    ElNotification.error({ title: '错误', message: '退款金额不能小于 0.01 元', position: 'bottom-right' })
    return
  }

  if (maxRefundAmount.value > 0 && form.value.amount > maxRefundAmount.value) {
    ElNotification.error({ title: '错误', message: `退款金额不能超过原交易金额 ${formatCurrency(maxRefundAmount.value)}`, position: 'bottom-right' })
    return
  }

  try {
    submitting.value = true
    await ledgerApi.refund(props.expenseTxn.txnId, {
      refundAmount: form.value.amount,
      accountId: form.value.accountId,
      occurredAt: form.value.occurredAt,
      note: form.value.note || undefined,
    })
    ElNotification.success({ title: '成功', message: '退款成功', position: 'bottom-right' })
    emit('success')
    handleClose()
  } catch (error: any) {
    ElNotification.error({ title: '错误', message: error.message || '退款失败', position: 'bottom-right' })
  } finally {
    submitting.value = false
  }
}
</script>
