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

    @Test
    fun transferDraftFormReadsBackBothAccounts() {
        val form = DraftEditState.formFromDraft(
            draft(parsedPayloadJson = """{"txnType":"TRANSFER","amount":100,"accountId":7,"targetAccountId":8}"""),
        )

        assertEquals("TRANSFER", form.txnType)
        assertEquals("7", form.accountId)
        assertEquals("8", form.targetAccountId)
        assertEquals("100", form.amount)
    }

    @Test
    fun transferFormBuildsTwoAccountPayload() {
        val result = DraftEditState.buildUpdateRequest(
            draft = draft(),
            form = DraftEditForm(
                txnType = "TRANSFER",
                amount = "100",
                accountId = "7",
                targetAccountId = "8",
                note = "资金分区",
            ),
        )

        assertTrue(result.isValid)
        val payload = DraftEditState.parsePayloadJson(result.request?.parsedPayloadJson)
        assertEquals("TRANSFER", payload["txnType"])
        assertEquals(7L, (payload["accountId"] as Number).toLong())
        assertEquals(8L, (payload["targetAccountId"] as Number).toLong())
    }

    @Test
    fun transferFormBlocksIdenticalAccounts() {
        val result = DraftEditState.buildUpdateRequest(
            draft = draft(),
            form = DraftEditForm(txnType = "TRANSFER", amount = "100", accountId = "7", targetAccountId = "7"),
        )

        assertFalse(result.isValid)
        assertTrue(result.error.orEmpty().contains("不能相同"))
    }

    @Test
    fun transferFormRequiresTargetAccountId() {
        val result = DraftEditState.buildUpdateRequest(
            draft = draft(),
            form = DraftEditForm(txnType = "TRANSFER", amount = "100", accountId = "7", targetAccountId = ""),
        )

        assertFalse(result.isValid)
        assertTrue(result.error.orEmpty().contains("targetAccountId"))
    }

    @Test
    fun switchingBackToExpenseDropsResidualTargetAccount() {
        val result = DraftEditState.buildUpdateRequest(
            draft = draft(),
            form = DraftEditForm(
                txnType = "EXPENSE",
                amount = "100",
                accountId = "7",
                targetAccountId = "8",
            ),
        )

        assertTrue(result.isValid)
        val payload = DraftEditState.parsePayloadJson(result.request?.parsedPayloadJson)
        assertFalse(payload.containsKey("targetAccountId"))
    }

    @Test
    fun stalePreviewCannotConfirmTransferDraft() {
        val transferDraft = draft(
            id = 10,
            parsedPayloadJson = """{"txnType":"TRANSFER","accountId":7,"targetAccountId":8,"amount":100}""",
        )
        val freshPreview = DraftPreviewDto(draftId = 10, txnType = "TRANSFER", confirmSupported = true)

        assertTrue(DraftEditState.canConfirm(transferDraft, freshPreview, false, false, false, false))
        assertFalse(DraftEditState.canConfirm(transferDraft, freshPreview.copy(draftId = 11), false, false, false, false))
        assertFalse(DraftEditState.canConfirm(transferDraft, freshPreview, editDirty = true, saving = false, previewing = false, confirming = false))
    }
    @Test
    fun investmentFormReadsBackRealProductFields() {
        val form = DraftEditState.formFromDraft(
            draft(
                parsedPayloadJson = """{"txnType":"BUY","amount":1000,"accountId":7,"productId":5,"productNameHint":"纳指ETF","expectedNavDate":"2026-09-29","expectedConfirmDate":"2026-09-30"}""",
            ),
        )

        assertEquals("BUY", form.txnType)
        assertEquals("1000", form.amount)
        assertEquals("7", form.accountId)
        assertEquals("5", form.productId)
        assertEquals("纳指ETF", form.productNameHint)
        assertEquals("2026-09-29", form.expectedNavDate)
        assertEquals("2026-09-30", form.expectedConfirmDate)
    }

    @Test
    fun buyAndSubscriptionAreSupportedTypes() {
        val buy = DraftEditState.buildUpdateRequest(
            draft(),
            DraftEditForm(txnType = "BUY", amount = "1000", accountId = "7", productId = "5"),
        )
        val subscription = DraftEditState.buildUpdateRequest(
            draft(),
            DraftEditForm(txnType = "SUBSCRIPTION", amount = "500", accountId = "7", productId = "6"),
        )

        assertTrue(buy.isValid)
        assertTrue(subscription.isValid)
        assertTrue(DraftEditState.isInvestmentType("buy"))
        assertTrue(DraftEditState.isInvestmentType(" subscription "))
        assertFalse(DraftEditState.isInvestmentType("EXPENSE"))
    }

    @Test
    fun investmentFormRequiresRealProductId() {
        val result = DraftEditState.buildUpdateRequest(
            draft(),
            DraftEditForm(txnType = "BUY", amount = "1000", accountId = "7", productId = ""),
        )

        assertFalse(result.isValid)
        assertTrue(result.error.orEmpty().contains("productId"))
    }

    @Test
    fun sellAndRedemptionAreSupportedTypes() {
        val sell = DraftEditState.buildUpdateRequest(
            draft(),
            DraftEditForm(
                txnType = "SELL",
                productId = "5",
                shares = "500",
                sourceAccountId = "7",
                targetAccountId = "8",
            ),
        )
        val redemption = DraftEditState.buildUpdateRequest(
            draft(),
            DraftEditForm(
                txnType = "REDEMPTION",
                productId = "6",
                shares = "1000",
                sourceAccountId = "7",
                targetAccountId = "8",
            ),
        )

        assertTrue(sell.isValid)
        assertTrue(redemption.isValid)
        assertTrue(DraftEditState.SUPPORTED_TXN_TYPES.contains("SELL"))
        assertTrue(DraftEditState.SUPPORTED_TXN_TYPES.contains("REDEMPTION"))
        assertTrue(DraftEditState.isSellRedeemType("sell"))
        assertTrue(DraftEditState.isSellRedeemType(" redemption "))
        assertFalse(DraftEditState.isSellRedeemType("BUY"))
    }

    @Test
    fun sellFormRequiresSharesSourceAndTarget() {
        val missingShares = DraftEditState.buildUpdateRequest(
            draft(),
            DraftEditForm(txnType = "SELL", productId = "5", sourceAccountId = "7", targetAccountId = "8"),
        )
        val missingSource = DraftEditState.buildUpdateRequest(
            draft(),
            DraftEditForm(txnType = "SELL", productId = "5", shares = "500", targetAccountId = "8"),
        )
        val missingTarget = DraftEditState.buildUpdateRequest(
            draft(),
            DraftEditForm(txnType = "SELL", productId = "5", shares = "500", sourceAccountId = "7"),
        )

        assertFalse(missingShares.isValid)
        assertTrue(missingShares.error.orEmpty().contains("份额"))
        assertFalse(missingSource.isValid)
        assertTrue(missingSource.error.orEmpty().contains("sourceAccountId"))
        assertFalse(missingTarget.isValid)
        assertTrue(missingTarget.error.orEmpty().contains("targetAccountId"))
    }

    @Test
    fun sellPayloadCarriesSharesSourceAndDropsAmount() {
        val result = DraftEditState.buildUpdateRequest(
            draft(),
            DraftEditForm(
                txnType = "SELL",
                productId = "5",
                productNameHint = "半导体ETF",
                shares = "500",
                sourceAccountId = "7",
                sourceAccountNameHint = "券商账户",
                targetAccountId = "8",
                accountId = "999",
            ),
        )

        assertTrue(result.isValid)
        val payload = DraftEditState.parsePayloadJson(result.request?.parsedPayloadJson)
        assertEquals("SELL", payload["txnType"])
        assertEquals(500.0, (payload["shares"] as Number).toDouble(), 0.0)
        assertEquals(5L, (payload["productId"] as Number).toLong())
        assertEquals(7L, (payload["sourceAccountId"] as Number).toLong())
        assertEquals(8L, (payload["targetAccountId"] as Number).toLong())
        assertEquals("半导体ETF", payload["productNameHint"])
        assertEquals("券商账户", payload["sourceAccountNameHint"])
        assertFalse(payload.containsKey("amount"))
        assertFalse(payload.containsKey("accountId"))
    }

    @Test
    fun switchingFromSellToExpenseClearsSellFields() {
        val sellForm = DraftEditForm(
            txnType = "SELL",
            productId = "5",
            productNameHint = "半导体ETF",
            shares = "500",
            sourceAccountId = "7",
            sourceAccountNameHint = "券商账户",
            targetAccountId = "8",
        )

        val expense = DraftEditState.switchTxnType(sellForm, "EXPENSE")

        assertEquals("EXPENSE", expense.txnType)
        assertEquals("", expense.shares)
        assertEquals("", expense.sourceAccountId)
        assertEquals("", expense.sourceAccountNameHint)
        assertEquals("", expense.targetAccountId)
        assertEquals("", expense.productId)
    }

    @Test
    fun formFromDraftReadsBackSellRedeemFields() {
        val form = DraftEditState.formFromDraft(
            draft(
                parsedPayloadJson =
                """{"txnType":"SELL","shares":500,"productId":5,"sourceAccountId":7,"sourceAccountNameHint":"券商账户","targetAccountId":8}""",
            ),
        )

        assertEquals("SELL", form.txnType)
        assertEquals("500", form.shares)
        assertEquals("5", form.productId)
        assertEquals("7", form.sourceAccountId)
        assertEquals("券商账户", form.sourceAccountNameHint)
        assertEquals("8", form.targetAccountId)
    }

    @Test
    fun investmentPayloadCarriesProductAndDropsTargetAccount() {
        val result = DraftEditState.buildUpdateRequest(
            draft(),
            DraftEditForm(
                txnType = "SUBSCRIPTION",
                amount = "500",
                accountId = "7",
                productId = "6",
                productNameHint = "兴全合润",
                expectedNavDate = "2026-09-29",
                expectedConfirmDate = "2026-09-30",
                targetAccountId = "9",
            ),
        )

        assertTrue(result.isValid)
        val payload = DraftEditState.parsePayloadJson(result.request?.parsedPayloadJson)
        assertEquals("SUBSCRIPTION", payload["txnType"])
        assertEquals(7L, (payload["accountId"] as Number).toLong())
        assertEquals(6L, (payload["productId"] as Number).toLong())
        assertEquals("兴全合润", payload["productNameHint"])
        assertEquals("2026-09-29", payload["expectedNavDate"])
        assertEquals("2026-09-30", payload["expectedConfirmDate"])
        assertFalse(payload.containsKey("targetAccountId"))
    }

    @Test
    fun switchingBackToOrdinaryTypesClearsInvestmentFields() {
        val investmentForm = DraftEditForm(
            txnType = "BUY",
            amount = "1000",
            accountId = "7",
            productId = "5",
            productNameHint = "纳指ETF",
            expectedNavDate = "2026-09-29",
            expectedConfirmDate = "2026-09-30",
        )

        val expense = DraftEditState.switchTxnType(investmentForm, "EXPENSE")

        assertEquals("EXPENSE", expense.txnType)
        assertEquals("", expense.productId)
        assertEquals("", expense.productNameHint)
        assertEquals("", expense.expectedNavDate)
        assertEquals("", expense.expectedConfirmDate)
        assertTrue(expense.accountId.isNotBlank())
    }

    @Test
    fun switchingFromTransferToInvestmentDropsTargetAccount() {
        val transfer = DraftEditForm(
            txnType = "TRANSFER",
            amount = "100",
            accountId = "7",
            targetAccountId = "8",
        )

        val investment = DraftEditState.switchTxnType(transfer, "BUY")

        assertEquals("BUY", investment.txnType)
        assertEquals("", investment.targetAccountId)
    }

    @Test
    fun stalePreviewCannotConfirmInvestmentDraft() {
        val buyDraft = draft(
            id = 10,
            parsedPayloadJson = """{"txnType":"BUY","productId":5,"accountId":7,"amount":1000}""",
        )
        val freshPreview = DraftPreviewDto(
            draftId = 10,
            txnType = "BUY",
            productId = 5,
            confirmSupported = true,
        )

        assertTrue(DraftEditState.canConfirm(buyDraft, freshPreview, false, false, false, false))
        assertFalse(
            DraftEditState.canConfirm(
                buyDraft,
                freshPreview.copy(confirmSupported = false),
                false,
                false,
                false,
                false,
            ),
        )
        assertFalse(
            DraftEditState.canConfirm(
                buyDraft,
                freshPreview,
                editDirty = true,
                saving = false,
                previewing = false,
                confirming = false,
            ),
        )
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
