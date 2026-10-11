import type { HoldingInfo, MarketQuoteRealtime, Nav } from '@wealth-hub/shared'

export function numberOrNull(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

// Older endpoints returned a Map/object. Preserve account provenance and explicit zeroes.
export function normalizeHoldings(data: unknown): HoldingInfo[] {
  const entries = data instanceof Map ? [...data.entries()] : Array.isArray(data)
    ? data.map(value => [undefined, value]) : data && typeof data === 'object' ? Object.entries(data) : null
  if (!entries) throw new Error('Invalid holdings response')
  return entries.map(([key, value]) => {
    const productId = value?.productId ?? Number(key)
    if (!value || typeof value !== 'object' || !Number.isSafeInteger(productId) || productId <= 0) throw new Error('Invalid holding')
    return { ...value, productId }
  })
}

export function holdingView(holding: HoldingInfo, quote?: MarketQuoteRealtime, nav?: Nav, today = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Shanghai' })) {
  const sharesCalc = numberOrNull(holding.totalShares ?? holding.shares)
  const avgCostCalc = numberOrNull(holding.avgCost ?? holding.averageCost)
  const totalCostCalc = numberOrNull(holding.totalCost ?? holding.cost)
  const validDate = (value: string | undefined) => !!value && /^\d{4}-\d{2}-\d{2}$/.test(value) &&
    Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value && value <= today
  const validNav = nav?.productId === holding.productId && numberOrNull(nav.nav) !== null && nav.nav > 0 && validDate(nav.navDate)
  const quoteDate = quote?.quoteTime?.slice(0, 10)
  const validQuote = quote?.productId === holding.productId && numberOrNull(quote.price) !== null && quote.price > 0 && validDate(quoteDate) && Number.isFinite(Date.parse(quote.quoteTime))
  const cleared = sharesCalc === 0
  // HoldingService values use NAV, not realtime quotes. Never synthesize valuation in the browser.
  const supported = sharesCalc !== null && sharesCalc > 0 && validNav && numberOrNull(holding.marketValue) !== null && holding.marketValue! > 0
  const marketValue = cleared ? numberOrNull(holding.marketValue) : supported ? numberOrNull(holding.marketValue) : null
  const unrealizedPnl = cleared ? numberOrNull(holding.unrealizedPnl) : supported && totalCostCalc !== null ? numberOrNull(holding.unrealizedPnl) : null
  const currentPrice = holding.channel === 'EXCHANGE' ? validQuote ? quote!.price : null : validNav ? nav!.nav : null
  return { ...holding, sharesCalc, avgCostCalc, totalCostCalc, marketValue, unrealizedPnl, currentPrice,
    priceTime: holding.channel === 'EXCHANGE' ? validQuote ? quote!.quoteTime : null : validNav ? nav!.navDate : null,
    priceSource: holding.channel === 'EXCHANGE' ? quote?.source : nav?.source,
    navDate: validNav ? nav!.navDate : null,
    quality: cleared ? '已清仓' : sharesCalc === null ? '份额未知' : supported ? '净值估值' : '估值未知',
    valuationNote: cleared ? '真实零份额；金额按服务器返回展示。' : supported
      ? '市值与浮盈来自服务器净值口径；净值日期为独立读取的证据，后端未返回估值时间。'
      : '缺少有效净值或服务器金额；报价不替代净值估值，默认零金额不作为资产证据。',
  }
}

export type HoldingView = ReturnType<typeof holdingView>

export function filterHoldings(rows: HoldingView[], filters: { keyword: string; broker: string; assetType: string; channel: string; productId: string; showCleared: boolean }) {
  const term = filters.keyword.trim().toLocaleLowerCase()
  return rows.filter(h => (!term || `${h.productName || ''} ${h.productCode || ''}`.toLocaleLowerCase().includes(term)) &&
    (!filters.broker || String(h.brokerAccountId ?? '') === filters.broker) &&
    (!filters.assetType || h.assetType === filters.assetType) && (!filters.channel || h.channel === filters.channel) &&
    (!filters.productId || String(h.productId) === filters.productId) && (filters.showCleared || h.sharesCalc !== 0))
}

export function pnlTone(value: number | null) { return value === null || value === 0 ? 'neutral' : value > 0 ? 'profit' : 'loss' }
