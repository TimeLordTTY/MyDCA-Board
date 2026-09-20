<template>
  <div class="portal-page">
    <div class="portal-shell">
      <header class="portal-header">
        <div>
          <div class="eyebrow">TIMELORDTTY.CN</div>
          <h1>我的站点目录</h1>
          <p>从这里进入各个子页面。你也可以继续直接访问原有子地址。</p>
        </div>
        <div class="header-actions">
          <span v-if="displayName" class="welcome">你好，{{ displayName }}</span>
          <el-button text @click="handleLogout">退出登录</el-button>
        </div>
      </header>

      <section class="section">
        <div class="section-title">
          <span>应用入口</span>
          <small>已接入 Wealth Hub 账号认证</small>
        </div>

        <div class="app-grid">
          <a
            v-for="item in apps"
            :key="item.href"
            class="app-card"
            :href="item.href"
          >
            <div class="app-icon" :class="item.iconClass">
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

      <section class="section quick-section">
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
        入口页面只负责导航，原有子地址和登录逻辑保持不变。
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  DataAnalysis,
  Iphone,
  House,
  Wallet,
  Document,
  TrendCharts,
  Setting,
} from '@element-plus/icons-vue'
import { useUserStore } from '@wealth-hub/shared'

const router = useRouter()
const userStore = useUserStore()

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
    description: '适合手机访问的快速录入、看板、待结算与持仓页面。',
    path: '/wealth-hub-mobile/',
    href: '/wealth-hub-mobile/dashboard',
    icon: Iphone,
    iconClass: 'violet',
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

onMounted(async () => {
  if (userStore.user) return

  try {
    await userStore.fetchCurrentUser()
  } catch {
    localStorage.removeItem('token')
    router.replace({ name: 'Login', query: { redirect: '/portal' } })
  }
})

async function handleLogout() {
  try {
    await userStore.logout()
  } finally {
    router.replace({ name: 'Login', query: { redirect: '/portal' } })
  }
}
</script>

<style scoped>
.portal-page {
  min-height: 100vh;
  padding: 56px 24px 36px;
  background:
    radial-gradient(circle at 10% 0%, rgba(78, 164, 255, 0.18), transparent 34%),
    radial-gradient(circle at 92% 10%, rgba(143, 104, 255, 0.14), transparent 30%),
    linear-gradient(180deg, #f7fbff 0%, #f4f7fb 100%);
}

.portal-shell {
  width: min(1120px, 100%);
  margin: 0 auto;
}

.portal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 32px;
  margin-bottom: 42px;
}

.eyebrow {
  margin-bottom: 10px;
  color: #6d7f93;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.portal-header h1 {
  margin: 0;
  color: #172033;
  font-size: clamp(32px, 4vw, 48px);
  line-height: 1.08;
  letter-spacing: -0.04em;
}

.portal-header p {
  margin: 14px 0 0;
  color: #6c7a8d;
  font-size: 16px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
}

.welcome {
  color: #66758a;
  font-size: 14px;
}

.section + .section {
  margin-top: 34px;
}

.section-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.section-title > span {
  color: #263246;
  font-size: 18px;
  font-weight: 700;
}

.section-title small {
  color: #8a97a8;
  font-size: 13px;
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.app-card {
  display: flex;
  gap: 18px;
  min-height: 176px;
  padding: 24px;
  color: inherit;
  text-decoration: none;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(219, 228, 239, 0.92);
  border-radius: 24px;
  box-shadow: 0 14px 42px rgba(41, 61, 89, 0.08);
  transition: transform 180ms ease, box-shadow 180ms ease, border-color 180ms ease;
}

.app-card:hover {
  transform: translateY(-4px);
  border-color: rgba(116, 163, 226, 0.55);
  box-shadow: 0 22px 54px rgba(41, 61, 89, 0.13);
}

.app-icon {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 54px;
  height: 54px;
  border-radius: 18px;
  font-size: 25px;
}

.app-icon.blue {
  color: #347dd6;
  background: #e8f3ff;
}

.app-icon.violet {
  color: #7658d7;
  background: #f0ecff;
}

.app-content {
  min-width: 0;
  flex: 1;
}

.app-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.app-title-row h2 {
  margin: 1px 0 0;
  color: #233047;
  font-size: 20px;
}

.app-title-row .el-icon {
  color: #9aa7b6;
}

.app-content p {
  min-height: 44px;
  margin: 12px 0 18px;
  color: #718095;
  font-size: 14px;
  line-height: 1.6;
}

.app-content code {
  color: #718095;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  background: #f4f7fa;
  border-radius: 8px;
  padding: 5px 8px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.quick-card {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 74px;
  padding: 12px;
  color: #536277;
  text-decoration: none;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(220, 228, 238, 0.9);
  border-radius: 18px;
  transition: background 160ms ease, transform 160ms ease;
}

.quick-card:hover {
  transform: translateY(-2px);
  color: #347dd6;
  background: #fff;
}

.portal-footer {
  margin-top: 34px;
  color: #9aa6b4;
  font-size: 12px;
  text-align: center;
}

@media (max-width: 820px) {
  .portal-page {
    padding: 34px 16px 28px;
  }

  .portal-header {
    flex-direction: column;
    gap: 16px;
    margin-bottom: 30px;
  }

  .app-grid {
    grid-template-columns: 1fr;
  }

  .quick-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .section-title {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }
}

@media (max-width: 420px) {
  .app-card {
    padding: 20px;
  }

  .quick-grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
