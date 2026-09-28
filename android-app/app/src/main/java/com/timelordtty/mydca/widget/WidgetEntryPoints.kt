package com.timelordtty.mydca.widget

/**
 * 小组件入口的展示顺序（纯逻辑，不涉及 Android 资源 id）。
 *
 * 完整尺寸必须提供四个入口；紧凑尺寸只保留“记一笔 + 草稿箱”两个主入口。
 */
object WidgetEntryPoints {
    val FULL: List<WidgetNavigationTarget> = listOf(
        WidgetNavigationTarget.QuickCapture,
        WidgetNavigationTarget.ManualText,
        WidgetNavigationTarget.ImageOcr,
        WidgetNavigationTarget.Drafts,
    )

    val COMPACT: List<WidgetNavigationTarget> = listOf(
        WidgetNavigationTarget.QuickCapture,
        WidgetNavigationTarget.Drafts,
    )

    fun entriesFor(compact: Boolean): List<WidgetNavigationTarget> = if (compact) COMPACT else FULL
}
