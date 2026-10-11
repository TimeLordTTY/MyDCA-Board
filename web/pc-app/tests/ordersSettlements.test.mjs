import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import * as vue from 'vue'
import * as shared from '../../shared/dist/index.js'

const read = path => readFile(new URL('../src/' + path, import.meta.url), 'utf8')
const moduleOf = code => import('data:text/javascript;base64,' + Buffer.from(ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.ESNext } }).outputText).toString('base64'))
const model = await moduleOf(await read('components/orderSettlementModel.ts'))
const gates = await moduleOf(await read('views/managementViewModel.ts'))
const deferred = () => { let resolve, reject; const promise = new Promise((r, j) => { resolve = r; reject = j }); return { promise, resolve, reject } }
const tick = async () => { await vue.nextTick(); await new Promise(r => setImmediate(r)) }
const row = (type = 'BUY', extra = {}) => ({ id: 1, orderId: 'O1', userId: 1, productId: 101, orderType: type, status: 'PENDING', amount: type === 'BUY' || type === 'SUBSCRIPTION' ? 100 : undefined, shares: type === 'SELL' || type === 'REDEMPTION' ? 10 : undefined, expectedNavDate: '2026-10-10', expectedConfirmDate: '2026-10-11', feeEstimate: 0, requestedAt: '2026-10-10 12:00:00', fundingLines: [{ id: 1, accountId: 11, lineType: 'SOURCE', currency: 'CNY', amount: 100 }, { id: 2, accountId: 21, lineType: 'TARGET', currency: 'CNY', shares: 10 }], ...extra })
const previewOf = (type = 'BUY', extra = {}) => ({ orderId: 'O1', orderType: type, orderStatus: 'PENDING', productId: 101, productName: '服务器产品', confirmSupported: true, freshPreviewToken: 'server-fixture-fresh-only', summaryLines: ['服务器现金/应收影响', '服务器持仓影响', '手续费 0'], postingsPreview: [{ accountId: 11, accountType: 'RECEIVABLE', postingType: 'CREDIT', amount: 100 }, { accountId: 31, accountType: 'POSITION', postingType: 'DEBIT', shares: 50 }], warnings: [], blockingReasons: [], computedShares: 50, computedAmount: 100, confirmFee: 0, ...extra })
const auditOf = (extra = {}) => ({ orderId: 'O1', orderType: 'BUY', orderStatus: 'CONFIRMED', productId: 101, productName: '服务器产品', settlement: { confirmDate: '2026-10-11', navDate: '2026-10-10', confirmNav: 2, confirmShares: 50, confirmFee: 0, previewDigest: 'display-only-not-token' }, fundingLines: row().fundingLines, postings: [], reasons: ['历史关联缺失'], reconciliationStatus: 'WARNING', ...extra })

async function setup(file = 'components/SettlementConfirmModal.vue', overrides = {}, order = row(), query = {}, initialUser = { id: 1, familyId: 1 }) {
  const user = vue.reactive({ token: 'fixture', user: initialUser }), notices = [], dialogs = [], routes = [], events = [], listeners = new Map()
  const lifecycle = { mounted: [], unmounted: [], activated: [], deactivated: [] }
  const props = vue.reactive({ modelValue: true, order })
  globalThis.window = { addEventListener: (name, fn) => listeners.set(name, fn), removeEventListener: name => listeners.delete(name) }
  const notify = info => notices.push(info)
  for (const kind of ['info', 'success', 'error', 'warning']) notify[kind] = notify
  const mocks = {
    vue: { ...vue, onMounted: fn => lifecycle.mounted.push(fn), onBeforeUnmount: fn => lifecycle.unmounted.push(fn), onActivated: fn => lifecycle.activated.push(fn), onDeactivated: fn => lifecycle.deactivated.push(fn) },
    'vue-router': { useRoute: () => ({ query }), useRouter: () => ({ push: async path => routes.push(path) }) },
    'element-plus': { ElNotification: notify, ElMessageBox: { confirm: async (message, title, options) => dialogs.push({ message, title, options }) } },
    '@wealth-hub/shared': { ...shared, useUserStore: () => user, orderApi: { getOrder: async () => order, getOrders: async () => [order], confirmSettlement: async () => {}, cancelOrder: async () => {} }, settlementApi: { previewSettlement: async () => previewOf(order.orderType), getPendingSettlements: async () => [order], getHistory: async () => [auditOf()], getAudit: async () => auditOf() }, navApi: { getNavByDate: async () => ({ nav: 2, navDate: '2026-10-10' }) }, productApi: { getProducts: async () => [{ id: 101, productName: '产品A' }] }, accountApi: { getAccounts: async () => [{ id: 11, accountName: '来源A' }] }, ...overrides },
    './managementViewModel': gates, '../views/managementViewModel': gates,
    './orderSettlementModel': model, '../components/orderSettlementModel': model,
  }
  globalThis.__orderMocks = mocks
  const descriptor = parse(await read(file)).descriptor
  assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: file, id: 'orders' }).errors, [])
  let code = ts.transpileModule(compileScript(descriptor, { id: 'orders' }).content, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.ESNext } }).outputText
  code = code.replace(/import \{([^}]+)\} from ['"]([^'"]+)['"]/g, (_, names, path) => 'const {' + names.replace(/ as /g, ': ') + '} = globalThis.__orderMocks[' + JSON.stringify(path) + ']')
  code = code.replace(/import (\w+) from ['"][^'"]+\.vue['"]/g, (_, name) => 'const ' + name + ' = {}')
  const component = await moduleOf(code + '\n// fixture ' + Math.random()), scope = vue.effectScope()
  const state = scope.run(() => component.default.setup(props, { expose() {}, emit(name, value) { events.push([name, value]); if (name === 'update:modelValue') props.modelValue = value } }))
  await tick()
  return { state, mocks, props, user, notices, dialogs, routes, events, lifecycle, listeners, close() { lifecycle.unmounted.forEach(fn => fn()); scope.stop() } }
}
async function readyPreview(s) { s.state.form.value.confirmNav = 2; s.state.form.value.confirmAmount = 100; await s.state.handlePreview() }

test('four directions keep read-only preview, explicit owner dialog and exact backend fresh token/input', async () => {
  for (const type of model.orderTypes) {
    const previews = [], confirms = [], s = await setup(undefined, { settlementApi: { previewSettlement: async input => { previews.push({ ...input }); return previewOf(type) } }, orderApi: { getOrder: async () => row(type), confirmSettlement: async input => confirms.push(input) } }, row(type))
    assert.equal(confirms.length, 0); assert.equal(previews.length, 0)
    await readyPreview(s)
    assert.equal(confirms.length, 0); assert.equal(s.state.canConfirm.value, true)
    await s.state.handleSubmit()
    assert.equal(confirms.length, 1); assert.deepEqual(confirms[0], { ...previews[0], freshPreviewToken: 'server-fixture-fresh-only' })
    assert.match(s.dialogs[0].message, /服务器现金.*影响/); assert.match(s.dialogs[0].message, /服务器持仓/); assert.equal(s.events.filter(e => e[0] === 'success').length, 1); s.close()
  }
})
test('all input edits synchronously invalidate eligibility, including amount/date/shares/NAV/fee/note', async () => {
  for (const [field, value] of Object.entries({ confirmDate: '2026-10-12', navDate: '2026-10-09', confirmNav: 3, confirmShares: 49, confirmAmount: 99, confirmFee: 1, note: 'changed' })) {
    const s = await setup(); await readyPreview(s); s.state.form.value[field] = value
    assert.equal(s.state.preview.value, null, field); assert.equal(s.state.canConfirm.value, false); s.close()
  }
})
test('invalid and cleared required values never call preview; unknown NAV does not become zero', async () => {
  let calls = 0
  const s = await setup(undefined, { settlementApi: { previewSettlement: async () => { calls++; return previewOf() } } })
  for (const value of [undefined, null, 0, -1, NaN, Infinity, '2']) { s.state.form.value.confirmNav = value; await s.state.handlePreview(); assert.equal(calls, 0) }
  s.state.form.value.confirmNav = 2
  for (const value of ['', null, '2026-02-30', 'n/a']) { s.state.form.value.confirmDate = value; await s.state.handlePreview(); assert.equal(calls, 0) }
  s.close()
})
test('sell/redeem require actual positive amount; missing buy amount is left for backend blocking', async () => {
  for (const type of ['SELL', 'REDEMPTION']) {
    let calls = 0; const s = await setup(undefined, { settlementApi: { previewSettlement: async () => { calls++; return previewOf(type) } } }, row(type))
    s.state.form.value.confirmNav = 2
    for (const value of [undefined, null, 0, -1, NaN, Infinity]) { s.state.form.value.confirmAmount = value; await s.state.handlePreview(); assert.equal(calls, 0) }
    s.close()
  }
  const s = await setup(undefined, { settlementApi: { previewSettlement: async () => previewOf('BUY', { confirmSupported: false, blockingReasons: ['出资金额缺失'] }) } }, row('BUY', { amount: undefined }))
  s.state.form.value.confirmNav = 2; await s.state.handlePreview(); await s.state.handleSubmit()
  assert.equal(s.state.canConfirm.value, false); assert.match(shared.buildSettlementBlockingText(s.state.preview.value), /金额缺失/); assert.equal(s.dialogs.length, 0); s.close()
})
test('fee undefined means server estimate, explicit zero stays zero; displayed unknown never fabricates financial values', async () => {
  const inputs = [], s = await setup(undefined, { settlementApi: { previewSettlement: async input => { inputs.push(input); return previewOf() } } }, row('BUY', { feeEstimate: undefined }))
  await readyPreview(s); assert.equal(inputs[0].confirmFee, undefined)
  s.state.form.value.confirmFee = 0; await s.state.handlePreview(); assert.equal(inputs[1].confirmFee, 0)
  for (const value of [undefined, null, NaN, Infinity, '0']) assert.equal(model.displayNumber(value), '未知')
  assert.equal(model.displayNumber(0), '0.00'); s.close()
})
test('unsupported, missing/blank token, mismatched ID/type/status cannot confirm', async () => {
  for (const extra of [{ confirmSupported: false }, { freshPreviewToken: undefined }, { freshPreviewToken: '' }, { freshPreviewToken: ' ' }, { orderId: 'O2' }, { orderType: 'SELL' }, { orderStatus: 'CONFIRMED' }, { orderStatus: undefined }]) {
    const s = await setup(undefined, { settlementApi: { previewSettlement: async () => previewOf('BUY', extra) } })
    await readyPreview(s); await s.state.handleSubmit(); assert.equal(s.dialogs.length, 0); assert.equal(s.state.canConfirm.value, false); s.close()
  }
})
test('failed re-preview clears old eligibility and preserves backend HTTP reason', async () => {
  let fail = false
  const s = await setup(undefined, { settlementApi: { previewSettlement: async () => { if (fail) throw { response: { status: 409, data: { message: '账户快照已变更' } } }; return previewOf() } } })
  await readyPreview(s); fail = true; await s.state.handlePreview(); assert.equal(s.state.preview.value, null); assert.match(s.state.actionError.value, /HTTP 409.*账户快照已变更/); s.close()
})
test('editing while preview is in flight discards its token; duplicate preview clicks request once', async () => {
  const d = deferred(); let calls = 0
  const s = await setup(undefined, { settlementApi: { previewSettlement: () => { calls++; return d.promise } } })
  s.state.form.value.confirmNav = 2; const first = s.state.handlePreview(); await s.state.handlePreview()
  assert.equal(calls, 1); s.state.form.value.confirmFee = 3; d.resolve(previewOf()); await first
  assert.equal(s.state.preview.value, null); assert.equal(s.state.previewing.value, false); s.close()
})
test('duplicate confirm clicks lock before owner dialog and stay locked through write', async () => {
  const dialog = deferred(), write = deferred(); let calls = 0
  const s = await setup(undefined, { orderApi: { getOrder: async () => row(), confirmSettlement: () => { calls++; return write.promise } } })
  await readyPreview(s); let dialogs = 0; s.mocks['element-plus'].ElMessageBox.confirm = () => { dialogs++; return dialog.promise }
  const first = s.state.handleSubmit(); await s.state.handleSubmit(); assert.equal(dialogs, 1); assert.equal(calls, 0)
  dialog.resolve(); await tick(); await s.state.handleSubmit(); assert.equal(calls, 1)
  write.resolve(); await first; assert.equal(s.state.preview.value, null); assert.equal(s.state.submitting.value, false); s.close()
})
test('cancelled second confirmation consumes preview and causes no write', async () => {
  const s = await setup(); await readyPreview(s); s.mocks['element-plus'].ElMessageBox.confirm = async () => { throw 'cancel' }
  await s.state.handleSubmit(); assert.equal(s.state.preview.value, null); assert.equal(s.events.filter(e => e[0] === 'success').length, 0); s.close()
})
test('edits, funding/account changes, identity changes, closing/unmount while dialog waits prevent old confirm', async () => {
  for (const action of ['amount', 'shares', 'date', 'account', 'token', 'family', 'order', 'closed', 'unmount', 'deactivate']) {
    const d = deferred(); let calls = 0
    const s = await setup(undefined, { orderApi: { getOrder: async () => row(), confirmSettlement: async () => { calls++ } } })
    await readyPreview(s); s.mocks['element-plus'].ElMessageBox.confirm = () => d.promise
    const p = s.state.handleSubmit()
    if (action === 'amount') s.state.form.value.confirmAmount = 99
    if (action === 'shares') s.state.form.value.confirmShares = 99
    if (action === 'date') s.state.form.value.navDate = '2026-10-09'
    if (action === 'account') s.props.order.fundingLines[0].accountId = 99
    if (action === 'token') s.user.token = 'other'
    if (action === 'family') s.user.user.familyId = 2
    if (action === 'order') s.props.order = row('BUY', { orderId: 'O2' })
    if (action === 'closed') s.props.modelValue = false
    if (action === 'unmount') s.close()
    if (action === 'deactivate') s.lifecycle.deactivated.forEach(fn => fn())
    d.resolve(); await p; assert.equal(calls, 0, action); s.close()
  }
})
test('409/401/403/400 refusals preserve exact reason and invalidate preview with no automatic retry', async () => {
  for (const status of [409, 401, 403, 400]) {
    let calls = 0
    const s = await setup(undefined, { orderApi: { getOrder: async () => row(), confirmSettlement: async () => { calls++; throw { response: { status, data: { message: '服务器拒绝或重复结算' } } } } } })
    await readyPreview(s); await s.state.handleSubmit(); await s.state.handleSubmit()
    assert.equal(calls, 1); assert.equal(s.state.preview.value, null); assert.match(s.state.actionError.value, new RegExp('HTTP ' + status + '.*服务器拒绝或重复结算')); s.close()
  }
})
test('lost/500/408 confirm response locks that order across reopening the same component', async () => {
  for (const error of [Error('offline'), { response: { status: 500 } }, { response: { status: 408 } }]) {
    let calls = 0
    const s = await setup(undefined, { orderApi: { getOrder: async () => row(), confirmSettlement: async () => { calls++; throw error } } })
    await readyPreview(s); await s.state.handleSubmit(); assert.equal(s.state.uncertain.value, true)
    s.props.modelValue = false; s.props.modelValue = true; await tick()
    await readyPreview(s); await s.state.handleSubmit(); assert.equal(calls, 1); assert.equal(s.state.canConfirm.value, false); s.close()
  }
})
test('latest order state prevents settlement even if the queue snapshot was pending', async () => {
  for (const status of ['CONFIRMED', 'CANCELLED', 'FAILED', 'OTHER']) {
    const s = await setup(undefined, { orderApi: { getOrder: async () => row('BUY', { status }) } })
    await readyPreview(s); assert.equal(s.state.preview.value, null); assert.equal(s.state.canConfirm.value, false); s.close()
  }
})
test('dated NAV must match chosen date; edits discard delayed NAV and latest NAV is never substituted', async () => {
  for (const action of ['date', 'fee', 'manualNav', 'close']) {
    const d = deferred(), s = await setup(undefined, { navApi: { getNavByDate: () => d.promise } })
    const p = s.state.loadNav()
    if (action === 'date') s.state.form.value.navDate = '2026-10-09'
    if (action === 'fee') s.state.form.value.confirmFee = 5
    if (action === 'manualNav') s.state.form.value.confirmNav = 7
    if (action === 'close') s.props.modelValue = false
    d.resolve({ nav: 2, navDate: '2026-10-10' }); await p
    assert.notEqual(s.state.form.value.confirmNav, 2); assert.equal(s.state.preview.value, null); s.close()
  }
  const s = await setup(undefined, { navApi: { getNavByDate: async () => ({ nav: 2, navDate: '2026-10-09' }) } }); await s.state.loadNav()
  assert.equal(s.state.form.value.confirmNav, undefined); assert.match(s.state.actionError.value, /未知/); s.close()
})
test('detail and preview late responses are discarded after session/identity/selection change', async () => {
  for (const action of ['token', 'family', 'close', 'unmount']) {
    const d = deferred(), s = await setup(undefined, { settlementApi: { previewSettlement: () => d.promise } })
    s.state.form.value.confirmNav = 2; const p = s.state.handlePreview()
    if (action === 'token') s.user.token = 'other'
    if (action === 'family') s.user.user.familyId = 2
    if (action === 'close') s.props.modelValue = false
    if (action === 'unmount') s.close()
    d.resolve(previewOf()); await p; assert.equal(s.state.preview.value, null); s.close()
  }
})
test('orders list preserves source/target and settled zero values; filters and related links use original API data', async () => {
  const s = await setup('views/Orders.vue')
  await s.state.loadOrders(); assert.equal(s.state.ready.value, true); assert.equal(s.state.orders.value[0].fundingLines[1].lineType, 'TARGET')
  s.state.keyword.value = '来源A'; assert.equal(s.state.filteredOrders.value.length, 1)
  s.state.statusFilter.value = 'CONFIRMED'; assert.equal(s.state.filteredOrders.value.length, 0); s.state.resetFilters()
  await s.state.handleViewDetail(row()); assert.equal(s.state.selectedOrder.value.orderId, 'O1')
  s.state.openHoldings(101); s.state.openAudit('O1'); assert.deepEqual(s.routes, [{ name: 'Holdings', query: { productId: 101 } }, { name: 'Settlements', query: { audit: 'O1' } }]); s.close()
})
test('order detail/hydration failures show explicit errors, and full list failure clears old rows', async () => {
  let listFail = false, detailFail = false
  const s = await setup('views/Orders.vue', { orderApi: { getOrders: async () => { if (listFail) throw Error('offline'); return [row()] }, getOrder: async () => { if (detailFail) throw { response: { status: 403, data: { message: '家庭不可见' } } }; return row() } } })
  await s.state.loadOrders(); detailFail = true; await s.state.loadOrders()
  assert.match(s.state.orders.value[0].detailError, /家庭不可见/)
  await s.state.handleViewDetail(row()); assert.equal(s.state.selectedOrder.value, null); assert.match(s.state.detailError.value, /HTTP 403.*家庭不可见/)
  listFail = true; await s.state.loadOrders(); assert.deepEqual(s.state.orders.value, []); assert.equal(s.state.ready.value, false); s.close()
})
test('cancel order is explicit, guarded during dialog and uses only original cancel endpoint', async () => {
  const d = deferred(); let calls = 0
  const s = await setup('views/Orders.vue', { orderApi: { getOrders: async () => [], getOrder: async () => row(), cancelOrder: async id => { assert.equal(id, 'O1'); calls++ } } })
  s.mocks['element-plus'].ElMessageBox.confirm = () => d.promise
  const p = s.state.handleCancelOrder(row()); await s.state.handleCancelOrder(row()); assert.equal(calls, 0)
  d.resolve(); await p; assert.equal(calls, 1); s.close()
})
test('settlement queue/history failures and permissions never appear as verified emptiness', async () => {
  let fail = false
  const s = await setup('views/Settlements.vue', { settlementApi: { getPendingSettlements: async () => { if (fail) throw { response: { status: 403, data: { message: '家庭权限不足' } } }; return [row()] }, getHistory: async () => { if (fail) throw Error('offline'); return [auditOf()] } } })
  await s.state.loadSettlements(); await s.state.loadHistory(); assert.equal(s.state.pendingReady.value, true); assert.equal(s.state.historyReady.value, true)
  fail = true; await s.state.loadSettlements(); await s.state.loadHistory()
  assert.deepEqual(s.state.pendingSettlements.value, []); assert.deepEqual(s.state.history.value, []); assert.equal(s.state.pendingReady.value, false); assert.equal(s.state.historyReady.value, false); assert.match(s.state.listError.value, /HTTP 403.*家庭权限不足/); s.close()
})
test('invalid pending records reject whole response, UNKNOWN amount remains readable without false zero', async () => {
  for (const value of [null, {}, [row(), row()], [row('BUY', { status: 'CONFIRMED' })], [row('OTHER')], [row('BUY', { orderId: '../x' })]]) assert.throws(() => model.validateOrders(value, true))
  assert.equal(model.validateOrders([row('BUY', { amount: undefined })], true)[0].amount, undefined)
  const s = await setup('views/Settlements.vue', { settlementApi: { getPendingSettlements: async () => [row('BUY', { status: 'FAILED' })] } })
  await s.state.loadSettlements(); assert.equal(s.state.pendingReady.value, false); assert.match(s.state.listError.value, /异常/); s.close()
})
test('rapid audit selection binds latest ID, displays warnings/digest read-only, and never repairs records', async () => {
  const d = deferred()
  const s = await setup('views/Settlements.vue', { settlementApi: { getAudit: id => id === 'O1' ? d.promise : Promise.resolve(auditOf({ orderId: id, reconciliationStatus: 'BROKEN' })) } })
  const p = s.state.loadAudit('O1'); await s.state.loadAudit('O2'); d.resolve(auditOf()); await p
  assert.equal(s.state.selectedAudit.value.orderId, 'O2'); assert.equal(s.state.auditLabel('BROKEN'), '不一致'); assert.equal(s.state.selectedAudit.value.settlement.previewDigest, 'display-only-not-token')
  s.state.openHoldings(101); s.state.openOrder('O2'); assert.equal(s.routes.length, 2); s.close()
})
test('identity reset clears queue/history/audit/forms and rejects delayed reads on both pages', async () => {
  for (const file of ['views/Orders.vue', 'views/Settlements.vue']) {
    const d = deferred(), overrides = file.includes('Orders') ? { orderApi: { getOrders: () => d.promise } } : { settlementApi: { getPendingSettlements: () => d.promise } }
    const s = await setup(file, overrides)
    const p = file.includes('Orders') ? s.state.loadOrders() : s.state.loadSettlements()
    s.user.user.familyId = 2; d.resolve([row()]); await p
    assert.deepEqual(file.includes('Orders') ? s.state.orders.value : s.state.pendingSettlements.value, []); assert.equal(s.state.confirmVisible.value, false); s.close()
  }
})
test('initial user hydration refetches current identity; unmount/deactivation remove listeners and reject old reads', async () => {
  for (const file of ['views/Orders.vue', 'views/Settlements.vue']) {
    const s = await setup(file, {}, row(), {}, null)
    for (const fn of s.lifecycle.mounted) await fn()
    s.user.user = { id: 1, familyId: 1 }; await tick()
    assert.equal(file.includes('Orders') ? s.state.ready.value : s.state.pendingReady.value, true)
    s.lifecycle.deactivated.forEach(fn => fn()); assert.equal(s.state.confirmVisible.value, false)
    s.close(); assert.equal(s.listeners.size, 0)
  }
})
test('buy and sell PENDING semantics are distinct, URLs only open forms and no local ledger exists', async () => {
  assert.match(model.orderStageText(row('BUY')), /已有付款.*应收.*不重复扣/); assert.match(model.orderStageText(row('SELL')), /只登记.*占用.*才减少持仓/)
  const modal = await read('components/SettlementConfirmModal.vue'), orders = await read('views/Orders.vue'), settlements = await read('views/Settlements.vue')
  for (const source of [modal, orders, settlements]) assert.doesNotMatch(source, /localStorage|createLedger|ledgerApi|setInterval|calculateShares|calculateAmount/)
  assert.match(modal, /freshPreviewToken: fresh.freshPreviewToken/)
  const s = await setup('views/Orders.vue', {}, row(), { settle: 'O1' })
  for (const fn of s.lifecycle.mounted) await fn(); assert.equal(s.state.confirmVisible.value, true); assert.equal(s.dialogs.length, 0); s.close()
})
test('settlement/order deep links survive initial user hydration and never auto-preview/confirm', async () => {
  for (const file of ['views/Orders.vue', 'views/Settlements.vue']) {
    const query = file.includes('Orders') ? { settle: 'O1' } : { audit: 'O1' }
    const s = await setup(file, {}, row(), query, null)
    for (const fn of s.lifecycle.mounted) await fn()
    await tick(); assert.equal(file.includes('Orders') ? s.state.confirmVisible.value : s.state.auditVisible.value, false)
    s.user.user = { id: 1, familyId: 1 }; await tick()
    assert.equal(file.includes('Orders') ? s.state.confirmVisible.value : s.state.auditVisible.value, true)
    if (!file.includes('Orders')) assert.equal(s.state.selectedAudit.value.orderId, 'O1')
    assert.equal(s.dialogs.length, 0); s.close()
  }
})
