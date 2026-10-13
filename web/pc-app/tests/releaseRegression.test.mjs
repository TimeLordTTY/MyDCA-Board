import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'

// Exercise fixture network guards without a browser or server. Stop before navigation.
const fixtures = [
  ['foundation', 3107], ['navigation', 3108],
  ['accountsProducts', 3110], ['holdingsExperience', 3110], ['ledgerExperience', 3110],
  ['draftWorkflow', 3113], ['ordersSettlements', 3114], ['goalsPlanning', 3115],
  ['strategyLab', 3115], ['riskRebalance', 3115], ['readinessSettings', 3115], ['portalAuth', 3115],
]
async function guard(name) {
  const source = await readFile(new URL(`${name}.browser.js`, import.meta.url), 'utf8')
  const fixture = vm.runInNewContext(`(${source})`, { URL, console, Date })
  const stop = Error('navigation intentionally stopped')
  let handler
  await assert.rejects(fixture({ on() {}, async route(pattern, callback) { assert.equal(pattern, '**/*'); handler = callback }, async addInitScript() {}, async setViewportSize() {}, async goto() { throw stop } }), error => error === stop)
  return handler
}
function route(url, method = 'GET', type = 'fetch') {
  const outcomes = []
  return { outcomes, request: () => ({ url: () => url, method: () => method, resourceType: () => type, postDataJSON: () => ({}) }), abort: async () => outcomes.push('abort'), fulfill: async () => outcomes.push('fixture'), continue: async () => outcomes.push('network') }
}
for (const [name, port] of fixtures) {
  test(`${name}: business API stays intercepted and unknown/foreign requests abort`, async () => {
    const handle = await guard(name), origin = `http://127.0.0.1:${port}`
    for (const [url, method, expected] of [
      [`${origin}/api/v2/users/me`, 'GET', 'fixture'],
      [`${origin}/api/v2/unapproved-business`, 'GET', 'abort'],
      [`${origin}/api/v2/unapproved-business`, 'POST', 'abort'],
      [`http://127.0.0.1:${port + 100}/api/v2/users/me`, 'GET', 'abort'],
      ['https://outside.invalid/api/v2/users/me', 'GET', 'abort'],
      [`${origin}/unapproved-business`, 'GET', 'abort'],
      [`${origin}/unapproved-business`, 'POST', 'abort'],
    ]) {
      const request = route(url, method)
      await handle(request)
      assert.deepEqual(request.outcomes, [expected], `${method} ${url}`)
    }
  })
}
