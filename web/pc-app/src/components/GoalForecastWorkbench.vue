<template>
  <section class="forecast" :aria-busy="busy">
    <header><small>SCENARIO DESK · 只读情景</small><h2>未来的目标，需要多少月？</h2><p>两套临时假设，逐月核对现金流。后台只读测算，不保存输入或改变资产、预算与流水。</p></header>
    <p class="planning-boundary">每个目标独立观察，不合并跨币种资产。共享结余只计算一次；达标或超额进度不代表可提现余额。</p>
    <p v-if="busy" role="status">正在读取授权数据，请稍候…</p>
    <p v-if="error" role="alert" class="warning">{{ error }} <button class="btn" :disabled="busy" @click="retry">人工重试</button></p>
    <button class="btn" :disabled="busy" @click="load">重新读取目标与预算</button>
    <p v-if="!busy && !error && !goals.length">暂无目标，请先在资产目标中创建规划。</p>
    <form @submit.prevent="run">
      <label>目标模式<select v-model="multiple" :disabled="busy" @change="invalidate(); ids = []"><option :value="false">单目标</option><option :value="true">多目标共享现金流（2至8项）</option></select></label>
      <fieldset :disabled="busy"><legend>选择目标 · {{ multiple ? '多选' : '单选' }}</legend><label v-for="g in goals" :key="g.id" class="choice"><input :key="`${g.id}:${multiple}`" v-model="ids" :type="multiple ? 'checkbox' : 'radio'" :value="g.id" @change="invalidate">{{ g.config.name }} · {{ labels[g.config.scope] }} / {{ g.config.currency }} · {{ labels[g.config.state] }}</label></fieldset>
      <button v-if="goalMore" type="button" class="btn" :disabled="busy" @click="more('goals')">加载更多目标</button><button v-if="budgetMore" type="button" class="btn" :disabled="busy" @click="more('budgets')">加载更多预算</button>
      <div class="scenario-inputs">
        <fieldset v-for="(s, index) in drafts" :key="index" :disabled="busy" @input="invalidate" @change="invalidate"><legend>情景 {{ index === 0 ? 'A' : 'B' }}</legend>
          <label>起始月<input v-model="s.start" type="month" required></label><label>期间（月）<input v-model.number="s.count" type="number" min="12" max="60" step="1" required></label>
          <label>预算模式<select v-model="s.mode"><option value="PLANNED">计划（统计月之后开始）</option><option value="ACTUAL_PLUS_REMAINING">实际 + 未发生计划（可含统计月）</option></select></label>
          <label>年化投资收益假设（比例，可选）<input v-model="s.rate" inputmode="decimal" placeholder="如 0.03 表示 3%；留空只算零收益"></label><p>收益假设由你输入，范围 -1 至 1；采用后台年化比例 / 12 简单月计息。数学情景独立展示，不是实际余额或市场预测。</p>
          <label>每月外部额外储蓄<input v-model="s.extra" inputmode="decimal" placeholder="未填写：不增加外部资金"></label>
          <p>单目标以可用结余上限作为投入；多目标另填每项目标额度。额外储蓄来自预算之外，不重复计算收入。</p>
          <label v-for="g in chosen" v-show="multiple" :key="g.id">{{ g.config.name }} · 月度投入额度<input v-model="s.allocations[g.id]" inputmode="decimal" placeholder="未填写：UNKNOWN，不自动分配"></label>
          <details><summary>逐月预算与覆盖声明（{{ months(s).length }}个月）</summary><p>仅匹配月份、币种、作用域的预算可选。缺失预算或未确认覆盖将保留 UNKNOWN/PARTIAL。</p>
            <div v-for="m in months(s)" :key="m" class="month-input"><label>{{ m }}<select v-model="s.budgets[m]"><option value="">未选择 / UNKNOWN</option><option v-for="b in matching(m)" :key="b.id" :value="b.id">{{ b.config.name }} · {{ b.config.currency }}</option></select></label><label class="choice"><input v-model="s.covered[m]" type="checkbox">已确认完整覆盖该月现金流</label></div>
          </details>
        </fieldset>
      </div>
      <p v-if="validation" role="alert" class="warning">{{ validation }}</p><button class="btn primary" :disabled="busy || !goals.length">手动计算两套情景</button>
    </form>
    <p v-if="source" class="warning">{{ source }}</p>
    <div v-if="results.length" class="scenario-results">
      <article v-for="s in results" :key="s.name"><h3>{{ s.name }}</h3><p v-for="w in s.warnings" :key="w" class="warning">{{ w }}</p>
        <p v-for="m in s.months.filter(m => m.overLimit !== false)" :key="m.month" class="warning">{{ m.month }}：{{ m.overLimit === true ? '目标之间争用：总投入超出月度结余上限' : '共享上限未知 / UNKNOWN，不能判断可行性' }}（{{ amount(m.specifiedTotal) }} / {{ amount(m.sharedUpperBound) }}）</p>
        <section v-for="g in s.goals" :key="g.goalId"><h4>{{ name(g.goalId) }} · {{ g.forecast.currency }}</h4><p :class="{ warning: stale(g.forecast) || g.forecast.baseline.quality !== 'OK' }">{{ labels[g.forecast.baseline.quality] }} · 统计日 {{ g.forecast.asOfDate }} {{ stale(g.forecast) ? '· 数据过期，请重新核对' : '' }}</p>
          <p>实际资产读数 {{ amount(g.forecast.actualProgress.currentValue) }} · 已知部分 {{ amount(g.forecast.actualProgress.knownValue) }} · {{ g.forecast.actualProgress.reason }}</p>
          <p>预计达成月：{{ achievement(g.forecast) }} · 目标日期 {{ g.targetDate }}</p>
          <p v-if="g.deadlineStatus === 'AFTER_TARGET_MONTH'" class="warning">时间不足：预计达成月晚于目标月份。</p>
          <details><summary>假设、来源与计算公式</summary><p>{{ g.forecast.baseline.assumption }}</p><p>计划结余 = 收入 − 支出 − 预留 + 外部额外储蓄；上限最低为0。实际模式采用已发生金额加各项未发生计划，当月扣除已计入资产的实际结余。预算赤字独立提示。累计进度 = 起始进度 + 投入 + 数学收益；缺口 = max(目标金额 − 累计进度, 0)。</p><pre>{{ JSON.stringify(g.forecast.fixedInputs, null, 2) }}</pre></details>
          <div v-for="projection in projections(g.forecast)" :key="projection.title" class="projection"><h4>{{ projection.title }} · {{ labels[projection.value.quality] }}</h4><p>{{ projection.value.assumption }}</p><p>情景达成月：{{ achievement(g.forecast, projection.value) }}。累计进度为假设值，不可提现。</p><div class="table-scroll" tabindex="0" role="region" :aria-label="projection.title + '逐月结果，可横向滚动'"><table><caption>逐月现金流与目标缺口 · {{ projection.title }}</caption><thead><tr><th>月份 / 状态</th><th>收入</th><th>支出</th><th>预留</th><th>结余上限</th><th>投入</th><th>数学收益</th><th>假设累计进度</th><th>缺口</th><th>解释</th></tr></thead><tbody><tr v-for="m in projection.value.months" :key="m.month"><th>{{ m.month }}<br>{{ labels[m.quality] }}</th><td>{{ amount(m.modeledIncome) }}</td><td>{{ amount(m.modeledExpenses) }}</td><td>{{ amount(m.plannedReserve) }}</td><td>{{ amount(m.surplusUpperBound) }}</td><td>{{ amount(m.contribution) }}</td><td>{{ amount(m.mathematicalReturn) }}</td><td>{{ amount(m.cumulativeProgress) }}</td><td>{{ amount(m.remainingGap) }}<meter v-if="m.remainingGap != null" min="0" :max="Math.max(1, Number(chosen.find(x => x.id === g.goalId)?.config.targetValue))" :value="m.remainingGap" aria-label="剩余目标缺口"></meter></td><td>{{ m.reason }}<strong v-if="deficit(m)" class="warning"> · 预算赤字</strong><details v-if="m.actualReading"><summary>实际来源</summary><pre>{{ JSON.stringify(m.actualReading, null, 2) }}</pre></details></td></tr></tbody></table></div></div>
        </section>
      </article>
    </div>
  </section>
</template>
<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, onDeactivated, watch } from 'vue'
import { goalApi, budgetApi, goalForecastApi, useUserStore } from '@wealth-hub/shared'
import type { Goal, Budget } from '@wealth-hub/shared'
import type { Forecast, ForecastMonth, ForecastRequest, ScenarioResult, Projection } from '@wealth-hub/shared'
import { labels, amount, failure } from './goalBudgetModel'
import { monthRange, validateForecast } from './goalForecastModel'
const goals = ref<Goal[]>([]), budgets = ref<Budget[]>([]), ids = ref<string[] | string>([]), multiple = ref(false)
const busy = ref(false), error = ref(''), validation = ref(''), results = ref<ScenarioResult[]>([]), source = ref('')
const goalMore = ref(false), budgetMore = ref(false)
let goalPage = 0, budgetPage = 0, lastAction: 'load' | 'run' | 'goals' | 'budgets' = 'load'
const nextMonth = monthRange(new Date().toISOString().slice(0, 7), 12)[1]
const drafts = ref([0, 1].map(() => ({ start: nextMonth, count: 12, mode: 'PLANNED' as ForecastRequest['mode'], extra: '', rate: '', allocations: {} as Record<string, string>, budgets: {} as Record<string, string>, covered: {} as Record<string, boolean> })))
type Draft = typeof drafts.value[number]
const chosen = computed(() => goals.value.filter(g => (Array.isArray(ids.value) ? ids.value : [ids.value]).includes(g.id)))
const months = (s: Draft) => monthRange(s.start, s.count)
const matching = (month: string) => budgets.value.filter(b => b.config.month === month && chosen.value.length && b.config.currency === chosen.value[0].config.currency && b.config.scope === chosen.value[0].config.scope)
const user = useUserStore()
let generation = 0, loadedOwner = '', readingOwner = ''
const identity = () => `${user.token || ''}:${JSON.stringify(user.user)}`
function invalidate() { generation++; busy.value = false; results.value = []; source.value = ''; validation.value = '' }
function resetIdentity() {
  loadedOwner = ''; readingOwner = ''
  invalidate(); goals.value = []; budgets.value = []; ids.value = []; goalMore.value = false; budgetMore.value = false
  error.value = '身份或权限已变化，请重新读取当前用户的目标与预算。'
  drafts.value.forEach(s => { s.allocations = {}; s.budgets = {}; s.covered = {}; s.extra = ''; s.rate = '' })
}
function current(version: number, owner: string) {
  if (version !== generation) return false
  if (owner !== identity()) { resetIdentity(); return false }
  return true
}
function authorized() {
  if (loadedOwner === identity()) return true
  resetIdentity(); return false
}
watch([() => user.token, () => user.user], ([token, profile], [priorToken, priorProfile]) => {
  resetIdentity()
  if (token && token === priorToken && !priorProfile && profile) void load()
}, { deep: true, flush: 'sync' })
watch([ids, multiple, drafts], () => { if (readingOwner && readingOwner !== identity()) resetIdentity(); else invalidate() }, { deep: true, flush: 'sync' })
onBeforeUnmount(resetIdentity)
onDeactivated(resetIdentity)
async function load() {
  if (busy.value) return
  lastAction = 'load'; invalidate(); error.value = ''; goals.value = []; budgets.value = []; ids.value = []; goalMore.value = false; budgetMore.value = false
  loadedOwner = ''; const version = generation, owner = identity(); readingOwner = owner
  busy.value = true
  try { const [g, b] = await Promise.all([goalApi.list(), budgetApi.list()]); if (!current(version, owner)) return; loadedOwner = owner; goals.value = g; budgets.value = b; goalPage = 0; budgetPage = 0; goalMore.value = g.length === 20; budgetMore.value = b.length === 20 } catch (e) { if (current(version, owner)) error.value = failure(e) } finally { if (version === generation) busy.value = false }
}
async function more(kind: 'goals' | 'budgets') {
  if (busy.value) return
  if (!authorized()) return
  lastAction = kind; invalidate(); busy.value = true; error.value = ''
  const version = generation, owner = identity(); readingOwner = owner
  try { if (kind === 'goals') { const rows = await goalApi.list(goalPage + 1); if (!current(version, owner)) return; goals.value.push(...rows); goalPage++; goalMore.value = rows.length === 20 } else { const rows = await budgetApi.list(budgetPage + 1); if (!current(version, owner)) return; budgets.value.push(...rows); budgetPage++; budgetMore.value = rows.length === 20 } } catch (e) { if (current(version, owner)) error.value = failure(e) } finally { if (version === generation) busy.value = false }
}
function request(s: Draft): ForecastRequest { const range = months(s); return { startMonth: s.start, endMonth: range[range.length - 1] ?? '', mode: s.mode, monthlyExtraSavings: s.extra || null, annualRate: s.rate || null, months: range.map(month => ({ month, budgetId: s.budgets[month] || null, cashflowCovered: s.covered[month] === true })) } }
async function run() {
  if (busy.value) return
  if (!authorized()) return
  invalidate(); error.value = ''; lastAction = 'run'
  const inputs = drafts.value.map((s, i) => ({ name: `情景 ${i === 0 ? 'A' : 'B'}`, cashflow: request(s), allocations: chosen.value.map(g => ({ goalId: g.id, monthlyAmount: s.allocations[g.id] || null })) }))
  if (multiple.value && chosen.value.length < 2) { validation.value = '多目标模式请选择2至8个目标'; return }
  for (const input of inputs) { validation.value = validateForecast(chosen.value, budgets.value, input.cashflow, input.allocations.map(a => a.monthlyAmount ?? '')); if (validation.value) return }
  busy.value = true
  const version = generation, owner = identity(); readingOwner = owner
  const target = chosen.value[0]
  try {
    if (multiple.value) { const result = await goalForecastApi.compare(inputs); if (!current(version, owner)) return; results.value = result.scenarios; source.value = `${result.source} · 读取时间 ${result.readStartedAt} — ${result.readCompletedAt}` }
    else {
      const forecasts = await Promise.all(inputs.map(i => goalForecastApi.forecast(target.id, i.cashflow)))
      if (!current(version, owner)) return
      results.value = forecasts.map((forecast, i) => ({ name: inputs[i].name, assumptions: inputs[i], warnings: [], months: [], goals: [{ goalId: forecast.goalId, targetDate: target.config.targetDate, allocationStatus: 'SPECIFIED', forecast, deadlineStatus: forecast.baseline.achievedMonth && forecast.baseline.achievedMonth > target.config.targetDate.slice(0, 7) ? 'AFTER_TARGET_MONTH' : 'UNKNOWN_OR_NOT_REACHED' }] }))
      source.value = '单目标两次授权只读请求；来源读取非原子快照，各情景统计日见下方。'
    }
  } catch (e) { if (current(version, owner)) { error.value = failure(e); results.value = [] } } finally { if (version === generation) busy.value = false }
}
function retry() { return lastAction === 'run' ? run() : lastAction === 'load' ? load() : more(lastAction) }
const name = (id: string) => goals.value.find(g => g.id === id)?.config.name ?? id
const stale = (f: Forecast) => !/^\d{4}-\d{2}-\d{2}$/.test(f.asOfDate) || f.asOfDate !== new Date().toISOString().slice(0, 10)
function achievement(f: Forecast, projection: Projection = f.baseline) { return stale(f) || projection.quality !== 'OK' ? '未知 / UNKNOWN（数据不完整或过期）' : projection.achievedMonth ?? (projection.outcome === 'UNREACHABLE_WITHIN_RANGE' ? '时间不足：期间内未达目标' : '未知 / UNKNOWN') }
const projections = (f: Forecast) => [{ title: '零收益基线', value: f.baseline }, ...(f.mathematicalScenario ? [{ title: `用户数学收益假设（年化比例 ${f.mathematicalScenario.annualRate}）`, value: f.mathematicalScenario }] : [])]
const deficit = (m: ForecastMonth) => m.modeledIncome != null && m.modeledExpenses != null && m.plannedReserve != null && m.modeledIncome - m.modeledExpenses - m.plannedReserve < 0
onMounted(load)
</script>
<style scoped src="../styles/goals-planning.css"></style>
