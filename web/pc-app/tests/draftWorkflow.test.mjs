import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import * as vue from 'vue'
import * as shared from '../../shared/dist/index.js'

const read = path => readFile(new URL(`../src/${path}`, import.meta.url), 'utf8')
const moduleOf = code => import(`data:text/javascript;base64,${Buffer.from(ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.ESNext } }).outputText).toString('base64')}`)
const model = await moduleOf(await read('views/managementViewModel.ts'))
const deferred = () => { let resolve, reject; const promise = new Promise((r, j) => { resolve = r; reject = j }); return { promise, resolve, reject } }
const draft = (type = 'EXPENSE', extra = {}) => ({ id: 1, ownerUserId: 1, ownerFamilyId: 1, status: 'DRAFT', sourceType: 'HERMES_TEXT', sourceRef: 'fixture-source-1', rawInput: '隔离草稿', missingFieldsJson: '[]', updatedAt: '2026-10-12 10:00:00', parsedPayloadJson: JSON.stringify({ txnType: type, amount: 12.34, accountId: 11, targetAccountId: 21, productId: 101, shares: 3.5, sourceAccountId: 31, note: 'fixture', extraEvidence: 'retained' }), ...extra })
const previewOf = (type = 'EXPENSE', extra = {}) => ({ draftId: 1, txnType: type, amount: 12.34, accountId: 11, accountName: '付款账户', targetAccountId: 21, targetAccountName: '到账账户', shares: 3.5, availableShares: 8, remainingShares: 4.5, productId: 101, productName: '真实产品', confirmSupported: true, ...extra })
const account = (id, extra = {}) => ({ id, accountName: `账户${id}`, accountType: 'BANK', accountKind: 'REAL', fundUsage: 'SPENDABLE', currency: 'CNY', isActive: true, ...extra })
const accountRows = [account(10), account(11, { parentAccountId: 10 }), account(20), account(21, { parentAccountId: 20 }), account(31, { fundUsage: 'INVESTABLE' }), account(41, { fundUsage: 'RESERVED' }), account(51, { accountKind: 'VIRTUAL', virtualSubtype: 'POSITION' }), account(61, { currency: 'USD' }), account(71, { isActive: false })]
const productRows = [{ id: 101, productName: '真实产品', productCode: 'F1', assetType: 'FUND', currency: 'CNY', isActive: true }, { id: 102, productName: '逆回购', assetType: 'BOND_REPO', currency: 'CNY', isActive: true }, { id: 103, currency: 'USD', isActive: false }]

async function setup(overrides = {}, initialUser = { id: 1, familyId: 1 }) {
  const user = vue.reactive({ token: 'fixture-only', user: initialUser }), notices = [], dialogs = [], listeners = new Map()
  const lifecycle = { mounted: [], unmounted: [], activated: [], deactivated: [] }
  globalThis.window = { addEventListener: (n, f) => listeners.set(n, f), removeEventListener: n => listeners.delete(n), dispatchEvent: e => listeners.get(e.type)?.() }
  const notify = info => notices.push(info)
  for (const kind of ['info', 'success', 'error', 'warning']) notify[kind] = notify
  const router = { push: async path => dialogs.push(path) }
  const mocks = {
    vue: { ...vue, onMounted: f => lifecycle.mounted.push(f), onBeforeUnmount: f => lifecycle.unmounted.push(f), onActivated: f => lifecycle.activated.push(f), onDeactivated: f => lifecycle.deactivated.push(f) },
    'vue-router': { useRoute: () => ({ query: {} }), useRouter: () => router },
    'element-plus': { ElNotification: notify, ElMessageBox: { confirm: async (message, title, options) => dialogs.push({ message, title, options }), prompt: async () => ({ value: '重复来源' }) } },
    '@wealth-hub/shared': { ...shared, useUserStore: () => user, accountApi: { getAccounts: async () => accountRows }, productApi: { getProducts: async () => productRows }, holdingApi: { getProductHoldingsByAccount: async () => [{ accountId: 31, accountName: '持仓来源', shares: 10 }] }, draftApi: { listDrafts: async () => [draft()], getDraft: async () => draft(), previewDraft: async () => previewOf(), confirmDraft: async () => draft('EXPENSE', { status: 'CONFIRMED', confirmTxnId: 'T1' }), history: async () => [] }, aiAccountingApi: {}, ...overrides },
    './managementViewModel': model,
  }
  globalThis.__draftMocks = mocks
  const descriptor = parse(await read('views/DraftInbox.vue')).descriptor
  assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: 'DraftInbox.vue', id: 'draft' }).errors, [])
  let code = ts.transpileModule(compileScript(descriptor, { id: 'draft' }).content, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.ESNext } }).outputText
  code = code.replace(/import \{([^}]+)\} from ['"]([^'"]+)['"]/g, (_, names, path) => `const {${names.replace(/ as /g, ': ')}} = globalThis.__draftMocks[${JSON.stringify(path)}]`)
  const scope = vue.effectScope(), component = await moduleOf(`${code}\n// fixture ${Math.random()}`)
  const state = scope.run(() => component.default.setup({}, { expose() {} }))
  return { state, mocks, user, notices, dialogs, lifecycle, listeners, close() { lifecycle.unmounted.forEach(f => f()); scope.stop() } }
}

test('all seven directions save existing DTO fields, preserve source/evidence and require a separate preview/confirm', async () => {
  for (const type of ['EXPENSE', 'INCOME', 'TRANSFER', 'BUY', 'SUBSCRIPTION', 'SELL', 'REDEMPTION']) {
    const row = draft(type), writes = [], previews = []
    const s = await setup({ draftApi: { getDraft: async () => row, listDrafts: async () => [row], updateDraft: async (id, payload) => { writes.push(payload); return { ...row, parsedPayloadJson: payload.parsedPayloadJson } }, previewDraft: async () => { previews.push(1); return previewOf(type) } } })
    await s.state.openEditDraft(row)
    if (['BUY', 'SUBSCRIPTION'].includes(type)) s.state.draftEditForm.value.accountId = 31
    assert.equal(s.state.validateDraftEditForm(), null, type)
    await s.state.handleSaveDraftEdit(); assert.equal(writes.length, 1); assert.equal(previews.length, 0)
    const payload = JSON.parse(writes[0].parsedPayloadJson)
    assert.equal(writes[0].sourceRef, row.sourceRef); assert.equal(payload.extraEvidence, 'retained'); assert.deepEqual(payload.missingFields, [])
    assert.equal(payload.txnType, type)
    if (['SELL', 'REDEMPTION'].includes(type)) { assert.equal(payload.shares, 3.5); assert.equal(payload.sourceAccountId, 31); assert.equal(payload.targetAccountId, 21); assert.equal(payload.amount, undefined); assert.equal(payload.accountId, undefined) }
    else { assert.equal(payload.amount, 12.34); assert.equal(payload.sourceAccountId, undefined); if (type === 'TRANSFER') assert.equal(payload.targetAccountId, 21); else assert.equal(payload.targetAccountId, undefined) }
    if (['BUY', 'SUBSCRIPTION', 'SELL', 'REDEMPTION'].includes(type)) { assert.equal(payload.productId, 101); assert.equal(payload.orderType, type) }
    else assert.equal(payload.productId, undefined)
    assert.equal(s.state.preview.value, null); s.close()
  }
})

test('invalid amounts/shares, same transfer account and unavailable product/source/target never save', async () => {
  let writes = 0
  const s = await setup({ draftApi: { getDraft: async () => draft(), updateDraft: async () => { writes++ } } })
  await s.state.openEditDraft(draft())
  for (const value of ['', '0', '-1', 'Infinity', 'NaN', '1e999', 'oops']) { s.state.draftEditForm.value.amount = value; await s.state.handleSaveDraftEdit(); assert.equal(writes, 0) }
  s.state.draftEditForm.value.amount = '12.34'; s.state.draftEditForm.value.txnType = 'TRANSFER'; s.state.draftEditForm.value.targetAccountId = 11
  assert.match(s.state.validateDraftEditForm(), /不能相同/)
  s.state.draftEditForm.value.targetAccountId = 61; assert.match(s.state.validateDraftEditForm(), /币种/)
  s.state.draftEditForm.value.txnType = 'SELL'; s.state.products.value = productRows; s.state.productHoldings.value = [{ accountId: 31, shares: 10 }]
  for (const value of ['0', '-2', 'Infinity', 'NaN']) { s.state.draftEditForm.value.shares = value; assert.match(s.state.validateDraftEditForm(), /份额/) }
  s.state.draftEditForm.value.shares = '3.5'; s.state.draftEditForm.value.productId = 101; s.state.draftEditForm.value.sourceAccountId = 999; s.state.draftEditForm.value.targetAccountId = 21
  assert.match(s.state.validateDraftEditForm(), /真实持仓/)
  s.state.draftEditForm.value.sourceAccountId = 31
  for (const id of [10, 51, 61, 71]) { s.state.draftEditForm.value.targetAccountId = id; assert.match(s.state.validateDraftEditForm(), /REAL/) }
  s.close()
})

test('investment only allows real leaf INVESTABLE or bond repo RESERVED, no name-based product matching', async () => {
  const s = await setup({ draftApi: { getDraft: async () => draft('BUY') } })
  await s.state.openEditDraft(draft('BUY'))
  assert.deepEqual(s.state.investmentAccountOptions.value.map(a => a.id), [31])
  s.state.draftEditForm.value.productId = 102
  assert.deepEqual(s.state.investmentAccountOptions.value.map(a => a.id).sort(), [31, 41])
  s.state.draftEditForm.value.productId = undefined; s.state.draftEditForm.value.productNameHint = '真实产品'; s.state.draftEditForm.value.accountId = 31
  assert.match(s.state.validateDraftEditForm(), /真实产品/); s.close()
})

test('saving and previewing after field completion still causes zero confirm requests', async () => {
  let confirms = 0, previews = 0
  const row = draft(), s = await setup({ draftApi: { getDraft: async () => row, updateDraft: async () => row, listDrafts: async () => [row], previewDraft: async () => { previews++; return previewOf() }, confirmDraft: async () => { confirms++ } } })
  await s.state.openEditDraft(row); await s.state.handleSaveDraftEdit(true)
  assert.equal(previews, 1); assert.equal(confirms, 0); assert.equal(s.state.canConfirmSelectedDraft.value, true); s.close()
})

test('duplicate confirm clicks share one fresh preview/dialog/write, and final state cannot reconfirm', async () => {
  const wait = deferred(); let confirms = 0, previews = 0
  const row = draft(), final = draft('EXPENSE', { status: 'CONFIRMED' })
  const s = await setup({ draftApi: { getDraft: async () => row, previewDraft: async () => { previews++; return previewOf() }, confirmDraft: async id => { assert.equal(id, 1); confirms++; return final }, listDrafts: async () => [] } })
  await s.state.selectDraft(row); await s.state.handlePreview(row)
  s.mocks['element-plus'].ElMessageBox.confirm = () => wait.promise
  const first = s.state.handleConfirm(row); await vue.nextTick(); await s.state.handleConfirm(row)
  assert.equal(previews, 2); assert.equal(confirms, 0); assert.equal(s.state.confirming.value, true)
  wait.resolve(); await first; assert.equal(confirms, 1); assert.equal(s.state.selectedDraft.value.status, 'CONFIRMED'); assert.equal(s.state.preview.value, null)
  await s.state.handleConfirm(final); assert.equal(confirms, 1); s.close()
})

test('each direction presents the backend impact before human confirmation with unchanged confirm signature', async () => {
  for (const type of ['EXPENSE', 'INCOME', 'TRANSFER', 'BUY', 'SUBSCRIPTION', 'SELL', 'REDEMPTION']) {
    const writes = [], row = draft(type)
    const s = await setup({ draftApi: { getDraft: async () => row, listDrafts: async () => [], previewDraft: async () => previewOf(type), confirmDraft: async (...args) => { writes.push(args); return draft(type, { status: 'CONFIRMED', confirmOrderId: ['BUY', 'SUBSCRIPTION', 'SELL', 'REDEMPTION'].includes(type) ? 'O1' : null }) } } })
    await s.state.selectDraft(row); await s.state.handlePreview(row); await s.state.handleConfirm(row)
    assert.deepEqual(writes, [[1]])
    const dialog = s.dialogs[0]
    if (['SELL', 'REDEMPTION'].includes(type)) { assert.match(dialog.message, /不会立即减少持仓/); assert.match(dialog.title, /3.5/); assert.match(s.notices.at(-1).message, /人工结算/) }
    else if (['BUY', 'SUBSCRIPTION'].includes(type)) { assert.match(dialog.message, /立即.*扣除/); assert.match(dialog.message, /不会自动成交/) }
    else if (type === 'TRANSFER') assert.match(dialog.title, /付款账户.*到账账户/)
    s.close()
  }
})

test('preview failure or unsupported fresh preview clears old eligibility and never opens confirmation', async () => {
  for (const outcome of ['failure', 'unsupported']) {
    let reads = 0, confirms = 0
    const s = await setup({ draftApi: { getDraft: async () => draft(), previewDraft: async () => { if (!reads++) return previewOf(); if (outcome === 'failure') throw Error('预览过期或离线'); return previewOf('SELL', { confirmSupported: false, message: '可用份额不足' }) }, confirmDraft: async () => { confirms++ } } })
    await s.state.selectDraft(draft()); await s.state.handlePreview(draft()); await s.state.handleConfirm(draft())
    assert.equal(confirms, 0); assert.equal(s.dialogs.length, 0); assert.equal(s.state.preview.value, null); assert.equal(s.state.canConfirmSelectedDraft.value, false)
    if (outcome === 'failure') assert.match(s.state.actionError.value, /过期|离线/)
    s.close()
  }
})

test('cancelled second confirmation consumes preview and never calls confirm', async () => {
  let confirms = 0
  const s = await setup({ draftApi: { getDraft: async () => draft(), previewDraft: async () => previewOf(), confirmDraft: async () => { confirms++ } } })
  await s.state.selectDraft(draft()); await s.state.handlePreview(draft())
  s.mocks['element-plus'].ElMessageBox.confirm = async () => { throw 'cancel' }
  await s.state.handleConfirm(draft()); assert.equal(confirms, 0); assert.equal(s.state.preview.value, null); assert.equal(s.state.confirming.value, false); s.close()
})

test('identity or refreshed selection changes while dialog is pending prevent old confirm requests', async () => {
  for (const action of ['token', 'family', 'refresh', 'unmount', 'deactivate']) {
    const wait = deferred(); let confirms = 0
    const s = await setup({ draftApi: { getDraft: async () => draft(), listDrafts: async () => [draft()], previewDraft: async () => previewOf(), confirmDraft: async () => { confirms++ } } })
    await s.state.selectDraft(draft()); await s.state.handlePreview(draft()); s.mocks['element-plus'].ElMessageBox.confirm = () => wait.promise
    const pending = s.state.handleConfirm(draft()); await vue.nextTick()
    if (action === 'token') s.user.token = 'different'
    if (action === 'family') s.user.user.familyId = 2
    if (action === 'refresh') await s.state.loadDrafts()
    if (action === 'unmount') s.close()
    if (action === 'deactivate') s.lifecycle.deactivated.forEach(f => f())
    wait.resolve(); await pending; assert.equal(confirms, 0, action); s.close()
  }
})

test('lost confirm response is locked per draft; permission errors retain server reason and clear preview', async () => {
  for (const error of [Error('offline'), { response: { status: 500, data: { message: '提交结果未知' } } }, { response: { status: 403, data: { message: '当前家庭没有确认权限' } } }]) {
    let calls = 0
    const s = await setup({ draftApi: { getDraft: async () => draft(), listDrafts: async () => [draft()], previewDraft: async () => previewOf(), confirmDraft: async () => { calls++; throw error } } })
    await s.state.selectDraft(draft()); await s.state.handlePreview(draft()); await s.state.handleConfirm(draft())
    assert.equal(s.state.preview.value, null)
    if (error.response?.status === 403) { assert.equal(s.state.uncertainConfirmIds.value.has(1), false); assert.match(s.state.actionError.value, /家庭.*权限/) }
    else { await s.state.loadDrafts(); await s.state.handlePreview(draft()); await s.state.handleConfirm(draft()); assert.equal(s.state.uncertainConfirmIds.value.has(1), true); assert.equal(calls, 1) }
    s.close()
  }
})

test('list and detail errors clear stale records; successful empty response is not a failed permission read', async () => {
  let error
  const s = await setup({ draftApi: { listDrafts: async () => { if (error) throw error; return [] }, getDraft: async () => { throw { response: { status: 403, data: { message: '无权查看草稿' } } } } } })
  await s.state.loadDrafts(); assert.equal(s.state.listError.value, '')
  error = Error('offline'); s.state.drafts.value = [draft()]; await s.state.loadDrafts(); assert.deepEqual(s.state.drafts.value, []); assert.match(s.state.listError.value, /offline/)
  await s.state.selectDraft(draft()); assert.equal(s.state.selectedDraft.value, null); assert.match(s.state.detailError.value, /无权/); s.close()
})

test('late or overlapping list/detail/preview/history responses cannot revive an old page or identity', async () => {
  for (const kind of ['list', 'detail', 'preview', 'history']) {
    for (const action of ['identity', 'unmount', 'deactivate', 'overlap']) {
      const wait = deferred(); let count = 0
      const methods = { list: 'listDrafts', detail: 'getDraft', preview: 'previewDraft', history: 'history' }, responses = { list: [draft()], detail: draft(), preview: previewOf(), history: [{ id: 1 }] }
      const s = await setup({ draftApi: {} })
      s.mocks['@wealth-hub/shared'].draftApi[methods[kind]] = () => count++ ? Promise.resolve(kind === 'list' || kind === 'history' ? [] : kind === 'detail' ? draft('INCOME', { id: 2 }) : previewOf('INCOME')) : wait.promise
      s.state.selectedDraft.value = draft(); s.state.detailVisible.value = true
      const run = () => kind === 'list' ? s.state.loadDrafts() : kind === 'detail' ? s.state.selectDraft(draft('INCOME', { id: 2 })) : kind === 'preview' ? s.state.requestPreview(draft()) : s.state.loadHistory(1)
      const pending = kind === 'detail' ? s.state.selectDraft(draft()) : run()
      if (action === 'identity') s.user.token = 'other'
      if (action === 'unmount') s.close()
      if (action === 'deactivate') s.lifecycle.deactivated.forEach(f => f())
      if (action === 'overlap') await run()
      wait.resolve(responses[kind]); await pending
      if (action !== 'overlap') { assert.equal(s.state.selectedDraft.value, null); assert.equal(s.state.preview.value, null); assert.deepEqual(s.state.history.value, []); assert.deepEqual(s.state.drafts.value, []) }
      else if (kind === 'list') assert.deepEqual(s.state.drafts.value, [])
      else if (kind === 'detail') assert.equal(s.state.selectedDraft.value.id, 2)
      else if (kind === 'preview') assert.equal(s.state.preview.value.txnType, 'INCOME')
      else assert.deepEqual(s.state.history.value, [])
      s.close()
    }
  }
})

test('product changes reject old holdings and source options fail closed with manual retry', async () => {
  const wait = deferred(); let call = 0
  const s = await setup({ draftApi: { getDraft: async () => draft('SELL') }, holdingApi: { getProductHoldingsByAccount: () => call++ ? Promise.resolve([{ accountId: 41, shares: 9 }]) : wait.promise } })
  const opening = s.state.openEditDraft(draft('SELL'))
  for (let i = 0; call === 0 && i < 10; i++) await new Promise(resolve => setImmediate(resolve))
  assert.equal(call, 1)
  s.state.draftEditForm.value.productId = 102; const change = s.state.handleProductChange(); await change
  wait.resolve([{ accountId: 31, shares: 10 }]); await opening
  assert.deepEqual(s.state.productHoldings.value.map(h => h.accountId), [41]); assert.equal(s.state.draftEditForm.value.sourceAccountId, undefined); s.close()
  const denied = await setup({ accountApi: { getAccounts: async () => { throw Error('选项无权限') } } })
  await denied.state.openEditDraft(draft()); assert.equal(denied.state.editOptionsReady.value, false); assert.match(denied.state.editError.value, /权限/); assert.match(denied.state.validateDraftEditForm(), /选项/); denied.close()
})

test('source conflicts remain visible and old preview never survives failed draft save', async () => {
  const s = await setup({ draftApi: { getDraft: async () => draft(), updateDraft: async () => { throw { response: { data: { message: '该来源已被另一条草稿占用' } } } } } })
  await s.state.openEditDraft(draft()); s.state.preview.value = previewOf(); await s.state.handleSaveDraftEdit()
  assert.match(s.state.editError.value, /来源.*占用/); assert.equal(s.state.preview.value, null); assert.equal(s.state.editingDraft.value, true); s.close()
})

test('text parsing clears old candidates; text change invalidates intent; duplicate creation returns real final status', async () => {
  const wait = deferred(); let parsed = 0, creates = 0
  const intent = { txnType: 'SELL', rawInput: '卖出3份', sourceType: 'HERMES_TEXT', sourceRef: 'same-source', missingFields: ['productId'], shares: 3 }
  const existing = draft('SELL', { status: 'CONFIRMED' })
  const s = await setup({ aiAccountingApi: { parseText: async () => { if (parsed++) throw Error('解析失败'); return intent }, draftFromIntent: () => { creates++; return wait.promise } }, draftApi: { listDrafts: async () => [existing], getDraft: async () => existing } })
  s.state.textInput.value = intent.rawInput; await s.state.handleParseText(); assert.equal(s.state.parsedIntent.value.shares, 3)
  const first = s.state.handleCreateDraft(); await s.state.handleCreateDraft(); assert.equal(creates, 1)
  wait.resolve({ draft: existing, intent }); await first; assert.equal(s.state.selectedDraft.value.status, 'CONFIRMED'); assert.equal(s.state.canConfirmSelectedDraft.value, false)
  s.state.textInput.value = intent.rawInput; s.state.parsedIntent.value = intent; await s.state.handleParseText(); assert.equal(s.state.parsedIntent.value, null); assert.match(s.state.textError.value, /失败/)
  s.state.parsedIntent.value = intent; s.state.textInput.value = 'changed'; assert.equal(s.state.parsedIntent.value, null); s.close()
})

test('late text parse response after identity change cannot restore candidate input', async () => {
  const wait = deferred(), s = await setup({ aiAccountingApi: { parseText: () => wait.promise } })
  s.state.textInput.value = 'fixture'; const pending = s.state.handleParseText(); s.user.token = 'other'; wait.resolve({ rawInput: 'fixture', missingFields: [] }); await pending
  assert.equal(s.state.parsedIntent.value, null); assert.equal(s.state.textInput.value, ''); s.close()
})

test('ignored/confirmed remain read-only, lifecycle needs human approval and identity guard', async () => {
  for (const status of ['IGNORED', 'CONFIRMED']) {
    let previews = 0, confirms = 0, updates = 0
    const row = draft('EXPENSE', { status })
    const s = await setup({ draftApi: { getDraft: async () => row, previewDraft: async () => { previews++ }, confirmDraft: async () => { confirms++ }, updateDraft: async () => { updates++ } } })
    await s.state.selectDraft(row); await s.state.openEditDraft(row); await s.state.handlePreview(row); await s.state.handleConfirm(row); await s.state.handleSaveDraftEdit()
    assert.deepEqual([previews, confirms, updates], [0, 0, 0]); s.close()
  }
  for (const [status, action, api] of [['DRAFT', 'handleIgnore', 'ignoreDraft'], ['IGNORED', 'handleReopen', 'reopen'], ['CONFIRMED', 'handleCopy', 'copyConfirmed']]) {
    const wait = deferred(); let writes = 0; const row = draft('EXPENSE', { status })
    const s = await setup({ draftApi: { getDraft: async () => row, [api]: async () => { writes++ } } })
    await s.state.selectDraft(row)
    s.mocks['element-plus'].ElMessageBox[status === 'DRAFT' ? 'prompt' : 'confirm'] = () => wait.promise
    const pending = s.state[action](row); await s.state[action](row); assert.equal(writes, 0)
    s.user.user.familyId = 2; wait.resolve({ value: '' }); await pending; assert.equal(writes, 0); s.close()
  }
})

test('successful ignore/reopen/copy retain original API and result status without formal confirm', async () => {
  for (const [status, action, api, target] of [['DRAFT', 'handleIgnore', 'ignoreDraft', 'IGNORED'], ['IGNORED', 'handleReopen', 'reopen', 'DRAFT'], ['CONFIRMED', 'handleCopy', 'copyConfirmed', 'DRAFT']]) {
    let calls = 0, copied = false; const row = draft('EXPENSE', { status }), result = draft('EXPENSE', { id: api === 'copyConfirmed' ? 2 : 1, status: target })
    const s = await setup({ draftApi: { getDraft: async () => copied ? result : row, listDrafts: async () => [result], [api]: async (id, body) => { assert.equal(id, 1); if (api === 'ignoreDraft') assert.equal(body.ignoreReason, '重复来源'); calls++; copied = true; return result } } })
    await s.state.selectDraft(row); await s.state[action](row); assert.equal(calls, 1); assert.equal(s.state.selectedDraft.value.status, target); assert.equal(s.state.preview.value, null); s.close()
  }
})

test('server pagination and local filters keep scope explicit; refresh listener removed on unmount', async () => {
  const queries = [], rows = [draft(), draft('SELL', { id: 2, sourceType: 'ocr', rawInput: '隔离图片', updatedAt: 'invalid' })]
  const s = await setup({ draftApi: { listDrafts: async query => { queries.push(query); return rows } } })
  await s.state.loadDrafts(); assert.deepEqual(queries[0], { status: 'DRAFT', page: 1, pageSize: 50 })
  s.state.typeFilter.value = 'SELL'; assert.equal(s.state.visibleDrafts.value[0].id, 2)
  s.state.timeFilter.value = '7'; assert.equal(s.state.visibleDrafts.value.length, 0)
  s.state.resetFilters(); s.state.sourceFilter.value = 'ocr'; assert.equal(s.state.visibleDrafts.value.length, 1)
  s.state.page.value = 2; s.state.statusFilter.value = ''; await s.state.loadDrafts(); assert.deepEqual(queries[1], { status: undefined, page: 2, pageSize: 50 })
  s.lifecycle.mounted.forEach(f => f()); assert.equal(s.listeners.has('data-refresh'), true); s.close(); assert.equal(s.listeners.has('data-refresh'), false)
  const source = await read('views/DraftInbox.vue'); assert.match(source, /只筛选当前页/); assert.doesNotMatch(source, /localStorage|settlementApi|createTransaction/)
})

test('unknown/non-finite quantities remain unknown and do not masquerade as zero', async () => {
  const s = await setup()
  assert.equal(s.state.asNumber(Infinity), null); assert.equal(s.state.asNumber('Infinity'), null)
  assert.equal(s.state.formatAmount(Infinity), '-'); assert.equal(s.state.formatShares(null), '-'); assert.equal(s.state.formatBooleanImpact(null), '未知'); assert.equal(s.state.formatBooleanImpact(false), '不会生成'); assert.match(s.state.formatAmount(0), /0.00/)
  s.close()
})

test('initial user hydration restarts the read with the known identity; later identity changes require refresh', async () => {
  const wait = deferred(); let reads = 0
  const s = await setup({ draftApi: { listDrafts: () => reads++ ? Promise.resolve([draft()]) : wait.promise } }, null)
  const pending = s.state.loadDrafts(); s.user.user = { id: 1, familyId: 1 }
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(reads, 2); assert.equal(s.state.drafts.value.length, 1); assert.equal(s.state.listError.value, '')
  wait.resolve([draft('SELL', { id: 999 })]); await pending; assert.equal(s.state.drafts.value[0].id, 1)
  s.user.user.familyId = 2; assert.equal(reads, 2); assert.deepEqual(s.state.drafts.value, []); assert.match(s.state.listError.value, /家庭/); s.close()
})

test('closing a drawer discards a late detail response and invalidates its old preview', async () => {
  const wait = deferred(), s = await setup({ draftApi: { getDraft: () => wait.promise } })
  const pending = s.state.selectDraft(draft()); let closed = false
  s.state.closeDetail(() => { closed = true }); wait.resolve(draft()); await pending
  assert.equal(closed, true); assert.equal(s.state.detailVisible.value, false); assert.equal(s.state.selectedDraft.value, null); assert.equal(s.state.preview.value, null); s.close()
})
