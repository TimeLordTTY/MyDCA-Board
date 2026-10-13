async page => {
  const check = (ok, message) => { if (!ok) throw Error(message) }
  const errors = [], requests = []
  const unexpectedRequests = []
  const origin = 'http://127.0.0.1:3110'
  let failList = false, empty = false
  page.on('pageerror', e => errors.push(e.message))
  const account = (id, name, parentAccountId) => ({ id, accountName: name, parentAccountId, accountKind: 'REAL', accountType: 'BANK', currency: 'CNY', balance: 1000, isActive: true })
  const rows = [
    { id: 1, txnId: 'T1', userId: 1, txnType: 'EXPENSE', categoryId: 999999, status: 'CONFIRMED', requestedAt: '2026-10-11 10:00:00', summaryAmount: 12.34, leafAccountName: '隔离生活账户', leafAccountBalance: 987.66, parentAccountBalance: 987.66, note: '隔离咖啡' },
    { id: 2, txnId: 'T2', userId: 1, txnType: 'INCOME', status: 'CONFIRMED', orderId: 'O1', requestedAt: '2026-10-11 11:00:00', summaryAmount: 0, leafAccountName: '隔离生活账户', note: '隔离只读历史' },
  ]
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.origin !== origin) { unexpectedRequests.push(route.request().url()); return route.abort() }
    if (!url.pathname.includes('/api/')) {
      if (route.request().method() !== 'GET' || ['fetch', 'xhr'].includes(route.request().resourceType())) { unexpectedRequests.push(url.pathname); return route.abort() }
      return route.continue()
    }
    requests.push({ path: url.pathname, method: route.request().method(), query: url.search, body: route.request().postDataJSON() })
    if (route.request().method() === 'POST' && url.pathname.endsWith('/ledger/txns')) return route.fulfill({ status: 500, json: { message: '隔离 fixture：提交结果未知' } })
    if (route.request().method() !== 'GET' || !/\/(users\/me|accounts|products|holdings|ledger\/txns(?:\/T\d)?|ledger\/stats\/(summary|trend|breakdown|top))$/.test(url.pathname)) { unexpectedRequests.push({ path: url.pathname, method: route.request().method() }); return route.abort() }
    let data = []
    if (url.pathname.endsWith('/users/me')) data = { id: 1, username: '隔离验收', familyId: 1 }
    if (url.pathname.endsWith('/accounts')) data = [account(10, '隔离银行'), account(11, '隔离生活账户', 10)]
    if (url.pathname.endsWith('/ledger/txns')) {
      if (failList) return route.fulfill({ status: 500, json: { message: '隔离列表失败' } })
      data = { list: empty ? [] : rows, total: empty ? 0 : 42, page: Number(url.searchParams.get('page') || 1), pageSize: 20, totalPages: 3 }
    }
    if (/\/ledger\/txns\/T\d$/.test(url.pathname)) data = { ...rows[0], postings: [{ id: 1, accountId: 11, postingType: 'CREDIT', accountType: 'CASH', amount: 12.34, currency: 'CNY' }] }
    if (url.pathname.endsWith('/stats/summary')) data = { totalIncome: 0, totalExpense: 12.34, netCashflow: -12.34, investmentInflow: 0, investmentOutflow: 0, avgDailyExpense: 1.23, txnCount: 1 }
    return route.fulfill({ json: data })
  })
  await page.addInitScript(() => localStorage.setItem('token', 'offline-fixture-only'))
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('http://127.0.0.1:3110/wealth-hub/ledger')
  await page.getByText('隔离咖啡', { exact: true }).waitFor()
  check((await page.locator('.ledger-table').innerText()).includes('未知分类（999999）'), 'unknown category missing')
  check((await page.locator('.ledger-table tbody tr').nth(1).innerText()).includes('0.00'), 'zero amount lost')
  await page.getByPlaceholder('搜索备注，按 Enter 查询').fill('咖啡')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await page.getByText('隔离咖啡', { exact: true }).waitFor()
  check(requests.some(r => r.query.includes('note=') && r.query.includes('page=1')), 'server search contract missing')
  await page.locator('.ledger-table tbody tr').first().getByRole('button', { name: '详情', exact: true }).click()
  await page.getByRole('dialog', { name: '流水详情' }).getByText(/交易ID.*T1/).waitFor()
  await page.keyboard.press('Escape')
  await page.getByRole('dialog', { name: '流水详情' }).waitFor({ state: 'hidden' })
  for (const width of [1440, 1100, 760]) {
    await page.setViewportSize({ width, height: 1000 })
    await page.screenshot({ path: `output/playwright/ledger-${width}.png`, fullPage: true })
    check(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), `page overflow at ${width}`)
  }
  failList = true
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await page.getByText(/隔离列表失败/).waitFor()
  check(await page.getByText('隔离咖啡', { exact: true }).count() === 0, 'stale records visible')
  failList = false; empty = true
  await page.getByRole('button', { name: '重试查询', exact: true }).click()
  await page.getByText(/当前查询没有流水记录/).waitFor()
  await page.getByRole('button', { name: '＋ 记一笔', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '记一笔', exact: true })
  await dialog.locator('.entry-type').first().click()
  await dialog.getByRole('button', { name: '核对这一笔 →' }).waitFor()
  await dialog.locator('.el-form-item').filter({ hasText: '金额（元）' }).getByRole('spinbutton').fill('12.34')
  await dialog.locator('.el-form-item').filter({ hasText: '分类' }).locator('.el-cascader').click()
  await page.getByRole('menuitem', { name: '其他', exact: true }).click()
  await dialog.locator('.el-form-item').filter({ hasText: '账户', exact: true }).locator('.el-cascader').click()
  await page.getByRole('menuitem', { name: /隔离银行/ }).click()
  await page.getByRole('menuitem', { name: /隔离生活账户/ }).click()
  check(requests.every(r => r.method === 'GET'), 'write before confirmation')
  check(await dialog.getByRole('button', { name: '核对这一笔 →' }).isEnabled(), 'options not loaded')
  await dialog.getByRole('button', { name: '核对这一笔 →' }).click()
  await dialog.getByText('核对后，再确认。', { exact: true }).waitFor()
  check((await dialog.innerText()).includes('12.34'), 'review missing entered amount')
  check(!(await dialog.innerText()).includes('转入价格'), 'irrelevant fields in review')
  check(requests.every(r => r.method === 'GET'), 'review performed write')
  await page.screenshot({ path: 'output/playwright/ledger-entry-review.png', fullPage: true })
  await dialog.getByRole('button', { name: '确认提交', exact: true }).click()
  await dialog.getByText(/提交结果尚未确认/).waitFor()
  check(await dialog.getByRole('button', { name: '确认提交', exact: true }).isDisabled(), 'uncertain write can be retried')
  check(requests.filter(r => r.method !== 'GET').length === 1, 'write count not one')
  check(requests.find(r => r.method === 'POST').body.postings[0].amount === 12.34, 'yuan amount changed')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  check(errors.length === 0, errors.join('\n'))
  check(requests.filter(r => r.method !== 'GET').every(r => r.path.endsWith('/ledger/txns') && r.method === 'POST'), 'unexpected write endpoint')
  check(unexpectedRequests.length === 0 && requests.length > 0, JSON.stringify(unexpectedRequests))
  return { result: 'PASS', boundary: 'ALL_BUSINESS_REQUESTS_INTERCEPTED_NO_SERVER_OR_PRODUCTION_CONNECTION', widths: [1440, 1100, 760], pageerrors: errors, unexpectedRequests, interceptedRequests: requests.length, financialWrites: 0, fixtureWrites: 1 }
}
