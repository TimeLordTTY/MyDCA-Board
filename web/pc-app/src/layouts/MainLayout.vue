<template>
  <div style="display: flex; flex-direction: column; height: 100vh; overflow: hidden;">
    <!-- Sticky Topbar -->
    <div class="topbar" style="flex-shrink: 0;">
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
            :class="{ active: route.name === item.name }"
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

    <div class="wrap" style="flex: 1; min-height: 0; overflow: hidden; display: flex; flex-direction: column;">
      <!-- Page Content -->
      <router-view style="flex: 1; min-height: 0; overflow: hidden;" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const navItems = [
  { name: 'Dashboard', path: '/dashboard', label: '总览' },
  { name: 'Ledger', path: '/ledger', label: '流水' },
  { name: 'Orders', path: '/orders', label: '订单&结算' },
  { name: 'Products', path: '/products', label: '产品' },
  { name: 'Accounts', path: '/accounts', label: '账户' },
  { name: 'Holdings', path: '/holdings', label: '持仓管理' },
  { name: 'Settings', path: '/settings', label: '设置' },
]

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
