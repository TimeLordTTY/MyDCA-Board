<template>
  <el-dialog
    v-model="visible"
    title="记一笔"
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
          <el-form-item v-if="selectedType === 'EXPENSE'" label="是否报销">
            <el-switch v-model="form.isReimbursable" />
            <span style="margin-left: 8px; color: #909399; font-size: 12px">标记为可报销</span>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.note" placeholder="比如：工资 / 咖啡 / 午餐" />
          </el-form-item>
        </template>

        <!-- 转账 -->
        <template v-else-if="selectedType === 'TRANSFER_OUT' || selectedType === 'TRANSFER_IN'">
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

        <!-- 买入/申购 -->
        <template v-else-if="selectedType === 'BUY' || selectedType === 'SUBSCRIPTION'">
          <el-form-item label="产品" required>
            <el-select v-model="form.productId" placeholder="选择产品" style="width: 100%" filterable>
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
              <el-option label="买入" value="BUY" />
              <el-option label="申购" value="SUBSCRIPTION" />
            </el-select>
          </el-form-item>
          <el-form-item label="发起时间" required>
            <el-date-picker
              v-model="form.requestedAt"
              type="datetime"
              placeholder="选择发起时间"
              style="width: 100%"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </el-form-item>
          <el-form-item label="确认日期">
            <el-date-picker
              v-model="form.confirmDate"
              type="date"
              placeholder="选择确认日期"
              style="width: 100%"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
          <el-form-item label="净值日期">
            <el-date-picker
              v-model="form.navDate"
              type="date"
              placeholder="选择净值日期"
              style="width: 100%"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
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
          <el-form-item label="金额（元）" required>
            <el-input-number v-model="form.amount" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="费用（元）">
            <el-input-number v-model="form.fee" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.note" placeholder="比如：周定投" />
          </el-form-item>
        </template>
        
        <!-- 卖出/赎回 -->
        <template v-else-if="selectedType === 'SELL' || selectedType === 'REDEMPTION'">
          <el-form-item label="产品" required>
            <el-select v-model="form.productId" placeholder="选择产品" style="width: 100%" filterable>
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
              <el-option label="卖出" value="SELL" />
              <el-option label="赎回" value="REDEMPTION" />
            </el-select>
          </el-form-item>
          <el-form-item label="发起时间" required>
            <el-date-picker
              v-model="form.requestedAt"
              type="datetime"
              placeholder="选择发起时间"
              style="width: 100%"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </el-form-item>
          <el-form-item label="确认日期">
            <el-date-picker
              v-model="form.confirmDate"
              type="date"
              placeholder="选择确认日期"
              style="width: 100%"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
          <el-form-item label="净值日期">
            <el-date-picker
              v-model="form.navDate"
              type="date"
              placeholder="选择净值日期"
              style="width: 100%"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
          <el-form-item label="到账账户" required>
            <el-select v-model="form.accountId" placeholder="选择账户" style="width: 100%">
              <el-option
                v-for="acc in cashLeafAccounts"
                :key="acc.id"
                :label="getAccountDisplayName(acc)"
                :value="acc.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="份额" required>
            <el-input-number v-model="form.shares" :min="0.01" :precision="4" style="width: 100%" />
          </el-form-item>
          <el-form-item label="费用（元）">
            <el-input-number v-model="form.fee" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.note" placeholder="比如：赎回" />
          </el-form-item>
        </template>
        
        <!-- 转托管 -->
        <template v-else-if="selectedType === 'CUSTODY_TRANSFER'">
          <el-form-item label="产品" required>
            <el-select v-model="form.productId" placeholder="选择产品" style="width: 100%" filterable>
              <el-option
                v-for="prod in products"
                :key="prod.id"
                :label="`${prod.productName} (${prod.productCode})`"
                :value="prod.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="转出类型" required>
            <el-select v-model="form.fromChannel" placeholder="选择转出类型" style="width: 100%">
              <el-option label="场外" value="OTC" />
            </el-select>
          </el-form-item>
          <el-form-item label="转入类型" required>
            <el-select v-model="form.toChannel" placeholder="选择转入类型" style="width: 100%">
              <el-option label="场内" value="EXCHANGE" />
            </el-select>
          </el-form-item>
          <el-form-item label="转出日期" required>
            <el-date-picker
              v-model="form.transferDate"
              type="date"
              placeholder="选择转出日期"
              style="width: 100%"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
          <el-form-item label="转出价格" required>
            <el-input-number v-model="form.transferPrice" :min="0" :precision="4" style="width: 100%" />
            <div style="color: #909399; font-size: 12px; margin-top: 4px">转托管价格，通常为0费用</div>
          </el-form-item>
          <el-form-item label="份额" required>
            <el-input-number v-model="form.shares" :min="0.01" :precision="4" style="width: 100%" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.note" placeholder="转托管说明" />
          </el-form-item>
        </template>
        
        <!-- 退款 -->
        <template v-else-if="selectedType === 'REFUND'">
          <el-form-item label="原交易ID" required>
            <el-input v-model="form.relatedTxnId" placeholder="输入原交易ID" />
          </el-form-item>
          <el-form-item label="退款金额（元）" required>
            <el-input-number v-model="form.amount" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="退款账户" required>
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
            <el-input v-model="form.note" placeholder="退款说明" />
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
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useAccountStore, useProductStore } from '@wealth-hub/shared'
import { ledgerApi, getFundUsageLabel, orderApi, expenseCategories, incomeCategories, getCategoryGroups } from '@wealth-hub/shared'
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
  occurredAt: new Date().toISOString().slice(0, 19).replace('T', ' '),
  category: [] as number[],
  accountId: undefined as number | undefined,
  fromAccountId: undefined as number | undefined,
  toAccountId: undefined as number | undefined,
  productId: undefined as number | undefined,
  orderType: 'BUY' as 'BUY' | 'SELL' | 'SUBSCRIPTION' | 'REDEMPTION',
  amount: undefined as number | undefined,
  shares: undefined as number | undefined,
  fee: 0,
  requestedAt: new Date().toISOString().slice(0, 19).replace('T', ' '),
  confirmDate: undefined as string | undefined,
  navDate: undefined as string | undefined,
  transferDate: undefined as string | undefined,
  transferOutPrice: 0,
  transferInPrice: 0,
  fromChannel: 'OTC',
  toChannel: 'EXCHANGE',
  relatedTxnId: undefined as string | undefined,
  isReimbursable: false,
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

const categories = computed(() => {
  if (selectedType.value === 'EXPENSE') return expenseCategories
  if (selectedType.value === 'INCOME') return incomeCategories
  return []
})

const categoryOptions = computed(() => {
  if (categories.value.length === 0) return []
  const groups = getCategoryGroups(categories.value)
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

onMounted(() => {
  productStore.fetchProducts()
  accountStore.fetchAccounts()
})

watch(visible, (val) => {
  if (val) {
    step.value = 1
    selectedType.value = ''
    form.value = {
      occurredAt: new Date().toISOString().slice(0, 19).replace('T', ' '),
      category: [],
      accountId: undefined,
      fromAccountId: undefined,
      toAccountId: undefined,
      productId: undefined,
      orderType: 'BUY',
      amount: undefined,
      shares: undefined,
      fee: 0,
      requestedAt: new Date().toISOString().slice(0, 19).replace('T', ' '),
      confirmDate: undefined,
      navDate: undefined,
      transferDate: undefined,
      transferOutPrice: 0,
      transferInPrice: 0,
      fromChannel: 'OTC',
      toChannel: 'EXCHANGE',
      relatedTxnId: undefined,
      isReimbursable: false,
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
      if (!form.value.accountId || !form.value.amount || !form.value.occurredAt || !form.value.category || (Array.isArray(form.value.category) && form.value.category.length === 0)) {
        ElMessage.error('请填写完整信息')
        return
      }
      const categoryId = Array.isArray(form.value.category) 
        ? (form.value.category[form.value.category.length - 1] as number)
        : (form.value.category as number)
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
      await ledgerApi.createTransaction({
        txnType: selectedType.value,
        postings,
        note: form.value.note || undefined,
        requestedAt: form.value.occurredAt,
        categoryId: categoryId,
        isReimbursable: form.value.isReimbursable,
      })
      ElMessage.success('提交成功')
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'INCOME') {
      if (!form.value.accountId || !form.value.amount || !form.value.occurredAt || !form.value.category || (Array.isArray(form.value.category) && form.value.category.length === 0)) {
        ElMessage.error('请填写完整信息')
        return
      }
      const categoryId = Array.isArray(form.value.category) 
        ? (form.value.category[form.value.category.length - 1] as number)
        : (form.value.category as number)
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
      await ledgerApi.createTransaction({
        txnType: selectedType.value,
        postings,
        note: form.value.note || undefined,
        requestedAt: form.value.occurredAt,
        categoryId: categoryId,
      })
      ElMessage.success('提交成功')
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'TRANSFER_OUT' || selectedType.value === 'TRANSFER_IN') {
      if (!form.value.fromAccountId || !form.value.toAccountId || !form.value.amount || !form.value.occurredAt) {
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
      await ledgerApi.createTransaction({
        txnType: selectedType.value,
        postings,
        note: form.value.note || undefined,
        requestedAt: form.value.occurredAt,
      })
      ElMessage.success('提交成功')
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'BUY' || selectedType.value === 'SUBSCRIPTION') {
      if (!form.value.productId || !form.value.accountId || !form.value.amount || !form.value.orderType || !form.value.requestedAt) {
        ElMessage.error('请填写完整信息')
        return
      }
      await orderApi.createOrder({
        productId: form.value.productId,
        orderType: form.value.orderType,
        amount: form.value.amount,
        fundingLines: [{
          accountId: form.value.accountId,
          amount: form.value.amount,
        }],
        tradeDate: form.value.requestedAt.split(' ')[0],
        expectedNavDate: form.value.navDate,
        note: form.value.note || undefined,
      })
      ElMessage.success('订单创建成功')
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'SELL' || selectedType.value === 'REDEMPTION') {
      if (!form.value.productId || !form.value.accountId || !form.value.shares || !form.value.orderType || !form.value.requestedAt) {
        ElMessage.error('请填写完整信息')
        return
      }
      await orderApi.createOrder({
        productId: form.value.productId,
        orderType: form.value.orderType,
        shares: form.value.shares,
        fundingLines: [{
          accountId: form.value.accountId,
          amount: 0,
        }],
        tradeDate: form.value.requestedAt.split(' ')[0],
        expectedNavDate: form.value.navDate,
        note: form.value.note || undefined,
      })
      ElMessage.success('订单创建成功')
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'CUSTODY_TRANSFER') {
      if (!form.value.productId || !form.value.shares || !form.value.transferDate || form.value.transferOutPrice === undefined || form.value.transferInPrice === undefined) {
        ElMessage.error('请填写完整信息')
        return
      }
      // 调用转托管API
      await ledgerApi.createCustodyTransfer({
        productId: form.value.productId,
        shares: form.value.shares,
        transferOutPrice: form.value.transferOutPrice,
        transferInPrice: form.value.transferInPrice,
        transferDate: form.value.transferDate,
        note: form.value.note || undefined,
      })
      ElMessage.success('转托管成功')
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'REFUND') {
      if (!form.value.relatedTxnId || !form.value.accountId || !form.value.amount) {
        ElMessage.error('请填写完整信息')
        return
      }
      await ledgerApi.refund(form.value.relatedTxnId, {
        refundAmount: form.value.amount,
        accountId: form.value.accountId,
        note: form.value.note || undefined,
      })
      ElMessage.success('退款成功')
      emit('success')
      handleClose()
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

    // 其他类型暂不支持
    ElMessage.error('不支持的业务类型')
    return
  } catch (error: any) {
    ElMessage.error(error.message || '提交失败')
  } finally {
    submitting.value = false
  }
}
</script>
