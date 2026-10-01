import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as vue from 'vue'
const root = new URL('../', import.meta.url)
const source = await readFile(new URL('src/components/riskWatchModel.ts', root), 'utf8')
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText
const { validateRisk, evidenceState, dedupeEvents, riskError } = await import(`data:text/javascript,${encodeURIComponent(code)}`)
const config = (extra = {}) => ({ scope: 'PERSONAL', type: 'CONCENTRATION', productId: 1, threshold: 0, target: null, direction: 'BELOW', severity: 'WARNING', note: '', muted: false, ...extra })
async function setup(api) {
  const view = await readFile(new URL('src/views/RiskCenter.vue', root), 'utf8')
  let script = compileScript(parse(view).descriptor, { id: 'risk-test' }).content
  script = script.replace(/import \{([^}]+)\} from 'vue'/g, (_, names) => `const {${names.replace(/ as /g, ': ')}} = globalThis.__riskMocks.vue`)
  script = script.replace(/import \{([^}]+)\} from '@wealth-hub\/shared'/g, (_, names) => `const {${names}} = globalThis.__riskMocks`)
  script = script.replace(/import \{([^}]+)\} from '..\/components\/riskWatchModel'/g, (_, names) => `const {${names}} = globalThis.__riskMocks.model`)
  globalThis.__riskMocks = { vue: { ...vue, onMounted() {} }, riskWatchApi: api, model: await import(`data:text/javascript,${encodeURIComponent(code)}`) }
  const output = ts.transpileModule(script, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText
  const module = await import(`data:text/javascript,${encodeURIComponent(output)}#${Math.random()}`)
  return module.default.setup({}, { expose() {} })
}
test('page loads without evaluation; permission failure clears old evidence and retry recovers', async () => {
  let fail = false, evaluations = 0
  const rule = { id: 'r', config: config() }
  const state = await setup({ list: async () => { if (fail) throw { response: { status: 403 } }; return [rule] }, snapshots: async () => [{ ruleId: 'r' }], events: async () => [], evaluate: async () => { evaluations++ } })
  await state.refresh()
  assert.equal(evaluations, 0); assert.equal(state.snapshots.value.length, 1)
  fail = true; await state.refresh()
  assert.match(state.error.value, /权限/); assert.equal(state.snapshots.value.length, 0)
  fail = false; await state.refresh()
  assert.equal(state.error.value, ''); assert.equal(state.rules.value.length, 1)
  await state.evaluate(rule); assert.equal(evaluations, 1)
})
test('page validates form before saving and sends manual acknowledge/mute only', async () => {
  const calls = [], rule = { id: 'r', config: config() }
  const state = await setup({ list: async () => [rule], snapshots: async () => [], events: async () => [], create: async c => calls.push(['create', c]), acknowledge: async (...args) => calls.push(['ack', ...args]), mute: async (...args) => calls.push(['mute', ...args]) })
  state.edit(); state.save(); assert.ok(state.formError.value); assert.equal(calls.length, 0)
  state.form.value = config(); state.save(); await new Promise(r => setTimeout(r, 0)); assert.equal(calls[0][0], 'create')
  await state.acknowledge({ evidence: { ruleId: 'r' }, fingerprint: 'fp' }); assert.deepEqual(calls[1], ['ack', 'r', 'fp'])
  state.muteRule.value = 'r'; state.muteUntil.value = ''; state.mute(); await new Promise(r => setTimeout(r, 0)); assert.deepEqual(calls[2], ['mute', 'r', null])
})
test('rule form validates inclusive boundaries and missing fields', () => {
  for (const threshold of [0, 1]) assert.equal(validateRisk(config({ threshold })), '')
  for (const threshold of [-0.01, 1.01, null, NaN, Infinity]) assert.ok(validateRisk(config({ threshold })))
  assert.ok(validateRisk(config({ productId: null })))
  assert.equal(validateRisk(config({ type: 'RETURN', threshold: -0.2 })), '')
  assert.ok(validateRisk(config({ type: 'RETURN', direction: null })))
  for (const threshold of [0, 3650]) assert.equal(validateRisk(config({ type: 'STALE', threshold })), '')
  for (const threshold of [-1, 3651, 1.5]) assert.ok(validateRisk(config({ type: 'STALE', threshold })))
  assert.equal(validateRisk(config({ type: 'ALLOCATION_DEVIATION', assetType: 'CASH', target: 1 })), '')
  assert.ok(validateRisk(config({ type: 'ALLOCATION_DEVIATION', assetType: 'CASH', target: 1.01 })))
  assert.equal(validateRisk(config({ type: 'NOTE', productId: null, threshold: null })), '')
})
test('unknown is never displayed as zero or recovery; stale evidence stays explicit', () => {
  const s = { status: 'OK', observedValue: 0, sourceDataTimestamp: '2026-10-02', matched: true }
  assert.equal(evidenceState(s, '2026-10-02'), '当前命中')
  assert.match(evidenceState({ ...s, observedValue: null }, '2026-10-02'), /UNKNOWN/)
  assert.match(evidenceState({ ...s, status: 'UNKNOWN' }, '2026-10-02'), /UNKNOWN/)
  assert.match(evidenceState(s, '2026-10-03'), /陈旧/)
})
test('history deduplication preserves server ack/mute/resolved states', () => {
  const event = { fingerprint: 'hash', evidence: { ruleId: 'r' }, state: 'OPEN' }
  for (const state of ['ACKNOWLEDGED', 'MUTED', 'RESOLVED']) {
    const rows = dedupeEvents([event, { ...event, state }])
    assert.equal(rows.length, 1); assert.equal(rows[0].state, state)
  }
  assert.equal(dedupeEvents([event, { ...event, evidence: { ruleId: 'other' } }]).length, 2)
  assert.match(riskError({ response: { status: 403 } }), /权限/)
  assert.match(riskError({ response: { status: 401 } }), /登录/)
})
test('API routes and payloads only write observation metadata', async () => {
  const { apiClient, riskWatchApi } = await import('../../shared/dist/index.js')
  const saved = { get: apiClient.get, post: apiClient.post, patch: apiClient.patch }; const calls = []
  for (const method of Object.keys(saved)) apiClient[method] = async (...args) => { calls.push([method, ...args]); return { data: [] } }
  try {
    await riskWatchApi.list(2); await riskWatchApi.create(config()); await riskWatchApi.edit('r', config({ muted: true })); await riskWatchApi.evaluate('r'); await riskWatchApi.events('r', 3); await riskWatchApi.acknowledge('r', 'a/b'); await riskWatchApi.mute('r', null)
    assert.equal(calls[0][2].params.page, 2)
    assert.equal(calls[2][2].muted, true)
    assert.equal(calls[4][2].params.page, 3)
    assert.equal(calls[5][1], '/risk-watch-rules/r/events/a%2Fb/acknowledge')
    assert.deepEqual(calls[6][2], { mutedUntil: null })
    assert.ok(calls.every(c => c[1].startsWith('/risk-watch-rules')))
  } finally { Object.assign(apiClient, saved) }
  const view = await readFile(new URL('src/views/RiskCenter.vue', root), 'utf8')
  assert.doesNotMatch(view, /一键卖出|一键买入|自动再平衡|orderApi|settlementApi|ledgerApi|confirmDraft/)
  assert.match(view, /readonly: 'true'/)
  assert.match(view, /role="alert"/)
  assert.match(view, /role="status"/)
  assert.match(view, /加载更早历史/)
})
