async page => {
  const check = (ok, message) => { if (!ok) throw Error(message) }
  const origin = 'http://127.0.0.1:3115', requests = [], unexpectedRequests = [], pageerrors = []
  let failRisk = false, denyFamily = false, unknownPreview = false, brokenScenario = false, financialWrites = 0
  const today = new Date().toLocaleDateString('en-CA')
  let risk = { id: 'risk1', config: { scope: 'PERSONAL', type: 'RETURN', productId: 1, assetType: null, threshold: 0, target: null, direction: 'BELOW', severity: 'CRITICAL', note: '隔离观察规则', muted: false } }
  const snap = (extra = {}) => ({ id: 's1', ruleId: 'risk1', sourceDataTimestamp: today, sourceDataHash: 'fixture-source-hash', status: 'OK', matched: true, severity: 'CRITICAL', reason: '已知零收益观察', observedValue: 0, threshold: 0, createdAt: today, disclaimer: '历史观察证据不是交易建议', ...extra })
  let events = ['OPEN', 'ACKNOWLEDGED', 'MUTED', 'RESOLVED'].map((state, i) => ({ fingerprint: `fp${i}`, evidence: snap({ id: `s${i}`, reason: `fixture-${state}`, ...(state === 'MUTED' ? { status: 'UNKNOWN', observedValue: null } : {}), ...(state === 'RESOLVED' ? { status: 'STALE' } : {}) }), state, visible: true, acknowledgedAt: state === 'ACKNOWLEDGED' ? today : null, resolvedAt: state === 'RESOLVED' ? today : null, mutedUntil: state === 'MUTED' ? '2099-01-01' : null }))
  let allocation = { id: 'a1', config: { scope: 'PERSONAL', productId: 1, assetType: null, target: 0.5, lowerBound: 0.4, upperBound: 0.6, returnThreshold: 0.1, takeProfitThresholds: [0.1, 0.2], enabled: true, note: '隔离配置规则' } }
  const preview = () => ({ policyId: 'a1', status: unknownPreview ? 'UNKNOWN' : 'BELOW_BAND', description: 'fixture 固定总资产情景', totalAssets: unknownPreview ? null : 1000, currentWeight: unknownPreview ? null : 0.3, targetWeight: 0.5, lowerBound: 0.4, upperBound: 0.6, deviationPercentagePoints: unknownPreview ? null : -20, targetScenario: unknownPreview ? null : { selectedAdjustment: 200, remainderAdjustment: brokenScenario ? -100 : -200 }, bandScenario: unknownPreview ? null : { selectedAdjustment: 100, remainderAdjustment: -100 }, dataDate: '2020-01-01', prices: [{ productId: 1, status: unknownPreview ? 'MISSING' : 'STALE', priceDate: null, valuationDate: '2020-01-01', priceSource: 'fixture-only' }], warnings: [{ code: 'FEES', status: 'NOT_MODELED', message: '税费未建模' }] })
  page.on('pageerror', e => pageerrors.push(e.message))
  await page.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url()), method = request.method()
    if (url.origin !== origin) { unexpectedRequests.push({ origin: url.origin, method }); return route.abort() }
    if (!url.pathname.includes('/api/')) {
      if (method !== 'GET' || !['document', 'script', 'stylesheet', 'image', 'font', 'other'].includes(request.resourceType())) { unexpectedRequests.push({ path: url.pathname, method }); return route.abort() }
      return route.continue()
    }
    const path = url.pathname.replace('/api/v2', ''), body = request.postDataJSON()
    requests.push({ path, method, body })
    let data
    if (method === 'GET' && path === '/users/me') data = { id: 1, username: 'offline-fixture-only', familyId: 1 }
    else if (path.startsWith('/risk-watch-rules')) {
      if (failRisk) return route.fulfill({ status: 503, json: { message: 'fixture unavailable' } })
      if (method === 'GET' && path === '/risk-watch-rules') data = [risk]
      else if (method === 'GET' && path === '/risk-watch-rules/risk1/snapshots') data = [snap()]
      else if (method === 'GET' && path === '/risk-watch-rules/risk1/events') data = events
      else if (method === 'POST' && path === '/risk-watch-rules/risk1/evaluate') data = snap()
      else if (method === 'PATCH' && path === '/risk-watch-rules/risk1') { risk = { ...risk, config: body }; data = risk }
      else if (method === 'PATCH' && path === '/risk-watch-rules/risk1/events/fp0/acknowledge') { events[0] = { ...events[0], state: 'ACKNOWLEDGED', acknowledgedAt: today }; data = null }
      else if (method === 'PATCH' && path === '/risk-watch-rules/risk1/mute') { events[0].mutedUntil = body.mutedUntil; data = null }
      else if (method === 'POST' && path === '/risk-watch-rules' && body.scope === 'FAMILY' && denyFamily) return route.fulfill({ status: 403, json: { message: 'fixture family denied' } })
      else { unexpectedRequests.push({ path, method }); return route.abort() }
    } else if (path.startsWith('/allocation-policies')) {
      if (method === 'GET' && path === '/allocation-policies') data = [allocation]
      else if (method === 'POST' && path === '/allocation-policies/a1/evaluate') data = { policyId: 'a1', status: 'UNKNOWN', reason: 'fixture 收益输入缺口', allocation: 0, returnRate: null, reachedTakeProfitThresholds: [], evaluatedAt: today, disclaimer: '仅观察' }
      else if (method === 'GET' && path === '/allocation-policies/a1/preview') data = preview()
      else if (method === 'PATCH' && path === '/allocation-policies/a1') { allocation = { ...allocation, config: body }; data = allocation }
      else if (method === 'POST' && path === '/allocation-policies' && body.scope === 'FAMILY' && denyFamily) return route.fulfill({ status: 403, json: { message: 'fixture family denied' } })
      else { unexpectedRequests.push({ path, method }); return route.abort() }
    } else if (method === 'GET' && path === '/holdings') data = [{ productId: 1, productName: '隔离来源产品', productCode: 'FIX1', channel: 'OTC', assetType: 'FUND', brokerAccountId: 10, brokerAccountName: '隔离来源账户', totalShares: 10, totalCost: 100, avgCost: 10, marketValue: 100, unrealizedPnl: 0 }]
    else if (method === 'GET' && path === '/nav/latest') data = { productId: 1, nav: 10, navDate: today, source: 'fixture' }
    else if (method === 'GET' && path === '/market/quotes') data = []
    else {
      if (method !== 'GET') financialWrites++
      unexpectedRequests.push({ path, method }); return route.abort()
    }
    return route.fulfill({ json: data })
  })
  await page.addInitScript(() => {
    if (location.pathname.endsWith('/login')) localStorage.removeItem('token')
    else localStorage.setItem('token', 'offline-fixture-only')
  })
  const goto = async name => { await page.goto(`${origin}/wealth-hub/${name}`); await page.locator(`.${name === 'risk-center' ? 'risk-center' : 'allocation-center'} aside article`).first().waitFor() }
  await page.setViewportSize({ width: 1440, height: 1000 })
  await goto('risk-center')
  const riskPage = page.locator('.risk-center')
  check(!requests.some(r => r.path.endsWith('/evaluate')), 'evaluation ran automatically')
  check((await riskPage.locator('.cards').innerText()).includes('0 / 0'), 'known zero lost')
  check((await riskPage.innerText()).includes('UNKNOWN / 数据未知'), 'unknown history lost')
  check((await riskPage.innerText()).includes('陈旧快照'), 'stale history lost')
  check((await riskPage.innerText()).includes('fixture-source-hash'), 'source fingerprint lost')
  const filters = riskPage.locator('.filters')
  for (const [value, text] of [['OPEN', 'fixture-OPEN'], ['ACKNOWLEDGED', 'fixture-ACKNOWLEDGED'], ['MUTED', 'fixture-MUTED'], ['RESOLVED', 'fixture-RESOLVED']]) {
    await filters.getByRole('combobox', { name: '状态', exact: true }).selectOption(value)
    const rows = riskPage.locator('.evidence-workspace > article').filter({ hasText: 'fixture-' })
    check(await rows.count() === 1 && (await rows.innerText()).includes(text), `history filter ${value}`)
  }
  await filters.getByRole('combobox', { name: '状态', exact: true }).selectOption('OPEN')
  await riskPage.getByRole('button', { name: '标为已读', exact: true }).click()
  await filters.getByRole('combobox', { name: '状态', exact: true }).selectOption('ACKNOWLEDGED')
  await riskPage.getByText('已读：' + today, { exact: false }).first().waitFor()
  await riskPage.getByRole('button', { name: '评估当前数据', exact: true }).click()
  const mute = riskPage.locator('form').filter({ hasText: '规则静默窗口' })
  await mute.getByRole('combobox', { name: '规则', exact: true }).selectOption('risk1')
  await mute.getByRole('button', { name: '保存静默窗口' }).click()
  failRisk = true
  await riskPage.getByRole('button', { name: '刷新列表与历史' }).click()
  await riskPage.getByRole('alert').waitFor()
  check(await riskPage.locator('.cards article').count() === 0, 'failed read kept snapshots')
  failRisk = false
  await riskPage.getByRole('button', { name: '重试读取', exact: true }).click()
  await riskPage.locator('aside article').waitFor()
  await riskPage.getByRole('link', { name: '查看来源持仓', exact: false }).click()
  await page.getByRole('button', { name: '查看隔离来源产品的持仓详情' }).waitFor()
  check(new URL(page.url()).searchParams.get('productId') === '1', 'source product navigation lost')
  await goto('rebalance-center')
  const allocationPage = page.locator('.allocation-center')
  check(!requests.some(r => r.path.endsWith('/preview')), 'preview ran automatically')
  await allocationPage.getByRole('button', { name: '观察当前数据', exact: true }).click()
  await allocationPage.getByText('fixture 收益输入缺口', { exact: true }).waitFor()
  check((await allocationPage.locator('.evidence-workspace > article').first().innerText()).includes('0.00%'), 'allocation zero lost')
  check((await allocationPage.locator('.evidence-workspace > article').first().innerText()).includes('UNKNOWN / 未知'), 'return gap became zero')
  await allocationPage.getByRole('button', { name: '查看假设调整' }).click()
  await allocationPage.getByText('fixture 固定总资产情景', { exact: true }).waitFor()
  check((await allocationPage.locator('table').innerText()).includes('50.00%'), 'target math missing')
  check((await allocationPage.locator('.scenario').innerText()).includes('NOT_MODELED'), 'unmodeled warnings lost')
  check((await allocationPage.locator('.scenario').innerText()).includes('陈旧'), 'bad price warning lost')
  brokenScenario = true
  await allocationPage.getByRole('button', { name: '查看假设调整' }).click()
  await allocationPage.getByText('数据不足或金额不守恒 / 不展示结果', { exact: true }).waitFor()
  unknownPreview = true
  await allocationPage.getByRole('button', { name: '查看假设调整' }).click()
  await allocationPage.getByText('UNKNOWN / 行情或组合数据不足', { exact: true }).waitFor()
  check(!(await allocationPage.locator('table').innerText()).includes('¥0.00'), 'unknown amount became zero')
  await allocationPage.getByRole('button', { name: '编辑', exact: true }).click()
  const form = allocationPage.locator('form')
  await form.getByLabel('下限', { exact: true }).fill('0.8')
  const before = requests.length
  await form.getByRole('button', { name: '保存配置' }).click()
  await form.getByRole('alert').waitFor()
  check(requests.length === before, 'invalid band sent to API')
  await form.getByLabel('下限', { exact: true }).fill('0.4')
  await form.getByRole('button', { name: '保存配置' }).click()
  await form.waitFor({ state: 'hidden' })
  for (const name of ['risk-center', 'rebalance-center']) {
    await goto(name)
    const section = page.locator(name === 'risk-center' ? '.risk-center' : '.allocation-center')
    check(await page.getByRole('button', { name: '记一笔', exact: true }).count() === 0, 'financial shortcut present')
    for (const width of [1440, 1100, 760, 390]) {
      await page.setViewportSize({ width, height: 1000 })
      check(await section.evaluate(n => n.scrollWidth <= n.clientWidth + 1), `${name} overflow ${width}`)
      if (width === 1440) await page.screenshot({ path: `output/playwright/${name}-light.png`, fullPage: true })
    }
    await section.locator('.section-nav a').last().focus(); await page.keyboard.press('Enter')
    check(new URL(page.url()).hash.endsWith('-source'), 'keyboard chapter navigation failed')
    await page.evaluate(() => document.documentElement.setAttribute('data-wh-theme', 'dark'))
    check(await section.evaluate(n => getComputedStyle(n).color !== 'rgb(25, 59, 73)'), 'hardcoded legacy color')
    await page.screenshot({ path: `output/playwright/${name}-dark-narrow.png`, fullPage: true })
    denyFamily = true
    await section.getByRole('button', { name: '新建规则', exact: true }).click()
    const editor = section.locator('form').first()
    await editor.getByRole('combobox', { name: '作用域', exact: true }).selectOption('FAMILY')
    if (name === 'risk-center') { await editor.getByRole('combobox', { name: '类型', exact: true }).selectOption('NOTE') }
    else { await editor.getByLabel('资产类别', { exact: true }).fill('FUND') }
    await editor.getByRole('button', { name: name === 'risk-center' ? '保存规则' : '保存配置', exact: true }).click()
    await page.waitForURL('**/wealth-hub/login')
    check((await requests.at(-1)).body.scope === 'FAMILY', 'family scope replaced')
    denyFamily = false
  }
  check(pageerrors.length === 0, pageerrors.join(';'))
  check(unexpectedRequests.length === 0, JSON.stringify(unexpectedRequests))
  check(financialWrites === 0 && requests.length > 0, 'isolation evidence missing')
  return { result: 'PASS', boundary: 'ALL_BUSINESS_REQUESTS_INTERCEPTED_NO_SERVER_OR_PRODUCTION_CONNECTION', pageerrors, unexpectedRequests, financialWrites, interceptedRequests: requests.length, widths: [1440, 1100, 760, 390], dark: true }
}
