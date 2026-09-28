package com.timelordtty.mydca.widget

import com.timelordtty.mydca.widget.WidgetNavigationArbiter.Chosen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三个一次性系统入口的确定优先级：桌面小组件 > 外部分享 > 通知候选。
 */
class WidgetNavigationArbiterTest {

    @Test
    fun widgetTargetAlwaysWinsAndTakesOverTheOtherTargets() {
        assertEquals(
            Chosen.WIDGET,
            WidgetNavigationArbiter.arbitrate(
                hasWidgetTarget = true,
                hasShareTarget = true,
                hasNotificationTarget = true,
            ),
        )
        assertEquals(
            Chosen.WIDGET,
            WidgetNavigationArbiter.arbitrate(
                hasWidgetTarget = true,
                hasShareTarget = false,
                hasNotificationTarget = true,
            ),
        )
        assertEquals(
            Chosen.WIDGET,
            WidgetNavigationArbiter.arbitrate(
                hasWidgetTarget = true,
                hasShareTarget = true,
                hasNotificationTarget = false,
            ),
        )
        assertTrue(
            "桌面点击接管时必须清掉分享与通知目标",
            WidgetNavigationArbiter.takesOverShareAndNotification(),
        )
    }

    @Test
    fun externalShareKeepsItsV08PriorityOverNotificationCandidates() {
        assertEquals(
            Chosen.EXTERNAL_SHARE,
            WidgetNavigationArbiter.arbitrate(
                hasWidgetTarget = false,
                hasShareTarget = true,
                hasNotificationTarget = true,
            ),
        )
        assertEquals(
            Chosen.EXTERNAL_SHARE,
            WidgetNavigationArbiter.arbitrate(
                hasWidgetTarget = false,
                hasShareTarget = true,
                hasNotificationTarget = false,
            ),
        )
    }

    @Test
    fun notificationCandidateAppliesOnlyWhenNothingElseIsPending() {
        assertEquals(
            Chosen.NOTIFICATION_CANDIDATE,
            WidgetNavigationArbiter.arbitrate(
                hasWidgetTarget = false,
                hasShareTarget = false,
                hasNotificationTarget = true,
            ),
        )
    }

    @Test
    fun noPendingTargetMeansNoNavigationAtAll() {
        assertEquals(
            Chosen.NONE,
            WidgetNavigationArbiter.arbitrate(
                hasWidgetTarget = false,
                hasShareTarget = false,
                hasNotificationTarget = false,
            ),
        )
    }
}
