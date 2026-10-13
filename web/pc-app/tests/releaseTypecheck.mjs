// Read only the controller's supplied baseline; never materialize an old source tree.
import { readFileSync, readdirSync, writeFileSync, mkdirSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { spawnSync } from 'node:child_process'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '../../..')
const baselinePath = process.argv[2]
if (!baselinePath) throw Error('Controller baseline.json path is required')
const baselineBytes = readFileSync(baselinePath)
const baseline = JSON.parse(baselineBytes)
const supplied = baseline.checks.find(check => check.check === 'types-baseline')
if (baseline.status !== 'passed' || !supplied || !baseline.diagnostics) throw Error('Missing controller baseline evidence')
const log = readFileSync(supplied.log)
const sha = bytes => createHash('sha256').update(bytes).digest('hex')
if (sha(log) !== supplied.sha256) throw Error('Controller baseline log hash mismatch')
const diagnostics = text => {
  const counts = {}
  for (const line of text.split(/\r?\n/)) {
    const match = line.replaceAll('\\', '/').match(/(?:web\/pc-app\/)?(src\/[^\r\n]+?)\(\d+,\d+\): (error TS\d+: .+)/)
    if (match) { const key = `${match[1]}: ${match[2]}`; counts[key] = (counts[key] || 0) + 1 }
  }
  return counts
}
const normalizedBaseline = diagnostics(log.toString())
if (JSON.stringify(Object.entries(normalizedBaseline).sort()) !== JSON.stringify(Object.entries(baseline.diagnostics).sort())) throw Error('Controller report and log disagree')
const compiler = resolve(root, 'web/node_modules/vue-tsc/bin/vue-tsc.js')
const run = config => {
  const result = spawnSync(process.execPath, [compiler, '--noEmit', '-p', config], { cwd: root, encoding: 'utf8' })
  if (result.error || result.status === null) throw result.error || Error('Compiler interrupted')
  const output = result.stdout + result.stderr
  console.log(`CHECK ${config} EXIT ${result.status}\n${output}`)
  return { exitCode: result.status, output }
}
const focused = []
for (const name of readdirSync(resolve(root, 'web/pc-app/tests')).filter(name => /^tsconfig\..+\.json$/.test(name)).sort()) {
  const result = run(`web/pc-app/tests/${name}`)
  focused.push({ config: name, exitCode: result.exitCode, diagnostics: diagnostics(result.output) })
}
const final = run('web/pc-app/tsconfig.json')
const current = diagnostics(final.output)
const added = Object.fromEntries(Object.entries(current).filter(([key, count]) => count > (baseline.diagnostics[key] || 0)).map(([key, count]) => [key, count - (baseline.diagnostics[key] || 0)]))
const removed = Object.fromEntries(Object.entries(baseline.diagnostics).filter(([key, count]) => count > (current[key] || 0)).map(([key, count]) => [key, count - (current[key] || 0)]))
if (sha(readFileSync(baselinePath)) !== sha(baselineBytes) || sha(readFileSync(supplied.log)) !== supplied.sha256) throw Error('Controller evidence changed')
const report = { baselineCommit: baseline.base, baselineLogSha256: supplied.sha256, baselineExitCode: baseline.type_exit_code, baselineCount: baseline.type_error_count, finalExitCode: final.exitCode, finalCount: Object.values(current).reduce((a, b) => a + b, 0), added, removed, diagnostics: current, focused, baselineReadOnly: true }
mkdirSync(resolve(root, 'web/pc-app/delivery'), { recursive: true })
writeFileSync(resolve(root, 'web/pc-app/delivery/r2-20-types.json'), JSON.stringify(report, null, 2) + '\n')
console.log(JSON.stringify(report, null, 2))
if (focused.some(check => check.exitCode !== 0) || Object.keys(added).length || ![0, baseline.type_exit_code].includes(final.exitCode)) process.exitCode = 1
