<template>
  <section class="asset-structure" aria-label="资产结构" :aria-busy="loading">
    <header><div><small>ASSET MIX</small><h3>资产结构</h3></div><router-link :to="{ name: 'Accounts' }">查看账户 ↗</router-link></header>
    <p class="sub">本次读取的现金与真实估值，负债单列。数据缺失时展示已知部分。</p>
    <dl><div v-for="item in structure" :key="item.label"><dt>{{ item.label }}</dt><dd>{{ amountText(item.amount, formatCurrency) }}</dd></div></dl>
    <p class="sub">与上方资产 KPI 使用同一批来源；非实时快照，不含模拟比例或未来趋势。</p>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { formatCurrency } from '@wealth-hub/shared'
import { amountText } from './dashboardKpiTruthModel'
import type { Snapshot } from './dashboardKpiTruthModel'
import { assetStructure } from './dashboardHomeModel'
const props = defineProps<{ snapshot: Snapshot | null; loading: boolean }>()
const structure = computed(() => assetStructure(props.snapshot, props.loading))
</script>
<style scoped>
header{display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap}small{color:var(--muted);letter-spacing:.14em;font-size:11px}h3{margin:8px 0}a{text-decoration:underline;text-underline-offset:4px;font-size:13px}dl{display:grid;gap:12px;margin:24px 0}dl div{padding:18px;border:1px solid var(--line);border-radius:16px;background:var(--bg)}dt{color:var(--muted);font-size:13px}dd{margin:8px 0 0;font-size:22px;font-weight:700;font-variant-numeric:tabular-nums;overflow-wrap:anywhere}
</style>
