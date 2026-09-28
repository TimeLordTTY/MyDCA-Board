package com.timelordtty.mydca.widget

/** 四个入口共用的固定 action 前缀；同时供枚举常量与 [WidgetNavigationTarget.ACTION_PREFIX] 使用。 */
private const val WIDGET_ACTION_PREFIX = "com.timelordtty.mydca.widget.action."

/**
 * 桌面小组件允许的四个受控导航目标。
 *
 * 只有这里列出的固定 action 会被接受：不接受任何外部 route 字符串，
 * 因此外部组件无法借小组件入口把 App 导航到枚举之外的位置。
 *
 * [action] 与 [requestCode] 一共同决定 PendingIntent 身份，两者在四个目标间都保持唯一。
 * [label] 是小组件按钮上真正显示的文案，[description] 只用作无障碍说明。
 */
enum class WidgetNavigationTarget(
    val action: String,
    val requestCode: Int,
    val label: String,
    val description: String,
) {
    QuickCapture(
        action = WIDGET_ACTION_PREFIX + "QUICK_CAPTURE",
        requestCode = 4201,
        label = "记一笔",
        description = "打开 App 并展开快速记账面板，之后的每一步都由你自己操作。",
    ),
    ManualText(
        action = WIDGET_ACTION_PREFIX + "MANUAL_TEXT",
        requestCode = 4202,
        label = "手工记账",
        description = "直接进入手工文本采集页，不会自动解析，也不会自动生成草稿。",
    ),
    ImageOcr(
        action = WIDGET_ACTION_PREFIX + "IMAGE_OCR",
        requestCode = 4203,
        label = "图片识别",
        description = "直接进入图片识别采集页，选图与识别都要你亲手触发。",
    ),
    Drafts(
        action = WIDGET_ACTION_PREFIX + "DRAFTS",
        requestCode = 4204,
        label = "草稿箱",
        description = "打开草稿箱查看草稿，确认入账仍然只能由你手动完成。",
    ),
    ;

    companion object {
        /** 四个入口共用的固定 action 前缀，避免与系统 action 或分享 action 混淆。 */
        const val ACTION_PREFIX = WIDGET_ACTION_PREFIX

        /** 未知 / 空 action（普通启动、外部分享等）一律安全忽略，不产生任何导航目标。 */
        fun fromAction(action: String?): WidgetNavigationTarget? =
            entries.firstOrNull { it.action == action }
    }
}
