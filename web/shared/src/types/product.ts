/**
 * 产品相关类型定义
 * 完全对应product_master表结构
 */

export interface ProductMaster {
  id: number
  productCode: string
  channel: 'EXCHANGE' | 'OTC'
  market: 'SH' | 'SZ' | 'NA'
  assetType: 'ETF' | 'LOF' | 'FUND' | 'MMF' | 'BANK_WM_NAV' | 'BANK_WM_BOX' | 'STOCK' | 'FUTURES' | 'OPTIONS' | 'BOND_REPO'
  currency: 'CNY' | 'USD' | 'HKD'
  productName: string
  isQdii?: boolean
  isqdii?: boolean // 兼容后端可能返回的小写字段
  trackIndex?: string
  buyFeeRate: number
  sellFeeRate: number
  buyConfirmOffset: number
  sellConfirmOffset: number
  cutoffTime: string
  dataSource?: string
  isActive: boolean
  sortOrder?: number // 排序顺序（数字越小越靠前）
  note?: string // 添加note字段
  createdAt: string
  updatedAt: string
}

export interface ProductQueryParams {
  keyword?: string
  assetType?: string
  channel?: string
}
