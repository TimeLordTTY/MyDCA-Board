<template>
  <div class="settings-page">
    <div class="card">
      <h3>设置</h3>
      <div class="sub">个人信息、安全与家庭成员管理</div>
      <div class="divider"></div>

      <div class="grid">
        <!-- 个人信息 -->
        <div class="panel">
          <h4>个人信息</h4>
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
              <el-button :disabled="profileSaving" @click="resetProfile">重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <!-- 安全 -->
        <div class="panel">
          <h4>安全</h4>
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
        </div>
      </div>

      <!-- 家庭成员 -->
      <div class="panel" style="margin-top: 12px">
        <div class="panel-header">
          <div>
            <h4 style="margin-bottom: 4px">家庭成员</h4>
            <div class="sub">仅管理员可添加/移除/改角色</div>
          </div>
          <div class="actions">
            <el-button :disabled="!userStore.user?.familyId" @click="reloadMembers">刷新</el-button>
            <el-button type="primary" :disabled="!userStore.user?.familyId" @click="addDialogVisible = true">添加成员</el-button>
          </div>
        </div>

        <div v-if="!userStore.user?.familyId" class="td-muted" style="padding: 12px 0">
          当前账号未加入家庭。请先在其他入口创建家庭（或后续在此页补齐“创建家庭”）。
        </div>

        <el-table v-else :data="members" style="width: 100%">
          <el-table-column prop="userId" label="用户ID" width="90" />
          <el-table-column prop="username" label="用户名" width="160" />
          <el-table-column prop="nickname" label="昵称" />
          <el-table-column prop="role" label="角色" width="120">
            <template #default="{ row }">
              <el-select v-model="row.role" size="small" style="width: 100px" @change="(v:any)=>onChangeRole(row.userId, v)">
                <el-option label="管理员" value="ADMIN" />
                <el-option label="成员" value="MEMBER" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button type="danger" link @click="removeMember(row.userId)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessageBox, ElNotification, type FormInstance, type FormRules } from 'element-plus'
import { familyApi, type FamilyMember, userApi, useUserStore } from '@wealth-hub/shared'

const userStore = useUserStore()

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
  await profileFormRef.value?.validate()
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
  } finally {
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
  await pwdFormRef.value?.validate()
  pwdSaving.value = true
  try {
    await userApi.changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    pwdFormRef.value?.clearValidate()
    ElNotification.success({ title: '成功', message: '密码已修改' })
  } finally {
    pwdSaving.value = false
  }
}

// 家庭成员
const members = ref<FamilyMember[]>([])
const addDialogVisible = ref(false)
const addSaving = ref(false)
const addForm = reactive({
  username: '',
  role: 'MEMBER' as 'ADMIN' | 'MEMBER',
})

async function reloadMembers() {
  if (!userStore.user?.familyId) return
  members.value = await familyApi.getMembers()
}

async function submitAddMember() {
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
  } finally {
    addSaving.value = false
  }
}

async function removeMember(userId: number) {
  await ElMessageBox.confirm('确定要移除该成员吗？', '确认移除', { type: 'warning' })
  await familyApi.removeMember(userId)
  await reloadMembers()
  ElNotification.success({ title: '成功', message: '成员已移除' })
}

async function onChangeRole(userId: number, role: 'ADMIN' | 'MEMBER') {
  await familyApi.updateMemberRole(userId, role)
  ElNotification.success({ title: '成功', message: '角色已更新' })
  await reloadMembers()
}

onMounted(async () => {
  if (!userStore.user) {
    await userStore.fetchCurrentUser()
  }
  fillProfileFromStore()
  await reloadMembers()
})
</script>

<style scoped>
.settings-page .grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.panel {
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 14px;
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
}
@media (max-width: 1100px) {
  .settings-page .grid {
    grid-template-columns: 1fr;
  }
}
</style>
