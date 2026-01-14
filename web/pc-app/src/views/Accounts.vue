<template>
  <div class="accounts-page-container">
    <div class="accounts-layout">
      <!-- 两列布局 -->
      <div class="accounts-two-column">
        <!-- 第一列 -->
        <div class="accounts-column">
          <div class="card">
          <div class="row-between">
            <div>
              <h3>
                账户管理
                <span class="tag blue tiny">可编辑</span>
              </h3>
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
          <div v-else-if="!accountTree || accountTree.length === 0" class="td-muted" style="text-align: center; padding: 20px">
            暂无平台账户
          </div>
          <div v-else class="account-tree-container">
            <div v-for="platform in accountTree.slice(0, Math.ceil(accountTree.length / 2))" :key="platform.id" class="platform-item">
              <div class="platform-header" @click="togglePlatform(platform.id)">
                <span class="expand-icon">{{ expandedPlatforms.has(platform.id) ? '▼' : '▶' }}</span>
                <div style="flex: 1;">
                  <div style="font-weight: 700;">{{ platform.accountName }}</div>
                  <div class="tiny muted">
                    父账户（容器）余额 = Σ子账户余额：<span class="mono">{{ formatCurrency(calculateParentBalance(platform)) }}</span>；占用：<span class="mono">{{ formatCurrency(calculateParentReservedAmount(platform)) }}</span>
                  </div>
                </div>
                  <div class="row-gap">
                    <button 
                      v-if="platform.accountType === 'BROKER'" 
                      class="btn" 
                      @click.stop="handleManageFeeConfig(platform)"
                    >
                      费率配置
                    </button>
                    <button class="btn" @click.stop="handleRenamePlatform(platform)">改名</button>
                  </div>
                </div>
                <div v-if="expandedPlatforms.has(platform.id)" class="platform-children">
                <div
                  v-for="child in platform.children"
                  :key="child.id"
                  class="bucket-item"
                  :class="{ 'bucket-inactive': !child.isActive }"
                >
                  <div style="font-weight: 800">
                    {{ child.accountName }}
                    <span v-if="!child.isActive" class="tag red" style="margin-left: 8px;">已停用</span>
                    <span class="tag" :class="getFundUsageTagClass(child.fundUsage)">
                      {{ getFundUsageLabel(child.fundUsage) }}
                    </span>
                  </div>
                  <div class="tiny muted">
                    余额：<span class="mono">{{ formatCurrency(child.balance) }}</span>；占用：<span class="mono">{{ formatCurrency(child.reservedAmount) }}</span>
                  </div>
                  <div class="bucket-actions">
                    <button class="btn" @click="handleEditAccount(child)">编辑</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
          </div>
        </div>
        <!-- 第二列 -->
        <div class="accounts-column">
          <div class="card">
            <div class="row-between">
              <div>
                <h3>
                  账户管理
                  <span class="tag blue tiny">可编辑</span>
                </h3>
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
            <div v-else-if="!accountTree || accountTree.length === 0" class="td-muted" style="text-align: center; padding: 20px">
              暂无平台账户
            </div>
            <div v-else class="account-tree-container">
              <div
                v-for="platform in accountTree.slice(Math.ceil(accountTree.length / 2))"
                :key="platform.id"
                class="platform-item"
              >
                <div class="platform-header" @click="togglePlatform(platform.id)">
                  <span class="expand-icon">{{ expandedPlatforms.has(platform.id) ? '▼' : '▶' }}</span>
                  <div style="flex: 1;">
                    <div style="font-weight: 700;">{{ platform.accountName }}</div>
                    <div class="tiny muted">
                      父账户（容器）余额 = Σ子账户余额：<span class="mono">{{ formatCurrency(calculateParentBalance(platform)) }}</span>；占用：<span class="mono">{{ formatCurrency(calculateParentReservedAmount(platform)) }}</span>
                    </div>
                  </div>
                  <div class="row-gap">
                    <button 
                      v-if="platform.accountType === 'BROKER'" 
                      class="btn" 
                      @click.stop="handleManageFeeConfig(platform)"
                    >
                      费率配置
                    </button>
                    <button class="btn" @click.stop="handleRenamePlatform(platform)">改名</button>
                  </div>
                </div>
                <div v-if="expandedPlatforms.has(platform.id)" class="platform-children">
                  <div
                    v-for="child in platform.children"
                    :key="child.id"
                    class="bucket-item"
                    :class="{ 'bucket-inactive': !child.isActive }"
                  >
                    <div style="font-weight: 800">
                      {{ child.accountName }}
                      <span v-if="!child.isActive" class="tag red" style="margin-left: 8px;">已停用</span>
                      <span class="tag" :class="getFundUsageTagClass(child.fundUsage)">
                        {{ getFundUsageLabel(child.fundUsage) }}
                      </span>
                    </div>
                    <div class="tiny muted">
                      余额：<span class="mono">{{ formatCurrency(child.balance) }}</span>；占用：<span class="mono">{{ formatCurrency(child.reservedAmount) }}</span>
                    </div>
                    <div class="bucket-actions">
                      <button class="btn" @click="handleEditAccount(child)">编辑</button>
                    </div>
                  </div>
                </div>
            </div>
          </div>
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
        <el-form-item v-if="isPlatform" label="平台说明">
          <div class="form-help-text">
            平台（ROOT）：用于分组，如"华宝证券"、"支付宝"。父账户仅用于分组展示；真实余额只在叶子账户。
          </div>
        </el-form-item>
        <el-form-item v-if="isPlatform" label="账户类型" prop="accountType">
          <el-select v-model="accountForm.accountType" placeholder="选择账户类型" style="width: 100%">
            <el-option
              v-for="(label, value) in accountTypeMap"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
          <div class="form-help-text">账户类型 <span class="field-en">(account_type)</span>，该平台下的所有信封将使用此账户类型</div>
        </el-form-item>
        <el-form-item label="账户代码" prop="accountCode">
          <el-input v-model="accountForm.accountCode" placeholder="账户代码" />
          <div class="form-help-text">账户代码 <span class="field-en">(account_code)</span></div>
        </el-form-item>
        <el-form-item label="账户名称" prop="accountName">
          <el-input v-model="accountForm.accountName" placeholder="账户名称" />
          <div class="form-help-text">账户名称 <span class="field-en">(account_name)</span></div>
        </el-form-item>
        <el-form-item v-if="!isPlatform" label="父账户" prop="parentAccountId">
            <el-select 
              v-model="accountForm.parentAccountId" 
              placeholder="选择父账户" 
              style="width: 100%"
              @change="handleParentAccountChange"
            >
              <el-option
                v-for="platform in accountTree"
                :key="platform.id"
                :label="platform.accountName"
                :value="platform.id"
              />
            </el-select>
          <div class="form-help-text">父账户 <span class="field-en">(parent_account_id)</span>，选择后将自动继承父账户的账户类型</div>
        </el-form-item>
        <el-form-item v-if="!isPlatform" label="账户类型">
          <el-input 
            :value="getParentAccountType() || accountForm.accountType || '未设置'" 
            disabled 
            style="width: 100%"
          />
          <div class="form-help-text">账户类型 <span class="field-en">(account_type)</span>，继承自父账户（平台）</div>
        </el-form-item>
        <el-form-item 
          v-if="!isPlatform && (accountForm.accountType === 'CREDIT_CARD' || accountForm.accountType === 'HUABEI' || accountForm.accountType === 'BAITIAO' || accountForm.accountType === 'LOAN')" 
          label="账户子类型" 
          prop="accountSubtype"
        >
          <el-input 
            v-model="accountForm.accountSubtype" 
            :placeholder="getAccountSubtypePlaceholder(accountForm.accountType)" 
          />
          <div class="form-help-text">账户子类型 <span class="field-en">(account_subtype)</span>，用于信贷账户的进一步分类</div>
        </el-form-item>
        <el-form-item v-if="!isPlatform" label="货币" prop="currency">
          <el-select v-model="accountForm.currency" placeholder="选择货币" style="width: 100%">
            <el-option
              v-for="(label, value) in currencyMap"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
          <div class="form-help-text">货币 <span class="field-en">(currency)</span></div>
        </el-form-item>
        <el-form-item 
          v-if="!isPlatform && !isCreditAccount(accountForm.accountType)" 
          label="资金用途" 
          prop="fundUsage"
        >
          <el-select 
            v-model="accountForm.fundUsage" 
            placeholder="选择资金用途" 
            style="width: 100%"
          >
            <el-option
              v-for="(label, value) in fundUsageMap"
              :key="value"
              :value="value"
              :label="label"
            >
              <template #default>
                <div>
                  <span>{{ label }}</span>
                  <span class="field-en">({{ value }})</span>
                  <span class="fund-usage-desc">
                    {{ getFundUsageDescription(value) }}
                  </span>
                </div>
              </template>
            </el-option>
          </el-select>
          <div class="form-help-text">
            <span class="field-en">fund_usage: </span>
            SPENDABLE：可支出，用于日常消费；RESERVED：专款，用于特定用途（如逆回购占用）；INVESTABLE：可投资，用于购买理财产品
          </div>
        </el-form-item>
        <el-form-item v-if="!isPlatform" label="初始余额">
          <el-input-number v-model="accountForm.initialBalance" :precision="2" style="width: 100%" />
          <div class="form-help-text">初始余额 <span class="field-en">(initial_balance)</span></div>
        </el-form-item>
        <el-form-item v-if="!isPlatform && accountForm.id" label="状态">
          <el-switch
            v-model="accountForm.isActive"
            active-text="启用"
            inactive-text="停用"
            :active-color="accountForm.isActive ? '#67c23a' : '#f56c6c'"
            :inactive-color="accountForm.isActive ? '#67c23a' : '#f56c6c'"
          />
          <div v-if="!accountForm.isActive" class="form-warning-text">
            ⚠️ 停用后，该信封将无法在记账时选择，但不会删除已有的记账数据。
          </div>
          <div class="form-help-text">是否启用 <span class="field-en">(is_active)</span></div>
        </el-form-item>
        <el-form-item v-if="!isPlatform" label="信封说明">
          <div class="form-help-text">
            信封（LEAF）：实际存储余额的账户，如"生活费"、"理财金"。每个平台默认有"待分配"。
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="accountForm.note" type="textarea" :rows="3" placeholder="备注信息" />
          <div class="form-help-text">备注 <span class="field-en">(note)</span></div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="accountDialogVisible = false">取消</el-button>
        <el-button 
          v-if="!isPlatform && accountForm.id && !accountForm.isActive"
          type="danger"
          :loading="accountSaving" 
          @click="handleAccountSave"
        >
          确认停用
        </el-button>
        <el-button 
          v-else
          type="primary" 
          :loading="accountSaving" 
          @click="handleAccountSave"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 券商费率配置对话框 -->
    <el-dialog v-model="feeConfigDialogVisible" title="券商费率配置" width="900px" @close="handleFeeConfigDialogClose">
      <div v-if="currentBrokerAccount">
        <div style="margin-bottom: 16px; padding: 12px; background: #f5f7fa; border-radius: 4px;">
          <div style="font-weight: 600; margin-bottom: 4px;">账户：{{ currentBrokerAccount.accountName }}</div>
          <div style="font-size: 12px; color: #666;">账户代码：{{ currentBrokerAccount.accountCode }}</div>
        </div>
        
        <div style="margin-bottom: 12px;">
          <el-button type="primary" size="small" @click="handleAddFeeConfig">+ 添加费率配置</el-button>
        </div>
        
        <el-table :data="feeConfigs" border size="small" style="width: 100%">
          <el-table-column prop="feeRuleType" label="费率规则类型" width="150">
            <template #default="{ row }">
              {{ getFeeRuleTypeLabel(row.feeRuleType) }}
            </template>
          </el-table-column>
          <el-table-column prop="buyFeeRatePercent" label="买入费率" width="120">
            <template #default="{ row }">
              {{ row.buyFeeRatePercent?.toFixed(6) || '0.000000' }}%
            </template>
          </el-table-column>
          <el-table-column prop="sellFeeRatePercent" label="卖出费率" width="120">
            <template #default="{ row }">
              {{ row.sellFeeRatePercent?.toFixed(6) || '0.000000' }}%
            </template>
          </el-table-column>
          <el-table-column prop="buyMinFee" label="买入最低手续费" width="130">
            <template #default="{ row }">
              ¥{{ row.buyMinFee?.toFixed(2) || '0.00' }}
            </template>
          </el-table-column>
          <el-table-column prop="sellMinFee" label="卖出最低手续费" width="130">
            <template #default="{ row }">
              ¥{{ row.sellMinFee?.toFixed(2) || '0.00' }}
            </template>
          </el-table-column>
          <el-table-column prop="subscriptionDiscountRate" label="申购折扣率" width="120">
            <template #default="{ row }">
              <span v-if="row.subscriptionDiscountRate != null">
                {{ (row.subscriptionDiscountRate * 100).toFixed(1) }}折
              </span>
              <span v-else style="color: #999;">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="isActive" label="启用" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isActive ? 'success' : 'info'" size="small">
                {{ row.isActive ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row, $index }">
              <el-button type="primary" size="small" link @click="handleEditFeeConfig(row, $index)">编辑</el-button>
              <el-button type="danger" size="small" link @click="handleDeleteFeeConfig(row.id, $index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <!-- 费率配置编辑对话框 -->
    <el-dialog 
      v-model="feeConfigEditDialogVisible" 
      :title="editingFeeConfigIndex >= 0 ? '编辑费率配置' : '添加费率配置'" 
      width="600px"
      @close="handleFeeConfigEditDialogClose"
    >
      <el-form :model="feeConfigForm" label-width="140px" ref="feeConfigFormRef">
        <el-form-item label="费率规则类型" prop="feeRuleType" required>
          <el-select v-model="feeConfigForm.feeRuleType" placeholder="选择费率规则类型" style="width: 100%">
            <el-option
              v-for="(label, value) in feeRuleTypeMap"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
        </el-form-item>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="买入费率" prop="buyFeeRatePercent" required>
              <el-input-number
                v-model="buyFeeRatePercent"
                :precision="6"
                :step="0.0001"
                :min="0"
                :max="100"
                style="width: 100%"
              >
                <template #suffix>%</template>
              </el-input-number>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="卖出费率" prop="sellFeeRatePercent" required>
              <el-input-number
                v-model="sellFeeRatePercent"
                :precision="6"
                :step="0.0001"
                :min="0"
                :max="100"
                style="width: 100%"
              >
                <template #suffix>%</template>
              </el-input-number>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="买入最低手续费" prop="buyMinFee" required>
              <el-input-number
                v-model="feeConfigForm.buyMinFee"
                :precision="2"
                :step="0.01"
                :min="0"
                style="width: 100%"
              >
                <template #prefix>¥</template>
              </el-input-number>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="卖出最低手续费" prop="sellMinFee" required>
              <el-input-number
                v-model="feeConfigForm.sellMinFee"
                :precision="2"
                :step="0.01"
                :min="0"
                style="width: 100%"
              >
                <template #prefix>¥</template>
              </el-input-number>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-form-item 
          v-if="feeConfigForm.feeRuleType === 'LOF_SUBSCRIPTION'" 
          label="申购折扣率" 
          prop="subscriptionDiscountRate"
        >
          <el-input-number
            v-model="subscriptionDiscountRatePercent"
            :precision="1"
            :step="0.1"
            :min="0"
            :max="100"
            style="width: 100%"
          >
            <template #suffix>折</template>
          </el-input-number>
          <div class="sub">如：10表示一折（0.1），50表示五折（0.5）</div>
        </el-form-item>
        
        <el-form-item label="是否启用">
          <el-switch v-model="feeConfigForm.isActive" />
        </el-form-item>
        
        <el-form-item label="备注">
          <el-input v-model="feeConfigForm.note" type="textarea" :rows="2" placeholder="备注信息" />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="feeConfigEditDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="feeConfigSaving" @click="handleSaveFeeConfig">保存</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  useAccountStore,
  formatCurrency,
  fundUsageMap,
  getFundUsageLabel,
  calculateParentBalance,
  calculateParentReservedAmount,
  accountTypeMap,
  currencyMap,
} from '@wealth-hub/shared'
import { brokerFeeApi, type BrokerFeeConfig } from '@wealth-hub/shared'
import type { Account } from '@wealth-hub/shared'

const accountStore = useAccountStore()

// 使用本地ref来存储数据，确保响应式
const accountTree = ref<Account[]>([])

// 树状结构展开状态
const expandedPlatforms = ref<Set<number>>(new Set())
const lastEditedAccountId = ref<number | null>(null)

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
  accountSubtype: undefined,
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

// 费率规则类型映射
const feeRuleTypeMap: Record<string, string> = {
  STOCK: 'A股',
  ETF: 'ETF',
  LOF: 'LOF场内交易',
  LOF_SUBSCRIPTION: 'LOF场内申购',
  CONVERTIBLE_BOND_SH: '上海可转债',
  CONVERTIBLE_BOND_SZ: '深圳可转债',
  BOND_REPO: '逆回购',
  FUND_OTC: '场外基金',
  DEFAULT: '默认规则',
}

function getFeeRuleTypeLabel(feeRuleType: string): string {
  return feeRuleTypeMap[feeRuleType] || feeRuleType
}

// 券商费率配置相关
const feeConfigDialogVisible = ref(false)
const feeConfigEditDialogVisible = ref(false)
const currentBrokerAccount = ref<Account | null>(null)
const feeConfigs = ref<(BrokerFeeConfig & { buyFeeRatePercent?: number; sellFeeRatePercent?: number })[]>([])
const editingFeeConfigIndex = ref(-1)
const feeConfigSaving = ref(false)
const feeConfigFormRef = ref<FormInstance>()
const feeConfigForm = reactive<Partial<BrokerFeeConfig> & { buyFeeRatePercent?: number; sellFeeRatePercent?: number }>({
  feeRuleType: 'STOCK',
  buyFeeRate: 0,
  sellFeeRate: 0,
  buyMinFee: 0,
  sellMinFee: 0,
  subscriptionDiscountRate: undefined,
  isActive: true,
  note: '',
})

// 费率百分比显示（用于前端输入）
const buyFeeRatePercent = computed({
  get: () => feeConfigForm.buyFeeRate != null ? feeConfigForm.buyFeeRate * 100 : 0,
  set: (val) => { feeConfigForm.buyFeeRate = val != null ? val / 100 : 0 }
})

const sellFeeRatePercent = computed({
  get: () => feeConfigForm.sellFeeRate != null ? feeConfigForm.sellFeeRate * 100 : 0,
  set: (val) => { feeConfigForm.sellFeeRate = val != null ? val / 100 : 0 }
})

const subscriptionDiscountRatePercent = computed({
  get: () => feeConfigForm.subscriptionDiscountRate != null ? feeConfigForm.subscriptionDiscountRate * 100 : 0,
  set: (val) => { feeConfigForm.subscriptionDiscountRate = val != null ? val / 100 : 0 }
})

function getFundUsageDescription(value: string): string {
  const descriptions: Record<string, string> = {
    SPENDABLE: '用于日常消费',
    RESERVED: '用于特定用途（如逆回购占用）',
    INVESTABLE: '用于购买理财产品',
  }
  return descriptions[value] || ''
}

function togglePlatform(platformId: number) {
  if (expandedPlatforms.value.has(platformId)) {
    expandedPlatforms.value.delete(platformId)
  } else {
    expandedPlatforms.value.add(platformId)
  }
}

function expandPlatformForAccount(accountId: number) {
  // 找到包含该账户的平台
  const platform = accountTree.value.find((p) =>
    p.children?.some((c) => c.id === accountId) || p.id === accountId
  )
  if (platform) {
    expandedPlatforms.value.add(platform.id)
  }
}


function getFundUsageTagClass(fundUsage?: string): string {
  if (fundUsage === 'SPENDABLE') return 'green'
  if (fundUsage === 'RESERVED') return 'orange'
  if (fundUsage === 'INVESTABLE') return 'blue'
  return 'gray'
}

function getAccountSubtypePlaceholder(accountType?: string): string {
  const placeholders: Record<string, string> = {
    CREDIT_CARD: '例如：主卡、副卡、金卡、白金卡等',
    HUABEI: '例如：花呗分期、花呗信用购、花呗当面付等',
    BAITIAO: '例如：白条分期、白条信用购等',
    LOAN: '例如：房贷、车贷、消费贷、经营贷等',
  }
  return placeholders[accountType || ''] || '账户子类型（可选）'
}

function isCreditAccount(accountType?: string): boolean {
  return accountType === 'CREDIT_CARD' || 
         accountType === 'HUABEI' || 
         accountType === 'BAITIAO' || 
         accountType === 'LOAN'
}

function getParentAccountType(): string | undefined {
  if (!accountForm.parentAccountId) return undefined
  const parentAccount = accountTree.value.find(p => p.id === accountForm.parentAccountId)
  return parentAccount?.accountType
}

function handleParentAccountChange() {
  // 当父账户改变时，自动继承父账户的账户类型
  if (accountForm.parentAccountId) {
    const parentAccount = accountTree.value.find(p => p.id === accountForm.parentAccountId)
    if (parentAccount) {
      accountForm.accountType = parentAccount.accountType
    }
  }
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
  accountForm.parentAccountId = accountTree.value[0]?.id
  // 自动继承父账户的账户类型
  if (accountForm.parentAccountId) {
    const parentAccount = accountTree.value.find(p => p.id === accountForm.parentAccountId)
    if (parentAccount) {
      accountForm.accountType = parentAccount.accountType
    }
  }
  accountDialogVisible.value = true
}

function handleEditAccount(account: Account) {
  if (account.parentAccountId) {
    // 子账户：完整编辑功能
    isPlatform.value = false
    accountDialogTitle.value = '编辑信封'
    Object.assign(accountForm, account)
    accountDialogVisible.value = true
    // 展开包含该账户的平台
    expandPlatformForAccount(account.id)
    lastEditedAccountId.value = account.id
  } else {
    // 父账户：只能改名
    handleRenamePlatform(account)
  }
}

function handleRenamePlatform(platform: Account) {
  isPlatform.value = true
  accountDialogTitle.value = platform.id ? '编辑平台' : '新增平台'
  Object.assign(accountForm, platform)
  accountDialogVisible.value = true
  // 展开该平台
  if (platform.id) {
    expandedPlatforms.value.add(platform.id)
    lastEditedAccountId.value = platform.id
  }
}

function resetAccountForm() {
  Object.assign(accountForm, {
    id: undefined,
    accountCode: '',
    accountName: '',
    accountKind: 'REAL',
    accountType: 'CASH',
    accountSubtype: undefined,
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

    // 如果是停用操作，显示确认提示
    if (accountForm.id && !accountForm.isActive) {
      const account = accountStore.accounts.find(a => a.id === accountForm.id)
      if (account?.isActive) {
        // 从启用变为停用，需要确认
        try {
          await ElMessageBox.confirm(
            '停用后，该信封将无法在记账时选择，但不会删除已有的记账数据。确定要停用吗？',
            '确认停用',
            {
              confirmButtonText: '确定停用',
              cancelButtonText: '取消',
              type: 'warning',
              confirmButtonClass: 'el-button--danger',
            }
          )
        } catch {
          // 用户取消
          return
        }
      }
    }

    accountSaving.value = true
    try {
      let savedAccountId: number | null = null
      if (accountForm.id) {
        await accountStore.updateAccount(accountForm.id, accountForm)
        ElMessage.success(accountForm.isActive ? '更新成功' : '已停用')
        savedAccountId = accountForm.id
      } else {
        const newAccount = await accountStore.createAccount(accountForm)
        ElMessage.success('创建成功')
        savedAccountId = newAccount.id
      }
      
      // 等待数据刷新完成
      await loadAccounts()
      
      // 展开包含新创建/编辑账户的平台
      if (savedAccountId) {
        expandPlatformForAccount(savedAccountId)
        lastEditedAccountId.value = savedAccountId
      }
      
      accountDialogVisible.value = false
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
      if (!currentAccount.value) return
      const accountId = currentAccount.value.id
      await accountStore.adjustBalance(accountId, balanceForm.balance, balanceForm.note)
      ElMessage.success('余额调整成功')
      balanceDialogVisible.value = false
      // 等待数据刷新完成
      await loadAccounts()
      // 展开包含该账户的平台
      expandPlatformForAccount(accountId)
      lastEditedAccountId.value = accountId
    } catch (error: any) {
      ElMessage.error(error.message || '调整失败')
    } finally {
      balanceSaving.value = false
    }
  })
}

// 加载账户数据
async function loadAccounts() {
  try {
    await accountStore.fetchAccounts()
    // 从store中获取数据并更新本地ref
    accountTree.value = accountStore.accountTree || []
    console.log('Accounts loaded:', accountStore.accounts.length, 'accounts')
    console.log('Account tree:', accountTree.value.length, 'platforms')
  } catch (error) {
    console.error('Failed to load accounts:', error)
    accountTree.value = []
  }
}

// 费率配置管理函数
async function handleManageFeeConfig(account: Account) {
  if (account.accountType !== 'BROKER') {
    ElMessage.warning('只有券商账户才能配置费率')
    return
  }
  
  currentBrokerAccount.value = account
  feeConfigDialogVisible.value = true
  await loadFeeConfigs(account.id)
}

async function loadFeeConfigs(accountId: number) {
  try {
    const configs = await brokerFeeApi.getFeeConfigs(accountId)
    feeConfigs.value = configs.map(config => ({
      ...config,
      buyFeeRatePercent: (config.buyFeeRate || 0) * 100,
      sellFeeRatePercent: (config.sellFeeRate || 0) * 100,
    }))
  } catch (error: any) {
    console.error('加载费率配置失败:', error)
    ElMessage.error('加载费率配置失败')
    feeConfigs.value = []
  }
}

function handleFeeConfigDialogClose() {
  currentBrokerAccount.value = null
  feeConfigs.value = []
}

function handleAddFeeConfig() {
  editingFeeConfigIndex.value = -1
  resetFeeConfigForm()
  feeConfigEditDialogVisible.value = true
}

function handleEditFeeConfig(config: BrokerFeeConfig & { buyFeeRatePercent?: number; sellFeeRatePercent?: number }, index: number) {
  editingFeeConfigIndex.value = index
  Object.assign(feeConfigForm, {
    ...config,
    buyFeeRate: config.buyFeeRate || 0,
    sellFeeRate: config.sellFeeRate || 0,
    subscriptionDiscountRate: config.subscriptionDiscountRate,
  })
  feeConfigEditDialogVisible.value = true
}

async function handleDeleteFeeConfig(id: number | undefined, index: number) {
  if (!id || !currentBrokerAccount.value) return
  
  try {
    await ElMessageBox.confirm('确定要删除这条费率配置吗？', '确认删除', {
      type: 'warning',
    })
    
    await brokerFeeApi.deleteFeeConfig(currentBrokerAccount.value.id, id)
    ElMessage.success('删除成功')
    await loadFeeConfigs(currentBrokerAccount.value.id)
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除费率配置失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

function resetFeeConfigForm() {
  Object.assign(feeConfigForm, {
    feeRuleType: 'STOCK',
    buyFeeRate: 0,
    sellFeeRate: 0,
    buyMinFee: 0,
    sellMinFee: 0,
    subscriptionDiscountRate: undefined,
    isActive: true,
    note: '',
  })
}

function handleFeeConfigEditDialogClose() {
  editingFeeConfigIndex.value = -1
  resetFeeConfigForm()
  feeConfigFormRef.value?.resetFields()
}

async function handleSaveFeeConfig() {
  if (!feeConfigFormRef.value || !currentBrokerAccount.value) return
  
  feeConfigSaving.value = true
  try {
    const configToSave: Partial<BrokerFeeConfig> = {
      feeRuleType: feeConfigForm.feeRuleType,
      buyFeeRate: feeConfigForm.buyFeeRate,
      sellFeeRate: feeConfigForm.sellFeeRate,
      buyMinFee: feeConfigForm.buyMinFee,
      sellMinFee: feeConfigForm.sellMinFee,
      subscriptionDiscountRate: feeConfigForm.subscriptionDiscountRate,
      isActive: feeConfigForm.isActive,
      note: feeConfigForm.note,
    }
    
    if (editingFeeConfigIndex.value >= 0 && feeConfigs.value[editingFeeConfigIndex.value]?.id) {
      // 更新
      await brokerFeeApi.updateFeeConfig(
        currentBrokerAccount.value.id,
        feeConfigs.value[editingFeeConfigIndex.value].id!,
        configToSave
      )
      ElMessage.success('更新成功')
    } else {
      // 创建
      await brokerFeeApi.createFeeConfig(currentBrokerAccount.value.id, configToSave)
      ElMessage.success('创建成功')
    }
    
    feeConfigEditDialogVisible.value = false
    await loadFeeConfigs(currentBrokerAccount.value.id)
  } catch (error: any) {
    console.error('保存费率配置失败:', error)
    ElMessage.error(error.message || '保存失败')
  } finally {
    feeConfigSaving.value = false
  }
}

onMounted(async () => {
  await loadAccounts()
})
</script>

<style scoped>
.accounts-page-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.accounts-layout {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.accounts-two-column {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.accounts-column {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.accounts-column .card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.account-tree-container {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  min-height: 0;
}

.account-tree-container::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
}

.account-tree-container {
  -ms-overflow-style: none;
  scrollbar-width: none;
}

.platform-item {
  margin-bottom: 12px;
  border: 1px solid rgba(230, 238, 247, 0.9);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
}

.platform-header {
  display: flex;
  align-items: flex-start;
  padding: 12px;
  cursor: pointer;
  gap: 8px;
  word-break: break-word;
}

.platform-header:hover {
  background: rgba(0, 0, 0, 0.02);
}

.expand-icon {
  font-size: 12px;
  color: #666;
  margin-top: 4px;
  flex-shrink: 0;
}

.platform-children {
  padding: 0 12px 12px 40px;
}

.bucket-item {
  padding: 10px 8px;
  border-radius: 8px;
  border: 1px solid rgba(230, 238, 247, 0.6);
  background: rgba(255, 255, 255, 0.8);
  margin-bottom: 8px;
  position: relative;
  word-break: break-word;
}

.bucket-item.bucket-inactive {
  opacity: 0.6;
  background: rgba(245, 108, 108, 0.05);
  border-color: rgba(245, 108, 108, 0.3);
}

.bucket-actions {
  position: absolute;
  top: 10px;
  right: 8px;
}


.field-en {
  color: #999;
  font-size: 11px;
  font-weight: normal;
  margin-left: 4px;
  white-space: nowrap;
  display: inline;
}

th .field-en,
h3 .field-en {
  margin-left: 4px;
}

.form-help-text {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
  line-height: 1.4;
}

.form-warning-text {
  font-size: 12px;
  color: #f56c6c;
  margin-top: 4px;
  line-height: 1.4;
  font-weight: 500;
  padding: 8px 12px;
  background: rgba(245, 108, 108, 0.1);
  border: 1px solid rgba(245, 108, 108, 0.3);
  border-radius: 6px;
}

.fund-usage-desc {
  color: #999;
  font-size: 11px;
  margin-left: 8px;
}

.tag .field-en {
  margin-left: 4px;
}
</style>
