<template>
  <div class="quick-entry-page">
    <van-nav-bar title="快速录入" fixed placeholder />

    <div class="page-container">
      <!-- 快速操作卡片 -->
      <div class="quick-cards">
        <div class="quick-card expense" @click="showExpenseForm = true">
          <van-icon name="minus" size="32" />
          <div class="card-label">消费</div>
        </div>
        <div class="quick-card income" @click="showIncomeForm = true">
          <van-icon name="plus" size="32" />
          <div class="card-label">收入</div>
        </div>
      </div>

      <!-- 统一记账入口 -->
      <div class="section-title">
        <span>更多记账类型</span>
        <van-icon name="arrow-down" />
      </div>

      <van-cell-group inset>
        <van-cell
          v-for="type in transactionTypes"
          :key="type.value"
          :title="type.label"
          :icon="type.icon"
          is-link
          @click="showUnifiedForm(type.value)"
        />
      </van-cell-group>

      <!-- 最近记录 -->
      <div class="recent-section" v-if="recentTransactions.length > 0">
        <div class="section-title">最近记录</div>
        <van-cell-group inset>
          <van-cell
            v-for="txn in recentTransactions"
            :key="txn.txnId"
            is-link
            @click="viewTransactionDetail(txn)"
          >
            <template #title>
              <div class="txn-title-row">
                <span class="txn-type-label" :class="getTxnTypeClass(txn.txnType)">{{ getTxnTypeLabel(txn.txnType) }}</span>
                <span class="txn-note-inline" v-if="txn.note">{{ txn.note }}</span>
              </div>
            </template>
            <template #label>
              <div class="txn-sub-row">
                <span>{{ formatDate(txn.requestedAt) }}</span>
                <span v-if="(txn as any).leafAccountName" class="txn-account">{{ (txn as any).leafAccountName }}</span>
              </div>
              <div
                v-if="getCategoryText(txn)"
                class="txn-sub-row secondary"
              >
                <span class="txn-category">{{ getCategoryText(txn) }}</span>
              </div>
            </template>
            <template #value>
              <span class="txn-amount" :class="getTxnTypeClass(txn.txnType)">{{ formatCurrency(getTxnAmount(txn)) }}</span>
            </template>
          </van-cell>
        </van-cell-group>
      </div>
    </div>

    <!-- ===================== 消费快速录入弹窗 ===================== -->
    <van-popup
      v-model:show="showExpenseForm"
      position="bottom"
      :style="{ height: '75%' }"
      round
      closeable
    >
      <div class="form-popup">
        <h3 class="popup-title">快速消费</h3>
        <van-form @submit="handleQuickExpense">
          <van-cell-group inset>
            <van-field
              name="occurredAt"
              label="发生时间"
              readonly
            >
              <template #input>
                <input
                  type="datetime-local"
                  :value="toDatetimeLocal(expenseForm.occurredAt)"
                  @input="expenseForm.occurredAt = fromDatetimeLocal(($event.target as HTMLInputElement).value)"
                  class="native-datetime-input"
                />
              </template>
            </van-field>
            <van-field
              :model-value="expenseCategoryLabel"
              name="category"
              label="分类"
              placeholder="选择分类"
              readonly
              is-link
              @click="openCategoryPicker('expense')"
              :rules="[{ required: true, message: '请选择分类' }]"
            />
            <van-field
              :model-value="getAccountNameById(expenseForm.accountId)"
              name="accountId"
              label="账户"
              placeholder="选择账户"
              readonly
              is-link
              @click="openAccountCascader('expense')"
              :rules="[{ required: true, message: '请选择账户' }]"
            />
            <van-field
              v-model="expenseForm.amount"
              name="amount"
              label="金额"
              placeholder="请输入金额"
              type="number"
              :rules="[{ required: true, message: '请输入金额' }, { validator: validateAmount }]"
            />
            <van-field
              v-model="expenseForm.note"
              name="note"
              label="备注"
              placeholder="选填"
              type="textarea"
              rows="2"
            />
          </van-cell-group>
          <div class="form-actions">
            <van-button round block type="primary" native-type="submit" :loading="submitting">
              确认
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <!-- ===================== 收入快速录入弹窗 ===================== -->
    <van-popup
      v-model:show="showIncomeForm"
      position="bottom"
      :style="{ height: '75%' }"
      round
      closeable
    >
      <div class="form-popup">
        <h3 class="popup-title">快速收入</h3>
        <van-form @submit="handleQuickIncome">
          <van-cell-group inset>
            <van-field
              name="occurredAt"
              label="发生时间"
              readonly
            >
              <template #input>
                <input
                  type="datetime-local"
                  :value="toDatetimeLocal(incomeForm.occurredAt)"
                  @input="incomeForm.occurredAt = fromDatetimeLocal(($event.target as HTMLInputElement).value)"
                  class="native-datetime-input"
                />
              </template>
            </van-field>
            <van-field
              :model-value="incomeCategoryLabel"
              name="category"
              label="分类"
              placeholder="选择分类"
              readonly
              is-link
              @click="openCategoryPicker('income')"
              :rules="[{ required: true, message: '请选择分类' }]"
            />
            <van-field
              :model-value="getAccountNameById(incomeForm.accountId)"
              name="accountId"
              label="账户"
              placeholder="选择账户"
              readonly
              is-link
              @click="openAccountCascader('income')"
              :rules="[{ required: true, message: '请选择账户' }]"
            />
            <van-field
              v-model="incomeForm.amount"
              name="amount"
              label="金额"
              placeholder="请输入金额"
              type="number"
              :rules="[{ required: true, message: '请输入金额' }, { validator: validateAmount }]"
            />
            <van-field
              v-model="incomeForm.note"
              name="note"
              label="备注"
              placeholder="选填"
              type="textarea"
              rows="2"
            />
          </van-cell-group>
          <div class="form-actions">
            <van-button round block type="primary" native-type="submit" :loading="submitting">
              确认
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <!-- ===================== 转账弹窗 ===================== -->
    <van-popup
      v-model:show="showTransferForm"
      position="bottom"
      :style="{ height: '75%' }"
      round
      closeable
    >
      <div class="form-popup">
        <h3 class="popup-title">转账</h3>
        <van-form @submit="handleTransferSubmit">
          <van-cell-group inset>
            <van-field
              name="occurredAt"
              label="发生时间"
              readonly
            >
              <template #input>
                <input
                  type="datetime-local"
                  :value="toDatetimeLocal(transferForm.occurredAt)"
                  @input="transferForm.occurredAt = fromDatetimeLocal(($event.target as HTMLInputElement).value)"
                  class="native-datetime-input"
                />
              </template>
            </van-field>
            <van-field
              :model-value="getAccountNameById(transferForm.fromAccountId)"
              name="fromAccount"
              label="转出账户"
              placeholder="选择转出账户"
              readonly
              is-link
              @click="openAccountCascader('transferFrom')"
              :rules="[{ required: true, message: '请选择转出账户' }]"
            />
            <van-field
              :model-value="getAccountNameById(transferForm.toAccountId)"
              name="toAccount"
              label="转入账户"
              placeholder="选择转入账户"
              readonly
              is-link
              @click="openAccountCascader('transferTo')"
              :rules="[{ required: true, message: '请选择转入账户' }]"
            />
            <van-field
              v-model="transferForm.amount"
              name="amount"
              label="金额"
              placeholder="请输入金额"
              type="number"
              :rules="[{ required: true, message: '请输入金额' }, { validator: validateAmount }]"
            />
            <van-field
              v-model="transferForm.note"
              name="note"
              label="备注"
              placeholder="选填，例如：卡间转账"
              type="textarea"
              rows="2"
            />
          </van-cell-group>
          <div class="form-actions">
            <van-button round block type="primary" native-type="submit" :loading="submitting">
              提交
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <!-- ===================== 还款弹窗 ===================== -->
    <van-popup
      v-model:show="showRepaymentForm"
      position="bottom"
      :style="{ height: '75%' }"
      round
      closeable
    >
      <div class="form-popup">
        <h3 class="popup-title">还款</h3>
        <van-form @submit="handleRepaymentSubmit">
          <van-cell-group inset>
            <van-field
              name="occurredAt"
              label="发生时间"
              readonly
            >
              <template #input>
                <input
                  type="datetime-local"
                  :value="toDatetimeLocal(repaymentForm.occurredAt)"
                  @input="repaymentForm.occurredAt = fromDatetimeLocal(($event.target as HTMLInputElement).value)"
                  class="native-datetime-input"
                />
              </template>
            </van-field>
            <van-field
              :model-value="getAccountNameById(repaymentForm.cashAccountId)"
              name="repayAccount"
              label="还款账户"
              placeholder="选择还款账户"
              readonly
              is-link
              @click="openAccountCascader('repayCash')"
              :rules="[{ required: true, message: '请选择还款账户' }]"
            />
            <van-field
              :model-value="getAccountNameById(repaymentForm.creditAccountId)"
              name="creditAccount"
              label="信贷账户"
              placeholder="选择要还的信用卡/借款"
              readonly
              is-link
              @click="openAccountCascader('repayCredit')"
              :rules="[{ required: true, message: '请选择信贷账户' }]"
            />
            <van-field
              v-model="repaymentForm.amount"
              name="amount"
              label="还款金额"
              placeholder="请输入还款金额"
              type="number"
              :rules="[{ required: true, message: '请输入还款金额' }, { validator: validateAmount }]"
            />
            <van-field
              v-model="repaymentForm.note"
              name="note"
              label="备注"
              placeholder="选填，例如：信用卡还款"
              type="textarea"
              rows="2"
            />
          </van-cell-group>
          <div class="form-actions">
            <van-button round block type="primary" native-type="submit" :loading="submitting">
              提交
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <!-- ===================== 买入弹窗（场内 + 场外） ===================== -->
    <van-popup
      v-model:show="showBuyForm"
      position="bottom"
      :style="{ height: '85%' }"
      round
      closeable
    >
      <div class="form-popup">
        <h3 class="popup-title">买入</h3>

        <!-- 场内/场外切换 -->
        <div class="channel-switch">
          <button
            type="button"
            :class="['channel-btn', buyChannel === 'EXCHANGE' ? 'active' : '']"
            @click="buyChannel = 'EXCHANGE'"
          >场内</button>
          <button
            type="button"
            :class="['channel-btn', buyChannel === 'OTC' ? 'active' : '']"
            @click="buyChannel = 'OTC'"
          >场外</button>
        </div>

        <van-form @submit="handleBuySubmit">
          <van-cell-group inset>
            <!-- 产品 -->
            <van-field
              :model-value="buyProductLabel"
              name="product"
              label="产品"
              placeholder="选择产品"
              readonly
              is-link
              @click="openProductPicker('buy')"
              :rules="[{ required: true, message: '请选择产品' }]"
            />

            <!-- ===== 场内买入 ===== -->
            <template v-if="buyChannel === 'EXCHANGE'">
              <van-field name="requestedAt" label="交易时间" readonly>
                <template #input>
                  <input
                    type="datetime-local"
                    :value="toDatetimeLocal(buyForm.requestedAt)"
                    @input="buyForm.requestedAt = fromDatetimeLocal(($event.target as HTMLInputElement).value)"
                    class="native-datetime-input"
                  />
                </template>
              </van-field>
              <van-field
                :model-value="getAccountNameById(buyForm.cashAccountId)"
                name="cashAccount"
                label="资金账户"
                placeholder="选择资金来源账户"
                readonly
                is-link
                @click="openAccountCascader('buyCash')"
                :rules="[{ required: true, message: '请选择资金账户' }]"
              />
              <van-field
                v-model="buyForm.price"
                name="price"
                label="成交价"
                placeholder="输入成交价格"
                type="number"
                :rules="[{ required: true, message: '请输入成交价格' }, { validator: validateAmount }]"
              />
              <van-field
                v-model="buyForm.shares"
                name="shares"
                label="买入份额"
                placeholder="输入买入份额"
                type="number"
                :rules="[{ required: true, message: '请输入买入份额' }, { validator: validateAmount }]"
              />
              <van-field
                v-model="buyForm.fee"
                name="fee"
                label="手续费"
                placeholder="可选，默认 0"
                type="number"
              />
              <!-- 成交金额预览 -->
              <van-field v-if="buyForm.price && buyForm.shares" label="成交金额" readonly>
                <template #input>
                  <span style="color: var(--primary); font-weight: 600;">
                    {{ (parseFloat(buyForm.price) * parseFloat(buyForm.shares)).toFixed(2) }} 元
                    <span v-if="buyForm.fee" style="color: var(--muted); font-size: 12px; margin-left: 4px;">
                      (含手续费共 {{ (parseFloat(buyForm.price) * parseFloat(buyForm.shares) + parseFloat(buyForm.fee || '0')).toFixed(2) }} 元)
                    </span>
                  </span>
                </template>
              </van-field>
            </template>

            <!-- ===== 场外申购 ===== -->
            <template v-else>
              <van-field name="requestedAt" label="发起时间" readonly>
                <template #input>
                  <input
                    type="datetime-local"
                    :value="toDatetimeLocal(buyForm.requestedAt)"
                    @input="buyForm.requestedAt = fromDatetimeLocal(($event.target as HTMLInputElement).value)"
                    class="native-datetime-input"
                  />
                </template>
              </van-field>
              <van-field name="confirmDate" label="确认日期" readonly>
                <template #input>
                  <input
                    type="date"
                    :value="buyForm.confirmDate"
                    @input="buyForm.confirmDate = ($event.target as HTMLInputElement).value"
                    class="native-datetime-input"
                  />
                </template>
              </van-field>
              <van-field name="navDate" label="净值日期" readonly>
                <template #input>
                  <input
                    type="date"
                    :value="buyForm.navDate"
                    @input="buyForm.navDate = ($event.target as HTMLInputElement).value"
                    class="native-datetime-input"
                  />
                </template>
              </van-field>
              <van-field
                v-model="buyForm.nav"
                name="nav"
                label="净值"
                placeholder="自动获取或手动输入"
                type="number"
              />
              <van-field
                :model-value="getAccountNameById(buyForm.cashAccountId)"
                name="cashAccount"
                label="资金账户"
                placeholder="选择资金来源账户"
                readonly
                is-link
                @click="openAccountCascader('buyCash')"
                :rules="[{ required: true, message: '请选择资金账户' }]"
              />
              <van-field
                v-model="buyForm.otcAmount"
                name="otcAmount"
                label="申购金额"
                placeholder="输入申购金额"
                type="number"
                :rules="[{ required: true, message: '请输入申购金额' }, { validator: validateAmount }]"
              />
              <van-field
                v-model="buyForm.fee"
                name="fee"
                label="手续费"
                placeholder="可选，默认 0"
                type="number"
              />
              <!-- 预计份额 -->
              <van-field v-if="buyForm.otcAmount && buyForm.nav" label="预计份额" readonly>
                <template #input>
                  <span style="color: var(--primary); font-weight: 600;">
                    {{ ((parseFloat(buyForm.otcAmount) - parseFloat(buyForm.fee || '0')) / parseFloat(buyForm.nav)).toFixed(4) }} 份
                  </span>
                </template>
              </van-field>
            </template>

            <van-field
              v-model="buyForm.note"
              name="note"
              label="备注"
              placeholder="选填"
              type="textarea"
              rows="2"
            />
          </van-cell-group>
          <div class="form-actions">
            <van-button round block type="primary" native-type="submit" :loading="submitting">
              提交
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <!-- ===================== 卖出弹窗（场内 + 场外） ===================== -->
    <van-popup
      v-model:show="showSellForm"
      position="bottom"
      :style="{ height: '85%' }"
      round
      closeable
    >
      <div class="form-popup">
        <h3 class="popup-title">卖出</h3>

        <!-- 场内/场外切换 -->
        <div class="channel-switch">
          <button
            type="button"
            :class="['channel-btn', sellChannel === 'EXCHANGE' ? 'active' : '']"
            @click="sellChannel = 'EXCHANGE'"
          >场内</button>
          <button
            type="button"
            :class="['channel-btn', sellChannel === 'OTC' ? 'active' : '']"
            @click="sellChannel = 'OTC'"
          >场外</button>
        </div>

        <van-form @submit="handleSellSubmit">
          <van-cell-group inset>
            <!-- 产品 -->
            <van-field
              :model-value="sellProductLabel"
              name="product"
              label="产品"
              placeholder="选择产品"
              readonly
              is-link
              @click="openProductPicker('sell')"
              :rules="[{ required: true, message: '请选择产品' }]"
            />

            <!-- ===== 场内卖出 ===== -->
            <template v-if="sellChannel === 'EXCHANGE'">
              <van-field name="requestedAt" label="交易时间" readonly>
                <template #input>
                  <input
                    type="datetime-local"
                    :value="toDatetimeLocal(sellForm.requestedAt)"
                    @input="sellForm.requestedAt = fromDatetimeLocal(($event.target as HTMLInputElement).value)"
                    class="native-datetime-input"
                  />
                </template>
              </van-field>
              <van-field
                :model-value="getAccountNameById(sellForm.cashAccountId)"
                name="cashAccount"
                label="到账账户"
                placeholder="选择到账账户"
                readonly
                is-link
                @click="openAccountCascader('sellCash')"
                :rules="[{ required: true, message: '请选择到账账户' }]"
              />
              <van-field
                v-model="sellForm.price"
                name="price"
                label="成交价"
                placeholder="输入成交价格"
                type="number"
                :rules="[{ required: true, message: '请输入成交价格' }, { validator: validateAmount }]"
              />
              <van-field
                v-model="sellForm.shares"
                name="shares"
                label="卖出份额"
                placeholder="输入卖出份额"
                type="number"
                :rules="[{ required: true, message: '请输入卖出份额' }, { validator: validateAmount }]"
              />
              <van-field
                v-model="sellForm.fee"
                name="fee"
                label="手续费"
                placeholder="可选，默认 0"
                type="number"
              />
              <!-- 成交金额预览 -->
              <van-field v-if="sellForm.price && sellForm.shares" label="成交金额" readonly>
                <template #input>
                  <span style="color: #f59e0b; font-weight: 600;">
                    {{ (parseFloat(sellForm.price) * parseFloat(sellForm.shares)).toFixed(2) }} 元
                    <span v-if="sellForm.fee" style="color: var(--muted); font-size: 12px; margin-left: 4px;">
                      (扣除手续费后到账 {{ (parseFloat(sellForm.price) * parseFloat(sellForm.shares) - parseFloat(sellForm.fee || '0')).toFixed(2) }} 元)
                    </span>
                  </span>
                </template>
              </van-field>
            </template>

            <!-- ===== 场外赎回 ===== -->
            <template v-else>
              <van-field name="requestedAt" label="发起时间" readonly>
                <template #input>
                  <input
                    type="datetime-local"
                    :value="toDatetimeLocal(sellForm.requestedAt)"
                    @input="sellForm.requestedAt = fromDatetimeLocal(($event.target as HTMLInputElement).value)"
                    class="native-datetime-input"
                  />
                </template>
              </van-field>
              <van-field name="confirmDate" label="确认日期" readonly>
                <template #input>
                  <input
                    type="date"
                    :value="sellForm.confirmDate"
                    @input="sellForm.confirmDate = ($event.target as HTMLInputElement).value"
                    class="native-datetime-input"
                  />
                </template>
              </van-field>
              <van-field name="navDate" label="净值日期" readonly>
                <template #input>
                  <input
                    type="date"
                    :value="sellForm.navDate"
                    @input="sellForm.navDate = ($event.target as HTMLInputElement).value"
                    class="native-datetime-input"
                  />
                </template>
              </van-field>
              <van-field
                v-model="sellForm.nav"
                name="nav"
                label="净值"
                placeholder="自动获取或手动输入"
                type="number"
              />
              <van-field
                v-model="sellForm.shares"
                name="shares"
                label="赎回份额"
                placeholder="输入赎回份额"
                type="number"
                :rules="[{ required: true, message: '请输入赎回份额' }, { validator: validateAmount }]"
              />
              <van-field
                v-model="sellForm.fee"
                name="fee"
                label="手续费"
                placeholder="可选，默认 0"
                type="number"
              />
              <van-field
                :model-value="getAccountNameById(sellForm.cashAccountId)"
                name="cashAccount"
                label="到账账户"
                placeholder="选择到账账户"
                readonly
                is-link
                @click="openAccountCascader('sellCash')"
                :rules="[{ required: true, message: '请选择到账账户' }]"
              />
              <!-- 预计到账金额 -->
              <van-field v-if="sellForm.shares && sellForm.nav" label="预计到账" readonly>
                <template #input>
                  <span style="color: #f59e0b; font-weight: 600;">
                    {{ (parseFloat(sellForm.shares) * parseFloat(sellForm.nav) - parseFloat(sellForm.fee || '0')).toFixed(2) }} 元
                  </span>
                </template>
              </van-field>
            </template>

            <van-field
              v-model="sellForm.note"
              name="note"
              label="备注"
              placeholder="选填"
              type="textarea"
              rows="2"
            />
          </van-cell-group>
          <div class="form-actions">
            <van-button round block type="primary" native-type="submit" :loading="submitting">
              提交
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <!-- ===================== 账户层级选择器（van-cascader） ===================== -->
    <van-popup v-model:show="showAccountCascader" position="bottom" round>
      <van-cascader
        v-model="accountCascaderValue"
        :title="accountCascaderTitle"
        :options="currentAccountCascaderOptions"
        @finish="handleAccountCascaderFinish"
        @close="showAccountCascader = false"
      />
    </van-popup>

    <!-- ===================== 产品选择器 ===================== -->
    <van-popup v-model:show="showProductPicker" position="bottom" round>
      <van-picker
        :columns="productPickerColumns"
        @confirm="handleProductSelect"
        @cancel="showProductPicker = false"
      />
    </van-popup>

    <!-- ===================== 分类选择器（支出/收入复用） ===================== -->
    <van-popup v-model:show="showCategoryPicker" position="bottom" round>
      <div class="category-picker">
        <div class="category-picker-header">
          <span class="category-picker-title">
            选择{{ categoryPickerFor === 'expense' ? '支出' : '收入' }}分类
          </span>
        </div>
        <van-cascader
          v-model="categoryPickerValue"
          :options="categoryPickerOptions"
          @finish="handleCategoryFinish"
        />
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ledgerApi, accountApi, productApi, orderApi } from '@wealth-hub/shared'
import { formatCurrency, formatDate, getTxnTypeLabel, buildAccountTree } from '@wealth-hub/shared'
import {
  expenseCategories,
  incomeCategories,
  findCategoryById,
  getCategoryGroups,
  type Category,
} from '@wealth-hub/shared'
import { showSuccessToast, showFailToast } from 'vant'
import type { Account, LedgerTxn, ProductMaster } from '@wealth-hub/shared'

// ─── 弹窗显示状态 ───
const showExpenseForm = ref(false)
const showIncomeForm = ref(false)
const showTransferForm = ref(false)
const showBuyForm = ref(false)
const showSellForm = ref(false)
const showRepaymentForm = ref(false)
const showProductPicker = ref(false)
const showCategoryPicker = ref(false)
const showAccountCascader = ref(false)
const submitting = ref(false)

// ─── 场内/场外渠道 ───
const buyChannel = ref<'EXCHANGE' | 'OTC'>('EXCHANGE')
const sellChannel = ref<'EXCHANGE' | 'OTC'>('EXCHANGE')

// ─── 分类选择器 ───
type CategoryPickerScene = 'expense' | 'income'
const categoryPickerFor = ref<CategoryPickerScene | null>(null)
const categoryPickerOptions = ref<any[]>([])
const categoryPickerValue = ref<string | number | undefined>(undefined)

// ─── 账户选择器（层级 cascader） ───
type AccountPickerScene =
  | 'expense'
  | 'income'
  | 'transferFrom'
  | 'transferTo'
  | 'buyCash'
  | 'sellCash'
  | 'repayCash'
  | 'repayCredit'
const accountPickerFor = ref<AccountPickerScene | null>(null)
const accountCascaderValue = ref<string | number | undefined>(undefined)
const accountCascaderTitle = ref('选择账户')

// ─── 产品选择器 ───
type ProductPickerScene = 'buy' | 'sell'
const productPickerFor = ref<ProductPickerScene | null>(null)
const productPickerColumns = ref<any[]>([])

// ─── 数据源 ───
const accounts = ref<Account[]>([])
const recentTransactions = ref<LedgerTxn[]>([])
const products = ref<ProductMaster[]>([])

// ─── 分类选择结果 ───
const expenseCategoryId = ref<number | null>(null)
const incomeCategoryId = ref<number | null>(null)
const expenseCategoryLabel = ref('')
const incomeCategoryLabel = ref('')

// ─── 表单数据 ───
const expenseForm = ref({
  accountId: undefined as number | undefined,
  amount: '',
  note: '',
  occurredAt: getNowString(),
})

const incomeForm = ref({
  accountId: undefined as number | undefined,
  amount: '',
  note: '',
  occurredAt: getNowString(),
})

const transferForm = ref({
  fromAccountId: undefined as number | undefined,
  toAccountId: undefined as number | undefined,
  amount: '',
  note: '',
  occurredAt: getNowString(),
})

const repaymentForm = ref({
  cashAccountId: undefined as number | undefined,
  creditAccountId: undefined as number | undefined,
  amount: '',
  note: '',
  occurredAt: getNowString(),
})

const buyForm = ref({
  productId: undefined as number | undefined,
  cashAccountId: undefined as number | undefined,
  price: '',
  shares: '',
  fee: '',
  note: '',
  requestedAt: getNowString(),
  // OTC 专用
  otcAmount: '',
  nav: '',
  navDate: '',
  confirmDate: '',
})
const buyProductLabel = ref('')

const sellForm = ref({
  productId: undefined as number | undefined,
  cashAccountId: undefined as number | undefined,
  price: '',
  shares: '',
  fee: '',
  note: '',
  requestedAt: getNowString(),
  // OTC 专用
  nav: '',
  navDate: '',
  confirmDate: '',
})
const sellProductLabel = ref('')

// ─── 分类构建 ───
function buildCategoryOptions(categories: Category[]) {
  const groups = getCategoryGroups(categories)
  return groups.map((group) => {
    if (group.categories.length === 1 && !group.categories[0].categoryL2) {
      const cat = group.categories[0]
      return { text: cat.categoryL1, value: cat.id }
    }
    return {
      text: group.categoryL1,
      value: group.categoryL1,
      children: group.categories.map((cat) => ({
        text: cat.categoryL2 || cat.categoryL1,
        value: cat.id,
      })),
    }
  })
}

const expenseCategoryOptions = computed(() => buildCategoryOptions(expenseCategories))
const incomeCategoryOptions = computed(() => buildCategoryOptions(incomeCategories))

// ─── 交易类型列表 ───
const transactionTypes = [
  { label: '转账', value: 'TRANSFER', icon: 'exchange' },
  { label: '买入', value: 'BUY', icon: 'arrow-up' },
  { label: '卖出', value: 'SELL', icon: 'arrow-down' },
  { label: '还款', value: 'REPAYMENT', icon: 'credit-pay' },
]

// ─── 信贷账户判断 ───
function isCreditAccountType(accountType: string): boolean {
  return accountType === 'CREDIT_CARD' ||
    accountType === 'HUABEI' ||
    accountType === 'BAITIAO' ||
    accountType === 'LOAN'
}

// ─── 计算父账户余额（排除信贷子账户） ───
function calculateParentBalances(account: Account): { balance: number; credit: number } {
  if (account.children && account.children.length > 0) {
    let balance = 0
    let credit = 0
    account.children.forEach((child) => {
      if (isCreditAccountType(child.accountType)) {
        credit += (child.balance || 0)
      } else {
        balance += (child.balance || 0)
      }
    })
    return { balance, credit }
  }
  if (isCreditAccountType(account.accountType)) {
    return { balance: 0, credit: account.balance || 0 }
  }
  return { balance: account.balance || 0, credit: 0 }
}

// ─── 构建账户树 ───
const accountTree = computed(() => buildAccountTree(accounts.value))

// ─── 账户层级选项（cascader 格式，展示余额，与 PC 端一致） ───
const realAccountCascaderOptions = computed(() => {
  return accountTree.value
    .filter((acc) => acc.accountKind === 'REAL')
    .map((parentAcc) => buildCascaderNode(parentAcc))
})

// ─── 信贷账户选项：遍历树，只取信贷类型的叶子账户 ───
const creditAccountCascaderOptions = computed(() => {
  const creditList: any[] = []
  function traverse(accs: Account[]) {
    accs.forEach((acc) => {
      if (
        acc.accountKind === 'REAL' &&
        isCreditAccountType(acc.accountType)
      ) {
        // 叶子节点才加入列表
        if (!acc.children || acc.children.length === 0) {
          creditList.push({
            text: `${acc.accountName} (欠${formatCurrency(acc.balance || 0)})`,
            value: acc.id,
          })
        }
      }
      if (acc.children && acc.children.length > 0) {
        traverse(acc.children)
      }
    })
  }
  traverse(accountTree.value)
  return creditList
})

const currentAccountCascaderOptions = computed(() => {
  if (accountPickerFor.value === 'repayCredit') {
    return creditAccountCascaderOptions.value
  }
  return realAccountCascaderOptions.value
})

function buildCascaderNode(acc: Account): any {
  if (acc.children && acc.children.length > 0) {
    const activeChildren = acc.children.filter((c) => c.accountKind === 'REAL' && c.isActive !== false)
    if (activeChildren.length > 0) {
      // 父节点：显示聚合余额（排除信贷账户）
      const parentBal = calculateParentBalances(acc)
      const balanceParts: string[] = []
      if (parentBal.balance > 0) balanceParts.push(formatCurrency(parentBal.balance))
      if (parentBal.credit > 0) balanceParts.push(`欠${formatCurrency(parentBal.credit)}`)
      const balanceText = balanceParts.length > 0 ? ` [${balanceParts.join(' ')}]` : ''

      return {
        text: `${acc.accountName}${balanceText}`,
        value: acc.id,
        children: activeChildren.map((child) => buildCascaderNode(child)),
      }
    }
  }
  // 叶子节点：显示自身余额
  const isCredit = isCreditAccountType(acc.accountType)
  const balanceLabel = isCredit
    ? `欠${formatCurrency(acc.balance || 0)}`
    : formatCurrency(acc.balance || 0)
  return {
    text: `${acc.accountName} (${balanceLabel})`,
    value: acc.id,
  }
}

// ─── 产品选项 ───
const productOptions = computed(() =>
  products.value
    .filter((p) => p.isActive)
    .map((p) => ({
      text: `${p.productName} (${p.productCode})`,
      value: p.id,
    })),
)

// ─── 时间处理 ───
function getNowString(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function toDatetimeLocal(s: string): string {
  if (!s) return ''
  return s.replace(' ', 'T')
}

function fromDatetimeLocal(val: string): string {
  if (!val) return ''
  const s = val.replace('T', ' ')
  // datetime-local 可能只给 HH:mm，需要补 :ss
  if (s.length === 16) return s + ':00'
  return s
}

// ─── 校验 ───
function validateAmount(val: string) {
  const num = parseFloat(val)
  if (isNaN(num) || num <= 0) return '请输入有效的金额'
  return true
}

// ─── 数据加载 ───
async function loadAccounts() {
  try {
    const list = await accountApi.getAccounts()
    accounts.value = list
  } catch (error) {
    console.error('加载账户失败:', error)
  }
}

async function loadRecentTransactions() {
  try {
    const response = await ledgerApi.getTransactions({ pageSize: 10 })
    recentTransactions.value = response.list || []
  } catch (error) {
    console.error('加载最近记录失败:', error)
  }
}

async function loadProducts() {
  try {
    const list = await productApi.getProducts()
    products.value = list || []
  } catch (error) {
    console.error('加载产品失败:', error)
  }
}

// ─── 在账户树中查找账户 ───
function findAccountInTree(accs: Account[], id: number): Account | null {
  for (const acc of accs) {
    if (acc.id === id) return acc
    if (acc.children && acc.children.length > 0) {
      const found = findAccountInTree(acc.children, id)
      if (found) return found
    }
  }
  return null
}

// ─── 账户 / 产品名称查询 ───
function getAccountNameById(id: number | undefined | null): string {
  if (!id) return ''
  // 先在树里找（覆盖后端返回树形数据的情况）
  const inTree = findAccountInTree(accountTree.value, id)
  if (inTree) return inTree.accountName
  // 兜底在扁平列表里找
  const acc = accounts.value.find((a) => a.id === id)
  return acc ? acc.accountName : ''
}

function getProductById(id: number | undefined | null): ProductMaster | undefined {
  if (!id) return undefined
  return products.value.find((p) => p.id === id)
}

// ─── 账户层级选择器 ───
function openAccountCascader(scene: AccountPickerScene) {
  accountPickerFor.value = scene
  accountCascaderValue.value = undefined

  const titleMap: Record<AccountPickerScene, string> = {
    expense: '选择账户',
    income: '选择账户',
    transferFrom: '选择转出账户',
    transferTo: '选择转入账户',
    buyCash: '选择资金账户',
    sellCash: '选择到账账户',
    repayCash: '选择还款账户',
    repayCredit: '选择信贷账户',
  }
  accountCascaderTitle.value = titleMap[scene] || '选择账户'
  showAccountCascader.value = true
}

function handleAccountCascaderFinish({ selectedOptions, value }: any) {
  if (!accountPickerFor.value || !selectedOptions || selectedOptions.length === 0) {
    showAccountCascader.value = false
    return
  }

  const leafId = typeof value === 'number' ? value : Number(value)
  if (isNaN(leafId)) {
    showAccountCascader.value = false
    return
  }

  switch (accountPickerFor.value) {
    case 'expense':
      expenseForm.value.accountId = leafId
      break
    case 'income':
      incomeForm.value.accountId = leafId
      break
    case 'transferFrom':
      transferForm.value.fromAccountId = leafId
      break
    case 'transferTo':
      transferForm.value.toAccountId = leafId
      break
    case 'buyCash':
      buyForm.value.cashAccountId = leafId
      break
    case 'sellCash':
      sellForm.value.cashAccountId = leafId
      break
    case 'repayCash':
      repaymentForm.value.cashAccountId = leafId
      break
    case 'repayCredit':
      repaymentForm.value.creditAccountId = leafId
      break
  }

  showAccountCascader.value = false
}

// ─── 产品选择器 ───
function openProductPicker(scene: ProductPickerScene) {
  productPickerFor.value = scene
  productPickerColumns.value = productOptions.value
  showProductPicker.value = true
}

function handleProductSelect({ selectedOptions }: any) {
  const selected = selectedOptions[0]
  if (!selected || !productPickerFor.value) {
    showProductPicker.value = false
    return
  }
  const productId = selected.value as number
  const label = selected.text as string

  if (productPickerFor.value === 'buy') {
    buyForm.value.productId = productId
    buyProductLabel.value = label
    // 如果是场外，尝试自动填充净值
    autoFillNav(productId, 'buy')
  } else {
    sellForm.value.productId = productId
    sellProductLabel.value = label
    autoFillNav(productId, 'sell')
  }

  showProductPicker.value = false
}

function autoFillNav(productId: number, _target: 'buy' | 'sell') {
  // 目前简化实现：不自动填充净值，用户手动输入
  // 后续可集成 navApi.getLatestNav(productId) 
  void productId
}

// ─── 分类选择器 ───
function openCategoryPicker(scene: CategoryPickerScene) {
  categoryPickerFor.value = scene
  categoryPickerOptions.value =
    scene === 'expense' ? expenseCategoryOptions.value : incomeCategoryOptions.value
  categoryPickerValue.value = undefined
  showCategoryPicker.value = true
}

function handleCategoryFinish({ selectedOptions }: any) {
  if (!categoryPickerFor.value || !selectedOptions || selectedOptions.length === 0) {
    showCategoryPicker.value = false
    return
  }
  const leaf = selectedOptions[selectedOptions.length - 1]
  const catId = typeof leaf.value === 'number' ? leaf.value : Number(leaf.value)
  const label = selectedOptions.map((o: any) => o.text).join(' / ')

  if (categoryPickerFor.value === 'expense') {
    expenseCategoryId.value = isNaN(catId) ? null : catId
    expenseCategoryLabel.value = label
  } else {
    incomeCategoryId.value = isNaN(catId) ? null : catId
    incomeCategoryLabel.value = label
  }

  showCategoryPicker.value = false
}

// ─── 消费提交 ───
async function handleQuickExpense() {
  if (!expenseForm.value.accountId || !expenseForm.value.amount || !expenseCategoryId.value) {
    showFailToast('请填写完整的账户、金额和分类')
    return
  }
  try {
    submitting.value = true
    await ledgerApi.quickEntry({
      type: 'EXPENSE',
      accountId: expenseForm.value.accountId,
      amount: parseFloat(expenseForm.value.amount),
      note: expenseForm.value.note,
      occurredAt: expenseForm.value.occurredAt || getNowString(),
      categoryId: expenseCategoryId.value || undefined,
    })
    showSuccessToast('消费记录已添加')
    showExpenseForm.value = false
    expenseForm.value = { accountId: undefined, amount: '', note: '', occurredAt: getNowString() }
    expenseCategoryId.value = null
    expenseCategoryLabel.value = ''
    loadRecentTransactions()
    window.dispatchEvent(new CustomEvent('data-refresh'))
  } catch (error: any) {
    showFailToast(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

// ─── 收入提交 ───
async function handleQuickIncome() {
  if (!incomeForm.value.accountId || !incomeForm.value.amount || !incomeCategoryId.value) {
    showFailToast('请填写完整的账户、金额和分类')
    return
  }
  try {
    submitting.value = true
    await ledgerApi.quickEntry({
      type: 'INCOME',
      accountId: incomeForm.value.accountId,
      amount: parseFloat(incomeForm.value.amount),
      note: incomeForm.value.note,
      occurredAt: incomeForm.value.occurredAt || getNowString(),
      categoryId: incomeCategoryId.value || undefined,
    })
    showSuccessToast('收入记录已添加')
    showIncomeForm.value = false
    incomeForm.value = { accountId: undefined, amount: '', note: '', occurredAt: getNowString() }
    incomeCategoryId.value = null
    incomeCategoryLabel.value = ''
    loadRecentTransactions()
    window.dispatchEvent(new CustomEvent('data-refresh'))
  } catch (error: any) {
    showFailToast(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

// ─── 转账提交 ───
async function handleTransferSubmit() {
  if (!transferForm.value.fromAccountId || !transferForm.value.toAccountId || !transferForm.value.amount) {
    showFailToast('请完整填写转出账户、转入账户和金额')
    return
  }
  if (transferForm.value.fromAccountId === transferForm.value.toAccountId) {
    showFailToast('转出账户和转入账户不能相同')
    return
  }
  const amount = parseFloat(transferForm.value.amount)
  if (!isFinite(amount) || amount <= 0) {
    showFailToast('请输入有效的金额')
    return
  }
  try {
    submitting.value = true
    const postings = [
      { postingType: 'CREDIT' as const, accountId: transferForm.value.fromAccountId!, accountType: 'CASH' as const, amount, currency: 'CNY' as const },
      { postingType: 'DEBIT' as const, accountId: transferForm.value.toAccountId!, accountType: 'CASH' as const, amount, currency: 'CNY' as const },
    ]
    const fromName = getAccountNameById(transferForm.value.fromAccountId)
    const toName = getAccountNameById(transferForm.value.toAccountId)
    const autoNote = `转账: ${fromName || '账户'} → ${toName || '账户'}`
    await ledgerApi.createTransaction({
      txnType: 'TRANSFER_OUT',
      postings,
      note: transferForm.value.note || autoNote,
      requestedAt: transferForm.value.occurredAt || getNowString(),
    })
    showSuccessToast('转账成功')
    showTransferForm.value = false
    transferForm.value = { fromAccountId: undefined, toAccountId: undefined, amount: '', note: '', occurredAt: getNowString() }
    loadRecentTransactions()
    window.dispatchEvent(new CustomEvent('data-refresh'))
  } catch (error: any) {
    showFailToast(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

// ─── 还款提交 ───
async function handleRepaymentSubmit() {
  if (!repaymentForm.value.cashAccountId || !repaymentForm.value.creditAccountId || !repaymentForm.value.amount) {
    showFailToast('请完整填写还款账户、信贷账户和金额')
    return
  }
  const amount = parseFloat(repaymentForm.value.amount)
  if (!isFinite(amount) || amount <= 0) {
    showFailToast('请输入有效的还款金额')
    return
  }
  try {
    submitting.value = true
    const postings = [
      { postingType: 'CREDIT' as const, accountId: repaymentForm.value.cashAccountId!, accountType: 'CASH' as const, amount, currency: 'CNY' as const },
      { postingType: 'DEBIT' as const, accountId: repaymentForm.value.creditAccountId!, accountType: 'CASH' as const, amount, currency: 'CNY' as const },
    ]
    const cashName = getAccountNameById(repaymentForm.value.cashAccountId)
    const creditName = getAccountNameById(repaymentForm.value.creditAccountId)
    const autoNote = `还款: ${cashName || '账户'} → ${creditName || '信贷账户'}`
    await ledgerApi.createTransaction({
      txnType: 'TRANSFER_OUT',
      postings,
      note: repaymentForm.value.note || autoNote,
      requestedAt: repaymentForm.value.occurredAt || getNowString(),
    })
    showSuccessToast('还款记录已提交')
    showRepaymentForm.value = false
    repaymentForm.value = { cashAccountId: undefined, creditAccountId: undefined, amount: '', note: '', occurredAt: getNowString() }
    loadRecentTransactions()
    window.dispatchEvent(new CustomEvent('data-refresh'))
  } catch (error: any) {
    showFailToast(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

// ─── 买入提交 ───
async function handleBuySubmit() {
  if (!buyForm.value.productId) {
    showFailToast('请选择产品')
    return
  }

  const product = getProductById(buyForm.value.productId)
  const currency = product?.currency || 'CNY'

  try {
    submitting.value = true

    if (buyChannel.value === 'EXCHANGE') {
      // ─── 场内买入 ───
      if (!buyForm.value.cashAccountId || !buyForm.value.price || !buyForm.value.shares) {
        showFailToast('请完整填写资金账户、成交价和份额')
        return
      }
      const price = parseFloat(buyForm.value.price)
      const shares = parseFloat(buyForm.value.shares)
      const fee = buyForm.value.fee ? parseFloat(buyForm.value.fee) : 0
      if (!isFinite(price) || price <= 0 || !isFinite(shares) || shares <= 0) {
        showFailToast('请输入有效的价格和份额')
        return
      }

      const totalAmount = price * shares
      const cashOut = totalAmount + fee
      const cost = totalAmount

      const postings: any[] = [
        { postingType: 'DEBIT', accountId: buyForm.value.cashAccountId!, accountType: 'POSITION', amount: cost, shares, currency },
        { postingType: 'CREDIT', accountId: buyForm.value.cashAccountId!, accountType: 'CASH', amount: cashOut, currency: 'CNY' },
      ]
      if (fee > 0) {
        postings.push({ postingType: 'DEBIT', accountId: buyForm.value.cashAccountId!, accountType: 'FEE', amount: fee, currency })
      }

      const prodName = product?.productName || '产品'
      await ledgerApi.createTransaction({
        txnType: 'BUY',
        productId: buyForm.value.productId!,
        postings,
        note: buyForm.value.note || `买入场内${prodName}`,
        requestedAt: buyForm.value.requestedAt || getNowString(),
      })
      showSuccessToast('买入记录已提交')
    } else {
      // ─── 场外申购 ───
      if (!buyForm.value.cashAccountId || !buyForm.value.otcAmount) {
        showFailToast('请完整填写资金账户和申购金额')
        return
      }
      const amount = parseFloat(buyForm.value.otcAmount)
      const otcFee = buyForm.value.fee ? parseFloat(buyForm.value.fee) : 0
      if (!isFinite(amount) || amount <= 0) {
        showFailToast('请输入有效的申购金额')
        return
      }

      const prodName = product?.productName || '产品'
      await orderApi.createOrder({
        productId: buyForm.value.productId!,
        orderType: 'SUBSCRIPTION',
        amount,
        fundingLines: [{ accountId: buyForm.value.cashAccountId!, amount }],
        expectedNavDate: buyForm.value.navDate || undefined,
        note: buyForm.value.note || `申购场外${prodName}`,
        feeEstimate: otcFee > 0 ? otcFee : undefined,
      } as any)
      showSuccessToast('申购订单已创建，请在订单中确认结算')
    }

    showBuyForm.value = false
    resetBuyForm()
    loadRecentTransactions()
    window.dispatchEvent(new CustomEvent('data-refresh'))
  } catch (error: any) {
    showFailToast(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

// ─── 卖出提交 ───
async function handleSellSubmit() {
  if (!sellForm.value.productId) {
    showFailToast('请选择产品')
    return
  }

  const product = getProductById(sellForm.value.productId)
  const currency = product?.currency || 'CNY'

  try {
    submitting.value = true

    if (sellChannel.value === 'EXCHANGE') {
      // ─── 场内卖出 ───
      if (!sellForm.value.cashAccountId || !sellForm.value.price || !sellForm.value.shares) {
        showFailToast('请完整填写到账账户、成交价和份额')
        return
      }
      const price = parseFloat(sellForm.value.price)
      const shares = parseFloat(sellForm.value.shares)
      const fee = sellForm.value.fee ? parseFloat(sellForm.value.fee) : 0
      if (!isFinite(price) || price <= 0 || !isFinite(shares) || shares <= 0) {
        showFailToast('请输入有效的价格和份额')
        return
      }

      const grossAmount = price * shares
      const netAmount = grossAmount - fee
      if (netAmount <= 0) {
        showFailToast('净到账金额必须大于 0')
        return
      }

      const postings: any[] = [
        { postingType: 'DEBIT', accountId: sellForm.value.cashAccountId!, accountType: 'CASH', amount: netAmount, currency: 'CNY' },
        { postingType: 'CREDIT', accountId: sellForm.value.cashAccountId!, accountType: 'POSITION', amount: grossAmount, shares, currency },
      ]
      if (fee > 0) {
        postings.push({ postingType: 'DEBIT', accountId: sellForm.value.cashAccountId!, accountType: 'FEE', amount: fee, currency })
      }

      const prodName = product?.productName || '产品'
      await ledgerApi.createTransaction({
        txnType: 'SELL',
        productId: sellForm.value.productId!,
        postings,
        note: sellForm.value.note || `卖出场内${prodName}`,
        requestedAt: sellForm.value.requestedAt || getNowString(),
      })
      showSuccessToast('卖出记录已提交')
    } else {
      // ─── 场外赎回 ───
      if (!sellForm.value.shares) {
        showFailToast('请输入赎回份额')
        return
      }
      const shares = parseFloat(sellForm.value.shares)
      if (!isFinite(shares) || shares <= 0) {
        showFailToast('请输入有效的赎回份额')
        return
      }
      const nav = sellForm.value.nav ? parseFloat(sellForm.value.nav) : 0
      const fee = sellForm.value.fee ? parseFloat(sellForm.value.fee) : 0
      const estimatedAmount = nav > 0 ? shares * nav - fee : 0

      const prodName = product?.productName || '产品'
      const fundingLines: any[] = []

      // SOURCE: 赎回的份额（简化为单一来源）
      if (sellForm.value.cashAccountId) {
        fundingLines.push({
          accountId: sellForm.value.cashAccountId,
          shares,
          lineType: 'SOURCE',
        })
        // TARGET: 到账账户
        fundingLines.push({
          accountId: sellForm.value.cashAccountId,
          amount: estimatedAmount > 0 ? estimatedAmount : undefined,
          lineType: 'TARGET',
        })
      }

      await orderApi.createOrder({
        productId: sellForm.value.productId!,
        orderType: 'REDEMPTION',
        shares,
        amount: estimatedAmount > 0 ? estimatedAmount : undefined,
        fundingLines,
        expectedNavDate: sellForm.value.navDate || undefined,
        note: sellForm.value.note || `赎回场外${prodName}`,
      } as any)
      showSuccessToast('赎回订单已创建，请在订单中确认结算')
    }

    showSellForm.value = false
    resetSellForm()
    loadRecentTransactions()
    window.dispatchEvent(new CustomEvent('data-refresh'))
  } catch (error: any) {
    showFailToast(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

// ─── 表单重置 ───
function resetBuyForm() {
  buyForm.value = {
    productId: undefined,
    cashAccountId: undefined,
    price: '',
    shares: '',
    fee: '',
    note: '',
    requestedAt: getNowString(),
    otcAmount: '',
    nav: '',
    navDate: '',
    confirmDate: '',
  }
  buyProductLabel.value = ''
}

function resetSellForm() {
  sellForm.value = {
    productId: undefined,
    cashAccountId: undefined,
    price: '',
    shares: '',
    fee: '',
    note: '',
    requestedAt: getNowString(),
    nav: '',
    navDate: '',
    confirmDate: '',
  }
  sellProductLabel.value = ''
}

// ─── 路由 / 展示 ───
function showUnifiedForm(txnType: string) {
  switch (txnType) {
    case 'TRANSFER':
      transferForm.value.occurredAt = getNowString()
      showTransferForm.value = true
      break
    case 'BUY':
      resetBuyForm()
      showBuyForm.value = true
      break
    case 'SELL':
      resetSellForm()
      showSellForm.value = true
      break
    case 'REPAYMENT':
      repaymentForm.value.occurredAt = getNowString()
      showRepaymentForm.value = true
      break
  }
}

function getTxnAmount(txn: LedgerTxn): number {
  return Math.abs((txn as any).summaryAmount || 0)
}

function getCategoryText(txn: LedgerTxn): string {
  const categoryId = txn.categoryId ? Number(txn.categoryId) : undefined
  if (!categoryId) return ''
  const categories = txn.txnType === 'EXPENSE' ? expenseCategories : incomeCategories
  const category = findCategoryById(categories, categoryId)
  if (!category) return ''
  if (category.categoryL2) return `${category.categoryL1} · ${category.categoryL2}`
  return category.categoryL1
}

function getTxnTypeClass(txnType: string): string {
  switch (txnType) {
    case 'INCOME':
    case 'REIMBURSE_IN':
    case 'DIVIDEND_CASH':
      return 'type-income'
    case 'EXPENSE':
      return 'type-expense'
    case 'TRANSFER_OUT':
    case 'TRANSFER_IN':
      return 'type-transfer'
    default:
      return 'type-other'
  }
}

function viewTransactionDetail(_txn: LedgerTxn) {
  // TODO: 跳转到流水详情
}

onMounted(() => {
  loadAccounts()
  loadProducts()
  loadRecentTransactions()
})
</script>

<style scoped>
.quick-entry-page {
  width: 100%;
  min-height: 100vh;
  background: var(--bg);
}

.page-container {
  padding: 16px;
  padding-bottom: calc(50px + var(--safe-area-inset-bottom) + 16px);
}

/* 快速操作卡片 */
.quick-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 24px;
}

.quick-card {
  background: var(--card);
  border-radius: var(--radius);
  padding: 32px 24px;
  text-align: center;
  box-shadow: var(--shadow);
  transition: all 0.2s ease;
  position: relative;
  overflow: hidden;
}

.quick-card:active {
  transform: scale(0.98);
  box-shadow: var(--shadow2);
}

.quick-card.expense {
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8787 100%);
  color: white;
}

.quick-card.income {
  background: linear-gradient(135deg, #51cf66 0%, #69db7c 100%);
  color: white;
}

.card-label {
  font-size: 16px;
  font-weight: 600;
  margin-top: 12px;
}

/* 区域标题 */
.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  color: var(--muted);
  margin: 24px 0 12px 0;
  padding: 0 4px;
}

/* 最近记录区域 */
.recent-section {
  margin-top: 24px;
}

/* 交易记录行样式 */
.txn-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
}

.txn-type-label {
  font-weight: 600;
  font-size: 14px;
  flex-shrink: 0;
}

.txn-type-label.type-expense { color: var(--bad); }
.txn-type-label.type-income { color: var(--good); }
.txn-type-label.type-transfer { color: var(--primary); }
.txn-type-label.type-other { color: var(--text); }

.txn-note-inline {
  font-size: 13px;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.txn-sub-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--muted);
  margin-top: 2px;
  overflow: hidden;
  white-space: nowrap;
}

.txn-sub-row.secondary {
  margin-top: 4px;
}

.txn-category {
  color: var(--text);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.txn-account {
  color: var(--primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.txn-account::before {
  content: '· ';
  color: var(--muted);
}

.txn-amount {
  font-weight: 600;
  font-size: 14px;
  white-space: nowrap;
}

.txn-amount.type-expense { color: var(--bad); }
.txn-amount.type-income { color: var(--good); }
.txn-amount.type-transfer { color: var(--primary); }
.txn-amount.type-other { color: var(--text); }

/* 表单弹窗 */
.form-popup {
  padding: 24px;
  height: 100%;
  overflow-y: auto;
}

.popup-title {
  font-size: 20px;
  font-weight: 600;
  text-align: center;
  margin: 0 0 16px 0;
  color: var(--text);
}

.form-actions {
  margin-top: 24px;
  padding: 0 16px;
}

.form-actions .van-button {
  height: 48px;
  font-size: 16px;
  font-weight: 600;
}

/* 场内/场外切换按钮 */
.channel-switch {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  padding: 0 4px;
}

.channel-btn {
  flex: 1;
  padding: 10px 0;
  border: 1.5px solid var(--border, #e5e7eb);
  border-radius: 8px;
  background: var(--card, #fff);
  color: var(--text, #333);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  text-align: center;
}

.channel-btn.active {
  border-color: var(--primary, #4ea4ff);
  background: var(--primary, #4ea4ff);
  color: #fff;
}

.channel-btn:active {
  transform: scale(0.97);
}

/* 原生时间输入框样式 */
.native-datetime-input {
  border: none;
  outline: none;
  width: 100%;
  font-size: 14px;
  background: transparent;
  color: var(--text, #333);
  padding: 0;
  line-height: 24px;
}
</style>
