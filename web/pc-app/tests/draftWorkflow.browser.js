async page => {
  const check = (condition, reason) => { if (!condition) throw Error(reason) }
  const errors = [], requests = []
  let failList = false, failPreview = false
  const account = (id, name, extra = {}) => ({ id, accountName: name, accountType: 'BANK', accountKind: 'REAL', fundUsage: 'SPENDABLE', currency: 'CNY', isActive: true, ...extra })
  const accounts = [account(10, '隔离银行'), account(11, '隔离生活账户', { parentAccountId: 10 }), account(21, '隔离到账账户'), account(31, '隔离持仓来源', { fundUsage: 'INVESTABLE' })]
  const products = [{ id: 101, productName: '隔离真实产品', productCode: 'MOCK1', assetType: 'FUND', currency: 'CNY', isActive: true }]
  const row = (id, type, status = 'DRAFT') => ({ id, ownerUserId: 1, ownerFamilyId: 1, sourceType: id === 3 ? 'ocr' : 'HERMES_TEXT', sourceRef: `fixture-${id}`, status, rawInput: `隔离记录${id}`, updatedAt: '2026-10-12 10:00:00', missingFieldsJson: id === 1 ? '["accountId"]' : '[]', parsedPayloadJson: JSON.stringify({ txnType: type, amount: 12.34, accountId: id === 1 ? null : 11, targetAccountId: 21, productId: 101, shares: 3.5, sourceAccountId: 31, note: '隔离复核' }), confirmOrderId: status === 'CONFIRMED' ? 'O1' : null, ignoreReason: status === 'IGNORED' ? '隔离重复' : null })
  let rows = [row(1, 'EXPENSE'), row(2, 'TRANSFER'), row(3, 'SELL'), row(4, 'BUY', 'CONFIRMED'), row(5, 'INCOME', 'IGNORED')]
  page.on('pageerror', e => errors.push(e.message))
  await page.route('**/*', async route => {
    const url = new URL(route.request().url()), method = route.request().method()
    if (url.hostname !== '127.0.0.1') return route.abort()
    if (!url.pathname.includes('/api/')) return route.continue()
    const body = route.request().postDataJSON()
    requests.push({ path: url.pathname, method, body, query: url.search })
    let data = []
    if (url.pathname.endsWith('/users/me')) data = { id: 1, username: '隔离验收', familyId: 1 }
    else if (url.pathname.endsWith('/accounts')) data = accounts
    else if (url.pathname.endsWith('/products')) data = products
    else if (url.pathname.includes('/holdings/product/')) data = [{ accountId: 31, accountName: '隔离持仓来源', shares: 10, marketValue: 100 }]
    else if (url.pathname.endsWith('/drafts')) {
      if (failList) return route.fulfill({ status: 500, json: { message: '隔离读取失败' } })
      const status = url.searchParams.get('status')
      data = rows.filter(r => !status || r.status === status)
    } else if (url.pathname.endsWith('/parse-text')) data = { sourceType: 'HERMES_TEXT', sourceRef: body.sourceRef, rawInput: body.text, txnType: 'INCOME', amount: 88, accountNameHint: '隔离生活账户', missingFields: ['accountId'], confidence: .8 }
    else if (url.pathname.endsWith('/draft-from-intent')) {
      const created = { ...row(6, 'INCOME'), sourceRef: body.intent.sourceRef, rawInput: body.intent.rawInput }
      rows.push(created); data = { draft: created, intent: body.intent }
    } else {
      const match = url.pathname.match(/\/drafts\/(\d+)(?:\/(\w+))?$/)
      if (!match) return route.fulfill({ status: 400, json: { message: 'fixture 拒绝未声明接口' } })
      const id = Number(match[1]), action = match[2], current = rows.find(r => r.id === id)
      if (!current) return route.fulfill({ status: 404, json: { message: 'fixture 草稿不存在' } })
      if (action === 'preview') {
        if (failPreview) return route.fulfill({ status: 409, json: { message: '隔离预览已失效，旧预览必须清除' } })
        const payload = JSON.parse(current.parsedPayloadJson)
        data = { draftId: id, ...payload, accountId: payload.txnType === 'SELL' ? 31 : payload.accountId, accountName: payload.txnType === 'SELL' ? '隔离持仓来源' : '隔离生活账户', targetAccountName: '隔离到账账户', productName: '隔离真实产品', productCurrency: 'CNY', confirmSupported: Boolean(payload.accountId), availableShares: 8, remainingShares: 4.5, accountDelta: -12.34, targetAccountDelta: 12.34, willCreateLedgerTxn: payload.txnType !== 'SELL', willCreateOrder: payload.txnType === 'SELL', willCreateSettlement: false, willAffectHolding: false, message: '隔离服务器影响预览', warnings: [], missingFields: payload.accountId ? [] : ['accountId'] }
      } else if (action === 'confirm') {
        current.status = 'CONFIRMED'; current.confirmTxnId = 'T-fixture'; data = current
      } else if (action === 'history') data = [{ id: 1, eventType: 'create', createdAt: '2026-10-12 10:00:00', statusAfter: current.status, summary: '隔离生命周期' }]
      else if (method === 'PUT') { Object.assign(current, body); data = current }
      else data = current
    }
    return route.fulfill({ json: data })
  })
  await page.addInitScript(() => localStorage.setItem('token', 'offline-fixture-only'))
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('http://127.0.0.1:3113/wealth-hub/drafts')
  await page.getByRole('heading', { name: '先留草稿，再认真确认。' }).waitFor()
  await page.locator('.draft-item').first().waitFor()
  check(await page.locator('.draft-item').count() === 3, 'server DRAFT filter lost')
  for (const width of [1440, 1100, 760]) {
    await page.setViewportSize({ width, height: 1000 })
    await page.screenshot({ path: `output/playwright/draft-${width}.png`, fullPage: true })
    check(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), `overflow ${width}`)
  }
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.getByPlaceholder('原文、来源标识或草稿编号').fill('fixture-3')
  check(await page.locator('.draft-item').count() === 1, 'local source search lost')
  await page.getByPlaceholder('原文、来源标识或草稿编号').fill('')
  await page.locator('.draft-item').first().getByRole('button', { name: '补齐字段', exact: true }).click()
  const edit = page.getByRole('dialog', { name: '补齐草稿字段', exact: true }), drawer = page.getByRole('dialog', { name: '逐笔复核', exact: true })
  await edit.getByRole('button', { name: '保存草稿', exact: true }).waitFor()
  await edit.getByPlaceholder('请输入正数金额').fill('0')
  await edit.getByRole('button', { name: '保存草稿', exact: true }).click()
  await edit.locator('.draft-error').filter({ hasText: '金额必须是大于 0 的数字。' }).waitFor()
  check(requests.every(r => r.method !== 'PUT'), 'invalid amount was saved')
  await edit.getByPlaceholder('请输入正数金额').fill('12.34')
  await edit.locator('.form-field').filter({ hasText: '现金/活钱账户' }).locator('.el-select').click()
  await page.getByRole('option', { name: /隔离生活账户/ }).click()
  await edit.getByRole('button', { name: '保存并预览', exact: true }).click()
  await edit.waitFor({ state: 'hidden' })
  await drawer.getByText('可进入二次确认', { exact: true }).waitFor()
  check(requests.filter(r => r.path.endsWith('/confirm')).length === 0, 'save/preview caused confirm')
  await drawer.getByRole('button', { name: '确认记账', exact: true }).click()
  const confirm = page.getByRole('dialog', { name: '确认正式记账', exact: true })
  await confirm.waitFor()
  check(requests.filter(r => r.path.endsWith('/1/preview')).length === 2, 'second confirm has no fresh preview')
  await confirm.getByRole('button', { name: '取消', exact: true }).click()
  check(requests.filter(r => r.path.endsWith('/confirm')).length === 0, 'cancelled confirmation wrote')
  check(await drawer.getByRole('button', { name: '确认记账', exact: true }).isDisabled(), 'cancel retained old preview')
  await drawer.getByRole('button', { name: '生成预览', exact: true }).click()
  await drawer.getByText('可进入二次确认', { exact: true }).waitFor()
  failPreview = true
  await drawer.getByRole('button', { name: '生成预览', exact: true }).click()
  await drawer.getByText(/隔离预览已失效/).waitFor()
  check(await drawer.getByRole('button', { name: '确认记账', exact: true }).isDisabled(), 'failed preview retained eligibility')
  failPreview = false
  await drawer.getByRole('button', { name: '生成预览', exact: true }).click()
  await drawer.getByText('可进入二次确认', { exact: true }).waitFor()
  await drawer.getByRole('button', { name: '确认记账', exact: true }).click()
  await confirm.getByRole('button', { name: '确认记账', exact: true }).click()
  await drawer.getByText('关联流水：T-fixture', { exact: true }).waitFor()
  check(requests.filter(r => r.path.endsWith('/confirm')).length === 1, 'confirmed more than once')
  check(await drawer.getByRole('button', { name: '确认记账', exact: true }).isDisabled(), 'final state reconfirmable')
  await page.screenshot({ path: 'output/playwright/draft-confirmed.png', fullPage: true })
  await page.keyboard.press('Escape'); await drawer.waitFor({ state: 'hidden' })
  await page.getByRole('button', { name: '已忽略', exact: true }).click()
  await page.locator('.draft-item').getByRole('button', { name: '查看记录 →', exact: true }).click()
  await drawer.getByText('忽略原因：隔离重复', { exact: true }).waitFor()
  check(await drawer.getByRole('button', { name: '生成预览', exact: true }).isDisabled(), 'ignored preview enabled')
  await page.keyboard.press('Escape'); await drawer.waitFor({ state: 'hidden' })
  await page.getByRole('button', { name: '待复核', exact: true }).click()
  await page.locator('.draft-item').filter({ hasText: '隔离记录3' }).getByRole('button', { name: '复核这笔 →', exact: true }).click()
  await drawer.getByRole('button', { name: '生成预览', exact: true }).click()
  await drawer.getByText('8 份 / 3.5 份 / 4.5 份', { exact: true }).waitFor()
  await page.screenshot({ path: 'output/playwright/draft-sell-review.png', fullPage: true })
  await page.evaluate(() => document.documentElement.setAttribute('data-wh-theme', 'dark'))
  await page.waitForFunction(() => getComputedStyle(document.querySelector('.el-drawer')).color === 'rgb(255, 254, 250)' && getComputedStyle(document.querySelector('.el-drawer')).backgroundColor === 'rgb(41, 44, 39)')
  await page.screenshot({ path: 'output/playwright/draft-dark-review.png', fullPage: true })
  await page.evaluate(() => document.documentElement.setAttribute('data-wh-theme', 'light'))
  await page.keyboard.press('Escape'); await drawer.waitFor({ state: 'hidden' })
  failList = true
  await page.getByRole('button', { name: '刷新草稿', exact: true }).click()
  await page.getByText('隔离读取失败', { exact: true }).waitFor()
  check(await page.locator('.draft-item').count() === 0, 'list failure shows stale records')
  failList = false
  await page.getByRole('button', { name: '重试读取', exact: true }).click()
  await page.locator('.draft-item').first().waitFor()
  await page.getByRole('button', { name: '＋ 文本录入', exact: true }).click()
  const entry = page.getByRole('dialog', { name: '文本录入 · 只生成草稿', exact: true })
  await entry.getByRole('textbox').fill('隔离收入88')
  await entry.getByRole('button', { name: '解析文本', exact: true }).click()
  await entry.getByText('解析结果', { exact: true }).waitFor()
  await entry.getByRole('button', { name: '生成草稿', exact: true }).click()
  await entry.waitFor({ state: 'hidden' }); await drawer.getByText('草稿 #6', { exact: true }).waitFor()
  check(requests.filter(r => r.path.endsWith('/confirm')).length === 1, 'text creation confirmed automatically')
  check(errors.length === 0, errors.join('\n'))
  check(requests.filter(r => r.method !== 'GET').every(r => /\/drafts\/\d+\/(preview|confirm)$/.test(r.path) || /\/drafts\/\d+$/.test(r.path) || /\/(parse-text|draft-from-intent)$/.test(r.path)), 'unexpected write endpoint')
  return { result: 'PASS', widths: [1440, 1100, 760], pageerrors: errors, requests: requests.length, fixtureConfirmWrites: 1, fixtureDraftSaves: requests.filter(r => r.method === 'PUT').length }
}
