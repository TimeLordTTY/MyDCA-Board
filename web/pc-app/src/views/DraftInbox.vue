<template>
  <div class="draft-inbox-page">
    <div class="pagehead">
      <h1>草稿箱</h1>
      <p>文本记账与 AI 解析只生成待确认草稿，不会直接入账。</p>
    </div>

    <div class="safety-banner">
      <strong>安全边界：</strong>
      AI/文本解析只会创建 DRAFT 草稿；只有在草稿预览显示可确认，并由你点击“确认记账”后，才会请求后端生成正式流水。
    </div>

    <div class="draft-grid">
      <section class="card text-entry-card">
        <div class="row-between">
          <div>
            <h3>
              文本记账入口
              <span class="tag blue tiny">Phase3 MVP</span>
            </h3>
            <div class="sub">先解析自然语言，再手动创建草稿；首版不接入真实大模型。</div>
          </div>
        </div>
        <div class="divider"></div>

        <el-input
          v-model="textInput"
          type="textarea"
          :rows="5"
          maxlength="500"
          show-word-limit
          placeholder="例如：午饭花了 32.5，用余额宝生活费，备注公司楼下简餐"
        />

        <div class="entry-actions">
          <el-button
            type="primary"
            :loading="parsing"
            :disabled="!textInput.trim()"
            @click="handleParseText"
          >
            解析文本
          </el-button>
          <el-button
            :loading="creatingDraft"
            :disabled="!parsedIntent"
            @click="handleCreateDraft"
          >
            生成草稿
          </el-button>
          <el-button :disabled="parsing || creatingDraft" @click="resetTextEntry">
            清空
          </el-button>
        </div>

        <div v-if="parsedIntent" class="intent-panel">
          <div class="intent-title">
            <span>解析结果</span>
            <span class="tag" :class="parsedIntent.missingFields.length ? 'orange' : 'green'">
              {{ parsedIntent.missingFields.length ? '需补齐' : '字段完整' }}
            </span>
          </div>
          <div class="intent-grid">
            <div>
              <span class="field-label">类型</span>
              <strong>{{ formatTxnType(parsedIntent.txnType) }}</strong>
            </div>
            <div>
              <span class="field-label">金额</span>
              <strong>{{ formatAmount(parsedIntent.amount) }}</strong>
            </div>
            <div>
              <span class="field-label">账户提示</span>
              <strong>{{ parsedIntent.accountNameHint || '-' }}</strong>
            </div>
            <div>
              <span class="field-label">置信度</span>
              <strong>{{ formatConfidence(parsedIntent.confidence) }}</strong>
            </div>
          </div>
          <div class="intent-note">
            <span class="field-label">备注</span>
            <p>{{ parsedIntent.note || '-' }}</p>
          </div>
          <div v-if="parsedIntent.missingFields.length" class="missing-list">
            <span class="field-label">缺失字段</span>
            <span
              v-for="field in parsedIntent.missingFields"
              :key="field"
              class="tag orange tiny"
            >
              {{ field }}
            </span>
          </div>
        </div>
      </section>

      <section class="card detail-card">
        <div class="row-between">
          <div>
            <h3>草稿预览</h3>
            <div class="sub">确认按钮只在 preview.confirmSupported=true 时启用。</div>
          </div>
          <span v-if="selectedDraft" class="tag gray tiny">#{{ selectedDraft.id }}</span>
        </div>
        <div class="divider"></div>

        <div v-if="!selectedDraft" class="empty-detail">
          从下方列表选择一条草稿后，可以预览、忽略或确认。
        </div>

        <template v-else>
          <div class="detail-section">
            <div class="detail-row">
              <span>状态</span>
              <span class="tag" :class="statusTagClass(selectedDraft.status)">
                {{ formatStatus(selectedDraft.status) }}
              </span>
            </div>
            <div class="detail-row">
              <span>来源</span>
              <strong>{{ selectedDraft.sourceType || '-' }}</strong>
            </div>
            <div class="detail-row">
              <span>更新时间</span>
              <strong>{{ formatDateTime(selectedDraft.updatedAt || selectedDraft.createdAt) }}</strong>
            </div>
          </div>

          <div class="raw-block">
            <span class="field-label">原始输入</span>
            <p>{{ selectedDraft.rawInput || '-' }}</p>
          </div>

          <div class="detail-actions">
            <el-button @click="loadHistory(selectedDraft.id)">查看历史</el-button>
            <el-button v-if="selectedDraft.status === 'IGNORED'" @click="handleReopen(selectedDraft)">恢复草稿</el-button>
            <el-button v-if="selectedDraft.status === 'CONFIRMED'" @click="handleCopy(selectedDraft)">复制为新草稿</el-button>
            <el-button
              type="primary"
              plain
              :disabled="selectedDraft.status !== 'DRAFT'"
              @click="openEditDraft(selectedDraft)"
            >
              编辑草稿
            </el-button>
            <el-button :loading="previewing" @click="handlePreview(selectedDraft)">
              生成预览
            </el-button>
            <el-button
              type="warning"
              plain
              :disabled="selectedDraft.status !== 'DRAFT'"
              @click="handleIgnore(selectedDraft)"
            >
              忽略
            </el-button>
            <el-button
              type="danger"
              :disabled="!canConfirmSelectedDraft"
              :loading="confirming"
              @click="handleConfirm(selectedDraft)"
            >
              确认记账
            </el-button>
          </div>

          <div v-if="historyVisible" class="detail-section">
            <h4>草稿历史</h4>
            <p v-if="historyLoading">正在加载历史…</p>
            <p v-else-if="historyError">历史加载失败：{{ historyError }}</p>
            <p v-else-if="!history.length">暂无历史记录</p>
            <div v-else v-for="event in history" :key="event.id" class="detail-row">
              <span>{{ formatDateTime(event.createdAt) }} · {{ eventLabel(event.eventType) }}</span>
              <strong>{{ historyStatus(event.statusBefore) }} → {{ historyStatus(event.statusAfter) }} · {{ event.summary }}</strong>
            </div>
          </div>

          <div v-if="selectedDraft.status !== 'DRAFT'" class="edit-disabled-tip">
            当前草稿已{{ formatStatus(selectedDraft.status) }}，不能再编辑或重新确认。
          </div>

          <div v-if="editingDraft" class="edit-panel">
            <div class="intent-title">
              <span>编辑草稿字段</span>
              <span class="tag blue tiny">只更新草稿</span>
            </div>
            <div class="edit-hint">
              补齐字段只会保存到草稿，不会直接入账。保存后请重新生成预览，确认无误后再点击“确认记账”。
            </div>

            <div class="edit-form-grid">
              <label class="form-field">
                <span class="field-label">交易类型</span>
                <el-select
                  v-model="draftEditForm.txnType"
                  placeholder="请选择类型"
                  @change="handleEditTxnTypeChange"
                >
                  <el-option label="支出" value="EXPENSE" />
                  <el-option label="收入" value="INCOME" />
                  <el-option label="转账" value="TRANSFER" />
                  <el-option label="买入" value="BUY" />
                  <el-option label="申购" value="SUBSCRIPTION" />
                  <el-option label="卖出" value="SELL" />
                  <el-option label="赎回" value="REDEMPTION" />
                </el-select>
              </label>

              <label v-if="!isSellRedeemEdit" class="form-field">
                <span class="field-label">金额</span>
                <el-input
                  v-model="draftEditForm.amount"
                  inputmode="decimal"
                  placeholder="请输入正数金额"
                />
              </label>

              <label v-if="isSellRedeemEdit" class="form-field">
                <span class="field-label">份额（必填）</span>
                <el-input
                  v-model="draftEditForm.shares"
                  inputmode="decimal"
                  placeholder="请输入大于 0 的份额"
                />
              </label>

              <label v-if="isProductEdit" class="form-field form-field-wide">
                <span class="field-label">真实产品（必选）</span>
                <el-select
                  v-model="draftEditForm.productId"
                  filterable
                  clearable
                  :loading="productStore.loading"
                  placeholder="请选择真实产品"
                  @change="handleProductChange"
                >
                  <el-option
                    v-for="product in productOptions"
                    :key="product.id"
                    :label="formatProductOption(product)"
                    :value="product.id"
                  >
                    <div class="account-option">
                      <span>{{ product.productName }}</span>
                      <small>{{ formatProductMeta(product) }}</small>
                    </div>
                  </el-option>
                </el-select>
              </label>

              <label v-if="isProductEdit" class="form-field form-field-wide">
                <span class="field-label">产品名称提示（仅复核）</span>
                <el-input
                  v-model="draftEditForm.productNameHint"
                  placeholder="保留 AI/文本给出的产品提示，不可替代真实产品 ID"
                />
              </label>

              <label v-if="isInvestmentEdit" class="form-field">
                <span class="field-label">预计净值日（可选）</span>
                <el-input v-model="draftEditForm.expectedNavDate" placeholder="例如 2026-09-29" />
              </label>

              <label v-if="isInvestmentEdit" class="form-field">
                <span class="field-label">预计确认日（可选）</span>
                <el-input v-model="draftEditForm.expectedConfirmDate" placeholder="例如 2026-09-30" />
              </label>

              <label v-if="!isSellRedeemEdit" class="form-field form-field-wide">
                <span class="field-label">
                  {{
                    isTransferEdit
                      ? '转出账户'
                      : isInvestmentEdit
                        ? '付款账户（投资只允许单一资金来源）'
                        : '现金/活钱账户'
                  }}
                </span>
                <el-select
                  v-model="draftEditForm.accountId"
                  filterable
                  clearable
                  :loading="accountStore.loading"
                  placeholder="请选择实际记账账户"
                >
                  <el-option
                    v-for="account in accountOptionsForEdit"
                    :key="account.id"
                    :label="formatAccountOption(account)"
                    :value="account.id"
                  >
                    <div class="account-option">
                      <span>{{ account.accountName }}</span>
                      <small>{{ formatAccountMeta(account) }}</small>
                    </div>
                  </el-option>
                </el-select>
              </label>

              <label v-if="isSellRedeemEdit" class="form-field form-field-wide">
                <span class="field-label">持仓来源账户（必选）</span>
                <el-select
                  v-model="draftEditForm.sourceAccountId"
                  filterable
                  clearable
                  :loading="holdingsLoading"
                  :placeholder="
                    draftEditForm.productId
                      ? '请选择该产品的真实持仓来源账户'
                      : '请先选择真实产品'
                  "
                >
                  <el-option
                    v-for="holding in sellRedeemSourceOptions"
                    :key="holding.accountId"
                    :label="formatHoldingOption(holding)"
                    :value="holding.accountId"
                  >
                    <div class="account-option">
                      <span>{{ holding.accountName }}</span>
                      <small>{{ formatHoldingMeta(holding) }}</small>
                    </div>
                  </el-option>
                </el-select>
              </label>

              <label v-if="isSellRedeemEdit" class="form-field form-field-wide">
                <span class="field-label">持仓来源提示</span>
                <el-input
                  v-model="draftEditForm.sourceAccountNameHint"
                  placeholder="保留 AI/文本给出的持仓来源提示，便于复核"
                />
              </label>

              <label v-if="isSellRedeemEdit" class="form-field form-field-wide">
                <span class="field-label">到账账户（必选，仅限 REAL 叶子账户）</span>
                <el-select
                  v-model="draftEditForm.targetAccountId"
                  filterable
                  clearable
                  :loading="accountStore.loading"
                  placeholder="请选择到账账户"
                >
                  <el-option
                    v-for="account in sellRedeemTargetAccountOptions"
                    :key="account.id"
                    :label="formatAccountOption(account)"
                    :value="account.id"
                  >
                    <div class="account-option">
                      <span>{{ account.accountName }}</span>
                      <small>{{ formatAccountMeta(account) }}</small>
                    </div>
                  </el-option>
                </el-select>
              </label>

              <label v-if="isTransferEdit" class="form-field form-field-wide">
                <span class="field-label">转入账户（仅转账）</span>
                <el-select
                  v-model="draftEditForm.targetAccountId"
                  filterable
                  clearable
                  :loading="accountStore.loading"
                  placeholder="请选择转入账户"
                >
                  <el-option
                    v-for="account in transferTargetAccountOptions"
                    :key="account.id"
                    :label="formatAccountOption(account)"
                    :value="account.id"
                  >
                    <div class="account-option">
                      <span>{{ account.accountName }}</span>
                      <small>{{ formatAccountMeta(account) }}</small>
                    </div>
                  </el-option>
                </el-select>
              </label>

              <label v-if="isTransferEdit || isSellRedeemEdit" class="form-field form-field-wide">
                <span class="field-label">{{ isSellRedeemEdit ? '到账账户提示' : '转入账户提示' }}</span>
                <el-input
                  v-model="draftEditForm.targetAccountNameHint"
                  :placeholder="
                    isSellRedeemEdit
                      ? '保留 AI/文本给出的到账账户提示，便于复核'
                      : '保留 AI/文本给出的转入账户提示，便于复核'
                  "
                />
              </label>

              <label v-if="!isSellRedeemEdit" class="form-field form-field-wide">
                <span class="field-label">账户提示</span>
                <el-input
                  v-model="draftEditForm.accountNameHint"
                  placeholder="保留 AI/文本给出的账户提示，便于复核"
                />
              </label>

              <label class="form-field form-field-wide">
                <span class="field-label">备注</span>
                <el-input
                  v-model="draftEditForm.note"
                  type="textarea"
                  :rows="3"
                  maxlength="200"
                  show-word-limit
                  placeholder="请输入正式流水备注"
                />
              </label>
            </div>

            <div class="edit-actions">
              <el-button :disabled="savingDraft" @click="cancelEditDraft">取消</el-button>
              <el-button type="primary" :loading="savingDraft" @click="handleSaveDraftEdit()">
                保存草稿
              </el-button>
              <el-button
                type="success"
                plain
                :disabled="savingDraft || !selectedDraft"
                @click="handleSaveDraftEdit(true)"
              >
                保存并预览
              </el-button>
            </div>
          </div>

          <div v-if="preview" class="preview-panel">
            <div class="intent-title">
              <span>确认预览</span>
              <span class="tag" :class="preview.confirmSupported ? 'green' : 'orange'">
                {{ preview.confirmSupported ? '可确认' : '暂不可确认' }}
              </span>
            </div>
            <div class="intent-grid">
              <div>
                <span class="field-label">类型</span>
                <strong>{{ formatTxnType(preview.txnType) }}</strong>
              </div>
              <div>
                <span class="field-label">{{ isSellRedeemPreview ? '份额' : '金额' }}</span>
                <strong>{{
                  isSellRedeemPreview ? formatShares(preview.shares) : formatAmount(preview.amount)
                }}</strong>
              </div>
              <div>
                <span class="field-label">
                  {{
                    isTransferPreview
                      ? '转出账户'
                      : isSellRedeemPreview
                        ? '持仓来源'
                        : isProductPreview
                          ? '付款账户'
                          : '账户'
                  }}
                </span>
                <strong>{{ formatPreviewAccount(preview.accountId) }}</strong>
              </div>
              <div v-if="isProductPreview">
                <span class="field-label">产品</span>
                <strong>
                  {{ preview.productName || '未选择' }}
                  <span v-if="preview.productCode"> / {{ preview.productCode }}</span>
                  <span v-if="preview.productAssetType"> / {{ preview.productAssetType }}</span>
                </strong>
              </div>
              <div v-if="isTransferPreview">
                <span class="field-label">转入账户</span>
                <strong>{{
                  formatPreviewAccount(preview.targetAccountId, preview.targetAccountName)
                }}</strong>
              </div>
              <div v-if="isSellRedeemPreview">
                <span class="field-label">到账账户</span>
                <strong>{{
                  formatPreviewAccount(preview.targetAccountId, preview.targetAccountName)
                }}</strong>
              </div>
              <div v-if="isSellRedeemPreview">
                <span class="field-label">可用 / 本次 / 预计剩余份额</span>
                <strong>
                  {{ formatShares(preview.availableShares) }} / {{ formatShares(preview.shares) }} /
                  {{ formatShares(preview.remainingShares) }}
                </strong>
              </div>
              <div>
                <span class="field-label">草稿 ID</span>
                <strong>{{ preview.draftId }}</strong>
              </div>
            </div>
            <div class="preview-impact-grid">
              <div class="impact-card">
                <span class="field-label">{{ isTransferPreview ? '转出账户影响' : '账户影响' }}</span>
                <strong>{{ formatImpactDirection(preview.impactDirection) }}</strong>
                <p>
                  {{ formatPreviewAccount(preview.accountId, preview.accountName) }}
                  <span v-if="preview.accountType"> / {{ preview.accountType }}</span>
                  <span v-if="preview.fundUsage"> / {{ preview.fundUsage }}</span>
                </p>
                <em>{{ formatSignedAmount(preview.accountDelta) }}</em>
              </div>
              <div v-if="isTransferPreview" class="impact-card">
                <span class="field-label">转入账户影响</span>
                <strong>转入</strong>
                <p>
                  {{ formatPreviewAccount(preview.targetAccountId, preview.targetAccountName) }}
                  <span v-if="preview.targetAccountType"> / {{ preview.targetAccountType }}</span>
                  <span v-if="preview.targetFundUsage"> / {{ preview.targetFundUsage }}</span>
                </p>
                <em>{{ formatSignedAmount(preview.targetAccountDelta) }}</em>
              </div>
              <div v-if="isInvestmentPreview" class="impact-card">
                <span class="field-label">待结算应收影响</span>
                <strong>应收增加</strong>
                <p>付款账户立即减少，同额待结算应收增加</p>
                <em>{{ formatSignedAmount(preview.receivableDelta) }}</em>
              </div>
              <div v-if="isSellRedeemPreview" class="impact-card">
                <span class="field-label">份额占用影响</span>
                <strong>仅内部待处理</strong>
                <p>确认只创建内部 PENDING 记录并占用持仓来源份额</p>
                <em>不减少持仓 · 不增加到账余额</em>
              </div>
              <div class="impact-card">
                <span class="field-label">正式对象影响</span>
                <p>正式流水：{{ formatBooleanImpact(preview.willCreateLedgerTxn) }}</p>
                <p>订单：{{ formatBooleanImpact(preview.willCreateOrder) }}</p>
                <p>待结算：{{ formatBooleanImpact(preview.willCreateSettlement) }}</p>
                <p>持仓：{{ formatBooleanImpact(preview.willAffectHolding) }}</p>
              </div>
            </div>
            <div class="intent-note">
              <span class="field-label">提示</span>
              <p>{{ preview.message || '预览完成，请确认内容无误后再正式记账。' }}</p>
            </div>
            <div v-if="isInvestmentPreview && preview.fundingMessage" class="intent-note">
              <span class="field-label">资金来源</span>
              <p>{{ preview.fundingMessage }}</p>
            </div>
            <div v-if="isSellRedeemPreview && preview.sharesMessage" class="intent-note">
              <span class="field-label">确认影响</span>
              <p>{{ preview.sharesMessage }}</p>
            </div>
            <div v-if="preview.warnings?.length" class="warning-list">
              <span class="field-label">风险 / 提示</span>
              <p v-for="warning in preview.warnings" :key="warning">{{ warning }}</p>
            </div>
            <div v-if="preview.missingFields?.length" class="missing-list">
              <span class="field-label">仍缺失</span>
              <span
                v-for="field in preview.missingFields"
                :key="field"
                class="tag orange tiny"
              >
                {{ field }}
              </span>
            </div>
          </div>

          <details class="json-details">
            <summary>查看解析 JSON</summary>
            <pre>{{ formatJson(selectedDraft.parsedPayloadJson) }}</pre>
          </details>
        </template>
      </section>
    </div>

    <section class="card draft-list-card">
      <div class="row-between">
        <div>
          <h3>草稿列表</h3>
          <div class="sub">列表读取 draftApi.listDrafts，支持按草稿状态过滤。</div>
        </div>
        <div class="row-gap">
          <el-select v-model="statusFilter" size="small" style="width: 140px" @change="loadDrafts">
            <el-option label="全部状态" value="" />
            <el-option label="待确认" value="DRAFT" />
            <el-option label="已确认" value="CONFIRMED" />
            <el-option label="已忽略" value="IGNORED" />
          </el-select>
          <el-button size="small" :loading="loadingDrafts" @click="loadDrafts">刷新</el-button>
        </div>
      </div>
      <div class="divider"></div>

      <div class="draft-table-container hide-scrollbar">
        <table class="draft-table">
          <thead>
            <tr>
              <th style="width: 90px;">状态</th>
              <th style="width: 120px;">来源</th>
              <th>原始输入</th>
              <th style="width: 210px;">候选摘要</th>
              <th style="width: 180px;">缺失字段</th>
              <th style="width: 150px;">更新时间</th>
              <th class="right" style="width: 190px;">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loadingDrafts">
              <td colspan="7" class="td-muted table-empty">草稿加载中...</td>
            </tr>
            <tr v-else-if="drafts.length === 0">
              <td colspan="7" class="td-muted table-empty">暂无草稿</td>
            </tr>
            <tr
              v-for="draft in drafts"
              v-else
              :key="draft.id"
              :class="{ active: selectedDraft?.id === draft.id }"
              @click="selectDraft(draft)"
            >
              <td>
                <span class="tag tiny" :class="statusTagClass(draft.status)">
                  {{ formatStatus(draft.status) }}
                </span>
              </td>
              <td>
                <div class="mono tiny">{{ draft.sourceType || '-' }}</div>
                <div class="td-muted tiny">{{ draft.sourceRef || '' }}</div>
              </td>
              <td class="draft-raw">{{ draft.rawInput || '-' }}</td>
              <td>{{ summarizeDraft(draft) }}</td>
              <td>
                <div class="missing-chips">
                  <span
                    v-for="field in parseMissingFields(draft.missingFieldsJson)"
                    :key="field"
                    class="tag orange tiny"
                  >
                    {{ field }}
                  </span>
                  <span v-if="parseMissingFields(draft.missingFieldsJson).length === 0" class="td-muted">
                    -
                  </span>
                </div>
              </td>
              <td class="mono tiny">{{ formatDateTime(draft.updatedAt || draft.createdAt) }}</td>
              <td class="right">
                <div class="table-actions">
                  <button class="btn-small" @click.stop="selectDraft(draft)">详情</button>
                  <button
                    class="btn-small"
                    :disabled="draft.status !== 'DRAFT'"
                    @click.stop="openEditDraft(draft)"
                  >
                    编辑
                  </button>
                  <button class="btn-small" @click.stop="handlePreview(draft)">预览</button>
                  <button
                    class="btn-small btn-danger"
                    :disabled="draft.status !== 'DRAFT'"
                    @click.stop="handleIgnore(draft)"
                  >
                    忽略
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox, ElNotification } from 'element-plus'
import {
  aiAccountingApi,
  draftApi,
  holdingApi,
  useAccountStore,
  useProductStore,
} from '@wealth-hub/shared'
import type {
  Account,
  AccountHoldingInfo,
  AccountingIntent,
  DraftLedgerEntry,
  DraftLifecycleEvent,
  DraftLedgerStatus,
  DraftPreview,
  ProductMaster,
} from '@wealth-hub/shared'

type DraftTxnType =
  | 'EXPENSE'
  | 'INCOME'
  | 'TRANSFER'
  | 'BUY'
  | 'SUBSCRIPTION'
  | 'SELL'
  | 'REDEMPTION'
  | ''

interface DraftEditForm {
  txnType: DraftTxnType
  amount: string
  note: string
  accountId?: number
  targetAccountId?: number
  accountNameHint: string
  targetAccountNameHint: string
  /** 投资 / 卖出 / 赎回草稿选定的真实产品 ID；必须由主人明确选择。 */
  productId?: number
  /** 产品名称提示，只供复核，不可代替 productId。 */
  productNameHint: string
  expectedNavDate: string
  expectedConfirmDate: string
  /** 卖出 / 赎回草稿本次要卖出或赎回的份额，字符串便于表单编辑。 */
  shares: string
  /** 卖出 / 赎回草稿的持仓来源账户 ID；必须来自该产品的真实持仓来源。 */
  sourceAccountId?: number
  /** 持仓来源账户名称提示，只供复核，不可代替 sourceAccountId。 */
  sourceAccountNameHint: string
}

const accountStore = useAccountStore()
const productStore = useProductStore()
const route = useRoute()
const textInput = ref('')
const parsedIntent = ref<AccountingIntent | null>(null)
const drafts = ref<DraftLedgerEntry[]>([])
const selectedDraft = ref<DraftLedgerEntry | null>(null)
const history = ref<DraftLifecycleEvent[]>([])
const historyVisible = ref(false)
const historyLoading = ref(false)
const historyError = ref('')
const preview = ref<DraftPreview | null>(null)
const statusFilter = ref<DraftLedgerStatus | ''>('DRAFT')
const loadingDrafts = ref(false)
const parsing = ref(false)
const creatingDraft = ref(false)
const previewing = ref(false)
const confirming = ref(false)
const editingDraft = ref(false)
const savingDraft = ref(false)
const draftEditForm = ref<DraftEditForm>(emptyDraftEditForm())
const routeDraftHintHandled = ref(false)
const productHoldings = ref<AccountHoldingInfo[]>([])
const holdingsLoading = ref(false)

const draftQueryParams = computed(() => ({
  status: statusFilter.value || undefined,
  page: 1,
  pageSize: 50,
}))

const canConfirmSelectedDraft = computed(() => {
  return Boolean(
    selectedDraft.value?.status === 'DRAFT' &&
      preview.value?.draftId === selectedDraft.value.id &&
      preview.value.confirmSupported &&
      !confirming.value
  )
})

const editableAccountOptions = computed(() => {
  const preferredAccounts = accountStore.cashLeafAccounts.length
    ? accountStore.cashLeafAccounts
    : accountStore.getAllLeafAccounts()

  return preferredAccounts.filter((account) => account.isActive !== false)
})

const rankedAccountOptions = computed(() => {
  const hint = draftEditForm.value.accountNameHint.trim().toLowerCase()
  return [...editableAccountOptions.value].sort((left, right) => {
    const leftScore = accountHintScore(left, hint)
    const rightScore = accountHintScore(right, hint)

    if (leftScore !== rightScore) return rightScore - leftScore
    return left.accountName.localeCompare(right.accountName, 'zh-Hans-CN')
  })
})

const transferTargetAccountOptions = computed(() =>
  [...editableAccountOptions.value].sort((left, right) =>
    left.accountName.localeCompare(right.accountName, 'zh-Hans-CN')
  )
)

/** 投资编辑态只允许 INVESTABLE 叶子账户；BOND_REPO 额外允许 RESERVED 专款。 */
const accountOptionsForEdit = computed(() =>
  isInvestmentEdit.value ? investmentAccountOptions.value : rankedAccountOptions.value
)

/** 转账编辑态：只有 TRANSFER 才显示转入账户，避免目标账户残留到 EXPENSE / INCOME。 */
const isTransferEdit = computed(() => draftEditForm.value.txnType === 'TRANSFER')

/** 投资编辑态：BUY（场内买入）与 SUBSCRIPTION（场外申购）。 */
const isInvestmentEdit = computed(
  () => draftEditForm.value.txnType === 'BUY' || draftEditForm.value.txnType === 'SUBSCRIPTION'
)

/** 卖出 / 赎回编辑态：SELL（场内卖出）与 REDEMPTION（场外赎回）。 */
const isSellRedeemEdit = computed(
  () => draftEditForm.value.txnType === 'SELL' || draftEditForm.value.txnType === 'REDEMPTION'
)

/** 需要主人明确选择真实产品的编辑态：BUY / SUBSCRIPTION / SELL / REDEMPTION。 */
const isProductEdit = computed(() => isInvestmentEdit.value || isSellRedeemEdit.value)

/** 投资预览态：BUY / SUBSCRIPTION 确认会生成付款账本，PC 不把它们当成不支持类型。 */
const isInvestmentPreview = computed(() => {
  const type = preview.value?.txnType?.trim().toUpperCase()
  return type === 'BUY' || type === 'SUBSCRIPTION'
})

/** 卖出 / 赎回预览态：确认只创建内部 PENDING 记录，不生成账本、不改现金与持仓。 */
const isSellRedeemPreview = computed(() => {
  const type = preview.value?.txnType?.trim().toUpperCase()
  return type === 'SELL' || type === 'REDEMPTION'
})

/** 需要展示产品信息的预览态：BUY / SUBSCRIPTION / SELL / REDEMPTION。 */
const isProductPreview = computed(() => isInvestmentPreview.value || isSellRedeemPreview.value)

/** 卖出 / 赎回持仓来源：只来自该产品真实持仓（/holdings/product/{id}/by-account），份额高者优先。 */
const sellRedeemSourceOptions = computed(() =>
  [...productHoldings.value].sort((left, right) => right.shares - left.shares)
)

/** 卖出 / 赎回到账账户：当前启用 REAL 叶子账户，币种与产品一致；禁止 POSITION / VIRTUAL / 父账户。 */
const sellRedeemTargetAccountOptions = computed(() => {
  const productCurrency = selectedProduct.value?.currency
  return accountStore
    .getAllLeafAccounts()
    .filter((account) => account.isActive !== false && account.accountKind === 'REAL')
    .filter((account) => account.virtualSubtype !== 'POSITION')
    .filter((account) => !productCurrency || account.currency === productCurrency)
    .sort((left, right) => left.accountName.localeCompare(right.accountName, 'zh-Hans-CN'))
})

/** 只展示启用中的产品；产品 ID 必须由主人明确选择，不使用名称自动匹配。 */
const productOptions = computed(() =>
  productStore.products.filter((product) => product.isActive !== false)
)

const selectedProduct = computed(() => {
  const productId = Number(draftEditForm.value.productId)
  if (!Number.isInteger(productId) || productId <= 0) return undefined
  return productOptions.value.find((product) => product.id === productId)
})

const selectedProductAssetType = computed(() => selectedProduct.value?.assetType || '')

/** 投资资金来源：只有 INVESTABLE 叶子账户；国债逆回购 BOND_REPO 额外允许 RESERVED 专款。 */
const investmentAccountOptions = computed(() => {
  const bondRepo = selectedProductAssetType.value === 'BOND_REPO'
  return accountStore
    .getAllLeafAccounts()
    .filter((account) => account.isActive !== false && account.accountKind === 'REAL')
    .filter((account) => {
      if (account.fundUsage === 'INVESTABLE') return true
      return bondRepo && account.fundUsage === 'RESERVED'
    })
    .sort((left, right) => left.accountName.localeCompare(right.accountName, 'zh-Hans-CN'))
})

/** 转账预览态：PC 只做查看 / 预览兼容，不把 TRANSFER 当成不支持类型。 */
const isTransferPreview = computed(
  () => preview.value?.txnType?.trim().toUpperCase() === 'TRANSFER'
)

/**
 * 确认弹窗标题：
 * - 卖出 / 赎回明确「确认创建【产品】卖出 / 赎回 X 份（来源 A）的内部待处理记录？」；
 * - 投资明确「确认创建【产品】买入 / 申购订单 ￥X？」；
 * - 转账明确「确认将 X 从 A 转到 B？」；
 * - 其它类型保持通用标题。
 */
const confirmDialogTitle = computed(() => {
  const current = preview.value
  if (current && isSellRedeemPreview.value) {
    const action = current.txnType?.trim().toUpperCase() === 'REDEMPTION' ? '赎回' : '卖出'
    const product = current.productName || '未选择产品'
    const source = current.accountName || '所选持仓来源'
    return `确认创建【${product}】${action} ${formatSharesPlain(current.shares)} 份（来源 ${source}）的内部待处理记录？`
  }
  if (current && isInvestmentPreview.value) {
    const action = current.txnType?.trim().toUpperCase() === 'SUBSCRIPTION' ? '申购' : '买入'
    const product = current.productName || '未选择产品'
    return `确认创建【${product}】${action}订单 ￥${formatPlainAmount(current.amount)}？`
  }
  if (!current || !isTransferPreview.value) return '确认正式记账'
  const from = current.accountName || `账户 #${current.accountId ?? '-'}`
  const to = current.targetAccountName || `账户 #${current.targetAccountId ?? '-'}`
  return `确认将 ￥${formatPlainAmount(current.amount)} 从 ${from} 转到 ${to}？`
})

/**
 * 确认弹窗正文：
 * - 卖出 / 赎回明确只创建内部 PENDING 记录并占用份额，不立即减少持仓、不立即增加到账余额；
 * - 投资明确会立即扣减付款账户并增加同额待结算应收，仍需后续结算、不会自动成交；
 * - 转账明确会生成一笔正式转账流水；
 * - 其它类型保持原文案。
 */
const confirmDialogMessage = computed(() => {
  const current = preview.value
  if (current && isSellRedeemPreview.value) {
    const source = current.accountName || '所选持仓来源'
    const target = current.targetAccountName || '所选到账账户'
    return `确认后只创建内部 PENDING 待处理记录，并占用【${source}】的 ${formatSharesPlain(current.shares)} 份；不会立即减少持仓，也不会立即增加【${target}】的到账余额。真正的资金与持仓变化只在后续人工结算时产生。`
  }
  if (current && isInvestmentPreview.value) {
    const account = current.accountName || '付款账户'
    return `确认后将立即从【${account}】扣除 ￥${formatPlainAmount(current.amount)}，并增加同额待结算应收；订单仍需后续结算，不会自动成交。`
  }
  if (isTransferPreview.value) {
    return '本操作会通过后端统一记账入口生成一笔从转出账户到转入账户的正式转账流水，并影响正式账本统计。请先确认金额与账户无误。'
  }
  return '确认后将通过后端统一记账入口生成正式流水，并影响正式账本统计。请确认草稿内容无误。'
})

/** 切换交易类型时清掉不属于该类型的残留字段，保证候选 payload 干净。 */
function handleEditTxnTypeChange(type: DraftTxnType) {
  if (type !== 'TRANSFER' && type !== 'SELL' && type !== 'REDEMPTION') {
    draftEditForm.value.targetAccountId = undefined
    draftEditForm.value.targetAccountNameHint = ''
  }
  if (type !== 'SELL' && type !== 'REDEMPTION') {
    draftEditForm.value.shares = ''
    draftEditForm.value.sourceAccountId = undefined
    draftEditForm.value.sourceAccountNameHint = ''
    productHoldings.value = []
  }
  if (type !== 'BUY' && type !== 'SUBSCRIPTION' && type !== 'SELL' && type !== 'REDEMPTION') {
    draftEditForm.value.productId = undefined
    draftEditForm.value.productNameHint = ''
    draftEditForm.value.expectedNavDate = ''
    draftEditForm.value.expectedConfirmDate = ''
    return
  }
  void ensureProductsLoaded()
  if (type === 'SELL' || type === 'REDEMPTION') {
    void ensureHoldingsLoaded()
  }
}

/** 只读加载启用中的产品主数据；PC 绝不用产品名称自动匹配 productId。 */
async function ensureProductsLoaded() {
  if (productStore.products.length > 0 || productStore.loading) return
  await productStore.fetchProducts()
}

/** 只读加载所选产品在各账户的真实持仓；PC 绝不用账户名称或历史订单猜测持仓来源。 */
async function ensureHoldingsLoaded() {
  const productId = Number(draftEditForm.value.productId)
  if (!Number.isInteger(productId) || productId <= 0) {
    productHoldings.value = []
    return
  }
  try {
    holdingsLoading.value = true
    productHoldings.value = await holdingApi.getProductHoldingsByAccount(productId)
  } catch (error: any) {
    productHoldings.value = []
    ElNotification.error({
      title: '持仓加载失败',
      message: getErrorMessage(error, '无法加载该产品的持仓来源'),
      position: 'bottom-right',
    })
  } finally {
    holdingsLoading.value = false
  }
}

/** 产品切换后重新加载卖出 / 赎回的持仓来源，并清掉不属于新产品来源的选择。 */
async function handleProductChange() {
  draftEditForm.value.sourceAccountId = undefined
  productHoldings.value = []
  if (isSellRedeemEdit.value) {
    await ensureHoldingsLoaded()
  }
}

function formatPlainAmount(amount?: number | null): string {
  if (amount === null || amount === undefined || Number.isNaN(amount)) return '-'
  return Number(amount).toFixed(2)
}

async function loadDrafts() {
  const currentSelectedId = selectedDraft.value?.id
  try {
    loadingDrafts.value = true
    const rows = await draftApi.listDrafts(draftQueryParams.value)
    drafts.value = rows

    if (!currentSelectedId && trySelectRouteDraft(rows)) {
      return
    }

    if (currentSelectedId) {
      const latestSelectedDraft = rows.find((item) => item.id === currentSelectedId) || null
      selectedDraft.value = latestSelectedDraft

      if (!latestSelectedDraft || preview.value?.draftId !== latestSelectedDraft.id) {
        preview.value = null
      }

      if (!latestSelectedDraft || latestSelectedDraft.status !== 'DRAFT') {
        editingDraft.value = false
      }
    }
  } catch (error: any) {
    ElNotification.error({
      title: '草稿加载失败',
      message: getErrorMessage(error, '无法读取草稿列表'),
      position: 'bottom-right',
    })
  } finally {
    loadingDrafts.value = false
  }
}

function trySelectRouteDraft(rows: DraftLedgerEntry[]): boolean {
  if (routeDraftHintHandled.value) return false

  const draftId = Number(route.query.draftId)
  if (!Number.isInteger(draftId) || draftId <= 0) {
    routeDraftHintHandled.value = true
    return false
  }

  const matchedDraft = rows.find((item) => item.id === draftId)
  routeDraftHintHandled.value = true

  if (matchedDraft) {
    selectDraft(matchedDraft)
    return true
  }

  ElNotification.info({
    title: '草稿未在当前列表中',
    message: `未在当前筛选结果中找到草稿 #${draftId}，请切换筛选条件或在列表中查看。`,
    position: 'bottom-right',
  })
  return false
}

async function ensureAccountsLoaded() {
  if (accountStore.accounts.length === 0 && !accountStore.loading) {
    await accountStore.fetchAccounts()
  }
}

async function handleParseText() {
  const text = textInput.value.trim()
  if (!text) return

  try {
    parsing.value = true
    parsedIntent.value = await aiAccountingApi.parseText({
      text,
      sourceRef: `pc-text-entry-${Date.now()}`,
    })
    ElNotification.success({
      title: '解析完成',
      message: '已生成候选记账意图，请确认后再创建草稿',
      position: 'bottom-right',
    })
  } catch (error: any) {
    ElNotification.error({
      title: '解析失败',
      message: getErrorMessage(error, '文本解析接口调用失败'),
      position: 'bottom-right',
    })
  } finally {
    parsing.value = false
  }
}

async function handleCreateDraft() {
  if (!parsedIntent.value) return

  try {
    creatingDraft.value = true
    const result = await aiAccountingApi.draftFromIntent({ intent: parsedIntent.value })
    selectedDraft.value = result.draft
    preview.value = null
    editingDraft.value = false
    statusFilter.value = 'DRAFT'
    await loadDrafts()
    ElNotification.success({
      title: '草稿已创建',
      message: `草稿 #${result.draft.id} 已进入待确认列表，尚未正式入账`,
      position: 'bottom-right',
    })
  } catch (error: any) {
    ElNotification.error({
      title: '创建草稿失败',
      message: getErrorMessage(error, 'draft-from-intent 接口调用失败'),
      position: 'bottom-right',
    })
  } finally {
    creatingDraft.value = false
  }
}

function resetTextEntry() {
  textInput.value = ''
  parsedIntent.value = null
}

function selectDraft(draft: DraftLedgerEntry) {
  selectedDraft.value = draft
  historyVisible.value = false
  history.value = []
  preview.value = null
  editingDraft.value = false
}

function eventLabel(type: string): string {
  return ({ create: '创建', edit: '编辑', preview: '预览', ignored: '忽略', reopen: '恢复',
    confirm_attempt: '尝试确认', confirmed: '确认成功', confirm_failed: '确认失败', copy: '复制' } as Record<string, string>)[type] || type
}

function historyStatus(status?: string | null): string {
  return ({ DRAFT: '待确认', IGNORED: '已忽略', CONFIRMED: '已确认' } as Record<string, string>)[status || ''] || '无'
}

async function loadHistory(draftId: number) {
  historyVisible.value = true
  historyLoading.value = true
  historyError.value = ''
  try {
    const rows = await draftApi.history(draftId)
    if (selectedDraft.value?.id === draftId) history.value = rows
  } catch (error: any) {
    historyError.value = getErrorMessage(error, '无法读取草稿历史')
  } finally {
    historyLoading.value = false
  }
}

async function handleReopen(draft: DraftLedgerEntry) {
  try {
    await ElMessageBox.confirm('恢复后仍需重新预览和二次确认，是否恢复？', '恢复草稿', {
      confirmButtonText: '确认恢复', cancelButtonText: '取消', type: 'warning',
    })
    selectedDraft.value = await draftApi.reopen(draft.id)
    preview.value = null
    await loadDrafts()
    await loadHistory(draft.id)
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') ElNotification.error({ title: '恢复失败', message: getErrorMessage(error, '无法恢复草稿') })
  }
}

async function handleCopy(draft: DraftLedgerEntry) {
  try {
    await ElMessageBox.confirm('将生成新的草稿和来源标识，仍需预览和二次确认。', '复制为新草稿', {
      confirmButtonText: '确认复制', cancelButtonText: '取消', type: 'warning',
    })
    const created = await draftApi.copyConfirmed(draft.id)
    statusFilter.value = 'DRAFT'
    await loadDrafts()
    selectDraft(created)
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') ElNotification.error({ title: '复制失败', message: getErrorMessage(error, '无法复制草稿') })
  }
}

async function openEditDraft(draft: DraftLedgerEntry) {
  if (draft.status !== 'DRAFT') {
    ElNotification.warning({
      title: '不可编辑',
      message: '只有 DRAFT 状态草稿允许编辑。',
      position: 'bottom-right',
    })
    return
  }

  selectedDraft.value = draft
  preview.value = null
  draftEditForm.value = buildEditForm(draft)
  editingDraft.value = true

  try {
    await ensureAccountsLoaded()
  } catch (error: any) {
    ElNotification.error({
      title: '账户加载失败',
      message: getErrorMessage(error, '无法加载账户列表'),
      position: 'bottom-right',
    })
  }

  // 投资 / 卖出 / 赎回草稿需要主人明确选择真实产品，这里只做只读加载，不会自动匹配或下单。
  if (isProductEdit.value) {
    try {
      await ensureProductsLoaded()
    } catch (error: any) {
      ElNotification.error({
        title: '产品加载失败',
        message: getErrorMessage(error, '无法加载产品列表'),
        position: 'bottom-right',
      })
    }
  }

  // 卖出 / 赎回还需要只读加载该产品的真实持仓来源，绝不猜测来源账户。
  if (isSellRedeemEdit.value) {
    await ensureHoldingsLoaded()
  }
}

function cancelEditDraft() {
  editingDraft.value = false
}

async function handleSaveDraftEdit(previewAfterSave = false) {
  if (!selectedDraft.value || selectedDraft.value.status !== 'DRAFT') return
  if (savingDraft.value) return

  const validationMessage = validateDraftEditForm()
  if (validationMessage) {
    ElNotification.warning({
      title: '请先补齐草稿字段',
      message: validationMessage,
      position: 'bottom-right',
    })
    return
  }

  const draftId = selectedDraft.value.id
  const amount = Number(draftEditForm.value.amount)
  const payload = buildUpdatedPayload(selectedDraft.value, amount)
  const missingFields = calculateMissingFields(payload)

  try {
    savingDraft.value = true
    preview.value = null
    const updated = await draftApi.updateDraft(draftId, {
      sourceType: selectedDraft.value.sourceType,
      sourceRef: selectedDraft.value.sourceRef || undefined,
      rawInput: selectedDraft.value.rawInput || undefined,
      parsedPayloadJson: JSON.stringify(payload),
      confidence: selectedDraft.value.confidence ?? undefined,
      missingFieldsJson: JSON.stringify(missingFields),
    })

    selectedDraft.value = updated
    preview.value = null
    statusFilter.value = 'DRAFT'
    await loadDrafts()
    const latestDraftForPreview = selectedDraft.value || updated
    editingDraft.value = false

    ElNotification.success({
      title: '草稿已保存',
      message: '字段已保存到草稿。请重新生成预览后再确认记账。',
      position: 'bottom-right',
    })

    if (previewAfterSave) {
      await handlePreview(latestDraftForPreview)
    }
  } catch (error: any) {
    ElNotification.error({
      title: '保存草稿失败',
      message: getErrorMessage(error, '草稿编辑保存失败'),
      position: 'bottom-right',
    })
  } finally {
    savingDraft.value = false
  }
}

async function handlePreview(draft: DraftLedgerEntry) {
  const draftId = draft.id
  try {
    selectedDraft.value = draft
    editingDraft.value = false
    preview.value = null
    previewing.value = true
    const latestPreview = await draftApi.previewDraft(draftId)

    if (selectedDraft.value?.id === draftId) {
      preview.value = latestPreview
    }
  } catch (error: any) {
    ElNotification.error({
      title: '预览失败',
      message: getErrorMessage(error, '草稿预览接口调用失败'),
      position: 'bottom-right',
    })
  } finally {
    previewing.value = false
  }
}

async function handleIgnore(draft: DraftLedgerEntry) {
  try {
    const result = await ElMessageBox.prompt('请输入忽略原因（可选）', '忽略草稿', {
      confirmButtonText: '忽略',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：重复录入、信息不足、不是记账内容',
    })

    const ignoreReason =
      typeof result === 'object' && result !== null && 'value' in result
        ? String((result as { value?: unknown }).value || '')
        : ''

    await draftApi.ignoreDraft(draft.id, { ignoreReason: ignoreReason || undefined })
    preview.value = null
    editingDraft.value = false
    await loadDrafts()
    ElNotification.success({
      title: '已忽略',
      message: `草稿 #${draft.id} 已标记为忽略`,
      position: 'bottom-right',
    })
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElNotification.error({
      title: '忽略失败',
      message: getErrorMessage(error, '草稿忽略接口调用失败'),
      position: 'bottom-right',
    })
  }
}

async function handleConfirm(draft: DraftLedgerEntry) {
  if (
    draft.status !== 'DRAFT' ||
    !preview.value ||
    preview.value.draftId !== draft.id ||
    !preview.value.confirmSupported
  ) {
    ElNotification.warning({
      title: '请先生成可确认预览',
      message: '只有当前草稿的预览返回 confirmSupported=true 后，才能正式确认记账。',
      position: 'bottom-right',
    })
    return
  }

  try {
    await ElMessageBox.confirm(confirmDialogMessage.value, confirmDialogTitle.value, {
      confirmButtonText: '确认记账',
      cancelButtonText: '取消',
      type: 'warning',
    })

    confirming.value = true
    const confirmed = await draftApi.confirmDraft(draft.id)
    selectedDraft.value = confirmed
    preview.value = null
    editingDraft.value = false
    await loadDrafts()
    window.dispatchEvent(new CustomEvent('data-refresh'))
    ElNotification.success({
      title: '确认完成',
      message: `草稿 #${draft.id} 已正式记账`,
      position: 'bottom-right',
    })
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElNotification.error({
      title: '确认失败',
      message: getErrorMessage(error, '草稿确认接口调用失败'),
      position: 'bottom-right',
    })
  } finally {
    confirming.value = false
  }
}

function emptyDraftEditForm(): DraftEditForm {
  return {
    txnType: '',
    amount: '',
    note: '',
    accountId: undefined,
    targetAccountId: undefined,
    accountNameHint: '',
    targetAccountNameHint: '',
    productId: undefined,
    productNameHint: '',
    expectedNavDate: '',
    expectedConfirmDate: '',
    shares: '',
    sourceAccountId: undefined,
    sourceAccountNameHint: '',
  }
}

function buildEditForm(draft: DraftLedgerEntry): DraftEditForm {
  const payload = normalizeIntentPayload(parseJsonRecord(draft.parsedPayloadJson))
  const amount = asNumber(payload?.amount)
  const accountId =
    asNumber(payload?.accountId) ??
    asNumber(payload?.cashAccountId) ??
    asNumber(payload?.sourceAccountId)
  const targetAccountId =
    asNumber(payload?.targetAccountId) ??
    asNumber(payload?.toAccountId) ??
    asNumber(payload?.destinationAccountId)

  const productId = asNumber(payload?.productId)
  const shares = asNumber(payload?.shares)
  const sourceAccountId = asNumber(payload?.sourceAccountId)

  return {
    txnType: normalizeEditTxnType(asString(payload?.txnType)),
    amount: amount === null ? '' : String(amount),
    note: asString(payload?.note) || draft.rawInput || '',
    accountId: accountId === null ? undefined : accountId,
    targetAccountId: targetAccountId === null ? undefined : targetAccountId,
    accountNameHint: asString(payload?.accountNameHint) || '',
    targetAccountNameHint: asString(payload?.targetAccountNameHint) || '',
    productId: productId === null ? undefined : productId,
    productNameHint: asString(payload?.productNameHint) || '',
    expectedNavDate: asString(payload?.expectedNavDate) || '',
    expectedConfirmDate: asString(payload?.expectedConfirmDate) || '',
    shares: shares === null ? '' : String(shares),
    sourceAccountId: sourceAccountId === null ? undefined : sourceAccountId,
    sourceAccountNameHint: asString(payload?.sourceAccountNameHint) || '',
  }
}

function validateDraftEditForm(): string | null {
  if (!draftEditForm.value.txnType) return '请选择交易类型。'

  if (isSellRedeemEdit.value) {
    const shares = Number(draftEditForm.value.shares)
    if (!Number.isFinite(shares) || shares <= 0) return '卖出 / 赎回份额必须是大于 0 的数字。'

    const productId = Number(draftEditForm.value.productId)
    if (!Number.isInteger(productId) || productId <= 0) return '卖出 / 赎回必须选择真实产品。'

    const sourceAccountId = Number(draftEditForm.value.sourceAccountId)
    if (!Number.isInteger(sourceAccountId) || sourceAccountId <= 0) {
      return '卖出 / 赎回必须选择该产品真实的持仓来源账户。'
    }

    const targetAccountId = Number(draftEditForm.value.targetAccountId)
    if (!Number.isInteger(targetAccountId) || targetAccountId <= 0) {
      return '卖出 / 赎回必须选择到账账户。'
    }

    return null
  }

  const amount = Number(draftEditForm.value.amount)
  if (!Number.isFinite(amount) || amount <= 0) return '金额必须是大于 0 的数字。'

  const accountId = Number(draftEditForm.value.accountId)
  if (!Number.isInteger(accountId) || accountId <= 0) return '请选择有效的现金或活钱账户。'

  if (draftEditForm.value.txnType === 'TRANSFER') {
    const targetAccountId = Number(draftEditForm.value.targetAccountId)
    if (!Number.isInteger(targetAccountId) || targetAccountId <= 0) {
      return '转账必须选择转入账户。'
    }
    if (targetAccountId === accountId) {
      return '转出账户与转入账户不能相同，请重新选择转入账户。'
    }
  }

  if (isInvestmentEdit.value) {
    const productId = Number(draftEditForm.value.productId)
    if (!Number.isInteger(productId) || productId <= 0) {
      return '投资买入 / 申购必须选择真实产品。'
    }
  }

  return null
}

function buildUpdatedPayload(draft: DraftLedgerEntry, amount: number): Record<string, unknown> {
  const payload = {
    ...(normalizeIntentPayload(parseJsonRecord(draft.parsedPayloadJson)) || {}),
  }

  payload.sourceType = payload.sourceType || draft.sourceType || 'APP_FORM'
  payload.sourceRef = payload.sourceRef || draft.sourceRef || null
  payload.rawInput = draft.rawInput || payload.rawInput || ''
  payload.txnType = draftEditForm.value.txnType
  payload.note = draftEditForm.value.note.trim() || draft.rawInput || ''

  if (isSellRedeemEdit.value) {
    // SELL / REDEMPTION 只使用份额与持仓来源，不写 amount / 付款账户。
    payload.shares = Number(draftEditForm.value.shares)
    payload.sourceAccountId = draftEditForm.value.sourceAccountId
    payload.sourceAccountNameHint = draftEditForm.value.sourceAccountNameHint.trim() || null
    payload.targetAccountId = draftEditForm.value.targetAccountId
    payload.targetAccountNameHint = draftEditForm.value.targetAccountNameHint.trim() || null
    delete payload.amount
    delete payload.accountId
    delete payload.accountNameHint
  } else {
    payload.amount = amount
    payload.accountId = draftEditForm.value.accountId
    payload.accountNameHint = draftEditForm.value.accountNameHint.trim() || null
    delete payload.shares
    delete payload.sourceAccountId
    delete payload.sourceAccountNameHint
    if (draftEditForm.value.txnType === 'TRANSFER') {
      payload.targetAccountId = draftEditForm.value.targetAccountId
      payload.targetAccountNameHint = draftEditForm.value.targetAccountNameHint.trim() || null
    } else {
      delete payload.targetAccountId
      delete payload.targetAccountNameHint
    }
  }

  if (isProductEdit.value) {
    payload.productId = draftEditForm.value.productId
    payload.productNameHint = draftEditForm.value.productNameHint.trim() || null
    payload.orderType = draftEditForm.value.txnType
    if (isInvestmentEdit.value) {
      payload.expectedNavDate = draftEditForm.value.expectedNavDate.trim() || null
      payload.expectedConfirmDate = draftEditForm.value.expectedConfirmDate.trim() || null
    } else {
      delete payload.expectedNavDate
      delete payload.expectedConfirmDate
    }
  } else {
    delete payload.productId
    delete payload.productNameHint
    delete payload.expectedNavDate
    delete payload.expectedConfirmDate
    delete payload.orderType
  }
  payload.missingFields = calculateMissingFields(payload)

  return payload
}

function calculateMissingFields(payload: Record<string, unknown>): string[] {
  const missingFields: string[] = []
  const txnType = normalizeEditTxnType(asString(payload.txnType))

  if (!txnType) missingFields.push('txnType')

  if (txnType === 'SELL' || txnType === 'REDEMPTION') {
    const shares = asNumber(payload.shares)
    if (shares === null || shares <= 0) missingFields.push('shares')

    const productId = asNumber(payload.productId)
    if (productId === null || productId <= 0) missingFields.push('productId')

    const sourceAccountId = asNumber(payload.sourceAccountId)
    if (sourceAccountId === null || sourceAccountId <= 0) missingFields.push('sourceAccountId')

    const targetAccountId = asNumber(payload.targetAccountId)
    if (targetAccountId === null || targetAccountId <= 0) missingFields.push('targetAccountId')

    return missingFields
  }

  const amount = asNumber(payload.amount)
  const accountId = asNumber(payload.accountId)

  if (amount === null || amount <= 0) missingFields.push('amount')
  if (accountId === null || accountId <= 0) missingFields.push('accountId')

  if (txnType === 'TRANSFER') {
    const targetAccountId = asNumber(payload.targetAccountId)
    if (targetAccountId === null || targetAccountId <= 0) missingFields.push('targetAccountId')
  }

  if (txnType === 'BUY' || txnType === 'SUBSCRIPTION') {
    const productId = asNumber(payload.productId)
    if (productId === null || productId <= 0) missingFields.push('productId')
  }

  return missingFields
}

function summarizeDraft(draft: DraftLedgerEntry): string {
  const payload = normalizeIntentPayload(parseJsonRecord(draft.parsedPayloadJson))
  const rawTxnType = asString(payload?.txnType)?.trim().toUpperCase()
  const sellRedeem = rawTxnType === 'SELL' || rawTxnType === 'REDEMPTION'
  const txnType = formatTxnType(asString(payload?.txnType))
  const amount = sellRedeem
    ? formatShares(asNumber(payload?.shares))
    : formatAmount(asNumber(payload?.amount))
  const account = formatPreviewAccount(asNumber(payload?.accountId))
  const targetAccountId = asNumber(payload?.targetAccountId)
  const accountLabel =
    targetAccountId === null ? account : `${account} → ${formatPreviewAccount(targetAccountId)}`
  const note = asString(payload?.note) || draft.rawInput || '-'
  const productId = asNumber(payload?.productId)
  const productLabel = productId === null ? '' : ` / 产品 #${productId}`
  return `${txnType} / ${amount} / ${accountLabel}${productLabel} / ${note}`
}

function parseMissingFields(raw?: string | null): string[] {
  const parsed = safeJsonParse(raw)
  if (Array.isArray(parsed)) {
    return parsed.map(String).filter(Boolean)
  }
  if (typeof parsed === 'string' && parsed) {
    return [parsed]
  }
  return []
}

function parseJsonRecord(raw?: string | null): Record<string, unknown> | null {
  const parsed = safeJsonParse(raw)
  return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
    ? (parsed as Record<string, unknown>)
    : null
}

function normalizeIntentPayload(payload: Record<string, unknown> | null): Record<string, unknown> | null {
  if (!payload) return null

  const nestedIntent = payload.intent
  if (nestedIntent && typeof nestedIntent === 'object' && !Array.isArray(nestedIntent)) {
    return nestedIntent as Record<string, unknown>
  }

  return payload
}

function safeJsonParse(raw?: string | null): unknown {
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return raw
  }
}

function formatJson(raw?: string | null): string {
  const parsed = safeJsonParse(raw)
  if (parsed === null || parsed === undefined || parsed === '') return '-'
  return typeof parsed === 'string' ? parsed : JSON.stringify(parsed, null, 2)
}

function asString(value: unknown): string | null {
  return typeof value === 'string' ? value : null
}

function asNumber(value: unknown): number | null {
  if (typeof value === 'number') return value
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

function normalizeEditTxnType(type?: string | null): DraftTxnType {
  const normalized = type?.trim().toUpperCase()
  if (normalized === 'EXPENSE' || normalized === 'INCOME' || normalized === 'TRANSFER') {
    return normalized
  }
  if (normalized === 'BUY' || normalized === 'SUBSCRIPTION') {
    return normalized
  }
  if (normalized === 'SELL' || normalized === 'REDEMPTION') {
    return normalized
  }
  return ''
}

function getErrorMessage(error: any, fallback: string): string {
  const data = error?.response?.data

  if (typeof data === 'string' && data.trim()) {
    return data
  }

  if (data && typeof data === 'object') {
    const message = (data as { message?: unknown }).message
    const detail = (data as { error?: unknown }).error

    if (typeof message === 'string' && message.trim()) {
      return message
    }

    if (typeof detail === 'string' && detail.trim()) {
      return detail
    }
  }

  return error?.message || fallback
}

function formatTxnType(type?: string | null): string {
  const labels: Record<string, string> = {
    EXPENSE: '支出',
    INCOME: '收入',
    TRANSFER: '转账',
    BUY: '买入',
    SUBSCRIPTION: '申购',
    SELL: '卖出',
    REDEMPTION: '赎回',
  }
  return type ? labels[type] || type : '-'
}

function formatStatus(status: DraftLedgerStatus): string {
  const labels: Record<DraftLedgerStatus, string> = {
    DRAFT: '待确认',
    CONFIRMED: '已确认',
    IGNORED: '已忽略',
  }
  return labels[status] || status
}

function statusTagClass(status: DraftLedgerStatus): string {
  const classes: Record<DraftLedgerStatus, string> = {
    DRAFT: 'orange',
    CONFIRMED: 'green',
    IGNORED: 'gray',
  }
  return classes[status] || 'gray'
}

function formatAmount(amount?: number | null): string {
  if (amount === null || amount === undefined || Number.isNaN(amount)) {
    return '-'
  }
  return `￥ ${Number(amount).toFixed(2)}`
}

function formatConfidence(confidence?: number | null): string {
  if (confidence === null || confidence === undefined || Number.isNaN(confidence)) {
    return '-'
  }
  return `${Math.round(confidence * 100)}%`
}

function formatDateTime(value?: string | null): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

function formatPreviewAccount(accountId?: number | null, accountName?: string | null): string {
  if (!accountId) return '-'
  if (accountName) return `${accountName} #${accountId}`
  const account = accountStore.getAccountById(accountId)
  return account ? `${account.accountName} #${account.id}` : `账户 ID ${accountId}`
}

function formatImpactDirection(direction?: string | null): string {
  const labels: Record<string, string> = {
    DECREASE: '账户资金减少',
    INCREASE: '账户资金增加',
    NONE: '暂无可确认资金影响',
  }
  return direction ? labels[direction] || direction : '-'
}

function formatSignedAmount(amount?: number | null): string {
  if (amount === null || amount === undefined || Number.isNaN(amount)) {
    return '预计变动：-'
  }
  const numericAmount = Number(amount)
  const prefix = numericAmount > 0 ? '+' : ''
  return `预计变动：${prefix}¥${numericAmount.toFixed(2)}`
}

function formatBooleanImpact(value?: boolean | null): string {
  return value ? '会生成' : '不会生成'
}

function formatProductOption(product: ProductMaster): string {
  return `${product.productName} / ${product.productCode} / ${product.assetType} / ${product.currency}`
}

function formatProductMeta(product?: ProductMaster): string {
  if (!product) return '未选择产品'
  return `${product.productCode} · ${product.assetType} · ${product.currency}`
}

/** 份额展示：去掉无意义尾随零，空值按 0 处理。 */
function formatShares(shares?: number | null): string {
  if (shares === null || shares === undefined || Number.isNaN(shares)) return '-'
  return `${formatSharesPlain(shares)} 份`
}

function formatSharesPlain(shares?: number | null): string {
  if (shares === null || shares === undefined || Number.isNaN(shares)) return '-'
  return String(Number(shares))
}

/** 卖出 / 赎回持仓来源选项文案：账户名 + 当前持仓份额。 */
function formatHoldingOption(holding: AccountHoldingInfo): string {
  return `${holding.accountName} / 持仓 ${formatSharesPlain(holding.shares)} 份`
}

function formatHoldingMeta(holding?: AccountHoldingInfo): string {
  if (!holding) return '未选择持仓来源'
  const parent = holding.parentAccountName ? `${holding.parentAccountName} · ` : ''
  return `${parent}持仓 ${formatSharesPlain(holding.shares)} 份 · 市值 ￥${formatPlainAmount(holding.marketValue)}`
}

function formatAccountOption(account: Account): string {
  return `${account.accountName} / ${account.accountType} / ${account.fundUsage || '未标记用途'}`
}

function formatAccountMeta(account: Account): string {
  const parts = [account.accountType, account.fundUsage || '未标记用途', account.currency]
  return parts.filter(Boolean).join(' · ')
}

function accountHintScore(account: Account, hint: string): number {
  let score = 0
  const name = account.accountName.toLowerCase()
  const type = account.accountType.toLowerCase()
  const usage = String(account.fundUsage || '').toLowerCase()

  if (hint && name.includes(hint)) score += 10
  if (hint && type.includes(hint)) score += 4
  if (hint && usage.includes(hint)) score += 2
  if (['CASH', 'BANK', 'PAYMENT', 'MMF'].includes(account.accountType)) score += 3
  if (account.fundUsage === 'SPENDABLE') score += 2

  return score
}

function refreshDraftInbox() {
  loadDrafts()
}

onMounted(() => {
  loadDrafts()
  ensureAccountsLoaded()
  window.addEventListener('data-refresh', refreshDraftInbox)
})

onBeforeUnmount(() => {
  window.removeEventListener('data-refresh', refreshDraftInbox)
})
</script>

<style scoped>
.draft-inbox-page {
  padding-bottom: 24px;
}

.safety-banner {
  margin: 4px 0 14px;
  padding: 12px 14px;
  border: 1px solid rgba(245, 158, 11, 0.28);
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.12), rgba(255, 255, 255, 0.84));
  color: #92400e;
  font-size: 13px;
  line-height: 1.7;
}

.draft-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  gap: var(--gap);
}

.text-entry-card,
.detail-card,
.draft-list-card {
  min-width: 0;
}

.entry-actions,
.detail-actions,
.edit-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 14px;
}

.intent-panel,
.preview-panel,
.edit-panel {
  margin-top: 14px;
  padding: 14px;
  border: 1px solid rgba(78, 164, 255, 0.16);
  border-radius: 14px;
  background: rgba(78, 164, 255, 0.06);
}

.edit-panel {
  border-color: rgba(34, 197, 94, 0.22);
  background: linear-gradient(135deg, rgba(34, 197, 94, 0.08), rgba(78, 164, 255, 0.06));
}

.edit-hint,
.edit-disabled-tip {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.78);
  color: var(--muted);
  font-size: 12px;
  line-height: 1.65;
}

.edit-disabled-tip {
  margin-top: 12px;
  color: #92400e;
  background: rgba(245, 158, 11, 0.1);
}

.intent-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  font-weight: 700;
}

.intent-grid,
.edit-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.form-field {
  min-width: 0;
}

.form-field-wide {
  grid-column: 1 / -1;
}

.intent-grid > div,
.detail-row,
.impact-card,
.warning-list {
  padding: 10px;
  border: 1px solid rgba(230, 238, 247, 0.95);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.86);
}

.preview-impact-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.impact-card p,
.warning-list p {
  margin: 4px 0 0;
  color: var(--text);
  line-height: 1.65;
}

.impact-card em {
  display: inline-block;
  margin-top: 6px;
  color: #0f766e;
  font-style: normal;
  font-weight: 700;
}

.field-label {
  display: block;
  margin-bottom: 4px;
  color: var(--muted);
  font-size: 12px;
}

.intent-note,
.raw-block {
  margin-top: 12px;
}

.intent-note p,
.raw-block p {
  margin: 4px 0 0;
  color: var(--text);
  line-height: 1.7;
  word-break: break-word;
}

.missing-list,
.missing-chips {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.missing-list {
  margin-top: 12px;
}

.empty-detail {
  min-height: 220px;
  display: grid;
  place-items: center;
  color: var(--muted);
  text-align: center;
  border: 1px dashed rgba(100, 116, 139, 0.24);
  border-radius: 14px;
  background: rgba(100, 116, 139, 0.04);
}

.detail-section {
  display: grid;
  gap: 8px;
}

.detail-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.account-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.account-option small {
  color: var(--muted);
}

.json-details {
  margin-top: 14px;
  color: var(--muted);
  font-size: 12px;
}

.json-details summary {
  cursor: pointer;
}

.json-details pre {
  max-height: 220px;
  overflow: auto;
  margin: 10px 0 0;
  padding: 12px;
  border-radius: 12px;
  background: #0f172a;
  color: #dbeafe;
  white-space: pre-wrap;
  word-break: break-word;
}

.draft-list-card {
  margin-top: var(--gap);
}

.draft-table-container {
  overflow: auto;
  max-height: calc(100vh - 520px);
  min-height: 280px;
}

.draft-table {
  min-width: 1100px;
}

.draft-table thead th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: linear-gradient(135deg, rgba(78, 164, 255, 0.12), rgba(124, 199, 255, 0.08));
  color: var(--text);
  font-weight: 600;
}

.draft-table tbody tr {
  cursor: pointer;
}

.draft-table tbody tr.active td {
  background: rgba(78, 164, 255, 0.12);
}

.draft-raw {
  color: var(--text);
  word-break: break-word;
}

.table-actions {
  display: inline-flex;
  justify-content: flex-end;
  gap: 6px;
  flex-wrap: nowrap;
}

.table-empty {
  padding: 28px;
  text-align: center;
}

@media (max-width: 1180px) {
  .draft-grid {
    grid-template-columns: 1fr;
  }

  .draft-table-container {
    max-height: none;
  }
}

@media (max-width: 720px) {
  .intent-grid,
  .edit-form-grid,
  .preview-impact-grid {
    grid-template-columns: 1fr;
  }
}
</style>
