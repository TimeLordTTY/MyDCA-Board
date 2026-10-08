import type { Goal, Budget } from '@wealth-hub/shared'
import type { ForecastRequest } from '@wealth-hub/shared'
export function monthRange(start: string, count: number) {
  if (!/^[1-9]\d{3}-(0[1-9]|1[0-2])$/.test(start) || !Number.isInteger(count) || count < 12 || count > 60) return []
  const [year, month] = start.split('-').map(Number)
  // Reject the entire horizon instead of silently shortening the owner's input.
  if (year * 12 + month - 1 + count - 1 > 9998 * 12 + 11) return []
  return Array.from({ length: count }, (_, i) => { const n = year * 12 + month - 1 + i; return `${Math.floor(n / 12)}-${String(n % 12 + 1).padStart(2, '0')}` })
}
export const validMoney = (value: string) => value === '' || /^\d{1,18}(\.\d{1,2})?$/.test(value)
export function validateForecast(goals: Goal[], budgets: Budget[], request: ForecastRequest, allocations: string[]) {
  if (!goals.length || goals.length > 8) return '请选择1至8个目标'
  if (new Set(goals.map(g => g.id)).size !== goals.length) return '目标不得重复'
  if (goals.some(g => g.config.currency !== goals[0].config.currency || g.config.scope !== goals[0].config.scope)) return '目标须具有相同币种和作用域'
  const months = monthRange(request.startMonth, request.months.length)
  if (months.length !== request.months.length || !months.length || months[months.length - 1] !== request.endMonth || request.months.some((m, i) => m.month !== months[i])) return '期间须为连续12至60个月'
  if (!['PLANNED', 'ACTUAL_PLUS_REMAINING'].includes(request.mode)) return '请选择预算模式'
  if (!validMoney(request.monthlyExtraSavings ?? '') || allocations.some(a => !validMoney(a))) return '金额须非负，最多18位整数和2位小数；空值保留未知'
  for (const m of request.months) {
    if (!m.budgetId) continue
    const b = budgets.find(b => b.id === m.budgetId)
    if (!b || b.config.month !== m.month || b.config.scope !== goals[0].config.scope || b.config.currency !== goals[0].config.currency) return '预算月份、币种及作用域须匹配目标'
  }
  return ''
}
