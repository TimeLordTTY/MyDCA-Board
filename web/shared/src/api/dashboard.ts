/**
 * 看板API
 */

import { apiClient } from './client'
import type { AssetOverview, AssetAllocation, TodayAction, Performance, PendingSettlement } from '../types'

export const dashboardApi = {
  /**
   * 获取资产概览
   */
  getAssetOverview: async (viewType?: 'personal' | 'family'): Promise<AssetOverview> => {
    const response = await apiClient.get<AssetOverview>('/dashboard/asset-overview', {
      params: { viewType },
    })
    return response.data
  },

  /**
   * 获取资产配置
   */
  getAssetAllocation: async (groupBy?: 'assetType' | 'account'): Promise<AssetAllocation> => {
    const response = await apiClient.get<AssetAllocation>('/dashboard/asset-allocation', {
      params: { groupBy },
    })
    return response.data
  },

  /**
   * 获取待结算清单（返回Order列表）
   */
  getPendingSettlements: async (): Promise<any[]> => {
    const response = await apiClient.get<any[]>('/dashboard/pending-settlements')
    return response.data
  },

  /**
   * 获取今日建议清单
   */
  getTodayActions: async (): Promise<TodayAction[]> => {
    const response = await apiClient.get<TodayAction[]>('/dashboard/today-actions')
    return response.data
  },

  /**
   * 获取收益统计
   */
  getPerformance: async (startDate?: string, endDate?: string): Promise<Performance> => {
    const response = await apiClient.get<Performance>('/dashboard/performance', {
      params: { startDate, endDate },
    })
    return response.data
  },
}
