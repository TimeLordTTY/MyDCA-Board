/**
 * 认证API
 */

import { apiClient } from './client'
import type { AuthRequest, AuthResponse } from '../types'

export const authApi = {
  /**
   * 用户注册
   */
  register: async (data: AuthRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/auth/register', data)
    if (response.data.token) {
      localStorage.setItem('token', response.data.token)
    }
    return response.data
  },

  /**
   * 用户登录
   */
  login: async (data: AuthRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/auth/login', data)
    if (response.data.token) {
      localStorage.setItem('token', response.data.token)
    }
    return response.data
  },

  /**
   * 用户登出
   */
  logout: async (): Promise<void> => {
    localStorage.removeItem('token')
    await apiClient.post('/auth/logout')
  },
}
