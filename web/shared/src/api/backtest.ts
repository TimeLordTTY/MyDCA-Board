import { apiClient } from './client'
import type { BacktestRun, BacktestCompareReport, BacktestResearchReport, BacktestResearchThresholds } from '../types/backtest'

export const backtestApi = {
  history: async (page = 0, size = 20): Promise<BacktestRun[]> =>
    (await apiClient.get<BacktestRun[]>('/backtest-lab/runs', { params: { page, size } })).data,
  detail: async (historyRunId: string): Promise<BacktestRun> =>
    (await apiClient.get<BacktestRun>(`/backtest-lab/runs/${encodeURIComponent(historyRunId)}`)).data,
  compare: async (runIds: string[]): Promise<BacktestCompareReport> =>
    (await apiClient.post<BacktestCompareReport>('/backtest-lab/runs/compare', { runIds })).data,
  research: async (runIds: string[], thresholds: BacktestResearchThresholds): Promise<BacktestResearchReport> =>
    (await apiClient.post<BacktestResearchReport>('/backtest-lab/runs/research', { runIds, thresholds })).data,
  evidence: async (runIds: string[], thresholds: BacktestResearchThresholds): Promise<Blob> =>
    (await apiClient.post<Blob>('/backtest-lab/runs/evidence', { runIds, thresholds }, { responseType: 'blob', timeout: 15000 })).data,
}
