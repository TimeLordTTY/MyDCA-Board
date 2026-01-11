/**
 * 流水API
 */

import { apiClient } from './client'
import type {
  LedgerTxn,
  LedgerTxnDetail,
  LedgerQueryParams,
  CreateTransactionRequest,
  QuickEntryRequest,
  RefundRequest,
  ReimburseRequest,
} from '../types'

export const ledgerApi = {
  /**
   * 获取流水列表
   */
  getTransactions: async (params?: LedgerQueryParams): Promise<LedgerTxn[]> => {
    const response = await apiClient.get<LedgerTxn[]>('/ledger/txns', { params })
    return response.data
  },

  /**
   * 获取流水详情
   */
  getTransactionDetail: async (txnId: string): Promise<LedgerTxnDetail> => {
    const response = await apiClient.get<LedgerTxnDetail>(`/ledger/txns/${txnId}`)
    return response.data
  },

  /**
   * 创建交易流水（统一记账入口）
   */
  createTransaction: async (data: CreateTransactionRequest): Promise<LedgerTxn> => {
    const response = await apiClient.post<LedgerTxn>('/ledger/txns', data)
    return response.data
  },

  /**
   * 快速录入（消费/收入）
   */
  quickEntry: async (data: QuickEntryRequest): Promise<LedgerTxn> => {
    const response = await apiClient.post<LedgerTxn>('/ledger/quick-entry', data)
    return response.data
  },

  /**
   * 创建退款交易
   */
  refund: async (txnId: string, data: RefundRequest): Promise<LedgerTxn> => {
    const response = await apiClient.post<LedgerTxn>(`/ledger/txns/${txnId}/refund`, data)
    return response.data
  },

  /**
   * 创建报销交易
   */
  reimburse: async (txnId: string, data: ReimburseRequest): Promise<LedgerTxn> => {
    const response = await apiClient.post<LedgerTxn>(`/ledger/txns/${txnId}/reimburse`, data)
    return response.data
  },

  /**
   * 撤销流水
   */
  reverseTransaction: async (txnId: string): Promise<LedgerTxn> => {
    const response = await apiClient.post<LedgerTxn>(`/ledger/txns/${txnId}/reverse`)
    return response.data
  },
}
