/**
 * Phase3 草稿流水类型定义。
 *
 * 草稿只代表待确认候选记录，确认前不应参与正式流水、账户余额、持仓成本或资产统计。
 */

/** 草稿状态：待确认、已确认或已忽略。 */
export type DraftLedgerStatus = 'DRAFT' | 'CONFIRMED' | 'IGNORED'

/** 不包含原文或完整候选数据的只读生命周期事件。 */
export interface DraftLifecycleEvent {
  id: number
  draftId: number
  eventType: string
  actorUserId: number
  sourceType: string
  statusBefore?: string | null
  statusAfter?: string | null
  summary?: string | null
  createdAt: string
}

/** 草稿来源类型，允许后续扩展微信、导入、OCR 等来源。 */
export type DraftSourceType = 'manual' | 'wechat' | 'import' | 'ocr' | string

/** 后端返回的草稿流水记录。 */
export interface DraftLedgerEntry {
  /** 草稿主键，用于详情、预览、确认和忽略接口。 */
  id: number
  /** 草稿归属用户 ID。 */
  ownerUserId: number
  /** 草稿归属家庭 ID，个人草稿可能为空。 */
  ownerFamilyId?: number | null
  /** 草稿来源类型，例如 manual、wechat、import、ocr。 */
  sourceType: DraftSourceType
  /** 外部来源引用号，例如消息 ID 或导入批次号。 */
  sourceRef?: string | null
  /** 原始输入文本或结构化来源摘要。 */
  rawInput?: string | null
  /** 自动解析得到的候选记账 JSON 字符串。 */
  parsedPayloadJson?: string | null
  /** 服务端生成的确认预览 JSON 字符串。 */
  previewPayloadJson?: string | null
  /** 当前草稿状态。 */
  status: DraftLedgerStatus
  /** 解析置信度，仅用于复核排序和提示。 */
  confidence?: number | null
  /** 缺失字段 JSON 字符串。 */
  missingFieldsJson?: string | null
  /** 确认后关联的正式流水 txn_id。 */
  confirmTxnId?: string | null
  /** 确认后关联的订单 order_id，首版通常为空。 */
  confirmOrderId?: string | null
  /** 忽略原因。 */
  ignoreReason?: string | null
  /** 草稿创建时间。 */
  createdAt?: string | null
  /** 草稿更新时间。 */
  updatedAt?: string | null
  /** 草稿确认时间。 */
  confirmedAt?: string | null
  /** 草稿忽略时间。 */
  ignoredAt?: string | null
}

/** 创建草稿请求，只写入草稿表，不写正式账本。 */
export interface CreateDraftRequest {
  /** 草稿来源类型。 */
  sourceType?: DraftSourceType
  /** 外部来源引用号。 */
  sourceRef?: string
  /** 原始输入内容。 */
  rawInput?: string
  /** 候选记账 JSON 字符串。 */
  parsedPayloadJson?: string
  /** 解析置信度。 */
  confidence?: number
  /** 缺失字段 JSON 字符串。 */
  missingFieldsJson?: string
}

/** 更新草稿请求，仅允许 DRAFT 状态草稿使用。 */
export interface UpdateDraftRequest {
  /** 草稿来源类型。 */
  sourceType?: DraftSourceType
  /** 外部来源引用号。 */
  sourceRef?: string
  /** 原始输入内容。 */
  rawInput?: string
  /** 候选记账 JSON 字符串。 */
  parsedPayloadJson?: string
  /** 解析置信度。 */
  confidence?: number
  /** 缺失字段 JSON 字符串。 */
  missingFieldsJson?: string
}

/** 草稿确认预览，说明当前草稿是否支持首版一键确认。 */
export interface DraftPreview {
  /** 草稿主键。 */
  draftId: number
  /**
   * 候选流水类型：EXPENSE / INCOME / TRANSFER / BUY / SUBSCRIPTION / SELL / REDEMPTION。
   * TRANSFER 需补齐转出与转入账户；BUY / SUBSCRIPTION 需补齐真实产品与单一资金来源账户；
   * SELL / REDEMPTION 需补齐真实产品、持仓来源账户、份额与到账账户。
   */
  txnType?: string | null
  /** 候选转出（来源）账户 ID。 */
  accountId?: number | null
  /** 候选账户名称，用于确认前复核影响对象。 */
  accountName?: string | null
  /** 候选账户类型，例如 CASH、BANK、PAYMENT、MMF。 */
  accountType?: string | null
  /** 候选账户资金用途，例如 SPENDABLE、RESERVED、INVESTABLE。 */
  fundUsage?: string | null
  /** 转账时的转入（目标）账户 ID；非 TRANSFER 草稿为空。 */
  targetAccountId?: number | null
  /** 转账时的转入账户名称，用于确认前复核影响对象。 */
  targetAccountName?: string | null
  /** 转账时的转入账户类型，例如 CASH、BANK、PAYMENT、MMF。 */
  targetAccountType?: string | null
  /** 转账时的转入账户资金用途，例如 SPENDABLE、RESERVED、INVESTABLE。 */
  targetFundUsage?: string | null
  /** 候选金额。 */
  amount?: number | null
  /** 对账户余额的影响方向：DECREASE、INCREASE 或 NONE。 */
  impactDirection?: 'DECREASE' | 'INCREASE' | 'NONE' | string | null
  /** 对候选（转出）账户余额的预计变动金额：支出为负数，收入为正数，转账为负数。 */
  accountDelta?: number | null
  /** 转账时对转入账户余额的预计变动金额，通常为正数；非 TRANSFER 草稿为空。 */
  targetAccountDelta?: number | null
  /** 投资草稿的订单类型：BUY（场内买入）或 SUBSCRIPTION（场外申购）；非投资草稿为空。 */
  orderType?: string | null
  /** 投资草稿选定的真实产品 ID；必须由主人明确选择，绝不由文本自动匹配。 */
  productId?: number | null
  /** 投资草稿产品名称。 */
  productName?: string | null
  /** 投资草稿产品代码。 */
  productCode?: string | null
  /** 投资草稿产品资产类型，例如 ETF、FUND、BOND_REPO。 */
  productAssetType?: string | null
  /** 投资草稿产品币种，必须与付款账户币种一致。 */
  productCurrency?: string | null
  /** 付款账户在本次确认前的可用余额（余额 - 已占用）。 */
  availableBefore?: number | null
  /** 确认后待结算应收的变动金额；买入 / 申购为正数。 */
  receivableDelta?: number | null
  /** 卖出 / 赎回草稿本次要卖出或赎回的份额。 */
  shares?: number | null
  /** 卖出 / 赎回草稿持仓来源账户当前可用份额（已扣除同产品 / 来源下仍为 PENDING 的占用份额）。 */
  availableShares?: number | null
  /** 卖出 / 赎回草稿确认后预计剩余可用份额。 */
  remainingShares?: number | null
  /** 卖出 / 赎回中文提示，说明确认只创建内部 PENDING 记录、不立即减少持仓。 */
  sharesMessage?: string | null
  /** 预计净值日（可选）。 */
  expectedNavDate?: string | null
  /** 预计确认日（可选）。 */
  expectedConfirmDate?: string | null
  /** 资金来源中文提示，便于确认前复核付款账户与可用余额。 */
  fundingMessage?: string | null
  /**
   * 确认后是否会生成正式流水；预览阶段始终不会写正式账本。
   * BUY / SUBSCRIPTION 确认会立即生成付款账本（付款账户 CASH CREDIT + 待结算应收 RECEIVABLE DEBIT）；
   * SELL / REDEMPTION 确认不会生成任何账本流水。
   */
  willCreateLedgerTxn?: boolean | null
  /**
   * 草稿确认是否会生成订单；BUY / SUBSCRIPTION / SELL / REDEMPTION 确认后会创建系统内 PENDING 订单。
   * SELL / REDEMPTION 订单只登记内部待处理份额占用，不生成账本、不改现金余额、不改持仓。
   */
  willCreateOrder?: boolean | null
  /** 草稿确认是否会生成结算记录；投资买入 / 申购 / 卖出 / 赎回当前都不会自动结算。 */
  willCreateSettlement?: boolean | null
  /** 首版草稿确认是否会影响持仓；SELL / REDEMPTION 确认不会立即影响持仓。 */
  willAffectHolding?: boolean | null
  /** 候选备注。 */
  note?: string | null
  /** 是否支持直接确认。 */
  confirmSupported: boolean
  /** 预览提示信息。 */
  message?: string | null
  /** 缺失字段列表。 */
  missingFields?: string[]
  /** 预览阶段提示或风险说明。 */
  warnings?: string[]
}

/** 草稿列表查询参数。 */
export interface DraftQueryParams {
  /** 可选状态过滤。 */
  status?: DraftLedgerStatus
  /** 页码，从 1 开始。 */
  page?: number
  /** 每页条数。 */
  pageSize?: number
}

/** 忽略草稿请求。 */
export interface IgnoreDraftRequest {
  /** 忽略原因。 */
  ignoreReason?: string
}
