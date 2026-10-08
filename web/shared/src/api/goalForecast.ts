import { apiClient } from './client'
import type { ForecastRequest, Forecast, ScenarioInput, ScenarioComparison } from '../types/goalForecast'
// These POST endpoints carry temporary inputs; backend transactions are read-only.
export const goalForecastApi = {
  forecast: async (id: string, input: ForecastRequest): Promise<Forecast> => (await apiClient.post(`/goals/${encodeURIComponent(id)}/forecast`, input, { timeout: 60000 })).data,
  compare: async (scenarios: ScenarioInput[]): Promise<ScenarioComparison> => (await apiClient.post('/goals/scenarios/compare', { scenarios }, { timeout: 60000 })).data,
}
