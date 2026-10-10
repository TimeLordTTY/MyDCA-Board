import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as vue from 'vue'
import postcss from 'postcss'

const read = path => readFile(new URL(`../src/${path}`, import.meta.url), 'utf8')
const load = code => import(`data:text/javascript,${encodeURIComponent(ts.transpileModule(code, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText)}#${Math.random()}`)
const appearance = await load(await read('styles/appearance.ts'))
function fixture(stored = null, denied = false) {
  const root = { dataset: {}, classList: { toggle: (...args) => { root.dark = args } } }
  let listener, writes = []
  const media = { matches: false, addEventListener: (_, cb) => { listener = cb }, removeEventListener: (_, cb) => { assert.equal(cb, listener); listener = undefined } }
  const storage = { getItem: key => { assert.equal(key, appearance.APPEARANCE_KEY); if (denied) throw Error(); return stored }, setItem: (key, value) => { if (denied) throw Error(); writes.push([key, JSON.parse(value)]) } }
  return { root, media, writes, change: () => listener?.(), controller: appearance.createAppearanceController(root, storage, media) }
}
test('appearance whitelist, corrupt storage, denied storage and default', () => {
  for (const stored of [null, '{broken', '{"version":2,"mode":"dark","palette":"peach"}', '{"version":1,"mode":"demo","palette":"cream"}']) {
    assert.deepEqual(fixture(stored).controller.get(), appearance.DEFAULT_APPEARANCE)
  }
  const f = fixture(null, true)
  f.controller.set({ version: 1, mode: 'dark', palette: 'peach' })
  assert.equal(f.root.dataset.whTheme, 'dark')
  const clean = fixture()
  clean.controller.set({ version: 1, mode: 'dark', palette: 'blue', account: 100, token: 'never-copy' })
  assert.deepEqual(clean.writes, [[appearance.APPEARANCE_KEY, { version: 1, mode: 'dark', palette: 'blue' }]])
})
test('theme attributes, system changes, legacy restoration and disposal', () => {
  const f = fixture()
  for (const mode of ['light', 'dark', 'legacy', 'system']) for (const palette of ['cream', 'blue', 'peach']) {
    f.controller.set({ version: 1, mode, palette })
    assert.equal(f.root.dataset.whTheme, mode === 'system' ? 'light' : mode)
    assert.equal(f.root.dataset.whPalette, palette)
  }
  f.media.matches = true; f.change(); assert.equal(f.root.dataset.whTheme, 'dark')
  f.controller.set({ version: 1, mode: 'light', palette: 'cream' }); f.change()
  assert.equal(f.root.dataset.whTheme, 'light'); assert.deepEqual(f.root.dark, ['dark', false])
  f.controller.dispose(); f.change()
})
// Render the actual Vue template with an in-memory host, without credentials or HTTP.
globalThis.__foundationVue = vue
const renderer = vue.createRenderer({
  createElement: tag => ({ tag, props: {}, children: [] }), createText: text => ({ text }), createComment: text => ({ text }),
  setText: (node, text) => { node.text = text }, setElementText: (node, text) => { node.text = text; node.children = [] },
  patchProp: (node, key, old, value) => { node.props[key] = value },
  insert: (node, parent) => { node.parent = parent; parent.children.push(node) },
  remove: node => { node.parent.children.splice(node.parent.children.indexOf(node), 1) },
  parentNode: node => node.parent, nextSibling: () => null,
})
async function component(path) {
  const script = compileScript(parse(await read(path)).descriptor, { id: path, inlineTemplate: true }).content
    .replace(/import \{([^}]+)\} from ["']vue["']/g, (_, names) => `const {${names.replace(/ as /g, ': ')}} = globalThis.__foundationVue`)
  return (await load(script)).default
}
const state = await component('components/FoundationState.vue')
const nodes = node => [node, ...(node.children || []).flatMap(nodes)]
test('state DOM roles, loading busy, explicit empty text and disabled manual retry', () => {
  for (const kind of ['loading', 'error', 'empty']) {
    let retries = 0
    const host = { children: [] }
    const app = renderer.createApp(state, { kind, retryable: true, disabled: true, onRetry: () => retries++ })
    app.mount(host)
    const all = nodes(host), root = all.find(n => n.tag === 'div')
    assert.equal(root.props.class, `wh-state wh-state--${kind}`)
    assert.equal(root.props.role, kind === 'error' ? 'alert' : 'status')
    assert.equal(root.props['aria-busy'], kind === 'loading')
    const button = all.find(n => n.tag === 'button')
    if (kind === 'error') { assert.equal(button.props.disabled, true); assert.equal(button.props.type, 'button') }
    else assert.equal(button, undefined)
    assert.equal(retries, 0); app.unmount()
  }
  const host = { children: [] }; let retries = 0
  renderer.createApp(state, { kind: 'error', retryable: true, onRetry: () => retries++ }).mount(host)
  nodes(host).find(n => n.tag === 'button').props.onClick(); assert.equal(retries, 1)
})
const css = postcss.parse(await read('styles/foundation.css'))
function tokens(selector) {
  const values = {}
  css.walkRules(rule => { if (rule.selector === selector) rule.walkDecls(d => { values[d.prop] = d.value }) })
  return values
}
const light = tokens(':root'), dark = { ...light, ...tokens(":root[data-wh-theme='dark']") }
function luminance(hex) {
  const values = hex.match(/[a-f0-9]{2}/gi).map(v => parseInt(v, 16) / 255).map(v => v <= .04045 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4)
  return values[0] * .2126 + values[1] * .7152 + values[2] * .0722
}
function contrast(a, b) { const x = luminance(a), y = luminance(b); return (Math.max(x, y) + .05) / (Math.min(x, y) + .05) }
test('text, semantic status, primary button and all decorative palettes meet 4.5:1; focus meets 3:1', () => {
  for (const theme of [light, dark]) {
    for (const background of ['--wh-page', '--wh-surface']) {
      for (const foreground of ['--wh-ink', '--wh-muted', '--wh-danger', '--wh-positive', '--wh-warning', '--wh-rise', '--wh-fall']) assert.ok(contrast(theme[foreground], theme[background]) >= 4.5, `${foreground}/${background}`)
      assert.ok(contrast(theme['--wh-accent'], theme[background]) >= 3)
      assert.ok(contrast(theme['--wh-border'], theme[background]) >= 3)
    }
    assert.ok(contrast(theme['--wh-on-accent'], theme['--wh-accent']) >= 4.5)
  }
  for (const [theme, backgrounds] of [[light, ['#eaf3dc', '#9bc1ff', '#ffc2a4']], [dark, ['#39472e', '#303e55', '#513b30']]]) for (const bg of backgrounds) assert.ok(contrast(theme['--wh-ink'], bg) >= 4.5)
})
test('CSS tools, legacy isolation, Element Plus bridge, responsive and motion rules', async () => {
  const selectors = []; css.walkRules(r => selectors.push(r.selector))
  for (const name of ['wh-card', 'wh-number', 'wh-chip', 'wh-label', 'wh-state', 'wh-button', 'wh-input']) assert.ok(selectors.some(s => s.includes(`.${name}`)))
  assert.ok(selectors.some(s => s.includes(':focus-visible')))
  assert.ok(selectors.some(s => s.includes(':disabled')))
  const bridge = tokens(":root[data-wh-theme]:not([data-wh-theme='legacy'])")
  assert.equal(bridge['--el-bg-color-overlay'], 'var(--wh-surface)')
  const media = []; css.walkAtRules('media', rule => media.push(rule.params))
  for (const breakpoint of ['1440', '1080', '640']) assert.ok(media.some(m => m.includes(breakpoint)))
  assert.ok(media.includes('(prefers-reduced-motion: reduce)'))
  const prefs = await read('components/AppearanceSettings.vue')
  const { descriptor } = parse(prefs)
  assert.ok(compileScript(descriptor, { id: 'prefs', inlineTemplate: true }))
  assert.match(prefs, /for="appearance-mode"/); assert.match(prefs, /id="appearance-mode"/)
  assert.match(prefs, /:disabled="appearance.mode === 'legacy'"/)
  assert.doesNotMatch(await read('styles/appearance.ts'), /fetch\(|apiClient|data-refresh|@wealth-hub\/shared/)
})
