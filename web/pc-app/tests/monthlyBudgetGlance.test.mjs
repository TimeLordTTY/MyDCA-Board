import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import * as vue from 'vue'
const read = p => readFile(new URL(`../src/components/${p}`, import.meta.url), 'utf8')
const transpile = s => ts.transpileModule(s, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText
const imp = s => import(`data:text/javascript,${encodeURIComponent(transpile(s))}#${Math.random()}`)
const review = await imp(await read('budgetReviewModel.ts'))
globalThis.__glanceReview = review
const model = await imp((await read('monthlyBudgetGlanceModel.ts')).replace("import { completeActual } from './budgetReviewModel'", 'const { completeActual } = globalThis.__glanceReview'))
const labels = await imp(await read('goalBudgetModel.ts'))
const source = await read('MonthlyBudgetGlance.vue')
const row = (id, extra = {}) => ({ id: String(id), config: { name: '脱敏计划', month: model.localMonth(), scope: 'PERSONAL', currency: 'CNY', items: [], ...extra } })
const comparison = (id, extra = {}) => ({ budgetId: String(id), quality: 'OK', plannedIncome: 0, plannedExpenses: 0, plannedReserve: 0, plannedSurplus: 0, actualIncome: 0, actualExpenses: 0, actualSurplus: 0, remainingBudget: 0, overspent: false, warnings: [], ...extra })
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
const readModel = api => model.readMonthlyGlance(api, model.localMonth(), 'PERSONAL', 'CNY', new AbortController().signal)
async function setup(api) {
  const user = vue.reactive({ token: 'fixture', user: { id: 1, familyId: 1 } }), route = vue.reactive({ fullPath: '/' })
  let cleanup, deactivate
  globalThis.__glance = { vue: { ...vue, onBeforeUnmount: fn => { cleanup = fn }, onDeactivated: fn => { deactivate = fn } }, useRoute: () => route, budgetApi: api, useUserStore: () => user, review, labels, model }
  let script = compileScript(parse(source).descriptor, { id: 'glance-test' }).content
  for (const [path, target] of [['vue', 'vue'], ['vue-router', ''], ['@wealth-hub/shared', ''], ['./budgetReviewModel', 'review'], ['./goalBudgetModel', 'labels'], ['./monthlyBudgetGlanceModel', 'model']]) {
    script = script.replace(new RegExp(`import \\{([^}]+)\\} from '${path.replaceAll('.', '\\.')}'`, 'g'), (_, names) => `const {${names.replace(/ as /g, ': ')}} = globalThis.__glance${target ? '.' + target : ''}`)
  }
  const state = (await imp(script)).default.setup({}, { expose() {} })
  return { state, user, route, cleanup: () => cleanup(), deactivate: () => deactivate() }
}
test('local calendar across year and leap day', () => {
  assert.equal(model.localMonth(new Date(2026, 0, 1)), '2026-01')
  assert.equal(model.localMonth(new Date(2025, 11, 31)), '2025-12')
  assert.equal(model.localMonth(new Date(2024, 1, 29)), '2024-02')
})
test('independent plans, scope/currency/month filters and no plan', async () => {
  const calls = []
  const result = await readModel({ list: async () => [row(1), row(2), row(3, { scope: 'FAMILY' }), row(4, { currency: 'USD' }), row(5, { month: '2020-01' })], comparison: async id => { calls.push(id); return comparison(id) } })
  assert.deepEqual(result.map(e => e.budget.id), ['1', '2']); assert.deepEqual(calls, ['1', '2'])
  assert.deepEqual(await readModel({ list: async () => [], comparison: () => assert.fail() }), [])
})
test('zero is known only with complete actuals; missing fields and all incomplete qualities', () => {
  assert.equal(model.actualAmount(comparison(1), 0), '0.00')
  for (const quality of ['PARTIAL', 'UNKNOWN', 'UNAVAILABLE']) assert.match(model.actualAmount(comparison(1, { quality }), 0), /未知/)
  for (const field of ['actualIncome', 'actualExpenses', 'actualSurplus', 'remainingBudget', 'overspent']) assert.match(model.actualAmount(comparison(1, { [field]: null }), 0), /未知/)
  for (const value of [null, undefined, NaN, Infinity]) assert.equal(model.glanceAmount(value), '未知')
})
test('scan and comparison bounds, concurrency and no writes', async () => {
  let pages = 0, calls = 0, active = 0, max = 0
  const api = { list: async p => { pages++; return p === 0 ? Array.from({ length: 20 }, (_, i) => row(i)) : [] }, comparison: async id => { calls++; active++; max = Math.max(max, active); await new Promise(r => setTimeout(r, 1)); active--; return comparison(id) }, create: () => assert.fail(), edit: () => assert.fail(), recalculate: () => assert.fail() }
  await readModel(api); assert.equal(calls, 20); assert.equal(max, 3)
  calls = 0; pages = 0
  await assert.rejects(readModel({ ...api, list: async () => { pages++; return Array.from({ length: 20 }, (_, i) => row(i)) } }), /无法证明本月全量/)
  assert.equal(pages, 5); assert.equal(calls, 0)
  await assert.rejects(readModel({ ...api, list: async p => Array.from({ length: p ? 1 : 20 }, (_, i) => row(p * 20 + i)) }), /20份/); assert.equal(calls, 0)
})
test('comparison identity and optional metadata conflicts rejected', async () => {
  for (const extra of [{ budgetId: 'other' }, { month: '2000-01' }, { scope: 'FAMILY' }, { currency: 'USD' }]) await assert.rejects(readModel({ list: async () => [row(1)], comparison: async () => comparison(1, extra) }), /不一致/)
})
test('manual load, failure clears data, authorization and network retries', async () => {
  let fail = 0, calls = 0
  const s = await setup({ list: async () => { calls++; if (fail) throw fail === 500 ? new Error('网络中断') : { response: { status: fail } }; return [row(1)] }, comparison: async id => comparison(id) })
  assert.equal(calls, 0); await s.state.load(); assert.equal(s.state.loaded.value, true)
  for (const status of [401, 403, 500]) { fail = status; await s.state.load(); assert.equal(s.state.entries.value.length, 0); assert.equal(s.state.loaded.value, false); assert.match(s.state.error.value, status === 500 ? /网络/ : status === 401 ? /登录已失效/ : /权限/); fail = 0; await s.state.load(); assert.equal(s.state.loaded.value, true) }
  s.cleanup()
})
test('cancel, session identity, filters, route and lifecycle invalidate late responses', async () => {
  for (const invalidate of [s => s.state.cancel(), s => { s.state.scope.value = 'FAMILY' }, s => { s.state.currency.value = 'USD' }, s => { s.user.token = null }, s => { s.user.user.id++ }, s => { s.user.user.familyId++ }, s => { s.route.fullPath = '/other' }, s => s.cleanup(), s => s.deactivate()]) {
    const wait = deferred(); let signal
    const s = await setup({ list: async (_, sig) => { signal = sig; return wait.promise }, comparison: () => assert.fail('stale') })
    const pending = s.state.load(); invalidate(s); assert.equal(signal.aborted, true); wait.resolve([row(1)]); await pending; assert.equal(s.state.entries.value.length, 0); assert.equal(s.state.loaded.value, false)
    s.cleanup()
  }
})
test('timeout clears data, aborts and allows manual retry', async () => {
  const saved = globalThis.setTimeout; let timeout, signal; const wait = deferred()
  globalThis.setTimeout = fn => { timeout = fn; return 0 }
  try {
    const s = await setup({ list: async (_, sig) => { signal = sig; return wait.promise }, comparison: () => assert.fail() })
    const pending = s.state.load(); timeout(); assert.equal(signal.aborted, true); assert.match(s.state.error.value, /超时/); wait.resolve([]); await pending; assert.equal(s.state.loaded.value, false); await s.state.load(); assert.equal(s.state.loaded.value, true); s.cleanup()
  } finally { globalThis.setTimeout = saved }
})
test('late old request cannot overwrite a successful retry', async () => {
  const wait = deferred(); let calls = 0
  const s = await setup({ list: async () => ++calls === 1 ? wait.promise : [row(2)], comparison: async id => comparison(id) })
  const old = s.state.load(); s.state.cancel(); await s.state.load(); wait.resolve([row(1)]); await old
  assert.deepEqual(s.state.entries.value.map(e => e.budget.id), ['2']); assert.equal(s.state.error.value, ''); s.cleanup()
})
test('safe route, labels and no persistence or financial actions', async () => {
  assert.match(source, /name: 'GoalBudgetCenter'/); assert.match(source, /aria-label="本月预算速览"/); assert.match(source, /role="status"/); assert.match(source, /role="alert"/); assert.match(source, /<label>作用域/); assert.match(source, /无计划不等于0元/)
  assert.doesNotMatch(source + await read('monthlyBudgetGlanceModel.ts'), /localStorage|sessionStorage|\.create\(|\.edit\(|\.post\(|\.patch\(|orderApi|settlementApi|recalculate|refreshAssets/)
})

const template = compileTemplate({ source: parse(source).descriptor.template.content, filename: 'MonthlyBudgetGlance.vue', id: 'copy-test' })
assert.deepEqual(template.errors, [])
globalThis.__copyVue = { ...vue, vModelSelect: {} } // DOM select directive is outside this text-rendering test.
const render = (await imp(template.code.replace(/import \{([^}]+)\} from "vue"/, (_, names) => `const {${names.replace(/ as /g, ': ')}} = globalThis.__copyVue`))).render
function rendered(state) {
  const renderer = vue.createRenderer({
    createElement: tag => ({ tag, children: [] }), createText: text => ({ text }), createComment: () => ({}),
    setText: (node, text) => { node.text = text }, setElementText: (node, text) => { node.text = text },
    insert: (node, parent) => { parent.children.push(node) }, remove() {},
    patchProp: (node, key, value, next) => { (node.props ||= {})[key] = next }, parentNode: () => null, nextSibling: () => null,
  })
  const root = { children: [] }
  const app = renderer.createApp({ render: () => render(vue.proxyRefs(state), []) })
  app.component('router-link', { setup: (_, { slots }) => () => vue.h('a', slots.default?.()) })
  app.mount(root)
  const collect = node => [node.text || '', ...(node.children || []).map(collect)].join(' ')
  const text = collect(root)
  app.unmount()
  return text
}
test('rendered budget states distinguish idle, empty, denied, failure and real zero', async () => {
  let mode = 'empty'
  const s = await setup({ list: async () => {
    if (mode === 'denied') throw { response: { status: 403 } }
    if (mode === 'failed') throw new Error('网络中断')
    return mode === 'empty' ? [] : [row(1)]
  }, comparison: async id => comparison(id, mode === 'partial' ? { quality: 'PARTIAL' } : {}) })
  assert.match(rendered(s.state), /还未读取本月预算/)
  await s.state.load(); assert.match(rendered(s.state), /还没有本月预算/); assert.match(rendered(s.state), /前往目标与预算创建/)
  mode = 'denied'; await s.state.load(); assert.match(rendered(s.state), /没有读取此预算的权限/); assert.doesNotMatch(rendered(s.state), /还没有本月预算|0\.00/)
  mode = 'failed'; await s.state.load(); assert.match(rendered(s.state), /预算读取失败/)
  mode = 'zero'; await s.state.load(); assert.match(rendered(s.state), /0\.00/); assert.doesNotMatch(rendered(s.state), /还没有本月预算|UNKNOWN|输入完整/)
  mode = 'partial'; await s.state.load(); assert.match(rendered(s.state), /部分流水未读全/); assert.doesNotMatch(rendered(s.state), /未超支（本预算口径）/)
  s.cleanup()
})
test('friendly quality labels leave missing actuals unresolved and offer creation navigation', () => {
  assert.match(model.budgetQualityText(comparison(1)), /核对流水/)
  for (const quality of ['PARTIAL', 'UNKNOWN', 'UNAVAILABLE']) assert.match(model.budgetQualityText(comparison(1, { quality })), /无法确认/)
  assert.match(model.budgetQualityText(comparison(1, { actualExpenses: null })), /不能按0支出/)
  assert.match(model.budgetGlanceFailure({ response: { status: 401 } }), /登录已失效/)
  assert.match(source, /前往目标与预算创建/); assert.match(source, /focus-visible/)
})
