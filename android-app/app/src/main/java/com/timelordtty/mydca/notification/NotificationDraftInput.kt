package com.timelordtty.mydca.notification

object NotificationDraftInput {
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
}
