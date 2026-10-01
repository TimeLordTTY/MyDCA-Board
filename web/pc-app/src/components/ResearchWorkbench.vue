<template>
  <section class="workbench" aria-labelledby="research-title">
    <header><div><small>RESEARCH / 研究档案</small><h2 id="research-title">研究方案工作台</h2></div><button :disabled="busy || running || runsLoading" @click="refresh">刷新方案与状态</button></header>
    <p class="notice">历史回测不代表未来表现。研究方案不是交易建议。</p>
    <router-link :to="{ name: 'RiskCenter', query: { readonly: 'true' } }">查看风险观察中心 ↗</router-link>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <p v-if="busy" role="status">正在读取或保存研究方案…</p>
    <form v-if="candidate" @submit.prevent="create">
      <h3>继续研究 · 加入研究方案</h3>
      <p>{{ candidate.strategy }} v{{ candidate.strategy_version }} · {{ candidate.run_ids.length }} 条来源证据</p>
      <label>方案名称<input v-model="newName" required maxlength="120" /></label>
      <label>研究备注<textarea v-model="newDescription" maxlength="4000" /></label>
      <button :disabled="busy || running || runsLoading || !newName.trim()">创建研究方案</button>
      <button type="button" :disabled="busy" @click="$emit('cancel')">取消</button>
    </form>
    <div class="columns">
      <nav aria-label="研究方案列表">
        <p v-if="!plans.length && !busy && !error">暂无研究方案。从下方候选点击“继续研究”创建。</p>
        <button v-for="item in plans" :key="item.id" :disabled="busy || running || runsLoading" :aria-pressed="plan?.id === item.id" @click="open(item.id)"><strong>{{ item.name }}</strong><span>{{ statusText(item.status) }} · {{ item.strategy }} v{{ item.strategyVersion }}</span></button>
        <button v-if="more" :disabled="busy" @click="loadMore">加载更多方案</button>
      </nav>
      <article v-if="plan">
        <h3>{{ plan.name }} · {{ statusText(plan.status) }}</h3>
        <p>来源候选：{{ plan.sourceCandidateId }}</p>
        <p>strategy/version：{{ plan.strategy }} / {{ plan.strategyVersion }}</p>
        <p>来源 run：{{ plan.sourceRunIds.join('、') }}</p>
        <p>dataset hash：{{ plan.datasetHashes.join('、') }}</p>
        <p>参数快照：{{ JSON.stringify(plan.canonicalParamsSnapshot) }}</p>
        <p>证据引用：{{ plan.evidenceBundleRef }}</p>
        <p v-for="(warning, index) in plan.warnings" :key="index" class="warning" role="alert">{{ warning }}</p>
        <details><summary>查看创建时证据快照</summary><pre>{{ JSON.stringify(plan.evidenceSnapshot, null, 2) }}</pre></details>
        <form @submit.prevent="save">
          <fieldset :disabled="busy || running || plan.status === 'ARCHIVED'">
            <label>名称<input v-model="name" required maxlength="120" /></label>
            <label>备注<textarea v-model="description" maxlength="4000" /></label>
            <label>研究状态<select v-model="status"><option value="DRAFT">草稿</option><option value="ACTIVE">研究中</option></select></label>
            <label>参数草稿（JSON 对象）<textarea v-model="params" rows="5" required /></label>
            <p>参数草稿仅保存研究想法，回测时由后端白名单和边界校验。</p>
            <button>保存编辑</button><button type="button" @click="archivePrompt = true">归档方案</button>
          </fieldset>
        </form>
        <div v-if="archivePrompt" role="alert"><p>归档后不能再编辑或运行此方案，仍可查看证据。确认归档？</p><button :disabled="busy" @click="archive">确认归档</button><button :disabled="busy" @click="archivePrompt = false">取消</button></div>
        <label>回测历史数据<select v-model="dataset" :disabled="running"><option value="">请选择</option><option v-for="item in datasets" :key="item">{{ item }}</option></select></label>
        <p v-if="!datasets.length">暂无历史数据，请刷新策略实验室或由管理员导入 CSV。</p>
        <button :disabled="busy || running || runsLoading || !dataset || plan.status === 'ARCHIVED'" @click="run">发起受控回测（使用已保存草稿）</button>
        <p v-if="runState" role="status">{{ runState }}。{{ runMessage }}</p>
        <h4>方案历史 run</h4>
        <p v-if="runsLoading" role="status">正在刷新历史 run…</p>
        <p v-if="runsError" role="alert" class="error">{{ runsError }}</p>
        <p v-if="!runs.length && !runsLoading && !runsError">已加载记录中暂无此方案 run；可加载更早记录。</p>
        <label v-for="item in runs" :key="item.historyRunId" class="run"><input type="checkbox" :checked="chosen.includes(item.historyRunId)" :disabled="item.status !== 'SUCCESS' || (!chosen.includes(item.historyRunId) && chosen.length >= 5)" @change="toggle(item.historyRunId)" />{{ item.startedAt }} · {{ item.status === 'SUCCESS' ? '成功' : '失败' }} · {{ item.historyRunId }} · {{ item.failureCode }}</label>
        <button v-if="runsMore" :disabled="runsLoading || running" @click="loadRuns">加载更早 run</button>
        <button :disabled="chosen.length < 2 || running" @click="$emit('evidence', [...chosen])">进入已有对比与证据导出（{{ chosen.length }} 条）</button>
        <p>选择 2 至 5 条成功 run；失败记录仅供查看，不参与对比或导出。</p>
      </article>
      <p v-else-if="!busy">选择方案查看参数、证据和历史运行。</p>
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { researchPlanApi, backtestApi } from '@wealth-hub/shared'
import type { ResearchPlan, BacktestRun, BacktestResearchReport, BacktestResearchThresholds } from '@wealth-hub/shared'
const props = defineProps<{ candidate: BacktestResearchReport['candidates'][number] | null; thresholds: BacktestResearchThresholds; datasets: string[] }>()
const emit = defineEmits<{ (event: 'cancel'): void; (event: 'evidence', ids: string[]): void; (event: 'refreshHistory'): void }>()
const plans = ref<ResearchPlan[]>([]), plan = ref<ResearchPlan | null>(null)
const busy = ref(false), error = ref(''), page = ref(0), more = ref(false)
const newName = ref(''), newDescription = ref(''), name = ref(''), description = ref(''), params = ref('{}'), status = ref<'DRAFT' | 'ACTIVE'>('DRAFT')
const archivePrompt = ref(false), dataset = ref(''), runState = ref(''), runMessage = ref('')
const running = computed(() => ['排队中', '运行中'].includes(runState.value))
const runs = ref<BacktestRun[]>([]), chosen = ref<string[]>([]), runsLoading = ref(false), runsError = ref(''), runsPage = ref(0), runsMore = ref(false)
function statusText(value: string) { return ({ DRAFT: '草稿', ACTIVE: '研究中', ARCHIVED: '已归档' } as Record<string, string>)[value] || value }
function failure(cause: unknown) {
  const e = cause as { response?: { status?: number }; message?: string }
  const text = e.message || ''
  const code = e.response?.status
  return code === 401 || /401|未授权|登录/.test(text) ? '登录已失效，请重新登录。' : code === 403 || /403|权限|禁止/.test(text) ? '没有研究方案访问权限。' : code === 404 ? '研究方案不存在或无权访问，请刷新列表。' : code === 409 || /已变更/.test(text) ? '方案已变更，请刷新后重试。' : /[\u4e00-\u9fff]/.test(text) ? text : '研究请求失败，请检查网络后重试。'
}
watch(() => props.candidate, c => { newName.value = c ? `${c.strategy} v${c.strategy_version} 研究` : ''; newDescription.value = '' })
function apply(value: ResearchPlan) { plan.value = value; name.value = value.name; description.value = value.description || ''; params.value = JSON.stringify(value.paramsDraft, null, 2); status.value = value.status === 'ACTIVE' ? 'ACTIVE' : 'DRAFT'; archivePrompt.value = false }
async function loadMore() {
  busy.value = true; error.value = ''
  try { const items = await researchPlanApi.list(page.value); plans.value.push(...items); page.value++; more.value = items.length === 20 }
  catch (e) { error.value = failure(e) } finally { busy.value = false }
}
async function open(id: string) {
  if (running.value || runsLoading.value) return
  busy.value = true; error.value = ''; plan.value = null; runState.value = ''; runs.value = []; chosen.value = []; runsPage.value = 0; runsMore.value = false
  try { apply(await researchPlanApi.detail(id)); await loadRuns() } catch (e) { error.value = failure(e) } finally { busy.value = false }
}
async function refresh() {
  if (running.value || runsLoading.value) return
  const id = plan.value?.id; plans.value = []; page.value = 0; more.value = false
  plan.value = null; runs.value = []; chosen.value = []; runsMore.value = false; runsError.value = ''; runState.value = ''
  await loadMore(); if (id && !error.value && !running.value) await open(id)
}
async function create() {
  if (!props.candidate || busy.value || running.value || runsLoading.value) return
  busy.value = true; error.value = ''
  try { const value = await researchPlanApi.create({ name: newName.value.trim(), description: newDescription.value, candidateId: props.candidate.candidate_id, runIds: [...props.candidate.run_ids], thresholds: { ...props.thresholds } }); emit('cancel'); apply(value); runs.value = []; chosen.value = []; runsPage.value = 0; runsMore.value = false; runState.value = ''; await loadRuns() }
  catch (e) { error.value = failure(e) } finally { busy.value = false }
  if (!error.value) { plans.value = []; page.value = 0; await loadMore() }
}
async function mutate(payload: Parameters<typeof researchPlanApi.edit>[1]) {
  if (!plan.value || busy.value || running.value || plan.value.status === 'ARCHIVED') return
  busy.value = true; error.value = ''
  try { apply(await researchPlanApi.edit(plan.value.id, payload)); plans.value = plans.value.map(p => p.id === plan.value?.id ? plan.value : p) as ResearchPlan[] }
  catch (e) { error.value = failure(e) } finally { busy.value = false }
}
async function save() {
  try { const parsed = JSON.parse(params.value); if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object' || Object.keys(parsed).length > 50 || new TextEncoder().encode(JSON.stringify(parsed)).length > 8000) throw new Error('参数草稿必须为最多 50 个属性、8000 字节以内的 JSON 对象。'); await mutate({ name: name.value.trim(), description: description.value, paramsDraft: parsed, status: status.value }) }
  catch (e) { error.value = failure(e) }
}
async function archive() { await mutate({ status: 'ARCHIVED' }) }
async function loadRuns() {
  if (!plan.value || runsLoading.value) return
  runsLoading.value = true; runsError.value = ''
  try { const items = await backtestApi.history(runsPage.value, 50); runs.value.push(...items.filter(r => r.researchPlanId === plan.value?.id)); runsPage.value++; runsMore.value = items.length === 50 }
  catch (e) { runsError.value = failure(e) } finally { runsLoading.value = false }
}
function toggle(id: string) { chosen.value = chosen.value.includes(id) ? chosen.value.filter(x => x !== id) : [...chosen.value, id] }
async function run() {
  if (!plan.value || running.value || busy.value || runsLoading.value || !dataset.value || plan.value.status === 'ARCHIVED') return
  const id = plan.value.id; runState.value = '排队中'; runMessage.value = '仅为本地提交状态，后端未提供队列进度。'; error.value = ''
  await new Promise(resolve => setTimeout(resolve, 0))
  runState.value = '运行中'; runMessage.value = '等待受控回测响应，请勿重复提交。'
  try { const result = await researchPlanApi.run(id, dataset.value); runState.value = '成功'; runMessage.value = result.history_run_id }
  catch (e) { runState.value = '失败'; runMessage.value = `${failure(e)} 网络中断时运行结果可能未知，请先刷新历史，勿自动重试。` }
  finally { runs.value = []; chosen.value = []; runsPage.value = 0; runsMore.value = false; await loadRuns(); emit('refreshHistory') }
}
onMounted(loadMore)
</script>
<style scoped>
.workbench{background:#fff;border:1px solid #b7cbd0;border-top:4px solid #0d635d;border-radius:12px;padding:24px;margin:24px 0;color:#172b38}.workbench header{display:flex;justify-content:space-between;align-items:center;gap:12px}.workbench small{letter-spacing:2px;color:#0d635d}.workbench h2{margin:6px 0}.notice{color:#627582}.columns{display:grid;grid-template-columns:240px minmax(0,1fr);gap:24px;margin-top:22px}.columns nav button{display:block;width:100%;text-align:left;margin-bottom:8px;background:#f2f7f6;color:#172b38;border:1px solid #dce5e8}.columns nav button[aria-pressed=true]{border-color:#0d635d}.columns nav span{display:block;font-size:12px;margin-top:6px}article{overflow-wrap:anywhere}label{display:grid;gap:6px;margin:12px 0}input,textarea,select{padding:9px;border:1px solid #b7cbd0;border-radius:6px;font:inherit;max-width:100%;box-sizing:border-box}fieldset{border:0;padding:0;margin:16px 0}button{background:#0d635d;color:white;border:0;border-radius:6px;padding:10px 14px;margin:4px;cursor:pointer}button:disabled{opacity:.5;cursor:default}.error{color:#a02c36}.warning{background:#fff5dc;color:#714607;padding:12px}.run{display:flex;align-items:center;gap:8px}pre{white-space:pre-wrap;font-size:12px;background:#f2f7f6;padding:16px}button:focus-visible,input:focus-visible,textarea:focus-visible,select:focus-visible{outline:2px solid #0d635d;outline-offset:3px}@media(max-width:800px){.columns{grid-template-columns:1fr}.workbench header{flex-wrap:wrap}}
</style>
