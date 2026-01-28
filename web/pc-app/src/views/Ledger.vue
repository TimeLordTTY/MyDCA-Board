<template>
  <div>
    <!-- 记一笔模态框 -->
    <UnifiedEntryModal
      v-model="unifiedEntryVisible"
      @success="handleEntrySuccess"
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
        <div>
          <button class="btn primary" @click="handleUnifiedEntry">
            📝 记一笔
          </button>
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
        <el-select 
          v-model="filters.parentAccountId" 
          placeholder="父账户" 
          style="width: 150px" 
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
          style="width: 150px" 
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
        <el-button @click="handleFilterChange">搜索</el-button>
      </div>

      <!-- 流水列表 -->
      <div class="ledger-table-container hide-scrollbar">
        <table class="ledger-table">
          <thead>
            <tr>
              <th>时间</th>
              <th class="right">金额</th>
              <th>分类</th>
              <th>叶子账户</th>
              <th class="right">叶子账户余额</th>
              <th class="right">父账户余额</th>
              <th>备注</th>
              <th class="right">操作</th>
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
              <td class="mono">{{ formatDateTime(txn.requestedAt) }}</td>
              <td class="right mono" :class="getAmountClass(txn.txnType)">
                {{ formatAmountWithSign(txn.txnType, (txn as any).summaryAmount || 0) }}
              </td>
              <td>{{ getCategoryDisplayText(txn) }}</td>
              <td>{{ (txn as any).leafAccountName || '—' }}</td>
              <td class="right mono">{{ formatCurrency((txn as any).leafAccountBalance || 0) }}</td>
              <td class="right mono">{{ formatCurrency((txn as any).parentAccountBalance || 0) }}</td>
              <td class="td-muted">{{ txn.note || '' }}</td>
              <td class="right">
                <div style="display: flex; align-items: center; gap: 8px;">
                  <div style="display: flex; gap: 8px;">
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
                  </div>
                  <button class="btn-small" style="margin-left: auto;" @click="handleViewDetail(txn)">详情</button>
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
import { ElMessage, ElPagination } from 'element-plus'
import { ledgerApi, formatDateTime, formatCurrency, getTxnTypeLabel, txnTypeMap, useAccountStore, expenseCategories, incomeCategories, findCategoryById } from '@wealth-hub/shared'
import type { LedgerTxn, LedgerTxnDetail, LedgerPosting } from '@wealth-hub/shared'
import UnifiedEntryModal from '../components/UnifiedEntryModal.vue'
import RefundModal from '../components/RefundModal.vue'
import ReimburseModal from '../components/ReimburseModal.vue'

const accountStore = useAccountStore()

const loading = ref(false)
const transactions = ref<LedgerTxn[]>([])
const detailVisible = ref(false)
const selectedTxn = ref<LedgerTxnDetail | null>(null)
const postings = ref<LedgerPosting[]>([])
const refundVisible = ref(false)
const reimburseVisible = ref(false)
const selectedExpenseTxn = ref<LedgerTxn | null>(null)
const unifiedEntryVisible = ref(false)

const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0,
})

const filters = reactive({
  dateRange: [] as Date[],
  parentAccountId: undefined as number | undefined,
  accountId: undefined as number | undefined,
  startDate: '',
  endDate: '',
})

// 父账户列表（有子账户的账户）
const parentAccounts = computed(() => {
  const parentList: any[] = []
  
  function traverse(accounts: any[]) {
    accounts.forEach(acc => {
      // 如果有children且children不为空，说明是父账户
      if (acc.children && acc.children.length > 0 && acc.accountKind === 'REAL') {
        parentList.push(acc)
        // 递归处理子账户（因为子账户可能也是父账户）
        traverse(acc.children)
      }
    })
  }
  
  traverse(accountStore.accountTree)
  return parentList
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
    filters.startDate = filters.dateRange[0].toISOString().split('T')[0]
    filters.endDate = filters.dateRange[1].toISOString().split('T')[0]
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
      accountId: filters.accountId || undefined,
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
    ElMessage.error(error.message || '加载失败')
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
    ElMessage.error(error.message || '加载详情失败')
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
 */
function formatAmountWithSign(txnType: string, amount: number): string {
  if (amount === 0) {
    return formatCurrency(0)
  }
  
  let sign = ''
  switch (txnType) {
    case 'INCOME':
    case 'REIMBURSE_IN':
    case 'REDEMPTION_IN':  // 赎回入金    case 'BUY':
    case 'SUBSCRIPTION':
    case 'TRANSFER_IN':
    case 'DIVIDEND_CASH':
    case 'DIVIDEND_REINVEST':
    case 'BOND_REPO':
      sign = '+' // 收入、买入、申购、转入、分红、逆回购：正号
      break
    case 'EXPENSE':
    case 'REIMBURSE_OUT':
    case 'SELL':
    case 'REDEMPTION':
    case 'REDEMPTION_OUT':  // 赎回出金
    case 'TRANSFER_OUT':
    case 'FEE':
    case 'TAX':
      sign = '-' // 支出、卖出、赎回、转出、费用：负号
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
    ElMessage.error(error.message || '加载失败')
  }
}

async function handleReimburse(txn: LedgerTxn) {
  try {
    // 获取完整的交易详情（包含postings）
    const detail = await ledgerApi.getTransactionDetail(txn.txnId)
    selectedExpenseTxn.value = detail as any
    reimburseVisible.value = true
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
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

onMounted(() => {
  accountStore.fetchAccounts()
  loadTransactions()
})
</script>

<style scoped>
/* 流水表格容器 - 支持隐藏滚动条 */
.ledger-table-container {
  overflow-y: auto;
  overflow-x: auto;
  max-height: calc(100vh - 400px); /* 根据实际布局调整 */
  min-height: 300px;
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

/* 备注列允许换行 */
.ledger-table tbody td:nth-child(7) {
  white-space: normal;
  max-width: 200px;
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
