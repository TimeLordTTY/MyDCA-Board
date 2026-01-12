import { apiClient } from './client'
import type { IndicatorDaily } from '../types'

/**
 * 指标数据API
 */
export const indicatorApi = {
  /**
   * 获取历史指标数据
   */
  getHistoryIndicators: async (
    productId: number,
    startDate?: string,
    endDate?: string,
    windowDays: number = 20
  ): Promise<IndicatorDaily[]> => {
    const params = new URLSearchParams()
    params.append('productId', productId.toString())
    params.append('windowDays', windowDays.toString())
    if (startDate) params.append('startDate', startDate)
    if (endDate) params.append('endDate', endDate)

    const response = await apiClient.get<IndicatorDaily[]>(`/indicators/history?${params.toString()}`)
    return response.data
  },

  /**
   * 获取最新指标数据
   */
  getLatestIndicator: async (productId: number, windowDays: number = 20): Promise<IndicatorDaily | null> => {
    try {
      const response = await apiClient.get<IndicatorDaily>(`/indicators/latest?productId=${productId}&windowDays=${windowDays}`)
      return response.data
    } catch (error: any) {
      if (error.response?.status === 404) {
        return null
      }
      throw error
    }
  },
}
