/** Authorized diagnostic evidence; READY does not mean financial goals are met. */
export interface ReadinessEvidence {
  area: string
  state: 'READY' | 'PARTIAL' | 'UNAVAILABLE' | 'UNKNOWN'
  reason: string
  source: string
  dataTime: string | null
  nextStep: string
}
export interface DataReadiness {
  scope: 'PERSONAL' | 'FAMILY'
  month: string
  checkedAt: string
  evidence: ReadinessEvidence[]
}
