<template>
  <section class="radar" aria-labelledby="radar-title" :aria-busy="loading">
    <header class="radar-header">
      <div>
        <p class="radar-eyebrow">DAILY / READ ONLY</p>
        <h2 id="radar-title">每日财富雷达 <small v-if="radar">{{ radar.date }} · {{ radar.scope === 'FAMILY' ? '家庭' : '个人' }}</small></h2>
        <p class="radar-subtitle">按问题优先级查看已有记录；处理操作请进入原页面。</p>
      </div>
      <button class="radar-retry" type="button" :disabled="loading" @click="load">{{ loading ? '加载中…' : '刷新雷达' }}</button>
    </header>

    <div v-if="loading && !radar" class="radar-state" role="status">正在读取今日事实…</div>
    <div v-else-if="error" class="radar-state radar-error" role="alert">
      {{ error }} <button type="button" @click="load">重试</button>
    </div>
    <template v-else-if="radar">
      <p v-if="!items.length" class="radar-state">暂无雷达数据。</p>
      <div v-else class="radar-grid">
        <article v-for="item in items" :key="item.id" class="radar-card" :class="`radar-${item.status.toLowerCase()}`">
          <div class="radar-card-top"><h3>{{ item.title }}</h3><span class="radar-status">{{ statusLabel[item.status] }}</span></div>
          <p>{{ item.detail }}</p>
          <router-link :to="{ name: item.route }" :aria-label="`${item.title}：查看详情`">查看详情 <span aria-hidden="true">↗</span></router-link>
        </article>
      </div>
      <p v-if="radar.markets.length === 0" class="radar-footnote">暂无持仓产品的行情和指标数据。</p>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { financeRadarApi } from '@wealth-hub/shared'
import type { FinanceRadar as FinanceRadarData } from '@wealth-hub/shared'
import { radarErrorText, radarItems, statusLabel } from './financeRadarModel'

const radar = ref<FinanceRadarData | null>(null)
const loading = ref(false)
const error = ref('')
const items = computed(() => radar.value ? radarItems(radar.value) : [])

async function load() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    radar.value = await financeRadarApi.get()
  } catch (cause) {
    radar.value = null
    error.value = radarErrorText(cause)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  window.addEventListener('data-refresh', load)
})
onUnmounted(() => window.removeEventListener('data-refresh', load))
</script>

<style scoped>
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
</style>
