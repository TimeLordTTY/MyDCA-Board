import type { BacktestResearchThresholds } from './backtest'

export interface ResearchPlan {
  id: string
  name: string
  description: string | null
  status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED'
  sourceCandidateId: string
  sourceRunIds: string[]
  strategy: string
  strategyVersion: string
  canonicalParamsSnapshot: Record<string, unknown>
  paramsDraft: Record<string, unknown>
  datasetHashes: string[]
  evidenceSnapshot: Record<string, unknown>
  evidenceBundleRef: string
  warnings: string[]
  createdAt: string
  updatedAt: string
}
export interface CreateResearchPlan {
  name: string
  description: string
  runIds: string[]
  candidateId: string
  thresholds: BacktestResearchThresholds
}
