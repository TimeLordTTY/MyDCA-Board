/**
 * 家庭API
 */

import { apiClient } from './client'
import type { Family, UserFamilyRole } from '../types'

export const familyApi = {
  /**
   * 获取家庭列表
   */
  getFamilies: async (): Promise<Family[]> => {
    const response = await apiClient.get<Family[]>('/families')
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
   * 添加家庭成员
   */
  addMember: async (data: { userId: number; familyId: number; role?: 'ADMIN' | 'MEMBER' }): Promise<UserFamilyRole> => {
    const response = await apiClient.post<UserFamilyRole>('/families/members', data)
    return response.data
  },
}
