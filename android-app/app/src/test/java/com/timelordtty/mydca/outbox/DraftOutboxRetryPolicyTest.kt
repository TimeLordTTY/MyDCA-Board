package com.timelordtty.mydca.outbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftOutboxRetryPolicyTest {
    private val policy = DraftOutboxRetryPolicy()

    @Test
    fun backoffGrowsAndIsCapped() {
        assertEquals(FIXED_NOW + 30_000L, policy.nextRetryAt(FIXED_NOW, failedAttempts = 1))
        assertEquals(FIXED_NOW + 120_000L, policy.nextRetryAt(FIXED_NOW, failedAttempts = 2))
        assertEquals(FIXED_NOW + 600_000L, policy.nextRetryAt(FIXED_NOW, failedAttempts = 3))
        assertEquals(FIXED_NOW + 1_800_000L, policy.nextRetryAt(FIXED_NOW, failedAttempts = 4))
        assertEquals(FIXED_NOW + 1_800_000L, policy.nextRetryAt(FIXED_NOW, failedAttempts = 9))
    }

    @Test
    fun automaticRetryStopsAfterTheAttemptLimit() {
        assertTrue(policy.isAutoRetryAllowed(0))
        assertTrue(policy.isAutoRetryAllowed(policy.maxAutoRetryAttempts - 1))
        assertFalse(policy.isAutoRetryAllowed(policy.maxAutoRetryAttempts))
    }

    @Test
    fun onlyPendingAndElapsedEntriesAreDue() {
        val entry = DraftOutboxEntry(
            id = "entry-1",
            sourceType = "APP_FORM",
            sourceRef = "android-ocr-request-1",
            origin = DraftOutboxOrigin.OCR,
            summary = "早餐 18 元",
            intent = accountingIntent(),
            createdAt = FIXED_NOW,
            nextRetryAt = FIXED_NOW + 30_000L,
        )

        assertFalse(policy.isDue(entry, FIXED_NOW))
        assertTrue(policy.isDue(entry, FIXED_NOW + 30_000L))
        assertFalse(policy.isDue(entry.copy(status = DraftOutboxStatus.AUTH_PAUSED), FIXED_NOW + 60_000L))
        assertFalse(policy.isDue(entry.copy(status = DraftOutboxStatus.BLOCKED), FIXED_NOW + 60_000L))
        assertFalse(
            policy.isDue(
                entry.copy(retryCount = policy.maxAutoRetryAttempts, status = DraftOutboxStatus.PENDING),
                FIXED_NOW + 60_000L,
            ),
        )
    }
}
