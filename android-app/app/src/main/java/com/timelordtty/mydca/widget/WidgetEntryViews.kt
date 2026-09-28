package com.timelordtty.mydca.widget

import com.timelordtty.mydca.R

/**
 * 入口与布局控件 id 的唯一对应关系。
 *
 * [nameOf] 与资源 id 一一对应：静态契约测试用 [nameOf] 去两个布局里核对控件确实存在，
 * 因此“绑定点击目标的 id”与“布局里真正包含的 id”不会悄悄漂移。
 */
object WidgetEntryViews {
    fun nameOf(target: WidgetNavigationTarget): String = when (target) {
        WidgetNavigationTarget.QuickCapture -> "widget_entry_quick_capture"
        WidgetNavigationTarget.ManualText -> "widget_entry_manual_text"
        WidgetNavigationTarget.ImageOcr -> "widget_entry_image_ocr"
        WidgetNavigationTarget.Drafts -> "widget_entry_drafts"
    }

    fun idOf(target: WidgetNavigationTarget): Int = when (target) {
        WidgetNavigationTarget.QuickCapture -> R.id.widget_entry_quick_capture
        WidgetNavigationTarget.ManualText -> R.id.widget_entry_manual_text
        WidgetNavigationTarget.ImageOcr -> R.id.widget_entry_image_ocr
        WidgetNavigationTarget.Drafts -> R.id.widget_entry_drafts
    }
}
