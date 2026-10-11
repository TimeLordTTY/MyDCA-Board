import { finite, kpiTruth } from './dashboardKpiTruthModel'
import type { Amount, Snapshot } from './dashboardKpiTruthModel'

export function assetStructure(snapshot: Snapshot | null, loading = false): { label: string; amount: Amount }[] {
  const truth = kpiTruth(snapshot, loading)
  const cash = snapshot?.overview?.cashBalance
  return [
    { label: '现金', amount: loading ? { status: 'loading', value: null } : finite(cash) ? { status: 'complete', value: cash } : { status: 'unavailable', value: null } },
    { label: '持仓市值', amount: truth.position },
    { label: '负债（单列）', amount: truth.liability },
  ]
}

export function homeHoldings(snapshot: Snapshot | null, loading = false) {
  if (loading || !snapshot?.holdings) return []
  return snapshot.holdings.map(h => {
    const shares = h.totalShares !== undefined ? h.totalShares : h.shares
    const cost = h.averageCost !== undefined ? h.averageCost : h.avgCost
    const quotedPrice = snapshot.prices.get(h.productId)
    const price = finite(quotedPrice) && quotedPrice > 0 ? quotedPrice : null
    const validShares = finite(shares) && shares >= 0 ? shares : null
    const value = validShares === 0 ? 0 : validShares !== null && price !== null ? validShares * price : null
    const totalCost = validShares !== null && finite(cost) ? validShares * cost : null
    const pnl = finite(value) && finite(totalCost) ? value - totalCost : null
    return { ...h, productId: h.productId, productName: h.productName, productCode: h.productCode,
      sharesCalc: validShares, avgCostCalc: finite(cost) ? cost : null, currentPrice: price,
      marketValue: finite(value) ? value : null, totalCostCalc: finite(totalCost) ? totalCost : null, unrealizedPnl: finite(pnl) ? pnl : null }
  }).sort((a, b) => {
    if (a.unrealizedPnl === null) return b.unrealizedPnl === null ? 0 : -1
    if (b.unrealizedPnl === null) return 1
    return Math.abs(b.unrealizedPnl) - Math.abs(a.unrealizedPnl)
  }).slice(0, 5)
}
