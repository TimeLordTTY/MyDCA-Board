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
    @Test
    fun investmentPreviewShowsProductFundingAccountAndReceivable() {
        val preview = DraftPreviewDto(
            draftId = 4,
            txnType = "BUY",
            orderType = "BUY",
            productId = 5,
            productName = "纳指ETF",
            productCode = "513100",
            productAssetType = "ETF",
            productCurrency = "CNY",
            accountName = "证券投资账户",
            fundUsage = "INVESTABLE",
            availableBefore = 5000.0,
            amount = 1000.0,
            accountDelta = -1000.0,
            receivableDelta = 1000.0,
            impactDirection = "DECREASE",
            confirmSupported = true,
            fundingMessage = "从【证券投资账户】扣款 1000 元，增加同额待结算应收。",
        )

        assertEquals("买入 BUY", DraftReview.txnTypeLabel("buy"))
        assertEquals("申购 SUBSCRIPTION", DraftReview.txnTypeLabel("SUBSCRIPTION"))
        assertEquals(
            listOf(
                "产品：纳指ETF / 513100 / ETF",
                "资金来源：证券投资账户 / INVESTABLE",
                "可用余额：5000",
                "本次金额：1000",
                "付款账户变动：-1000",
                "待结算应收变动：+1000",
                "从【证券投资账户】扣款 1000 元，增加同额待结算应收。",
            ),
            DraftReview.investmentImpactLines(preview),
        )
        assertTrue(DraftReview.transferImpactLines(preview).isEmpty())
        assertTrue(DraftReview.investmentImpactLines(null).isEmpty())
    }

    @Test
    fun buyConfirmDialogStatesImmediatePaymentLedgerImpact() {
        val preview = DraftPreviewDto(
            draftId = 4,
            txnType = "BUY",
            productName = "纳指ETF",
            accountName = "证券投资账户",
            amount = 1000.0,
            confirmSupported = true,
        )

        assertEquals("确认创建【纳指ETF】买入订单 ¥1000？", DraftReview.confirmDialogTitle(preview))
        val message = DraftReview.confirmDialogMessage(preview, canConfirm = true)
        assertTrue(message.contains("立即从【证券投资账户】扣除 ¥1000"))
        assertTrue(message.contains("待结算应收"))
        assertTrue(message.contains("不会自动成交"))
        assertTrue(DraftReview.confirmDialogMessage(preview, canConfirm = false).contains("阻止"))
    }

    @Test
    fun subscriptionConfirmDialogUsesSubscriptionWording() {
        val preview = DraftPreviewDto(
            draftId = 5,
            txnType = "SUBSCRIPTION",
            productName = "兴全合润",
            accountName = "基金投资账户",
            amount = 500.0,
            confirmSupported = true,
        )

        assertEquals("确认创建【兴全合润】申购订单 ¥500？", DraftReview.confirmDialogTitle(preview))
        assertTrue(DraftReview.confirmDialogMessage(preview, canConfirm = true).contains("基金投资账户"))
    }

    @Test
    fun expensePreviewHasNoInvestmentLines() {
        val preview = DraftPreviewDto(draftId = 1, txnType = "EXPENSE", amount = 12.3, confirmSupported = true)

        assertTrue(DraftReview.investmentImpactLines(preview).isEmpty())
        assertEquals("确认正式记账？", DraftReview.confirmDialogTitle(preview))
    }

    @Test
    fun sellRedeemPreviewShowsSharesSourceAndTarget() {
        val preview = DraftPreviewDto(
            draftId = 6,
            txnType = "SELL",
            orderType = "SELL",
            productName = "半导体ETF",
            productCode = "512480",
            productAssetType = "ETF",
            productCurrency = "CNY",
            accountName = "券商账户",
            targetAccountName = "到账银行卡",
            targetFundUsage = "SPENDABLE",
            shares = 500.0,
            availableShares = 1000.0,
            remainingShares = 500.0,
            confirmSupported = true,
            sharesMessage = "确认后只创建内部 PENDING 卖出记录并占用 券商账户 的 500 份；不会立即减少持仓，也不会立即增加 到账银行卡 的到账余额。",
        )

        assertEquals("卖出 SELL", DraftReview.txnTypeLabel("sell"))
        assertEquals("赎回 REDEMPTION", DraftReview.txnTypeLabel("redemption"))
        assertTrue(DraftReview.investmentImpactLines(preview).isEmpty())
        assertEquals(
            listOf(
                "产品：半导体ETF / 512480 / ETF",
                "持仓来源：券商账户",
                "当前可用份额：1000",
                "本次份额：500",
                "预计剩余份额：500",
                "到账账户：到账银行卡 / SPENDABLE",
                "确认后只创建内部 PENDING 卖出记录并占用 券商账户 的 500 份；不会立即减少持仓，也不会立即增加 到账银行卡 的到账余额。",
            ),
            DraftReview.sellRedeemImpactLines(preview),
        )
        assertTrue(DraftReview.sellRedeemImpactLines(null).isEmpty())
    }

    @Test
    fun sellRedeemConfirmDialogStatesPendingOnlyNoImmediateImpact() {
        val preview = DraftPreviewDto(
            draftId = 6,
            txnType = "REDEMPTION",
            productName = "兴全合润",
            accountName = "基金账户",
            targetAccountName = "到账银行卡",
            shares = 1000.0,
            confirmSupported = true,
        )

        assertEquals("确认创建【兴全合润】赎回订单 1000 份？", DraftReview.confirmDialogTitle(preview))
        val message = DraftReview.confirmDialogMessage(preview, canConfirm = true)
        assertTrue(message.contains("内部 PENDING"))
        assertTrue(message.contains("不会立即减少持仓"))
        assertTrue(message.contains("1000 份"))
        assertTrue(DraftReview.confirmDialogMessage(preview, canConfirm = false).contains("阻止"))
    }

    @Test
    fun sellRedeemImpactSummaryReadsShares() {
        val draft = draft(
            id = 9,
            parsedPayloadJson = """{"txnType":"SELL","shares":500,"productId":5,"sourceAccountId":7}""",
        )

        val info = DraftReview.parsedInfo(draft)
        assertEquals("500", info.shares)
        assertEquals("7", info.sourceAccountId)
        assertTrue(DraftReview.summary(draft).contains("份额 500"))
        assertTrue(DraftReview.summary(draft).contains("持仓来源 7"))
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
