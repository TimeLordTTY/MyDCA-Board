package com.timelordtty.mydca.notification

import org.junit.Assert.*
import org.junit.Test

class NotificationCandidatePolicyTest {
    @Test fun removesExpiredDeduplicatesAndKeepsFifty() {
        val now = NotificationCandidatePolicy.RETENTION_MILLIS + 1_000
        val items = (0..55).map { candidate("id-$it", now - it) } +
            candidate("duplicate", now, fingerprint = "id-0") +
            candidate("expired", 0)
        val result = NotificationCandidatePolicy.normalize(items, now)
        assertEquals(50, result.size)
        assertEquals(result.size, result.map { it.fingerprint }.distinct().size)
        assertFalse(result.any { it.id == "expired" })
    }

    @Test fun candidateStateKeepsDraftIdAndReminderFlag() {
        val candidate = candidate("one", 1).copy(status = NotificationCandidateStatus.DRAFT_CREATED, createdDraftId = 42, reminderSent = true)
        assertEquals(NotificationCandidateStatus.DRAFT_CREATED, candidate.status)
        assertEquals(42L, candidate.createdDraftId)
        assertTrue(candidate.reminderSent)
    }

    private fun candidate(id: String, postedAt: Long, fingerprint: String = id) = NotificationCandidate(
        id = id, fingerprint = fingerprint, packageName = "com.tencent.mm", postedAt = postedAt, amount = "1.00",
    )
}
