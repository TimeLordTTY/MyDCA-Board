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
    <p v-if="!datasets.length && !loading" class="empty">暂无可用历史数据。请由管理员将 CSV 放入回测数据目录。</p>
    <p v-if="!results.length && !error" class="empty">运行回测后，结果将显示在这里。</p>
    <section v-for="result in results" :key="result.run_id" class="panel result">
      <div class="heading"><h2>{{ result.strategy.name }} v{{ result.strategy.version }}</h2><small>{{ result.data_range.start }} 至 {{ result.data_range.end }} · {{ result.data_range.rows }} 条 · {{ result.run_id }}</small></div>
      <div class="metrics"><div v-for="[label, value] in metrics(result)" :key="label"><span>{{ label }}</span><strong>{{ value }}</strong></div></div>
      <p>参数：{{ JSON.stringify(result.strategy.params) }}</p>
      <p class="notice">历史回测不代表未来表现。本页面不创建订单或交易。</p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { apiClient } from '@wealth-hub/shared'
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
onMounted(async () => { try { datasets.value = (await apiClient.get<string[]>('/backtest-lab/datasets')).data; data.value = datasets.value[0] || ''; results.value = (await apiClient.get<Result[]>('/backtest-lab/recent')).data.reverse() } catch { error.value = '回测数据加载失败，请稍后重试' } finally { loading.value = false } })
async function run() {
  loading.value = true; error.value = ''
  const params: Record<string, number> = { contribution: contribution.value, interval_days: interval.value }
  if (strategy.value === 'ma_enhanced') Object.assign(params, {ma_window: window.value, dip_multiplier: multiplier.value})
  if (strategy.value.startsWith('profit_recycle')) Object.assign(params, {profit_threshold: threshold.value, sell_fraction: fraction.value})
  const [name, version = '1'] = strategy.value.split(':')
  try { const result = (await apiClient.post<Result>('/backtest-lab/runs', {data: data.value, strategy: name, version, params})).data; results.value = [result, ...results.value.filter(x => x.run_id !== result.run_id)] }
  catch (e: any) { error.value = e?.response?.data?.message || '回测失败，请检查参数与历史数据' }
  finally { loading.value = false }
}
const number = (value: number | null | undefined) => value == null ? '—' : value.toLocaleString('zh-CN', {maximumFractionDigits: 4})
const percent = (value: number | null | undefined) => value == null ? '—' : `${(value * 100).toFixed(2)}%`
function metrics(result: Result): [string, string][] { const m = result.metrics; const b = result.baseline; return [['总收益', percent(m.total_return)], ['年化收益', percent(m.annualized_return)], ['最大回撤', percent(m.max_drawdown)], ['累计投入', number(m.invested)], ['期末资产', number(m.final_assets)], ['剩余现金', number(m.cash)], ['交易次数', number(m.trade_count)], ['相对基准资产差', number(b?.final_assets_delta as number)], ['相对基准年化差', percent(b?.annualized_return_delta as number)]] }
</script>

<style scoped>
.lab{padding:32px;max-width:1200px;margin:auto;color:#172b38}.lab h1{font-size:30px;margin:0}.lab header p,.notice{color:#627582}.panel{background:white;border:1px solid #dce5e8;border-radius:14px;padding:22px;margin:20px 0;box-shadow:0 6px 22px rgba(18,52,68,.05)}.panel:first-of-type{display:flex;flex-wrap:wrap;align-items:end;gap:16px}.panel label{display:grid;gap:6px;font-size:13px;color:#475b67}.panel input,.panel select{border:1px solid #b7cbd0;border-radius:8px;padding:9px;min-width:125px;background:white}.panel button{border:0;border-radius:8px;background:#0d635d;color:white;padding:11px 18px;cursor:pointer}.panel button:disabled{opacity:.5;cursor:default}.heading{display:flex;justify-content:space-between;align-items:center;gap:20px;flex-wrap:wrap}.heading h2{margin:0}.heading small{color:#64747b}.metrics{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px;margin:20px 0}.metrics div{background:#f2f7f6;border-radius:9px;padding:13px}.metrics span{display:block;font-size:12px;color:#526b70}.metrics strong{display:block;font-size:20px;margin-top:4px}.error{color:#a02c36}.empty{padding:20px;color:#61737c}
</style>
