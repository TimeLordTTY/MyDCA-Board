<template>
  <section class="readiness" :aria-busy="busy">
    <header><p class="eyebrow">财富中枢 / 来源与证据</p><h1>数据就绪检查</h1>
      <p>先确认数据能否使用，再解读统计。READY 仅表示来源可读，不代表目标达成或资金可操作。</p></header>
    <form class="toolbar" @submit.prevent="refresh">
      <label>诊断作用域<select v-model="scope" :disabled="busy" @change="clearReport"><option value="PERSONAL">个人</option><option value="FAMILY">家庭（管理员）</option></select></label>
      <label>预算月份<input v-model="month" type="month" min="1000-01" max="9998-12" required :disabled="busy" @input="clearReport" /></label>
      <button class="btn primary" :disabled="busy">{{ busy ? '正在读取…' : '刷新只读诊断' }}</button>
      <button type="button" class="btn" :disabled="busy || !report" @click="copySummary">复制脱敏问题摘要</button>
    </form>
    <p role="status" aria-live="polite">{{ busy ? '正在读取授权范围内的证据…' : report ? `诊断时间：${report.checkedAt}（来源更新时间见各项）` : '尚无诊断证据，请刷新。' }} {{ copyStatus }}</p>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <aside class="deployment"><strong>数据库结构：以目标环境部署记录为准</strong><p>数据库结构由部署记录核验，当前页面不能实时验证其结构状态。业务来源的 READY / PARTIAL / UNKNOWN 仍以本次只读诊断为准，部署成功不代表资产、目标或预算数据完整。</p>
      <p>可阅读来源：仓库文档 docs/mydca_production_deployment_20261010.md（2026-10-10 部署报告，仅适用于报告中的目标环境）。需有仓库访问权限；详细部署证据由获授权部署管理员核对。本页不提供服务器证据访问，也不执行 SQL 或修改财务记录。</p></aside>
    <div class="sections"><section v-for="group in groups" :key="group.title" class="evidence-group"><h2>{{ group.title }}</h2>
      <article v-for="item in rows(report, group.areas)" :key="item.area">
        <h3>{{ labels[item.area] }} <span class="state" :class="item.area === 'SCHEMA' ? 'UNKNOWN' : item.state">{{ stateLabel(item) }}</span></h3>
        <p>{{ item.reason }}</p><dl><dt>证据来源</dt><dd>{{ item.source || '未知' }}</dd><dt>来源更新时间 / 期间</dt><dd>{{ item.dataTime || '未知' }} · {{ freshness(item) }}</dd><dt>下一步人工核对</dt><dd>{{ item.nextStep }}</dd></dl>
      </article>
    </section></div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { dataReadinessApi } from '@wealth-hub/shared'
import type { DataReadiness } from '@wealth-hub/shared'
import { groups, labels, rows, stateLabel, freshness, diagnosticError, issueSummary } from '../components/dataReadinessModel'
const date = new Date()
const scope = ref<DataReadiness['scope']>('PERSONAL')
const month = ref(`${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`)
const report = ref<DataReadiness | null>(null)
const busy = ref(false), error = ref(''), copyStatus = ref('')
function clearReport() { report.value = null; error.value = ''; copyStatus.value = '' }
async function refresh() {
  if (busy.value) return
  clearReport()
  const year = Number(month.value.slice(0, 4))
  if (!/^\d{4}-(?:0[1-9]|1[0-2])$/.test(month.value) || year < 1000 || year > 9998) {
    error.value = '请选择1000年至9998年的有效月份。'; return
  }
  busy.value = true
  try {
    const result = await dataReadinessApi.diagnose(scope.value, month.value)
    if (result.scope !== scope.value || result.month !== month.value || !Array.isArray(result.evidence)) throw new Error()
    report.value = result
  } catch (e) { error.value = diagnosticError(e) }
  finally { busy.value = false }
}
async function copySummary() {
  if (!report.value || busy.value) return
  try { await navigator.clipboard.writeText(issueSummary(report.value)); copyStatus.value = '已复制脱敏问题摘要。' }
  catch { copyStatus.value = '无法访问剪贴板，请检查浏览器权限后重试。' }
}
onMounted(refresh)
</script>

<style scoped>
.readiness { padding: 28px; color: var(--text, #202e38); overflow-wrap: anywhere; }
.eyebrow { letter-spacing: .12em; color: #526970; font-size: 12px; }
h1 { margin: 8px 0; font-size: 30px; } h2 { font-size: 19px; } h3 { font-size: 15px; line-height: 1.8; }
.toolbar { display: flex; align-items: end; flex-wrap: wrap; gap: 16px; margin: 24px 0 16px; }
label { display: grid; gap: 6px; } select, input { padding: 9px; border: 1px solid #85979e; border-radius: 6px; background: var(--card, white); color: inherit; }
.deployment { border-left: 4px solid #718c92; padding: 18px 22px; background: #eef3f5; color: #34434b; }
.sections { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 24px; margin-top: 24px; }
.evidence-group { border-top: 2px solid #718c92; min-width: 0; } article { padding: 8px 0 16px; border-bottom: 1px solid #b9c7ca; }
.state { display: inline-block; margin-left: 8px; padding: 2px 8px; border-radius: 4px; font-size: 12px; background: #e6ecee; color: #34434b; }
.READY { background: #dceee5; color: #225741; } .PARTIAL { background: #fff0cf; color: #674614; } .UNAVAILABLE { background: #ffe3df; color: #8c3024; }
dl { display: grid; grid-template-columns: 130px minmax(0, 1fr); gap: 8px; font-size: 14px; } dd { margin: 0; } dt { color: #60747b; }
.error { color: #a32d22; } button:focus-visible, select:focus-visible, input:focus-visible { outline: 3px solid #267782; outline-offset: 3px; }
@media (max-width: 900px) { .sections { grid-template-columns: 1fr; } .readiness { padding: 18px; } }
</style>
