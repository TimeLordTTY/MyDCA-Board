/**
 * 持仓API
 */

import { apiClient } from './client'
import type { HoldingInfo, HoldingDetail, HoldingQueryParams } from '../types'

export interface InitialHoldingImport {
  productCode: string
  productName: string
  channel: 'EXCHANGE' | 'OTC'
  shares: number
  costPrice: number
  note?: string
}

export const holdingApi = {
  /**
   * 获取持仓列表
   */
  getHoldings: async (params?: HoldingQueryParams): Promise<HoldingInfo[]> => {
    const response = await apiClient.get<HoldingInfo[]>('/holdings', { params })
    return response.data
  },

  /**
   * 获取持仓详情
   */
  getHoldingDetail: async (productId: number): Promise<HoldingDetail> => {
    const response = await apiClient.get<HoldingDetail>(`/holdings/${productId}`)
    return response.data
  },

  /**
   * 导入初始持仓
   */
  importInitialHoldings: async (holdings: InitialHoldingImport[]): Promise<void> => {
    await apiClient.post<void>('/holdings/import-initial', holdings)
  },
}
