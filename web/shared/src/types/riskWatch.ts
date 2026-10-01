export type RiskType = 'RETURN' | 'DRAWDOWN' | 'ALLOCATION_DEVIATION' | 'STALE' | 'CONCENTRATION' | 'NOTE'
export type RiskSeverity = 'INFO' | 'WARNING' | 'CRITICAL'
export interface RiskConfig {
  scope: 'PERSONAL' | 'FAMILY'; type: RiskType; productId: number | null; assetType: string | null
  threshold: number | null; target: number | null; direction: 'ABOVE' | 'BELOW' | null
  severity: RiskSeverity; note: string; muted: boolean
}
export interface RiskRule { id: string; config: RiskConfig; createdAt: string; disclaimer: string }
export interface RiskSnapshot {
  id: string; ruleId: string; sourceDataTimestamp: string | null; sourceDataHash: string
  status: string; matched: boolean; severity: RiskSeverity; reason: string
  observedValue: number | null; threshold: number | null; createdAt: string; disclaimer: string
}
export interface RiskEvent {
  fingerprint: string; evidence: RiskSnapshot; state: 'OPEN' | 'ACKNOWLEDGED' | 'MUTED' | 'RESOLVED'
  visible: boolean; acknowledgedAt: string | null; resolvedAt: string | null; mutedUntil: string | null
}
