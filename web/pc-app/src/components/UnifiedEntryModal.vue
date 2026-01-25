<template>
  <el-dialog
    v-model="visible"
    title="记一笔"
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
          <el-form-item label="父账户" required>
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-select 
                v-model="form.parentAccountId" 
                placeholder="选择父账户" 
                style="flex: 1"
                @change="handleParentAccountChange"
              >
                <el-option
                  v-for="acc in parentAccounts"
                  :key="acc.id"
                  :label="acc.accountName"
                  :value="acc.id"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span style="flex: 1; min-width: 0;">
                      <span>{{ acc.accountName }}</span>
                    </span>
                    <span style="color: #4ea4ff; font-size: 12px; margin-left: 12px; white-space: nowrap;">
                      {{ formatCurrency(acc.balance || 0) }}
                    </span>
                  </div>
                </el-option>
              </el-select>
              <span v-if="form.parentAccountId" style="color: #4ea4ff; font-size: 12px; white-space: nowrap; min-width: 80px; text-align: right;">
                {{ formatCurrency(selectedParentAccountBalance) }}
              </span>
            </div>
          </el-form-item>
          <el-form-item label="子账户" required>
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-select 
                v-model="form.accountId" 
                placeholder="选择子账户" 
                style="flex: 1"
                :disabled="!form.parentAccountId"
              >
                <el-option
                  v-for="acc in availableChildAccounts"
                  :key="acc.id"
                  :value="acc.id"
                  :label="acc.accountName"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span style="flex: 1; min-width: 0;">
                      <span>{{ acc.accountName }}</span>
                      <span v-if="acc.fundUsage" style="color: #909399; font-size: 12px; margin-left: 8px;">
                        ({{ getFundUsageLabel(acc.fundUsage) }})
                      </span>
                    </span>
                    <span style="color: #4ea4ff; font-size: 12px; margin-left: 12px; white-space: nowrap;">
                      {{ formatCurrency(acc.balance || 0) }}
                    </span>
                  </div>
                </el-option>
              </el-select>
              <span v-if="form.parentAccountId && form.accountId" style="color: #4ea4ff; font-size: 12px; white-space: nowrap; min-width: 80px; text-align: right;">
                {{ formatCurrency(selectedChildAccountBalance) }}
              </span>
            </div>
          </el-form-item>
          <el-form-item label="金额（元）" required>
            <el-input-number v-model="form.amount" :min="0.01" :precision="2" style="width: 100%" />
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
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </el-form-item>
          <el-form-item label="还款账户" required>
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-select 
                v-model="form.parentAccountId" 
                placeholder="选择还款账户（父账户）" 
                style="flex: 1"
                @change="handleRepaymentAccountChange"
              >
                <el-option
                  v-for="acc in parentAccounts"
                  :key="acc.id"
                  :label="acc.accountName"
                  :value="acc.id"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span style="flex: 1; min-width: 0;">
                      <span>{{ acc.accountName }}</span>
                    </span>
                    <span style="color: #4ea4ff; font-size: 12px; margin-left: 12px; white-space: nowrap;">
                      {{ formatCurrency(acc.balance || 0) }}
                    </span>
                  </div>
                </el-option>
              </el-select>
              <span v-if="form.parentAccountId" style="color: #4ea4ff; font-size: 12px; white-space: nowrap; min-width: 80px; text-align: right;">
                {{ formatCurrency(selectedParentAccountBalance) }}
              </span>
            </div>
          </el-form-item>
          <el-form-item label="子账户" required>
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-select 
                v-model="form.accountId" 
                placeholder="选择子账户" 
                style="flex: 1"
                :disabled="!form.parentAccountId"
              >
                <el-option
                  v-for="acc in availableChildAccounts"
                  :key="acc.id"
                  :value="acc.id"
                  :label="acc.accountName"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span style="flex: 1; min-width: 0;">
                      <span>{{ acc.accountName }}</span>
                    </span>
                    <span style="color: #4ea4ff; font-size: 12px; margin-left: 12px; white-space: nowrap;">
                      {{ formatCurrency(acc.balance || 0) }}
                    </span>
                  </div>
                </el-option>
              </el-select>
              <span v-if="form.parentAccountId && form.accountId" style="color: #4ea4ff; font-size: 12px; white-space: nowrap; min-width: 80px; text-align: right;">
                {{ formatCurrency(selectedChildAccountBalance) }}
              </span>
            </div>
          </el-form-item>
          <el-form-item label="信贷账户" required>
            <el-select v-model="form.creditAccountId" placeholder="选择要还款的信贷账户" style="width: 100%">
              <el-option
                v-for="acc in creditAccounts"
                :key="acc.id"
                :label="acc.accountName"
                :value="acc.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="还款金额（元）" required>
            <el-input-number v-model="form.amount" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.note" placeholder="比如：还花呗" />
          </el-form-item>
        </template>

        <!-- 转账 -->
        <template v-else-if="selectedType === 'TRANSFER_OUT' || selectedType === 'TRANSFER_IN'">
          <el-form-item label="发生时间" required>
            <el-date-picker
              v-model="form.occurredAt"
              type="datetime"
              placeholder="选择发生时间"
              style="width: 100%"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </el-form-item>
          <el-form-item label="转出父账户" required>
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-select 
                v-model="form.fromParentAccountId" 
                placeholder="选择转出账户的父账户" 
                style="flex: 1"
                @change="handleFromParentAccountChange"
              >
                <el-option
                  v-for="acc in parentAccounts"
                  :key="acc.id"
                  :label="acc.accountName"
                  :value="acc.id"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span style="flex: 1; min-width: 0;">
                      <span>{{ acc.accountName }}</span>
                    </span>
                    <span style="color: #4ea4ff; font-size: 12px; margin-left: 12px; white-space: nowrap;">
                      {{ formatCurrency(acc.balance || 0) }}
                    </span>
                  </div>
                </el-option>
              </el-select>
              <span v-if="form.fromParentAccountId" style="color: #4ea4ff; font-size: 12px; white-space: nowrap; min-width: 80px; text-align: right;">
                {{ formatCurrency(selectedFromParentAccountBalance) }}
              </span>
            </div>
          </el-form-item>
          <el-form-item label="转出子账户" required>
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-select 
                v-model="form.fromAccountId" 
                placeholder="选择转出账户的子账户" 
                style="flex: 1"
                :disabled="!form.fromParentAccountId"
              >
                <el-option
                  v-for="acc in availableFromChildAccounts"
                  :key="acc.id"
                  :value="acc.id"
                  :label="acc.accountName"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span style="flex: 1; min-width: 0;">
                      <span>{{ acc.accountName }}</span>
                      <span v-if="acc.fundUsage" style="color: #909399; font-size: 12px; margin-left: 8px;">
                        ({{ getFundUsageLabel(acc.fundUsage) }})
                      </span>
                    </span>
                    <span style="color: #4ea4ff; font-size: 12px; margin-left: 12px; white-space: nowrap;">
                      {{ formatCurrency(acc.balance || 0) }}
                    </span>
                  </div>
                </el-option>
              </el-select>
              <span v-if="form.fromParentAccountId && form.fromAccountId" style="color: #4ea4ff; font-size: 12px; white-space: nowrap; min-width: 80px; text-align: right;">
                {{ formatCurrency(selectedFromChildAccountBalance) }}
              </span>
            </div>
          </el-form-item>
          <el-form-item label="转入父账户" required>
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-select 
                v-model="form.toParentAccountId" 
                placeholder="选择转入账户的父账户" 
                style="flex: 1"
                @change="handleToParentAccountChange"
              >
                <el-option
                  v-for="acc in parentAccounts"
                  :key="acc.id"
                  :label="acc.accountName"
                  :value="acc.id"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span style="flex: 1; min-width: 0;">
                      <span>{{ acc.accountName }}</span>
                    </span>
                    <span style="color: #4ea4ff; font-size: 12px; margin-left: 12px; white-space: nowrap;">
                      {{ formatCurrency(acc.balance || 0) }}
                    </span>
                  </div>
                </el-option>
              </el-select>
              <span v-if="form.toParentAccountId" style="color: #4ea4ff; font-size: 12px; white-space: nowrap; min-width: 80px; text-align: right;">
                {{ formatCurrency(selectedToParentAccountBalance) }}
              </span>
            </div>
          </el-form-item>
          <el-form-item label="转入子账户" required>
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-select 
                v-model="form.toAccountId" 
                placeholder="选择转入账户的子账户" 
                style="flex: 1"
                :disabled="!form.toParentAccountId"
              >
                <el-option
                  v-for="acc in availableToChildAccounts"
                  :key="acc.id"
                  :value="acc.id"
                  :label="acc.accountName"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span style="flex: 1; min-width: 0;">
                      <span>{{ acc.accountName }}</span>
                      <span v-if="acc.fundUsage" style="color: #909399; font-size: 12px; margin-left: 8px;">
                        ({{ getFundUsageLabel(acc.fundUsage) }})
                      </span>
                    </span>
                    <span style="color: #4ea4ff; font-size: 12px; margin-left: 12px; white-space: nowrap;">
                      {{ formatCurrency(acc.balance || 0) }}
                    </span>
                  </div>
                </el-option>
              </el-select>
              <span v-if="form.toParentAccountId && form.toAccountId" style="color: #4ea4ff; font-size: 12px; white-space: nowrap; min-width: 80px; text-align: right;">
                {{ formatCurrency(selectedToChildAccountBalance) }}
              </span>
            </div>
          </el-form-item>
          <el-form-item label="金额（元）" required>
            <el-input-number v-model="form.amount" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.note" placeholder="比如：分配到房租预备金" />
          </el-form-item>
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
          <el-form-item label="订单类型" required>
            <el-select v-model="form.orderType" placeholder="选择订单类型" style="width: 100%">
              <el-option label="买入" value="BUY" />
              <el-option label="申购" value="SUBSCRIPTION" />
            </el-select>
          </el-form-item>
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
              :min="0.0001" 
              :precision="4" 
              style="width: 100%"
              placeholder="自动获取或手动输入"
            />
            <div style="color: #909399; font-size: 12px; margin-top: 4px">
              选择产品后自动获取，也可手动输入。买入时将根据金额和净值计算份额。
            </div>
          </el-form-item>
          <el-form-item v-if="form.amount && form.nav" label="预计份额">
            <div style="color: #4ea4ff; font-weight: 600">
              {{ ((form.amount - (form.fee || 0)) / form.nav).toFixed(4) }} 份
            </div>
          </el-form-item>
          <el-form-item label="来源父账户" required>
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-select 
                v-model="form.parentAccountId" 
                placeholder="选择资金来源的父账户" 
                style="flex: 1"
                @change="handleBuyParentAccountChange"
              >
                <el-option
                  v-for="acc in parentAccounts"
                  :key="acc.id"
                  :label="acc.accountName"
                  :value="acc.id"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span style="flex: 1; min-width: 0;">
                      <span>{{ acc.accountName }}</span>
                    </span>
                    <span style="color: #4ea4ff; font-size: 12px; margin-left: 12px; white-space: nowrap;">
                      {{ formatCurrency(acc.balance || 0) }}
                    </span>
                  </div>
                </el-option>
              </el-select>
              <span v-if="form.parentAccountId" style="color: #4ea4ff; font-size: 12px; white-space: nowrap; min-width: 80px; text-align: right;">
                {{ formatCurrency(selectedParentAccountBalance) }}
              </span>
            </div>
          </el-form-item>
          <el-form-item label="来源子账户" required>
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-select 
                v-model="form.accountId" 
                placeholder="选择资金来源的子账户" 
                style="flex: 1"
                :disabled="!form.parentAccountId"
                @change="handleBuyAccountChange"
              >
                <el-option
                  v-for="acc in availableChildAccounts"
                  :key="acc.id"
                  :value="acc.id"
                  :label="acc.accountName"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span style="flex: 1; min-width: 0;">
                      <span>{{ acc.accountName }}</span>
                      <span v-if="acc.fundUsage" style="color: #909399; font-size: 12px; margin-left: 8px;">
                        ({{ getFundUsageLabel(acc.fundUsage) }})
                      </span>
                    </span>
                    <span style="color: #4ea4ff; font-size: 12px; margin-left: 12px; white-space: nowrap;">
                      {{ formatCurrency(acc.balance || 0) }}
                    </span>
                  </div>
                </el-option>
              </el-select>
              <span v-if="form.parentAccountId && form.accountId" style="color: #4ea4ff; font-size: 12px; white-space: nowrap; min-width: 80px; text-align: right;">
                {{ formatCurrency(selectedChildAccountBalance) }}
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
                  style="display: flex; gap: 8px; margin-bottom: 8px; align-items: center;"
                >
                  <el-select
                    v-model="line.accountId"
                    placeholder="选择子账户"
                    style="flex: 1"
                  >
                    <el-option
                      v-for="acc in buyAvailableChildAccounts"
                      :key="acc.id"
                      :label="acc.accountName"
                      :value="acc.id"
                      :disabled="buyFundingLines.some((fl, i) => i !== index && fl.accountId === acc.id)"
                    />
                  </el-select>
                  <el-input-number
                    v-model="line.amount"
                    :min="0.01"
                    :precision="2"
                    placeholder="金额"
                    style="width: 150px"
                  />
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

          <el-form-item label="总金额（元）" required>
            <el-input-number v-model="form.amount" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="费用（元）">
            <el-input-number v-model="form.fee" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.note" placeholder="比如：周定投" />
          </el-form-item>
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
          <el-form-item label="订单类型" required>
            <el-select v-model="form.orderType" placeholder="选择订单类型" style="width: 100%">
              <el-option label="卖出" value="SELL" />
              <el-option label="赎回" value="REDEMPTION" />
            </el-select>
          </el-form-item>
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
              :min="0.0001" 
              :precision="4" 
              style="width: 100%"
              placeholder="自动获取或手动输入"
            />
            <div style="color: #909399; font-size: 12px; margin-top: 4px">
              选择产品后自动获取，也可手动输入。卖出时将根据份额和净值计算金额。
            </div>
          </el-form-item>
          <el-form-item v-if="form.shares && form.nav" label="预计到账金额">
            <div style="color: #f59e0b; font-weight: 600">
              {{ (form.shares * form.nav - (form.fee || 0)).toFixed(2) }} 元
            </div>
          </el-form-item>
          <el-form-item label="到账父账户" required>
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-select 
                v-model="form.parentAccountId" 
                placeholder="选择到账的父账户" 
                style="flex: 1"
                @change="handleSellParentAccountChange"
              >
                <el-option
                  v-for="acc in parentAccounts"
                  :key="acc.id"
                  :label="acc.accountName"
                  :value="acc.id"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span style="flex: 1; min-width: 0;">
                      <span>{{ acc.accountName }}</span>
                    </span>
                    <span style="color: #4ea4ff; font-size: 12px; margin-left: 12px; white-space: nowrap;">
                      {{ formatCurrency(acc.balance || 0) }}
                    </span>
                  </div>
                </el-option>
              </el-select>
              <span v-if="form.parentAccountId" style="color: #4ea4ff; font-size: 12px; white-space: nowrap; min-width: 80px; text-align: right;">
                {{ formatCurrency(selectedParentAccountBalance) }}
              </span>
            </div>
          </el-form-item>
          <el-form-item label="到账子账户" required>
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-select 
                v-model="form.accountId" 
                placeholder="选择到账的子账户" 
                style="flex: 1"
                :disabled="!form.parentAccountId"
                @change="handleSellAccountChange"
              >
                <el-option
                  v-for="acc in availableChildAccounts"
                  :key="acc.id"
                  :value="acc.id"
                  :label="acc.accountName"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span style="flex: 1; min-width: 0;">
                      <span>{{ acc.accountName }}</span>
                      <span v-if="acc.fundUsage" style="color: #909399; font-size: 12px; margin-left: 8px;">
                        ({{ getFundUsageLabel(acc.fundUsage) }})
                      </span>
                    </span>
                    <span style="color: #4ea4ff; font-size: 12px; margin-left: 12px; white-space: nowrap;">
                      {{ formatCurrency(acc.balance || 0) }}
                    </span>
                  </div>
                </el-option>
              </el-select>
              <span v-if="form.parentAccountId && form.accountId" style="color: #4ea4ff; font-size: 12px; white-space: nowrap; min-width: 80px; text-align: right;">
                {{ formatCurrency(selectedChildAccountBalance) }}
              </span>
            </div>
            <div v-if="selectedSellAccount?.linkedProductId && sellChildAccounts.length > 0" class="form-help-text" style="color: #f59e0b; margin-top: 4px;">
              此账户已关联产品，可以按子账户分别设置卖出份额
            </div>
          </el-form-item>

          <!-- 多子账户配置（卖出时） -->
          <el-form-item 
            v-if="selectedSellAccount?.linkedProductId && sellChildAccounts.length > 0"
            label="子账户卖出份额分配"
          >
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
                    style="flex: 1"
                  >
                    <el-option
                      v-for="acc in sellAvailableChildAccounts"
                      :key="acc.id"
                      :label="acc.accountName"
                      :value="acc.id"
                      :disabled="sellFundingLines.some((fl, i) => i !== index && fl.accountId === acc.id)"
                    />
                  </el-select>
                  <el-input-number
                    v-model="line.shares"
                    :min="0.01"
                    :precision="4"
                    placeholder="份额"
                    style="width: 150px"
                  />
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
          </el-form-item>

          <el-form-item label="总份额" required>
            <el-input-number v-model="form.shares" :min="0.01" :precision="4" style="width: 100%" />
          </el-form-item>
          <el-form-item label="费用（元）">
            <el-input-number v-model="form.fee" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.note" placeholder="比如：赎回" />
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
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </el-form-item>
          <el-form-item label="账户父账户" required>
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-select 
                v-model="form.parentAccountId" 
                placeholder="选择账户的父账户" 
                style="flex: 1"
                @change="handleParentAccountChange"
              >
                <el-option
                  v-for="acc in parentAccounts"
                  :key="acc.id"
                  :label="acc.accountName"
                  :value="acc.id"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span style="flex: 1; min-width: 0;">
                      <span>{{ acc.accountName }}</span>
                    </span>
                    <span style="color: #4ea4ff; font-size: 12px; margin-left: 12px; white-space: nowrap;">
                      {{ formatCurrency(acc.balance || 0) }}
                    </span>
                  </div>
                </el-option>
              </el-select>
              <span v-if="form.parentAccountId" style="color: #4ea4ff; font-size: 12px; white-space: nowrap; min-width: 80px; text-align: right;">
                {{ formatCurrency(selectedParentAccountBalance) }}
              </span>
            </div>
          </el-form-item>
          <el-form-item label="账户子账户" required>
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-select 
                v-model="form.accountId" 
                placeholder="选择账户的子账户" 
                style="flex: 1"
                :disabled="!form.parentAccountId"
              >
                <el-option
                  v-for="acc in availableChildAccounts"
                  :key="acc.id"
                  :value="acc.id"
                  :label="acc.accountName"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span style="flex: 1; min-width: 0;">
                      <span>{{ acc.accountName }}</span>
                      <span v-if="acc.fundUsage" style="color: #909399; font-size: 12px; margin-left: 8px;">
                        ({{ getFundUsageLabel(acc.fundUsage) }})
                      </span>
                    </span>
                    <span style="color: #4ea4ff; font-size: 12px; margin-left: 12px; white-space: nowrap;">
                      {{ formatCurrency(acc.balance || 0) }}
                    </span>
                  </div>
                </el-option>
              </el-select>
              <span v-if="form.parentAccountId && form.accountId" style="color: #4ea4ff; font-size: 12px; white-space: nowrap; min-width: 80px; text-align: right;">
                {{ formatCurrency(selectedChildAccountBalance) }}
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
          <el-form-item label="备注">
            <el-input v-model="form.note" placeholder="逆回购说明" />
          </el-form-item>
        </template>

        <!-- 转托管 -->
        <template v-else-if="selectedType === 'CUSTODY_TRANSFER'">
          <el-form-item label="产品" required>
            <el-select v-model="form.productId" placeholder="选择产品" style="width: 100%" filterable>
              <el-option
                v-for="prod in products"
                :key="prod.id"
                :label="`${prod.productName} (${prod.productCode})`"
                :value="prod.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="转出类型" required>
            <el-select v-model="form.fromChannel" placeholder="选择转出类型" style="width: 100%">
              <el-option label="场外" value="OTC" />
              <el-option label="场内" value="EXCHANGE" />
            </el-select>
          </el-form-item>
          <el-form-item label="转入类型" required>
            <el-select v-model="form.toChannel" placeholder="选择转入类型" style="width: 100%">
              <el-option label="场内" value="EXCHANGE" />
              <el-option label="场外" value="OTC" />
            </el-select>
          </el-form-item>
          <el-form-item label="转出日期" required>
            <el-date-picker
              v-model="form.transferDate"
              type="date"
              placeholder="选择转出日期"
              style="width: 100%"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
          <el-form-item label="转出价格" required>
            <el-input-number v-model="form.transferOutPrice" :min="0" :precision="4" style="width: 100%" />
            <div style="color: #909399; font-size: 12px; margin-top: 4px">转托管价格，通常为0费用</div>
          </el-form-item>
          <el-form-item label="转入价格" required>
            <el-input-number v-model="form.transferInPrice" :min="0" :precision="4" style="width: 100%" />
          </el-form-item>
          <el-form-item label="份额" required>
            <el-input-number v-model="form.shares" :min="0.01" :precision="4" style="width: 100%" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.note" placeholder="转托管说明" />
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
          <el-form-item label="退款账户父账户" required>
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-select 
                v-model="form.parentAccountId" 
                placeholder="选择退款账户的父账户" 
                style="flex: 1"
                @change="handleParentAccountChange"
              >
                <el-option
                  v-for="acc in parentAccounts"
                  :key="acc.id"
                  :label="acc.accountName"
                  :value="acc.id"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span style="flex: 1; min-width: 0;">
                      <span>{{ acc.accountName }}</span>
                    </span>
                    <span style="color: #4ea4ff; font-size: 12px; margin-left: 12px; white-space: nowrap;">
                      {{ formatCurrency(acc.balance || 0) }}
                    </span>
                  </div>
                </el-option>
              </el-select>
              <span v-if="form.parentAccountId" style="color: #4ea4ff; font-size: 12px; white-space: nowrap; min-width: 80px; text-align: right;">
                {{ formatCurrency(selectedParentAccountBalance) }}
              </span>
            </div>
          </el-form-item>
          <el-form-item label="退款账户子账户" required>
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-select 
                v-model="form.accountId" 
                placeholder="选择退款账户的子账户" 
                style="flex: 1"
                :disabled="!form.parentAccountId"
              >
                <el-option
                  v-for="acc in availableChildAccounts"
                  :key="acc.id"
                  :value="acc.id"
                  :label="acc.accountName"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span style="flex: 1; min-width: 0;">
                      <span>{{ acc.accountName }}</span>
                      <span v-if="acc.fundUsage" style="color: #909399; font-size: 12px; margin-left: 8px;">
                        ({{ getFundUsageLabel(acc.fundUsage) }})
                      </span>
                    </span>
                    <span style="color: #4ea4ff; font-size: 12px; margin-left: 12px; white-space: nowrap;">
                      {{ formatCurrency(acc.balance || 0) }}
                    </span>
                  </div>
                </el-option>
              </el-select>
              <span v-if="form.parentAccountId && form.accountId" style="color: #4ea4ff; font-size: 12px; white-space: nowrap; min-width: 80px; text-align: right;">
                {{ formatCurrency(selectedChildAccountBalance) }}
              </span>
            </div>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.note" placeholder="退款说明" />
          </el-form-item>
        </template>

        <!-- 调整 -->
        <template v-else-if="selectedType === 'ADJUST'">
          <el-form-item label="账户父账户" required>
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-select 
                v-model="form.parentAccountId" 
                placeholder="选择账户的父账户" 
                style="flex: 1"
                @change="handleParentAccountChange"
              >
                <el-option
                  v-for="acc in parentAccounts"
                  :key="acc.id"
                  :label="acc.accountName"
                  :value="acc.id"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span style="flex: 1; min-width: 0;">
                      <span>{{ acc.accountName }}</span>
                    </span>
                    <span style="color: #4ea4ff; font-size: 12px; margin-left: 12px; white-space: nowrap;">
                      {{ formatCurrency(acc.balance || 0) }}
                    </span>
                  </div>
                </el-option>
              </el-select>
              <span v-if="form.parentAccountId" style="color: #4ea4ff; font-size: 12px; white-space: nowrap; min-width: 80px; text-align: right;">
                {{ formatCurrency(selectedParentAccountBalance) }}
              </span>
            </div>
          </el-form-item>
          <el-form-item label="账户子账户" required>
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-select 
                v-model="form.accountId" 
                placeholder="选择账户的子账户" 
                style="flex: 1"
                :disabled="!form.parentAccountId"
              >
                <el-option
                  v-for="acc in availableChildAccounts"
                  :key="acc.id"
                  :value="acc.id"
                  :label="acc.accountName"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span style="flex: 1; min-width: 0;">
                      <span>{{ acc.accountName }}</span>
                      <span v-if="acc.fundUsage" style="color: #909399; font-size: 12px; margin-left: 8px;">
                        ({{ getFundUsageLabel(acc.fundUsage) }})
                      </span>
                    </span>
                    <span style="color: #4ea4ff; font-size: 12px; margin-left: 12px; white-space: nowrap;">
                      {{ formatCurrency(acc.balance || 0) }}
                    </span>
                  </div>
                </el-option>
              </el-select>
              <span v-if="form.parentAccountId && form.accountId" style="color: #4ea4ff; font-size: 12px; white-space: nowrap; min-width: 80px; text-align: right;">
                {{ formatCurrency(selectedChildAccountBalance) }}
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
import { ElMessage } from 'element-plus'
import { useAccountStore, useProductStore } from '@wealth-hub/shared'
import { ledgerApi, getFundUsageLabel, expenseCategories, incomeCategories, getCategoryGroups, navApi, productApi, formatCurrency, orderApi } from '@wealth-hub/shared'
import type { Account } from '@wealth-hub/shared'

const props = defineProps<{
  modelValue: boolean
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

// 卖出时的多子账户配置
const sellFundingLines = ref<Array<{
  accountId: number | undefined
  shares?: number
}>>([])

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
  
  const parentAccount = findAccountById(accountStore.accountTree, form.value.parentAccountId)
  if (!parentAccount || !parentAccount.children) return []
  
  // 返回父账户的所有REAL类型的子账户
  return parentAccount.children.filter(acc => acc.accountKind === 'REAL')
})

// 转出账户的可用于账户（根据选择的转出父账户）
const availableFromChildAccounts = computed(() => {
  if (!form.value.fromParentAccountId) return []
  
  const parentAccount = findAccountById(accountStore.accountTree, form.value.fromParentAccountId)
  if (!parentAccount || !parentAccount.children) return []
  
  // 返回父账户的所有REAL类型的子账户
  return parentAccount.children.filter(acc => acc.accountKind === 'REAL')
})

// 转入账户的可用于账户（根据选择的转入父账户）
const availableToChildAccounts = computed(() => {
  if (!form.value.toParentAccountId) return []
  
  const parentAccount = findAccountById(accountStore.accountTree, form.value.toParentAccountId)
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

// 买入时的账户相关计算
const selectedBuyAccount = computed(() => {
  if (!form.value.accountId || (selectedType.value !== 'BUY' && selectedType.value !== 'SUBSCRIPTION')) return null
  const account = accountStore.accounts.find((a) => a.id === form.value.accountId)
  return (account as Account & { linkedProductId?: number }) || null
})

const buyChildAccounts = computed(() => {
  if (!form.value.accountId || selectedType.value !== 'BUY' && selectedType.value !== 'SUBSCRIPTION') return []
  return accountStore.accounts.filter((a) => a.parentAccountId === form.value.accountId)
})

const buyAvailableChildAccounts = computed(() => buyChildAccounts.value)

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

// 卖出时的账户相关计算
const selectedSellAccount = computed(() => {
  if (!form.value.accountId || (selectedType.value !== 'SELL' && selectedType.value !== 'REDEMPTION')) return null
  const account = accountStore.accounts.find((a) => a.id === form.value.accountId)
  return (account as Account & { linkedProductId?: number }) || null
})

const sellChildAccounts = computed(() => {
  if (!form.value.accountId || selectedType.value !== 'SELL' && selectedType.value !== 'REDEMPTION') return []
  return accountStore.accounts.filter((a) => a.parentAccountId === form.value.accountId)
})

const sellAvailableChildAccounts = computed(() => sellChildAccounts.value)

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

// 余额计算属性
// 父账户余额：计算所有子账户余额之和
const selectedParentAccountBalance = computed(() => {
  if (!form.value.parentAccountId) return 0
  const account = findAccountInTree(accountStore.accountTree, form.value.parentAccountId)
  if (!account) return 0
  // 如果有子账户，计算所有子账户余额之和
  if (account.children && account.children.length > 0) {
    return account.children.reduce((sum: number, child: Account) => {
      return sum + (child.balance || 0)
    }, 0)
  }
  // 如果没有子账户，直接返回账户余额
  return account.balance || 0
})

// 子账户余额：从账户树中获取
const selectedChildAccountBalance = computed(() => {
  if (!form.value.accountId) return 0
  const account = findAccountInTree(accountStore.accountTree, form.value.accountId)
  if (!account) return 0
  return account.balance || 0
})

// 转出父账户余额
const selectedFromParentAccountBalance = computed(() => {
  if (!form.value.fromParentAccountId) return 0
  const account = findAccountInTree(accountStore.accountTree, form.value.fromParentAccountId)
  if (!account) return 0
  // 如果有子账户，计算所有子账户余额之和
  if (account.children && account.children.length > 0) {
    return account.children.reduce((sum: number, child: Account) => {
      return sum + (child.balance || 0)
    }, 0)
  }
  return account.balance || 0
})

// 转出子账户余额
const selectedFromChildAccountBalance = computed(() => {
  if (!form.value.fromAccountId) return 0
  const account = findAccountInTree(accountStore.accountTree, form.value.fromAccountId)
  if (!account) return 0
  return account.balance || 0
})

// 转入父账户余额
const selectedToParentAccountBalance = computed(() => {
  if (!form.value.toParentAccountId) return 0
  const account = findAccountInTree(accountStore.accountTree, form.value.toParentAccountId)
  if (!account) return 0
  // 如果有子账户，计算所有子账户余额之和
  if (account.children && account.children.length > 0) {
    return account.children.reduce((sum: number, child: Account) => {
      return sum + (child.balance || 0)
    }, 0)
  }
  return account.balance || 0
})

// 转入子账户余额
const selectedToChildAccountBalance = computed(() => {
  if (!form.value.toAccountId) return 0
  const account = findAccountInTree(accountStore.accountTree, form.value.toAccountId)
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

onMounted(() => {
  productStore.fetchProducts()
  accountStore.fetchAccounts()
})

watch(visible, async (val) => {
  if (val) {
    // 打开对话框时，刷新账户数据以获取最新余额
    await accountStore.fetchAccounts()
    step.value = 1
    selectedType.value = ''
    form.value = {
      occurredAt: new Date().toISOString().slice(0, 19).replace('T', ' '),
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
      requestedAt: new Date().toISOString().slice(0, 19).replace('T', ' '),
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
  }
})

// 监听订单类型变化，清空fundingLines
watch(() => selectedType.value, () => {
  buyFundingLines.value = []
  sellFundingLines.value = []
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
}

function handleParentAccountChange() {
  form.value.accountId = undefined
}

function handleRepaymentAccountChange() {
  form.value.accountId = undefined
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

function handleSellParentAccountChange() {
  // 卖出父账户改变时，清空子账户选择和份额分配
  form.value.accountId = undefined
  sellFundingLines.value = []
}

function handleSellAccountChange() {
  sellFundingLines.value = []
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

function handleAddSellFundingLine() {
  sellFundingLines.value.push({
    accountId: undefined,
    shares: undefined,
  })
}

function handleRemoveSellFundingLine(index: number) {
  sellFundingLines.value.splice(index, 1)
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
        form.value.nav = nav.nav
      }
    } catch (error) {
      console.error('获取净值失败:', error)
    }
  }
})

// 递归查找账户树中的账户
function findAccountInTree(accounts: Account[], accountId: number): Account | null {
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
    ElMessage.error('请选择业务类型')
    return
  }

  try {
    submitting.value = true

    // 根据不同的业务类型构建postings
    const postings: any[] = []

    if (selectedType.value === 'EXPENSE') {
      if (!form.value.parentAccountId || !form.value.accountId || !form.value.amount || !form.value.occurredAt || !form.value.category || (Array.isArray(form.value.category) && form.value.category.length === 0)) {
        ElMessage.error('请填写完整信息（包括父账户和子账户）')
        return
      }
      const categoryId = Array.isArray(form.value.category) 
        ? (form.value.category[form.value.category.length - 1] as number)
        : (form.value.category as number)
      // CASH CREDIT + EXPENSE DEBIT
      postings.push({
        postingType: 'CREDIT',
        accountId: form.value.accountId,
        accountType: 'CASH',
        amount: form.value.amount,
        currency: 'CNY',
      })
      // EXPENSE账户由后端自动创建
      postings.push({
        postingType: 'DEBIT',
        accountId: form.value.accountId, // 后端会替换为虚拟账户
        accountType: 'EXPENSE',
        amount: form.value.amount,
        currency: 'CNY',
      })
      await ledgerApi.createTransaction({
        txnType: selectedType.value,
        postings,
        note: form.value.note || undefined,
        requestedAt: form.value.occurredAt,
        categoryId: categoryId,
        isReimbursable: form.value.isReimbursable,
      })
      ElMessage.success('提交成功')
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'INCOME') {
      if (!form.value.parentAccountId || !form.value.accountId || !form.value.amount || !form.value.occurredAt || !form.value.category || (Array.isArray(form.value.category) && form.value.category.length === 0)) {
        ElMessage.error('请填写完整信息（包括父账户和子账户）')
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
      await ledgerApi.createTransaction({
        txnType: selectedType.value,
        postings,
        note: form.value.note || undefined,
        requestedAt: form.value.occurredAt,
        categoryId: categoryId,
      })
      ElMessage.success('提交成功')
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'REPAYMENT') {
      if (!form.value.parentAccountId || !form.value.accountId || !form.value.creditAccountId || !form.value.amount || !form.value.occurredAt) {
        ElMessage.error('请填写完整信息（包括还款账户、子账户和信贷账户）')
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
      await ledgerApi.createTransaction({
        txnType: 'TRANSFER_OUT',
        postings,
        note: form.value.note || '还款',
        requestedAt: form.value.occurredAt,
      })
      ElMessage.success('提交成功')
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'TRANSFER_OUT' || selectedType.value === 'TRANSFER_IN') {
      if (!form.value.fromParentAccountId || !form.value.fromAccountId || 
          !form.value.toParentAccountId || !form.value.toAccountId || 
          !form.value.amount || !form.value.occurredAt) {
        ElMessage.error('请填写完整信息（包括转出和转入的父账户、子账户）')
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
      await ledgerApi.createTransaction({
        txnType: selectedType.value,
        postings,
        note: form.value.note || undefined,
        requestedAt: form.value.occurredAt,
      })
      ElMessage.success('提交成功')
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'BUY' || selectedType.value === 'SUBSCRIPTION') {
      if (!form.value.channel || !form.value.productId || !form.value.parentAccountId || !form.value.accountId || !form.value.amount || !form.value.orderType || !form.value.requestedAt) {
        ElMessage.error('请填写完整信息（包括场内/场外、资金来源父账户和子账户）')
        return
      }

      // 如果选择了关联产品的账户且有子账户，必须配置fundingLines
      if (selectedBuyAccount.value?.linkedProductId && buyChildAccounts.value.length > 0) {
        if (buyFundingLines.value.length === 0) {
          ElMessage.error('请至少添加一个子账户并分配金额')
          return
        }
        // 校验所有行都填写完整
        for (let i = 0; i < buyFundingLines.value.length; i++) {
          const line = buyFundingLines.value[i]
          if (!line.accountId) {
            ElMessage.error(`第 ${i + 1} 行请选择子账户`)
            return
          }
          if (line.amount == null || line.amount <= 0) {
            ElMessage.error(`第 ${i + 1} 行请填写买入金额`)
            return
          }
        }
        // 校验总金额是否匹配
        if (buyAllocationError.value) {
          ElMessage.error(buyAllocationError.value)
          return
        }
      }

      // 获取产品信息
      const product = await productApi.getProduct(form.value.productId)
      if (!product) {
        ElMessage.error('产品不存在')
        return
      }

      // 获取净值（用于计算份额）
      let nav = form.value.nav
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
        ElMessage.error('无法获取产品净值，请手动输入净值日期')
        return
      }

      // 构建fundingLines（多子账户或单账户）
      let finalFundingLines: Array<{ accountId: number; amount: number }> = []
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

      // 计算总金额和份额
      const totalAmount = finalFundingLines.reduce((sum, fl) => sum + fl.amount, 0)
      const totalShares = totalAmount / nav
      const cost = totalAmount - (form.value.fee || 0)  // 成本 = 总金额 - 手续费

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

      // 对于场外（OTC）产品，需要同时创建订单和记账
      if (form.value.channel === 'OTC') {
        try {
          // 先创建订单（占用资金）
          await orderApi.createOrder({
            productId: form.value.productId,
            orderType: form.value.orderType,
            amount: totalAmount,
            fundingLines: finalFundingLines,
            note: autoNote,
          })
          
          // 然后进行记账（立即扣款）
          await ledgerApi.createTransaction({
            txnType: form.value.orderType,
            productId: form.value.productId,
            postings,
            note: autoNote,
            requestedAt: form.value.requestedAt,
          })
          
          ElMessage.success('订单创建成功，已记账扣款')
        } catch (error: any) {
          ElMessage.error(error.message || '操作失败')
          return
        }
      } else {
        // 场内（EXCHANGE）产品，直接记账
        await ledgerApi.createTransaction({
          txnType: form.value.orderType,
          productId: form.value.productId,
          postings,
          note: autoNote,
          requestedAt: form.value.requestedAt,
        })
        ElMessage.success('买入/申购记录成功')
      }
      
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'SELL' || selectedType.value === 'REDEMPTION') {
      if (!form.value.channel || !form.value.productId || !form.value.parentAccountId || !form.value.accountId || !form.value.shares || !form.value.orderType || !form.value.requestedAt) {
        ElMessage.error('请填写完整信息（包括场内/场外、到账父账户和子账户）')
        return
      }

      // 如果选择了关联产品的账户且有子账户，必须配置fundingLines
      if (selectedSellAccount.value?.linkedProductId && sellChildAccounts.value.length > 0) {
        if (sellFundingLines.value.length === 0) {
          ElMessage.error('请至少添加一个子账户并分配份额')
          return
        }
        // 校验所有行都填写完整
        for (let i = 0; i < sellFundingLines.value.length; i++) {
          const line = sellFundingLines.value[i]
          if (!line.accountId) {
            ElMessage.error(`第 ${i + 1} 行请选择子账户`)
            return
          }
          if (line.shares == null || line.shares <= 0) {
            ElMessage.error(`第 ${i + 1} 行请填写卖出份额`)
            return
          }
        }
        // 校验总份额是否匹配
        if (sellAllocationError.value) {
          ElMessage.error(sellAllocationError.value)
          return
        }
      }

      // 获取产品信息
      const product = await productApi.getProduct(form.value.productId)
      if (!product) {
        ElMessage.error('产品不存在')
        return
      }

      // 获取净值（用于计算金额）
      let nav = form.value.nav
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
        ElMessage.error('无法获取产品净值，请手动输入净值日期')
        return
      }

      // 构建fundingLines（多子账户或单账户）
      let finalFundingLines: Array<{ accountId: number; shares: number }> = []
      if (selectedSellAccount.value?.linkedProductId && sellChildAccounts.value.length > 0 && sellFundingLines.value.length > 0) {
        finalFundingLines = sellFundingLines.value
          .filter((fl) => fl.accountId != null && fl.shares != null)
          .map((fl) => ({
            accountId: fl.accountId!,
            shares: fl.shares!,
          }))
      } else {
        finalFundingLines = [{
          accountId: form.value.accountId!,
          shares: form.value.shares!,
        }]
      }

      // 计算总份额和金额
      const totalShares = finalFundingLines.reduce((sum, fl) => sum + fl.shares, 0)
      const totalAmount = totalShares * nav
      const netAmount = totalAmount - (form.value.fee || 0)  // 净到账金额

      // 生成分录
      const postings: any[] = []

      // CASH DEBIT（现金增加，按fundingLines拆分）
      // 按份额比例分配金额到各个子账户
      for (const fundingLine of finalFundingLines) {
        const accountAmount = (fundingLine.shares / totalShares) * netAmount
        postings.push({
          postingType: 'DEBIT',
          accountId: fundingLine.accountId,
          accountType: 'CASH',
          amount: accountAmount,
          currency: 'CNY',
        })
      }

      // POSITION CREDIT（持仓减少，按平均成本法计算成本扣减）
      // 简化处理：使用净到账金额作为成本扣减（实际应该从持仓快照计算平均成本）
      const costDeduction = netAmount
      postings.push({
        postingType: 'CREDIT',
        accountId: form.value.accountId!,  // 后端会自动替换为持仓账户
        accountType: 'POSITION',
        amount: costDeduction,
        shares: totalShares,
        currency: product.currency || 'CNY',
      })

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
      const orderTypeLabel = form.value.orderType === 'SELL' ? '卖出' : '赎回'
      const autoNote = `${orderTypeLabel}${channelLabel}${product.productName}`

      await ledgerApi.createTransaction({
        txnType: form.value.orderType,
        productId: form.value.productId,
        postings,
        note: autoNote,
        requestedAt: form.value.requestedAt,
      })
      ElMessage.success('卖出/赎回记录成功')
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'BOND_REPO') {
      if (!form.value.parentAccountId || !form.value.accountId || !form.value.amount || !form.value.occurredAt || !form.value.repoDays) {
        ElMessage.error('请填写完整信息（包括账户父账户和子账户）')
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
      await ledgerApi.createTransaction({
        txnType: 'BOND_REPO',
        postings,
        note: form.value.note || `逆回购 ${form.value.repoDays}天期${form.value.repoRate ? `，年化利率${form.value.repoRate}%` : ''}`,
        requestedAt: form.value.occurredAt,
      })
      ElMessage.success('逆回购记录成功')
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'CUSTODY_TRANSFER') {
      if (!form.value.productId || !form.value.shares || !form.value.transferDate || form.value.transferOutPrice === undefined || form.value.transferInPrice === undefined) {
        ElMessage.error('请填写完整信息')
        return
      }
      // 调用转托管API（后端使用 transferPrice，这里使用 transferOutPrice）
      await ledgerApi.createCustodyTransfer({
        productId: form.value.productId,
        shares: form.value.shares,
        transferOutPrice: form.value.transferOutPrice,
        transferInPrice: form.value.transferInPrice,
        transferDate: form.value.transferDate,
        note: form.value.note || undefined,
      })
      ElMessage.success('转托管成功')
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'REFUND') {
      if (!form.value.relatedTxnId || !form.value.parentAccountId || !form.value.accountId || !form.value.amount) {
        ElMessage.error('请填写完整信息（包括退款账户父账户和子账户）')
        return
      }
      await ledgerApi.refund(form.value.relatedTxnId, {
        refundAmount: form.value.amount,
        accountId: form.value.accountId,
        note: form.value.note || undefined,
      })
      ElMessage.success('退款成功')
      emit('success')
      handleClose()
      return
    } else if (selectedType.value === 'ADJUST') {
      if (!form.value.parentAccountId || !form.value.accountId || form.value.amount === undefined) {
        ElMessage.error('请填写完整信息（包括账户父账户和子账户）')
        return
      }
      // 调整：需要计算差额，生成ADJUST分录
      ElMessage.warning('余额调整功能建议使用账户管理页面的余额调整功能')
      return
    }

    // 其他类型暂不支持
    ElMessage.error('不支持的业务类型')
    return
  } catch (error: any) {
    ElMessage.error(error.message || '提交失败')
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
