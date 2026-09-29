import { apiClient } from './client'
import type { FinanceRadar } from '../types/financeRadar'

export const financeRadarApi = {
  async get(scope: 'PERSONAL' | 'FAMILY' = 'PERSONAL'): Promise<FinanceRadar> {
    const response = await apiClient.get<FinanceRadar>('/finance-radar', { params: { scope } })
    return response.data
  },
}
