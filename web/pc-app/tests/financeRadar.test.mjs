import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const root = new URL('../', import.meta.url)
const source = await readFile(new URL('src/components/financeRadarModel.ts', root), 'utf8')
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2020 } }).outputText
const { radarErrorText, radarItems, warningRoute } = await import(`data:text/javascript,${encodeURIComponent(code)}`)

const fixture = (overrides = {}) => ({
  date: '2026-09-29', scope: 'PERSONAL',
  assets: { status: 'OK', cashBalance: 100, investmentCost: 20, positionValue: 25, liabilities: 0, totalAssets: 125, netWorth: 125 },
  counts: { drafts: 0, outbox: null, pendingOrders: 0, awaitingSettlement: 0, reconciliationWarning: 0, reconciliationBroken: 0 },
  markets: [], warnings: [], ...overrides,
})

test('normal and empty fixtures keep known zero distinct from device unknown', () => {
  const items = radarItems(fixture())
  assert.equal(items.find(i => i.id === 'assets').status, 'OK')
  assert.equal(items.find(i => i.id === 'drafts').detail, '0 项')
  assert.equal(items.find(i => i.id === 'outbox').status, 'UNKNOWN')
  assert.equal(items.find(i => i.id === 'outbox').detail.includes('未知'), true)
})

test('partial unknown, stale market, and broken reconciliation are prioritized', () => {
  const items = radarItems(fixture({
    assets: { ...fixture().assets, status: 'UNKNOWN', netWorth: null },
    counts: { ...fixture().counts, pendingOrders: null, reconciliationBroken: 2 },
    markets: [{ productId: 5, status: 'WARNING', priceDate: '2026-09-20', valuationDate: null, indicatorStatus: 'UNKNOWN', indicatorDate: null }],
    warnings: [{ code: 'ACCOUNT_VALUE_UNKNOWN', status: 'UNKNOWN', message: '账户余额未知' }],
  }))
  assert.equal(items[0].id, 'reconciliation')
  assert.equal(items[0].status, 'BROKEN')
  assert.equal(items.find(i => i.id === 'assets').detail.includes('净资产 未知'), true)
  assert.equal(items.find(i => i.id === 'market-5').status, 'WARNING')
  assert.equal(items.find(i => i.id === 'orders').status, 'UNKNOWN')
  assert.equal(items.find(i => i.id === 'warning-0')?.route, 'Accounts')
})

test('drilldown targets exist in protected router and cannot call write APIs', async () => {
  const router = await readFile(new URL('src/router/index.ts', root), 'utf8')
  const api = await readFile(new URL('../shared/src/api/financeRadar.ts', root), 'utf8')
  const component = await readFile(new URL('src/components/FinanceRadar.vue', root), 'utf8')
  for (const item of radarItems(fixture())) assert.match(router, new RegExp(`name: '${item.route}'`))
  assert.match(router, /meta: \{ requiresAuth: true \}/)
  assert.equal(warningRoute('FAMILY_ORDER_SCOPE_UNKNOWN'), 'Orders')
  assert.match(api, /apiClient\.get<FinanceRadar>\('\/finance-radar'/)
  assert.doesNotMatch(api + component, /apiClient\.(post|put|patch|delete)|confirmDraft|confirmSettlement|settleOrder/)
  assert.match(component, /:to="\{ name: item\.route \}"/)
})

test('API and permission failure stay distinct and show retry', async () => {
  const { apiClient, financeRadarApi } = await import('../../shared/dist/index.js')
  const originalGet = apiClient.get
  const calls = []
  apiClient.get = async (path, options) => {
    calls.push({ path, options })
    throw new Error('请求失败: 403')
  }
  try {
    await assert.rejects(financeRadarApi.get(), /403/)
    assert.deepEqual(calls, [{ path: '/finance-radar', options: { params: { scope: 'PERSONAL' } } }])
  } finally {
    apiClient.get = originalGet
  }
  const component = await readFile(new URL('src/components/FinanceRadar.vue', root), 'utf8')
  assert.match(radarErrorText(new Error('网络错误')), /读取失败/)
  assert.match(radarErrorText(new Error('请求失败: 403')), /无权查看/)
  assert.match(radarErrorText(new Error('未授权')), /重新登录/)
  assert.match(component, /v-else-if="error && !radar"/)
  assert.match(component, /role="alert"/)
  assert.match(component, /@click="load">重试/)
  assert.match(component, /以下为上次成功读取的旧快照/)
})
