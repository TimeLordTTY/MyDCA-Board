/**
 * 流水相关类型定义
 * 完全对应ledger_txn和ledger_posting表结构
 */

export interface LedgerTxn {
  id: number
  txnId: string
  userId: number
  familyId?: number
  txnType: string
  bizGroupKey?: string
  productId?: number
  orderId?: string
  relatedTxnId?: string
  relatedOrderId?: string
  relationType: 'NONE' | 'TRANSFER_PAIR' | 'REFUND' | 'REFUND_OF' | 'REIMBURSE' | 'REIMBURSEMENT_OF' | 'REVERSAL' | 'CUSTODY_TRANSFER_OF'
  requestedAt: string
  tradeDate?: string
  navDate?: string
  confirmDate?: string
  fetchDate?: string
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'REVERSED'
  note?: string
  categoryId?: number
  isReimbursable?: boolean
  isReimbursed?: boolean
  isReversed: boolean
  reversedByTxnId?: string
  createdAt: string
  updatedAt: string
}

export interface LedgerPosting {
  id: number
  txnId: string
  postingType: 'DEBIT' | 'CREDIT'
  accountId: number
  accountType: 'CASH' | 'POSITION' | 'FEE' | 'INCOME' | 'EXPENSE' | 'LIABILITY' | 'RECEIVABLE'
  amount: number
  shares?: number
  currency: 'CNY' | 'USD' | 'HKD'
  note?: string
  createdAt: string
}

export interface LedgerTxnDetail extends LedgerTxn {
  postings: LedgerPosting[]
  refundedTotal?: number
  reimbursedTotal?: number
  remaining?: number
}

export interface LedgerQueryParams {
  txnType?: string
  startDate?: string
  endDate?: string
  productId?: number
  accountId?: number
  page?: number
  pageSize?: number
}

export interface LedgerListResponse {
  list: LedgerTxn[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

export interface PaymentLine {
  accountId: number
  amount: number
}

export interface CreateTransactionRequest {
  txnType: string
  postings: Array<{
    postingType: 'DEBIT' | 'CREDIT'
    accountId: number
    accountType: 'CASH' | 'POSITION' | 'FEE' | 'INCOME' | 'EXPENSE' | 'LIABILITY' | 'RECEIVABLE'
    amount: number
    shares?: number
    currency?: 'CNY' | 'USD' | 'HKD'
  }>
  bizGroupKey?: string
  relatedTxnId?: string
  productId?: number
  note?: string
  requestedAt?: string
  categoryId?: number
  isReimbursable?: boolean
}

export interface QuickEntryRequest {
  type: 'EXPENSE' | 'INCOME'
  accountId: number
  amount: number
  paymentLines?: PaymentLine[]
  note?: string
  occurredAt?: string
  categoryId?: number
}

export interface RefundRequest {
  refundAmount: number
  accountId: number
  occurredAt?: string
  note?: string
}

export interface ReimburseRequest {
  reimburseAmount: number
  accountId: number
  occurredAt?: string
  note?: string
}
