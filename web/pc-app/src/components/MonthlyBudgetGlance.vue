<template>
  <article class="month-glance" aria-label="本月预算速览" :aria-busy="loading">
    <header><div><small>MONTHLY PLAN · 只读</small><h2>本月预算速览 <span>{{ month }}</span></h2></div><router-link :to="{ name: 'GoalBudgetCenter' }">查看完整预算</router-link></header>
    <div class="filters">
      <label>作用域<select v-model="scope"><option value="PERSONAL">个人</option><option value="FAMILY">家庭（由后端授权）</option></select></label>
      <label>币种<select v-model="currency"><option v-for="c in ['CNY', 'USD', 'HKD', 'EUR', 'JPY', 'GBP']" :key="c">{{ c }}</option></select></label>
      <button class="btn" :disabled="loading" @click="load">{{ loaded ? '刷新本月预算' : '手动加载本月预算' }}</button><button v-if="loading" class="btn" @click="cancel">取消加载</button>
    </div>
    <p v-if="loading" role="status">正在读取本月预算…</p><p v-else-if="error" role="alert">{{ error }}</p><p v-else-if="!loaded" role="status">点击后读取；切换作用域或币种后请重新手动加载。</p>
    <p v-if="loaded && !entries.length" role="status">当前作用域与币种未找到本月计划，金额未知；无计划不等于0元。</p>
    <section v-for="e in entries" :key="e.budget.id" class="plan" :aria-label="e.budget.config.name">
      <h3>{{ e.budget.config.name }} <small>独立预算 #{{ e.budget.id }} · {{ e.budget.config.currency }} · {{ e.budget.config.scope === 'PERSONAL' ? '个人' : '家庭' }}</small></h3>
      <dl><div><dt>计划支出</dt><dd>{{ glanceAmount(e.comparison.plannedExpenses) }}</dd></div><div><dt>计划收入</dt><dd>{{ glanceAmount(e.comparison.plannedIncome) }}</dd></div><div><dt>计划预留</dt><dd>{{ glanceAmount(e.comparison.plannedReserve) }}</dd></div><div><dt>计划结余</dt><dd>{{ glanceAmount(e.comparison.plannedSurplus) }}</dd></div><div><dt>实际支出</dt><dd>{{ actualAmount(e.comparison, e.comparison.actualExpenses) }}</dd></div><div><dt>剩余预算</dt><dd>{{ actualAmount(e.comparison, e.comparison.remainingBudget) }}</dd></div></dl>
      <p>质量：{{ quality(e.comparison) }} · {{ completeActual(e.comparison) ? e.comparison.overspent ? '已超支' : '未超支（本预算口径）' : '超支状态未知' }}</p>
      <p v-for="w in e.comparison.warnings" :key="w">{{ w }}</p><small>来源：既有预算对比 GET · 客户端读取 {{ e.readAt }}</small>
    </section>
    <footer>各份预算单独展示，不求和为整月或家庭总额。家庭数据仅代表后端授权口径；预留是计划，不是实际转账。读取时间不是财务统一快照。最多5页 / 100份列表、20份对比、并发3个、30秒超时。</footer>
  </article>
</template>
<script setup lang="ts">
import { ref, watch, onBeforeUnmount, onDeactivated } from 'vue'
import { useRoute } from 'vue-router'
import { budgetApi, useUserStore } from '@wealth-hub/shared'
import type { BudgetComparison } from '@wealth-hub/shared'
import { completeActual } from './budgetReviewModel'
import { failure } from './goalBudgetModel'
import { localMonth, readMonthlyGlance, glanceAmount, actualAmount, GLANCE_LIMITS } from './monthlyBudgetGlanceModel'
import type { GlanceEntry } from './monthlyBudgetGlanceModel'
const user = useUserStore(), route = useRoute()
const month = ref(localMonth()), scope = ref('PERSONAL'), currency = ref('CNY')
const entries = ref<GlanceEntry[]>([]), loading = ref(false), loaded = ref(false), error = ref('')
let generation = 0, controller: AbortController | null = null, timer: ReturnType<typeof setTimeout> | undefined
function reset() { generation++; controller?.abort(); controller = null; clearTimeout(timer); entries.value = []; loading.value = false; loaded.value = false; error.value = ''; month.value = localMonth() }
function cancel() { reset(); error.value = '加载已取消，请手动重试。' }
const quality = (c: BudgetComparison) => completeActual(c) ? '完整 / OK' : c.quality === 'PARTIAL' ? '部分已知 / PARTIAL' : String(c.quality) === 'UNAVAILABLE' ? '不可用 / UNAVAILABLE' : '未知 / UNKNOWN'
async function load() {
  reset(); loading.value = true
  const version = generation, active = new AbortController(); controller = active
  timer = setTimeout(() => { if (version === generation) { reset(); error.value = '加载超时（30秒），请手动重试。' } }, GLANCE_LIMITS.timeoutMs)
  try {
    const result = await readMonthlyGlance(budgetApi, month.value, scope.value, currency.value, active.signal)
    if (version !== generation) return
    entries.value = result; loaded.value = true
  } catch (e) { if (version === generation) { reset(); error.value = failure(e) } }
  finally { if (version === generation) { clearTimeout(timer); controller = null; loading.value = false } }
}
watch([scope, currency, () => user.token, () => user.user, () => route.fullPath], reset, { deep: true, flush: 'sync' })
onBeforeUnmount(reset)
onDeactivated(reset)
</script>
<style scoped>
.month-glance{margin-bottom:24px;padding:22px;border:1px solid #82958c66;border-top:3px solid #568979;border-radius:14px}header,.filters{display:flex;align-items:center;gap:16px;flex-wrap:wrap}header{justify-content:space-between}h2{margin:8px 0 18px}h2 span{font-size:16px;font-weight:400}small,footer{color:var(--text-secondary,#64756d)}.filters label{display:grid;gap:6px}select{padding:8px;color:inherit;background:var(--bg,#fff);border:1px solid #82958c66;border-radius:6px}.plan{margin:18px 0;padding:16px;background:#5689790a;border-left:3px solid #568979}.plan h3{margin:0 0 14px}.plan h3 small{display:block;font-weight:400;margin-top:6px}dl{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:16px}dt{font-size:13px}dd{margin:6px 0;font-variant-numeric:tabular-nums;font-weight:600}footer{margin-top:18px;font-size:12px;line-height:1.7}button:focus-visible,select:focus-visible,a:focus-visible{outline:2px solid #568979;outline-offset:3px}
</style>
