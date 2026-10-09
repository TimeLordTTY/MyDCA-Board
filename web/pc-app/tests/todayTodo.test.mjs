import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
const source = await readFile(new URL('../src/components/todayTodoModel.ts', import.meta.url), 'utf8')
const model = await import(`data:text/javascript,${encodeURIComponent(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText)}`)
const item = id => ({ type: 'DRAFT', refId: String(id), title: '测试草稿', status: 'DRAFT', actionPath: '/drafts?draftId=' + id })
const data = n => ({ date: '2026-10-09', totalCount: n, draftCount: n, settlementCount: 0, suggestionCount: 0, items: Array.from({ length: Math.min(n, 20) }, (_, i) => item(i + 1)) })
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
const setup = read => { let state = { status: 'idle', data: null }, owner = 'fixture-a'; const loader = model.createTodayTodoLoader(read, () => owner, s => { state = s }); return { loader, state: () => state, change: () => { owner = 'fixture-b'; loader.reset() } } }
test('initial/loading and successful zero/N/limited details preserve covered counts', async () => {
  for (const n of [0, 2, 25]) { const d = deferred(), h = setup(() => d.promise); assert.equal(h.state().status, 'idle'); const p = h.loader.load(); assert.deepEqual(h.state(), { status: 'loading', data: null }); d.resolve(data(n)); await p; assert.equal(h.state().status, 'success'); assert.equal(h.state().data.draftCount, n) }
})
test('401/403/500/offline are unknown, retry recovers', async () => {
  for (const reason of [401, 403, 500, 'offline']) { let fail = true; const h = setup(async () => { if (fail) throw new Error(String(reason)); return data(0) }); await h.loader.load(); assert.deepEqual(h.state(), { status: 'error', data: null }); fail = false; await h.loader.load(); assert.equal(h.state().status, 'success') }
})
test('null, missing, bad counts/date/list/type/text and inconsistent structures fail closed', async () => {
  const bad = [null, {}, ...['totalCount','draftCount','settlementCount','suggestionCount'].flatMap(k => [undefined,null,NaN,Infinity,-1,'0',0.1].map(v => ({ ...data(0), [k]: v }))), { ...data(0), date: '2026-02-30' }, { ...data(0), items: null }, { ...data(2), items: [item(1)] }, { ...data(2), items: [item(1), item(1)] }, { ...data(0), settlementCount: 1 }, { ...data(0), suggestionCount: 1 }, ...[{ type: 'SETTLEMENT' }, { title: null }, { description: {} }, { refId: '../x' }, { status: 'CONFIRMED' }, { actionPath: {} }].map(v => ({ ...data(1), items: [{ ...item(1), ...v }] }))]
  for (const value of bad) { const h = setup(async () => value); await h.loader.load(); assert.equal(h.state().status, 'error'); assert.equal(h.state().data, null) }
})
test('refresh clears old success immediately and newest request wins', async () => {
  const a = deferred(), b = deferred(); let i = 0; const h = setup(() => i++ ? b.promise : a.promise)
  const pa = h.loader.load(), pb = h.loader.load(); b.resolve(data(2)); await pb; a.resolve(data(0)); await pa; assert.equal(h.state().data.draftCount, 2)
  const pc = h.loader.load(); assert.equal(h.state().data, null); await pc
})
test('late failure cannot overwrite newer success', async () => {
  const a = deferred(); let i = 0; const h = setup(() => i++ ? Promise.resolve(data(1)) : a.promise); const p = h.loader.load(); await h.loader.load(); a.reject(new Error('offline')); await p; assert.equal(h.state().status, 'success')
})
test('account change and teardown invalidate pending response without new query', async () => {
  for (const invalidate of ['change','reset']) { const d = deferred(); let calls = 0; const h = setup(() => { calls++; return d.promise }); const p = h.loader.load(); if (invalidate === 'change') h.change(); else h.loader.reset(); d.resolve(data(1)); await p; assert.deepEqual(h.state(), { status: 'idle', data: null }); assert.equal(calls, 1) }
})
test('identity mismatch without watcher and midnight invalidate late response', async () => {
  const d = deferred(); let owner = 'a', state; const h = model.createTodayTodoLoader(() => d.promise, () => owner, s => { state = s }); const p = h.load(); owner = 'b'; d.resolve(data(1)); await p; assert.equal(state.status, 'idle')
  const OriginalDate = globalThis.Date; let tomorrow = false
  globalThis.Date = class extends OriginalDate { constructor(...args) { super(...(args.length ? args : [tomorrow ? '2026-10-10T12:00:00Z' : '2026-10-09T12:00:00Z'])) } }
  try { const e = deferred(), h2 = setup(() => e.promise); const p2 = h2.loader.load(); tomorrow = true; e.resolve(data(1)); await p2; assert.equal(h2.state().status, 'idle') } finally { globalThis.Date = OriginalDate }
})
test('navigation ignores actionPath and rejects unknown types or unsafe IDs', () => {
  assert.deepEqual(model.draftDestination({ ...item(1), actionPath: 'https://invalid.example' }), { name: 'DraftInbox', query: { draftId: '1' } })
  for (const v of [{ ...item(1), type: 'SETTLEMENT' }, { ...item(1), refId: '1&confirm=true' }, { ...item(1), refId: '//evil' }]) assert.equal(model.draftDestination(v), null)
})
test('Dashboard wiring keeps independent settlements unchanged and todo GET/manual navigation only', async () => {
  const dashboard = (await readFile(new URL('../src/views/Dashboard.vue', import.meta.url), 'utf8')).replaceAll('\r\n', '\n')
  assert.match(dashboard, /未接入 \/ 未统计/); assert.match(dashboard, /当前已加载的草稿待办为0/); assert.match(dashboard, /手动重试/)
  assert.doesNotMatch(dashboard, /router.push\(item.actionPath\)|console.warn\('加载今日待办/)
  assert.match(dashboard, /watch\(\[\(\) => userStore.token, \(\) => userStore.user\], todoLoader.reset/)
  assert.match(dashboard, /onBeforeUnmount\(todoLoader.suspend\)/); assert.match(dashboard, /onDeactivated\(todoLoader.suspend\)/)
  const cp = await import('node:child_process'); const baseline = cp.execFileSync('git', ['show', 'HEAD:web/pc-app/src/views/Dashboard.vue'], { encoding: 'utf8' }).replaceAll('\r\n', '\n')
  const handlers = s => s.slice(s.indexOf('// ---------- 今日建议 - 订单结算相关逻辑'), s.indexOf('function handleTodoItemClick'))
  assert.equal(handlers(dashboard), handlers(baseline))
  assert.doesNotMatch(source, /confirm\(|settlementApi|orderApi|console\.|localStorage|fetch\(/)
})

test('unmounted/deactivated loader blocks delayed initialization; activation does not query', async () => {
  let calls = 0; const h = setup(async () => { calls++; return data(0) })
  h.loader.suspend(); await h.loader.load(); assert.equal(calls, 0)
  h.loader.activate(); assert.equal(calls, 0); await h.loader.load(); assert.equal(calls, 1)
  h.change(); assert.deepEqual(h.state(), { status: 'idle', data: null })
})
