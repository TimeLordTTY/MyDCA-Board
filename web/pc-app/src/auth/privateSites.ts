import { ref } from 'vue'
import { apiClient } from '@wealth-hub/shared'

export const PRIVATE_SESSION_PATH = '/private-sites/session'
export const PRIVATE_SESSION_EVENT = 'wealth-hub:private-session-invalidated'
export const PRIVATE_SESSION_STORAGE_KEY = 'wealth-hub:private-session-event'
export const PRIVATE_SESSION_END = 'wealth-hub:private-session-end'
export const EXPLORE_DESTINATION = '/explore/'

type Reply = { status: number; data: unknown; noStore: boolean }
type PrivateState = { authenticated: true; csrfToken: string; expiresAt: string }
type Dependencies = {
  token: () => string | null
  exchange: () => Promise<Reply>
  cookie: (logoutCsrf?: string) => Promise<Reply>
  navigate: (path: string) => void
  publish: (reason: string) => void
}

class PrivateSessionError extends Error {}

function state(reply: Reply): PrivateState {
  const value = reply.data as Partial<PrivateState> | null
  if (reply.status !== 200 || !reply.noStore || value?.authenticated !== true
    || typeof value.csrfToken !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(value.csrfToken)
    || typeof value.expiresAt !== 'string' || !(Date.parse(value.expiresAt) > Date.now())) {
    throw new PrivateSessionError('私有会话响应无效或已过期，请重试；尚未进入无限探索。')
  }
  return value as PrivateState
}

function failure(status: number): string {
  if (status === 401) return '登录身份或私有会话已过期，请重新登录后重试。'
  if (status === 403) return '当前账号无无限探索访问权限，或请求来源校验未通过；仅 Owner 可进入。'
  if (status === 404 || status === 503) return '无限探索会话服务尚未启用或暂不可用，请稍后重试。'
  if (status === 429) return '私有会话数量已达上限，请稍后重试。'
  return '无限探索会话服务请求失败，请稍后重试。'
}

// Serializing cookie mutations also orders revocation after any delayed exchange.
// A revision invalidates navigation immediately, before network cleanup finishes.
export function createPrivateSiteSession(deps: Dependencies) {
  const busy = ref(false), message = ref('验证当前账号后可进入，仅 Owner 获准访问。')
  let revision = 0, queue: Promise<unknown> = Promise.resolve(), active: Promise<void> | null = null
  const serial = <T>(work: () => Promise<T>): Promise<T> => {
    const result = queue.then(work, work)
    queue = result.catch(() => {})
    return result
  }
  const revoke = async () => {
    try {
      // Read the current cookie's CSRF, including after a page reload or late exchange.
      const reply = await deps.cookie()
      if (reply.status === 401 && reply.noStore) return true
      const current = state(reply)
      const result = await deps.cookie(current.csrfToken)
      return result.noStore && (result.status === 204 || result.status === 401)
    } catch { return false }
  }
  function invalidate(reason: string, broadcast = true): Promise<void> {
    const current = ++revision
    active = null; busy.value = false
    message.value = '私有会话接续已清除，请验证当前账号后重试。'
    if (broadcast) deps.publish(reason)
    return serial(async () => {
      const revoked = await revoke()
      if (current === revision && !revoked) message.value = '本地接续已清除；私有会话撤销未获确认，请勿继续使用已打开的私有页面，恢复连接后重试。'
    })
  }
  function enter(identity: () => Promise<unknown>): Promise<void> {
    if (active) return active
    const current = revision, token = deps.token()
    if (!token) { message.value = '请先登录并验证当前账号。'; return Promise.resolve() }
    const valid = () => current === revision && !!token && deps.token() === token
    busy.value = true
    message.value = '正在验证账号并接续私有会话…'
    const operation = serial(async () => {
      try {
        if (!valid()) return
        const user = await identity() as { id?: unknown } | null
        if (!valid()) return
        if (user?.id == null) throw new PrivateSessionError('账号身份恢复失败，请重试读取账号。')
        const reply = await deps.exchange()
        if (!valid()) return
        if (reply.status !== 200) throw new PrivateSessionError(failure(reply.status))
        state(reply)
        // Never reuse a previous success, server-supplied redirect or URL credential.
        deps.navigate(EXPLORE_DESTINATION)
      } catch (error) {
        if (valid()) message.value = error instanceof PrivateSessionError
          ? error.message : '网络不可达或会话接续失败，请检查网络后重试；尚未进入无限探索。'
      } finally {
        if (current === revision) { busy.value = false; active = null }
      }
    })
    active = operation
    return operation
  }
  return { busy, message, enter, invalidate }
}

const noStore = (value: unknown) => typeof value === 'string' && /(?:^|,)\s*no-store\s*(?:,|$)/i.test(value)

export const privateSiteSession = createPrivateSiteSession({
  token: () => localStorage.getItem('token'),
  exchange: async () => {
    const reply = await apiClient.post(PRIVATE_SESSION_PATH + '/exchange', undefined, {
      headers: { 'X-Private-Site-Request': '1', 'Cache-Control': 'no-store' },
      // Private ACL failures must not be treated as financial API JWT failures.
      validateStatus: () => true,
    })
    return { status: reply.status, data: reply.data, noStore: noStore(reply.headers['cache-control']) }
  },
  cookie: async csrf => {
    // The shared client's Bearer interceptor is deliberately absent here: the
    // delivered server rejects Authorization on cookie state/logout requests.
    const reply = await window.fetch('/api/v2' + PRIVATE_SESSION_PATH + (csrf ? '/logout' : ''), {
      method: csrf ? 'POST' : 'GET', credentials: 'same-origin', cache: 'no-store',
      redirect: 'error', referrerPolicy: 'same-origin', signal: AbortSignal.timeout(10000),
      headers: { 'X-Private-Site-Request': '1', ...(csrf ? { 'X-CSRF-Token': csrf } : {}) },
    })
    return { status: reply.status, data: reply.status === 200 ? await reply.json() : null, noStore: noStore(reply.headers.get('cache-control')) }
  },
  navigate: path => window.location.assign(path),
  publish: reason => {
    // Metadata only. No identity, JWT, cookie ID or CSRF is persisted/broadcast.
    const event = { version: 1, reason, nonce: crypto.randomUUID() }
    window.dispatchEvent(new CustomEvent(PRIVATE_SESSION_EVENT, { detail: event }))
    try { localStorage.setItem(PRIVATE_SESSION_STORAGE_KEY, JSON.stringify(event)) } catch { /* Same-tab invalidation still applies. */ }
  },
})

window.addEventListener(PRIVATE_SESSION_END, event => {
  const detail = (event as CustomEvent<{ reason: string; broadcast: boolean; pending: Promise<void>[] }>).detail
  detail.pending.push(privateSiteSession.invalidate(detail.reason, detail.broadcast))
})
window.addEventListener('storage', event => {
  if (event.key === PRIVATE_SESSION_STORAGE_KEY) void privateSiteSession.invalidate('other-tab', false)
})
