<template>
  <div class="wealth-shell" :class="{ 'nav-pinned': navExpanded }">
    <a class="wealth-skip" href="#wealth-content">跳到主内容</a>
    <aside class="wealth-rail" aria-label="主导航">
      <div class="wealth-brand"><span class="wealth-monogram" aria-hidden="true">W</span><b class="wealth-nav-label">财富中枢</b></div>
      <button class="wealth-rail-toggle" type="button" :aria-expanded="navExpanded" aria-controls="desktop-navigation" @click="navExpanded = !navExpanded">
        <span aria-hidden="true">{{ navExpanded ? '‹' : '›' }}</span><span class="wealth-nav-label">{{ navExpanded ? '收起导航' : '展开导航' }}</span>
        <span class="wealth-sr-only">{{ navExpanded ? '收起完整导航' : '展开完整导航' }}</span>
      </button>
      <WealthNavigation id="desktop-navigation" />
      <router-link to="/portal" class="wealth-directory" title="站点目录" aria-label="站点目录"><span aria-hidden="true">↗</span><span class="wealth-nav-label">站点目录</span></router-link>
    </aside>
    <section class="wealth-main">
      <header class="wealth-header">
        <div class="wealth-heading">
          <button ref="menuButton" class="wealth-mobile-menu wh-button" type="button" aria-controls="mobile-navigation" :aria-expanded="mobileMenuOpen" @click="mobileMenuOpen = true">目录</button>
          <div>
            <nav class="wealth-breadcrumb" aria-label="面包屑"><router-link to="/dashboard">财富中枢</router-link><span aria-hidden="true"> / </span><span>{{ currentNav?.group || '业务页面' }}</span></nav>
            <h1>{{ currentNav?.label || '财富中枢' }}</h1>
          </div>
        </div>
        <div class="wealth-identity" aria-live="polite">
          <router-link to="/settings" aria-label="账号设置">{{ identity.account }}</router-link>
          <span>{{ identity.scope }}</span>
          <button class="wh-button" type="button" :disabled="loggingOut" @click="handleLogout">{{ loggingOut ? '退出中…' : '安全退出' }}</button>
        </div>
        <div v-if="showBusinessActions" class="wealth-actions" aria-label="页面操作">
          <button class="wh-button" type="button" :disabled="refreshingData" title="仅主动点击后重算历史余额并刷新页面" @click="handleRefresh">{{ refreshingData ? '重算中…' : '刷新数据（重算历史余额）' }}</button>
          <button class="wh-button" type="button" :disabled="refreshingMarket" @click="handleRefreshMarketData">{{ refreshingMarket ? '采集中…' : '刷新行情' }}</button>
          <button class="wh-button wh-button--primary" type="button" @click="handleUnifiedEntry">记一笔</button>
        </div>
      </header>
      <main id="wealth-content" ref="content" class="wealth-content" tabindex="-1">
        <router-view />
      </main>
    </section>
    <el-drawer v-model="mobileMenuOpen" title="财富中枢 · 目录" direction="ltr" size="min(300px, 88vw)" class="wealth-mobile-drawer" @closed="menuButton?.focus()">
      <WealthNavigation id="mobile-navigation" @navigate="mobileMenuOpen = false" />
      <router-link to="/portal" class="wealth-directory" @click="mobileMenuOpen = false">站点目录 ↗</router-link>
    </el-drawer>
    <UnifiedEntryModal v-model="unifiedEntryVisible" @success="handleEntrySuccess" />
    <QuickEntryModal v-model="quickExpenseVisible" type="EXPENSE" @success="handleEntrySuccess" />
    <QuickEntryModal v-model="quickIncomeVisible" type="INCOME" @success="handleEntrySuccess" />
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElNotification } from 'element-plus'
import UnifiedEntryModal from '../components/UnifiedEntryModal.vue'
import QuickEntryModal from '../components/QuickEntryModal.vue'
import { productApi, apiClient, ledgerApi, useUserStore } from '@wealth-hub/shared'
import WealthNavigation from '../components/WealthNavigation.vue'
import { identityContext, navItems } from './navigation'
import '../styles/navigation-shell.css'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const currentNav = computed(() => navItems.find(item => item.name === route.name))
const identity = computed(() => identityContext(userStore.user))
const showBusinessActions = computed(() => !['GoalBudgetCenter', 'DataReadiness', 'StrategyLab', 'RiskCenter', 'RebalanceCenter'].includes(String(route.name)))
const navExpanded = ref(false)
const mobileMenuOpen = ref(false)
const menuButton = ref<HTMLButtonElement>()
const content = ref<HTMLElement>()
const loggingOut = ref(false)
const wideScreen = window.matchMedia('(min-width: 769px)')
const closeOnWideScreen = () => { if (wideScreen.matches) mobileMenuOpen.value = false }

watch(() => route.fullPath, () => {
  mobileMenuOpen.value = false
  content.value?.scrollTo({ top: 0 })
})
onMounted(async () => {
  wideScreen.addEventListener('change', closeOnWideScreen)
  if (!userStore.user) {
    try { await userStore.fetchCurrentUser() }
    catch { /* The shared client handles expired sessions; never invent an identity. */ }
  }
})
onUnmounted(() => wideScreen.removeEventListener('change', closeOnWideScreen))

async function handleLogout() {
  if (loggingOut.value) return
  loggingOut.value = true
  try { await userStore.logout() }
  catch { /* Local logout must also complete when the server is unavailable. */ }
  finally {
    localStorage.removeItem('token')
    userStore.$patch({ token: null, user: null })
    await router.replace({ name: 'Login' })
    loggingOut.value = false
  }
}

const unifiedEntryVisible = ref(false)
const quickExpenseVisible = ref(false)
const quickIncomeVisible = ref(false)
const refreshingMarket = ref(false)
const refreshingData = ref(false)

async function handleRefresh() {
  if (refreshingData.value) return
  try {
    refreshingData.value = true
    // 触发后台历史余额重算，同时刷新页面数据。
    // 如果源流水数据正确，重算应当是幂等的，重复执行也不会改变正确结果。
    ElNotification.info({
      title: '刷新中',
      message: '已触发历史余额重算（后台执行中），页面数据将刷新',
      position: 'bottom-right',
      duration: 2500,
    })

    // 立即刷新可读数据；历史余额重算仍只由本按钮主动触发。
    window.dispatchEvent(new CustomEvent('data-refresh'))
    await ledgerApi.recalculateAllBalanceHistory()
      .then((res) => {
        ElNotification.success({
          title: '刷新完成',
          message: `${res.message}（耗时 ${res.durationMs}ms）`,
          position: 'bottom-right',
          duration: 4000,
        })
        window.dispatchEvent(new CustomEvent('data-refresh'))
      })
      .catch((error: any) => {
        ElNotification.error({
          title: '重算失败',
          message: error.message || '重算历史余额失败',
          position: 'bottom-right',
        })
      })
  } catch (error: any) {
    ElNotification.error({
      title: '错误',
      message: error.message || '刷新失败',
      position: 'bottom-right',
    })
  } finally {
    refreshingData.value = false
  }
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

function handleEntrySuccess() {
  // 记账成功后，触发当前页面的数据刷新
  // 通过全局事件通知各个页面刷新数据
  window.dispatchEvent(new CustomEvent('data-refresh'))
}

</script>
