package com.timelordtty.mydca.widget

import com.timelordtty.mydca.ui.AppRoute
import com.timelordtty.mydca.ui.QuickCaptureAction
import com.timelordtty.mydca.ui.QuickCaptureFocus
import com.timelordtty.mydca.ui.QuickCaptureHub
import com.timelordtty.mydca.ui.screens.OcrEntryMode

/**
 * 小组件点击后的确定性导航决策。
 *
 * [route] 为 null 表示“保持当前页面”：`记一笔` 只展开既有快速面板，不强行跳页。
 * 决策只描述“去哪个既有页面”，类型上不存在解析 / 草稿 / 预览 / 确认能力。
 */
data class WidgetNavigationDecision(
    val route: AppRoute?,
    val entryMode: OcrEntryMode? = null,
    val focus: QuickCaptureFocus = QuickCaptureFocus.NONE,
    val openQuickCapturePanel: Boolean = false,
    val clearSelectedDraftId: Boolean = true,
    val clearSelectedCandidateId: Boolean = true,
)

/**
 * 桌面入口到既有页面的纯映射。
 *
 * `手工记账` / `图片识别` 直接复用 v0.7 快速面板的决策，避免两处导航规则漂移；
 * 四个目标都只落在既有的 DRAFT 采集边界之内，自动解析 / 建档 / 预览 / 确认都不在这里。
 */
object WidgetNavigationHub {
    fun decide(target: WidgetNavigationTarget): WidgetNavigationDecision = when (target) {
        WidgetNavigationTarget.QuickCapture -> WidgetNavigationDecision(
            route = null,
            openQuickCapturePanel = true,
        )
        WidgetNavigationTarget.ManualText -> fromQuickCapture(QuickCaptureAction.ManualText)
        WidgetNavigationTarget.ImageOcr -> fromQuickCapture(QuickCaptureAction.ImageOcr)
        WidgetNavigationTarget.Drafts -> WidgetNavigationDecision(route = AppRoute.Drafts)
    }

    private fun fromQuickCapture(action: QuickCaptureAction): WidgetNavigationDecision {
        val decision = QuickCaptureHub.decide(action)
        return WidgetNavigationDecision(
            route = decision.route,
            entryMode = decision.entryMode,
            focus = decision.focus,
            clearSelectedDraftId = decision.clearSelectedDraftId,
            clearSelectedCandidateId = decision.clearSelectedCandidateId,
        )
    }
}
