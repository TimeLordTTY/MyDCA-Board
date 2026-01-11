/**
 * 账户相关类型定义
 * 完全对应accounts表结构
 */

export interface Account {
  id: number
  accountCode: string
  accountName: string
  accountKind: 'REAL' | 'VIRTUAL'
  accountType: 'BANK' | 'PAYMENT' | 'BROKER' | 'MMF' | 'CASH' | 'CREDIT_CARD' | 'HUABEI' | 'BAITIAO' | 'LOAN' | 'OTHER'
  accountSubtype?: string
  virtualSubtype?: 'POSITION' | 'FEE' | 'INCOME' | 'EXPENSE' | 'RECEIVABLE' | 'LIABILITY'
  ownerType: 'PERSONAL' | 'FAMILY'
  ownerUserId?: number
  ownerFamilyId?: number
  currency: 'CNY' | 'USD' | 'HKD'
  parentAccountId?: number
  fundUsage?: 'SPENDABLE' | 'RESERVED' | 'INVESTABLE'
  balance: number
  reservedAmount: number
  initialBalance: number
  isActive: boolean
  note?: string
  createdAt: string
  updatedAt: string
  // 树形结构字段
  children?: Account[]
}

export interface AccountQueryParams {
  ownerType?: 'PERSONAL' | 'FAMILY'
}

export interface AdjustBalanceRequest {
  balance: number
  note?: string
}
