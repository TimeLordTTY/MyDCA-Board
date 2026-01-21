<template>
  <div style="display: flex; flex-direction: column; height: 100vh; overflow: hidden;">
    <!-- 统一记账对话框 -->
    <UnifiedEntryModal v-model="unifiedEntryVisible" @success="handleEntrySuccess" />
    
    <!-- 快速支出对话框 -->
    <QuickEntryModal
      v-model="quickExpenseVisible"
      type="EXPENSE"
      @success="handleEntrySuccess"
    />
    
    <!-- 快速收入对话框 -->
    <QuickEntryModal
      v-model="quickIncomeVisible"
      type="INCOME"
      @success="handleEntrySuccess"
    />

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
          <button class="btn" @click="handleQuickExpense">⚡ 快速支出</button>
          <button class="btn" @click="handleQuickIncome">⚡ 快速收入</button>
          <button class="btn primary" @click="handleUnifiedEntry">📝 记一笔</button>
          <div class="avatar" @click="handleProfile">👤</div>
        </div>
      </div>
    </div>

    <div class="wrap" style="flex: 1; min-height: 0; overflow: hidden; display: flex; flex-direction: column;">
      <!-- Page Content -->
      <!--
        关键：页面滚动应该发生在 router-view 内部（而不是 body）。
        否则 MainLayout/body 的 overflow:hidden 会导致 Dashboard 等页面无法上下滚动。
      -->
      <router-view style="flex: 1; min-height: 0; overflow: auto;" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import UnifiedEntryModal from '../components/UnifiedEntryModal.vue'
import QuickEntryModal from '../components/QuickEntryModal.vue'

const router = useRouter()
const route = useRoute()

const unifiedEntryVisible = ref(false)
const quickExpenseVisible = ref(false)
const quickIncomeVisible = ref(false)

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
  unifiedEntryVisible.value = true
}

function handleQuickExpense() {
  quickExpenseVisible.value = true
}

function handleQuickIncome() {
  quickIncomeVisible.value = true
}

function handleEntrySuccess() {
  // 记账成功后，如果当前在流水页面，可以刷新数据
  // 这里可以触发一个全局事件，让各个页面自己决定是否刷新
  if (route.name === 'Ledger' || route.name === 'Orders') {
    // 触发页面刷新（通过事件或直接刷新）
    window.dispatchEvent(new CustomEvent('data-refresh'))
  }
}

function handleProfile() {
  router.push({ name: 'Settings' })
}
</script>
