<template>
  <div>
    <div class="two">
      <div class="card">
        <div class="row-between">
          <div>
            <h3>
              账户管理
              <span class="tag blue tiny">可编辑</span>
            </h3>
            <div class="sub">父账户仅用于分组展示；真实余额只在叶子账户。每个平台默认有"待分配"。</div>
          </div>
          <div class="row-gap">
            <el-button @click="handleAddPlatform">＋ 新增平台</el-button>
            <el-button @click="handleAddBucket">＋ 新增信封</el-button>
          </div>
        </div>
        <div class="divider"></div>

        <!-- 账户树 -->
        <div v-if="accountStore.loading" class="td-muted" style="text-align: center; padding: 20px">
          加载中...
        </div>
        <div v-else>
          <div v-for="platform in accountStore.accountTree" :key="platform.id" class="card" style="margin-bottom: 12px; padding: 12px">
            <div class="row-between">
              <div>
                <div style="font-weight: 900">{{ platform.accountName }}</div>
                <div class="tiny muted">
                  父账户（容器）余额 = Σ子账户余额：<span class="mono">{{ formatCurrency(calculateParentBalance(platform)) }}</span>；占用：<span class="mono">{{ formatCurrency(calculateParentReservedAmount(platform)) }}</span>
                </div>
              </div>
              <div class="row-gap">
                <button class="btn" @click="handleRenamePlatform(platform)">改名</button>
              </div>
            </div>
            <div class="divider"></div>
            <div v-if="platform.children && platform.children.length > 0">
              <div
                v-for="child in platform.children"
                :key="child.id"
                class="row-between"
                style="padding: 10px 8px; border-radius: 14px; border: 1px solid rgba(230,238,247,.9); background: rgba(255,255,255,.92); margin-bottom: 10px"
              >
                <div>
                  <div style="font-weight: 800">
                    {{ child.accountName }}
                    <span class="tag" :class="getFundUsageTagClass(child.fundUsage)">
                      {{ getFundUsageLabel(child.fundUsage) }}
                    </span>
                  </div>
                  <div class="tiny muted">
                    余额：<span class="mono">{{ formatCurrency(child.balance) }}</span>；占用：<span class="mono">{{ formatCurrency(child.reservedAmount) }}</span>
                  </div>
                </div>
                <div class="row-gap">
                  <button class="btn" @click="handleEditAccount(child)">编辑</button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="divider"></div>

        <h3>现金叶子账户（摘要）</h3>
        <div style="margin-top: 10px; overflow: auto">
          <table>
            <thead>
              <tr>
                <th>平台</th>
                <th>信封</th>
                <th>用途</th>
                <th class="right">余额</th>
                <th class="right">占用</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="accountStore.cashLeafAccounts.length === 0">
                <td colspan="5" class="td-muted">暂无现金账户</td>
              </tr>
              <tr v-for="acc in accountStore.cashLeafAccounts" :key="acc.id">
                <td><b>{{ getPlatformName(acc) }}</b></td>
                <td>{{ acc.accountName }}</td>
                <td>
                  <span class="tag" :class="getFundUsageTagClass(acc.fundUsage)">
                    {{ getFundUsageLabel(acc.fundUsage) }}
                  </span>
                </td>
                <td class="right mono">{{ formatCurrency(acc.balance) }}</td>
                <td class="right mono">{{ formatCurrency(acc.reservedAmount) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="card">
        <h3>账户说明</h3>
        <div class="sub">
          <b>账户结构：</b><br />
          • 平台（ROOT）：用于分组，如"华宝证券"、"支付宝"<br />
          • 信封（LEAF）：实际存储余额的账户，如"生活费"、"理财金"<br />
          <div class="spacer"></div>
          <b>资金用途（fund_usage）：</b><br />
          • SPENDABLE：可支出，用于日常消费<br />
          • RESERVED：专款，用于特定用途（如逆回购占用）<br />
          • INVESTABLE：可投资，用于购买理财产品<br />
        </div>
      </div>
    </div>

    <!-- 账户编辑对话框 -->
    <el-dialog
      v-model="accountDialogVisible"
      :title="accountDialogTitle"
      width="600px"
      @close="handleAccountDialogClose"
    >
      <el-form :model="accountForm" :rules="accountRules" ref="accountFormRef" label-width="120px">
        <el-form-item label="账户代码" prop="accountCode">
          <el-input v-model="accountForm.accountCode" placeholder="账户代码" />
        </el-form-item>
        <el-form-item label="账户名称" prop="accountName">
          <el-input v-model="accountForm.accountName" placeholder="账户名称" />
        </el-form-item>
        <el-form-item v-if="!isPlatform" label="父账户" prop="parentAccountId">
          <el-select v-model="accountForm.parentAccountId" placeholder="选择父账户" style="width: 100%">
            <el-option
              v-for="platform in accountStore.accountTree"
              :key="platform.id"
              :label="platform.accountName"
              :value="platform.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isPlatform" label="资金用途" prop="fundUsage">
          <el-select v-model="accountForm.fundUsage" placeholder="选择资金用途" style="width: 100%">
            <el-option
              v-for="(label, value) in fundUsageMap"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isPlatform" label="初始余额">
          <el-input-number v-model="accountForm.initialBalance" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="accountForm.note" type="textarea" :rows="3" placeholder="备注信息" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="accountDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="accountSaving" @click="handleAccountSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 余额调整对话框 -->
    <el-dialog v-model="balanceDialogVisible" title="调整余额" width="500px">
      <el-form :model="balanceForm" :rules="balanceRules" ref="balanceFormRef" label-width="120px">
        <el-form-item label="当前余额">
          <el-input :value="formatCurrency(currentAccount?.balance)" disabled />
        </el-form-item>
        <el-form-item label="新余额" prop="balance">
          <el-input-number v-model="balanceForm.balance" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="balanceForm.note" type="textarea" :rows="3" placeholder="调整原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="balanceDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="balanceSaving" @click="handleBalanceSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  useAccountStore,
  formatCurrency,
  fundUsageMap,
  getFundUsageLabel,
  calculateParentBalance,
  calculateParentReservedAmount,
} from '@wealth-hub/shared'
import type { Account } from '@wealth-hub/shared'

const accountStore = useAccountStore()

const accountDialogVisible = ref(false)
const accountDialogTitle = ref('')
const isPlatform = ref(false)
const accountSaving = ref(false)
const accountFormRef = ref<FormInstance>()
const accountForm = reactive<Partial<Account>>({
  accountCode: '',
  accountName: '',
  accountKind: 'REAL',
  accountType: 'CASH',
  ownerType: 'PERSONAL',
  parentAccountId: undefined,
  fundUsage: 'SPENDABLE',
  initialBalance: 0,
  currency: 'CNY',
  isActive: true,
  note: '',
})

const accountRules: FormRules = {
  accountCode: [{ required: true, message: '请输入账户代码', trigger: 'blur' }],
  accountName: [{ required: true, message: '请输入账户名称', trigger: 'blur' }],
}

const balanceDialogVisible = ref(false)
const currentAccount = ref<Account | null>(null)
const balanceSaving = ref(false)
const balanceFormRef = ref<FormInstance>()
const balanceForm = reactive({
  balance: 0,
  note: '',
})

const balanceRules: FormRules = {
  balance: [{ required: true, message: '请输入新余额', trigger: 'blur' }],
}

function getPlatformName(account: Account): string {
  // 如果账户有parentAccountId，说明它是子账户，需要查找父账户
  if (account.parentAccountId) {
    // 从所有账户中查找父账户（因为accountTree可能还没有构建好children）
    const parent = accountStore.accounts.find((a) => a.id === account.parentAccountId)
    if (parent) {
      return parent.accountName
    }
  }
  
  // 如果没有parentAccountId，尝试从accountTree中查找（可能是通过children关系）
  const platform = accountStore.accountTree.find((p) =>
    p.children?.some((c) => c.id === account.id)
  )
  return platform?.accountName || '未知'
}

function getFundUsageTagClass(fundUsage?: string): string {
  if (fundUsage === 'SPENDABLE') return 'green'
  if (fundUsage === 'RESERVED') return 'orange'
  if (fundUsage === 'INVESTABLE') return 'blue'
  return 'gray'
}

function handleAddPlatform() {
  isPlatform.value = true
  accountDialogTitle.value = '新增平台'
  resetAccountForm()
  accountForm.accountType = 'CASH'
  accountDialogVisible.value = true
}

function handleAddBucket() {
  isPlatform.value = false
  accountDialogTitle.value = '新增信封'
  resetAccountForm()
  accountForm.parentAccountId = accountStore.accountTree[0]?.id
  accountDialogVisible.value = true
}

function handleEditAccount(account: Account) {
  if (account.parentAccountId) {
    // 子账户：可以编辑余额和用途
    currentAccount.value = account
    balanceForm.balance = account.balance || 0
    balanceDialogVisible.value = true
  } else {
    // 父账户：只能改名
    handleRenamePlatform(account)
  }
}

function handleRenamePlatform(platform: Account) {
  isPlatform.value = true
  accountDialogTitle.value = '重命名平台'
  Object.assign(accountForm, platform)
  accountDialogVisible.value = true
}

function resetAccountForm() {
  Object.assign(accountForm, {
    accountCode: '',
    accountName: '',
    accountKind: 'REAL',
    accountType: 'CASH',
    ownerType: 'PERSONAL',
    parentAccountId: undefined,
    fundUsage: 'SPENDABLE',
    initialBalance: 0,
    currency: 'CNY',
    isActive: true,
    note: '',
  })
}

function handleAccountDialogClose() {
  resetAccountForm()
  accountFormRef.value?.resetFields()
}

async function handleAccountSave() {
  if (!accountFormRef.value) return

  await accountFormRef.value.validate(async (valid) => {
    if (!valid) return

    accountSaving.value = true
    try {
      if (accountForm.id) {
        await accountStore.updateAccount(accountForm.id, accountForm)
        ElMessage.success('更新成功')
      } else {
        await accountStore.createAccount(accountForm)
        ElMessage.success('创建成功')
      }
      accountDialogVisible.value = false
      await accountStore.fetchAccounts()
    } catch (error: any) {
      ElMessage.error(error.message || '保存失败')
    } finally {
      accountSaving.value = false
    }
  })
}

async function handleBalanceSave() {
  if (!balanceFormRef.value || !currentAccount.value) return

  await balanceFormRef.value.validate(async (valid) => {
    if (!valid) return

    balanceSaving.value = true
    try {
      await accountStore.adjustBalance(currentAccount.value.id, balanceForm.balance, balanceForm.note)
      ElMessage.success('余额调整成功')
      balanceDialogVisible.value = false
      await accountStore.fetchAccounts()
    } catch (error: any) {
      ElMessage.error(error.message || '调整失败')
    } finally {
      balanceSaving.value = false
    }
  })
}

onMounted(() => {
  accountStore.fetchAccounts()
})
</script>
