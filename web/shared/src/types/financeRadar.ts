export type RadarStatus = 'OK' | 'WARNING' | 'BROKEN' | 'UNKNOWN'

export interface FinanceRadar {
  date: string
  scope: 'PERSONAL' | 'FAMILY'
  assets: {
    status: RadarStatus
    cashBalance: number | null
    investmentCost: number | null
    positionValue: number | null
    liabilities: number | null
    totalAssets: number | null
    netWorth: number | null
  }
  counts: {
    drafts: number
    outbox: number | null
    pendingOrders: number | null
    awaitingSettlement: number | null
    reconciliationWarning: number | null
    reconciliationBroken: number | null
  }
  markets: Array<{
    productId: number
    status: RadarStatus
    priceDate: string | null
    valuationDate: string | null
    indicatorStatus: RadarStatus
    indicatorDate: string | null
  }>
  warnings: Array<{ code: string; status: RadarStatus; message: string }>
}
