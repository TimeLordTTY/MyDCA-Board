package com.timelordtty.mydca.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证支付通知解析保持保守，不把普通通知、验证码或订单号误判为可用金额。
 */
class PaymentNotificationParserTest {
    @Test
    fun parseWechatPaymentWithAmount() {
        val result = PaymentNotificationParser.parse(
            packageName = "com.tencent.mm",
            appLabel = "微信",
            title = "微信支付",
            text = "你已成功付款 18.80 元",
        )

        assertTrue(result.isPaymentCandidate)
        assertEquals("18.80", result.amount)
        assertEquals("微信", result.sourceHint)
    }

    @Test
    fun parseBankDebitWithCurrencyPrefix() {
        val result = PaymentNotificationParser.parse(
            packageName = "com.bank.demo",
            appLabel = "银行",
            title = "账户扣款提醒",
            text = "消费成功，金额￥128.50",
        )

        assertTrue(result.isPaymentCandidate)
        assertEquals("128.50", result.amount)
        assertEquals("银行", result.sourceHint)
    }

    @Test
    fun ignoreNormalChatNotification() {
        val result = PaymentNotificationParser.parse(
            packageName = "com.tencent.mm",
            appLabel = "微信",
            title = "好友消息",
            text = "今晚一起吃饭吗",
        )

        assertFalse(result.isPaymentCandidate)
        assertNull(result.amount)
    }

    @Test
    fun doesNotTreatVerificationCodeAsAmount() {
        val result = PaymentNotificationParser.parse(
            packageName = "com.demo.pay",
            appLabel = "支付应用",
            title = "验证码",
            text = "验证码 123456，请勿泄露",
        )

        assertTrue(result.isPaymentCandidate)
        assertNull(result.amount)
    }

    @Test
    fun doesNotTreatOrderNumberAsAmount() {
        val result = PaymentNotificationParser.parse(
            packageName = "com.demo.pay",
            appLabel = "支付应用",
            title = "支付成功",
            text = "订单号 202606220001 已完成",
        )

        assertTrue(result.isPaymentCandidate)
        assertNull(result.amount)
    }

    @Test
    fun refundCandidateCanHaveNoAmount() {
        val result = PaymentNotificationParser.parse(
            packageName = "com.alipay.mobile",
            appLabel = "支付宝",
            title = "退款到账",
            text = "退款已到账，请稍后查看余额",
        )

        assertTrue(result.isPaymentCandidate)
        assertNull(result.amount)
        assertEquals("支付宝", result.sourceHint)
    }
}
