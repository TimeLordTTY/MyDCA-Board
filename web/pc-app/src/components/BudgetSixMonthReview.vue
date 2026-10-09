<template>
  <article class="budget-review">
    <header><div><small>MONTHLY REVIEW · 只读回顾</small><h2>近六个月预算回顾</h2></div><button class="btn" :aria-expanded="open" @click="toggle">{{ open ? '关闭回顾' : '展开回顾' }}</button></header>
    <template v-if="open">
      <p>按月并列已建预算，不合并不同计划。家庭口径仅为后端授权的当前用户流水，不代表家庭总现金流。预留仅为计划。</p>
      <div class="filters"><label>作用域<select v-model="scope"><option value="PERSONAL">个人</option><option value="FAMILY">家庭（需管理员权限）</option></select></label><label>币种<select v-model="currency"><option v-for="c in ['CNY','USD','HKD','EUR','JPY','GBP']" :key="c">{{ c }}</option></select></label><button class="btn" :disabled="loading || disabled" @click="load">{{ loaded ? '刷新回顾' : error ? '重试加载回顾' : '加载回顾' }}</button><button v-if="loading" class="btn" @click="reset">取消加载</button></div>
      <p>每次最多扫描5页 / 100份预算、对比30份，并发3个；超限不生成不完整回顾。</p>
      <p v-if="loading" role="status">正在读取近六个月预算…</p><p v-else-if="error" role="alert" class="warning">{{ error }}</p><p v-else-if="!loaded" role="status">{{ months[0] }} 至 {{ months[5] }} · 点击“加载回顾”读取。</p>
      <div v-if="loaded" class="table-scroll" tabindex="0" role="region" aria-label="近六个月预算回顾表格，可横向滚动">
        <table><caption>{{ months[0] }} 至 {{ months[5] }} · {{ scope === 'PERSONAL' ? '个人' : '家庭（管理员）' }} · {{ currency }} · 金额来源：既有预算对比 API；时间为客户端读取时间，非账务快照</caption><thead><tr><th scope="col">月份 / 预算</th><th scope="col">币种 / 口径</th><th scope="col">计划收入 / 支出</th><th scope="col">计划预留 / 结余</th><th scope="col">实际收入 / 支出</th><th scope="col">实际结余 / 剩余预算</th><th scope="col">完整性 / 超支 / 来源</th><th scope="col">读取时间</th></tr></thead>
          <tbody v-for="month in months" :key="month"><tr v-if="!entries.some(e => e.budget.config.month === month)"><th scope="row">{{ month }}</th><td colspan="7">未创建预算（当前作用域与币种），金额未知</td></tr><tr v-for="e in entries.filter(e => e.budget.config.month === month)" :key="e.budget.id">
            <th scope="row">{{ month }}<br><button class="btn" :disabled="disabled" @click="select(e.budget)">{{ e.budget.config.name }} · 查看详情</button><br><small>独立预算 #{{ e.budget.id }}</small></th><td>{{ e.budget.config.currency }}<br>{{ labels[e.budget.config.scope] }}<br>仅所配收入 / 支出分类</td>
            <td>{{ amount(e.comparison.plannedIncome) }} / {{ amount(e.comparison.plannedExpenses) }}</td><td>{{ amount(e.comparison.plannedReserve) }} / {{ amount(e.comparison.plannedSurplus) }}</td>
            <td>{{ actual(e.comparison, e.comparison.actualIncome) }} / {{ actual(e.comparison, e.comparison.actualExpenses) }}</td><td>{{ actual(e.comparison, e.comparison.actualSurplus) }} / {{ actual(e.comparison, e.comparison.remainingBudget) }}</td>
            <td><strong>{{ quality(e.comparison) }}</strong><br>{{ completeActual(e.comparison) ? e.comparison.overspent ? '已超支' : '未超支（本预算口径）' : '超支状态未知' }}<p>未匹配流水：{{ e.comparison.unmatchedPostings ?? '未知' }}</p><p v-for="w in e.comparison.warnings" :key="w" class="warning">{{ w }}</p><details v-if="!completeActual(e.comparison)"><summary>查看已知部分来源（不作实际趋势）</summary><p v-for="(i,n) in e.comparison.items" :key="n">{{ i.item.name }} · {{ labels[i.item.kind] }} · {{ labels[i.quality] || i.quality }} · 已知部分 {{ amount(i.knownActual) }}</p></details></td><td>{{ e.readAt }}</td>
          </tr></tbody>
        </table>
      </div>
    </template>
  </article>
</template>
<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { budgetApi, useUserStore } from '@wealth-hub/shared'
import type { Budget, BudgetComparison } from '@wealth-hub/shared'
import { labels, amount, failure } from './goalBudgetModel'
import { recentMonths, completeActual, readReview, REVIEW_LIMITS } from './budgetReviewModel'
import type { ReviewEntry } from './budgetReviewModel'
const props = defineProps<{ disabled: boolean; revision: number }>()
const emit = defineEmits<{ select: [budget: Budget] }>()
const user = useUserStore()
const open = ref(false), loading = ref(false), loaded = ref(false), error = ref('')
const scope = ref('PERSONAL'), currency = ref('CNY'), months = ref(recentMonths()), entries = ref<ReviewEntry[]>([])
let generation = 0, controller: AbortController | null = null
function reset() { generation++; controller?.abort(); controller = null; entries.value = []; loaded.value = false; loading.value = false; error.value = '' }
function toggle() { reset(); open.value = !open.value }
function select(budget: Budget) { reset(); emit('select', budget) }
const quality = (c: BudgetComparison) => c.quality === 'OK' && !completeActual(c) ? '未知 / UNKNOWN（实际字段缺失）' : labels[c.quality] || (String(c.quality) === 'UNAVAILABLE' ? '不可用 / UNAVAILABLE' : '未知 / UNKNOWN')
const actual = (c: BudgetComparison, n: number | null) => completeActual(c) ? amount(n) : quality(c)
async function load() {
  reset(); months.value = recentMonths(); loading.value = true
  const version = generation, active = new AbortController(); controller = active
  const timer = setTimeout(() => { if (version === generation) { reset(); error.value = '回顾加载超时（30秒），请手动重试。' } }, REVIEW_LIMITS.timeoutMs)
  try {
    const result = await readReview(budgetApi, months.value, scope.value, currency.value, active.signal)
    if (version !== generation) return
    entries.value = result; loaded.value = true
  } catch (e) { if (version === generation) { reset(); error.value = failure(e) } }
  finally { clearTimeout(timer); if (version === generation) { loading.value = false; controller = null } }
}
watch([scope, currency, () => props.revision, () => user.token, () => user.user], reset, { deep: true, flush: 'sync' })
onBeforeUnmount(reset)
</script>
<style scoped>
.budget-review{padding:22px;border:1px solid #82958c66;border-top:3px solid #568979;border-radius:14px;margin-bottom:24px}header,.filters{display:flex;align-items:center;gap:16px;flex-wrap:wrap}header{justify-content:space-between}small{color:#568979}h2{margin:8px 0}.filters label{display:grid;gap:6px}select{padding:8px;color:inherit;background:var(--bg,#fff);border:1px solid #82958c66;border-radius:6px}.table-scroll{overflow:auto}table{border-collapse:collapse;min-width:1100px;width:100%;text-align:left;font-variant-numeric:tabular-nums}th,td{padding:12px;border-bottom:1px solid #82958c33;vertical-align:top}thead{background:#56897915}caption{text-align:left;padding:16px 0}.warning{color:#c37c35}.btn{margin:4px}button:focus-visible,select:focus-visible,summary:focus-visible,.table-scroll:focus-visible{outline:2px solid #568979;outline-offset:3px}
</style>
