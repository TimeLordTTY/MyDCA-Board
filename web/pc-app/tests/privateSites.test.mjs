import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import * as vue from 'vue'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'

const source = await readFile(new URL('../src/auth/privateSites.ts', import.meta.url), 'utf8')
const sessionSource = await readFile(new URL('../src/auth/session.ts', import.meta.url), 'utf8')
const load = text => import(`data:text/javascript,${encodeURIComponent(ts.transpileModule(text, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText)}#${Math.random()}`)
const pending = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
const tick = () => new Promise(resolve => setImmediate(resolve))
const csrf = 'c'.repeat(43)
const ok = (data = {}) => ({ status: 200, noStore: true, data: { authenticated: true, csrfToken: csrf, expiresAt: new Date(Date.now() + 60000).toISOString(), ...data } })

async function fixture() {
  let token = 'synthetic-owner-a'
  const calls = [], navigations = [], events = [], fetchCalls = [], apiCalls = []
  const target = new EventTarget()
  globalThis.window = { addEventListener: target.addEventListener.bind(target), dispatchEvent: target.dispatchEvent.bind(target), location: { assign: path => navigations.push(path) }, fetch: async (...args) => {
    fetchCalls.push(args)
    return { status: 200, json: async () => ok().data, headers: new Headers({ 'Cache-Control': 'private, no-store' }) }
  } }
  globalThis.localStorage = { getItem: () => token, setItem: (key, value) => events.push({ key, value }), removeItem: () => { token = null } }
  globalThis.__private = { ...vue, apiClient: { post: async (...args) => { apiCalls.push(args); return { status: 200, data: ok().data, headers: { 'cache-control': 'private, no-store' } } } } }
  const module = await load('const { ref, apiClient } = globalThis.__private;\n' + source.replace(/^import .*$/gm, ''))
  const deps = { token: () => token, exchange: async () => { calls.push('exchange'); return ok() }, cookie: async secret => { calls.push(secret ? ['logout', secret] : 'state'); return secret ? { status: 204, noStore: true, data: null } : ok() }, navigate: path => navigations.push(path), publish: reason => events.push(reason) }
  const manager = module.createPrivateSiteSession(deps)
  return { module, manager, deps, calls, navigations, events, fetchCalls, apiCalls, setToken: value => { token = value } }
}

test('verified Owner exchanges afresh and navigates only to fixed explore path', async () => {
  const f = await fixture()
  let identities = 0
  await f.manager.enter(async () => { identities++; return { id: 7001 } })
  await f.manager.enter(async () => { identities++; return { id: 7001 } })
  assert.equal(identities, 2)
  assert.deepEqual(f.calls, ['exchange', 'exchange'])
  assert.deepEqual(f.navigations, ['/explore/', '/explore/'])
  assert.equal(f.manager.busy.value, false)
})

for (const [status, copy] of [[401, /过期/], [403, /仅 Owner/], [404, /尚未启用/], [503, /尚未启用/], [429, /上限/], [500, /请求失败/]]) {
  test(`exchange ${status} stays in portal with Chinese status and supports retry`, async () => {
    const f = await fixture()
    f.deps.exchange = async () => ({ status, noStore: true, data: { message: 'Bearer synthetic-secret' } })
    await f.manager.enter(async () => ({ id: 7002 }))
    assert.equal(f.navigations.length, 0)
    assert.match(f.manager.message.value, copy)
    assert.doesNotMatch(f.manager.message.value, /synthetic-secret/)
    f.deps.exchange = async () => ok()
    await f.manager.enter(async () => ({ id: 7001 }))
    assert.deepEqual(f.navigations, ['/explore/'])
  })
}

test('network failure is retryable; a local token cannot substitute for verified identity', async () => {
  const f = await fixture()
  await f.manager.enter(async () => { throw Error('synthetic-secret') })
  assert.equal(f.calls.length, 0); assert.equal(f.navigations.length, 0)
  assert.match(f.manager.message.value, /网络/)
  f.deps.exchange = async () => { throw Error('request with synthetic-secret') }
  await f.manager.enter(async () => ({ id: 7001 }))
  assert.match(f.manager.message.value, /网络/); assert.doesNotMatch(f.manager.message.value, /synthetic-secret/)
  f.setToken(null)
  await f.manager.enter(async () => { throw Error('must not run') })
  assert.match(f.manager.message.value, /请先登录/)
})

test('pending repeated activation sends one exchange and one navigation', async () => {
  const f = await fixture(), gate = pending()
  f.deps.exchange = () => { f.calls.push('exchange'); return gate.promise }
  const first = f.manager.enter(async () => ({ id: 7001 }))
  await tick()
  const second = f.manager.enter(async () => ({ id: 7001 }))
  assert.equal(first, second); assert.equal(f.manager.busy.value, true)
  gate.resolve(ok()); await first
  assert.deepEqual(f.calls, ['exchange']); assert.equal(f.navigations.length, 1)
})

for (const reply of [ok({ authenticated: false }), ok({ csrfToken: '' }), ok({ expiresAt: 'bad' }), ok({ expiresAt: '2000-01-01T00:00:00Z' }), { ...ok(), noStore: false }, { ...ok(), data: '<html>login</html>' }]) {
  test(`malformed/expired/cacheable success is rejected: ${JSON.stringify(reply.data)}`, async () => {
    const f = await fixture(); f.deps.exchange = async () => reply
    await f.manager.enter(async () => ({ id: 7001 }))
    assert.equal(f.navigations.length, 0); assert.match(f.manager.message.value, /响应无效或已过期/)
  })
}

test('late identity cannot exchange after logout or token switch', async () => {
  const f = await fixture(), identity = pending()
  const entering = f.manager.enter(() => identity.promise)
  await tick()
  f.setToken('synthetic-owner-b')
  const revoking = f.manager.invalidate('account-change')
  identity.resolve({ id: 7001 }); await Promise.all([entering, revoking])
  assert.ok(!f.calls.includes('exchange')); assert.equal(f.navigations.length, 0)
})

test('late exchange is revoked before the next account exchange, including token ABA', async () => {
  const f = await fixture(), exchange = pending()
  f.deps.exchange = () => { f.calls.push('old-exchange'); return exchange.promise }
  const entering = f.manager.enter(async () => ({ id: 7001 })); await tick()
  f.setToken('synthetic-owner-b')
  const revoking = f.manager.invalidate('account-change')
  f.setToken('synthetic-owner-a')
  f.deps.exchange = async () => { f.calls.push('new-exchange'); return ok() }
  const next = f.manager.enter(async () => ({ id: 7001 }))
  exchange.resolve(ok())
  await Promise.all([entering, revoking, next])
  assert.deepEqual(f.calls, ['old-exchange', 'state', ['logout', csrf], 'new-exchange'])
  assert.deepEqual(f.navigations, ['/explore/'])
})

test('logout after reload obtains CSRF through cookie state and reports unconfirmed revocation', async () => {
  const f = await fixture()
  await f.manager.invalidate('logout')
  assert.deepEqual(f.calls, ['state', ['logout', csrf]])
  f.deps.cookie = async () => ({ status: 403, data: null, noStore: true })
  await f.manager.invalidate('logout')
  assert.match(f.manager.message.value, /撤销未获确认/)
  assert.equal(f.navigations.length, 0)
})

test('production transport uses original apiClient only for exchange and fetch cookie CSRF without Bearer', async () => {
  const f = await fixture()
  await f.module.privateSiteSession.enter(async () => ({ id: 7001 }))
  const [path, body, config] = f.apiCalls[0]
  assert.equal(path, '/private-sites/session/exchange'); assert.equal(body, undefined)
  assert.equal(config.headers['X-Private-Site-Request'], '1'); assert.equal(config.validateStatus(403), true)
  await f.module.privateSiteSession.invalidate('logout')
  assert.equal(f.fetchCalls.length, 2)
  for (const [url, config] of f.fetchCalls) {
    assert.match(url, /^\/api\/v2\/private-sites\/session(?:\/logout)?$/)
    assert.equal(config.cache, 'no-store'); assert.equal(config.credentials, 'same-origin')
    assert.equal(config.redirect, 'error'); assert.equal(config.headers.Authorization, undefined)
    assert.equal(config.headers['X-Private-Site-Request'], '1')
  }
  assert.equal(f.fetchCalls[1][1].headers['X-CSRF-Token'], csrf)
  const metadata = JSON.parse(f.events[0].value)
  assert.deepEqual(Object.keys(metadata).sort(), ['nonce', 'reason', 'version'])
  assert.doesNotMatch(f.events[0].value, /synthetic-owner|cccccccc|Bearer/)
})

async function sessionFixture() {
  const f = await fixture(), requests = [], responses = [], navigations = []
  const store = value => { const result = vue.reactive(value); result.$patch = change => Object.assign(result, change); return result }
  const user = store({ token: 'synthetic-owner-a', user: { id: 7001 } })
  const accounts = store({ accounts: [], loading: false }), products = store({ products: [], loading: false })
  const apiClient = { defaults: {}, interceptors: { request: { use: fn => requests.push(fn) }, response: { use: fn => responses.push(fn) } } }
  const userApi = { getCurrentUser: async () => ({ id: 7001 }) }
  const router = { currentRoute: vue.ref({ name: 'Portal', fullPath: '/portal' }), replace: async to => { navigations.push(to) } }
  globalThis.__session = { ...vue, apiClient, userApi, useUserStore: () => user, useAccountStore: () => accounts, useProductStore: () => products }
  const module = await load('const { ref, watch, apiClient, userApi, useUserStore, useAccountStore, useProductStore } = globalThis.__session;\n' + sessionSource.replace(/^import .*$/gm, ''))
  const scope = vue.effectScope(); scope.run(() => module.installSessionBoundary({}, router))
  return { ...f, user, userApi, session: module, request: requests[0], response: responses[0], navigations, stop: () => scope.stop() }
}

test('session boundary preserves financial 401 revocation but private 403 does not log out Wealth Hub', async () => {
  const f = await sessionFixture()
  const config = f.request({ url: '/private-sites/session/exchange' })
  assert.equal(f.response({ config, status: 403 }).status, 403)
  assert.equal(f.user.token, 'synthetic-owner-a')
  const financial = f.request({ url: '/accounts' })
  assert.throws(() => f.response({ config: financial, status: 401, data: {} }), /重新登录/)
  assert.equal(f.user.token, null); assert.equal(f.navigations.at(-1).name, 'PortalLogin')
  await tick(); f.stop()
})

test('identity restoration ignores older reads and responses after token/account changes', async () => {
  const f = await sessionFixture(), first = pending(), second = pending()
  let count = 0; f.userApi.getCurrentUser = () => ++count === 1 ? first.promise : second.promise
  const a = f.session.readSessionIdentity(f.user), b = f.session.readSessionIdentity(f.user)
  second.resolve({ id: 7002 }); await b
  first.resolve({ id: 7001 }); await assert.rejects(a, /已切换/)
  assert.equal(f.user.user.id, 7002)
  const gate = pending(); f.userApi.getCurrentUser = () => gate.promise
  const c = f.session.readSessionIdentity(f.user)
  f.setToken('synthetic-owner-b'); f.user.token = 'synthetic-owner-b'
  gate.resolve({ id: 7001 }); await assert.rejects(c, /已切换/)
  assert.equal(f.user.user, null); await tick(); f.stop()
})

test('legacy logout request awaits private revocation before proceeding', async () => {
  const f = await sessionFixture(), gate = pending()
  window.addEventListener(f.module.PRIVATE_SESSION_END, event => event.detail.pending.push(gate.promise))
  let done = false
  const request = f.request({ url: '/auth/logout' }).then(() => { done = true })
  await tick(); assert.equal(done, false)
  gate.resolve(); await request
  assert.equal(done, true); f.stop()
})

test('response revision rejects token ABA even when the token text matches again', async () => {
  const f = await sessionFixture(), config = f.request({ url: '/private-sites/session/exchange' })
  f.setToken('synthetic-owner-b'); f.user.token = 'synthetic-owner-b'
  f.setToken('synthetic-owner-a'); f.user.token = 'synthetic-owner-a'
  assert.throws(() => f.response({ config, status: 200 }), /已切换/)
  await tick(); f.stop()
})

test('Portal delayed logout cannot clear a newer account; offline logout still clears its own JWT', async () => {
  const portal = await readFile(new URL('../src/views/Portal.vue', import.meta.url), 'utf8')
  let script = compileScript(parse(portal).descriptor, { id: 'portal' }).content
  const ast = ts.createSourceFile('portal.ts', script, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  for (const item of [...ast.statements].reverse()) if (ts.isImportDeclaration(item)) script = script.slice(0, item.getStart(ast)) + script.slice(item.end)
  for (const changeAccount of [false, true]) {
    const gate = pending(), targets = []
    let token = 'synthetic-owner-a', logouts = 0
    const user = { token, user: { id: 7001 }, $patch: value => Object.assign(user, value), logout: async () => { logouts++; token = null; throw Error('offline') } }
    const revision = vue.ref(0)
    globalThis.localStorage = { getItem: () => token, removeItem: () => { token = null } }
    globalThis.__portal = { ...vue, onMounted() {}, onBeforeUnmount() {}, useRouter: () => ({ replace: async to => targets.push(to) }), useUserStore: () => user, endPrivateSession: () => gate.promise, readSessionIdentity: async () => user.user, sessionRevision: revision, privateSiteSession: { busy: vue.ref(false), message: vue.ref(''), enter() {} } }
    const icons = 'const ArrowRight = {}, DataAnalysis = {}, PictureFilled = {}, Reading = {}, Sunny = {}, Iphone = {}, House = {}, Wallet = {}, Document = {}, TrendCharts = {}, Setting = {};\n'
    const compiled = icons + script.replace('export default', 'const { defineComponent: _defineComponent, computed, onMounted, onBeforeUnmount, ref, useRouter, useUserStore, endPrivateSession, readSessionIdentity, sessionRevision, privateSiteSession } = globalThis.__portal; export default')
    const entry = (await load(compiled)).default.setup({}, { expose() {} })
    const loggingOut = entry.handleLogout()
    if (changeAccount) { token = 'synthetic-owner-b'; user.token = token; user.user = { id: 7002 }; revision.value++ }
    gate.resolve(); await loggingOut
    if (changeAccount) { assert.equal(user.token, 'synthetic-owner-b'); assert.equal(user.user.id, 7002); assert.equal(logouts, 0); assert.equal(targets.length, 0) }
    else { assert.equal(user.token, null); assert.equal(token, null); assert.equal(logouts, 1); assert.equal(targets[0].name, 'PortalLogin') }
  }
})

test('Portal retains independent sites and native keyboard button; no URL/JWT handoff', async () => {
  const portal = await readFile(new URL('../src/views/Portal.vue', import.meta.url), 'utf8')
  const descriptor = parse(portal).descriptor
  assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: 'Portal.vue', id: 'private' }).errors, [])
  assert.match(portal, /aria-describedby="explore-status"/); assert.match(portal, /role="status"/)
  assert.match(portal, /各站点分别校验权限/); assert.doesNotMatch(portal, /已接入 Wealth Hub 账号认证/)
  assert.match(portal, /noopener noreferrer/); assert.match(portal, /https:\/\/poem\.timelordtty\.cn\//)
  assert.doesNotMatch(source, /console\.|document\.cookie|URLSearchParams|location\.hash|location\.search/)
})
