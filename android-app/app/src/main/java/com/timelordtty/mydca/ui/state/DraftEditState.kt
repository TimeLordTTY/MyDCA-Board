package com.timelordtty.mydca.ui.state

import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.timelordtty.mydca.data.dto.DraftLedgerEntryDto
import com.timelordtty.mydca.data.dto.DraftPreviewDto
import com.timelordtty.mydca.data.dto.UpdateDraftRequestDto
import java.math.BigDecimal
import java.math.RoundingMode
import okio.Buffer

/**
 * Android 草稿编辑表单状态。
 *
 * accountId 是后端真实账户 ID；accountNameHint 只作为人工提示，不参与账户匹配。
 */
data class DraftEditForm(
    val txnType: String = "EXPENSE",
    val amount: String = "",
    val note: String = "",
    val accountId: String = "",
    val accountNameHint: String = "",
)

/**
 * 表单校验与请求体构造结果。
 */
data class DraftEditRequestResult(
    val request: UpdateDraftRequestDto? = null,
    val error: String? = null,
) {
    val isValid: Boolean
        get() = request != null && error == null
}

/**
 * 集中维护草稿编辑、保存后预览和确认按钮的安全规则，便于 UI 与单元测试复用。
 */
object DraftEditState {
    private val moshi = Moshi.Builder().build()
    private val mapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        Any::class.java,
    )
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)

    fun formFromDraft(draft: DraftLedgerEntryDto): DraftEditForm {
        val payload = parsePayload(draft.parsedPayloadJson)
        val txnType = firstString(payload, "txnType", "transactionType", "type") ?: "EXPENSE"
        val amount = firstScalar(payload, "amount").orEmpty()
        val accountId = firstScalar(payload, "accountId", "cashAccountId").orEmpty()
        val note = firstString(payload, "note", "remark", "description") ?: draft.rawInput.orEmpty()
        val accountNameHint = firstString(payload, "accountNameHint").orEmpty()
        return DraftEditForm(
            txnType = txnType.uppercase().takeIf { it == "EXPENSE" || it == "INCOME" } ?: "EXPENSE",
            amount = amount,
            note = note,
            accountId = accountId,
            accountNameHint = accountNameHint,
        )
    }

    fun buildUpdateRequest(draft: DraftLedgerEntryDto, form: DraftEditForm): DraftEditRequestResult {
        if (!draft.isDraft()) {
            return DraftEditRequestResult(error = "只有 DRAFT 状态草稿可以编辑。")
        }

        val normalizedType = form.txnType.trim().uppercase()
        if (normalizedType !in setOf("EXPENSE", "INCOME")) {
            return DraftEditRequestResult(error = "交易类型必须是 EXPENSE 或 INCOME。")
        }

        val amount = parseAmount(form.amount)
            ?: return DraftEditRequestResult(error = "金额必须是大于 0 且最多两位小数的数字。")

        val accountId = parseAccountId(form.accountId)
            ?: return DraftEditRequestResult(error = "accountId 必须是后端真实账户 ID，且为大于 0 的正整数。")

        val normalizedNote = form.note.trim()
        val normalizedAccountNameHint = form.accountNameHint.trim()
        val request = UpdateDraftRequestDto(
            sourceType = draft.sourceType,
            sourceRef = draft.sourceRef,
            rawInput = normalizedNote.ifBlank { draft.rawInput.orEmpty() },
            parsedPayloadJson = buildParsedPayloadJson(
                txnType = normalizedType,
                amount = amount,
                note = normalizedNote,
                accountId = accountId,
                accountNameHint = normalizedAccountNameHint,
            ),
            confidence = draft.confidence,
            missingFieldsJson = buildStringArrayJson(emptyList()),
        )
        return DraftEditRequestResult(request = request)
    }

    fun canConfirm(
        draft: DraftLedgerEntryDto?,
        preview: DraftPreviewDto?,
        editDirty: Boolean,
        saving: Boolean,
        previewing: Boolean,
        confirming: Boolean,
    ): Boolean {
        return draft?.isDraft() == true &&
            preview?.draftId == draft.id &&
            preview.confirmSupported &&
            !editDirty &&
            !saving &&
            !previewing &&
            !confirming
    }

    fun parsePayloadJson(json: String?): Map<String, Any?> = parsePayload(json)

    private fun DraftLedgerEntryDto.isDraft(): Boolean = status?.equals("DRAFT", ignoreCase = true) == true

    private fun parseAmount(value: String): BigDecimal? {
        val trimmed = value.trim()
        if (!Regex("""^\d+(\.\d{1,2})?$""").matches(trimmed)) {
            return null
        }
        val amount = trimmed.toBigDecimalOrNull() ?: return null
        if (amount <= BigDecimal.ZERO || amount.scale() > 2) {
            return null
        }
        return amount.setScale(amount.scale().coerceAtLeast(0), RoundingMode.UNNECESSARY)
    }

    private fun parseAccountId(value: String): Long? {
        val trimmed = value.trim()
        if (!Regex("""^\d+$""").matches(trimmed)) {
            return null
        }
        return trimmed.toLongOrNull()?.takeIf { it > 0L }
    }

    private fun buildParsedPayloadJson(
        txnType: String,
        amount: BigDecimal,
        note: String,
        accountId: Long,
        accountNameHint: String,
    ): String {
        return writeJsonObject {
            name("txnType").value(txnType)
            name("amount").value(amount.stripTrailingZeros())
            name("accountId").value(accountId)
            if (note.isNotBlank()) {
                name("note").value(note)
            }
            if (accountNameHint.isNotBlank()) {
                name("accountNameHint").value(accountNameHint)
            }
        }
    }

    private fun buildStringArrayJson(values: List<String>): String {
        val buffer = Buffer()
        JsonWriter.of(buffer).use { writer ->
            writer.beginArray()
            values.forEach { writer.value(it) }
            writer.endArray()
        }
        return buffer.readUtf8()
    }

    private fun writeJsonObject(writeFields: JsonWriter.() -> Unit): String {
        val buffer = Buffer()
        JsonWriter.of(buffer).use { writer ->
            writer.beginObject()
            writer.writeFields()
            writer.endObject()
        }
        return buffer.readUtf8()
    }

    private fun parsePayload(json: String?): Map<String, Any?> {
        if (json.isNullOrBlank()) {
            return emptyMap()
        }
        return try {
            mapAdapter.fromJson(json).orEmpty()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun firstString(payload: Map<String, Any?>, vararg keys: String): String? {
        return firstScalar(payload, *keys)?.takeIf { it.isNotBlank() }
    }

    private fun firstScalar(payload: Map<String, Any?>, vararg keys: String): String? {
        for (key in keys) {
            val value = payload[key] ?: continue
            val normalized = when (value) {
                is String -> value
                is Number -> normalizeNumber(value)
                else -> value.toString()
            }
            if (normalized.isNotBlank()) {
                return normalized
            }
        }
        return null
    }

    private fun normalizeNumber(value: Number): String {
        return BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
    }
}
