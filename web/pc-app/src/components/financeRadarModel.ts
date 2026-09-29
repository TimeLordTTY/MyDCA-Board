import type { FinanceRadar, RadarStatus } from '@wealth-hub/shared'

export type RadarRoute = 'Accounts' | 'Holdings' | 'DraftInbox' | 'Orders' | 'Settlements' | 'Products'
export interface RadarItem {
  id: string
  title: string
  detail: string
  status: RadarStatus
  route: RadarRoute
}

export const statusLabel: Record<RadarStatus, string> = {
  OK: '正常', WARNING: '提醒', BROKEN: '异常', UNKNOWN: '未知',
}

export function radarErrorText(cause: unknown): string {
  const message = cause instanceof Error ? cause.message : ''
  return /401|403|权限|未授权|登录/.test(message)
    ? '当前账号无权查看雷达，或登录已失效。请重新登录后重试。'
    : '雷达读取失败，请检查网络或稍后重试。'
}

const countText = (count: number | null) => count === null ? '未知' : String(count)
const countStatus = (count: number | null, elevated: RadarStatus = 'WARNING'): RadarStatus =>
  count === null ? 'UNKNOWN' : count > 0 ? elevated : 'OK'

export function warningRoute(code: string): RadarRoute {
  if (code.startsWith('MARKET_') || code.startsWith('INDICATOR_')) return 'Products'
  if (code.startsWith('HOLDING_')) return 'Holdings'
  if (code.startsWith('ACCOUNT_') || code.startsWith('LINKED_')) return 'Accounts'
  if (code.startsWith('FAMILY_ORDER_')) return 'Orders'
  return 'DraftInbox'
}

export function radarItems(radar: FinanceRadar): RadarItem[] {
  const { assets, counts } = radar
  const items: RadarItem[] = [
    { id: 'assets', title: '资产摘要', detail: `净资产 ${assets.netWorth === null ? '未知' : `¥${assets.netWorth.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`} · 现金 ${assets.cashBalance === null ? '未知' : `¥${assets.cashBalance.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`} · 持仓 ${assets.positionValue === null ? '未知' : `¥${assets.positionValue.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`}`, status: assets.status, route: 'Accounts' },
    { id: 'drafts', title: '待处理草稿', detail: `${countText(counts.drafts)} 项`, status: countStatus(counts.drafts), route: 'DraftInbox' },
    { id: 'orders', title: '待处理订单', detail: `${countText(counts.pendingOrders)} 项`, status: countStatus(counts.pendingOrders), route: 'Orders' },
    { id: 'settlements', title: '待结算', detail: `${countText(counts.awaitingSettlement)} 项`, status: countStatus(counts.awaitingSettlement), route: 'Settlements' },
    { id: 'reconciliation', title: '结算对账', detail: `异常 ${countText(counts.reconciliationBroken)} · 提醒 ${countText(counts.reconciliationWarning)}`, status: counts.reconciliationBroken === null || counts.reconciliationWarning === null ? 'UNKNOWN' : counts.reconciliationBroken > 0 ? 'BROKEN' : countStatus(counts.reconciliationWarning), route: 'Settlements' },
    { id: 'outbox', title: '设备 Outbox', detail: `${countText(counts.outbox)} 项 · 仅设备本地可查`, status: countStatus(counts.outbox), route: 'DraftInbox' },
  ]
  for (const market of radar.markets) {
    const status = market.status === 'WARNING' || market.status === 'BROKEN' ? market.status : market.indicatorStatus !== 'OK' ? market.indicatorStatus : market.status
    items.push({ id: `market-${market.productId}`, title: `产品 ${market.productId} · 数据时效`, detail: `行情 ${market.priceDate ?? '未知'} · 净值 ${market.valuationDate ?? '未知'} · 指标 ${market.indicatorDate ?? '未知'}`, status, route: 'Products' })
  }
  for (const [index, warning] of radar.warnings.entries()) {
    // Market and Outbox facts already have dedicated cards; retain other owner-facing warnings.
    if (warning.code === 'OUTBOX_UNKNOWN' || warning.code.startsWith('MARKET_') || warning.code.startsWith('INDICATOR_')) continue
    items.push({ id: `warning-${index}`, title: '需要关注', detail: warning.message, status: warning.status, route: warningRoute(warning.code) })
  }
  const priority: Record<RadarStatus, number> = { BROKEN: 0, WARNING: 1, UNKNOWN: 2, OK: 3 }
  return items.sort((a, b) => priority[a.status] - priority[b.status])
}
