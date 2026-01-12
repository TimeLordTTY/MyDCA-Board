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
   * 注意：后端API目前只返回LedgerTxn，不包含postings
   * 需要前端单独获取postings或修改后端API
   */
  getTransactionDetail: async (txnId: string): Promise<LedgerTxnDetail> => {
    const response = await apiClient.get<LedgerTxnDetail>(`/ledger/txns/${txnId}`)
    // 如果后端返回了postings，直接使用；否则返回空数组
    return {
      ...response.data,
      postings: response.data.postings || [],
    } as LedgerTxnDetail
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

  /**
   * 创建转托管交易
   */
  createCustodyTransfer: async (data: {
    productId: number
    shares: number
    transferOutPrice: number
    transferInPrice: number
    transferDate: string
    note?: string
  }): Promise<LedgerTxn> => {
    const response = await apiClient.post<LedgerTxn>('/ledger/txns/custody-transfer', data)
    return response.data
  },
}
