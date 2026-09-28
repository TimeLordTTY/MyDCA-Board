package com.timelordtty.mydca.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews
import com.timelordtty.mydca.R

/**
 * 桌面快速记账小组件。
 *
 * 只做两件事：按系统给出的尺寸渲染静态中文入口，并为每个入口绑定一个显式 Intent 的 PendingIntent。
 *
 * 明确没有的能力：不连接网络、不查询后端、不读取真实账本数据、不写任何数据、
 * 不解析 / 不识别图片 / 不创建草稿 / 不预览 / 不确认；没有后台轮询、Alarm、WorkManager 或常驻通知。
 * 推送给桌面的只有静态入口文案，不含余额、持仓、通知候选原文等隐私数据，也不需要实时刷新。
 */
class QuickCaptureWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(
                appWidgetId,
                remoteViewsFor(context, appWidgetManager.getAppWidgetOptions(appWidgetId)),
            )
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        appWidgetManager.updateAppWidget(appWidgetId, remoteViewsFor(context, newOptions))
    }

    /** 按可用尺寸选择完整 / 紧凑布局，并绑定对应入口的点击目标。 */
    fun remoteViewsFor(context: Context, options: Bundle?): RemoteViews {
        val minWidthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val minHeightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
        val compact = WidgetSizePolicy.isCompact(minWidthDp, minHeightDp)
        val layoutId = if (compact) {
            R.layout.widget_quick_capture_compact
        } else {
            R.layout.widget_quick_capture
        }
        val views = RemoteViews(context.packageName, layoutId)
        WidgetEntryPoints.entriesFor(compact).forEach { target ->
            val viewId = WidgetEntryViews.idOf(target)
            views.setTextViewText(viewId, target.label)
            views.setContentDescription(viewId, target.description)
            views.setOnClickPendingIntent(
                viewId,
                WidgetNavigationIntents.pendingIntentFor(context, target),
            )
        }
        return views
    }
}
