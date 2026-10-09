export type PendingSettlementRow = {
  orderId: string
  orderType: string
  productName: string | null
  amount: number | null
  currency: string | null
  expectedConfirmDate: string | null
  incomplete: boolean
}
export type PendingSettlementState = {
  status: 'idle' | 'loading' | 'success' | 'error'
  data: PendingSettlementRow[] | null
}

// Reject invalid locating fields; optional display data never becomes financial zero.
export function validatePendingSettlements(value: unknown): PendingSettlementRow[] {
  if (!Array.isArray(value)) throw new Error('invalid settlement list')
  const ids = new Set<string>()
  return value.map(order => {
    if (!order || typeof order.orderId !== 'string'
      || !/^[A-Za-z0-9_-]{1,128}$/.test(order.orderId) || ids.has(order.orderId)
      || !['BUY', 'SELL', 'SUBSCRIPTION', 'REDEMPTION'].includes(order.orderType)
      || order.status !== 'PENDING') throw new Error('invalid settlement record')
    ids.add(order.orderId)
    const productName = typeof order.productName === 'string' && order.productName.trim() ? order.productName : null
    const amount = typeof order.amount === 'number' && Number.isFinite(order.amount) ? order.amount : null
    const currency = ['CNY', 'USD', 'HKD'].includes(order.currency) ? order.currency as string : null
    const date = order.expectedConfirmDate
    const expectedConfirmDate = typeof date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(date)
      && Number.isFinite(Date.parse(date)) && new Date(date).toISOString().slice(0, 10) === date ? date : null
    return { orderId: order.orderId, orderType: order.orderType, productName, amount, currency,
      expectedConfirmDate, incomplete: productName === null || amount === null || currency === null || expectedConfirmDate === null }
  })
}

export function createPendingSettlementLoader(read: () => Promise<unknown>, identity: () => string,
  publish: (state: PendingSettlementState) => void) {
  let generation = 0
  let active = true
  const reset = () => { generation++; publish({ status: 'idle', data: null }) }
  const suspend = () => { active = false; reset() }
  const activate = () => { active = true }
  const load = async () => {
    if (!active) return
    const version = ++generation, owner = identity()
    publish({ status: 'loading', data: null })
    const current = () => active && version === generation && owner === identity()
    try {
      const data = validatePendingSettlements(await read())
      if (current()) publish({ status: 'success', data })
      else if (version === generation) reset()
    } catch {
      if (current()) publish({ status: 'error', data: null })
      else if (version === generation) reset()
    }
  }
  return { load, reset, suspend, activate }
}
