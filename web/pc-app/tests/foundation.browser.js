// Pass this function to playwright-cli run-code on the isolated test page.
async (page) => {
  const check = (value, message) => { if (!value) throw new Error(message) }
  await page.route('**/*', route => {
    const url = new URL(route.request().url())
    if (url.hostname !== '127.0.0.1' || url.pathname.includes('/api/')) return route.abort()
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
  return { cases: results.length, pass: true, results }
}
