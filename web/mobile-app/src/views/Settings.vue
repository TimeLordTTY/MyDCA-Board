<template>
  <div class="settings-page">
    <van-nav-bar title="我的" fixed placeholder />

    <div class="page-container">
      <!-- 用户信息卡片 -->
      <div class="user-card">
        <div class="user-avatar">
          <van-icon name="user-circle-o" size="64" color="#4ea4ff" />
        </div>
        <div class="user-info">
          <div class="user-name">{{ userStore.user?.nickname || userStore.user?.username || '用户' }}</div>
          <div class="user-meta">{{ userStore.user?.email || userStore.user?.username }}</div>
        </div>
      </div>

      <!-- 功能菜单 -->
      <van-cell-group inset>
        <van-cell title="账户管理" icon="balance-o" is-link @click="$router.push('/accounts')" />
        <van-cell title="产品管理" icon="goods-collect-o" is-link @click="$router.push('/products')" />
        <van-cell title="流水查询" icon="orders-o" is-link @click="$router.push('/ledger')" />
        <van-cell title="订单管理" icon="shopping-cart-o" is-link @click="$router.push('/orders')" />
      </van-cell-group>

      <!-- 其他设置 -->
      <van-cell-group inset style="margin-top: 16px">
        <van-cell title="关于" icon="info-o" is-link @click="showAbout = true" />
        <van-cell title="退出登录" icon="logout" is-link @click="handleLogout" />
      </van-cell-group>
    </div>

    <!-- 关于弹窗 -->
    <van-popup
      v-model:show="showAbout"
      position="center"
      round
      :style="{ width: '80%', padding: '24px' }"
    >
      <div class="about-content">
        <h3 class="about-title">财富中枢</h3>
        <p class="about-version">版本 1.0.0</p>
        <p class="about-desc">个人财富管理平台</p>
        <p class="about-desc">支持资产盘点、统一记账、投资建议等功能</p>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@wealth-hub/shared'
import { showConfirmDialog, showSuccessToast } from 'vant'

const router = useRouter()
const userStore = useUserStore()
const showAbout = ref(false)

async function handleLogout() {
  try {
    await showConfirmDialog({
      title: '确认退出',
      message: '确定要退出登录吗？',
    })
    
    await userStore.logout()
    showSuccessToast('已退出登录')
    router.push('/login')
  } catch {
    // 用户取消
  }
}
</script>

<style scoped>
.settings-page {
  width: 100%;
  min-height: 100vh;
  background: var(--bg);
}

.page-container {
  padding: 16px;
  padding-bottom: calc(50px + var(--safe-area-inset-bottom) + 16px);
}

/* 用户信息卡片 */
.user-card {
  background: var(--card);
  border-radius: var(--radius);
  padding: 24px;
  margin-bottom: 16px;
  box-shadow: var(--shadow);
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-avatar {
  flex-shrink: 0;
}

.user-info {
  flex: 1;
}

.user-name {
  font-size: 20px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 4px;
}

.user-meta {
  font-size: 14px;
  color: var(--muted);
}

/* 关于内容 */
.about-content {
  text-align: center;
}

.about-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--text);
  margin: 0 0 8px 0;
}

.about-version {
  font-size: 14px;
  color: var(--muted);
  margin: 0 0 16px 0;
}

.about-desc {
  font-size: 14px;
  color: var(--text);
  line-height: 1.6;
  margin: 8px 0;
}
</style>
