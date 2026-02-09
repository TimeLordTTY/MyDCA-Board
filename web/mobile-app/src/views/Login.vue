<template>
  <div class="login-page">
    <!-- 背景装饰 -->
    <div class="login-bg">
      <div class="bg-gradient"></div>
    </div>

    <!-- 登录表单卡片 -->
    <div class="login-container">
      <div class="login-header">
        <div class="logo">
          <van-icon name="balance-pay" size="48" color="#4ea4ff" />
        </div>
        <h1 class="title">财富中枢</h1>
        <p class="subtitle">个人财富管理平台</p>
      </div>

      <div class="login-form">
        <van-form @submit="handleLogin">
          <van-cell-group inset>
            <van-field
              v-model="form.username"
              name="username"
              label="用户名"
              placeholder="请输入用户名"
              left-icon="user-o"
              :rules="[{ required: true, message: '请输入用户名' }]"
              clearable
            />
            <van-field
              v-model="form.password"
              type="password"
              name="password"
              label="密码"
              placeholder="请输入密码"
              left-icon="lock"
              :rules="[{ required: true, message: '请输入密码' }]"
              clearable
            />
          </van-cell-group>

          <div class="login-actions">
            <van-button
              round
              block
              type="primary"
              native-type="submit"
              :loading="loading"
              loading-text="登录中..."
              class="login-button"
            >
              登录
            </van-button>

            <div class="register-link">
              <span>还没有账号？</span>
              <span class="link" @click="showRegister = true">立即注册</span>
            </div>
          </div>
        </van-form>
      </div>
    </div>

    <!-- 注册弹窗 -->
    <van-popup
      v-model:show="showRegister"
      position="bottom"
      :style="{ height: '70%' }"
      round
      closeable
      close-icon-position="top-right"
    >
      <div class="register-popup">
        <h2 class="popup-title">注册账号</h2>
        <van-form @submit="handleRegister">
          <van-cell-group inset>
            <van-field
              v-model="registerForm.username"
              name="username"
              label="用户名"
              placeholder="请输入用户名"
              left-icon="user-o"
              :rules="[{ required: true, message: '请输入用户名' }]"
              clearable
            />
            <van-field
              v-model="registerForm.password"
              type="password"
              name="password"
              label="密码"
              placeholder="请输入密码（至少6位）"
              left-icon="lock"
              :rules="[
                { required: true, message: '请输入密码' },
                { min: 6, message: '密码至少6位' }
              ]"
              clearable
            />
            <van-field
              v-model="registerForm.nickname"
              name="nickname"
              label="昵称"
              placeholder="请输入昵称"
              left-icon="smile-o"
              :rules="[{ required: true, message: '请输入昵称' }]"
              clearable
            />
          </van-cell-group>

          <div class="register-actions">
            <van-button
              round
              block
              type="primary"
              native-type="submit"
              :loading="registerLoading"
              loading-text="注册中..."
            >
              注册
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@wealth-hub/shared'
import { authApi } from '@wealth-hub/shared'
import { showSuccessToast, showFailToast } from 'vant'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const registerLoading = ref(false)
const showRegister = ref(false)

const form = ref({
  username: '',
  password: '',
})

const registerForm = ref({
  username: '',
  password: '',
  nickname: '',
})

async function handleLogin() {
  if (loading.value) return

  try {
    loading.value = true
    await userStore.login({
      username: form.value.username,
      password: form.value.password,
    })
    
    showSuccessToast('登录成功')
    
    // 跳转到目标页面或看板
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.push(redirect)
  } catch (error: any) {
    showFailToast(error.message || '登录失败，请检查用户名和密码')
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  if (registerLoading.value) return

  try {
    registerLoading.value = true
    await userStore.register({
      username: registerForm.value.username,
      password: registerForm.value.password,
      nickname: registerForm.value.nickname,
    })
    
    showSuccessToast('注册成功')
    showRegister.value = false
    
    // 跳转到看板
    router.push('/dashboard')
  } catch (error: any) {
    showFailToast(error.message || '注册失败，请重试')
  } finally {
    registerLoading.value = false
  }
}
</script>

<style scoped>
.login-page {
  width: 100%;
  min-height: 100vh;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  overflow: hidden;
}

.login-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 0;
}

.bg-gradient {
  width: 100%;
  height: 100%;
  background: radial-gradient(
      circle at 30% 20%,
      rgba(78, 164, 255, 0.3) 0%,
      transparent 50%
    ),
    radial-gradient(
      circle at 70% 80%,
      rgba(124, 199, 255, 0.2) 0%,
      transparent 50%
    ),
    linear-gradient(180deg, #eef7ff 0%, #f4faff 100%);
}

.login-container {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 400px;
  animation: fadeIn 0.5s ease-out;
}

.login-header {
  text-align: center;
  margin-bottom: 40px;
}

.logo {
  margin-bottom: 16px;
  animation: float 3s ease-in-out infinite;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

.title {
  font-size: 32px;
  font-weight: 700;
  color: var(--text);
  margin: 0 0 8px 0;
}

.subtitle {
  font-size: 14px;
  color: var(--muted);
  margin: 0;
}

.login-form {
  background: var(--card);
  border-radius: 20px;
  padding: 24px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.1);
}

.login-actions {
  margin-top: 24px;
}

.login-button {
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
}

.register-link {
  text-align: center;
  font-size: 14px;
  color: var(--muted);
}

.register-link .link {
  color: var(--primary);
  font-weight: 500;
  margin-left: 4px;
}

.register-popup {
  padding: 24px;
  height: 100%;
  overflow-y: auto;
}

.popup-title {
  font-size: 20px;
  font-weight: 600;
  text-align: center;
  margin: 0 0 24px 0;
  color: var(--text);
}

.register-actions {
  margin-top: 24px;
}

.register-actions .van-button {
  height: 48px;
  font-size: 16px;
  font-weight: 600;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
