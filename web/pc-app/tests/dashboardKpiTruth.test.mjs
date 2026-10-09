import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
const source = await readFile(new URL('../src/components/dashboardKpiTruthModel.ts', import.meta.url), 'utf8')
const m = await import(`data:text/javascript,${encodeURIComponent(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText)}`)
const zero = () => ({ accounts: [], overview: { cashBalance: 0, liability: 0, todayPnl: 0, monthInflow: 0 }, holdings: [], prices: new Map() })
const holding = (id = 1) => ({ productId: id, channel: 'EXCHANGE', totalShares: 2, avgCost: 3 })
const deferred = () => { let resolve, reject; const promise = new Promise((a,b) => { resolve=a; reject=b }); return { promise, resolve, reject } }
test('first paint and loading have no fake zero', () => {
 for (const a of Object.values(m.kpiTruth(null))) assert.equal(a.value, null)
 for (const a of Object.values(m.kpiTruth(null, true))) { assert.equal(a.status, 'loading'); assert.doesNotMatch(m.amountText(a, String), /0/) }
})
test('successful empty accounts and holdings preserve explicit zero', () => {
 for (const a of Object.values(m.kpiTruth(zero()))) assert.deepEqual(a, { status: 'complete', value: 0 })
})
test('negative balances preserve cash plus position minus liability', () => {
 const s=zero(); s.accounts=[{balance:-5,reservedAmount:2,fundUsage:'SPENDABLE'}]; s.overview.cashBalance=-5
 const r=m.kpiTruth(s); assert.equal(r.available.value,-7); assert.equal(r.spendable.value,-7); assert.equal(r.netWorth.value,-5)
})
for (const input of [null, undefined, NaN, Infinity, '0', '', {}]) test(`invalid amounts ${String(input)} do not become zero`, () => {
 const s=zero(); s.overview.liability=input; s.accounts=[{balance:input,reservedAmount:input}]
 const r=m.kpiTruth(s); assert.equal(r.liability.value,null); assert.equal(r.available.value,null); assert.equal(r.netWorth.value,null)
})
for (const key of ['accounts','overview','holdings']) test(`${key} GET failure is unknown`, () => {
 const s=zero(); s[key]=null; assert.equal(m.kpiTruth(s).netWorth.value,null)
 if(key==='holdings') assert.equal(m.kpiTruth(s).position.value,null)
 if(key==='accounts') assert.equal(m.kpiTruth(s).available.value,null)
})
test('missing, partial and complete prices never report missing valuation as a loss', () => {
 const s=zero(); s.holdings=[holding(),holding(2)];
 assert.equal(m.kpiTruth(s).totalPnl.value,null)
 s.prices.set(1,5); let r=m.kpiTruth(s); assert.deepEqual(r.position,{status:'partial',value:10}); assert.equal(r.netWorth.value,null)
 s.prices.set(2,4); r=m.kpiTruth(s); assert.equal(r.position.value,18); assert.equal(r.netWorth.value,18); assert.equal(r.totalPnl.value,6)
})
test('invalid shares, costs and prices fail closed; explicit zero shares need no quote', () => {
 for(const value of [null,undefined,NaN,Infinity,'2',-1]) {const s=zero(); s.holdings=[{...holding(),totalShares:value}]; s.prices.set(1,5); assert.equal(m.kpiTruth(s).position.value,null)}
 for(const price of [null,undefined,NaN,Infinity,'2',0,-1]) {const s=zero(); s.holdings=[holding()]; s.prices.set(1,price); assert.equal(m.kpiTruth(s).position.value,null)}
 const s=zero(); s.holdings=[{...holding(),totalShares:0}]; assert.equal(m.kpiTruth(s).position.value,0)
 s.holdings=[{...holding(),avgCost:null}]; s.prices.set(1,5); assert.equal(m.kpiTruth(s).totalPnl.value,null)
})
test('invalid leaf does not poison reliable known portion or imply full funds', () => {
 const s=zero(); s.accounts=[{balance:4,reservedAmount:1},{balance:null,reservedAmount:0}]; assert.deepEqual(m.kpiTruth(s).available,{status:'partial',value:3})
})
for(const reason of ['offline',403,500]) test(`read ${reason} failure and manual retry`,async()=>{
 let fail=true,state; const l=m.createKpiLoader(async()=>{if(fail)throw Error(String(reason));return zero()},()=> 'a',s=>state=s)
 await l.load(); assert.equal(state.snapshot,null); fail=false; await l.load(); assert.equal(state.snapshot.overview.liability,0)
})
test('repeated refresh newest wins and clears previous display',async()=>{
 const a=deferred(),b=deferred();let i=0,state;const l=m.createKpiLoader(()=>i++?b.promise:a.promise,()=> 'a',s=>state=s)
 const p=l.load(),q=l.load();assert.equal(state.snapshot,null); b.resolve(zero());await q;a.reject(Error('old'));await p;assert.equal(state.snapshot.overview.liability,0)
})
test('identity changes, unmount and deactivation reject old responses; activation does not query',async()=>{
 for(const action of ['identity','suspend','reset']) {const d=deferred();let state,owner='a',calls=0;const l=m.createKpiLoader(()=>{calls++;return d.promise},()=>owner,s=>state=s);const p=l.load();if(action==='identity'){owner='b';l.reset()}else l[action]();d.resolve(zero());await p;assert.equal(state.snapshot,null);l.activate();assert.equal(calls,1)}
})
test('Dashboard top cards use truth model and preserve financial action handlers',async()=>{
 const s=await readFile(new URL('../src/views/Dashboard.vue',import.meta.url),'utf8');const top=s.slice(s.indexOf('<!-- 资产概览KPI -->'),s.indexOf('<div class="card today-todo-card">'))
 assert.doesNotMatch(top,/formatCurrency\(|good|bad|%/); assert.match(top,/手动重试资产 KPI/); assert.match(top,/口径未完全核实/)
 assert.doesNotMatch(source,/settlementApi|orderApi|localStorage|console\./)
 assert.match(s,/onDeactivated\(kpiLoader.suspend\)/); assert.match(s,/watch\(\[\(\) => userStore.token, \(\) => userStore.user\], kpiLoader.reset/)
 assert.doesNotMatch(s,/console.log\('资产概览|console.log\('账户/)
})
test('HTTP identity revocation without store watch clears loading on completion',async()=>{
 const d=deferred();let owner='a',state;const l=m.createKpiLoader(()=>d.promise,()=>owner,s=>state=s);const p=l.load();owner='revoked';d.resolve(zero());await p;assert.deepEqual(state,{loading:false,snapshot:null,readAt:null})
})
