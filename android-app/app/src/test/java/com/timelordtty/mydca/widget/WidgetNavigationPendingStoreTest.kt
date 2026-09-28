package com.timelordtty.mydca.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一次性小组件目标：只消费一次、未登录保留、主动导航后可清空。
 */
class WidgetNavigationPendingStoreTest {

    @Test
    fun publishedTargetIsConsumedExactlyOnce() {
        val store = WidgetNavigationPendingStore()

        val published = store.publish(WidgetNavigationTarget.Drafts)
        assertNotNull(published)
        assertEquals(WidgetNavigationTarget.Drafts, store.pending.value?.target)

        val consumed = store.consume()
        assertEquals(WidgetNavigationTarget.Drafts, consumed?.target)
        assertNull("同一个目标只能消费一次", store.consume())
        assertNull("消费后必须立即清空 pending", store.pending.value)
    }

    @Test
    fun repeatedClicksOnTheSameEntryAreDistinctEvents() {
        val store = WidgetNavigationPendingStore()

        val first = store.publish(WidgetNavigationTarget.QuickCapture)
        store.consume()
        val second = store.publish(WidgetNavigationTarget.QuickCapture)

        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals("连续两次点击必须是两个事件", first?.token, second?.token)
        assertTrue("token 必须单调递增", (second?.token ?: 0L) > (first?.token ?: 0L))
    }

    @Test
    fun nonWidgetIntentDoesNotOverwriteAPendingTarget() {
        val store = WidgetNavigationPendingStore()
        store.publish(WidgetNavigationTarget.ImageOcr)

        assertNull("普通启动 / 分享 Intent 不得覆盖待消费目标", store.publish(null))
        assertEquals(WidgetNavigationTarget.ImageOcr, store.pending.value?.target)
        assertEquals(WidgetNavigationTarget.ImageOcr, store.consume()?.target)
    }

    @Test
    fun pendingTargetSurvivesUntilLoginAndIsThenConsumedOnce() {
        val store = WidgetNavigationPendingStore()
        store.publish(WidgetNavigationTarget.ManualText)

        // 未登录期间：登录页不消费目标，只做会话恢复，pending 必须原样保留。
        repeat(3) { assertNotNull(store.pending.value) }

        // 登录成功后：只消费一次，随后不再残留。
        assertEquals(WidgetNavigationTarget.ManualText, store.consume()?.target)
        assertNull(store.pending.value)
        assertNull(store.consume())
    }

    @Test
    fun manualNavigationClearsThePendingTarget() {
        val store = WidgetNavigationPendingStore()
        store.publish(WidgetNavigationTarget.Drafts)

        store.clear()

        assertNull(store.pending.value)
        assertNull(store.consume())
        assertTrue(WidgetNavigationArbiter.clearsWidgetTargetOnManualNavigation())
    }

    @Test
    fun newerClickReplacesTheOlderTargetInsteadOfStacking() {
        val store = WidgetNavigationPendingStore()
        store.publish(WidgetNavigationTarget.ManualText)
        store.publish(WidgetNavigationTarget.ImageOcr)

        assertEquals(WidgetNavigationTarget.ImageOcr, store.consume()?.target)
        assertNull("旧目标不得残留成第二次跳转", store.pending.value)
    }
}
