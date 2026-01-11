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
  isQdii: boolean
  trackIndex?: string
  buyFeeRate: number
  sellFeeRate: number
  buyConfirmOffset: number
  sellConfirmOffset: number
  cutoffTime: string
  dataSource?: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface ProductQueryParams {
  keyword?: string
  assetType?: string
  channel?: string
}
