async page => {
  const check = (value, reason) => { if (!value) throw Error(reason) }
  const requests = [], errors = [], unexpectedRequests = [], blockedExternalRequests = []
  const today = new Date().toISOString().slice(0, 10), month = today.slice(0, 7)
  const goal = (id, currency = 'CNY') => ({ id, createdAt: today, config: { name: `旅行基金 ${id}`, targetValue: '10000', targetDate: '2027-10-01', scope: 'PERSONAL', currency, measure: 'CASH', state: 'ACTIVE', note: '隔离验收规划' } })
  const goals = [goal('g'), goal('g2'), goal('usd', 'USD')]
  const budget = { id: 'b', createdAt: today, config: { name: '生活留白', month, scope: 'PERSONAL', currency: 'CNY', items: [{ name: '生活', kind: 'FLEXIBLE_EXPENSE', categoryId: 1, planned: '500' }] } }
  const comparison = { budgetId: 'b', quality: 'UNKNOWN', plannedIncome: 1000, plannedExpenses: 500, plannedReserve: 100, plannedSurplus: 400, actualIncome: null, actualExpenses: null, actualSurplus: null, remainingBudget: null, overspent: null, unmatchedPostings: 0, items: [{ item: budget.config.items[0], quality: 'UNKNOWN', actual: null, knownActual: 0, remaining: null, overspent: null }], warnings: ['没有流水，实际支出未知'] }
  const progress = id => ({ goalId: id, quality: 'OK', currentValue: 0, knownValue: 0, completionRate: 0, completed: false, asOfDate: today, daysRemaining: 365, overdue: false, reason: '真实零 fixture' })
  const projection = (rate, quality = 'OK') => ({ annualRate: rate, assumption: '后台 fixture 数学假设，非真实余额', quality, outcome: 'REACHED', achievedMonth: month, months: [{ month, quality, modeledIncome: 1000, modeledExpenses: 500, plannedReserve: 100, surplusUpperBound: 400, contribution: 100, mathematicalReturn: rate ? 3 : 0, cumulativeProgress: 100, remainingGap: 9900, reason: '独立只读结果' }] })
  const forecast = (id, body) => ({ goalId: id, currency: 'CNY', asOfDate: today, actualProgress: progress(id), fixedInputs: body, baseline: projection(0), mathematicalScenario: body.annualRate == null ? null : projection(Number(body.annualRate)) })
  page.on('pageerror', e => errors.push(e.message))
  await page.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url()), method = request.method()
    if (url.origin !== 'http://127.0.0.1:3115') { blockedExternalRequests.push(url.origin); return route.abort() }
    if (!url.pathname.includes('/api/')) { if (method !== 'GET' || ['fetch', 'xhr'].includes(request.resourceType())) { unexpectedRequests.push({ path: url.pathname, method }); return route.abort() }; return route.continue() }
    if (method !== 'GET' && !(method === 'POST' && /\/(forecast|compare)$/.test(url.pathname))) { unexpectedRequests.push({ path: url.pathname, method }); return route.abort() }
    const body = request.postDataJSON(); requests.push({ path: url.pathname, method, body })
    let data
    if (url.pathname.endsWith('/users/me')) data = { id: 1, username: '隔离验收', familyId: 1 }
    else if (url.pathname.endsWith('/goals/scenarios/compare')) data = { source: '只读 fixture', readStartedAt: today, readCompletedAt: today, scenarios: body.scenarios.map(s => ({ name: s.name, assumptions: s, warnings: ['目标起始资产不可合并'], months: [{ month, sharedUpperBound: 100, specifiedTotal: 200, overLimit: true }], goals: s.allocations.map(a => ({ goalId: a.goalId, targetDate: '2027-10-01', deadlineStatus: 'UNKNOWN_OR_NOT_REACHED', forecast: { ...forecast(a.goalId, s.cashflow), baseline: projection(0, 'UNKNOWN') } })) })) }
    else if (/\/goals\/[^/]+\/forecast$/.test(url.pathname)) data = forecast(url.pathname.split('/').at(-2), body)
    else if (/\/goals\/[^/]+\/progress$/.test(url.pathname)) data = progress(url.pathname.split('/').at(-2))
    else if (url.pathname.endsWith('/goals')) data = goals
    else if (/\/goals\/[^/]+$/.test(url.pathname)) data = goals.find(g => g.id === url.pathname.split('/').at(-1))
    else if (/\/monthly-budgets\/[^/]+\/comparison$/.test(url.pathname)) data = comparison
    else if (url.pathname.endsWith('/monthly-budgets')) data = [budget]
    else if (/\/monthly-budgets\/[^/]+$/.test(url.pathname)) data = budget
    else { unexpectedRequests.push({ path: url.pathname, method }); return route.abort() }
    await route.fulfill({ json: data })
  })
  await page.addInitScript(() => localStorage.setItem('token', 'offline-fixture-only'))
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('http://127.0.0.1:3115/wealth-hub/goal-budget-center')
  await page.getByRole('heading', { name: '给未来，留一份计划。' }).waitFor()
  await page.getByRole('link', { name: '目标与预算', exact: true }).click()
  await page.getByRole('button', { name: '刷新本页', exact: true }).click()
  await page.locator('.workspace aside article').first().waitFor()
  await page.locator('.workspace aside article').first().getByRole('button', { name: '详情与进度', exact: true }).click()
  await page.getByRole('heading', { name: '完成率 0.00%' }).waitFor()
  check((await page.locator('.workspace main').innerText()).includes('实际资产读数（CNY）0.00'), 'zero lost')
  await page.getByRole('button', { name: '月度预算', exact: true }).click()
  await page.locator('.workspace aside article').first().getByRole('button', { name: '详情与对比', exact: true }).click()
  await page.locator('.workspace main').getByRole('heading', { name: /月度计划 \/ 实际/ }).waitFor()
  check((await page.locator('.workspace main').innerText()).includes('没有流水，实际支出未知'), 'unknown represented as zero')
  await page.getByRole('button', { name: '复制为下月预算', exact: true }).last().click()
  await page.locator('.workspace main').getByRole('heading', { name: '新增规划', exact: true }).waitFor()
  check((await page.locator('.workspace main').innerText()).includes('不修改旧预算'), 'copy boundary missing')
  await page.locator('.workspace main').getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('button', { name: '展开回顾', exact: true }).click()
  await page.getByRole('button', { name: '加载回顾', exact: true }).click()
  await page.locator('.budget-review table').waitFor()
  const desk = page.locator('.forecast')
  await desk.getByRole('button', { name: '重新读取目标与预算', exact: true }).click()
  await desk.getByRole('radio').first().check()
  await desk.getByLabel('年化投资收益假设（比例，可选）').first().fill('0.03')
  await desk.getByRole('button', { name: '手动计算两套情景', exact: true }).click()
  await desk.getByRole('heading', { name: /用户数学收益假设/ }).waitFor()
  check(await desk.locator('.projection').count() === 3, 'baseline or optional projection lost')
  await desk.getByLabel('年化投资收益假设（比例，可选）').first().fill('0.04')
  check(await desk.locator('.scenario-results').count() === 0, 'input failed to clear old result')
  await desk.getByLabel('目标模式').selectOption('true')
  await desk.getByRole('checkbox').nth(0).check(); await desk.getByRole('checkbox').nth(1).check()
  await desk.getByRole('button', { name: '手动计算两套情景', exact: true }).click()
  await desk.getByText(/目标之间争用/).first().waitFor()
  await page.screenshot({ path: 'output/playwright/goals-scenarios.png', fullPage: true })
  for (const width of [1440, 1100, 760, 390]) {
    await page.setViewportSize({ width, height: 1000 })
    check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `overflow ${width}`)
    await page.getByRole('heading', { name: '给未来，留一份计划。' }).scrollIntoViewIfNeeded()
    await page.screenshot({ path: `output/playwright/goals-${width}.png`, fullPage: true })
  }
  await page.evaluate(() => document.documentElement.setAttribute('data-wh-theme', 'dark'))
  await page.screenshot({ path: 'output/playwright/goals-dark.png', fullPage: true })
  const financial = requests.filter(r => !['GET'].includes(r.method) && !/\/(forecast|compare)$/.test(r.path))
  check(financial.length === 0, 'unexpected write'); check(unexpectedRequests.length === 0, JSON.stringify(unexpectedRequests)); check(errors.length === 0, errors.join(';'))
  return { result: 'PASS', boundary: 'ALL_BUSINESS_REQUESTS_INTERCEPTED_NO_SERVER_OR_PRODUCTION_CONNECTION', interceptedRequests: requests.length, unexpectedRequests, blockedExternalRequests, requests: requests.length, readonlyScenarioPosts: requests.filter(r => r.method === 'POST').length, financialWrites: financial.length, pageerrors: errors, widths: [1440, 1100, 760, 390] }
}
