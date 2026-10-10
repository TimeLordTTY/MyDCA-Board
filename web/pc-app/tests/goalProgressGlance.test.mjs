import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import * as vue from 'vue'
const read = p => readFile(new URL(`../src/components/${p}`, import.meta.url), 'utf8')
const imp = s => import(`data:text/javascript,${encodeURIComponent(ts.transpileModule(s, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText)}#${Math.random()}`)
const model = await imp(await read('goalProgressGlanceModel.ts'))
const source = await read('GoalProgressGlance.vue')
const row = (id, extra = {}) => ({ id: String(id), config: { name: '测试目标', targetValue: 100, targetDate: '2026-10-20', scope: 'PERSONAL', currency: 'CNY', measure: 'TOTAL_ASSETS', state: 'ACTIVE', ...extra } })
const progress = (id, extra = {}) => ({ goalId: String(id), quality: 'OK', reason: '只读资产进度', currentValue: 0, knownValue: 0, completionRate: 0, completed: false, asOfDate: '2026-10-10', daysRemaining: 10, overdue: false, ...extra })
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
const readModel = (api, scope = 'PERSONAL', currency = 'CNY') => model.readGoalGlance(api, scope, currency, () => true)
async function setup(api) {
  const user = vue.reactive({ token: 'fixture', user: { id: 1, familyId: 1 } }), route = vue.reactive({ fullPath: '/' })
  let cleanup, deactivate
  globalThis.__goalGlance = { vue: { ...vue, onBeforeUnmount: fn => { cleanup = fn }, onDeactivated: fn => { deactivate = fn } }, useRoute: () => route, goalApi: api, useUserStore: () => user, model }
  let script = compileScript(parse(source).descriptor, { id: 'goal-test' }).content
  for (const [path, target] of [['vue', 'vue'], ['vue-router', ''], ['@wealth-hub/shared', ''], ['./goalProgressGlanceModel', 'model']]) {
    script = script.replace(/import \{([^}]+)\} from '([^']+)'/g, (statement, names, module) => module === path ? `const {${names.replace(/ as /g, ': ')}} = globalThis.__goalGlance${target ? '.' + target : ''}` : statement)
  }
  const state = (await imp(script)).default.setup({}, { expose() {} })
  return { state, user, route, cleanup: () => cleanup(), deactivate: () => deactivate() }
}
test('complete empty list and first visit performs no requests', async () => {
  let calls = 0
  const s = await setup({ list: async () => { calls++; return [] }, progress: () => assert.fail() })
  assert.equal(calls, 0); await s.state.load(); assert.equal(calls, 1); assert.equal(s.state.loaded.value, true); assert.deepEqual(s.state.entries.value, []); s.cleanup()
})
test('ACTIVE, authorized scope, currency, stable dates and at most three; no totals', async () => {
  const rows = [row(1, { targetDate: '2027-01-01' }), row(2), row(3), row(4, { targetDate: '2026-10-01' }), row(5, { state: 'PAUSED' }), row(6, { state: 'ARCHIVED' }), row(7, { scope: 'FAMILY' }), row(8, { currency: 'USD' })]
  let calls = [], active = 0, max = 0
  const api = { list: async () => rows, progress: async id => { calls.push(id); active++; max = Math.max(max, active); await Promise.resolve(); active--; return progress(id) } }
  assert.deepEqual((await readModel(api)).map(e => e.goal.id), ['4', '2', '3']); assert.equal(max, 3); assert.equal(calls.length, 3)
  assert.deepEqual((await readModel(api, 'FAMILY')).map(e => e.goal.id), ['7'])
  assert.deepEqual((await readModel(api, 'PERSONAL', 'USD')).map(e => e.goal.id), ['8'])
})
test('pagination terminates on short page; full fifth page rejects without progress', async () => {
  let pages = 0, calls = 0
  const api = { list: async page => { pages++; return Array.from({ length: 20 }, (_, i) => row(page * 20 + i)) }, progress: async id => { calls++; return progress(id) } }
  await assert.rejects(readModel(api), /无法确认完整/); assert.equal(pages, 5); assert.equal(calls, 0)
  pages = 0; api.list = async page => { pages++; return page === 4 ? [] : Array.from({ length: 20 }, (_, i) => row(page * 20 + i)) }
  assert.equal((await readModel(api)).length, 3); assert.equal(pages, 5)
})
test('invalid/duplicate IDs, malformed lists/config/dates and mismatched progress rejected', async () => {
  for (const rows of [null, [row('')], [row(1), row(1)], [{ config: row(1).config }], [row(1, { targetDate: '2026-02-30' })], [row(1, { targetDate: '0999-01-01' })], [row(1, { measure: 'BAD' })], [row(1, { targetValue: Infinity })], Array.from({ length: 21 }, (_, i) => row(i))]) {
    await assert.rejects(readModel({ list: async () => rows, progress: () => assert.fail() }), /无法确认完整/)
  }
  for (const p of [null, progress(2), progress(1, { quality: 'BAD' }), progress(1, { reason: null })]) await assert.rejects(readModel({ list: async () => [row(1)], progress: async () => p }), /响应无效/)
})
test('quality guards zero, over 100%, partial/unknown and invalid numeric/boolean contracts', () => {
  assert.equal(model.goalPercent(progress(1)), '0.00%')
  const high = progress(1, { completionRate: 1.25, completed: true }); assert.equal(model.goalPercent(high), '125.00%')
  for (const completionRate of [null, undefined, NaN, Infinity, '0']) assert.equal(model.completeGoalProgress(progress(1, { completionRate })), false)
  for (const completed of [null, undefined, 'false', true]) assert.equal(model.completeGoalProgress(progress(1, { completed })), false)
  for (const quality of ['PARTIAL', 'UNKNOWN']) assert.equal(model.completeGoalProgress(progress(1, { quality })), false)
  assert.equal(model.goalAmount(0), '0.00'); for (const n of [null, undefined, NaN, Infinity]) assert.equal(model.goalAmount(n), '未知')
  assert.match(model.goalReason(progress(1, { reason: 'token=secret account=private' })), /不可安全展示/)
})
test('real date validation and countdown rely on supplied date, never device time', () => {
  assert.equal(model.validGoalDate('2024-02-29'), true)
  for (const d of ['2026-02-29', '2026-13-01', '0000-01-01', '10000-01-01', null, '2026-10-10T00:00:00Z']) assert.equal(model.validGoalDate(d), false)
  assert.match(model.goalCountdown(progress(1), '2026-10-20'), /10 天/)
  assert.match(model.goalCountdown(progress(1, { asOfDate: '2026-10-21', daysRemaining: -1, overdue: true }), '2026-10-20'), /已逾期 1/)
  for (const extra of [{ asOfDate: null }, { daysRemaining: Infinity }, { daysRemaining: 9 }, { overdue: true }]) assert.equal(model.goalCountdown(progress(1, extra), '2026-10-20'), '倒计时未知')
})
test('401/403/500/offline clears previous success and supports manual retry', async () => {
  let fail = null
  const s = await setup({ list: async () => { if (fail) throw fail; return [row(1)] }, progress: async id => progress(id) })
  await s.state.load()
  for (const e of [{ response: { status: 401 } }, { response: { status: 403 } }, { response: { status: 500 } }, new Error('private network details')]) {
    fail = e; await s.state.load(); assert.equal(s.state.entries.value.length, 0); assert.equal(s.state.loaded.value, false); assert.match(s.state.error.value, /权限|网络/); fail = null; await s.state.load(); assert.equal(s.state.loaded.value, true)
  }
  s.cleanup()
})
test('identity, filters, route, unmount and deactivate stop late list from issuing progress', async () => {
  for (const invalidate of [s => { s.state.scope.value = 'FAMILY' }, s => { s.state.currency.value = 'USD' }, s => { s.user.token = null }, s => { s.user.user = { id: 2 } }, s => { s.user.user.id++ }, s => { s.user.user.familyId++ }, s => { s.route.fullPath = '/other' }, s => s.cleanup(), s => s.deactivate()]) {
    const wait = deferred(), s = await setup({ list: async () => wait.promise, progress: () => assert.fail('stale progress request') })
    const pending = s.state.load(); invalidate(s); wait.resolve([row(1)]); await pending; assert.equal(s.state.entries.value.length, 0); assert.equal(s.state.loaded.value, false); s.cleanup()
  }
})
test('timeout invalidates outstanding requests and newer refresh beats late progress', async () => {
  const saved = globalThis.setTimeout; let timeout
  globalThis.setTimeout = fn => { timeout = fn; return 0 }
  try {
    const wait = deferred(), s = await setup({ list: async () => wait.promise, progress: () => assert.fail() })
    const pending = s.state.load(); timeout(); assert.match(s.state.error.value, /超时/); wait.resolve([]); await pending; assert.equal(s.state.loaded.value, false); await s.state.load(); assert.equal(s.state.loaded.value, true); s.cleanup()
  } finally { globalThis.setTimeout = saved }
  const wait = deferred(); let calls = 0
  const s = await setup({ list: async () => [row(++calls)], progress: async id => id === '1' ? wait.promise : progress(id) })
  const old = s.state.load(); await Promise.resolve(); await s.state.load(); wait.resolve(progress(1)); await old
  assert.deepEqual(s.state.entries.value.map(e => e.goal.id), ['2']); s.cleanup()
})
test('Vue template compiles, safe navigation, GET-only API surface and accessible states', async () => {
  const descriptor = parse(source).descriptor
  assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: 'GoalProgressGlance.vue', id: 'goal-test' }).errors, [])
  assert.match(source, /name: 'GoalBudgetCenter'/); assert.match(source, /role="status"/); assert.match(source, /role="alert"/); assert.match(source, /focus-visible/)
  assert.doesNotMatch(source + await read('goalProgressGlanceModel.ts'), /localStorage|sessionStorage|\.create\(|\.edit\(|\.post\(|\.patch\(|orderApi|settlementApi|recalculate/)
})
test('duplicate across pages and stale pagination never start progress', async () => {
  await assert.rejects(readModel({ list: async page => page === 0 ? Array.from({ length: 20 }, (_, i) => row(i)) : [row(0)], progress: () => assert.fail() }), /无法确认完整/)
  let current = true, pages = 0
  const wait = deferred()
  const pending = model.readGoalGlance({ list: async () => { pages++; return wait.promise }, progress: () => assert.fail() }, 'PERSONAL', 'CNY', () => current)
  current = false; wait.resolve(Array.from({ length: 20 }, (_, i) => row(i))); assert.deepEqual(await pending, []); assert.equal(pages, 1)
})
test('identity change during progress and malformed response clear all card data', async () => {
  const wait = deferred()
  const s = await setup({ list: async () => [row(1)], progress: async () => wait.promise })
  const pending = s.state.load(); await Promise.resolve(); s.user.user.familyId++; wait.resolve(progress(1)); await pending
  assert.equal(s.state.entries.value.length, 0); assert.equal(s.state.loaded.value, false); s.cleanup()
  const bad = await setup({ list: async () => [row(1)], progress: async () => progress(2) })
  await bad.state.load(); assert.match(bad.state.error.value, /响应无效/); assert.equal(bad.state.entries.value.length, 0); bad.cleanup()
})
