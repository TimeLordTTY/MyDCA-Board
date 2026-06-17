/**
 * Phase3 草稿流水类型定义。
 *
 * 草稿只代表待确认候选记录，确认前不应参与正式流水、账户余额、持仓成本或资产统计。
 */

/** 草稿状态：待确认、已确认或已忽略。 */
export type DraftLedgerStatus = 'DRAFT' | 'CONFIRMED' | 'IGNORED'

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
  /** 候选流水类型，首版仅 EXPENSE/INCOME 可确认。 */
  txnType?: string | null
  /** 候选现金账户 ID。 */
  accountId?: number | null
  /** 候选账户名称，用于确认前复核影响对象。 */
  accountName?: string | null
  /** 候选账户类型，例如 CASH、BANK、PAYMENT、MMF。 */
  accountType?: string | null
  /** 候选账户资金用途，例如 SPENDABLE、RESERVED、INVESTABLE。 */
  fundUsage?: string | null
  /** 候选金额。 */
  amount?: number | null
  /** 对账户余额的影响方向：DECREASE、INCREASE 或 NONE。 */
  impactDirection?: 'DECREASE' | 'INCREASE' | 'NONE' | string | null
  /** 对候选账户余额的预计变动金额，支出为负数，收入为正数。 */
  accountDelta?: number | null
  /** 确认后是否会生成正式流水；预览阶段始终不会写正式账本。 */
  willCreateLedgerTxn?: boolean | null
  /** 首版草稿确认是否会生成订单。 */
  willCreateOrder?: boolean | null
  /** 首版草稿确认是否会生成待结算记录。 */
  willCreateSettlement?: boolean | null
  /** 首版草稿确认是否会影响持仓。 */
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
