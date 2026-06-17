/**
 * 今日待办类型定义。
 *
 * 待办只用于展示和跳转确认入口，不代表自动执行、自动确认或自动交易。
 */

export type TodoItemType = 'DRAFT' | 'SETTLEMENT' | 'SUGGESTION' | string

export interface TodoItem {
  /** 待办类型，首版主要为 DRAFT。 */
  type: TodoItemType
  /** 关联业务记录 ID。 */
  refId: string
  /** 展示标题。 */
  title: string
  /** 展示摘要。 */
  description?: string | null
  /** 当前业务状态。 */
  status?: string | null
  /** 前端跳转路径，只用于打开确认入口。 */
  actionPath?: string | null
}

export interface TodayTodo {
  /** 服务端日期。 */
  date: string
  /** 待办总数。 */
  totalCount: number
  /** 待确认草稿数量。 */
  draftCount: number
  /** 待结算数量；首版未接入时为 0。 */
  settlementCount: number
  /** 策略建议数量；Phase4 未实现时为 0。 */
  suggestionCount: number
  /** 待办明细。 */
  items: TodoItem[]
}
