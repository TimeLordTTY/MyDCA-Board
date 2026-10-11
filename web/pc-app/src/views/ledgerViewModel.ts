import type { LedgerTxn } from '@wealth-hub/shared'

export type LedgerRow = LedgerTxn & {
  originalTxnId?: string
  summaryAmount?: number
  leafAccountName?: string
  leafAccountBalance?: number
  parentAccountBalance?: number
  currency?: string
  canEdit?: boolean
}

export function localDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function localDateTime(date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${localDate(date)} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function monthRange(month: string): [string, string] | null {
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) return null
  const [year, number] = month.split('-').map(Number)
  return [`${month}-01`, `${month}-${new Date(year, number, 0).getDate()}`]
}

// These are UI hints only. The server still checks owner and business constraints.
export function editReason(txn: LedgerRow, userId?: number): string {
  if (userId == null || txn.userId !== userId) return '仅记录所有者可编辑，最终以服务器授权为准'
  if (txn.canEdit === false) return '服务器标记为不可编辑'
  if (txn.orderId || txn.relatedOrderId) return '关联订单的历史流水请到订单与结算查看'
  if (txn.isReversed || txn.status === 'REVERSED' || txn.status === 'CANCELLED') return '已冲销或取消的历史流水只读'
  if (!['EXPENSE', 'INCOME', 'TRANSFER_OUT', 'TRANSFER_IN', 'BUY', 'SUBSCRIPTION', 'SELL', 'REDEMPTION'].includes(txn.txnType)) return '此类型暂无编辑表单，请查看原始分录'
  return ''
}
