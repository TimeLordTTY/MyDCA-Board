async page => {
  const check = (ok, message) => { if (!ok) throw Error(message) }
  const requests = []
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  const account = (id, extra = {}) => ({ id, accountCode: `fixture-${id}`, accountName: `测试账户${id}`, accountKind: 'REAL', accountType: 'BANK', currency: 'CNY', balance: 0, reservedAmount: 0, isActive: true, ...extra })
  const product = (id, extra = {}) => ({ id, productCode: `fixture-${id}`, productName: `隔离产品${id}`, assetType: 'ETF', channel: 'EXCHANGE', market: 'CN', currency: 'CNY', isActive: true, buyFeeRate: 0, buyConfirmOffset: 1, sellConfirmOffset: 1, ...extra })
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.hostname !== '127.0.0.1') return route.abort()
    if (url.pathname.includes('/api/')) {
      requests.push({ path: url.pathname, method: route.request().method() })
      if (route.request().method() !== 'GET') return route.fulfill({ json: {} })
      let data = []
      if (url.pathname.endsWith('/users/me')) data = { id: 1, username: 'offline-fixture', familyId: 1 }
      if (url.pathname.endsWith('/accounts')) data = [account(1, { accountName: '隔离银行' }), account(2, { parentAccountId: 1, accountName: '旅行信封', balance: -12 }), account(3, { parentAccountId: 1, accountName: '日常信封' })]
      if (url.pathname.endsWith('/products')) data = [product(1), product(2), product(3, { channel: 'OTC', assetType: 'MMF' })]
      return route.fulfill({ json: data })
    }
    return route.continue()
  })
  await page.addInitScript(() => localStorage.setItem('token', 'offline-fixture-only'))
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('http://127.0.0.1:3110/wealth-hub/accounts')
  await page.getByRole('button', { name: '展开隔离银行' }).waitFor()
  await page.getByRole('button', { name: '展开隔离银行' }).press('Enter')
  await page.getByText('旅行信封', { exact: false }).first().waitFor()
  await page.getByRole('textbox', { name: '搜索账户名称或代码' }).fill('旅行')
  check(await page.getByText('日常信封', { exact: false }).count() > 0, 'search removed sibling')
  check((await page.locator('.account-tree-container').innerText()).includes('-12.00'), 'negative balance lost')
  await page.locator('.bucket-actions').first().getByRole('button', { name: '详情', exact: true }).click()
  await page.getByRole('dialog', { name: '账户详情' }).waitFor()
  await page.keyboard.press('Escape')
  await page.getByRole('dialog', { name: '账户详情' }).waitFor({ state: 'hidden' })
  await page.screenshot({ path: 'output/playwright/accounts-light.png', fullPage: true })
  await page.goto('http://127.0.0.1:3110/wealth-hub/products')
  await page.getByRole('button', { name: '查看隔离产品1持仓' }).waitFor()
  await page.getByRole('button', { name: '上移隔离产品2' }).press('Enter')
  check(requests.some(r => r.path.endsWith('/products/sort-order') && r.method === 'POST'), 'keyboard sort did not use shared API')
  await page.locator('.management-columns').getByRole('button', { name: '详情', exact: true }).first().click()
  await page.getByRole('dialog', { name: '产品详情' }).waitFor()
  check((await page.getByRole('dialog').innerText()).includes('未知'), 'unknown dates/source omitted')
  await page.keyboard.press('Escape')
  await page.getByRole('dialog', { name: '产品详情' }).waitFor({ state: 'hidden' })
  await page.screenshot({ path: 'output/playwright/products-light.png', fullPage: true })
  for (const width of [1100, 760]) {
    await page.setViewportSize({ width, height: 1000 })
    check(await page.locator('.management-page').evaluate(n => n.scrollWidth <= n.clientWidth + 1), `page overflows at ${width}`)
  }
  const beforeTheme = requests.length
  await page.evaluate(async () => {
    const { getAppearanceController } = await import('/wealth-hub/src/styles/appearance.ts')
    getAppearanceController().set({ version: 1, mode: 'dark', palette: 'cream' })
  })
  await page.screenshot({ path: 'output/playwright/products-dark-narrow.png', fullPage: true })
  check(errors.length === 0, errors.join('; '))
  check(requests.length === beforeTheme, 'appearance caused a business request')
  check(requests.every(r => r.method === 'GET' || r.path.endsWith('/products/sort-order')), 'unexpected mutation')
  console.log(JSON.stringify({ result: 'PASS', requests: requests.length, pageErrors: errors.length, widths: [1440, 1100, 760], mockedWrites: 1 }))
}
