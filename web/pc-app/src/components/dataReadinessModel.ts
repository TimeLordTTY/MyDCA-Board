import type { ReadinessEvidence, DataReadiness } from '@wealth-hub/shared'

export const groups = [
  { title: '资产与行情', areas: ['ASSETS', 'MARKET', 'EXCHANGE_RATES'] },
  { title: '目标与预算', areas: ['GOALS', 'BUDGETS'] },
  { title: '风险与配置观察', areas: ['RISK_RULES', 'ALLOCATION'] },
  { title: '策略研究 / 回测', areas: ['RESEARCH'] },
  { title: '规划情景', areas: ['FORECAST_INPUTS'] },
  { title: '数据库结构验证边界', areas: ['SCHEMA'] },
]
export const labels: Record<string, string> = {
  ASSETS: '资产来源', MARKET: '行情、净值与指标', EXCHANGE_RATES: '汇率', GOALS: '目标进度',
  BUDGETS: '月度预算', RISK_RULES: '风险历史', ALLOCATION: '配置观察', RESEARCH: '研究证据',
  FORECAST_INPUTS: '预测输入', SCHEMA: '数据库结构',
}
export function rows(report: DataReadiness | null, areas: string[]): ReadinessEvidence[] {
  return areas.map<ReadinessEvidence>(area => report?.evidence?.find(e => e.area === area) ?? {
    area, state: 'UNKNOWN', reason: '尚无可用诊断证据，不能按零或健康处理。',
    source: '未知', dataTime: null, nextStep: '人工刷新后核对作用域、月份与服务可用性。',
  }).map(item => item.area === 'SCHEMA' ? {
    ...item, state: 'UNKNOWN' as const,
    reason: '当前诊断接口不提供迁移覆盖事实，本页不能实时验证数据库结构状态；UNKNOWN 不表示迁移未部署。',
    nextStep: '由获授权部署管理员核对目标环境部署记录；业务数据完整性仍查看各项诊断。',
  } : item)
}
export function stateLabel(e: ReadinessEvidence): string {
  if (e.area === 'SCHEMA') return 'UNKNOWN · 本页无法实时验证'
  return ({ READY: 'READY · 可读', PARTIAL: 'PARTIAL · 部分已知', UNKNOWN: 'UNKNOWN · 未知',
    UNAVAILABLE: 'UNAVAILABLE · 读取失败' })[e.state] ?? 'UNKNOWN · 未知'
}
/** Only date/day timestamps have freshness semantics; a budget month is a period. */
export function freshness(e: ReadinessEvidence, now = new Date()): string {
  if (!e.dataTime || /^\d{4}-\d{2}$/.test(e.dataTime)) return '时效未核实'
  const time = Date.parse(e.dataTime.slice(0, 10))
  if (!Number.isFinite(time)) return '时效未核实'
  const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate())
  const days = (today - time) / 86400000
  if (days < 0) return '来源时间异常，请人工核对'
  if (days > 3) return '来源已过期（超过3个日历日），请人工核对'
  return '近期来源；完整性仍以诊断说明为准'
}
export function diagnosticError(error: any): string {
  if (error?.response?.status === 401) return '登录已失效，请重新登录后查看。'
  if (error?.response?.status === 403) return '没有该作用域的访问权限；家庭诊断需要家庭管理员权限。'
  return '诊断读取失败，可能网络断开、请求超时或服务暂不可用。请人工重试。'
}
/** Do not copy server prose, IDs, financial values, debug details or credentials. */
export function issueSummary(report: DataReadiness): string {
  return ['数据就绪检查（脱敏摘要）', `作用域：${report.scope === 'FAMILY' ? '家庭' : '个人'}`,
    ...groups.flatMap(g => rows(report, g.areas).filter(e => e.state !== 'READY' || e.area === 'SCHEMA')
      .map(e => `${labels[e.area]}：${stateLabel(e)}`)),
    '数据库结构由目标环境部署记录核验；本页无法实时验证，不代表迁移未部署。',
    '请人工核对来源、时效、覆盖范围和部署记录。'].join('\n')
}
