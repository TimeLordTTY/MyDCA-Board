/**
 * 账户API
 */

import { apiClient } from './client'
import type { Account, AccountQueryParams, AdjustBalanceRequest } from '../types'

/**
 * MMF 子账户份额详情
 */
export interface ChildAccountShares {
  accountId: number
  accountName: string
  isFixedAmount: boolean
  amount: number
  shares: number
}

/**
 * MMF 平台份额分配详情
 */
export interface MmfSharesDetail {
  platformId: number
  platformName: string
  productId: number
  nav: number
  totalShares: number
  totalAmount: number
  allocatedAmount: number
  unallocatedAmount: number
  childAccounts: ChildAccountShares[]
}

export const accountApi = {
  /**
   * 获取账户列表（树形结构）
   */
  getAccounts: async (params?: AccountQueryParams): Promise<Account[]> => {
    const response = await apiClient.get<Account[]>('/accounts', { params })
    return response.data
  },

  /**
   * 获取账户详情
   */
  getAccount: async (id: number): Promise<Account> => {
    const response = await apiClient.get<Account>(`/accounts/${id}`)
    return response.data
  },

  /**
   * 创建账户
   */
  createAccount: async (data: Partial<Account>): Promise<Account> => {
    const response = await apiClient.post<Account>('/accounts', data)
    return response.data
  },

  /**
   * 更新账户
   */
  updateAccount: async (id: number, data: Partial<Account>): Promise<Account> => {
    const response = await apiClient.put<Account>(`/accounts/${id}`, data)
    return response.data
  },

  /**
   * 调整账户余额（生成ADJUST流水）
   */
  adjustBalance: async (id: number, data: AdjustBalanceRequest): Promise<Account> => {
    const response = await apiClient.put<Account>(`/accounts/${id}/balance`, data)
    return response.data
  },

  /**
   * 获取 MMF 平台的份额分配详情
   */
  getMmfSharesDetail: async (platformId: number): Promise<MmfSharesDetail | null> => {
    try {
      const response = await apiClient.get<MmfSharesDetail>(`/accounts/${platformId}/mmf-shares`)
      return response.data
    } catch (error: any) {
      if (error.response?.status === 404) {
        return null
      }
      throw error
    }
  },
}
