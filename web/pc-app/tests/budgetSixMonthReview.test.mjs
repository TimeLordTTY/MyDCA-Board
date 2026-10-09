import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as vue from 'vue'
const root = new URL('../', import.meta.url)
const source = await readFile(new URL('src/components/BudgetSixMonthReview.vue', root), 'utf8')
const transpile = s => ts.transpileModule(s, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText
const model = await import(`data:text/javascript,${encodeURIComponent(transpile(await readFile(new URL('src/components/budgetReviewModel.ts', root), 'utf8')))}`)
globalThis.__csvModel = model
const csv = await import(`data:text/javascript,${encodeURIComponent(transpile(await readFile(new URL('src/components/budgetReviewCsv.ts', root), 'utf8')).replace("import { completeActual } from './budgetReviewModel';", 'const { completeActual } = globalThis.__csvModel;'))}`)
const labelsModel = await import(`data:text/javascript,${encodeURIComponent(transpile(await readFile(new URL('src/components/goalBudgetModel.ts', root), 'utf8')))}`)
const row = (id, extra = {}) => ({ id: String(id), createdAt: '2026-10-09', config: { name: '预算', month: '2026-10', scope: 'PERSONAL', currency: 'CNY', items: [], ...extra } })
const comparison = (id, extra = {}) => ({ budgetId: String(id), quality: 'OK', actualIncome: 10, actualExpenses: 5, actualSurplus: 5, remainingBudget: 2, overspent: false, warnings: [], items: [], ...extra })
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
async function setup(api, download) {
  const user = vue.reactive({ token: 'test', user: { id: 1, familyId: 1 } }), props = vue.reactive({ disabled: false, revision: 0 })
  let script = compileScript(parse(source).descriptor, { id: 'review-test' }).content
  script = script.replace(/import \{([^}]+)\} from 'vue'/g, (_, n) => `const {${n.replace(/ as /g, ': ')}} = globalThis.__review.vue`)
  script = script.replace(/import \{([^}]+)\} from '@wealth-hub\/shared'/g, (_, n) => `const {${n}} = globalThis.__review`)
  script = script.replace(/import \{([^}]+)\} from '.\/goalBudgetModel'/g, (_, n) => `const {${n}} = globalThis.__review.labelsModel`)
  script = script.replace(/import \{([^}]+)\} from '.\/budgetReviewModel'/g, (_, n) => `const {${n}} = globalThis.__review.model`)
  script = script.replace(/import \{([^}]+)\} from '.\/budgetReviewCsv'/g, (_, n) => `const {${n}} = globalThis.__review.csv`)
  let cleanup
  const downloads = []
  globalThis.__review = { vue: { ...vue, onBeforeUnmount(fn) { cleanup = fn } }, model, labelsModel, csv: { ...csv, downloadBudgetReviewCsv: download || ((...args) => downloads.push(args)) }, budgetApi: api, useUserStore: () => user }
  const state = (await import(`data:text/javascript,${encodeURIComponent(transpile(script))}#${Math.random()}`)).default.setup(props, { expose() {}, emit() {} })
  return { state, props, user, downloads, cleanup: () => cleanup() }
}

test('CSV export is manual, loaded only, and never requests more data; invalidation blocks old data', async () => {
  for (const invalidate of [s => s.state.toggle(), s => s.state.reset(), s => { s.state.scope.value = 'FAMILY' }, s => { s.state.currency.value = 'USD' }, s => { s.user.user.id++ }, s => { s.user.token = 'changed' }, s => { s.props.disabled = true }, s => s.cleanup()]) {
    let calls = 0
    const s = await setup({ list: async () => { calls++; return [row(1)] }, comparison: async id => { calls++; return comparison(id) } })
    s.state.exportCsv(); s.state.toggle(); s.state.exportCsv(); assert.equal(s.downloads.length, 0)
    const pending = s.state.load(); s.state.exportCsv(); assert.equal(s.downloads.length, 0); await pending
    s.state.exportCsv(); assert.equal(s.downloads.length, 1); assert.equal(calls, 2)
    invalidate(s); s.state.exportCsv(); assert.equal(s.downloads.length, 1); assert.equal(calls, 2)
  }
  const s = await setup({ list: async () => { throw new Error('failed') } })
  s.state.toggle(); await s.state.load(); s.state.exportCsv(); assert.equal(s.downloads.length, 0)
})

test('download failures are reported in Chinese without losing the loaded review', async () => {
  const s = await setup({ list: async () => [], comparison: () => assert.fail() }, () => { throw new Error('denied') })
  s.state.toggle(); await s.state.load()
  s.state.exportCsv(); assert.match(s.state.exportError.value, /CSV 导出失败/)
  assert.equal(s.state.canExport.value, true)
})
test('local calendar: six continuous months across year, leap day, UTC boundary', () => {
  assert.deepEqual(model.recentMonths(new Date(2026, 0, 31)), ['2025-08','2025-09','2025-10','2025-11','2025-12','2026-01'])
  assert.deepEqual(model.recentMonths(new Date(2024, 1, 29)), ['2023-09','2023-10','2023-11','2023-12','2024-01','2024-02'])
  assert.equal(model.recentMonths(new Date(2026, 9, 1, 0, 1)).at(-1), '2026-10')
})
test('all missing actual fields and incomplete quality never produce actual trend or no-overspend', () => {
  assert.equal(model.completeActual(comparison(1)), true)
  for (const quality of ['PARTIAL', 'UNKNOWN', 'UNAVAILABLE']) assert.equal(model.completeActual(comparison(1, { quality })), false)
  for (const field of ['actualIncome', 'actualExpenses', 'actualSurplus', 'remainingBudget', 'overspent']) {
    for (const value of [null, undefined]) assert.equal(model.completeActual(comparison(1, { [field]: value })), false)
  }
  assert.equal(model.completeActual(comparison(1, { actualExpenses: NaN })), false)
})
test('duplicate month plans stay separate; scope/currency filtering; no budget is empty', async () => {
  const calls = [], signal = new AbortController().signal
  const api = { list: async () => [row(1), row(2), row(3, { scope: 'FAMILY' }), row(4, { currency: 'USD' }), row(5, { month: '2025-01' })], comparison: async id => { calls.push(id); return comparison(id) } }
  const result = await model.readReview(api, ['2026-10'], 'PERSONAL', 'CNY', signal)
  assert.deepEqual(result.map(e => e.budget.id), ['1','2']); assert.deepEqual(calls, ['1','2']); assert.ok(result.every(e => e.readAt))
  assert.deepEqual(await model.readReview({ list: async () => [], comparison: () => assert.fail() }, ['2026-10'], 'PERSONAL', 'CNY', signal), [])
})
test('page/request bounds and comparison concurrency; no writes', async () => {
  let pages = 0, calls = 0, active = 0, max = 0
  const api = { list: async p => { pages++; return Array.from({ length: p === 0 ? 20 : 10 }, (_, i) => row(p * 20 + i)) }, comparison: async id => { calls++; active++; max = Math.max(max, active); await new Promise(r => setTimeout(r, 1)); active--; return comparison(id) }, create: () => assert.fail('write'), edit: () => assert.fail('write') }
  await model.readReview(api, ['2026-10'], 'PERSONAL', 'CNY', new AbortController().signal)
  assert.equal(pages, 2); assert.equal(calls, 30); assert.equal(max, 3)
  pages = 0; calls = 0
  await assert.rejects(model.readReview({ ...api, list: async () => { pages++; return Array.from({ length: 20 }, (_, i) => row(i)) } }, ['2026-10'], 'PERSONAL', 'CNY', new AbortController().signal), /扫描上限/)
  assert.equal(pages, 5); assert.equal(calls, 0)
  await assert.rejects(model.readReview({ ...api, list: async p => Array.from({ length: p === 0 ? 20 : 11 }, (_, i) => row(p * 20 + i)) }, ['2026-10'], 'PERSONAL', 'CNY', new AbortController().signal), /30份/)
  assert.equal(calls, 0)
})
test('manual load only; permission failures clear data and retry recovers', async () => {
  let fail = false, calls = 0
  const { state } = await setup({ list: async () => { calls++; return [row(1)] }, comparison: async id => { if (fail) throw { response: { status: fail } }; return comparison(id) } })
  state.toggle(); assert.equal(calls, 0)
  await state.load(); assert.equal(state.entries.value.length, 1)
  for (const status of [401, 403]) { fail = status; await state.load(); assert.equal(state.entries.value.length, 0); assert.equal(state.loaded.value, false); assert.match(state.error.value, /权限/); fail = false; await state.load(); assert.equal(state.loaded.value, true) }
  state.toggle(); assert.equal(state.entries.value.length, 0)
})
test('scope, currency, selection, identity, revision and unmount cancel stale responses', async () => {
  for (const invalidate of [s => { s.state.scope.value = 'FAMILY' }, s => { s.state.currency.value = 'USD' }, s => s.state.select(row(1)), s => { s.user.user.familyId = 2 }, s => { s.user.token = null }, s => { s.props.revision++ }, s => s.cleanup()]) {
    const wait = deferred(); let signal
    const s = await setup({ list: async (p, abort) => { signal = abort; return wait.promise }, comparison: () => assert.fail('stale comparison') })
    const pending = s.state.load(); invalidate(s); assert.equal(signal.aborted, true); wait.resolve([row(1)]); await pending
    assert.equal(s.state.entries.value.length, 0); assert.equal(s.state.loaded.value, false)
  }
})
test('old failure cannot erase a newer successful retry; identity mismatch rejects', async () => {
  const wait = deferred(); let calls = 0
  const { state } = await setup({ list: async () => ++calls === 1 ? wait.promise : [row(1)], comparison: async id => comparison(id) })
  const old = state.load(); state.reset(); await state.load(); wait.resolve([row(1)]); await old
  assert.equal(state.entries.value.length, 1); assert.equal(state.error.value, '')
  await assert.rejects(model.readReview({ list: async () => [row(1)], comparison: async () => comparison(2) }, ['2026-10'], 'PERSONAL', 'CNY', new AbortController().signal), /标识/)
})
test('timeout aborts and permits manual retry', async () => {
  const saved = globalThis.setTimeout; let timeout, signal, wait = deferred()
  globalThis.setTimeout = fn => { timeout = fn; return 0 }
  try {
    const { state } = await setup({ list: async (p, s) => { signal = s; return wait.promise }, comparison: async id => comparison(id) })
    const pending = state.load(); timeout(); assert.equal(signal.aborted, true); assert.match(state.error.value, /超时/); wait.resolve([]); await pending
    assert.equal(state.loaded.value, false); await state.load(); assert.equal(state.loaded.value, true)
  } finally { globalThis.setTimeout = saved }
})
test('accessible table, explicit missing months and no financial writes or persistence', () => {
  assert.match(source, /scope="col"/); assert.match(source, /scope="row"/); assert.match(source, /tabindex="0"/); assert.match(source, /未创建预算/); assert.match(source, /部分来源/)
  assert.doesNotMatch(source, /localStorage|sessionStorage|\.create\(|\.edit\(|orderApi|settlementApi|ledgerApi/)
})
test('review API forwards cancellation on GET only', async () => {
  const { apiClient, budgetApi } = await import('../../shared/dist/index.js')
  const saved = { get: apiClient.get, post: apiClient.post, patch: apiClient.patch }, calls = [], signal = new AbortController().signal
  apiClient.get = async (...args) => { calls.push(args); return { data: [] } }
  apiClient.post = apiClient.patch = () => assert.fail('review must not write')
  try {
    await budgetApi.list(3, signal); await budgetApi.comparison('a/b', signal)
    assert.deepEqual(calls, [['/monthly-budgets', { params: { page: 3, size: 20 }, signal }], ['/monthly-budgets/a%2Fb/comparison', { signal }]])
  } finally { Object.assign(apiClient, saved) }
})
