/**
 * Axios客户端配置
 * 统一处理JWT token和错误处理
 */

import axios, { AxiosInstance, AxiosError, InternalAxiosRequestConfig } from 'axios'

// API基础URL
// 开发环境：通过 vite proxy 代理到 http://localhost:8766
// 生产环境：通过 nginx 将 /api/** 代理到后端服务
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

      // 401未授权或403禁止访问（通常表示会话过期），清除token并跳转登录
      if (status === 401 || status === 403) {
        // 清除token
        localStorage.removeItem('token')
        // 自动检测当前部署的 base 路径（如 /wealth-hub/ 或 /wealth-hub-mobile/）
        const basePath = window.location.pathname.match(/^\/[^/]+\//)?.[0] || '/'
        const loginPath = basePath + 'login'
        console.log('Session expired, redirecting to login...', {
          currentPath: window.location.pathname,
          loginPath,
          status
        })
        if (!window.location.pathname.endsWith('/login')) {
          // 使用replace而不是href，避免在历史记录中留下记录
          window.location.replace(loginPath)
        }
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
