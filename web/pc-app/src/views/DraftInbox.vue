<template>
  <div class="draft-inbox-page">
    <header class="draft-hero">
      <div><span class="draft-eyebrow">REVIEW ROOM / 人工复核</span><h1>先留草稿，再认真确认。</h1><p>把零散记录放在这里，逐笔补齐、预览、核对。</p></div>
      <button class="btn primary" :disabled="operationBusy" @click="textEntryVisible = true">＋ 文本录入</button>
    </header>
    <div class="draft-safety"><strong>草稿不计入余额</strong><span>01 补齐字段 → 02 服务器影响预览 → 03 你明确二次确认。投资订单仍需后续人工结算。</span></div>
    <section class="card draft-list-card" aria-label="草稿列表">
      <div class="draft-toolbar">
        <div class="draft-status-tabs" role="group" aria-label="草稿状态">
          <button v-for="tab in statusTabs" :key="tab.value" :aria-pressed="statusFilter === tab.value" :class="{ active: statusFilter === tab.value }" :disabled="operationBusy" @click="changeStatus(tab.value)">{{ tab.label }}</button>
        </div>
        <el-button :loading="loadingDrafts" :disabled="operationBusy" @click="loadDrafts">刷新草稿</el-button>
      </div>
      <div class="draft-filters">
        <label><span>搜索本页</span><el-input v-model="searchText" clearable placeholder="原文、来源标识或草稿编号" /></label>
        <label><span>来源</span><el-select v-model="sourceFilter" placeholder="全部来源" clearable><el-option v-for="source in sourceOptions" :key="source" :value="source" :label="sourceLabel(source)" /></el-select></label>
        <label><span>方向</span><el-select v-model="typeFilter" placeholder="全部方向" clearable><el-option v-for="type in txnTypes" :key="type" :value="type" :label="formatTxnType(type)" /></el-select></label>
        <label><span>更新时间</span><el-select v-model="timeFilter" placeholder="不限时间"><el-option label="不限时间" value="" /><el-option label="最近 7 天" value="7" /><el-option label="最近 30 天" value="30" /></el-select></label>
      </div>
      <p class="draft-scope">状态由服务器筛选；来源、方向、时间与搜索只筛选当前页（每页最多 50 条）。不汇总草稿金额为资产。</p>
      <div v-if="loadingDrafts" class="draft-state" role="status">正在读取草稿…</div>
      <div v-else-if="listError" class="draft-state" role="alert"><p>{{ listError }}</p><el-button @click="loadDrafts">重试读取</el-button></div>
      <div v-else-if="!drafts.length" class="draft-state"><strong>这一页还没有草稿</strong><p>试试其他状态，或用文本录入留下一笔候选记录。</p></div>
      <div v-else-if="!visibleDrafts.length" class="draft-state"><strong>本页没有匹配的草稿</strong><p>调整筛选，或继续翻页查看。</p><el-button @click="resetFilters">清除本页筛选</el-button></div>
      <div v-else class="draft-cards">
        <article v-for="draft in visibleDrafts" :key="draft.id" class="draft-item" :class="{ active: selectedDraft?.id === draft.id }">
          <div class="draft-item-top"><span class="tag" :class="statusTagClass(draft.status)">{{ formatStatus(draft.status) }}</span><span class="draft-number">#{{ draft.id }}</span></div>
          <h3>{{ draftHeading(draft) }}</h3><p class="draft-raw">{{ draft.rawInput || '未提供原始输入' }}</p>
          <p class="draft-summary">{{ summarizeDraft(draft) }}</p>
          <div class="missing-chips"><span v-for="field in parseMissingFields(draft.missingFieldsJson)" :key="field" class="tag orange tiny">待补 {{ fieldLabel(field) }}</span><span v-if="!parseMissingFields(draft.missingFieldsJson).length" class="draft-scope">字段待服务器复核</span></div>
          <div class="draft-meta"><span>{{ sourceLabel(draft.sourceType) }}</span><time>{{ formatDateTime(draft.updatedAt || draft.createdAt) }}</time></div>
          <div v-if="draft.sourceRef" class="draft-ref">来源标识：{{ draft.sourceRef }}</div>
          <div class="draft-item-actions"><button class="btn" :disabled="operationBusy" @click="selectDraft(draft)">{{ draft.status === 'DRAFT' ? '复核这笔 →' : '查看记录 →' }}</button><button v-if="draft.status === 'DRAFT'" class="btn" :disabled="operationBusy" @click="openEditDraft(draft)">补齐字段</button></div>
        </article>
      </div>
      <div class="draft-pagination"><span>第 {{ page }} 页 · 本页显示 {{ visibleDrafts.length }} / {{ drafts.length }} 条</span><div><el-button :disabled="page === 1 || loadingDrafts || operationBusy" @click="changePage(-1)">上一页</el-button><el-button :disabled="drafts.length < 50 || loadingDrafts || operationBusy" @click="changePage(1)">下一页</el-button></div></div>
    </section>
    <el-dialog v-model="textEntryVisible" title="文本录入 · 只生成草稿" width="min(640px, 94vw)" :before-close="closeTextEntry" :close-on-click-modal="false" :close-on-press-escape="!parsing && !creatingDraft">
      <p class="draft-scope">描述一笔记录，先解析，再由你手动生成草稿。解析结果仍需复核。</p>
        <el-input
          v-model="textInput"
          type="textarea"
          :rows="5"
          :disabled="parsing || creatingDraft"
          maxlength="500"
          show-word-limit
          placeholder="例如：午饭花了 32.5，用余额宝生活费，备注公司楼下简餐"
        />

        <div class="entry-actions">
          <el-button
            type="primary"
            :loading="parsing"
            :disabled="!textInput.trim() || parsing || creatingDraft"
            @click="handleParseText"
          >
            解析文本
          </el-button>
          <el-button
            :loading="creatingDraft"
            :disabled="!parsedIntent || parsing || creatingDraft"
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
            <div v-if="parsedIntent.shares != null">
              <span class="field-label">份额</span><strong>{{ formatShares(parsedIntent.shares) }}</strong>
            </div>
            <div v-if="parsedIntent.productNameHint">
              <span class="field-label">产品提示 · 仍需选择真实产品</span><strong>{{ parsedIntent.productNameHint }}</strong>
            </div>
            <div v-if="parsedIntent.sourceAccountNameHint">
              <span class="field-label">持仓来源提示</span><strong>{{ parsedIntent.sourceAccountNameHint }}</strong>
            </div>
            <div v-if="parsedIntent.targetAccountNameHint">
              <span class="field-label">转入 / 到账提示</span><strong>{{ parsedIntent.targetAccountNameHint }}</strong>
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
              {{ fieldLabel(field) }}
            </span>
          </div>
        </div>

      <p v-if="textError" role="alert" class="draft-error">{{ textError }}</p>
    </el-dialog>
    <el-drawer v-model="detailVisible" title="逐笔复核" size="min(760px, 96vw)" :before-close="closeDetail" :close-on-click-modal="!operationBusy" :close-on-press-escape="!operationBusy">
      <p v-if="detailLoading" role="status">正在读取最新草稿…</p>
      <p v-if="detailError" role="alert" class="draft-error">{{ detailError }}</p>
      <template v-if="selectedDraft">
        <div class="draft-review-heading"><span class="draft-eyebrow">草稿 #{{ selectedDraft.id }}</span><h2>{{ draftHeading(selectedDraft) }}</h2><p>先补齐，再预览。最终操作需要你再次确认。</p></div>
        <p class="draft-summary">{{ summarizeDraft(selectedDraft) }}</p>
        <p v-if="selectedDraft.sourceRef" class="draft-ref">来源标识：{{ selectedDraft.sourceRef }}</p>
        <p v-if="actionError" role="alert" class="draft-error">{{ actionError }}</p>
        <p v-if="uncertainConfirmIds.has(selectedDraft.id)" role="alert" class="draft-error">确认结果未知，请刷新草稿并核对关联流水或订单。本次页面禁止重提，不自动重试。</p>
          <div class="detail-section">
            <div class="detail-row">
              <span>状态</span>
              <span class="tag" :class="statusTagClass(selectedDraft.status)">
                {{ formatStatus(selectedDraft.status) }}
              </span>
            </div>
            <div class="detail-row">
              <span>来源</span>
              <strong>{{ sourceLabel(selectedDraft.sourceType) }}</strong>
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
            <el-button v-if="selectedDraft.confirmOrderId" @click="openSettlementAudit(selectedDraft.confirmOrderId)">查看关联订单与结算审计</el-button>
            <el-button v-if="selectedDraft.status === 'IGNORED'" :disabled="operationBusy || detailLoading" @click="handleReopen(selectedDraft)">恢复草稿</el-button>
            <el-button v-if="selectedDraft.status === 'CONFIRMED'" :disabled="operationBusy || detailLoading" @click="handleCopy(selectedDraft)">复制为新草稿</el-button>
            <el-button
              type="primary"
              plain
              :disabled="selectedDraft.status !== 'DRAFT' || operationBusy || detailLoading"
              @click="openEditDraft(selectedDraft)"
            >
              编辑草稿
            </el-button>
            <el-button :loading="previewing" :disabled="selectedDraft.status !== 'DRAFT' || operationBusy || detailLoading" @click="handlePreview(selectedDraft)">
              生成预览
            </el-button>
            <el-button
              type="warning"
              plain
              :disabled="selectedDraft.status !== 'DRAFT' || operationBusy || detailLoading"
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
              {{ isProductPreview ? '确认内部订单' : '确认记账' }}
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

          <div v-if="preview" class="preview-panel">
            <div class="intent-title">
              <span>确认预览</span>
              <span class="tag" :class="preview.confirmSupported ? 'green' : 'orange'">
                {{ preview.confirmSupported ? '可进入二次确认' : '暂不可确认' }}
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
              <div v-if="isInvestmentPreview"><span class="field-label">付款账户可用余额 · 服务器预览</span><strong>{{ formatAmount(preview.availableBefore) }}</strong></div>
              <div v-if="isProductPreview"><span class="field-label">产品币种</span><strong>{{ preview.productCurrency || '未知' }}</strong></div>
              <div v-if="isInvestmentPreview"><span class="field-label">预计净值日 / 确认日</span><strong>{{ preview.expectedNavDate || '未指定' }} / {{ preview.expectedConfirmDate || '未指定' }}</strong></div>
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
                <p>确认只创建内部 PENDING 记录，登记待处理份额占用</p>
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
                {{ fieldLabel(field) }}
              </span>
            </div>
          </div>

          <details class="json-details">
            <summary>查看解析 JSON</summary>
            <pre>{{ formatJson(selectedDraft.parsedPayloadJson) }}</pre>
          </details>

        <div class="detail-section draft-result" v-if="selectedDraft.status !== 'DRAFT'">
          <p v-if="selectedDraft.ignoreReason">忽略原因：{{ selectedDraft.ignoreReason }}</p>
          <p v-if="selectedDraft.confirmTxnId">关联流水：{{ selectedDraft.confirmTxnId }}</p>
          <p v-if="selectedDraft.confirmOrderId">关联内部订单：{{ selectedDraft.confirmOrderId }}；确认订单不代表已结算到账。</p>
          <p>创建：{{ formatDateTime(selectedDraft.createdAt) }} · 确认：{{ formatDateTime(selectedDraft.confirmedAt) }} · 忽略：{{ formatDateTime(selectedDraft.ignoredAt) }}</p>
        </div>
      </template>
    </el-drawer>
    <el-dialog v-model="editingDraft" title="补齐草稿字段" width="min(720px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!savingDraft" :before-close="closeEditor">
      <p v-if="!editOptionsReady && !editError" role="status">正在读取账户、产品及持仓来源…</p>
      <div v-if="editError" role="alert" class="draft-error">{{ editError }}<el-button :disabled="savingDraft" @click="retryEditOptions">重试选项</el-button></div>
          <div v-if="selectedDraft" class="edit-panel" :inert="savingDraft || !editOptionsReady">
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
                  :loading="optionsLoading"
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
                  :loading="optionsLoading"
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
                  :loading="optionsLoading"
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
                  :loading="optionsLoading"
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


    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, onActivated, onDeactivated, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElNotification } from 'element-plus'
import {
  aiAccountingApi,
  draftApi,
  holdingApi,
  accountApi,
  productApi,
  buildAccountTree,
  getLeafAccounts,
  useUserStore,
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

import { createReadGate } from './managementViewModel'

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

const user = useUserStore()
const identity = () => JSON.stringify([user.token, user.user?.id, user.user?.familyId])
const listGate = createReadGate(identity)
const detailGate = createReadGate(identity)
const previewGate = createReadGate(identity)
const historyGate = createReadGate(identity)
const optionsGate = createReadGate(identity)
const holdingsGate = createReadGate(identity)
const textGate = createReadGate(identity)
const gates = [listGate, detailGate, previewGate, historyGate, optionsGate, holdingsGate, textGate]
const accounts = ref<Account[]>([])
const products = ref<ProductMaster[]>([])
const leafAccounts = computed(() => getLeafAccounts(buildAccountTree(accounts.value)))
const cashLeafAccounts = computed(() => leafAccounts.value.filter(a => a.accountKind === 'REAL' && ['CASH', 'BANK', 'PAYMENT', 'MMF', 'BROKER'].includes(a.accountType)))
const optionsLoading = ref(false)
const editOptionsReady = ref(false)
const editError = ref('')
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const textEntryVisible = ref(false)
const textError = ref('')
const listError = ref('')
const actionError = ref('')
const page = ref(1)
const searchText = ref('')
const sourceFilter = ref('')
const typeFilter = ref('')
const timeFilter = ref('')
const lifecycleBusy = ref(false)
// A lost confirm response is not permission to replay a financial operation.
const uncertainConfirmIds = ref(new Set<number>())
const operationBusy = computed(() => confirming.value || savingDraft.value || lifecycleBusy.value || creatingDraft.value || previewing.value || loadingDrafts.value)
const txnTypes: DraftTxnType[] = ['EXPENSE', 'INCOME', 'TRANSFER', 'BUY', 'SUBSCRIPTION', 'SELL', 'REDEMPTION']
const statusTabs: { value: DraftLedgerStatus | ''; label: string }[] = [{ value: 'DRAFT', label: '待复核' }, { value: 'CONFIRMED', label: '已确认' }, { value: 'IGNORED', label: '已忽略' }, { value: '', label: '全部' }]
const sourceOptions = computed(() => [...new Set(drafts.value.map(d => d.sourceType))])
const visibleDrafts = computed(() => drafts.value.filter(draft => {
  const payload = normalizeIntentPayload(parseJsonRecord(draft.parsedPayloadJson))
  const term = searchText.value.trim().toLowerCase()
  const timestamp = new Date(draft.updatedAt || draft.createdAt || '').getTime()
  return (!sourceFilter.value || draft.sourceType === sourceFilter.value)
    && (!typeFilter.value || payload?.txnType === typeFilter.value)
    && (!timeFilter.value || (Number.isFinite(timestamp) && timestamp >= Date.now() - Number(timeFilter.value) * 86400000 && timestamp <= Date.now()))
    && (!term || `${draft.id} ${draft.rawInput || ''} ${draft.sourceRef || ''}`.toLowerCase().includes(term))
}))
function resetFilters() { searchText.value = ''; sourceFilter.value = ''; typeFilter.value = ''; timeFilter.value = '' }
function changeStatus(status: DraftLedgerStatus | '') { statusFilter.value = status; page.value = 1; resetFilters(); void loadDrafts() }
function changePage(delta: number) { page.value = Math.max(1, page.value + delta); void loadDrafts() }
function sourceLabel(source: string) { return ({ HERMES_TEXT: '文本解析', HERMES_IMAGE: '图片候选', APP_FORM: '手工录入', PAYMENT_NOTIFICATION: '支付通知', MANUAL: '手工录入', manual: '手工录入', wechat: '微信来源', import: '导入', ocr: '图片识别' } as Record<string, string>)[source] || source || '未知来源' }
function fieldLabel(field: string) { return ({ txnType: '交易方向', amount: '金额', accountId: '资金账户', targetAccountId: '转入 / 到账账户', productId: '真实产品', shares: '份额', sourceAccountId: '持仓来源' } as Record<string, string>)[field] || field }
function draftHeading(draft: DraftLedgerEntry) { const payload = normalizeIntentPayload(parseJsonRecord(draft.parsedPayloadJson)); return `${formatTxnType(asString(payload?.txnType))} · ${['SELL', 'REDEMPTION'].includes(String(payload?.txnType)) ? formatShares(asNumber(payload?.shares)) : formatAmount(asNumber(payload?.amount))}` }
function clearPreview() { previewGate.invalidate(); preview.value = null; previewing.value = false }
function closeDetail(done: () => void) { if (operationBusy.value) return; detailVisible.value = false; selectedDraft.value = null; detailGate.invalidate(); historyGate.invalidate(); clearPreview(); done() }
function closeEditor(done: () => void) { if (savingDraft.value) return; cancelEditDraft(); done() }
function closeTextEntry(done: () => void) { if (parsing.value || creatingDraft.value) return; textGate.invalidate(); done() }
function validSelection(draftId: number, owner: string) { return detailGate.active && identity() === owner && detailVisible.value && selectedDraft.value?.id === draftId }

const route = useRoute()
const textInput = ref('')
const parsedIntent = ref<AccountingIntent | null>(null)
const drafts = ref<DraftLedgerEntry[]>([])
const selectedDraft = ref<DraftLedgerEntry | null>(null)
const settlementRouter = useRouter()
function openSettlementAudit(orderId: string) {
  settlementRouter.push({ name: 'Settlements', query: { audit: orderId } })
}
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
  page: page.value,
  pageSize: 50,
}))

const canConfirmSelectedDraft = computed(() => {
  return Boolean(
    selectedDraft.value?.status === 'DRAFT' &&
      preview.value?.draftId === selectedDraft.value.id &&
      preview.value.confirmSupported &&
      !operationBusy.value && !loadingDrafts.value && !detailLoading.value && !editingDraft.value && !uncertainConfirmIds.value.has(selectedDraft.value.id)
  )
})

const editableAccountOptions = computed(() => {
  const preferredAccounts = cashLeafAccounts.value.length
    ? cashLeafAccounts.value
    : leafAccounts.value

  return preferredAccounts.filter((account) => account.isActive !== false && account.accountKind === 'REAL')
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
  isInvestmentEdit.value ? investmentAccountOptions.value : draftEditForm.value.txnType === 'EXPENSE' ? rankedAccountOptions.value.filter(a => a.fundUsage === 'SPENDABLE') : rankedAccountOptions.value
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
  return leafAccounts.value
    .filter((account) => account.isActive !== false && account.accountKind === 'REAL')
    .filter((account) => account.virtualSubtype !== 'POSITION')
    .filter((account) => !productCurrency || account.currency === productCurrency)
    .sort((left, right) => left.accountName.localeCompare(right.accountName, 'zh-Hans-CN'))
})

/** 只展示启用中的产品；产品 ID 必须由主人明确选择，不使用名称自动匹配。 */
const productOptions = computed(() =>
  products.value.filter((product) => product.isActive !== false)
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
  return leafAccounts.value
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
  clearPreview()
  holdingsGate.invalidate()
  if (!['TRANSFER', 'SELL', 'REDEMPTION'].includes(type)) { draftEditForm.value.targetAccountId = undefined; draftEditForm.value.targetAccountNameHint = '' }
  if (!['SELL', 'REDEMPTION'].includes(type)) { draftEditForm.value.shares = ''; draftEditForm.value.sourceAccountId = undefined; draftEditForm.value.sourceAccountNameHint = ''; productHoldings.value = [] }
  if (!['BUY', 'SUBSCRIPTION', 'SELL', 'REDEMPTION'].includes(type)) { draftEditForm.value.productId = undefined; draftEditForm.value.productNameHint = ''; draftEditForm.value.expectedNavDate = ''; draftEditForm.value.expectedConfirmDate = '' }
  void retryEditOptions()
}

async function retryEditOptions() {
  const current = optionsGate.begin()
  optionsLoading.value = true; editOptionsReady.value = false; editError.value = ''; productHoldings.value = []
  try {
    const [accountRows, productRows] = await Promise.all([accountApi.getAccounts(), isProductEdit.value ? productApi.getProducts() : Promise.resolve([])])
    if (!current() || !editingDraft.value) return
    accounts.value = accountRows; products.value = productRows
    if (isSellRedeemEdit.value) await ensureHoldingsLoaded()
    if (current() && editingDraft.value && !editError.value) editOptionsReady.value = true
  } catch (error: any) { if (current()) editError.value = getErrorMessage(error, '无法读取可用账户或产品，请重试。') }
  finally { if (current()) optionsLoading.value = false }
}

async function ensureHoldingsLoaded() {
  const current = holdingsGate.begin(), productId = Number(draftEditForm.value.productId)
  productHoldings.value = []; holdingsLoading.value = false
  if (!Number.isInteger(productId) || productId <= 0) return
  try {
    holdingsLoading.value = true
    const rows = await holdingApi.getProductHoldingsByAccount(productId)
    if (current() && draftEditForm.value.productId === productId && isSellRedeemEdit.value && editingDraft.value) productHoldings.value = rows
  } catch (error: any) { if (current()) editError.value = getErrorMessage(error, '无法读取该产品的持仓来源，请重试。') }
  finally { if (current()) holdingsLoading.value = false }
}

async function handleProductChange() {
  draftEditForm.value.sourceAccountId = undefined; productHoldings.value = []; clearPreview()
  if (isSellRedeemEdit.value) {
    editOptionsReady.value = false; editError.value = ''
    await ensureHoldingsLoaded()
    if (editingDraft.value && !editError.value) editOptionsReady.value = true
  }
}

function formatPlainAmount(amount?: number | null): string {
  if (amount === null || amount === undefined || !Number.isFinite(amount)) return '-'
  return Number(amount).toFixed(2)
}

async function loadDrafts() {
  const current = listGate.begin(), currentSelectedId = selectedDraft.value?.id
  clearPreview(); detailGate.invalidate(); historyGate.invalidate()
  loadingDrafts.value = true; listError.value = ''; drafts.value = []
  try {
    const rows = await draftApi.listDrafts(draftQueryParams.value)
    if (!current()) return
    drafts.value = rows
    if (!currentSelectedId && trySelectRouteDraft(rows)) return
    if (currentSelectedId && selectedDraft.value?.id === currentSelectedId) {
      const latest = rows.find(d => d.id === currentSelectedId)
      if (latest) { selectedDraft.value = latest; if (latest.status !== 'DRAFT') editingDraft.value = false }
      else if (selectedDraft.value?.status === 'DRAFT') { selectedDraft.value = null; detailVisible.value = false; editingDraft.value = false }
    }
  } catch (error: any) {
    if (!current()) return
    selectedDraft.value = null; detailVisible.value = false; editingDraft.value = false
    listError.value = getErrorMessage(error, '草稿读取失败，请检查网络或登录权限。这不表示没有草稿。')
  } finally { if (current()) { loadingDrafts.value = false; detailLoading.value = false; historyLoading.value = false } }
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

async function handleParseText() {
  const text = textInput.value.trim()
  if (!text || parsing.value || creatingDraft.value) return
  const current = textGate.begin()
  parsedIntent.value = null; parsing.value = true; textError.value = ''
  try {
    const intent = await aiAccountingApi.parseText({ text, sourceRef: `pc-text-entry-${Date.now()}` })
    if (current() && textInput.value.trim() === text) parsedIntent.value = intent
  } catch (error: any) { if (current()) textError.value = getErrorMessage(error, '文本解析失败，请手动重试。') }
  finally { if (current()) parsing.value = false }
}

async function handleCreateDraft() {
  if (!parsedIntent.value || parsing.value || creatingDraft.value) return
  const current = textGate.begin(), intent = parsedIntent.value
  creatingDraft.value = true; textError.value = ''
  try {
    const result = await aiAccountingApi.draftFromIntent({ intent })
    if (!current()) return
    // Duplicate source may return an existing final-state draft; never relabel it DRAFT.
    statusFilter.value = result.draft.status; page.value = 1
    await loadDrafts()
    if (!current()) return
    textEntryVisible.value = false; parsedIntent.value = null; textInput.value = ''
    await selectDraft(result.draft)
    ElNotification.success({ title: '草稿返回', message: `草稿 #${result.draft.id}：${formatStatus(result.draft.status)}。请核对来源；未执行确认。` })
  } catch (error: any) { if (current()) textError.value = getErrorMessage(error, '创建草稿失败，请检查来源或网络。') }
  finally { if (current()) creatingDraft.value = false }
}

function resetTextEntry() { textGate.invalidate(); textInput.value = ''; parsedIntent.value = null; textError.value = ''; parsing.value = false }
watch(textInput, () => { if (!parsing.value && !creatingDraft.value) parsedIntent.value = null }, { flush: 'sync' })

async function selectDraft(draft: DraftLedgerEntry) {
  const current = detailGate.begin()
  clearPreview(); historyGate.invalidate(); optionsGate.invalidate(); holdingsGate.invalidate()
  selectedDraft.value = draft; detailVisible.value = true; detailLoading.value = true; detailError.value = ''; actionError.value = ''
  historyVisible.value = false; history.value = []; historyLoading.value = false; editingDraft.value = false
  try {
    const latest = await draftApi.getDraft(draft.id)
    if (current() && detailVisible.value) selectedDraft.value = latest
  } catch (error: any) {
    if (current()) { selectedDraft.value = null; detailError.value = getErrorMessage(error, '无法读取最新草稿，请关闭后重新打开。') }
  } finally { if (current()) detailLoading.value = false }
}

function eventLabel(type: string): string {
  return ({ create: '创建', edit: '编辑', preview: '预览', ignored: '忽略', reopen: '恢复',
    confirm_attempt: '尝试确认', confirmed: '确认成功', confirm_failed: '确认失败', copy: '复制' } as Record<string, string>)[type] || type
}

function historyStatus(status?: string | null): string {
  return ({ DRAFT: '待确认', IGNORED: '已忽略', CONFIRMED: '已确认' } as Record<string, string>)[status || ''] || '无'
}

async function loadHistory(draftId: number) {
  const current = historyGate.begin()
  historyVisible.value = true; historyLoading.value = true; historyError.value = ''; history.value = []
  try { const rows = await draftApi.history(draftId); if (current() && selectedDraft.value?.id === draftId) history.value = rows }
  catch (error: any) { if (current()) historyError.value = getErrorMessage(error, '无法读取草稿历史') }
  finally { if (current()) historyLoading.value = false }
}

async function handleReopen(draft: DraftLedgerEntry) { await changeLifecycle(draft, 'reopen') }
async function handleCopy(draft: DraftLedgerEntry) { await changeLifecycle(draft, 'copy') }
async function handleIgnore(draft: DraftLedgerEntry) { await changeLifecycle(draft, 'ignore') }

async function changeLifecycle(draft: DraftLedgerEntry, action: 'reopen' | 'copy' | 'ignore') {
  if (operationBusy.value || (action === 'ignore' ? draft.status !== 'DRAFT' : action === 'reopen' ? draft.status !== 'IGNORED' : draft.status !== 'CONFIRMED')) return
  const owner = identity(), snapshot = selectedDraft.value
  lifecycleBusy.value = true; clearPreview(); actionError.value = ''
  try {
    let reason = ''
    if (action === 'ignore') {
      const result = await ElMessageBox.prompt('请输入忽略原因（可选）', '忽略草稿', { confirmButtonText: '忽略', cancelButtonText: '取消', inputPlaceholder: '例如：重复录入、信息不足' })
      reason = typeof result === 'object' && result !== null && 'value' in result ? String(result.value || '') : ''
    } else await ElMessageBox.confirm(action === 'reopen' ? '恢复后仍需重新预览和二次确认。是否恢复？' : '生成新的草稿和来源标识，仍需预览和二次确认。', action === 'reopen' ? '恢复草稿' : '复制为新草稿', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' })
    if (!validSelection(draft.id, owner) || selectedDraft.value !== snapshot) return
    const result = action === 'reopen' ? await draftApi.reopen(draft.id) : action === 'copy' ? await draftApi.copyConfirmed(draft.id) : await draftApi.ignoreDraft(draft.id, { ignoreReason: reason || undefined })
    if (!validSelection(draft.id, owner)) return
    selectedDraft.value = result; statusFilter.value = result.status; page.value = 1; editingDraft.value = false
    await loadDrafts()
    if (identity() === owner && detailGate.active) await selectDraft(result)
  } catch (error: any) { if (error !== 'cancel' && error !== 'close' && identity() === owner) actionError.value = getErrorMessage(error, '草稿操作失败，请刷新后核对状态。') }
  finally { lifecycleBusy.value = false }
}

async function openEditDraft(draft: DraftLedgerEntry) {
  if (operationBusy.value || draft.status !== 'DRAFT') return
  await selectDraft(draft)
  if (!selectedDraft.value || selectedDraft.value.id !== draft.id || selectedDraft.value.status !== 'DRAFT' || detailLoading.value) return
  draftEditForm.value = buildEditForm(selectedDraft.value); editingDraft.value = true; clearPreview()
  await retryEditOptions()
}
function cancelEditDraft() { if (savingDraft.value) return; editingDraft.value = false; optionsGate.invalidate(); holdingsGate.invalidate(); editOptionsReady.value = false }

async function handleSaveDraftEdit(previewAfterSave = false) {
  if (!selectedDraft.value || selectedDraft.value.status !== 'DRAFT' || operationBusy.value) return
  const message = validateDraftEditForm()
  if (message) { editError.value = message; ElNotification.warning({ title: '请先补齐字段', message }); return }
  const owner = identity(), draft = selectedDraft.value
  const payload = buildUpdatedPayload(draft, Number(draftEditForm.value.amount))
  savingDraft.value = true; editError.value = ''; clearPreview()
  try {
    const updated = await draftApi.updateDraft(draft.id, { sourceType: draft.sourceType, sourceRef: draft.sourceRef || undefined, rawInput: draft.rawInput || undefined, parsedPayloadJson: JSON.stringify(payload), confidence: draft.confidence ?? undefined, missingFieldsJson: JSON.stringify(calculateMissingFields(payload)) })
    if (!validSelection(draft.id, owner)) return
    selectedDraft.value = updated; editingDraft.value = false
    await loadDrafts()
    if (validSelection(draft.id, owner) && previewAfterSave) await requestPreview(updated)
  } catch (error: any) { if (identity() === owner && detailGate.active) editError.value = getErrorMessage(error, '草稿保存失败，请核对来源或字段。') }
  finally { savingDraft.value = false }
}

async function requestPreview(draft: DraftLedgerEntry): Promise<DraftPreview | null> {
  const current = previewGate.begin(), owner = identity()
  preview.value = null; actionError.value = ''; previewing.value = true
  try {
    const fresh = await draftApi.previewDraft(draft.id)
    if (!current() || !validSelection(draft.id, owner) || selectedDraft.value?.status !== 'DRAFT') return null
    if (fresh.draftId !== draft.id) throw new Error('服务器预览与当前草稿不一致，请重新预览。')
    preview.value = fresh
    return preview.value
  } catch (error: any) {
    if (current()) { preview.value = null; actionError.value = getErrorMessage(error, '预览失败，旧预览已清除，请检查网络或字段。') }
    return null
  } finally { if (current()) previewing.value = false }
}

async function handlePreview(draft: DraftLedgerEntry) {
  if (operationBusy.value || draft.status !== 'DRAFT') return
  if (selectedDraft.value?.id !== draft.id || !detailVisible.value) await selectDraft(draft)
  if (selectedDraft.value?.id !== draft.id || selectedDraft.value.status !== 'DRAFT' || detailLoading.value) return
  editingDraft.value = false
  await requestPreview(selectedDraft.value)
}

async function handleConfirm(draft: DraftLedgerEntry) {
  if (!canConfirmSelectedDraft.value || draft.id !== selectedDraft.value?.id) return
  // Lock before preview/dialog awaits. The existing confirm API accepts only the draft ID;
  // backend confirm recalculates fresh preview and owns the idempotency/permission checks.
  confirming.value = true
  const owner = identity()
  let submitted = false
  try {
    const fresh = await requestPreview(draft)
    if (!fresh?.confirmSupported) {
      if (fresh) actionError.value = [fresh.message || '当前字段或状态不支持确认，请补齐后重新预览。', ...(fresh.warnings || [])].join(' ')
      return
    }
    await ElMessageBox.confirm(confirmDialogMessage.value, confirmDialogTitle.value, { confirmButtonText: isProductPreview.value ? '确认内部订单' : '确认记账', cancelButtonText: '取消', type: 'warning' })
    if (!validSelection(draft.id, owner) || selectedDraft.value?.status !== 'DRAFT' || preview.value !== fresh || editingDraft.value) return
    submitted = true
    const confirmed = await draftApi.confirmDraft(draft.id)
    if (confirmed.id !== draft.id || confirmed.status !== 'CONFIRMED') throw new Error('服务器未返回已确认结果，请刷新核对，禁止重复提交。')
    if (!validSelection(draft.id, owner)) return
    selectedDraft.value = confirmed; clearPreview(); editingDraft.value = false
    await loadDrafts()
    if (identity() !== owner || !detailGate.active) return
    window.dispatchEvent(new CustomEvent('data-refresh'))
    ElNotification.success({ title: '确认完成', message: confirmed.confirmOrderId ? `已关联内部订单 ${confirmed.confirmOrderId}，仍需后续人工结算。` : `已关联流水 ${confirmed.confirmTxnId || ''}，请核对记录。` })
  } catch (error: any) {
    if (submitted && (error?.response?.status == null || error.response.status >= 500 || error.response.status === 408)) uncertainConfirmIds.value = new Set([...uncertainConfirmIds.value, draft.id])
    if (error !== 'cancel' && error !== 'close' && identity() === owner && detailGate.active) actionError.value = getErrorMessage(error, '确认失败，请刷新草稿核对状态。')
  } finally { clearPreview(); confirming.value = false }
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
  if (!editOptionsReady.value || optionsLoading.value || holdingsLoading.value) return '选项尚未读取完成，请重试选项。'
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

    if (!selectedProduct.value) return '所选产品不可用，请重新选择。'
    if (!sellRedeemSourceOptions.value.some(h => h.accountId === sourceAccountId)) return '所选账户不是该产品的真实持仓来源。'
    if (!sellRedeemTargetAccountOptions.value.some(a => a.id === targetAccountId)) return '到账账户必须是同币种的启用 REAL 叶子账户。'
    return null
  }

  const amount = Number(draftEditForm.value.amount)
  if (!Number.isFinite(amount) || amount <= 0) return '金额必须是大于 0 的数字。'

  const accountId = Number(draftEditForm.value.accountId)
  if (!Number.isInteger(accountId) || accountId <= 0) return '请选择有效的现金或活钱账户。'

  if (!accountOptionsForEdit.value.some(a => a.id === accountId)) return '所选资金账户不可用或不满足用途要求。'

  if (draftEditForm.value.txnType === 'TRANSFER') {
    const targetAccountId = Number(draftEditForm.value.targetAccountId)
    if (!Number.isInteger(targetAccountId) || targetAccountId <= 0) {
      return '转账必须选择转入账户。'
    }
    if (!transferTargetAccountOptions.value.some(a => a.id === targetAccountId)) return '所选转入账户不可用。'
    if (accounts.value.find(a => a.id === accountId)?.currency !== accounts.value.find(a => a.id === targetAccountId)?.currency) return '转出与转入账户币种必须一致。'
    if (targetAccountId === accountId) {
      return '转出账户与转入账户不能相同，请重新选择转入账户。'
    }
  }

  if (isInvestmentEdit.value) {
    const productId = Number(draftEditForm.value.productId)
    if (!Number.isInteger(productId) || productId <= 0 || !selectedProduct.value) return '投资买入 / 申购必须选择真实产品。'
    if (accounts.value.find(a => a.id === accountId)?.currency !== selectedProduct.value.currency) return '产品与付款账户币种必须一致。'
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
  const account = formatPreviewAccount(asNumber(sellRedeem ? payload?.sourceAccountId : payload?.accountId))
  const targetAccountId = asNumber(payload?.targetAccountId)
  const accountLabel =
    targetAccountId === null || (!sellRedeem && rawTxnType !== 'TRANSFER') ? account : `${account} → ${formatPreviewAccount(targetAccountId)}`
  const note = asString(payload?.note) || draft.rawInput || '-'
  const productId = asNumber(payload?.productId)
  const productLabel = productId === null || !['BUY', 'SUBSCRIPTION', 'SELL', 'REDEMPTION'].includes(rawTxnType || '') ? '' : ` / 产品 #${productId}`
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
  if (typeof value === 'number') return Number.isFinite(value) ? value : null
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
  if (amount === null || amount === undefined || !Number.isFinite(amount)) {
    return '-'
  }
  return `￥ ${Number(amount).toFixed(2)}`
}

function formatConfidence(confidence?: number | null): string {
  if (confidence === null || confidence === undefined || !Number.isFinite(confidence)) {
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
  const account = accounts.value.find(a => a.id === accountId)
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
  if (amount === null || amount === undefined || !Number.isFinite(amount)) {
    return '预计变动：-'
  }
  const numericAmount = Number(amount)
  const prefix = numericAmount > 0 ? '+' : ''
  return `预计变动：${prefix}¥${numericAmount.toFixed(2)}`
}

function formatBooleanImpact(value?: boolean | null): string {
  return value == null ? '未知' : value ? '会生成' : '不会生成'
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
  if (shares === null || shares === undefined || !Number.isFinite(shares)) return '-'
  return `${formatSharesPlain(shares)} 份`
}

function formatSharesPlain(shares?: number | null): string {
  if (shares === null || shares === undefined || !Number.isFinite(shares)) return '-'
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

function clearPage() {
  gates.forEach(g => g.invalidate()); drafts.value = []; selectedDraft.value = null; preview.value = null
  history.value = []; productHoldings.value = []; accounts.value = []; products.value = []; parsedIntent.value = null; textInput.value = ''
  detailVisible.value = false; editingDraft.value = false; textEntryVisible.value = false
  editOptionsReady.value = false; optionsLoading.value = false; loadingDrafts.value = false; previewing.value = false; parsing.value = false; historyLoading.value = false; detailLoading.value = false; holdingsLoading.value = false
  listError.value = ''; detailError.value = ''; actionError.value = ''; textError.value = ''; editError.value = ''
}
watch(identity, (current, previous) => {
  const before = JSON.parse(previous), after = JSON.parse(current)
  clearPage()
  // Initial user hydration can arrive after this view mounts. Retry this read with
  // the now-known identity; subsequent account/family switches require manual refresh.
  if (!before[1] && after[1] && before[0] === after[0] && listGate.active) void loadDrafts()
  else listError.value = '登录身份或家庭已变化，请刷新读取当前可见草稿。'
}, { flush: 'sync' })
function refreshDraftInbox() { if (!operationBusy.value) void loadDrafts() }
onMounted(() => { void loadDrafts(); window.addEventListener('data-refresh', refreshDraftInbox) })
onActivated(() => { gates.forEach(g => g.activate()) })
onDeactivated(() => { clearPage(); gates.forEach(g => g.suspend()) })
onBeforeUnmount(() => { clearPage(); gates.forEach(g => g.suspend()); window.removeEventListener('data-refresh', refreshDraftInbox) })

</script>

<style scoped>
.draft-hero { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 12px 0 24px; }
.draft-hero h1 { font: 600 clamp(24px, 2.4vw, 34px)/1.35 'Songti SC', SimSun, serif; margin: 10px 0; letter-spacing: .02em; }
.draft-hero p, .draft-review-heading p { color: var(--muted); line-height: 1.7; margin: 0; }
.draft-eyebrow { font-size: 11px; letter-spacing: .15em; color: var(--primary); font-weight: 700; }
.draft-safety { display: flex; gap: 20px; padding: 16px 20px; margin-bottom: 20px; border: 1px solid var(--line); border-left: 4px solid var(--primary); background: var(--primarySoft); border-radius: 0 14px 14px 0; font-size: 13px; line-height: 1.8; }
.draft-safety strong { white-space: nowrap; }
.draft-toolbar, .draft-pagination { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
.draft-status-tabs { display: flex; flex-wrap: wrap; gap: 6px; }
.draft-status-tabs button { padding: 10px 16px; border: 1px solid var(--line); border-radius: 24px; background: var(--card); color: var(--muted); cursor: pointer; font: inherit; font-size: 13px; }
.draft-status-tabs button.active { background: var(--text); color: var(--card); border-color: var(--text); }
.draft-filters { display: grid; grid-template-columns: 2fr 1fr 1fr 1fr; gap: 14px; margin-top: 22px; }
.draft-filters label { min-width: 0; }
.draft-filters label > span { display: block; color: var(--muted); font-size: 12px; margin-bottom: 6px; }
.draft-filters .el-select { width: 100%; }
.draft-scope { color: var(--muted); font-size: 12px; line-height: 1.8; }
.draft-cards { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin: 22px 0; }
.draft-item { display: flex; flex-direction: column; gap: 12px; border: 1px solid var(--line); border-radius: 18px; padding: 20px; background: var(--card); transition: border-color .2s; min-width: 0; }
.draft-item.active, .draft-item:focus-within { border-color: var(--primary); }
.draft-item-top, .draft-meta { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.draft-number { font: 12px Georgia, serif; color: var(--muted); }
.draft-item h3 { margin: 0; font: 600 21px/1.5 'Songti SC', SimSun, serif; }
.draft-item p { margin: 0; line-height: 1.8; }
.draft-item .draft-raw { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.draft-summary, .draft-ref { font-size: 12px; color: var(--muted); overflow-wrap: anywhere; }
.draft-meta { margin-top: auto; padding-top: 12px; border-top: 1px dashed var(--line); font-size: 11px; color: var(--muted); flex-wrap: wrap; }
.draft-item-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.draft-item-actions .btn { min-height: 40px; padding: 8px 12px; font-size: 12px; }
.draft-state { padding: 40px 20px; margin: 22px 0; border: 1px dashed var(--border); border-radius: 18px; color: var(--muted); text-align: center; line-height: 1.8; }
.draft-error { border: 1px solid var(--wh-danger, #b52c33); color: var(--wh-danger, #b52c33); padding: 12px; margin: 12px 0; border-radius: 12px; line-height: 1.8; overflow-wrap: anywhere; }
.draft-pagination { border-top: 1px solid var(--line); padding-top: 18px; color: var(--muted); font-size: 12px; }
.draft-review-heading { margin-bottom: 20px; }
.draft-review-heading h2 { font: 600 28px/1.5 'Songti SC', SimSun, serif; margin: 8px 0; }
.draft-result { margin-top: 20px; color: var(--muted); font-size: 12px; line-height: 1.8; }
.draft-inbox-page :deep(.el-select) { width: 100%; }
.draft-inbox-page :deep(.el-dialog), .draft-inbox-page :deep(.el-drawer) { color: var(--text); background: var(--card); }
.draft-inbox-page .intent-panel, .draft-inbox-page .preview-panel, .draft-inbox-page .edit-panel { background: var(--primarySoft); border-color: var(--line); }
.draft-inbox-page .intent-grid > div, .draft-inbox-page .detail-row, .draft-inbox-page .impact-card, .draft-inbox-page .warning-list { background: var(--card); border-color: var(--line); }
.draft-inbox-page .edit-hint { background: var(--card); }
.draft-inbox-page .detail-row { align-items: flex-start; overflow-wrap: anywhere; }
@media (max-width: 1250px) { .draft-cards { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 850px) { .draft-cards { grid-template-columns: 1fr; } .draft-filters { grid-template-columns: 1fr 1fr; } .draft-hero { align-items: flex-start; flex-direction: column; gap: 16px; } .draft-safety { flex-direction: column; gap: 4px; } }
@media (prefers-reduced-motion: reduce) { .draft-item { transition: none; } }
.draft-inbox-page {
  padding-bottom: 24px;
}

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
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--primarySoft);
}

.edit-hint,
.edit-disabled-tip {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--card);
  color: var(--muted);
  font-size: 12px;
  line-height: 1.65;
}

.edit-disabled-tip {
  margin-top: 12px;
  color: var(--wh-warning, #86570e);
  background: var(--primarySoft);
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
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--card);
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
  color: var(--primary);
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

.draft-raw {
  color: var(--text);
  word-break: break-word;
}

@media (max-width: 720px) {
  .intent-grid,
  .edit-form-grid,
  .preview-impact-grid {
    grid-template-columns: 1fr;
  }
}
</style>
