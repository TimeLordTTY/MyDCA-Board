<template>
  <section class="goal-center" :aria-busy="busy">
    <header><div><small>WEALTH PLANNING · 财富规划</small><h1>目标与预算中心</h1><p>管理目标与月度计划，基于已有资产和流水只读观察进度。</p></div><button class="btn" :disabled="busy" @click="refresh">刷新本页</button></header>
    <nav aria-label="规划类型"><button class="btn" :aria-pressed="tab === 'goals'" :disabled="busy" @click="switchTab('goals')">资产目标</button><button class="btn" :aria-pressed="tab === 'budgets'" :disabled="busy" @click="switchTab('budgets')">月度预算</button><button class="btn primary" :disabled="busy" @click="edit()">{{ tab === 'goals' ? '创建目标' : '创建预算' }}</button></nav>
    <p v-if="busy" role="status">正在加载 / 保存…</p><p v-if="error" role="alert" class="warning">{{ error }} <button class="btn" :disabled="busy" @click="refresh">重试加载</button></p><p v-if="notice" role="status">{{ notice }}</p>
    <BudgetSixMonthReview v-if="tab === 'budgets'" :disabled="busy" :revision="reviewRevision" @select="observe" />
    <div class="workspace">
      <aside><h2>{{ tab === 'goals' ? '目标列表' : '预算列表' }}</h2><p v-if="!busy && !error && !rows.length">暂无{{ tab === 'goals' ? '目标' : '预算' }}，可先创建一项规划。</p>
        <article v-for="row in rows" :key="row.id" :class="{ selected: selected?.id === row.id }"><h3>{{ row.config.name }}</h3><p>{{ labels[row.config.scope] }} · {{ row.config.currency }} · {{ 'state' in row.config ? labels[row.config.state] : row.config.month }}</p><p v-if="'targetValue' in row.config">目标 {{ amount(row.config.targetValue) }} · {{ row.config.targetDate }}</p><button class="btn" :disabled="busy" @click="observe(row)">详情与{{ tab === 'goals' ? '进度' : '对比' }}</button><button class="btn" :disabled="busy" @click="edit(row)">编辑</button><button v-if="tab === 'budgets' && 'items' in row.config" class="btn" :disabled="busy" @click="copyNextMonth(row)">复制为下月预算</button></article>
        <button v-if="more" class="btn" :disabled="busy" @click="loadMore">加载更多</button>
      </aside>
      <main>
        <form v-if="form" @submit.prevent="save"><h2>{{ editing ? '编辑规划' : '新增规划' }}</h2>
          <p v-if="copied" role="status" class="warning">这是创建新月份的计划草稿，不修改旧预算、不带入实际流水、不自动记账/转账。原金额只是参考，请按新月份确认收入、支出及预留后再保存规划。</p>
          <label>名称<input v-model="form.name" maxlength="200" required :disabled="busy"></label>
          <label>作用域<select v-model="form.scope" :disabled="busy"><option value="PERSONAL">个人</option><option value="FAMILY">家庭（需管理员权限）</option></select></label>
          <label>币种<select v-model="form.currency" :disabled="busy"><option v-for="c in ['CNY', 'USD', 'HKD', 'EUR', 'JPY', 'GBP']" :key="c">{{ c }}</option></select></label>
          <template v-if="'targetValue' in form"><label>目标金额<input v-model="form.targetValue" inputmode="decimal" required :disabled="busy"></label><label>目标日期<input v-model="form.targetDate" type="date" required :disabled="busy"></label><label>统计口径<select v-model="form.measure" :disabled="busy"><option v-for="m in ['TOTAL_ASSETS', 'CASH', 'POSITION_VALUE']" :key="m" :value="m">{{ labels[m] }}</option></select></label><label>备注<textarea v-model="form.note" maxlength="2000" :disabled="busy"></textarea></label><p>当前状态：{{ labels[form.state] }}。暂停、恢复与归档在详情中操作。</p><p>当前资产进度仅支持人民币，其他币种显示 UNKNOWN，不自动换汇。</p></template>
          <template v-else><label>预算月份<input v-model="form.month" type="month" required :disabled="busy"></label><p>预留仅为计划；不对应实际资金划转。收入及支出按既有流水分类对比。</p>
            <fieldset v-for="(item, index) in form.items" :key="index" :disabled="busy"><legend>预算项 {{ index + 1 }}</legend><label>项目名称<input v-model="item.name" required maxlength="200"></label><label>类型<select v-model="item.kind" @change="item.categoryId = null"><option v-for="k in ['INCOME', 'FIXED_EXPENSE', 'FLEXIBLE_EXPENSE', 'RESERVE']" :key="k" :value="k">{{ labels[k] }}</option></select></label><label v-if="item.kind !== 'RESERVE'">流水分类<select v-model="item.categoryId" required><option :value="null" disabled>请选择分类</option><option v-for="c in (item.kind === 'INCOME' ? incomeCategories : expenseCategories)" :key="c.id" :value="c.id">{{ c.categoryL1 }} / {{ c.categoryL2 || '其他' }} (#{{ c.id }})</option></select></label><label>计划金额<input v-model="item.planned" inputmode="decimal" required></label><button type="button" class="btn" @click="form && 'items' in form && form.items.splice(index, 1)">移除项目</button></fieldset>
            <button type="button" class="btn" :disabled="busy || form.items.length >= 100" @click="form.items.push({ name: '', kind: 'FLEXIBLE_EXPENSE', categoryId: null, planned: '' })">添加预算项</button>
          </template>
          <p v-if="formError" role="alert" class="warning">{{ formError }}</p><div><button class="btn primary" :disabled="busy">保存规划</button><button type="button" class="btn" :disabled="busy" @click="cancelForm">取消</button></div>
        </form>
        <article v-else-if="selected"><h2>{{ selected.config.name }}</h2><p>{{ labels[selected.config.scope] }} · {{ selected.config.currency }} · 创建于 {{ selected.createdAt }}</p>
          <template v-if="'state' in selected.config"><p>{{ labels[selected.config.measure] }} · {{ labels[selected.config.state] }} · 目标日期 {{ selected.config.targetDate }}</p><p>{{ selected.config.note || '无备注' }}</p><button v-if="selected.config.state !== 'ARCHIVED'" class="btn" :disabled="busy" @click="changeState(selected.config.state === 'ACTIVE' ? 'PAUSED' : 'ACTIVE')">{{ selected.config.state === 'ACTIVE' ? '暂停目标' : '恢复目标' }}</button><button v-if="selected.config.state !== 'ARCHIVED'" class="btn" :disabled="busy" @click="archivePrompt = true">归档目标</button><div v-if="archivePrompt" role="alert"><p>归档后保留历史目标和只读进度，确认归档？</p><button class="btn" :disabled="busy" @click="changeState('ARCHIVED')">确认归档</button><button class="btn" :disabled="busy" @click="archivePrompt = false">取消</button></div></template>
          <button v-if="tab === 'budgets' && 'items' in selected.config" class="btn" :disabled="busy" @click="copyNextMonth(selected)">复制为下月预算</button>
          <button class="btn" :disabled="busy" @click="observe(selected)">重新读取详情</button>
          <template v-if="progress"><h3>完成率 {{ percent(progress.completionRate) }}</h3><progress v-if="progress.completionRate != null" :value="Math.max(0, Math.min(1, progress.completionRate))" max="1" aria-label="目标完成率"></progress><p>{{ labels[progress.quality] }} · {{ progress.reason }}</p><p>当前金额 {{ amount(progress.currentValue) }} · 已知部分 {{ amount(progress.knownValue) }}</p><p>完成状态：{{ progress.completed == null ? '未知 / UNKNOWN' : progress.completed ? '已达目标' : '尚未达目标' }}</p><p>数据日期 {{ progress.asOfDate }} · {{ progress.overdue ? '已逾期' : '距离目标' }} {{ Math.abs(progress.daysRemaining) }} 天</p></template>
          <template v-if="comparison"><h3>月度计划 / 实际 · {{ labels[comparison.quality] }}</h3><dl><dt>收入</dt><dd>{{ amount(comparison.plannedIncome) }} / {{ amount(comparison.actualIncome) }}</dd><dt>支出</dt><dd>{{ amount(comparison.plannedExpenses) }} / {{ amount(comparison.actualExpenses) }}</dd><dt>计划预留</dt><dd>{{ amount(comparison.plannedReserve) }}</dd><dt>结余（计划已扣预留）</dt><dd>{{ amount(comparison.plannedSurplus) }} / {{ amount(comparison.actualSurplus) }}</dd><dt>剩余支出预算</dt><dd>{{ amount(comparison.remainingBudget) }}</dd></dl><p class="warning">{{ comparison.overspent == null ? '超支状态未知 / UNKNOWN' : comparison.overspent ? '已超支，请审视计划' : '未超支' }}</p><p>未匹配流水 {{ comparison.unmatchedPostings }} 条</p><p v-for="w in comparison.warnings" :key="w" class="warning">{{ w }}</p><div class="table-scroll"><table><caption>预算项目对比（{{ selected.config.currency }}）</caption><thead><tr><th>项目 / 类型</th><th>计划</th><th>实际</th><th>已知部分</th><th>剩余</th><th>状态</th></tr></thead><tbody><tr v-for="(i, n) in comparison.items" :key="n"><th>{{ i.item.name }} / {{ labels[i.item.kind] }}</th><td>{{ amount(i.item.planned) }}</td><td>{{ amount(i.actual) }}</td><td>{{ amount(i.knownActual) }}</td><td>{{ amount(i.remaining) }}</td><td>{{ i.item.kind === 'RESERVE' ? '仅计划' : labels[i.quality] }} {{ i.overspent === true ? '· 已超支' : '' }}</td></tr></tbody></table></div></template>
        </article><article v-else><h2>让计划拥有清晰的刻度</h2><p>选择一项目标查看完成率，或选择月度预算比较计划与实际。部分数据与未知金额保留原始状态。</p></article>
      </main>
    </div>
    <GoalForecastWorkbench />
  </section>
</template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount, watch, ref } from 'vue'
import GoalForecastWorkbench from '../components/GoalForecastWorkbench.vue'
import BudgetSixMonthReview from '../components/BudgetSixMonthReview.vue'
import { goalApi, budgetApi, incomeCategories, expenseCategories, useUserStore } from '@wealth-hub/shared'
import type { Goal, Budget, GoalConfig, BudgetConfig, GoalProgress, BudgetComparison } from '@wealth-hub/shared'
import { labels, amount, percent, failure, validateGoal, validateBudget, copyBudgetNextMonth } from '../components/goalBudgetModel'
const tab = ref<'goals' | 'budgets'>('goals'), rows = ref<(Goal | Budget)[]>([]), selected = ref<Goal | Budget | null>(null)
const busy = ref(false), error = ref(''), notice = ref(''), more = ref(false), archivePrompt = ref(false)
const form = ref<GoalConfig | BudgetConfig | null>(null), editing = ref(''), formError = ref('')
const progress = ref<GoalProgress | null>(null), comparison = ref<BudgetComparison | null>(null)
const reviewRevision = ref(0)
const copied = ref(false), user = useUserStore()
let page = 0, generation = 0
const identity = () => `${user.token || ''}:${JSON.stringify(user.user)}:${localStorage.getItem('token') || ''}`
let visibleOwner = identity()
function current(version: number, owner: string) {
  if (version !== generation) return false
  if (owner !== identity()) { invalidateIdentity(); return false }
  return true
}
function cancelForm() { form.value = null; editing.value = ''; copied.value = false; formError.value = '' }
function invalidateIdentity() {
  generation++; busy.value = false; rows.value = []; more.value = false; page = 0
  cancelForm(); clearDetail(); notice.value = ''; error.value = '身份或权限已变化，请重新加载当前用户的预算。'
}
watch([() => user.token, () => user.user], invalidateIdentity, { deep: true, flush: 'sync' })
onBeforeUnmount(() => { generation++ })
async function copyNextMonth(row: Goal | Budget) {
  if (busy.value || tab.value !== 'budgets' || !('items' in row.config) ||
      !(rows.value.includes(row) || selected.value === row)) return
  if (visibleOwner !== identity()) { invalidateIdentity(); return }
  const version = generation, owner = identity(), sourceScope = row.config.scope
  busy.value = true; error.value = ''; cancelForm(); clearDetail(); notice.value = ''
  try {
    // Fresh authorized detail, never copy comparison/actual fields or cached configs.
    const detail = await budgetApi.detail(row.id)
    if (!current(version, owner) || tab.value !== 'budgets') return
    if (!detail?.config || detail.id !== row.id || detail.config.scope !== sourceScope) throw new Error('预算来源或作用域已变化，请刷新后重试。')
    const draft = copyBudgetNextMonth(detail.config)
    editing.value = ''; form.value = draft; copied.value = true
  } catch (e) { if (version === generation) error.value = `复制预算失败：${failure(e)}` }
  finally { if (version === generation) busy.value = false }
}
function clearDetail() { reviewRevision.value++; selected.value = null; progress.value = null; comparison.value = null; archivePrompt.value = false }
async function refresh() {
  if (busy.value) return
  if (visibleOwner !== identity()) invalidateIdentity()
  const version = generation, owner = identity()
  busy.value = true; error.value = ''; rows.value = []; more.value = false; page = 0; clearDetail()
  try { const result = await (tab.value === 'goals' ? goalApi.list(0) : budgetApi.list(0)); if (!current(version, owner)) return; visibleOwner = owner; rows.value = result; more.value = rows.value.length === 20 } catch (e) { if (version === generation) error.value = failure(e) } finally { if (version === generation) busy.value = false }
}
async function loadMore() {
  if (busy.value) return
  if (visibleOwner !== identity()) { invalidateIdentity(); return }
  const version = generation, owner = identity()
  busy.value = true; error.value = ''
  try { const batch = await (tab.value === 'goals' ? goalApi.list(page + 1) : budgetApi.list(page + 1)); if (!current(version, owner)) return; rows.value.push(...batch); page++; more.value = batch.length === 20 } catch (e) { if (version === generation) { error.value = failure(e); clearDetail() } } finally { if (version === generation) busy.value = false }
}
async function switchTab(next: 'goals' | 'budgets') { if (busy.value) return; tab.value = next; form.value = null; notice.value = ''; await refresh() }
function edit(row?: Goal | Budget) {
  if (busy.value) return
  if (visibleOwner !== identity()) { invalidateIdentity(); return }
  clearDetail(); copied.value = false; formError.value = ''; notice.value = ''; editing.value = row?.id || ''
  form.value = row ? JSON.parse(JSON.stringify(row.config)) : tab.value === 'goals' ? { name: '', targetValue: '', targetDate: '', currency: 'CNY', scope: 'PERSONAL', measure: 'TOTAL_ASSETS', state: 'ACTIVE', note: '' } : { name: '', month: new Date().toISOString().slice(0, 7), currency: 'CNY', scope: 'PERSONAL', items: [{ name: '', kind: 'FLEXIBLE_EXPENSE', categoryId: null, planned: '' }] }
  if (form.value && 'note' in form.value) form.value.note = form.value.note ?? ''
}
async function save() {
  if (busy.value || !form.value) return
  if (visibleOwner !== identity()) { invalidateIdentity(); return }
  formError.value = 'targetValue' in form.value ? validateGoal(form.value) : validateBudget(form.value)
  if (formError.value) return
  const version = generation, owner = identity()
  busy.value = true; error.value = ''
  try {
    const c = JSON.parse(JSON.stringify(form.value))
    if ('targetValue' in c) { if (editing.value) await goalApi.edit(editing.value, c); else await goalApi.create(c) }
    else { if (editing.value) await budgetApi.edit(editing.value, c); else await budgetApi.create(c) }
    if (!current(version, owner)) return
    copied.value = false; form.value = null; notice.value = '规划已保存。'; busy.value = false; await refresh()
  } catch (e) { if (version === generation) formError.value = `保存规划失败：${failure(e)}` } finally { if (version === generation) busy.value = false }
}
async function observe(row: Goal | Budget) {
  if (busy.value) return
  if (visibleOwner !== identity()) { invalidateIdentity(); return }
  const version = generation, owner = identity()
  busy.value = true; error.value = ''; form.value = null; clearDetail()
  try {
    if (tab.value === 'goals') { const detail = await goalApi.detail(row.id); const p = await goalApi.progress(row.id); if (!current(version, owner)) return; selected.value = detail; progress.value = p }
    else { const detail = await budgetApi.detail(row.id); const c = await budgetApi.comparison(row.id); if (!current(version, owner)) return; selected.value = detail; comparison.value = c }
  } catch (e) { if (version === generation) error.value = failure(e) } finally { if (version === generation) busy.value = false }
}
async function changeState(state: GoalConfig['state']) {
  if (busy.value || !selected.value || !('state' in selected.value.config)) return
  const row = selected.value as Goal
  const version = generation, owner = identity()
  busy.value = true; error.value = ''
  try { await goalApi.edit(row.id, { ...row.config, state }); if (!current(version, owner)) return; busy.value = false; notice.value = '目标状态已更新。'; await refresh() } catch (e) { if (version === generation) { error.value = failure(e); clearDetail() } } finally { if (version === generation) busy.value = false }
}
onMounted(refresh)
</script>
<style scoped>
.goal-center{padding:28px;color:var(--text,#243343)}header{display:flex;justify-content:space-between;align-items:center;gap:20px}small{letter-spacing:.16em;color:#568979}h1{font-size:30px;margin:10px 0}h2{font-size:20px}nav{display:flex;gap:12px;margin:24px 0}.workspace{display:grid;grid-template-columns:320px minmax(0,1fr);gap:24px}article,form{padding:22px;border:1px solid #b4c6c533;border-radius:14px;margin-bottom:16px;background:var(--card,#ffffff08)}article.selected{border-color:#568979}label{display:grid;gap:6px;margin:14px 0}input,select,textarea{width:100%;padding:10px;border:1px solid #82958c66;border-radius:6px;color:inherit;background:var(--bg,#ffffff0d);box-sizing:border-box}option{color:#243343;background:#fff}fieldset{border:1px solid #82958c66;margin:18px 0;border-radius:8px}.btn{margin:4px}.warning{color:#c37c35}progress{width:100%;accent-color:#568979}dl{display:grid;grid-template-columns:1fr 2fr;gap:14px}dd{margin:0;font-variant-numeric:tabular-nums}.table-scroll{overflow:auto}table{width:100%;border-collapse:collapse;text-align:left}th,td{padding:12px;border-bottom:1px solid #82958c33}caption{text-align:left;padding:16px 0}button:focus-visible,input:focus-visible,select:focus-visible,textarea:focus-visible{outline:2px solid #568979;outline-offset:3px}@media(max-width:900px){.workspace{grid-template-columns:1fr}header{align-items:flex-start}.goal-center{padding:16px}}
</style>
