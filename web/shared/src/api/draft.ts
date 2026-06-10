/**
 * Phase3 草稿流水 API。
 *
 * 这些接口默认只操作草稿表；只有 confirmDraft 会请求后端通过统一记账入口生成正式流水。
 */

import { apiClient } from './client'
import type {
  CreateDraftRequest,
  DraftLedgerEntry,
  DraftPreview,
  DraftQueryParams,
  IgnoreDraftRequest,
  UpdateDraftRequest,
} from '../types'

export const draftApi = {
  /**
   * 创建一条待确认草稿，不触发正式账本入账。
   */
  createDraft: async (data: CreateDraftRequest): Promise<DraftLedgerEntry> => {
    const response = await apiClient.post<DraftLedgerEntry>('/drafts', data)
    return response.data
  },

  /**
   * 查询当前用户或家庭可见草稿列表。
   */
  listDrafts: async (params?: DraftQueryParams): Promise<DraftLedgerEntry[]> => {
    const response = await apiClient.get<DraftLedgerEntry[]>('/drafts', { params })
    return response.data
  },

  /**
   * 查询单条草稿详情。
   */
  getDraft: async (draftId: number): Promise<DraftLedgerEntry> => {
    const response = await apiClient.get<DraftLedgerEntry>(`/drafts/${draftId}`)
    return response.data
  },

  /**
   * 更新 DRAFT 状态草稿候选内容。
   */
  updateDraft: async (draftId: number, data: UpdateDraftRequest): Promise<DraftLedgerEntry> => {
    const response = await apiClient.put<DraftLedgerEntry>(`/drafts/${draftId}`, data)
    return response.data
  },

  /**
   * 生成草稿确认预览，不创建正式流水。
   */
  previewDraft: async (draftId: number): Promise<DraftPreview> => {
    const response = await apiClient.post<DraftPreview>(`/drafts/${draftId}/preview`)
    return response.data
  },

  /**
   * 确认草稿；首版由后端限制为 EXPENSE/INCOME 快速记账。
   */
  confirmDraft: async (draftId: number): Promise<DraftLedgerEntry> => {
    const response = await apiClient.post<DraftLedgerEntry>(`/drafts/${draftId}/confirm`)
    return response.data
  },

  /**
   * 忽略草稿，后端会记录忽略原因并终止该草稿。
   */
  ignoreDraft: async (draftId: number, data?: IgnoreDraftRequest): Promise<DraftLedgerEntry> => {
    const response = await apiClient.post<DraftLedgerEntry>(`/drafts/${draftId}/ignore`, data ?? {})
    return response.data
  },
}
