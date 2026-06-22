package com.timelordtty.mydca.notification

/**
 * 支付通知保守解析器。
 * 首版只给出是否疑似支付和可选金额，不创建草稿、不调用后端、不输出通知原文。
 */
object PaymentNotificationParser {
    private val paymentKeywords = listOf(
        "支付",
        "付款",
        "消费",
        "扣款",
        "收款",
        "到账",
        "退款",
        "交易",
        "转账",
    )
    private val sourceHints = mapOf(
        "alipay" to "支付宝",
        "支付宝" to "支付宝",
        "wechat" to "微信",
        "weixin" to "微信",
        "微信" to "微信",
        "bank" to "银行",
        "银行" to "银行",
        "unionpay" to "银联",
        "银联" to "银联",
    )
    private val amountPattern = Regex("(?<![0-9])(?:¥|￥|RMB|人民币)?\\s*([0-9]{1,6}(?:\\.[0-9]{1,2})?)\\s*(?:元|CNY|RMB)?(?![0-9])")

    fun parse(packageName: String, appLabel: String?, title: String?, text: String?): ParsedPaymentNotification {
        val combined = listOfNotNull(appLabel, title, text).joinToString(" ").trim()
        val hasPaymentKeyword = paymentKeywords.any { combined.contains(it, ignoreCase = true) }
        val sourceHint = resolveSourceHint(packageName, appLabel, combined)
        val amount = extractAmount(combined)
        return ParsedPaymentNotification(
            isPaymentCandidate = hasPaymentKeyword || (sourceHint != null && amount != null),
            amount = amount,
            sourceHint = sourceHint,
        )
    }

    private fun resolveSourceHint(packageName: String, appLabel: String?, text: String): String? {
        val sourceText = listOfNotNull(packageName, appLabel, text).joinToString(" ")
        return sourceHints.entries.firstOrNull { (needle, _) ->
            sourceText.contains(needle, ignoreCase = true)
        }?.value
    }

    private fun extractAmount(text: String): String? {
        return amountPattern.findAll(text)
            .mapNotNull { match -> match.groupValues.getOrNull(1) }
            .filterNot { value -> looksLikeDateOrTime(text, value) }
            .firstOrNull()
    }

    private fun looksLikeDateOrTime(text: String, value: String): Boolean {
        val index = text.indexOf(value)
        if (index < 0) return false
        val start = (index - 3).coerceAtLeast(0)
        val end = (index + value.length + 3).coerceAtMost(text.length)
        val context = text.substring(start, end)
        return context.contains("年") ||
            context.contains("月") ||
            context.contains("日") ||
            context.contains(":") ||
            context.contains("时") ||
            context.contains("分")
    }
}

data class ParsedPaymentNotification(
    val isPaymentCandidate: Boolean,
    val amount: String? = null,
    val sourceHint: String? = null,
)
