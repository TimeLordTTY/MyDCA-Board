// Compare only controller evidence; neither source tree is ever replaced.
import { readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { spawnSync } from 'node:child_process'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '../../..')
const baselinePath = process.argv[2]
if (!baselinePath) throw Error('Controller baseline.json required')
const bytes = readFileSync(baselinePath)
const baseline = JSON.parse(bytes)
const evidence = baseline.checks.find(c => c.check === 'types-baseline')
const hash = b => createHash('sha256').update(b).digest('hex')
if (baseline.status !== 'passed' || !evidence || !baseline.diagnostics) throw Error('Missing baseline evidence')
const log = readFileSync(evidence.log)
if (hash(log) !== evidence.sha256) throw Error('Baseline log hash mismatch')
const diagnostics = text => {
  const result = {}
  for (const line of text.split(/\r?\n/)) {
    const match = line.replaceAll('\\', '/').match(/(?:web\/pc-app\/)?(src\/[^\r\n]+?)\(\d+,\d+\): (error TS\d+: .+)/)
    if (match) { const key = `${match[1]}: ${match[2]}`; result[key] = (result[key] || 0) + 1 }
  }
  return result
}
if (JSON.stringify(Object.entries(diagnostics(log.toString())).sort()) !== JSON.stringify(Object.entries(baseline.diagnostics).sort())) throw Error('Baseline report/log disagree')
const run = config => {
  const result = spawnSync(process.execPath, [resolve(root, 'web/node_modules/vue-tsc/bin/vue-tsc.js'), '--noEmit', '-p', config], { cwd: root, encoding: 'utf8' })
  if (result.error || result.status === null) throw result.error || Error('Compiler interrupted')
  const output = result.stdout + result.stderr
  console.log(`CHECK ${config} EXIT ${result.status}\n${output}`)
  return { config, exitCode: result.status, output, diagnostics: diagnostics(output) }
}
const final = run('web/pc-app/tsconfig.json')
const focused = readdirSync(import.meta.dirname).filter(n => /^tsconfig\..+\.json$/.test(n)).sort().map(n => run(`web/pc-app/tests/${n}`))
const added = Object.fromEntries(Object.entries(final.diagnostics).filter(([key, count]) => count > (baseline.diagnostics[key] || 0)))
const report = { node: process.version, baselineCommit: baseline.base, baselineLogSha256: evidence.sha256, baselineExitCode: baseline.type_exit_code, baselineCount: baseline.type_error_count, baselineDiagnostics: baseline.diagnostics, final, focused, added, removed: baseline.diagnostics, baselineReadOnly: true }
if (hash(readFileSync(baselinePath)) !== hash(bytes) || hash(readFileSync(evidence.log)) !== evidence.sha256) throw Error('Baseline evidence changed')
writeFileSync(resolve(import.meta.dirname, 'typecheckClearance.result.json'), JSON.stringify(report, null, 2) + '\n')
console.log(JSON.stringify(report, null, 2))
if (final.exitCode !== 0 || focused.some(c => c.exitCode !== 0) || Object.keys(added).length) process.exitCode = 1
