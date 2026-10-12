async page => {
  const check = (ok, message) => { if (!ok) throw Error(message) }
  const origin = 'http://127.0.0.1:3115', requests = [], pageerrors = [], unexpectedRequests = [], matrix = []
  let financialWrites = 0, owner = 'A', denial = 0, offlineLogin = false, offlineIdentity = false, releaseLogin, holdLogin = false
  let holdAccounts = false, accountStarted
  const accountReleases = []
  const user = () => ({ id: owner === 'A' ? 1 : 2, username: `offline-fixture-${owner}`, nickname: `隔离身份${owner}`, familyId: owner === 'A' ? 1 : 2 })
  const account = () => ({ id: 10, accountCode: 'FIXTURE', accountName: `隔离账户${owner}`, accountKind: 'REAL', accountType: 'BANK', currency: 'CNY', balance: 0, reservedAmount: 0, isActive: true })
  const product = { id: 1, productCode: 'FIXTURE', productName: '隔离来源产品', assetType: 'ETF', channel: 'EXCHANGE', market: 'CN', currency: 'CNY', isActive: true }
  const holding = { productId: 1, productName: product.productName, productCode: 'FIXTURE', channel: 'EXCHANGE', assetType: 'ETF', brokerAccountId: 10, brokerAccountName: '隔离账户', totalShares: 10, totalCost: 100, avgCost: 10, marketValue: 100, unrealizedPnl: 0 }
  const listPaths = new Set(['/orders', '/settlements/pending', '/settlements/history', '/drafts', '/goals', '/monthly-budgets', '/risk-watch-rules', '/allocation-policies', '/research-plans', '/backtests/history', '/backtest/history', '/backtest-lab/datasets', '/backtest-lab/recent', '/backtest-lab/runs', '/dashboard/pending-settlements', '/dashboard/today-actions', '/market/quotes', '/families/members'])
  page.on('pageerror', e => pageerrors.push(e.message))
  await page.route('**/*', async route => {
    const req = route.request(), url = new URL(req.url()), method = req.method()
    if (url.origin !== origin) { unexpectedRequests.push({ origin: url.origin, method }); return route.abort() }
    if (!url.pathname.startsWith('/api/')) {
      if (method !== 'GET' || !['document', 'script', 'stylesheet', 'image', 'font', 'other'].includes(req.resourceType())) { unexpectedRequests.push({ path: url.pathname, method }); return route.abort() }
      return route.continue()
    }
    const path = url.pathname.replace('/api/v2', '')
    requests.push({ path, method })
    if (method === 'POST' && ['/auth/login', '/auth/register'].includes(path)) {
      if (offlineLogin) return route.abort('internetdisconnected')
      if (holdLogin) await new Promise(resolve => { releaseLogin = resolve })
      return route.fulfill({ json: { token: `offline-fixture-${owner}`, user: user() } })
    }
    if (method === 'POST' && path === '/auth/logout') return route.abort('internetdisconnected')
    if (method !== 'GET') { financialWrites++; unexpectedRequests.push({ path, method }); return route.abort() }
    if (denial && ['/users/me', '/accounts'].includes(path)) return route.fulfill({ status: denial, json: { message: '隔离会话拒绝' } })
    if (offlineIdentity && path === '/users/me') return route.abort('internetdisconnected')
    let data
    if (path === '/users/me') data = user()
    else if (path === '/accounts') {
      data = [account()]
      if (holdAccounts) await new Promise(resolve => { accountReleases.push(resolve); accountStarted?.() })
    }
    else if (path === '/products') data = [product]
    else if (path === '/holdings') data = [holding]
    else if (path === '/ledger/txns') data = { list: [], total: 0, page: 1, pageSize: 20, totalPages: 0 }
    else if (path === '/dashboard/asset-overview') data = { cashBalance: 0, liability: 0, totalAssets: 100, positionValue: 100, netWorth: 100 }
    else if (path === '/todos/today') data = { date: '2026-10-12', counts: { draftCount: 0, pendingOrderCount: 0, pendingSettlementCount: 0 }, drafts: [], pendingOrders: [], pendingSettlements: [] }
    else if (path === '/nav/latest') data = { productId: 1, nav: 10, navDate: '2026-10-12', source: 'isolated-fixture' }
    else if (path === '/finance-radar') data = { date: '2026-10-12', scope: 'PERSONAL', assets: { status: 'UNKNOWN', netWorth: null, cashBalance: null, positionValue: null }, counts: {}, markets: [], warnings: [] }
    else if (path === '/data-readiness') data = { scope: 'PERSONAL', month: '2026-10', checkedAt: '2026-10-12', evidence: [] }
    else if (path.endsWith('/stats/summary')) data = { totalIncome: 0, totalExpense: 0, netCashflow: 0, txnCount: 0 }
    else if (/\/stats\/(trend|breakdown|top)$/.test(path)) data = []
    else if (listPaths.has(path)) data = []
    else { unexpectedRequests.push({ path, method }); return route.abort() }
    return route.fulfill({ json: data })
  })
  const goto = async name => { await page.goto(`${origin}/wealth-hub/${name}`) }
  const login = async portal => {
    await page.getByLabel('用户名', { exact: true }).fill('offline-fixture-only')
    await page.getByLabel('密码', { exact: true }).fill('fixture-password-not-real')
    await page.getByRole('button', { name: portal ? '进入站点目录' : '登录', exact: true }).click()
  }
  await page.setViewportSize({ width: 1440, height: 1000 })
  await goto('portal')
  await page.waitForURL('**/portal-login?redirect=**')
  check(await page.getByRole('button', { name: /立即注册/ }).count() === 0, 'portal exposed independent registration')
  offlineLogin = true
  await login(true)
  await page.getByRole('alert').waitFor()
  check((await page.getByRole('alert').innerText()).includes('手动重试'), 'network error lacks manual retry')
  check(requests.filter(r => r.path === '/auth/login').length === 1, 'offline login repeated')
  offlineLogin = false; holdLogin = true
  await page.getByLabel('密码', { exact: true }).press('Enter')
  await page.getByRole('button', { name: '正在验证账号…' }).waitFor()
  await page.keyboard.press('Enter')
  await page.waitForFunction(() => document.querySelector('form').getAttribute('aria-busy') === 'true')
  check(requests.filter(r => r.path === '/auth/login').length === 2, 'Enter submitted twice')
  holdLogin = false; releaseLogin()
  await page.waitForURL('**/wealth-hub/portal')
  await page.getByText('你好，隔离身份A').waitFor()
  const external = page.locator('.app-card[target="_blank"]')
  check(await external.count() === 2, 'independent sites lost')
  check((await external.first().getAttribute('aria-label')).includes('独立站点'), 'external destination unlabelled')
  for (const width of [1440, 1100, 1024, 760, 390]) {
    await page.setViewportSize({ width, height: 1000 })
    check(await page.locator('.portal-page').evaluate(n => n.scrollWidth <= n.clientWidth + 1), `portal overflow ${width}`)
    if (width === 390 || width === 1440) await page.screenshot({ path: `output/playwright/portalAuth-portal-${width}.png`, fullPage: true })
    await page.locator('.quick-card').last().focus(); await page.keyboard.press('Tab')
  }
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await page.waitForURL('**/portal-login?redirect=**')
  check(await page.evaluate(() => localStorage.getItem('token')) === null, 'offline logout retained JWT')
  for (const name of ['login', 'portal-login']) for (const width of [1440, 1100, 1024, 760, 390]) {
    await goto(name); await page.setViewportSize({ width, height: 1000 })
    check(await page.locator('.auth-page').evaluate(n => n.scrollWidth <= n.clientWidth + 1), `${name} overflow ${width}`)
    if (width === 390 || width === 1440) await page.screenshot({ path: `output/playwright/portalAuth-${name}-${width}.png`, fullPage: true })
    await page.getByLabel('用户名', { exact: true }).focus(); await page.keyboard.press('Tab')
    check(await page.getByLabel('密码', { exact: true }).evaluate(n => n === document.activeElement), 'credential labels/tab order failed')
  }
  await goto('login?redirect=%2F%2Foutside.invalid')
  await page.getByLabel('用户名', { exact: true }).fill('offline-registration-fixture')
  await page.getByLabel('密码', { exact: true }).fill('fixture-password-not-real')
  await page.getByRole('button', { name: /立即注册/ }).click()
  await page.waitForURL('**/wealth-hub/dashboard')
  check(requests.some(r => r.path === '/auth/register'), 'registration no longer uses server auth')
  await page.getByRole('button', { name: '安全退出', exact: true }).click()
  await page.waitForURL('**/wealth-hub/login')
  await goto('login?redirect=%2Fholdings%3FproductId%3D1')
  await login(false)
  await page.waitForURL('**/holdings?productId=1')
  await page.getByRole('button', { name: '查看隔离来源产品的持仓详情' }).waitFor()
  await page.reload()
  await page.getByRole('button', { name: '查看隔离来源产品的持仓详情' }).waitFor()
  const pages = ['dashboard', 'accounts', 'products', 'holdings', 'ledger', 'drafts', 'orders', 'settlements', 'goal-budget-center', 'strategy-lab', 'risk-center', 'rebalance-center', 'data-readiness', 'settings']
  for (const name of pages) {
    await page.setViewportSize({ width: 1440, height: 1000 })
    // Exercise the actual rail using keyboard activation for every route.
    const link = page.locator(`#desktop-navigation a[href="/wealth-hub/${name}"]`)
    await link.focus(); await page.keyboard.press('Enter')
    await page.waitForURL(`**/wealth-hub/${name}`)
    await page.locator('.wealth-content > *').first().waitFor()
    check(await link.getAttribute('aria-current') === 'page', `${name} ARIA active route lost`)
    for (const width of [1440, 1100, 1024, 760, 390]) {
      await page.setViewportSize({ width, height: 1000 })
      const overflow = await page.locator('.wealth-content').evaluate(n => ({ scroll: n.scrollWidth, client: n.clientWidth }))
      check(overflow.scroll <= overflow.client + 1, `${name} overflow ${width}: ${JSON.stringify(overflow)}`)
      check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `${name} document overflow ${width}`)
      await page.emulateMedia({ reducedMotion: 'reduce' })
      check(await link.evaluate(n => getComputedStyle(n).transitionDuration === '0s'), `${name} reduce-motion ignored`)
      matrix.push({ page: name, width, overflow: false, keyboard: true, aria: true, reducedMotion: true })
    }
    const menu = page.getByRole('button', { name: '目录', exact: true })
    await menu.click()
    const drawer = page.getByRole('dialog', { name: '财富中枢 · 目录' })
    await drawer.waitFor()
    for (let i = 0; i < 18; i++) {
      await page.keyboard.press('Tab')
      check(await drawer.evaluate(n => n.contains(document.activeElement)), `${name} drawer focus escaped`)
    }
    await page.keyboard.press('Escape'); await drawer.waitFor({ state: 'hidden' })
    check(await menu.evaluate(n => n === document.activeElement), `${name} drawer focus not restored`)
    if (await page.getByRole('button', { name: '记一笔', exact: true }).count()) {
      await page.getByRole('button', { name: '记一笔', exact: true }).click()
      const dialog = page.getByRole('dialog').filter({ visible: true })
      await dialog.waitFor()
      check(await dialog.evaluate(n => n.scrollWidth <= n.clientWidth + 1), `${name} entry dialog overflow`)
      for (let i = 0; i < 12; i++) {
        await page.keyboard.press('Tab')
        check(await dialog.evaluate(n => n.contains(document.activeElement)), `${name} dialog focus escaped`)
      }
      const dialogURL = page.url()
      await page.keyboard.press('Alt+8')
      check(page.url() === dialogURL, `${name} shortcut escaped dialog`)
      await page.keyboard.press('Escape'); await dialog.waitFor({ state: 'hidden' })
    }
  }
  await page.locator('#wealth-content').focus()
  await page.keyboard.press('Alt+8')
  await page.waitForURL('**/wealth-hub/accounts')
  const search = page.getByRole('textbox', { name: '搜索账户名称或代码' })
  await search.focus(); await page.keyboard.press('Alt+1')
  check(page.url().endsWith('/accounts'), 'shortcut interrupted text entry')
  // Both denied status codes must revoke the session at both login boundaries.
  for (const status of [401, 403]) for (const portal of [true, false]) {
    denial = status
    await goto(portal ? 'portal' : 'accounts')
    await page.waitForURL(portal ? '**/portal-login?**' : '**/login?**')
    await page.getByRole('status').filter({ hasText: '登录已失效' }).waitFor()
    check(await page.evaluate(() => localStorage.getItem('token')) === null, `status ${status} retained token`)
    denial = 0
    owner = owner === 'A' ? 'B' : 'A'
    await login(portal)
    await page.waitForURL(portal ? '**/wealth-hub/portal' : '**/wealth-hub/accounts')
    if (!portal) await page.getByRole('button', { name: `展开隔离账户${owner}` }).waitFor()
  }
  // Offline identity reads do not revoke an otherwise valid session.
  offlineIdentity = true
  await goto('portal')
  await page.getByRole('alert').waitFor()
  check(await page.evaluate(() => localStorage.getItem('token')) !== null, 'transport failure revoked JWT')
  offlineIdentity = false
  await page.getByRole('button', { name: '重试读取账号' }).click()
  await page.getByText(`你好，隔离身份${owner}`).waitFor()
  // Cross-tab identity changes remount page state and clear old account caches.
  holdAccounts = true
  const started = new Promise(resolve => { accountStarted = resolve })
  await goto('accounts')
  await started
  holdAccounts = false
  owner = owner === 'A' ? 'B' : 'A'
  await page.evaluate(token => {
    localStorage.setItem('token', token)
    window.dispatchEvent(new StorageEvent('storage', { key: 'token', newValue: token }))
  }, `offline-fixture-${owner}`)
  await page.waitForURL('**/wealth-hub/dashboard')
  await page.getByRole('link', { name: '账号设置' }).filter({ hasText: `隔离身份${owner}` }).waitFor()
  accountReleases.forEach(resolve => resolve())
  await goto('accounts')
  await page.getByRole('button', { name: `展开隔离账户${owner}` }).waitFor()
  check(!((await page.locator('.wealth-content').innerText()).includes(`隔离账户${owner === 'A' ? 'B' : 'A'}`)), 'old identity account displayed')
  check(pageerrors.length === 0, JSON.stringify(pageerrors))
  check(unexpectedRequests.length === 0, JSON.stringify(unexpectedRequests))
  check(financialWrites === 0 && requests.length > 0, 'isolation evidence missing')
  return { result: 'PASS', boundary: 'ALL_BUSINESS_REQUESTS_INTERCEPTED_NO_SERVER_OR_PRODUCTION_CONNECTION', pageerrors, unexpectedRequests, financialWrites, interceptedRequests: requests.length, widths: [1440, 1100, 760, 390], matrix }
}
