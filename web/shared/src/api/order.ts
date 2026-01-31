/**
 * 订单API
 */

import { apiClient } from './client'
import type { Order, OrderDetail, OrderQueryParams, CreateOrderRequest, ConfirmSettlementRequest } from '../types'

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

  /**
   * 确认结算
   */
  confirmSettlement: async (data: ConfirmSettlementRequest): Promise<void> => {
    await apiClient.post(`/orders/${data.orderId}/settle`, data)
  },

  /**
   * 计算场内交易手续费
   */
  calculateFee: async (data: {
    productId: number
    accountId?: number
    orderType: 'BUY' | 'SELL'
    amount: number
  }): Promise<{ fee: number; productId: number; orderType: string; amount: number }> => {
    const response = await apiClient.post('/orders/calculate-fee', data)
    return response.data
  },
}
