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

export interface BacktestCompareRun {
  run_id: string
  dataset_hash: string
  strategy: string
  strategy_version: string
  engine_version: string
  canonical_params: Record<string, number>
  data_range: { start: string; end: string }
  metrics: Record<string, number | null>
  baseline_delta: Record<string, number | null>
}

export interface BacktestCompareReport {
  schema_version: string
  generated_at: string
  disclaimer: string
  warnings: string[]
  runs: BacktestCompareRun[]
  markdown: string
}
