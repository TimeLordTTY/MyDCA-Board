async page => {
  const check = (ok, message) => { if (!ok) throw Error(message) }
  const errors = [], requests = []
  let fail = false, failNav = false
  page.on('pageerror', e => errors.push(e.message))
  const h = (id, extra = {}) => ({ productId: id, productName: `隔离持仓${id}`, productCode: `T${id}`, channel: id === 2 ? 'EXCHANGE' : 'OTC', assetType: id === 2 ? 'ETF' : 'FUND', brokerAccountId: 10, brokerAccountName: '隔离来源', totalShares: 10, totalCost: 100, avgCost: 10, marketValue: 90, unrealizedPnl: -10, ...extra })
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.hostname !== '127.0.0.1') return route.abort()
    if (url.pathname.includes('/api/')) {
      requests.push({ path: url.pathname, method: route.request().method() })
      if (route.request().method() !== 'GET') throw Error('Unexpected financial mutation')
      if (url.pathname.endsWith('/holdings') && fail) return route.fulfill({ status: 500, json: { message: 'fixture failure' } })
      if (url.pathname.endsWith('/nav/latest') && (failNav || url.searchParams.get('productId') === '2')) return route.fulfill({ status: failNav ? 500 : 404, json: {} })
      let data = []
      if (url.pathname.endsWith('/users/me')) data = { id: 1, username: 'offline-fixture', familyId: 1 }
      if (url.pathname.endsWith('/holdings')) data = [h(1), h(2, { marketValue: 0, unrealizedPnl: 0 }), h(3, { totalShares: 0, marketValue: 0, unrealizedPnl: 0 })]
      if (url.pathname.endsWith('/nav/latest')) data = { productId: Number(url.searchParams.get('productId')), nav: 9, navDate: '2026-10-10', source: 'fixture' }
      if (url.pathname.endsWith('/market/quotes')) data = [{ productId: 2, price: 12, quoteTime: '2026-10-10T12:00:00', source: 'fixture' }]
      if (url.pathname.endsWith('/products')) data = [1, 2, 3].map(id => ({ id, productCode: `T${id}`, productName: `隔离持仓${id}`, assetType: 'FUND', channel: 'OTC', market: 'CN', currency: 'CNY', isActive: true }))
      return route.fulfill({ json: data })
    }
    return route.continue()
  })
  await page.addInitScript(() => localStorage.setItem('token', 'offline-fixture-only'))
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('http://127.0.0.1:3110/wealth-hub/holdings')
  await page.getByRole('button', { name: '查看隔离持仓1的持仓详情' }).waitFor()
  check((await page.locator('.holdings-table').innerText()).includes('-10.00'), 'negative PNL missing')
  check((await page.locator('.holdings-table tbody tr').nth(1).innerText()).includes('未知'), 'missing NAV became zero')
  check((await page.locator('.holdings-table tbody tr').nth(1).innerText()).includes('12.0000'), 'quote not displayed separately from missing valuation')
  await page.getByText('显示已清仓', { exact: true }).click()
  check(await page.locator('.holdings-table tbody tr').count() === 3, 'zero shares lost')
  await page.getByRole('textbox', { name: '搜索持仓名称或代码' }).fill('T1')
  check(await page.locator('.holdings-table tbody tr').count() === 1, 'search did not filter')
  await page.getByRole('button', { name: '查看隔离持仓1的持仓详情' }).press('Enter')
  await page.getByRole('dialog').waitFor()
  check((await page.getByRole('dialog').innerText()).includes('暂未提供'), 'realized PNL unsupported not explained')
  await page.getByRole('tab', { name: '交易记录' }).click()
  await page.getByText('当前范围暂无详情证据；不表示行情或收益为零。').waitFor()
  await page.keyboard.press('Escape')
  await page.getByRole('dialog').waitFor({ state: 'hidden' })
  await page.getByRole('button', { name: '重置筛选' }).click()
  await page.screenshot({ path: 'output/playwright/holdings-light.png', fullPage: true })
  for (const width of [1100, 760]) {
    await page.setViewportSize({ width, height: 1000 })
    check(await page.locator('.management-page').evaluate(n => n.scrollWidth <= n.clientWidth + 1), `page overflow ${width}`)
  }
  await page.getByRole('button', { name: '查看隔离持仓1的产品档案' }).click()
  await page.getByRole('dialog', { name: '产品详情' }).waitFor()
  check((await page.getByRole('dialog').innerText()).includes('隔离持仓1'), 'wrong product archive')
  await page.goto('http://127.0.0.1:3110/wealth-hub/holdings?productId=1')
  await page.getByRole('button', { name: '查看隔离持仓1的持仓详情' }).waitFor()
  check(await page.locator('.holdings-table tbody tr').count() === 1, 'product deep link ignored')
  failNav = true; await page.locator('.management-hero').getByRole('button', { name: '重新读取', exact: true }).click()
  await page.getByText('部分报价或净值读取失败', { exact: false }).waitFor()
  check((await page.locator('.holdings-table').innerText()).includes('估值未知'), 'old NAV survived failed refresh')
  fail = true; await page.locator('.management-hero').getByRole('button', { name: '重新读取', exact: true }).click()
  await page.getByText('持仓读取失败', { exact: false }).waitFor()
  check(await page.locator('.holdings-table').count() === 0, 'stale holdings survived failure')
  fail = false; failNav = false; await page.getByRole('button', { name: '重试', exact: true }).click()
  await page.getByRole('button', { name: '查看隔离持仓1的持仓详情' }).waitFor()
  await page.evaluate(async () => { const { getAppearanceController } = await import('/wealth-hub/src/styles/appearance.ts'); getAppearanceController().set({ version: 1, mode: 'dark', palette: 'cream' }) })
  await page.screenshot({ path: 'output/playwright/holdings-dark-narrow.png', fullPage: true })
  check(errors.length === 0, errors.join('; '))
  check(requests.every(r => r.method === 'GET'), 'unexpected mutation')
  return { result: 'PASS', requests: requests.length, pageErrors: errors.length, widths: [1440, 1100, 760], financialWrites: 0 }
}
