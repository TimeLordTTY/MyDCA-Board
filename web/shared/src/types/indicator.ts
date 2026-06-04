/**
 * 指标数据类型定义
 */

/**
 * 日更指标
 */
export interface IndicatorDaily {
  id: number
  productId: number
  tradeDate: string // YYYY-MM-DD
  windowDays: number // 窗口天数（如20/60）
  pctRank?: number // 分位0~1
  qBuyPrice?: number // 买入分位对应的价格阈值
  qMidPrice?: number // 50%分位价格
  qHighPrice?: number // 80%分位价格
  peakClose?: number // 滚动窗口内峰值close
  drawdownFromPeak?: number // 回撤比例
  ma20?: number // 20日均线
  ma60?: number // 60日均线
  bollMiddle?: number // BOLL中轨
  bollUpper?: number // BOLL上轨
  bollLower?: number // BOLL下轨
  bollStd?: number // BOLL标准差
  bollWindow?: number // BOLL窗口天数
  kdjK?: number // KDJ K值
  kdjD?: number // KDJ D值
  kdjJ?: number // KDJ J值
  kdjRsv?: number // KDJ RSV值
  kdjWindow?: number // KDJ窗口天数
}
