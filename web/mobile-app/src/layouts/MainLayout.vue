<template>
  <div class="main-layout">
    <!-- 页面内容区域 -->
    <div class="page-content">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </div>

    <!-- 底部Tab导航栏 -->
    <van-tabbar 
      v-model="activeTab" 
      fixed 
      placeholder
      safe-area-inset-bottom
      @change="handleTabChange"
    >
      <van-tabbar-item 
        v-for="tab in tabBarItems" 
        :key="tab.name"
        :name="tab.name"
        :to="tab.path"
        :icon="tab.icon"
        :badge="tab.badge"
      >
        {{ tab.title }}
      </van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { settlementApi } from '@wealth-hub/shared'

const route = useRoute()
const router = useRouter()

const activeTab = ref('dashboard')
const pendingCount = ref(0)

// Tab导航配置
const tabBarItems = computed(() => [
  {
    name: 'dashboard',
    path: '/dashboard',
    title: '看板',
    icon: 'home-o',
  },
  {
    name: 'quick-entry',
    path: '/quick-entry',
    title: '快速录入',
    icon: 'plus',
  },
  {
    name: 'settlements',
    path: '/settlements',
    title: '待结算',
    icon: 'clock-o',
    badge: pendingCount.value > 0 ? pendingCount.value : undefined,
  },
  {
    name: 'holdings',
    path: '/holdings',
    title: '持仓',
    icon: 'chart-trending-o',
  },
  {
    name: 'settings',
    path: '/settings',
    title: '我的',
    icon: 'user-o',
  },
])

// 监听路由变化，同步Tab状态
watch(
  () => route.name,
  (name) => {
    if (name && typeof name === 'string') {
      activeTab.value = name.toLowerCase()
    }
  },
  { immediate: true }
)

// Tab切换处理
function handleTabChange(name: string) {
  const tab = tabBarItems.value.find(t => t.name === name)
  if (tab) {
    router.push(tab.path)
  }
}

// 加载待结算数量
async function loadPendingCount() {
  try {
    const settlements = await settlementApi.getPendingSettlements()
    pendingCount.value = settlements.length
  } catch (error) {
    console.error('加载待结算数量失败:', error)
  }
}

onMounted(() => {
  loadPendingCount()
  
  // 监听数据刷新事件
  window.addEventListener('data-refresh', loadPendingCount)
})
</script>

<style scoped>
.main-layout {
  width: 100%;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.page-content {
  flex: 1;
  width: 100%;
  overflow-x: hidden;
}

/* 页面切换动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
