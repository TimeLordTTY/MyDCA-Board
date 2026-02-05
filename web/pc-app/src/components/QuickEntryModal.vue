<template>
  <el-dialog
    v-model="visible"
    :title="type === 'EXPENSE' ? '快速录入 - 支出' : '快速录入 - 收入'"
    width="500px"
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
import { ElNotification } from 'element-plus'
import { useAccountStore } from '@wealth-hub/shared'
import { ledgerApi, getFundUsageLabel, expenseCategories, incomeCategories, getCategoryGroups, getCategoryDisplayName } from '@wealth-hub/shared'
import type { Account, Category } from '@wealth-hub/shared'

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
  occurredAt: new Date().toISOString().slice(0, 19).replace('T', ' '),
  category: [] as number[],
  accountId: undefined as number | undefined,
  amount: undefined as number | undefined,
  note: '',
})

const cashLeafAccounts = computed(() => accountStore.cashLeafAccounts)

const categories = computed(() => props.type === 'EXPENSE' ? expenseCategories : incomeCategories)

const categoryOptions = computed(() => {
  const groups = getCategoryGroups(categories.value)
  return groups.map(group => {
    // 如果一级分类下只有一个分类且没有二级分类，直接返回一级分类
    if (group.categories.length === 1 && !group.categories[0].categoryL2) {
      return {
        value: group.categories[0].id,
        label: group.categoryL1,
      }
    }
    // 否则返回级联结构
    return {
      value: group.categoryL1,
      label: group.categoryL1,
      children: group.categories.map(cat => ({
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
  if (val) {
    form.value = {
      occurredAt: new Date().toISOString().slice(0, 19).replace('T', ' '),
      category: [],
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
  if (!form.value.accountId || !form.value.amount || !form.value.occurredAt || !form.value.category || (Array.isArray(form.value.category) && form.value.category.length === 0)) {
    ElNotification({ type: 'error', title: '错误', message: '请填写完整信息', position: 'bottom-right', duration: 3000 })
    return
  }

  try {
    submitting.value = true
    // 处理分类ID：如果是数组，取最后一个；如果是单个值，直接使用
    const categoryId = Array.isArray(form.value.category) 
      ? (form.value.category[form.value.category.length - 1] as number)
      : (form.value.category as number)
    
    await ledgerApi.quickEntry({
      type: props.type,
      accountId: form.value.accountId,
      amount: form.value.amount,
      note: form.value.note || undefined,
      occurredAt: form.value.occurredAt,
      categoryId: categoryId,
    })
    const quickMessage = form.value.note 
      ? `提交成功：${form.value.note}`
      : '提交成功'
    ElNotification.success({ title: '成功', message: quickMessage, position: 'bottom-right', duration: 3000 })
    emit('success')
    handleClose()
  } catch (error: any) {
    ElNotification.error({ title: '错误', message: error.message || '提交失败', position: 'bottom-right', duration: 3000 })
  } finally {
    submitting.value = false
  }
}
</script>
