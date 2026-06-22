package com.timelordtty.mydca.notification

/**
 * 本地候选通知事件。
 * rawText 仅保存在内存中，UI 默认展示脱敏摘要，不持久化完整通知原文。
 */
data class NotificationCandidate(
    val id: String,
    val packageName: String,
    val appLabel: String? = null,
    val postedAt: Long,
    val titleSnippet: String? = null,
    val textSnippet: String? = null,
    val isPaymentCandidate: Boolean = false,
    val amount: String? = null,
    val sourceHint: String? = null,
    val rawText: String? = null,
)
