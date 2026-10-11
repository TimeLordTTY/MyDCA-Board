import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { execFileSync } from 'node:child_process'
import ts from 'typescript'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import * as vue from 'vue'

const read = name => readFile(new URL(`../src/views/${name}`, import.meta.url), 'utf8')
const importScript = code => import(`data:text/javascript;base64,${Buffer.from(ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.ESNext } }).outputText).toString('base64')}`)
const model = await importScript(await read('managementViewModel.ts'))
const shared = await import('../../shared/dist/index.js')
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
const account = (id = 1, extra = {}) => ({ id, accountCode: `a${id}`, accountName: `账户${id}`, accountKind: 'REAL', accountType: 'CASH', currency: 'CNY', balance: 0, reservedAmount: 0, isActive: true, ...extra })
const product = (id = 1, extra = {}) => ({ id, productCode: `p${id}`, productName: `产品${id}`, channel: 'EXCHANGE', assetType: 'ETF', currency: 'CNY', isActive: true, ...extra })

async function setup(file, overrides = {}) {
  const user = vue.reactive({ token: 'fixture', user: { id: 1, familyId: 1 } })
  const accounts = vue.reactive({ accounts: [], updateAccount: async () => {}, createAccount: async () => ({ id: 2 }) })
  const lifecycle = { mounted: [], unmounted: [], activated: [], deactivated: [] }
  const events = [], notices = []
  globalThis.window = { dispatchEvent() {}, addEventListener() {}, removeEventListener() {}, setInterval: () => 1, clearInterval() {} }
  globalThis.CustomEvent = class {}
  const mocks = {
    vue: { ...vue, onMounted: f => lifecycle.mounted.push(f), onUnmounted: f => lifecycle.unmounted.push(f), onBeforeUnmount: f => lifecycle.unmounted.push(f), onActivated: f => lifecycle.activated.push(f), onDeactivated: f => lifecycle.deactivated.push(f) },
    'vue-router': { useRouter: () => ({ push: path => events.push(path) }) },
    'element-plus': { ElNotification: Object.fromEntries(['error', 'warning', 'success'].map(key => [key, v => notices.push(v)])), ElMessageBox: { confirm: async () => events.push('confirm') }, ...overrides.element },
    '@wealth-hub/shared': { ...shared, useUserStore: () => user, useAccountStore: () => accounts, useProductStore: () => ({ createProduct: async () => product(), updateProduct: async () => product() }), accountApi: { getAccounts: async () => [] }, productApi: { getProducts: async () => [] }, holdingApi: { getHoldings: async () => [] }, ...overrides.shared },
    './managementViewModel': model,
  }
  globalThis.__managementMocks = mocks
  let code = compileScript(parse(await read(file)).descriptor, { id: `management-${Math.random()}` }).content
  code = ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.ESNext } }).outputText
  code = code.replace(/import \{([^}]+)\} from ['"]([^'"]+)['"]/g, (_, names, path) => `const {${names.replace(/ as /g, ': ')}} = globalThis.__managementMocks[${JSON.stringify(path)}]`)
  const state = (await importScript(code + `\n// fixture ${Math.random()}\n//# sourceURL=management-fixture.js`)).default.setup({}, { expose() {} })
  return { state, user, accounts, events, notices, lifecycle, close() { lifecycle.unmounted.forEach(f => f()) } }
}

test('account search keeps complete parents, siblings and signed balances without mutating the tree', () => {
  const tree = [account(1, { children: [account(2, { accountName: '旅行', balance: -10 }), account(3)] }), account(4)]
  const result = model.filterAccountTree(tree, '  旅行 ', 'CASH')
  assert.deepEqual(result, [tree[0]]); assert.equal(result[0], tree[0]); assert.equal(result[0].children[0].balance, -10)
  assert.equal(result[0].children.length, 2); assert.deepEqual(model.filterAccountTree(tree, '', 'BANK'), [])
  for (const value of [null, undefined, NaN, Infinity, '0']) assert.equal(model.knownNumber(value), false)
  for (const value of [0, -10, 3.2]) assert.equal(model.knownNumber(value), true)
})

test('account reads distinguish empty, denied and failure; unknown quotes never become a zero price', async () => {
  let response = [], error
  const s = await setup('Accounts.vue', { shared: { accountApi: { getAccounts: async params => { assert.equal(params, undefined); if (error) throw error; return response } }, holdingApi: { getHoldings: async () => [{ productId: 2, brokerAccountId: 1, channel: 'EXCHANGE', totalShares: 3 }] }, marketApi: { getRealtimeQuotes: async () => [] } } })
  await s.state.loadAccounts(); assert.equal(s.state.accountError.value, ''); assert.deepEqual(s.state.accountTree.value, [])
  response = [account()]; await s.state.loadAccounts(); assert.equal(s.state.brokerHoldings.value[0].currentPrice, null); assert.equal(s.state.getBrokerHoldingsMarketValue(1), null)
  assert.equal(s.state.formatCurrency(0), shared.formatCurrency(0)); assert.equal(s.state.formatCurrency(-10), shared.formatCurrency(-10)); assert.equal(s.state.formatCurrency(null), '未知')
  for (const status of [401, 403]) { error = { response: { status } }; await s.state.loadAccounts(); assert.match(s.state.accountError.value, /权限/); assert.deepEqual(s.state.accountTree.value, []) }
  error = Error('offline'); await s.state.loadAccounts(); assert.match(s.state.accountError.value, /读取失败/)
  error = null; await s.state.loadAccounts(); assert.equal(s.state.accountError.value, '')
  s.close()
})

test('both pages discard old reads on newer requests, user/family/token changes and deactivation', async () => {
  for (const file of ['Accounts.vue', 'Products.vue']) for (const action of ['newer', 'user', 'family', 'token', 'deactivate', 'unmount']) {
    const wait = deferred(); let calls = 0
    const isAccount = file === 'Accounts.vue', value = isAccount ? account : product
    const api = { [isAccount ? 'getAccounts' : 'getProducts']: async () => calls++ ? [value(2)] : wait.promise }
    const s = await setup(file, { shared: { [isAccount ? 'accountApi' : 'productApi']: api } })
    const load = isAccount ? s.state.loadAccounts : s.state.loadAllProducts
    const rows = isAccount ? s.state.accountTree : s.state.allProducts
    const old = load()
    if (action === 'newer') await load()
    else if (action === 'user') s.user.user.id = 2
    else if (action === 'family') s.user.user.familyId = 2
    else if (action === 'token') s.user.token = ''
    else s.lifecycle[action === 'deactivate' ? 'deactivated' : 'unmounted'].forEach(f => f())
    wait.resolve([value(1)]); await old
    assert.equal(rows.value[0]?.id, action === 'newer' ? 2 : undefined)
    s.close()
  }
})

test('product filters preserve channels, disabled records, QDII compatibility and holding route', async () => {
  const records = [product(1), product(2, { isActive: false }), product(3, { channel: 'OTC', isqdii: true })]
  const s = await setup('Products.vue', { shared: { productApi: { getProducts: async () => records } } })
  await s.state.loadAllProducts(); assert.deepEqual(s.state.exchangeProducts.value.map(p => p.id), [1]); assert.equal(s.state.otcProducts.value[0].isQdii, true)
  s.state.exchangeFilters.isActive = undefined; s.state.exchangeFilters.keyword = ' p2 '; s.state.loadExchangeProducts(); assert.deepEqual(s.state.exchangeProducts.value.map(p => p.id), [2])
  s.state.handleViewHolding(records[2]); assert.deepEqual(s.events, [{ name: 'Holdings', query: { productId: 3 } }])
  s.state.handleAddProduct(); assert.equal(s.state.dialogVisible.value, true); s.state.handleDialogClose(); assert.equal(s.state.form.productName, '')
  s.close()
})

test('delayed initial identity initialization reloads current data instead of presenting a false empty list', async () => {
  for (const file of ['Accounts.vue', 'Products.vue']) {
    const s = await setup(file)
    s.user.user = null
    s.user.user = { id: 1, familyId: 1 }
    await new Promise(resolve => setTimeout(resolve, 0))
    assert.equal((file === 'Accounts.vue' ? s.state.accountReady : s.state.productsReady).value, true)
    s.close()
  }
})

test('product loading failures are visible and retryable, not false empty results', async () => {
  let fail = true
  const s = await setup('Products.vue', { shared: { productApi: { getProducts: async () => { if (fail) throw { response: { status: 403 } }; return [] } } } })
  await s.state.loadAllProducts(); assert.match(s.state.loadError.value, /权限/); assert.deepEqual(s.state.allProducts.value, [])
  fail = false; await s.state.loadAllProducts(); assert.equal(s.state.loadError.value, '')
  s.close()
})

test('keyboard ordering persists through the existing API and failed ordering retains original order', async () => {
  const calls = []; let fail = false
  const s = await setup('Products.vue', { shared: { productApi: { getProducts: async () => [product(1), product(2)], updateProductSortOrder: async rows => { calls.push(rows); if (fail) throw Error('offline') } } } })
  await s.state.loadAllProducts(); await s.state.moveProduct(1, -1, 'exchange')
  assert.deepEqual(calls[0], [{ id: 2, sortOrder: 1 }, { id: 1, sortOrder: 2 }]); assert.deepEqual(s.state.exchangeProducts.value.map(p => p.id), [2, 1])
  fail = true; await s.state.moveProduct(0, 1, 'exchange'); assert.deepEqual(s.state.exchangeProducts.value.map(p => p.id), [2, 1]); assert.equal(s.state.sorting.value, false)
  s.close()
})

test('account validation and cancelled disable confirmation never submit, backend failure leaves form open', async () => {
  let valid = false, calls = 0, cancel = true
  const s = await setup('Accounts.vue', { element: { ElMessageBox: { confirm: async () => { if (cancel) throw Error('cancel') } } } })
  s.accounts.accounts = [account(1)]
  s.accounts.updateAccount = async () => { calls++; throw Error('backend validation') }
  s.state.accountFormRef.value = { validate: async fn => fn(valid) }
  Object.assign(s.state.accountForm, account(1, { isActive: false }))
  s.state.accountDialogVisible.value = true
  await s.state.handleAccountSave(); assert.equal(calls, 0)
  valid = true; await s.state.handleAccountSave(); assert.equal(calls, 0)
  cancel = false; await s.state.handleAccountSave(); assert.equal(calls, 1); assert.equal(s.state.accountDialogVisible.value, true)
  s.close()
})

test('late product fee details cannot reopen a cancelled form or cross identities; failed fee reads block saving', async () => {
  const wait = deferred(); let calls = 0
  const s = await setup('Products.vue', { shared: { productApi: { getSellFeeTiers: async () => wait.promise }, useProductStore: () => ({ updateProduct: async () => { calls++ } }) } })
  const editing = s.state.handleEdit(product(1, { channel: 'OTC', assetType: 'FUND' }))
  s.state.handleDialogClose(); wait.resolve([]); await editing
  assert.equal(s.state.dialogVisible.value, false); assert.deepEqual(s.state.sellFeeTiers.value, [])
  s.state.feeLoadError.value = '读取失败'; s.state.formRef.value = { validate: async fn => fn(true) }; await s.state.handleSave(); assert.equal(calls, 0)
  s.close()
})

test('templates compile; account financial handlers and original product save behavior remain intact', async () => {
  for (const file of ['Accounts.vue', 'Products.vue']) {
    const source = await read(file), parsed = parse(source)
    assert.deepEqual(parsed.errors, []); assert.deepEqual(compileTemplate({ source: parsed.descriptor.template.content, filename: file, id: 'management' }).errors, [])
    const baseline = execFileSync('git', ['show', `83a3fb062edbdd01b96cc82a26ab2f63145bde11:web/pc-app/src/views/${file}`], { encoding: 'utf8' }).replaceAll('\r\n', '\n')
    const current = source.replaceAll('\r\n', '\n')
    const slice = (s, a, b) => s.slice(s.indexOf(a), s.indexOf(b, s.indexOf(a)))
    if (file === 'Accounts.vue') {
      assert.equal(slice(current, 'async function handleAccountSave()', 'async function loadAccounts()'), slice(baseline, 'async function handleAccountSave()', 'async function loadAccounts()'))
      for (const entry of ['handleAddPlatform', 'handleAddBucket', 'handleRenamePlatform', 'handleEditMmfPlatform', 'handleManageFeeConfig', 'handleEditAccount', 'handleBalanceSave', 'handleSaveFeeConfig']) assert.ok(current.includes(`@click${entry === 'handleRenamePlatform' || entry === 'handleEditMmfPlatform' || entry === 'handleManageFeeConfig' ? '.stop' : ''}="${entry}`))
    } else {
      const marker = '  await formRef.value.validate(async (valid) => {'
      assert.equal(slice(current, marker, 'onMounted(async'), slice(baseline, marker, 'onMounted(async'))
      assert.ok(current.includes("name: 'Holdings'")); assert.ok(current.includes('@dragstart="handleDragStart'))
    }
  }
})
