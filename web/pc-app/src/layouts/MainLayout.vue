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
          <button class="btn ghost" @click="handleRefreshMarketData" :disabled="refreshingMarket">
            {{ refreshingMarket ? '⏳ 采集中...' : '📈 刷新行情' }}
          </button>
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
import { ElNotification } from 'element-plus'
import UnifiedEntryModal from '../components/UnifiedEntryModal.vue'
import QuickEntryModal from '../components/QuickEntryModal.vue'
import { productApi, apiClient } from '@wealth-hub/shared'

const router = useRouter()
const route = useRoute()

const unifiedEntryVisible = ref(false)
const quickExpenseVisible = ref(false)
const quickIncomeVisible = ref(false)
const refreshingMarket = ref(false)

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

async function handleRefreshMarketData() {
  if (refreshingMarket.value) return
  
  try {
    refreshingMarket.value = true

    // 优先使用 shared 中封装好的 API 方法；
    // 如果旧版本 shared 中还没有该方法，则直接调用底层 HTTP 接口，避免报错。
    if ((productApi as any).refreshAllMarketData) {
      await (productApi as any).refreshAllMarketData()
    } else {
      await apiClient.post('/products/refresh-all-market-data')
    }
    ElNotification.success({
      title: '成功',
      message: '行情数据采集任务已启动，将在后台执行',
      position: 'bottom-right',
      duration: 3000
    })
  } catch (error: any) {
    ElNotification.error({
      title: '错误',
      message: error.message || '刷新行情失败',
      position: 'bottom-right'
    })
  } finally {
    // 3秒后恢复按钮状态（实际采集在后台执行）
    setTimeout(() => {
      refreshingMarket.value = false
    }, 3000)
  }
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
  // 记账成功后，触发当前页面的数据刷新
  // 通过全局事件通知各个页面刷新数据
  window.dispatchEvent(new CustomEvent('data-refresh'))
}

function handleProfile() {
  router.push({ name: 'Settings' })
}
</script>
