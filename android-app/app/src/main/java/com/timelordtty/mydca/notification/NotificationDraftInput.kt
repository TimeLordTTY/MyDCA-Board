package com.timelordtty.mydca.notification

object NotificationDraftInput {
    /** 队列与 UI 展示用的脱敏摘要上限，避免把长文本带进本地队列。 */
    const val SUMMARY_MAX_LENGTH = 48

    fun canCreateDraft(
        candidate: NotificationCandidate,
        apiAvailable: Boolean,
        createdDraftId: Long?,
    ): Boolean {
        return candidate.isPaymentCandidate &&
            amountValue(candidate) != null &&
            apiAvailable &&
            createdDraftId == null
    }

    fun amountValue(candidate: NotificationCandidate): Double? {
        return candidate.amount?.toDoubleOrNull()
    }

    fun buildRawInput(candidate: NotificationCandidate): String {
        return listOfNotNull(
            candidate.sourceHint?.let { "来源：$it" },
            candidate.appLabel?.let { "应用：$it" },
            candidate.titleSnippet?.let { "标题：$it" },
            candidate.textSnippet?.let { "摘要：$it" },
            candidate.amount?.let { "金额：$it 元" },
        ).joinToString("；")
    }

    /** 本地队列摘要：只由已脱敏的候选片段拼成，不包含完整通知原文。 */
    fun summary(candidate: NotificationCandidate): String {
        return listOfNotNull(
            candidate.sourceHint ?: candidate.appLabel,
            candidate.amount?.let { "$it 元" },
            candidate.textSnippet ?: candidate.titleSnippet,
        ).joinToString(" · ").trim().take(SUMMARY_MAX_LENGTH)
    }
}
