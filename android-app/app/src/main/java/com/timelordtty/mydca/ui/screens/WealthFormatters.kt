package com.timelordtty.mydca.ui.screens

import java.math.BigDecimal
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val moneyFormatter = DecimalFormat("#,##0.00")
private val sharesFormatter = DecimalFormat("#,##0.####")

fun formatMoney(value: String?): String {
    val number = value?.toBigDecimalOrNull() ?: return "暂无"
    val sign = if (number < BigDecimal.ZERO) "-" else ""
    return sign + "¥" + moneyFormatter.format(number.abs())
}

fun formatSignedMoney(value: String?): String {
    val number = value?.toBigDecimalOrNull() ?: return "暂无"
    val prefix = when {
        number > BigDecimal.ZERO -> "+"
        number < BigDecimal.ZERO -> "-"
        else -> ""
    }
    return prefix + "¥" + moneyFormatter.format(number.abs())
}

fun formatShares(value: String?): String {
    val number = value?.toBigDecimalOrNull() ?: return "暂无"
    return sharesFormatter.format(number)
}

fun formatDateTime(value: String?): String {
    if (value.isNullOrBlank()) return "暂无"
    return runCatching {
        LocalDateTime.parse(value).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.getOrElse {
        runCatching {
            LocalDate.parse(value).format(DateTimeFormatter.ISO_LOCAL_DATE)
        }.getOrDefault(value)
    }
}
