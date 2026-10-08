import { apiClient } from './client'
import type { DataReadiness } from '../types/dataReadiness'

export const dataReadinessApi = {
  diagnose: async (scope: DataReadiness['scope'], month: string): Promise<DataReadiness> =>
    (await apiClient.get('/data-readiness', { params: { scope, month } })).data,
}
