// Offline source/contract inventory. This never executes business HTTP or old code.
import { existsSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '../../..')
const git = (...args) => execFileSync('git', ['--no-pager', ...args], { cwd: root, encoding: 'utf8' }).trim()
const contractCommit = 'e87d1044a8390b001af2e0db564531d14b61ae96'
const reviewedHead = '33bdb3b916b8a344966742ebba280752a5dd0bfc'
const changedFiles = git('diff', '--name-only', `${contractCommit}..${reviewedHead}`).split('\n')
const protectedChanges = changedFiles.filter(path => /^(backend|sql|android-app|web\/shared|scripts)\//.test(path) || path === 'web/package-lock.json')
if (protectedChanges.length) throw Error(`Protected source changed: ${protectedChanges.join(', ')}`)
const files = directory => readdirSync(resolve(root, directory), { withFileTypes: true }).flatMap(entry => entry.isDirectory() ? files(`${directory}/${entry.name}`) : [`${directory}/${entry.name}`])
const sourceFiles = files('web/pc-app/src').filter(path => /\.(vue|ts|css)$/.test(path))
const callsites = [], endpoints = []
for (const path of sourceFiles) {
  const source = readFileSync(resolve(root, path), 'utf8')
  const pattern = /\b(\w+(?:Api|Store)|apiClient)\.(\w+)\s*(?:<[^;\n]*?>)?\s*\(/g
  for (const match of source.matchAll(pattern)) callsites.push({ file: path, line: source.slice(0, match.index).split('\n').length, call: `${match[1]}.${match[2]}` })
}
for (const path of files('web/shared/src/api').filter(path => path.endsWith('.ts'))) {
  const source = readFileSync(resolve(root, path), 'utf8')
  for (const match of source.matchAll(/apiClient\.(get|post|put|patch|delete)(?:<[^;\n]*?>)?\(\s*([`'"])(.*?)\2/g)) endpoints.push({ file: path, line: source.slice(0, match.index).split('\n').length, method: match[1].toUpperCase(), path: match[3] })
}
const commits = git('log', '--reverse', '--format=%H%x09%s', `${contractCommit}..${reviewedHead}`).split('\n').map(line => {
  const [hash, subject] = line.split('\t')
  return { hash, subject, files: git('diff-tree', '--no-commit-id', '--name-only', '-r', hash).split('\n') }
})
const browserFixtures = files('web/pc-app/tests').filter(path => path.endsWith('.browser.js')).map(path => ({ file: path, sha256: createHash('sha256').update(readFileSync(resolve(root, path))).digest('hex') }))
const report = { contractCommit, reviewedHead, comparison: 'read-only Git diff and R2-06 contract; no old source materialized or executed', commits, commitCount: commits.length, changedFileCount: changedFiles.length, changedFiles, protectedChanges, sourceFileCount: sourceFiles.length, sourceFiles, callsiteCount: callsites.length, callsites, sharedEndpointCount: endpoints.length, endpoints, browserFixtures, caveat: 'Inventory counts are source evidence, not proof of backend authorization or production deployment. See release review for per-page semantic checks.' }
writeFileSync(resolve(root, 'web/pc-app/delivery/r2-20-inventory.json'), JSON.stringify(report, null, 2) + '\n')
const mapperPath = resolve(root, 'web/pc-app/output/playwright/r2-06-19-mapper.json')
if (existsSync(mapperPath)) {
  const coverage = JSON.parse(readFileSync(mapperPath, 'utf8'))
  if (coverage['目标版本'] !== reviewedHead || coverage['基线版本'] !== contractCommit) throw Error('Mapper inventory belongs to another review')
  writeFileSync(resolve(root, 'web/pc-app/delivery/r2-20-database-coverage.json'), JSON.stringify({ base: contractCommit, target: reviewedHead, summary: coverage['汇总'], mappers: coverage['Mapper清单'], scripts: coverage['SQL脚本清单'], matrix: coverage['Mapper数据库脚本覆盖矩阵'], manualReview: coverage['强制人工复核'], caveat: 'Read-only repository script coverage. No SQL executed; no schema, migration, rollback or production validation.' }, null, 2) + '\n')
}
console.log(JSON.stringify({ commitCount: commits.length, changedFileCount: changedFiles.length, protectedChanges, sourceFileCount: sourceFiles.length, callsiteCount: callsites.length, sharedEndpointCount: endpoints.length, fixtureCount: browserFixtures.length }))
