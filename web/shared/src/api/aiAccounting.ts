/**
 * Phase3 AI 文本记账 API。
 *
 * parse-text 只生成候选 intent，draft-from-intent 只创建 DRAFT 草稿；二者都不会直接确认入账。
 */

import { apiClient } from './client'
import type {
  AccountingIntent,
  DraftFromIntentRequest,
  DraftFromIntentResponse,
  ParseTextRequest,
} from '../types'

export const aiAccountingApi = {
  /**
   * 将自然语言文本解析成候选记账意图。
   */
  parseText: async (data: ParseTextRequest): Promise<AccountingIntent> => {
    const response = await apiClient.post<AccountingIntent>('/ai/accounting/parse-text', data)
    return response.data
  },

  /**
   * 根据候选 intent 创建 DRAFT 草稿，正式入账仍需用户手动确认。
   */
  draftFromIntent: async (data: DraftFromIntentRequest): Promise<DraftFromIntentResponse> => {
    const response = await apiClient.post<DraftFromIntentResponse>('/ai/accounting/draft-from-intent', data)
    return response.data
  },
}
