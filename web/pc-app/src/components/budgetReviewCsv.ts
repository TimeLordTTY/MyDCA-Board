import { completeActual } from './budgetReviewModel'
import type { ReviewEntry } from './budgetReviewModel'

export const CSV_COLUMNS = ['月份', '预算名称', '预算ID', '作用域', '币种', '计划收入', '计划支出', '计划预留', '计划结余', '实际收入', '实际支出', '实际结余', '剩余预算', 'quality', '超支状态', '未匹配流水数', '读取时间（客户端，非账务快照）', '证据/备注']
const number = (n: unknown): string => typeof n === 'number' && Number.isFinite(n) ? String(n) : '未知'
// Quote every cell; neutralize formula prefixes even after whitespace/control characters.
function cell(value: string): string {
  const safe = /^[\s\u0000-\u001f\u007f-\u009f]*[=+@\-]/u.test(value) || /[\u0000-\u001f\u007f-\u009f]/u.test(value) ? `'${value}` : value
  return `"${safe.replace(/"/g, '""')}"`
}
export function serializeBudgetReviewCsv(months: string[], entries: ReviewEntry[], scope: string, currency: string): string {
  const rows: string[][] = [CSV_COLUMNS]
  for (const month of [...months].sort()) {
    const matching = entries.filter(e => e.budget.config.month === month && e.budget.config.scope === scope && e.budget.config.currency === currency)
      .sort((a, b) => a.budget.id < b.budget.id ? -1 : a.budget.id > b.budget.id ? 1 : 0)
    if (!matching.length) rows.push([month, '未创建预算', '', scope, currency, ...Array(8).fill('未知'), 'UNAVAILABLE', '超支状态未知', '未知', '未知', '完整扫描内未创建预算；金额未知'])
    for (const e of matching) {
      const c = e.comparison, complete = completeActual(c)
      const quality = ['OK', 'PARTIAL', 'UNKNOWN', 'UNAVAILABLE'].includes(c.quality) ? c.quality : 'UNKNOWN'
      // Free-text warnings are deliberately omitted: fixed evidence avoids leaking arbitrary backend text.
      rows.push([month, e.budget.config.name, e.budget.id, scope, currency,
        ...[c.plannedIncome, c.plannedExpenses, c.plannedReserve, c.plannedSurplus].map(number),
        ...[c.actualIncome, c.actualExpenses, c.actualSurplus, c.remainingBudget].map(n => complete ? number(n) : `未知 / ${quality === 'OK' ? 'UNKNOWN' : quality}`),
        complete ? 'OK' : quality === 'OK' ? 'UNKNOWN' : quality,
        complete ? c.overspent ? '已超支' : '未超支（本预算口径）' : '超支状态未知', number(c.unmatchedPostings), e.readAt,
        '既有预算对比API；仅所配分类；家庭为授权当前用户口径；计划预留非实际转账/支出；读取时间非账务快照；自由文本warnings未导出'])
    }
  }
  return '\uFEFF' + rows.map(row => row.map(cell).join(',')).join('\r\n') + '\r\n'
}
export function budgetReviewFilename(months: string[], scope: string, currency: string): string {
  const period = [...months].filter(m => /^\d{4}-\d{2}$/.test(m)).sort()
  return `budget-review-${period[0] || 'unknown'}-${period[period.length - 1] || 'unknown'}-${scope === 'FAMILY' ? 'FAMILY' : 'PERSONAL'}-${['CNY','USD','HKD','EUR','JPY','GBP'].includes(currency) ? currency : 'UNKNOWN'}.csv`
}
export function downloadBudgetReviewCsv(csv: string, filename: string): void {
  if (typeof document === 'undefined' || typeof Blob === 'undefined' || typeof URL.createObjectURL !== 'function') throw new Error('当前环境不支持浏览器下载。')
  let url: string | undefined, anchor: HTMLAnchorElement | undefined
  try {
    url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
    anchor = document.createElement('a'); anchor.href = url; anchor.download = filename
    document.body.appendChild(anchor); anchor.click()
  } finally {
    anchor?.remove()
    // Defer revocation so the browser has time to consume the download URL.
    if (url) { const resource = url; setTimeout(() => URL.revokeObjectURL(resource), 1000) }
  }
}
