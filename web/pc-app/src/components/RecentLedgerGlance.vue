<template>
  <section class="card recent-ledger" aria-label="最近流水" :aria-busy="state.status === 'loading'">
    <header><div><small>RECENT ACTIVITY</small><h3>最近流水</h3></div><router-link :to="{ name: 'Ledger' }">查看全部流水 ↗</router-link></header>
    <button class="btn" type="button" :disabled="state.status === 'loading'" @click="loader.load()">{{ state.status === 'error' ? '手动重试流水' : '手动读取最近流水' }}</button>
    <p v-if="state.status === 'idle'" role="status">尚未读取。手动查看当前账号获授权的最近5条记录。</p>
    <p v-else-if="state.status === 'loading'" role="status">正在读取流水…</p>
    <p v-else-if="state.status === 'error'" role="alert">{{ state.error }}</p>
    <p v-else-if="!state.rows.length" role="status">本次接口返回没有流水记录。</p>
    <ol v-else><li v-for="row in state.rows" :key="row.txnId"><strong>{{ getTxnTypeLabel(row.txnType) }}</strong><span>{{ row.requestedAt }}</span><span>{{ row.note || '未填写备注' }}</span><small>{{ row.txnId }}</small></li></ol>
    <p class="sub">按服务端发起时间倒序。列表契约没有金额或币种，金额详情请进入流水页；这里不推算收支。</p>
  </section>
</template>
<script setup lang="ts">
import { ref, watch, onBeforeUnmount, onDeactivated, onActivated } from 'vue'
import { useRoute } from 'vue-router'
import { ledgerApi, useUserStore, getTxnTypeLabel } from '@wealth-hub/shared'
import { createRecentLedgerLoader } from './recentLedgerModel'
import type { RecentLedgerState } from './recentLedgerModel'
const user = useUserStore(), route = useRoute()
const state = ref<RecentLedgerState>({ status: 'idle', rows: [], error: '' })
const loader = createRecentLedgerLoader(() => ledgerApi.getTransactions({ page: 1, pageSize: 5 }),
  () => JSON.stringify([user.token, user.user, route.fullPath]), value => { state.value = value })
watch([() => user.token, () => user.user, () => route.fullPath], loader.reset, { deep: true, flush: 'sync' })
onBeforeUnmount(loader.suspend)
onDeactivated(loader.suspend)
onActivated(loader.activate)
</script>
<style scoped>
header{display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap;margin-bottom:14px}header small{color:var(--muted);letter-spacing:.14em;font-size:11px}h3{margin:8px 0}a{text-decoration:underline;text-underline-offset:4px;font-size:13px}ol{list-style:none;padding:0}li{display:grid;grid-template-columns:1fr auto;gap:6px;padding:16px 0;border-bottom:1px solid var(--line);overflow-wrap:anywhere}li span,li small{color:var(--muted);font-size:13px}p{line-height:1.7}@media(max-width:600px){li{grid-template-columns:1fr}}
</style>
