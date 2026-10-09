import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
const transpile = s => ts.transpileModule(s, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText
const source = name => readFile(new URL(`../src/components/${name}.ts`, import.meta.url), 'utf8')
globalThis.__csvModel = await import(`data:text/javascript,${encodeURIComponent(transpile(await source('budgetReviewModel')))}`)
const csv = await import(`data:text/javascript,${encodeURIComponent(transpile(await source('budgetReviewCsv')).replace("import { completeActual } from './budgetReviewModel';", 'const { completeActual } = globalThis.__csvModel;'))}`)
const months = globalThis.__csvModel.recentMonths(new Date(2026, 0, 31))
const entry = (id = 'a', extra = {}, config = {}) => ({ budget: { id, config: { month: '2026-01', name: '中文预算', scope: 'PERSONAL', currency: 'CNY', ...config } }, comparison: { quality: 'OK', plannedIncome: 0, plannedExpenses: null, plannedReserve: 3, plannedSurplus: 4, actualIncome: 0, actualExpenses: 0, actualSurplus: 0, remainingBudget: 0, overspent: false, unmatchedPostings: 0, warnings: ['secret warning'], ...extra }, readAt: '2026-01-31 10:00' })
// Parse quoted RFC4180 cells, including embedded CRLF and doubled quotes.
function parse(text) {
  const rows = []; let row = [], value = '', quoted = false
  for (let i = 1; i < text.length; i++) {
    const c = text[i]
    if (c === '"') { if (quoted && text[i + 1] === '"') { value += '"'; i++ } else quoted = !quoted }
    else if (!quoted && c === ',') { row.push(value); value = '' }
    else if (!quoted && c === '\r' && text[i + 1] === '\n') { row.push(value); rows.push(row); row = []; value = ''; i++ }
    else value += c
  }
  return rows
}
const serialize = entries => csv.serializeBudgetReviewCsv(months, entries, 'PERSONAL', 'CNY')
test('cross-year months, separate stable budgets, scope/currency isolation and empty months', () => {
  const entries = [entry('z'), entry('a'), entry('foreign', {}, { currency: 'USD' }), entry('family', {}, { scope: 'FAMILY' })]
  const output = serialize(entries), rows = parse(output)
  assert.equal(output.charCodeAt(0), 0xfeff); assert.equal(rows.length, 8)
  assert.deepEqual(rows.slice(1, 6).map(r => r[0]), months.slice(0, 5))
  assert.deepEqual(rows.slice(6).map(r => r[2]), ['a', 'z'])
  assert.equal(rows[1][1], '未创建预算'); assert.ok(rows[1].slice(5, 13).every(v => v === '未知'))
  assert.ok(rows.every(r => r.length === csv.CSV_COLUMNS.length))
  assert.equal(serialize([...entries].reverse()), output)
  assert.doesNotMatch(output, /foreign|secret warning/)
})
test('actual completeness, genuine zero and nonfinite/untrusted numbers', () => {
  const complete = parse(serialize([entry()])).at(-1)
  assert.equal(complete[5], '0'); assert.equal(complete[6], '未知'); assert.equal(complete[9], '0'); assert.match(complete[14], /未超支/)
  for (const quality of ['PARTIAL', 'UNKNOWN', 'UNAVAILABLE']) {
    const r = parse(serialize([entry('a', { quality })])).at(-1)
    assert.equal(r[5], '0'); assert.ok(r.slice(9, 13).every(v => v.includes(quality))); assert.equal(r[14], '超支状态未知')
  }
  for (const field of ['actualIncome', 'actualExpenses', 'actualSurplus', 'remainingBudget', 'overspent']) {
    const r = parse(serialize([entry('a', { [field]: undefined })])).at(-1)
    assert.ok(r.slice(9, 13).every(v => v === '未知 / UNKNOWN')); assert.equal(r[14], '超支状态未知')
  }
  for (const n of [null, NaN, Infinity, '-1+2']) assert.equal(parse(serialize([entry('a', { plannedReserve: n })])).at(-1)[7], '未知')
})
test('Chinese, commas, quotes, CRLF and formula/control prefixes are safely quoted', () => {
  for (const text of ['=SUM(A1)', '  +1', '\t-2', '\r@evil', '\u0000=evil', '\u0085=evil', ' \n=evil']) {
    const r = parse(serialize([entry(text, {}, { name: text })])).at(-1)
    assert.equal(r[1], "'" + text); assert.equal(r[2], "'" + text)
  }
  const r = parse(serialize([entry('a', {}, { name: '中文,"名称"\r\n下一行' })])).at(-1)
  assert.equal(r[1], "'中文,\"名称\"\r\n下一行")
  assert.match(csv.budgetReviewFilename(months, '=private', '=secret'), /^budget-review-2025-08-2026-01-PERSONAL-UNKNOWN\.csv$/)
})
test('missing browser environment rejects; download cleanup runs on success and rejection', () => {
  assert.throws(() => csv.downloadBudgetReviewCsv('data', 'safe.csv'), /不支持/)
  const saved = { document: globalThis.document, create: URL.createObjectURL, revoke: URL.revokeObjectURL, timeout: globalThis.setTimeout }
  try {
    for (const fail of [false, true]) {
      let removed = 0, revoked = 0, clicked = 0, timer
      globalThis.document = { body: { appendChild() {} }, createElement: () => ({ remove() { removed++ }, click() { clicked++; if (fail) throw new Error('denied') } }) }
      URL.createObjectURL = () => 'blob:test'; URL.revokeObjectURL = u => { assert.equal(u, 'blob:test'); revoked++ }
      globalThis.setTimeout = fn => { timer = fn }
      if (fail) assert.throws(() => csv.downloadBudgetReviewCsv('data', 'safe.csv'), /denied/)
      else csv.downloadBudgetReviewCsv('data', 'safe.csv')
      assert.equal(clicked, 1); assert.equal(removed, 1); assert.equal(revoked, 0); timer(); assert.equal(revoked, 1)
    }
  } finally { globalThis.document = saved.document; URL.createObjectURL = saved.create; URL.revokeObjectURL = saved.revoke; globalThis.setTimeout = saved.timeout }
})
