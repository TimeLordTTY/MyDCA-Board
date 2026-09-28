package com.timelordtty.mydca.widget

/**
 * 桌面小组件尺寸自适应规则（纯 Kotlin）。
 *
 * 完整尺寸展示四个入口；空间不足时折叠为“记一笔 + 草稿箱”两个主入口，
 * 保证任何摆放尺寸下都不会出现挤压到无法点击的按钮。
 */
object WidgetSizePolicy {
    const val FULL_MIN_WIDTH_DP = 180
    const val FULL_MIN_HEIGHT_DP = 110

    /**
     * 未知尺寸（<= 0，例如刚添加、系统还没给出 options）按完整尺寸处理，
     * 只有明确量到不够时才折叠，避免大尺寸控件一开始就退化成两个入口。
     */
    fun isCompact(minWidthDp: Int, minHeightDp: Int): Boolean {
        if (minWidthDp <= 0 || minHeightDp <= 0) return false
        return minWidthDp < FULL_MIN_WIDTH_DP || minHeightDp < FULL_MIN_HEIGHT_DP
    }

    fun entriesFor(minWidthDp: Int, minHeightDp: Int): List<WidgetNavigationTarget> =
        WidgetEntryPoints.entriesFor(isCompact(minWidthDp, minHeightDp))
}
