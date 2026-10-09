// Compare full PC diagnostics with task-start HEAD without editing tracked source files.
const fs = require('node:fs')
const cp = require('node:child_process')
const path = require('node:path')
const entry = require.resolve('vue-tsc/bin/vue-tsc.js')
const target = path.resolve(__dirname, '../src/components/BudgetSixMonthReview.vue')
const original = fs.readFileSync
if (process.env.BUDGET_REVIEW_BASELINE === '1') {
  const revision = process.env.BUDGET_REVIEW_BASE_REV || '1d4b28f81f9f23036396d2bd66c62825f6eeaadd'
  const baseline = cp.execFileSync('git', ['show', `${revision}:web/pc-app/src/components/BudgetSixMonthReview.vue`], { encoding: 'utf8' })
  fs.readFileSync = function (file, ...args) {
    if (typeof file === 'string' && path.resolve(file) === target) return typeof args[0] === 'string' || args[0]?.encoding ? baseline : Buffer.from(baseline)
    return original.call(this, file, ...args)
  }
}
process.argv = [process.execPath, entry, '--noEmit', '-p', path.resolve(__dirname, '../tsconfig.json')]
require(entry)
