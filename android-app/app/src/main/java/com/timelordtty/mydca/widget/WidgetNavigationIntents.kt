package com.timelordtty.mydca.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.timelordtty.mydca.MainActivity

/**
 * 小组件点击 Intent 的唯一构造点（Android 侧）。
 *
 * - 只构造指向本 App [MainActivity] 的显式组件 Intent，action 只能取自 [WidgetNavigationTarget]；
 * - 不写入任何 extra，因此小组件 Intent 里不会出现账户、草稿、通知原文、图片 URI、Token 或 Cookie；
 * - 每个入口使用独立 requestCode 与独立 action，避免四个 PendingIntent 被系统复用成同一个；
 * - `FLAG_IMMUTABLE` 让接收方无法改写 Intent，`FLAG_UPDATE_CURRENT` 让 action 变更被正确更新；
 * - `NEW_TASK | CLEAR_TOP | SINGLE_TOP` 复用已有任务，`onCreate` / `onNewIntent` 都会走同一套解析。
 */
object WidgetNavigationIntents {
    fun forTarget(context: Context, target: WidgetNavigationTarget): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(target.action)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )

    fun pendingIntentFor(context: Context, target: WidgetNavigationTarget): PendingIntent =
        PendingIntent.getActivity(
            context,
            target.requestCode,
            forTarget(context, target),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
