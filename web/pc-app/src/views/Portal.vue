<template>
  <main class="portal-page">
    <div class="portal-shell">
      <header class="portal-header">
        <div>
          <div class="eyebrow">TIMELORDTTY.CN</div>
          <h1>我的站点目录</h1>
          <p>从这里进入各个子页面。你也可以继续直接访问原有子地址。</p>
        </div>
        <div class="header-actions">
          <span v-if="displayName" class="welcome">你好，{{ displayName }}</span>
          <el-button text :disabled="loggingOut" @click="handleLogout">{{ loggingOut ? '退出中…' : '退出登录' }}</el-button>
        </div>
      </header>

      <p v-if="identityError" class="auth-feedback" role="alert">{{ identityError }} <button class="wh-button" :disabled="identityLoading" @click="loadIdentity">重试读取账号</button></p>
      <section class="section" aria-label="应用入口">
        <div class="section-title">
          <span>应用入口</span>
          <small>各站点分别校验权限；无限探索仅限 Owner</small>
        </div>

        <div class="app-grid">
          <button
            class="app-card portal-explore-card"
            type="button"
            aria-label="进入无限探索"
            :disabled="identityLoading || privateBusy || loggingOut"
            :aria-busy="privateBusy"
            aria-describedby="explore-status"
            @click="enterExplore"
          >
            <span class="app-icon green" aria-hidden="true"><Reading /></span>
            <span class="app-content">
              <span class="app-title-row">
                <span class="portal-explore-heading">无限探索</span>
                <el-icon aria-hidden="true"><ArrowRight /></el-icon>
              </span>
              <span class="portal-explore-description">使用当前 Wealth Hub 身份接续私有会话，无需再次输入密码。</span>
              <code>/explore/</code>
              <span
                id="explore-status"
                class="portal-explore-status"
                :class="{ 'is-visible': privateBusy || privateMessage !== '验证当前账号后可进入，仅 Owner 获准访问。' }"
                role="status"
                aria-live="polite"
              >{{ privateMessage }}</span>
            </span>
          </button>
          <a
            v-for="item in apps"
            :key="item.href"
            class="app-card"
            :href="item.href"
            :target="item.external ? '_blank' : undefined"
            :rel="item.external ? 'noopener noreferrer' : undefined"
            :aria-label="item.title + (item.external ? '（新窗口，独立站点）' : '')"
          >
            <div class="app-icon" :class="item.iconClass" aria-hidden="true">
              <component :is="item.icon" />
            </div>
            <div class="app-content">
              <div class="app-title-row">
                <h2>{{ item.title }}</h2>
                <el-icon><ArrowRight /></el-icon>
              </div>
              <p>{{ item.description }}</p>
              <code>{{ item.path }}</code>
            </div>
          </a>
        </div>
      </section>

      <section class="section quick-section" aria-label="财富中枢快捷入口">
        <div class="section-title">
          <span>财富中枢快捷入口</span>
          <small>少点几下，直接到常用页面</small>
        </div>

        <div class="quick-grid">
          <a
            v-for="item in quickLinks"
            :key="item.href"
            class="quick-card"
            :href="item.href"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </a>
        </div>
      </section>

      <footer class="portal-footer">
        外部站点在新窗口打开，并独立核实访问权限。
      </footer>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  DataAnalysis,
  PictureFilled,
  Reading,
  Sunny,
  Iphone,
  House,
  Wallet,
  Document,
  TrendCharts,
  Setting,
} from '@element-plus/icons-vue'
import { useUserStore } from '@wealth-hub/shared'
import { endPrivateSession, readSessionIdentity, sessionRevision } from '../auth/session'
import { privateSiteSession } from '../auth/privateSites'

const router = useRouter()
const userStore = useUserStore()
const loggingOut = ref(false), identityLoading = ref(false), identityError = ref('')
const { busy: privateBusy, message: privateMessage } = privateSiteSession
const enterExplore = () => privateSiteSession.enter(() => readSessionIdentity(userStore))

const apps = [
  {
    title: '财富中枢 · PC',
    description: '完整的资产、账户、流水、持仓与投资管理界面。',
    path: '/wealth-hub/',
    href: '/wealth-hub/dashboard',
    icon: DataAnalysis,
    iconClass: 'blue',
  },
  {
    title: '财富中枢 · 移动版',
    description: '历史兼容 H5 入口；原生移动能力由 Android 应用提供。',
    path: '/wealth-hub-mobile/',
    href: '/wealth-hub-mobile/dashboard',
    icon: Iphone,
    iconClass: 'violet',
  },
  {
    title: '游戏墙',
    description: '浏览和管理已收录的游戏内容，访问权限由该站独立校验。',
    path: '/game-wall/games',
    href: '/game-wall/games',
    icon: PictureFilled,
    iconClass: 'orange',
  },
  {
    title: '诗词站',
    description: '进入独立的诗词与创作站点。',
    path: 'poem.timelordtty.cn',
    href: 'https://poem.timelordtty.cn/',
    icon: Reading,
    iconClass: 'green',
    external: true,
  },
  {
    title: '情晓录',
    description: '诗词站中的情晓录页面。',
    path: 'poem.timelordtty.cn/qingxiaolu/',
    href: 'https://poem.timelordtty.cn/qingxiaolu/',
    icon: Sunny,
    iconClass: 'rose',
    external: true,
  },
]

const quickLinks = [
  { title: '总览', href: '/wealth-hub/dashboard', icon: House },
  { title: '账户', href: '/wealth-hub/accounts', icon: Wallet },
  { title: '流水', href: '/wealth-hub/ledger', icon: Document },
  { title: '持仓', href: '/wealth-hub/holdings', icon: TrendCharts },
  { title: '设置', href: '/wealth-hub/settings', icon: Setting },
]

const displayName = computed(() => userStore.user?.nickname || userStore.user?.username || '')

async function loadIdentity() {
  if (identityLoading.value) return
  identityLoading.value = true
  identityError.value = ''
  try {
    await readSessionIdentity(userStore)
  } catch (error: unknown) {
    identityError.value = `${error instanceof Error ? error.message : '账号读取失败'}。请检查网络后手动重试。`
  } finally { identityLoading.value = false }
}
onMounted(() => { void loadIdentity() })
onBeforeUnmount(() => { if (privateBusy.value) void endPrivateSession('portal-leave') })

async function handleLogout() {
  if (loggingOut.value) return
  loggingOut.value = true
  const token = userStore.token, revision = sessionRevision.value
  try {
    await endPrivateSession('logout')
    if (userStore.token !== token || localStorage.getItem('token') !== token || sessionRevision.value !== revision) return
    await userStore.logout()
  } catch {
    // Finish local logout even when the service is unreachable.
  } finally {
    // An older logout must not clear a different tab's newly restored account.
    if ((userStore.token === token && sessionRevision.value === revision && [token, null].includes(localStorage.getItem('token')))
      || (userStore.token === null && localStorage.getItem('token') === null)) {
      localStorage.removeItem('token')
      userStore.$patch({ token: null, user: null })
      await router.replace({ name: 'PortalLogin', query: { redirect: '/portal' } })
    }
    loggingOut.value = false
  }
}
</script>

<style src="../styles/auth-portal.css"></style>
