export interface BacktestRun {
  historyRunId: string
  ownerUserId: number
  ownerFamilyId: number | null
  dataset: string | null
  datasetHash: string | null
  strategy: string | null
  strategyVersion: string | null
  canonicalParams: string | null
  paramsHash: string | null
  engineVersion: string | null
  startedAt: string
  finishedAt: string
  status: 'SUCCESS' | 'FAILED' | 'TIMEOUT' | 'INVALID'
  cacheHit: boolean
  failureCode: string | null
  metrics: Record<string, number | null> | null
  result: Record<string, unknown> | null
}
