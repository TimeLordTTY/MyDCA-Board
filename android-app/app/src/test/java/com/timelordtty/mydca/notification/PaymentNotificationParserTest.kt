package com.timelordtty.mydca.notification

import org.junit.Assert.*
import org.junit.Test

class PaymentNotificationParserTest {
    @Test fun acceptsSupportedPaymentWithAmount() {
        val result = PaymentNotificationParser.parse("com.tencent.mm", "微信", "微信支付", "付款 18.80 元")
        assertTrue(result.isPaymentCandidate)
        assertEquals("18.80", result.amount)
        assertEquals("微信支付", result.sourceHint)
    }

    @Test fun rejectsUnknownPackageEvenWithPaymentText() {
        assertFalse(PaymentNotificationParser.parse("com.bank.demo", "银行", "扣款", "消费 128 元").isPaymentCandidate)
    }

    @Test fun rejectsChatMarketingVerificationAndMissingAmount() {
        assertFalse(PaymentNotificationParser.parse("com.tencent.mm", "微信", "好友消息", "今晚吃饭吗").isPaymentCandidate)
        assertFalse(PaymentNotificationParser.parse("com.eg.android.AlipayGphone", "支付宝", "活动", "优惠支付 10 元").isPaymentCandidate)
        assertFalse(PaymentNotificationParser.parse("com.unionpay", "云闪付", "验证码", "支付验证码 123456").isPaymentCandidate)
        assertFalse(PaymentNotificationParser.parse("com.tencent.mm", "微信", "退款到账", "请查看余额").isPaymentCandidate)
    }

    @Test fun sanitizesSensitiveValuesAndTruncates() {
        val value = PaymentNotificationParser.sanitizeSnippet("验证码：123456 手机 13812345678 订单号 ABCDEF123456 " + "内容".repeat(40))!!
        assertFalse(value.contains("123456"))
        assertFalse(value.contains("13812345678"))
        assertFalse(value.contains("ABCDEF123456"))
        assertTrue(value.length <= 49)
    }

    @Test fun fingerprintIsStableWithinMinuteAndChangesWithAmount() {
        val first = PaymentNotificationParser.fingerprint("com.tencent.mm", "18.80", "支付", "付款 18.80 元", 60_001)
        val repeated = PaymentNotificationParser.fingerprint("com.tencent.mm", "18.80", "支付", "付款 18.80 元", 119_999)
        val other = PaymentNotificationParser.fingerprint("com.tencent.mm", "19.80", "支付", "付款 19.80 元", 60_001)
        assertEquals(first, repeated)
        assertNotEquals(first, other)
    }
}
