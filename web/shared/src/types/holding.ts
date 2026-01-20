/**
 * 持仓相关类型定义
 * 完全对应后端HoldingService.HoldingInfo
 */

export interface HoldingInfo {
  productId: number
  productCode?: string
  productName?: string
  channel?: 'EXCHANGE' | 'OTC'
  assetType?: string
  // 后端返回的字段名（与HoldingService.HoldingInfo一致）
  totalShares: number
  totalCost: number
  avgCost: number
  marketValue?: number
  unrealizedPnl?: number
  // 兼容前端使用的字段名
  shares?: number
  cost?: number
  averageCost?: number
}

export interface HoldingDetail extends HoldingInfo {
  history?: HoldingSnapshot[]
}

export interface HoldingSnapshot {
  snapshotDate: string
  totalShares: number
  totalCost: number
  averageCost: number
  marketValue: number
  unrealizedPnl: number
}

export interface HoldingQueryParams {
  snapshotDate?: string
}
