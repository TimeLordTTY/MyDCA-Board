import type { RiskConfig, RiskEvent, RiskSnapshot } from '@wealth-hub/shared'
export const typeLabels = { RETURN: '收益率', DRAWDOWN: '回撤', ALLOCATION_DEVIATION: '类别偏离', STALE: '行情陈旧', CONCENTRATION: '集中度', NOTE: '观察备注' }
export const stateLabels = { OPEN: '未解除 · 未读', ACKNOWLEDGED: '未解除 · 已读', MUTED: '未解除 · 静默', RESOLVED: '已解除' }
export function validateRisk(c: RiskConfig): string {
  if (!['PERSONAL', 'FAMILY'].includes(c.scope) || !Object.prototype.hasOwnProperty.call(typeLabels, c.type) || !['INFO', 'WARNING', 'CRITICAL'].includes(c.severity)) return '规则类型、作用域或级别无效'
  if (c.note.length > 2000) return '备注最多 2000 字'
  if (['RETURN', 'DRAWDOWN', 'STALE', 'CONCENTRATION'].includes(c.type) && (!Number.isSafeInteger(c.productId) || Number(c.productId) <= 0)) return '请选择有效标的 ID'
  if (c.type !== 'NOTE' && (c.threshold === null || !Number.isFinite(c.threshold))) return '请填写有限数值阈值'
  if (c.type === 'RETURN' && !['ABOVE', 'BELOW'].includes(c.direction ?? '')) return '请选择阈值方向'
  if (c.type !== 'RETURN' && c.threshold !== null && c.threshold < 0) return '阈值不得为负数'
  if (['DRAWDOWN', 'CONCENTRATION', 'ALLOCATION_DEVIATION'].includes(c.type) && Number(c.threshold) > 1) return '比例阈值须在 0 至 1 之间'
  if (c.type === 'STALE' && (!Number.isInteger(c.threshold) || Number(c.threshold) > 3650)) return '陈旧阈值须为 0 至 3650 整数天'
  if (c.type === 'ALLOCATION_DEVIATION' && (!c.assetType?.trim() || c.assetType.length > 60 || c.target === null || !Number.isFinite(c.target) || c.target < 0 || c.target > 1)) return '请填写资产类别及 0 至 1 的目标占比'
  return ''
}
export function evidenceState(s: RiskSnapshot, today = new Date().toLocaleDateString('en-CA')): string {
  if (s.status === 'UNKNOWN' || (s.observedValue === null && s.threshold !== null)) return 'UNKNOWN / 数据未知'
  if (!s.sourceDataTimestamp || s.sourceDataTimestamp.slice(0, 10) !== today) return '陈旧快照 / 请重新评估'
  return s.matched ? '当前命中' : '未命中'
}
export function dedupeEvents(events: RiskEvent[]): RiskEvent[] {
  return [...new Map(events.map(e => [`${e.evidence.ruleId}:${e.fingerprint}`, e])).values()]
}
export function riskError(cause: unknown): string {
  const e = cause as { response?: { status?: number }; message?: string }
  if (e.response?.status === 403) return '无权访问：家庭规则需要家庭管理员权限。'
  if (e.response?.status === 401) return '登录已过期，请重新登录。'
  return `风险观察操作失败：${e.message || '请检查网络或服务状态'}。请重试。`
}
