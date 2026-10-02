<template>
  <section class="allocation-center" :aria-busy="busy">
    <header><div><small>ALLOCATION / OBSERVATION</small><h1>配置偏离与止盈观察</h1><p>规则仅保存观察配置。所有情景都是假设调整，不构成交易建议。</p></div><button :disabled="busy" @click="refresh">刷新规则列表</button></header>
    <nav><router-link :to="{ name: 'RiskCenter', query: { readonly: 'true' } }">风险观察 ↗</router-link><router-link :to="{ name: 'StrategyLab', query: { readonly: 'true' } }">研究方案 ↗</router-link></nav>
    <p v-if="busy" role="status">正在读取或保存观察配置…</p>
    <p v-if="error" class="error" role="alert">{{ error }} <button :disabled="busy" @click="refresh">重试读取列表</button></p>
    <div class="workspace">
      <aside><h2>配置规则</h2><button :disabled="busy" @click="edit()">新建规则</button><p v-if="!busy && !error && !rules.length">暂无规则，请先配置目标区间。</p>
        <article v-for="r in rules" :key="r.id"><h3>{{ title(r) }}</h3><p>{{ r.config.scope === 'FAMILY' ? '家庭' : '个人' }} · {{ r.config.enabled ? '已启用' : '已停用' }}</p><p>目标 {{ percent(r.config.target) }}<br />区间 {{ percent(r.config.lowerBound) }} — {{ percent(r.config.upperBound) }}</p><p>{{ r.config.note }}</p>
          <button :disabled="busy" @click="edit(r)">编辑</button><button :disabled="busy" @click="toggle(r)">{{ r.config.enabled ? '停用' : '启用' }}</button><button :disabled="busy || !r.config.enabled" @click="observe(r)">观察当前数据</button><button :disabled="busy || !r.config.enabled" @click="loadPreview(r)">查看假设调整</button>
        </article><button v-if="more" :disabled="busy" @click="loadMore">加载更多规则</button>
      </aside>
      <main>
        <form v-if="form" @submit.prevent="save"><h2>{{ editing ? '编辑配置' : '新建配置' }}</h2><fieldset :disabled="busy">
          <label>作用域<select v-model="form.scope"><option value="PERSONAL">个人</option><option value="FAMILY">家庭（管理员）</option></select></label>
          <label>观察对象<select v-model="kind" @change="changeKind"><option value="ASSET">资产类别</option><option value="PRODUCT">产品</option></select></label>
          <label v-if="kind === 'PRODUCT'">产品 ID<input v-model.number="form.productId" type="number" min="1" step="1" required /></label>
          <label v-else>资产类别<input v-model="form.assetType" maxlength="60" placeholder="沿用持仓类别，如 CASH" required /></label>
          <div class="bands"><label>下限<input v-model.number="form.lowerBound" type="number" min="0" max="1" step="any" required /></label><label>目标<input v-model.number="form.target" type="number" min="0" max="1" step="any" required /></label><label>上限<input v-model.number="form.upperBound" type="number" min="0" max="1" step="any" required /></label></div>
          <p>比例以小数填写，0.1 = 10%；边界包含等号。</p>
          <label>收益观察阈值（可留空）<input v-model="returnText" type="number" min="0" step="any" /></label><label>分段止盈观察阈值（小数，逗号分隔）<input v-model="thresholdText" placeholder="如 0.1, 0.2, 0.3" /></label>
          <label>备注<textarea v-model="form.note" maxlength="2000" /></label><label class="check"><input v-model="form.enabled" type="checkbox" /> 启用观察</label><p v-if="formError" role="alert" class="error">{{ formError }}</p><button>保存配置</button><button type="button" @click="form = null">取消</button>
        </fieldset></form>
        <article><h2>当前配置与收益观察</h2><p v-if="!evaluation">点击规则的“观察当前数据”读取权重与收益状态。</p><template v-else><h3>{{ selectedTitle }}</h3><strong>{{ observationLabels[evaluation.status] }}</strong><p>{{ evaluation.reason }}</p><dl><dt>当前权重 / 目标 / 区间</dt><dd>{{ percent(evaluation.allocation) }} / {{ percent(selectedConfig?.target) }} / {{ percent(selectedConfig?.lowerBound) }} — {{ percent(selectedConfig?.upperBound) }}</dd><dt>偏离目标（百分点）</dt><dd>{{ evaluation.allocation == null || !selectedConfig ? 'UNKNOWN / 未知' : ((evaluation.allocation - selectedConfig.target) * 100).toFixed(2) }}</dd><dt>收益率 / 观察阈值</dt><dd>{{ percent(evaluation.returnRate) }} / {{ selectedConfig?.returnThreshold == null ? '未配置' : percent(selectedConfig.returnThreshold) }}</dd><dt>已达到的分段阈值</dt><dd>{{ reachedThresholds(evaluation) }}</dd><dt>评估时间</dt><dd>{{ evaluation.evaluatedAt }}</dd></dl><p>行情数据时间由下方手动情景预览提供；评估时间不代表行情时间。</p><p>{{ evaluation.disclaimer }}</p></template></article>
        <article class="scenario"><h2>只读情景 · 假设调整</h2><p v-if="!preview">仅在主动点击“查看假设调整”后加载，不会生成订单或保存财务记录。</p><template v-else><h3>{{ previewTitle }}</h3><strong>{{ dataState(preview) }}</strong><p>{{ preview.description }}</p><p>数据日期：{{ preview.dataDate ?? 'UNKNOWN / 未知' }} · 总资产 {{ amount(preview.totalAssets) }}</p><p>当前权重 {{ percent(preview.currentWeight) }} · 目标 {{ percent(preview.targetWeight) }} · 区间 {{ percent(preview.lowerBound) }} — {{ percent(preview.upperBound) }} · 偏离 {{ preview.deviationPercentagePoints ?? 'UNKNOWN / 未知' }} 个百分点</p>
          <div class="table-scroll"><table><caption>总资产固定的数学假设</caption><thead><tr><th>情景</th><th>选中资产假设调整</th><th>其余组合假设调整</th><th>调整前权重</th><th>假设调整后权重</th></tr></thead><tbody><tr v-for="row in [{ label: '回到目标', value: preview.targetScenario }, { label: '回到最近边界', value: preview.bandScenario }]" :key="row.label"><th>{{ row.label }}</th><td>{{ amount(row.value?.selectedAdjustment) }}</td><td>{{ amount(row.value?.remainderAdjustment) }}</td><td>{{ percent(preview.currentWeight) }}</td><td>{{ percent(afterWeight(preview, row.value)) }}</td></tr></tbody></table></div>
          <p>正数表示假设增加，负数表示假设减少；金额舍入至分，调整后权重可能仍有微小偏离。税费、滑点、交易限制和外汇未建模，不校验资金可用性。</p>
          <ul><li v-for="(w, i) in preview.warnings" :key="i">{{ w.code }} · {{ w.status }}：{{ w.message }}</li></ul><h3>行情证据</h3><p v-if="!preview.prices.length">无产品行情证据；请结合数据状态判断。</p><p v-for="p in preview.prices" :key="p.productId">产品 #{{ p.productId }} · {{ p.status }} · 价格日期 {{ p.priceDate ?? 'UNKNOWN' }} · 估值日期 {{ p.valuationDate ?? 'UNKNOWN' }} · 来源 {{ p.priceSource }}</p>
        </template></article>
      </main>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { allocationPolicyApi } from '@wealth-hub/shared'
import type { AllocationConfig, AllocationPolicy, AllocationEvaluation, RebalancePreview } from '@wealth-hub/shared'
import { observationLabels, validateAllocation, percent, amount, afterWeight, dataState, allocationError, reachedThresholds } from '../components/allocationPolicyModel'
const rules = ref<AllocationPolicy[]>([]), busy = ref(false), error = ref(''), more = ref(false)
const form = ref<AllocationConfig | null>(null), editing = ref(''), formError = ref(''), kind = ref('ASSET'), returnText = ref(''), thresholdText = ref('')
const evaluation = ref<AllocationEvaluation | null>(null), preview = ref<RebalancePreview | null>(null), selectedId = ref(''), previewTitle = ref('')
let page = 0
const title = (r: AllocationPolicy) => r.config.productId ? `产品 #${r.config.productId}` : `类别 ${r.config.assetType}`
const selectedConfig = computed(() => rules.value.find(r => r.id === selectedId.value)?.config)
const selectedTitle = computed(() => { const r = rules.value.find(r => r.id === selectedId.value); return r ? title(r) : '' })
function clearEvidence() { evaluation.value = null; preview.value = null; selectedId.value = ''; previewTitle.value = '' }
async function action(fn: () => Promise<void>) { if (busy.value) return; busy.value = true; error.value = ''; try { await fn() } catch (e) { clearEvidence(); error.value = allocationError(e) } finally { busy.value = false } }
async function reload() { clearEvidence(); rules.value = []; more.value = false; page = 0; const rows = await allocationPolicyApi.list(page); rules.value = rows; page++; more.value = rows.length === 50 }
const refresh = () => action(reload)
const loadMore = () => action(async () => { const rows = await allocationPolicyApi.list(page); rules.value.push(...rows); page++; more.value = rows.length === 50 })
function edit(r?: AllocationPolicy) {
  editing.value = r?.id ?? ''; formError.value = ''
  form.value = r ? { ...r.config, takeProfitThresholds: [...(r.config.takeProfitThresholds ?? [])] } : { scope: 'PERSONAL', productId: null, assetType: '', target: 0, lowerBound: 0, upperBound: 1, returnThreshold: null, takeProfitThresholds: [], enabled: true, note: '' }
  kind.value = r?.config.productId ? 'PRODUCT' : 'ASSET'; returnText.value = r?.config.returnThreshold?.toString() ?? ''; thresholdText.value = r?.config.takeProfitThresholds?.join(', ') ?? ''
}
function changeKind() { if (!form.value) return; form.value.productId = null; form.value.assetType = kind.value === 'ASSET' ? '' : null }
async function save() {
  if (!form.value || busy.value) return
  const c = { ...form.value, returnThreshold: returnText.value.trim() ? Number(returnText.value) : null, takeProfitThresholds: thresholdText.value.trim() ? thresholdText.value.split(/[,，]/).map(n => n.trim() ? Number(n) : NaN) : [] }
  formError.value = validateAllocation(c); if (formError.value) return
  await action(async () => { if (editing.value) await allocationPolicyApi.edit(editing.value, c); else await allocationPolicyApi.create(c); form.value = null; await reload() })
}
const toggle = (r: AllocationPolicy) => action(async () => { await allocationPolicyApi.edit(r.id, { ...r.config, enabled: !r.config.enabled }); await reload() })
const observe = (r: AllocationPolicy) => action(async () => { clearEvidence(); selectedId.value = r.id; evaluation.value = await allocationPolicyApi.evaluate(r.id) })
const loadPreview = (r: AllocationPolicy) => action(async () => { preview.value = null; previewTitle.value = title(r); preview.value = await allocationPolicyApi.preview(r.id) })
onMounted(refresh)
</script>

<style scoped>
.allocation-center { padding: 24px; color: #193b49; background: #f9fcfd; } header, nav { display: flex; justify-content: space-between; gap: 16px; flex-wrap: wrap; } h1 { margin: 6px 0; font-size: 30px; } small { letter-spacing: .16em; color: #31697a; } p, li { line-height: 1.6; color: #52646b; } .workspace { display: grid; grid-template-columns: 290px minmax(0, 1fr); gap: 28px; margin-top: 24px; } article, form { padding: 20px; border: 1px solid #d5e4ed; border-radius: 12px; background: white; margin-bottom: 14px; } .scenario { border-top: 4px solid #31697a; } h2 { font-size: 21px; } h3 { font-size: 16px; } button, input, select, textarea { padding: 8px 10px; border: 1px solid #9dbbc8; border-radius: 7px; background: white; color: #193b49; font: inherit; } button { cursor: pointer; margin: 4px; } button:disabled { opacity: .5; cursor: default; } label { display: flex; flex-direction: column; gap: 6px; margin: 10px 0; } .check { flex-direction: row; } .bands { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; } input { min-width: 0; } fieldset { border: 0; padding: 0; } .error { color: #9b2926; } dd { margin: 5px 0 14px; font-variant-numeric: tabular-nums; } dt { color: #667d87; font-size: 13px; } .table-scroll { overflow: auto; } table { width: 100%; border-collapse: collapse; font-variant-numeric: tabular-nums; } th, td { padding: 12px; border-bottom: 1px solid #d5e4ed; text-align: left; } caption { text-align: left; padding: 12px; } a { color: #165773; } :focus-visible { outline: 3px solid #245f78; outline-offset: 2px; } @media(max-width: 900px) { .workspace { grid-template-columns: 1fr; } .bands { grid-template-columns: 1fr; } }
</style>
