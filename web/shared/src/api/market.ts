import { apiClient } from './client'
import type { MarketBarDaily, MarketQuoteRealtime } from '../types'

/**
 * 行情数据API
 */
export const marketApi = {
  /**
   * 获取历史行情（日K线）
   */
  getHistoryBars: async (
    productId: number,
    startDate?: string,
    endDate?: string
  ): Promise<MarketBarDaily[]> => {
    const params = new URLSearchParams()
    params.append('productId', productId.toString())
    if (startDate) params.append('startDate', startDate)
    if (endDate) params.append('endDate', endDate)

    const response = await apiClient.get<MarketBarDaily[]>(`/market/bars?${params.toString()}`)
    return response.data
  },

  /**
   * 获取最新日K线
   */
  getLatestBar: async (productId: number): Promise<MarketBarDaily | null> => {
    try {
      const response = await apiClient.get<MarketBarDaily>(`/market/bars/latest?productId=${productId}`)
      return response.data
    } catch (error: any) {
      if (error.response?.status === 404) {
        return null
      }
      throw error
    }
  },

  /**
   * 获取实时行情（批量）
   */
  getRealtimeQuotes: async (productIds: number[]): Promise<MarketQuoteRealtime[]> => {
    const params = productIds.map(id => `productIds=${id}`).join('&')
    const response = await apiClient.get<MarketQuoteRealtime[]>(`/market/quotes?${params}`)
    return response.data
  },

  /**
   * 获取单个产品的最新实时行情
   */
  getLatestQuote: async (productId: number): Promise<MarketQuoteRealtime | null> => {
    try {
      const response = await apiClient.get<MarketQuoteRealtime>(`/market/quotes/latest?productId=${productId}`)
      return response.data
    } catch (error: any) {
      if (error.response?.status === 404) {
        return null
      }
      throw error
    }
  },
}
