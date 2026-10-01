export interface BacktestRun {
  researchPlanId?: string | null
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

export interface BacktestResearchThresholds {
  minSampleDays: number
  maxDrawdown: number
  minBaselineAnnualizedDelta: number
  minTradeCount: number
}

export interface BacktestResearchReport {
  schema_version: string
  disclaimer: string
  thresholds: { min_sample_days: number; max_drawdown: number; min_baseline_annualized_delta: number; min_trade_count: number }
  excluded_runs: { run_id: string; status: string; reason: string }[]
  candidates: {
    candidate_id: string
    strategy: string
    strategy_version: string
    canonical_params: Record<string, number>
    run_ids: string[]
    dataset_hashes: string[]
    evidence_status: 'CONSISTENT' | 'INSUFFICIENT'
    status: 'WORTH_FURTHER_RESEARCH' | 'EVIDENCE_INSUFFICIENT' | 'DOES_NOT_MEET_CRITERIA'
    reasons: string[]
    warnings: string[]
    evidence: { run_id: string; data_range: { start: string; end: string }; sample_days: number; metrics: Record<string, number | null>; baseline_delta: Record<string, number | null> }[]
  }[]
}
