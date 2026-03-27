<template>
  <el-dialog
    v-model="visible"
    :title="props.editingTxn ? '编辑流水' : '记一笔'"
    width="800px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <!-- 第一步：选择业务类型 -->
    <div v-if="step === 1">
      <div class="sub" style="margin-bottom: 16px">第一步：选择业务类型</div>
      <div class="divider"></div>
      <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-top: 16px">
        <button
          v-for="(label, type) in txnTypeOptions"
          :key="type"
          class="btn"
          style="flex-direction: column; padding: 16px; text-align: center"
          @click="selectType(type)"
        >
          <div style="font-size: 20px; margin-bottom: 4px">{{ label.icon }}</div>
          <div style="font-weight: 600">{{ label.name }}</div>
          <div class="tiny muted" style="margin-top: 4px">{{ type }}</div>
        </button>
      </div>
    </div>

    <!-- 第二步：填写详情 -->
    <div v-else>
      <div class="sub" style="margin-bottom: 16px">第二步：填写{{ txnTypeOptions[selectedType]?.name }}详情</div>
      <div class="divider"></div>
      <el-form :model="form" label-width="120px">
        <!-- 支出/收入 -->
        <template v-if="selectedType === 'EXPENSE' || selectedType === 'INCOME'">
          <el-form-item label="发生时间" required>
            <el-date-picker
              v-model="form.occurredAt"
              type="datetime"
              placeholder="选择发生时间"
              style="width: 100%"
              size="small"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </el-form-item>
          <el-form-item label="分类" required>
            <el-cascader
              v-model="form.category"
              :options="categoryOptions"
              :props="cascaderProps"
              placeholder="选择分类"
              style="width: 100%"
              clearable
            />
          </el-form-item>
          <el-form-item label="金额（元）" required>
            <el-input-number v-model="form.amount" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
          
          <!-- 组合支付开关（仅支出时显示） -->
          <el-form-item v-if="selectedType === 'EXPENSE'" label="组合支付">
            <el-switch v-model="useComboPayment" @change="handleComboPaymentChange" />
            <span style="margin-left: 8px; color: #909399; font-size: 12px">从多个账户付款</span>
          </el-form-item>

          <!-- 单账户选择（非组合支付） -->
          <el-form-item v-if="!useComboPayment" label="账户" required>
            <div style="display: flex; align-items: center; gap: 12px;">
              <el-cascader
                v-model="selectedAccount"
                :options="accountCascaderOptions"
                :props="accountCascaderProps"
                placeholder="选择账户"
                style="flex: 1; min-width: 280px;"
                clearable
              >
                <template #default="{ node, data }">
                  <span>{{ data.label }}</span>
                  <span v-if="data.balanceText" :style="{ color: data.isCredit ? '#ef4444' : '#4ea4ff', fontSize: '12px', marginLeft: '12px' }">
                    {{ data.balanceText }}
                  </span>
                </template>
              </el-cascader>
              <span v-if="form.accountId" :style="{ color: getSelectedAccountBalance(form.parentAccountId, form.accountId).isCredit ? '#ef4444' : '#4ea4ff', fontSize: '13px', whiteSpace: 'nowrap' }">
                {{ getSelectedAccountBalance(form.parentAccountId, form.accountId).text }}
              </span>
            </div>
          </el-form-item>

          <!-- 组合支付账户配置 -->
          <el-form-item v-if="useComboPayment && selectedType === 'EXPENSE'" label="付款账户" required>
            <div style="border: 1px solid #e5e7eb; border-radius: 8px; padding: 12px; background: #f9fafb;">
              <div style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center;">
                <span style="font-size: 13px; color: #6b7280;">
                  总金额：{{ form.amount || 0 }} 元
                  <span v-if="expenseRemainingAmount > 0.01 && expenseFundingLines.some(l => l.accountId)" style="color: #f59e0b; margin-left: 8px;">
                    还需 {{ expenseRemainingAmount.toFixed(2) }} 元
                  </span>
                  <span v-else-if="form.amount && expenseTotalAllocatedAmount > 0 && expenseRemainingAmount <= 0.01" style="color: #16a34a; margin-left: 8px;">
                    ✓ 已平衡
                  </span>
                </span>
                <el-button 
                  v-if="expenseRemainingAmount > 0.01 || expenseFundingLines.length === 0"
                  size="small" 
                  type="primary" 
                  text 
                  @click="handleAddExpenseFundingLine"
                  :disabled="!form.amount || form.amount <= 0"
                >
                  + 添加账户
                </el-button>
              </div>
              <div v-if="expenseFundingLines.length === 0" style="text-align: center; color: #9ca3af; padding: 20px;">
                {{ form.amount && form.amount > 0 ? '点击"添加账户"开始选择付款来源' : '请先填写金额' }}
              </div>
              <div v-else>
                <div 
                  v-for="(line, index) in expenseFundingLines" 
                  :key="index"
                  style="display: flex; gap: 12px; margin-bottom: 8px; align-items: center;"
                >
                  <el-cascader
                    :model-value="line.parentAccountId && line.accountId ? [line.parentAccountId, line.accountId] : []"
                    :options="getExpenseAvailableAccountOptions(index)"
                    :props="accountCascaderProps"
                    placeholder="选择账户"
                    style="flex: 1; min-width: 200px;"
                    clearable
                    @update:model-value="(val: any) => handleExpenseAccountChange(index, val)"
                  >
                    <template #default="{ node, data }">
                      <span>{{ data.label }}</span>
                      <span v-if="data.balanceText" :style="{ color: data.isCredit ? '#ef4444' : '#4ea4ff', fontSize: '11px', marginLeft: '8px' }">
                        {{ data.balanceText }}
                      </span>
                    </template>
                  </el-cascader>
                  <!-- 支付金额（只读，自动计算） -->
                  <div style="display: flex; align-items: center; gap: 4px; min-width: 100px;">
                    <span style="color: #374151; font-weight: 500;">¥</span>
                    <span style="color: #374151; font-size: 14px; font-weight: 500;">{{ (line.amount || 0).toFixed(2) }}</span>
                  </div>
                  <!-- 账户余额 -->
                  <span v-if="line.accountId" :style="{ color: getExpenseLineAccountBalance(line).isCredit ? '#ef4444' : '#4ea4ff', fontSize: '12px', whiteSpace: 'nowrap', minWidth: '80px' }">
                    余额 {{ getExpenseLineAccountBalance(line).text }}
                  </span>
                  <el-button
                    type="danger"
                    text
                    size="small"
                    @click="handleRemoveExpenseFundingLine(index)"
                    :disabled="expenseFundingLines.length <= 1"
                  >
                    删除
                  </el-button>
                </div>
              </div>
            </div>
          </el-form-item>

          <el-form-item v-if="selectedType === 'EXPENSE'" label="是否报销">
            <el-switch v-model="form.isReimbursable" />
            <span style="margin-left: 8px; color: #909399; font-size: 12px">标记为可报销</span>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.note" placeholder="比如：工资 / 咖啡 / 午餐" />
          </el-form-item>
        </template>
        
        <!-- 还款 -->
        <template v-else-if="selectedType === 'REPAYMENT'">
          <el-form-item label="发生时间" required>
            <el-date-picker
              v-model="form.occurredAt"
              type="datetime"
              placeholder="选择发生时间"
              style="width: 100%"
              size="small"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </el-form-item>
          <el-form-item label="还款账户" required>
            <div style="display: flex; align-items: center; gap: 12px;">
              <el-cascader
                v-model="selectedAccount"
                :options="accountCascaderOptions"
                :props="accountCascaderProps"
                placeholder="选择还款账户"
                style="flex: 1; min-width: 280px;"
                clearable
              >
                <template #default="{ node, data }">
                  <span>{{ data.label }}</span>
                  <span v-if="data.balanceText" :style="{ color: data.isCredit ? '#ef4444' : '#4ea4ff', fontSize: '12px', marginLeft: '12px' }">
                    {{ data.balanceText }}
                  </span>
                </template>
              </el-cascader>
              <span v-if="form.accountId" :style="{ color: getSelectedAccountBalance(form.parentAccountId, form.accountId).isCredit ? '#ef4444' : '#4ea4ff', fontSize: '13px', whiteSpace: 'nowrap' }">
                {{ getSelectedAccountBalance(form.parentAccountId, form.accountId).text }}
              </span>
            </div>
          </el-form-item>
          <el-form-item label="信贷账户" required>
            <div style="display: flex; align-items: center; gap: 12px;">
              <el-select v-model="form.creditAccountId" placeholder="选择要还款的信贷账户" style="flex: 1; min-width: 200px;">
                <el-option
                  v-for="acc in creditAccounts"
                  :key="acc.id"
                  :label="acc.accountName"
                  :value="acc.id"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center;">
                    <span>{{ acc.accountName }}</span>
                    <span style="color: #ef4444; font-size: 12px; margin-left: 12px;">
                      欠{{ formatCurrency(acc.balance || 0) }}
                    </span>
                  </div>
                </el-option>
              </el-select>
              <span v-if="form.creditAccountId" style="color: #ef4444; font-size: 13px; white-space: nowrap;">
                欠{{ formatCurrency(selectedCreditAccountBalance) }}
              </span>
            </div>
          </el-form-item>
          <el-form-item label="还款金额（元）" required>
            <el-input-number v-model="form.amount" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
        </template>

        <!-- 转账/分配 -->
        <template v-else-if="selectedType === 'TRANSFER_OUT' || selectedType === 'TRANSFER_IN'">
          <!-- 转账类型选择 -->
          <el-form-item label="转账类型" required>
            <el-radio-group v-model="transferType" @change="handleTransferTypeChange">
              <el-radio-button value="AMOUNT">金额转账</el-radio-button>
              <el-radio-button value="SHARE">份额转移</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="发生时间" required>
            <el-date-picker
              v-model="form.occurredAt"
              type="datetime"
              placeholder="选择发生时间"
              style="width: 100%"
              size="small"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </el-form-item>

          <!-- 金额转账 -->
          <template v-if="transferType === 'AMOUNT'">
            <el-form-item label="转出账户" required>
              <div style="display: flex; align-items: center; gap: 12px;">
                <el-cascader
                  v-model="selectedFromAccount"
                  :options="accountCascaderOptions"
                  :props="accountCascaderProps"
                  placeholder="选择转出账户"
                  style="flex: 1; min-width: 280px;"
                  clearable
                >
                  <template #default="{ node, data }">
                    <span>{{ data.label }}</span>
                    <span v-if="data.balanceText" :style="{ color: data.isCredit ? '#ef4444' : '#4ea4ff', fontSize: '12px', marginLeft: '12px' }">
                      {{ data.balanceText }}
                    </span>
                  </template>
                </el-cascader>
                <span v-if="form.fromAccountId" :style="{ color: getSelectedAccountBalance(form.fromParentAccountId, form.fromAccountId).isCredit ? '#ef4444' : '#4ea4ff', fontSize: '13px', whiteSpace: 'nowrap' }">
                  {{ getSelectedAccountBalance(form.fromParentAccountId, form.fromAccountId).text }}
                </span>
              </div>
            </el-form-item>
            <el-form-item label="转入账户" required>
              <div style="display: flex; align-items: center; gap: 12px;">
                <el-cascader
                  v-model="selectedToAccount"
                  :options="accountCascaderOptions"
                  :props="accountCascaderProps"
                  placeholder="选择转入账户"
                  style="flex: 1; min-width: 280px;"
                  clearable
                >
                  <template #default="{ node, data }">
                    <span>{{ data.label }}</span>
                    <span v-if="data.balanceText" :style="{ color: data.isCredit ? '#ef4444' : '#4ea4ff', fontSize: '12px', marginLeft: '12px' }">
                      {{ data.balanceText }}
                    </span>
                  </template>
                </el-cascader>
                <span v-if="form.toAccountId" :style="{ color: getSelectedAccountBalance(form.toParentAccountId, form.toAccountId).isCredit ? '#ef4444' : '#4ea4ff', fontSize: '13px', whiteSpace: 'nowrap' }">
                  {{ getSelectedAccountBalance(form.toParentAccountId, form.toAccountId).text }}
                </span>
              </div>
            </el-form-item>
            <el-form-item label="金额（元）" required>
              <el-input-number v-model="form.amount" :min="0.01" :precision="2" style="width: 100%" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="form.note" placeholder="留空则自动生成（转账: 转出账户 → 转入账户）" />
            </el-form-item>
          </template>

          <!-- 份额转移（同一父账户下的子账户间转移份额） -->
          <template v-else-if="transferType === 'SHARE'">
            <el-form-item label="父账户（关联产品）" required>
              <el-select 
                v-model="shareTransfer.parentAccountId" 
                placeholder="选择关联产品的父账户" 
                style="width: 100%"
                @change="handleShareTransferParentChange"
              >
                <el-option
                  v-for="acc in linkedProductParentAccounts"
                  :key="acc.id"
                  :label="acc.accountName"
                  :value="acc.id"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center;">
                    <span>{{ acc.accountName }}</span>
                    <span style="color: #909399; font-size: 12px; margin-left: 8px;">
                      总份额: {{ formatNumber((acc as any).initialShares || 0, 4) }}
                    </span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="源账户（转出）" required>
              <el-select 
                v-model="shareTransfer.fromAccountId" 
                placeholder="选择转出的子账户" 
                style="width: 100%"
                :disabled="!shareTransfer.parentAccountId"
                @change="handleShareTransferFromChange"
              >
                <el-option
                  v-for="acc in shareTransferChildAccounts"
                  :key="acc.id"
                  :label="acc.accountName"
                  :value="acc.id"
                  :disabled="acc.id === shareTransfer.toAccountId"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span>
                      {{ acc.accountName }}
                      <span v-if="(acc as any).isFixedAmount" style="color: #f59e0b; font-size: 11px; margin-left: 4px;">[固定]</span>
                    </span>
                    <span style="color: #4ea4ff; font-size: 12px; margin-left: 8px;">
                      {{ formatNumber(getChildAccountShares(acc.id), 4) }}份
                    </span>
                  </div>
                </el-option>
              </el-select>
              <div v-if="shareTransfer.fromAccountId" style="margin-top: 4px; font-size: 12px; color: #909399;">
                当前份额：{{ formatNumber(getChildAccountShares(shareTransfer.fromAccountId), 4) }}份
              </div>
            </el-form-item>
            <el-form-item label="目标账户（转入）" required>
              <el-select 
                v-model="shareTransfer.toAccountId" 
                placeholder="选择转入的子账户" 
                style="width: 100%"
                :disabled="!shareTransfer.parentAccountId"
              >
                <el-option
                  v-for="acc in shareTransferChildAccounts"
                  :key="acc.id"
                  :label="acc.accountName"
                  :value="acc.id"
                  :disabled="acc.id === shareTransfer.fromAccountId"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span>
                      {{ acc.accountName }}
                      <span v-if="(acc as any).isFixedAmount" style="color: #f59e0b; font-size: 11px; margin-left: 4px;">[固定]</span>
                    </span>
                    <span style="color: #4ea4ff; font-size: 12px; margin-left: 8px;">
                      {{ formatNumber(getChildAccountShares(acc.id), 4) }}份
                    </span>
                  </div>
                </el-option>
              </el-select>
              <div v-if="shareTransfer.toAccountId" style="margin-top: 4px; font-size: 12px; color: #909399;">
                当前份额：{{ formatNumber(getChildAccountShares(shareTransfer.toAccountId), 4) }}份
              </div>
            </el-form-item>
            <el-form-item label="转移份额" required>
              <div style="display: flex; align-items: center; gap: 8px;">
                <el-input-number 
                  v-model="shareTransfer.shares" 
                  :min="0.0001"
                  :max="shareTransfer.fromAccountId ? getChildAccountShares(shareTransfer.fromAccountId) : undefined"
                  :precision="4" 
                  style="flex: 1" 
                  placeholder="输入要转移的份额"
                />
                <el-button 
                  v-if="shareTransfer.fromAccountId"
                  size="small" 
                  type="primary" 
                  text 
                  @click="shareTransfer.shares = getChildAccountShares(shareTransfer.fromAccountId)"
                >
                  全部
                </el-button>
              </div>
            </el-form-item>
            <div v-if="shareTransfer.shares && shareTransfer.shares > 0 && shareTransfer.fromAccountId && shareTransfer.toAccountId" 
                 style="background: #f0f9ff; border: 1px solid #bae6fd; border-radius: 4px; padding: 12px; margin-bottom: 16px;">
              <div style="font-size: 13px; color: #0369a1; margin-bottom: 8px;">转移后预览：</div>
              <div style="display: flex; justify-content: space-between; font-size: 12px;">
                <span>{{ getAccountName(shareTransfer.fromAccountId) }}：{{ formatNumber(getChildAccountShares(shareTransfer.fromAccountId) - shareTransfer.shares, 4) }}份</span>
                <span>{{ getAccountName(shareTransfer.toAccountId) }}：{{ formatNumber(getChildAccountShares(shareTransfer.toAccountId) + shareTransfer.shares, 4) }}份</span>
              </div>
            </div>
            <el-form-item label="转移原因">
              <el-input
                v-model="shareTransfer.note"
                type="textarea"
                placeholder="请说明转移原因（如：赎回后余额清理）"
              />
            </el-form-item>
          </template>
        </template>

        <!-- 买入/申购 -->
        <template v-else-if="selectedType === 'BUY' || selectedType === 'SUBSCRIPTION'">
          <el-form-item label="场内/场外" required>
            <div style="display: flex; gap: 8px">
              <button
                type="button"
                :class="['channel-btn', form.channel === 'EXCHANGE' ? 'active' : '']"
                @click="form.channel = 'EXCHANGE'; handleChannelChange()"
              >
                场内
              </button>
              <button
                type="button"
                :class="['channel-btn', form.channel === 'OTC' ? 'active' : '']"
                @click="form.channel = 'OTC'; handleChannelChange()"
              >
                场外
              </button>
            </div>
          </el-form-item>
          <el-form-item label="产品" required>
            <el-select v-model="form.productId" placeholder="选择产品" style="width: 100%" filterable>
              <el-option
                v-for="prod in filteredProducts"
                :key="prod.id"
                :label="`${prod.productName} (${prod.productCode})`"
                :value="prod.id"
              />
            </el-select>
          </el-form-item>
          <!-- 场内买入：简化表单 -->
          <template v-if="form.channel === 'EXCHANGE'">
            <el-form-item label="交易时间" required>
              <el-date-picker
                v-model="form.requestedAt"
                type="datetime"
                placeholder="选择交易时间"
                style="width: 100%"
                format="YYYY-MM-DD HH:mm:ss"
                value-format="YYYY-MM-DD HH:mm:ss"
              />
            </el-form-item>
            <el-form-item label="成交价格" required>
              <el-input-number 
                v-model="form.nav" 
                :min="0.001" 
                :precision="4" 
                style="width: 100%"
                placeholder="输入成交价格"
              />
            </el-form-item>
            <el-form-item label="买入数量" required>
              <el-input-number v-model="form.shares" :min="1" :precision="0" style="width: 100%" />
            </el-form-item>
            <el-form-item label="手续费">
              <el-input-number v-model="form.fee" :min="0" :precision="2" style="width: 100%" />
              <div style="color: #67c23a; font-size: 12px; margin-top: 4px">
                <span v-if="isCalculatingFee">计算中...</span>
                <span v-else>根据券商费率自动计算（可手动修改）</span>
              </div>
            </el-form-item>
            <el-form-item v-if="form.shares && form.nav" label="成交金额">
              <div style="color: #4ea4ff; font-weight: 600; font-size: 16px;">
                {{ (form.shares * form.nav).toFixed(2) }} 元
                <span v-if="form.fee" style="color: #909399; font-size: 12px; margin-left: 8px;">
                  (含手续费共: {{ (form.shares * form.nav + form.fee).toFixed(2) }} 元)
                </span>
              </div>
            </el-form-item>
          </template>
          
          <!-- 场外申购：完整表单 -->
          <template v-else>
          <el-form-item label="发起时间" required>
            <el-date-picker
              v-model="form.requestedAt"
              type="datetime"
              placeholder="选择发起时间"
              style="width: 100%"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </el-form-item>
          <el-form-item label="确认日期">
            <el-date-picker
              v-model="form.confirmDate"
              type="date"
              placeholder="选择确认日期"
              style="width: 100%"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
          <el-form-item label="净值日期">
            <el-date-picker
              v-model="form.navDate"
              type="date"
              placeholder="选择净值日期"
              style="width: 100%"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
          <el-form-item label="净值">
            <el-input-number 
              v-model="form.nav" 
              :min="0.000001" 
              :precision="6" 
              style="width: 100%"
              placeholder="自动获取或手动输入"
            />
            <div style="color: #909399; font-size: 12px; margin-top: 4px">
                选择产品后自动获取，也可手动输入。申购时将根据金额和净值计算份额。
            </div>
          </el-form-item>
          <el-form-item v-if="form.amount && form.nav" label="预计份额">
            <div style="color: #4ea4ff; font-weight: 600">
              {{ ((form.amount - (form.fee || 0)) / form.nav).toFixed(2) }} 份
            </div>
          </el-form-item>
          </template>
          
          <el-form-item label="资金来源账户" required>
            <div style="display: flex; align-items: center; gap: 12px;">
              <el-cascader
                v-model="selectedAccount"
                :options="accountCascaderOptions"
                :props="accountCascaderProps"
                placeholder="选择资金来源账户"
                style="flex: 1; min-width: 280px;"
                clearable
                @change="handleBuyAccountCascaderChange"
              >
                <template #default="{ node, data }">
                  <span>{{ data.label }}</span>
                  <span v-if="data.balanceText" :style="{ color: data.isCredit ? '#ef4444' : '#4ea4ff', fontSize: '12px', marginLeft: '12px' }">
                    {{ data.balanceText }}
                  </span>
                </template>
              </el-cascader>
              <span v-if="form.accountId" :style="{ color: getSelectedAccountBalance(form.parentAccountId, form.accountId).isCredit ? '#ef4444' : '#4ea4ff', fontSize: '13px', whiteSpace: 'nowrap' }">
                {{ getSelectedAccountBalance(form.parentAccountId, form.accountId).text }}
              </span>
            </div>
            <div v-if="selectedBuyAccount?.linkedProductId && buyChildAccounts.length > 0" class="form-help-text" style="color: #f59e0b; margin-top: 4px;">
              此账户已关联产品，可以按子账户分别设置买入金额
            </div>
          </el-form-item>

          <!-- 多子账户配置（买入时） -->
          <el-form-item 
            v-if="selectedBuyAccount?.linkedProductId && buyChildAccounts.length > 0"
            label="子账户买入金额分配"
          >
            <div style="border: 1px solid #e5e7eb; border-radius: 4px; padding: 12px; background: #f9fafb;">
              <div style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center;">
                <span style="font-size: 13px; color: #6b7280;">
                  总金额：{{ form.amount || 0 }} 元，已分配：{{ buyTotalAllocatedAmount.toFixed(2) }} 元
                </span>
                <el-button 
                  size="small" 
                  type="primary" 
                  text 
                  @click="handleAddBuyFundingLine"
                >
                  + 添加子账户
                </el-button>
              </div>
              <div v-if="buyFundingLines.length === 0" style="text-align: center; color: #9ca3af; padding: 20px;">
                点击"添加子账户"开始分配
              </div>
              <div v-else>
                <div 
                  v-for="(line, index) in buyFundingLines" 
                  :key="index"
                  style="display: flex; gap: 8px; margin-bottom: 8px; align-items: center; flex-wrap: wrap;"
                >
                  <el-select
                    v-model="line.accountId"
                    placeholder="选择子账户"
                    style="flex: 1; min-width: 150px;"
                    @change="handleBuyFundingLineAccountChange(index)"
                  >
                    <el-option
                      v-for="acc in buyAvailableChildAccounts"
                      :key="acc.id"
                      :label="acc.accountName"
                      :value="acc.id"
                      :disabled="buyFundingLines.some((fl, i) => i !== index && fl.accountId === acc.id)"
                    >
                      <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                        <span>
                          {{ acc.accountName }}
                          <span v-if="acc.isFixedAmount" style="color: #f59e0b; font-size: 11px; margin-left: 4px;">[固定]</span>
                        </span>
                        <span style="color: #4ea4ff; font-size: 12px; margin-left: 8px;">
                          {{ formatCurrency(acc.balance) }}
                        </span>
                      </div>
                    </el-option>
                  </el-select>
                  <el-input-number
                    v-model="line.amount"
                    :min="0.01"
                    :precision="2"
                    placeholder="金额"
                    style="width: 130px"
                  />
                  <span v-if="line.amount && form.nav" style="color: #909399; font-size: 12px; white-space: nowrap;">
                    ≈{{ (line.amount / form.nav).toFixed(4) }}份
                  </span>
                  <el-button 
                    v-if="getBuyAccountFixedAmount(line.accountId)"
                    size="small" 
                    type="primary" 
                    text 
                    @click="fillBuyFixedAmount(index)"
                    title="填充固定金额"
                  >
                    固定
                  </el-button>
                  <el-button 
                    size="small" 
                    type="danger" 
                    text 
                    @click="handleRemoveBuyFundingLine(index)"
                  >
                    删除
                  </el-button>
                </div>
                <div v-if="buyAllocationError" style="color: #ef4444; font-size: 12px; margin-top: 8px;">
                  {{ buyAllocationError }}
                </div>
              </div>
            </div>
          </el-form-item>

          <!-- 场外申购需要填金额 -->
          <template v-if="form.channel !== 'EXCHANGE'">
            <el-form-item label="申购金额（元）" required>
            <el-input-number v-model="form.amount" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
            <el-form-item label="手续费（元）">
            <el-input-number v-model="form.fee" :min="0" :precision="2" style="width: 100%" />
            <div style="color: #67c23a; font-size: 12px; margin-top: 4px">
              根据产品买入费率自动计算（可手动修改）
            </div>
          </el-form-item>
          </template>
        </template>
        
        <!-- 卖出/赎回 -->
        <template v-else-if="selectedType === 'SELL' || selectedType === 'REDEMPTION'">
          <el-form-item label="场内/场外" required>
            <div style="display: flex; gap: 8px">
              <button
                type="button"
                :class="['channel-btn', form.channel === 'EXCHANGE' ? 'active' : '']"
                @click="form.channel = 'EXCHANGE'; handleChannelChange()"
              >
                场内
              </button>
              <button
                type="button"
                :class="['channel-btn', form.channel === 'OTC' ? 'active' : '']"
                @click="form.channel = 'OTC'; handleChannelChange()"
              >
                场外
              </button>
            </div>
          </el-form-item>
          <el-form-item label="产品" required>
            <el-select v-model="form.productId" placeholder="选择产品" style="width: 100%" filterable>
              <el-option
                v-for="prod in filteredProducts"
                :key="prod.id"
                :label="`${prod.productName} (${prod.productCode})`"
                :value="prod.id"
              />
            </el-select>
          </el-form-item>
          <!-- 产品持仓信息展示 -->
          <el-form-item v-if="form.productId && productHolding" label="当前持仓">
            <div style="display: flex; gap: 24px; color: #606266;">
              <span>
                <strong style="color: #409eff;">{{ productHolding.totalShares.toFixed(4) }}</strong> 份
              </span>
              <span>
                市值 <strong style="color: #f59e0b;">{{ formatCurrency(productHolding.marketValue) }}</strong>
              </span>
            </div>
            <div v-if="productAccountHoldings.length > 0" style="margin-top: 8px; font-size: 12px; color: #909399;">
              各账户持仓：
              <span v-for="(ah, idx) in productAccountHoldings" :key="ah.accountId" style="margin-left: 8px;">
                {{ ah.parentAccountName ? `${ah.parentAccountName}-` : '' }}{{ ah.accountName }}: {{ ah.shares.toFixed(4) }}份
                <span v-if="idx < productAccountHoldings.length - 1">; </span>
              </span>
            </div>
          </el-form-item>
          <el-form-item v-else-if="form.productId && loadingProductHolding" label="当前持仓">
            <span style="color: #909399;">加载中...</span>
          </el-form-item>
          <el-form-item v-else-if="form.productId && !productHolding && !loadingProductHolding" label="当前持仓">
            <span style="color: #909399;">暂无持仓</span>
          </el-form-item>
          <!-- 场内卖出：简化表单 -->
          <template v-if="form.channel === 'EXCHANGE'">
            <el-form-item label="交易时间" required>
              <el-date-picker
                v-model="form.requestedAt"
                type="datetime"
                placeholder="选择交易时间"
                style="width: 100%"
                format="YYYY-MM-DD HH:mm:ss"
                value-format="YYYY-MM-DD HH:mm:ss"
              />
            </el-form-item>
            <el-form-item label="成交价格" required>
              <el-input-number 
                v-model="form.nav" 
                :min="0.001" 
                :precision="4" 
                style="width: 100%"
                placeholder="输入成交价格"
              />
            </el-form-item>
            <el-form-item label="卖出数量" required>
              <el-input-number v-model="form.shares" :min="1" :precision="0" style="width: 100%" />
            </el-form-item>
            <el-form-item label="手续费">
              <el-input-number v-model="form.fee" :min="0" :precision="2" style="width: 100%" />
              <div style="color: #67c23a; font-size: 12px; margin-top: 4px">
                <span v-if="isCalculatingFee">计算中...</span>
                <span v-else>根据券商费率自动计算（可手动修改）</span>
              </div>
            </el-form-item>
            <el-form-item v-if="form.shares && form.nav" label="成交金额">
              <div style="color: #f59e0b; font-weight: 600; font-size: 16px;">
                {{ (form.shares * form.nav).toFixed(2) }} 元
                <span v-if="form.fee" style="color: #909399; font-size: 12px; margin-left: 8px;">
                  (扣除手续费后到账: {{ (form.shares * form.nav - form.fee).toFixed(2) }} 元)
                </span>
              </div>
            </el-form-item>
          </template>
          
          <!-- 场外赎回：完整表单 -->
          <template v-else>
          <el-form-item label="发起时间" required>
            <el-date-picker
              v-model="form.requestedAt"
              type="datetime"
              placeholder="选择发起时间"
              style="width: 100%"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </el-form-item>
          <el-form-item label="确认日期">
            <el-date-picker
              v-model="form.confirmDate"
              type="date"
              placeholder="选择确认日期"
              style="width: 100%"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
          <el-form-item label="净值日期">
            <el-date-picker
              v-model="form.navDate"
              type="date"
              placeholder="选择净值日期"
              style="width: 100%"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
          <el-form-item label="净值">
            <el-input-number 
              v-model="form.nav" 
              :min="0.000001" 
              :precision="6" 
              style="width: 100%"
              placeholder="自动获取或手动输入"
            />
            <div style="color: #909399; font-size: 12px; margin-top: 4px">
                选择产品后自动获取，也可手动输入。赎回时将根据份额和净值计算金额。
            </div>
          </el-form-item>
            <el-form-item label="赎回份额" required>
            <el-input-number v-model="form.shares" :min="0.01" :precision="4" style="width: 100%" />
          </el-form-item>
            <el-form-item label="手续费">
              <el-input-number v-model="form.fee" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item v-if="form.shares && form.nav" label="预计到账金额">
            <div style="color: #f59e0b; font-weight: 600; font-size: 16px;">
              {{ (form.shares * form.nav - (form.fee || 0)).toFixed(2) }} 元
            </div>
          </el-form-item>
          </template>
          
          <!-- 到账账户（收到赎回款的账户） -->
          <el-form-item label="到账账户" required>
            <div style="display: flex; align-items: center; gap: 12px;">
              <el-cascader
                v-model="selectedAccount"
                :options="accountCascaderOptions"
                :props="accountCascaderProps"
                placeholder="选择到账账户"
                style="flex: 1; min-width: 280px;"
                clearable
                @change="handleSellAccountCascaderChange"
              >
                <template #default="{ node, data }">
                  <span>{{ data.label }}</span>
                  <span v-if="data.balanceText" :style="{ color: data.isCredit ? '#ef4444' : '#4ea4ff', fontSize: '12px', marginLeft: '12px' }">
                    {{ data.balanceText }}
                  </span>
                </template>
              </el-cascader>
              <span v-if="form.accountId" :style="{ color: getSelectedAccountBalance(form.parentAccountId, form.accountId).isCredit ? '#ef4444' : '#4ea4ff', fontSize: '13px', whiteSpace: 'nowrap' }">
                {{ getSelectedAccountBalance(form.parentAccountId, form.accountId).text }}
              </span>
            </div>
          </el-form-item>

          <!-- 出金账户（从哪些账户赎回份额） -->
          <div 
            v-if="productAccountHoldings.length > 0"
            class="form-section"
            style="margin-top: 16px;"
          >
            <div class="form-section-title" style="font-weight: 600; margin-bottom: 8px;">出金来源分配</div>
            <div style="color: #909399; font-size: 12px; margin-bottom: 8px;">
              💡 从以下账户赎回份额，赎回款将到账至上方选择的账户
            </div>
            <div style="border: 1px solid #e5e7eb; border-radius: 4px; padding: 12px; background: #f9fafb;">
              <div style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center;">
                <span style="font-size: 13px; color: #6b7280;">
                  总份额：{{ form.shares || 0 }}，已分配：{{ sellTotalAllocatedShares.toFixed(4) }}
                </span>
                <el-button 
                  size="small" 
                  type="primary" 
                  text 
                  @click="handleAddSellFundingLine"
                >
                  + 添加子账户
                </el-button>
              </div>
              <div v-if="sellFundingLines.length === 0" style="text-align: center; color: #9ca3af; padding: 20px;">
                点击"添加子账户"开始分配
              </div>
              <div v-else>
                <div 
                  v-for="(line, index) in sellFundingLines" 
                  :key="index"
                  style="display: flex; gap: 8px; margin-bottom: 8px; align-items: center;"
                >
                  <el-select
                    v-model="line.accountId"
                    placeholder="选择子账户"
                    style="flex: 1; min-width: 120px;"
                    @change="handleSellFundingLineAccountChange(index)"
                  >
                    <el-option
                      v-for="acc in sellAvailableChildAccounts"
                      :key="acc.id"
                      :label="acc.accountName"
                      :value="acc.id"
                      :disabled="sellFundingLines.some((fl, i) => i !== index && fl.accountId === acc.id)"
                    >
                      <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                        <span>
                          {{ acc.accountName }}
                          <span v-if="(acc as any).isFixedAmount" style="color: #f59e0b; font-size: 11px; margin-left: 4px;">[固定]</span>
                        </span>
                        <span style="font-size: 12px; margin-left: 8px;">
                          <span v-if="(acc as any).availableShares" style="color: #909399;">
                            {{ (acc as any).availableShares.toFixed(4) }}份
                          </span>
                          <span style="color: #4ea4ff; margin-left: 4px;">
                            {{ formatCurrency(acc.balance) }}
                          </span>
                        </span>
                      </div>
                    </el-option>
                  </el-select>
                  <!-- 统一按份额赎回（结算时由后端处理固定金额账户的金额分配） -->
                  <el-input-number
                    v-model="line.shares"
                    :min="0.01"
                    :precision="4"
                    placeholder="份额"
                    style="width: 130px"
                  />
                  <span v-if="line.shares && form.nav" style="color: #909399; font-size: 12px; white-space: nowrap;">
                    ≈{{ formatCurrency(line.shares * form.nav) }}
                  </span>
                  <span v-if="getSellAccountInfo(line.accountId)?.isFixedAmount" style="color: #f59e0b; font-size: 11px; margin-left: 4px;">
                    [固定{{ formatCurrency(getSellAccountInfo(line.accountId)?.fixedAmount || 0) }}]
                  </span>
                  <el-button 
                    v-if="getAccountAvailableShares(line.accountId)"
                    size="small" 
                    type="primary" 
                    text 
                    @click="fillMaxShares(index)"
                    title="填充该账户最大可用份额"
                  >
                    最大
                  </el-button>
                  <el-button 
                    size="small" 
                    type="danger" 
                    text 
                    @click="handleRemoveSellFundingLine(index)"
                  >
                    删除
                  </el-button>
                </div>
                <div v-if="sellAllocationError" style="color: #ef4444; font-size: 12px; margin-top: 8px;">
                  {{ sellAllocationError }}
                </div>
              </div>
            </div>
          </div>

          <el-form-item label="费用（元）">
            <el-input-number v-model="form.fee" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
        </template>

        <!-- 逆回购 -->
        <template v-else-if="selectedType === 'BOND_REPO'">
          <el-form-item label="发生时间" required>
            <el-date-picker
              v-model="form.occurredAt"
              type="datetime"
              placeholder="选择发生时间"
              style="width: 100%"
              size="small"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </el-form-item>
          <el-form-item label="账户" required>
            <div style="display: flex; align-items: center; gap: 12px;">
              <el-cascader
                v-model="selectedAccount"
                :options="accountCascaderOptions"
                :props="accountCascaderProps"
                placeholder="选择账户"
                style="flex: 1; min-width: 280px;"
                clearable
              >
                <template #default="{ node, data }">
                  <span>{{ data.label }}</span>
                  <span v-if="data.balanceText" :style="{ color: data.isCredit ? '#ef4444' : '#4ea4ff', fontSize: '12px', marginLeft: '12px' }">
                    {{ data.balanceText }}
                  </span>
                </template>
              </el-cascader>
              <span v-if="form.accountId" :style="{ color: getSelectedAccountBalance(form.parentAccountId, form.accountId).isCredit ? '#ef4444' : '#4ea4ff', fontSize: '13px', whiteSpace: 'nowrap' }">
                {{ getSelectedAccountBalance(form.parentAccountId, form.accountId).text }}
              </span>
            </div>
          </el-form-item>
          <el-form-item label="金额（元）" required>
            <el-input-number v-model="form.amount" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="期限（天）" required>
            <el-input-number v-model="form.repoDays" :min="1" :max="365" :precision="0" style="width: 100%" />
            <div style="color: #909399; font-size: 12px; margin-top: 4px">通常为1天期逆回购</div>
          </el-form-item>
          <el-form-item label="年化利率（%）">
            <el-input-number v-model="form.repoRate" :min="0" :precision="4" style="width: 100%" />
          </el-form-item>
        </template>

        <!-- 转托管（场外转场内） -->
        <template v-else-if="selectedType === 'CUSTODY_TRANSFER'">
          <el-form-item label="产品" required>
            <el-select v-model="form.productId" placeholder="选择场外产品" style="width: 100%" filterable>
              <el-option
                v-for="prod in otcProducts"
                :key="prod.id"
                :label="`${prod.productName} (${prod.productCode})`"
                :value="prod.id"
              />
            </el-select>
            <div style="color: #909399; font-size: 12px; margin-top: 4px">场外 → 场内</div>
          </el-form-item>
          <el-form-item label="到账日期" required>
            <el-date-picker
              v-model="form.transferDate"
              type="date"
              placeholder="选择场内到账日期"
              style="width: 100%"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
            />
            <div style="color: #909399; font-size: 12px; margin-top: 4px">填写场内显示的转入成功日期</div>
          </el-form-item>
          <el-form-item label="场内价格" required>
            <el-input-number v-model="form.transferInPrice" :min="0" :precision="4" style="width: 100%" />
            <div style="color: #909399; font-size: 12px; margin-top: 4px">场内到账时的价格（同花顺显示的转入价格）</div>
          </el-form-item>
          <el-form-item label="份额" required>
            <el-input-number v-model="form.shares" :min="1" :precision="0" :step="1" style="width: 100%" />
            <div style="color: #909399; font-size: 12px; margin-top: 4px">转托管只能转整数份额，至少保留1份在原账户</div>
          </el-form-item>
          <!-- 转托管时显示场外持仓信息 -->
          <el-form-item v-if="form.productId && otcHoldingForTransfer" label="场外可转出持仓">
            <div style="background: #f0f9ff; border: 1px solid #bae6fd; border-radius: 4px; padding: 12px;">
              <div style="display: flex; gap: 24px; align-items: center;">
                <span style="color: #0369a1; font-weight: 600;">
                  {{ otcHoldingForTransfer.parentAccountName ? `${otcHoldingForTransfer.parentAccountName}-` : '' }}{{ otcHoldingForTransfer.accountName }}
                </span>
                <span>
                  当前持仓: <strong style="color: #409eff; font-size: 16px;">{{ otcHoldingForTransfer.shares.toFixed(4) }}</strong> 份
                </span>
              </div>
              <div style="margin-top: 8px; font-size: 12px; color: #0369a1;">
                最大可转出: {{ Math.max(0, Math.floor(otcHoldingForTransfer.shares) - 1) }} 份（保留1份）
              </div>
            </div>
          </el-form-item>
          <el-form-item v-else-if="form.productId && !loadingProductHolding" label="场外持仓">
            <span style="color: #ef4444;">该产品暂无场外持仓，无法转托管</span>
          </el-form-item>
        </template>
        
        <!-- 退款 -->
        <template v-else-if="selectedType === 'REFUND'">
          <el-form-item label="原交易ID" required>
            <el-input v-model="form.relatedTxnId" placeholder="输入原交易ID" />
          </el-form-item>
          <el-form-item label="退款金额（元）" required>
            <el-input-number v-model="form.amount" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="退款账户" required>
            <div style="display: flex; align-items: center; gap: 12px;">
              <el-cascader
                v-model="selectedAccount"
                :options="accountCascaderOptions"
                :props="accountCascaderProps"
                placeholder="选择退款账户"
                style="flex: 1; min-width: 280px;"
                clearable
              >
                <template #default="{ node, data }">
                  <span>{{ data.label }}</span>
                  <span v-if="data.balanceText" :style="{ color: data.isCredit ? '#ef4444' : '#4ea4ff', fontSize: '12px', marginLeft: '12px' }">
                    {{ data.balanceText }}
                  </span>
                </template>
              </el-cascader>
              <span v-if="form.accountId" :style="{ color: getSelectedAccountBalance(form.parentAccountId, form.accountId).isCredit ? '#ef4444' : '#4ea4ff', fontSize: '13px', whiteSpace: 'nowrap' }">
                {{ getSelectedAccountBalance(form.parentAccountId, form.accountId).text }}
              </span>
            </div>
          </el-form-item>
        </template>

        <!-- 调整（仅余额调整） -->
        <template v-else-if="selectedType === 'ADJUST'">
          <el-form-item label="账户" required>
            <div style="display: flex; align-items: center; gap: 12px;">
              <el-cascader
                v-model="selectedAccount"
                :options="accountCascaderOptions"
                :props="accountCascaderProps"
                placeholder="选择账户"
                style="flex: 1; min-width: 280px;"
                clearable
              >
                <template #default="{ node, data }">
                  <span>{{ data.label }}</span>
                  <span v-if="data.balanceText" :style="{ color: data.isCredit ? '#ef4444' : '#4ea4ff', fontSize: '12px', marginLeft: '12px' }">
                    {{ data.balanceText }}
                  </span>
                </template>
              </el-cascader>
              <span v-if="form.accountId" :style="{ color: getSelectedAccountBalance(form.parentAccountId, form.accountId).isCredit ? '#ef4444' : '#4ea4ff', fontSize: '13px', whiteSpace: 'nowrap' }">
                {{ getSelectedAccountBalance(form.parentAccountId, form.accountId).text }}
              </span>
            </div>
          </el-form-item>
            <el-form-item label="调整后余额（元）" required>
              <el-input-number v-model="form.amount" :precision="2" style="width: 100%" />
            </el-form-item>
            <el-form-item label="调整原因">
              <el-input
                v-model="form.note"
                type="textarea"
                placeholder="请说明调整原因（如：对账差异修正）"
              />
            </el-form-item>
        </template>
      </el-form>
      <div style="display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px">
        <el-button @click="step = 1">← 返回</el-button>
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">提交</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { ElNotification } from 'element-plus'
import { useAccountStore, useProductStore } from '@wealth-hub/shared'
import { ledgerApi, getFundUsageLabel, expenseCategories, incomeCategories, getCategoryGroups, findCategoryById, getCategoryDisplayName, navApi, productApi, formatCurrency, orderApi, holdingApi } from '@wealth-hub/shared'
import type { Account, LedgerTxnDetail } from '@wealth-hub/shared'

// 账户持仓信息类型
interface AccountHoldingInfo {
  accountId: number
  accountName: string
  parentAccountName?: string
  shares: number
  marketValue: number
}

const props = defineProps<{
  modelValue: boolean
  editingTxn?: LedgerTxnDetail | null  // 编辑模式：传入要编辑的交易
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const accountStore = useAccountStore()
const productStore = useProductStore()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const step = ref(1)
const selectedType = ref('')
const submitting = ref(false)

const form = ref({
  occurredAt: new Date().toISOString().slice(0, 19).replace('T', ' '),
  category: [] as number[],
  parentAccountId: undefined as number | undefined,
  accountId: undefined as number | undefined,
  creditAccountId: undefined as number | undefined,
  fromParentAccountId: undefined as number | undefined,
  fromAccountId: undefined as number | undefined,
  toParentAccountId: undefined as number | undefined,
  toAccountId: undefined as number | undefined,
  productId: undefined as number | undefined,
  orderType: 'BUY' as 'BUY' | 'SELL' | 'SUBSCRIPTION' | 'REDEMPTION',
  amount: undefined as number | undefined,
  shares: undefined as number | undefined,
  fee: 0,
  requestedAt: new Date().toISOString().slice(0, 19).replace('T', ' '),
  confirmDate: undefined as string | undefined,
  navDate: undefined as string | undefined,
  nav: undefined as number | undefined,  // 净值（用于计算份额）
  transferDate: undefined as string | undefined,
  transferOutPrice: 0,
  transferInPrice: 0,
  fromChannel: 'OTC',
  toChannel: 'EXCHANGE',
  channel: undefined as 'EXCHANGE' | 'OTC' | undefined,
  relatedTxnId: undefined as string | undefined,
  isReimbursable: false,
  repoDays: 1,
  repoRate: undefined as number | undefined,
  note: '',
})

// 买入时的多子账户配置
const buyFundingLines = ref<Array<{
  accountId: number | undefined
  amount?: number
}>>([])

// 卖出时的多子账户配置（统一按份额赎回，结算时由后端处理金额分配）
const sellFundingLines = ref<Array<{
  accountId: number | undefined
  shares?: number
}>>([])

// 支出时的组合支付配置（多账户付款）
const expenseFundingLines = ref<Array<{
  parentAccountId: number | undefined
  accountId: number | undefined
  amount?: number
}>>([])

// 是否启用组合支付
const useComboPayment = ref(false)

// 转账类型：AMOUNT（金额转账） | SHARE（份额转移）
const transferType = ref<'AMOUNT' | 'SHARE'>('AMOUNT')

// 调整类型：BALANCE（余额调整）- 份额转移已移至转账功能
const adjustType = ref<'BALANCE'>('BALANCE')

// 份额转移表单（用于转账功能中的份额转移）
const shareTransfer = ref({
  parentAccountId: undefined as number | undefined,
  fromAccountId: undefined as number | undefined,
  toAccountId: undefined as number | undefined,
  shares: undefined as number | undefined,
  note: ''
})

// 份额转移相关的子账户份额缓存
const shareTransferAccountShares = ref<Map<number, number>>(new Map())

const txnTypeOptions: Record<string, { name: string; icon: string }> = {
  EXPENSE: { name: '支出', icon: '💸' },
  INCOME: { name: '收入', icon: '💵' },
  TRANSFER_OUT: { name: '转账/分配', icon: '⇄' },
  REPAYMENT: { name: '还款', icon: '💳' },
  BUY: { name: '买入/申购', icon: '📈' },
  SELL: { name: '卖出/赎回', icon: '📉' },
  BOND_REPO: { name: '逆回购', icon: '🔄' },
  CUSTODY_TRANSFER: { name: '转托管', icon: '↔️' },
  ADJUST: { name: '调整', icon: '⚙️' },
}

// const cashLeafAccounts = computed(() => accountStore.cashLeafAccounts) // 未使用，已注释

// 父账户列表（有子账户的账户）
const parentAccounts = computed(() => {
  // 从账户树中获取所有有子账户的账户
  const parentList: Account[] = []
  
  // 安全检查：确保 accountTree 存在且是数组
  if (!accountStore.accountTree || !Array.isArray(accountStore.accountTree)) {
    return parentList
  }
  
  function traverse(accounts: Account[]) {
    accounts.forEach(acc => {
      // 如果有children且children不为空，说明是父账户
      if (acc.children && acc.children.length > 0 && acc.accountKind === 'REAL') {
        parentList.push(acc)
        // 递归处理子账户（因为子账户可能也是父账户）
        traverse(acc.children)
      }
    })
  }
  
  traverse(accountStore.accountTree)
  return parentList
})

// 从账户树中查找账户的辅助函数
function findAccountById(accounts: Account[], id: number): Account | null {
  for (const acc of accounts) {
    if (acc.id === id) {
      return acc
    }
    if (acc.children && acc.children.length > 0) {
      const found = findAccountById(acc.children, id)
      if (found) return found
    }
  }
  return null
}

// 可用的子账户（根据选择的父账户）
const availableChildAccounts = computed(() => {
  if (!form.value.parentAccountId) return []
  
  // 安全检查：确保 accountTree 存在且是数组
  if (!accountStore.accountTree || !Array.isArray(accountStore.accountTree)) {
    return []
  }
  
  const parentAccount = findAccountById(accountStore.accountTree, form.value.parentAccountId)
  if (!parentAccount || !parentAccount.children) return []
  
  // 返回父账户的所有REAL类型的子账户
  return parentAccount.children.filter(acc => acc.accountKind === 'REAL')
})

// 转出账户的可用于账户（根据选择的转出父账户）
const availableFromChildAccounts = computed(() => {
  if (!form.value.fromParentAccountId) return []
  
  // 安全检查：确保 accountTree 存在且是数组
  if (!accountStore.accountTree || !Array.isArray(accountStore.accountTree)) {
    return []
  }
  
  const parentAccount = findAccountById(accountStore.accountTree, form.value.fromParentAccountId)
  if (!parentAccount || !parentAccount.children) return []
  
  // 返回父账户的所有REAL类型的子账户
  return parentAccount.children.filter(acc => acc.accountKind === 'REAL')
})

// 转入账户的可用于账户（根据选择的转入父账户）
const availableToChildAccounts = computed(() => {
  if (!form.value.toParentAccountId) return []
  
  // 安全检查：确保 accountTree 存在且是数组
  if (!accountStore.accountTree || !Array.isArray(accountStore.accountTree)) {
    return []
  }
  
  const parentAccount = findAccountById(accountStore.accountTree, form.value.toParentAccountId)
  if (!parentAccount || !parentAccount.children) return []
  
  // 返回父账户的所有REAL类型的子账户
  return parentAccount.children.filter(acc => acc.accountKind === 'REAL')
})

// 关联产品的父账户列表（有 linkedProductId 的账户）
const linkedProductParentAccounts = computed(() => {
  const result: Account[] = []
  
  // 安全检查：确保 accountTree 存在且是数组
  if (!accountStore.accountTree || !Array.isArray(accountStore.accountTree)) {
    return result
  }
  
  function traverse(accounts: Account[]) {
    accounts.forEach(acc => {
      // 有关联产品ID且有子账户的账户
      if (acc.accountKind === 'REAL' && 
          (acc as any).linkedProductId && 
          acc.children && acc.children.length > 0) {
        result.push(acc)
      }
      if (acc.children && acc.children.length > 0) {
        traverse(acc.children)
      }
    })
  }
  
  traverse(accountStore.accountTree)
  return result
})

// 份额转移的子账户列表
const shareTransferChildAccounts = computed(() => {
  if (!shareTransfer.value.parentAccountId) return []
  
  const parentAccount = findAccountById(accountStore.accountTree, shareTransfer.value.parentAccountId)
  if (!parentAccount || !parentAccount.children) return []
  
  // 返回父账户的所有REAL类型的子账户
  return parentAccount.children.filter(acc => acc.accountKind === 'REAL')
})

// 信贷账户列表（只包括叶子账户，即子账户）
const creditAccounts = computed(() => {
  // 从账户树中获取所有信贷账户的叶子账户
  const creditList: Account[] = []
  
  function traverse(accounts: Account[]) {
    accounts.forEach(acc => {
      // 如果是信贷账户类型
      if (acc.accountKind === 'REAL' &&
          (acc.accountType === 'CREDIT_CARD' || 
           acc.accountType === 'HUABEI' || 
           acc.accountType === 'BAITIAO' || 
           acc.accountType === 'LOAN')) {
        // 如果是叶子账户（没有子账户），添加到列表
        if (!acc.children || acc.children.length === 0) {
          creditList.push(acc)
        }
      }
      // 递归处理子账户
      if (acc.children && acc.children.length > 0) {
        traverse(acc.children)
      }
    })
  }
  
  traverse(accountStore.accountTree)
  return creditList
})

const products = computed(() => productStore.products.filter((p) => p.isActive))

// 场外产品（用于转托管）
const otcProducts = computed(() => productStore.products.filter((p) => p.isActive && p.channel === 'OTC'))

// 转托管时获取场外持仓（只取场外的持仓账户）
const otcHoldingForTransfer = computed(() => {
  if (selectedType.value !== 'CUSTODY_TRANSFER' || !form.value.productId) {
    return null
  }
  // 从产品持仓明细中找场外持仓（账户名包含"场外"或"OTC"，或账户类型为POSITION且不是场内）
  const holding = productAccountHoldings.value.find(h => 
    h.accountName?.includes('场外') || 
    h.accountName?.includes('OTC') ||
    (h.accountName && !h.accountName.includes('场内') && !h.accountName.includes('EXCHANGE') && h.shares > 0)
  )
  return holding || null
})

// 产品持仓信息（用于卖出/赎回时显示）
const productHolding = ref<{ totalShares: number; marketValue: number } | null>(null)
const productAccountHoldings = ref<AccountHoldingInfo[]>([])
const loadingProductHolding = ref(false)

// 缓存所有持仓数据（在对话框打开时加载一次）
interface HoldingEntry {
  productId: number
  totalShares?: number
  marketValue?: number
}
const allHoldingsCache = ref<Map<number, HoldingEntry>>(new Map())

// 预加载所有持仓数据
async function preloadAllHoldings() {
  try {
    const holdings = await holdingApi.getHoldings()
    // API 返回的是对象格式 { "1": {...}, "6": {...} } 或数组格式
    const holdingsMap = new Map<number, HoldingEntry>()
    
    if (Array.isArray(holdings)) {
      // 数组格式
      holdings.forEach((h: any) => {
        if (h.productId) {
          holdingsMap.set(h.productId, h)
        }
      })
    } else if (holdings && typeof holdings === 'object') {
      // 对象格式，key 可能是字符串的 productId
      Object.entries(holdings).forEach(([key, value]: [string, any]) => {
        const productId = value?.productId || parseInt(key)
        if (productId && value) {
          holdingsMap.set(productId, value)
        }
      })
    }
    
    allHoldingsCache.value = holdingsMap
    console.log('持仓数据加载完成，共', holdingsMap.size, '个产品')
  } catch (error) {
    console.error('加载持仓数据失败:', error)
    allHoldingsCache.value = new Map()
  }
}

// 从缓存中获取产品持仓信息
function getProductHoldingFromCache(productId: number) {
  const holding = allHoldingsCache.value.get(productId)
  if (holding) {
    productHolding.value = {
      totalShares: holding.totalShares || 0,
      marketValue: holding.marketValue || 0
    }
  } else {
    productHolding.value = null
  }
}

// 获取产品在各账户的持仓明细
async function fetchProductAccountHoldings(productId: number) {
  if (!productId) {
    productAccountHoldings.value = []
    return
  }
  
  loadingProductHolding.value = true
  try {
    // @ts-ignore - API method exists, TypeScript may not see it until rebuild
    const accountHoldings = await holdingApi.getProductHoldingsByAccount(productId) as AccountHoldingInfo[]
    productAccountHoldings.value = accountHoldings
  } catch (error) {
    console.error('获取产品账户持仓失败:', error)
    productAccountHoldings.value = []
  } finally {
    loadingProductHolding.value = false
  }
}

// 监听产品选择变化，从缓存获取持仓信息（在卖出/赎回/转托管时）
watch(() => form.value.productId, async (productId) => {
  if (productId && (selectedType.value === 'SELL' || selectedType.value === 'REDEMPTION' || selectedType.value === 'CUSTODY_TRANSFER')) {
    // 从缓存获取总持仓
    getProductHoldingFromCache(productId)
    // 异步获取各账户持仓明细
    await fetchProductAccountHoldings(productId)
    
    // 如果只有一个账户有持仓，自动选择并填充份额（场外赎回时）
    if ((selectedType.value === 'SELL' || selectedType.value === 'REDEMPTION') && 
        productAccountHoldings.value.length === 1) {
      const singleHolding = productAccountHoldings.value[0]
      sellFundingLines.value = [{
        accountId: singleHolding.accountId,
        shares: singleHolding.shares
      }]
      // 同时设置赎回份额
      form.value.shares = singleHolding.shares
    }
  } else {
    productHolding.value = null
    productAccountHoldings.value = []
  }
})

// 买入时的账户相关计算
const selectedBuyAccount = computed(() => {
  if (!form.value.accountId || (selectedType.value !== 'BUY' && selectedType.value !== 'SUBSCRIPTION')) return null
  const account = accountStore.accounts.find((a) => a.id === form.value.accountId)
  return (account as Account & { linkedProductId?: number }) || null
})

const buyChildAccounts = computed(() => {
  if (!form.value.accountId || selectedType.value !== 'BUY' && selectedType.value !== 'SUBSCRIPTION') return []
  // 只返回启用的子账户
  return accountStore.accounts.filter((a) => a.parentAccountId === form.value.accountId && a.isActive !== false)
})

// 扩展类型定义用于子账户
interface ChildAccountInfo {
  id: number
  accountName: string
  balance: number
  isFixedAmount?: boolean
  fixedAmount?: number
}

const buyAvailableChildAccounts = computed((): ChildAccountInfo[] => {
  return buyChildAccounts.value.map(acc => ({
    id: acc.id,
    accountName: acc.accountName,
    balance: acc.balance || 0,
    isFixedAmount: (acc as any).isFixedAmount || false,
    fixedAmount: (acc as any).fixedAmount || null
  }))
})

const buyTotalAllocatedAmount = computed(() => {
  return buyFundingLines.value
    .filter((fl) => fl.amount != null)
    .reduce((sum, fl) => sum + (fl.amount || 0), 0)
})

const buyAllocationError = computed(() => {
  if (form.value.amount == null) return null
  const diff = Math.abs(buyTotalAllocatedAmount.value - form.value.amount)
  if (diff > 0.01) {
    return `已分配金额 ${buyTotalAllocatedAmount.value.toFixed(2)} 元，与总金额 ${form.value.amount.toFixed(2)} 元不一致（差额：${diff.toFixed(2)} 元）`
  }
  return null
})

// 组合支付总金额
const expenseTotalAllocatedAmount = computed(() => {
  return expenseFundingLines.value
    .filter((fl) => fl.amount != null)
    .reduce((sum, fl) => sum + (fl.amount || 0), 0)
})

// 组合支付分配错误提示
const expenseAllocationError = computed(() => {
  if (!useComboPayment.value || form.value.amount == null) return null
  const diff = Math.abs(expenseTotalAllocatedAmount.value - form.value.amount)
  if (diff > 0.01) {
    return `已分配金额 ${expenseTotalAllocatedAmount.value.toFixed(2)} 元，与总金额 ${form.value.amount.toFixed(2)} 元不一致（差额：${diff.toFixed(2)} 元）`
  }
  return null
})

// 组合支付 - 还需分配的金额
const expenseRemainingAmount = computed(() => {
  if (!form.value.amount) return 0
  return Math.max(0, form.value.amount - expenseTotalAllocatedAmount.value)
})

// 卖出时的账户相关计算
const selectedSellAccount = computed(() => {
  if (!form.value.accountId || (selectedType.value !== 'SELL' && selectedType.value !== 'REDEMPTION')) return null
  const account = accountStore.accounts.find((a) => a.id === form.value.accountId)
  return (account as Account & { linkedProductId?: number }) || null
})

const sellChildAccounts = computed(() => {
  if (!form.value.accountId || selectedType.value !== 'SELL' && selectedType.value !== 'REDEMPTION') return []
  // 只返回启用的子账户
  return accountStore.accounts.filter((a) => a.parentAccountId === form.value.accountId && a.isActive !== false)
})

// 产品关联账户的子账户（用于赎回来源选择）
// 优先使用产品持仓明细中的账户列表
interface ProductLinkedChildAccount {
  accountId: number
  accountName: string
  shares: number
  marketValue: number
}

const productLinkedChildAccounts = computed((): ProductLinkedChildAccount[] => {
  if (productAccountHoldings.value.length > 0) {
    // 有产品持仓明细，返回有持仓的账户
    return productAccountHoldings.value.map((ah: AccountHoldingInfo) => ({
      accountId: ah.accountId,
      accountName: ah.parentAccountName ? `${ah.parentAccountName}-${ah.accountName}` : ah.accountName,
      shares: ah.shares,
      marketValue: ah.marketValue
    }))
  }
  return []
})

const sellAvailableChildAccounts = computed(() => {
  // 如果有产品持仓明细，返回有持仓的账户
  if (productLinkedChildAccounts.value.length > 0) {
    return productLinkedChildAccounts.value.map((plc: ProductLinkedChildAccount) => {
      const account = accountStore.accounts.find(a => a.id === plc.accountId)
      return {
        id: plc.accountId,
        accountName: account?.accountName || plc.accountName,
        balance: account?.balance || 0,
        availableShares: plc.shares,
        isFixedAmount: (account as any)?.isFixedAmount || false,
        fixedAmount: (account as any)?.fixedAmount || null
      }
    })
  }
  // 返回子账户信息，包含固定金额字段
  return sellChildAccounts.value.map(acc => ({
    id: acc.id,
    accountName: acc.accountName,
    balance: acc.balance || 0,
    availableShares: null as number | null,
    isFixedAmount: (acc as any).isFixedAmount || false,
    fixedAmount: (acc as any).fixedAmount || null
  }))
})

const sellTotalAllocatedShares = computed(() => {
  return sellFundingLines.value
    .filter((fl) => fl.shares != null)
    .reduce((sum, fl) => sum + (fl.shares || 0), 0)
})

const sellAllocationError = computed(() => {
  if (form.value.shares == null) return null
  const diff = Math.abs(sellTotalAllocatedShares.value - form.value.shares)
  if (diff > 0.0001) {
    return `已分配份额 ${sellTotalAllocatedShares.value.toFixed(4)}，与总份额 ${form.value.shares.toFixed(4)} 不一致（差额：${diff.toFixed(4)}）`
  }
  return null
})

// 判断是否为信贷账户类型
function isCreditAccountType(accountType: string): boolean {
  return accountType === 'CREDIT_CARD' || 
         accountType === 'HUABEI' || 
         accountType === 'BAITIAO' ||
         accountType === 'LOAN'
}

// 计算父账户的余额和贷款额
function calculateParentBalances(account: Account | null): { balance: number; credit: number } {
  if (!account) return { balance: 0, credit: 0 }
  // 如果有子账户，分别计算正常余额和贷款额
  if (account.children && account.children.length > 0) {
    let balance = 0
    let credit = 0
    account.children.forEach((child: Account) => {
      if (isCreditAccountType(child.accountType)) {
        credit += (child.balance || 0)
      } else {
        balance += (child.balance || 0)
      }
    })
    return { balance, credit }
  }
  // 如果没有子账户，判断账户类型
  if (isCreditAccountType(account.accountType)) {
    return { balance: 0, credit: account.balance || 0 }
  }
  return { balance: account.balance || 0, credit: 0 }
}

// 余额计算属性
// 父账户余额和贷款额
const selectedParentBalances = computed(() => {
  if (!form.value.parentAccountId) return { balance: 0, credit: 0 }
  const account = findAccountInTree(accountStore.accountTree || null, form.value.parentAccountId)
  return calculateParentBalances(account)
})

const selectedParentAccountBalance = computed(() => selectedParentBalances.value.balance)
const selectedParentAccountCredit = computed(() => selectedParentBalances.value.credit)

// 子账户余额：从账户树中获取
const selectedChildAccountBalance = computed(() => {
  if (!form.value.accountId) return 0
  const account = findAccountInTree(accountStore.accountTree || null, form.value.accountId)
  if (!account) return 0
  return account.balance || 0
})

// 子账户是否为信贷账户
const isSelectedChildCreditAccount = computed(() => {
  if (!form.value.accountId) return false
  const account = findAccountInTree(accountStore.accountTree || null, form.value.accountId)
  if (!account) return false
  return isCreditAccountType(account.accountType)
})

// 转出父账户余额和贷款额
const selectedFromParentBalances = computed(() => {
  if (!form.value.fromParentAccountId) return { balance: 0, credit: 0 }
  const account = findAccountInTree(accountStore.accountTree, form.value.fromParentAccountId)
  return calculateParentBalances(account)
})

const selectedFromParentAccountBalance = computed(() => selectedFromParentBalances.value.balance)
const selectedFromParentAccountCredit = computed(() => selectedFromParentBalances.value.credit)

// 转出子账户余额
const selectedFromChildAccountBalance = computed(() => {
  if (!form.value.fromAccountId) return 0
  const account = findAccountInTree(accountStore.accountTree, form.value.fromAccountId)
  if (!account) return 0
  return account.balance || 0
})

// 转出子账户是否为信贷账户
const isSelectedFromChildCreditAccount = computed(() => {
  if (!form.value.fromAccountId) return false
  const account = findAccountInTree(accountStore.accountTree, form.value.fromAccountId)
  if (!account) return false
  return isCreditAccountType(account.accountType)
})

// 转入父账户余额和贷款额
const selectedToParentBalances = computed(() => {
  if (!form.value.toParentAccountId) return { balance: 0, credit: 0 }
  const account = findAccountInTree(accountStore.accountTree, form.value.toParentAccountId)
  return calculateParentBalances(account)
})

const selectedToParentAccountBalance = computed(() => selectedToParentBalances.value.balance)
const selectedToParentAccountCredit = computed(() => selectedToParentBalances.value.credit)

// 转入子账户余额
const selectedToChildAccountBalance = computed(() => {
  if (!form.value.toAccountId) return 0
  const account = findAccountInTree(accountStore.accountTree, form.value.toAccountId)
  if (!account) return 0
  return account.balance || 0
})

// 转入子账户是否为信贷账户
const isSelectedToChildCreditAccount = computed(() => {
  if (!form.value.toAccountId) return false
  const account = findAccountInTree(accountStore.accountTree, form.value.toAccountId)
  if (!account) return false
  return isCreditAccountType(account.accountType)
})

// 信贷账户余额（用于还款）
const selectedCreditAccountBalance = computed(() => {
  if (!form.value.creditAccountId) return 0
  const account = findAccountInTree(accountStore.accountTree, form.value.creditAccountId)
  if (!account) return 0
  return account.balance || 0
})

const filteredProducts = computed(() => {
  if (!form.value.channel) {
    return []
  }
  return products.value.filter((p) => p.channel === form.value.channel)
})

const categories = computed(() => {
  if (selectedType.value === 'EXPENSE') return expenseCategories
  if (selectedType.value === 'INCOME') return incomeCategories
  return []
})

const categoryOptions = computed(() => {
  if (categories.value.length === 0) return []
  const groups = getCategoryGroups(categories.value)
  return groups.map((group: any) => {
    if (group.categories.length === 1 && !group.categories[0].categoryL2) {
      return {
        value: group.categories[0].id,
        label: group.categoryL1,
      }
    }
    return {
      value: group.categoryL1,
      label: group.categoryL1,
      children: group.categories.map((cat: any) => ({
        value: cat.id,
        label: cat.categoryL2 || cat.categoryL1,
      })),
    }
  })
})

const cascaderProps = {
  value: 'value',
  label: 'label',
  children: 'children',
  emitPath: true,
  checkStrictly: false,
  expandTrigger: 'hover' as const,
}

// 判断是否是"待分配"账户
function isUnallocatedAccount(account: Account): boolean {
  const name = account.accountName || ''
  const code = (account as any).accountCode || ''
  return name.includes('待分配') || code.includes('待分配')
}

// 获取 MMF 账户的净值（从持仓缓存中获取）
function getMmfNavFromCache(linkedProductId: number): number {
  const holding = allHoldingsCache.value.get(linkedProductId)
  if (holding && (holding as any).currentPrice) {
    return (holding as any).currentPrice
  }
  return 1 // 默认净值为1
}

// 计算 MMF 子账户的实际金额
function calculateMmfChildAmount(parent: Account, child: Account): number {
  if (!parent.linkedProductId || !parent.initialShares) {
    return child.balance || 0
  }
  
  const nav = getMmfNavFromCache(parent.linkedProductId)
  
  // 固定金额账户
  if (child.isFixedAmount && child.fixedAmount) {
    return child.fixedAmount
  }
  
  // 有 balance 的普通账户
  if (child.balance && child.balance > 0) {
    return child.balance
  }
  
  // "待分配"账户：计算未分配的金额
  if (isUnallocatedAccount(child)) {
    let allocatedShares = 0
    for (const c of parent.children || []) {
      if (isUnallocatedAccount(c)) continue
      if (c.isFixedAmount && c.fixedAmount) {
        allocatedShares += c.fixedAmount / nav
      } else if (c.balance && c.balance > 0) {
        allocatedShares += c.balance / nav
      }
    }
    const unallocatedShares = Math.max(0, parent.initialShares - allocatedShares)
    return unallocatedShares * nav
  }
  
  return 0
}

// 账户级联选项（父账户 -> 子账户）
const accountCascaderOptions = computed(() => {
  const options: any[] = []
  
  // 安全检查：确保 accountTree 存在且是数组
  if (!accountStore.accountTree || !Array.isArray(accountStore.accountTree)) {
    return options
  }
  
  function buildOptions(accounts: Account[]) {
    accounts.forEach(acc => {
      if (acc.children && acc.children.length > 0 && acc.accountKind === 'REAL') {
        // 对于 MMF 账户，计算总金额
        let parentTotalBalance = 0
        const isMmf = acc.accountType === 'MMF' && acc.linkedProductId && acc.initialShares
        
        if (isMmf) {
          const nav = getMmfNavFromCache(acc.linkedProductId!)
          parentTotalBalance = acc.initialShares! * nav
        } else {
          const parentBalances = calculateParentBalances(acc)
          parentTotalBalance = parentBalances.balance
        }
        
        const parentBalances = calculateParentBalances(acc)
        const balanceText = []
        if (isMmf) {
          balanceText.push(formatCurrency(parentTotalBalance))
        } else {
          if (parentBalances.balance > 0) balanceText.push(formatCurrency(parentBalances.balance))
          if (parentBalances.credit > 0) balanceText.push(`欠${formatCurrency(parentBalances.credit)}`)
        }
        
        options.push({
          value: acc.id,
          label: acc.accountName,
          balance: isMmf ? parentTotalBalance : parentBalances.balance,
          credit: parentBalances.credit,
          balanceText: balanceText.join(' '),
          children: acc.children.filter(child => child.isActive).map(child => {
            const isCredit = isCreditAccountType(child.accountType)
            
            // 对于 MMF 账户的子账户，计算实际金额
            let childAmount = child.balance || 0
            if (isMmf) {
              childAmount = calculateMmfChildAmount(acc, child)
            }
            
            return {
              value: child.id,
              label: child.accountName,
              balance: childAmount,
              isCredit,
              balanceText: `${isCredit ? '欠' : ''}${formatCurrency(childAmount)}`,
              fundUsage: child.fundUsage,
            }
          })
        })
      }
    })
  }
  
  buildOptions(accountStore.accountTree)
  return options
})

// 账户级联选择器配置
const accountCascaderProps = {
  value: 'value',
  label: 'label',
  children: 'children',
  emitPath: true,
  checkStrictly: false,
  expandTrigger: 'hover' as const,
}

// 获取账户选中值（用于cascader的v-model）
const selectedAccount = computed({
  get: () => {
    if (form.value.parentAccountId && form.value.accountId) {
      return [form.value.parentAccountId, form.value.accountId]
    }
    return []
  },
  set: (val: number[]) => {
    if (val && val.length === 2) {
      form.value.parentAccountId = val[0]
      form.value.accountId = val[1]
    } else {
      form.value.parentAccountId = undefined
      form.value.accountId = undefined
    }
  }
})

// 转出账户选中值
const selectedFromAccount = computed({
  get: () => {
    if (form.value.fromParentAccountId && form.value.fromAccountId) {
      return [form.value.fromParentAccountId, form.value.fromAccountId]
    }
    return []
  },
  set: (val: number[]) => {
    if (val && val.length === 2) {
      form.value.fromParentAccountId = val[0]
      form.value.fromAccountId = val[1]
    } else {
      form.value.fromParentAccountId = undefined
      form.value.fromAccountId = undefined
    }
  }
})

// 转入账户选中值
const selectedToAccount = computed({
  get: () => {
    if (form.value.toParentAccountId && form.value.toAccountId) {
      return [form.value.toParentAccountId, form.value.toAccountId]
    }
    return []
  },
  set: (val: number[]) => {
    if (val && val.length === 2) {
      form.value.toParentAccountId = val[0]
      form.value.toAccountId = val[1]
    } else {
      form.value.toParentAccountId = undefined
      form.value.toAccountId = undefined
    }
  }
})

// 获取选中账户的余额显示
function getSelectedAccountBalance(parentId: number | undefined, childId: number | undefined) {
  if (!childId) return { text: '', isCredit: false, balance: 0 }
  const account = findAccountInTree(accountStore.accountTree || null, childId)
  if (!account) return { text: '', isCredit: false, balance: 0 }
  
  const isCredit = isCreditAccountType(account.accountType)

  // 对于 MMF 子账户，需要计算实际金额
  let actualBalance = account.balance || 0
  if (parentId) {
    const parent = findAccountInTree(accountStore.accountTree || null, parentId)
    if (parent && parent.accountType === 'MMF' && parent.linkedProductId && parent.initialShares) {
      actualBalance = calculateMmfChildAmount(parent, account)
    }
  }
  
  return {
    text: `${isCredit ? '欠' : ''}${formatCurrency(actualBalance)}`,
    isCredit,
    balance: actualBalance
  }
}

onMounted(() => {
  productStore.fetchProducts()
  accountStore.fetchAccounts()
})

watch(visible, async (val) => {
  if (val) {
    // 打开对话框时，刷新账户和产品数据，并预加载持仓数据
    await Promise.all([
      accountStore.fetchAccounts(),
      productStore.fetchProducts(),
      preloadAllHoldings()
    ])
    
    // 编辑模式：回填数据
    if (props.editingTxn) {
      // 先重置表单，确保没有缓存数据
      const now = new Date()
      const pad = (n: number) => n.toString().padStart(2, '0')
      const localNow = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
      form.value = {
        occurredAt: localNow,
        category: [],
        parentAccountId: undefined,
        accountId: undefined,
        creditAccountId: undefined,
        fromParentAccountId: undefined,
        fromAccountId: undefined,
        toParentAccountId: undefined,
        toAccountId: undefined,
        productId: undefined,
        orderType: 'BUY',
        amount: undefined,
        shares: undefined,
        fee: 0,
        requestedAt: localNow,
        confirmDate: undefined,
        navDate: undefined,
        nav: undefined,
        transferDate: undefined,
        transferOutPrice: 0,
        transferInPrice: 0,
        fromChannel: 'OTC',
        toChannel: 'EXCHANGE',
        channel: undefined,
        relatedTxnId: undefined,
        isReimbursable: false,
        repoDays: 1,
        repoRate: undefined,
        note: '',
      }
      buyFundingLines.value = []
      sellFundingLines.value = []
      expenseFundingLines.value = []
      useComboPayment.value = false
      transferType.value = 'AMOUNT'
      adjustType.value = 'BALANCE'
      shareTransfer.value = {
        parentAccountId: undefined,
        fromAccountId: undefined,
        toAccountId: undefined,
        shares: undefined,
        note: ''
      }
      shareTransferAccountShares.value.clear()
      
      // 然后回填数据
      selectedType.value = props.editingTxn.txnType
      step.value = 2  // 直接进入第二步
      
      // 回填基本信息（直接使用后端时间字符串，避免时区偏移）
      if (props.editingTxn.requestedAt) {
        form.value.occurredAt = props.editingTxn.requestedAt
      }
      form.value.note = props.editingTxn.note || ''
      form.value.isReimbursable = props.editingTxn.isReimbursable || false
      
      // 回填分类（级联选择器需要完整的路径）
      if (props.editingTxn.categoryId) {
        const categories = (props.editingTxn.txnType === 'EXPENSE' ? expenseCategories : incomeCategories)
        const category = findCategoryById(categories, props.editingTxn.categoryId)
        if (category) {
          // 级联选择器的路径格式：
          // - 如果有二级分类：[categoryL1, categoryId]
          // - 如果只有一级分类：直接使用 [categoryId]（但需要找到对应的group）
          if (category.categoryL2) {
            // 有二级分类，路径是 [categoryL1, categoryId]
            form.value.category = [category.categoryL1, props.editingTxn.categoryId]
          } else {
            // 只有一级分类，需要找到对应的group
            const groups = getCategoryGroups(categories)
            const group = groups.find((g: any) => g.categoryL1 === category.categoryL1)
            if (group && group.categories.length === 1) {
              // 如果group只有一个分类且没有二级分类，路径就是 [categoryId]
              form.value.category = [props.editingTxn.categoryId]
            } else {
              // 如果有多个分类，路径是 [categoryL1, categoryId]
              form.value.category = [category.categoryL1, props.editingTxn.categoryId]
            }
          }
        }
      }
      
      // 对于支出和收入，从postings中提取账户和金额信息
      if ((props.editingTxn.txnType === 'EXPENSE' || props.editingTxn.txnType === 'INCOME') && props.editingTxn.postings) {
        const postings = props.editingTxn.postings
        
        // 支出：找到CREDIT的CASH账户（付款账户）和金额
        if (props.editingTxn.txnType === 'EXPENSE') {
          const cashCreditPosting = postings.find(p => p.postingType === 'CREDIT' && p.accountType === 'CASH')
          if (cashCreditPosting) {
            form.value.amount = Number(cashCreditPosting.amount)
            const account = findAccountInTree(accountStore.accountTree || null, cashCreditPosting.accountId)
            if (account) {
              form.value.accountId = account.id
              form.value.parentAccountId = account.parentAccountId
            }
            
            // 检查是否有多个付款账户（组合支付）
            const cashCreditPostings = postings.filter(p => p.postingType === 'CREDIT' && p.accountType === 'CASH')
            if (cashCreditPostings.length > 1) {
              useComboPayment.value = true
              expenseFundingLines.value = cashCreditPostings.map(p => {
                const acc = findAccountInTree(accountStore.accountTree || null, p.accountId)
                return {
                  parentAccountId: acc?.parentAccountId,
                  accountId: p.accountId,
                  amount: Number(p.amount)
                }
              })
            }
          }
        }
        
        // 收入：找到DEBIT的CASH账户（收款账户）和金额
        if (props.editingTxn.txnType === 'INCOME') {
          const cashDebitPosting = postings.find(p => p.postingType === 'DEBIT' && p.accountType === 'CASH')
          if (cashDebitPosting) {
            form.value.amount = Number(cashDebitPosting.amount)
            const account = findAccountInTree(accountStore.accountTree || null, cashDebitPosting.accountId)
            if (account) {
              form.value.accountId = account.id
              form.value.parentAccountId = account.parentAccountId
            }
          }
        }
      }
      
      // 转账：从postings中提取转出和转入账户信息
      if ((props.editingTxn.txnType === 'TRANSFER_OUT' || props.editingTxn.txnType === 'TRANSFER_IN') && props.editingTxn.postings) {
        const postings = props.editingTxn.postings
        transferType.value = 'AMOUNT'  // 默认金额转账模式
        
        // 找到 CREDIT（转出）的账户
        const creditPosting = postings.find(p => p.postingType === 'CREDIT')
        if (creditPosting) {
          form.value.amount = Number(creditPosting.amount)
          const fromAccount = findAccountInTree(accountStore.accountTree || null, creditPosting.accountId)
          if (fromAccount) {
            form.value.fromAccountId = fromAccount.id
            form.value.fromParentAccountId = fromAccount.parentAccountId
          }
        }
        
        // 找到 DEBIT（转入）的账户
        const debitPosting = postings.find(p => p.postingType === 'DEBIT')
        if (debitPosting) {
          const toAccount = findAccountInTree(accountStore.accountTree || null, debitPosting.accountId)
          if (toAccount) {
            form.value.toAccountId = toAccount.id
            form.value.toParentAccountId = toAccount.parentAccountId
          }
        }

        // 根据原交易账户信息，判断备注是否为系统自动生成的"转账: A → B"
        const fromParentAccount = form.value.fromParentAccountId
          ? findAccountInTree(accountStore.accountTree || null, form.value.fromParentAccountId)
          : null
        const fromLeafAccount = form.value.fromAccountId
          ? findAccountInTree(accountStore.accountTree || null, form.value.fromAccountId)
          : null
        const toParentAccount = form.value.toParentAccountId
          ? findAccountInTree(accountStore.accountTree || null, form.value.toParentAccountId)
          : null
        const toLeafAccount = form.value.toAccountId
          ? findAccountInTree(accountStore.accountTree || null, form.value.toAccountId)
          : null

        const fromParentName = fromParentAccount?.accountName || ''
        const fromName = fromLeafAccount?.accountName || '账户'
        const toParentName = toParentAccount?.accountName || ''
        const toName = toLeafAccount?.accountName || '账户'
        const fromFullName = fromParentName ? `${fromParentName}-${fromName}` : fromName
        const toFullName = toParentName ? `${toParentName}-${toName}` : toName
        const generatedAutoNote = `转账: ${fromFullName} → ${toFullName}`

        // 如果现有备注与自动生成的完全一致，认为是系统备注，编辑时清空，方便根据新账户重新生成
        if (props.editingTxn.note && props.editingTxn.note === generatedAutoNote) {
          form.value.note = ''
        } else {
          // 否则认为是用户自定义备注，保留原备注供手动修改
          form.value.note = props.editingTxn.note || ''
        }
      }

      // 买入/申购、卖出/赎回：回填产品、时间、净值、金额、份额、手续费、资金来源
      if ((props.editingTxn.txnType === 'BUY' || props.editingTxn.txnType === 'SUBSCRIPTION' || props.editingTxn.txnType === 'SELL' || props.editingTxn.txnType === 'REDEMPTION') && props.editingTxn.postings) {
        const postings = props.editingTxn.postings
        if (props.editingTxn.productId) {
          form.value.productId = props.editingTxn.productId
          const product = productStore.products.find(p => p.id === props.editingTxn!.productId)
          if (product && (product.channel === 'EXCHANGE' || product.channel === 'OTC')) {
            form.value.channel = product.channel
          }
          if (props.editingTxn.txnType === 'BUY' || props.editingTxn.txnType === 'SUBSCRIPTION') {
            form.value.orderType = product?.channel === 'OTC' ? 'SUBSCRIPTION' : 'BUY'
          } else {
            form.value.orderType = product?.channel === 'OTC' ? 'REDEMPTION' : 'SELL'
          }
        }
        if (props.editingTxn.requestedAt) {
          form.value.requestedAt = props.editingTxn.requestedAt
        }
        if (props.editingTxn.confirmDate) {
          form.value.confirmDate = String(props.editingTxn.confirmDate).slice(0, 10)
        }
        if (props.editingTxn.navDate) {
          form.value.navDate = String(props.editingTxn.navDate).slice(0, 10)
        }
        // 从分录取：FEE、POSITION(份额/净值)、CASH CREDIT(金额/资金来源)
        const feePosting = postings.find(p => p.accountType === 'FEE')
        if (feePosting) {
          form.value.fee = Number(feePosting.amount)
        }
        const positionPosting = postings.find(p => p.accountType === 'POSITION')
        if (positionPosting) {
          if (positionPosting.shares != null) {
            form.value.shares = Number(positionPosting.shares)
          }
          if (positionPosting.amount != null && positionPosting.shares != null && Number(positionPosting.shares) > 0) {
            form.value.nav = Number((Number(positionPosting.amount) / Number(positionPosting.shares)).toFixed(6))
          }
        }
        const cashCreditPostings = postings.filter(p => p.accountType === 'CASH' && p.postingType === 'CREDIT')
        if (cashCreditPostings.length > 0) {
          const totalAmount = cashCreditPostings.reduce((sum, p) => sum + Number(p.amount), 0)
          if (props.editingTxn.txnType === 'BUY' || props.editingTxn.txnType === 'SUBSCRIPTION') {
            form.value.amount = totalAmount
            if (cashCreditPostings.length === 1) {
              const acc = findAccountInTree(accountStore.accountTree || null, cashCreditPostings[0].accountId)
              if (acc) {
                form.value.parentAccountId = acc.parentAccountId
                form.value.accountId = acc.id
              }
            } else {
              buyFundingLines.value = cashCreditPostings.map(p => ({
                accountId: p.accountId,
                amount: Number(p.amount)
              }))
            }
          } else if (props.editingTxn.txnType === 'SELL' || props.editingTxn.txnType === 'REDEMPTION') {
            // 卖出/赎回：CASH CREDIT 是出金来源账户，优先按该分录回显来源分配
            const positionCreditPostings = postings.filter(
              p => p.accountType === 'POSITION' && p.postingType === 'CREDIT'
            )
            const totalCreditAmount = cashCreditPostings.reduce((sum, p) => sum + Number(p.amount), 0)
            const totalPositionShares = positionCreditPostings.reduce((sum, p) => sum + Number(p.shares || 0), 0)

            sellFundingLines.value = cashCreditPostings.map(p => {
              const postingShares = Number(p.shares || 0)
              // CASH 分录通常没有 shares，这里按金额占比回推份额，保证编辑时可提交
              const inferredShares =
                postingShares > 0
                  ? postingShares
                  : (totalPositionShares > 0 && totalCreditAmount > 0
                      ? Number(((Number(p.amount) / totalCreditAmount) * totalPositionShares).toFixed(6))
                      : undefined)
              return {
                accountId: p.accountId,
                shares: inferredShares,
              }
            })
          }
        }
        const cashDebitPostings = postings.filter(p => p.accountType === 'CASH' && p.postingType === 'DEBIT')
        if ((props.editingTxn.txnType === 'SELL' || props.editingTxn.txnType === 'REDEMPTION') && cashDebitPostings.length > 0) {
          // 卖出/赎回：CASH DEBIT 是到账账户与到账金额
          form.value.amount = cashDebitPostings.reduce((sum, p) => sum + Number(p.amount), 0)
          if (cashDebitPostings.length === 1) {
            const acc = findAccountInTree(accountStore.accountTree || null, cashDebitPostings[0].accountId)
            if (acc) {
              form.value.parentAccountId = acc.parentAccountId
              form.value.accountId = acc.id
            }
          }
        }
      }
    } else {
      // 新建模式：重置表单
      step.value = 1
      selectedType.value = ''
    // 默认使用本地时间，避免少8小时的问题
    const now = new Date()
    const pad = (n: number) => n.toString().padStart(2, '0')
    const localNow = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`

    form.value = {
      occurredAt: localNow,
        category: [],
        parentAccountId: undefined,
        accountId: undefined,
        creditAccountId: undefined,
        fromParentAccountId: undefined,
        fromAccountId: undefined,
        toParentAccountId: undefined,
        toAccountId: undefined,
        productId: undefined,
        orderType: 'BUY',
        amount: undefined,
        shares: undefined,
        fee: 0,
      requestedAt: localNow,
        confirmDate: undefined,
        navDate: undefined,
        nav: undefined,
        transferDate: undefined,
        transferOutPrice: 0,
        transferInPrice: 0,
        fromChannel: 'OTC',
        toChannel: 'EXCHANGE',
        channel: undefined,
        relatedTxnId: undefined,
        isReimbursable: false,
        repoDays: 1,
        repoRate: undefined,
        note: '',
      }
      buyFundingLines.value = []
      sellFundingLines.value = []
      expenseFundingLines.value = []
      useComboPayment.value = false
      transferType.value = 'AMOUNT'
      adjustType.value = 'BALANCE'
      shareTransfer.value = {
        parentAccountId: undefined,
        fromAccountId: undefined,
        toAccountId: undefined,
        shares: undefined,
        note: ''
      }
      shareTransferAccountShares.value.clear()
    }
  }
})

// 监听订单类型变化，清空fundingLines
watch(() => selectedType.value, () => {
  buyFundingLines.value = []
  sellFundingLines.value = []
  expenseFundingLines.value = []
  useComboPayment.value = false
  transferType.value = 'AMOUNT'
  adjustType.value = 'BALANCE'
  shareTransfer.value = {
    parentAccountId: undefined,
    fromAccountId: undefined,
    toAccountId: undefined,
    shares: undefined,
    note: ''
  }
  shareTransferAccountShares.value.clear()
})

// 监听总金额变化，重新计算组合支付的分配金额
watch(() => form.value.amount, () => {
  if (useComboPayment.value && selectedType.value === 'EXPENSE') {
    recalculateExpenseAmounts()
  }
})

function selectType(type: string) {
  selectedType.value = type
  step.value = 2
  // 重置表单
  form.value.channel = undefined
  form.value.productId = undefined
}

function handleChannelChange() {
  // 切换场内/场外时，清空产品选择
  form.value.productId = undefined
  form.value.nav = undefined
  
  // 根据场内/场外自动设置交易类型
  // 场内 → 买入/卖出，场外 → 申购/赎回
  if (selectedType.value === 'BUY' || selectedType.value === 'SUBSCRIPTION') {
    form.value.orderType = form.value.channel === 'OTC' ? 'SUBSCRIPTION' : 'BUY'
  } else if (selectedType.value === 'SELL' || selectedType.value === 'REDEMPTION') {
    form.value.orderType = form.value.channel === 'OTC' ? 'REDEMPTION' : 'SELL'
  }
}

function handleParentAccountChange() {
  form.value.accountId = undefined
}

function handleRepaymentAccountChange() {
  form.value.accountId = undefined
}

// 转账类型切换（金额转账 / 份额转移）
function handleTransferTypeChange() {
  // 切换类型时清空相关表单数据
  if (transferType.value === 'AMOUNT') {
    shareTransfer.value = {
      parentAccountId: undefined,
      fromAccountId: undefined,
      toAccountId: undefined,
      shares: undefined,
      note: ''
    }
    shareTransferAccountShares.value.clear()
  } else {
    form.value.fromParentAccountId = undefined
    form.value.fromAccountId = undefined
    form.value.toParentAccountId = undefined
    form.value.toAccountId = undefined
    form.value.amount = undefined
  }
}

// 份额转移父账户选择
async function handleShareTransferParentChange() {
  shareTransfer.value.fromAccountId = undefined
  shareTransfer.value.toAccountId = undefined
  shareTransfer.value.shares = undefined
  shareTransferAccountShares.value.clear()
  
  // 获取该父账户下所有子账户的份额
  if (shareTransfer.value.parentAccountId) {
    await loadShareTransferAccountShares()
  }
}

// 份额转移源账户选择
function handleShareTransferFromChange() {
  shareTransfer.value.shares = undefined
}

// 加载份额转移相关的子账户份额
async function loadShareTransferAccountShares() {
  const parentAccountId = shareTransfer.value.parentAccountId
  if (!parentAccountId) return
  
  const parentAccount = findAccountById(accountStore.accountTree, parentAccountId)
  if (!parentAccount || !(parentAccount as any).linkedProductId) return
  
  const productId = (parentAccount as any).linkedProductId
  const totalShares = (parentAccount as any).initialShares || 0
  
  // 获取最新净值
  let nav = 1
  try {
    const navData = await navApi.getLatestNav(productId)
    if (navData && navData.nav) {
      nav = navData.nav
    }
  } catch (e) {
    console.warn('获取净值失败', e)
  }
  
  // 计算各子账户的份额（基于余额和净值）
  const childAccounts = shareTransferChildAccounts.value
  let allocatedShares = 0
  const nonZeroAccounts: Account[] = []
  
  childAccounts.forEach(child => {
    if ((child as any).isFixedAmount && (child as any).fixedAmount) {
      // 固定金额账户：份额 = 固定金额 / 净值
      const shares = (child as any).fixedAmount / nav
      shareTransferAccountShares.value.set(child.id, shares)
      allocatedShares += shares
    } else if (child.balance && child.balance > 0) {
      // 普通账户：份额 = 余额 / 净值
      const shares = child.balance / nav
      shareTransferAccountShares.value.set(child.id, shares)
      allocatedShares += shares
      nonZeroAccounts.push(child)
    } else {
      shareTransferAccountShares.value.set(child.id, 0)
    }
  })
  
  // 处理剩余份额（分配给最后一个有余额的非固定金额账户）
  const remainingShares = totalShares - allocatedShares
  if (remainingShares > 0.0001 && nonZeroAccounts.length > 0) {
    const lastAccount = nonZeroAccounts[nonZeroAccounts.length - 1]
    const currentShares = shareTransferAccountShares.value.get(lastAccount.id) || 0
    shareTransferAccountShares.value.set(lastAccount.id, currentShares + remainingShares)
  }
}

// 获取子账户份额
function getChildAccountShares(accountId: number | undefined): number {
  if (!accountId) return 0
  return shareTransferAccountShares.value.get(accountId) || 0
}

// 获取账户名称
function getAccountName(accountId: number | undefined): string {
  if (!accountId) return ''
  const account = findAccountById(accountStore.accountTree, accountId)
  return account?.accountName || ''
}

// 数字格式化
function formatNumber(value: number, decimals: number = 2): string {
  return value.toFixed(decimals)
}

function handleFromParentAccountChange() {
  // 转出父账户改变时，清空转出子账户选择
  form.value.fromAccountId = undefined
}

function handleToParentAccountChange() {
  // 转入父账户改变时，清空转入子账户选择
  form.value.toAccountId = undefined
}

function handleBuyParentAccountChange() {
  // 买入父账户改变时，清空子账户选择和资金分配
  form.value.accountId = undefined
  buyFundingLines.value = []
}

function handleBuyAccountChange() {
  buyFundingLines.value = []
}

// 买入账户级联选择器变化处理
function handleBuyAccountCascaderChange() {
  // 清空资金分配
  buyFundingLines.value = []
}

function handleSellParentAccountChange() {
  // 卖出父账户改变时，清空子账户选择和份额分配
  form.value.accountId = undefined
  sellFundingLines.value = []
}

// 卖出到账账户级联选择器变化处理
function handleSellAccountCascaderChange() {
  // 选择到账账户时，不清空出金来源分配（sellFundingLines）
  // 因为出金来源和到账账户是两个不同的概念
}

function handleAddBuyFundingLine() {
  buyFundingLines.value.push({
    accountId: undefined,
    amount: undefined,
  })
}

function handleRemoveBuyFundingLine(index: number) {
  buyFundingLines.value.splice(index, 1)
}

// 组合支付 - 添加付款账户
function handleAddExpenseFundingLine() {
  expenseFundingLines.value.push({
    parentAccountId: undefined,
    accountId: undefined,
    amount: undefined,
  })
}

// 组合支付 - 移除付款账户
function handleRemoveExpenseFundingLine(index: number) {
  expenseFundingLines.value.splice(index, 1)
  // 删除后重新计算剩余账户的金额
  recalculateExpenseAmounts()
}

// 组合支付 - 切换开关时清空数据
function handleComboPaymentChange(enabled: boolean) {
  if (enabled) {
    // 启用组合支付时，清空单账户选择，初始化一个空的付款行
    form.value.parentAccountId = undefined
    form.value.accountId = undefined
    expenseFundingLines.value = [{
      parentAccountId: undefined,
      accountId: undefined,
      amount: undefined
    }]
  } else {
    // 禁用组合支付时，清空付款行
    expenseFundingLines.value = []
  }
}

// 获取组合支付行的账户余额
function getExpenseLineAccountBalance(line: { parentAccountId?: number, accountId?: number }) {
  if (!line.accountId) return { text: '', isCredit: false, balance: 0 }
  return getSelectedAccountBalance(line.parentAccountId, line.accountId)
}

// 获取组合支付可用的账户选项（排除已选的账户）
function getExpenseAvailableAccountOptions(currentIndex: number) {
  // 获取已选择的账户ID列表（排除当前行）
  const selectedAccountIds = expenseFundingLines.value
    .filter((_, i) => i !== currentIndex)
    .filter(fl => fl.accountId)
    .map(fl => fl.accountId)
  
  // 深拷贝并过滤已选择的账户
  return accountCascaderOptions.value.map(parent => ({
    ...parent,
    children: parent.children?.filter((child: any) => !selectedAccountIds.includes(child.value))
  })).filter(parent => parent.children && parent.children.length > 0)
}

// 组合支付 - 选择账户后自动计算金额
function handleExpenseAccountChange(index: number, val: any) {
  const line = expenseFundingLines.value[index]
  line.parentAccountId = val?.[0]
  line.accountId = val?.[1]
  
  // 重新计算所有行的金额
  recalculateExpenseAmounts()
}

// 重新计算组合支付所有行的金额
function recalculateExpenseAmounts() {
  const totalAmount = form.value.amount || 0
  if (totalAmount <= 0) {
    expenseFundingLines.value.forEach(line => {
      line.amount = 0
    })
    return
  }
  
  let remaining = totalAmount
  
  // 按顺序分配金额：每个账户用完余额后，剩余的分配给下一个账户
  for (const line of expenseFundingLines.value) {
    if (!line.accountId) {
      line.amount = 0
      continue
    }
    
    // 获取账户余额
    const balanceInfo = getSelectedAccountBalance(line.parentAccountId, line.accountId)
    const accountBalance = balanceInfo.balance || 0
    
    // 信用账户（负债）不能用于支付支出
    if (balanceInfo.isCredit) {
      line.amount = 0
      continue
    }
    
    // 当前账户使用的金额 = min(账户余额, 剩余需要的金额)
    const useAmount = Math.min(accountBalance, remaining)
    line.amount = Math.max(0, Number(useAmount.toFixed(2)))
    remaining = Math.max(0, remaining - useAmount)
  }
}

// 获取买入账户的固定金额（如果是固定金额账户）
function getBuyAccountFixedAmount(accountId: number | undefined): number | null {
  if (!accountId) return null
  const acc = buyAvailableChildAccounts.value.find(a => a.id === accountId)
  if (acc?.isFixedAmount && acc?.fixedAmount) {
    return acc.fixedAmount
  }
  return null
}

// 填充固定金额
function fillBuyFixedAmount(index: number) {
  const line = buyFundingLines.value[index]
  if (!line?.accountId) return
  
  const fixedAmount = getBuyAccountFixedAmount(line.accountId)
  if (fixedAmount) {
    line.amount = fixedAmount
  }
}

// 选择买入账户后，如果是固定金额账户，自动填充固定金额
function handleBuyFundingLineAccountChange(index: number) {
  const line = buyFundingLines.value[index]
  if (line?.accountId) {
    const fixedAmount = getBuyAccountFixedAmount(line.accountId)
    if (fixedAmount) {
      line.amount = fixedAmount
    }
  }
}

function handleAddSellFundingLine() {
  sellFundingLines.value.push({
    accountId: undefined,
    shares: undefined,
  })
}

function handleRemoveSellFundingLine(index: number) {
  sellFundingLines.value.splice(index, 1)
}

// 获取账户可用份额（从产品持仓明细）
function getAccountAvailableShares(accountId: number | undefined): number {
  if (!accountId) return 0
  const holding = productAccountHoldings.value.find((h: AccountHoldingInfo) => h.accountId === accountId)
  return holding?.shares || 0
}

// 填充账户最大可用份额（智能填充：优先使用剩余需要的份额）
function fillMaxShares(index: number) {
  const line = sellFundingLines.value[index]
  if (!line?.accountId) return
  
  const availableShares = getAccountAvailableShares(line.accountId)
  if (availableShares <= 0) return

  // 计算当前其他行已分配的份额（不包括当前行）
  const otherAllocatedShares = sellFundingLines.value
    .filter((fl, i) => i !== index && fl.shares != null)
    .reduce((sum, fl) => sum + (fl.shares || 0), 0)
  
  // 计算剩余需要分配的份额
  const totalShares = form.value.shares || 0
  const remainingShares = Math.max(0, totalShares - otherAllocatedShares)
  
  // 智能填充：如果可用份额 >= 剩余需要的份额，填入剩余份额；否则填入全部可用份额
  // 同时处理精度问题：如果差值小于0.01，认为是相等的
  const diff = Math.abs(availableShares - remainingShares)
  if (diff < 0.01 || availableShares >= remainingShares) {
    line.shares = Number(remainingShares.toFixed(4))
  } else {
    line.shares = Number(availableShares.toFixed(4))
  }
}

// 选择赎回账户后，自动处理份额填充
function handleSellFundingLineAccountChange(index: number) {
  const line = sellFundingLines.value[index]
  if (line?.accountId) {
    const availableShares = getAccountAvailableShares(line.accountId)
    
    // 计算当前其他行已分配的份额（不包括当前行）
    const otherAllocatedShares = sellFundingLines.value
      .filter((fl, i) => i !== index && fl.shares != null)
      .reduce((sum, fl) => sum + (fl.shares || 0), 0)
    
    // 计算剩余需要分配的份额
    const totalShares = form.value.shares || 0
    const remainingShares = Math.max(0, totalShares - otherAllocatedShares)
    
    // 统一按份额赎回（结算时由后端处理固定金额账户的金额分配）
    if (availableShares > 0) {
      // 智能填充：处理精度问题
      // 如果可用份额和剩余需要的份额差值小于0.01，认为是相等的，填入用户输入的份额
      const diff = Math.abs(availableShares - remainingShares)
      let sharesToFill: number
      if (diff < 0.01 || availableShares >= remainingShares) {
        // 可用份额足够（或精度误差范围内），填入剩余需要的份额
        sharesToFill = remainingShares
      } else {
        // 可用份额不足，填入全部可用份额
        sharesToFill = availableShares
      }
      line.shares = sharesToFill > 0 ? Number(sharesToFill.toFixed(4)) : Number(availableShares.toFixed(4))
    }
  }
}

// 获取卖出账户信息
function getSellAccountInfo(accountId: number | undefined) {
  if (!accountId) return null
  return sellAvailableChildAccounts.value.find(a => a.id === accountId)
}

// 监听产品变化，自动获取净值
watch(() => form.value.productId, async (newProductId) => {
  if (newProductId && (selectedType.value === 'BUY' || selectedType.value === 'SUBSCRIPTION' || selectedType.value === 'SELL' || selectedType.value === 'REDEMPTION')) {
    try {
      // 优先使用navDate，否则使用最新净值
      if (form.value.navDate) {
        const nav = await navApi.getNavByDate(newProductId, form.value.navDate)
        if (nav) {
          form.value.nav = nav.nav
        }
      } else {
        const nav = await navApi.getLatestNav(newProductId)
        if (nav) {
          form.value.nav = nav.nav
        }
      }
    } catch (error) {
      console.error('获取净值失败:', error)
    }
  }
})

// 监听净值日期变化，重新获取净值
watch(() => form.value.navDate, async (newNavDate) => {
  if (newNavDate && form.value.productId) {
    try {
      const nav = await navApi.getNavByDate(form.value.productId, newNavDate)
      if (nav) {
        const oldNav = form.value.nav
        form.value.nav = nav.nav
        // 显示净值变化提示
        if (oldNav && oldNav !== nav.nav) {
          ElNotification.info({ title: '提示', message: `净值已更新为 ${nav.nav.toFixed(6)}（日期：${newNavDate}）`, position: 'bottom-right', duration: 3000 })
        }
      } else {
        ElNotification.warning({ title: '警告', message: `未找到 ${newNavDate} 的净值数据，请手动输入`, position: 'bottom-right', duration: 3000 })
      }
    } catch (error) {
      console.error('获取净值失败:', error)
      ElNotification.warning({ title: '警告', message: '获取净值失败，请手动输入', position: 'bottom-right', duration: 3000 })
    }
  }
})

// 场内交易自动计算手续费
const isCalculatingFee = ref(false)

// 计算场内交易手续费的函数
async function calculateExchangeFee() {
  // 只有场内交易才自动计算手续费
  if (form.value.channel !== 'EXCHANGE' || !form.value.productId || !form.value.shares || !form.value.nav) {
    return
  }
  
  // 只对买入/卖出类型计算
  if (selectedType.value !== 'BUY' && selectedType.value !== 'SUBSCRIPTION' && 
      selectedType.value !== 'SELL' && selectedType.value !== 'REDEMPTION') {
    return
  }
  
  if (isCalculatingFee.value) return
  
  try {
    isCalculatingFee.value = true
    const amount = form.value.shares * form.value.nav
    const orderType = (selectedType.value === 'BUY' || selectedType.value === 'SUBSCRIPTION') ? 'BUY' : 'SELL'
    
    // 尝试从选中的账户找到券商账户
    let brokerAccountId: number | undefined
    if (form.value.accountId) {
      // 查找父账户是否为券商账户
      const account = accountStore.accounts.find(a => a.id === form.value.accountId)
      if (account?.parentAccountId) {
        const parent = accountStore.accounts.find(a => a.id === account.parentAccountId)
        if (parent?.accountType === 'BROKER') {
          brokerAccountId = parent.id
        }
      } else if (account?.accountType === 'BROKER') {
        brokerAccountId = account.id
      }
    }
    
    // 如果没找到，尝试在账户列表中找华宝证券
    if (!brokerAccountId) {
      const huabao = accountStore.accounts.find(a => 
        a.accountType === 'BROKER' && (a.accountName?.includes('华宝') || a.accountCode?.includes('huabao'))
      )
      brokerAccountId = huabao?.id
    }
    
    console.log('计算手续费参数:', { productId: form.value.productId, accountId: brokerAccountId, orderType, amount })
    
    const result = await orderApi.calculateFee({
      productId: form.value.productId,
      accountId: brokerAccountId,
      orderType,
      amount
    })
    
    console.log('手续费计算结果:', result)
    
    // 自动填充手续费
    form.value.fee = result.fee
  } catch (error) {
    console.error('计算手续费失败:', error)
    // 计算失败时不影响用户操作，手续费保持原值或默认值
  } finally {
    isCalculatingFee.value = false
  }
}

// 计算场外申购手续费的函数
function calculateOTCFee() {
  // 只有场外申购才自动计算手续费
  if (form.value.channel !== 'OTC' || !form.value.productId || !form.value.amount) {
    return
  }
  
  // 只对申购类型计算
  if (selectedType.value !== 'BUY' && selectedType.value !== 'SUBSCRIPTION') {
    return
  }
  
  // 获取产品的买入费率
  const product = productStore.products.find(p => p.id === form.value.productId)
  if (!product || product.buyFeeRate == null) {
    return
  }
  
  // 计算手续费：金额 × 买入费率（费率是百分比，如 0.1 表示 0.1%）
  const fee = form.value.amount * product.buyFeeRate / 100
  form.value.fee = Math.round(fee * 100) / 100  // 保留2位小数
}

// 计算场外赎回手续费的函数（根据产品费率分段和赎回金额）
async function calculateOTCRedemptionFee() {
  // 只有场外赎回才自动计算手续费
  if (form.value.channel !== 'OTC' || !form.value.productId || !form.value.shares) {
    return
  }
  
  // 只对赎回类型计算
  if (selectedType.value !== 'SELL' && selectedType.value !== 'REDEMPTION') {
    return
  }
  
  try {
    // 获取净值来计算金额
    let nav = form.value.nav
    if (!nav && form.value.navDate) {
      const navData = await navApi.getNavByDate(form.value.productId, form.value.navDate)
      nav = navData?.nav
    }
    if (!nav) {
      const navData = await navApi.getLatestNav(form.value.productId)
      nav = navData?.nav
    }
    if (!nav || nav <= 0) return
    
    const amount = form.value.shares * nav
    
    // 获取产品的卖出费率分段
    const tiers = await productApi.getSellFeeTiers(form.value.productId)
    if (!tiers || tiers.length === 0) {
      form.value.fee = 0
      return
    }
    
    // 使用最高档费率（持有0天），用户可手动修改
    // 费率分段按 minDays 升序排列，第一个通常是持有天数最少的（费率最高）
    const firstTier = tiers[0]
    const feeRate = firstTier.sellFeeRate || 0
    const fee = amount * feeRate
    form.value.fee = Math.round(fee * 100) / 100  // 保留2位小数
  } catch (error) {
    console.error('计算场外赎回手续费失败:', error)
    // 计算失败时默认为0
    form.value.fee = 0
  }
}

// 监听数量和价格变化，自动计算场内手续费
watch(
  () => [form.value.channel, form.value.productId, form.value.shares, form.value.nav],
  () => {
    calculateExchangeFee()
  },
  { immediate: false }
)

// 监听场外申购金额变化，自动计算手续费
watch(
  () => [form.value.channel, form.value.productId, form.value.amount],
  () => {
    calculateOTCFee()
  },
  { immediate: false }
)

// 监听场外赎回份额和净值变化，自动计算手续费
watch(
  () => [form.value.channel, form.value.productId, form.value.shares, form.value.nav, form.value.navDate],
  () => {
    if (selectedType.value === 'SELL' || selectedType.value === 'REDEMPTION') {
      calculateOTCRedemptionFee()
    }
  },
  { immediate: false }
)

// 递归查找账户树中的账户
function findAccountInTree(accounts: Account[] | null | undefined, accountId: number): Account | null {
  if (!accounts || !Array.isArray(accounts)) {
    return null
  }
  for (const acc of accounts) {
    if (acc.id === accountId) {
      return acc
    }
    if (acc.children && acc.children.length > 0) {
      const found = findAccountInTree(acc.children, accountId)
      if (found) return found
    }
  }
  return null
}

// function getAccountDisplayName(acc: Account): string {
//   // 如果账户有父账户ID，递归查找父账户
//   if (acc.parentAccountId) {
//     const parent = findAccountInTree(accountStore.accountTree, acc.parentAccountId)
//     if (parent) {
//       return `${parent.accountName} / ${acc.accountName}`
//     }
//   }
//   return acc.accountName
// }
// 未使用，已注释

function handleClose() {
  visible.value = false
}

async function handleSubmit() {
  if (!selectedType.value) {
    ElNotification.error({ title: '错误', message: '请选择业务类型', position: 'bottom-right', duration: 3000 })
    return
  }

  try {
    submitting.value = true

    // 根据不同的业务类型构建postings
    const postings: any[] = []

    if (selectedType.value === 'EXPENSE') {
      // 验证必填字段
      if (!form.value.amount || !form.value.occurredAt || !form.value.category || (Array.isArray(form.value.category) && form.value.category.length === 0)) {
        ElNotification.error({
        title: '错误',
        message: '请填写完整信息（金额、时间、分类）', position: 'bottom-right', duration: 3000 })
        return
      }
      
      // 组合支付验证
      if (useComboPayment.value) {
        const validLines = expenseFundingLines.value.filter(fl => fl.accountId && fl.amount)
        if (validLines.length === 0) {
          ElNotification.error({
        title: '错误',
        message: '请至少添加一个付款账户', position: 'bottom-right', duration: 3000 })
          return
        }
        // 验证分配金额
        const totalAllocated = validLines.reduce((sum, fl) => sum + (fl.amount || 0), 0)
        if (Math.abs(totalAllocated - (form.value.amount || 0)) > 0.01) {
          ElNotification.error({
        title: '错误',
        message: `分配金额 ${totalAllocated.toFixed(2)} 元与总金额 ${form.value.amount?.toFixed(2)} 元不一致`, position: 'bottom-right', duration: 3000 })
          return
        }
        
        // 为每个付款账户生成分录
        for (const line of validLines) {
          postings.push({
            postingType: 'CREDIT',
            accountId: line.accountId,
            accountType: 'CASH',
            amount: line.amount,
            currency: 'CNY',
          })
        }
      } else {
        // 单账户验证
        if (!form.value.parentAccountId || !form.value.accountId) {
          ElNotification.error({
        title: '错误',
        message: '请选择付款账户', position: 'bottom-right', duration: 3000 })
          return
        }
        postings.push({
          postingType: 'CREDIT',
          accountId: form.value.accountId,
          accountType: 'CASH',
          amount: form.value.amount,
          currency: 'CNY',
        })
      }
      
      const categoryId = Array.isArray(form.value.category) 
        ? (form.value.category[form.value.category.length - 1] as number)
        : (form.value.category as number)
      
      // EXPENSE账户由后端自动创建（使用第一个账户作为参考）
      const refAccountId = useComboPayment.value 
        ? expenseFundingLines.value.find(fl => fl.accountId)?.accountId 
        : form.value.accountId
      postings.push({
        postingType: 'DEBIT',
        accountId: refAccountId, // 后端会替换为虚拟账户
        accountType: 'EXPENSE',
        amount: form.value.amount,
        currency: 'CNY',
      })
      
      // 如果没有备注，自动生成：分类 - 账户名
      let autoNote = form.value.note
      if (!autoNote) {
        const category = findCategoryById(expenseCategories, categoryId)
        const categoryName = category ? getCategoryDisplayName(category) : ''
        const accountName = useComboPayment.value 
          ? '组合支付'
          : (findAccountInTree(accountStore.accountTree || null, form.value.accountId!)?.accountName || '')
        autoNote = categoryName + (accountName ? ` - ${accountName}` : '')
      }
      
      // 编辑模式：更新交易
      if (props.editingTxn) {
        await ledgerApi.updateTransaction(props.editingTxn.txnId, {
          txnType: selectedType.value,
          postings,
          note: autoNote || undefined,
          requestedAt: form.value.occurredAt,
          categoryId: categoryId,
          isReimbursable: form.value.isReimbursable,
        })
        ElNotification.success({
          title: '成功',
          message: '支出记录已更新',
          position: 'bottom-right',
          duration: 3000,
        })
      } else {
        // 新建模式：创建交易
        await ledgerApi.createTransaction({
          txnType: selectedType.value,
          postings,
          note: autoNote || undefined,
          requestedAt: form.value.occurredAt,
          categoryId: categoryId,
          isReimbursable: form.value.isReimbursable,
        })
        const expenseMessage = autoNote 
          ? `支出记录已提交：${autoNote}`
          : '支出记录已提交'
        ElNotification.success({
          title: '成功',
          message: expenseMessage,
          position: 'bottom-right',
          duration: 3000,
        })
      }
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'INCOME') {
      if (!form.value.parentAccountId || !form.value.accountId || !form.value.amount || !form.value.occurredAt || !form.value.category || (Array.isArray(form.value.category) && form.value.category.length === 0)) {
        ElNotification.error({
        title: '错误',
        message: '请填写完整信息（包括父账户和子账户）', position: 'bottom-right', duration: 3000 })
        return
      }
      const categoryId = Array.isArray(form.value.category) 
        ? (form.value.category[form.value.category.length - 1] as number)
        : (form.value.category as number)
      // CASH DEBIT + INCOME CREDIT
      postings.push({
        postingType: 'DEBIT',
        accountId: form.value.accountId,
        accountType: 'CASH',
        amount: form.value.amount,
        currency: 'CNY',
      })
      postings.push({
        postingType: 'CREDIT',
        accountId: form.value.accountId, // 后端会替换为虚拟账户
        accountType: 'INCOME',
        amount: form.value.amount,
        currency: 'CNY',
      })
      
      // 如果没有备注，自动生成：分类 - 账户名
      let incomeAutoNote = form.value.note
      if (!incomeAutoNote) {
        const category = findCategoryById(incomeCategories, categoryId)
        const categoryName = category ? getCategoryDisplayName(category) : ''
        const accountName = findAccountInTree(accountStore.accountTree || null, form.value.accountId!)?.accountName || ''
        incomeAutoNote = categoryName + (accountName ? ` - ${accountName}` : '')
      }
      
      // 编辑模式：更新交易
      if (props.editingTxn) {
        await ledgerApi.updateTransaction(props.editingTxn.txnId, {
          txnType: selectedType.value,
          postings,
          note: incomeAutoNote || undefined,
          requestedAt: form.value.occurredAt,
          categoryId: categoryId,
        })
        ElNotification.success({
          title: '成功',
          message: '收入记录已更新',
          position: 'bottom-right',
          duration: 3000,
        })
      } else {
        // 新建模式：创建交易
        await ledgerApi.createTransaction({
          txnType: selectedType.value,
          postings,
          note: incomeAutoNote || undefined,
          requestedAt: form.value.occurredAt,
          categoryId: categoryId,
        })
        const notificationMessage = incomeAutoNote 
          ? `收入记录已提交：${incomeAutoNote}`
          : '收入记录已提交'
        ElNotification.success({
          title: '成功',
          message: notificationMessage,
          position: 'bottom-right',
          duration: 3000,
        })
      }
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'REPAYMENT') {
      if (!form.value.parentAccountId || !form.value.accountId || !form.value.creditAccountId || !form.value.amount || !form.value.occurredAt) {
        ElNotification.error({
        title: '错误',
        message: '请填写完整信息（包括还款账户、子账户和信贷账户）', position: 'bottom-right', duration: 3000 })
        return
      }
      // 还款：从还款账户（子账户）扣款，还到信贷账户
      // 使用 TRANSFER_OUT 类型，从还款账户转出到信贷账户
      // CASH CREDIT (还款账户扣款) + CASH DEBIT (信贷账户减少负债，信贷账户余额为负表示欠款)
      postings.push({
        postingType: 'CREDIT',
        accountId: form.value.accountId,
        accountType: 'CASH',
        amount: form.value.amount,
        currency: 'CNY',
      })
      postings.push({
        postingType: 'DEBIT',
        accountId: form.value.creditAccountId,
        accountType: 'CASH',
        amount: form.value.amount,
        currency: 'CNY',
      })
      // 生成自动备注（包含父账户名称）
      const repayParentAccount = findAccountInTree(accountStore.accountTree || null, form.value.parentAccountId!)
      const repayAccount = findAccountInTree(accountStore.accountTree || null, form.value.accountId!)
      const creditAccount = findAccountInTree(accountStore.accountTree || null, form.value.creditAccountId!)
      const repayParentName = repayParentAccount?.accountName || ''
      const repayName = repayAccount?.accountName || '账户'
      const creditName = creditAccount?.accountName || '信贷账户'
      // 还款时信贷账户本身就是叶子账户，需要找它的父账户
      const creditParentAccount = creditAccount?.parentAccountId 
        ? findAccountInTree(accountStore.accountTree || null, creditAccount.parentAccountId) 
        : null
      const creditParentName = creditParentAccount?.accountName || ''
      const repayFullName = repayParentName ? `${repayParentName}-${repayName}` : repayName
      const creditFullName = creditParentName ? `${creditParentName}-${creditName}` : creditName
      const repayNote = `还款: ${repayFullName} → ${creditFullName}`
      
      await ledgerApi.createTransaction({
        txnType: 'TRANSFER_OUT',
        postings,
        note: repayNote,
        requestedAt: form.value.occurredAt,
      })
      const repayMessage = repayNote 
        ? `还款记录已提交：${repayNote}`
        : '还款记录已提交'
      ElNotification.success({
        title: '成功',
        message: repayMessage,
        position: 'bottom-right',
        duration: 3000,
      })
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'TRANSFER_OUT' || selectedType.value === 'TRANSFER_IN') {
      // 根据转账类型处理
      if (transferType.value === 'AMOUNT') {
        // 金额转账
        if (!form.value.fromParentAccountId || !form.value.fromAccountId || 
            !form.value.toParentAccountId || !form.value.toAccountId || 
            !form.value.amount || !form.value.occurredAt) {
          ElNotification.error({
        title: '错误',
        message: '请填写完整信息（包括转出和转入的父账户、子账户）', position: 'bottom-right', duration: 3000 })
          return
        }
        
        // FROM CREDIT + TO DEBIT
        postings.push({
          postingType: 'CREDIT',
          accountId: form.value.fromAccountId,
          accountType: 'CASH',
          amount: form.value.amount,
          currency: 'CNY',
        })
        postings.push({
          postingType: 'DEBIT',
          accountId: form.value.toAccountId,
          accountType: 'CASH',
          amount: form.value.amount,
          currency: 'CNY',
        })
        // 获取账户名称用于自动备注（包含父账户名称，避免子账户重名）
        const fromParentAccount = findAccountInTree(accountStore.accountTree || null, form.value.fromParentAccountId!)
        const fromAccount = findAccountInTree(accountStore.accountTree || null, form.value.fromAccountId!)
        const toParentAccount = findAccountInTree(accountStore.accountTree || null, form.value.toParentAccountId!)
        const toAccount = findAccountInTree(accountStore.accountTree || null, form.value.toAccountId!)
        const fromParentName = fromParentAccount?.accountName || ''
        const fromName = fromAccount?.accountName || '账户'
        const toParentName = toParentAccount?.accountName || ''
        const toName = toAccount?.accountName || '账户'
        // 格式：父账户-子账户
        const fromFullName = fromParentName ? `${fromParentName}-${fromName}` : fromName
        const toFullName = toParentName ? `${toParentName}-${toName}` : toName
        const autoNote = `转账: ${fromFullName} → ${toFullName}`
        
        // 备注处理：如果用户手动输入了备注，使用用户的；否则使用自动生成的
        const finalNote = form.value.note || autoNote
        
        // 编辑模式：更新交易
        if (props.editingTxn) {
          await ledgerApi.updateTransaction(props.editingTxn.txnId, {
            txnType: selectedType.value,
            postings,
            note: finalNote,
            requestedAt: form.value.occurredAt,
          })
          ElNotification.success({
            title: '成功',
            message: '转账记录已更新',
            position: 'bottom-right',
            duration: 3000
          })
        } else {
          // 新建模式：创建交易
          await ledgerApi.createTransaction({
            txnType: selectedType.value,
            postings,
            note: finalNote,
            requestedAt: form.value.occurredAt,
          })
          const transferMessage = finalNote 
            ? `转账成功：${finalNote}`
            : '转账成功'
          ElNotification.success({ title: '成功', message: transferMessage, position: 'bottom-right', duration: 3000 })
        }
        emit('success')
        handleClose()
        return
      } else if (transferType.value === 'SHARE') {
        // 份额转移
        if (!shareTransfer.value.parentAccountId || 
            !shareTransfer.value.fromAccountId || 
            !shareTransfer.value.toAccountId ||
            !shareTransfer.value.shares || shareTransfer.value.shares <= 0 ||
            !form.value.occurredAt) {
          ElNotification.error({
        title: '错误',
        message: '请填写完整信息（父账户、源账户、目标账户、转移份额和发生时间）', position: 'bottom-right', duration: 3000 })
          return
        }
        
        const fromShares = getChildAccountShares(shareTransfer.value.fromAccountId)
        if (shareTransfer.value.shares > fromShares) {
          ElNotification.error({
        title: '错误',
        message: `转移份额不能超过源账户可用份额（${formatNumber(fromShares, 4)}份）`, position: 'bottom-right', duration: 3000 })
          return
        }
        
        // 获取净值计算金额
        const parentAccount = findAccountById(accountStore.accountTree, shareTransfer.value.parentAccountId)
        if (!parentAccount || !(parentAccount as any).linkedProductId) {
          ElNotification.error({
        title: '错误',
        message: '父账户未关联产品', position: 'bottom-right', duration: 3000 })
          return
        }
        
        const productId = (parentAccount as any).linkedProductId
        let nav = 1
        try {
          const navData = await navApi.getLatestNav(productId)
          if (navData && navData.nav) {
            nav = navData.nav
          }
        } catch (e) {
          console.warn('获取净值失败，使用默认净值1', e)
        }
        
        const amount = shareTransfer.value.shares * nav
        
        // 获取父账户名称
        const parentName = parentAccount.accountName
        const fromName = getAccountName(shareTransfer.value.fromAccountId)
        const toName = getAccountName(shareTransfer.value.toAccountId)
        
        // 生成自动备注
        const autoNote = shareTransfer.value.note || 
          `份额转移: ${parentName}-${fromName} → ${parentName}-${toName}, ${formatNumber(shareTransfer.value.shares, 4)}份`
        
        // 创建转账交易：从源账户转出，转入目标账户
        await ledgerApi.createTransaction({
          txnType: 'TRANSFER_OUT',
          requestedAt: form.value.occurredAt,
          postings: [
            {
              postingType: 'CREDIT',
              accountId: shareTransfer.value.fromAccountId,
              accountType: 'CASH',
              amount: amount,
              currency: 'CNY',
            },
            {
              postingType: 'DEBIT',
              accountId: shareTransfer.value.toAccountId,
              accountType: 'CASH',
              amount: amount,
              currency: 'CNY',
            },
          ],
          note: autoNote,
        })
        
        const shareTransferMessage = form.value.note 
          ? `份额转移成功：${form.value.note}`
          : '份额转移成功'
        ElNotification.success({ title: '成功', message: shareTransferMessage, position: 'bottom-right', duration: 3000 })
        emit('success')
        handleClose()
        return
      }
    } else if (selectedType.value === 'BUY' || selectedType.value === 'SUBSCRIPTION') {
      // 场内买入：需要数量和价格；场外申购：需要金额
      if (form.value.channel === 'EXCHANGE') {
        if (!form.value.productId || !form.value.parentAccountId || !form.value.accountId || !form.value.shares || !form.value.nav || !form.value.requestedAt) {
          ElNotification.error({
        title: '错误',
        message: '请填写完整信息（产品、数量、价格、资金来源账户）', position: 'bottom-right', duration: 3000 })
        return
        }
        // 场内买入：自动计算金额
        form.value.amount = form.value.shares * form.value.nav
      } else {
        if (!form.value.productId || !form.value.parentAccountId || !form.value.accountId || !form.value.amount || !form.value.requestedAt) {
        ElNotification.error({
        title: '错误',
        message: '请填写完整信息（包括场内/场外、资金来源父账户和子账户）', position: 'bottom-right', duration: 3000 })
        return
        }
      }
      // 确保 orderType 已自动设置
      if (!form.value.orderType) {
        form.value.orderType = form.value.channel === 'OTC' ? 'SUBSCRIPTION' : 'BUY'
      }

      // 如果选择了关联产品的账户且有子账户，必须配置fundingLines
      if (selectedBuyAccount.value?.linkedProductId && buyChildAccounts.value.length > 0) {
        if (buyFundingLines.value.length === 0) {
          ElNotification.error({
        title: '错误',
        message: '请至少添加一个子账户并分配金额', position: 'bottom-right', duration: 3000 })
          return
        }
        // 校验所有行都填写完整
        for (let i = 0; i < buyFundingLines.value.length; i++) {
          const line = buyFundingLines.value[i]
          if (!line.accountId) {
            ElNotification.error({
        title: '错误',
        message: `第 ${i + 1} 行请选择子账户`, position: 'bottom-right', duration: 3000 })
            return
          }
          if (line.amount == null || line.amount <= 0) {
            ElNotification.error({
        title: '错误',
        message: `第 ${i + 1} 行请填写买入金额`, position: 'bottom-right', duration: 3000 })
            return
          }
        }
        // 校验总金额是否匹配
        if (buyAllocationError.value) {
          ElNotification.error({
        title: '错误',
        message: buyAllocationError.value, position: 'bottom-right', duration: 3000 })
          return
        }
      }

      // 获取产品信息
      const product = await productApi.getProduct(form.value.productId)
      if (!product) {
        ElNotification.error({
        title: '错误',
        message: '产品不存在', position: 'bottom-right', duration: 3000 })
        return
      }

      // 获取价格/净值（用于计算份额）
      let nav = form.value.nav
      if (form.value.channel === 'EXCHANGE') {
        // 场内买入：使用用户输入的成交价格
        if (!nav || nav <= 0) {
          ElNotification.error({
        title: '错误',
        message: '请输入成交价格', position: 'bottom-right', duration: 3000 })
          return
        }
      } else {
        // 场外申购：获取净值
      if (!nav) {
        if (form.value.navDate) {
          const navData = await navApi.getNavByDate(form.value.productId, form.value.navDate)
          nav = navData?.nav
        } else {
          const navData = await navApi.getLatestNav(form.value.productId)
          nav = navData?.nav
        }
      }
      if (!nav || nav <= 0) {
        ElNotification.error({
        title: '错误',
        message: '无法获取产品净值，请手动输入净值日期', position: 'bottom-right', duration: 3000 })
        return
        }
      }

      // 计算金额和份额
      let totalAmount: number
      let totalShares: number
      let cost: number
      let finalFundingLines: Array<{ accountId: number; amount: number }> = []
      
      if (form.value.channel === 'EXCHANGE') {
        // 场内买入：金额 = 份额 × 价格
        totalShares = form.value.shares!
        totalAmount = totalShares * nav
        cost = totalShares * nav  // 场内买入成本 = 份额 × 价格
        
        finalFundingLines = [{
          accountId: form.value.accountId!,
          amount: totalAmount + (form.value.fee || 0),  // 支出 = 成交金额 + 手续费
        }]
      } else {
        // 场外申购：构建fundingLines（多子账户或单账户）
      if (selectedBuyAccount.value?.linkedProductId && buyChildAccounts.value.length > 0 && buyFundingLines.value.length > 0) {
        finalFundingLines = buyFundingLines.value
          .filter((fl) => fl.accountId != null && fl.amount != null)
          .map((fl) => ({
            accountId: fl.accountId!,
            amount: fl.amount!,
          }))
      } else {
        finalFundingLines = [{
          accountId: form.value.accountId!,
          amount: form.value.amount!,
        }]
        }
        totalAmount = finalFundingLines.reduce((sum, fl) => sum + fl.amount, 0)
        totalShares = (totalAmount - (form.value.fee || 0)) / nav  // 场外申购：(金额-手续费)/净值
        cost = totalAmount - (form.value.fee || 0)  // 成本 = 总金额 - 手续费
      }

      // 生成分录
      const postings: any[] = []

      // POSITION DEBIT（持仓增加）
      postings.push({
        postingType: 'DEBIT',
        accountId: form.value.accountId!,  // 后端会自动替换为持仓账户
        accountType: 'POSITION',
        amount: cost,
        shares: totalShares,
        currency: product.currency || 'CNY',
      })

      // CASH CREDIT（现金减少，按fundingLines拆分）
      for (const fundingLine of finalFundingLines) {
        postings.push({
          postingType: 'CREDIT',
          accountId: fundingLine.accountId,
          accountType: 'CASH',
          amount: fundingLine.amount,
          currency: 'CNY',
        })
      }

      // FEE DEBIT（手续费）
      if (form.value.fee && form.value.fee > 0) {
        postings.push({
          postingType: 'DEBIT',
          accountId: form.value.accountId!,  // 后端会自动替换为FEE账户
          accountType: 'FEE',
          amount: form.value.fee,
          currency: product.currency || 'CNY',
        })
      }

      // 自动生成备注：交易类型+场内外+产品名称
      const channelLabel = form.value.channel === 'OTC' ? '场外' : '场内'
      const orderTypeLabel = form.value.orderType === 'BUY' ? '买入' : '申购'
      const autoNote = `${orderTypeLabel}${channelLabel}${product.productName}`

      // 对于场外（OTC）产品，检查是否是货币基金且有关联账户
      if (form.value.channel === 'OTC') {
        // 检查是否是MMF且有关联账户
        const linkedAccount = accountStore.accounts.find(
          (a) => a.linkedProductId === form.value.productId && a.accountType === 'MMF'
        )
        
        if (product.assetType === 'MMF' && linkedAccount) {
          // 货币基金快速购买（N+0，无需订单和结算）
          try {
            // 确定入金账户：如果选择了关联账户的子账户，使用它；否则使用第一个子账户
            let targetAccountId: number | undefined
            if (form.value.accountId && buyChildAccounts.value.length > 0) {
              const selectedChild = buyChildAccounts.value.find((c) => c.id === form.value.accountId)
              if (selectedChild) {
                targetAccountId = selectedChild.id
              }
            }
            if (!targetAccountId && buyChildAccounts.value.length > 0) {
              targetAccountId = buyChildAccounts.value[0].id
            }
            
            // 使用第一个出金账户（通常只有一个）
            const sourceAccountId = finalFundingLines[0]?.accountId
            if (!sourceAccountId) {
              ElNotification.error({
                title: '错误',
                message: '请选择出金账户', position: 'bottom-right', duration: 3000 })
              return
            }
            
            await ledgerApi.quickBuyMoneyMarketFund({
              productId: form.value.productId,
              sourceAccountId,
              targetAccountId,
              amount: totalAmount,
              nav: nav || undefined,
              note: autoNote,
              occurredAt: form.value.requestedAt,
            })
            
            ElNotification.success({
              title: '购买成功',
              message: `货币基金购买成功：${product.productName}`,
              position: 'bottom-right',
              duration: 3000,
            })
          } catch (error: any) {
            ElNotification.error({
              title: '错误',
              message: error.message || '操作失败', position: 'bottom-right', duration: 3000 })
            return
          }
        } else {
          // 普通场外产品：创建订单，等待结算
          try {
            // 创建订单（占用资金），不立即记账
            // 场外产品需要等待结算确认，份额以结算确认为准
            await orderApi.createOrder({
              productId: form.value.productId,
              orderType: form.value.orderType,
              amount: totalAmount,
              fundingLines: finalFundingLines,
            // 这里的订单主要用于"待结算/今日建议"提醒，份额以结算确认为准，因此不写shares
            requestedAt: form.value.requestedAt, // 传递用户指定的发起时间
            expectedNavDate: form.value.navDate,
            expectedConfirmDate: form.value.confirmDate,
            feeEstimate: form.value.fee || undefined, // 传递手续费
              note: autoNote,
            })
            
            ElNotification.success({
              title: '订单创建成功',
              message: '请在"订单&结算"中确认结算',
              position: 'bottom-right',
              duration: 3000,
            })
          } catch (error: any) {
            ElNotification.error({
          title: '错误',
          message: error.message || '操作失败', position: 'bottom-right', duration: 3000 })
            return
          }
        }
      } else {
        // 场内（EXCHANGE）产品：编辑则更新，否则新建
        if (props.editingTxn) {
          await ledgerApi.updateTransaction(props.editingTxn.txnId, {
            txnType: form.value.orderType,
            productId: form.value.productId,
            postings,
            note: autoNote,
            requestedAt: form.value.requestedAt,
          })
          ElNotification.success({
            title: '成功',
            message: '买入/申购记录已更新',
            position: 'bottom-right',
            duration: 3000,
          })
        } else {
          await ledgerApi.createTransaction({
            txnType: form.value.orderType,
            productId: form.value.productId,
            postings,
            note: autoNote,
            requestedAt: form.value.requestedAt,
          })
          const buyMessage = autoNote 
            ? `买入/申购记录成功：${autoNote}`
            : '买入/申购记录成功'
          ElNotification.success({ title: '成功', message: buyMessage, position: 'bottom-right', duration: 3000 })
        }
      }
      
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'SELL' || selectedType.value === 'REDEMPTION') {
      // 场内卖出：需要数量和价格；场外赎回：需要份额
      if (form.value.channel === 'EXCHANGE') {
        if (!form.value.productId || !form.value.parentAccountId || !form.value.accountId || !form.value.shares || !form.value.nav || !form.value.requestedAt) {
          ElNotification.error({
        title: '错误',
        message: '请填写完整信息（产品、数量、价格、到账账户）', position: 'bottom-right', duration: 3000 })
        return
        }
      } else {
        if (!form.value.productId || !form.value.parentAccountId || !form.value.accountId || !form.value.shares || !form.value.requestedAt) {
        ElNotification.error({
        title: '错误',
        message: '请填写完整信息（包括场内/场外、到账父账户和子账户）', position: 'bottom-right', duration: 3000 })
        return
        }
      }
      // 确保 orderType 已自动设置
      if (!form.value.orderType) {
        form.value.orderType = form.value.channel === 'OTC' ? 'REDEMPTION' : 'SELL'
      }

      // 如果产品有多账户持仓或选择了关联产品的账户，必须配置fundingLines
      const needMultiAccountRedeem = productAccountHoldings.value.length > 1 || 
        (selectedSellAccount.value?.linkedProductId && sellChildAccounts.value.length > 0)
      if (needMultiAccountRedeem) {
        if (sellFundingLines.value.length === 0) {
          ElNotification.error({
        title: '错误',
        message: '请至少添加一个账户并分配赎回份额', position: 'bottom-right', duration: 3000 })
          return
        }
        // 校验所有行都填写完整
        for (let i = 0; i < sellFundingLines.value.length; i++) {
          const line = sellFundingLines.value[i]
          if (!line.accountId) {
            ElNotification.error({
        title: '错误',
        message: `第 ${i + 1} 行请选择子账户`, position: 'bottom-right', duration: 3000 })
            return
          }
          if (line.shares == null || line.shares <= 0) {
            ElNotification.error({
        title: '错误',
        message: `第 ${i + 1} 行请填写卖出份额`, position: 'bottom-right', duration: 3000 })
            return
          }
        }
        // 校验总份额是否匹配
        if (sellAllocationError.value) {
          ElNotification.error({
        title: '错误',
        message: sellAllocationError.value, position: 'bottom-right', duration: 3000 })
          return
        }
      }

      // 获取产品信息
      const product = await productApi.getProduct(form.value.productId)
      if (!product) {
        ElNotification.error({
        title: '错误',
        message: '产品不存在', position: 'bottom-right', duration: 3000 })
        return
      }

      // 获取价格/净值（用于计算金额）
      let nav = form.value.nav
      if (form.value.channel === 'EXCHANGE') {
        // 场内卖出：使用用户输入的成交价格
        if (!nav || nav <= 0) {
          ElNotification.error({
        title: '错误',
        message: '请输入成交价格', position: 'bottom-right', duration: 3000 })
          return
        }
      } else {
        // 场外赎回：获取净值
      if (!nav) {
        if (form.value.navDate) {
          const navData = await navApi.getNavByDate(form.value.productId, form.value.navDate)
          nav = navData?.nav
        } else {
          const navData = await navApi.getLatestNav(form.value.productId)
          nav = navData?.nav
        }
      }
      if (!nav || nav <= 0) {
        ElNotification.error({
        title: '错误',
        message: '无法获取产品净值，请手动输入净值日期', position: 'bottom-right', duration: 3000 })
        return
        }
      }

      // 构建fundingLines（统一按份额）
      let finalFundingLines: Array<{ accountId: number; shares: number }> = []
      
      // 如果有赎回来源分配，使用它；否则报错（赎回必须指定来源）
      if (sellFundingLines.value.length > 0) {
        finalFundingLines = sellFundingLines.value
          .filter((fl) => fl.accountId != null && fl.shares != null)
          .map((fl) => ({
            accountId: fl.accountId!,
            shares: fl.shares!,
          }))
      }
      
      if (finalFundingLines.length === 0) {
        ElNotification.error({
        title: '错误',
        message: '请选择赎回来源账户并分配份额', position: 'bottom-right', duration: 3000 })
        return
      }

      // 计算总份额和金额
      const totalShares = finalFundingLines.reduce((sum, fl) => sum + fl.shares, 0)
      const totalAmount = totalShares * nav
      const netAmount = totalAmount - (form.value.fee || 0)  // 净到账金额

      // 场外产品走订单流程，场内产品直接记录流水
      if (form.value.channel === 'OTC') {
        // 验证到账账户
        if (!form.value.accountId) {
          ElNotification.error({
        title: '错误',
        message: '请选择到账账户', position: 'bottom-right', duration: 3000 })
          return
        }

        // 构建 fundingLines：SOURCE（出金账户）+ TARGET（到账账户）
        const allFundingLines: Array<{ accountId: number; shares?: number; amount?: number; lineType: string }> = []
        
        // 出金账户（SOURCE）- 从哪些账户赎回份额
        for (const fl of finalFundingLines) {
          allFundingLines.push({
            accountId: fl.accountId,
            shares: fl.shares,
            amount: (fl.shares / totalShares) * netAmount,
            lineType: 'SOURCE',
          })
        }
        
        // 到账账户（TARGET）- 赎回款到哪个账户
        allFundingLines.push({
          accountId: form.value.accountId,
          amount: netAmount,
          lineType: 'TARGET',
        })

        // 场外赎回 - 创建订单，等待结算
        const orderPayload = {
          orderType: form.value.orderType as 'BUY' | 'SELL' | 'SUBSCRIPTION' | 'REDEMPTION',
          productId: form.value.productId!,
          shares: totalShares,
          amount: totalAmount,
          requestedAt: form.value.requestedAt, // 传递用户指定的发起时间
          expectedNavDate: form.value.navDate,
          expectedConfirmDate: form.value.confirmDate,
          feeEstimate: form.value.fee || undefined, // 传递手续费
          note: `赎回场外${product.productName}`,
          fundingLines: allFundingLines,
        }
        console.log('创建赎回订单 payload:', JSON.stringify(orderPayload, null, 2))
        await orderApi.createOrder(orderPayload)
        ElNotification.success({
          title: '成功',
          message: '赎回订单已创建，请在"订单&结算"中确认结算',
          position: 'bottom-right',
          duration: 3000
        })
        emit('success')
        handleClose()
        return
      }

      // 场内卖出 - 直接记录流水
      // 生成分录
      const postings: any[] = []

      // CASH DEBIT（现金增加）- 到账账户收到卖出款（扣除手续费后）
        postings.push({
          postingType: 'DEBIT',
        accountId: form.value.accountId!,  // 到账账户（华宝证券/交易资金）
          accountType: 'CASH',
        amount: netAmount,  // 净到账金额
          currency: 'CNY',
        })

      // POSITION CREDIT（持仓减少）- 从各持仓账户扣减份额
      // 按摊薄成本法：卖出收入直接减少持仓成本
      for (const fundingLine of finalFundingLines) {
        const lineAmount = (fundingLine.shares / totalShares) * totalAmount  // 按份额比例分摊成交金额
      postings.push({
        postingType: 'CREDIT',
          accountId: fundingLine.accountId,  // 持仓账户
        accountType: 'POSITION',
          amount: lineAmount,
          shares: fundingLine.shares,
        currency: product.currency || 'CNY',
      })
      }

      // FEE DEBIT（手续费）
      if (form.value.fee && form.value.fee > 0) {
        postings.push({
          postingType: 'DEBIT',
          accountId: form.value.accountId!,  // 到账账户（手续费从这里扣）
          accountType: 'FEE',
          amount: form.value.fee,
          currency: product.currency || 'CNY',
        })
      }

      // 自动生成备注：交易类型+场内外+产品名称
      const channelLabel = '场内'
      const orderTypeLabel = '卖出'
      const autoNote = `${orderTypeLabel}${channelLabel}${product.productName}`

      if (props.editingTxn) {
        await ledgerApi.updateTransaction(props.editingTxn.txnId, {
          txnType: form.value.orderType,
          productId: form.value.productId,
          postings,
          note: autoNote,
          requestedAt: form.value.requestedAt,
        })
        ElNotification.success({
          title: '成功',
          message: '卖出记录已更新',
          position: 'bottom-right',
          duration: 3000,
        })
      } else {
        await ledgerApi.createTransaction({
          txnType: form.value.orderType,
          productId: form.value.productId,
          postings,
          note: autoNote,
          requestedAt: form.value.requestedAt,
        })
        const sellMessage = autoNote 
          ? `卖出记录成功：${autoNote}`
          : '卖出记录成功'
        ElNotification.success({ title: '成功', message: sellMessage, position: 'bottom-right', duration: 3000 })
      }
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'BOND_REPO') {
      if (!form.value.parentAccountId || !form.value.accountId || !form.value.amount || !form.value.occurredAt || !form.value.repoDays) {
        ElNotification.error({
        title: '错误',
        message: '请填写完整信息（包括账户父账户和子账户）', position: 'bottom-right', duration: 3000 })
        return
      }
      // 逆回购：CASH CREDIT + CASH DEBIT（到期后）
      // 这里简化处理，只记录逆回购的发起，实际应该分为发起和到期两笔交易
      // 暂时使用统一的记账接口
      const postings: any[] = []
      // CASH CREDIT（资金冻结）
      postings.push({
        postingType: 'CREDIT',
        accountId: form.value.accountId,
        accountType: 'CASH',
        amount: form.value.amount,
        currency: 'CNY',
      })
      // 逆回购作为特殊交易，暂时使用调整类型，或者创建专门的逆回购处理
      // 这里先使用简单的记账方式
      // 自动生成备注
      const repoNote = `逆回购 ${form.value.repoDays}天期${form.value.repoRate ? `，年化利率${form.value.repoRate}%` : ''}`
      
      await ledgerApi.createTransaction({
        txnType: 'BOND_REPO',
        postings,
        note: repoNote,
        requestedAt: form.value.occurredAt,
      })
      const repoMessage = repoNote 
        ? `逆回购记录成功：${repoNote}`
        : '逆回购记录成功'
      ElNotification.success({ title: '成功', message: repoMessage, position: 'bottom-right', duration: 3000 })
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'CUSTODY_TRANSFER') {
      if (!form.value.productId || !form.value.shares || !form.value.transferDate || form.value.transferInPrice === undefined) {
        ElNotification.error({
        title: '错误',
        message: '请填写完整信息', position: 'bottom-right', duration: 3000 })
        return
      }
      
      // 验证：转托管份额必须是整数
      if (!Number.isInteger(form.value.shares)) {
        ElNotification.error({
        title: '错误',
        message: '转托管份额必须是整数', position: 'bottom-right', duration: 3000 })
        return
      }
      
      // 验证：转托管后场外必须至少保留1份
      if (!otcHoldingForTransfer.value) {
        ElNotification.error({
        title: '错误',
        message: '该产品暂无场外持仓，无法转托管', position: 'bottom-right', duration: 3000 })
        return
      }
      
      const currentShares = Math.floor(otcHoldingForTransfer.value.shares) // 取整数部分
      const minKeep = 1 // 最少保留1份
      const maxTransfer = currentShares - minKeep
      if (form.value.shares > maxTransfer) {
        ElNotification.error({
        title: '错误',
        message: `转出份额不能超过 ${maxTransfer} 份（当前持仓 ${currentShares} 份，最少保留 ${minKeep} 份）`, position: 'bottom-right', duration: 3000 })
        return
      }
      
      // 获取产品名称用于自动备注
      const product = productStore.products.find(p => p.id === form.value.productId)
      const productName = product?.productName || '产品'
      const custodyNote = `转托管: ${productName} ${form.value.shares}份，场内价格${form.value.transferInPrice}`
      
      // 调用转托管API（只传场内价格，后端用于计算场内成本）
      await ledgerApi.createCustodyTransfer({
        productId: form.value.productId,
        shares: form.value.shares,
        transferPrice: form.value.transferInPrice, // 场内价格作为 transferPrice
        transferDate: form.value.transferDate,
        note: custodyNote,
      })
      const custodyMessage = custodyNote 
        ? `转托管成功：${custodyNote}`
        : '转托管成功'
      ElNotification.success({ title: '成功', message: custodyMessage, position: 'bottom-right', duration: 3000 })
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'REFUND') {
      if (!form.value.relatedTxnId || !form.value.parentAccountId || !form.value.accountId || !form.value.amount) {
        ElNotification.error({
        title: '错误',
        message: '请填写完整信息（包括退款账户父账户和子账户）', position: 'bottom-right', duration: 3000 })
        return
      }
      // 自动生成备注
      const refundNote = `退款: 原交易${form.value.relatedTxnId}`
      
      await ledgerApi.refund(form.value.relatedTxnId, {
        refundAmount: form.value.amount,
        accountId: form.value.accountId,
        note: refundNote,
      })
      const refundMessage = refundNote 
        ? `退款成功：${refundNote}`
        : '退款成功'
      ElNotification.success({ title: '成功', message: refundMessage, position: 'bottom-right', duration: 3000 })
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'ADJUST') {
      // 余额调整
      if (!form.value.parentAccountId || !form.value.accountId || form.value.amount === undefined) {
        ElNotification.error({
        title: '错误',
        message: '请填写完整信息（包括账户父账户和子账户）', position: 'bottom-right', duration: 3000 })
        return
      }
      // 调整：需要计算差额，生成ADJUST分录
      ElNotification.warning({ title: '警告', message: '余额调整功能建议使用账户管理页面的余额调整功能', position: 'bottom-right', duration: 3000 })
      return
    }

    // 其他类型暂不支持
    ElNotification.error({ title: '错误', message: '不支持的业务类型', position: 'bottom-right', duration: 3000 })
    return
  } catch (error: any) {
    ElNotification.error({ title: '错误', message: error.message || '提交失败', position: 'bottom-right', duration: 3000 })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
/* 场内/场外开关按钮样式 */
.channel-btn {
  flex: 1;
  padding: 10px 20px;
  border: 1px solid rgba(230, 238, 247, 0.95);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.95);
  color: var(--text);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  outline: none;
}

.channel-btn:hover {
  background: rgba(78, 164, 255, 0.08);
  border-color: rgba(78, 164, 255, 0.3);
}

.channel-btn.active {
  background: var(--primary);
  color: white;
  border-color: var(--primary);
  font-weight: 600;
}

.channel-btn.active:hover {
  background: var(--primary2);
  border-color: var(--primary2);
}
</style>
