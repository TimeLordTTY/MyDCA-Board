import { reactive } from 'vue'

// Shared by the old and new entry surfaces; no automatic financial retries.
export const entryWriteState = reactive({ pending: false, uncertain: false })
export const uncertainWriteMessage = '提交结果尚未确认，请刷新流水及订单核对记录；不要重复提交。核对后重新打开页面。'

export async function financialWrite<T>(action: () => Promise<T>): Promise<T> {
  if (entryWriteState.uncertain) throw new Error(uncertainWriteMessage)
  if (entryWriteState.pending) throw new Error('另一笔操作正在提交，请等待结果')
  entryWriteState.pending = true
  try { return await action() }
  catch (error) {
    const status = (error as { response?: { status?: number } })?.response?.status
    if (status == null || status >= 500 || status === 408) entryWriteState.uncertain = true
    throw error
  } finally { entryWriteState.pending = false }
}

export function guardWrites<T extends object>(api: T, methods: Array<keyof T>, onSuccess?: () => void, beforeWrite?: (method: keyof T) => void): T {
  return new Proxy(api, {
    get(target, key, receiver) {
      const original = Reflect.get(target, key, receiver)
      if (!methods.includes(key as keyof T) || typeof original !== 'function') return original
      return (...args: unknown[]) => {
        beforeWrite?.(key as keyof T)
        return financialWrite(async () => {
          const result = await original.apply(target, args)
          onSuccess?.()
          return result
        })
      }
    },
  })
}
