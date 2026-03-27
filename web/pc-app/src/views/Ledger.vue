<template>
  <div>
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
      @success="loadTransactions"
    />

    <!-- 流水详情模态框 -->
    <el-dialog v-model="detailVisible" title="流水详情" width="800px">
      <div v-if="selectedTxn">
        <div style="margin-bottom: 16px">
          <div><strong>交易ID：</strong>{{ selectedTxn.txnId }}</div>
          <div><strong>类型：</strong>{{ getTxnTypeLabel(selectedTxn.txnType) }}</div>
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
      <div class="row-gap" style="margin-bottom: 16px">
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
          placeholder="父账户" 
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
          placeholder="子账户" 
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
          placeholder="备注查询"
          style="width: 150px"
          size="small"
          clearable
          @keyup.enter="handleFilterChange"
        />
        <el-button size="small" @click="handleFilterChange">搜索</el-button>
      </div>

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
              <th class="right" style="width: 220px; position: sticky; right: 0; background: white; z-index: 10;">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="8" class="td-muted" style="text-align: center; padding: 24px">加载中...</td>
            </tr>
            <tr v-else-if="transactions.length === 0">
              <td colspan="8" class="td-muted" style="text-align: center; padding: 24px">暂无流水记录</td>
            </tr>
            <tr v-for="(txn, index) in transactions" :key="txn.txnId" :class="{ 'row-even': index % 2 === 0 }">
              <td class="mono time-cell">
                <div class="time-date">{{ formatDate(txn.requestedAt) }}</div>
                <div class="time-time">{{ formatTime(txn.requestedAt) }}</div>
              </td>
              <td class="right mono" :class="getAmountClass(txn.txnType)">
                {{ formatAmountWithSign(txn.txnType, (txn as any).summaryAmount || 0) }}
              </td>
              <td class="category-cell">
                <div class="category-parent">{{ getCategoryParent(txn) }}</div>
                <div class="category-child">{{ getCategoryChild(txn) }}</div>
              </td>
              <td>{{ (txn as any).leafAccountName || '—' }}</td>
              <td class="right mono">{{ formatCurrency((txn as any).leafAccountBalance || 0) }}</td>
              <td class="right mono">{{ formatCurrency((txn as any).parentAccountBalance || 0) }}</td>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElNotification, ElPagination, ElMessageBox } from 'element-plus'
import { ledgerApi, formatDateTime, formatCurrency, getTxnTypeLabel, txnTypeMap, useAccountStore, expenseCategories, incomeCategories, findCategoryById } from '@wealth-hub/shared'
import type { LedgerTxn, LedgerTxnDetail, LedgerPosting } from '@wealth-hub/shared'
import UnifiedEntryModal from '../components/UnifiedEntryModal.vue'
import RefundModal from '../components/RefundModal.vue'
import ReimburseModal from '../components/ReimburseModal.vue'

const accountStore = useAccountStore()

// 本地账户数据（确保响应式）
const localAccounts = ref<any[]>([])

const loading = ref(false)
const transactions = ref<LedgerTxn[]>([])
const detailVisible = ref(false)
const selectedTxn = ref<LedgerTxnDetail | null>(null)
const postings = ref<LedgerPosting[]>([])
const refundVisible = ref(false)
const reimburseVisible = ref(false)
const selectedExpenseTxn = ref<LedgerTxn | null>(null)
const unifiedEntryVisible = ref(false)
const editVisible = ref(false)
const editingTxn = ref<LedgerTxnDetail | null>(null)

const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0,
})

const filters = reactive({
  dateRange: [] as Date[],
  parentAccountId: undefined as number | undefined,
  accountId: undefined as number | undefined,
  note: '',
  startDate: '',
  endDate: '',
})

// 避免使用 toISOString 触发时区转换（导致日期被减一天），这里按本地时间格式化日期
function formatLocalDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

// 父账户列表（有子账户的账户）- 从本地 accounts 计算
const parentAccounts = computed(() => {
  // API 返回的是树形结构，父账户 = 有 children 的账户
  const allAccounts = localAccounts.value
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
  
  const parentAccount = findAccountById(accountStore.accountTree, filters.parentAccountId)
  if (!parentAccount || !parentAccount.children) return []
  
  // 返回父账户的所有REAL类型的子账户
  return parentAccount.children.filter((acc: any) => acc.accountKind === 'REAL')
})

function handleParentAccountChange() {
  filters.accountId = undefined
  handleFilterChange()
}

function handleDateRangeChange() {
  if (filters.dateRange && filters.dateRange.length === 2) {
    filters.startDate = formatLocalDate(filters.dateRange[0])
    filters.endDate = formatLocalDate(filters.dateRange[1])
  } else {
    filters.startDate = ''
    filters.endDate = ''
  }
  handleFilterChange()
}

function handleFilterChange() {
  pagination.page = 1 // 重置到第一页
  loadTransactions()
}

async function loadTransactions() {
  loading.value = true
  try {
    const response = await ledgerApi.getTransactions({
      startDate: filters.startDate || undefined,
      endDate: filters.endDate || undefined,
      parentAccountId: filters.parentAccountId || undefined,
      accountId: filters.accountId || undefined,
      note: filters.note || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    // 确保 categoryId 等字段被正确解析
    transactions.value = response.list.map(txn => ({
      ...txn,
      categoryId: txn.categoryId ? Number(txn.categoryId) : undefined,
    })) as LedgerTxn[]
    pagination.total = response.total
  } catch (error: any) {
    ElNotification.error({ title: '错误', message: error.message || '加载失败', position: 'bottom-right' })
  } finally {
    loading.value = false
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
  try {
    // 对于转账交易，使用 originalTxnId（如果存在）
    const txnId = (txn as any).originalTxnId || txn.txnId
    const detail = await ledgerApi.getTransactionDetail(txnId)
    selectedTxn.value = detail
    postings.value = detail.postings || []
    detailVisible.value = true
  } catch (error: any) {
    ElNotification.error({ title: '错误', message: error.message || '加载详情失败', position: 'bottom-right' })
  }
}

function getAccountName(accountId: number): string {
  const account = accountStore.accountTree.find((a) => a.id === accountId)
  if (!account) return `账户ID: ${accountId}`
  const parent = account.parentAccountId
    ? accountStore.accountTree.find((a) => a.id === account.parentAccountId)
    : null
  return parent ? `${parent.accountName} / ${account.accountName}` : account.accountName
}

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

function getSummaryText(txn: LedgerTxn): string {
  const txnAny = txn as any
  if (txn.note) {
    return txn.note
  }
  // 如果有主要账户ID，显示账户名称
  if (txnAny.mainAccountId) {
    const accountName = getAccountName(txnAny.mainAccountId)
    return accountName
  }
  return '—'
}

function getCategoryDisplayText(txn: LedgerTxn): string {
  // 确保 categoryId 是数字类型
  const categoryId = txn.categoryId ? Number(txn.categoryId) : undefined
  if (!categoryId) {
    return '—'
  }
  
  // 根据交易类型选择分类列表
  const categories = txn.txnType === 'EXPENSE' ? expenseCategories : incomeCategories
  const category = findCategoryById(categories, categoryId)
  
  if (!category) {
    // 如果找不到分类，输出调试信息
    console.warn(`分类未找到: categoryId=${categoryId}, txnType=${txn.txnType}`, {
      availableIds: categories.map(c => c.id),
      txnData: txn
    })
    return '—'
  }
  
  // 如果有二级分类，显示"一级分类 - 二级分类"，否则只显示一级分类
  if (category.categoryL2) {
    return `${category.categoryL1} - ${category.categoryL2}`
  }
  return category.categoryL1
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
    return '—'
  }
  
  const categories = txn.txnType === 'EXPENSE' ? expenseCategories : incomeCategories
  const category = findCategoryById(categories, categoryId)
  
  if (!category) {
    return '—'
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

function isReimbursable(txn: LedgerTxn): boolean {
  // 检查isReimbursable字段，且未报销
  const txnAny = txn as any
  return txnAny.isReimbursable === true && txnAny.isReimbursed !== true
}

async function handleRefund(txn: LedgerTxn) {
  try {
    // 获取完整的交易详情（包含postings）
    const detail = await ledgerApi.getTransactionDetail(txn.txnId)
    selectedExpenseTxn.value = detail as any
    refundVisible.value = true
  } catch (error: any) {
    ElNotification.error({ title: '错误', message: error.message || '加载失败', position: 'bottom-right' })
  }
}

async function handleReimburse(txn: LedgerTxn) {
  try {
    // 获取完整的交易详情（包含postings）
    const detail = await ledgerApi.getTransactionDetail(txn.txnId)
    selectedExpenseTxn.value = detail as any
    reimburseVisible.value = true
  } catch (error: any) {
    ElNotification.error({ title: '错误', message: error.message || '加载失败', position: 'bottom-right' })
  }
}

async function handleRefundSuccess() {
  // 退款成功后自动刷新账户数据和流水列表
  await accountStore.fetchAccounts()
  loadTransactions()
}

function handleUnifiedEntry() {
  unifiedEntryVisible.value = true
}

async function handleEntrySuccess() {
  // 记账成功后自动刷新账户数据和流水列表
  await accountStore.fetchAccounts()
  loadTransactions()
}

async function handleEdit(txn: LedgerTxn) {
  try {
    const txnId = (txn as any).originalTxnId || txn.txnId
    const detail = await ledgerApi.getTransactionDetail(txnId)
    editingTxn.value = detail
    editVisible.value = true
  } catch (error: any) {
    ElNotification.error({ title: '错误', message: error.message || '加载失败', position: 'bottom-right' })
  }
}

async function handleEditSuccess() {
  editVisible.value = false
  editingTxn.value = null
  await accountStore.fetchAccounts()
  loadTransactions()
}

async function handleDelete(txn: LedgerTxn) {
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
    
    const txnId = (txn as any).originalTxnId || txn.txnId
    await ledgerApi.deleteTransaction(txnId)
    
    ElNotification.success({ title: '成功', message: '流水已删除', position: 'bottom-right' })
    await accountStore.fetchAccounts()
    loadTransactions()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElNotification.error({ title: '错误', message: error.message || '删除失败', position: 'bottom-right' })
    }
  }
}

onMounted(async () => {
  // 先加载账户数据，确保父账户下拉框有数据
  await accountStore.fetchAccounts()
  // 手动赋值到本地 ref（确保响应式）
  localAccounts.value = accountStore.accounts || []
  
  loadTransactions()
  
  // 监听全局数据刷新事件
  window.addEventListener('data-refresh', loadTransactions)
})
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
  background: white;
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
