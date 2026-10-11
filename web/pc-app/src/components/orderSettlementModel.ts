import type { Order } from '@wealth-hub/shared'

export const orderTypes = ['BUY', 'SUBSCRIPTION', 'SELL', 'REDEMPTION'] as const
export const orderStatuses = ['PENDING', 'CONFIRMED', 'CANCELLED', 'FAILED'] as const
export const isBuyOrder = (type: string) => type === 'BUY' || type === 'SUBSCRIPTION'
export const knownNumber = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value)
export function displayNumber(value: unknown, digits = 2): string {
  return knownNumber(value) ? value.toLocaleString('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits }) : '未知'
}
export function validDate(value: unknown): value is string {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)
    && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value
}
export function localToday(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}
export function orderStageText(order: Pick<Order, 'orderType' | 'status'>): string {
  if (!orderStatuses.includes(order.status) || !orderTypes.includes(order.orderType)) return '状态或方向异常，请重新读取并核对。'
  if (order.status === 'CANCELLED' || order.status === 'FAILED') return '此订单不可结算；资金与份额结果请核对服务器记录。'
  if (order.status === 'CONFIRMED') return '结算结果以服务器记录为准，可查看持仓与结算审计。'
  return isBuyOrder(order.orderType)
    ? '下单已有付款与待结算应收；结算清理应收、形成持仓并处理手续费，不重复扣下单现金。'
    : '下单只登记待处理份额占用；结算后才减少持仓、增加到账现金并处理手续费。'
}
// Malformed locating/status fields never produce an actionable financial row.
export function validateOrders(value: unknown, pendingOnly = false): Order[] {
  if (!Array.isArray(value)) throw new Error('服务器订单列表格式异常')
  const ids = new Set<string>()
  for (const order of value) {
    if (!order || typeof order.orderId !== 'string' || !/^[A-Za-z0-9_-]{1,128}$/.test(order.orderId)
      || ids.has(order.orderId) || !orderTypes.includes(order.orderType) || !orderStatuses.includes(order.status)
      || (pendingOnly && order.status !== 'PENDING') || !Number.isInteger(order.productId) || order.productId <= 0) {
      throw new Error('服务器订单状态或标识异常，请刷新核对')
    }
    ids.add(order.orderId)
  }
  return value
}
export function settlementError(error: unknown, fallback: string): string {
  const e = error as { message?: string; response?: { status?: number; data?: { message?: string; error?: string } } }
  const status = e?.response?.status
  const reason = e?.response?.data?.message || e?.response?.data?.error || e?.message || fallback
  const hint = status === 401 ? '会话失效，请重新登录。' : status === 403 ? '服务器拒绝访问，请核对权限。'
    : status === 409 ? '订单或预览发生冲突，请重新读取并预览。' : ''
  return `${status ? `HTTP ${status} · ` : ''}${reason}${hint ? ` ${hint}` : ''}`
}
