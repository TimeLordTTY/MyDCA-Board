package com.timelordtty.mydca.notification

import java.security.MessageDigest

/** 仅接受明确支付应用、支付语义和有效金额的保守解析器。 */
object PaymentNotificationParser {
    private val supportedPackages = mapOf(
        "com.eg.android.AlipayGphone" to "支付宝",
        "com.tencent.mm" to "微信支付",
        "com.unionpay" to "云闪付",
    )
    private val paymentKeywords = listOf("支付", "付款", "消费", "扣款", "收款", "到账", "退款", "交易", "转账")
    private val blockedKeywords = listOf("验证码", "校验码", "广告", "优惠券", "活动", "营销", "促销", "聊天消息")
    private val amountPatterns = listOf(
        Regex("(?:¥|￥|RMB|人民币)\\s*([0-9]{1,8}(?:\\.[0-9]{1,2})?)(?![0-9])", RegexOption.IGNORE_CASE),
        Regex("(?<![0-9])([0-9]{1,8}(?:\\.[0-9]{1,2})?)\\s*(?:元|CNY|RMB)(?![0-9])", RegexOption.IGNORE_CASE),
    )

    fun parse(packageName: String, appLabel: String?, title: String?, text: String?): ParsedPaymentNotification {
        val source = supportedPackages[packageName]
        val combined = listOfNotNull(appLabel, title, text).joinToString(" ").trim()
        val amount = extractAmount(combined)
        val accepted = source != null &&
            paymentKeywords.any { combined.contains(it, ignoreCase = true) } &&
            blockedKeywords.none { combined.contains(it, ignoreCase = true) } &&
            amount != null
        return ParsedPaymentNotification(accepted, if (accepted) amount else null, if (accepted) source else null)
    }

    fun sanitizeSnippet(value: String?, maxLength: Int = 48): String? {
        var text = value?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        if (text.isBlank()) return null
        text = text
            .replace(Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)"), "***手机号")
            .replace(Regex("(?<!\\d)\\d{12,19}(?!\\d)"), "****卡号")
            .replace(Regex("(?i)(验证码|校验码)[:：\\s]*[0-9]{4,8}"), "$1：****")
            .replace(Regex("(?i)(订单号|交易号|流水号|单号)[:：\\s]*[A-Z0-9_-]{6,}"), "$1：****")
        return if (text.length <= maxLength) text else text.take(maxLength) + "…"
    }

    fun fingerprint(packageName: String, amount: String, title: String?, text: String?, postedAt: Long): String {
        val minuteBucket = postedAt / 60_000L
        val normalized = listOf(packageName, amount, sanitizeSnippet(title), sanitizeSnippet(text), minuteBucket)
            .joinToString("|") { it?.toString().orEmpty().lowercase() }
        return MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun extractAmount(text: String): String? = amountPatterns.asSequence()
        .flatMap { it.findAll(text).mapNotNull { match -> match.groupValues.getOrNull(1) } }
        .mapNotNull { value -> value.toDoubleOrNull()?.takeIf { it > 0.0 }?.let { value } }
        .firstOrNull()
}

data class ParsedPaymentNotification(
    val isPaymentCandidate: Boolean,
    val amount: String? = null,
    val sourceHint: String? = null,
)
