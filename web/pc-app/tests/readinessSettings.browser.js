async page => {
  const check = (ok, message) => { if (!ok) throw Error(message) }
  const origin = 'http://127.0.0.1:3115', requests = [], unexpectedRequests = [], pageerrors = []
  let mode = 'normal', role = 'ADMIN', family = true, financialWrites = 0, passwordFailure = false, membersFailure = false
  const user = () => ({ id: 1, username: 'offline-fixture', nickname: '隔离用户', email: 'fixture@example.invalid', phone: '000', familyId: family ? 2 : undefined })
  page.on('pageerror', e => pageerrors.push(e.message))
  await page.route('**/*', async route => {
    const req = route.request(), url = new URL(req.url()), method = req.method()
    if (url.origin !== origin) { unexpectedRequests.push({ origin: url.origin, method }); return route.abort() }
    if (!url.pathname.startsWith('/api/')) {
      if (method !== 'GET' || !['document', 'script', 'stylesheet', 'image', 'font', 'other'].includes(req.resourceType())) { unexpectedRequests.push({ path: url.pathname, method }); return route.abort() }
      return route.continue()
    }
    const path = url.pathname.replace('/api/v2', ''), body = req.postDataJSON()
    requests.push({ path, method, body })
    let data
    if (path === '/users/me' && ['GET', 'PUT'].includes(method)) data = user()
    else if (path === '/users/change-password' && method === 'POST') {
      if (passwordFailure) return route.fulfill({ status: 422, json: { message: 'fixture rejected' } })
      data = { ok: true }
    } else if (path === '/families/members' && method === 'GET') {
      if (membersFailure) return route.fulfill({ status: 503, json: { message: 'fixture unavailable' } })
      data = [{ userId: 1, username: 'offline-fixture', role }, { userId: 2, username: 'fixture-member', role: 'MEMBER' }]
    }
    else if (path === '/families/members/2' && method === 'DELETE') data = { ok: true }
    else if (path === '/families/members/2/role' && method === 'PUT') data = { ok: true }
    else if (path === '/families/members' && method === 'POST') data = { ok: true }
    else if (path === '/data-readiness' && method === 'GET') {
      if (mode === 'failure') return route.fulfill({ status: 503, json: { message: 'fixture unavailable' } })
      if (mode === 'denied') return route.fulfill({ status: 403, json: { message: 'fixture denied' } })
      data = { scope: url.searchParams.get('scope'), month: url.searchParams.get('month'), checkedAt: '2026-10-12T10:00:00', evidence: mode === 'empty' ? [] : ['ASSETS', 'MARKET', 'GOALS', 'BUDGETS'].map((area, i) => ({ area, state: ['READY', 'PARTIAL', 'UNKNOWN', 'UNAVAILABLE'][i], reason: `fixture-${area}`, source: '隔离证据来源', dataTime: area === 'BUDGETS' ? '2026-10' : '2020-01-01', nextStep: '人工核对来源' })) }
    } else { if (method !== 'GET') financialWrites++; unexpectedRequests.push({ path, method }); return route.abort() }
    return route.fulfill({ json: data })
  })
  await page.addInitScript(() => {
    if (location.pathname.endsWith('/login')) localStorage.removeItem('token')
    else localStorage.setItem('token', 'offline-fixture-only')
  })
  const goto = async name => { await page.goto(`${origin}/wealth-hub/${name}`) }
  await page.setViewportSize({ width: 1440, height: 1000 })
  await goto('data-readiness')
  const readiness = page.locator('.readiness')
  await readiness.getByText('fixture-ASSETS', { exact: true }).first().waitFor()
  check((await readiness.locator('.quality-grid').innerText()).includes('PARTIAL'), 'partial omitted')
  check((await readiness.locator('.quality-grid').innerText()).includes('UNKNOWN'), 'unknown omitted')
  await readiness.locator('#evidence-MARKET summary').click()
  check((await readiness.locator('#evidence-MARKET').innerText()).includes('超过3个日历日'), 'stale omitted')
  mode = 'failure'
  await readiness.getByRole('button', { name: '刷新只读诊断' }).click()
  await readiness.getByRole('alert').waitFor()
  check(!(await readiness.locator('.quality-grid').innerText()).includes('fixture-ASSETS'), 'stale evidence after failure')
  mode = 'empty'
  await readiness.getByRole('button', { name: '重试读取', exact: true }).click()
  await readiness.getByRole('alert').waitFor({ state: 'hidden' })
  check((await readiness.locator('.quality-grid').innerText()).includes('尚无可用诊断证据'), 'missing evidence not neutral')
  for (const scope of ['PERSONAL', 'FAMILY']) {
    mode = 'denied'
    await readiness.getByRole('combobox', { name: '诊断作用域' }).selectOption(scope)
    await readiness.getByRole('button', { name: '刷新只读诊断' }).click()
    await page.waitForURL('**/wealth-hub/login')
    check(requests.at(-1).path === '/data-readiness', 'denial bypassed')
    mode = 'normal'; await goto('data-readiness')
    await readiness.getByText('fixture-ASSETS', { exact: true }).first().waitFor()
  }
  await goto('settings')
  const settings = page.locator('.settings-page')
  await settings.getByRole('button', { name: '添加成员', exact: true }).waitFor()
  await page.waitForFunction(() => !document.querySelector('#settings-family .actions .el-button--primary').disabled)
  const inputs = settings.locator('#settings-profile input')
  await inputs.nth(1).fill('隔离昵称'); await inputs.nth(2).fill('fixture@example.invalid'); await inputs.nth(3).fill('000')
  await settings.getByRole('button', { name: '保存', exact: true }).click()
  await page.waitForFunction(() => document.querySelector('#settings-profile input:nth-child(1)') !== null)
  await page.getByText('个人信息已更新', { exact: true }).waitFor()
  check(requests.some(r => r.path === '/users/me' && r.method === 'PUT' && r.body.nickname === '隔离昵称'), 'profile API missing')
  const security = settings.locator('#settings-security'), pw = security.locator('input')
  let count = requests.length
  await security.getByRole('button', { name: '修改密码' }).click()
  check(requests.length === count, 'empty password bypassed validation')
  await pw.nth(0).fill('fixture-old'); await pw.nth(1).fill('fixture-new'); await pw.nth(2).fill('fixture-new')
  passwordFailure = true
  await security.getByRole('button', { name: '修改密码' }).click()
  await settings.getByRole('alert').waitFor()
  check(await pw.nth(0).inputValue() === 'fixture-old', 'failed password cleared')
  passwordFailure = false
  await security.getByRole('button', { name: '修改密码' }).click()
  await page.getByText('密码已修改', { exact: true }).waitFor()
  check(await pw.nth(0).inputValue() === '', 'password retained after success')
  const familyPanel = settings.locator('#settings-family')
  await familyPanel.getByRole('button', { name: '移除', exact: true }).nth(1).click()
  const confirm = page.getByRole('dialog').filter({ hasText: '确认移除' })
  count = requests.filter(r => r.method === 'DELETE').length
  await confirm.getByRole('button', { name: '取消', exact: true }).click()
  check(requests.filter(r => r.method === 'DELETE').length === count, 'cancel sent removal')
  await familyPanel.getByRole('button', { name: '移除', exact: true }).nth(1).click()
  await confirm.getByRole('button', { name: '确定', exact: true }).click()
  await page.getByText('成员已移除', { exact: true }).waitFor()
  await familyPanel.locator('.el-select').nth(1).click()
  await page.getByRole('option', { name: '管理员', exact: true }).last().click()
  const roleConfirm = page.getByRole('dialog', { name: '确认角色变更' })
  count = requests.filter(r => r.path.endsWith('/role')).length
  await roleConfirm.getByRole('button', { name: '取消', exact: true }).click()
  check(requests.filter(r => r.path.endsWith('/role')).length === count, 'cancel sent role change')
  await familyPanel.locator('.el-select').nth(1).click()
  await page.getByRole('option', { name: '管理员', exact: true }).last().click()
  await roleConfirm.getByRole('button', { name: '确定', exact: true }).click()
  await page.getByText('角色已更新', { exact: true }).waitFor()
  membersFailure = true
  await familyPanel.getByRole('button', { name: '刷新', exact: true }).click()
  await familyPanel.getByRole('alert').waitFor()
  check(await settings.getByRole('button', { name: '添加成员', exact: true }).isDisabled(), 'failed read retained admin')
  membersFailure = false
  await familyPanel.getByRole('button', { name: '重试读取成员' }).click()
  await familyPanel.getByRole('alert').waitFor({ state: 'hidden' })
  for (const theme of ['dark', 'light', 'system', 'legacy', 'light']) {
    await settings.getByLabel('明暗模式').selectOption(theme)
    check(await page.evaluate(() => JSON.parse(localStorage.getItem('wealthhub.appearance.v1')).mode) === theme, 'theme not saved')
  }
  for (const palette of ['blue', 'peach', 'cream']) {
    await settings.getByLabel('配色', { exact: true }).selectOption(palette)
    check(await page.evaluate(() => document.documentElement.dataset.whPalette) === palette, 'palette not applied')
  }
  const preference = await page.evaluate(() => JSON.parse(localStorage.getItem('wealthhub.appearance.v1')))
  check(Object.keys(preference).sort().join() === 'mode,palette,version', 'business state mixed with appearance')
  for (const name of ['settings', 'data-readiness']) {
    await goto(name)
    const section = page.locator(name === 'settings' ? '.settings-page' : '.readiness')
    await section.locator('.section-nav a').first().waitFor()
    for (const width of [1440, 1100, 760, 390]) {
      await page.setViewportSize({ width, height: 1000 })
      check(await section.evaluate(n => n.scrollWidth <= n.clientWidth + 1), `${name} overflow ${width}`)
    }
    await page.evaluate(() => document.documentElement.dataset.whTheme = 'dark')
    check(await section.evaluate(n => getComputedStyle(n).color === 'rgb(255, 254, 250)'), 'dark theme text not applied')
    await page.screenshot({ path: `output/playwright/readinessSettings-${name}-dark.png`, fullPage: true })
    await section.locator('.section-nav a').last().focus(); await page.keyboard.press('Enter')
    check(new URL(page.url()).hash.includes(name === 'settings' ? 'settings-business' : 'readiness-deployment'), 'keyboard navigation failed')
  }
  role = 'MEMBER'; await goto('settings')
  await settings.getByText('当前没有已核实的管理员权限，成员信息可供查看。').waitFor()
  check(await settings.getByRole('button', { name: '添加成员', exact: true }).isDisabled(), 'member can manage')
  family = false; await goto('settings')
  await settings.getByText('当前账号未加入家庭，暂无家庭成员可管理。').waitFor()
  check(await settings.getByRole('button', { name: '添加成员', exact: true }).isDisabled(), 'no-family can manage')
  check(pageerrors.length === 0, pageerrors.join(';'))
  check(unexpectedRequests.length === 0, JSON.stringify(unexpectedRequests))
  check(financialWrites === 0 && requests.length > 0, 'isolation evidence missing')
  return { result: 'PASS', boundary: 'ALL_BUSINESS_REQUESTS_INTERCEPTED_NO_SERVER_OR_PRODUCTION_CONNECTION', pageerrors, unexpectedRequests, financialWrites, interceptedRequests: requests.length, themes: ['dark', 'light', 'system', 'legacy'], palettes: ['blue', 'peach', 'cream'], widths: [1440, 1100, 760, 390] }
}
