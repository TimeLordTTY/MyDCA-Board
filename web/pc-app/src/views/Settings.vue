<template>
  <div class="settings-page">
    <header><p class="eyebrow">WEALTH HUB / YOUR SPACE</p><h1>把这里，设成你的样子。</h1><p class="sub">个人资料、阅读偏好和账号安全，各自有序。</p></header>
    <nav class="section-nav" aria-label="设置章节"><a href="#settings-profile">01 用户资料</a><a href="#settings-appearance">02 外观主题</a><a href="#settings-security">03 账号安全</a><a href="#settings-family">04 家庭管理</a><a href="#settings-business">05 业务设置</a></nav>
    <div id="settings-appearance"><AppearanceSettings /><p class="sub appearance-note">外观仅保存此浏览器的阅读偏好；不保存或改变资产、交易和账本状态。</p></div>
    <p v-if="settingsError" class="error" role="alert">{{ settingsError }}</p>
    <div class="settings-workspace">

      <div class="grid">
        <!-- 个人信息 -->
        <section id="settings-profile" class="panel">
          <p class="eyebrow">01 / PROFILE</p><h2>用户资料</h2><p class="sub">更新联系方式，用户名保持与你的账号一致。</p>
          <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="88px">
            <el-form-item label="用户名">
              <el-input :model-value="userStore.user?.username" disabled />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="profileForm.nickname" placeholder="可选" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="profileForm.email" placeholder="可选" />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="profileForm.phone" placeholder="可选" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="profileSaving" @click="saveProfile">保存</el-button>
              <el-button :disabled="profileSaving" @click="resetProfile">还原资料输入</el-button>
            </el-form-item>
          </el-form>
        </section>

        <!-- 安全 -->
        <section id="settings-security" class="panel">
          <p class="eyebrow">03 / SECURITY</p><h2>账号安全</h2><p class="sub">修改密码需验证旧密码；提交后由服务端校验。</p>
          <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="88px">
            <el-form-item label="旧密码" prop="oldPassword">
              <el-input v-model="pwdForm.oldPassword" type="password" show-password autocomplete="current-password" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="pwdForm.newPassword" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="pwdForm.confirmPassword" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="pwdSaving" @click="changePassword">修改密码</el-button>
            </el-form-item>
          </el-form>
        </section>
      </div>

      <!-- 家庭成员 -->
      <section id="settings-family" class="panel family-panel">
        <div class="panel-header">
          <div>
            <p class="eyebrow">04 / FAMILY</p><h2>家庭成员</h2>
            <div class="sub">仅管理员可添加/移除/改角色；权限由服务端核验。</div>
          </div>
          <div class="actions">
            <el-button :disabled="!userStore.user?.familyId || membersLoading" @click="reloadMembers">刷新</el-button>
            <el-button type="primary" :disabled="!canManageFamily || membersLoading" @click="addDialogVisible = true">添加成员</el-button>
          </div>
        </div>

        <div v-if="!userStore.user?.familyId" class="td-muted" style="padding: 12px 0">
          当前账号未加入家庭，暂无家庭成员可管理。
        </div>

        <p v-if="membersLoading" role="status">正在读取家庭成员…</p>
        <p v-if="membersError" class="error" role="alert">{{ membersError }} <el-button :disabled="membersLoading" @click="reloadMembers">重试读取成员</el-button></p>
        <p v-if="userStore.user?.familyId && !membersLoading && !membersError && !canManageFamily" class="sub">当前没有已核实的管理员权限，成员信息可供查看。</p>
        <div class="member-table"><el-table v-if="userStore.user?.familyId" :data="members" style="width: 100%">
          <el-table-column prop="userId" label="用户ID" width="90" />
          <el-table-column prop="username" label="用户名" width="160" />
          <el-table-column prop="nickname" label="昵称" />
          <el-table-column prop="role" label="角色" width="120">
            <template #default="{ row }">
              <el-select :model-value="row.role" :disabled="!canManageFamily || familySaving" :aria-label="`${row.username}的角色`" size="small" style="width: 100px" @change="(v:any)=>onChangeRole(row.userId, v)">
                <el-option label="管理员" value="ADMIN" />
                <el-option label="成员" value="MEMBER" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button type="danger" link :disabled="!canManageFamily || familySaving" @click="removeMember(row.userId)">移除</el-button>
            </template>
          </el-table-column>
        </el-table></div>
      </section>
      <section id="settings-business" class="panel business-panel"><p class="eyebrow">05 / BUSINESS</p><h2>原有业务设置</h2><p class="sub">在对应页面核对规则与来源，再由你决定是否保存。</p><div class="business-links"><router-link :to="{ name: 'RiskCenter' }">风险观察规则 ↗</router-link><router-link :to="{ name: 'RebalanceCenter' }">资产配置规则 ↗</router-link><router-link :to="{ name: 'DataReadiness' }">数据就绪检查 ↗</router-link></div></section>
    </div>

    <!-- 添加成员弹窗 -->
    <el-dialog v-model="addDialogVisible" title="添加家庭成员" width="420px">
      <el-form :model="addForm" label-width="88px">
        <el-form-item label="用户名">
          <el-input v-model="addForm.username" placeholder="输入对方用户名" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="addForm.role" style="width: 100%">
            <el-option label="成员" value="MEMBER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="addSaving" @click="submitAddMember">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import AppearanceSettings from '../components/AppearanceSettings.vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessageBox, ElNotification, type FormInstance, type FormRules } from 'element-plus'
import { familyApi, type FamilyMember, userApi, useUserStore } from '@wealth-hub/shared'

const userStore = useUserStore()
const settingsError = ref('')
const showError = () => { settingsError.value = '设置未保存，请核对权限、输入与网络后重试。' }

// 个人信息
const profileFormRef = ref<FormInstance>()
const profileSaving = ref(false)
const profileForm = reactive({
  nickname: '',
  email: '',
  phone: '',
})

const profileRules: FormRules = {
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}

function fillProfileFromStore() {
  profileForm.nickname = userStore.user?.nickname || ''
  profileForm.email = userStore.user?.email || ''
  profileForm.phone = userStore.user?.phone || ''
}

async function resetProfile() {
  fillProfileFromStore()
  profileFormRef.value?.clearValidate()
}

async function saveProfile() {
  if (profileSaving.value || !profileFormRef.value || !await profileFormRef.value.validate().catch(() => false)) return
  settingsError.value = ''
  profileSaving.value = true
  try {
    await userApi.updateProfile({
      nickname: profileForm.nickname || undefined,
      email: profileForm.email || undefined,
      phone: profileForm.phone || undefined,
    })
    await userStore.fetchCurrentUser()
    fillProfileFromStore()
    ElNotification.success({ title: '成功', message: '个人信息已更新' })
  } catch { showError() } finally {
    profileSaving.value = false
  }
}

// 修改密码
const pwdFormRef = ref<FormInstance>()
const pwdSaving = ref(false)
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, message: '新密码至少 8 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (value !== pwdForm.newPassword) callback(new Error('两次输入不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

async function changePassword() {
  if (pwdSaving.value || !pwdFormRef.value || !await pwdFormRef.value.validate().catch(() => false)) return
  settingsError.value = ''
  pwdSaving.value = true
  try {
    await userApi.changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    pwdFormRef.value?.clearValidate()
    ElNotification.success({ title: '成功', message: '密码已修改' })
  } catch { showError() } finally {
    pwdSaving.value = false
  }
}

// 家庭成员
const members = ref<FamilyMember[]>([])
const membersLoading = ref(false), membersError = ref(''), familySaving = ref(false)
const memberIdentity = ref('')
const currentIdentity = () => `${userStore.user?.id ?? ''}:${userStore.user?.familyId ?? ''}`
const canManageFamily = computed(() => !!userStore.user?.familyId && memberIdentity.value === currentIdentity() && members.value.some(m => m.userId === userStore.user?.id && m.role === 'ADMIN'))
watch(() => currentIdentity(), () => { members.value = []; memberIdentity.value = ''; addDialogVisible.value = false })
const addDialogVisible = ref(false)
const addSaving = ref(false)
const addForm = reactive({
  username: '',
  role: 'MEMBER' as 'ADMIN' | 'MEMBER',
})

async function reloadMembers() {
  if (membersLoading.value) return
  members.value = []; memberIdentity.value = ''; membersError.value = ''
  if (!userStore.user?.familyId) return
  membersLoading.value = true
  const identity = currentIdentity()
  try {
    const result = await familyApi.getMembers()
    if (identity === currentIdentity()) { members.value = result; memberIdentity.value = identity }
  }
  catch { membersError.value = '家庭成员读取失败，请核对权限与网络后重试。' }
  finally { membersLoading.value = false }
}

async function submitAddMember() {
  if (!canManageFamily.value || addSaving.value || familySaving.value) return
  settingsError.value = ''
  if (!addForm.username.trim()) {
    ElNotification.warning({ title: '提示', message: '请输入用户名' })
    return
  }
  addSaving.value = true
  try {
    await familyApi.addMember({ username: addForm.username.trim(), role: addForm.role })
    addDialogVisible.value = false
    addForm.username = ''
    addForm.role = 'MEMBER'
    await reloadMembers()
    ElNotification.success({ title: '成功', message: '成员已添加' })
  } catch { showError() } finally {
    addSaving.value = false
  }
}

async function removeMember(userId: number) {
  if (!canManageFamily.value || familySaving.value) return
  settingsError.value = ''
  familySaving.value = true
  try {
    if (!await ElMessageBox.confirm('确定要移除该成员吗？', '确认移除', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }).then(() => true, () => false)) return
    if (!canManageFamily.value) return
    await familyApi.removeMember(userId)
    await reloadMembers()
    ElNotification.success({ title: '成功', message: '成员已移除' })
  } catch { showError() } finally { familySaving.value = false }
}

async function onChangeRole(userId: number, role: 'ADMIN' | 'MEMBER') {
  if (!canManageFamily.value || familySaving.value) return
  settingsError.value = ''
  familySaving.value = true
  try {
    if (!await ElMessageBox.confirm('确定修改该成员的家庭角色吗？', '确认角色变更', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }).then(() => true, () => false)) return
    if (!canManageFamily.value) return
    await familyApi.updateMemberRole(userId, role)
    ElNotification.success({ title: '成功', message: '角色已更新' })
    await reloadMembers()
  } catch { showError() } finally { familySaving.value = false }
}

onMounted(async () => {
  try {
    if (!userStore.user) {
      await userStore.fetchCurrentUser()
    }
    fillProfileFromStore()
    await reloadMembers()
  } catch { settingsError.value = '账号资料读取失败，请重新登录或刷新页面。' }
})
</script>

<style scoped>
.settings-page { max-width: 1380px; margin: auto; color: var(--text); padding: 12px 0; overflow-wrap: anywhere; }
.eyebrow { font-size: 11px; color: var(--muted); letter-spacing: .12em; }
h1, h2 { font-family: 'Songti SC', 'STSong', 'SimSun', serif; font-weight: 600; letter-spacing: -.03em; }
h1 { font-size: clamp(30px, 3.5vw, 46px); margin: 16px 0; }
h2 { font-size: 26px; margin: 12px 0; }
.sub { font-size: 14px; line-height: 1.85; color: var(--muted); }
.section-nav, .business-links { display: flex; flex-wrap: wrap; gap: 12px; margin: 26px 0; }
.section-nav a, .business-links a { padding: 12px 18px; border: 1px solid var(--line); border-radius: 999px; background: var(--card); color: var(--text); text-decoration: none; font-size: 13px; }
.appearance-note { margin: -12px 24px 28px; }
#settings-appearance :deep(.wh-card) { border-top: 4px solid var(--primary); }
.settings-workspace { display: grid; gap: 24px; }
.error { color: var(--wh-danger, #b52c33); font-size: 14px; line-height: 1.8; }
.member-table { overflow-x: auto; }
.member-table :deep(.el-table) { min-width: 680px; }
.panel :deep(.el-form) { margin-top: 24px; }
.panel :deep(.el-button) { min-height: 44px; border-radius: 999px; }
.panel :deep(.el-input__wrapper) { min-height: 40px; }
.panel, #settings-appearance { scroll-margin-top: 24px; min-width: 0; }
:focus-visible { outline: 3px solid var(--primary); outline-offset: 3px; }
.settings-page .grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}
.panel {
  border: 1px solid var(--line);
  border-radius: 23px;
  padding: 24px;
  background: var(--card);
}
.panel h4 {
  margin: 0 0 10px 0;
}
.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
@media (max-width: 1100px) {
  .settings-page .grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 760px) { .panel { padding: 18px; } .panel-header { flex-direction: column; } }
</style>
