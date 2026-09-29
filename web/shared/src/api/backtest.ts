import { apiClient } from './client'
import type { BacktestRun } from '../types/backtest'

export const backtestApi = {
  history: async (page = 0, size = 20): Promise<BacktestRun[]> =>
    (await apiClient.get<BacktestRun[]>('/backtest-lab/runs', { params: { page, size } })).data,
  detail: async (historyRunId: string): Promise<BacktestRun> =>
    (await apiClient.get<BacktestRun>(`/backtest-lab/runs/${encodeURIComponent(historyRunId)}`)).data,
}
