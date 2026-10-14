<template>
  <el-dialog
    v-model="visible"
    title="退款"
    width="min(600px, 94vw)"
    :close-on-click-modal="false"
    :show-close="!submitting"
    :close-on-press-escape="!submitting"
    @close="handleClose"
  >
    <p v-if="serverError" role="alert">{{ serverError }}</p><p v-if="entryWriteState.uncertain" role="alert">{{ uncertainWriteMessage }}</p>
    <el-form :disabled="submitting" :model="form" label-width="120px">
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
import { ElNotification, ElMessageBox } from 'element-plus'
import { useAccountStore, useUserStore } from '@wealth-hub/shared'
import { ledgerApi as rawLedgerApi, getFundUsageLabel, formatCurrency } from '@wealth-hub/shared'
import type { Account, LedgerTxn } from '@wealth-hub/shared'

import { guardWrites, entryWriteState, uncertainWriteMessage } from './entrySafety'
import { localDateTime } from '../views/ledgerViewModel'
const ledgerApi = guardWrites(rawLedgerApi, ['refund'])
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
    
    // 默认使用原流水的发生时间
    const defaultOccurredAt = expense.requestedAt || localDateTime()
    
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
    serverError.value = ''
    form.value = {
      occurredAt: localDateTime(),
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
    await ElMessageBox.confirm(`请核对金额 ${form.value.amount} 元、账户 ${form.value.accountId} 及时间 ${form.value.occurredAt}。确认后提交正式接口。`, '确认这一笔', { confirmButtonText: '确认提交', cancelButtonText: '返回修改', type: 'warning' })
    if (owner !== identity() || !visible.value || snapshot !== JSON.stringify([form.value, props])) { serverError.value = '身份或填写内容已变化，请重新核对。'; return }
    await ledgerApi.refund(props.expenseTxn.txnId, {
      refundAmount: form.value.amount,
      accountId: form.value.accountId,
      occurredAt: form.value.occurredAt,
      note: form.value.note || undefined,
    })
    ElNotification.success({ title: '成功', message: '退款成功', position: 'bottom-right' })
    emit('success')
    visible.value = false
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    serverError.value = error.message || '提交失败'
    ElNotification.error({ title: '错误', message: error.message || '退款失败', position: 'bottom-right' })
  } finally {
    submitting.value = false
  }
}
</script>
