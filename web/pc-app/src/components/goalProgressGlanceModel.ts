import type { Goal, GoalProgress } from '@wealth-hub/shared'

export const GOAL_GLANCE_LIMITS = { pages: 5, pageSize: 20, goals: 3, timeoutMs: 30_000 } as const
export const goalCurrencies = ['CNY', 'USD', 'HKD', 'EUR', 'JPY', 'GBP']
export interface GoalGlanceEntry { goal: Goal; progress: GoalProgress }
const finite = (n: unknown): n is number => typeof n === 'number' && Number.isFinite(n)
export function validGoalDate(value: unknown): value is string {
  if (typeof value !== 'string' || !/^[1-9]\d{3}-\d{2}-\d{2}$/.test(value)) return false
  const date = new Date(`${value}T00:00:00Z`)
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value
}
const incomplete = () => new Error('目标列表无法确认完整，请进入目标中心。')
export function completeGoalProgress(p: GoalProgress) {
  return p.quality === 'OK' && finite(p.completionRate) && Number.isFinite(p.completionRate * 100) &&
    typeof p.completed === 'boolean' && p.completed === (p.completionRate >= 1)
}
export const goalAmount = (n: unknown) => finite(n) || typeof n === 'string' && /^\d{1,18}(\.\d{1,2})?$/.test(n)
  ? Number(n).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '未知'
export const goalPercent = (p: GoalProgress) => completeGoalProgress(p) ? `${(p.completionRate! * 100).toFixed(2)}%` : '未知'
// Only known public contract reasons reach the card; arbitrary server text can contain private details.
export function goalReason(p: GoalProgress) {
  const safe = ['数据不足，进度未知', '现有资产汇总仅支持人民币，未进行汇率换算', '仅部分资产金额已知，无法确定完成率', '只读资产进度']
  return safe.includes(p.reason) ? p.reason : '来源原因不可安全展示，进度未知；请在目标中心核验。'
}
export function goalCountdown(p: GoalProgress, targetDate: string) {
  if (!validGoalDate(p.asOfDate) || !validGoalDate(targetDate) || !Number.isSafeInteger(p.daysRemaining) || typeof p.overdue !== 'boolean') return '倒计时未知'
  const days = (Date.parse(`${targetDate}T00:00:00Z`) - Date.parse(`${p.asOfDate}T00:00:00Z`)) / 86400000
  if (days !== p.daysRemaining || p.overdue !== (days < 0)) return '倒计时未知'
  return days < 0 ? `已逾期 ${Math.abs(days)} 天（相对数据日期）` : `距目标 ${days} 天（相对数据日期）`
}
export function goalGlanceFailure(e: unknown) {
  const status = (e as { response?: { status?: number } })?.response?.status
  if (status === 401 || status === 403) return '登录失效或没有访问权限，请重新登录或联系家庭管理员后手动重试。'
  if (e instanceof Error && ['目标列表无法确认完整，请进入目标中心。', '目标进度响应无效，请进入目标中心核验。'].includes(e.message)) return e.message
  return '网络或服务暂不可用，请手动重试。'
}
export async function readGoalGlance(api: Pick<typeof import('@wealth-hub/shared').goalApi, 'list' | 'progress'>,
  scope: string, currency: string, isCurrent: () => boolean): Promise<GoalGlanceEntry[]> {
  const goals: Goal[] = [], ids = new Set<string>()
  for (let page = 0; page < GOAL_GLANCE_LIMITS.pages; page++) {
    if (!isCurrent()) return []
    const rows = await api.list(page)
    if (!isCurrent()) return []
    if (!Array.isArray(rows) || rows.length > 20) throw incomplete()
    for (const g of rows) {
      const c = g?.config
      if (!g || typeof g.id !== 'string' || !g.id.trim() || ids.has(g.id) || !c ||
        typeof c.name !== 'string' || !c.name.trim() || c.name.length > 200 || !validGoalDate(c.targetDate) ||
        !['PERSONAL', 'FAMILY'].includes(c.scope) || !goalCurrencies.includes(c.currency) ||
        !['ACTIVE', 'PAUSED', 'ARCHIVED'].includes(c.state) || !['TOTAL_ASSETS', 'CASH', 'POSITION_VALUE'].includes(c.measure) ||
        !/^\d{1,18}(\.\d{1,2})?$/.test(String(c.targetValue)) || !(Number(c.targetValue) > 0)) throw incomplete()
      ids.add(g.id); goals.push(g)
    }
    if (rows.length < 20) break
    if (page === 4) throw incomplete()
  }
  const selected = goals.filter(g => g.config.state === 'ACTIVE' && g.config.scope === scope && g.config.currency === currency)
    .sort((a, b) => a.config.targetDate.localeCompare(b.config.targetDate)).slice(0, 3)
  return Promise.all(selected.map(async goal => {
    if (!isCurrent()) throw new Error('请求已失效')
    const progress = await api.progress(goal.id)
    if (!isCurrent()) throw new Error('请求已失效')
    if (!progress || progress.goalId !== goal.id || !['OK', 'PARTIAL', 'UNKNOWN'].includes(progress.quality) || typeof progress.reason !== 'string')
      throw new Error('目标进度响应无效，请进入目标中心核验。')
    return { goal, progress }
  }))
}
