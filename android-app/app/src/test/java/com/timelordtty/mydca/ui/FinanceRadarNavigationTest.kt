package com.timelordtty.mydca.ui

import com.timelordtty.mydca.ui.screens.countText
import com.timelordtty.mydca.ui.screens.radarMoney
import com.timelordtty.mydca.ui.screens.statusText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceRadarNavigationTest {
    @Test fun destinationsOnlyOpenExistingReadOnlyEntryPages() {
        assertEquals(AppRoute.Drafts, FinanceRadarDestination.DRAFTS.navigation().route)
        assertEquals(AppRoute.Drafts, FinanceRadarDestination.OUTBOX.navigation().route)
        assertTrue(FinanceRadarDestination.OUTBOX.navigation().focusOutbox)
        assertEquals(AppRoute.TodayTodo, FinanceRadarDestination.SETTLEMENTS.navigation().route)
        assertTrue(FinanceRadarDestination.SETTLEMENTS.navigation().openSettlements)
        assertEquals(AppRoute.Accounts, FinanceRadarDestination.ASSETS.navigation().route)
        assertFalse(FinanceRadarDestination.ASSETS.navigation().openSettlements)
        assertEquals(4, FinanceRadarDestination.entries.size)
    }

    @Test fun unknownFactsNeverLookLikeZero() {
        assertEquals("未知", countText(null))
        assertEquals("未知", radarMoney(null))
        assertEquals("未知", statusText("UNKNOWN"))
        assertEquals("过期或需关注", statusText("WARNING"))
        assertEquals("0", countText(0))
    }
}
