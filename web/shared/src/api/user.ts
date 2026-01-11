/**
 * 用户API
 */

import { apiClient } from './client'
import type { UserInfo } from '../types'

export const userApi = {
  /**
   * 获取当前用户信息
   */
  getCurrentUser: async (): Promise<UserInfo> => {
    const response = await apiClient.get<UserInfo>('/users/me')
    return response.data
  },
}
