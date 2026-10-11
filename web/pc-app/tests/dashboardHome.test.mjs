import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { execFileSync } from 'node:child_process'
import ts from 'typescript'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import * as vue from 'vue'

// Offline SFC harness: no browser or business service is accessed.
globalThis.window = { addEventListener() {}, removeEventListener() {} }
globalThis.localStorage = { getItem: () => 'fixture' }

const read = p => readFile(new URL('../src/' + p, import.meta.url), 'utf8')
const imp = s => import(`data:text/javascript,${encodeURIComponent(ts.transpileModule(s, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText)}#${Math.random()}`)
const kpi = await imp(await read('components/dashboardKpiTruthModel.ts'))
globalThis.__homeKpi = kpi
const home = await imp((await read('components/dashboardHomeModel.ts')).replace("import { finite, kpiTruth } from './dashboardKpiTruthModel'", 'const { finite, kpiTruth } = globalThis.__homeKpi'))
const radarModel = await imp(await read('components/financeRadarModel.ts'))
const recent = await imp(await read('components/recentLedgerModel.ts'))
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
const zero = () => ({ accounts: [], overview: { cashBalance: 0, liability: 0 }, holdings: [], prices: new Map() })
const holding = id => ({ productId: id, totalShares: 2, averageCost: 3, channel: 'EXCHANGE' })
const radarFixture = () => ({ date: '2026-10-10', scope: 'PERSONAL', assets: { status: 'OK', netWorth: 0, cashBalance: 0, positionValue: 0 }, counts: { drafts: 0, outbox: null, pendingOrders: 0, awaitingSettlement: 0, reconciliationBroken: 0, reconciliationWarning: 0 }, markets: [], warnings: [] })
const row = id => ({ txnId: id, txnType: 'EXPENSE', requestedAt: '2026-10-10T12:00:00', note: '离线夹具' })

test('home structure preserves verified zero, unknown, partial quotes and negative cash', () => {
  assert.deepEqual(home.assetStructure(zero()).map(r => r.amount.value), [0, 0, 0])
  assert.deepEqual(home.assetStructure(null).map(r => r.amount.value), [null, null, null])
  assert.ok(home.assetStructure(zero(), true).every(r => r.amount.status === 'loading'))
  const s = zero(); s.overview.cashBalance = -5; s.holdings = [holding(1), holding(2)]; s.prices.set(1, 5)
  const amounts = home.assetStructure(s)
  assert.equal(amounts[0].amount.value, -5)
  assert.deepEqual(amounts[1].amount, { status: 'partial', value: 10 })
  const rows = home.homeHoldings(s)
  assert.equal(rows[0].productId, 2); assert.equal(rows[0].unrealizedPnl, null); assert.equal(rows[0].marketValue, null)
  assert.equal(rows[1].unrealizedPnl, 4)
  assert.deepEqual(home.homeHoldings(s, true), [])
  s.holdings = [{ ...holding(2), totalShares: 0 }]
  assert.equal(home.homeHoldings(s)[0].marketValue, 0)
  for (const value of [null, undefined, NaN, Infinity, '0', -1]) {
    s.holdings = [{ ...holding(2), totalShares: value }]
    assert.equal(home.homeHoldings(s)[0].marketValue, null)
  }
})

test('radar overview caps ordinary items but never hides urgent anomalies or unknown checks', () => {
  const fixture = radarFixture()
  fixture.counts.reconciliationBroken = 2; fixture.counts.reconciliationWarning = null
  fixture.markets = [{ productId: 1, status: 'WARNING', indicatorStatus: 'BROKEN' }]
  fixture.warnings = Array.from({ length: 10 }, (_, i) => ({ code: `MARKET_BROKEN_${i}`, status: 'BROKEN', message: `真实异常${i}` }))
  const items = radarModel.radarItems(fixture), summary = radarModel.radarOverview(items)
  assert.equal(summary.urgentCount, 12); assert.equal(summary.priorityItems.length, 12)
  assert.ok(summary.priorityItems.every(item => item.status === 'BROKEN'))
  assert.equal(summary.attentionCount, 13) // includes the unknown device source
  assert.equal(items.find(item => item.id === 'reconciliation').status, 'BROKEN')
  const ordinary = Array.from({ length: 30 }, (_, i) => ({ id: i, status: i % 2 ? 'UNKNOWN' : 'WARNING' }))
  assert.equal(radarModel.radarOverview(ordinary).priorityItems.length, 3)
  assert.equal(radarModel.radarOverview(ordinary).attentionCount, 30)
  assert.equal(radarModel.radarDateStale('2026-10-10', new Date(2026, 9, 10, 23, 59)), false)
  assert.equal(radarModel.radarDateStale('2026-10-10', new Date(2026, 9, 11, 0, 0)), true)
})

test('recent ledger is idle until manual load; empty, error, denied and retry are distinct', async () => {
  let calls = 0, result = { list: [] }, state, failure
  const loader = recent.createRecentLedgerLoader(async () => { calls++; if (failure) throw failure; return result }, () => 'fixture', s => { state = s })
  assert.equal(calls, 0)
  await loader.load(); assert.equal(state.status, 'success'); assert.equal(state.rows.length, 0)
  result = { list: [row('one')] }; await loader.load(); assert.equal(state.rows[0].txnId, 'one')
  failure = { response: { status: 403 } }; await loader.load(); assert.equal(state.status, 'error'); assert.match(state.error, /权限/); assert.deepEqual(state.rows, [])
  failure = new Error('offline'); await loader.load(); assert.match(state.error, /读取失败/)
  failure = null; await loader.load(); assert.equal(state.status, 'success')
  for (const value of [null, {}, { list: null }, { list: [null] }, { list: [row('one'), row('one')] }, { list: Array.from({ length: 6 }, (_, i) => row(String(i))) }]) assert.throws(() => recent.recentLedgerRows(value))
})

test('recent ledger discards old concurrent responses, identity revocation and suspended requests', async () => {
  for (const action of ['newer', 'identity', 'suspend', 'reset']) {
    const wait = deferred(); let calls = 0, owner = 'old', state
    const loader = recent.createRecentLedgerLoader(() => calls++ ? Promise.resolve({ list: [row('new')] }) : wait.promise, () => owner, s => { state = s })
    const old = loader.load()
    if (action === 'newer') await loader.load()
    else if (action === 'identity') owner = 'other'
    else loader[action]()
    wait.resolve({ list: [row('old')] }); await old
    assert.equal(state.status, action === 'newer' ? 'success' : 'idle')
    assert.equal(state.rows[0]?.txnId, action === 'newer' ? 'new' : undefined)
    loader.suspend(); await loader.load(); loader.activate(); assert.equal(calls, action === 'newer' ? 2 : 1)
  }
})

async function setupComponent(file, shared, extra = {}) {
  const user = vue.reactive({ token: 'fixture', user: { id: 1, familyId: 1 } })
  const route = vue.reactive({ fullPath: '/dashboard' })
  const before = [], deactivated = [], activated = [], mounted = [], unmounted = []
  const mocks = {
    vue: { ...vue, onMounted: f => mounted.push(f), onBeforeUnmount: f => before.push(f), onUnmounted: f => unmounted.push(f), onDeactivated: f => deactivated.push(f), onActivated: f => activated.push(f) },
    'vue-router': { useRoute: () => route, useRouter: () => ({ currentRoute: { value: route }, push: extra.push || (() => {}) }) },
    '@wealth-hub/shared': { ...shared, useUserStore: () => user },
    './financeRadarModel': radarModel, '../components/dashboardKpiTruthModel': kpi,
    '../components/dashboardHomeModel': home,
    '../components/pendingSettlementListModel': await imp(await read('components/pendingSettlementListModel.ts')),
    '../components/todayTodoModel': await imp(await read('components/todayTodoModel.ts')),
    'element-plus': extra.element || {},
  }
  globalThis.__homeMocks = mocks
  let script = compileScript(parse(await read(file)).descriptor, { id: 'home-test' }).content
  script = script.replace(/import \{([^}]+)\} from ['"]([^'"]+)['"]/g, (_, names, path) => `const {${names.replace(/ as /g, ': ')}} = globalThis.__homeMocks[${JSON.stringify(path)}]`)
  script = script.replace(/import (\w+) from ['"][^'"]+\.vue['"]/g, (_, name) => `const ${name} = {}`)
  const state = (await imp(script)).default.setup({}, { expose() {} })
  return { state, user, route, mounted, deactivated, activated, cleanup() { for (const f of [...before, ...unmounted]) f() } }
}

test('radar retains error/stale evidence but clears old data on identity change and deactivation', async () => {
  let wait = deferred(), response = radarFixture(), fail = false, calls = 0
  const s = await setupComponent('components/FinanceRadar.vue', { financeRadarApi: { get: async () => { calls++; if (fail) throw Error(fail === true ? 'offline' : fail); return wait ? wait.promise : response } } })
  const old = s.state.load(); s.user.user.familyId = 2; wait.resolve(response); await old
  assert.equal(s.state.radar.value, null); assert.equal(s.state.loading.value, false)
  wait = null; await s.state.load(); s.state.clock.value = new Date(2026, 9, 11); assert.equal(s.state.staleDate.value, true)
  fail = true; await s.state.load(); assert.deepEqual(s.state.radar.value, response); assert.match(s.state.error.value, /读取失败/)
  fail = '403'; await s.state.load(); assert.equal(s.state.radar.value, null); assert.match(s.state.error.value, /无权查看/)
  s.deactivated[0](); assert.equal(s.state.radar.value, null)
  const before = calls; await s.state.load(); assert.equal(calls, before); s.activated[0](); assert.equal(calls, before)
  s.cleanup()
})

test('all original manual settlement types keep preview, blocking, second confirmation and token gates', async () => {
  globalThis.window = { addEventListener() {}, removeEventListener() {} }
  globalThis.localStorage = { getItem: () => 'fixture' }
  const shared = await import('../../shared/dist/index.js')
  for (const type of ['BUY', 'SUBSCRIPTION', 'SELL', 'REDEMPTION']) {
    const events = []; let supported = true, cancelled = false
    const detail = { orderId: 'fixture-order', orderType: type, status: 'PENDING', productId: 1, amount: 10, shares: 2 }
    const s = await setupComponent('views/Dashboard.vue', {
      ...shared, useAccountStore: () => ({ accounts: [], cashLeafAccounts: [], fetchAccounts: async () => {} }), useProductStore: () => ({ products: [], fetchProducts: async () => {} }),
      orderApi: { getOrder: async () => { events.push('detail'); return detail }, confirmSettlement: async input => { assert.equal(input.freshPreviewToken, 'fresh-fixture'); events.push('confirm') } },
      navApi: { getLatestNav: async () => ({ nav: 5 }) },
      settlementApi: { previewSettlement: async () => { events.push('preview'); return { confirmSupported: supported, freshPreviewToken: 'fresh-fixture', blockingReasons: ['blocked'], summaryLines: [], postingsPreview: [] } } },
    }, { element: { ElNotification: { error() {}, warning() {}, success() {} }, ElMessageBox: { confirm: async () => { events.push('owner'); if (cancelled) throw Error('cancel') } } } })
    await s.state.handleConfirmSettlement('fixture-order')
    assert.equal(s.state.settlementVisible.value, true); assert.equal(s.state.settlementOrder.value.orderType, type)
    assert.deepEqual(events, ['detail'])
    supported = false; assert.equal(await s.state.previewAndConfirmSettlement({ orderId: detail.orderId }), false); assert.deepEqual(events.slice(1), ['preview'])
    supported = true; cancelled = true; assert.equal(await s.state.previewAndConfirmSettlement({ orderId: detail.orderId }), false)
    assert.deepEqual(events.slice(-2), ['preview', 'owner']); assert.equal(events.includes('confirm'), false)
    cancelled = false; assert.equal(await s.state.previewAndConfirmSettlement({ orderId: detail.orderId }), true)
    assert.deepEqual(events.slice(-3), ['preview', 'owner', 'confirm'])
    s.cleanup()
  }
})

test('today suggestions discard late requests and distinguish failure from verified empty results', async () => {
  const shared = await import('../../shared/dist/index.js')
  let wait = deferred(), fail = false
  const s = await setupComponent('views/Dashboard.vue', {
    ...shared, useAccountStore: () => ({ accounts: [], cashLeafAccounts: [] }), useProductStore: () => ({ products: [] }),
    dashboardApi: { getTodayActions: async () => { if (fail) throw Error('offline'); return wait ? wait.promise : [] } },
  }, { element: {} })
  const old = s.state.loadTodayActions(); s.user.user.familyId = 2
  wait.resolve([{ id: 'old', type: 'SETTLE_ORDER' }]); await old
  assert.equal(s.state.actionStatus.value, 'idle'); assert.deepEqual(s.state.todayActions.value, [])
  wait = null; await s.state.loadTodayActions(); assert.equal(s.state.actionStatus.value, 'success')
  fail = true; await s.state.loadTodayActions(); assert.equal(s.state.actionStatus.value, 'error'); assert.deepEqual(s.state.todayActions.value, [])
  fail = false; await s.state.loadTodayActions(); assert.equal(s.state.actionStatus.value, 'success')
  s.cleanup()
})

test('home templates compile, show factual contracts and preserve financial handlers verbatim', async () => {
  const files = ['views/Dashboard.vue', 'components/FinanceRadar.vue', 'components/RecentLedgerGlance.vue', 'components/AssetStructureGlance.vue', 'components/MonthlyBudgetGlance.vue', 'components/GoalProgressGlance.vue']
  for (const file of files) {
    const source = await read(file)
    assert.deepEqual(compileTemplate({ source: parse(source).descriptor.template.content, filename: file, id: 'home-test' }).errors, [])
  }
  const dashboard = (await read('views/Dashboard.vue')).replaceAll('\r\n', '\n')
  assert.ok(dashboard.indexOf('<!-- 资产概览KPI -->') < dashboard.indexOf('<FinanceRadar />'))
  for (const title of ['净资产', '可用资金', '持仓市值', '负债', '今日待办', '待结算清单']) assert.ok(dashboard.includes(title))
  for (const entry of ['handleDashboardAmountChange', 'handleDashboardSharesChange', 'handleDashboardNavChange', 'handleSettlementNavDateChange', 'handleConfirmSettlementFromDashboard', 'handleTodayActionClick', 'handleTodoItemClick']) assert.match(dashboard, new RegExp(`@(?:click|change)="${entry}`))
  assert.match(dashboard, /name: 'Holdings'/); assert.match(dashboard, /name: 'DraftInbox'/)
  const baseline = execFileSync('git', ['show', 'e88c3cd43833fd0dadc87224550925f0e8ae1d0f:web/pc-app/src/views/Dashboard.vue'], { encoding: 'utf8' }).replaceAll('\r\n', '\n')
  const handlers = s => s.slice(s.indexOf('// ---------- 今日建议 - 订单结算相关逻辑'), s.indexOf('function updateAllocationChart') >= 0 ? s.indexOf('function updateAllocationChart') : s.indexOf('function handleTodayActionClick')) + s.slice(s.indexOf('function handleTodayActionClick'), s.indexOf('function handleTodoItemClick'))
  assert.equal(handlers(dashboard), handlers(baseline))
  const radar = await read('components/FinanceRadar.vue')
  assert.match(radar, /v-for="item in items"/); assert.match(radar, /JSON.stringify\(radar, null, 2\)/); assert.match(radar, /radar.warnings/); assert.match(radar, /setInterval/)
  const ledger = await read('components/RecentLedgerGlance.vue')
  assert.match(ledger, /getTransactions\(\{ page: 1, pageSize: 5 \}\)/); assert.doesNotMatch(ledger, /onMounted|\.post\(|\.put\(|\.delete\(|summaryAmount|localStorage/)
  const layout = await read('layouts/MainLayout.vue')
  assert.match(layout, /UnifiedEntryModal/)
})
