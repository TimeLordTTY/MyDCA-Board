import type { GoalConfig, BudgetConfig } from '@wealth-hub/shared'
export const labels: Record<string, string> = { ACTIVE: '进行中', PAUSED: '已暂停', ARCHIVED: '已归档', TOTAL_ASSETS: '总资产', CASH: '现金', POSITION_VALUE: '持仓市值', PERSONAL: '个人', FAMILY: '家庭（管理员）', INCOME: '收入', FIXED_EXPENSE: '固定支出', FLEXIBLE_EXPENSE: '弹性支出', RESERVE: '预留', OK: '完整', PARTIAL: '部分已知 / PARTIAL', UNKNOWN: '未知 / UNKNOWN' }
export const amount = (n: number | string | null | undefined) => n == null ? '未知 / UNKNOWN' : Number(n).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
export const percent = (n: number | null) => n == null ? '未知 / UNKNOWN' : `${(n * 100).toFixed(2)}%`
export function failure(e: unknown) { const x = e as { response?: { status?: number }; message?: string }; return [401, 403].includes(x?.response?.status ?? 0) ? '登录失效或没有访问权限，请重新登录或联系家庭管理员。' : x?.message || '请求失败，请重试。' }
const money = (n: number | string, positive = false) => /^\d{1,18}(\.\d{1,2})?$/.test(String(n)) && (!positive || Number(n) > 0)
const base = (c: { name: string; scope: string; currency: string }) => !c.name.trim() || c.name.length > 200 ? '名称须为1至200字符' : !['PERSONAL', 'FAMILY'].includes(c.scope) ? '请选择作用域' : !['CNY', 'USD', 'HKD', 'EUR', 'JPY', 'GBP'].includes(c.currency) ? '请选择币种' : ''
export function validateGoal(c: GoalConfig) {
  const date = new Date(`${c.targetDate}T00:00:00Z`)
  return base(c) || (!money(c.targetValue, true) ? '目标金额须为正数，最多18位整数和2位小数' : !/^\d{4}-\d{2}-\d{2}$/.test(c.targetDate) || !Number.isFinite(date.getTime()) || date.toISOString().slice(0, 10) !== c.targetDate || Number(c.targetDate.slice(0, 4)) < 1000 ? '目标日期无效' : !['TOTAL_ASSETS', 'CASH', 'POSITION_VALUE'].includes(c.measure) || !['ACTIVE', 'PAUSED', 'ARCHIVED'].includes(c.state) ? '请选择统计口径和状态' : c.note.length > 2000 ? '备注最多2000字符' : '')
}
export function validateBudget(c: BudgetConfig) {
  if (base(c)) return base(c)
  if (!/^[1-9]\d{3}-(0[1-9]|1[0-2])$/.test(c.month) || c.month.startsWith('9999')) return '月份须为1000至9998年的YYYY-MM'
  if (!c.items.length || c.items.length > 100) return '预算须包含1至100项'
  const seen = new Set<string>()
  for (const i of c.items) {
    if (!i.name.trim() || i.name.length > 200 || !money(i.planned) || !['INCOME', 'FIXED_EXPENSE', 'FLEXIBLE_EXPENSE', 'RESERVE'].includes(i.kind)) return '预算项名称、类型或金额无效（最多2位小数）'
    if (i.kind === 'RESERVE') { if (i.categoryId != null) return '预留项不能绑定分类'; continue }
    if (!Number.isSafeInteger(i.categoryId) || Number(i.categoryId) <= 0) return '收入和支出须绑定既有分类'
    const key = `${i.kind === 'INCOME' ? 'INCOME' : 'EXPENSE'}:${i.categoryId}`
    if (seen.has(key)) return '同一收入或支出分类不能重复预算'
    seen.add(key)
  }
  return ''
}

// Copy only plan fields. Runtime shape checks precede the existing business validation.
export function copyBudgetNextMonth(source: BudgetConfig): BudgetConfig {
  if (!source || typeof source.name !== 'string' || typeof source.month !== 'string' ||
      !Array.isArray(source.items) || source.items.some(i => !i || typeof i.name !== 'string' ||
        !['string', 'number'].includes(typeof i.planned))) throw new Error('预算配置格式无效，无法复制。')
  const invalid = validateBudget(source)
  if (invalid) throw new Error(invalid)
  let year = Number(source.month.slice(0, 4)), month = Number(source.month.slice(5)) + 1
  if (month === 13) { year++; month = 1 }
  if (year > 9998) throw new Error('下一月份超出1000至9998年范围，无法复制。')
  const copy: BudgetConfig = {
    name: source.name, scope: source.scope, currency: source.currency,
    month: `${year}-${String(month).padStart(2, '0')}`,
    items: source.items.map(i => ({ name: i.name, kind: i.kind, categoryId: i.categoryId, planned: i.planned })),
  }
  return copy
}
