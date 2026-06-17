/**
 * 今日待办 API。
 *
 * 这些接口只读展示待处理事项，不会自动确认草稿或修改正式账本。
 */

import { apiClient } from './client'
import type { TodayTodo } from '../types'

export const todoApi = {
  /** 查询当前用户或家庭可见的今日待办摘要。 */
  getTodayTodos: async (): Promise<TodayTodo> => {
    const response = await apiClient.get<TodayTodo>('/todos/today')
    return response.data
  },
}
