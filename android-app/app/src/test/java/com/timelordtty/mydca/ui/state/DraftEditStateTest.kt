package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.data.dto.DraftLedgerEntryDto
import com.timelordtty.mydca.data.dto.DraftPreviewDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftEditStateTest {
    @Test
    fun formFromDraftReadsBackendCompatiblePayloadFields() {
        val draft = draft(
            parsedPayloadJson = """
                {"transactionType":"income","amount":18.8,"cashAccountId":42,"description":"工资"}
            """.trimIndent(),
        )

        val form = DraftEditState.formFromDraft(draft)

        assertEquals("INCOME", form.txnType)
        assertEquals("18.8", form.amount)
        assertEquals("42", form.accountId)
        assertEquals("工资", form.note)
    }

    @Test
    fun buildUpdateRequestEscapesJsonSpecialChars() {
        val result = DraftEditState.buildUpdateRequest(
            draft = draft(),
            form = DraftEditForm(
                txnType = "EXPENSE",
                amount = "18.80",
                accountId = "7",
                accountNameHint = "现金\"账户",
                note = "咖啡\n早餐\\账单",
            ),
        )

        assertTrue(result.isValid)
        val payload = DraftEditState.parsePayloadJson(result.request?.parsedPayloadJson)
        assertEquals("EXPENSE", payload["txnType"])
        assertEquals(18.8, (payload["amount"] as Number).toDouble(), 0.0)
        assertEquals(7L, (payload["accountId"] as Number).toLong())
        assertEquals("咖啡\n早餐\\账单", payload["note"])
        assertEquals("现金\"账户", payload["accountNameHint"])
        assertEquals("[]", result.request?.missingFieldsJson)
    }

    @Test
    fun invalidAmountOrAccountBlocksSaveRequest() {
        val draft = draft()

        val invalidAmount = DraftEditState.buildUpdateRequest(
            draft,
            DraftEditForm(amount = "12.345", accountId = "8"),
        )
        val invalidAccount = DraftEditState.buildUpdateRequest(
            draft,
            DraftEditForm(amount = "12.34", accountId = "账户A"),
        )

        assertFalse(invalidAmount.isValid)
        assertTrue(invalidAmount.error.orEmpty().contains("最多两位小数"))
        assertFalse(invalidAccount.isValid)
        assertTrue(invalidAccount.error.orEmpty().contains("accountId"))
    }

    @Test
    fun nonDraftCannotBuildSaveRequest() {
        val result = DraftEditState.buildUpdateRequest(
            draft = draft(status = "CONFIRMED"),
            form = DraftEditForm(amount = "12.34", accountId = "9"),
        )

        assertFalse(result.isValid)
        assertTrue(result.error.orEmpty().contains("DRAFT"))
    }

    @Test
    fun confirmRequiresFreshPreviewAndIdleState() {
        val draft = draft(id = 10)
        val preview = DraftPreviewDto(draftId = 10, confirmSupported = true)

        assertTrue(
            DraftEditState.canConfirm(
                draft = draft,
                preview = preview,
                editDirty = false,
                saving = false,
                previewing = false,
                confirming = false,
            ),
        )
        assertFalse(DraftEditState.canConfirm(draft, preview, editDirty = true, saving = false, previewing = false, confirming = false))
        assertFalse(DraftEditState.canConfirm(draft, preview.copy(draftId = 11), false, false, false, false))
        assertFalse(DraftEditState.canConfirm(draft, preview.copy(confirmSupported = false), false, false, false, false))
        assertFalse(DraftEditState.canConfirm(draft, preview, false, saving = true, previewing = false, confirming = false))
        assertFalse(DraftEditState.canConfirm(draft, preview, false, saving = false, previewing = true, confirming = false))
    }

    @Test
    fun validUpdateRequestKeepsDtoContractFields() {
        val result = DraftEditState.buildUpdateRequest(
            draft = draft(sourceType = "android", sourceRef = "candidate-1", confidence = 0.77),
            form = DraftEditForm(amount = "88", accountId = "12", note = "", accountNameHint = ""),
        )

        val request = result.request
        assertNotNull(request)
        assertEquals("android", request?.sourceType)
        assertEquals("candidate-1", request?.sourceRef)
        assertEquals("原始输入", request?.rawInput)
        assertEquals(0.77, request?.confidence ?: 0.0, 0.0)
        assertNotNull(request?.parsedPayloadJson)
    }

    private fun draft(
        id: Long = 1L,
        status: String = "DRAFT",
        sourceType: String? = "manual",
        sourceRef: String? = null,
        rawInput: String? = "原始输入",
        parsedPayloadJson: String? = """{"txnType":"EXPENSE","amount":12.34,"accountId":3}""",
        confidence: Double? = 0.5,
    ): DraftLedgerEntryDto {
        return DraftLedgerEntryDto(
            id = id,
            status = status,
            sourceType = sourceType,
            sourceRef = sourceRef,
            rawInput = rawInput,
            parsedPayloadJson = parsedPayloadJson,
            confidence = confidence,
        )
    }
}
