<template>
  <div>
    <!-- Sticky Topbar -->
    <div class="topbar">
      <div class="topbar-inner">
        <div class="brand">
          <div class="logo">W</div>
          <div class="brand-title">
            <b>WealthHub</b>
            <span>财富中枢系统</span>
          </div>
        </div>

        <div class="nav">
          <router-link
            v-for="item in navItems"
            :key="item.name"
            :to="item.path"
            class="pill"
            :class="{ active: $route.name === item.name }"
          >
            <span class="dot"></span>
            {{ item.label }}
          </router-link>
        </div>

        <div class="actions">
          <button class="btn ghost" @click="handleRefresh">⟳ 刷新数据</button>
          <button class="btn primary" @click="handleUnifiedEntry">📝 记一笔</button>
          <button class="btn" @click="handleNewOrder">▦ 新建订单</button>
          <div class="avatar" @click="handleProfile">👤</div>
        </div>
      </div>
    </div>

    <div class="wrap">
      <!-- Page Header -->
      <div class="pagehead">
        <h1>{{ pageTitle }}</h1>
        <p>{{ pageDesc }}</p>
      </div>

      <!-- Page Content -->
      <router-view />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@wealth-hub/shared'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const navItems = [
  { name: 'Dashboard', path: '/dashboard', label: '总览' },
  { name: 'Ledger', path: '/ledger', label: '流水' },
  { name: 'Orders', path: '/orders', label: '订单&结算' },
  { name: 'Products', path: '/products', label: '产品' },
  { name: 'Accounts', path: '/accounts', label: '账户' },
  { name: 'Settings', path: '/settings', label: '设置' },
]

const pageTitle = computed(() => {
  const titles: Record<string, string> = {
    Dashboard: '财富中枢 · 看板',
    Ledger: '交易流水',
    Orders: '订单&结算',
    Products: '产品管理',
    Accounts: '账户管理',
    Holdings: '持仓查看',
    Settlements: '结算确认',
    Settings: '设置',
  }
  return titles[route.name as string] || '财富中枢系统'
})

const pageDesc = computed(() => {
  const descs: Record<string, string> = {
    Dashboard: '资产概览、待结算清单、核心持仓',
    Ledger: '展示所有记账操作流水',
    Orders: '订单列表和结算确认',
    Products: '产品是全局字典：ETF / 基金 / 货基 / 逆回购',
    Accounts: '父账户仅用于分组展示；真实余额只在叶子账户',
    Holdings: '持仓列表和详情',
    Settlements: '待结算清单和确认结算',
    Settings: '用户管理和系统设置',
  }
  return descs[route.name as string] || ''
})

function handleRefresh() {
  // TODO: 刷新当前页面数据
  window.location.reload()
}

function handleUnifiedEntry() {
  router.push({ name: 'Ledger', query: { action: 'create' } })
}

function handleNewOrder() {
  router.push({ name: 'Orders', query: { action: 'create' } })
}

function handleProfile() {
  router.push({ name: 'Settings' })
}
</script>
