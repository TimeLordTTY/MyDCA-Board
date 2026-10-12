<template>
  <section class="workbench" aria-labelledby="research-title">
    <header><div><small>RESEARCH / 研究档案</small><h2 id="research-title" tabindex="-1">研究方案工作台</h2></div><button :disabled="busy || running || runsLoading" @click="refresh">刷新方案与状态</button></header>
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
        <p class="notice">创建：{{ plan.createdAt }} · 最近保存：{{ plan.updatedAt }}。来源证据为创建时快照，警告保留如下。</p>
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
        <p v-if="dirty" class="warning">有未保存编辑，请先保存参数草稿再运行；当前回测只读取已保存的方案。</p>
        <button :disabled="busy || running || runsLoading || !datasets.includes(dataset) || dirty || plan.status === 'ARCHIVED'" @click="run">发起受控回测（使用已保存草稿）</button>
        <p v-if="runState" role="status">{{ runState }}。{{ runMessage }}</p>
        <h4>方案历史 run</h4>
        <p v-if="runsLoading" role="status">正在刷新历史 run…</p>
        <p v-if="runsError" role="alert" class="error">{{ runsError }}</p>
        <button v-if="runsError" :disabled="runsLoading || running" @click="loadRuns">重试读取方案历史</button>
        <p v-if="!runs.length && !runsLoading && !runsError">已加载记录中暂无此方案 run；可加载更早记录。</p>
        <label v-for="item in runs" :key="item.historyRunId" class="run"><input type="checkbox" :checked="chosen.includes(item.historyRunId)" :disabled="item.status !== 'SUCCESS' || (!chosen.includes(item.historyRunId) && chosen.length >= 5)" @change="toggle(item.historyRunId)" /><span>{{ item.startedAt }} · {{ runStatus(item.status) }} · {{ item.historyRunId }}<small>{{ item.dataset || '数据集未知' }} · {{ item.datasetHash || 'hash 未知' }} · {{ item.failureCode }}</small></span></label>
        <button v-if="runsMore" :disabled="runsLoading || running" @click="loadRuns">加载更早 run</button>
        <button :disabled="chosen.length < 2 || running" @click="$emit('evidence', [...chosen])">进入已有对比与证据导出（{{ chosen.length }} 条）</button>
        <p>选择 2 至 5 条成功 run；失败记录仅供查看，不参与对比或导出。</p>
      </article>
      <p v-else-if="!busy">选择方案查看参数、证据和历史运行。</p>
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { researchPlanApi, backtestApi, useUserStore } from '@wealth-hub/shared'
import type { ResearchPlan, BacktestRun, BacktestResearchReport, BacktestResearchThresholds } from '@wealth-hub/shared'
const props = defineProps<{ candidate: BacktestResearchReport['candidates'][number] | null; thresholds: BacktestResearchThresholds; datasets: string[] }>()
const emit = defineEmits<{ (event: 'cancel'): void; (event: 'evidence', ids: string[]): void; (event: 'refreshHistory'): void }>()
const plans = ref<ResearchPlan[]>([]), plan = ref<ResearchPlan | null>(null)
const busy = ref(false), error = ref(''), page = ref(0), more = ref(false)
const newName = ref(''), newDescription = ref(''), name = ref(''), description = ref(''), params = ref('{}'), status = ref<'DRAFT' | 'ACTIVE'>('DRAFT')
const archivePrompt = ref(false), dataset = ref(''), runState = ref(''), runMessage = ref('')
const running = computed(() => ['排队中', '运行中'].includes(runState.value))
const runs = ref<BacktestRun[]>([]), chosen = ref<string[]>([]), runsLoading = ref(false), runsError = ref(''), runsPage = ref(0), runsMore = ref(false)
let alive = true
onBeforeUnmount(() => { alive = false })
const user = useUserStore()
const owner = () => JSON.stringify([user.token, user.user?.id, user.user?.familyId])
const initialOwner = owner()
const active = () => alive && owner() === initialOwner
const dirty = computed(() => !!plan.value && (name.value !== plan.value.name || description.value !== (plan.value.description || '') || params.value !== JSON.stringify(plan.value.paramsDraft, null, 2) || (plan.value.status !== 'ARCHIVED' && status.value !== plan.value.status)))
function runStatus(value: string) { return ({ SUCCESS: '成功', FAILED: '失败', TIMEOUT: '超时', INVALID: '无效' } as Record<string, string>)[value] || '未知状态' }
function statusText(value: string) { return ({ DRAFT: '草稿', ACTIVE: '研究中', ARCHIVED: '已归档' } as Record<string, string>)[value] || value }
function failure(cause: unknown) {
  const e = cause as { response?: { status?: number }; message?: string }
  const text = e.message || ''
  const code = e.response?.status
  return code === 401 || /401|未授权|登录/.test(text) ? '登录已失效，请重新登录。' : code === 403 || /403|权限|禁止/.test(text) ? '没有研究方案访问权限。' : code === 404 ? '研究方案不存在或无权访问，请刷新列表。' : code === 409 || /已变更/.test(text) ? '方案已变更，请刷新后重试。' : /[\u4e00-\u9fff]/.test(text) ? text : '研究请求失败，请检查网络后重试。'
}
watch(() => props.candidate, c => { newName.value = c ? `${c.strategy} v${c.strategy_version} 研究` : ''; newDescription.value = '' })
watch(() => props.datasets, items => { if (!items.includes(dataset.value)) dataset.value = '' })
function apply(value: ResearchPlan) { plan.value = value; name.value = value.name; description.value = value.description || ''; params.value = JSON.stringify(value.paramsDraft, null, 2); status.value = value.status === 'ACTIVE' ? 'ACTIVE' : 'DRAFT'; archivePrompt.value = false }
async function loadMore() {
  if (!active() || busy.value) return
  busy.value = true; error.value = ''
  try { const items = await researchPlanApi.list(page.value); if (!active()) return; plans.value.push(...items); page.value++; more.value = items.length === 20 }
  catch (e) { if (active()) error.value = failure(e) } finally { if (active()) busy.value = false }
}
async function open(id: string) {
  if (!active() || busy.value || running.value || runsLoading.value) return
  busy.value = true; error.value = ''; plan.value = null; runState.value = ''; runs.value = []; chosen.value = []; runsPage.value = 0; runsMore.value = false
  try { const detail = await researchPlanApi.detail(id); if (!active()) return; apply(detail); await loadRuns() } catch (e) { if (active()) error.value = failure(e) } finally { if (active()) busy.value = false }
}
async function refresh() {
  if (!active() || busy.value || running.value || runsLoading.value) return
  const id = plan.value?.id; plans.value = []; page.value = 0; more.value = false
  plan.value = null; runs.value = []; chosen.value = []; runsMore.value = false; runsError.value = ''; runState.value = ''
  await loadMore(); if (active() && id && !error.value && !running.value) await open(id)
}
async function create() {
  if (!active() || !props.candidate || !newName.value.trim() || busy.value || running.value || runsLoading.value) return
  busy.value = true; error.value = ''
  try { const value = await researchPlanApi.create({ name: newName.value.trim(), description: newDescription.value, candidateId: props.candidate.candidate_id, runIds: [...props.candidate.run_ids], thresholds: { ...props.thresholds } }); if (!active()) return; emit('cancel'); apply(value); runs.value = []; chosen.value = []; runsPage.value = 0; runsMore.value = false; runState.value = ''; await loadRuns() }
  catch (e) { if (active()) error.value = failure(e) } finally { if (active()) busy.value = false }
  if (active() && !error.value) { plans.value = []; page.value = 0; await loadMore() }
}
async function mutate(payload: Parameters<typeof researchPlanApi.edit>[1]) {
  if (!active() || !plan.value || busy.value || running.value || plan.value.status === 'ARCHIVED') return
  busy.value = true; error.value = ''
  try { const value = await researchPlanApi.edit(plan.value.id, payload); if (!active()) return; apply(value); plans.value = plans.value.map(p => p.id === value.id ? value : p) }
  catch (e) { if (active()) error.value = failure(e) } finally { if (active()) busy.value = false }
}
async function save() {
  try { const parsed = JSON.parse(params.value); if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object' || Object.keys(parsed).length > 50 || new TextEncoder().encode(JSON.stringify(parsed)).length > 8000) throw new Error('参数草稿必须为最多 50 个属性、8000 字节以内的 JSON 对象。'); await mutate({ name: name.value.trim(), description: description.value, paramsDraft: parsed, status: status.value }) }
  catch (e) { error.value = failure(e) }
}
async function archive() { await mutate({ status: 'ARCHIVED' }) }
async function loadRuns() {
  if (!active() || !plan.value || runsLoading.value) return
  runsLoading.value = true; runsError.value = ''
  try { const items = await backtestApi.history(runsPage.value, 50); if (!active()) return; runs.value.push(...items.filter(r => r.researchPlanId === plan.value?.id)); runsPage.value++; runsMore.value = items.length === 50 }
  catch (e) { if (active()) runsError.value = failure(e) } finally { if (active()) runsLoading.value = false }
}
function toggle(id: string) { if (!chosen.value.includes(id) && (chosen.value.length >= 5 || runs.value.find(item => item.historyRunId === id)?.status !== 'SUCCESS')) return; chosen.value = chosen.value.includes(id) ? chosen.value.filter(x => x !== id) : [...chosen.value, id] }
async function run() {
  if (!active() || !plan.value || running.value || busy.value || runsLoading.value || !props.datasets.includes(dataset.value) || dirty.value || plan.value.status === 'ARCHIVED') return
  const id = plan.value.id; runState.value = '排队中'; runMessage.value = '仅为本地提交状态，后端未提供队列进度。'; error.value = ''
  await new Promise(resolve => setTimeout(resolve, 0))
  if (!active()) return
  runState.value = '运行中'; runMessage.value = '等待受控回测响应，请勿重复提交。'
  try { const result = await researchPlanApi.run(id, dataset.value); if (!active()) return; runState.value = '成功'; runMessage.value = result.history_run_id }
  catch (e) { if (active()) { runState.value = '失败'; runMessage.value = `${failure(e)} 网络中断时运行结果可能未知，请先刷新历史，勿自动重试。` } }
  finally { if (active()) { runs.value = []; chosen.value = []; runsPage.value = 0; runsMore.value = false; await loadRuns(); if (active()) emit('refreshHistory') } }
}
onMounted(loadMore)
</script>
<style scoped src="../styles/strategy-lab.css"></style>
