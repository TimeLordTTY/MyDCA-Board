import { ref, watch } from 'vue'
import type { Pinia } from 'pinia'
import type { Router } from 'vue-router'
import { apiClient, userApi, useAccountStore, useProductStore, useUserStore } from '@wealth-hub/shared'
import type { InternalAxiosRequestConfig } from 'axios'
import './privateSites'

export const sessionRevision = ref(0)
type SessionConfig = InternalAxiosRequestConfig & { pcSessionToken?: string | null; pcSessionRevision?: number }

// Synchronous event delivery invalidates pending private navigation immediately;
// callers may await all cookie revocations before finishing legacy JWT logout.
export async function endPrivateSession(reason: string, broadcast = true) {
  const detail = { reason, broadcast, pending: [] as Promise<void>[] }
  window.dispatchEvent?.(new CustomEvent('wealth-hub:private-session-end', { detail }))
  await Promise.all(detail.pending)
}

let identityRead = 0

// A transport failure does not revoke a JWT. The response boundary handles
// actual 401/403; only verified identity responses enter the store.
export async function readSessionIdentity(user: ReturnType<typeof useUserStore>) {
  const read = ++identityRead, token = user.token, revision = sessionRevision.value
  const identity = await userApi.getCurrentUser()
  if (read !== identityRead || token !== user.token || revision !== sessionRevision.value || token !== localStorage.getItem('token')) {
    throw new Error('账号会话已切换，请重新读取身份')
  }
  user.user = identity
  return user.user
}

// Only a registered local route may be resumed after authentication.
export function loginDestination(router: Router, value: unknown, portal = false): string {
  const fallback = portal ? '/portal' : '/dashboard'
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || /[\\\u0000-\u0020]/.test(value)) return fallback
  const target = router.resolve(value)
  if (!target.matched.length || !target.meta.requiresAuth || (portal && target.name !== 'Portal')) return fallback
  return target.fullPath
}

export function installSessionBoundary(pinia: Pinia, router: Router) {
  const user = useUserStore(pinia)
  const accounts = useAccountStore(pinia)
  const products = useProductStore(pinia)
  const clearCaches = () => {
    accounts.$patch({ accounts: [], loading: false })
    products.$patch({ products: [], loading: false })
    sessionRevision.value++
  }
  watch(() => user.token, () => { user.user = null; clearCaches(); void endPrivateSession('account-change') }, { flush: 'sync' })
  watch(() => [user.user?.id, user.user?.familyId], (next, previous) => {
    if (previous[0] != null && (next[0] !== previous[0] || next[1] !== previous[1])) {
      clearCaches(); void endPrivateSession('identity-change')
    }
  }, { flush: 'sync' })
  const expire = () => {
    const portal = ['Portal', 'PortalLogin'].includes(String(router.currentRoute.value.name))
    const redirect = router.currentRoute.value.fullPath
    localStorage.removeItem('token')
    user.$patch({ token: null, user: null })
    clearCaches()
    void router.replace({ name: portal ? 'PortalLogin' : 'Login', query: { redirect, reason: 'session' } })
  }
  // Handle auth failures in PC before the shared client's hard redirect.
  const validate = apiClient.defaults.validateStatus
  apiClient.defaults.validateStatus = status => status === 401 || status === 403 || (validate ? validate(status) : status >= 200 && status < 300)
  apiClient.interceptors.request.use(config => {
    if (config.url === '/auth/logout') return endPrivateSession('logout').then(() => {
      ;(config as SessionConfig).pcSessionToken = localStorage.getItem('token')
      ;(config as SessionConfig).pcSessionRevision = sessionRevision.value
      return config
    })
    ;(config as SessionConfig).pcSessionToken = localStorage.getItem('token')
    ;(config as SessionConfig).pcSessionRevision = sessionRevision.value
    return config
  })
  apiClient.interceptors.response.use(response => {
    const config = response.config as SessionConfig
    if (config.pcSessionToken !== localStorage.getItem('token') || config.pcSessionRevision !== sessionRevision.value) throw new Error('账号会话已切换，请重新读取数据')
    if (config.url === '/private-sites/session/exchange') return response
    if (response.status === 401 || response.status === 403) {
      if (!['Login', 'PortalLogin'].includes(String(router.currentRoute.value.name))) expire()
      throw Object.assign(new Error(response.data?.message || '登录已失效或无访问权限，请重新登录'), { response })
    }
    return response
  })
  window.addEventListener('storage', event => {
    if (event.key !== 'token' && event.key !== null) return
    const token = event.key === null ? localStorage.getItem('token') : event.newValue
    void endPrivateSession('other-tab-account-change', false)
    user.$patch({ token, user: null })
    clearCaches()
    if (!token) expire()
    else void router.replace({ name: ['Portal', 'PortalLogin'].includes(String(router.currentRoute.value.name)) ? 'Portal' : 'Dashboard' })
  })
}
