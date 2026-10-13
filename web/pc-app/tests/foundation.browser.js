// Pass this function to playwright-cli run-code on the isolated test page.
async (page) => {
  const check = (value, message) => { if (!value) throw new Error(message) }
  const origin = 'http://127.0.0.1:3107', requests = [], pageerrors = [], unexpectedRequests = []
  page.on('pageerror', error => pageerrors.push(error.message))
  await page.route('**/*', route => {
    const request = route.request(), url = new URL(request.url()), method = request.method()
    if (url.origin !== origin) { unexpectedRequests.push({ origin: url.origin, method }); return route.abort() }
    if (url.pathname.includes('/api/')) {
      if (method !== 'GET' || !/\/(users\/me|accounts|products|holdings|market\/quotes|nav\/latest)$/.test(url.pathname)) { unexpectedRequests.push({ path: url.pathname, method }); return route.abort() }
      requests.push({ path: url.pathname, method })
      return route.fulfill({ json: url.pathname.endsWith('/users/me') ? { id: 1, username: 'offline-foundation-fixture', familyId: 1 } : [] })
    }
    if (method !== 'GET' || ['fetch', 'xhr'].includes(request.resourceType())) { unexpectedRequests.push({ path: url.pathname, method }); return route.abort() }
    return route.continue()
  })
  await page.goto('http://127.0.0.1:3107/wealth-hub/tests/foundation-browser.html')
  await page.locator('#appearance-mode').waitFor()
  await page.locator('#draft-input').fill('保留输入，不提交')
  const results = []
  for (const width of [1440, 1080, 390]) {
    await page.setViewportSize({ width, height: 900 })
    for (const mode of ['light', 'dark']) for (const palette of ['cream', 'blue', 'peach']) {
      await page.locator('#appearance-mode').selectOption(mode)
      await page.locator('#appearance-palette').selectOption(palette)
      // Measure settled colors, after Element Plus's existing color transition.
      await page.waitForTimeout(300)
      await page.locator('.el-button--primary').hover()
      await page.waitForTimeout(300)
      check(await page.locator('#draft-input').inputValue() === '保留输入，不提交', 'theme reset editing state')
      const result = await page.evaluate(() => {
        const luminance = color => {
          const v = color.match(/[\d.]+/g).slice(0, 3).map(Number).map(n => n / 255).map(n => n <= .04045 ? n / 12.92 : ((n + .055) / 1.055) ** 2.4)
          return v[0] * .2126 + v[1] * .7152 + v[2] * .0722
        }
        const ratio = style => {
          const a = luminance(style.color), b = luminance(style.backgroundColor)
          return (Math.max(a, b) + .05) / (Math.min(a, b) + .05)
        }
        const action = document.querySelector('.el-button--primary')
        return {
          theme: document.documentElement.dataset.whTheme,
          palette: document.documentElement.dataset.whPalette,
          overflow: document.documentElement.scrollWidth > innerWidth,
          primaryContrast: ratio(getComputedStyle(action)),
          cardContrast: ratio(getComputedStyle(document.querySelector('.wh-card'))),
          disabled: document.querySelector('.wh-button').disabled,
        }
      })
      check(result.theme === mode && result.palette === palette, 'wrong theme attributes')
      check(!result.overflow, `horizontal overflow at ${width}`)
      check(result.primaryContrast >= 4.5 && result.cardContrast >= 4.5, `computed contrast failed: ${JSON.stringify(result)}`)
      check(result.disabled, 'disabled action enabled')
      results.push({ width, mode, palette, ...result })
    }
  }
  await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' })
  await page.locator('#appearance-mode').selectOption('system')
  check(await page.locator('html').getAttribute('data-wh-theme') === 'dark', 'system theme failed')
  const transition = await page.locator('.wh-button').first().evaluate(node => getComputedStyle(node).transitionDuration)
  check(transition === '0s', 'reduced motion failed')
  await page.locator('#appearance-mode').focus()
  check(await page.locator('#appearance-mode').evaluate(node => getComputedStyle(node).outlineStyle) === 'solid', 'keyboard focus missing')
  await page.locator('#appearance-mode').selectOption('legacy')
  check(await page.locator('#appearance-palette').isDisabled(), 'legacy palette not disabled')
  await page.reload()
  check(await page.locator('html').getAttribute('data-wh-theme') === 'legacy', 'preference not restored')
  const stored = await page.evaluate(() => JSON.parse(localStorage.getItem('wealthhub.appearance.v1')))
  check(Object.keys(stored).sort().join(',') === 'mode,palette,version', 'non-appearance data persisted')
  // Also check the restored appearance on a real empty business page. These are
  // actual page reads, intercepted above, rather than an invented request count.
  await page.addInitScript(() => localStorage.setItem('token', 'offline-foundation-only'))
  await page.goto(`${origin}/wealth-hub/holdings`)
  await page.locator('.holdings-page').waitFor()
  await page.getByRole('status').filter({ hasText: '当前访问范围暂无持仓' }).waitFor()
  check(pageerrors.length === 0 && unexpectedRequests.length === 0 && requests.length > 0, JSON.stringify({ pageerrors, unexpectedRequests }))
  return { result: 'PASS', boundary: 'ALL_BUSINESS_REQUESTS_INTERCEPTED_NO_SERVER_OR_PRODUCTION_CONNECTION', pageerrors, unexpectedRequests, interceptedRequests: requests.length, financialWrites: 0, cases: results.length, results }
}
