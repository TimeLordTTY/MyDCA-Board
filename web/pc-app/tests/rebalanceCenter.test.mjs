import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as vue from 'vue'
const root = new URL('../', import.meta.url)
const source = await readFile(new URL('src/components/allocationPolicyModel.ts', root), 'utf8')
const transpile = s => ts.transpileModule(s, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText
const model = await import(`data:text/javascript,${encodeURIComponent(transpile(source))}`)
const config = (extra = {}) => ({ scope: 'PERSONAL', productId: 1, assetType: null, target: 0.5, lowerBound: 0, upperBound: 1, returnThreshold: null, takeProfitThresholds: [], enabled: true, note: '', ...extra })
async function setup(api) {
  const view = await readFile(new URL('src/views/RebalanceCenter.vue', root), 'utf8')
  let script = compileScript(parse(view).descriptor, { id: 'allocation-test' }).content
  script = script.replace(/import \{([^}]+)\} from 'vue'/g, (_, names) => `const {${names.replace(/ as /g, ': ')}} = globalThis.__allocationMocks.vue`)
  script = script.replace(/import \{([^}]+)\} from '@wealth-hub\/shared'/g, (_, names) => `const {${names}} = globalThis.__allocationMocks`)
  script = script.replace(/import \{([^}]+)\} from '..\/components\/allocationPolicyModel'/g, (_, names) => `const {${names}} = globalThis.__allocationMocks.model`)
  globalThis.__allocationMocks = { vue: { ...vue, onMounted() {} }, allocationPolicyApi: api, model }
  const module = await import(`data:text/javascript,${encodeURIComponent(transpile(script))}#${Math.random()}`)
  return module.default.setup({}, { expose() {} })
}
test('inclusive bands, invalid numbers and observation threshold boundaries', () => {
  for (const target of [0, 1]) assert.equal(model.validateAllocation(config({ target })), '')
  for (const target of [-1, 1.01, NaN, Infinity, '']) assert.ok(model.validateAllocation(config({ target })))
  for (const extra of [{ lowerBound: 0.6 }, { upperBound: 0.4 }, { productId: 0 }, { productId: 1.5 }, { assetType: 'CASH' }, { productId: null }, { returnThreshold: -1 }, { takeProfitThresholds: [0.2, 0.2] }, { takeProfitThresholds: [0.3, 0.2] }, { takeProfitThresholds: Array(21).fill(1) }, { note: 'x'.repeat(2001) }]) assert.ok(model.validateAllocation(config(extra)))
  assert.equal(model.validateAllocation(config({ takeProfitThresholds: [0, 0.1, 1] })), '')
  assert.ok(model.validateAllocation(config({ productId: null, assetType: 'CASH', returnThreshold: 0 })))
})
test('UNKNOWN never becomes zero, stale dates explicit and after weights use rounded amounts', () => {
  assert.match(model.percent(null), /UNKNOWN/); assert.match(model.amount(null), /UNKNOWN/)
  assert.equal(model.percent(0), '0.00%')
  const p = { status: 'OK', currentWeight: 0.4, totalAssets: 1000, dataDate: '2026-10-02', prices: [] }
  assert.equal(model.afterWeight(p, { selectedAdjustment: 100 }), 0.5)
  assert.equal(model.afterWeight({ ...p, status: 'UNKNOWN' }, { selectedAdjustment: 100 }), null)
  assert.match(model.dataState(p, '2026-10-03'), /陈旧/)
  assert.match(model.dataState({ ...p, status: 'UNKNOWN' }, '2026-10-02'), /UNKNOWN/)
})
test('preview is explicit, permission errors clear evidence, retry recovers', async () => {
  let previews = 0, fail = false
  const rule = { id: 'r', config: config() }
  const state = await setup({ list: async () => [rule], preview: async () => { previews++; if (fail) throw { response: { status: 403 } }; return { policyId: 'r' } }, evaluate: async () => ({ policyId: 'r', allocation: null }) })
  await state.refresh(); assert.equal(previews, 0)
  await state.observe(rule); assert.equal(previews, 0)
  await state.loadPreview(rule); assert.equal(previews, 1); assert.ok(state.preview.value)
  fail = true; await state.loadPreview(rule); assert.equal(state.preview.value, null); assert.equal(state.evaluation.value, null); assert.match(state.error.value, /权限/)
  fail = false; await state.loadPreview(rule); assert.equal(state.error.value, '')
})
test('create/edit/toggle metadata and reject malformed form before API call', async () => {
  const calls = [], rule = { id: 'r', config: config() }
  const state = await setup({ list: async () => [rule], create: async c => calls.push(['create', c]), edit: async (id, c) => calls.push(['edit', id, c]) })
  state.edit(); await state.save(); assert.equal(calls.length, 0); assert.ok(state.formError.value)
  state.form.value = config(); state.thresholdText.value = '0.1,'; await state.save(); assert.equal(calls.length, 0)
  state.thresholdText.value = '0, 0.1'; await state.save(); assert.equal(calls[0][0], 'create')
  state.edit(rule); state.form.value.target = 0.6; await state.save(); assert.equal(calls[1][2].target, 0.6)
  await state.toggle(rule); assert.equal(calls[2][2].enabled, false)
  assert.equal(rule.config.target, 0.5)
})
test('API uses only configuration metadata and GET preview; page has no financial action', async () => {
  const { apiClient, allocationPolicyApi } = await import('../../shared/dist/index.js')
  const saved = { get: apiClient.get, post: apiClient.post, patch: apiClient.patch }, calls = []
  for (const method of Object.keys(saved)) apiClient[method] = async (...args) => { calls.push([method, ...args]); return { data: [] } }
  try {
    await allocationPolicyApi.list(2); await allocationPolicyApi.create(config()); await allocationPolicyApi.edit('a/b', config()); await allocationPolicyApi.evaluate('a/b'); await allocationPolicyApi.preview('a/b')
    assert.equal(calls[0][2].params.page, 2); assert.deepEqual(calls[4], ['get', '/allocation-policies/a%2Fb/preview'])
    assert.ok(calls.every(c => c[1].startsWith('/allocation-policies')))
  } finally { Object.assign(apiClient, saved) }
  const view = await readFile(new URL('src/views/RebalanceCenter.vue', root), 'utf8')
  assert.doesNotMatch(view, /一键再平衡|自动止盈|立即买卖|orderApi|settlementApi|ledgerApi|confirmDraft/)
  assert.match(view, /readonly: 'true'/); assert.match(view, /role="alert"/); assert.match(view, /role="status"/)
})
