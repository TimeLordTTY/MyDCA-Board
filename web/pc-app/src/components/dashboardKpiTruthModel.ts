export type Quality = 'loading' | 'complete' | 'partial' | 'unavailable'
export interface Amount { status: Quality; value: number | null }
export interface Snapshot {
  accounts: Record<string, any>[] | null
  overview: Record<string, any> | null
  holdings: Record<string, any>[] | null
  prices: Map<number, unknown>
}
export const finite = (v: unknown): v is number => typeof v === 'number' && Number.isFinite(v)
const unknown = (): Amount => ({ status: 'unavailable', value: null })
const amount = (v: unknown): Amount => finite(v) ? { status: 'complete', value: v } : unknown()
function sum(values: unknown[] | null): Amount {
  if (!values) return unknown()
  const known = values.filter(finite)
  const value = known.reduce((a, b) => a + b, 0)
  if (!finite(value)) return unknown()
  return known.length === values.length ? amount(value) : known.length ? { status: 'partial', value } : unknown()
}
export function kpiTruth(s: Snapshot | null, loading = false) {
  const accounts = s?.accounts ?? null
  const rows = s?.holdings ?? null
  const valuation = (h: Record<string, any>) => {
    const shares = h.totalShares !== undefined ? h.totalShares : h.shares
    const price = s?.prices.get(h.productId)
    if (!finite(shares) || shares < 0) return null
    if (shares === 0) return 0
    return finite(price) && price > 0 ? shares * price : null
  }
  const pnl = (h: Record<string, any>) => {
    const value = valuation(h)
    const shares = h.totalShares !== undefined ? h.totalShares : h.shares
    const cost = h.averageCost !== undefined ? h.averageCost : h.avgCost
    return finite(value) && finite(shares) && finite(cost) ? value - shares * cost : null
  }
  const position = sum(rows?.map(valuation) ?? null)
  // An empty holding response needs corroborating account/overview sources.
  if (rows?.length === 0 && (!accounts || !s?.overview)) Object.assign(position, unknown())
  const liability = amount(s?.overview?.liability)
  const cash = amount(s?.overview?.cashBalance)
  const netWorth = accounts && [cash, position, liability].every(a => a.status === 'complete')
    ? amount(cash.value! + position.value! - liability.value!) : unknown()
  const available = (a: Record<string, any>) => finite(a.balance) && finite(a.reservedAmount) ? a.balance - a.reservedAmount : null
  const result = {
    netWorth, position, liability,
    available: sum(accounts?.map(available) ?? null),
    reserved: sum(accounts?.map(a => a.reservedAmount) ?? null),
    spendable: sum(accounts?.filter(a => a.fundUsage === 'SPENDABLE').map(available) ?? null),
    totalPnl: sum(rows?.map(pnl) ?? null),
    exchangePnl: sum(rows?.map(h => h.channel === 'EXCHANGE' ? pnl(h) : h.channel === 'OTC' ? 0 : null) ?? null),
    otcPnl: sum(rows?.map(h => h.channel === 'OTC' ? pnl(h) : h.channel === 'EXCHANGE' ? 0 : null) ?? null),
    todayPnl: amount(s?.overview?.todayPnl), monthInflow: amount(s?.overview?.monthInflow),
  }
  if (rows?.length === 0 && position.status !== 'complete') {
    result.totalPnl = unknown(); result.exchangePnl = unknown(); result.otcPnl = unknown()
  }
  if (loading) for (const key of Object.keys(result) as (keyof typeof result)[]) result[key] = { status: 'loading', value: null }
  return result
}
export function amountText(a: Amount, format: (v: number) => string) {
  if (a.status === 'loading') return '读取中 · 未知'
  if (a.value === null) return '未知 · 来源未完整取得'
  return `${a.status === 'partial' ? '已知部分 ' : ''}${format(a.value)} · ${a.status === 'complete' ? '输入完整' : '部分已知'}`
}
export interface KpiState { loading: boolean; snapshot: Snapshot | null; readAt: string | null }
export function createKpiLoader(read: (valid: () => boolean) => Promise<Snapshot>, identity: () => string, publish: (s: KpiState) => void) {
  let generation = 0, active = true
  const reset = () => { generation++; publish({ loading: false, snapshot: null, readAt: null }) }
  return {
    reset,
    suspend() { active = false; reset() },
    activate() { active = true },
    async load() {
      if (!active) return
      const id = ++generation, owner = identity()
      const valid = () => active && id === generation && owner === identity()
      publish({ loading: true, snapshot: null, readAt: null })
      try {
        const snapshot = await read(valid)
        if (valid()) publish({ loading: false, snapshot, readAt: new Date().toLocaleString('zh-CN') })
      } catch {
        if (valid()) publish({ loading: false, snapshot: null, readAt: null })
      } finally {
        // The shared HTTP client can clear storage on 403 without updating Pinia.
        if (active && id === generation && owner !== identity()) reset()
      }
    },
  }
}
