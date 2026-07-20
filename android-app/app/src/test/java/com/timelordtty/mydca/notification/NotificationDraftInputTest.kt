package com.timelordtty.mydca.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDraftInputTest {
    @Test
    fun rawInputUsesOnlySanitizedCandidateFields() {
        val candidate = paymentCandidate()

        val rawInput = NotificationDraftInput.buildRawInput(candidate)

        assertTrue(rawInput.contains("来源：微信"))
        assertTrue(rawInput.contains("摘要：你已成功付款"))
        assertTrue(rawInput.contains("金额：18.80 元"))
        assertFalse(rawInput.contains("完整通知原文"))
    }

    @Test
    fun paymentCandidateWithAmountCanCreateDraftWhenApiReady() {
        val candidate = paymentCandidate()

        assertTrue(
            NotificationDraftInput.canCreateDraft(
                candidate = candidate,
                apiAvailable = true,
                createdDraftId = null,
            ),
        )
    }

    @Test
    fun normalChatCannotCreateDraft() {
        val candidate = paymentCandidate(isPaymentCandidate = false)

        assertFalse(NotificationDraftInput.canCreateDraft(candidate, apiAvailable = true, createdDraftId = null))
    }

    @Test
    fun missingOrInvalidAmountCannotCreateDraft() {
        assertFalse(NotificationDraftInput.canCreateDraft(paymentCandidate(amount = null), true, null))
        assertFalse(NotificationDraftInput.canCreateDraft(paymentCandidate(amount = "十八元"), true, null))
    }

    @Test
    fun createdDraftPreventsDuplicateDraftCreation() {
        val candidate = paymentCandidate()

        assertFalse(NotificationDraftInput.canCreateDraft(candidate, apiAvailable = true, createdDraftId = 42L))
    }

    private fun paymentCandidate(
        isPaymentCandidate: Boolean = true,
        amount: String? = "18.80",
    ): NotificationCandidate {
        return NotificationCandidate(
            id = "candidate-1",
            packageName = "com.tencent.mm",
            appLabel = "微信",
            postedAt = 1L,
            titleSnippet = "微信支付",
            textSnippet = "你已成功付款",
            isPaymentCandidate = isPaymentCandidate,
            amount = amount,
            sourceHint = "微信",
        )
    }
}
