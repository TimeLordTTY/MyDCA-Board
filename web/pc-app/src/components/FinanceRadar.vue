<template>
  <section class="radar" aria-labelledby="radar-title" :aria-busy="loading">
    <header class="radar-header">
      <div>
        <p class="radar-eyebrow">DAILY / READ ONLY</p>
        <h2 id="radar-title">每日财富雷达 <small v-if="radar">{{ radar.date }} · {{ radar.scope === 'FAMILY' ? '家庭' : '个人' }}</small></h2>
        <p class="radar-subtitle">先看需要核查的事项，处理操作请进入原页面。</p>
        <router-link :to="{ name: 'RiskCenter', query: { readonly: 'true' } }">风险观察中心 ↗</router-link>
      </div>
      <button class="radar-retry" type="button" :disabled="loading" @click="load">{{ loading ? '加载中…' : '刷新雷达' }}</button>
    </header>

    <div v-if="loading && !radar" class="radar-state" role="status">正在读取今日事实…</div>
    <div v-else-if="error && !radar" class="radar-state radar-error" role="alert">
      {{ error }} <button type="button" @click="load">重试</button>
    </div>
    <template v-else-if="radar">
      <p v-if="loading || error || staleDate" class="radar-state" :class="{ 'radar-error': !!error }" :role="error ? 'alert' : 'status'">
        {{ error ? `${error} 以下为上次成功读取的旧快照。` : loading ? '正在刷新；当前显示上次成功读取的快照。' : `快照日期为 ${radar.date}，请刷新确认当前状态。` }}
      </p>
      <p v-if="!items.length" class="radar-state">暂无雷达数据。</p>
      <template v-else>
      <div class="radar-overview" role="status">
        <strong>{{ summary.attentionCount }} <span>项待核查</span></strong>
        <span>{{ summary.urgentCount ? `${summary.urgentCount} 项异常已优先展示` : '没有已识别异常；未知项仍需核查' }} · 包含数据未知项，非交易笔数</span>
      </div>
      <div class="radar-grid">
        <article v-for="item in summary.priorityItems" :key="item.id" class="radar-card" :class="`radar-${item.status.toLowerCase()}`">
          <div class="radar-card-top"><h3>{{ item.title }}</h3><span class="radar-status">{{ statusLabel[item.status] ?? '未知' }}</span></div>
          <p>{{ item.detail }}</p>
          <router-link :to="{ name: item.route }" :aria-label="`${item.title}：查看详情`">查看详情 <span aria-hidden="true">↗</span></router-link>
        </article>
      </div>
      <details class="radar-details">
        <summary>查看完整雷达与原始证据（{{ items.length }} 个检查项）</summary>
        <div class="radar-grid">
        <article v-for="item in items" :key="item.id" class="radar-card" :class="`radar-${item.status.toLowerCase()}`">
          <div class="radar-card-top"><h3>{{ item.title }}</h3><span class="radar-status">{{ statusLabel[item.status] ?? '未知' }}</span></div>
          <p>{{ item.detail }}</p>
          <router-link :to="{ name: item.route }" :aria-label="`${item.title}：查看详情`">查看详情 <span aria-hidden="true">↗</span></router-link>
        </article>
        </div>
        <section class="radar-evidence" aria-label="雷达原始证据">
          <h3>服务端警告证据</h3>
          <p v-if="!radar.warnings.length">本次返回没有警告证据。</p>
          <p v-for="(warning, index) in radar.warnings" :key="index"><code>{{ warning.code }}</code> · {{ statusLabel[warning.status] }} · {{ warning.message }}</p>
          <details><summary>查看本次只读响应</summary><pre>{{ JSON.stringify(radar, null, 2) }}</pre></details>
        </section>
      </details>
      </template>
      <p v-if="radar.markets.length === 0" class="radar-footnote">暂无持仓产品的行情和指标数据。</p>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, onDeactivated, onActivated, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { financeRadarApi, useUserStore } from '@wealth-hub/shared'
import type { FinanceRadar as FinanceRadarData } from '@wealth-hub/shared'
import { radarErrorText, radarItems, statusLabel, radarOverview, radarDateStale } from './financeRadarModel'

const radar = ref<FinanceRadarData | null>(null)
const loading = ref(false)
const error = ref('')
const items = computed(() => radar.value ? radarItems(radar.value) : [])
const summary = computed(() => radarOverview(items.value))
const clock = ref(new Date())
const staleDate = computed(() => !!radar.value && radarDateStale(radar.value.date, clock.value))
let dateTimer: ReturnType<typeof setInterval> | undefined
const user = useUserStore(), route = useRoute()
let generation = 0, active = true
const identity = () => JSON.stringify([user.token, user.user, route.fullPath])
function reset() { generation++; radar.value = null; error.value = ''; loading.value = false }
watch([() => user.token, () => user.user, () => route.fullPath], reset, { deep: true, flush: 'sync' })

async function load() {
  if (!active || loading.value) return
  const version = ++generation, owner = identity()
  const valid = () => active && version === generation && owner === identity()
  loading.value = true
  error.value = ''
  try {
    const data = await financeRadarApi.get()
    if (valid()) { radar.value = data; clock.value = new Date() }
  } catch (cause) {
    if (valid()) {
      error.value = radarErrorText(cause)
      if (error.value.includes('无权查看')) radar.value = null
    }
  } finally {
    if (valid()) loading.value = false
    else if (active && version === generation && owner !== identity()) reset()
  }
}

onMounted(() => {
  load()
  dateTimer = setInterval(() => { clock.value = new Date() }, 60_000)
  window.addEventListener('data-refresh', load)
})
onDeactivated(() => { active = false; reset() })
onActivated(() => { active = true })
onUnmounted(() => { active = false; reset(); clearInterval(dateTimer); window.removeEventListener('data-refresh', load) })
</script>

<style scoped>
.radar-overview { display: flex; flex-wrap: wrap; align-items: baseline; gap: 18px; margin-bottom: 16px; color: var(--muted); font-size: 13px; }
.radar-overview strong { color: var(--text); font-size: 32px; font-variant-numeric: tabular-nums; }
.radar-overview strong span { font-size: 16px; }
.radar-details { margin-top: 16px; }
.radar-details summary { cursor: pointer; padding: 8px 0; font-weight: 600; }
.radar-evidence { margin-top: 16px; font-size: 13px; overflow-wrap: anywhere; }
.radar-evidence pre { white-space: pre-wrap; overflow-wrap: anywhere; max-height: 360px; overflow: auto; }
.radar { margin-bottom: 20px; padding: 22px; border: 1px solid #d5e4ed; border-radius: 20px; background: #f9fcfd; box-shadow: 0 8px 25px #1637480a; }
.radar-header, .radar-card-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 14px; }
.radar-header { margin-bottom: 18px; }
.radar-eyebrow { margin: 0 0 5px; color: #31697a; font-size: 11px; font-weight: 800; letter-spacing: .18em; }
.radar h2 { margin: 0; color: #193b49; font-size: 22px; }
.radar h2 small { margin-left: 10px; color: #667d87; font-size: 13px; font-weight: 500; }
.radar-subtitle, .radar-footnote { margin: 5px 0 0; color: #5b737e; font-size: 13px; }
.radar-retry, .radar-state button { border: 1px solid #9dbbc8; border-radius: 9px; background: #fff; color: #174d61; padding: 8px 13px; cursor: pointer; white-space: nowrap; }
.radar-retry:disabled { opacity: .6; cursor: wait; }
.radar-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 245px), 1fr)); gap: 11px; }
.radar-card { min-height: 130px; display: flex; flex-direction: column; gap: 10px; padding: 15px; border: 1px solid #d9e5e9; border-left: 4px solid #4d9b83; border-radius: 11px; background: #fff; }
.radar-card h3 { margin: 0; font-size: 15px; }
.radar-card p { margin: 0; color: #52646b; font-size: 13px; line-height: 1.5; overflow-wrap: anywhere; }
.radar-card a { align-self: flex-start; margin-top: auto; color: #165773; font-size: 13px; font-weight: 700; text-decoration: underline; text-underline-offset: 3px; }
.radar-status { flex: none; border-radius: 99px; padding: 3px 8px; background: #e4f4ed; color: #176344; font-size: 12px; font-weight: 700; }
.radar-warning { border-left-color: #d49321; } .radar-warning .radar-status { background: #fff2d7; color: #805000; }
.radar-broken { border-left-color: #c44c49; } .radar-broken .radar-status { background: #ffebe9; color: #9b2926; }
.radar-unknown { border-left-color: #788390; } .radar-unknown .radar-status { background: #e9edf1; color: #45525e; }
.radar-state { padding: 16px; color: #455b65; } .radar-error { color: #9b2926; }
.radar button:focus-visible, .radar a:focus-visible { outline: 3px solid #245f78; outline-offset: 2px; }
@media (max-width: 600px) { .radar { padding: 16px; } .radar-header { flex-direction: column; } .radar h2 small { display: block; margin: 4px 0 0; } }
.radar{background:var(--card);color:var(--text);border-color:var(--line);box-shadow:none;border-radius:var(--r20)}.radar h2{color:var(--text)}.radar :is(.radar-eyebrow,.radar-subtitle,.radar-footnote,.radar-card p,.radar h2 small){color:var(--muted)}.radar-card{background:var(--bg);border-color:var(--line);border-left-color:var(--primary)}.radar-warning{border-left-color:var(--warn)}.radar-broken{border-left-color:var(--good)}.radar-unknown{border-left-color:var(--muted)}.radar :is(a,.radar-state){color:var(--text)}.radar :is(.radar-retry,.radar-state button){background:var(--card);color:var(--text);border-color:var(--line)}.radar-error{color:var(--good)!important}
</style>
