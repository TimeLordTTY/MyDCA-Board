import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as vue from 'vue'
const root = new URL('../', import.meta.url)
const transpile = source => ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText
const model = await import(`data:text/javascript,${encodeURIComponent(transpile(await readFile(new URL('src/components/goalBudgetModel.ts', root), 'utf8')))}`)
const goal = (extra = {}) => ({ name: '储备目标', targetValue: '10000.00', targetDate: '2027-01-01', scope: 'PERSONAL', currency: 'CNY', state: 'ACTIVE', measure: 'CASH', note: '', ...extra })
const item = (extra = {}) => ({ name: '生活', kind: 'FLEXIBLE_EXPENSE', categoryId: 1, planned: '500.00', ...extra })
const budget = (items = [item()]) => ({ name: '月度计划', month: '2026-10', scope: 'PERSONAL', currency: 'CNY', items })
async function setup(goalApi, budgetApi = {}) {
  const source = await readFile(new URL('src/views/GoalBudgetCenter.vue', root), 'utf8')
  let script = compileScript(parse(source).descriptor, { id: 'goal-budget-test' }).content
  script = script.replace(/import GoalForecastWorkbench from '[^']+'/g, 'const GoalForecastWorkbench = {}')
  script = script.replace(/import \{([^}]+)\} from 'vue'/g, (_, names) => `const {${names.replace(/ as /g, ': ')}} = globalThis.__goalBudget.vue`)
  script = script.replace(/import \{([^}]+)\} from '@wealth-hub\/shared'/g, (_, names) => `const {${names}} = globalThis.__goalBudget`)
  script = script.replace(/import \{([^}]+)\} from '..\/components\/goalBudgetModel'/g, (_, names) => `const {${names}} = globalThis.__goalBudget.model`)
  globalThis.__goalBudget = { vue: { ...vue, onMounted() {} }, goalApi, budgetApi, model, incomeCategories: [], expenseCategories: [] }
  return (await import(`data:text/javascript,${encodeURIComponent(transpile(script))}#${Math.random()}`)).default.setup({}, { expose() {} })
}
test('validation rejects invalid dates, amounts and duplicate expense categories', () => {
  assert.equal(model.validateGoal(goal()), '')
  for (const extra of [{ targetValue: '' }, { targetValue: 0 }, { targetValue: '1.001' }, { targetDate: '2026-02-30' }, { scope: 'OTHER' }, { note: 'x'.repeat(2001) }]) assert.ok(model.validateGoal(goal(extra)))
  assert.equal(model.validateBudget(budget()), '')
  assert.ok(model.validateBudget(budget([item(), item({ kind: 'FIXED_EXPENSE' })])))
  assert.ok(model.validateBudget(budget([item({ categoryId: null })])))
  assert.ok(model.validateBudget(budget([item({ kind: 'RESERVE' })])))
  assert.equal(model.validateBudget(budget([item({ kind: 'RESERVE', categoryId: null, planned: 0 })])), '')
  assert.equal(model.amount(null), '未知 / UNKNOWN'); assert.equal(model.percent(null), '未知 / UNKNOWN'); assert.equal(model.percent(0), '0.00%'); assert.equal(model.percent(1.2), '120.00%')
})
test('goal create/edit, pause/resume/archive and save errors preserve form', async () => {
  const row = { id: 'g', config: goal() }, calls = []
  let fail = false
  const state = await setup({ list: async () => [row], detail: async () => row, progress: async () => ({ quality: 'PARTIAL', currentValue: null, knownValue: 300, completionRate: null }), create: async c => calls.push(['create', c]), edit: async (id, c) => { if (fail) throw { response: { status: 403 } }; calls.push(['edit', id, c]) } })
  state.edit(); await state.save(); assert.equal(calls.length, 0)
  state.form.value = goal(); await state.save(); assert.equal(calls[0][0], 'create')
  state.edit(row); state.form.value.name = '更新目标'; await state.save(); assert.equal(calls[1][2].name, '更新目标'); assert.equal(row.config.name, '储备目标')
  for (const value of ['PAUSED', 'ACTIVE', 'ARCHIVED']) { await state.observe(row); await state.changeState(value); assert.equal(calls.at(-1)[2].state, value) }
  state.edit(row); fail = true; await state.save(); assert.ok(state.form.value); assert.match(state.formError.value, /权限/)
})
test('progress failure clears old evidence and retry recovers; pagination and empty list', async () => {
  const row = { id: 'g', config: goal() }, pages = []; let fail = false
  const state = await setup({ list: async p => { pages.push(p); return p === 0 ? Array.from({ length: 20 }, (_, i) => ({ ...row, id: String(i) })) : [] }, detail: async () => row, progress: async () => { if (fail) throw { response: { status: 403 } }; return { quality: 'UNKNOWN', currentValue: null, completionRate: null } } })
  await state.refresh(); assert.equal(state.more.value, true); await state.loadMore(); assert.deepEqual(pages, [0, 1]); assert.equal(state.more.value, false)
  await state.observe(row); assert.equal(state.progress.value.quality, 'UNKNOWN'); fail = true; await state.observe(row); assert.equal(state.progress.value, null); assert.equal(state.selected.value, null); assert.match(state.error.value, /权限/)
  fail = false; await state.observe(row); assert.equal(state.error.value, '')
  const empty = await setup({ list: async () => [] }); await empty.refresh(); assert.equal(empty.rows.value.length, 0); assert.equal(empty.error.value, '')
})
test('budget create/edit, comparison PARTIAL and failed refresh clears evidence', async () => {
  const row = { id: 'b', config: budget() }, calls = []; let fail = false
  const state = await setup({}, { list: async () => { if (fail) throw Error('离线'); return [row] }, create: async c => calls.push(c), edit: async (id, c) => calls.push(c), detail: async () => row, comparison: async () => ({ quality: 'PARTIAL', actualExpenses: null, remainingBudget: null, overspent: null, items: [], warnings: ['未知'] }) })
  await state.switchTab('budgets'); state.edit(); state.form.value = budget(); await state.save(); assert.equal(calls.length, 1)
  state.edit(row); state.form.value.items[0].planned = '600'; await state.save(); assert.equal(calls[1].items[0].planned, '600'); assert.equal(row.config.items[0].planned, '500.00')
  await state.observe(row); assert.equal(state.comparison.value.remainingBudget, null); fail = true; await state.refresh(); assert.equal(state.comparison.value, null); assert.equal(state.rows.value.length, 0); assert.match(state.error.value, /离线/)
})
test('API paths are metadata-only; read-only evidence GET; encoded identifiers', async () => {
  const { apiClient, goalApi, budgetApi } = await import('../../shared/dist/index.js')
  const saved = { get: apiClient.get, post: apiClient.post, patch: apiClient.patch }, calls = []
  for (const method of Object.keys(saved)) apiClient[method] = async (...args) => { calls.push([method, ...args]); return { data: [] } }
  try {
    await goalApi.list(2); await goalApi.create(goal()); await goalApi.edit('a/b', goal()); await goalApi.detail('a/b'); await goalApi.progress('a/b')
    await budgetApi.list(1); await budgetApi.create(budget()); await budgetApi.edit('a/b', budget()); await budgetApi.detail('a/b'); await budgetApi.comparison('a/b')
    assert.equal(calls[0][2].params.page, 2); assert.deepEqual(calls[4], ['get', '/goals/a%2Fb/progress']); assert.deepEqual(calls[9], ['get', '/monthly-budgets/a%2Fb/comparison'])
    assert.ok(calls.every(c => /^\/(goals|monthly-budgets)/.test(c[1])))
  } finally { Object.assign(apiClient, saved) }
  const source = await readFile(new URL('src/views/GoalBudgetCenter.vue', root), 'utf8')
  assert.doesNotMatch(source, /orderApi|settlementApi|ledgerApi|confirmDraft|自动交易|立即买入/)
  assert.match(source, /role="alert"/); assert.match(source, /role="status"/); assert.match(source, /确认归档/)
})
