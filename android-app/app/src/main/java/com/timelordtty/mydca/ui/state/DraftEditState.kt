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
 * productId 同样是后端真实 product_master.id；productNameHint 只作为人工提示，不参与产品匹配。
 *
 * 投资草稿（BUY / SUBSCRIPTION）本轮只支持单资金来源账户，且确认后会立即生成付款账本
 * （付款账户 CASH CREDIT + 待结算应收 RECEIVABLE DEBIT），但不会自动结算、不会生成最终持仓。
 */
data class DraftEditForm(
    val txnType: String = "EXPENSE",
    val amount: String = "",
    val note: String = "",
    val accountId: String = "",
    val targetAccountId: String = "",
    val accountNameHint: String = "",
    /** 投资草稿（BUY / SUBSCRIPTION）选定的真实产品 ID；由主人明确选择，不做文本自动匹配。 */
    val productId: String = "",
    /** 产品名称提示，只供复核，不可代替 productId。 */
    val productNameHint: String = "",
    val expectedNavDate: String = "",
    val expectedConfirmDate: String = "",
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
    /** 后端 confirm 当前支持的草稿类型；SELL / REDEMPTION 仍由后端拒绝。 */
    val SUPPORTED_TXN_TYPES = setOf("EXPENSE", "INCOME", "TRANSFER", "BUY", "SUBSCRIPTION")

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
        val normalizedType = txnType.uppercase()
        val amount = firstScalar(payload, "amount").orEmpty()
        val accountId = firstScalar(payload, "accountId", "cashAccountId", "sourceAccountId").orEmpty()
        val targetAccountId =
            firstScalar(payload, "targetAccountId", "toAccountId", "destinationAccountId").orEmpty()
        val note = firstString(payload, "note", "remark", "description") ?: draft.rawInput.orEmpty()
        val accountNameHint = firstString(payload, "accountNameHint").orEmpty()
        val productId = firstScalar(payload, "productId").orEmpty()
        val productNameHint = firstString(payload, "productNameHint").orEmpty()
        val expectedNavDate = firstString(payload, "expectedNavDate").orEmpty()
        val expectedConfirmDate = firstString(payload, "expectedConfirmDate").orEmpty()
        return DraftEditForm(
            txnType = normalizedType.takeIf { it in SUPPORTED_TXN_TYPES } ?: "EXPENSE",
            amount = amount,
            note = note,
            accountId = accountId,
            targetAccountId = targetAccountId,
            accountNameHint = accountNameHint,
            productId = productId,
            productNameHint = productNameHint,
            expectedNavDate = expectedNavDate,
            expectedConfirmDate = expectedConfirmDate,
        )
    }

    /** 切换交易类型：非转账清空转入账户，非投资清空投资字段，避免残留字段污染候选载荷。 */
    fun switchTxnType(form: DraftEditForm, txnType: String): DraftEditForm {
        val normalized = txnType.trim().uppercase()
        val cleared = form.copy(txnType = normalized)
        val withoutTransfer = if (normalized == "TRANSFER") cleared else cleared.copy(targetAccountId = "")
        return if (isInvestmentType(normalized)) withoutTransfer else withoutInvestmentFields(withoutTransfer)
    }

    /** 投资候选类型：本轮只支持 BUY（场内买入）与 SUBSCRIPTION（场外申购）。 */
    fun isInvestmentType(txnType: String?): Boolean =
        txnType?.trim()?.uppercase() in setOf("BUY", "SUBSCRIPTION")

    private fun withoutInvestmentFields(form: DraftEditForm): DraftEditForm = form.copy(
        productId = "",
        productNameHint = "",
        expectedNavDate = "",
        expectedConfirmDate = "",
    )

    fun buildUpdateRequest(draft: DraftLedgerEntryDto, form: DraftEditForm): DraftEditRequestResult {
        if (!draft.isDraft()) {
            return DraftEditRequestResult(error = "只有 DRAFT 状态草稿可以编辑。")
        }

        val normalizedType = form.txnType.trim().uppercase()
        if (normalizedType !in SUPPORTED_TXN_TYPES) {
            return DraftEditRequestResult(
                error = "交易类型必须是 EXPENSE、INCOME、TRANSFER、BUY 或 SUBSCRIPTION。",
            )
        }

        val amount = parseAmount(form.amount)
            ?: return DraftEditRequestResult(error = "金额必须是大于 0 且最多两位小数的数字。")

        val accountId = parseAccountId(form.accountId)
            ?: return DraftEditRequestResult(error = "accountId 必须是后端真实账户 ID，且为大于 0 的正整数。")

        val isTransfer = normalizedType == "TRANSFER"
        val targetAccountId = if (isTransfer) {
            parseAccountId(form.targetAccountId)
                ?: return DraftEditRequestResult(
                    error = "转入账户 targetAccountId 必须是后端真实账户 ID，且为大于 0 的正整数。",
                )
        } else {
            null
        }
        if (isTransfer && targetAccountId == accountId) {
            return DraftEditRequestResult(error = "转出账户与转入账户不能相同，请重新选择转入账户。")
        }

        val isInvestment = isInvestmentType(normalizedType)
        val productId = if (isInvestment) {
            parseAccountId(form.productId)
                ?: return DraftEditRequestResult(
                    error = "投资草稿必须选择真实产品，productId 必须是大于 0 的正整数。",
                )
        } else {
            null
        }

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
                targetAccountId = targetAccountId,
                productId = productId,
                productNameHint = form.productNameHint.trim(),
                expectedNavDate = form.expectedNavDate.trim(),
                expectedConfirmDate = form.expectedConfirmDate.trim(),
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
        targetAccountId: Long?,
        productId: Long?,
        productNameHint: String,
        expectedNavDate: String,
        expectedConfirmDate: String,
    ): String {
        return writeJsonObject {
            name("txnType").value(txnType)
            name("amount").value(amount.stripTrailingZeros())
            name("accountId").value(accountId)
            if (targetAccountId != null) {
                name("targetAccountId").value(targetAccountId)
            }
            if (productId != null) {
                name("productId").value(productId)
            }
            if (note.isNotBlank()) {
                name("note").value(note)
            }
            if (accountNameHint.isNotBlank()) {
                name("accountNameHint").value(accountNameHint)
            }
            if (productNameHint.isNotBlank()) {
                name("productNameHint").value(productNameHint)
            }
            if (expectedNavDate.isNotBlank()) {
                name("expectedNavDate").value(expectedNavDate)
            }
            if (expectedConfirmDate.isNotBlank()) {
                name("expectedConfirmDate").value(expectedConfirmDate)
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
