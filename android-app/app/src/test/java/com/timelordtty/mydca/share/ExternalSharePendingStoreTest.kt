package com.timelordtty.mydca.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 进程内一次性 pending share 的语义：只消费一次、未登录期间保留、拒绝只留提示。
 */
class ExternalSharePendingStoreTest {

    @Test
    fun acceptedShareIsConsumedExactlyOnce() {
        val store = ExternalSharePendingStore()
        val published = store.publish(accepted(ExternalSharePayload.Text("早餐 18 元 微信支付")))

        assertNotNull(published)
        assertEquals(published, store.pending.value)

        assertEquals(published, store.consume())
        assertNull(store.pending.value)
        assertNull("同一个 payload 不能被消费两次", store.consume())
    }

    @Test
    fun pendingShareSurvivesUntilLoginAndIsThenConsumedOnce() {
        val store = ExternalSharePendingStore()
        store.publish(accepted(ExternalSharePayload.Text("打车 32.5 元 支付宝")))

        // 未登录阶段没有任何消费方：pending 必须原样保留，也不产生第二份副本。
        repeat(3) {
            assertNotNull("未登录时 pending 不应被清掉", store.pending.value)
        }

        assertNotNull("登录后应能消费到这一次分享", store.consume())
        assertNull("登录后同样只消费一次", store.consume())
        assertNull(store.pending.value)
    }

    @Test
    fun twoShareEventsStayDistinctEvenWithIdenticalContent() {
        val store = ExternalSharePendingStore()

        val first = store.publish(accepted(ExternalSharePayload.Text("同一句话 18 元")))
        val second = store.publish(accepted(ExternalSharePayload.Text("同一句话 18 元")))

        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals("不同分享事件必须有不同的 token", first!!.token, second!!.token)
        assertNotEquals("不同分享事件必须有不同的 sourceRef", first.sourceRef, second.sourceRef)
        assertEquals("后一次分享覆盖前一次，仍然只有一条 pending", second, store.pending.value)
    }

    @Test
    fun rejectedShareLeavesNoPayloadAndOnlyOneChineseNotice() {
        val store = ExternalSharePendingStore()

        val capture = store.publish(
            ExternalShareResolution.Rejected(ExternalShareRejection.UntrustedImageSource),
        )

        assertNull("被拒绝的分享不能产生 payload", capture)
        assertNull(store.pending.value)
        assertEquals(ExternalShareRejection.UntrustedImageSource.message, store.consumeNotice())
        assertNull("提示只消费一次", store.consumeNotice())
    }

    @Test
    fun truncatedShareSurfacesTruncationNoticeOnce() {
        val store = ExternalSharePendingStore()

        store.publish(
            ExternalShareResolution.Accepted(
                payload = ExternalSharePayload.Text("a".repeat(ExternalShareResolver.MAX_TEXT_LENGTH)),
                truncated = true,
            ),
        )

        assertEquals(ExternalSharePendingStore.TRUNCATED_NOTICE, store.consumeNotice())
        assertNull(store.consumeNotice())
    }

    @Test
    fun acceptedShareClearsAnyStaleNotice() {
        val store = ExternalSharePendingStore()
        store.publish(ExternalShareResolution.Rejected(ExternalShareRejection.MissingText))

        store.publish(accepted(ExternalSharePayload.Text("午餐 42 元")))

        assertNull("成功的分享不应残留上一次的拒绝提示", store.consumeNotice())
    }

    @Test
    fun clearDropsPendingWithoutLeakingItToTheNextCapture() {
        val store = ExternalSharePendingStore()
        store.publish(accepted(ExternalSharePayload.Text("早餐 18 元")))

        store.clear()

        assertNull(store.pending.value)
        assertNull(store.consume())
    }

    @Test
    fun shareSourceRefIsStablePrefixedAndFreeOfPayloadDetails() {
        val store = ExternalSharePendingStore()
        val secret = "午餐 42 元 微信支付 订单号 202609270001 https://example.com/pay"

        val textCapture = store.publish(accepted(ExternalSharePayload.Text(secret)))!!
        assertTrue(textCapture.sourceRef.startsWith(ExternalShareSourceRef.TEXT_PREFIX))
        assertTrue(
            "文本分享 sourceRef 必须是固定前缀 + UUID",
            textCapture.sourceRef.matches(
                Regex("^android-share-text-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"),
            ),
        )
        listOf("午餐", "微信支付", "订单号", "example.com", "202609270001").forEach { token ->
            assertFalse("sourceRef 不能包含分享原文：$token", textCapture.sourceRef.contains(token))
        }

        val imageCapture = store.publish(accepted(ExternalSharePayload.Image("content://com.example.provider/pay/9")))!!
        assertTrue(imageCapture.sourceRef.startsWith(ExternalShareSourceRef.IMAGE_PREFIX))
        assertTrue(
            "图片分享 sourceRef 必须是固定前缀 + UUID",
            imageCapture.sourceRef.matches(
                Regex("^android-share-image-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"),
            ),
        )
        listOf("content://", "provider", "pay/9").forEach { token ->
            assertFalse("sourceRef 不能包含图片 URI：$token", imageCapture.sourceRef.contains(token))
        }
    }

    @Test
    fun sourceRefStaysStableForOneCaptureEvenWhenAskedRepeatedly() {
        val store = ExternalSharePendingStore(sourceRefFactory = { "android-share-text-fixed" })
        val capture = store.publish(accepted(ExternalSharePayload.Text("早餐 18 元")))!!

        assertEquals("android-share-text-fixed", capture.sourceRef)
        assertEquals(capture.sourceRef, store.consume()?.sourceRef)
    }

    private fun accepted(payload: ExternalSharePayload) = ExternalShareResolution.Accepted(payload)
}
