/**
 * 行情数据类型定义
 */

/**
 * 日线行情
 */
export interface MarketBarDaily {
  id: number
  productId: number
  tradeDate: string // YYYY-MM-DD
  openPrice?: number
  highPrice?: number
  lowPrice?: number
  closePrice: number
  volume?: number
  amount?: number
  prevClose?: number
  source: string
}

/**
 * 实时行情
 */
export interface MarketQuoteRealtime {
  id: number
  productId: number
  quoteTime: string // ISO datetime string
  price: number
  prevClose?: number
  pctChg?: number // 涨跌幅（%）
  volume?: number
  amount?: number
  iopv?: number // IOPV实时估值（基金份额参考净值）
  premiumRate?: number // 溢价率
  openPrice?: number
  highPrice?: number
  lowPrice?: number
  source: string
}
