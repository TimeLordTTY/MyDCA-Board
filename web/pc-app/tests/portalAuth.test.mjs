import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import * as vue from 'vue'

const read = p => readFile(new URL('../src/' + p, import.meta.url), 'utf8')
const load = s => import(`data:text/javascript,${encodeURIComponent(ts.transpileModule(s, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText)}#${Math.random()}`)
const sessionSource = await read('auth/session.ts')
async function sessionFixture() {
  const listeners = {}, requests = [], responses = [], navigations = []
  let token = 'owner-a'
  globalThis.localStorage = { getItem: () => token, removeItem: () => { token = null } }
  globalThis.window = { addEventListener: (name, cb) => { listeners[name] = cb } }
  const store = value => { const state = vue.reactive(value); state.$patch = change => Object.assign(state, change); return state }
  const user = store({ token, user: { id: 1 } }), accounts = store({ accounts: [1], loading: true }), products = store({ products: [1], loading: true })
  const apiClient = { defaults: { validateStatus: s => s >= 200 && s < 300 }, interceptors: { request: { use: fn => requests.push(fn) }, response: { use: fn => responses.push(fn) } } }
  const router = { currentRoute: vue.ref({ name: 'Portal', fullPath: '/portal' }), replace: async to => { navigations.push(to) }, resolve: p => ({ matched: p === '/bad' ? [] : [1], meta: { requiresAuth: !p.includes('login') }, name: p.startsWith('/portal') ? 'Portal' : 'Ledger', fullPath: p }) }
  globalThis.__auth = { ...vue, apiClient, userApi: { getCurrentUser: async () => { throw Error('network') } }, useUserStore: () => user, useAccountStore: () => accounts, useProductStore: () => products }
  const module = await load(sessionSource.replace(/^import .*$/gm, '').replace('export const sessionRevision', 'const { ref, watch, apiClient, userApi, useUserStore, useAccountStore, useProductStore } = globalThis.__auth; export const sessionRevision'))
  const scope = vue.effectScope()
  scope.run(() => module.installSessionBoundary({}, router))
  return { module, user, accounts, products, router, listeners, navigations, request: requests[0], response: responses[0], setToken: t => { token = t }, stop: () => scope.stop(), apiClient }
}

test('registered local deep links retain query/hash; external, auth and unsupported destinations rejected', async () => {
  const f = await sessionFixture()
  for (const bad of ['//outside.invalid', 'https://outside.invalid', '/\\outside', '/bad', '/login', '/portal-login', null, ['x']]) assert.equal(f.module.loginDestination(f.router, bad), '/dashboard')
  assert.equal(f.module.loginDestination(f.router, '/ledger?q=1#row'), '/ledger?q=1#row')
  assert.equal(f.module.loginDestination(f.router, '/ledger', true), '/portal')
  assert.equal(f.module.loginDestination(f.router, '/portal', true), '/portal')
  f.stop()
})
test('401/403 clear shared caches and identity and preserve independent portal login', async () => {
  for (const status of [401, 403]) for (const portal of [true, false]) {
    const f = await sessionFixture()
    f.router.currentRoute.value = { name: portal ? 'Portal' : 'Ledger', fullPath: portal ? '/portal' : '/ledger?q=1' }
    assert.equal(f.apiClient.defaults.validateStatus(status), true)
    assert.equal(f.apiClient.defaults.validateStatus(500), false)
    const config = f.request({})
    assert.throws(() => f.response({ config, status, data: {} }), /重新登录/)
    assert.equal(f.user.token, null); assert.equal(f.user.user, null)
    assert.deepEqual(f.accounts.accounts, []); assert.deepEqual(f.products.products, [])
    assert.equal(f.navigations[0].name, portal ? 'PortalLogin' : 'Login')
    assert.equal(f.navigations[0].query.reason, 'session')
    f.stop()
  }
})
test('late identity/data/denial response cannot cross sessions; token change clears forms and caches', async () => {
  const f = await sessionFixture(), config = f.request({})
  f.setToken('owner-b'); f.user.token = 'owner-b'
  for (const status of [200, 401, 403]) assert.throws(() => f.response({ config, status, data: {} }), /会话已切换/)
  assert.equal(f.navigations.length, 0); assert.equal(f.user.token, 'owner-b'); assert.equal(f.user.user, null)
  assert.deepEqual(f.accounts.accounts, []); assert.deepEqual(f.products.products, [])
  assert.ok(f.module.sessionRevision.value > 0)
  f.stop()
})
test('transport failure retains JWT; cross-tab revocation clears data', async () => {
  const f = await sessionFixture()
  await assert.rejects(() => f.module.readSessionIdentity(f.user), /network/)
  assert.equal(f.user.token, 'owner-a')
  f.listeners.storage({ key: 'token', newValue: null })
  assert.equal(f.user.user, null); assert.deepEqual(f.accounts.accounts, [])
  assert.equal(f.navigations.at(-1).name, 'PortalLogin')
  f.stop()
})

test('login and registration reject double activation while pending, preserve errors and use server auth', async () => {
  const source = await read('components/AuthEntry.vue')
  let resolve, reject, calls = 0, target
  const pending = () => new Promise((a, b) => { resolve = a; reject = b; calls++ })
  globalThis.__entry = { ...vue, useRouter: () => ({ replace: async v => { target = v } }), useRoute: () => ({ query: { redirect: '/ledger?q=1' } }), useUserStore: () => ({ login: pending, register: pending }), loginDestination: (_r, v) => v }
  const script = compileScript(parse(source).descriptor, { id: 'entry' }).content.replace(/^import .*$/gm, '').replace('export default', 'const { defineComponent: _defineComponent, reactive, ref, useRouter, useRoute, useUserStore, loginDestination } = globalThis.__entry; export default')
  const entry = (await load(script)).default.setup({ portal: false }, { expose() {} })
  entry.form.username = 'offline-fixture'; entry.form.password = 'fixture-only'
  const first = entry.submit(false); await entry.submit(false); await entry.submit(true)
  assert.equal(calls, 1); assert.equal(entry.loading.value, true)
  reject(Error('网络错误')); await first
  assert.match(entry.error.value, /手动重试.*不会自动重复提交/)
  assert.equal(entry.form.password, 'fixture-only')
  const second = entry.submit(true); resolve(); await second
  assert.equal(calls, 2); assert.equal(target, '/ledger?q=1'); assert.equal(entry.form.password, '')
})
test('auth/portal templates compile with labels, live feedback and scrolling; no fake financial samples', async () => {
  for (const p of ['components/AuthEntry.vue', 'views/Login.vue', 'views/PortalLogin.vue', 'views/Portal.vue']) {
    const d = parse(await read(p)).descriptor
    assert.deepEqual(compileTemplate({ source: d.template.content, filename: p, id: p }).errors, [])
  }
  const entry = await read('components/AuthEntry.vue'), portal = await read('views/Portal.vue')
  assert.match(entry, /autocomplete="username"/); assert.match(entry, /autocomplete="current-password"/)
  assert.match(entry, /for="auth-password"/); assert.match(entry, /role="alert"/)
  assert.doesNotMatch(entry, /keyup.enter|setTimeout|localStorage/)
  assert.match(portal, /noopener noreferrer/); assert.match(portal, /PortalLogin/)
  assert.match(portal, /href: 'https:\/\/games\.timelordtty\.cn\/games'/)
  assert.match(portal, /title: '游戏墙',[\s\S]*?external: true/)
  assert.doesNotMatch(portal, /\/game-wall\/games/)
  assert.match(await read('styles/auth-portal.css'), /height: 100dvh; overflow: auto/)
})
