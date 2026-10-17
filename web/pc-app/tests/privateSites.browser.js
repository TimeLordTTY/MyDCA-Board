async page => {
  const check = (ok, message) => { if (!ok) throw Error(message) }
  const origin = 'http://127.0.0.1:3115', context = page.context()
  const pageerrors = [], unexpectedRequests = [], requests = [], matrix = []
  const csrf = 'f'.repeat(43), tokenA = 'synthetic-private-owner-A', tokenB = 'synthetic-private-member-B'
  let financialWrites = 0, mode = 'ok', cookieOwner = null, holdExchange = false, releaseExchange, exchangeStarted
  let holdIdentity = false, releaseIdentity, identityStarted, denyIdentity = false
  const state = () => ({ authenticated: true, csrfToken: csrf, expiresAt: new Date(Date.now() + 60000).toISOString(), redirect: 'https://outside.invalid/?credential=must-never-follow' })
  const observe = tab => tab.on('pageerror', error => pageerrors.push(error.message))
  observe(page); context.on('page', observe)
  await context.route('**/*', async route => {
    const req = route.request(), url = new URL(req.url()), method = req.method(), headers = req.headers()
    if (url.origin !== origin) { unexpectedRequests.push({ origin: url.origin, method }); return route.abort() }
    if (url.pathname === '/fixture-control') return route.fulfill({ contentType: 'text/html', body: '<!doctype html><title>Isolated fixture control</title>' })
    if (url.pathname === '/explore/') {
      requests.push({ path: url.pathname, method })
      check(method === 'GET' && !url.search && !url.hash, 'explore destination carried credentials')
      return route.fulfill({ contentType: 'text/html', body: '<!doctype html><title>Mock navigation boundary</title><p>Isolated navigation only</p>', headers: { 'Cache-Control': 'private, no-store' } })
    }
    if (!url.pathname.startsWith('/api/')) {
      if (method === 'GET' && url.pathname.startsWith('/wealth-hub/') && ['document', 'script', 'stylesheet', 'image', 'font', 'other'].includes(req.resourceType())) return route.continue()
      unexpectedRequests.push({ path: url.pathname, method }); return route.abort()
    }
    const path = url.pathname.replace('/api/v2', '')
    requests.push({ path, method })
    const reply = (status, data) => route.fulfill({ status, ...(status === 204 ? {} : { json: data }), headers: { 'Cache-Control': 'private, no-store' } })
    if (path === '/users/me' && method === 'GET') {
      const captured = headers.authorization
      if (holdIdentity) { holdIdentity = false; await new Promise(resolve => { releaseIdentity = resolve; identityStarted?.() }) }
      if (denyIdentity) return reply(401, {})
      if (captured !== `Bearer ${tokenA}` && captured !== `Bearer ${tokenB}`) return reply(401, {})
      return reply(200, { id: captured === `Bearer ${tokenA}` ? 7001 : 7002, username: 'fixture-only', nickname: captured === `Bearer ${tokenA}` ? '隔离Owner' : '隔离成员' })
    }
    if (path === '/private-sites/session/exchange' && method === 'POST') {
      check(headers['x-private-site-request'] === '1', 'exchange missing CSRF bootstrap header')
      check(headers.authorization === `Bearer ${tokenA}` || headers.authorization === `Bearer ${tokenB}`, 'exchange did not reuse Bearer client')
      const captured = headers.authorization, capturedMode = mode
      if (holdExchange) await new Promise(resolve => { releaseExchange = resolve; exchangeStarted?.() })
      cookieOwner = null
      if (capturedMode === 'network') return route.abort('internetdisconnected')
      if (typeof capturedMode === 'number') return reply(capturedMode, {})
      if (captured === `Bearer ${tokenB}`) return reply(403, {})
      cookieOwner = 'A'
      if (capturedMode === 'expired') return reply(200, { ...state(), expiresAt: '2000-01-01T00:00:00Z' })
      if (capturedMode === 'cacheable') return route.fulfill({ json: state() })
      return reply(200, state())
    }
    if (path === '/private-sites/session' && method === 'GET') {
      check(!headers.authorization && headers['x-private-site-request'] === '1', 'cookie state carried Bearer or missed header')
      return cookieOwner ? reply(200, state()) : reply(401, {})
    }
    if (path === '/private-sites/session/logout' && method === 'POST') {
      check(!headers.authorization && headers['x-private-site-request'] === '1', 'private logout carried Bearer')
      check(headers['x-csrf-token'] === csrf, 'private logout missing server CSRF')
      cookieOwner = null; return reply(204)
    }
    if (path === '/auth/logout' && method === 'POST') return route.abort('internetdisconnected')
    if (method !== 'GET') financialWrites++
    unexpectedRequests.push({ path, method }); return route.abort()
  })
  const control = await context.newPage()
  await control.goto(origin + '/fixture-control')
  const setToken = token => control.evaluate(value => value ? localStorage.setItem('token', value) : localStorage.removeItem('token'), token)
  const goto = () => page.goto(origin + '/wealth-hub/portal')
  const button = () => page.getByRole('button', { name: '进入无限探索', exact: true })
  const ready = async () => { await button().waitFor(); await page.waitForFunction(() => !document.querySelector('[aria-describedby="explore-status"]').disabled) }
  await setToken(tokenA)
  holdIdentity = true
  const initial = new Promise(resolve => { identityStarted = resolve })
  await goto(); await initial
  check(await button().isDisabled(), 'entry enabled before original identity recovery')
  check(await button().evaluate(node => node.classList.contains('app-card') && node.classList.contains('portal-explore-card') && node.tagName === 'BUTTON'), 'private entry must be the native whole-card portal button')
  check(!requests.some(r => r.path.endsWith('/exchange')), 'token presence authorized private entry')
  holdIdentity = false; releaseIdentity(); await ready()
  await page.getByText('你好，隔离Owner').waitFor()
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 900 })
    check(await page.locator('.portal-page').evaluate(node => node.scrollWidth <= node.clientWidth + 1), `portal overflow ${width}`)
    await button().focus()
    check(await button().evaluate(node => node === document.activeElement), `keyboard entry unreachable ${width}`)
    check(await button().evaluate(node => getComputedStyle(node).outlineStyle !== 'none'), `missing keyboard focus outline ${width}`)
    check(await page.locator('.app-grid').evaluate(grid => {
      const cards = [...grid.children].map(node => node.getBoundingClientRect())
      return cards.every((a, i) => cards.every((b, j) => i === j || a.right <= b.left + 1 || b.right <= a.left + 1 || a.bottom <= b.top + 1 || b.bottom <= a.top + 1))
    }), `portal cards overlap ${width}`)
    check(await page.locator('.portal-explore-heading').evaluate(node => {
      const peer = document.querySelector('.app-title-row h2')
      return getComputedStyle(node).fontSize === getComputedStyle(peer).fontSize && getComputedStyle(node).fontWeight === getComputedStyle(peer).fontWeight
    }), `card title typography differs ${width}`)
    matrix.push(`layout-keyboard-${width}`)
  }
  check(await page.locator('.app-card[target="_blank"][rel="noopener noreferrer"]').count() === 2, 'independent poem entries changed')
  check(await page.getByText('各站点分别校验权限；无限探索仅限 Owner').count() === 1, 'portal claims blanket SSO')
  for (const [failure, copy] of [[401, '过期'], [403, '仅 Owner'], [404, '尚未启用'], [503, '尚未启用'], ['network', '网络'], ['expired', '响应无效或已过期'], ['cacheable', '响应无效或已过期']]) {
    mode = failure; await button().focus(); await page.keyboard.press('Enter')
    await page.waitForFunction(text => document.querySelector('#explore-status')?.textContent.includes(text), copy)
    check(await page.locator('#explore-status').evaluate(node => node.getAttribute('role') === 'status' && node.getAttribute('aria-live') === 'polite' && node.getBoundingClientRect().height > 1), `error not visibly announced ${failure}`)
    check(page.url().endsWith('/portal'), `failure ${failure} navigated`)
    check(await page.evaluate(() => localStorage.getItem('token')) === tokenA, `private ${failure} revoked Wealth Hub token`)
    matrix.push(`failure-${failure}`)
  }
  mode = 'ok'; holdExchange = true
  const started = new Promise(resolve => { exchangeStarted = resolve })
  const before = requests.filter(r => r.path.endsWith('/exchange')).length
  await button().focus(); await page.keyboard.press('Space'); await started
  check(await button().isDisabled() && await button().getAttribute('aria-busy') === 'true', 'exchange did not expose disabled/busy state')
  await page.keyboard.press('Enter')
  check(requests.filter(r => r.path.endsWith('/exchange')).length === before + 1, 'duplicate keyboard exchange')
  holdExchange = false; releaseExchange()
  await page.waitForURL(origin + '/explore/')
  matrix.push('owner-fixed-destination-retry-dedup')

  // Reloaded portal must revoke via cookie state, without a cached CSRF value.
  await goto(); await ready()
  const startLogout = requests.length
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await page.waitForURL('**/portal-login?**')
  check(await page.evaluate(() => localStorage.getItem('token')) === null, 'offline legacy logout retained JWT')
  check(cookieOwner === null, 'private cookie session survived logout')
  const logoutRequests = requests.slice(startLogout).map(r => r.path)
  check(logoutRequests.indexOf('/private-sites/session/logout') >= 0 && logoutRequests.indexOf('/private-sites/session/logout') < logoutRequests.indexOf('/auth/logout'), 'legacy logout preceded private revocation')
  const event = await control.evaluate(() => JSON.parse(localStorage.getItem('wealth-hub:private-session-event')))
  check(Object.keys(event).sort().join(',') === 'nonce,reason,version', 'cross-tab event leaked identity or credentials')
  matrix.push('reload-csrf-logout-offline-jwt-clear')

  // A real second tab changes localStorage while the previous exchange is delayed.
  await setToken(tokenA); await goto(); await ready()
  holdExchange = true
  const oldStarted = new Promise(resolve => { exchangeStarted = resolve })
  await button().click(); await oldStarted
  await setToken(tokenB)
  await page.getByText('你好，隔离成员').waitFor()
  holdExchange = false; releaseExchange()
  await page.waitForFunction(() => document.querySelector('#explore-status')?.textContent.includes('已清除'))
  await ready()
  mode = 'ok'; await button().click()
  await page.waitForFunction(() => document.querySelector('#explore-status')?.textContent.includes('仅 Owner'))
  check(page.url().endsWith('/portal') && cookieOwner === null, 'stale Owner exchange restored private access')
  matrix.push('real-cross-tab-account-switch-late-exchange-non-owner')

  await setToken(tokenA); await goto(); await ready()
  holdIdentity = true
  const oldIdentity = new Promise(resolve => { identityStarted = resolve })
  const identityBefore = requests.filter(r => r.path.endsWith('/exchange')).length
  await button().click(); await oldIdentity
  await setToken(tokenB)
  holdIdentity = false; releaseIdentity()
  await page.getByText('你好，隔离成员').waitFor()
  check(requests.filter(r => r.path.endsWith('/exchange')).length === identityBefore, 'old identity initiated exchange')
  matrix.push('late-original-identity-discarded')

  // Logout while an exchange is in flight: never follow its eventual success.
  await setToken(tokenA); await goto(); await ready()
  holdExchange = true
  const logoutStarted = new Promise(resolve => { exchangeStarted = resolve })
  await button().click(); await logoutStarted
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  check(await page.getByRole('button', { name: '退出中…', exact: true }).isDisabled(), 'logout was not locked')
  holdExchange = false; releaseExchange()
  await page.waitForURL('**/portal-login?**')
  check(cookieOwner === null, 'delayed exchange survived logout')
  matrix.push('pending-exchange-logout')

  // The old Portal logout may finish after a newer tab has restored another user.
  await setToken(tokenA); await goto(); await ready()
  holdExchange = true
  const switchingLogout = new Promise(resolve => { exchangeStarted = resolve })
  await button().click(); await switchingLogout
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await setToken(tokenB)
  await page.getByText('你好，隔离成员').waitFor()
  holdExchange = false; releaseExchange()
  await ready(); await button().click()
  await page.waitForFunction(() => document.querySelector('#explore-status')?.textContent.includes('仅 Owner'))
  check(await page.evaluate(() => localStorage.getItem('token')) === tokenB, 'old logout cleared new account')
  check(cookieOwner === null, 'old logout restored Owner cookie')
  matrix.push('delayed-logout-preserves-new-account')

  await setToken(tokenA); denyIdentity = true; await goto()
  await page.waitForURL('**/portal-login?**')
  check(await page.evaluate(() => localStorage.getItem('token')) === null, 'expired original JWT remained')
  matrix.push('original-identity-401-regression')
  check(pageerrors.length === 0, JSON.stringify(pageerrors))
  check(unexpectedRequests.length === 0, JSON.stringify(unexpectedRequests))
  check(financialWrites === 0 && requests.length > 0, 'missing request isolation evidence')
  await control.close()
  return { result: 'PASS', boundary: 'ALL_BUSINESS_REQUESTS_INTERCEPTED_NO_SERVER_OR_PRODUCTION_CONNECTION', pageerrors, unexpectedRequests, financialWrites, interceptedRequests: requests.length, widths: [1440, 390], matrix }
}
