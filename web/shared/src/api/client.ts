/**
 * Axios客户端配置
 * 统一处理JWT token和错误处理
 */

import axios, { AxiosInstance, AxiosError, InternalAxiosRequestConfig } from 'axios'

const API_BASE_URL = '/api/v2'

// 创建axios实例
export const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器：添加JWT token
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error: AxiosError) => {
    return Promise.reject(error)
  }
)

// 响应拦截器：统一错误处理
apiClient.interceptors.response.use(
  (response) => {
    return response
  },
  (error: AxiosError) => {
    if (error.response) {
      const status = error.response.status
      const data = error.response.data as any

      // 401未授权，清除token并跳转登录
      if (status === 401) {
        localStorage.removeItem('token')
        // 触发自定义事件，让应用层处理跳转
        window.dispatchEvent(new CustomEvent('auth:unauthorized'))
      }

      // 返回错误信息
      const message = data?.message || data?.error || `请求失败: ${status}`
      return Promise.reject(new Error(message))
    }

    // 网络错误
    if (error.request) {
      return Promise.reject(new Error('网络错误，请检查网络连接'))
    }

    // 其他错误
    return Promise.reject(error)
  }
)
