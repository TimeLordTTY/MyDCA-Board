<template>
  <el-dialog
    v-model="visible"
    title="新建订单"
    width="600px"
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
        :label="form.orderType === 'BUY' || form.orderType === 'SUBSCRIPTION' ? '金额（元）' : '份额'"
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
        <el-select v-model="form.accountId" placeholder="选择账户" style="width: 100%">
          <el-option
            v-for="acc in cashLeafAccounts"
            :key="acc.id"
            :label="getAccountDisplayName(acc)"
            :value="acc.id"
          />
        </el-select>
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

const cashLeafAccounts = computed(() => accountStore.cashLeafAccounts)

const products = computed(() => productStore.products.filter((p) => p.isActive))

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
  if (!form.value.productId || !form.value.accountId) {
    ElMessage.error('请填写完整信息')
    return
  }

  if (
    (form.value.orderType === 'BUY' || form.value.orderType === 'SUBSCRIPTION') &&
    !form.value.amount
  ) {
    ElMessage.error('请填写金额')
    return
  }

  if (
    (form.value.orderType === 'SELL' || form.value.orderType === 'REDEMPTION') &&
    !form.value.shares
  ) {
    ElMessage.error('请填写份额')
    return
  }

  try {
    submitting.value = true
    await orderApi.createOrder({
      productId: form.value.productId,
      orderType: form.value.orderType,
      amount: form.value.amount,
      shares: form.value.shares,
      fundingLines: [
        {
          accountId: form.value.accountId,
          amount: form.value.amount || 0,
        },
      ],
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
