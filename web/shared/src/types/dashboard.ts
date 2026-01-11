/**
 * 看板相关类型定义
 */

export interface AssetOverview {
  totalAssets: number
  netWorth: number
  cashBalance: number
  positionValue: number
  liability: number
  todayPnl?: number
  monthInflow?: number
}

export interface AssetAllocation {
  groupBy: 'assetType' | 'account'
  items: Array<{
    label: string
    value: number
    weight: number
  }>
}

// PendingSettlement 定义在 order.ts 中，这里不再重复定义

export interface TodayAction {
  id: string
  type: string
  title: string
  description?: string
  priority: 'HIGH' | 'MEDIUM' | 'LOW'
  actionUrl?: string
}

export interface Performance {
  totalReturn: number
  annualReturn: number
  maxDrawdown: number
  sharpeRatio?: number
  winRate?: number
}
