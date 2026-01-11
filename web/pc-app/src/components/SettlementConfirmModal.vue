<template>
  <el-dialog
    v-model="visible"
    title="确认结算"
    width="600px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div v-if="order" style="margin-bottom: 16px">
      <div><strong>订单ID：</strong>{{ order.orderId }}</div>
      <div><strong>类型：</strong>{{ getOrderTypeLabel(order.orderType) }}</div>
      <div><strong>金额：</strong>{{ formatCurrency(order.amount) }}</div>
    </div>
    <div class="divider"></div>
    <el-form :model="form" label-width="120px" style="margin-top: 16px">
      <el-form-item label="确认日期" required>
        <el-date-picker v-model="form.confirmDate" type="date" style="width: 100%" />
      </el-form-item>
      <el-form-item label="净值日期" required>
        <el-date-picker v-model="form.navDate" type="date" style="width: 100%" />
      </el-form-item>
      <el-form-item label="确认净值" required>
        <el-input-number v-model="form.confirmNav" :min="0.0001" :precision="4" style="width: 100%" />
      </el-form-item>
      <el-form-item label="确认份额">
        <el-input-number v-model="form.confirmShares" :min="0" :precision="4" style="width: 100%" />
      </el-form-item>
      <el-form-item label="确认金额">
        <el-input-number v-model="form.confirmAmount" :min="0" :precision="2" style="width: 100%" />
      </el-form-item>
      <el-form-item label="确认费用">
        <el-input-number v-model="form.confirmFee" :min="0" :precision="2" style="width: 100%" />
      </el-form-item>
      <el-form-item label="手动覆盖">
        <el-switch v-model="form.isManualOverride" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.note" type="textarea" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="submitting">确认结算</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { settlementApi, formatCurrency, getOrderTypeLabel } from '@wealth-hub/shared'
import type { Order } from '@wealth-hub/shared'

const props = defineProps<{
  modelValue: boolean
  order: Order | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const submitting = ref(false)

const form = ref({
  confirmDate: '',
  navDate: '',
  confirmNav: undefined as number | undefined,
  confirmShares: undefined as number | undefined,
  confirmAmount: undefined as number | undefined,
  confirmFee: 0,
  isManualOverride: false,
  note: '',
})

watch(visible, (val) => {
  if (val && props.order) {
    form.value = {
      confirmDate: props.order.expectedConfirmDate || new Date().toISOString().split('T')[0],
      navDate: props.order.expectedNavDate || new Date().toISOString().split('T')[0],
      confirmNav: undefined,
      confirmShares: undefined,
      confirmAmount: undefined,
      confirmFee: props.order.feeEstimate || 0,
      isManualOverride: false,
      note: '',
    }
  }
})

function handleClose() {
  visible.value = false
}

async function handleSubmit() {
  if (!props.order) {
    ElMessage.error('订单信息缺失')
    return
  }

  if (!form.value.confirmDate || !form.value.navDate || !form.value.confirmNav) {
    ElMessage.error('请填写必填项')
    return
  }

  try {
    submitting.value = true
    await settlementApi.confirmSettlement({
      orderId: props.order.orderId,
      confirmDate: typeof form.value.confirmDate === 'string' ? form.value.confirmDate : form.value.confirmDate.toISOString().split('T')[0],
      navDate: typeof form.value.navDate === 'string' ? form.value.navDate : form.value.navDate.toISOString().split('T')[0],
      confirmNav: form.value.confirmNav,
      confirmShares: form.value.confirmShares,
      confirmAmount: form.value.confirmAmount,
      confirmFee: form.value.confirmFee,
      isManualOverride: form.value.isManualOverride,
      note: form.value.note || undefined,
    })
    ElMessage.success('结算确认成功')
    emit('success')
    handleClose()
  } catch (error: any) {
    ElMessage.error(error.message || '确认失败')
  } finally {
    submitting.value = false
  }
}
</script>
