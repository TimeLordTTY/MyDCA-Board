/**
 * 账户API
 */

import { apiClient } from './client'
import type { Account, AccountQueryParams, AdjustBalanceRequest } from '../types'

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
}
