async page => {
  const check = (value, reason) => { if (!value) throw Error(reason) }
  const origin = 'http://127.0.0.1:3115', requests = [], unexpected = [], errors = []
  let failData = false, failHistory = false, failExport = false, failPlan = false
  const params = { contribution: 100, interval_days: 30 }
  const run = (id, status = 'SUCCESS') => ({ historyRunId: id, ownerUserId: 1, ownerFamilyId: 1, researchPlanId: 'p1', strategy: 'pure_sip', strategyVersion: '1', dataset: 'fixture.csv', datasetHash: 'fixture-source-hash', canonicalParams: JSON.stringify(params), paramsHash: 'fixture-params-hash', engineVersion: 'fixture-engine', startedAt: '2026-10-01T10:00:00', finishedAt: '2026-10-01T10:01:00', status, cacheHit: false, failureCode: status === 'SUCCESS' ? null : 'ENGINE_TIMEOUT', metrics: null, result: null })
  const history = [run('r1'), run('r2'), run('r3', 'TIMEOUT'), run('r4', 'INVALID')]
  let plan = { id: 'p1', name: '隔离研究方案', description: '仅供界面验收', status: 'DRAFT', strategy: 'pure_sip', strategyVersion: '1', sourceCandidateId: 'fixture-candidate', sourceRunIds: ['r1', 'r2'], datasetHashes: ['fixture-source-hash'], canonicalParamsSnapshot: params, paramsDraft: params, evidenceSnapshot: { source: 'fixture-only', warnings: ['样本证据有限'] }, evidenceBundleRef: 'fixture-source-ref', warnings: ['来源为创建时快照，不代表当前数据'], createdAt: '2026-10-01', updatedAt: '2026-10-12' }
  const compareRun = id => ({ run_id: id, strategy: 'pure_sip', strategy_version: '1', engine_version: 'fixture-engine', dataset_hash: 'fixture-source-hash', canonical_params: params, data_range: { start: '2025-01-01', end: '2025-12-31' }, metrics: { total_return: 0, annualized_return: null, max_drawdown: null }, baseline_delta: {} })
  const candidate = { candidate_id: 'fixture-candidate', strategy: 'pure_sip', strategy_version: '1', canonical_params: params, run_ids: ['r1', 'r2'], dataset_hashes: ['fixture-source-hash'], evidence_status: 'INSUFFICIENT', status: 'EVIDENCE_INSUFFICIENT', reasons: ['样本证据不足'], warnings: ['不可预测未来收益'], evidence: [{ run_id: 'r1', data_range: { start: '2025-01-01', end: '2025-12-31' }, sample_days: 365, metrics: { annualized_return: null }, baseline_delta: {} }] }
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url()), method = request.method()
    if (url.origin !== origin) { unexpected.push(url.origin); return route.abort() }
    if (!url.pathname.includes('/api/')) { if (method !== 'GET') unexpected.push({ path: url.pathname, method }); return method === 'GET' ? route.continue() : route.abort() }
    const path = url.pathname.replace('/api/v2', ''), body = request.postDataJSON()
    requests.push({ path, method, body })
    let data
    if (method === 'GET' && path === '/users/me') data = { id: 1, username: '隔离验收', familyId: 1 }
    else if (method === 'GET' && path === '/backtest-lab/datasets') {
      if (failData) return route.fulfill({ status: 503, json: { message: 'fixture data root unavailable' } })
      data = ['fixture.csv']
    } else if (method === 'GET' && path === '/backtest-lab/recent') data = []
    else if (method === 'GET' && path === '/backtest-lab/runs') {
      if (failHistory) return route.fulfill({ status: 503, json: { message: 'fixture history root unavailable' } })
      data = history
    } else if (method === 'GET' && path === '/research-plans') data = [plan]
    else if (method === 'GET' && path === '/research-plans/p1') {
      if (failPlan) return route.fulfill({ status: 409, json: { message: '方案已变更' } })
      data = plan
    } else if (method === 'PATCH' && path === '/research-plans/p1') { plan = { ...plan, ...body }; data = plan }
    else if (method === 'POST' && path === '/research-plans') { plan = { ...plan, name: body.name, description: body.description, status: 'DRAFT' }; data = plan }
    else if (method === 'POST' && path === '/research-plans/p1/runs') {
      check(body.dataset === 'fixture.csv', 'dataset outside whitelist')
      history.unshift(run('new-plan-run')); data = { history_run_id: 'new-plan-run' }
    } else if (method === 'POST' && path === '/backtest-lab/runs') {
      check(body.data === 'fixture.csv' && body.strategy === 'pure_sip' && body.version === '1', 'run not whitelist')
      history.unshift(run('new-run'))
      data = { run_id: 'new-run', strategy: { name: 'pure_sip', version: '1', params: body.params }, data_range: { start: '2025-01-01', end: '2025-12-31', rows: 365 }, metrics: { total_return: 0, annualized_return: null } }
    } else if (method === 'POST' && path === '/backtest-lab/runs/compare') data = { schema_version: '1', generated_at: '2026-10-12T10:00:00', disclaimer: '历史回测不代表未来表现', warnings: ['fixture 质量提示'], runs: body.runIds.map(compareRun), markdown: 'fixture 原始日期、参数、数据 hash 与质量提示' }
    else if (method === 'POST' && path === '/backtest-lab/runs/research') data = { schema_version: '1', disclaimer: '仅为历史证据', thresholds: {}, excluded_runs: [], candidates: [candidate] }
    else if (method === 'POST' && path === '/backtest-lab/runs/evidence') {
      if (failExport) return route.fulfill({ status: 503, json: { message: 'fixture evidence failed' } })
      return route.fulfill({ contentType: 'application/zip', body: 'fixture original evidence bytes, not a real report' })
    } else { unexpected.push({ path, method }); return route.abort() }
    return route.fulfill({ json: data })
  })
  await page.addInitScript(() => localStorage.setItem('token', 'offline-fixture-only'))
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto(`${origin}/wealth-hub/strategy-lab`)
  await page.locator('.workbench nav button').first().waitFor()
  await page.getByRole('button', { name: '刷新方案与状态', exact: true }).click()
  await page.locator('.workbench nav button').first().click()
  const bench = page.locator('.workbench')
  await bench.getByText('来源为创建时快照，不代表当前数据', { exact: true }).waitFor()
  await bench.getByLabel('名称', { exact: true }).fill('改名研究方案')
  await bench.getByLabel('回测历史数据').selectOption('fixture.csv')
  check(await bench.getByRole('button', { name: /发起受控回测/ }).isDisabled(), 'unsaved run enabled')
  await bench.getByRole('button', { name: '保存编辑', exact: true }).click()
  await bench.getByRole('button', { name: /发起受控回测/ }).click()
  await bench.getByText(/成功。new-plan-run/).waitFor()
  failPlan = true
  await bench.getByRole('button', { name: '刷新方案与状态', exact: true }).click()
  await bench.getByText(/方案已变更，请刷新后重试/).waitFor()
  check(await bench.locator('article').count() === 0, 'conflicted plan still editable')
  failPlan = false
  await bench.getByRole('button', { name: '刷新方案与状态', exact: true }).click()
  await bench.locator('nav button').first().click()
  await bench.getByRole('button', { name: '归档方案', exact: true }).click()
  await bench.getByRole('button', { name: '取消', exact: true }).click()
  check(plan.status === 'DRAFT', 'cancel archived')
  await bench.getByRole('button', { name: '归档方案', exact: true }).click()
  await bench.getByRole('button', { name: '确认归档', exact: true }).click()
  check(await bench.getByRole('button', { name: /发起受控回测/ }).isDisabled(), 'archived run enabled')
  const form = page.locator('.run-form')
  await form.getByRole('button', { name: '运行只读回测', exact: true }).click()
  await page.locator('.result').waitFor()
  check((await page.locator('.result').innerText()).includes('0.00%'), 'historical zero lost')
  check((await page.locator('.result').innerText()).includes('—'), 'unknown made zero')
  const rows = page.locator('.history-row')
  await rows.first().getByRole('checkbox').check(); await rows.nth(1).getByRole('checkbox').check()
  check(await rows.filter({ hasText: '超时' }).getByRole('checkbox').isDisabled(), 'timeout selectable')
  check(await rows.filter({ hasText: '无效' }).getByRole('checkbox').isDisabled(), 'invalid selectable')
  await page.getByRole('button', { name: '比较 2 条并生成报告', exact: true }).click()
  await page.locator('.compare-scroll table').waitFor()
  await page.getByText('fixture 质量提示', { exact: false }).waitFor()
  for (const format of ['JSON', 'Markdown']) {
    const downloading = page.waitForEvent('download')
    await page.getByRole('button', { name: `导出 ${format}`, exact: true }).click()
    check((await downloading).suggestedFilename().startsWith('backtest-compare-2026-10-12'), 'report date lost')
  }
  failExport = true
  await page.getByRole('button', { name: '导出研究证据', exact: true }).click()
  await page.getByText('研究证据导出失败，请检查所选回测和网络后重试。', { exact: true }).waitFor()
  failExport = false
  const downloading = page.waitForEvent('download')
  await page.getByRole('button', { name: '导出研究证据', exact: true }).click()
  check((await downloading).suggestedFilename() === 'backtest-research-evidence.zip', 'evidence filename changed')
  await page.getByRole('button', { name: '生成研究候选', exact: true }).click()
  await page.locator('.candidate').waitFor()
  await page.getByRole('button', { name: '继续研究 · 加入研究方案', exact: true }).click()
  await bench.getByLabel('方案名称', { exact: true }).fill('从证据继续研究')
  await bench.getByRole('button', { name: '创建研究方案', exact: true }).click()
  await bench.getByRole('heading', { name: '从证据继续研究 · 草稿', exact: true }).waitFor()
  for (const width of [1440, 1100, 760, 390]) {
    await page.setViewportSize({ width, height: 1000 })
    check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `page overflow ${width}`)
    await page.screenshot({ path: `output/playwright/strategy-${width}.png`, fullPage: true })
  }
  await page.getByRole('link', { name: '04 研究候选', exact: true }).focus()
  await page.keyboard.press('Enter')
  check(await page.evaluate(() => location.hash === '#candidate-title'), 'keyboard anchor failed')
  await page.evaluate(() => document.documentElement.setAttribute('data-wh-theme', 'dark'))
  await page.screenshot({ path: 'output/playwright/strategy-dark.png', fullPage: true })
  failHistory = true
  await page.getByRole('button', { name: '刷新历史状态', exact: true }).click()
  await page.getByText(/回测历史加载失败；/).waitFor()
  check(await rows.count() === 0, 'stale history shown after refresh failure')
  failHistory = false
  await page.getByRole('button', { name: '重试读取历史', exact: true }).click()
  await rows.first().waitFor()
  failData = true
  await page.getByRole('button', { name: '刷新数据与最近结果', exact: true }).click()
  await page.getByText(/回测数据加载失败；/).waitFor()
  check(await form.getByRole('button').isDisabled(), 'failed data allows run')
  failData = false
  await page.getByRole('button', { name: '刷新数据与最近结果', exact: true }).click()
  check(await page.getByRole('button', { name: '记一笔', exact: true }).count() === 0, 'financial entry on lab page')
  check(errors.length === 0, errors.join(';')); check(unexpected.length === 0, JSON.stringify(unexpected))
  return { result: 'PASS', boundary: 'ALL_BUSINESS_REQUESTS_INTERCEPTED_NO_SERVER_OR_PRODUCTION_CONNECTION', widths: [1440, 1100, 760, 390], dark: true, pageerrors: errors, unexpectedRequests: unexpected, interceptedRequests: requests.length, financialWrites: 0 }
}
