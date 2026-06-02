import { apiClient } from './client'
import type {
  LedgerStatsBreakdown,
  LedgerStatsQuery,
  LedgerStatsSummary,
  LedgerStatsTop,
  LedgerStatsTrend,
} from '../types'

function serializeStatsParams(params?: LedgerStatsQuery) {
  if (!params) return undefined
  return {
    ...params,
    txnTypes: params.txnTypes?.join(','),
    accountIds: params.accountIds?.join(','),
    parentAccountIds: params.parentAccountIds?.join(','),
    categoryIds: params.categoryIds?.join(','),
    productIds: params.productIds?.join(','),
  }
}

export const ledgerStatsApi = {
  getSummary: async (params?: LedgerStatsQuery): Promise<LedgerStatsSummary> => {
    const response = await apiClient.get<LedgerStatsSummary>('/ledger/stats/summary', { params: serializeStatsParams(params) })
    return response.data
  },

  getTrend: async (params?: LedgerStatsQuery): Promise<LedgerStatsTrend[]> => {
    const response = await apiClient.get<LedgerStatsTrend[]>('/ledger/stats/trend', { params: serializeStatsParams(params) })
    return response.data
  },

  getBreakdown: async (params?: LedgerStatsQuery): Promise<LedgerStatsBreakdown[]> => {
    const response = await apiClient.get<LedgerStatsBreakdown[]>('/ledger/stats/breakdown', { params: serializeStatsParams(params) })
    return response.data
  },

  getTop: async (params?: LedgerStatsQuery): Promise<LedgerStatsTop[]> => {
    const response = await apiClient.get<LedgerStatsTop[]>('/ledger/stats/top', { params: serializeStatsParams(params) })
    return response.data
  },
}
