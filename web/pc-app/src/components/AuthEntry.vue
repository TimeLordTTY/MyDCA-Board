<template>
  <main class="auth-page">
    <section class="auth-story" aria-labelledby="auth-story-title">
      <router-link class="auth-brand" :to="portal ? '/login' : '/portal-login'"><span aria-hidden="true">W</span> WEALTH / HUB</router-link>
      <p class="auth-eyebrow">{{ portal ? 'YOUR DIGITAL DIRECTORY' : 'A CLEARER VIEW OF YOUR WEALTH' }}</p>
      <h1 id="auth-story-title">{{ portal ? '你的站点，\n从这里出发。' : '理清财富，\n留白给生活。' }}</h1>
      <p>{{ portal ? '一个账号，打开站点目录。各应用仍保留自己的访问与权限边界。' : '账户、持仓与每一笔流水，在一个清晰的空间里。' }}</p>
      <span class="auth-seal">{{ portal ? '站点目录 / PORTAL' : '财富中枢 / PERSONAL FINANCE' }}</span>
    </section>
    <section class="auth-card" aria-labelledby="auth-title">
      <p class="auth-eyebrow">{{ portal ? 'PORTAL ACCESS' : 'WELCOME BACK' }}</p>
      <h2 id="auth-title">{{ portal ? '登录站点目录' : '登录财富中枢' }}</h2>
      <p class="wh-note">使用你的财富中枢账号。{{ portal ? '无需创建独立门户账号。' : '数据与权限由服务端核实。' }}</p>
      <p v-if="route.query.reason === 'session'" role="status" class="auth-feedback">登录已失效或无访问权限，请重新登录。</p>
      <form :aria-busy="loading" @submit.prevent="submit(false)">
        <label for="auth-username">用户名</label>
        <input id="auth-username" v-model="form.username" class="wh-input" name="username" autocomplete="username" required :disabled="loading" />
        <label for="auth-password">密码</label>
        <input id="auth-password" v-model="form.password" class="wh-input" name="password" type="password" autocomplete="current-password" required :disabled="loading" />
        <p v-if="error" class="auth-feedback" role="alert">{{ error }}</p>
        <button class="wh-button wh-button--primary auth-submit" type="submit" :disabled="loading">{{ loading ? '正在验证账号…' : portal ? '进入站点目录' : '登录' }}</button>
        <button v-if="!portal" class="wh-button auth-submit" type="button" :disabled="loading" @click="register">还没有账号？立即注册</button>
      </form>
      <router-link class="auth-other" :to="portal ? '/login' : '/portal-login'">{{ portal ? '前往财富中枢登录 →' : '前往站点目录登录 →' }}</router-link>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@wealth-hub/shared'
import { loginDestination } from '../auth/session'
import '../styles/auth-portal.css'

const props = defineProps<{ portal?: boolean }>()
const router = useRouter(), route = useRoute(), userStore = useUserStore()
const form = reactive({ username: '', password: '' })
const loading = ref(false), error = ref('')
function register(event: MouseEvent) {
  const element = event.currentTarget as HTMLButtonElement
  if (element.form?.reportValidity()) void submit(true)
}
async function submit(registration: boolean) {
  if (loading.value || !form.username.trim() || !form.password) return
  loading.value = true
  error.value = ''
  try {
    const credentials = { username: form.username, password: form.password }
    if (registration) await userStore.register(credentials)
    else await userStore.login(credentials)
    form.password = ''
    await router.replace(loginDestination(router, route.query.redirect, props.portal))
  } catch (failure: unknown) {
    error.value = `${failure instanceof Error ? failure.message : '账号验证失败'}。请检查账号或网络后手动重试；不会自动重复提交。`
  } finally { loading.value = false }
}
</script>
