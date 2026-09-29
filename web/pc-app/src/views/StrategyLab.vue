<template>
  <main class="lab">
    <header><h1>策略实验室</h1><p>仅使用已导入的历史净值数据。历史回测不代表未来表现。</p></header>
    <section class="panel">
      <label>历史数据 <select v-model="data"><option value="">请选择</option><option v-for="name in datasets" :key="name">{{ name }}</option></select></label>
      <label>策略 <select v-model="strategy"><option value="pure_sip">定期投入 v1</option><option value="ma_enhanced">均线增强 v1</option><option value="profit_recycle">收益回收 v1</option><option value="profit_recycle:2">收益回收 v2</option></select></label>
      <label>每次投入 <input v-model.number="contribution" type="number" min="0.01" max="1000000" /></label>
      <label>投入间隔（天） <input v-model.number="interval" type="number" min="1" max="365" /></label>
      <label v-if="strategy === 'ma_enhanced'">均线窗口 <input v-model.number="window" type="number" min="2" max="365" /></label>
      <label v-if="strategy === 'ma_enhanced'">低点倍数 <input v-model.number="multiplier" type="number" min="1" max="1000000" step="0.1" /></label>
      <label v-if="strategy.startsWith('profit_recycle')">收益阈值 <input v-model.number="threshold" type="number" min="0.001" max="1" step="0.01" /></label>
      <label v-if="strategy.startsWith('profit_recycle')">回收比例 <input v-model.number="fraction" type="number" min="0.001" max="1" step="0.01" /></label>
      <button :disabled="loading || !data" @click="run">{{ loading ? '计算中…' : '运行只读回测' }}</button>
    </section>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <p v-if="!datasets.length && !loading && !error" class="empty">暂无可用历史数据。请由管理员将 CSV 放入回测数据目录。</p>
    <p v-if="!results.length && !error && !loading" class="empty">运行回测后，结果将显示在这里。</p>
    <section v-for="result in results" :key="result.run_id" class="panel result">
      <div class="heading"><h2>{{ result.strategy.name }} v{{ result.strategy.version }}</h2><small>{{ result.data_range.start }} 至 {{ result.data_range.end }} · {{ result.data_range.rows }} 条 · {{ result.run_id }}</small></div>
      <div class="metrics"><div v-for="[label, value] in metrics(result)" :key="label"><span>{{ label }}</span><strong>{{ value }}</strong></div></div>
      <p>参数：{{ JSON.stringify(result.strategy.params) }}</p>
      <p class="notice">历史回测不代表未来表现。本页面不创建订单或交易。</p>
    </section>
    <section class="panel">
      <h2>回测历史对比</h2>
      <p>选择 2 至 5 条成功记录。报告仅使用已保存的结果，不会重新运行回测。</p>
      <p v-if="historyError" role="alert" class="error">{{ historyError }}</p>
      <div v-if="!history.length" class="empty">暂无回测历史。</div>
      <div v-for="item in history" :key="item.historyRunId" class="history-row">
        <label><input type="checkbox" :checked="selected.includes(item.historyRunId)" :disabled="item.status !== 'SUCCESS' || (!selected.includes(item.historyRunId) && selected.length >= 5)" @change="toggle(item.historyRunId)" />
          {{ item.strategy || '未知策略' }} v{{ item.strategyVersion || '—' }} · {{ item.startedAt }} · {{ item.status }} · {{ item.historyRunId }}
        </label>
      </div>
      <button v-if="hasMoreHistory" :disabled="loadingHistory" @click="loadMoreHistory">{{ loadingHistory ? '加载中…' : '加载更早记录' }}</button>
      <button :disabled="selected.length < 2 || comparing" @click="compare">{{ comparing ? '生成中…' : `比较 ${selected.length} 条并生成报告` }}</button>
      <button :disabled="selected.length < 2 || exporting || !validResearchThresholds" @click="exportEvidence">{{ exporting ? '导出中…' : '导出研究证据' }}</button>
      <p v-if="exportError" role="alert" class="error">{{ exportError }}</p>
      <template v-if="report">
        <div v-for="warning in report.warnings" :key="warning" class="compare-warning" role="alert">⚠ {{ warning }}</div>
        <div class="compare-scroll"><table><thead><tr><th>指标</th><th v-for="run in report.runs" :key="run.run_id">{{ run.strategy }} v{{ run.strategy_version }}<br /><small>{{ run.run_id }}</small></th></tr></thead>
          <tbody><tr v-for="row in compareRows" :key="row.key"><th>{{ row.label }}</th><td v-for="run in report.runs" :key="run.run_id">{{ rowValue(run, row.key) }}</td></tr></tbody></table></div>
        <p>生成时间：{{ report.generated_at }}。{{ report.disclaimer }}。</p>
        <button @click="download('json')">导出 JSON</button> <button @click="download('md')">导出 Markdown</button>
      </template>
    </section>
    <section class="panel">
      <h2>只读研究候选</h2>
      <p>从上方勾选的成功历史记录中，按策略、版本和参数分组。阈值只用于筛查证据，不构成买卖建议。</p>
      <div class="research-gates">
        <label>最小样本区间（天）<input v-model.number="researchThresholds.minSampleDays" type="number" min="1" max="3650" step="1" /></label>
        <label>最大回撤上限<input v-model.number="researchThresholds.maxDrawdown" type="number" min="0" max="1" step="0.01" /></label>
        <label>最低相对基准年化差<input v-model.number="researchThresholds.minBaselineAnnualizedDelta" type="number" min="-1" max="1" step="0.01" /></label>
        <label>最少交易次数<input v-model.number="researchThresholds.minTradeCount" type="number" min="0" max="100000" step="1" /></label>
      </div>
      <button :disabled="!selected.length || researching || !validResearchThresholds" @click="research">{{ researching ? '筛查中…' : '生成研究候选' }}</button>
      <p v-if="researchError" role="alert" class="error">{{ researchError }}</p>
      <template v-if="researchReport">
        <p class="notice">{{ researchReport.disclaimer }}</p>
        <p v-for="item in researchReport.excluded_runs" :key="item.run_id" class="compare-warning">{{ item.run_id }} · {{ item.status }}：{{ item.reason }}</p>
        <p v-if="!researchReport.candidates.length" class="empty">所选记录没有可用的成功回测证据。</p>
        <article v-for="candidate in researchReport.candidates" :key="candidate.strategy + candidate.strategy_version + JSON.stringify(candidate.canonical_params)" class="candidate">
          <h3>{{ candidate.strategy }} v{{ candidate.strategy_version }} · {{ researchStatus(candidate.status) }}</h3>
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
import { computed, onMounted, ref, watch } from 'vue'
import { apiClient, backtestApi } from '@wealth-hub/shared'
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
const history = ref<BacktestRun[]>([])
const selected = ref<string[]>([])
const report = ref<BacktestCompareReport | null>(null)
const comparing = ref(false)
const historyError = ref('')
const historyPage = ref(0)
const hasMoreHistory = ref(false)
const loadingHistory = ref(false)
const researchThresholds = ref<BacktestResearchThresholds>({minSampleDays: 180, maxDrawdown: 0.30, minBaselineAnnualizedDelta: 0, minTradeCount: 3})
const researchReport = ref<BacktestResearchReport | null>(null)
const researching = ref(false)
const researchError = ref('')
const exporting = ref(false)
const exportError = ref('')
const validResearchThresholds = computed(() => {
  const t = researchThresholds.value
  return Number.isInteger(t.minSampleDays) && t.minSampleDays >= 1 && t.minSampleDays <= 3650 &&
    Number.isFinite(t.maxDrawdown) && t.maxDrawdown >= 0 && t.maxDrawdown <= 1 &&
    Number.isFinite(t.minBaselineAnnualizedDelta) && t.minBaselineAnnualizedDelta >= -1 && t.minBaselineAnnualizedDelta <= 1 &&
    Number.isInteger(t.minTradeCount) && t.minTradeCount >= 0 && t.minTradeCount <= 100000
})
watch(researchThresholds, () => { researchReport.value = null }, {deep: true})
async function loadMoreHistory() {
  loadingHistory.value = true; historyError.value = ''
  try { const page = await backtestApi.history(historyPage.value, 50); history.value = [...history.value, ...page]; historyPage.value++; hasMoreHistory.value = page.length === 50 }
  catch { historyError.value = '回测历史加载失败，请稍后重试' }
  finally { loadingHistory.value = false }
}
onMounted(async () => { try { datasets.value = (await apiClient.get<string[]>('/backtest-lab/datasets')).data; data.value = datasets.value[0] || ''; results.value = (await apiClient.get<Result[]>('/backtest-lab/recent')).data.reverse(); await loadMoreHistory() } catch (e: any) { error.value = e?.response?.status === 403 ? '没有策略实验室访问权限，请联系管理员。' : '回测数据加载失败，请稍后重试' } finally { loading.value = false } })
async function run() {
  loading.value = true; error.value = ''
  const params: Record<string, number> = { contribution: contribution.value, interval_days: interval.value }
  if (strategy.value === 'ma_enhanced') Object.assign(params, {ma_window: window.value, dip_multiplier: multiplier.value})
  if (strategy.value.startsWith('profit_recycle')) Object.assign(params, {profit_threshold: threshold.value, sell_fraction: fraction.value})
  const [name, version = '1'] = strategy.value.split(':')
  try { const result = (await apiClient.post<Result>('/backtest-lab/runs', {data: data.value, strategy: name, version, params})).data; results.value = [result, ...results.value.filter(x => x.run_id !== result.run_id)]; history.value = []; historyPage.value = 0; await loadMoreHistory() }
  catch (e: any) { error.value = e?.response?.status === 403 ? '没有运行策略回测的权限，请联系管理员。' : e?.response?.data?.message || '回测失败，请检查参数与历史数据' }
  finally { loading.value = false }
}
const number = (value: number | null | undefined) => value == null ? '—' : value.toLocaleString('zh-CN', {maximumFractionDigits: 4})
const percent = (value: number | null | undefined) => value == null ? '—' : `${(value * 100).toFixed(2)}%`
function metrics(result: Result): [string, string][] { const m = result.metrics; const b = result.baseline; return [['总收益', percent(m.total_return)], ['年化收益', percent(m.annualized_return)], ['最大回撤', percent(m.max_drawdown)], ['累计投入', number(m.invested)], ['期末资产', number(m.final_assets)], ['剩余现金', number(m.cash)], ['交易次数', number(m.trade_count)], ['相对基准资产差', number(b?.final_assets_delta as number)], ['相对基准年化差', percent(b?.annualized_return_delta as number)]] }
function toggle(id: string) {
  selected.value = selected.value.includes(id) ? selected.value.filter(x => x !== id) : [...selected.value, id]
  report.value = null
  researchReport.value = null
  exportError.value = ''
}
async function exportEvidence() {
  exporting.value = true; exportError.value = ''
  try {
    const blob = await backtestApi.evidence(selected.value, researchThresholds.value)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'backtest-research-evidence.zip'
    link.click()
    URL.revokeObjectURL(url)
  } catch {
    exportError.value = '研究证据导出失败，请检查所选回测和网络后重试'
  } finally { exporting.value = false }
}
function researchStatus(status: BacktestResearchReport['candidates'][number]['status']): string {
  return {WORTH_FURTHER_RESEARCH: '值得继续研究', EVIDENCE_INSUFFICIENT: '证据不足', DOES_NOT_MEET_CRITERIA: '不满足条件'}[status]
}
async function research() {
  researching.value = true; researchError.value = ''; researchReport.value = null
  try { researchReport.value = await backtestApi.research(selected.value, researchThresholds.value) }
  catch (e: any) { researchError.value = e?.response?.data?.message || '研究候选生成失败' }
  finally { researching.value = false }
}
async function compare() {
  comparing.value = true; historyError.value = ''; report.value = null
  try { report.value = await backtestApi.compare(selected.value) }
  catch (e: any) { historyError.value = e?.response?.data?.message || '对比报告生成失败' }
  finally { comparing.value = false }
}
const compareRows = [
  {key: 'data_range', label: '数据区间'}, {key: 'dataset_hash', label: '数据 hash'},
  {key: 'total_return', label: '总收益'}, {key: 'annualized_return', label: '年化'},
  {key: 'max_drawdown', label: '最大回撤'}, {key: 'invested', label: '投入'},
  {key: 'final_assets', label: '期末资产'}, {key: 'cash', label: '现金'},
  {key: 'trade_count', label: '交易次数'}, {key: 'final_assets_delta', label: '相对基准资产差'},
  {key: 'annualized_return_delta', label: '相对基准年化差'}, {key: 'max_drawdown_delta', label: '相对基准回撤差'},
]
function rowValue(run: BacktestCompareRun, key: string): string {
  if (key === 'data_range') return `${run.data_range.start} 至 ${run.data_range.end}`
  if (key === 'dataset_hash') return run.dataset_hash
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

<style scoped>
.lab{padding:32px;max-width:1200px;margin:auto;color:#172b38}.lab h1{font-size:30px;margin:0}.lab header p,.notice{color:#627582}.panel{background:white;border:1px solid #dce5e8;border-radius:14px;padding:22px;margin:20px 0;box-shadow:0 6px 22px rgba(18,52,68,.05)}.panel:first-of-type{display:flex;flex-wrap:wrap;align-items:end;gap:16px}.panel label{display:grid;gap:6px;font-size:13px;color:#475b67}.panel input,.panel select{border:1px solid #b7cbd0;border-radius:8px;padding:9px;min-width:125px;background:white}.panel button{border:0;border-radius:8px;background:#0d635d;color:white;padding:11px 18px;cursor:pointer}.panel button:disabled{opacity:.5;cursor:default}.heading{display:flex;justify-content:space-between;align-items:center;gap:20px;flex-wrap:wrap}.heading h2{margin:0}.heading small{color:#64747b}.metrics{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px;margin:20px 0}.metrics div{background:#f2f7f6;border-radius:9px;padding:13px}.metrics span{display:block;font-size:12px;color:#526b70}.metrics strong{display:block;font-size:20px;margin-top:4px}.error{color:#a02c36}.empty{padding:20px;color:#61737c}
.history-row{padding:8px 0;border-bottom:1px solid #e5ecee}.history-row label{display:flex;align-items:center;gap:8px;overflow-wrap:anywhere}.history-row input{min-width:auto}.compare-warning{margin:12px 0;padding:12px;border:1px solid #db9b39;background:#fff5dc;color:#714607;border-radius:8px}.compare-scroll{overflow-x:auto;margin-top:18px}table{width:100%;border-collapse:collapse;text-align:left}th,td{padding:10px;border-bottom:1px solid #dce5e8;min-width:140px}th small{font-weight:400;overflow-wrap:anywhere}
.research-gates{display:flex;flex-wrap:wrap;gap:16px;margin:18px 0}.research-gates label{display:grid;gap:6px}.candidate{border-top:1px solid #dce5e8;padding:16px 0;overflow-wrap:anywhere}.candidate h3{margin:0 0 8px}.candidate ul{padding-left:22px}.evidence{background:#f2f7f6;border-radius:8px;padding:10px;margin:8px 0}.evidence span{display:block;margin-top:4px}
</style>
