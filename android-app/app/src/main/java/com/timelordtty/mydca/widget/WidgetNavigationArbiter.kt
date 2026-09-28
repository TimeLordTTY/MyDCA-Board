package com.timelordtty.mydca.widget

/**
 * 三个一次性系统入口（桌面小组件 / 外部分享 / 通知候选）同时存在时的确定优先级。
 *
 * 规则固定且可测试：桌面小组件点击优先接管，其次是分享目标，最后才是通知候选；
 * 被接管的目标必须立即清空，保证同一次点击不会先跳目标页、再被残留目标二次跳转。
 */
object WidgetNavigationArbiter {
    enum class Chosen {
        WIDGET,
        EXTERNAL_SHARE,
        NOTIFICATION_CANDIDATE,
        NONE,
    }

    fun arbitrate(
        hasWidgetTarget: Boolean,
        hasShareTarget: Boolean,
        hasNotificationTarget: Boolean,
    ): Chosen = when {
        hasWidgetTarget -> Chosen.WIDGET
        hasShareTarget -> Chosen.EXTERNAL_SHARE
        hasNotificationTarget -> Chosen.NOTIFICATION_CANDIDATE
        else -> Chosen.NONE
    }

    /** 桌面点击接管时必须清掉分享与通知目标，避免 v0.7 / v0.8 的一次性状态残留。 */
    fun takesOverShareAndNotification(): Boolean = true

    /** 用户主动切换底部导航时必须清空一次性小组件目标，避免返回后错误跳页。 */
    fun clearsWidgetTargetOnManualNavigation(): Boolean = true
}
