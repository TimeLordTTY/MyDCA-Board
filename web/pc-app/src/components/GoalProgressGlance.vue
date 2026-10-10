<template>
  <article class="goal-glance" aria-label="资产目标速览" :aria-busy="loading">
    <header><div><small>ASSET GOALS · 只读</small><h2>资产目标速览</h2></div><router-link :to="{ name: 'GoalBudgetCenter' }">查看完整目标</router-link></header>
    <div class="filters">
      <label>作用域<select v-model="scope"><option value="PERSONAL">个人</option><option value="FAMILY">家庭（由后端授权）</option></select></label>
      <label>币种<select v-model="currency"><option v-for="c in goalCurrencies" :key="c">{{ c }}</option></select></label>
      <button class="btn" :disabled="loading" @click="load">{{ error ? '手动重试' : loaded ? '手动刷新目标' : '手动加载目标' }}</button>
    </div>
    <p v-if="loading" role="status">正在读取目标与进度…</p><p v-else-if="error" role="alert">{{ error }}</p><p v-else-if="!loaded" role="status">点击后读取；切换作用域或币种后请重新手动加载。</p>
    <p v-if="loaded && !entries.length" role="status">当前作用域与币种没有进行中目标；无目标不等于资产为0。</p>
    <div class="goals">
      <section v-for="e in entries" :key="e.goal.id" class="goal" :aria-label="e.goal.config.name">
        <h3>{{ e.goal.config.name }}</h3><small>{{ e.goal.config.currency }} · {{ e.goal.config.scope === 'PERSONAL' ? '个人' : '家庭（后端授权）' }} · {{ measures[e.goal.config.measure] }}</small>
        <dl><div><dt>目标金额</dt><dd>{{ goalAmount(e.goal.config.targetValue) }}</dd></div><div><dt>目标日期</dt><dd>{{ e.goal.config.targetDate }}</dd></div></dl>
        <template v-if="completeGoalProgress(e.progress)"><p class="rate">{{ goalPercent(e.progress) }} <small>{{ e.progress.completed ? '已达目标' : '尚未达目标' }}</small></p><progress :value="Math.max(0, Math.min(1, e.progress.completionRate!))" max="1" aria-label="目标完成率"></progress><p>当前金额 {{ goalAmount(e.progress.currentValue) }}</p></template>
        <p v-else>完成率与达标状态未知<span v-if="e.progress.quality === 'PARTIAL'"> · 部分已知金额 {{ goalAmount(e.progress.knownValue) }}</span></p>
        <p>质量：{{ e.progress.quality === 'OK' && !completeGoalProgress(e.progress) ? '未知（完整进度契约不足）' : qualities[e.progress.quality] }} · {{ goalReason(e.progress) }}</p>
        <p>数据截至 {{ validGoalDate(e.progress.asOfDate) ? e.progress.asOfDate : '未知' }}</p><p>{{ goalCountdown(e.progress, e.goal.config.targetDate) }}</p>
      </section>
    </div>
    <footer>按目标日期仅显示最近3项进行中目标，各目标独立展示，不合计、不换汇。进度来自既有只读接口，达标不创建订单、转账或预留资金。</footer>
  </article>
</template>
<script setup lang="ts">
import { ref, watch, onBeforeUnmount, onDeactivated } from 'vue'
import { useRoute } from 'vue-router'
import { goalApi, useUserStore } from '@wealth-hub/shared'
import { goalCurrencies, GOAL_GLANCE_LIMITS, readGoalGlance, completeGoalProgress, goalAmount, goalPercent, goalReason, goalCountdown, validGoalDate, goalGlanceFailure } from './goalProgressGlanceModel'
import type { GoalGlanceEntry } from './goalProgressGlanceModel'
const user = useUserStore(), route = useRoute()
const scope = ref('PERSONAL'), currency = ref('CNY'), entries = ref<GoalGlanceEntry[]>([])
const loading = ref(false), loaded = ref(false), error = ref('')
const measures = { TOTAL_ASSETS: '总资产', CASH: '现金', POSITION_VALUE: '持仓市值' }
const qualities = { OK: '完整 / OK', PARTIAL: '部分已知 / PARTIAL', UNKNOWN: '未知 / UNKNOWN' }
let generation = 0, timer: ReturnType<typeof setTimeout> | undefined
function reset() { generation++; clearTimeout(timer); entries.value = []; loading.value = false; loaded.value = false; error.value = '' }
async function load() {
  reset(); loading.value = true
  const version = generation, token = user.token, identity = user.user, path = route.fullPath
  const selectedScope = scope.value, selectedCurrency = currency.value
  // APIs have no AbortSignal: invalidation also prevents subsequent pages/progress requests.
  const isCurrent = () => version === generation && token === user.token && identity === user.user && path === route.fullPath && selectedScope === scope.value && selectedCurrency === currency.value
  timer = setTimeout(() => { if (isCurrent()) { reset(); error.value = '加载超时（30秒），请手动重试。' } }, GOAL_GLANCE_LIMITS.timeoutMs)
  try {
    const result = await readGoalGlance(goalApi, selectedScope, selectedCurrency, isCurrent)
    if (!isCurrent()) return
    entries.value = result; loaded.value = true
  } catch (e) { if (isCurrent()) { reset(); error.value = goalGlanceFailure(e) } }
  finally { if (isCurrent()) { clearTimeout(timer); loading.value = false } }
}
watch([scope, currency, () => user.token, () => user.user, () => route.fullPath], reset, { deep: true, flush: 'sync' })
onBeforeUnmount(reset)
onDeactivated(reset)
</script>
<style scoped>
.goal-glance{margin-bottom:24px;padding:22px;border:1px solid #82958c66;border-top:3px solid #568979;border-radius:14px}header,.filters{display:flex;align-items:center;gap:16px;flex-wrap:wrap}header{justify-content:space-between}h2{margin:8px 0 18px}small,footer,dt{color:var(--text-secondary,#64756d)}.filters label{display:grid;gap:6px}select{padding:8px;color:inherit;background:var(--bg,#fff);border:1px solid #82958c66;border-radius:6px}.goals{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,260px),1fr));gap:16px}.goal{margin-top:18px;padding:16px;background:#5689790a;border-left:3px solid #568979;min-width:0;overflow-wrap:anywhere}.goal h3{margin:0 0 8px}dl{display:flex;flex-wrap:wrap;gap:20px}dt{font-size:13px}dd{margin:6px 0;font-weight:600;font-variant-numeric:tabular-nums}.rate{font-size:26px;font-variant-numeric:tabular-nums}.rate small{font-size:13px}progress{width:100%;accent-color:#568979}footer{margin-top:18px;font-size:12px;line-height:1.7}button:focus-visible,select:focus-visible,a:focus-visible{outline:2px solid #568979;outline-offset:3px}
</style>
