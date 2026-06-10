/**
 * Phase3 AI 文本记账类型定义。
 *
 * 这些类型只表示候选解析结果和草稿创建结果；正式入账仍必须走草稿确认接口。
 */

import type { DraftLedgerEntry } from './draft'

/** 文本记账解析请求。 */
export interface ParseTextRequest {
  /** 原始自然语言文本，例如“午饭花了32.5，用余额宝生活费”。 */
  text: string
  /** 外部来源引用，例如 Hermes 消息 ID。 */
  sourceRef?: string
}

/** 文本解析得到的候选记账意图。 */
export interface AccountingIntent {
  /** 草稿来源类型，文本解析 MVP 默认使用 HERMES_TEXT。 */
  sourceType: 'HERMES_TEXT' | string
  /** 外部来源引用。 */
  sourceRef?: string | null
  /** 原始输入文本。 */
  rawInput: string
  /** 候选交易类型，不确定时为空。 */
  txnType?: 'EXPENSE' | 'INCOME' | string | null
  /** 候选金额，不确定时为空。 */
  amount?: number | null
  /** 候选备注。 */
  note?: string | null
  /** 候选账户 ID；首版规则解析通常为空，等待用户补齐。 */
  accountId?: number | null
  /** 账户名称提示，只供复核，不自动当成账户 ID。 */
  accountNameHint?: string | null
  /** 规则解析置信度。 */
  confidence?: number | null
  /** 仍需补齐的字段。 */
  missingFields: string[]
  /** 后端生成的标准化 intent JSON。 */
  parsedPayloadJson?: string | null
}

/** 根据 intent 创建草稿请求。 */
export interface DraftFromIntentRequest {
  /** parse-text 返回或外部安全解析器生成的候选意图。 */
  intent: AccountingIntent
}

/** 根据 intent 创建草稿响应。 */
export interface DraftFromIntentResponse {
  /** 本次用于创建草稿的标准化 intent。 */
  intent: AccountingIntent
  /** 新建的 DRAFT 草稿。 */
  draft: DraftLedgerEntry
}
