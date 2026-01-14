/**
 * 产品API
 */

import { apiClient } from './client'
import type { ProductMaster, ProductQueryParams } from '../types'

export interface FundSellFeeTier {
  id?: number
  productId?: number
  minDays: number
  maxDays: number | null
  sellFeeRate: number
  sortOrder: number
  isActive: boolean
  note?: string
}

export const productApi = {
  /**
   * 获取产品列表
   */
  getProducts: async (params?: ProductQueryParams): Promise<ProductMaster[]> => {
    const response = await apiClient.get<ProductMaster[]>('/products', { params })
    return response.data
  },

  /**
   * 获取产品详情
   */
  getProduct: async (id: number): Promise<ProductMaster> => {
    const response = await apiClient.get<ProductMaster>(`/products/${id}`)
    return response.data
  },

  /**
   * 创建产品
   */
  createProduct: async (data: Partial<ProductMaster>): Promise<ProductMaster> => {
    const response = await apiClient.post<ProductMaster>('/products', data)
    return response.data
  },

  /**
   * 更新产品
   */
  updateProduct: async (id: number, data: Partial<ProductMaster>): Promise<ProductMaster> => {
    const response = await apiClient.put<ProductMaster>(`/products/${id}`, data)
    return response.data
  },

  /**
   * 批量更新产品排序
   */
  updateProductSortOrder: async (updates: Array<{ id: number; sortOrder: number }>): Promise<void> => {
    await apiClient.post<void>('/products/sort-order', updates)
  },

  /**
   * 获取产品的卖出费率分段
   */
  getSellFeeTiers: async (productId: number): Promise<FundSellFeeTier[]> => {
    const response = await apiClient.get<FundSellFeeTier[]>(`/products/${productId}/sell-fee-tiers`)
    return response.data
  },

  /**
   * 保存产品的卖出费率分段
   */
  saveSellFeeTiers: async (productId: number, tiers: FundSellFeeTier[]): Promise<void> => {
    await apiClient.post(`/products/${productId}/sell-fee-tiers`, tiers)
  },
}
