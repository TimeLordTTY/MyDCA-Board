package com.timelordtty.mydca.ui

import com.timelordtty.mydca.notification.NotificationCandidate
import com.timelordtty.mydca.notification.NotificationCandidateStatus
import com.timelordtty.mydca.outbox.DraftOutboxEntry
import com.timelordtty.mydca.ui.screens.OcrEntryMode

/**
 * 快速记账采集中心里的四个入口。
 *
 * 入口只负责把用户带到既有采集位置，本身不发起任何网络写入：
 * 没有 parse / draft / preview / confirm / QuickEntry，也没有正式入账能力。
 */
enum class QuickCaptureAction(
    val title: String,
    val description: String,
) {
    ManualText(
        title = "手工记一笔",
        description = "输入一句话，例如“早餐 18 元 微信支付”，再由你确认解析。",
    ),
    ImageOcr(
        title = "图片识别",
        description = "用系统相册选图，在本机识别文字，图片不会上传。",
    ),
    NotificationCandidates(
        title = "支付通知候选",
        description = "查看本机脱敏候选，只有你手动点击才会生成草稿。",
    ),
    OutboxRetry(
        title = "待重试",
        description = "创建草稿失败的采集已加密暂存，只会重试创建草稿。",
    ),
}

/** 入口右侧的数量提示；没有待处理时也要能看到“暂无待处理”，而不是隐藏入口。 */
data class QuickCaptureBadge(val count: Int) {
    val hasPending: Boolean get() = count > 0

    val text: String get() = if (hasPending) count.toString() + " 待处理" else "暂无待处理"
}

/** 面板条目 = 入口标题 + 一句说明 + 可选数量。 */
data class QuickCaptureEntry(
    val action: QuickCaptureAction,
    val badge: QuickCaptureBadge? = null,
)

/**
 * 一次性“定位”信号：用户从快速面板进入通知候选 / 待重试区域时，对应页面给出醒目标记。
 * 离开页面或主动切换导航即复位，避免返回后错误高亮。
 */
data class QuickCaptureFocus(
    val notificationCandidates: Boolean = false,
    val outbox: Boolean = false,
) {
    companion object {
        val NONE = QuickCaptureFocus()
    }
}

/**
 * 点击入口后的确定性导航决策。
 *
 * 所有入口都会清空旧的草稿选中与候选选中，保证不会把上次的 draftId / candidateId 带进新流程。
 */
data class QuickCaptureDecision(
    val route: AppRoute,
    val entryMode: OcrEntryMode? = null,
    val focus: QuickCaptureFocus = QuickCaptureFocus.NONE,
    val clearSelectedDraftId: Boolean = true,
    val clearSelectedCandidateId: Boolean = true,
)

/** 只读数量统计；候选数只来自本机脱敏候选，重试数只来自加密本地队列。 */
object QuickCaptureCounts {
    fun pendingCandidates(candidates: List<NotificationCandidate>): Int =
        candidates.count { it.status != NotificationCandidateStatus.DISMISSED }

    fun outboxRetries(entries: List<DraftOutboxEntry>): Int = entries.size
}

/**
 * 快速记账面板的纯逻辑：入口可见规则、面板条目与数量徽标、点击后的路由决策与状态复位。
 *
 * 抽成纯 Kotlin 便于单元测试，不引入新的导航框架。
 */
object QuickCaptureHub {
    const val PANEL_TITLE = "快速记账"
    const val PANEL_DESCRIPTION = "四个入口都只走到草稿为止：不会自动解析、不会自动生成草稿、不会自动确认入账。"

    /**
     * 悬浮入口可见规则：
     * - 设置页布局不适合放悬浮入口，保持一致隐藏；
     * - 已经进入手工 / 图片录入子页面时隐藏，避免打扰当前的采集流程。
     */
    fun isEntryVisibleOn(route: AppRoute, isSubFlowOpen: Boolean = false): Boolean =
        route != AppRoute.Settings && !isSubFlowOpen

    fun entries(
        pendingCandidateCount: Int,
        outboxCount: Int,
    ): List<QuickCaptureEntry> = listOf(
        QuickCaptureEntry(QuickCaptureAction.ManualText),
        QuickCaptureEntry(QuickCaptureAction.ImageOcr),
        QuickCaptureEntry(
            action = QuickCaptureAction.NotificationCandidates,
            badge = QuickCaptureBadge(pendingCandidateCount.coerceAtLeast(0)),
        ),
        QuickCaptureEntry(
            action = QuickCaptureAction.OutboxRetry,
            badge = QuickCaptureBadge(outboxCount.coerceAtLeast(0)),
        ),
    )

    fun decide(action: QuickCaptureAction): QuickCaptureDecision = when (action) {
        QuickCaptureAction.ManualText -> QuickCaptureDecision(
            route = AppRoute.Drafts,
            entryMode = OcrEntryMode.ManualText,
        )
        QuickCaptureAction.ImageOcr -> QuickCaptureDecision(
            route = AppRoute.Drafts,
            entryMode = OcrEntryMode.Image,
        )
        QuickCaptureAction.NotificationCandidates -> QuickCaptureDecision(
            route = AppRoute.TodayTodo,
            focus = QuickCaptureFocus(notificationCandidates = true),
        )
        QuickCaptureAction.OutboxRetry -> QuickCaptureDecision(
            route = AppRoute.Drafts,
            focus = QuickCaptureFocus(outbox = true),
        )
    }

    /**
     * 打开面板、关闭面板都不产生导航决策：关闭后仍停留在打开面板前的页面。
     * 返回 null 表示“不改路由、不改选中状态”。
     */
    fun onPanelOpen(): QuickCaptureDecision? = null

    fun onPanelDismiss(): QuickCaptureDecision? = null

    /**
     * 用户主动切换底部导航时复位一次性定位，避免返回后错误跳页或残留高亮。
     * 任何显式导航都放弃之前由面板产生的定位，因此与目标页面无关。
     */
    fun focusAfterManualNavigation(): QuickCaptureFocus = QuickCaptureFocus.NONE
}
