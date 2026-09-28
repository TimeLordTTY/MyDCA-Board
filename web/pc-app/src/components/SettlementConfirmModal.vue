<template>
  <el-dialog
    v-model="visible"
    title="确认结算"
    width="640px"
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

    <!-- v0.13.0：只读结算预览（不产生任何业务写入） -->
    <div v-if="preview" style="margin-top: 8px">
      <div class="divider"></div>
      <h4 style="margin: 12px 0 8px">结算影响预览（只读）</h4>
      <el-alert
        v-if="!preview.confirmSupported"
        type="error"
        :closable="false"
        title="当前输入无法结算"
        :description="buildSettlementBlockingText(preview)"
        style="margin-bottom: 8px"
      />
      <ul style="margin: 0 0 8px 0; padding-left: 18px">
        <li v-for="(line, index) in preview.summaryLines" :key="index">{{ line }}</li>
      </ul>
      <el-table :data="preview.postingsPreview" size="small" border>
        <el-table-column prop="accountName" label="账户" min-width="140" />
        <el-table-column prop="accountType" label="类型" width="90" />
        <el-table-column prop="postingType" label="方向" width="80" />
        <el-table-column prop="amount" label="金额" width="110" />
        <el-table-column prop="shares" label="份额" width="110" />
      </el-table>
      <el-alert
        v-for="(warning, index) in preview.warnings"
        :key="`w-${index}`"
        type="warning"
        :closable="false"
        :title="warning"
        style="margin-top: 8px"
      />
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button @click="handlePreview" :loading="previewing">生成结算预览</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="submitting" :disabled="!preview || !preview.confirmSupported">
        确认结算
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessageBox, ElNotification } from 'element-plus'
import {
  settlementApi,
  orderApi,
  formatCurrency,
  getOrderTypeLabel,
  buildSettlementPreviewText,
  buildSettlementBlockingText,
  buildSettlementConfirmTitle,
} from '@wealth-hub/shared'
import type { Order, SettlementPreview } from '@wealth-hub/shared'

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
const previewing = ref(false)
/** fresh preview：任何输入变化都会清空，禁止拿旧预览直接 confirm */
const preview = ref<SettlementPreview | null>(null)

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
    preview.value = null
  }
})

// 输入变化后旧预览立即失效
watch(
  form,
  () => {
    preview.value = null
  },
  { deep: true }
)

function handleClose() {
  visible.value = false
}

function toDateString(value: string | Date): string {
  return typeof value === 'string' ? value : value.toISOString().split('T')[0]
}

function buildRequest() {
  if (!props.order) return null
  return {
    orderId: props.order.orderId,
    confirmDate: toDateString(form.value.confirmDate),
    navDate: toDateString(form.value.navDate),
    confirmNav: form.value.confirmNav,
    confirmShares: form.value.confirmShares,
    confirmAmount: form.value.confirmAmount,
    confirmFee: form.value.confirmFee,
    note: form.value.note || undefined,
  }
}

async function handlePreview() {
  const request = buildRequest()
  if (!request) {
    ElNotification.error({ title: '错误', message: '订单信息缺失', position: 'bottom-right' })
    return
  }
  if (!request.confirmDate || !request.navDate || !request.confirmNav) {
    ElNotification.error({ title: '错误', message: '请填写必填项', position: 'bottom-right' })
    return
  }
  try {
    previewing.value = true
    preview.value = await settlementApi.previewSettlement(request)
  } catch (error: any) {
    ElNotification.error({ title: '错误', message: error.message || '生成结算预览失败', position: 'bottom-right' })
  } finally {
    previewing.value = false
  }
}

async function handleSubmit() {
  const request = buildRequest()
  if (!request) {
    ElNotification.error({ title: '错误', message: '订单信息缺失', position: 'bottom-right' })
    return
  }
  if (!preview.value || !preview.value.confirmSupported) {
    ElNotification.warning({
      title: '请先生成结算预览',
      message: '只有当前输入的最新结算预览通过校验后，才能正式确认结算。',
      position: 'bottom-right',
    })
    return
  }

  // 主人二次确认：展示现金 / 持仓 / 手续费影响
  try {
    await ElMessageBox.confirm(buildSettlementPreviewText(preview.value), buildSettlementConfirmTitle(preview.value), {
      confirmButtonText: '确认结算',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch (e) {
    return
  }

  try {
    submitting.value = true
    await orderApi.confirmSettlement({ ...request, freshPreviewToken: preview.value.freshPreviewToken })
    ElNotification({
      title: '结算成功',
      message: '订单已确认结算',
      type: 'success',
      position: 'bottom-right',
      duration: 3000,
    })
    emit('success')
    handleClose()
  } catch (error: any) {
    ElNotification.error({ title: '错误', message: error.message || '确认失败', position: 'bottom-right' })
  } finally {
    submitting.value = false
  }
}
</script>