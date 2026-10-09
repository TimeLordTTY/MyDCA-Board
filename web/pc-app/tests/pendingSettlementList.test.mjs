import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { execFileSync } from 'node:child_process'
import ts from 'typescript'
const source = await readFile(new URL('../src/components/pendingSettlementListModel.ts', import.meta.url), 'utf8')
const model = await import(`data:text/javascript,${encodeURIComponent(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText)}`)
const row = (id = 'order-1') => ({ orderId: id, orderType: 'BUY', status: 'PENDING', amount: 0, currency: 'CNY', productName: '测试产品', expectedConfirmDate: '2026-10-09' })
const deferred = () => { let resolve, reject; const promise = new Promise((a,b) => { resolve=a; reject=b }); return { promise, resolve, reject } }
const setup = read => { let state = { status: 'idle', data: null }, identity = 'fixture'; const loader = model.createPendingSettlementLoader(read, () => identity, s => { state=s }); return { loader, state: () => state, change: value => { identity=value } } }
test('initial/loading/verified empty and N records', async () => {
  for (const rows of [[], [row(),row('order-2')]]) { const d=deferred(), h=setup(() => d.promise); assert.equal(h.state().status,'idle'); const p=h.loader.load(); assert.deepEqual(h.state(),{status:'loading',data:null}); d.resolve(rows); await p; assert.equal(h.state().status,'success'); assert.equal(h.state().data.length,rows.length) }
})
test('500/401/403/offline clear previous rows; manual retry recovers', async () => {
  for (const reason of [500,401,403,'offline']) { let fail=false; const h=setup(async () => { if(fail) throw Error(String(reason)); return [row()] }); await h.loader.load(); fail=true; const p=h.loader.load(); assert.equal(h.state().data,null); await p; assert.deepEqual(h.state(),{status:'error',data:null}); fail=false; await h.loader.load(); assert.equal(h.state().status,'success') }
})
test('nonarrays, malformed/duplicate IDs, abnormal types and non-PENDING reject whole response', async () => {
  const invalid=[null,{},'[]',[row(),row()],...[undefined,null,'','../x','a?confirm=true',1].map(orderId=>[{...row(),orderId}]),...['CONFIRMED','FAILED','CANCELLED',null,undefined].map(status=>[{...row(),status}]),...[null,'DRAFT','OTHER',{}].map(orderType=>[{...row(),orderType}])]
  for(const value of invalid) { const h=setup(async()=>value); await h.loader.load(); assert.deepEqual(h.state(),{status:'error',data:null}) }
})
test('unknown amounts never become zero; date/product/currency incompleteness explicit', () => {
  for(const amount of [undefined,null,NaN,Infinity,-Infinity,'0']) { const [r]=model.validatePendingSettlements([{...row(),amount}]); assert.equal(r.amount,null); assert.equal(r.incomplete,true) }
  assert.equal(model.validatePendingSettlements([row()])[0].amount,0)
  assert.equal(model.validatePendingSettlements([row()])[0].incomplete,false)
  for(const [field,values] of Object.entries({expectedConfirmDate:[null,undefined,'','2026-02-30','invalid'],productName:[null,undefined,'',{}],currency:[null,undefined,'','OTHER']})) {
    for(const value of values) { const [r]=model.validatePendingSettlements([{...row(),[field]:value}]); assert.equal(r[field],null); assert.equal(r.incomplete,true) }
  }
})
test('rapid refresh newest success wins over late success or failure', async () => {
  for(const fail of [false,true]) { const d=deferred(); let calls=0; const h=setup(()=>calls++ ? Promise.resolve([row('new')]) : d.promise); const p=h.loader.load(); await h.loader.load(); if(fail)d.reject(Error('offline')); else d.resolve([]); await p; assert.equal(h.state().data[0].orderId,'new') }
})
test('token/family/role/route change, reset, unmount/deactivation discard old responses', async () => {
  for(const change of ['token','family','role','route','reset','suspend']) { const d=deferred(),h=setup(()=>d.promise); const p=h.loader.load(); if(change==='reset')h.loader.reset(); else if(change==='suspend')h.loader.suspend(); else h.change(change); d.resolve([row()]); await p; assert.deepEqual(h.state(),{status:'idle',data:null}) }
  let calls=0;const h=setup(async()=>{calls++;return []});h.loader.suspend();await h.loader.load();assert.equal(calls,0);h.loader.activate();assert.equal(calls,0);await h.loader.load();assert.equal(calls,1)
})
test('Dashboard independent GET survives upstream failure; wiring preserves original manual chain and todo isolation', async () => {
  const dashboard=(await readFile(new URL('../src/views/Dashboard.vue',import.meta.url),'utf8')).replaceAll('\r\n','\n')
  const baseline=execFileSync('git',['show','HEAD:web/pc-app/src/views/Dashboard.vue'],{encoding:'utf8'}).replaceAll('\r\n','\n')
  const handlers=s=>s.slice(s.indexOf('// ---------- 今日建议 - 订单结算相关逻辑'),s.indexOf('function handleTodoItemClick'))
  assert.equal(handlers(dashboard),handlers(baseline))
  assert.match(dashboard,/onBeforeUnmount\(pendingSettlementLoader.suspend\)/);assert.match(dashboard,/onDeactivated\(pendingSettlementLoader.suspend\)/)
  assert.match(dashboard,/router.currentRoute.value.fullPath/);assert.match(dashboard,/pendingSettlementState.status === 'success' && pendingSettlements.length === 0/)
  assert.match(dashboard,/@click="handleConfirmSettlement\(settlement.orderId\)"/)
  assert.match(dashboard,/未接入 \/ 未统计/);assert.doesNotMatch(source,/settlementCount|todoApi|console\.|confirm\(|localStorage/)
  const start=dashboard.slice(dashboard.indexOf('async function loadData() {'),dashboard.indexOf('// 加载资产概览'))
  assert.ok(start.indexOf('pendingSettlementLoader.load()')<start.indexOf('await accountStore.fetchAccounts()'))
  const h=setup(async()=>[row()]);let requested
  await assert.rejects((async()=>{requested=h.loader.load();await Promise.reject(Error('upstream'))})());await requested;assert.equal(h.state().status,'success')
})
