import type { Budget, BudgetComparison } from '@wealth-hub/shared'

export const REVIEW_LIMITS = { pages: 5, comparisons: 30, concurrency: 3, timeoutMs: 30000 }
export function recentMonths(now = new Date()): string[] {
  // Local calendar arithmetic starts on day one, avoiding UTC shifts and month-end rollover.
  return Array.from({ length: 6 }, (_, index) => {
    const date = new Date(now.getFullYear(), now.getMonth() - 5 + index, 1)
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
  })
}
export function completeActual(c: BudgetComparison): boolean {
  return c.quality === 'OK' && [c.actualIncome, c.actualExpenses, c.actualSurplus, c.remainingBudget].every(n => typeof n === 'number' && Number.isFinite(n)) && typeof c.overspent === 'boolean'
}
export interface ReviewEntry { budget: Budget; comparison: BudgetComparison; readAt: string }
interface ReadApi {
  list(page: number, signal?: AbortSignal): Promise<Budget[]>
  comparison(id: string, signal?: AbortSignal): Promise<BudgetComparison>
}
export async function readReview(api: ReadApi, months: string[], scope: string, currency: string, signal: AbortSignal) {
  const budgets: Budget[] = [], seen = new Set<string>()
  let exhausted = false
  const check = () => { if (signal.aborted) throw new Error('回顾已取消') }
  for (let page = 0; page < REVIEW_LIMITS.pages; page++) {
    check()
    const batch = await api.list(page, signal)
    check()
    for (const budget of batch) {
      if (!seen.has(budget.id) && months.includes(budget.config.month) && budget.config.scope === scope && budget.config.currency === currency) budgets.push(budget)
      seen.add(budget.id)
    }
    if (batch.length < 20) { exhausted = true; break }
  }
  // Never call a truncated scan an empty month: no server month filter or total count exists.
  if (!exhausted) throw new Error('已达5页 / 100份预算扫描上限，无法确认月份完整性。请使用原预算列表查看；回顾未生成。')
  if (budgets.length > REVIEW_LIMITS.comparisons) throw new Error('匹配预算超过30份对比上限，请使用原预算列表逐份查看；回顾未生成。')
  const entries: ReviewEntry[] = []
  let cursor = 0, failed = false
  await Promise.all(Array.from({ length: Math.min(REVIEW_LIMITS.concurrency, budgets.length) }, async () => {
    while (!failed && cursor < budgets.length) {
      check()
      const budget = budgets[cursor++]
      try {
        const comparison = await api.comparison(budget.id, signal)
        check()
        if (comparison.budgetId !== budget.id) throw new Error('预算对比标识不一致，请重试。')
        entries.push({ budget, comparison, readAt: new Date().toLocaleString('zh-CN') })
      } catch (e) { failed = true; throw e }
    }
  }))
  check()
  return entries.sort((a, b) => a.budget.config.month.localeCompare(b.budget.config.month) || a.budget.id.localeCompare(b.budget.id))
}
