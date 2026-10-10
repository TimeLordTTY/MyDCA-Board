import type { Budget, BudgetComparison } from '@wealth-hub/shared'
import { completeActual } from './budgetReviewModel'

export const GLANCE_LIMITS = { pages: 5, comparisons: 20, concurrency: 3, timeoutMs: 30000 }
export const localMonth = (now = new Date()) => `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
export const glanceAmount = (n: unknown) => typeof n === 'number' && Number.isFinite(n) ? n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '未知'
export const actualAmount = (c: BudgetComparison, n: unknown) => completeActual(c) ? glanceAmount(n) : c.quality === 'PARTIAL' ? '流水未读全（完整实际未知）' : '未知'
export interface GlanceEntry { budget: Budget; comparison: BudgetComparison; readAt: string }
interface ReadApi {
  list(page: number, signal?: AbortSignal): Promise<Budget[]>
  comparison(id: string, signal?: AbortSignal): Promise<BudgetComparison>
}
export async function readMonthlyGlance(api: ReadApi, month: string, scope: string, currency: string, signal: AbortSignal): Promise<GlanceEntry[]> {
  const budgets: Budget[] = [], seen = new Map<string, string>()
  const check = () => { if (signal.aborted) throw new Error('预算加载已取消') }
  let exhausted = false
  for (let page = 0; page < GLANCE_LIMITS.pages; page++) {
    check()
    const batch = await api.list(page, signal)
    check()
    if (!Array.isArray(batch) || batch.length > 20) throw new Error('列表格式异常，无法证明本月全量')
    for (const b of batch) {
      if (!b || typeof b.id !== 'string' || !b.id || !b.config || typeof b.config.name !== 'string') throw new Error('预算格式异常，无法证明本月全量')
      const signature = JSON.stringify(b.config)
      if (seen.has(b.id) && seen.get(b.id) !== signature) throw new Error('预算标识配置不一致，无法证明本月全量')
      if (!seen.has(b.id) && b.config.month === month && b.config.scope === scope && b.config.currency === currency) budgets.push(b)
      seen.set(b.id, signature)
    }
    if (batch.length < 20) { exhausted = true; break }
  }
  if (!exhausted) throw new Error('已达5页 / 100份扫描上限，无法证明本月全量，请查看完整预算')
  if (budgets.length > GLANCE_LIMITS.comparisons) throw new Error('本月预算超过20份对比上限，无法证明本月全量，请查看完整预算')
  const entries: GlanceEntry[] = []
  let cursor = 0, failed = false
  await Promise.all(Array.from({ length: Math.min(budgets.length, GLANCE_LIMITS.concurrency) }, async () => {
    while (!failed && cursor < budgets.length) {
      check()
      const budget = budgets[cursor++]
      try {
        const comparison = await api.comparison(budget.id, signal)
        check()
        // The existing comparison contract has no month/scope/currency fields.
        // Bind it to the authorized list configuration; reject optional conflicting metadata.
        const metadata = comparison as BudgetComparison & { month?: string; scope?: string; currency?: string }
        if (comparison.budgetId !== budget.id || budget.config.month !== month || budget.config.scope !== scope || budget.config.currency !== currency ||
            (metadata.month !== undefined && metadata.month !== month) || (metadata.scope !== undefined && metadata.scope !== scope) || (metadata.currency !== undefined && metadata.currency !== currency)) throw new Error('预算对比标识 / 月份 / 作用域 / 币种不一致，请重试')
        entries.push({ budget, comparison, readAt: new Date().toLocaleString('zh-CN') })
      } catch (e) { failed = true; throw e }
    }
  }))
  check()
  return entries.sort((a, b) => a.budget.id.localeCompare(b.budget.id))
}

// Presentation only: retain completeActual and the API quality contract.
export function budgetQualityText(c: BudgetComparison) {
  if (completeActual(c)) return '已按本预算分类核对流水'
  if (c.quality === 'PARTIAL') return '部分流水未读全，暂无法确认剩余预算或是否超支'
  return '实际流水暂无法确认，不能按0支出计算'
}
export function budgetGlanceFailure(e: unknown) {
  const status = (e as { response?: { status?: number } })?.response?.status
  if (status === 401) return '登录已失效，请重新登录后手动重试；暂无法读取预算与流水。'
  if (status === 403) return '没有读取此预算的权限，请联系家庭管理员；暂无法确认余额与流水。'
  return '预算读取失败，请手动重试。' + (e instanceof Error ? e.message : '网络或服务暂不可用。')
}
