async (page) => {
  const check = (value, message) => { if (!value) throw Error(message) }
  const requests = []
  const unexpectedRequests = [], pageerrors = []
  page.on('pageerror', error => pageerrors.push(error.message))
  let touchActivations = 0
  const activate = async locator => {
    try { await locator.tap(); touchActivations++ }
    catch (error) {
      if (!String(error).includes('does not support tap')) throw error
      await locator.click()
    }
  }
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.origin !== 'http://127.0.0.1:3108') { unexpectedRequests.push(url.origin); return route.abort() }
    if (url.pathname.includes('/api/')) {
      requests.push({ path: url.pathname, method: route.request().method() })
      if (route.request().method() === 'GET' && url.pathname === '/api/v2/users/me') return route.fulfill({ json: { id: 1, username: 'test-only', nickname: '离线验收', familyId: 7 } })
      if (route.request().method() === 'POST' && url.pathname.endsWith('/auth/logout')) return route.abort()
      if (route.request().method() !== 'GET' || !/\/(accounts|products|holdings|market\/quotes|nav\/latest)$/.test(url.pathname)) { unexpectedRequests.push(url.pathname); return route.abort() }
      return route.fulfill({ json: [] })
    }
    if (route.request().method() !== 'GET' || ['fetch', 'xhr'].includes(route.request().resourceType())) { unexpectedRequests.push(url.pathname); return route.abort() }
    return route.continue()
  })
  await page.addInitScript(() => localStorage.setItem('token', 'offline-test-only'))
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('http://127.0.0.1:3108/wealth-hub/tests/navigation-browser.html')
  await page.locator('.wealth-header h1').waitFor()
  await page.getByRole('link', { name: '账号设置' }).waitFor()
  check(await page.locator('.wealth-header h1').innerText() === '持仓管理', 'wrong initial business title')
  check(page.url().includes('/wealth-hub/holdings?productId=42'), 'base/query lost')
  check((await page.locator('.wealth-rail').boundingBox()).width === 82, 'rail not 82px')
  await page.locator('.wealth-rail').hover()
  check((await page.locator('.wealth-rail').boundingBox()).width === 222, 'hover expansion failed')
  await page.mouse.move(800, 40)
  await page.locator('.wealth-rail-toggle').focus()
  check((await page.locator('.wealth-rail').boundingBox()).width === 222, 'keyboard expansion failed')
  await page.locator('.wealth-rail-toggle').press('Enter')
  check(await page.locator('.wealth-shell').evaluate(n => n.classList.contains('nav-pinned')), 'explicit expansion failed')
  await page.locator('.wealth-rail-toggle').click()
  const links = page.locator('#desktop-navigation a')
  check(await links.count() === 14, 'missing routes')
  for (let i = 0; i < 14; i++) {
    const link = links.nth(i), label = await link.getAttribute('aria-label')
    await link.click()
    check(await page.locator('.wealth-header h1').innerText() === label, `unreachable ${label}`)
    check(await link.getAttribute('aria-current') === 'page', `missing active state ${label}`)
    check((await page.locator('.wealth-actions').count()) === (['目标与预算', '数据就绪检查', '策略实验室', '风险观察', '配置观察'].includes(label) ? 0 : 1), `unsafe actions ${label}`)
  }
  await page.locator('#retained-input').fill('主题切换保留输入')
  for (const mode of ['light', 'dark', 'legacy', 'system']) {
    await page.locator('#appearance-mode').selectOption(mode)
    if (mode !== 'legacy') {
      await page.waitForFunction(() => {
        const dark = document.documentElement.dataset.whTheme === 'dark'
        return getComputedStyle(document.querySelector('.wealth-identity button')).backgroundColor === (dark ? 'rgb(41, 44, 39)' : 'rgb(255, 254, 250)')
      })
      const ratios = await page.locator('.wealth-header .wh-button').evaluateAll(buttons => {
        const luminance = color => color.match(/[\d.]+/g).slice(0, 3).map(Number).map(n => n / 255).map(n => n <= .04045 ? n / 12.92 : ((n + .055) / 1.055) ** 2.4).reduce((sum, n, i) => sum + n * [.2126, .7152, .0722][i], 0)
        return buttons.map(button => { const css = getComputedStyle(button), a = luminance(css.color), b = luminance(css.backgroundColor); return (Math.max(a, b) + .05) / (Math.min(a, b) + .05) })
      })
      check(ratios.every(ratio => ratio >= 4.5), `header contrast ${mode}: ${JSON.stringify(ratios)}`)
    }
    check(await page.locator('#retained-input').inputValue() === '主题切换保留输入', 'theme reset form')
    check(!(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)), 'desktop overflow')
  }
  await page.locator('#appearance-mode').selectOption('light')
  await page.locator('#page-bottom').scrollIntoViewIfNeeded()
  check(await page.locator('.wealth-content').evaluate(n => n.scrollTop > 0), 'page cannot scroll')
  check((await page.locator('.wealth-header').boundingBox()).y === 0, 'header scrolled or hidden')
  await links.first().click()
  check(await page.locator('.wealth-content').evaluate(n => n.scrollTop === 0), 'route retained old scroll')
  await page.setViewportSize({ width: 390, height: 844 })
  check(!(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)), 'mobile shell overflow')
  await activate(page.getByRole('button', { name: '目录', exact: true }))
  await page.locator('#mobile-navigation').waitFor()
  for (let i = 0; i < 20; i++) {
    await page.keyboard.press('Tab')
    check(await page.locator('.wealth-mobile-drawer').evaluate(n => n.contains(document.activeElement)), 'drawer focus escaped')
  }
  await page.keyboard.press('Escape')
  await page.locator('.wealth-mobile-drawer').waitFor({ state: 'hidden' })
  check(await page.getByRole('button', { name: '目录', exact: true }).evaluate(n => n === document.activeElement), 'drawer did not restore focus')
  await activate(page.getByRole('button', { name: '目录', exact: true }))
  await activate(page.locator('#mobile-navigation a').last())
  await page.locator('.wealth-mobile-drawer').waitFor({ state: 'hidden' })
  check(await page.locator('.wealth-header h1').innerText() === '设置', 'touch route failed')
  await activate(page.getByRole('button', { name: '目录', exact: true }))
  await page.locator('.el-overlay:visible').click({ position: { x: 375, y: 400 } })
  await page.locator('.wealth-mobile-drawer').waitFor({ state: 'hidden' })
  await activate(page.getByRole('button', { name: '目录', exact: true }))
  await page.setViewportSize({ width: 1080, height: 900 })
  await page.locator('.wealth-mobile-drawer').waitFor({ state: 'hidden' })
  check(requests.every(r => r.method === 'GET'), 'automatic write or theme write')
  await page.getByRole('button', { name: '安全退出', exact: true }).click()
  await page.waitForURL('**/wealth-hub/login')
  check(await page.evaluate(() => localStorage.getItem('token')) === null, 'logout retained credential')
  check(await page.evaluate(() => window.navigationFixture.store.user === null), 'logout retained identity')
  await page.evaluate(() => window.navigationFixture.router.push('/holdings?productId=42'))
  check(page.url().includes('/wealth-hub/login?redirect='), 'unauthenticated deep link bypassed guard')
  check(decodeURIComponent(page.url()).includes('/holdings?productId=42'), 'guard lost query redirect')
  // Inspect real app history fallback on deep-link refresh with every HTTP request mocked.
  await page.evaluate(() => localStorage.setItem('token', 'offline-test-only'))
  await page.goto('http://127.0.0.1:3108/wealth-hub/holdings?productId=42')
  await page.locator('.wealth-header h1').waitFor()
  check(await page.locator('.wealth-header h1').innerText() === '持仓管理', 'deep link history fallback failed')
  await page.reload()
  await page.locator('.wealth-header h1').waitFor()
  check(await page.locator('.wealth-header h1').innerText() === '持仓管理', 'deep link refresh failed')
  check(requests.filter(r => r.method !== 'GET').every(r => r.path.endsWith('/auth/logout')), 'unexpected financial write')
  check(pageerrors.length === 0 && unexpectedRequests.length === 0 && requests.length > 0, JSON.stringify({ pageerrors, unexpectedRequests }))
  return { result: 'PASS', boundary: 'ALL_BUSINESS_REQUESTS_INTERCEPTED_NO_SERVER_OR_PRODUCTION_CONNECTION', pageerrors, unexpectedRequests, interceptedRequests: requests.length, financialWrites: 0, routes: 14, widths: [1440, 1080, 390], themes: 4, headerContrastAtLeast: 4.5, touchActivations, requests }
}
