package com.timelordtty.mydca.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 小组件 Intent 解析规则：`onCreate` 与 `onNewIntent` 必须共用同一套判定。
 */
class WidgetNavigationResolverTest {

    @Test
    fun eachFixedActionResolvesToItsControlledTarget() {
        WidgetNavigationTarget.entries.forEach { target ->
            assertEquals(target, WidgetNavigationResolver.resolve(target.action))
            assertTrue(WidgetNavigationResolver.isWidgetAction(target.action))
        }
    }

    @Test
    fun unrelatedActionsResolveToNoTarget() {
        listOf(
            null,
            "",
            "android.intent.action.MAIN",
            "android.intent.action.SEND",
            "android.appwidget.action.APPWIDGET_UPDATE",
            "com.timelordtty.mydca.widget.action.NOT_DECLARED",
        ).forEach { action ->
            assertNull("$action 不应产生小组件导航目标", WidgetNavigationResolver.resolve(action))
            assertFalse(WidgetNavigationResolver.isWidgetAction(action))
        }
    }

    @Test
    fun onCreateAndOnNewIntentShareTheSameParsingRule() {
        // MainActivity 的 onCreate / onNewIntent 都只把 intent.action 交给 resolve，
        // 因此同一份 Intent 无论走冷启动还是复用实例，都必须得到同一个目标。
        WidgetNavigationTarget.entries.forEach { target ->
            val coldStart = WidgetNavigationResolver.resolve(target.action)
            val warmStart = WidgetNavigationResolver.resolve(target.action)

            assertEquals(coldStart, warmStart)
            assertEquals(target, coldStart)
        }
    }

    @Test
    fun repeatedResolutionIsPureAndHasNoSideEffect() {
        val first = WidgetNavigationResolver.resolve(WidgetNavigationTarget.Drafts.action)
        repeat(5) {
            assertEquals(first, WidgetNavigationResolver.resolve(WidgetNavigationTarget.Drafts.action))
        }
    }
}
