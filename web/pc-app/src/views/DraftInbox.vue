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
                <el-select v-model="draftEditForm.txnType" placeholder="请选择类型">
                  <el-option label="支出" value="EXPENSE" />
                  <el-option label="收入" value="INCOME" />
                </el-select>
              </label>

              <label class="form-field">
                <span class="field-label">金额</span>
                <el-input
                  v-model="draftEditForm.amount"
                  inputmode="decimal"
                  placeholder="请输入正数金额"
                />
              </label>

              <label class="form-field form-field-wide">
                <span class="field-label">现金/活钱账户</span>
                <el-select
                  v-model="draftEditForm.accountId"
                  filterable
                  clearable
                  :loading="accountStore.loading"
                  placeholder="请选择实际记账账户"
                >
                  <el-option
                    v-for="account in rankedAccountOptions"
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

              <label class="form-field form-field-wide">
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
                <span class="field-label">金额</span>
                <strong>{{ formatAmount(preview.amount) }}</strong>
              </div>
              <div>
                <span class="field-label">账户</span>
                <strong>{{ formatPreviewAccount(preview.accountId) }}</strong>
              </div>
              <div>
                <span class="field-label">草稿 ID</span>
                <strong>{{ preview.draftId }}</strong>
              </div>
            </div>
            <div class="preview-impact-grid">
              <div class="impact-card">
                <span class="field-label">账户影响</span>
                <strong>{{ formatImpactDirection(preview.impactDirection) }}</strong>
                <p>
                  {{ formatPreviewAccount(preview.accountId, preview.accountName) }}
                  <span v-if="preview.accountType"> / {{ preview.accountType }}</span>
                  <span v-if="preview.fundUsage"> / {{ preview.fundUsage }}</span>
                </p>
                <em>{{ formatSignedAmount(preview.accountDelta) }}</em>
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
import { ElMessageBox, ElNotification } from 'element-plus'
import { aiAccountingApi, draftApi, useAccountStore } from '@wealth-hub/shared'
import type {
  Account,
  AccountingIntent,
  DraftLedgerEntry,
  DraftLedgerStatus,
  DraftPreview,
} from '@wealth-hub/shared'

type DraftTxnType = 'EXPENSE' | 'INCOME' | ''

interface DraftEditForm {
  txnType: DraftTxnType
  amount: string
  note: string
  accountId?: number
  accountNameHint: string
}

const accountStore = useAccountStore()
const textInput = ref('')
const parsedIntent = ref<AccountingIntent | null>(null)
const drafts = ref<DraftLedgerEntry[]>([])
const selectedDraft = ref<DraftLedgerEntry | null>(null)
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

async function loadDrafts() {
  const currentSelectedId = selectedDraft.value?.id
  try {
    loadingDrafts.value = true
    const rows = await draftApi.listDrafts(draftQueryParams.value)
    drafts.value = rows

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
  preview.value = null
  editingDraft.value = false
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
    await ElMessageBox.confirm(
      '确认后将通过后端统一记账入口生成正式流水，并影响正式账本统计。请确认草稿内容无误。',
      '确认正式记账',
      {
        confirmButtonText: '确认记账',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )

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
    accountNameHint: '',
  }
}

function buildEditForm(draft: DraftLedgerEntry): DraftEditForm {
  const payload = normalizeIntentPayload(parseJsonRecord(draft.parsedPayloadJson))
  const amount = asNumber(payload?.amount)
  const accountId = asNumber(payload?.accountId)

  return {
    txnType: normalizeEditTxnType(asString(payload?.txnType)),
    amount: amount === null ? '' : String(amount),
    note: asString(payload?.note) || draft.rawInput || '',
    accountId: accountId === null ? undefined : accountId,
    accountNameHint: asString(payload?.accountNameHint) || '',
  }
}

function validateDraftEditForm(): string | null {
  if (!draftEditForm.value.txnType) return '请选择交易类型。'

  const amount = Number(draftEditForm.value.amount)
  if (!Number.isFinite(amount) || amount <= 0) return '金额必须是大于 0 的数字。'

  const accountId = Number(draftEditForm.value.accountId)
  if (!Number.isInteger(accountId) || accountId <= 0) return '请选择有效的现金或活钱账户。'

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
  payload.amount = amount
  payload.note = draftEditForm.value.note.trim() || draft.rawInput || ''
  payload.accountId = draftEditForm.value.accountId
  payload.accountNameHint = draftEditForm.value.accountNameHint.trim() || null
  payload.missingFields = calculateMissingFields(payload)

  return payload
}

function calculateMissingFields(payload: Record<string, unknown>): string[] {
  const missingFields: string[] = []
  const txnType = normalizeEditTxnType(asString(payload.txnType))
  const amount = asNumber(payload.amount)
  const accountId = asNumber(payload.accountId)

  if (!txnType) missingFields.push('txnType')
  if (amount === null || amount <= 0) missingFields.push('amount')
  if (accountId === null || accountId <= 0) missingFields.push('accountId')

  return missingFields
}

function summarizeDraft(draft: DraftLedgerEntry): string {
  const payload = normalizeIntentPayload(parseJsonRecord(draft.parsedPayloadJson))
  const txnType = formatTxnType(asString(payload?.txnType))
  const amount = formatAmount(asNumber(payload?.amount))
  const account = formatPreviewAccount(asNumber(payload?.accountId))
  const note = asString(payload?.note) || draft.rawInput || '-'
  return `${txnType} / ${amount} / ${account} / ${note}`
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
  if (normalized === 'EXPENSE' || normalized === 'INCOME') return normalized
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
