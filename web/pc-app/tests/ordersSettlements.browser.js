async page => {
  const check = (value, reason) => { if (!value) throw Error(reason) }
  const errors = [], requests = []
  const unexpectedRequests = []
  const origin = 'http://127.0.0.1:3114'
  let failList = false, failConfirm = true
  const source = { id: 1, accountId: 11, lineType: 'SOURCE', amount: 100, currency: 'CNY' }, target = { id: 2, accountId: 21, lineType: 'TARGET', shares: 10, currency: 'CNY' }
  const row = (orderId, orderType, status = 'PENDING') => ({ id: 1, orderId, userId: 1, productId: 101, orderType, status, amount: ['BUY', 'SUBSCRIPTION'].includes(orderType) ? 100 : undefined, shares: ['SELL', 'REDEMPTION'].includes(orderType) ? 10 : undefined, expectedConfirmDate: '2026-10-11', expectedNavDate: '2026-10-10', requestedAt: '2026-10-10 12:00:00', feeEstimate: 0, fundingLines: [source, target] })
  const rows = [row('O1', 'BUY'), row('O2', 'REDEMPTION'), row('O3', 'SELL'), row('O4', 'SUBSCRIPTION', 'CANCELLED')]
  const audit = { orderId: 'H1', orderType: 'BUY', orderStatus: 'CONFIRMED', productId: 101, productName: '隔离审计产品', settlement: { confirmDate: '2026-10-11', navDate: '2026-10-10', confirmNav: 2, confirmShares: 50, confirmFee: 0, confirmedAt: '2026-10-11 12:00:00', previewDigest: 'fixture-display-only' }, fundingLines: [source, target], postings: [], reasons: ['历史关联缺失'], reconciliationStatus: 'WARNING' }
  page.on('pageerror', e => errors.push(e.message))
  await page.route('**/*', async route => {
    const url = new URL(route.request().url()), method = route.request().method()
    if (url.origin !== origin) { unexpectedRequests.push(route.request().url()); return route.abort() }
    if (!url.pathname.includes('/api/')) {
      if (method !== 'GET' || ['fetch', 'xhr'].includes(route.request().resourceType())) { unexpectedRequests.push(url.pathname); return route.abort() }
      return route.continue()
    }
    const read = method === 'GET' && /\/(users\/me|accounts|products|orders(?:\/O\d)?|settlements\/(pending|history(?:\/H1)?)|nav(?:\/.*)?)$/.test(url.pathname)
    const action = method === 'POST' && /\/(settlements\/preview|orders\/O\d\/settle)$/.test(url.pathname)
    if (!read && !action) { unexpectedRequests.push({ path: url.pathname, method }); return route.abort() }
    const body = route.request().postDataJSON()
    requests.push({ path: url.pathname, method, body })
    let data
    if (url.pathname.endsWith('/users/me')) data = { id: 1, username: '隔离验收', familyId: 1 }
    else if (url.pathname.endsWith('/accounts')) data = [{ id: 11, accountName: '隔离来源', accountType: 'BANK', accountKind: 'REAL', currency: 'CNY', isActive: true }, { id: 21, accountName: '隔离到账', accountType: 'BANK', accountKind: 'REAL', currency: 'CNY', isActive: true }]
    else if (url.pathname.endsWith('/products')) data = [{ id: 101, productName: '隔离真实产品', productCode: 'FIX101', assetType: 'FUND', channel: 'OTC', currency: 'CNY', isActive: true }]
    else if (url.pathname.endsWith('/orders')) data = rows
    else if (url.pathname.endsWith('/settlements/pending')) {
      if (failList) return route.fulfill({ status: 500, json: { message: '隔离服务器读取失败' } })
      data = rows.filter(r => r.status === 'PENDING')
    } else if (url.pathname.endsWith('/settlements/history')) data = [audit]
    else if (url.pathname.endsWith('/settlements/history/H1')) data = audit
    else if (url.pathname.endsWith('/settlements/preview')) {
      const order = rows.find(r => r.orderId === body.orderId)
      data = { ...body, orderType: order.orderType, orderStatus: order.status, productId: 101, productName: '隔离真实产品', confirmSupported: true, freshPreviewToken: 'fixture-backend-fresh', summaryLines: ['服务器现金 / 应收影响', '服务器持仓影响', '服务器手续费 0'], postingsPreview: [{ accountId: 11, accountName: '隔离来源', accountType: 'RECEIVABLE', postingType: 'CREDIT', amount: 100, currency: 'CNY' }, { accountId: 31, accountName: '隔离持仓', accountType: 'POSITION', postingType: 'DEBIT', amount: 100, shares: 50, currency: 'CNY' }], warnings: ['隔离预览警告'], blockingReasons: [], computedShares: 50, computedAmount: 100, confirmFee: 0 }
    } else if (/\/orders\/[^/]+\/settle$/.test(url.pathname)) {
      check(body.freshPreviewToken === 'fixture-backend-fresh', 'missing backend token')
      if (failConfirm) return route.fulfill({ status: 409, json: { message: '隔离账户快照变化，请重新预览' } })
      rows.find(r => r.orderId === body.orderId).status = 'CONFIRMED'; data = {}
    } else if (/\/orders\/[^/]+$/.test(url.pathname)) data = rows.find(r => r.orderId === url.pathname.split('/').at(-1))
    else if (url.pathname.includes('/nav')) data = { nav: 2, navDate: '2026-10-10', productId: 101 }
    else return route.fulfill({ status: 400, json: { message: 'fixture 拒绝未声明业务接口' } })
    return route.fulfill({ json: data })
  })
  await page.addInitScript(() => localStorage.setItem('token', 'offline-fixture-only'))
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('http://127.0.0.1:3114/wealth-hub/orders')
  await page.getByRole('heading', { name: '每笔订单，都有来去。' }).waitFor()
  await page.locator('.trade-table tbody tr').first().waitFor()
  check(await page.locator('.trade-table tbody tr').count() === 4, 'order list missing')
  await page.getByRole('button', { name: '待结算', exact: true }).click()
  check(await page.locator('.trade-table tbody tr').count() === 3, 'status filter failed')
  await page.locator('.trade-table tbody tr').first().getByRole('button', { name: '详情', exact: true }).click()
  const detail = page.getByRole('dialog', { name: '订单详情 · 资金路径', exact: true })
  await detail.getByRole('heading', { name: '来源 → 到账', exact: true }).waitFor()
  check((await detail.innerText()).includes('不重复扣下单现金'), 'buy semantic copy wrong')
  await detail.getByRole('button', { name: '编辑结算输入与预览', exact: true }).click()
  const modal = page.getByRole('dialog', { name: '人工结算 · 逐笔复核', exact: true })
  await modal.getByRole('button', { name: '读取该日净值', exact: true }).waitFor()
  const confirm = modal.getByRole('button', { name: '核对后确认结算', exact: true })
  check(await confirm.isDisabled(), 'confirm enabled without preview')
  await modal.getByRole('button', { name: '读取该日净值', exact: true }).click()
  await modal.getByRole('button', { name: '生成只读预览', exact: true }).click()
  await modal.getByRole('heading', { name: '如果确认，会发生什么？' }).waitFor()
  check(await confirm.isEnabled(), 'valid preview disabled')
  await page.screenshot({ path: 'output/playwright/orders-preview.png', fullPage: true })
  await modal.getByRole('textbox').last().fill('隔离备注编辑')
  check(await confirm.isDisabled(), 'edit did not invalidate token')
  await modal.getByRole('button', { name: '生成只读预览', exact: true }).click()
  await confirm.click()
  const second = page.locator('.el-message-box')
  await second.getByRole('button', { name: '再检查一下', exact: true }).click()
  check(requests.filter(r => r.path.endsWith('/settle')).length === 0, 'cancelled second confirmation wrote')
  await modal.getByRole('button', { name: '生成只读预览', exact: true }).click()
  await confirm.click()
  await second.getByRole('button', { name: '我已核对，确认结算', exact: true }).click()
  await modal.getByRole('alert').filter({ hasText: '隔离账户快照变化' }).waitFor().catch(async () => { await modal.locator('.trade-error').filter({ hasText: '隔离账户快照变化' }).waitFor() })
  check(await confirm.isDisabled(), '409 left old preview')
  failConfirm = false
  await modal.getByRole('button', { name: '生成只读预览', exact: true }).click()
  await confirm.click(); await second.getByRole('button', { name: '我已核对，确认结算', exact: true }).click()
  await modal.waitFor({ state: 'hidden' })
  check(rows[0].status === 'CONFIRMED', 'manual success missing')
  await page.goto('http://127.0.0.1:3114/wealth-hub/settlements')
  await page.getByRole('heading', { name: '先看影响，再落下确认。' }).waitFor()
  await page.locator('.settlement-ticket').first().waitFor()
  check(await page.locator('.settlement-ticket').count() === 2, 'pending list not refreshed')
  for (const width of [1440, 1100, 760]) {
    await page.setViewportSize({ width, height: 1000 })
    await page.screenshot({ path: 'output/playwright/settlements-' + width + '.png', fullPage: true })
    check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), 'page overflow ' + width)
  }
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.locator('.settlement-ticket').first().getByRole('button', { name: '打开结算复核 →', exact: true }).click()
  await modal.getByRole('button', { name: '读取该日净值', exact: true }).waitFor()
  check((await modal.innerText()).includes('结算后才减少持仓'), 'redeem semantic wrong')
  await modal.getByRole('button', { name: '读取该日净值', exact: true }).click()
  await modal.getByRole('button', { name: '生成只读预览', exact: true }).click()
  await modal.locator('.trade-error').filter({ hasText: '须填写正数实际确认金额' }).waitFor()
  check(await confirm.isDisabled(), 'missing redeem amount allowed')
  await modal.getByRole('button', { name: '关闭', exact: true }).click()
  await page.getByRole('button', { name: '查看审计', exact: true }).click()
  const drawer = page.getByRole('dialog', { name: '结算审计 · 只读', exact: true })
  await drawer.getByRole('heading', { name: '隔离审计产品', exact: true }).waitFor()
  check((await drawer.innerText()).includes('历史关联缺失'), 'warning audit lost')
  check((await drawer.innerText()).includes('不能用于结算确认'), 'digest boundary lost')
  await drawer.getByRole('button', { name: 'Close this dialog', exact: true }).click()
  await page.evaluate(() => document.documentElement.setAttribute('data-wh-theme', 'dark'))
  await page.screenshot({ path: 'output/playwright/settlements-dark.png', fullPage: true })
  failList = true
  await page.getByRole('button', { name: '刷新待结算', exact: true }).click()
  await page.locator('.trade-state').filter({ hasText: '隔离服务器读取失败' }).waitFor()
  check(await page.locator('.settlement-ticket').count() === 0, 'error retained actionable rows')
  failList = false
  await page.getByRole('button', { name: '重试读取', exact: true }).click()
  await page.locator('.settlement-ticket').first().waitFor()
  check(errors.length === 0, 'pageerrors: ' + errors.join(';'))
  check(unexpectedRequests.length === 0 && requests.length > 0, JSON.stringify(unexpectedRequests))
  return { result: 'PASS', pageerrors: errors, unexpectedRequests, financialWrites: 0, interceptedRequests: requests.length, previews: requests.filter(r => r.path.endsWith('/preview')).length, confirms: requests.filter(r => r.path.endsWith('/settle')).length, boundary: 'ALL_BUSINESS_REQUESTS_INTERCEPTED_NO_SERVER_OR_PRODUCTION_CONNECTION', screenshots: 'output/playwright/orders-preview.png + settlements-{1440,1100,760,dark}.png' }
}
