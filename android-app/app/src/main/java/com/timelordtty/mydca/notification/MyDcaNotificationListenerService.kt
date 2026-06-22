package com.timelordtty.mydca.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * MyDCA 通知监听基础服务。
 * 首版只生成本地候选事件，不持久化通知原文、不打印日志、不调用后端创建草稿。
 */
class MyDcaNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        if (shouldSkipNotification(notification)) return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extractText(notification)
        val appLabel = resolveAppLabel(sbn.packageName)
        val parsed = PaymentNotificationParser.parse(
            packageName = sbn.packageName,
            appLabel = appLabel,
            title = title,
            text = text,
        )
        val rawText = listOfNotNull(title, text).joinToString(" ").ifBlank { null }
        val candidate = NotificationCandidate(
            id = "${sbn.packageName}:${sbn.postTime}:${sbn.id}",
            packageName = sbn.packageName,
            appLabel = appLabel,
            postedAt = sbn.postTime,
            titleSnippet = sanitizeSnippet(title),
            textSnippet = sanitizeSnippet(text),
            isPaymentCandidate = parsed.isPaymentCandidate,
            amount = parsed.amount,
            sourceHint = parsed.sourceHint,
            rawText = rawText,
        )
        NotificationCandidateStore.add(candidate)
    }

    private fun shouldSkipNotification(notification: Notification): Boolean {
        val flags = notification.flags
        return flags and Notification.FLAG_ONGOING_EVENT != 0 ||
            flags and Notification.FLAG_GROUP_SUMMARY != 0
    }

    private fun extractText(notification: Notification): String? {
        val extras = notification.extras ?: return null
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        return bigText ?: text
    }

    private fun resolveAppLabel(packageName: String): String? {
        return runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrNull()
    }

    private fun sanitizeSnippet(value: String?): String? {
        val compact = value
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
        if (compact.isBlank()) return null
        return if (compact.length <= 40) compact else compact.take(40) + "…"
    }
}
