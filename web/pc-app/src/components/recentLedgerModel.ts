import type { LedgerTxn } from '@wealth-hub/shared'

export interface RecentLedgerState { status: 'idle' | 'loading' | 'success' | 'error'; rows: LedgerTxn[]; error: string }
export function recentLedgerRows(response: unknown): LedgerTxn[] {
  const list = (response as { list?: unknown } | null)?.list
  if (!Array.isArray(list) || list.length > 5 || list.some(row => !row || typeof row.txnId !== 'string' || !row.txnId || typeof row.txnType !== 'string' || typeof row.requestedAt !== 'string')) {
    throw new Error('流水响应不完整')
  }
  if (new Set(list.map(row => row.txnId)).size !== list.length) throw new Error('流水响应重复')
  return list.map(row => ({ ...row }))
}
export function createRecentLedgerLoader(read: () => Promise<unknown>, identity: () => string, publish: (s: RecentLedgerState) => void) {
  let generation = 0, active = true
  const reset = () => { generation++; publish({ status: 'idle', rows: [], error: '' }) }
  return {
    reset,
    suspend() { active = false; reset() },
    activate() { active = true },
    async load() {
      if (!active) return
      const version = ++generation, owner = identity()
      const valid = () => active && version === generation && owner === identity()
      publish({ status: 'loading', rows: [], error: '' })
      try {
        const rows = recentLedgerRows(await read())
        if (valid()) publish({ status: 'success', rows, error: '' })
      } catch (cause) {
        if (valid()) {
          const status = (cause as { response?: { status?: number } } | null)?.response?.status
          const denied = status === 401 || status === 403 || /401|403|权限|登录/.test(cause instanceof Error ? cause.message : '')
          publish({ status: 'error', rows: [], error: denied ? '登录已失效或没有读取权限，请重新登录后重试。' : '流水读取失败或响应不完整，请手动重试。' })
        }
      } finally { if (active && version === generation && owner !== identity()) reset() }
    },
  }
}
