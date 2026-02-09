<template>
  <div class="sub-page">
    <van-nav-bar 
      :title="title" 
      left-text="返回" 
      left-arrow 
      @click-left="handleBack" 
      fixed 
      placeholder 
    />
    <div class="page-container">
      <van-empty :description="description" image="search" />
      <div class="tip-text">该功能正在开发中，请在PC端操作</div>
      <div class="tip-link">
        <van-button type="primary" size="small" round @click="handleBack">返回上一页</van-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const titleMap: Record<string, string> = {
  'Accounts': '账户管理',
  'Products': '产品管理',
  'Ledger': '流水查询',
  'Orders': '订单管理',
}

const title = computed(() => titleMap[route.name as string] || '功能页面')
const description = computed(() => `${title.value} · 移动端开发中`)

function handleBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/dashboard')
  }
}
</script>

<style scoped>
.sub-page {
  width: 100%;
  min-height: 100vh;
  background: var(--bg);
}

.page-container {
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.tip-text {
  font-size: 14px;
  color: var(--muted);
  text-align: center;
  margin-top: 8px;
}

.tip-link {
  margin-top: 24px;
}
</style>
