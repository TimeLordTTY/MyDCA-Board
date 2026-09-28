package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.data.dto.PendingSettlementOrderDto
import com.timelordtty.mydca.data.dto.SettlementPostingPreviewDto
import com.timelordtty.mydca.data.dto.SettlementPreviewDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.13.0 人工结算安全链的纯逻辑测试：
 * 字段差异、输入校验、fresh preview gate、字段修改后旧预览失效、中文文案。
 */
class SettlementEditStateTest {
    private val buyOrder = PendingSettlementOrderDto(
        id = 1L,
        orderId = "ORD-20260928-AAA111",
        productId = 5L,
        orderType = "BUY",
        amount = 1000.0,
        expectedNavDate = "2026-09-28",
        expectedConfirmDate = "2026-09-29",
        status = "PENDING",
    )

    private val sellOrder = buyOrder.copy(
        id = 2L,
        orderId = "ORD-20260928-BBB222",
        orderType = "SELL",
        amount = null,
        shares = 100.0,
    )

    private fun validBuyForm(): SettlementEditForm = SettlementEditForm(
        orderId = buyOrder.orderId,
        orderType = "BUY",
        confirmDate = "2026-09-29",
        navDate = "2026-09-28",
        confirmNav = "1.2345",
        confirmShares = "",
        confirmFee = "",
    )

    private fun validSellForm(): SettlementEditForm = SettlementEditForm(
        orderId = sellOrder.orderId,
        orderType = "SELL",
        confirmDate = "2026-09-29",
        navDate = "2026-09-28",
        confirmNav = "1.2345",
        confirmShares = "100",
        confirmAmount = "120.50",
        confirmFee = "5.00",
    )

    private fun buyPreview(form: SettlementEditForm): SettlementPreviewDto = SettlementPreviewDto(
        orderId = form.orderId,
        orderType = "BUY",
        orderTypeLabel = "买入 BUY",
        orderStatus = "PENDING",
        productName = "沪深300ETF",
        productCode = "510300",
        confirmDate = form.confirmDate,
        navDate = form.navDate,
        confirmNav = 1.2345,
        computedShares = 806.0,
        confirmFee = 0.0,
        confirmSupported = true,
        freshPreviewToken = "token-buy-1",
        summaryLines = listOf("持仓账户 POSITION：+806.00 份", "手续费：0.00 元"),
        postingsPreview = listOf(
            SettlementPostingPreviewDto(
                accountName = "券商持仓账户",
                accountType = "POSITION",
                postingType = "DEBIT",
                shares = 806.0,
            ),
        ),
        willCreateSettlementConfirm = true,
        willCreateLedgerTxn = true,
        willChangeHolding = true,
        willChangeCash = false,
    )

    @Test
    fun fieldLabelsDifferBetweenBuyAndSellOrders() {
        assertEquals(
            listOf("确认日期", "净值日期", "实际净值", "实际份额", "手续费"),
            SettlementEditState.fieldLabels("BUY"),
        )
        assertEquals(
            listOf("确认日期", "净值日期", "实际净值", "实际份额", "手续费"),
            SettlementEditState.fieldLabels("SUBSCRIPTION"),
        )
        assertEquals(
            listOf("确认日期", "净值日期", "实际净值", "实际份额", "实际到账金额", "手续费"),
            SettlementEditState.fieldLabels("SELL"),
        )
        assertEquals(
            listOf("确认日期", "净值日期", "实际净值", "实际份额", "实际到账金额", "手续费"),
            SettlementEditState.fieldLabels("REDEMPTION"),
        )
        assertTrue(SettlementEditState.fieldLabels("TRANSFER").isEmpty())
    }

    @Test
    fun orderTypeHelpersOnlyAcceptFourInvestmentTypes() {
        assertTrue(SettlementEditState.isBuyLike("buy"))
        assertTrue(SettlementEditState.isBuyLike("SUBSCRIPTION"))
        assertTrue(SettlementEditState.isSellLike("sell"))
        assertTrue(SettlementEditState.isSellLike("redemption"))
        assertFalse(SettlementEditState.isSellLike("BUY"))
        assertFalse(SettlementEditState.isBuyLike("SELL"))
        assertNull(SettlementEditState.normalizeOrderType("TRANSFER"))
        assertNull(SettlementEditState.normalizeOrderType(null))
        assertEquals("买入 BUY", SettlementEditState.orderTypeLabel("buy"))
        assertEquals("申购 SUBSCRIPTION", SettlementEditState.orderTypeLabel("SUBSCRIPTION"))
        assertEquals("卖出 SELL", SettlementEditState.orderTypeLabel("SELL"))
        assertEquals("赎回 REDEMPTION", SettlementEditState.orderTypeLabel("REDEMPTION"))
        // 不支持的类型原样展示订单类型码，由页面明确提示「暂不支持人工结算」。
        assertEquals("TRANSFER", SettlementEditState.orderTypeLabel("TRANSFER"))
        assertEquals("类型待补充", SettlementEditState.orderTypeLabel(null))
        assertEquals("结算", SettlementEditState.actionLabel("TRANSFER"))
    }

    @Test
    fun formFromOrderPrefillsExpectedDates() {
        val form = SettlementEditState.formFromOrder(buyOrder, today = "2026-09-28")

        assertEquals(buyOrder.orderId, form.orderId)
        assertEquals("BUY", form.orderType)
        assertEquals("2026-09-29", form.confirmDate)
        assertEquals("2026-09-28", form.navDate)
        // 实际净值 / 份额 / 手续费必须由主人填写，移动端不会预填任何猜测值。
        assertEquals("", form.confirmNav)
        assertEquals("", form.confirmShares)
        assertEquals("", form.confirmFee)
    }

    @Test
    fun formFromOrderFallsBackToTodayWhenOrderHasNoExpectedDate() {
        val order = buyOrder.copy(expectedConfirmDate = null, expectedNavDate = null)

        val form = SettlementEditState.formFromOrder(order, today = "2026-09-28")

        assertEquals("2026-09-28", form.confirmDate)
        assertEquals("2026-09-28", form.navDate)
    }

    @Test
    fun buyRequestOnlyNeedsDatesAndNav() {
        val result = SettlementEditState.buildRequest(validBuyForm())

        assertTrue(result.isValid)
        val request = requireNotNull(result.request)
        assertEquals(buyOrder.orderId, request.orderId)
        assertEquals("2026-09-29", request.confirmDate)
        assertEquals("2026-09-28", request.navDate)
        assertEquals(1.2345, request.confirmNav)
        // 留空表示由后端按金额与净值计算，绝不能悄悄变成 0。
        assertNull(request.confirmShares)
        assertNull(request.confirmAmount)
        assertNull(request.confirmFee)
        // preview 阶段不能携带任何令牌。
        assertNull(request.freshPreviewToken)
    }

    @Test
    fun sellRequestRequiresPositiveConfirmAmount() {
        assertTrue(SettlementEditState.buildRequest(validSellForm()).isValid)

        val missing = SettlementEditState.buildRequest(validSellForm().copy(confirmAmount = ""))
        assertFalse(missing.isValid)
        assertTrue(missing.error!!.contains("实际到账金额"))

        val zero = SettlementEditState.buildRequest(validSellForm().copy(confirmAmount = "0"))
        assertFalse(zero.isValid)
        assertTrue(zero.error!!.contains("必须大于 0"))

        val negative = SettlementEditState.buildRequest(validSellForm().copy(confirmAmount = "-10"))
        assertFalse(negative.isValid)
        assertTrue(negative.error!!.contains("必须大于 0"))
    }

    @Test
    fun explicitZeroFeeIsKeptWhileBlankFeeMeansEstimate() {
        val estimated = SettlementEditState.buildRequest(validSellForm().copy(confirmFee = ""))
        assertNull(requireNotNull(estimated.request).confirmFee)

        val explicitZero = SettlementEditState.buildRequest(validSellForm().copy(confirmFee = "0"))
        assertEquals(0.0, requireNotNull(explicitZero.request).confirmFee)

        val explicitZeroWithScale = SettlementEditState.buildRequest(validSellForm().copy(confirmFee = "0.00"))
        assertEquals(0.0, requireNotNull(explicitZeroWithScale.request).confirmFee)
    }

    @Test
    fun illegalDatesAndNavAreBlockedWithChineseReasons() {
        val missingDate = SettlementEditState.buildRequest(validBuyForm().copy(confirmDate = ""))
        assertFalse(missingDate.isValid)
        assertTrue(missingDate.error!!.contains("确认日期"))

        val badDate = SettlementEditState.buildRequest(validBuyForm().copy(navDate = "2026-13-45"))
        assertFalse(badDate.isValid)
        assertTrue(badDate.error!!.contains("净值日期"))

        val missingNav = SettlementEditState.buildRequest(validBuyForm().copy(confirmNav = ""))
        assertFalse(missingNav.isValid)
        assertTrue(missingNav.error!!.contains("实际净值"))

        val zeroNav = SettlementEditState.buildRequest(validBuyForm().copy(confirmNav = "0"))
        assertTrue(zeroNav.error!!.contains("必须大于 0"))

        val negativeNav = SettlementEditState.buildRequest(validBuyForm().copy(confirmNav = "-1.2"))
        assertTrue(negativeNav.error!!.contains("必须大于 0"))
    }

    @Test
    fun unsupportedOrderTypeAndMissingOrderIdAreBlocked() {
        val transfer = SettlementEditState.buildRequest(SettlementEditForm(orderId = "ORD-1", orderType = "TRANSFER"))
        assertFalse(transfer.isValid)
        assertTrue(transfer.error!!.contains("暂不支持"))

        val noOrder = SettlementEditState.buildRequest(validBuyForm().copy(orderId = ""))
        assertFalse(noOrder.isValid)
        assertTrue(noOrder.error!!.contains("订单号"))
    }

    @Test
    fun negativeSharesAndNegativeFeeAreBlocked() {
        val negativeShares = SettlementEditState.buildRequest(validBuyForm().copy(confirmShares = "-1"))
        assertFalse(negativeShares.isValid)
        assertTrue(negativeShares.error!!.contains("实际份额"))

        val negativeFee = SettlementEditState.buildRequest(validSellForm().copy(confirmFee = "-1"))
        assertFalse(negativeFee.isValid)
        assertTrue(negativeFee.error!!.contains("手续费"))
    }

    @Test
    fun confirmIsBlockedWithoutAnyPreview() {
        val form = validBuyForm()

        assertFalse(SettlementEditState.canConfirm(form = form, preview = null, previewFingerprint = null))
        assertFalse(
            SettlementEditState.canConfirm(
                form = form,
                preview = null,
                previewFingerprint = SettlementEditState.inputFingerprint(form),
            ),
        )
    }

    @Test
    fun confirmIsAllowedOnlyWithFreshPreviewOfSameOrder() {
        val form = validBuyForm()
        val preview = buyPreview(form)

        assertTrue(
            SettlementEditState.canConfirm(
                form = form,
                preview = preview,
                previewFingerprint = SettlementEditState.inputFingerprint(form),
            ),
        )
    }

    @Test
    fun confirmIsBlockedWhenPreviewIsNotSupported() {
        val form = validBuyForm()
        val blocked = buyPreview(form).copy(confirmSupported = false, blockingReasons = listOf("实际净值必须大于 0"))

        assertFalse(
            SettlementEditState.canConfirm(
                form = form,
                preview = blocked,
                previewFingerprint = SettlementEditState.inputFingerprint(form),
            ),
        )
        assertTrue(SettlementEditState.blockingText(blocked).contains("实际净值必须大于 0"))
    }

    @Test
    fun confirmIsBlockedWithoutFreshPreviewToken() {
        val form = validBuyForm()
        val tokenless = buyPreview(form).copy(freshPreviewToken = null)

        assertFalse(
            SettlementEditState.canConfirm(
                form = form,
                preview = tokenless,
                previewFingerprint = SettlementEditState.inputFingerprint(form),
            ),
        )
        assertFalse(
            SettlementEditState.canConfirm(
                form = form,
                preview = buyPreview(form).copy(freshPreviewToken = "  "),
                previewFingerprint = SettlementEditState.inputFingerprint(form),
            ),
        )
    }

    @Test
    fun confirmIsBlockedWhenPreviewBelongsToAnotherOrderOrType() {
        val form = validBuyForm()
        val fingerprint = SettlementEditState.inputFingerprint(form)

        assertFalse(
            SettlementEditState.canConfirm(
                form = form,
                preview = buyPreview(form).copy(orderId = "ORD-OTHER"),
                previewFingerprint = fingerprint,
            ),
        )
        assertFalse(
            SettlementEditState.canConfirm(
                form = form,
                preview = buyPreview(form).copy(orderType = "SELL"),
                previewFingerprint = fingerprint,
            ),
        )
    }

    @Test
    fun editingAnyFieldInvalidatesTheOldPreview() {
        val form = validBuyForm()
        val preview = buyPreview(form)
        val fingerprint = SettlementEditState.inputFingerprint(form)
        assertTrue(SettlementEditState.canConfirm(form, preview, fingerprint))

        val edits = listOf(
            form.copy(confirmDate = "2026-09-30"),
            form.copy(navDate = "2026-09-27"),
            form.copy(confirmNav = "1.2346"),
            form.copy(confirmShares = "800"),
            form.copy(confirmAmount = "1"),
            form.copy(confirmFee = "1.00"),
        )
        edits.forEach { edited ->
            assertTrue(SettlementEditState.isPreviewStale(edited, fingerprint))
            assertFalse(SettlementEditState.canConfirm(edited, preview, fingerprint))
        }
    }

    @Test
    fun equalNumbersWithDifferentScalesKeepTheSameFingerprint() {
        val base = validSellForm()
        val scaled = base.copy(confirmAmount = "120.5000", confirmShares = "100.000", confirmFee = "5")

        // 等价输入不能因为写法不同就让主人重新预览。
        assertEquals(
            SettlementEditState.inputFingerprint(base),
            SettlementEditState.inputFingerprint(scaled),
        )
        assertFalse(SettlementEditState.isPreviewStale(scaled, SettlementEditState.inputFingerprint(base)))
    }

    @Test
    fun fingerprintIsMissingWhenNoPreviewWasEverGenerated() {
        assertTrue(SettlementEditState.isPreviewStale(validBuyForm(), null))
    }

    @Test
    fun confirmIsBlockedWhileAnInvalidFormIsStillIncomplete() {
        val form = validBuyForm().copy(confirmNav = "")
        val preview = buyPreview(form)

        assertFalse(
            SettlementEditState.canConfirm(
                form = form,
                preview = preview,
                previewFingerprint = SettlementEditState.inputFingerprint(form),
            ),
        )
    }

    @Test
    fun confirmIsBlockedWhileConfirmingIsAlreadyRunning() {
        val form = validBuyForm()

        assertFalse(
            SettlementEditState.canConfirm(
                form = form,
                preview = buyPreview(form),
                previewFingerprint = SettlementEditState.inputFingerprint(form),
                confirming = true,
            ),
        )
    }

    @Test
    fun previewTextExplainsCashHoldingAndFeeInChinese() {
        val form = validSellForm()
        val preview = buyPreview(form).copy(
            orderType = "SELL",
            orderTypeLabel = "卖出 SELL",
            confirmShares = 100.0,
            confirmAmount = 120.5,
            willChangeCash = true,
            willChangeHolding = true,
            summaryLines = listOf("现金账户 CASH：-120.50 元", "持仓账户 POSITION：-100.00 份", "手续费：5.00 元"),
        )

        val text = SettlementEditState.previewText(preview)

        assertTrue(text.contains("订单：${preview.orderId}"))
        assertTrue(text.contains("产品：沪深300ETF（510300）"))
        assertTrue(text.contains("确认日期："))
        assertTrue(text.contains("净值日期："))
        assertTrue(text.contains("实际净值：1.2345"))
        assertTrue(text.contains("实际份额：100"))
        assertTrue(text.contains("实际到账金额：120.5"))
        assertTrue(text.contains("手续费：5"))
        assertTrue(text.contains("结算影响："))
        assertTrue(text.contains("· 现金账户 CASH：-120.50 元"))
        assertTrue(text.contains("· 持仓账户 POSITION：-100.00 份"))
        assertTrue(text.contains("会改变现金"))
        assertTrue(text.contains("会改变持仓"))
        assertTrue(text.contains("会生成内部账本分录"))
    }

    @Test
    fun previewTextShowsBlockingReasonWhenPreviewIsRejected() {
        val form = validSellForm()
        val blocked = buyPreview(form).copy(
            confirmSupported = false,
            blockingReasons = listOf("订单当前状态为 CONFIRMED，只有 PENDING 订单可以结算"),
            willCreateSettlementConfirm = false,
            willCreateLedgerTxn = false,
            willChangeCash = false,
            willChangeHolding = false,
        )

        val text = SettlementEditState.previewText(blocked)

        assertTrue(text.contains("当前不可确认："))
        assertTrue(text.contains("只有 PENDING 订单可以结算"))
        assertTrue(text.contains("不写入结算确认"))
    }

    @Test
    fun previewTextNeverPretendsThereIsAPreview() {
        assertEquals("尚未生成结算预览。", SettlementEditState.previewText(null))
    }

    @Test
    fun confirmDialogCopyStatesInternalLedgerOnly() {
        val form = validSellForm()
        val preview = buyPreview(form).copy(willChangeCash = true, willChangeHolding = true)

        val title = SettlementEditState.confirmDialogTitle(preview)
        assertTrue(title.startsWith("确认结算【"))
        assertTrue(title.contains("买入"))
        assertTrue(title.endsWith("？"))

        val message = SettlementEditState.confirmDialogMessage(preview, canConfirm = true)
        assertTrue(message.contains("真正生成结算确认与内部账本分录"))
        assertTrue(message.contains("会改变相关账户现金余额"))
        assertTrue(message.contains("会改变相关持仓份额"))
        assertTrue(message.contains("不会向券商 / 基金公司 / 交易所发起任何真实交易"))

        val blockedMessage = SettlementEditState.confirmDialogMessage(preview, canConfirm = false)
        assertTrue(blockedMessage.contains("已阻止本次确认"))

        assertEquals("确认结算？", SettlementEditState.confirmDialogTitle(null))
    }

    @Test
    fun staleNoticeAndListSummaryAreExplicit() {
        assertTrue(SettlementEditState.staleNotice().contains("旧预览已失效"))

        val summary = SettlementEditState.orderSummary(sellOrder, productName = "沪深300ETF")
        assertTrue(summary.contains(sellOrder.orderId))
        assertTrue(summary.contains("卖出 SELL"))
        assertTrue(summary.contains("沪深300ETF"))
        assertTrue(summary.contains("份额 100"))
        assertTrue(summary.contains("预计确认 2026-09-29"))

        val fallback = SettlementEditState.orderSummary(
            buyOrder.copy(productId = 7L, expectedConfirmDate = null),
            productName = null,
        )
        assertTrue(fallback.contains("产品#7"))
        assertTrue(fallback.contains("金额 1000"))
    }

    @Test
    fun pendingCountOnlyCountsPendingOrders() {
        val orders = listOf(
            buyOrder,
            sellOrder,
            buyOrder.copy(orderId = "ORD-3", status = "CONFIRMED"),
            buyOrder.copy(orderId = "ORD-4", status = null),
            buyOrder.copy(orderId = "ORD-5", status = "pending"),
        )

        assertEquals(3, SettlementEditState.pendingCount(orders))
        assertTrue(SettlementEditState.isPending(buyOrder))
        assertFalse(SettlementEditState.isPending(buyOrder.copy(status = "CANCELLED")))
    }

    @Test
    fun sellFormKeepsConfirmAmountAndSharesSeparateFromBuyForm() {
        val sell = SettlementEditState.buildRequest(validSellForm())
        assertNotNull(sell.request)
        assertEquals(120.5, requireNotNull(sell.request).confirmAmount)
        assertEquals(100.0, requireNotNull(sell.request).confirmShares)
        assertEquals(5.0, requireNotNull(sell.request).confirmFee)

        val buy = SettlementEditState.buildRequest(validBuyForm().copy(confirmShares = "806.123456"))
        assertEquals(806.123456, requireNotNull(buy.request).confirmShares)
    }
}