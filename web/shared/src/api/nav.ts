import { apiClient } from './client'
import type { Nav } from '../types'

/**
 * 净值数据API
 */
export const navApi = {
  /**
   * 获取历史净值
   */
  getHistoryNav: async (
    productId: number,
    startDate?: string,
    endDate?: string
  ): Promise<Nav[]> => {
    const params = new URLSearchParams()
    params.append('productId', productId.toString())
    if (startDate) params.append('startDate', startDate)
    if (endDate) params.append('endDate', endDate)

    const response = await apiClient.get<Nav[]>(`/nav/history?${params.toString()}`)
    return response.data
  },

  /**
   * 获取最新净值
   */
  getLatestNav: async (productId: number): Promise<Nav | null> => {
    try {
      const response = await apiClient.get<Nav>(`/nav/latest?productId=${productId}`)
      return response.data
    } catch (error: any) {
      if (error.response?.status === 404) {
        return null
      }
      throw error
    }
  },

  /**
   * 获取指定日期的净值
   */
  getNavByDate: async (productId: number, navDate: string): Promise<Nav | null> => {
    try {
      const response = await apiClient.get<Nav>(`/nav/by-date?productId=${productId}&navDate=${navDate}`)
      return response.data
    } catch (error: any) {
      if (error.response?.status === 404) {
        return null
      }
      throw error
    }
  },
}
