/**
 * 结算API
 */

import { apiClient } from './client'
import type { Order, ConfirmSettlementRequest, SettlementPreview, SettlementPreviewRequest } from '../types'

export const settlementApi = {
  /**
   * 获取待结算清单（返回Order列表）
   */
  getPendingSettlements: async (): Promise<Order[]> => {
    const response = await apiClient.get<Order[]>('/settlements/pending')
    return response.data
  },

  /**
   * 生成只读结算预览。
   *
   * 该接口不写 settlement_confirm / ledger_txn，也不改 reserved_amount / initial_shares / order.status，
   * 返回现金 / 持仓 / 手续费影响以及 freshPreviewToken。主人确认前必须先调用本接口。
   */
  previewSettlement: async (data: SettlementPreviewRequest): Promise<SettlementPreview> => {
    const response = await apiClient.post<SettlementPreview>('/settlements/preview', data)
    return response.data
  },

  /**
   * 确认结算（真实落账）。
   *
   * 必须携带 previewSettlement 返回的 freshPreviewToken；服务端会重新计算并比对指纹，
   * 任何关键输入 / 订单 / 资金来源 / 账户快照变化都会阻断本次确认。
   */
  confirmSettlement: async (data: ConfirmSettlementRequest): Promise<void> => {
    await apiClient.post('/settlements/confirm', data)
  },
}