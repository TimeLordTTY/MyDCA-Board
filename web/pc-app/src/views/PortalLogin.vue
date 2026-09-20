<template>
  <main class="portal-login-page">
    <section class="portal-login-card" aria-labelledby="portal-login-title">
      <div class="portal-mark" aria-hidden="true">T</div>
      <p class="eyebrow">TIMELORDTTY.CN</p>
      <h1 id="portal-login-title">站点目录</h1>
      <p class="intro">使用财富中枢账号登录，进入你的站点入口。</p>

      <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" size="large" placeholder="财富中枢用户名" autocomplete="username" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            size="large"
            type="password"
            placeholder="密码"
            autocomplete="current-password"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-button class="submit-button" type="primary" size="large" :loading="loading" native-type="submit">
          进入站点目录
        </el-button>
      </el-form>

      <p class="hint">账号由财富中枢统一管理，不创建独立 Portal 账号。</p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElNotification, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@wealth-hub/shared'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login(form)
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect
      : '/portal'
    await router.replace(redirect)
  } catch (error: any) {
    ElNotification.error({ title: '登录失败', message: error.message || '请检查用户名和密码', position: 'bottom-right' })
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.portal-login-page {
  display: grid;
  min-height: 100vh;
  padding: 24px;
  place-items: center;
  background:
    radial-gradient(circle at 12% 16%, rgba(74, 139, 213, 0.22), transparent 31%),
    radial-gradient(circle at 86% 80%, rgba(66, 164, 132, 0.17), transparent 33%),
    #f5f8fc;
}

.portal-login-card {
  width: min(100%, 420px);
  padding: 44px;
  text-align: center;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(213, 224, 237, 0.95);
  border-radius: 28px;
  box-shadow: 0 28px 70px rgba(39, 62, 88, 0.14);
}

.portal-mark {
  display: grid;
  width: 52px;
  height: 52px;
  margin: 0 auto 20px;
  color: #fff;
  font-family: Georgia, serif;
  font-size: 25px;
  font-weight: 700;
  place-items: center;
  background: #20354c;
  border-radius: 17px;
  box-shadow: 0 12px 24px rgba(32, 53, 76, 0.24);
}

.eyebrow {
  margin: 0 0 8px;
  color: #8090a3;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.19em;
}

h1 {
  margin: 0;
  color: #203047;
  font-size: 30px;
  letter-spacing: -0.04em;
}

.intro {
  margin: 12px 0 30px;
  color: #728196;
  font-size: 14px;
}

.submit-button {
  width: 100%;
  margin-top: 4px;
}

.hint {
  margin: 22px 0 0;
  color: #99a5b4;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 480px) {
  .portal-login-page {
    padding: 16px;
  }

  .portal-login-card {
    padding: 36px 26px;
  }
}
</style>
