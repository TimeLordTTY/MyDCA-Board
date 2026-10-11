import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import * as vue from 'vue'
import * as shared from '../../shared/dist/index.js'

const read = name => readFile(new URL(`../src/${name}`, import.meta.url), 'utf8')
const moduleOf = code => import(`data:text/javascript;base64,${Buffer.from(ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.ESNext } }).outputText).toString('base64')}`)
const model = await moduleOf(await read('views/ledgerViewModel.ts'))
const management = await moduleOf(await read('views/managementViewModel.ts'))
globalThis.__ledgerVue = vue
const safety = await moduleOf((await read('components/entrySafety.ts')).replace("import { reactive } from 'vue'", 'const { reactive } = globalThis.__ledgerVue'))
const deferred = () => { let resolve, reject; const promise = new Promise((r, j) => { resolve = r; reject = j }); return { promise, resolve, reject } }
const txn = extra => ({ txnId: 'T1', txnType: 'EXPENSE', userId: 1, status: 'CONFIRMED', requestedAt: '2026-10-11 10:00:00', summaryAmount: 0, categoryId: shared.expenseCategories[0].id, postings: [], ...extra })
async function setup(file = 'views/Ledger.vue', overrides = {}, input = {}) {
  safety.entryWriteState.pending = false; safety.entryWriteState.uncertain = false
  const user = vue.reactive({ token: 'fixture', user: { id: 1, familyId: 1 } })
  const props = vue.reactive({ modelValue: true, ...input }), emitted = [], notices = [], listeners = new Map()
  const lifecycle = { mounted: [], unmounted: [], activated: [], deactivated: [] }
  const store = vue.reactive({ accounts: [], accountTree: [], cashLeafAccounts: [], fetchAccounts: async () => {}, getAccountById: () => null })
  globalThis.window = { addEventListener: (name, fn) => listeners.set(name, fn), removeEventListener: name => listeners.delete(name), dispatchEvent: e => { listeners.get(e.type)?.() } }
  const notify = value => notices.push(value)
  for (const type of ['success', 'warning', 'error']) notify[type] = notify
  const mocks = {
    vue: { ...vue, onMounted: f => lifecycle.mounted.push(f), onBeforeUnmount: f => lifecycle.unmounted.push(f), onActivated: f => lifecycle.activated.push(f), onDeactivated: f => lifecycle.deactivated.push(f) },
    'element-plus': { ElNotification: notify, ElPagination: {}, ElMessageBox: { confirm: async () => {} } },
    '@wealth-hub/shared': { ...shared, useUserStore: () => user, useAccountStore: () => store, useProductStore: () => ({ products: [], fetchProducts: async () => {} }), accountApi: { getAccounts: async () => [] }, holdingApi: { getHoldings: async () => [] }, ledgerApi: { getTransactions: async () => ({ list: [txn()], total: 1 }), getTransactionDetail: async () => txn(), createTransaction: async () => txn() }, ledgerStatsApi: { getSummary: async () => ({ totalIncome: 0 }), getTrend: async () => [], getBreakdown: async () => [], getTop: async () => [] }, ...overrides },
    './ledgerViewModel': model, '../views/ledgerViewModel': model, './managementViewModel': management,
    './entrySafety': safety, '../components/entrySafety': safety,
  }
  globalThis.__ledgerMocks = mocks
  const descriptor = parse(await read(file)).descriptor
  assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: file, id: file }).errors, [])
  let code = ts.transpileModule(compileScript(descriptor, { id: file }).content, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.ESNext } }).outputText
  code = code.replace(/import \{([^}]+)\} from ['"]([^'"]+)['"]/g, (_, names, path) => `const {${names.replace(/ as /g, ': ')}} = globalThis.__ledgerMocks[${JSON.stringify(path)}]`)
    .replace(/import (\w+) from ['"][^'"]+\.vue['"]/g, (_, name) => `const ${name} = {}`)
  const scope = vue.effectScope(), component = await moduleOf(code + `\n// fixture ${Math.random()}`)
  const state = scope.run(() => component.default.setup(props, { expose() {}, emit(name, value) { emitted.push({ name, value }); if (name === 'update:modelValue') props.modelValue = value } }))
  return { state, user, props, store, emitted, notices, lifecycle, listeners, close() { lifecycle.unmounted.forEach(f => f()); scope.stop() } }
}
function fillEntry(s, type = 'EXPENSE') {
  s.state.selectedType.value = type
  s.state.optionsReady.value = true; s.state.step.value = 2
  Object.assign(s.state.form.value, { amount: 12.34, category: [shared.expenseCategories[0].id], parentAccountId: 10, accountId: 11, occurredAt: '2026-10-11 10:00:00', requestedAt: '2026-10-11 10:00:00' })
}

test('month query uses local calendar including leap years and preserves zero/unknown amounts', async () => {
  assert.deepEqual(model.monthRange('2024-02'), ['2024-02-01', '2024-02-29'])
  assert.equal(model.monthRange('2026-13'), null)
  assert.equal(model.localDate(new Date(2026, 9, 11)), '2026-10-11')
  assert.match(model.localDateTime(new Date(2026, 9, 11, 2, 3, 4)), /2026-10-11 02:03:04/)
  const s = await setup(); assert.match(s.state.rowAmount(txn()), /0\.00/); assert.equal(s.state.rowAmount(txn({ summaryAmount: undefined })), '未知')
  assert.equal(s.state.knownMoney(null), '未知'); assert.match(s.state.rowAmount(txn({ summaryAmount: -12, currency: 'HKD' })), /HKD/)
  s.close()
})

test('server search/pagination unchanged; category only current page and statistics uses real categoryIds', async () => {
  const queries = [], stats = []
  const s = await setup('views/Ledger.vue', { ledgerApi: { getTransactions: async q => { queries.push(q); return { list: [txn(), txn({ txnId: 'unknown', categoryId: 999999 }), txn({ txnId: 'missing', categoryId: undefined })], total: 80 } } }, ledgerStatsApi: { getSummary: async q => { stats.push(q); return {} }, getTrend: async () => [], getBreakdown: async () => [], getTop: async () => [] } })
  Object.assign(s.state.filters, { startDate: '2026-10-01', endDate: '2026-10-31', parentAccountId: 10, accountId: 11, txnType: 'EXPENSE', note: '午饭' })
  s.state.pagination.page = 3; s.state.pagination.pageSize = 20
  await s.state.loadTransactions(); assert.deepEqual(queries[0], { startDate: '2026-10-01', endDate: '2026-10-31', parentAccountId: 10, accountId: 11, txnType: 'EXPENSE', note: '午饭', page: 3, pageSize: 20 })
  s.state.pageCategory.value = 'unknown'; assert.equal(s.state.visibleTransactions.value[0].txnId, 'unknown'); assert.match(s.state.getCategoryParent(txn({ categoryId: 999999 })), /未知分类/)
  s.state.pageCategory.value = 'missing'; assert.equal(s.state.visibleTransactions.value.length, 1); assert.equal(s.state.pagination.total, 80)
  s.state.statsCategoryId.value = 100; await s.state.loadStats(); assert.deepEqual(stats[0].categoryIds, [100]); assert.equal(stats[0].includeTransfer, false); assert.equal(stats[0].note, undefined)
  s.close()
})

test('list/statistics failures clear stale data; empty response is separate; denied errors remain visible', async () => {
  let error
  const s = await setup('views/Ledger.vue', { ledgerApi: { getTransactions: async () => { if (error) throw error; return { list: [], total: 0 } } }, ledgerStatsApi: { getSummary: async () => { if (error) throw error; return { totalIncome: 0 } }, getTrend: async () => [], getBreakdown: async () => [], getTop: async () => [] } })
  await s.state.loadTransactions(); assert.equal(s.state.loadError.value, '')
  for (const e of [Error('offline'), { response: { status: 403 }, message: '没有权限' }]) {
    error = e; s.state.transactions.value = [txn()]; s.state.statsSummary.value = { totalIncome: 99 }
    await s.state.loadTransactions(); await s.state.loadStats()
    assert.equal(s.state.transactions.value.length, 0); assert.equal(s.state.statsSummary.value, null); assert.match(s.state.loadError.value, /失败|权限/); assert.match(s.state.statsError.value, /失败|权限/)
  }
  s.close()
})

test('late list/detail responses cannot restore closed or cross-identity data; refresh listener removed', async () => {
  for (const action of ['identity', 'unmount', 'deactivate', 'overlap']) {
    const wait = deferred(); let count = 0
    const s = await setup('views/Ledger.vue', { ledgerApi: { getTransactions: () => count++ ? Promise.resolve({ list: [], total: 0 }) : wait.promise, getTransactionDetail: () => wait.promise } })
    const pending = s.state.loadTransactions()
    if (action === 'identity') s.user.user.familyId = 2
    if (action === 'unmount') s.close()
    if (action === 'deactivate') s.lifecycle.deactivated.forEach(f => f())
    if (action === 'overlap') await s.state.loadTransactions()
    wait.resolve({ list: [txn()], total: 1 }); await pending; assert.deepEqual(s.state.transactions.value, [])
    s.close()
  }
  const wait = deferred(), s = await setup('views/Ledger.vue', { ledgerApi: { getTransactions: async () => ({ list: [], total: 0 }), getTransactionDetail: () => wait.promise } })
  const pending = s.state.handleViewDetail(txn()); s.state.detailVisible.value = false; wait.resolve(txn()); await pending; assert.equal(s.state.selectedTxn.value, null)
  s.lifecycle.mounted.forEach(f => f()); assert.equal(s.listeners.has('data-refresh'), true); s.close(); assert.equal(s.listeners.has('data-refresh'), false)
})

test('editing hints preserve owner and read-only history; actual backend errors are retained', async () => {
  for (const extra of [{ orderId: 'O1' }, { isReversed: true }, { status: 'CANCELLED' }, { canEdit: false }, { userId: 2 }, { txnType: 'REFUND' }]) assert.notEqual(model.editReason(txn(extra), 1), '')
  assert.equal(model.editReason(txn(), 1), '')
  const s = await setup('views/Ledger.vue', { ledgerApi: { getTransactionDetail: async () => { throw Error('服务器拒绝编辑') } } })
  await s.state.handleEdit(txn()); assert.equal(s.state.editVisible.value, false); assert.match(s.notices.at(-1).message, /服务器拒绝/); s.close()
})

test('unified entry requires review, detects changed input, preserves yuan/time payload, and double click writes once', async () => {
  const wait = deferred(), calls = []
  const s = await setup('components/UnifiedEntryModal.vue', { ledgerApi: { createTransaction: payload => { calls.push(payload); return wait.promise } } })
  fillEntry(s); await vue.nextTick(); await s.state.handleSubmit(); assert.equal(calls.length, 0)
  s.state.reviewEntry(); s.state.form.value.amount = 12.35; await s.state.handleSubmit(); assert.equal(calls.length, 0); assert.equal(s.state.step.value, 2)
  s.state.reviewEntry(); const first = s.state.handleSubmit(), second = s.state.handleSubmit()
  assert.equal(calls.length, 1); assert.equal(calls[0].postings[0].amount, 12.35); assert.equal(calls[0].requestedAt, '2026-10-11 10:00:00'); assert.equal(calls[0].postings[0].currency, 'CNY')
  wait.resolve(txn()); await Promise.all([first, second]); assert.equal(s.emitted.filter(e => e.name === 'success').length, 1); s.close()
})

test('edit uses PUT adapter once and never creates a copy; income and transfer retain formal APIs', async () => {
  let updates = 0, creates = 0
  const s = await setup('components/UnifiedEntryModal.vue', { ledgerApi: { updateTransaction: async (id, payload) => { assert.equal(id, 'T1'); assert.equal(payload.postings[0].amount, 12.34); updates++; return txn() }, createTransaction: async () => { creates++; return txn() } } }, { editingTxn: txn() })
  fillEntry(s); await vue.nextTick(); s.state.reviewEntry(); await s.state.handleSubmit(); assert.equal(updates, 1); assert.equal(creates, 0); s.close()
  for (const type of ['INCOME', 'TRANSFER_OUT']) {
    const calls = [], s = await setup('components/UnifiedEntryModal.vue', { ledgerApi: { createTransaction: async payload => { calls.push(payload); return txn() } } })
    fillEntry(s, type); Object.assign(s.state.form.value, { fromParentAccountId: 10, fromAccountId: 11, toParentAccountId: 20, toAccountId: 21 }); await vue.nextTick()
    s.state.reviewEntry(); await s.state.handleSubmit(); assert.equal(calls.length, 1); assert.equal(calls[0].txnType, type); s.close()
  }
})

test('shared old/new write lock stops network/500 replay but preserves 403 feedback and read API behavior', async () => {
  safety.entryWriteState.uncertain = false
  const wait = deferred(), api = safety.guardWrites({ write: () => wait.promise, read: async () => 'read' }, ['write'])
  const first = api.write(); await assert.rejects(api.write(), /正在提交/); assert.equal(await api.read(), 'read'); wait.resolve('done'); assert.equal(await first, 'done')
  for (const error of [Error('timeout'), { response: { status: 500 } }]) {
    safety.entryWriteState.uncertain = false; let count = 0
    const api = safety.guardWrites({ write: async () => { count++; throw error } }, ['write'])
    await assert.rejects(api.write()); await assert.rejects(api.write(), /不要重复提交/); assert.equal(count, 1)
  }
  safety.entryWriteState.uncertain = false
  await assert.rejects(safety.financialWrite(async () => { throw { response: { status: 403 } } })); assert.equal(safety.entryWriteState.uncertain, false)
})

test('combo payment keeps real postings; edited investment cannot create a new order', async () => {
  const calls = []
  const s = await setup('components/UnifiedEntryModal.vue', { ledgerApi: { createTransaction: async payload => { calls.push(payload); return txn() } } })
  fillEntry(s); await vue.nextTick()
  s.state.useComboPayment.value = true
  s.state.expenseFundingLines.value = [{ accountId: 11, amount: 2.34 }, { accountId: 21, amount: 10 }]
  s.state.reviewEntry(); await s.state.handleSubmit()
  assert.equal(calls.length, 1); assert.deepEqual(calls[0].postings.map(p => p.amount), [2.34, 10, 12.34]); s.close()
  const edit = await setup('components/UnifiedEntryModal.vue', {}, { editingTxn: txn({ txnType: 'SUBSCRIPTION' }) })
  assert.throws(() => edit.state.checkSubmissionOwner('createOrder'), /身份|页面/)
  edit.state.submissionOwner = edit.state.identity()
  edit.state.reviewedSnapshot = edit.state.entrySnapshot()
  assert.throws(() => edit.state.checkSubmissionOwner('createOrder'), /不会新建副本/)
  assert.throws(() => edit.state.checkSubmissionOwner('createTransaction'), /不会新建副本/)
  edit.close()
})

test('refresh still reaches other views when account reload fails and no historical recalculation is invoked', async () => {
  const s = await setup('views/Ledger.vue', { accountApi: { getAccounts: async () => { throw Error('账户读取失败') } } })
  let refresh = 0
  s.listeners.set('data-refresh', () => { refresh++ })
  await s.state.handleEntrySuccess(); assert.equal(refresh, 1); assert.match(s.state.accountError.value, /失败/)
  assert.doesNotMatch(await read('views/Ledger.vue'), /recalculateAllBalanceHistory\(/)
  s.close()
})

test('quick/refund/reimburse require human confirmation; cancelling or repeated click creates no duplicate', async () => {
  for (const [file, method] of [['QuickEntryModal.vue', 'quickEntry'], ['RefundModal.vue', 'refund'], ['ReimburseModal.vue', 'reimburse']]) {
    const wait = deferred(), calls = []
    const s = await setup(`components/${file}`, { ledgerApi: { [method]: async (...args) => { calls.push(args); return txn() } } }, { type: 'EXPENSE', expenseTxn: txn({ postings: [{ accountType: 'EXPENSE', amount: 100 }] }) })
    Object.assign(s.state.form.value, { amount: 12.34, accountId: 11, category: [shared.expenseCategories[0].id], occurredAt: '2026-10-11 10:00:00' })
    globalThis.__ledgerMocks['element-plus'].ElMessageBox.confirm = () => wait.promise
    const pending = s.state.handleSubmit(); await s.state.handleSubmit(); assert.equal(calls.length, 0)
    wait.reject('cancel'); await pending; assert.equal(calls.length, 0); assert.equal(s.state.submitting.value, false)
    globalThis.__ledgerMocks['element-plus'].ElMessageBox.confirm = async () => {}
    await s.state.handleSubmit(); assert.equal(calls.length, 1); s.close()
  }
})

test('identity change while quick confirmation is pending never sends the old form', async () => {
  const wait = deferred(); let writes = 0
  const s = await setup('components/QuickEntryModal.vue', { ledgerApi: { quickEntry: async () => { writes++; return txn() } } }, { type: 'EXPENSE' })
  Object.assign(s.state.form.value, { amount: 12.34, accountId: 11, category: [1], occurredAt: '2026-10-11 10:00:00' })
  globalThis.__ledgerMocks['element-plus'].ElMessageBox.confirm = () => wait.promise
  const pending = s.state.handleSubmit(); s.user.token = 'new'; wait.resolve(); await pending
  assert.equal(writes, 0); assert.equal(s.props.modelValue, false); assert.match(s.state.serverError.value, /身份/); s.close()
})
