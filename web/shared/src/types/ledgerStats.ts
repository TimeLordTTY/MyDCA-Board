export type LedgerStatsScope = 'PERSONAL' | 'FAMILY'
export type LedgerStatsPeriod = 'DAY' | 'WEEK' | 'MONTH'
export type LedgerStatsGroupBy = 'CATEGORY' | 'CATEGORY_L1' | 'CATEGORY_L2' | 'TXN_TYPE' | 'ACCOUNT' | 'PARENT_ACCOUNT'

export interface LedgerStatsQuery {
  startDate?: string
  endDate?: string
  txnTypes?: string[]
  accountIds?: number[]
  parentAccountIds?: number[]
  categoryIds?: number[]
  categoryL1?: string
  categoryL2?: string
  productIds?: number[]
  includeTransfer?: boolean
  scope?: LedgerStatsScope
  period?: LedgerStatsPeriod
  groupBy?: LedgerStatsGroupBy
  limit?: number
}

export interface LedgerStatsSummary {
  totalIncome: number
  totalExpense: number
  netCashflow: number
  investmentInflow: number
  investmentOutflow: number
  transferAmount: number
  reimbursableExpense: number
  reimbursedAmount: number
  avgDailyExpense: number
  maxExpenseAmount: number
  txnCount: number
  expenseTxnCount: number
  incomeTxnCount: number
}

export interface LedgerStatsTrend {
  period: string
  income: number
  expense: number
  netCashflow: number
  investmentInflow: number
  investmentOutflow: number
  txnCount: number
}

export interface LedgerStatsBreakdown {
  key: string
  name: string
  groupBy: string
  amount: number
  income: number
  expense: number
  investmentInflow: number
  investmentOutflow: number
  percentage: number
  txnCount: number
}

export interface LedgerStatsTop {
  txnId: string
  txnType: string
  tradeDate?: string
  note?: string
  categoryId?: number
  accountId?: number
  accountName?: string
  amount: number
}
