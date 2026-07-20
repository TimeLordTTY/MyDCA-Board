package com.timelordtty.mydca.notification

enum class NotificationCandidateStatus {
    NEW,
    DRAFT_CREATED,
    DISMISSED,
}

/** 仅包含脱敏、截断后字段的本地支付通知候选。 */
data class NotificationCandidate(
    val id: String,
    val fingerprint: String = id,
    val packageName: String,
    val appLabel: String? = null,
    val postedAt: Long,
    val titleSnippet: String? = null,
    val textSnippet: String? = null,
    val isPaymentCandidate: Boolean = true,
    val amount: String? = null,
    val sourceHint: String? = null,
    val status: NotificationCandidateStatus = NotificationCandidateStatus.NEW,
    val createdDraftId: Long? = null,
    val reminderSent: Boolean = false,
)
