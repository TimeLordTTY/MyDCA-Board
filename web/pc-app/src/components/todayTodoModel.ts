import type { TodayTodo, TodoItem } from '@wealth-hub/shared'

// Current contract covers DRAFT only, with at most 20 detail rows.
export function validateTodayTodo(value: unknown): TodayTodo {
  const data = value as TodayTodo | null
  const count = (n: unknown) => typeof n === 'number' && Number.isSafeInteger(n) && n >= 0
  if (!data || typeof data.date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(data.date)
    || !Number.isFinite(Date.parse(data.date)) || new Date(data.date).toISOString().slice(0, 10) !== data.date
    || ![data.totalCount, data.draftCount, data.settlementCount, data.suggestionCount].every(count)
    || data.settlementCount !== 0 || data.suggestionCount !== 0 || data.totalCount !== data.draftCount
    || !Array.isArray(data.items) || data.items.length !== Math.min(data.draftCount, 20)) {
    throw new Error('invalid todo contract')
  }
  const ids = new Set<string>()
  for (const item of data.items) {
    if (!draftDestination(item) || typeof item.title !== 'string' || !item.title.trim()
      || (item.description != null && typeof item.description !== 'string')
      || (item.status != null && item.status !== 'DRAFT')
      || (item.actionPath != null && typeof item.actionPath !== 'string') || ids.has(item.refId)) {
      throw new Error('invalid todo item')
    }
    ids.add(item.refId)
  }
  return data
}

export function draftDestination(item: TodoItem) {
  return item && item.type === 'DRAFT' && typeof item.refId === 'string' && /^[1-9]\d*$/.test(item.refId)
    ? { name: 'DraftInbox', query: { draftId: item.refId } } : null
}

export type TodoState = { status: 'idle' | 'loading' | 'success' | 'error'; data: TodayTodo | null }
export function createTodayTodoLoader(read: () => Promise<unknown>, identity: () => string, publish: (state: TodoState) => void) {
  let generation = 0
  let active = true
  const reset = () => { generation++; publish({ status: 'idle', data: null }) }
  const suspend = () => { active = false; reset() }
  const activate = () => { active = true }
  const load = async () => {
    if (!active) return
    const version = ++generation, owner = identity(), day = new Date().toDateString()
    publish({ status: 'loading', data: null })
    const current = () => version === generation && owner === identity() && day === new Date().toDateString()
    try {
      const data = validateTodayTodo(await read())
      if (current()) publish({ status: 'success', data })
      else if (version === generation) reset()
    } catch {
      if (current()) publish({ status: 'error', data: null })
      else if (version === generation) reset()
    }
  }
  return { load, reset, suspend, activate }
}
