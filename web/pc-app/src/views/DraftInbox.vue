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
              :disabled="!preview?.confirmSupported || selectedDraft.status !== 'DRAFT'"
              :loading="confirming"
              @click="handleConfirm(selectedDraft)"
            >
              确认记账
            </el-button>
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
                <span class="field-label">账户 ID</span>
                <strong>{{ preview.accountId ?? '-' }}</strong>
              </div>
              <div>
                <span class="field-label">草稿 ID</span>
                <strong>{{ preview.draftId }}</strong>
              </div>
            </div>
            <div class="intent-note">
              <span class="field-label">提示</span>
              <p>{{ preview.message || '预览完成，请确认内容无误后再正式记账。' }}</p>
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
import { aiAccountingApi, draftApi } from '@wealth-hub/shared'
import type {
  AccountingIntent,
  DraftLedgerEntry,
  DraftLedgerStatus,
  DraftPreview,
} from '@wealth-hub/shared'

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

const draftQueryParams = computed(() => ({
  status: statusFilter.value || undefined,
  page: 1,
  pageSize: 50,
}))

async function loadDrafts() {
  try {
    loadingDrafts.value = true
    const rows = await draftApi.listDrafts(draftQueryParams.value)
    drafts.value = rows

    if (selectedDraft.value) {
      selectedDraft.value = rows.find((item) => item.id === selectedDraft.value?.id) || null
    }
  } catch (error: any) {
    ElNotification.error({
      title: '草稿加载失败',
      message: error.message || '无法读取草稿列表',
      position: 'bottom-right',
    })
  } finally {
    loadingDrafts.value = false
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
      message: error.message || '文本解析接口调用失败',
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
    await loadDrafts()
    ElNotification.success({
      title: '草稿已创建',
      message: `草稿 #${result.draft.id} 已进入待确认列表，尚未正式入账`,
      position: 'bottom-right',
    })
  } catch (error: any) {
    ElNotification.error({
      title: '创建草稿失败',
      message: error.message || 'draft-from-intent 接口调用失败',
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
}

async function handlePreview(draft: DraftLedgerEntry) {
  try {
    selectedDraft.value = draft
    previewing.value = true
    preview.value = await draftApi.previewDraft(draft.id)
  } catch (error: any) {
    ElNotification.error({
      title: '预览失败',
      message: error.message || '草稿预览接口调用失败',
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
      message: error.message || '草稿忽略接口调用失败',
      position: 'bottom-right',
    })
  }
}

async function handleConfirm(draft: DraftLedgerEntry) {
  if (!preview.value?.confirmSupported) return

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
      message: error.message || '草稿确认接口调用失败',
      position: 'bottom-right',
    })
  } finally {
    confirming.value = false
  }
}

function summarizeDraft(draft: DraftLedgerEntry): string {
  const payload = parseJsonRecord(draft.parsedPayloadJson)
  const txnType = formatTxnType(asString(payload?.txnType))
  const amount = formatAmount(asNumber(payload?.amount))
  const note = asString(payload?.note) || draft.rawInput || '-'
  return `${txnType} / ${amount} / ${note}`
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

function refreshDraftInbox() {
  loadDrafts()
}

onMounted(() => {
  loadDrafts()
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
.detail-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 14px;
}

.intent-panel,
.preview-panel {
  margin-top: 14px;
  padding: 14px;
  border: 1px solid rgba(78, 164, 255, 0.16);
  border-radius: 14px;
  background: rgba(78, 164, 255, 0.06);
}

.intent-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  font-weight: 700;
}

.intent-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.intent-grid > div,
.detail-row {
  padding: 10px;
  border: 1px solid rgba(230, 238, 247, 0.95);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.86);
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
  .intent-grid {
    grid-template-columns: 1fr;
  }
}
</style>
