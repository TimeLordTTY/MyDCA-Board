import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import * as vue from 'vue'
const read = name => readFile(new URL(`../src/${name}`, import.meta.url), 'utf8')
const moduleOf = code => import(`data:text/javascript;base64,${Buffer.from(ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.ESNext } }).outputText).toString('base64')}`)
const model = await moduleOf(await read('views/holdingsViewModel.ts'))
const management = await moduleOf(await read('views/managementViewModel.ts'))
const shared = await import('../../shared/dist/index.js')
const base = extra => ({ productId: 1, productName: '测试基金', productCode: 'T1', channel: 'OTC', assetType: 'FUND', brokerAccountId: 10, brokerAccountName: '来源A', totalShares: 10, totalCost: 100, avgCost: 10, marketValue: 90, unrealizedPnl: -10, ...extra })
const nav = extra => ({ productId: 1, nav: 9, navDate: '2026-10-10', source: 'fixture', ...extra })
const quote = extra => ({ productId: 1, price: 12, quoteTime: '2026-10-10T12:00:00+08:00', source: 'fixture', ...extra })
const view = (h, q, n) => model.holdingView(h, q, n, '2026-10-11')
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
async function setup(file = 'views/Holdings.vue', overrides = {}, props = {}) {
  const user = vue.reactive({ token: 'fixture', user: { id: 1, familyId: 1 } })
  const route = vue.reactive({ query: {} }), events = [], listeners = new Map()
  const lifecycle = { mounted: [], unmounted: [], activated: [], deactivated: [] }
  globalThis.window = { addEventListener: (name, fn) => listeners.set(name, fn), removeEventListener: name => listeners.delete(name) }
  globalThis.document = { getElementById: () => null }
  const mocks = {
    vue: { ...vue, onMounted: f => lifecycle.mounted.push(f), onBeforeUnmount: f => lifecycle.unmounted.push(f), onUnmounted: f => lifecycle.unmounted.push(f), onActivated: f => lifecycle.activated.push(f), onDeactivated: f => lifecycle.deactivated.push(f) },
    'vue-router': { useRoute: () => route, useRouter: () => ({ push: path => events.push(path), replace: path => { route.query = path.query; events.push(path) } }) },
    '@wealth-hub/shared': { ...shared, useUserStore: () => user, holdingApi: { getHoldings: async () => [base()] }, marketApi: { getRealtimeQuotes: async () => [], getQuoteHistory: async () => [], getHistoryBars: async () => [] }, navApi: { getLatestNav: async () => nav(), getHistoryNav: async () => [] }, ledgerApi: { getTransactions: async () => ({ list: [] }) }, ...overrides },
    './holdingsViewModel': model, '../views/holdingsViewModel': model, './managementViewModel': management, '../views/managementViewModel': management, echarts: { init: () => ({ setOption() {}, dispose() {}, resize() {} }) },
  }
  globalThis.__holdingMocks = mocks
  const source = await read(file), descriptor = parse(source).descriptor
  assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: file, id: file }).errors, [])
  let code = ts.transpileModule(compileScript(descriptor, { id: file }).content, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.ESNext } }).outputText
  code = code.replace(/import \{([^}]+)\} from ['"]([^'"]+)['"]/g, (_, names, path) => `const {${names.replace(/ as /g, ': ')}} = globalThis.__holdingMocks[${JSON.stringify(path)}]`)
    .replace(/import \* as (\w+) from ['"]([^'"]+)['"]/g, (_, name, path) => `const ${name} = globalThis.__holdingMocks[${JSON.stringify(path)}]`)
    .replace(/import (\w+) from ['"][^'"]+\.vue['"]/g, (_, name) => `const ${name} = {}`)
  const scope = vue.effectScope()
  const state = scope.run(() => moduleOf(code + `\n// fixture ${Math.random()}`)).then(m => scope.run(() => m.default.setup(props, { expose() {}, emit() {} })))
  return { state: await state, user, route, events, listeners, lifecycle, close() { lifecycle.unmounted.forEach(f => f()); scope.stop() } }
}

test('valuation never calculates from quote; missing prices, invalid dates and default zero are unknown', () => {
  for (const evidence of [undefined, nav({ nav: 0 }), nav({ nav: NaN }), nav({ navDate: '2026-11-01' }), nav({ navDate: '2026-02-30' }), nav({ productId: 2 })]) {
    const result = view(base({ marketValue: 0, unrealizedPnl: 0 }), quote(), evidence)
    assert.equal(result.marketValue, null); assert.equal(result.unrealizedPnl, null)
  }
  const exchange = view(base({ channel: 'EXCHANGE' }), quote(), undefined)
  assert.equal(exchange.currentPrice, 12); assert.equal(exchange.marketValue, null)
  assert.equal(view(base(), quote(), nav()).marketValue, 90) // authoritative server amount; no shares * quote
  assert.equal(view(base(), undefined, nav()).unrealizedPnl, -10)
  assert.equal(view(base({ unrealizedPnl: 0 }), undefined, nav()).unrealizedPnl, 0)
  assert.equal(model.pnlTone(0), 'neutral'); assert.equal(model.pnlTone(null), 'neutral'); assert.equal(model.pnlTone(-10), 'loss')
})

test('real zero shares survive legacy aliases; unknown shares stay visible; dates and product types stay distinct', () => {
  const cleared = view(base({ totalShares: 0, shares: 100, marketValue: 0, unrealizedPnl: 0 }), undefined, undefined)
  assert.equal(cleared.sharesCalc, 0); assert.equal(cleared.marketValue, 0); assert.equal(cleared.quality, '已清仓')
  const unknown = view(base({ totalShares: undefined, shares: undefined }), undefined, nav())
  assert.equal(unknown.sharesCalc, null); assert.equal(unknown.marketValue, null)
  const filters = { keyword: '', broker: '', channel: '', productId: '', assetType: '', showCleared: false }
  assert.deepEqual(model.filterHoldings([cleared, unknown], filters), [unknown])
  assert.equal(model.filterHoldings([cleared, unknown], { ...filters, showCleared: true }).length, 2)
  for (const type of ['ETF', 'QDII', 'BOND_REPO', 'MMF']) {
    const result = view(base({ assetType: type, channel: 'EXCHANGE' }), quote({ quoteTime: '2026-10-09T10:00:00' }), nav())
    assert.equal(result.priceTime, '2026-10-09T10:00:00'); assert.equal(result.navDate, '2026-10-10'); assert.equal(result.assetType, type)
  }
  assert.equal(view(base({ channel: 'OTC' }), quote(), nav({ navDate: '2026-09-01' })).priceTime, '2026-09-01')
})

test('legacy array/object/Map preserve product and source; malformed response reports failure', () => {
  for (const data of [[base()], { 1: { ...base(), productId: undefined } }, new Map([[1, { ...base(), productId: undefined }]])]) {
    const result = model.normalizeHoldings(data); assert.equal(result[0].productId, 1); assert.equal(result[0].brokerAccountId, 10)
  }
  for (const data of [null, 'bad', { broken: {} }, [{}]]) assert.throws(() => model.normalizeHoldings(data))
})

test('search, source, product deep link and channel filters compose; reset clears the route', async () => {
  const s = await setup(); await s.state.loadHoldings()
  s.route.query = { productId: '1' }; await vue.nextTick(); assert.equal(s.state.visibleHoldings.value.length, 1)
  s.state.filters.broker = '20'; assert.equal(s.state.visibleHoldings.value.length, 0)
  s.state.resetFilters(); await vue.nextTick(); assert.equal(s.state.visibleHoldings.value.length, 1); assert.equal(s.route.query.productId, undefined)
  s.state.filters.keyword = ' T1 '; assert.equal(s.state.visibleHoldings.value.length, 1)
  s.state.filters.assetType = 'ETF'; assert.equal(s.state.visibleHoldings.value.length, 0)
  s.state.filters.assetType = ''; s.state.filters.channel = 'EXCHANGE'; assert.equal(s.state.visibleHoldings.value.length, 0)
  s.state.viewProduct(s.state.rows.value[0]); assert.deepEqual(s.events.at(-1), { name: 'Products', query: { productId: 1 } })
  s.close()
})

test('empty, failure, denied and partial NAV failures have separate states; old prices are cleared on refresh', async () => {
  let error, data = [base(), base({ productId: 2 })], failNav = false
  const s = await setup('views/Holdings.vue', { holdingApi: { getHoldings: async params => { assert.equal(params, undefined); if (error) throw error; return data } }, navApi: { getLatestNav: async id => { if (failNav && id === 1) throw Error('offline'); return nav({ productId: id }) } } })
  await s.state.loadHoldings(); assert.equal(s.state.rows.value[0].marketValue, 90)
  failNav = true; await s.state.loadHoldings(); assert.equal(s.state.rows.value[0].marketValue, null); assert.equal(s.state.rows.value[1].marketValue, 90); assert.match(s.state.evidenceError.value, /失败/)
  data = []; failNav = false; await s.state.loadHoldings(); assert.equal(s.state.ready.value, true); assert.deepEqual(s.state.rows.value, [])
  for (const value of [Error('offline'), { response: { status: 401 } }, { response: { status: 403 } }]) { error = value; await s.state.loadHoldings(); assert.equal(s.state.ready.value, false); assert.match(s.state.loadError.value, /失败|权限/); assert.deepEqual(s.state.rows.value, []) }
  s.close()
})

test('overlapping refresh and token/user/family/deactivation/unmount discard delayed holdings', async () => {
  for (const action of ['refresh', 'token', 'user', 'family', 'deactivate', 'unmount']) {
    const wait = deferred(); let count = 0
    const s = await setup('views/Holdings.vue', { holdingApi: { getHoldings: () => count++ ? Promise.resolve([base({ productId: 2 })]) : wait.promise } })
    const pending = s.state.loadHoldings()
    if (action === 'refresh') await s.state.loadHoldings()
    else if (action === 'token') s.user.token = 'new'
    else if (action === 'user') s.user.user.id = 2
    else if (action === 'family') s.user.user.familyId = 2
    else if (action === 'deactivate') s.lifecycle.deactivated.forEach(f => f())
    else s.lifecycle.unmounted.forEach(f => f())
    wait.resolve([base()]); await pending
    assert.equal(s.state.rows.value.some(h => h.productId === 1), false)
    assert.equal(s.state.detailVisible.value, false); assert.equal(s.state.importDialogVisible.value, false)
    s.close()
  }
})

test('late NAV response cannot restore values or detail after identity changes; refresh listener is removed', async () => {
  const wait = deferred()
  const s = await setup('views/Holdings.vue', { navApi: { getLatestNav: () => wait.promise } })
  const pending = s.state.loadHoldings(); await vue.nextTick()
  s.user.user.familyId = 2; wait.resolve(nav()); await pending
  assert.deepEqual(s.state.rows.value, []); assert.equal(s.state.ready.value, false)
  s.lifecycle.mounted.forEach(f => f()); assert.equal(s.listeners.has('data-refresh'), true)
  s.close(); assert.equal(s.listeners.has('data-refresh'), false)
})

test('detail prevents delayed product/tab/identity responses and distinguishes read failure from no history', async () => {
  const wait = deferred(); let fail = false
  const props = vue.reactive({ modelValue: true, holding: view(base(), undefined, nav()) })
  const s = await setup('components/HoldingDetailModal.vue', { navApi: { getHistoryNav: () => wait.promise }, ledgerApi: { getTransactions: async params => { assert.equal(params.productId, 2); if (fail) throw Error('offline'); return { list: [] } } } }, props)
  await vue.nextTick(); props.holding = view(base({ productId: 2 }), undefined, nav({ productId: 2 })); s.state.activeTab.value = 'transactions'; await vue.nextTick()
  wait.resolve([nav()]); await s.state.loadActiveTab()
  assert.deepEqual(s.state.navData.value, []); assert.match(s.state.detailError.value, /暂无详情证据/)
  fail = true; await s.state.loadActiveTab(); assert.match(s.state.detailError.value, /读取失败/)
  assert.equal(s.state.displayNumber(undefined, 4), '未知'); assert.equal(s.state.displayMoney(null), '未知'); assert.equal(s.state.displayMoney(0), shared.formatCurrency(0))
  props.modelValue = false; await vue.nextTick(); assert.deepEqual(s.state.transactionHistory.value, [])
  s.close(); assert.equal(s.listeners.has('resize'), false)
})

test('existing detail tabs and import chain remain, no automatic financial writes are introduced', async () => {
  const detail = await read('components/HoldingDetailModal.vue'), page = await read('views/Holdings.vue'), products = await read('views/Products.vue')
  for (const tab of ['净值曲线', 'IOPV/估值', '债券行情', 'K线图', '技术指标', '交易记录']) assert.ok(detail.includes(tab))
  assert.ok(page.includes('InitialHoldingImportModal')); assert.ok(page.includes('@success="loadHoldings"'))
  assert.ok(products.includes('router.currentRoute?.value.query.productId')); assert.ok(products.includes('allProducts.value.find'))
  assert.equal(/importInitialHoldings|\.confirm\(|createOrder|localStorage\.setItem/.test(page + detail), false)
})
