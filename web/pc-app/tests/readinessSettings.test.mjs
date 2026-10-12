import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import * as vue from 'vue'
const root = new URL('../', import.meta.url)
const source = await readFile(new URL('src/views/Settings.vue', root), 'utf8')
async function setup({ admin = true, fail = false, confirm = true } = {}) {
  const calls = []
  const members = [{ userId: 1, username: 'fixture', role: admin ? 'ADMIN' : 'MEMBER' }]
  const store = vue.reactive({ user: { id: 1, familyId: 2, nickname: 'original' }, fetchCurrentUser: async () => { calls.push('me') } })
  const api = new Proxy({}, { get: (target, name) => target[name] ?? (async (...args) => { calls.push([name, ...args]); if (fail) throw Error('private detail'); return members }) })
  const shared = { useUserStore: () => store, userApi: api, familyApi: api }
  globalThis.__settingsTest = { vue: { ...vue, onMounted() {} }, shared, element: { ElNotification: { success() {}, warning() {} }, ElMessageBox: { confirm: async () => { calls.push('confirm'); if (!confirm) throw Error('cancel') } } } }
  let script = compileScript(parse(source).descriptor, { id: 'settings-test' }).content
  script = script.replace(/import AppearanceSettings[^\n]+/g, 'const AppearanceSettings = {}')
    .replace(/import \{([^}]+)\} from 'vue'/g, (_, names) => `const {${names.replace(/ as /g, ': ')}} = globalThis.__settingsTest.vue`)
    .replace(/import \{([^}]+)\} from 'element-plus'/g, (_, names) => `const {${names.split(',').filter(n => !n.trim().startsWith('type ')).join(',')}} = globalThis.__settingsTest.element`)
    .replace(/import \{([^}]+)\} from '@wealth-hub\/shared'/g, (_, names) => `const {${names.split(',').filter(n => !n.trim().startsWith('type ')).join(',')}} = globalThis.__settingsTest.shared`)
  const js = ts.transpileModule(script, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText
  const module = await import(`data:text/javascript,${encodeURIComponent(js)}#${Math.random()}`)
  return { state: module.default.setup({}, { expose() {} }), calls, store, api }
}
test('settings forms validate before API, preserve fields, and clear password only on success', async () => {
  const { state: s, calls } = await setup()
  s.profileFormRef.value = { validate: async () => false }
  await s.saveProfile(); assert.equal(calls.length, 0)
  s.profileFormRef.value.validate = async () => true
  Object.assign(s.profileForm, { nickname: 'fixture', email: 'fixture@example.invalid', phone: '000' })
  await s.saveProfile()
  assert.deepEqual(calls[0], ['updateProfile', { nickname: 'fixture', email: 'fixture@example.invalid', phone: '000' }])
  s.pwdFormRef.value = { validate: async () => true, clearValidate() {} }
  Object.assign(s.pwdForm, { oldPassword: 'fixture-old', newPassword: 'fixture-new', confirmPassword: 'fixture-new' })
  await s.changePassword()
  assert.deepEqual(calls.at(-1), ['changePassword', { oldPassword: 'fixture-old', newPassword: 'fixture-new' }])
  assert.equal(s.pwdForm.oldPassword, '')
  const failed = await setup({ fail: true })
  failed.state.pwdFormRef.value = { validate: async () => true }
  failed.state.pwdForm.oldPassword = 'fixture-old'
  await failed.state.changePassword()
  assert.equal(failed.state.pwdForm.oldPassword, 'fixture-old')
  assert.doesNotMatch(failed.state.settingsError.value, /private detail/)
})
test('family mutations require verified admin and explicit confirmation; cancellation sends no mutation', async () => {
  for (const admin of [false, true]) {
    const { state: s, calls } = await setup({ admin })
    await s.reloadMembers()
    assert.equal(s.canManageFamily.value, admin)
    await s.removeMember(3); await s.onChangeRole(3, 'ADMIN')
    assert.equal(calls.filter(c => Array.isArray(c) && c[0] === 'removeMember').length, Number(admin))
    assert.equal(calls.filter(c => c === 'confirm').length, admin ? 2 : 0)
  }
  const cancelled = await setup({ confirm: false })
  await cancelled.state.reloadMembers(); await cancelled.state.removeMember(3); await cancelled.state.onChangeRole(3, 'ADMIN')
  assert.equal(cancelled.calls.filter(c => Array.isArray(c) && ['removeMember', 'updateMemberRole'].includes(c[0])).length, 0)
  assert.equal(cancelled.state.familySaving.value, false)
})
test('failed member read clears previous role evidence and no-family is neutral', async () => {
  const { state: s, store } = await setup({ fail: true })
  s.members.value = [{ userId: 1, role: 'ADMIN' }]
  await s.reloadMembers(); assert.equal(s.members.value.length, 0); assert.equal(s.canManageFamily.value, false)
  assert.match(s.membersError.value, /读取失败/)
  store.user.familyId = undefined; await s.reloadMembers(); assert.equal(s.membersError.value, '')
})
test('switching identity invalidates admin evidence, including a late member response', async () => {
  const { state: s, store, calls, api } = await setup()
  await s.reloadMembers(); assert.equal(s.canManageFamily.value, true)
  store.user.familyId = 3
  assert.equal(s.canManageFamily.value, false)
  await s.submitAddMember(); assert.equal(calls.filter(c => Array.isArray(c) && c[0] === 'addMember').length, 0)
  let resolve
  api.getMembers = () => new Promise(r => { resolve = r })
  const pending = s.reloadMembers()
  store.user.familyId = 4
  resolve([{ userId: 1, role: 'ADMIN' }]); await pending
  assert.equal(s.canManageFamily.value, false); assert.equal(s.members.value.length, 0)
})
test('both views compile; appearance and business remain separated with keyboard chapters', async () => {
  for (const file of ['Settings.vue', 'DataReadiness.vue']) {
    const view = await readFile(new URL(`src/views/${file}`, root), 'utf8')
    assert.deepEqual(compileTemplate({ source: parse(view).descriptor.template.content, filename: file, id: file }).errors, [])
    assert.match(view, /section-nav/); assert.match(view, /focus-visible/)
    assert.doesNotMatch(view, /localStorage|resetLedger|exportAll|productionConfig|v-html/)
  }
  const readiness = await readFile(new URL('src/views/DataReadiness.vue', root), 'utf8')
  assert.match(readiness, /\['ASSETS', 'MARKET', 'GOALS', 'BUDGETS'\]/)
  assert.match(readiness, /<details><summary>证据详情与人工核对/)
  assert.match(source, /还原资料输入/)
})
