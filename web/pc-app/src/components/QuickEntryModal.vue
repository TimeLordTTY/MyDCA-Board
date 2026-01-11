<template>
  <el-dialog
    v-model="visible"
    :title="type === 'EXPENSE' ? '快速录入 - 支出' : '快速录入 - 收入'"
    width="500px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form :model="form" label-width="100px">
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
        <el-input v-model="form.note" :placeholder="type === 'EXPENSE' ? '比如：咖啡 / 午餐' : '比如：工资 / 奖金'" />
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
import { useAccountStore } from '@wealth-hub/shared'
import { ledgerApi, getFundUsageLabel } from '@wealth-hub/shared'
import type { Account } from '@wealth-hub/shared'

const props = defineProps<{
  modelValue: boolean
  type: 'EXPENSE' | 'INCOME'
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
  accountId: undefined as number | undefined,
  amount: undefined as number | undefined,
  note: '',
})

const cashLeafAccounts = computed(() => accountStore.cashLeafAccounts)

watch(visible, (val) => {
  if (val) {
    form.value = {
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
  if (!form.value.accountId || !form.value.amount) {
    ElMessage.error('请填写完整信息')
    return
  }

  try {
    submitting.value = true
    await ledgerApi.quickEntry({
      type: props.type,
      accountId: form.value.accountId,
      amount: form.value.amount,
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
