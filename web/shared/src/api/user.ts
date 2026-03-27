/**
 * 用户API
 */

import { apiClient } from './client'
import type { ChangePasswordRequest, SimpleOkResponse, UpdateProfileRequest, UserInfo } from '../types'

export const userApi = {
  /**
   * 获取当前用户信息
   */
  getCurrentUser: async (): Promise<UserInfo> => {
    const response = await apiClient.get<UserInfo>('/users/me')
    return response.data
  },

  /**
   * 更新当前用户资料
   */
  updateProfile: async (data: UpdateProfileRequest): Promise<UserInfo> => {
    const response = await apiClient.put<UserInfo>('/users/me', data)
    return response.data
  },

  /**
   * 修改密码
   */
  changePassword: async (data: ChangePasswordRequest): Promise<SimpleOkResponse> => {
    const response = await apiClient.post<SimpleOkResponse>('/users/change-password', data)
    return response.data
  },
}
