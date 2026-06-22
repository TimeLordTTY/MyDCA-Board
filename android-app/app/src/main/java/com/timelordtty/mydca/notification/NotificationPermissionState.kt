package com.timelordtty.mydca.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

/**
 * 通知监听权限状态工具。
 * 只检查系统授权状态和打开系统设置页，不申请无关敏感权限。
 */
object NotificationPermissionState {
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val component = ComponentName(context, MyDcaNotificationListenerService::class.java)
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledListeners)
        while (splitter.hasNext()) {
            if (ComponentName.unflattenFromString(splitter.next()) == component) {
                return true
            }
        }
        return false
    }

    fun notificationListenerSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
}
