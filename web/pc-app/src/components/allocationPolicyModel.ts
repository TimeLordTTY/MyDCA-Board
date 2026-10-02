import type { AllocationConfig, RebalancePreview, RebalanceScenario } from '@wealth-hub/shared'
export const observationLabels = { IN_RANGE: '区间内', BELOW_BAND: '低于下限', ABOVE_BAND: '高于上限', TAKE_PROFIT_WATCH: '收益阈值观察', UNKNOWN: 'UNKNOWN / 数据未知' }
export function validateAllocation(c: AllocationConfig): string {
  if (!['PERSONAL', 'FAMILY'].includes(c.scope) || typeof c.enabled !== 'boolean') return '请选择作用域及启用状态'
  if ((c.productId === null) === (c.assetType === null)) return '请选择一个产品或资产类别'
  if (c.productId !== null && (!Number.isSafeInteger(c.productId) || c.productId <= 0)) return '产品 ID 须为正整数'
  if (c.assetType !== null && (!c.assetType.trim() || c.assetType.length > 60)) return '资产类别须为 1 至 60 字'
  if ([c.lowerBound, c.target, c.upperBound].some(n => typeof n !== 'number' || !Number.isFinite(n) || n < 0 || n > 1) || c.lowerBound > c.target || c.target > c.upperBound) return '须满足 0 ≤ 下限 ≤ 目标 ≤ 上限 ≤ 1'
  const thresholds = c.takeProfitThresholds
  if ((c.returnThreshold !== null && (!Number.isFinite(c.returnThreshold) || c.returnThreshold < 0)) || thresholds.length > 20 || thresholds.some((n, i) => !Number.isFinite(n) || n < 0 || (i > 0 && n <= thresholds[i - 1]))) return '收益阈值须非负；分段阈值最多 20 个且严格递增'
  if (c.assetType === 'CASH' && (c.returnThreshold !== null || thresholds.length)) return '现金类别不支持收益观察'
  if ((c.note?.length ?? 0) > 2000) return '备注最多 2000 字'
  return ''
}
export const percent = (n: number | null | undefined) => n == null || !Number.isFinite(n) ? 'UNKNOWN / 未知' : `${(n * 100).toFixed(2)}%`
export const amount = (n: number | null | undefined) => n == null || !Number.isFinite(n) ? 'UNKNOWN / 未知' : `¥${n.toFixed(2)}`
export function afterWeight(p: RebalancePreview, s: RebalanceScenario | null): number | null {
  if (p.status === 'UNKNOWN' || !s || p.currentWeight === null || !p.totalAssets || p.totalAssets <= 0) return null
  return p.currentWeight + s.selectedAdjustment / p.totalAssets
}
export function dataState(p: RebalancePreview, today = new Date().toLocaleDateString('en-CA')): string {
  if (p.status === 'UNKNOWN') return 'UNKNOWN / 行情或组合数据不足'
  if (!p.dataDate || p.dataDate !== today || p.prices.some(r => r.status !== 'OK' || !r.priceDate || !r.valuationDate || r.priceDate < today || r.valuationDate < today)) return '行情或快照陈旧，请核对数据日期'
  return '请核对下方数据时间与价格来源'
}
export function allocationError(cause: unknown): string {
  const e = cause as { response?: { status?: number }; message?: string }
  if (e.response?.status === 403) return '无权访问，家庭配置需要管理员权限。'
  if (e.response?.status === 401) return '登录已过期，请重新登录。'
  return `配置观察操作失败：${e.message || '网络或服务不可用'}。请重试。`
}
