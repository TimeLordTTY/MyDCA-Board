package com.timelordtty.mydca.outbox

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.6.0 发布回归：把“可靠记账采集”的验收行为固定成可重复执行的证据。
 *
 * 覆盖两件此前没有单独测试覆盖的收口项：
 * 1. 手工文本 / 图片 OCR / 支付通知候选三个入口都只创建 DRAFT，重试成功后出队并保留来源；
 * 2. Outbox 的网络边界在类型层面只存在“创建 DRAFT”，不存在 preview / confirm / ignore 能力。
 */
class DraftOutboxReleaseRegressionTest {
    @Test
    fun allThreeCaptureOriginsOnlyCreateDraftAndDequeueAfterRetry() = runTest {
        assertEquals(
            setOf(
                DraftOutboxOrigin.MANUAL_TEXT,
                DraftOutboxOrigin.OCR,
                DraftOutboxOrigin.PAYMENT_NOTIFICATION,
            ),
            DraftOutboxOrigin.entries.toSet(),
        )

        DraftOutboxOrigin.entries.forEach { origin ->
            val storage = InMemoryDraftOutboxStorage()
            val queue = DraftOutboxQueue(
                storage = storage,
                policy = DraftOutboxRetryPolicy(backoffSeconds = listOf(0L)),
                clock = { FIXED_NOW },
                idGenerator = incrementingIds(),
            )
            val sourceRef = "release-regression-${origin.name.lowercase()}"

            val entry = queue.enqueue(
                intent = accountingIntent(sourceRef = sourceRef),
                origin = origin,
                summary = "早餐 18 元",
                failure = networkFailure(IOException("offline")),
            )

            assertNotNull("$origin 的可恢复网络失败必须进入 outbox", entry)
            assertEquals(origin, entry?.origin)
            assertEquals(1, queue.entries.value.size)

            val gateway = RecordingDraftGateway()
            assertEquals(1, queue.retryDueEntries(gateway))

            assertTrue("$origin 重试成功后必须出队", queue.entries.value.isEmpty())
            assertEquals(sourceRef, gateway.intents.single().sourceRef)

            val outcome = queue.lastOutcome.value
            assertEquals(origin, outcome?.origin)
            assertEquals("DRAFT", outcome?.draftStatus)
            assertTrue("$origin 重试成功后应能打开 DRAFT", outcome?.canOpenDraft == true)
            assertTrue(storage.read().isEmpty())
        }
    }

    @Test
    fun outboxTypesExposeNoPreviewConfirmOrIgnoreCapability() {
        val forbiddenTokens = listOf("preview", "confirm", "ignore", "quickentry")
        val outboxSurfaces = listOf(
            DraftCreationGateway::class.java,
            DraftOutboxQueue::class.java,
            DraftOutboxStorage::class.java,
            InMemoryDraftOutboxStorage::class.java,
            EncryptedDraftOutboxStorage::class.java,
            DraftOutboxErrorClassifier::class.java,
            DraftOutboxRetryPolicy::class.java,
        )

        assertEquals(
            listOf("createDraft"),
            DraftCreationGateway::class.java.declaredMethods.map { it.name }.sorted(),
        )

        outboxSurfaces.forEach { surface ->
            surface.declaredMethods.forEach { method ->
                val name = method.name.lowercase()
                forbiddenTokens.forEach { token ->
                    assertFalse(
                        "$surface 暴露了越过人工确认边界的能力：${method.name}",
                        name.contains(token),
                    )
                }
            }
        }
    }
}
