/**
 * 家庭API
 */

import { apiClient } from './client'
import type { Family, FamilyMember, SimpleOkResponse } from '../types'

export const familyApi = {
  /**
   * 获取家庭信息（当前用户所属家庭）
   */
  getFamily: async (): Promise<Family> => {
    const response = await apiClient.get<Family>('/families')
    return response.data
  },

  /**
   * 创建家庭
   */
  createFamily: async (data: { familyCode: string; familyName: string }): Promise<Family> => {
    const response = await apiClient.post<Family>('/families', data)
    return response.data
  },

  /**
   * 获取成员列表
   */
  getMembers: async (): Promise<FamilyMember[]> => {
    const response = await apiClient.get<FamilyMember[]>('/families/members')
    return response.data
  },

  /**
   * 添加成员（支持 userId 或 username）
   */
  addMember: async (data: { userId?: number; username?: string; role?: 'ADMIN' | 'MEMBER' }): Promise<void> => {
    await apiClient.post('/families/members', data)
  },

  /**
   * 移除成员
   */
  removeMember: async (userId: number): Promise<SimpleOkResponse> => {
    const response = await apiClient.delete<SimpleOkResponse>(`/families/members/${userId}`)
    return response.data
  },

  /**
   * 更新成员角色
   */
  updateMemberRole: async (userId: number, role: 'ADMIN' | 'MEMBER'): Promise<SimpleOkResponse> => {
    const response = await apiClient.put<SimpleOkResponse>(`/families/members/${userId}/role`, { role })
    return response.data
  },
}
