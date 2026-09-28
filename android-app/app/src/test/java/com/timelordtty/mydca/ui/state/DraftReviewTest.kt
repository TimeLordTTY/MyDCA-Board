package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.data.dto.DraftLedgerEntryDto
import com.timelordtty.mydca.data.dto.DraftPreviewDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftReviewTest {
    @Test
    fun onlyDraftStatusCountsAsPending() {
        assertTrue(DraftReview.isDraft(draft(status = "DRAFT")))
        assertTrue(DraftReview.isDraft(draft(status = "draft")))
        assertFalse(DraftReview.isDraft(draft(status = "CONFIRMED")))
        assertFalse(DraftReview.isDraft(null))

        assertEquals(
            2,
            DraftReview.pendingDraftCount(
                listOf(draft(id = 1, status = "DRAFT"), draft(id = 2, status = "IGNORED"), draft(id = 3, status = "DRAFT")),
            ),
        )
    }

    @Test
    fun statusAndTypeLabelsStayExplicit() {
        assertEquals("DRAFT（未入账，等待人工确认）", DraftReview.statusLabel("DRAFT"))
        assertEquals("CONFIRMED（已确认入账）", DraftReview.statusLabel("CONFIRMED"))
        assertEquals("未知状态", DraftReview.statusLabel(null))
        assertEquals("支出 EXPENSE", DraftReview.txnTypeLabel("expense"))
        assertEquals("类型待补充", DraftReview.txnTypeLabel(null))
    }

    @Test
    fun parsedInfoReadsBackendCompatiblePayloadKeys() {
        val info = DraftReview.parsedInfo(
            draft(
                parsedPayloadJson =
                """{"txnType":"EXPENSE","amount":18.80,"cashAccountId":42,"accountNameHint":"现金","description":"早餐"}""",
                missingFieldsJson = """["accountId","note"]""",
                confidence = 0.9,
            ),
        )

        assertEquals("EXPENSE", info.txnType)
        assertEquals("18.8", info.amount)
        assertEquals("42", info.accountId)
        assertEquals("现金", info.accountNameHint)
        assertEquals("早餐", info.note)
        assertEquals("0.9", info.confidence)
        assertEquals(listOf("accountId", "note"), info.missingFields)
        assertEquals("accountId、note", info.missingFieldText)
        assertTrue(info.hasParsedField)
    }

    @Test
    fun emptyPayloadNeverInventsParsedFields() {
        val info = DraftReview.parsedInfo(draft(parsedPayloadJson = null, missingFieldsJson = null))

        assertFalse(info.hasParsedField)
        assertEquals("无", info.missingFieldText)
        assertTrue(DraftReview.parseStringList(null).isEmpty())
        assertTrue(DraftReview.parseStringList("").isEmpty())
        assertTrue(DraftReview.parseStringList("not-json").isEmpty())
    }

    @Test
    fun summaryAndListLabelStateWhatIsStillMissing() {
        val complete = draft(id = 7, parsedPayloadJson = """{"txnType":"EXPENSE","amount":12.3,"accountId":5}""")
        val incomplete = draft(id = 8, parsedPayloadJson = null)

        assertTrue(DraftReview.summary(incomplete).contains("金额待补充"))
        assertTrue(DraftReview.summary(incomplete).contains("账户待选择"))
        assertEquals("#7 · 待确认 · 支出 EXPENSE · 金额 12.3 · 账户 5", DraftReview.listLabel(complete))
    }

    @Test
    fun transferPreviewShowsBothAccountsAndBothDeltas() {
        val preview = DraftPreviewDto(
            draftId = 3,
            txnType = "TRANSFER",
            accountName = "专款账户",
            fundUsage = "RESERVED",
            targetAccountName = "日常账户",
            targetFundUsage = "SPENDABLE",
            amount = 300.0,
            accountDelta = -300.0,
            targetAccountDelta = 300.0,
            confirmSupported = true,
            warnings = listOf("本次会把资金从 RESERVED 转到 SPENDABLE，请确认这是主动调整资金分区。"),
        )

        assertEquals("转账 TRANSFER", DraftReview.txnTypeLabel("transfer"))
        assertEquals(
            listOf(
                "从：专款账户 / RESERVED",
                "到：日常账户 / SPENDABLE",
                "金额：300",
                "转出账户变动：-300",
                "转入账户变动：+300",
            ),
            DraftReview.transferImpactLines(preview),
        )
    }

    @Test
    fun transferConfirmDialogStatesFromToAndAmount() {
        val preview = DraftPreviewDto(
            draftId = 3,
            txnType = "TRANSFER",
            accountName = "专款账户",
            targetAccountName = "日常账户",
            amount = 300.0,
            confirmSupported = true,
        )

        assertEquals("确认将 ¥300 从 专款账户 转到 日常账户？", DraftReview.confirmDialogTitle(preview))
        assertTrue(DraftReview.confirmDialogMessage(preview, canConfirm = true).contains("正式转账流水"))
        assertTrue(DraftReview.confirmDialogMessage(preview, canConfirm = false).contains("阻止"))
        assertEquals("确认正式记账？", DraftReview.confirmDialogTitle(null))
    }

    @Test
    fun expensePreviewHasNoTransferLines() {
        val preview = DraftPreviewDto(draftId = 1, txnType = "EXPENSE", amount = 12.3, confirmSupported = true)

        assertTrue(DraftReview.transferImpactLines(preview).isEmpty())
        assertTrue(DraftReview.transferImpactLines(null).isEmpty())
        assertEquals("确认正式记账？", DraftReview.confirmDialogTitle(preview))
    }
    private fun draft(
        id: Long = 1L,
        status: String = "DRAFT",
        parsedPayloadJson: String? = null,
        missingFieldsJson: String? = null,
        confidence: Double? = null,
    ) = DraftLedgerEntryDto(
        id = id,
        status = status,
        parsedPayloadJson = parsedPayloadJson,
        missingFieldsJson = missingFieldsJson,
        confidence = confidence,
    )
}
