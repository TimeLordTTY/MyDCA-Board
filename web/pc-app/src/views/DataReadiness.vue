<template>
  <section class="readiness" :aria-busy="busy">
    <header><p class="eyebrow">WEALTH HUB / DATA NOTES</p><h1>数据就绪检查<span class="readiness-stamp">来源笔记</span></h1>
      <p>哪些数据可用，哪些还缺资料？先看证据，再解读你的财富。</p>
      <p>READY 仅表示来源可读，不代表目标达成或资金可操作。</p></header>
    <nav class="section-nav" aria-label="数据就绪章节"><a href="#readiness-quality">01 数据质量</a><a href="#readiness-evidence">02 来源证据</a><a href="#readiness-deployment">03 结构边界</a></nav>
    <form class="toolbar" @submit.prevent="refresh">
      <label>诊断作用域<select v-model="scope" :disabled="busy" @change="clearReport"><option value="PERSONAL">个人</option><option value="FAMILY">家庭（管理员）</option></select></label>
      <label>预算月份<input v-model="month" type="month" min="1000-01" max="9998-12" required :disabled="busy" @input="clearReport" /></label>
      <button class="btn primary" :disabled="busy">{{ busy ? '正在读取…' : '刷新只读诊断' }}</button>
      <button type="button" class="btn" :disabled="busy || !report" @click="copySummary">复制脱敏问题摘要</button>
    </form>
    <p role="status" aria-live="polite">{{ busy ? '正在读取授权范围内的证据…' : report ? `诊断时间：${report.checkedAt || '未知'}（来源更新时间见各项）` : '尚无诊断证据，请刷新。' }} {{ copyStatus }}</p>
    <p v-if="error" role="alert" class="error">{{ error }} <button type="button" class="btn" :disabled="busy" @click="refresh">重试读取</button></p>
    <section id="readiness-quality" class="quality" aria-labelledby="quality-title"><h2 id="quality-title">数据质量一览</h2><p>可读与完整是两件事。部分已知、未知和读取失败都需要核对，缺少证据不按零处理。</p>
      <div class="quality-grid"><article v-for="item in rows(report, ['ASSETS', 'MARKET', 'GOALS', 'BUDGETS'])" :key="item.area"><h3>{{ labels[item.area] }}</h3><strong class="state" :class="item.state">{{ stateLabel(item) }}</strong><p>{{ item.reason }}</p><p class="source-date">来源日期 / 期间：{{ item.dataTime || '未知' }}</p><a :href="`#evidence-${item.area}`">查看来源与下一步 ↗</a></article></div>
    </section>
    <aside id="readiness-deployment" class="deployment"><strong>数据库结构：以目标环境部署记录为准</strong><p>数据库结构由部署记录核验，当前页面不能实时验证其结构状态。业务来源的 READY / PARTIAL / UNKNOWN 仍以本次只读诊断为准，部署成功不代表资产、目标或预算数据完整。</p>
      <p>可阅读来源：仓库文档 docs/mydca_production_deployment_20261010.md（2026-10-10 部署报告，仅适用于报告中的目标环境）。需有仓库访问权限；详细部署证据由获授权部署管理员核对。本页不提供服务器证据访问，也不执行 SQL 或修改财务记录。</p></aside>
    <h2 id="readiness-evidence">来源与可解释证据</h2>
    <div class="sections"><section v-for="group in groups" :key="group.title" class="evidence-group"><h2>{{ group.title }}</h2>
      <article v-for="item in rows(report, group.areas)" :id="`evidence-${item.area}`" :key="item.area" tabindex="-1">
        <h3>{{ labels[item.area] }} <span class="state" :class="item.area === 'SCHEMA' ? 'UNKNOWN' : item.state">{{ stateLabel(item) }}</span></h3>
        <p>{{ item.reason }}</p><details><summary>证据详情与人工核对</summary><dl><dt>证据来源</dt><dd>{{ item.source || '未知' }}</dd><dt>来源更新时间 / 期间</dt><dd>{{ item.dataTime || '未知' }} · {{ freshness(item) }}</dd><dt>下一步人工核对</dt><dd>{{ item.nextStep }}</dd></dl></details>
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
.readiness { max-width: 1380px; margin: auto; padding: 12px 0; color: var(--text); overflow-wrap: anywhere; }
.eyebrow { letter-spacing: .12em; color: var(--muted); font-size: 11px; }
h1, h2 { font-family: 'Songti SC', 'STSong', 'SimSun', serif; font-weight: 600; letter-spacing: -.03em; }
h1 { margin: 16px 0; font-size: clamp(30px, 3.5vw, 46px); } h2 { font-size: 25px; } h3 { font-size: 16px; line-height: 1.8; }
p { font-size: 14px; line-height: 1.85; color: var(--muted); }
.readiness-stamp { display: inline-block; vertical-align: middle; margin-left: 24px; padding: 16px 22px; border-radius: 50%; transform: rotate(-8deg); background: var(--primarySoft); font: 13px sans-serif; letter-spacing: .08em; }
.section-nav { display: flex; flex-wrap: wrap; gap: 12px; margin: 26px 0; }
a { color: var(--primary); font-size: 13px; }
.section-nav a { padding: 12px 18px; border: 1px solid var(--line); border-radius: 999px; background: var(--card); color: var(--text); text-decoration: none; }
.quality-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; margin: 24px 0; }
.quality-grid article { border: 1px solid var(--line); border-top: 4px solid var(--primary); border-radius: 22px; padding: 20px; background: var(--card); min-width: 0; }
.source-date { font-size: 12px; font-variant-numeric: tabular-nums; }
.toolbar { display: flex; align-items: end; flex-wrap: wrap; gap: 16px; margin: 24px 0 16px; }
label { display: grid; gap: 8px; font-size: 14px; } select, input { padding: 11px; min-height: 44px; box-sizing: border-box; border: 1px solid var(--border); border-radius: 10px; background: var(--card); color: inherit; font: inherit; }
.deployment { border-left: 4px solid var(--primary); border-radius: 4px 22px 22px 4px; padding: 24px; background: var(--primarySoft); color: var(--text); margin: 28px 0; }
.sections { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 24px; margin-top: 24px; }
.evidence-group { border: 1px solid var(--line); border-radius: 23px; padding: 24px; background: var(--card); min-width: 0; } article { padding: 8px 0 16px; border-bottom: 1px solid var(--line); scroll-margin-top: 24px; }
.state { display: inline-block; padding: 4px 10px; border: 1px solid currentColor; border-radius: 999px; font-size: 12px; background: var(--bg); color: var(--muted); }
h3 .state { margin-left: 8px; }
.READY { color: var(--wh-positive, #28704f); } .PARTIAL { color: var(--wh-warning, #86570e); } .UNAVAILABLE { color: var(--wh-danger, #b52c33); }
summary { min-height: 44px; display: list-item; align-content: center; cursor: pointer; color: var(--primary); font-size: 14px; }
dl { display: grid; grid-template-columns: 130px minmax(0, 1fr); gap: 10px; font-size: 14px; line-height: 1.8; } dd { margin: 0; } dt { color: var(--muted); }
.error { color: var(--wh-danger, #b52c33); } :focus-visible { outline: 3px solid var(--primary); outline-offset: 3px; }
@media (max-width: 1100px) { .quality-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 760px) { .sections, .quality-grid { grid-template-columns: 1fr; } .readiness-stamp { display: none; } .evidence-group { padding: 18px; } dl { grid-template-columns: 1fr; } .toolbar label { width: 100%; } }
</style>
