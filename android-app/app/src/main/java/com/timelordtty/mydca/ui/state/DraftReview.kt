package com.timelordtty.mydca.ui.state

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.timelordtty.mydca.data.dto.DraftLedgerEntryDto
import com.timelordtty.mydca.data.dto.DraftPreviewDto
import java.math.BigDecimal

/** 草稿解析结果的可复核展示口径，只读取后端已返回的候选字段。 */
data class DraftParsedInfo(
    val txnType: String? = null,
    val amount: String? = null,
    val shares: String? = null,
    val sourceAccountId: String? = null,
    val sourceAccountNameHint: String? = null,
    val accountId: String? = null,
    val accountNameHint: String? = null,
    val note: String? = null,
    val confidence: String? = null,
    val missingFields: List<String> = emptyList(),
) {
    /** 是否至少解析出一个可复核字段。 */
    val hasParsedField: Boolean
        get() = !txnType.isNullOrBlank() ||
            !amount.isNullOrBlank() ||
            !shares.isNullOrBlank() ||
            !sourceAccountId.isNullOrBlank() ||
            !accountId.isNullOrBlank() ||
            !accountNameHint.isNullOrBlank() ||
            !note.isNullOrBlank()

    val missingFieldText: String
        get() = if (missingFields.isEmpty()) "无" else missingFields.joinToString("、")
}

/**
 * 草稿状态与解析信息的统一展示口径。
 *
 * 该对象是纯展示层：不发送任何请求，因此不会 preview、confirm 或生成正式流水。
 */
object DraftReview {
    private val stringListAdapter = Moshi.Builder()
        .build()
        .adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))

    fun isDraft(draft: DraftLedgerEntryDto?): Boolean =
        draft?.status?.equals("DRAFT", ignoreCase = true) == true

    fun statusLabel(status: String?): String = when (status?.trim()?.uppercase()) {
        "DRAFT" -> "DRAFT（未入账，等待人工确认）"
        "CONFIRMED" -> "CONFIRMED（已确认入账）"
        "IGNORED" -> "IGNORED（已忽略）"
        null, "" -> "未知状态"
        else -> status.trim().uppercase()
    }

    fun statusShortLabel(status: String?): String = when (status?.trim()?.uppercase()) {
        "DRAFT" -> "待确认"
        "CONFIRMED" -> "已确认"
        "IGNORED" -> "已忽略"
        else -> "未知"
    }

    fun txnTypeLabel(txnType: String?): String = when (txnType?.trim()?.uppercase()) {
        "EXPENSE" -> "支出 EXPENSE"
        "INCOME" -> "收入 INCOME"
        "TRANSFER" -> "转账 TRANSFER"
        "BUY" -> "买入 BUY"
        "SUBSCRIPTION" -> "申购 SUBSCRIPTION"
        "SELL" -> "卖出 SELL"
        "REDEMPTION" -> "赎回 REDEMPTION"
        else -> txnType?.trim().orEmpty().ifBlank { "类型待补充" }
    }

    /** TRANSFER 预览的中文双账户影响行；非转账预览返回空列表。 */
    fun transferImpactLines(preview: DraftPreviewDto?): List<String> {
        if (preview == null || !isTransfer(preview)) return emptyList()
        return listOf(
            "从：${preview.accountName ?: "未匹配"} / ${displayFundUsage(preview.fundUsage)}",
            "到：${preview.targetAccountName ?: "未匹配"} / ${displayFundUsage(preview.targetFundUsage)}",
            "金额：${formatPreviewAmount(preview.amount)}",
            "转出账户变动：${formatSignedAmount(preview.accountDelta)}",
            "转入账户变动：${formatSignedAmount(preview.targetAccountDelta)}",
        )
    }

    /**
     * 投资买入 / 申购预览的中文影响行：产品、资金来源、可用余额、付款账户与待结算应收变动。
     * 非投资预览返回空列表。
     */
    fun investmentImpactLines(preview: DraftPreviewDto?): List<String> {
        if (preview == null || !isInvestment(preview)) return emptyList()
        val lines = mutableListOf(
            "产品：${preview.productName ?: "未选择"} / ${preview.productCode ?: "无代码"} / ${preview.productAssetType ?: "未知类型"}",
            "资金来源：${preview.accountName ?: "未匹配"} / ${displayFundUsage(preview.fundUsage)}",
            "可用余额：${formatPreviewAmount(preview.availableBefore)}",
            "本次金额：${formatPreviewAmount(preview.amount)}",
            "付款账户变动：${formatSignedAmount(preview.accountDelta)}",
            "待结算应收变动：${formatSignedAmount(preview.receivableDelta)}",
        )
        preview.fundingMessage?.takeIf { it.isNotBlank() }?.let { lines += it }
        return lines
    }

    /**
     * 卖出 / 赎回预览的中文影响行：产品、持仓来源、当前可用份额、本次份额、预计剩余份额与到账账户。
     * 非卖出 / 赎回预览返回空列表。
     */
    fun sellRedeemImpactLines(preview: DraftPreviewDto?): List<String> {
        if (preview == null || !isSellRedeem(preview)) return emptyList()
        val lines = mutableListOf(
            "产品：${preview.productName ?: "未选择"} / ${preview.productCode ?: "无代码"} / ${preview.productAssetType ?: "未知类型"}",
            "持仓来源：${preview.accountName ?: "未匹配"}",
            "当前可用份额：${formatShares(preview.availableShares)}",
            "本次份额：${formatShares(preview.shares)}",
            "预计剩余份额：${formatShares(preview.remainingShares)}",
            "到账账户：${preview.targetAccountName ?: "未匹配"} / ${displayFundUsage(preview.targetFundUsage)}",
        )
        preview.sharesMessage?.takeIf { it.isNotBlank() }?.let { lines += it }
        return lines
    }

    /** 投资确认弹窗标题：明确「确认创建【产品】买入 / 申购订单 ¥X？」。 */
    fun confirmDialogTitle(preview: DraftPreviewDto?): String {
        if (preview == null) return "确认正式记账？"
        if (isInvestment(preview)) {
            val action = investmentActionLabel(preview.txnType)
            return "确认创建【${preview.productName ?: "未选择产品"}】${action}订单 " +
                "¥${formatPreviewAmount(preview.amount)}？"
        }
        if (isSellRedeem(preview)) {
            val action = sellRedeemActionLabel(preview.txnType)
            return "确认创建【${preview.productName ?: "未选择产品"}】${action}订单 " +
                "${formatShares(preview.shares)} 份？"
        }
        if (!isTransfer(preview)) return "确认正式记账？"
        return "确认将 ¥${formatPreviewAmount(preview.amount)} 从 ${preview.accountName ?: "转出账户"}" +
            " 转到 ${preview.targetAccountName ?: "转入账户"}？"
    }

    /**
     * 确认弹窗正文：
     * - 投资草稿明确「会立即扣减付款账户并增加同额待结算应收，仍需后续结算，不会自动成交」；
     * - 卖出 / 赎回草稿明确「只创建内部 PENDING 记录，不立即减少持仓，也不立即增加到账余额」；
     * - 转账草稿明确会生成正式转账流水；
     * - 没有 fresh preview 时说明已被阻止。
     */
    fun confirmDialogMessage(preview: DraftPreviewDto?, canConfirm: Boolean): String {
        if (!canConfirm) return "当前草稿没有可确认预览，移动端已阻止本次确认。"
        if (preview != null && isInvestment(preview)) {
            val account = preview.accountName ?: "付款账户"
            return "确认后将立即从【$account】扣除 ¥${formatPreviewAmount(preview.amount)}，" +
                "并增加同额待结算应收；订单仍需后续结算，不会自动成交。"
        }
        if (preview != null && isSellRedeem(preview)) {
            val source = preview.accountName ?: "持仓来源"
            val target = preview.targetAccountName ?: "到账账户"
            return "确认后只创建内部 PENDING 卖出 / 赎回记录，并占用【$source】的 " +
                "${formatShares(preview.shares)} 份；不会立即减少持仓，也不会立即增加【$target】的到账余额，" +
                "真正的资金与持仓变化只在后续人工结算时产生。"
        }
        if (preview != null && isTransfer(preview)) {
            return "本操作会调用后端 confirm 接口，生成一笔从转出账户到转入账户的正式转账流水；请再次确认金额与账户。"
        }
        return "本操作会调用后端 confirm 接口。请确认预览内容无误后再继续。"
    }

    private fun investmentActionLabel(txnType: String?): String = when (txnType?.trim()?.uppercase()) {
        "SUBSCRIPTION" -> "申购"
        else -> "买入"
    }

    private fun isInvestment(preview: DraftPreviewDto): Boolean =
        preview.txnType?.trim()?.uppercase() in setOf("BUY", "SUBSCRIPTION")

    private fun sellRedeemActionLabel(txnType: String?): String = when (txnType?.trim()?.uppercase()) {
        "REDEMPTION" -> "赎回"
        else -> "卖出"
    }

    private fun isSellRedeem(preview: DraftPreviewDto): Boolean =
        preview.txnType?.trim()?.uppercase() in setOf("SELL", "REDEMPTION")

    private fun isTransfer(preview: DraftPreviewDto): Boolean =
        preview.txnType?.trim()?.equals("TRANSFER", ignoreCase = true) == true

    private fun displayFundUsage(fundUsage: String?): String =
        fundUsage?.takeIf { it.isNotBlank() } ?: "未知"

    private fun formatPreviewAmount(amount: Double?): String {
        if (amount == null) return "未知金额"
        return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString()
    }

    private fun formatShares(shares: Double?): String {
        if (shares == null) return "未知份额"
        return BigDecimal.valueOf(shares).stripTrailingZeros().toPlainString()
    }

    private fun formatSignedAmount(delta: Double?): String {
        if (delta == null) return "未知"
        val value = BigDecimal.valueOf(delta).stripTrailingZeros().toPlainString()
        return if (delta > 0) "+$value" else value
    }

    fun parsedInfo(draft: DraftLedgerEntryDto): DraftParsedInfo {
        val payload = DraftEditState.parsePayloadJson(draft.parsedPayloadJson)
        return DraftParsedInfo(
            txnType = payload.firstText("txnType", "transactionType", "type"),
            amount = payload.firstNumber("amount"),
            shares = payload.firstNumber("shares"),
            sourceAccountId = payload.firstNumber("sourceAccountId"),
            sourceAccountNameHint = payload.firstText("sourceAccountNameHint"),
            accountId = payload.firstNumber("accountId", "cashAccountId"),
            accountNameHint = payload.firstText("accountNameHint"),
            note = payload.firstText("note", "remark", "description"),
            confidence = draft.confidence?.let(::normalizeNumber),
            missingFields = parseStringList(draft.missingFieldsJson),
        )
    }

    /** 列表摘要：类型 · 金额 / 份额 · 账户，缺什么就明确提示缺什么。 */
    fun summary(draft: DraftLedgerEntryDto): String {
        val info = parsedInfo(draft)
        val parts = mutableListOf(txnTypeLabel(info.txnType))
        parts += if (!info.shares.isNullOrBlank()) {
            "份额 ${info.shares}"
        } else if (!info.amount.isNullOrBlank()) {
            "金额 ${info.amount}"
        } else if (isSellRedeemType(info.txnType)) {
            "份额待补充"
        } else {
            "金额待补充"
        }
        parts += when {
            !info.sourceAccountId.isNullOrBlank() -> "持仓来源 ${info.sourceAccountId}"
            !info.sourceAccountNameHint.isNullOrBlank() -> "持仓来源提示 ${info.sourceAccountNameHint}"
            !info.accountId.isNullOrBlank() -> "账户 ${info.accountId}"
            !info.accountNameHint.isNullOrBlank() -> "账户提示 ${info.accountNameHint}"
            else -> "账户待选择"
        }
        return parts.joinToString(" · ")
    }

    private fun isSellRedeemType(txnType: String?): Boolean =
        txnType?.trim()?.uppercase() in setOf("SELL", "REDEMPTION")

    fun listLabel(draft: DraftLedgerEntryDto): String =
        "#${draft.id} · ${statusShortLabel(draft.status)} · ${summary(draft)}"

    /** 待处理草稿数量，只统计仍为 DRAFT 的条目。 */
    fun pendingDraftCount(drafts: List<DraftLedgerEntryDto>): Int = drafts.count { isDraft(it) }

    fun parseStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        val parsed = runCatching { stringListAdapter.fromJson(json) }.getOrNull() ?: return emptyList()
        return parsed.filter { it.isNotBlank() }
    }

    private fun Map<String, Any?>.firstText(vararg keys: String): String? {
        for (key in keys) {
            val value = this[key] ?: continue
            val text = if (value is Number) normalizeNumber(value) else value.toString()
            if (text.isNotBlank()) return text
        }
        return null
    }

    private fun Map<String, Any?>.firstNumber(vararg keys: String): String? {
        for (key in keys) {
            val value = this[key] ?: continue
            if (value is Number) return normalizeNumber(value)
            val text = value.toString().trim()
            if (text.isNotBlank() && text.toBigDecimalOrNull() != null) return normalizeNumber(text)
        }
        return null
    }

    private fun normalizeNumber(value: Number): String = normalizeNumber(value.toString())

    private fun normalizeNumber(value: String): String =
        value.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: value
}
