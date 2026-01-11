/**
 * 结算API
 */

import { apiClient } from './client'
import type { Order, ConfirmSettlementRequest } from '../types'

export const settlementApi = {
  /**
   * 获取待结算清单（返回Order列表）
   */
  getPendingSettlements: async (): Promise<Order[]> => {
    const response = await apiClient.get<Order[]>('/settlements/pending')
    return response.data
  },

  /**
   * 确认结算
   */
  confirmSettlement: async (data: ConfirmSettlementRequest): Promise<void> => {
    await apiClient.post('/settlements/confirm', data)
  },
}
