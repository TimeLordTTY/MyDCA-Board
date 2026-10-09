import { apiClient } from './client'
import type { Goal, GoalConfig, GoalProgress, Budget, BudgetConfig, BudgetComparison } from '../types/goalBudget'
const goalPath = (id: string) => `/goals/${encodeURIComponent(id)}`
const budgetPath = (id: string) => `/monthly-budgets/${encodeURIComponent(id)}`
export const goalApi = {
  list: async (page = 0): Promise<Goal[]> => (await apiClient.get('/goals', { params: { page, size: 20 } })).data,
  detail: async (id: string): Promise<Goal> => (await apiClient.get(goalPath(id))).data,
  create: async (config: GoalConfig): Promise<Goal> => (await apiClient.post('/goals', config)).data,
  edit: async (id: string, config: GoalConfig): Promise<Goal> => (await apiClient.patch(goalPath(id), config)).data,
  progress: async (id: string): Promise<GoalProgress> => (await apiClient.get(`${goalPath(id)}/progress`)).data,
}
export const budgetApi = {
  list: async (page = 0, signal?: AbortSignal): Promise<Budget[]> => (await apiClient.get('/monthly-budgets', { params: { page, size: 20 }, signal })).data,
  detail: async (id: string): Promise<Budget> => (await apiClient.get(budgetPath(id))).data,
  create: async (config: BudgetConfig): Promise<Budget> => (await apiClient.post('/monthly-budgets', config)).data,
  edit: async (id: string, config: BudgetConfig): Promise<Budget> => (await apiClient.patch(budgetPath(id), config)).data,
  comparison: async (id: string, signal?: AbortSignal): Promise<BudgetComparison> => (await apiClient.get(`${budgetPath(id)}/comparison`, { signal })).data,
}
