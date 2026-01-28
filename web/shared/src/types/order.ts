/**
 * 订单相关类型定义
 * 完全对应orders、order_funding_line、settlement_confirm表结构
 */

export interface Order {
  id: number
  orderId: string
  userId: number
  productId: number
  orderType: 'BUY' | 'SELL' | 'SUBSCRIPTION' | 'REDEMPTION'
  amount?: number
  shares?: number
  requestedAt: string
  tradeDate?: string
  expectedNavDate?: string
  expectedConfirmDate?: string
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'FAILED'
  feeEstimate?: number
  note?: string
  createdAt: string
  updatedAt: string
}

export interface OrderFundingLine {
  id: number
  orderId: string
  lineNo: number
  accountId: number
  amount: number
  shares?: number  // 卖出份额（卖出/赎回时使用，买入/申购时为undefined）
  currency: 'CNY' | 'USD' | 'HKD'
  lineType?: 'SOURCE' | 'TARGET'  // SOURCE=出金来源, TARGET=到账目标
  createdAt: string
  updatedAt: string
}

export interface OrderDetail extends Order {
  fundingLines: OrderFundingLine[]
  settlement?: SettlementConfirm
}

export interface SettlementConfirm {
  id: number
  orderId: string
  confirmDate: string
  confirmDatetime?: string
  navDate: string
  confirmNav: number
  confirmShares?: number
  confirmAmount?: number
  confirmFee?: number
  isManualOverride: boolean
  confirmedByUserId?: number
  confirmedAt: string
  note?: string
  createdAt: string
}

export interface OrderQueryParams {
  status?: string
  productId?: number
}

export interface CreateOrderRequest {
  productId: number
  orderType: 'BUY' | 'SELL' | 'SUBSCRIPTION' | 'REDEMPTION'
  amount?: number
  shares?: number
  fundingLines: Array<{
    accountId: number
    amount?: number  // 买入时使用
    shares?: number  // 卖出时使用
    lineType?: 'SOURCE' | 'TARGET'  // SOURCE=出金来源, TARGET=到账目标
  }>
  tradeDate?: string
  expectedNavDate?: string
  note?: string
}

export interface PendingSettlement {
  orderId: string
  orderType: 'BUY' | 'SELL' | 'SUBSCRIPTION' | 'REDEMPTION'
  productId: number
  productName?: string
  amount: number
  expectedConfirmDate: string
  fundingLines: Array<{
    accountId: number
    accountName?: string
    amount?: number  // 买入时使用
    shares?: number  // 卖出时使用
  }>
}

export interface ConfirmSettlementRequest {
  orderId: string
  confirmDate: string
  navDate: string
  confirmNav: number
  confirmShares?: number
  confirmAmount?: number
  confirmFee?: number
  isManualOverride?: boolean
  note?: string
}
