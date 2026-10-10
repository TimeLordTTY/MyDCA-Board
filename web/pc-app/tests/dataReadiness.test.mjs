import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import * as vue from 'vue'
const root = new URL('../', import.meta.url)
const transpile = source => ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText
const model = await import(`data:text/javascript,${encodeURIComponent(transpile(await readFile(new URL('src/components/dataReadinessModel.ts', root), 'utf8')))}`)
const view = await readFile(new URL('src/views/DataReadiness.vue', root), 'utf8')
async function setup(api) {
  let script = compileScript(parse(view).descriptor, { id: 'readiness-test' }).content
  script = script.replace(/import \{([^}]+)\} from 'vue'/g, (_, names) => `const {${names.replace(/ as /g, ': ')}} = globalThis.__readiness.vue`)
    .replace(/import \{([^}]+)\} from '@wealth-hub\/shared'/g, (_, names) => `const {${names}} = globalThis.__readiness`)
    .replace(/import \{([^}]+)\} from '..\/components\/dataReadinessModel'/g, (_, names) => `const {${names}} = globalThis.__readiness.model`)
  globalThis.__readiness = { vue: { ...vue, onMounted() {} }, model, dataReadinessApi: api }
  const module = await import(`data:text/javascript,${encodeURIComponent(transpile(script))}#${Math.random()}`)
  return module.default.setup({}, { expose() {} })
}
const evidence = (extra = {}) => ({ area: 'ASSETS', state: 'PARTIAL', reason: '部分已知', source: '资产来源', dataTime: '2020-01-01', nextStep: '人工核对', ...extra })
test('missing, partial, unavailable, stale and schema evidence remain explicit', () => {
  assert.equal(model.rows(null, ['ASSETS'])[0].state, 'UNKNOWN')
  assert.match(model.stateLabel(evidence()), /部分已知/)
  assert.match(model.stateLabel(evidence({ state: 'UNAVAILABLE' })), /读取失败/)
  assert.match(model.freshness(evidence(), new Date('2026-10-08')), /过期/)
  assert.match(model.freshness(evidence({ dataTime: '2026-10-05' }), new Date('2026-10-08T12:00:00')), /近期/)
  assert.match(model.freshness(evidence({ dataTime: '2026-10-09' }), new Date('2026-10-08T12:00:00')), /时间异常/)
  assert.match(model.freshness(evidence({ dataTime: '2026-10' })), /未核实/)
  assert.match(model.stateLabel(evidence({ area: 'SCHEMA', state: 'READY' })), /无法实时验证/)
  assert.equal(model.rows({ evidence: [evidence()] }, ['ASSETS', 'MARKET'])[1].state, 'UNKNOWN')
})
test('page clears previous evidence after permission, network or timeout failure and recovers', async () => {
  let failure, calls = []
  const state = await setup({ diagnose: async (scope, month) => { calls.push([scope, month]); if (failure) throw failure; return { scope, month, checkedAt: '2026-10-08', evidence: [evidence()] } } })
  for (const f of [{ response: { status: 403 } }, { response: { status: 401 } }, new Error('secret network debug'), { code: 'ECONNABORTED' }]) {
    failure = null; await state.refresh(); assert.equal(state.report.value.evidence.length, 1)
    failure = f; await state.refresh(); assert.equal(state.report.value, null); assert.ok(state.error.value); assert.doesNotMatch(state.error.value, /secret/)
  }
  failure = null; state.scope.value = 'FAMILY'; await state.refresh(); assert.equal(calls.at(-1)[0], 'FAMILY')
  state.month.value = '9999-01'; await state.refresh(); assert.equal(state.report.value, null); assert.match(state.error.value, /有效月份/)
})
test('empty evidence produces unknown sections; summary copies no server prose or values', async () => {
  const state = await setup({ diagnose: async (scope, month) => ({ scope, month, evidence: [] }) })
  await state.refresh(); assert.equal(model.rows(state.report.value, ['GOALS'])[0].state, 'UNKNOWN')
  const summary = model.issueSummary({ scope: 'PERSONAL', evidence: [evidence({ reason: 'token=secret 123456', source: 'db-password', nextStep: 'private host' })] })
  assert.doesNotMatch(summary, /secret|123456|db-password|private host/)
  assert.match(summary, /部分已知/); assert.match(summary, /无法实时验证/)
})
test('refresh is bounded and clipboard failure has a safe retry state', async () => {
  let resolve, calls = 0
  const state = await setup({ diagnose: (scope, month) => { calls++; return new Promise(r => { resolve = () => r({ scope, month, evidence: [evidence()] }) }) } })
  const pending = state.refresh(); await state.refresh(); assert.equal(calls, 1)
  resolve(); await pending
  const original = Object.getOwnPropertyDescriptor(globalThis, 'navigator')
  let copied = ''
  try {
    Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { clipboard: { writeText: async () => { throw new Error('internal secret') } } } })
    await state.copySummary(); assert.match(state.copyStatus.value, /剪贴板/); assert.doesNotMatch(state.copyStatus.value, /secret/)
    navigator.clipboard.writeText = async text => { copied = text }
    await state.copySummary(); assert.match(copied, /脱敏摘要/); assert.match(state.copyStatus.value, /已复制/)
    state.clearReport(); assert.equal(state.report.value, null); assert.equal(state.copyStatus.value, '')
  } finally { if (original) Object.defineProperty(globalThis, 'navigator', original); else delete globalThis.navigator }
})
test('shared API uses only authorized GET with scope/month; navigation excludes global write buttons', async () => {
  const { apiClient, dataReadinessApi } = await import('../../shared/dist/index.js')
  const saved = apiClient.get; const calls = []
  apiClient.get = async (...args) => { calls.push(args); return { data: { evidence: [] } } }
  try { await dataReadinessApi.diagnose('FAMILY', '2026-10') } finally { apiClient.get = saved }
  assert.deepEqual(calls, [['/data-readiness', { params: { scope: 'FAMILY', month: '2026-10' } }]])
  const layout = await readFile(new URL('src/layouts/MainLayout.vue', root), 'utf8')
  assert.equal((layout.match(/\['GoalBudgetCenter', 'DataReadiness'\]/g) || []).length, 3)
  assert.match(layout, /label: '数据就绪检查'/)
  assert.match(await readFile(new URL('src/router/index.ts', root), 'utf8'), /import\('\.\.\/views\/DataReadiness.vue'\)/)
  assert.doesNotMatch(view, /orderApi|settlementApi|ledgerApi|apiClient\.post|v-html/)
  assert.match(view, /role="alert"/); assert.match(view, /role="status"/); assert.match(view, /focus-visible/)
  assert.deepEqual(compileTemplate({ source: parse(view).descriptor.template.content, filename: 'DataReadiness.vue', id: 'readiness' }).errors, [])
})

test('静态迁移告警消失，结构说明限定目标环境和证据访问权限', () => {
  assert.doesNotMatch(view, /未核实部署|演练未执行|目标环境待部署方验证/)
  assert.match(view, /当前页面不能实时验证其结构状态/)
  assert.match(view, /docs\/mydca_production_deployment_20261010\.md/)
  assert.match(view, /仅适用于报告中的目标环境/)
  assert.match(view, /需有仓库访问权限/)
  assert.doesNotMatch(view, /所有环境已完成部署/)
})

test('结构来源缺失或旧接口断言不伪装成功，也不误报迁移未部署', async () => {
  for (const items of [[], [evidence({ area: 'SCHEMA', state: 'UNKNOWN', reason: '未获部署确认' })], [evidence({ area: 'SCHEMA', state: 'READY' })]]) {
    const state = await setup({ diagnose: async (scope, month) => ({ scope, month, evidence: items }) })
    await state.refresh()
    const schema = model.rows(state.report.value, ['SCHEMA'])[0]
    assert.equal(schema.state, 'UNKNOWN')
    assert.match(schema.reason, /接口不提供迁移覆盖事实/)
    assert.doesNotMatch(schema.reason, /未获部署确认|所有环境已完成部署/)
    assert.match(model.issueSummary(state.report.value), /无法实时验证/)
  }
})

test('手动刷新保留资产目标预算部分已知，权限和网络失败清空证据后可重试', async () => {
  let failure, calls = 0
  const items = ['ASSETS', 'GOALS', 'BUDGETS'].map(area => evidence({ area }))
  const state = await setup({ diagnose: async (scope, month) => {
    calls++
    if (failure) throw failure
    return { scope, month, evidence: items }
  } })
  for (const [error, message] of [[{ response: { status: 401 } }, /登录已失效/], [{ response: { status: 403 } }, /家庭管理员权限/], [new Error('private network'), /网络断开/]]) {
    failure = null
    await state.refresh()
    for (const row of model.rows(state.report.value, ['ASSETS', 'GOALS', 'BUDGETS'])) {
      assert.equal(row.state, 'PARTIAL')
      assert.match(model.stateLabel(row), /部分已知/)
    }
    failure = error
    await state.refresh()
    assert.equal(state.report.value, null)
    assert.match(state.error.value, message)
    assert.equal(model.rows(state.report.value, ['GOALS'])[0].state, 'UNKNOWN')
  }
  failure = null
  await state.refresh()
  assert.equal(calls, 7)
  assert.equal(state.error.value, '')
  assert.equal(state.report.value.evidence[1].state, 'PARTIAL')
})
