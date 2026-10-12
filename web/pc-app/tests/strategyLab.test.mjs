import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as vue from 'vue'

const source = await readFile(new URL('../src/views/StrategyLab.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
let script = compileScript(descriptor, { id: 'strategy-lab-test' }).content
script = script.replace(/import \{([^}]+)\} from 'vue'/g, (_, names) => `const {${names.replace(/ as /g, ': ')}} = globalThis.__labMocks.vue`)
script = script.replace(/import \{([^}]+)\} from '@wealth-hub\/shared'/g, (_, names) => `const {${names}} = globalThis.__labMocks`)
script = script.replace(/import ResearchWorkbench from '[^']+'/g, 'const ResearchWorkbench = {}')
const code = ts.transpileModule(script, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2020 } }).outputText
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
const result = { run_id: 'run-1', strategy: { name: 'pure_sip', version: '1', params: { contribution: 100 } }, data_range: { start: '2025-01-01', end: '2025-12-31', rows: 365 }, metrics: { total_return: 0, annualized_return: null } }
async function setup(overrides = {}) {
  const calls = [], unmounts = [], scope = vue.effectScope()
  const user = vue.reactive({ token: 'test-session', user: { id: 1, familyId: 10 } })
  globalThis.__labMocks = {
    vue: { ...vue, onMounted() {}, onBeforeUnmount(fn) { unmounts.push(fn) } }, useUserStore: () => user,
    apiClient: {
      get: async path => ({ data: path.endsWith('datasets') ? ['nav.csv'] : [result] }),
      post: async (...args) => { calls.push(args); return { data: result } }, ...overrides.client,
    },
    backtestApi: { history: async () => [], compare: async () => ({ generated_at: '2026-10-12', runs: [], warnings: [], disclaimer: '历史', markdown: '来源与参数' }), research: async () => ({ candidates: [], excluded_runs: [] }), evidence: async () => new Blob(['evidence']), ...overrides.api },
  }
  const module = await import(`data:text/javascript,${encodeURIComponent(code)}#${Math.random()}`)
  const state = scope.run(() => module.default.setup({}, { expose() {} }))
  return { state, calls, user, dispose() { unmounts.forEach(fn => fn()); scope.stop() } }
}

test('controlled run validates server dataset, whitelist and boundaries, blocks duplicate and refreshes failures', async () => {
  const pending = deferred(), pages = []
  const { state: s, calls, dispose } = await setup({ client: { post: (...args) => { calls.push(args); return pending.promise } }, api: { history: async page => { pages.push(page); return [] } } })
  await s.loadData()
  s.data.value = '../other.csv'; await s.run(); assert.equal(calls.length, 0)
  s.data.value = 'nav.csv'; s.strategy.value = 'invented'; await s.run(); assert.equal(calls.length, 0)
  s.strategy.value = 'ma_enhanced'; s.window.value = 1; assert.equal(s.validRun.value, false)
  s.window.value = 20; s.interval.value = 1.5; assert.equal(s.validRun.value, false)
  s.interval.value = 30; s.contribution.value = Infinity; assert.equal(s.validRun.value, false)
  s.contribution.value = 100
  const run = s.run(); await s.run(); assert.equal(calls.length, 1)
  assert.deepEqual(calls[0], ['/backtest-lab/runs', { data: 'nav.csv', strategy: 'ma_enhanced', version: '1', params: { contribution: 100, interval_days: 30, ma_window: 20, dip_multiplier: 2 } }, { timeout: 120000 }])
  pending.reject(new Error('timeout')); await run
  assert.equal(s.running.value, false); assert.match(s.runMessage.value, /结果可能未知/); assert.deepEqual(pages, [0])
  assert.equal(s.results.value.length, 1) // previously fetched result stays historical
  assert.equal(s.percent(0), '0.00%'); assert.equal(s.percent(null), '—')
  dispose()
})

test('owner and family switch clears results, drafts, selections and ignores late data/history/run/report', async () => {
  const oldData = deferred(), oldHistory = deferred(), oldRun = deferred(), oldReport = deferred()
  let reads = 0, histories = 0
  const { state: s, user, dispose } = await setup({ client: { get: path => path.endsWith('datasets') && reads++ === 0 ? oldData.promise : Promise.resolve({ data: path.endsWith('datasets') ? ['new.csv'] : [] }), post: () => oldRun.promise }, api: { history: () => histories++ === 0 ? oldHistory.promise : Promise.resolve([]), compare: () => oldReport.promise } })
  const data = s.loadData(), history = s.loadMoreHistory()
  s.loading.value = false; s.datasets.value = ['nav.csv']; s.data.value = 'nav.csv'; s.selected.value = ['one', 'two']
  const run = s.run(), compare = s.compare(); s.planCandidate.value = { candidate_id: 'old' }
  user.user.familyId = 20
  assert.equal(s.results.value.length, 0); assert.deepEqual(s.selected.value, []); assert.equal(s.planCandidate.value, null)
  oldData.resolve({ data: ['old.csv'] }); oldHistory.resolve([{ historyRunId: 'old' }]); oldRun.resolve({ data: result }); oldReport.resolve({ runs: ['old'] })
  await Promise.all([data, history, run, compare]); await vue.nextTick()
  assert.deepEqual(s.datasets.value, ['new.csv']); assert.deepEqual(s.history.value, []); assert.equal(s.report.value, null); assert.equal(s.runMessage.value, '')
  user.token = null
  assert.deepEqual(s.datasets.value, []); assert.equal(s.data.value, '')
  dispose()
})

test('selection revision rejects late compare/research and stale evidence even when same ids are reselected', async () => {
  const comparison = deferred(), candidate = deferred(), evidence = deferred()
  const { state: s, dispose } = await setup({ api: { compare: () => comparison.promise, research: () => candidate.promise, evidence: () => evidence.promise } })
  s.selected.value = ['one', 'two']
  const compare = s.compare(), research = s.research(), download = s.exportEvidence()
  s.selected.value = ['one']; s.selected.value = ['one', 'two']
  comparison.resolve({ runs: ['obsolete'] }); candidate.resolve({ candidates: ['obsolete'] }); evidence.resolve(new Blob(['obsolete']))
  await Promise.all([compare, research, download]) // no document in Node: a stale download must never attempt a click
  assert.equal(s.report.value, null); assert.equal(s.researchReport.value, null); assert.equal(s.exportError.value, '')
  assert.equal(s.comparing.value, false); assert.equal(s.researching.value, false); assert.equal(s.exporting.value, false)
  dispose()
})

test('history pagination and directory failure are distinguishable from empty data; manual retry uses same page', async () => {
  const pages = []; let offline = false
  const { state: s, dispose } = await setup({ api: { history: async page => { pages.push(page); if (offline) throw new Error('unconfigured history root'); return page === 0 ? Array.from({ length: 50 }, (_, id) => ({ historyRunId: String(id), status: id === 0 ? 'TIMEOUT' : 'SUCCESS' })) : [] } } })
  await s.loadMoreHistory(); assert.equal(s.hasMoreHistory.value, true)
  s.toggle('0'); assert.deepEqual(s.selected.value, [])
  for (const id of ['1', '2', '3', '4', '5', '6']) s.toggle(id)
  assert.equal(s.selected.value.length, 5)
  offline = true; await s.loadMoreHistory(); assert.equal(s.historyPage.value, 1); assert.match(s.historyError.value, /目录配置/)
  offline = false; await s.loadMoreHistory(); assert.deepEqual(pages, [0, 1, 1]); assert.equal(s.hasMoreHistory.value, false)
  assert.equal(s.runStatus('TIMEOUT'), '超时'); assert.match(s.requestError({ response: { status: 403 } }, ''), /权限/)
  dispose()
})

test('current failed exports remain actionable, successful evidence and reports keep original content', async () => {
  let fail = true; const clicks = [], blobs = []
  const { state: s, dispose } = await setup({ api: { evidence: async () => { if (fail) throw { response: { status: 403 } }; return new Blob(['original source evidence']) } } })
  s.selected.value = ['one', 'two']; await s.exportEvidence(); assert.match(s.exportError.value, /权限/)
  const originalDocument = globalThis.document, create = URL.createObjectURL, revoke = URL.revokeObjectURL
  globalThis.document = { createElement: () => ({ click() { clicks.push(this.download) } }) }
  URL.createObjectURL = blob => { blobs.push(blob); return 'blob:test' }; URL.revokeObjectURL = () => {}
  try {
    fail = false; await s.exportEvidence(); assert.equal(await blobs[0].text(), 'original source evidence')
    s.report.value = { generated_at: '2026-10-12', markdown: 'original dates, source hash, params and warnings', runs: [], warnings: ['quality'], disclaimer: 'historical' }
    s.download('md'); s.download('json')
    assert.equal(await blobs[1].text(), s.report.value.markdown); assert.deepEqual(JSON.parse(await blobs[2].text()), s.report.value)
    assert.deepEqual(clicks, ['backtest-research-evidence.zip', 'backtest-compare-2026-10-12.md', 'backtest-compare-2026-10-12.json'])
  } finally { globalThis.document = originalDocument; URL.createObjectURL = create; URL.revokeObjectURL = revoke; dispose() }
})

test('same-page keyboard anchors, narrow-screen styles and backend-only research boundary remain available', async () => {
  for (const id of ['research-title', 'controlled-run', 'history-compare-title', 'candidate-title']) assert.match(source, new RegExp(`href="#${id}"`))
  assert.match(source, /tabindex="0" role="region"/)
  assert.doesNotMatch(source, /localStorage|orderApi|ledgerApi|settlementApi|brokerApi/)
  const css = await readFile(new URL('../src/styles/strategy-lab.css', import.meta.url), 'utf8')
  assert.match(css, /max-width: 760px/); assert.match(css, /focus-visible/); assert.match(css, /var\(--wh-decoration/)
  const layout = await readFile(new URL('../src/layouts/MainLayout.vue', import.meta.url), 'utf8')
  assert.match(layout, /showBusinessActions = computed\(\(\) => !\['GoalBudgetCenter', 'DataReadiness', 'StrategyLab'[,\]]/)
})
