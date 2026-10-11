<template>
  <div class="ledger-page">
    <header class="ledger-hero">
      <div><span class="ledger-eyebrow">MONEY JOURNAL / 收支日记</span><h1>每一笔，都有迹可循。</h1><p>查找收支、追溯分录，也给今天留下一笔记录。</p></div>
      <button class="btn primary ledger-add" @click="unifiedEntryVisible = true">＋ 记一笔</button>
    </header>
    <!-- 记一笔模态框 -->
    <UnifiedEntryModal
      v-model="unifiedEntryVisible"
      @success="handleEntrySuccess"
    />

    <!-- 编辑流水模态框（复用UnifiedEntryModal） -->
    <UnifiedEntryModal
      v-model="editVisible"
      :editing-txn="editingTxn"
      @success="handleEditSuccess"
    />

    <!-- 退款模态框 -->
    <RefundModal
      v-model="refundVisible"
      :expense-txn="selectedExpenseTxn"
      @success="handleRefundSuccess"
    />

    <!-- 报销模态框 -->
    <ReimburseModal
      v-model="reimburseVisible"
      :expense-txn="selectedExpenseTxn"
      @success="handleEntrySuccess"
    />

    <!-- 流水详情模态框 -->
    <el-dialog v-model="detailVisible" title="流水详情" width="min(800px, 94vw)" @close="detailGate.invalidate()">
      <p v-if="detailLoading" role="status">正在读取原始分录…</p>
      <div v-else-if="detailError" role="alert" class="ledger-state"><p>{{ detailError }}</p><button class="btn" @click="retryDetail">重试详情</button></div>
      <div v-if="selectedTxn && !detailLoading && !detailError">
        <div style="margin-bottom: 16px">
          <div><strong>交易ID：</strong>{{ selectedTxn.txnId }}</div>
          <div><strong>类型：</strong>{{ getTxnTypeLabel(selectedTxn.txnType) }}</div>
          <div><strong>状态：</strong>{{ selectedTxn.status }} · {{ editReason(selectedTxn, user.user?.id) || "可申请编辑，最终由服务器校验" }}</div>
          <div><strong>时间：</strong>{{ formatDateTime(selectedTxn.requestedAt) }}</div>
          <div v-if="selectedTxn.note"><strong>备注：</strong>{{ selectedTxn.note }}</div>
        </div>
        <div class="divider"></div>
        <div style="margin-top: 16px">
          <h4>分录明细</h4>
          <div style="margin-top: 8px; margin-bottom: 12px; padding: 12px; background: rgba(78, 164, 255, 0.06); border-radius: 8px; font-size: 13px; color: var(--muted);">
            <strong>复式记账说明：</strong>每笔交易必须包含至少2个分录（1个借方 + 1个贷方），确保借贷平衡。
            对于收入：现金账户（借，增加现金）+ 收入账户（贷，增加收入）。
          </div>
          <table class="detail-table" style="margin-top: 12px">
            <thead>
              <tr>
                <th>方向</th>
                <th>账户</th>
                <th>父账户</th>
                <th class="right">金额</th>
                <th>币种</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="postings.length === 0"><td colspan="5" class="ledger-state">服务器未提供分录明细；请核对原记录，不据此补记。</td></tr>
              <tr v-for="(posting, index) in postings" :key="posting.id" :class="{ 'row-even': index % 2 === 0 }">
                <td>
                  <span class="tag" :class="posting.postingType === 'DEBIT' ? 'blue' : 'green'">
                    {{ posting.postingType === 'DEBIT' ? '借' : '贷' }}
                  </span>
                </td>
                <td>
                  <div>{{ getLeafAccountName(posting.accountId) }}</div>
                  <div v-if="getAccountBalance(posting.accountId) !== null" style="font-size: 12px; color: #909399; margin-top: 4px;">
                    余额：{{ formatCurrency(getAccountBalance(posting.accountId) || 0) }}
                  </div>
                </td>
                <td>
                  <div v-if="getParentAccountName(posting.accountId)">{{ getParentAccountName(posting.accountId) }}</div>
                  <div v-else style="color: #909399;">—</div>
                  <div v-if="getParentAccountBalance(posting.accountId) !== null" style="font-size: 12px; color: #909399; margin-top: 4px;">
                    余额：{{ formatCurrency(getParentAccountBalance(posting.accountId) || 0) }}
                  </div>
                </td>
                <td class="right mono">{{ formatCurrency(posting.amount) }}</td>
                <td>{{ posting.currency }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </el-dialog>

    <div class="card">
      <div class="row-between">
        <div>
          <h3>
            交易流水
            <span class="tag gray tiny">记账流水</span>
          </h3>
          <div class="sub">展示所有记账操作流水。流水记录的是记账操作（入金、支出、转账、订单结算等），不包括行情波动。</div>
        </div>
      </div>
      <div class="divider"></div>

      <!-- 筛选 -->
      <div class="ledger-filters">
        <label class="ledger-field">月份<el-date-picker v-model="selectedMonth" type="month" value-format="YYYY-MM" placeholder="全部月份" clearable @change="handleMonthChange" /></label>
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 200px"
          size="small"
          @change="handleDateRangeChange"
        />
        <el-select 
          v-model="filters.parentAccountId" 
          placeholder="父账户" aria-label="父账户"
          style="width: 140px" 
          size="small"
          clearable 
          @change="handleParentAccountChange"
        >
          <el-option
            v-for="acc in parentAccounts"
            :key="acc.id"
            :label="acc.accountName"
            :value="acc.id"
          />
        </el-select>
        <el-select 
          v-model="filters.accountId" 
          placeholder="子账户" aria-label="子账户"
          style="width: 140px" 
          size="small"
          clearable 
          :disabled="!filters.parentAccountId"
          @change="handleFilterChange"
        >
          <el-option
            v-for="acc in availableChildAccounts"
            :key="acc.id"
            :label="acc.accountName"
            :value="acc.id"
          />
        </el-select>
        <el-input
          v-model="filters.note"
          placeholder="搜索备注，按 Enter 查询" aria-label="搜索备注"
          style="width: 230px"
          size="small"
          clearable
          @keyup.enter="handleFilterChange"
        />
        <el-select v-model="filters.txnType" placeholder="所有交易类型" aria-label="交易类型" clearable @change="handleFilterChange"><el-option v-for="option in txnTypeOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select>
        <el-select v-model="pageCategory" placeholder="分类 · 仅当前页" aria-label="当前页分类" clearable><el-option label="未分类" value="missing" /><el-option label="未知分类" value="unknown" /><el-option v-for="option in categoryOptions" :key="option.value" :label="option.label" :value="String(option.value)" /></el-select>
        <el-button @click="handleFilterChange">搜索</el-button><el-button @click="resetFilters">重置</el-button>
      </div>

      <p v-if="accountError" role="alert">{{ accountError }} <button class="btn-small" @click="loadAccounts">重试账户</button></p>
      <p class="sub">服务器共 {{ pagination.total }} 条 · 当前页显示 {{ visibleTransactions.length }} 条。分类仅筛选当前页；全期间分类查询请使用下方统计。</p>
      <p v-if="loadError" role="alert" class="ledger-state">{{ loadError }} <button class="btn" @click="loadTransactions">重试查询</button></p>
      <!-- 流水列表 -->
      <div class="ledger-table-container hide-scrollbar">
        <table class="ledger-table">
          <thead>
            <tr>
              <th style="width: 110px;">时间</th>
              <th class="right" style="width: 100px;">金额</th>
              <th style="width: 110px;">分类</th>
              <th style="width: 200px;">叶子账户</th>
              <th class="right" style="width: 110px;">叶子账户余额</th>
              <th class="right" style="width: 110px;">父账户余额</th>
              <th style="width: 250px;">备注</th>
              <th class="right" style="width: 220px;">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="8" class="td-muted" style="text-align: center; padding: 24px">加载中...</td>
            </tr>
            <tr v-else-if="!loadError && transactions.length === 0">
              <td colspan="8" class="td-muted" style="text-align: center; padding: 24px">当前查询没有流水记录。可调整条件或点击「记一笔」。</td>
            </tr>
            <tr v-if="!loading && !loadError && transactions.length > 0 && visibleTransactions.length === 0"><td colspan="8" class="ledger-state">本页没有所选分类；可翻页查找，或查看下方分类统计。</td></tr>
            <tr v-for="(txn, index) in visibleTransactions" :key="txn.txnId" :class="{ 'row-even': index % 2 === 0 }">
              <td class="mono time-cell">
                <div class="time-date">{{ formatDate(txn.requestedAt) }}</div>
                <div class="time-time">{{ formatTime(txn.requestedAt) }}</div><small>{{ getTxnTypeLabel(txn.txnType) }}</small>
              </td>
              <td class="right mono" :class="getAmountClass(txn.txnType)">
                {{ rowAmount(txn) }}
              </td>
              <td class="category-cell">
                <div class="category-parent">{{ getCategoryParent(txn) }}</div>
                <div class="category-child">{{ getCategoryChild(txn) }}</div>
              </td>
              <td>{{ (txn as any).leafAccountName || '—' }}</td>
              <td class="right mono">{{ knownMoney(txn.leafAccountBalance) }}</td>
              <td class="right mono">{{ knownMoney(txn.parentAccountBalance) }}</td>
              <td class="td-muted note-cell">{{ txn.note || '' }}</td>
              <td class="right operation-cell">
                <div style="display: flex; align-items: center; justify-content: flex-end; gap: 8px; flex-wrap: nowrap;">
                  <button
                    v-if="txn.txnType === 'EXPENSE'"
                    class="btn-small"
                    @click="handleRefund(txn)"
                  >
                    退款
                  </button>
                  <button
                    v-if="txn.txnType === 'EXPENSE' && (txn as any).isReimbursable"
                    class="btn-small"
                    @click="handleReimburse(txn)"
                  >
                    报销
                  </button>
                  <button
                    v-if="!txn.orderId"
                    class="btn-small"
                    :disabled="!!editReason(txn, user.user?.id)"
                    :title="editReason(txn, user.user?.id)"
                    @click="handleEdit(txn)"
                  >
                    编辑
                  </button>
                  <button
                    v-if="!txn.orderId"
                    class="btn-small btn-danger"
                    @click="handleDelete(txn)"
                  >
                    删除
                  </button>
                  <button class="btn-small" @click="handleViewDetail(txn)">详情</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      
      <!-- 分页 -->
      <div style="margin-top: 16px; display: flex; justify-content: flex-end">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handlePageSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <div class="card ledger-stats-card">
      <div class="row-between">
        <div>
          <h3>
            流水统计
            <span class="tag gray tiny">服务器统计</span>
          </h3>
          <div class="sub">只统计已确认、未冲销流水；默认不把转账混入生活收支。统计沿用服务器金额口径，不按列表当前页相加。</div>
          <p class="sub">日期与账户跟随查询；下方类型、分类独立生效。备注搜索及当前页分类不影响统计。跨币种不在前端折算。</p>
        </div>
        <el-button size="small" :loading="statsLoading" @click="loadStats">刷新统计</el-button>
      </div>
      <div class="divider"></div>

      <div class="row-gap" style="margin-bottom: 16px">
        <el-select v-model="statsPeriod" placeholder="统计周期" style="width: 120px" size="small" @change="loadStats">
          <el-option label="按日" value="DAY" />
          <el-option label="按周" value="WEEK" />
          <el-option label="按月" value="MONTH" />
        </el-select>
        <el-select v-model="statsGroupBy" placeholder="拆分维度" style="width: 140px" size="small" @change="loadStats">
          <el-option label="分类" value="CATEGORY" />
          <el-option label="交易类型" value="TXN_TYPE" />
          <el-option label="子账户" value="ACCOUNT" />
          <el-option label="父账户" value="PARENT_ACCOUNT" />
        </el-select>
        <el-select v-model="statsTxnType" placeholder="交易类型" style="width: 140px" size="small" clearable @change="loadStats">
          <el-option
            v-for="option in txnTypeOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select v-model="statsCategoryId" placeholder="分类" style="width: 170px" size="small" clearable @change="loadStats">
          <el-option
            v-for="option in categoryOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-switch
          v-model="statsIncludeTransfer"
          active-text="包含转账"
          inactive-text="排除转账"
          @change="loadStats"
        />
      </div>

      <div v-if="statsLoading" class="td-muted" style="padding: 24px; text-align: center">统计加载中...</div>
      <p v-else-if="statsError" role="alert" class="ledger-state">{{ statsError }} <button class="btn" @click="loadStats">重试统计</button></p>
      <template v-else-if="statsSummary">
        <div class="stats-summary-grid">
          <div class="stats-card">
            <div class="stats-label">生活收入</div>
            <div class="stats-value amount-income">{{ knownMoney(statsSummary?.totalIncome) }}</div>
          </div>
          <div class="stats-card">
            <div class="stats-label">生活支出</div>
            <div class="stats-value amount-expense">{{ knownMoney(statsSummary?.totalExpense) }}</div>
          </div>
          <div class="stats-card">
            <div class="stats-label">生活净流入</div>
            <div class="stats-value">{{ knownMoney(statsSummary?.netCashflow) }}</div>
          </div>
          <div class="stats-card">
            <div class="stats-label">投资流入 / 流出</div>
            <div class="stats-value">{{ knownMoney(statsSummary?.investmentInflow) }} / {{ knownMoney(statsSummary?.investmentOutflow) }}</div>
          </div>
          <div class="stats-card">
            <div class="stats-label">日均支出</div>
            <div class="stats-value">{{ knownMoney(statsSummary?.avgDailyExpense) }}</div>
          </div>
          <div class="stats-card">
            <div class="stats-label">交易笔数</div>
            <div class="stats-value">{{ statsSummary?.txnCount ?? "未知" }}</div>
          </div>
        </div>

        <div class="stats-panels">
          <div class="stats-panel">
            <h4>趋势</h4>
            <table class="mini-table">
              <thead>
                <tr>
                  <th>周期</th>
                  <th class="right">收入</th>
                  <th class="right">支出</th>
                  <th class="right">净流入</th>
                  <th class="right">笔数</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in statsTrend" :key="item.period">
                  <td>{{ item.period }}</td>
                  <td class="right amount-income">{{ formatCurrency(item.income) }}</td>
                  <td class="right amount-expense">{{ formatCurrency(item.expense) }}</td>
                  <td class="right">{{ formatCurrency(item.netCashflow) }}</td>
                  <td class="right">{{ item.txnCount }}</td>
                </tr>
                <tr v-if="statsTrend.length === 0"><td colspan="5" class="td-muted">暂无趋势数据</td></tr>
              </tbody>
            </table>
          </div>

          <div class="stats-panel">
            <h4>拆分</h4>
            <table class="mini-table">
              <thead>
                <tr>
                  <th>维度</th>
                  <th class="right">金额</th>
                  <th class="right">占比</th>
                  <th class="right">笔数</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in statsBreakdown" :key="item.key">
                  <td>{{ formatBreakdownName(item.name) }}</td>
                  <td class="right">{{ formatCurrency(item.amount) }}</td>
                  <td class="right">{{ item.percentage }}%</td>
                  <td class="right">{{ item.txnCount }}</td>
                </tr>
                <tr v-if="statsBreakdown.length === 0"><td colspan="4" class="td-muted">暂无拆分数据</td></tr>
              </tbody>
            </table>
          </div>

          <div class="stats-panel stats-panel-wide">
            <h4>Top 流水</h4>
            <table class="mini-table">
              <thead>
                <tr>
                  <th>日期</th>
                  <th>类型</th>
                  <th>账户</th>
                  <th>备注</th>
                  <th class="right">金额</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in statsTop" :key="item.txnId">
                  <td>{{ item.tradeDate || '-' }}</td>
                  <td>{{ getTxnTypeLabel(item.txnType) }}</td>
                  <td>{{ item.accountName || '-' }}</td>
                  <td class="td-muted">{{ item.note || '' }}</td>
                  <td class="right">{{ formatCurrency(item.amount) }}</td>
                </tr>
                <tr v-if="statsTop.length === 0"><td colspan="5" class="td-muted">暂无 Top 数据</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, onActivated, onDeactivated } from 'vue'
import { ElNotification, ElPagination, ElMessageBox } from 'element-plus'
import { ledgerApi, ledgerStatsApi, accountApi, buildAccountTree, formatDateTime, formatCurrency, getTxnTypeLabel, txnTypeMap, useAccountStore, useUserStore, expenseCategories, incomeCategories, findCategoryById } from '@wealth-hub/shared'
import type { LedgerTxn, LedgerTxnDetail, LedgerPosting, LedgerStatsBreakdown, LedgerStatsGroupBy, LedgerStatsPeriod, LedgerStatsSummary, LedgerStatsTop, LedgerStatsTrend } from '@wealth-hub/shared'
import UnifiedEntryModal from '../components/UnifiedEntryModal.vue'
import RefundModal from '../components/RefundModal.vue'
import ReimburseModal from '../components/ReimburseModal.vue'

import { createReadGate, readError, knownNumber } from './managementViewModel'
import { localDate, monthRange, editReason } from './ledgerViewModel'
import type { LedgerRow } from './ledgerViewModel'
import { financialWrite } from '../components/entrySafety'

const accountStore = useAccountStore(), user = useUserStore()
const identity = () => JSON.stringify([user.token, user.user?.id, user.user?.familyId])
const listGate = createReadGate(identity), statsGate = createReadGate(identity), detailGate = createReadGate(identity), accountGate = createReadGate(identity)
const loadError = ref(''), statsError = ref(''), detailError = ref(''), accountError = ref(''), detailLoading = ref(false)
const selectedMonth = ref(''), pageCategory = ref('')
let detailTarget: LedgerTxn | null = null
const visibleTransactions = computed(() => transactions.value.filter(txn => {
  if (!pageCategory.value) return true
  if (pageCategory.value === 'missing') return txn.categoryId == null
  if (pageCategory.value === 'unknown') return txn.categoryId != null && !findCategoryById([...expenseCategories, ...incomeCategories], Number(txn.categoryId))
  return String(txn.categoryId) === pageCategory.value
}))
const knownMoney = (value: unknown) => knownNumber(value) ? formatCurrency(value) : '未知'
function rowAmount(txn: LedgerRow) {
  return knownNumber(txn.summaryAmount) ? `${formatAmountWithSign(txn.txnType, txn.summaryAmount)}${txn.currency ? ` · ${txn.currency}` : ''}` : '未知'
}


// 本地账户数据（确保响应式）
const localAccounts = ref<any[]>([])

const loading = ref(false)
const transactions = ref<LedgerRow[]>([])
const detailVisible = ref(false)
const selectedTxn = ref<LedgerTxnDetail | null>(null)
const postings = ref<LedgerPosting[]>([])
const refundVisible = ref(false)
const reimburseVisible = ref(false)
const selectedExpenseTxn = ref<LedgerTxn | null>(null)
const unifiedEntryVisible = ref(false)
const editVisible = ref(false)
const editingTxn = ref<LedgerTxnDetail | null>(null)
const statsLoading = ref(false)
const statsPeriod = ref<LedgerStatsPeriod>('MONTH')
const statsGroupBy = ref<LedgerStatsGroupBy>('CATEGORY')
const statsTxnType = ref<string | undefined>()
const statsCategoryId = ref<number | undefined>()
const statsIncludeTransfer = ref(false)
const statsSummary = ref<LedgerStatsSummary | null>(null)
const statsTrend = ref<LedgerStatsTrend[]>([])
const statsBreakdown = ref<LedgerStatsBreakdown[]>([])
const statsTop = ref<LedgerStatsTop[]>([])

const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0,
})

const deletePending = ref(false)
const filters = reactive({
  dateRange: [] as Date[],
  parentAccountId: undefined as number | undefined,
  accountId: undefined as number | undefined,
  note: '',
  txnType: undefined as string | undefined,
  startDate: '',
  endDate: '',
})

function handleMonthChange() {
  const range = monthRange(selectedMonth.value || '')
  filters.dateRange = []
  filters.startDate = range?.[0] || ''; filters.endDate = range?.[1] || ''
  handleFilterChange()
}
function resetFilters() {
  Object.assign(filters, { dateRange: [], parentAccountId: undefined, accountId: undefined, txnType: undefined, note: '', startDate: '', endDate: '' })
  selectedMonth.value = ''; pageCategory.value = ''; handleFilterChange()
}

// 父账户列表（有子账户的账户）- 从本地 accounts 计算
const parentAccounts = computed(() => {
  // API 返回的是树形结构，父账户 = 有 children 的账户
  const allAccounts = buildAccountTree(localAccounts.value)
  if (!allAccounts || allAccounts.length === 0) return []
  
  // 返回有 children 且是 REAL 类型的账户
  return allAccounts.filter((acc: any) => 
    acc.children && acc.children.length > 0 && acc.accountKind === 'REAL'
  )
})

// 可用的子账户（根据选择的父账户）
const availableChildAccounts = computed(() => {
  if (!filters.parentAccountId) return []
  
  // 从账户树中查找父账户，然后返回其子账户
  function findAccountById(accounts: any[], id: number): any | null {
    for (const acc of accounts) {
      if (acc.id === id) {
        return acc
      }
      if (acc.children && acc.children.length > 0) {
        const found = findAccountById(acc.children, id)
        if (found) return found
      }
    }
    return null
  }
  
  const parentAccount = findAccountById(buildAccountTree(localAccounts.value), filters.parentAccountId)
  if (!parentAccount || !parentAccount.children) return []
  
  // 返回父账户的所有REAL类型的子账户
  return parentAccount.children.filter((acc: any) => acc.accountKind === 'REAL')
})

const txnTypeOptions = computed(() => Object.entries(txnTypeMap).map(([value, label]) => ({ value, label })))

const categoryOptions = computed(() => {
  const merged = [...expenseCategories, ...incomeCategories]
  return merged.map(category => ({
    value: category.id,
    label: category.categoryL2 ? `${category.categoryL1} - ${category.categoryL2}` : category.categoryL1,
  }))
})

function handleParentAccountChange() {
  filters.accountId = undefined
  handleFilterChange()
}

function handleDateRangeChange() {
  selectedMonth.value = ""
  if (filters.dateRange && filters.dateRange.length === 2) {
    filters.startDate = localDate(filters.dateRange[0])
    filters.endDate = localDate(filters.dateRange[1])
  } else {
    filters.startDate = ''
    filters.endDate = ''
  }
  handleFilterChange()
}

function handleFilterChange() {
  pagination.page = 1 // 重置到第一页
  loadTransactions()
  loadStats()
}

async function loadTransactions() {
  if (!listGate.active) return
  const current = listGate.begin()
  transactions.value = []; pagination.total = 0; loadError.value = ""
  loading.value = true
  try {
    const response = await ledgerApi.getTransactions({
      txnType: filters.txnType || undefined,
      startDate: filters.startDate || undefined,
      endDate: filters.endDate || undefined,
      parentAccountId: filters.parentAccountId || undefined,
      accountId: filters.accountId || undefined,
      note: filters.note || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    if (!current()) return
    if (!Array.isArray(response.list) || !knownNumber(response.total)) throw new Error("流水响应格式不正确")
    // 确保 categoryId 等字段被正确解析
    transactions.value = response.list.map(txn => ({
      ...txn,
      categoryId: txn.categoryId != null ? Number(txn.categoryId) : undefined,
    })) as LedgerRow[]
    pagination.total = response.total
  } catch (error: any) {
    if (current()) loadError.value = `${readError(error, '流水')} ${error.message || ''}`
  } finally {
    if (current()) loading.value = false
  }
}

function buildStatsQuery() {
  return {
    startDate: filters.startDate || undefined,
    endDate: filters.endDate || undefined,
    accountIds: filters.accountId ? [filters.accountId] : undefined,
    parentAccountIds: filters.parentAccountId && !filters.accountId ? [filters.parentAccountId] : undefined,
    txnTypes: statsTxnType.value ? [statsTxnType.value] : undefined,
    categoryIds: statsCategoryId.value ? [statsCategoryId.value] : undefined,
    includeTransfer: statsIncludeTransfer.value,
    period: statsPeriod.value,
    groupBy: statsGroupBy.value,
    limit: 10,
  }
}

async function loadStats() {
  if (!statsGate.active) return
  const current = statsGate.begin()
  statsSummary.value = null; statsTrend.value = []; statsBreakdown.value = []; statsTop.value = []; statsError.value = ""
  statsLoading.value = true
  try {
    const query = buildStatsQuery()
    const [summary, trend, breakdown, top] = await Promise.all([
      ledgerStatsApi.getSummary(query),
      ledgerStatsApi.getTrend(query),
      ledgerStatsApi.getBreakdown(query),
      ledgerStatsApi.getTop(query),
    ])
    if (!current()) return
    statsSummary.value = summary
    statsTrend.value = trend
    statsBreakdown.value = breakdown
    statsTop.value = top
  } catch (error: any) {
    if (current()) statsError.value = `${readError(error, '统计')} ${error.message || ''}`
  } finally {
    if (current()) statsLoading.value = false
  }
}

function handlePageChange(page: number) {
  pagination.page = page
  loadTransactions()
}

function handlePageSizeChange(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1 // 重置到第一页
  loadTransactions()
}


async function handleViewDetail(txn: LedgerTxn) {
  detailTarget = txn; detailVisible.value = true; selectedTxn.value = null; postings.value = []; detailError.value = ''
  const current = detailGate.begin(); detailLoading.value = true
  try {
    const detail = await ledgerApi.getTransactionDetail((txn as LedgerRow).originalTxnId || txn.txnId)
    if (!current() || !detailVisible.value) return
    selectedTxn.value = detail; postings.value = detail.postings || []
  } catch (error: any) { if (current()) detailError.value = `${readError(error, '流水详情')} ${error.message || ''}` }
  finally { if (current()) detailLoading.value = false }
}
function retryDetail() { if (detailTarget) void handleViewDetail(detailTarget) }

function getLeafAccountName(accountId: number): string {
  // 显示规则：父账户名称-叶子账户名称
  const account = findAccountInTree(accountId)
  if (!account) {
    // 如果找不到账户，尝试从accountStore.accounts中查找（包括虚拟账户）
    const flatAccount = accountStore.accounts.find((a) => a.id === accountId)
    if (flatAccount) {
      // 如果是虚拟账户，直接返回名称
      if (flatAccount.accountKind === 'VIRTUAL') {
        return flatAccount.accountName
      }
      // 如果是真实账户，查找父账户
      if (flatAccount.parentAccountId) {
        const parent = accountStore.accounts.find((a) => a.id === flatAccount.parentAccountId)
        if (parent) {
          return `${parent.accountName}-${flatAccount.accountName}`
        }
      }
      return flatAccount.accountName
    }
    return `账户ID: ${accountId}`
  }
  // 如果有父账户，显示父账户名称-叶子账户名称
  if (account.parentAccountId) {
    const parent = findAccountInTree(account.parentAccountId)
    if (parent) {
      return `${parent.accountName}-${account.accountName}`
    }
  }
  return account.accountName
}

function findAccountInTree(accountId: number): any | null {
  function traverse(accounts: any[]): any | null {
    for (const acc of accounts) {
      if (acc.id === accountId) {
        return acc
      }
      if (acc.children && acc.children.length > 0) {
        const found = traverse(acc.children)
        if (found) return found
      }
    }
    return null
  }
  return traverse(accountStore.accountTree)
}

function getParentAccountName(accountId: number): string | null {
  const account = findAccountInTree(accountId)
  if (!account) {
    // 如果找不到账户，尝试从accountStore.accounts中查找（包括虚拟账户）
    const flatAccount = accountStore.accounts.find((a) => a.id === accountId)
    if (flatAccount && flatAccount.parentAccountId) {
      const parent = accountStore.accounts.find((a) => a.id === flatAccount.parentAccountId)
      if (parent) {
        return parent.accountName
      }
    }
    return null
  }
  if (!account.parentAccountId) return null
  const parent = findAccountInTree(account.parentAccountId)
  if (!parent) return null
  return parent.accountName
}

function getAccountBalance(accountId: number): number | null {
  const account = findAccountInTree(accountId)
  if (!account) {
    // 如果找不到账户，尝试从accountStore.accounts中查找（包括虚拟账户）
    const flatAccount = accountStore.accounts.find((a) => a.id === accountId)
    if (flatAccount) {
      return flatAccount.balance || 0
    }
    return null
  }
  return account.balance || 0
}

function getParentAccountBalance(accountId: number): number | null {
  const account = findAccountInTree(accountId)
  if (!account || !account.parentAccountId) return null
  const parent = findAccountInTree(account.parentAccountId)
  if (!parent) return null
  // 计算父账户余额（所有子账户余额之和）
  if (parent.children && parent.children.length > 0) {
    return parent.children.reduce((sum: number, child: any) => {
      return sum + (child.balance || 0)
    }, 0)
  }
  return parent.balance || 0
}

function formatDate(dateTimeStr: string): string {
  if (!dateTimeStr) return '—'
  const date = new Date(dateTimeStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatTime(dateTimeStr: string): string {
  if (!dateTimeStr) return '—'
  const date = new Date(dateTimeStr)
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${hours}:${minutes}:${seconds}`
}

function getCategoryParent(txn: LedgerTxn): string {
  const categoryId = txn.categoryId ? Number(txn.categoryId) : undefined
  if (!categoryId) {
    return '未分类'
  }
  
  const categories = txn.txnType === 'EXPENSE' ? expenseCategories : incomeCategories
  const category = findCategoryById(categories, categoryId)
  
  if (!category) {
    return `未知分类（${categoryId}）`
  }
  
  return category.categoryL1
}

function getCategoryChild(txn: LedgerTxn): string {
  const categoryId = txn.categoryId ? Number(txn.categoryId) : undefined
  if (!categoryId) {
    return ''
  }
  
  const categories = txn.txnType === 'EXPENSE' ? expenseCategories : incomeCategories
  const category = findCategoryById(categories, categoryId)
  
  if (!category || !category.categoryL2) {
    return ''
  }
  
  return category.categoryL2
}

function formatBreakdownName(name: string): string {
  if (!name || !name.startsWith('分类 ')) {
    return name || '-'
  }
  const categoryId = Number(name.replace('分类 ', ''))
  if (!categoryId) {
    return name
  }
  const expenseCategory = findCategoryById(expenseCategories, categoryId)
  const incomeCategory = findCategoryById(incomeCategories, categoryId)
  const category = expenseCategory || incomeCategory
  if (!category) {
    return name
  }
  return category.categoryL2 ? `${category.categoryL1} - ${category.categoryL2}` : category.categoryL1
}

function refreshLedgerPage() {
  loadTransactions()
  loadStats()
}

/**
 * 根据交易类型获取金额显示的颜色类
 * 颜色约定：
 * - 收入/报销入/赎回入金：红色（钱进来）
 * - 支出/报销出：绿色（钱出去）
 * - 买入/申购：蓝色（投资行为）
 * - 卖出/赎回/赎回出金：橙色（变现行为）
 * - 转账：黑色/灰色（中性）
 * - 逆回购：青色（短期理财）
 * - 转托管：紫色（调仓行为）
 * - 调整：黄色（修正行为）
 */
function getAmountClass(txnType: string): string {
  switch (txnType) {
    case 'INCOME':
    case 'REIMBURSE_IN':
    case 'REDEMPTION_IN':  // 赎回入金
    case 'DIVIDEND_CASH':
    case 'DIVIDEND_REINVEST':
      return 'amount-income' // 收入/分红：红色（钱进来）
    case 'EXPENSE':
    case 'REIMBURSE_OUT':
    case 'FEE':
    case 'TAX':
      return 'amount-expense' // 支出/费用：绿色（钱出去）
    case 'BUY':
    case 'SUBSCRIPTION':
      return 'amount-buy' // 买入/申购：蓝色
    case 'SELL':
    case 'REDEMPTION':
    case 'REDEMPTION_OUT':  // 赎回出金
      return 'amount-sell' // 卖出/赎回：橙色
    case 'TRANSFER_OUT':
    case 'TRANSFER_IN':
      return 'amount-transfer' // 转账：黑色/灰色
    case 'CUSTODY_TRANSFER':
    case 'CUSTODY_TRANSFER_OF':
      return 'amount-custody' // 转托管：紫色
    case 'BOND_REPO':
      return 'amount-repo' // 逆回购：青色
    case 'ADJUST':
      return 'amount-adjust' // 调整：黄色
    default:
      return ''
  }
}

/**
 * 格式化金额，根据交易类型添加正负号
 * 注意：流水是从"到账账户"的角度展示的
 * - 卖出/赎回：现金增加（DEBIT），显示正号
 * - 买入/申购：现金减少（CREDIT），显示负号
 * - 支出：现金减少（CREDIT），显示负号
 * - 收入：现金增加（DEBIT），显示正号
 */
function formatAmountWithSign(txnType: string, amount: number): string {
  if (amount === 0) {
    return formatCurrency(0)
  }
  
  let sign = ''
  switch (txnType) {
    case 'INCOME':
    case 'REIMBURSE_IN':
    case 'REDEMPTION_IN':  // 赎回入金
    case 'SELL':           // 卖出：现金入账
    case 'REDEMPTION':     // 赎回：现金入账
    case 'TRANSFER_IN':
    case 'DIVIDEND_CASH':
    case 'DIVIDEND_REINVEST':
    case 'BOND_REPO':
      sign = '+' // 收入、卖出、赎回、转入、分红、逆回购：正号（现金增加）
      break
    case 'EXPENSE':
    case 'REIMBURSE_OUT':
    case 'BUY':            // 买入：现金支出
    case 'SUBSCRIPTION':   // 申购：现金支出
    case 'REDEMPTION_OUT':  // 赎回出金（子账户减少）
    case 'TRANSFER_OUT':
    case 'FEE':
    case 'TAX':
      sign = '-' // 支出、买入、申购、转出、费用：负号（现金减少）
      break
    case 'CUSTODY_TRANSFER':
    case 'CUSTODY_TRANSFER_OF':
    case 'ADJUST':
      // 转托管和调整：不显示符号（金额本身可能为正或负）
      return formatCurrency(amount)
    default:
      // 其他类型：不显示符号
      return formatCurrency(amount)
  }
  
  return `${sign}${formatCurrency(Math.abs(amount))}`
}

async function handleRefund(txn: LedgerTxn) {
  const current = detailGate.begin()
  try {
    // 获取完整的交易详情（包含postings）
    const detail = await ledgerApi.getTransactionDetail(txn.txnId)
    if (!current()) return
    selectedExpenseTxn.value = detail as any
    refundVisible.value = true
  } catch (error: any) {
    ElNotification.error({ title: '错误', message: error.message || '加载失败', position: 'bottom-right' })
  }
}

async function handleReimburse(txn: LedgerTxn) {
  const current = detailGate.begin()
  try {
    // 获取完整的交易详情（包含postings）
    const detail = await ledgerApi.getTransactionDetail(txn.txnId)
    if (!current()) return
    selectedExpenseTxn.value = detail as any
    reimburseVisible.value = true
  } catch (error: any) {
    ElNotification.error({ title: '错误', message: error.message || '加载失败', position: 'bottom-right' })
  }
}

async function loadAccounts() {
  const current = accountGate.begin(); accountError.value = ''
  try { const accounts = await accountApi.getAccounts(); if (current()) { localAccounts.value = accounts; accountStore.accounts = accounts } }
  catch (error: any) { if (current()) accountError.value = `${readError(error, '账户')} ${error.message || ''}` }
}
async function handleEntrySuccess() {
  await loadAccounts()
  // Refresh all existing views from backend state, even if the account read fails.
  window.dispatchEvent(new Event('data-refresh'))
}
const handleRefundSuccess = handleEntrySuccess

async function handleEdit(txn: LedgerTxn) {
  const reason = editReason(txn, user.user?.id)
  if (reason) { ElNotification.warning({ title: "只读流水", message: reason }); return }
  const current = detailGate.begin()
  try {
    const txnId = (txn as any).originalTxnId || txn.txnId
    const detail = await ledgerApi.getTransactionDetail(txnId)
    if (!current()) return
    const reason = editReason(detail, user.user?.id)
    if (reason) { ElNotification.warning({ title: "只读流水", message: reason }); return }
    editingTxn.value = detail
    editVisible.value = true
  } catch (error: any) {
    ElNotification.error({ title: '错误', message: error.message || '加载失败', position: 'bottom-right' })
  }
}

async function handleEditSuccess() {
  editVisible.value = false
  editingTxn.value = null
  await handleEntrySuccess()
}

async function handleDelete(txn: LedgerTxn) {
  if (deletePending.value) return
  const owner = identity()
  deletePending.value = true
  try {
    await ElMessageBox.confirm(
      `确定要删除这条流水吗？删除后无法恢复，且会影响后续流水的余额计算。`,
      '确认删除',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    
    if (owner !== identity()) return
    const txnId = (txn as any).originalTxnId || txn.txnId
    await financialWrite(() => ledgerApi.deleteTransaction(txnId))
    
    ElNotification.success({ title: '成功', message: '流水已删除', position: 'bottom-right' })
    await handleEntrySuccess()
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElNotification.error({ title: '错误', message: error.message || '删除失败', position: 'bottom-right' })
    }
  } finally { deletePending.value = false }
}

function clearData() {
  for (const gate of [listGate, statsGate, detailGate, accountGate]) gate.invalidate()
  transactions.value = []; pagination.total = 0; statsSummary.value = null; statsTrend.value = []; statsBreakdown.value = []; statsTop.value = []
  selectedTxn.value = null; postings.value = []; editingTxn.value = null; selectedExpenseTxn.value = null
  detailVisible.value = false; editVisible.value = false; unifiedEntryVisible.value = false; refundVisible.value = false; reimburseVisible.value = false
  localAccounts.value = []; detailTarget = null; loading.value = false; statsLoading.value = false
}
watch(identity, (_, previous) => {
  clearData()
  if (JSON.parse(previous)[1] == null && user.user?.id && user.token) { void loadAccounts(); refreshLedgerPage() }
}, { flush: 'sync' })
onMounted(() => { void loadAccounts(); refreshLedgerPage(); window.addEventListener('data-refresh', refreshLedgerPage) })
onActivated(() => { for (const gate of [listGate, statsGate, detailGate, accountGate]) gate.activate() })
onDeactivated(() => { clearData(); for (const gate of [listGate, statsGate, detailGate, accountGate]) gate.suspend() })
onBeforeUnmount(() => { clearData(); window.removeEventListener('data-refresh', refreshLedgerPage); for (const gate of [listGate, statsGate, detailGate, accountGate]) gate.suspend() })
</script>

<style scoped>
/* 流水表格容器 - 支持横向滚动，确保操作列可见 */
.ledger-table-container {
  overflow-y: auto;
  overflow-x: auto;
  max-height: calc(100vh - 310px); /* 调整高度避免外层滚动，同时尽量多显示记录 */
  min-height: 300px;
  position: relative;
}

.ledger-stats-card {
  margin-top: 16px;
}

.stats-summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.stats-card {
  padding: 14px;
  border: 1px solid rgba(78, 164, 255, 0.16);
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(78, 164, 255, 0.08), rgba(255, 255, 255, 0.8));
}

.stats-label {
  color: #64748b;
  font-size: 12px;
  margin-bottom: 8px;
}

.stats-value {
  color: var(--text);
  font-size: 18px;
  font-weight: 700;
}

.stats-panels {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.stats-panel {
  min-width: 0;
}

.stats-panel-wide {
  grid-column: 1 / -1;
}

.mini-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
}

.mini-table th {
  padding: 10px;
  background: rgba(78, 164, 255, 0.08);
  color: var(--text);
  font-weight: 600;
  text-align: left;
}

.mini-table td {
  padding: 10px;
  border-bottom: 1px solid rgba(230, 238, 247, 0.7);
}

@media (max-width: 900px) {
  .stats-panels {
    grid-template-columns: 1fr;
  }
}

/* 流水表格样式 - 更大、更美观 */
.ledger-table {
  font-size: 14px;
  border-radius: 12px;
  overflow: hidden;
  width: 100%;
}

.ledger-table thead th {
  background: linear-gradient(135deg, rgba(78, 164, 255, 0.12), rgba(124, 199, 255, 0.08));
  color: var(--text);
  font-weight: 600;
  padding: 12px 12px;
  font-size: 13px;
  border-bottom: 2px solid rgba(78, 164, 255, 0.2);
  white-space: nowrap;
}

.ledger-table tbody td {
  padding: 14px 12px;
  border-bottom: 1px solid rgba(230, 238, 247, 0.6);
  font-size: 14px;
  vertical-align: middle;
  white-space: nowrap;
}

/* 时间列：换行显示，字体适中 */
.ledger-table tbody td.time-cell {
  white-space: normal;
  font-size: 12px;
  line-height: 1.4;
  padding: 8px 8px;
}

.time-cell .time-date {
  font-weight: 500;
  margin-bottom: 3px;
}

.time-cell .time-time {
  color: #909399;
  font-size: 11px;
}

/* 分类列：换行显示，字体适中 */
.ledger-table tbody td.category-cell {
  white-space: normal;
  font-size: 12px;
  line-height: 1.4;
  padding: 8px 8px;
}

.category-cell .category-parent {
  font-weight: 500;
  margin-bottom: 3px;
}

.category-cell .category-child {
  color: #909399;
  font-size: 11px;
}

/* 备注列：允许换行，字体缩小 */
.ledger-table tbody td.note-cell {
  white-space: normal;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.4;
  padding: 10px 12px;
}

/* 操作列：固定右侧，确保可见 */
.ledger-table thead th:last-child {
  position: sticky;
  right: 0;
  background: linear-gradient(135deg, rgba(78, 164, 255, 0.12), rgba(124, 199, 255, 0.08));
  z-index: 10;
  box-shadow: -2px 0 4px rgba(0, 0, 0, 0.05);
}

.ledger-table tbody td.operation-cell {
  white-space: nowrap;
  position: sticky;
  right: 0;
  background: var(--card);
  z-index: 5;
  box-shadow: -2px 0 4px rgba(0, 0, 0, 0.05);
}

.ledger-table tbody tr.row-even td.operation-cell {
  background: rgba(78, 164, 255, 0.04);
}

.ledger-table tbody tr:hover td.operation-cell {
  background: rgba(78, 164, 255, 0.1) !important;
}

.ledger-table tbody tr.row-even {
  background: rgba(78, 164, 255, 0.04);
}

.ledger-table tbody tr:hover {
  background: rgba(78, 164, 255, 0.1) !important;
  transition: background 0.2s ease;
}

.ledger-table tbody tr:last-child td {
  border-bottom: none;
}

/* 详情表格样式 */
.el-dialog table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  margin-top: 12px;
}

.el-dialog table thead th {
  background: rgba(78, 164, 255, 0.08);
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 600;
  text-align: left;
  border-bottom: 2px solid rgba(78, 164, 255, 0.15);
}

.el-dialog table tbody td {
  padding: 14px 16px;
  border-bottom: 1px solid rgba(230, 238, 247, 0.6);
  font-size: 14px;
}

.el-dialog table tbody tr:nth-child(even) {
  background: rgba(78, 164, 255, 0.03);
}

.el-dialog table tbody tr:hover {
  background: rgba(78, 164, 255, 0.08);
}

.detail-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
}

.detail-table thead th {
  background: rgba(78, 164, 255, 0.08);
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 600;
  text-align: left;
  border-bottom: 2px solid rgba(78, 164, 255, 0.15);
}

.detail-table tbody td {
  padding: 14px 16px;
  border-bottom: 1px solid rgba(230, 238, 247, 0.6);
  font-size: 14px;
}

.detail-table tbody tr.row-even {
  background: rgba(78, 164, 255, 0.03);
}

.detail-table tbody tr:hover {
  background: rgba(78, 164, 255, 0.08);
}

/* 金额颜色样式 - 中国习惯：红涨绿跌 */
.amount-income {
  color: #ef4444; /* 红色 - 收入（钱进来） */
  font-weight: 600;
}

.amount-expense {
  color: #16a34a; /* 绿色 - 支出（钱出去） */
  font-weight: 600;
}

.amount-buy {
  color: #3b82f6; /* 蓝色 - 买入/申购 */
  font-weight: 600;
}

.amount-sell {
  color: #f59e0b; /* 橙色 - 卖出/赎回 */
  font-weight: 600;
}

.amount-transfer {
  color: #64748b; /* 灰色 - 转账 */
  font-weight: 500;
}

.amount-custody {
  color: #a855f7; /* 紫色 - 转托管 */
  font-weight: 500;
}

.amount-dividend {
  color: #06b6d4; /* 青色 - 分红 */
  font-weight: 500;
}

.amount-fee {
  color: #16a34a; /* 绿色 - 费用/税费（钱出去） */
  font-weight: 500;
}

.amount-adjust {
  color: #eab308; /* 黄色 - 调整 */
  font-weight: 500;
}

.amount-repo {
  color: #06b6d4; /* 青色 - 逆回购 */
  font-weight: 500;
}
</style>

<style scoped>
.ledger-page { display: grid; gap: 24px; min-width: 0; }
.ledger-hero { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 16px 4px 0; }
.ledger-eyebrow { font-size: 11px; font-weight: 700; letter-spacing: .16em; color: var(--muted); }
.ledger-hero h1 { margin: 10px 0; font-size: clamp(24px, 2.6vw, 36px); letter-spacing: -.035em; }
.ledger-hero p { color: var(--muted); margin: 0; }
.ledger-add { white-space: nowrap; padding: 14px 22px; font-weight: 700; }
.ledger-filters { display: flex; flex-wrap: wrap; align-items: end; gap: 12px; margin-bottom: 16px; }
.ledger-field { display: grid; gap: 6px; color: var(--muted); font-size: 12px; }
.ledger-filters :deep(.el-select) { width: 170px; }
.ledger-state { padding: 24px; color: var(--muted); background: var(--card); border-radius: 12px; }
.ledger-table th, .ledger-table td { background: var(--card); border-color: var(--line); }
.ledger-table th { color: var(--muted); }
.stats-card { background: var(--card); border-color: var(--line); }
.stats-label { color: var(--muted); }
.stats-value { font-variant-numeric: tabular-nums; }
.btn-small:disabled { opacity: .55; cursor: not-allowed; }
.ledger-table .operation-cell { background: var(--card); }
@media (max-width: 760px) { .ledger-hero { align-items: start; flex-direction: column; gap: 16px; } .ledger-filters { align-items: start; } }
</style>
