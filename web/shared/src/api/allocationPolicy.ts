import { apiClient } from './client'
import type { AllocationConfig, AllocationPolicy, AllocationEvaluation, RebalancePreview } from '../types/allocationPolicy'
const base = '/allocation-policies'
const path = (id: string) => `${base}/${encodeURIComponent(id)}`
export const allocationPolicyApi = {
  list: async (page = 0): Promise<AllocationPolicy[]> => (await apiClient.get(base, { params: { page, size: 50 } })).data,
  create: async (config: AllocationConfig): Promise<AllocationPolicy> => (await apiClient.post(base, config)).data,
  edit: async (id: string, config: AllocationConfig): Promise<AllocationPolicy> => (await apiClient.patch(path(id), config)).data,
  evaluate: async (id: string): Promise<AllocationEvaluation> => (await apiClient.post(`${path(id)}/evaluate`)).data,
  preview: async (id: string): Promise<RebalancePreview> => (await apiClient.get(`${path(id)}/preview`)).data,
}
