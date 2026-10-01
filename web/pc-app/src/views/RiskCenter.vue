<template>
  <section class="risk-center" :aria-busy="busy">
    <header><div><small>RISK WATCH / READ ONLY</small><h1>风险观察中心</h1><p>仅供观察，不构成交易建议。评估只读取财务数据，规则与已读仅保存观察元数据。</p></div><button :disabled="busy" @click="refresh">刷新列表与历史</button></header>
    <nav class="links"><router-link :to="{ name: 'Dashboard', query: { readonly: 'true' } }">查看财富雷达 ↗</router-link><router-link :to="{ name: 'StrategyLab', query: { readonly: 'true' } }">查看研究方案 ↗</router-link></nav>
    <p v-if="busy" role="status">正在读取或保存观察数据…</p>
    <p v-if="error" role="alert" class="error">{{ error }} <button :disabled="busy" @click="refresh">重试读取</button></p>
    <div class="workspace">
      <aside><h2>观察规则 <span>{{ rules.length }}</span></h2><button :disabled="busy" @click="edit()">新建规则</button><p v-if="!busy && !error && !rules.length">暂无规则，请新建观察规则。</p>
        <article v-for="r in rules" :key="r.id"><h3>{{ typeLabels[r.config.type] }} · {{ r.config.productId ? `标的 #${r.config.productId}` : r.config.assetType || '备注' }}</h3><p>{{ r.config.scope === 'FAMILY' ? '家庭' : '个人' }} · {{ r.config.severity }} · {{ r.config.muted ? '已停用 / 持续静默' : '已启用' }}</p><p class="identifier">{{ r.id }}</p><button :disabled="busy" @click="edit(r)">编辑</button><button :disabled="busy" @click="toggle(r)">{{ r.config.muted ? '启用' : '停用' }}</button><button :disabled="busy" @click="evaluate(r)">评估当前数据</button></article>
        <button v-if="moreRules" :disabled="busy" @click="loadRules">加载更多规则</button>
      </aside>
      <main>
        <form v-if="form" @submit.prevent="save"><h2>{{ editing ? '编辑规则' : '新建规则' }}</h2><fieldset :disabled="busy">
          <label>作用域<select v-model="form.scope"><option value="PERSONAL">个人</option><option value="FAMILY">家庭（管理员）</option></select></label>
          <label>类型<select v-model="form.type"><option v-for="(label, key) in typeLabels" :key="key" :value="key">{{ label }}</option></select></label>
          <label v-if="needsProduct">标的 ID<input v-model.number="form.productId" type="number" min="1" step="1" required /></label>
          <template v-if="form.type === 'ALLOCATION_DEVIATION'"><label>资产类别<input v-model="form.assetType" maxlength="60" required placeholder="如 CASH，沿用持仓 assetType" /></label><label>目标占比<input v-model.number="form.target" type="number" min="0" max="1" step="any" required /></label></template>
          <label v-if="form.type !== 'NOTE'">阈值（{{ form.type === 'STALE' ? '整数天' : '小数比例，0.1 = 10%' }}）<input v-model.number="form.threshold" type="number" step="any" required /></label>
          <label v-if="form.type === 'RETURN'">方向<select v-model="form.direction"><option value="ABOVE">大于等于</option><option value="BELOW">小于等于</option></select></label>
          <label>级别<select v-model="form.severity"><option value="INFO">INFO · 提示</option><option value="WARNING">WARNING · 警告</option><option value="CRITICAL">CRITICAL · 严重</option></select></label><label>备注<textarea v-model="form.note" maxlength="2000" /></label><label><input v-model="form.muted" type="checkbox" /> 停用（持续静默）</label>
          <p>阈值包含等号。回撤、集中度和类别偏离为 0 至 1；收益率允许负值。未知数据不会视为解除。</p><p v-if="formError" role="alert" class="error">{{ formError }}</p><button>保存规则</button><button type="button" @click="form = null">取消</button>
        </fieldset></form>
        <h2>最近评估 / 当前命中</h2><p>显示最近保存的快照；点击规则的“评估当前数据”读取最新事实。停用或静默不抹去命中证据。</p><p v-if="!busy && !error && !snapshots.length">暂无评估快照。</p>
        <div class="cards"><article v-for="s in snapshots" :key="s.ruleId" :class="s.severity.toLowerCase()"><h3>{{ ruleTitle(s.ruleId) }} · {{ s.severity }}</h3><strong>{{ evidenceState(s) }}</strong><p>{{ s.reason }}</p><dl><dt>观察值 / 阈值</dt><dd>{{ s.observedValue ?? '未知' }} / {{ s.threshold ?? '无' }}</dd><dt>数据时间</dt><dd>{{ s.sourceDataTimestamp ?? '未知' }}</dd><dt>评估时间</dt><dd>{{ s.createdAt }}</dd></dl></article></div>
        <h2>提醒历史</h2><div class="filters"><label>标的 ID<input v-model="productFilter" inputmode="numeric" /></label><label>规则<select v-model="ruleFilter"><option value="">全部</option><option v-for="r in rules" :key="r.id" :value="r.id">{{ ruleTitle(r.id) }} · {{ r.id }}</option></select></label><label>级别<select v-model="severityFilter"><option value="">全部</option><option>INFO</option><option>WARNING</option><option>CRITICAL</option></select></label><label>状态<select v-model="stateFilter"><option value="">全部</option><option value="OPEN">未读 OPEN</option><option value="UNRESOLVED">全部未解除</option><option value="RESOLVED">已解除 RESOLVED</option></select></label></div>
        <p v-if="!busy && !error && !filteredEvents.length">已加载历史中暂无符合筛选的提醒。</p>
        <article v-for="e in filteredEvents" :key="`${e.evidence.ruleId}:${e.fingerprint}`"><h3>{{ ruleTitle(e.evidence.ruleId) }} · {{ e.evidence.severity }} · {{ stateLabels[e.state] }}</h3><p>{{ e.evidence.reason }}</p><p>{{ evidenceState(e.evidence) }} · 观察值 {{ e.evidence.observedValue ?? '未知' }} / 阈值 {{ e.evidence.threshold ?? '无' }} · 数据时间 {{ e.evidence.sourceDataTimestamp ?? '未知' }}</p><p>已读：{{ e.acknowledgedAt ?? '未读' }} · 解除：{{ e.resolvedAt ?? '未解除' }} · 静默至：{{ e.mutedUntil ?? '无时间窗口' }}</p><button :disabled="busy || !!e.acknowledgedAt || e.state === 'RESOLVED'" @click="acknowledge(e)">标为已读</button></article>
        <button v-if="moreEvents.length" :disabled="busy" @click="loadHistory">加载更早历史</button>
        <form @submit.prevent="mute"><h3>规则静默窗口</h3><fieldset :disabled="busy"><label>规则<select v-model="muteRule" required><option value="">请选择</option><option v-for="r in rules" :key="r.id" :value="r.id">{{ ruleTitle(r.id) }} · {{ r.id }}</option></select></label><label>静默截止（本地时间）<input v-model="muteUntil" type="datetime-local" /></label><p>留空并保存可清除时间窗口；持续静默请在规则中启用/停用。</p><button>保存静默窗口</button></fieldset></form>
      </main>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { riskWatchApi } from '@wealth-hub/shared'
import type { RiskRule, RiskConfig, RiskSnapshot, RiskEvent } from '@wealth-hub/shared'
import { typeLabels, stateLabels, validateRisk, evidenceState, dedupeEvents, riskError } from '../components/riskWatchModel'
const rules = ref<RiskRule[]>([]), snapshots = ref<RiskSnapshot[]>([]), events = ref<RiskEvent[]>([])
const busy = ref(false), error = ref(''), formError = ref(''), form = ref<RiskConfig | null>(null), editing = ref('')
const productFilter = ref(''), ruleFilter = ref(''), severityFilter = ref(''), stateFilter = ref(''), muteRule = ref(''), muteUntil = ref('')
const moreRules = ref(false), moreEvents = ref<string[]>([])
let rulePage = 0
const eventPages = new Map<string, number>()
const needsProduct = computed(() => form.value && ['RETURN', 'DRAWDOWN', 'STALE', 'CONCENTRATION'].includes(form.value.type))
const ruleTitle = (id: string) => { const c = rules.value.find(r => r.id === id)?.config; return c ? `${typeLabels[c.type]} ${c.productId ? '#' + c.productId : c.assetType || ''}` : id }
const filteredEvents = computed(() => events.value.filter(e => (!productFilter.value || String(rules.value.find(r => r.id === e.evidence.ruleId)?.config.productId) === productFilter.value.trim()) && (!ruleFilter.value || e.evidence.ruleId === ruleFilter.value) && (!severityFilter.value || e.evidence.severity === severityFilter.value) && (!stateFilter.value || (stateFilter.value === 'UNRESOLVED' ? e.state !== 'RESOLVED' : e.state === stateFilter.value))))
async function action(fn: () => Promise<void>) { if (busy.value) return; busy.value = true; error.value = ''; try { await fn() } catch (e) { error.value = riskError(e) } finally { busy.value = false } }
async function readEvidence(rs: RiskRule[]) {
  // Publish only a complete read, so failures never masquerade as an empty history.
  const rows = await Promise.all(rs.map(async r => ({ id: r.id, snapshots: await riskWatchApi.snapshots(r.id), events: await riskWatchApi.events(r.id) })))
  snapshots.value.push(...rows.flatMap(r => r.snapshots))
  events.value = dedupeEvents([...events.value, ...rows.flatMap(r => r.events)])
  for (const row of rows) { eventPages.set(row.id, 1); if (row.events.length === 50) moreEvents.value.push(row.id) }
}
async function reload() { rules.value = []; snapshots.value = []; events.value = []; moreEvents.value = []; moreRules.value = false; eventPages.clear(); rulePage = 0; const rs = await riskWatchApi.list(rulePage); await readEvidence(rs); rules.value = rs; rulePage++; moreRules.value = rs.length === 50 }
const refresh = () => action(reload)
const loadRules = () => action(async () => { const rs = await riskWatchApi.list(rulePage); await readEvidence(rs); rules.value.push(...rs); rulePage++; moreRules.value = rs.length === 50 })
const loadHistory = () => action(async () => { const rows = await Promise.all(moreEvents.value.map(async id => ({ id, events: await riskWatchApi.events(id, eventPages.get(id)) }))); events.value = dedupeEvents([...events.value, ...rows.flatMap(r => r.events)]); moreEvents.value = rows.filter(r => r.events.length === 50).map(r => r.id); rows.forEach(r => eventPages.set(r.id, (eventPages.get(r.id) ?? 1) + 1)) })
function edit(r?: RiskRule) { editing.value = r?.id ?? ''; formError.value = ''; form.value = r ? { ...r.config, note: r.config.note ?? '' } : { scope: 'PERSONAL', type: 'RETURN', productId: null, assetType: null, threshold: null, target: null, direction: 'BELOW', severity: 'WARNING', note: '', muted: false } }
function save() { if (!form.value) return; const c = { ...form.value }; formError.value = validateRisk(c); if (formError.value) return; void action(async () => { if (editing.value) await riskWatchApi.edit(editing.value, c); else await riskWatchApi.create(c); form.value = null; await reload() }) }
const toggle = (r: RiskRule) => action(async () => { await riskWatchApi.edit(r.id, { ...r.config, muted: !r.config.muted }); await reload() })
const evaluate = (r: RiskRule) => action(async () => { await riskWatchApi.evaluate(r.id); await reload() })
const acknowledge = (e: RiskEvent) => action(async () => { await riskWatchApi.acknowledge(e.evidence.ruleId, e.fingerprint); await reload() })
function mute() { void action(async () => { const date = muteUntil.value ? new Date(muteUntil.value) : null; if (date && (!Number.isFinite(date.getTime()) || date.getTime() <= Date.now())) throw new Error('静默截止时间须晚于当前时间'); await riskWatchApi.mute(muteRule.value, date?.toISOString() ?? null); await reload() }) }
onMounted(refresh)
</script>

<style scoped>
.risk-center { padding: 24px; color: #193b49; background: #f9fcfd; }
header, .links, .filters { display: flex; justify-content: space-between; gap: 16px; flex-wrap: wrap; } h1 { margin: 6px 0; font-size: 30px; } small { letter-spacing: .16em; color: #31697a; } p { line-height: 1.6; color: #52646b; } .workspace { display: grid; grid-template-columns: 280px minmax(0, 1fr); gap: 28px; margin-top: 24px; } article, form { padding: 18px; border: 1px solid #d5e4ed; border-radius: 12px; background: white; margin-bottom: 12px; } h2 { font-size: 21px; } h3 { margin: 0 0 10px; font-size: 16px; } .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 12px; } .cards article { border-left: 4px solid #4d9b83; } .cards .warning { border-left-color: #d49321; } .cards .critical { border-left-color: #c44c49; } button, input, select, textarea { padding: 8px 10px; border: 1px solid #9dbbc8; border-radius: 7px; background: white; color: #193b49; font: inherit; } button { cursor: pointer; margin: 4px; } button:disabled { opacity: .5; cursor: default; } label { display: flex; flex-direction: column; gap: 6px; margin: 10px 0; } fieldset { border: 0; padding: 0; } textarea { min-height: 70px; } .error { color: #9b2926; } .identifier { font-size: 11px; overflow-wrap: anywhere; } dd { margin: 4px 0 12px; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; } dt { font-size: 12px; color: #667d87; } a { color: #165773; } :focus-visible { outline: 3px solid #245f78; outline-offset: 2px; } @media(max-width: 900px) { .workspace { grid-template-columns: 1fr; } .risk-center { padding: 16px; } }
</style>
