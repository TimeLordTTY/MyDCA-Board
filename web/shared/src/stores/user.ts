/**
 * 用户Store
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'
import { userApi, authApi } from '../api'
import type { UserInfo, AuthRequest } from '../types'

export const useUserStore = defineStore('user', () => {
  const user = ref<UserInfo | null>(null)
  const token = ref<string | null>(localStorage.getItem('token'))

  /**
   * 登录
   */
  async function login(data: AuthRequest) {
    const response = await authApi.login(data)
    token.value = response.token
    user.value = response.user
    return response
  }

  /**
   * 注册
   */
  async function register(data: AuthRequest) {
    const response = await authApi.register(data)
    token.value = response.token
    user.value = response.user
    return response
  }

  /**
   * 登出
   */
  async function logout() {
    await authApi.logout()
    token.value = null
    user.value = null
  }

  /**
   * 获取当前用户信息
   */
  async function fetchCurrentUser() {
    try {
      const userInfo = await userApi.getCurrentUser()
      user.value = userInfo
      return userInfo
    } catch (error) {
      // 如果获取失败，清除token
      token.value = null
      user.value = null
      throw error
    }
  }

  /**
   * 检查是否已登录
   */
  function isAuthenticated(): boolean {
    return !!token.value
  }

  return {
    user,
    token,
    login,
    register,
    logout,
    fetchCurrentUser,
    isAuthenticated,
  }
})
