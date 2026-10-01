import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as vue from 'vue'

const source = await readFile(new URL('../src/components/ResearchWorkbench.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
let script = compileScript(descriptor, { id: 'research-test' }).content
script = script.replace(/import \{([^}]+)\} from 'vue'/g, (_, names) => `const {${names.replace(/ as /g, ': ')}} = globalThis.__researchMocks.vue`)
script = script.replace(/import \{([^}]+)\} from '@wealth-hub\/shared'/g, (_, names) => `const {${names}} = globalThis.__researchMocks`)
const code = ts.transpileModule(script, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2020 } }).outputText
const fixture = (overrides = {}) => ({ id: 'plan-1', name: '研究', status: 'DRAFT', description: '', paramsDraft: { interval_days: 30 }, sourceRunIds: ['source'], warnings: [], ...overrides })
async function setup(overrides = {}) {
  const calls = [], events = []
  const api = {
    list: async () => [fixture()], detail: async () => fixture(),
    create: async request => { calls.push(['create', request]); return fixture() },
    edit: async (id, request) => { calls.push(['edit', id, request]); return fixture(request) },
    run: async (id, dataset) => { calls.push(['run', id, dataset]); return { history_run_id: 'new-run' } },
    ...overrides,
  }
  globalThis.__researchMocks = { vue: { ...vue, onMounted() {} }, researchPlanApi: api, backtestApi: { history: async () => [{ historyRunId: 'new-run', researchPlanId: 'plan-1', status: 'SUCCESS' }, { historyRunId: 'other', researchPlanId: 'other-plan', status: 'SUCCESS' }] } }
  const module = await import(`data:text/javascript,${encodeURIComponent(code)}#${Math.random()}`)
  const props = { candidate: { candidate_id: 'candidate-hash', run_ids: ['source'], strategy: 'pure_sip', strategy_version: '1' }, thresholds: { minSampleDays: 180 }, datasets: ['nav.csv'] }
  const state = module.default.setup(props, { expose() {}, emit: (...args) => events.push(args) })
  return { state, calls, events }
}

test('create copies candidate references, edits bounded draft and archives explicitly', async () => {
  const { state: s, calls } = await setup()
  s.newName.value = '研究计划'
  await s.create()
  assert.equal(calls[0][1].candidateId, 'candidate-hash')
  assert.deepEqual(calls[0][1].runIds, ['source'])
  assert.deepEqual(s.runs.value.map(r => r.historyRunId), ['new-run'])
  s.params.value = '{"interval_days":15}'
  await s.save()
  assert.equal(calls[1][2].paramsDraft.interval_days, 15)
  s.params.value = '[]'
  await s.save()
  assert.equal(calls.length, 2)
  assert.match(s.error.value, /JSON 对象/)
  await s.archive()
  assert.equal(s.plan.value.status, 'ARCHIVED')
  s.dataset.value = 'nav.csv'
  await s.run()
  assert.equal(calls.length, 3)
})

test('run shows pending state, blocks repeat submission and refreshes linked history', async () => {
  let resolve
  const pending = new Promise(r => { resolve = r })
  const { state: s, events } = await setup({ run: () => pending })
  await s.open('plan-1'); s.dataset.value = 'nav.csv'
  const running = s.run()
  assert.equal(s.runState.value, '排队中')
  await new Promise(r => setTimeout(r, 5))
  assert.equal(s.runState.value, '运行中')
  await s.run()
  resolve({ history_run_id: 'new-run' }); await running
  assert.equal(s.runState.value, '成功')
  assert.equal(s.runs.value.length, 1)
  assert.deepEqual(events.at(-1), ['refreshHistory'])
})

test('API errors, permission failures, conflict and uncertain run remain visible', async () => {
  const { state: s } = await setup({ list: async () => { throw Object.assign(new Error('forbidden'), { response: { status: 403 } }) }, run: async () => { throw new Error('网络错误') } })
  await s.loadMore()
  assert.match(s.error.value, /权限/)
  assert.equal(s.busy.value, false)
  assert.match(s.failure({ response: { status: 401 } }), /登录/)
  assert.match(s.failure({ response: { status: 409 } }), /刷新/)
  await s.open('plan-1'); s.dataset.value = 'nav.csv'; await s.run()
  assert.equal(s.runState.value, '失败')
  assert.match(s.runMessage.value, /结果可能未知/)
  assert.equal(s.runs.value.length, 1)
})

test('workbench exposes research only and keeps empty/loading/error and evidence actions', async () => {
  assert.doesNotMatch(source, /orderApi|settlementApi|ledgerApi|brokerApi|confirmDraft|confirmSettlement/)
  assert.match(source, /历史回测不代表未来表现/)
  assert.match(source, /研究方案不是交易建议/)
  assert.match(source, /role="alert"/)
  assert.match(source, /role="status"/)
  assert.match(source, /暂无研究方案/)
  assert.match(source, /\$emit\('evidence', \[\.\.\.chosen\]\)/)
  const { state: s } = await setup({ list: async () => [] })
  await s.loadMore()
  assert.equal(s.plans.value.length, 0)
  assert.equal(s.more.value, false)
})

test('failed refresh clears stale editable plan and selections; retry restores detail', async () => {
  let offline = false
  const { state: s } = await setup({ list: async () => {
    if (offline) throw new Error('Network Error')
    return [fixture()]
  } })
  await s.open('plan-1'); s.chosen.value = ['new-run']
  offline = true
  await s.refresh()
  assert.equal(s.plan.value, null)
  assert.deepEqual(s.runs.value, [])
  assert.deepEqual(s.chosen.value, [])
  assert.match(s.error.value, /网络.*重试/)
  assert.match(s.failure({ response: { status: 404 } }), /不存在或无权访问/)
  offline = false
  await s.refresh(); await s.open('plan-1')
  assert.equal(s.plan.value.id, 'plan-1')
  assert.equal(s.error.value, '')
})

test('research API limits run body to dataset and uses encoded owner-scoped paths', async () => {
  const { apiClient, researchPlanApi } = await import('../../shared/dist/index.js')
  const original = apiClient.post
  const calls = []
  apiClient.post = async (...args) => { calls.push(args); return { data: { history_run_id: 'run' } } }
  try { await researchPlanApi.run('a/b', 'nav.csv'); assert.deepEqual(calls[0], ['/research-plans/a%2Fb/runs', { dataset: 'nav.csv' }, { timeout: 120000 }]) }
  finally { apiClient.post = original }
})
