package com.timelordtty.mydca.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.timelordtty.mydca.MainActivity
import com.timelordtty.mydca.R

class MyDcaNotificationListenerService : NotificationListenerService() {
    override fun onCreate() {
        super.onCreate()
        NotificationCandidateStore.initialize(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0 || notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val title = notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = notification.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val appLabel = resolveAppLabel(sbn.packageName)
        val parsed = PaymentNotificationParser.parse(sbn.packageName, appLabel, title, text)
        val amount = parsed.amount ?: return
        if (!parsed.isPaymentCandidate) return

        val fingerprint = PaymentNotificationParser.fingerprint(sbn.packageName, amount, title, text, sbn.postTime)
        val candidate = NotificationCandidate(
            id = fingerprint,
            fingerprint = fingerprint,
            packageName = sbn.packageName,
            appLabel = appLabel,
            postedAt = sbn.postTime,
            titleSnippet = PaymentNotificationParser.sanitizeSnippet(title),
            textSnippet = PaymentNotificationParser.sanitizeSnippet(text),
            amount = amount,
            sourceHint = parsed.sourceHint,
        )
        if (NotificationCandidateStore.add(candidate)) {
            sendLocalReminder(candidate)
        }
    }

    private fun sendLocalReminder(candidate: NotificationCandidate) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = getSystemService(NotificationManager::class.java)
        val channelId = "payment_candidates"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(channelId, "支付通知候选", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(NotificationNavigationTarget.EXTRA_CANDIDATE_ID, candidate.id)
        }
        val pendingIntent = PendingIntent.getActivity(this, candidate.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val reminder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("发现一条支付通知候选")
            .setContentText("${candidate.sourceHint ?: "支付应用"} · ${candidate.amount} 元，请手动核对后生成草稿")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(candidate.id.hashCode(), reminder)
        NotificationCandidateStore.markReminderSent(candidate.id)
    }

    private fun resolveAppLabel(packageName: String): String? = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()
}
