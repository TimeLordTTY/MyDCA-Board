import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import * as vue from 'vue'

const read = path => readFile(new URL(`../src/${path}`, import.meta.url), 'utf8')
const load = source => import(`data:text/javascript,${encodeURIComponent(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText)}#${Math.random()}`)
const navigation = await load(await read('layouts/navigation.ts'))
const layout = await read('layouts/MainLayout.vue')

test('all 14 business routes reachable once; no unsupported links; router base and JWT guard preserved', async () => {
  const router = await read('router/index.ts')
  const routes = [...router.matchAll(/path: '([^']+)',\s*name: '([^']+)'/g)].filter(r => !r[1].startsWith('/'))
  assert.equal(routes.length, 14)
  assert.equal(navigation.navItems.length, 14)
  assert.equal(new Set(navigation.navItems.map(n => n.path)).size, 14)
  for (const [, path, name] of routes) assert.ok(navigation.navItems.some(n => n.name === name && n.path === `/${path}`))
  assert.match(router, /createWebHistory\(import.meta.env.BASE_URL\)/)
  assert.match(router, /to.meta.requiresAuth && !token/)
  assert.match(router, /redirect: to.fullPath/)
  assert.match(await readFile(new URL('../vite.config.ts', import.meta.url), 'utf8'), /base: '\/wealth-hub\/'/)
})

test('identity displays known family association without guessing role or active scope', () => {
  assert.match(navigation.identityContext(null).account, /未确认/)
  assert.match(navigation.identityContext({ username: '测试', familyId: 7 }).scope, /家庭 #7.*以页面选择为准/)
  assert.match(navigation.identityContext({ username: '测试' }).scope, /个人账号/)
  assert.deepEqual(navigation.identityContext({ username: 'test', nickname: '昵称' }).account, '昵称（test）')
  assert.doesNotMatch(JSON.stringify(navigation.identityContext({ username: 'test', familyId: 1 })), /ADMIN|管理员/)
})

async function setup({ logoutError = false } = {}) {
  const calls = [], mounted = [], disposed = []
  const route = vue.reactive({ name: 'Dashboard', fullPath: '/dashboard' })
  const store = { user: null, token: 'test-only', async fetchCurrentUser() { calls.push('identity') }, async logout() { calls.push('logout'); if (logoutError) throw Error('offline') }, $patch(v) { Object.assign(this, v) } }
  globalThis.window = { matchMedia: () => ({ matches: false, addEventListener() {}, removeEventListener() {} }), dispatchEvent: e => calls.push(e.type) }
  globalThis.localStorage = { removeItem: key => calls.push(`remove:${key}`) }
  globalThis.__shell = {
    vue: { ...vue, onMounted: cb => mounted.push(cb), onUnmounted: cb => disposed.push(cb) },
    useRoute: () => route, useRouter: () => ({ replace: async target => calls.push(target.name) }),
    ElNotification: { info() {}, success() {}, error() {} },
    productApi: {}, apiClient: {}, ledgerApi: { recalculateAllBalanceHistory: async () => { calls.push('recalculate'); return { message: 'mock', durationMs: 0 } } },
    useUserStore: () => store, ...navigation,
  }
  let script = compileScript(parse(layout).descriptor, { id: 'shell' }).content
  script = script.replace(/import \{([^}]+)\} from ['"]vue['"]/g, (_, names) => `const {${names.replace(/ as /g, ': ')}} = globalThis.__shell.vue`)
    .replace(/import \{([^}]+)\} from ['"][^'"]+['"]/g, (_, names) => `const {${names}} = globalThis.__shell`)
    .replace(/^import (\w+) from .*$/gm, (_, name) => `const ${name} = {}`)
    .replace(/^import .*$/gm, '')
  const scope = vue.effectScope()
  const component = (await load(script)).default
  const result = scope.run(() => component.setup({}, { expose() {} }))
  return { state: result, route, store, calls, mounted, dispose: () => { disposed.forEach(cb => cb()); scope.stop() } }
}

test('mount, navigation and layout controls never trigger historical writes; refresh is manual', async () => {
  const f = await setup()
  await Promise.all(f.mounted.map(cb => cb()))
  assert.deepEqual(f.calls, ['identity'])
  f.state.mobileMenuOpen.value = true
  f.route.name = 'GoalBudgetCenter'; f.route.fullPath = '/goal-budget-center'
  await vue.nextTick()
  assert.equal(f.state.mobileMenuOpen.value, false)
  assert.equal(f.state.showBusinessActions.value, false)
  f.route.name = 'DataReadiness'; await vue.nextTick(); assert.equal(f.state.showBusinessActions.value, false)
  f.route.name = 'StrategyLab'; await vue.nextTick(); assert.equal(f.state.showBusinessActions.value, false)
  f.route.name = 'Ledger'; await vue.nextTick(); assert.equal(f.state.showBusinessActions.value, true)
  assert.deepEqual(f.calls, ['identity'])
  await f.state.handleRefresh()
  assert.equal(f.calls.filter(c => c === 'recalculate').length, 1)
  assert.equal(f.state.refreshingData.value, false)
  f.dispose()
})

test('logout clears credentials and store, and replaces protected history even offline', async () => {
  for (const logoutError of [false, true]) {
    const f = await setup({ logoutError })
    await f.state.handleLogout()
    assert.deepEqual(f.calls, ['logout', 'remove:token', 'Login'])
    assert.equal(f.store.user, null); assert.equal(f.store.token, null)
    assert.equal(f.state.loggingOut.value, false)
    f.dispose()
  }
})

test('Vue templates compile; accessible navigation, drawer, theme tokens and scrolling boundaries', async () => {
  for (const path of ['layouts/MainLayout.vue', 'components/WealthNavigation.vue']) {
    const source = await read(path), descriptor = parse(source).descriptor
    assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: path, id: path }).errors, [])
  }
  const links = await read('components/WealthNavigation.vue')
  assert.match(links, /:aria-label="item.label"/); assert.match(links, /:aria-current=/)
  assert.match(layout, /:aria-expanded="mobileMenuOpen"/)
  assert.match(layout, /@closed="menuButton\?\.focus\(\)"/)
  assert.match(layout, /watch\(\(\) => route.fullPath/)
  const css = await read('styles/navigation-shell.css')
  assert.match(css, /grid-template-columns: 82px minmax\(0, 1fr\)/)
  assert.match(css, /\.wealth-rail:focus-within/)
  assert.match(css, /\.wealth-content \{[^}]*overflow: auto/)
  assert.match(css, /\.wealth-header \{[^}]*flex-shrink: 0/)
  assert.doesNotMatch(css, /position: sticky|#[0-9a-f]{6}/i)
})
