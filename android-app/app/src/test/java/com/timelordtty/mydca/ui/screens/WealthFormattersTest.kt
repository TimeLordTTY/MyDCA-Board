package com.timelordtty.mydca.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class WealthFormattersTest {
    @Test
    fun formatsMoneyAndSigns() {
        assertEquals("¥1,234.50", formatMoney("1234.5"))
        assertEquals("-¥8.00", formatSignedMoney("-8"))
        assertEquals("+¥8.00", formatSignedMoney("8"))
    }

    @Test
    fun formatsDateAndFallback() {
        assertEquals("2026-07-20 12:34", formatDateTime("2026-07-20T12:34:00"))
        assertEquals("暂无", formatDateTime(null))
    }
}
