/**
 * 净值数据类型定义
 */

/**
 * 基金净值
 */
export interface Nav {
  id: number
  productId: number
  navDate: string // YYYY-MM-DD
  nav: number // 单位净值
  accNav?: number // 累计净值
  dailyReturn?: number // 日收益率
  dividend?: number // 分红
  source: string
}
