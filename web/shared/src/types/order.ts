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
  /** Display-only digest; cannot be used as freshPreviewToken. */
  previewDigest?: string
  ledgerTxnId?: string
}

export interface SettlementAudit {
  orderId: string
  orderType: Order['orderType']
  orderStatus: string
  productId: number
  productName?: string
  settlement: SettlementConfirm
  fundingLines: OrderFundingLine[]
  ledgerTxnId?: string
  postings: Array<{ txnId: string; accountId: number; accountType: string; postingType: string; amount: number; shares?: number }>
  cashDelta: number
  positionSharesDelta: number
  feeAmount: number
  reconciliationStatus: 'OK' | 'WARNING' | 'BROKEN'
  reasons: string[]
}

/**
 * 结算影响预览中的单条分录（只读，不代表已经落账）。
 * 对应后端 SettlementPostingPreviewDTO；preview 阶段不会写入 ledger_txn / ledger_posting。
 */
export interface SettlementPostingPreview {
  /** 受影响账户 ID；账户将在结算时自动创建时可能为空 */
  accountId?: number
  /** 受影响账户名称 */
  accountName?: string
  /** 分录语义类型：CASH / POSITION / RECEIVABLE / FEE / INCOME */
  accountType: 'CASH' | 'POSITION' | 'RECEIVABLE' | 'FEE' | 'INCOME'
  /** 分录方向：DEBIT=增加该账户余额/持仓，CREDIT=减少该账户余额/持仓 */
  postingType: 'DEBIT' | 'CREDIT'
  /** 分录金额 */
  amount?: number
  /** 分录份额（仅 POSITION 分录有意义） */
  shares?: number
  /** 币种 */
  currency?: string
  /** 中文说明，例如「现金账户：+1,000.00 元」 */
  description?: string
}

/**
 * 人工结算只读预览结果。
 * 对应后端 SettlementPreviewDTO；生成预览绝不会写入 settlement_confirm / ledger_txn，
 * 也不改 reserved_amount / initial_shares / order.status。
 */
export interface SettlementPreview {
  orderId: string
  orderType: 'BUY' | 'SELL' | 'SUBSCRIPTION' | 'REDEMPTION'
  /** 订单类型中文名：买入 / 申购 / 卖出 / 赎回 */
  orderTypeLabel?: string
  orderStatus?: string
  productId?: number
  productName?: string
  productCode?: string
  currency?: string
  confirmDate?: string
  navDate?: string
  confirmNav?: number
  confirmShares?: number
  confirmAmount?: number
  confirmFee?: number
  /** 按现有规则计算的份额（BUY / SUBSCRIPTION 有值） */
  computedShares?: number
  /** 按现有规则计算的金额（SELL / REDEMPTION 有值） */
  computedAmount?: number
  /** 订单出资金额合计 */
  totalFundingAmount?: number
  /** 中文警告：不阻断确认，但需要主人知晓 */
  warnings: string[]
  /** 是否允许确认结算；false 时禁止调用 confirm */
  confirmSupported: boolean
  /** 中文阻断原因；confirmSupported=false 时必填 */
  blockingReasons: string[]
  /** 结算影响预览分录（现金 / 持仓 / 手续费） */
  postingsPreview: SettlementPostingPreview[]
  /** 结算影响中文摘要行，可直接展示 */
  summaryLines: string[]
  willCreateSettlementConfirm: boolean
  willCreateLedgerTxn: boolean
  willChangeHolding: boolean
  willChangeCash: boolean
  /** 新鲜预览令牌；confirm 必须原样携带 */
  freshPreviewToken?: string
  previewFingerprint?: string
}

/**
 * 人工结算预览 / 确认请求。
 * preview 与 confirm 共用同一份输入；confirm 必须携带 preview 返回的 freshPreviewToken，
 * 任何关键输入 / 订单 / 资金来源 / 账户快照变化都会使旧令牌失效。
 */
export interface SettlementPreviewRequest {
  orderId: string
  /** 确认日期（ISO，例如 2026-09-28） */
  confirmDate?: string
  /** 净值日期（ISO，例如 2026-09-28） */
  navDate?: string
  confirmNav?: number
  confirmShares?: number
  confirmAmount?: number
  /** 手续费；null/undefined 表示按 BrokerFeeService 估算，明确输入 0 表示使用 0 */
  confirmFee?: number
  /** fresh preview 令牌；仅 confirm 需要携带 */
  freshPreviewToken?: string
  note?: string
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

/**
 * 人工结算 confirm 请求。
 *
 * v0.13.0 起 confirm 与 preview 共用同一份结算输入（SettlementPreviewRequest），
 * 但必须额外携带 preview 返回的 freshPreviewToken，禁止仅凭 orderId 直接结算。
 */
export interface ConfirmSettlementRequest extends SettlementPreviewRequest {
  isManualOverride?: boolean
  /** v0.13.0：confirm 必须携带 preview 返回的 freshPreviewToken，禁止仅凭 orderId 直接结算 */
  freshPreviewToken?: string
}
