/**
 * 券商费率配置API
 */

import { apiClient } from './client'

export interface BrokerFeeConfig {
  id?: number
  accountId: number
  feeRuleType: 'STOCK' | 'ETF' | 'LOF' | 'LOF_SUBSCRIPTION' | 'CONVERTIBLE_BOND_SH' | 'CONVERTIBLE_BOND_SZ' | 'BOND_REPO' | 'FUND_OTC' | 'DEFAULT'
  buyFeeRate: number
  sellFeeRate: number
  buyMinFee: number
  sellMinFee: number
  subscriptionDiscountRate?: number
  isActive: boolean
  note?: string
  createdAt?: string
  updatedAt?: string
}

export const brokerFeeApi = {
  /**
   * 获取券商账户的所有费率配置
   */
  getFeeConfigs: async (accountId: number): Promise<BrokerFeeConfig[]> => {
    const response = await apiClient.get<BrokerFeeConfig[]>(`/accounts/${accountId}/broker-fee-configs`)
    return response.data
  },

  /**
   * 获取单个费率配置
   */
  getFeeConfig: async (accountId: number, id: number): Promise<BrokerFeeConfig> => {
    const response = await apiClient.get<BrokerFeeConfig>(`/accounts/${accountId}/broker-fee-configs/${id}`)
    return response.data
  },

  /**
   * 创建费率配置
   */
  createFeeConfig: async (accountId: number, data: Partial<BrokerFeeConfig>): Promise<BrokerFeeConfig> => {
    const response = await apiClient.post<BrokerFeeConfig>(`/accounts/${accountId}/broker-fee-configs`, data)
    return response.data
  },

  /**
   * 更新费率配置
   */
  updateFeeConfig: async (accountId: number, id: number, data: Partial<BrokerFeeConfig>): Promise<BrokerFeeConfig> => {
    const response = await apiClient.put<BrokerFeeConfig>(`/accounts/${accountId}/broker-fee-configs/${id}`, data)
    return response.data
  },

  /**
   * 删除费率配置
   */
  deleteFeeConfig: async (accountId: number, id: number): Promise<void> => {
    await apiClient.delete(`/accounts/${accountId}/broker-fee-configs/${id}`)
  },
}
