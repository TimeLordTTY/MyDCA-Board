package com.timelordtty.mydca.ui.state

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.timelordtty.mydca.data.dto.DraftLedgerEntryDto
import java.math.BigDecimal

/** 草稿解析结果的可复核展示口径，只读取后端已返回的候选字段。 */
data class DraftParsedInfo(
    val txnType: String? = null,
    val amount: String? = null,
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
        else -> txnType?.trim().orEmpty().ifBlank { "类型待补充" }
    }

    fun parsedInfo(draft: DraftLedgerEntryDto): DraftParsedInfo {
        val payload = DraftEditState.parsePayloadJson(draft.parsedPayloadJson)
        return DraftParsedInfo(
            txnType = payload.firstText("txnType", "transactionType", "type"),
            amount = payload.firstNumber("amount"),
            accountId = payload.firstNumber("accountId", "cashAccountId"),
            accountNameHint = payload.firstText("accountNameHint"),
            note = payload.firstText("note", "remark", "description"),
            confidence = draft.confidence?.let(::normalizeNumber),
            missingFields = parseStringList(draft.missingFieldsJson),
        )
    }

    /** 列表摘要：类型 · 金额 · 账户，缺什么就明确提示缺什么。 */
    fun summary(draft: DraftLedgerEntryDto): String {
        val info = parsedInfo(draft)
        val parts = mutableListOf(txnTypeLabel(info.txnType))
        parts += if (!info.amount.isNullOrBlank()) "金额 ${info.amount}" else "金额待补充"
        parts += when {
            !info.accountId.isNullOrBlank() -> "账户 ${info.accountId}"
            !info.accountNameHint.isNullOrBlank() -> "账户提示 ${info.accountNameHint}"
            else -> "账户待选择"
        }
        return parts.joinToString(" · ")
    }

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
