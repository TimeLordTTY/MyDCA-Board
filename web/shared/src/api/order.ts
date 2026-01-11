/**
 * 订单API
 */

import { apiClient } from './client'
import type { Order, OrderDetail, OrderQueryParams, CreateOrderRequest } from '../types'

export const orderApi = {
  /**
   * 获取订单列表
   */
  getOrders: async (params?: OrderQueryParams): Promise<Order[]> => {
    const response = await apiClient.get<Order[]>('/orders', { params })
    return response.data
  },

  /**
   * 获取订单详情
   */
  getOrder: async (orderId: string): Promise<OrderDetail> => {
    const response = await apiClient.get<OrderDetail>(`/orders/${orderId}`)
    return response.data
  },

  /**
   * 创建订单
   */
  createOrder: async (data: CreateOrderRequest): Promise<Order> => {
    const response = await apiClient.post<Order>('/orders', data)
    return response.data
  },

  /**
   * 取消订单
   */
  cancelOrder: async (orderId: string): Promise<void> => {
    await apiClient.post(`/orders/${orderId}/cancel`)
  },
}
