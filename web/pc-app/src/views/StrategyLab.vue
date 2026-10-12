<template>
  <main class="lab">
    <header class="lab-header"><div><small>WEALTH HUB / RESEARCH NOTES</small><h1>策略实验室</h1><p>把研究想法，留在有来源的证据里。</p></div><span class="lab-stamp">历史研究<br />人工发起</span></header>
    <p class="lab-boundary"><strong>先看证据，再做研究。</strong>仅使用已导入的历史净值数据。历史回测不代表未来表现。本页不创建订单或交易。</p>
    <nav class="lab-nav" aria-label="策略研究导航"><a href="#research-title">01 研究方案</a><a href="#controlled-run">02 受控回测</a><a href="#history-compare-title">03 历史与报告</a><a href="#candidate-title">04 研究候选</a></nav>
    <ResearchWorkbench :key="contextKey" :candidate="planCandidate" :thresholds="researchThresholds" :datasets="datasets" @cancel="planCandidate = null" @evidence="usePlanRuns" @refresh-history="refreshHistory" />
    <section class="panel" aria-labelledby="controlled-run">
      <div class="heading"><div><small>CONTROLLED BACKTEST / 白名单历史计算</small><h2 id="controlled-run" tabindex="-1">受控回测</h2></div><button :disabled="loading || running" @click="loadData">刷新数据与最近结果</button></div>
      <p>仅可使用后端允许目录中的 CSV 与已支持的策略版本；参数由后端再次校验。</p>
      <form class="run-form" @submit.prevent="run">
      <label>历史数据 <select v-model="data"><option value="">请选择</option><option v-for="name in datasets" :key="name">{{ name }}</option></select></label>
      <label>策略 <select v-model="strategy"><option value="pure_sip">定期投入 v1</option><option value="ma_enhanced">均线增强 v1</option><option value="profit_recycle">收益回收 v1</option><option value="profit_recycle:2">收益回收 v2</option></select></label>
      <label>每次投入 <input v-model.number="contribution" type="number" min="0.01" max="1000000" step="any" /></label>
      <label>投入间隔（天） <input v-model.number="interval" type="number" min="1" max="365" /></label>
      <label v-if="strategy === 'ma_enhanced'">均线窗口 <input v-model.number="window" type="number" min="2" max="365" /></label>
      <label v-if="strategy === 'ma_enhanced'">低点倍数 <input v-model.number="multiplier" type="number" min="1" max="1000000" step="0.1" /></label>
      <label v-if="strategy.startsWith('profit_recycle')">收益阈值 <input v-model.number="threshold" type="number" min="0.001" max="1" step="any" /></label>
      <label v-if="strategy.startsWith('profit_recycle')">回收比例 <input v-model.number="fraction" type="number" min="0.001" max="1" step="any" /></label>
      <button :disabled="loading || running || !validRun">{{ running ? '等待回测响应…' : '运行只读回测' }}</button>
      </form>
      <p v-if="data && !validRun" class="error">请填写允许范围内的有限数字；间隔和均线窗口须为整数。</p>
      <p v-if="runMessage" role="status" class="notice">{{ runMessage }}</p>
    </section>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <p v-if="loading" class="empty" role="status">正在读取回测数据…</p>
    <p v-if="!datasets.length && !loading && !error" class="empty">暂无可用历史数据。请由管理员将 CSV 放入回测数据目录。</p>
    <p v-if="!results.length && !error && !loading" class="empty">暂无最近回测结果；可从下方查看已保存的历史记录。</p>
    <section v-for="result in results" :key="result.run_id" class="panel result">
      <div class="heading"><h2>{{ result.strategy.name }} v{{ result.strategy.version }}</h2><small>{{ result.data_range.start }} 至 {{ result.data_range.end }} · {{ result.data_range.rows }} 条 · {{ result.run_id }}</small></div>
      <div class="metrics"><div v-for="[label, value] in metrics(result)" :key="label"><span>{{ label }}</span><strong>{{ value }}</strong></div></div>
      <p>参数：{{ JSON.stringify(result.strategy.params) }}</p>
      <p class="notice">历史回测不代表未来表现。本页面不创建订单或交易。</p>
    </section>
    <section class="panel">
      <div class="heading"><div><small>HISTORY / 证据留痕</small><h2 id="history-compare-title" tabindex="-1">回测历史与报告</h2></div><button :disabled="loadingHistory" @click="refreshHistory">刷新历史状态</button></div>
      <p>选择 2 至 5 条成功记录。报告仅使用已保存的结果，不会重新运行回测。</p>
      <p v-if="historyError" role="alert" class="error">{{ historyError }}</p>
      <p v-if="loadingHistory" role="status" class="empty">正在读取回测历史…</p>
      <button v-if="historyError && !loadingHistory" @click="loadMoreHistory">重试读取历史</button>
      <div v-if="!history.length && !loadingHistory && !historyError" class="empty">暂无回测历史。</div>
      <div v-for="item in history" :key="item.historyRunId" class="history-row">
        <label><input type="checkbox" :checked="selected.includes(item.historyRunId)" :disabled="item.status !== 'SUCCESS' || (!selected.includes(item.historyRunId) && selected.length >= 5)" @change="toggle(item.historyRunId)" />
          <span><strong>{{ item.strategy || '未知策略' }} v{{ item.strategyVersion || '—' }}</strong> · <span class="status" :data-status="item.status">{{ runStatus(item.status) }}</span><small>{{ item.startedAt }} → {{ item.finishedAt || '结束时间未知' }} · {{ item.historyRunId }}</small><small>{{ item.dataset || '数据集未知' }} · hash {{ item.datasetHash || '未知' }} · {{ item.cacheHit ? '命中缓存' : '独立运行' }}</small><small>参数：{{ item.canonicalParams || '未知' }}<template v-if="item.failureCode"> · 失败原因：{{ item.failureCode }}</template></small></span>
        </label>
      </div>
      <button v-if="hasMoreHistory" :disabled="loadingHistory" @click="loadMoreHistory">{{ loadingHistory ? '加载中…' : '加载更早记录' }}</button>
      <button :disabled="selected.length < 2 || comparing" @click="compare">{{ comparing ? '生成中…' : `比较 ${selected.length} 条并生成报告` }}</button>
      <p v-if="compareError" role="alert" class="error">{{ compareError }}</p>
      <button :disabled="selected.length < 2 || exporting || !validResearchThresholds" @click="exportEvidence">{{ exporting ? '导出中…' : '导出研究证据' }}</button>
      <p v-if="exportError" role="alert" class="error">{{ exportError }}</p>
      <template v-if="report">
        <div v-for="warning in report.warnings" :key="warning" class="compare-warning" role="alert">⚠ {{ warning }}</div>
        <div class="compare-scroll" tabindex="0" role="region" aria-label="历史回测比较表，可横向滚动"><table><thead><tr><th>指标</th><th v-for="run in report.runs" :key="run.run_id">{{ run.strategy }} v{{ run.strategy_version }}<br /><small>{{ run.run_id }}</small></th></tr></thead>
          <tbody><tr v-for="row in compareRows" :key="row.key"><th>{{ row.label }}</th><td v-for="run in report.runs" :key="run.run_id">{{ rowValue(run, row.key) }}</td></tr></tbody></table></div>
        <p>生成时间：{{ report.generated_at }}。{{ report.disclaimer }}。</p>
        <button @click="download('json')">导出 JSON</button> <button @click="download('md')">导出 Markdown</button>
      </template>
    </section>
    <section class="panel">
      <small>CANDIDATES / 从历史证据出发</small><h2 id="candidate-title" tabindex="-1">只读研究候选</h2>
      <p>从上方勾选的成功历史记录中，按策略、版本和参数分组。阈值只用于筛查证据，不构成买卖建议。</p>
      <div class="research-gates">
        <label>最小样本区间（天）<input v-model.number="researchThresholds.minSampleDays" type="number" min="1" max="3650" step="1" /></label>
        <label>最大回撤上限<input v-model.number="researchThresholds.maxDrawdown" type="number" min="0" max="1" step="0.01" /></label>
        <label>最低相对基准年化差<input v-model.number="researchThresholds.minBaselineAnnualizedDelta" type="number" min="-1" max="1" step="0.01" /></label>
        <label>最少交易次数<input v-model.number="researchThresholds.minTradeCount" type="number" min="0" max="100000" step="1" /></label>
      </div>
      <p v-if="!validResearchThresholds" class="error">筛查阈值超出范围，请检查样本天数、回撤、基准差和交易次数。</p>
      <button :disabled="!selected.length || researching || !validResearchThresholds" @click="research">{{ researching ? '筛查中…' : '生成研究候选' }}</button>
      <p v-if="researchError" role="alert" class="error">{{ researchError }}</p>
      <p v-if="researching" role="status" class="empty">正在筛查已保存的回测证据…</p>
      <p v-else-if="!researchReport && !researchError && !selected.length" class="empty">先从回测历史选择至少一条成功记录。</p>
      <p v-else-if="!researchReport && !researchError" class="empty">尚未生成研究候选；选择或阈值变化后需重新筛查。</p>
      <template v-if="researchReport">
        <p class="notice">{{ researchReport.disclaimer }}</p>
        <p v-for="item in researchReport.excluded_runs" :key="item.run_id" class="compare-warning">{{ item.run_id }} · {{ item.status }}：{{ item.reason }}</p>
        <p v-if="!researchReport.candidates.length" class="empty">所选记录没有可用的成功回测证据。</p>
        <article v-for="candidate in researchReport.candidates" :key="candidate.strategy + candidate.strategy_version + JSON.stringify(candidate.canonical_params)" class="candidate">
          <h3>{{ candidate.strategy }} v{{ candidate.strategy_version }} · {{ researchStatus(candidate.status) }}</h3>
          <button @click="continueResearch(candidate)">继续研究 · 加入研究方案</button>
          <p>证据状态：{{ candidate.evidence_status === 'CONSISTENT' ? '一致' : '不足或不可合并' }} · 参数：{{ JSON.stringify(candidate.canonical_params) }}</p>
          <p>历史 run：{{ candidate.run_ids.join('、') }}</p>
          <p>数据集 hash：{{ candidate.dataset_hashes.join('、') }}</p>
          <ul><li v-for="reason in candidate.reasons" :key="reason">{{ reason }}</li><li v-for="warning in candidate.warnings" :key="warning">⚠ {{ warning }}</li></ul>
          <div v-for="item in candidate.evidence" :key="item.run_id" class="evidence">
            <strong>{{ item.run_id }}</strong> · {{ item.data_range.start }} 至 {{ item.data_range.end }}（{{ item.sample_days }} 天）
            <span>年化 {{ percent(item.metrics.annualized_return) }} · 回撤 {{ percent(item.metrics.max_drawdown) }} · 相对基准年化差 {{ percent(item.baseline_delta.annualized_return_delta) }} · 交易 {{ number(item.metrics.trade_count) }} 次</span>
          </div>
        </article>
      </template>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import ResearchWorkbench from '../components/ResearchWorkbench.vue'
import { apiClient, backtestApi, useUserStore } from '@wealth-hub/shared'
import type { BacktestRun, BacktestCompareReport, BacktestCompareRun, BacktestResearchReport, BacktestResearchThresholds } from '@wealth-hub/shared'
type Result = { run_id: string; strategy: {name: string; version: string; params: Record<string, number>}; data_range: {start: string; end: string; rows: number}; metrics: Record<string, number | null>; baseline?: Record<string, number | string | null> }
const datasets = ref<string[]>([])
const data = ref('')
const strategy = ref('pure_sip')
const contribution = ref(100)
const interval = ref(30)
const window = ref(20)
const multiplier = ref(2)
const threshold = ref(0.2)
const fraction = ref(0.25)
const results = ref<Result[]>([])
const loading = ref(true)
const error = ref('')
const running = ref(false), runMessage = ref('')
const user = useUserStore()
const contextKey = computed(() => JSON.stringify([user.token, user.user?.id, user.user?.familyId]))
let generation = 0, alive = true
const ticket = () => ({ generation, owner: contextKey.value })
const current = (request: ReturnType<typeof ticket>) => alive && request.generation === generation && request.owner === contextKey.value
onBeforeUnmount(() => { alive = false; generation++ })
const history = ref<BacktestRun[]>([])
const selected = ref<string[]>([])
const report = ref<BacktestCompareReport | null>(null)
const comparing = ref(false)
const historyError = ref('')
const compareError = ref('')
const historyPage = ref(0)
const hasMoreHistory = ref(false)
const loadingHistory = ref(false)
const researchThresholds = ref<BacktestResearchThresholds>({minSampleDays: 180, maxDrawdown: 0.30, minBaselineAnnualizedDelta: 0, minTradeCount: 3})
const researchReport = ref<BacktestResearchReport | null>(null)
const researching = ref(false)
const researchError = ref('')
const exporting = ref(false)
const exportError = ref('')
const planCandidate = ref<BacktestResearchReport['candidates'][number] | null>(null)
function continueResearch(candidate: BacktestResearchReport['candidates'][number]) {
  planCandidate.value = candidate
  document.getElementById('research-title')?.scrollIntoView({ behavior: 'smooth' })
  document.getElementById('research-title')?.focus({ preventScroll: true })
}
async function refreshHistory() {
  if (loadingHistory.value) return
  history.value = []; historyPage.value = 0; hasMoreHistory.value = false; selected.value = []; report.value = null; researchReport.value = null
  await loadMoreHistory()
}
function usePlanRuns(ids: string[]) {
  selected.value = ids; report.value = null; researchReport.value = null; compareError.value = ''; exportError.value = ''
  void compare()
  document.getElementById('history-compare-title')?.scrollIntoView({ behavior: 'smooth' })
  document.getElementById('history-compare-title')?.focus({ preventScroll: true })
}
const validResearchThresholds = computed(() => {
  const t = researchThresholds.value
  return Number.isInteger(t.minSampleDays) && t.minSampleDays >= 1 && t.minSampleDays <= 3650 &&
    Number.isFinite(t.maxDrawdown) && t.maxDrawdown >= 0 && t.maxDrawdown <= 1 &&
    Number.isFinite(t.minBaselineAnnualizedDelta) && t.minBaselineAnnualizedDelta >= -1 && t.minBaselineAnnualizedDelta <= 1 &&
    Number.isInteger(t.minTradeCount) && t.minTradeCount >= 0 && t.minTradeCount <= 100000
})
let evidenceRevision = 0
watch([selected, researchThresholds], () => { evidenceRevision++; report.value = null; researchReport.value = null; planCandidate.value = null; compareError.value = ''; researchError.value = ''; exportError.value = '' }, {deep: true, flush: 'sync'})
function requestError(cause: unknown, fallback: string): string {
  const status = (cause as { response?: { status?: number } })?.response?.status
  return status === 401 ? '登录已失效，请重新登录后重试。' : status === 403 ? '当前账号没有策略实验室访问权限。' : fallback
}
async function loadMoreHistory() {
  if (loadingHistory.value) return
  const request = ticket()
  loadingHistory.value = true; historyError.value = ''
  try { const page = await backtestApi.history(historyPage.value, 50); if (!current(request)) return; history.value = [...history.value, ...page]; historyPage.value++; hasMoreHistory.value = page.length === 50 }
  catch (cause) { if (current(request)) historyError.value = requestError(cause, '回测历史加载失败；请检查网络或由管理员检查历史目录配置和权限后重试。') }
  finally { if (current(request)) loadingHistory.value = false }
}
async function loadData() {
  if (running.value) return
  const request = ticket()
  loading.value = true; error.value = ''; datasets.value = []; results.value = []; data.value = ''
  try {
    const response = await apiClient.get<string[]>('/backtest-lab/datasets')
    if (!current(request)) return
    datasets.value = response.data
    data.value = datasets.value[0] || ''
    const recent = await apiClient.get<Result[]>('/backtest-lab/recent')
    if (current(request)) results.value = recent.data.reverse()
  } catch (cause) { if (current(request)) { datasets.value = []; data.value = ''; results.value = []; error.value = requestError(cause, '回测数据加载失败；请检查网络或由管理员检查数据与历史目录配置后重试。') } }
  finally { if (current(request)) loading.value = false }
}
onMounted(() => { void loadData(); void loadMoreHistory() })
watch(contextKey, () => {
  // Invalidate every in-flight response and remount the owner-scoped workbench.
  generation++; datasets.value = []; data.value = ''; results.value = []; history.value = []; selected.value = []
  report.value = null; researchReport.value = null; planCandidate.value = null; historyPage.value = 0; hasMoreHistory.value = false
  loading.value = false; running.value = false; loadingHistory.value = false; comparing.value = false; researching.value = false; exporting.value = false
  error.value = ''; historyError.value = ''; compareError.value = ''; researchError.value = ''; exportError.value = ''; runMessage.value = ''
  strategy.value = 'pure_sip'; contribution.value = 100; interval.value = 30; window.value = 20; multiplier.value = 2; threshold.value = 0.2; fraction.value = 0.25
  researchThresholds.value = {minSampleDays: 180, maxDrawdown: 0.30, minBaselineAnnualizedDelta: 0, minTradeCount: 3}
  if (user.token) { void loadData(); void loadMoreHistory() }
}, { flush: 'sync' })
const validRun = computed(() => datasets.value.includes(data.value) && ['pure_sip', 'ma_enhanced', 'profit_recycle', 'profit_recycle:2'].includes(strategy.value) &&
  Number.isFinite(contribution.value) && contribution.value > 0 && contribution.value <= 1000000 &&
  Number.isInteger(interval.value) && interval.value >= 1 && interval.value <= 365 &&
  (strategy.value !== 'ma_enhanced' || (Number.isInteger(window.value) && window.value >= 2 && window.value <= 365 && Number.isFinite(multiplier.value) && multiplier.value >= 1 && multiplier.value <= 1000000)) &&
  (!strategy.value.startsWith('profit_recycle') || (Number.isFinite(threshold.value) && threshold.value > 0 && threshold.value <= 1 && Number.isFinite(fraction.value) && fraction.value > 0 && fraction.value <= 1)))
async function run() {
  if (loading.value || running.value || !validRun.value) return
  const request = ticket()
  running.value = true; error.value = ''; runMessage.value = '等待受控回测响应。后端未提供队列或实时进度，请勿重复提交。'
  const params: Record<string, number> = { contribution: contribution.value, interval_days: interval.value }
  if (strategy.value === 'ma_enhanced') Object.assign(params, {ma_window: window.value, dip_multiplier: multiplier.value})
  if (strategy.value.startsWith('profit_recycle')) Object.assign(params, {profit_threshold: threshold.value, sell_fraction: fraction.value})
  const [name, version = '1'] = strategy.value.split(':')
  try { const result = (await apiClient.post<Result>('/backtest-lab/runs', {data: data.value, strategy: name, version, params}, { timeout: 120000 })).data; if (!current(request)) return; results.value = [result, ...results.value.filter(x => x.run_id !== result.run_id)]; runMessage.value = `成功 · ${result.run_id}` }
  catch (cause) { if (current(request)) { error.value = requestError(cause, '回测失败，请检查参数与历史数据。'); runMessage.value = '失败或超时。网络中断时结果可能未知，请先刷新历史状态，勿自动重试。' } }
  finally { if (current(request)) { running.value = false; await refreshHistory() } }
}
function runStatus(status: string) { return ({ SUCCESS: '成功', FAILED: '失败', TIMEOUT: '超时', INVALID: '无效' } as Record<string, string>)[status] || '未知状态' }
const number = (value: number | null | undefined) => value == null ? '—' : value.toLocaleString('zh-CN', {maximumFractionDigits: 4})
const percent = (value: number | null | undefined) => value == null ? '—' : `${(value * 100).toFixed(2)}%`
function metrics(result: Result): [string, string][] { const m = result.metrics; const b = result.baseline; return [['总收益', percent(m.total_return)], ['年化收益', percent(m.annualized_return)], ['最大回撤', percent(m.max_drawdown)], ['累计投入', number(m.invested)], ['期末资产', number(m.final_assets)], ['剩余现金', number(m.cash)], ['交易次数', number(m.trade_count)], ['相对基准资产差', number(b?.final_assets_delta as number)], ['相对基准年化差', percent(b?.annualized_return_delta as number)]] }
function toggle(id: string) {
  if (!selected.value.includes(id) && (selected.value.length >= 5 || history.value.find(item => item.historyRunId === id)?.status !== 'SUCCESS')) return
  selected.value = selected.value.includes(id) ? selected.value.filter(x => x !== id) : [...selected.value, id]
  report.value = null
  compareError.value = ''
  researchReport.value = null
  planCandidate.value = null
  researchError.value = ''
  exportError.value = ''
}
async function exportEvidence() {
  if (exporting.value || selected.value.length < 2 || selected.value.length > 5 || !validResearchThresholds.value) return
  const request = ticket(), revision = evidenceRevision, ids = [...selected.value], thresholds = {...researchThresholds.value}
  exporting.value = true; exportError.value = ''
  try {
    const blob = await backtestApi.evidence(ids, thresholds)
    if (!current(request) || revision !== evidenceRevision) return
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'backtest-research-evidence.zip'
    link.click()
    URL.revokeObjectURL(url)
  } catch (cause) {
    if (current(request) && revision === evidenceRevision) exportError.value = requestError(cause, '研究证据导出失败，请检查所选回测和网络后重试。')
  } finally { if (current(request)) exporting.value = false }
}
function researchStatus(status: BacktestResearchReport['candidates'][number]['status']): string {
  return {WORTH_FURTHER_RESEARCH: '值得继续研究', EVIDENCE_INSUFFICIENT: '证据不足', DOES_NOT_MEET_CRITERIA: '不满足条件'}[status] ?? '未知'
}
async function research() {
  if (researching.value || !selected.value.length || selected.value.length > 5 || !validResearchThresholds.value) return
  const request = ticket(), revision = evidenceRevision
  researching.value = true; researchError.value = ''; researchReport.value = null
  const runIds = [...selected.value]
  const thresholds = {...researchThresholds.value}
  try {
    const result = await backtestApi.research(runIds, thresholds)
    if (current(request) && revision === evidenceRevision) researchReport.value = result
  }
  catch (cause) { if (current(request) && revision === evidenceRevision) researchError.value = requestError(cause, '研究候选生成失败，请检查所选记录后重试。') }
  finally { if (current(request)) researching.value = false }
}
async function compare() {
  if (comparing.value || selected.value.length < 2 || selected.value.length > 5) return
  const request = ticket(), revision = evidenceRevision
  comparing.value = true; compareError.value = ''; report.value = null
  const runIds = [...selected.value]
  try {
    const result = await backtestApi.compare(runIds)
    if (current(request) && revision === evidenceRevision) report.value = result
  }
  catch (cause) { if (current(request) && revision === evidenceRevision) compareError.value = requestError(cause, '对比报告生成失败，请检查所选记录后重试。') }
  finally { if (current(request)) comparing.value = false }
}
const compareRows = [
  {key: 'data_range', label: '数据区间'}, {key: 'dataset_hash', label: '数据 hash'},
  {key: 'canonical_params', label: '参数'}, {key: 'engine_version', label: '引擎版本'},
  {key: 'total_return', label: '总收益'}, {key: 'annualized_return', label: '年化'},
  {key: 'max_drawdown', label: '最大回撤'}, {key: 'invested', label: '投入'},
  {key: 'final_assets', label: '期末资产'}, {key: 'cash', label: '现金'},
  {key: 'trade_count', label: '交易次数'}, {key: 'final_assets_delta', label: '相对基准资产差'},
  {key: 'annualized_return_delta', label: '相对基准年化差'}, {key: 'max_drawdown_delta', label: '相对基准回撤差'},
]
function rowValue(run: BacktestCompareRun, key: string): string {
  if (key === 'data_range') return `${run.data_range.start} 至 ${run.data_range.end}`
  if (key === 'dataset_hash') return run.dataset_hash
  if (key === 'canonical_params') return JSON.stringify(run.canonical_params)
  if (key === 'engine_version') return run.engine_version
  const value = run.metrics[key] ?? run.baseline_delta[key]
  return key.includes('return') || key.includes('drawdown') ? percent(value) : number(value)
}
function download(format: 'json' | 'md') {
  if (!report.value) return
  const content = format === 'json' ? JSON.stringify(report.value, null, 2) + '\n' : report.value.markdown
  const url = URL.createObjectURL(new Blob([content], {type: format === 'json' ? 'application/json' : 'text/markdown'}))
  const link = document.createElement('a'); link.href = url; link.download = `backtest-compare-${report.value.generated_at.slice(0, 10)}.${format}`; link.click(); URL.revokeObjectURL(url)
}
</script>

<style scoped src="../styles/strategy-lab.css"></style>
