import { apiClient } from './client'
import type { ResearchPlan, CreateResearchPlan } from '../types/researchPlan'

const path = (id: string) => `/research-plans/${encodeURIComponent(id)}`
export const researchPlanApi = {
  list: async (page = 0, size = 20): Promise<ResearchPlan[]> =>
    (await apiClient.get<ResearchPlan[]>('/research-plans', { params: { page, size } })).data,
  detail: async (id: string): Promise<ResearchPlan> =>
    (await apiClient.get<ResearchPlan>(path(id))).data,
  create: async (request: CreateResearchPlan): Promise<ResearchPlan> =>
    (await apiClient.post<ResearchPlan>('/research-plans', request)).data,
  edit: async (id: string, request: Partial<Pick<ResearchPlan, 'name' | 'description' | 'paramsDraft' | 'status'>>): Promise<ResearchPlan> =>
    (await apiClient.patch<ResearchPlan>(path(id), request)).data,
  run: async (id: string, dataset: string): Promise<{ history_run_id: string }> =>
    (await apiClient.post<{ history_run_id: string }>(`${path(id)}/runs`, { dataset }, { timeout: 120000 })).data,
}
