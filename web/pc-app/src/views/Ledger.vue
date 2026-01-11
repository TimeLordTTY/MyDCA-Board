<template>
  <div>
    <div class="card">
      <div class="row-between">
        <div>
          <h3>
            交易流水
            <span class="tag gray tiny">记账流水</span>
          </h3>
          <div class="sub">展示所有记账操作流水。流水记录的是记账操作（入金、支出、转账、订单结算等），不包括行情波动。</div>
        </div>
        <div class="row-gap">
          <el-button @click="handleUnifiedEntry">📝 统一记账</el-button>
          <el-button @click="handleQuickEntry">⚡ 快速录入</el-button>
        </div>
      </div>
      <div class="divider"></div>

      <!-- 筛选 -->
      <div class="row-gap" style="margin-bottom: 16px">
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 240px"
          @change="handleDateRangeChange"
        />
        <el-select v-model="filters.txnType" placeholder="交易类型" style="width: 150px" clearable @change="loadTransactions">
          <el-option
            v-for="(label, value) in txnTypeMap"
            :key="value"
            :label="label"
            :value="value"
          />
        </el-select>
        <el-button @click="loadTransactions">搜索</el-button>
      </div>

      <!-- 流水列表 -->
      <div style="overflow: auto">
        <table>
          <thead>
            <tr>
              <th>时间</th>
              <th>类型</th>
              <th>摘要</th>
              <th class="right">金额</th>
              <th>备注</th>
              <th class="right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="6" class="td-muted" style="text-align: center">加载中...</td>
            </tr>
            <tr v-else-if="transactions.length === 0">
              <td colspan="6" class="td-muted" style="text-align: center">暂无流水记录</td>
            </tr>
            <tr v-for="txn in transactions" :key="txn.txnId">
              <td class="mono">{{ formatDateTime(txn.requestedAt) }}</td>
              <td>
                <span class="tag blue">{{ getTxnTypeLabel(txn.txnType) }}</span>
              </td>
              <td><b>{{ txn.note || '—' }}</b></td>
              <td class="right mono">—</td>
              <td class="td-muted">{{ txn.note || '' }}</td>
              <td class="right">
                <button class="btn" @click="handleViewDetail(txn)">详情</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ledgerApi, formatDateTime, getTxnTypeLabel, txnTypeMap } from '@wealth-hub/shared'
import type { LedgerTxn } from '@wealth-hub/shared'

const loading = ref(false)
const transactions = ref<LedgerTxn[]>([])

const filters = reactive({
  dateRange: [] as Date[],
  txnType: '',
  startDate: '',
  endDate: '',
})

function handleDateRangeChange() {
  if (filters.dateRange && filters.dateRange.length === 2) {
    filters.startDate = filters.dateRange[0].toISOString().split('T')[0]
    filters.endDate = filters.dateRange[1].toISOString().split('T')[0]
  } else {
    filters.startDate = ''
    filters.endDate = ''
  }
  loadTransactions()
}

async function loadTransactions() {
  loading.value = true
  try {
    transactions.value = await ledgerApi.getTransactions({
      txnType: filters.txnType || undefined,
      startDate: filters.startDate || undefined,
      endDate: filters.endDate || undefined,
    })
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function handleUnifiedEntry() {
  ElMessage.info('统一记账功能开发中')
}

function handleQuickEntry() {
  ElMessage.info('快速录入功能开发中')
}

function handleViewDetail(txn: LedgerTxn) {
  ElMessage.info('流水详情功能开发中')
}

onMounted(() => {
  loadTransactions()
})
</script>
