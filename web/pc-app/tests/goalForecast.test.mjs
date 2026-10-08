import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as vue from 'vue'
const root = new URL('../', import.meta.url)
const read = path => readFile(new URL(path, root), 'utf8')
const transpile = source => ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText
const moduleOf = source => import(`data:text/javascript,${encodeURIComponent(transpile(source))}#${Math.random()}`)
const model = await moduleOf(await read('src/components/goalForecastModel.ts'))
const display = await moduleOf(await read('src/components/goalBudgetModel.ts'))
const goal = (id = 'g', extra = {}) => ({ id, config: { name: id, currency: 'CNY', scope: 'PERSONAL', targetValue: 10000, targetDate: '2027-10-01', state: 'ACTIVE', ...extra } })
async function setup(api = {}) {
  let script = compileScript(parse(await read('src/components/GoalForecastWorkbench.vue')).descriptor, { id: 'forecast' }).content
  script = script.replace(/import \{([^}]+)\} from 'vue'/g, (_, names) => `const {${names.replace(/ as /g, ': ')}} = globalThis.__forecast.vue`)
  script = script.replace(/import \{([^}]+)\} from '@wealth-hub\/shared'/g, (_, names) => `const {${names}} = globalThis.__forecast`)
  script = script.replace(/import \{([^}]+)\} from '[^']*goalForecast'/g, (_, names) => `const {${names}} = globalThis.__forecast`)
  script = script.replace(/import \{([^}]+)\} from '.\/goalBudgetModel'/g, (_, names) => `const {${names}} = globalThis.__forecast.display`)
  script = script.replace(/import \{([^}]+)\} from '.\/goalForecastModel'/g, (_, names) => `const {${names}} = globalThis.__forecast.model`)
  globalThis.__forecast = { vue: { ...vue, onMounted() {} }, display, model, goalApi: { list: async () => [goal(), goal('g2')] }, budgetApi: { list: async () => [] }, goalForecastApi: api }
  return (await moduleOf(script)).default.setup({}, { expose() {} })
}
test('bounded dates, amounts, matching currency/scope and partial inputs', () => {
  assert.equal(model.monthRange('2026-11', 12).at(-1), '2027-10')
  assert.equal(model.monthRange('9998-01', 12).at(-1), '9998-12')
  assert.deepEqual(model.monthRange('9997-01', 60), [])
  assert.deepEqual(model.monthRange('9999-01', 12), [])
  for (const [start, n] of [['2026-13', 12], ['2026-11', 11], ['2026-11', 61], ['2026-11', 12.5]]) assert.deepEqual(model.monthRange(start, n), [])
  for (const value of ['-1', '1.001', 'NaN', '1e4']) assert.equal(model.validMoney(value), false)
  const months = model.monthRange('2026-11', 12).map(month => ({ month, budgetId: null, cashflowCovered: false }))
  const request = { startMonth: '2026-11', endMonth: '2027-10', mode: 'PLANNED', months, monthlyExtraSavings: null }
  assert.equal(model.validateForecast([goal()], [], request, ['']), '')
  assert.match(model.validateForecast([goal(), goal('g2', { currency: 'USD' })], [], request, ['', '']), /币种/)
  assert.match(model.validateForecast([goal()], [], { ...request, months: [{ ...months[0], budgetId: 'missing' }, ...months.slice(1)] }, ['']), /预算/)
})
test('manual single and multi comparison; input validation prevents requests; unknown evidence retained', async () => {
  const calls = []
  const forecast = { goalId: 'g', asOfDate: '2000-01-01', baseline: { quality: 'PARTIAL', achievedMonth: '2027-01', months: [] }, actualProgress: { knownValue: 20 } }
  const state = await setup({ forecast: async (id, input) => { calls.push(input); return forecast }, compare: async inputs => { calls.push(inputs); return { scenarios: [], source: '只读', readStartedAt: 'a', readCompletedAt: 'b' } } })
  await state.load(); await state.run(); assert.equal(calls.length, 0)
  state.ids.value = 'g'; await state.run(); assert.equal(calls.length, 2); assert.equal(calls[0].months.length, 12); assert.equal(calls[0].months[0].cashflowCovered, false); assert.equal(calls[0].months[0].budgetId, null)
  assert.match(state.achievement(forecast), /UNKNOWN/); assert.equal(state.results.value.length, 2)
  state.invalidate(); assert.equal(state.results.value.length, 0)
  state.multiple.value = true; state.ids.value = ['g', 'g2']; await state.run(); assert.equal(calls.length, 3); assert.equal(calls[2][0].allocations[0].monthlyAmount, null)
  state.drafts.value[0].allocations.g = '-1'; await state.run(); assert.equal(calls.length, 3); assert.match(state.validation.value, /金额/)
})
test('permission, interruption, failure clear evidence and manual retry recovers', async () => {
  let fail = true
  const state = await setup({ compare: async () => { if (fail) throw { response: { status: 403 } }; return { scenarios: [], source: '恢复' } } })
  await state.load(); state.multiple.value = true; state.ids.value = ['g', 'g2']; await state.run(); assert.match(state.error.value, /权限/); assert.equal(state.busy.value, false); assert.deepEqual(state.results.value, [])
  fail = false; await state.retry(); assert.equal(state.error.value, '')
  globalThis.__forecast.goalForecastApi.compare = async () => { throw Error('响应中断') }; await state.run(); assert.match(state.error.value, /响应中断/); assert.deepEqual(state.results.value, [])
})
test('over-limit multi-goal evidence and unknown amounts survive comparison unchanged', async () => {
  const scenario = { name: '情景 A', months: [{ month: '2026-11', sharedUpperBound: 100, specifiedTotal: 200, overLimit: true }, { month: '2026-12', sharedUpperBound: null, specifiedTotal: 200, overLimit: null }], goals: [{ goalId: 'g', forecast: { baseline: { quality: 'UNKNOWN', achievedMonth: null } } }], warnings: ['目标起始资产可能重叠'] }
  const state = await setup({ compare: async () => ({ scenarios: [scenario], source: '授权读取' }) })
  await state.load(); state.multiple.value = true; state.ids.value = ['g', 'g2']; await state.run()
  assert.equal(state.results.value[0].months[0].overLimit, true)
  assert.equal(state.results.value[0].months[1].sharedUpperBound, null)
  assert.equal(display.amount(state.results.value[0].months[1].sharedUpperBound), '未知 / UNKNOWN')
  assert.equal(state.results.value[0].goals[0].forecast.baseline.achievedMonth, null)
  assert.equal(state.deficit({ modeledIncome: 100, modeledExpenses: 120, plannedReserve: 20 }), true)
  assert.equal(state.deficit({ modeledIncome: null, modeledExpenses: 120, plannedReserve: 20 }), false)
})
test('read-only POST contract and UI risk presentation', async () => {
  const calls = []
  globalThis.__client = { post: async (...args) => { calls.push(args); return { data: {} } } }
  const api = await moduleOf((await readFile(new URL('../../shared/src/api/goalForecast.ts', import.meta.url), 'utf8')).replace("import { apiClient } from './client'", 'const apiClient = globalThis.__client'))
  await api.goalForecastApi.forecast('a/b', {}); await api.goalForecastApi.compare([])
  assert.equal(calls[0][0], '/goals/a%2Fb/forecast'); assert.equal(calls[1][0], '/goals/scenarios/compare'); assert.equal(calls[0][2].timeout, 60000)
  const source = await read('src/components/GoalForecastWorkbench.vue')
  for (const text of ['目标之间争用', '预算赤字', '时间不足', 'UNKNOWN', 'PARTIAL', 'role="alert"', 'role="status"']) assert.ok(source.includes(text))
  assert.doesNotMatch(source, /ledgerApi|orderApi|settlementApi|\.create\(|\.edit\(|localStorage/)
})
