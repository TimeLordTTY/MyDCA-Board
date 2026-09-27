package com.timelordtty.mydca.ui

import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.dto.AccountingIntentDto
import com.timelordtty.mydca.data.repository.AiAccountingRepository
import com.timelordtty.mydca.data.repository.DraftRepository
import com.timelordtty.mydca.notification.NotificationCandidate
import com.timelordtty.mydca.notification.NotificationCandidateStatus
import com.timelordtty.mydca.outbox.DraftOutboxEntry
import com.timelordtty.mydca.outbox.DraftOutboxOrigin
import com.timelordtty.mydca.ui.screens.OcrEntryMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.7.0 快速记账采集中心：把“入口可见规则 / 路由决策 / 数量统计 / 安全边界”固定成可重复执行的证据。
 *
 * 全部为纯 Kotlin 断言，不依赖 Compose instrumentation，因此可以在 JVM 单元测试里稳定回归。
 */
class QuickCaptureHubTest {

    @Test
    fun quickEntryIsVisibleOnMainRoutesAndHiddenOnSettings() {
        val visible = AppRoute.entries.filter { QuickCaptureHub.isEntryVisibleOn(it) }

        assertEquals(
            setOf(AppRoute.Overview, AppRoute.TodayTodo, AppRoute.Drafts, AppRoute.Accounts),
            visible.toSet(),
        )
        assertFalse(QuickCaptureHub.isEntryVisibleOn(AppRoute.Settings))
    }

    @Test
    fun quickEntryHidesInsideCaptureSubFlow() {
        AppRoute.entries.forEach { route ->
            assertFalse(
                "$route 上的手工 / 图片录入子页面不应再叠加悬浮入口",
                QuickCaptureHub.isEntryVisibleOn(route, isSubFlowOpen = true),
            )
        }
    }

    @Test
    fun openingAndDismissingPanelKeepsCurrentRoute() {
        // 打开 / 关闭面板都不产生导航决策，因此当前 route 不会被改写。
        assertNull(QuickCaptureHub.onPanelOpen())
        assertNull(QuickCaptureHub.onPanelDismiss())
    }

    @Test
    fun manualTextEntryRoutesToExistingManualFlow() {
        val decision = QuickCaptureHub.decide(QuickCaptureAction.ManualText)

        assertEquals(AppRoute.Drafts, decision.route)
        assertEquals(OcrEntryMode.ManualText, decision.entryMode)
        assertEquals(QuickCaptureFocus.NONE, decision.focus)
    }

    @Test
    fun imageOcrEntryRoutesToExistingOcrFlow() {
        val decision = QuickCaptureHub.decide(QuickCaptureAction.ImageOcr)

        assertEquals(AppRoute.Drafts, decision.route)
        assertEquals(OcrEntryMode.Image, decision.entryMode)
        assertEquals(QuickCaptureFocus.NONE, decision.focus)
    }

    @Test
    fun notificationCandidateEntryRoutesToCandidateSection() {
        val decision = QuickCaptureHub.decide(QuickCaptureAction.NotificationCandidates)

        assertEquals(AppRoute.TodayTodo, decision.route)
        assertNull(decision.entryMode)
        assertTrue(decision.focus.notificationCandidates)
        assertFalse(decision.focus.outbox)
    }

    @Test
    fun outboxEntryRoutesToOutboxSectionInDrafts() {
        val decision = QuickCaptureHub.decide(QuickCaptureAction.OutboxRetry)

        assertEquals(AppRoute.Drafts, decision.route)
        assertNull(decision.entryMode)
        assertTrue(decision.focus.outbox)
        assertFalse(decision.focus.notificationCandidates)
    }

    @Test
    fun everyEntryClearsStaleDraftAndCandidateSelection() {
        QuickCaptureAction.entries.forEach { action ->
            val decision = QuickCaptureHub.decide(action)
            assertTrue("$action 必须清空旧 draftId", decision.clearSelectedDraftId)
            assertTrue("$action 必须清空旧 candidateId", decision.clearSelectedCandidateId)
        }
    }

    @Test
    fun manualNavigationResetsFocusSoStaleHighlightCannotSurvive() {
        assertEquals(QuickCaptureFocus.NONE, QuickCaptureHub.focusAfterManualNavigation())
        assertFalse(QuickCaptureFocus.NONE.notificationCandidates)
        assertFalse(QuickCaptureFocus.NONE.outbox)
    }

    @Test
    fun panelShowsBadgesForCandidatesAndOutbox() {
        val entries = QuickCaptureHub.entries(pendingCandidateCount = 2, outboxCount = 1)

        assertEquals(
            listOf(
                QuickCaptureAction.ManualText,
                QuickCaptureAction.ImageOcr,
                QuickCaptureAction.NotificationCandidates,
                QuickCaptureAction.OutboxRetry,
            ),
            entries.map { it.action },
        )
        assertNull("手工入口不需要数量徽标", entries[0].badge)
        assertNull("图片入口不需要数量徽标", entries[1].badge)
        assertEquals("2 待处理", entries[2].badge?.text)
        assertEquals("1 待处理", entries[3].badge?.text)
    }

    @Test
    fun emptyCountsKeepEntriesVisibleWithPlaceholder() {
        val entries = QuickCaptureHub.entries(pendingCandidateCount = 0, outboxCount = 0)

        assertEquals(4, entries.size)
        assertEquals("暂无待处理", entries[2].badge?.text)
        assertEquals("暂无待处理", entries[3].badge?.text)
        assertFalse(entries[2].badge!!.hasPending)
        assertFalse(entries[3].badge!!.hasPending)
    }

    @Test
    fun negativeCountsAreClampedInsteadOfLeakingRawNumbers() {
        val entries = QuickCaptureHub.entries(pendingCandidateCount = -3, outboxCount = -1)

        assertEquals("暂无待处理", entries[2].badge?.text)
        assertEquals("暂无待处理", entries[3].badge?.text)
    }

    @Test
    fun countsComeFromLocalStoresOnly() {
        val candidates = listOf(
            candidate("c-new", NotificationCandidateStatus.NEW),
            candidate("c-drafted", NotificationCandidateStatus.DRAFT_CREATED),
            candidate("c-dismissed", NotificationCandidateStatus.DISMISSED),
        )
        val entries = listOf(outboxEntry(1), outboxEntry(2))

        assertEquals(2, QuickCaptureCounts.pendingCandidates(candidates))
        assertEquals(2, QuickCaptureCounts.outboxRetries(entries))
        assertEquals(0, QuickCaptureCounts.pendingCandidates(emptyList()))
        assertEquals(0, QuickCaptureCounts.outboxRetries(emptyList()))
    }

    @Test
    fun panelCopyStaysUserFacingWithoutInternalNames() {
        val forbiddenWords = listOf(
            "outbox",
            "draft",
            "ocr",
            "preview",
            "confirm",
            "quickentry",
            "manualtext",
            "sourceRef".lowercase(),
        )

        (listOf(QuickCaptureHub.PANEL_TITLE, QuickCaptureHub.PANEL_DESCRIPTION) +
            QuickCaptureAction.entries.flatMap { listOf(it.title, it.description) })
            .forEach { text ->
                assertTrue("文案不能为空", text.isNotBlank())
                forbiddenWords.forEach { word ->
                    assertFalse("文案不应暴露内部名称 $word：$text", text.lowercase().contains(word))
                }
            }
    }

    @Test
    fun hubExposesNoParsePreviewConfirmOrQuickEntryCapability() {
        val forbiddenNameTokens = listOf(
            "preview",
            "confirm",
            "ignore",
            "quickentry",
            "createdraft",
            "draftfromintent",
            "parsetext",
            "execute",
        )
        val forbiddenTypes = setOf(
            AiAccountingRepository::class.java,
            DraftRepository::class.java,
            WealthHubApi::class.java,
        )
        val surfaces = listOf(
            QuickCaptureHub::class.java,
            QuickCaptureAction::class.java,
            QuickCaptureEntry::class.java,
            QuickCaptureBadge::class.java,
            QuickCaptureFocus::class.java,
            QuickCaptureDecision::class.java,
            QuickCaptureCounts::class.java,
        )

        surfaces.forEach { surface ->
            surface.declaredMethods.forEach { method ->
                val name = method.name.lowercase()
                forbiddenNameTokens.forEach { token ->
                    assertFalse(
                        "$surface 暴露了越过草稿边界的入口：${method.name}",
                        name.contains(token),
                    )
                }
                assertFalse(
                    "$surface 直接依赖了网络写入门面：${method.name}",
                    method.returnType in forbiddenTypes,
                )
                method.parameterTypes.forEach { parameter ->
                    assertFalse(
                        "$surface 直接依赖了网络写入门面：${method.name}",
                        parameter in forbiddenTypes,
                    )
                }
            }
        }
    }

    private fun candidate(id: String, status: NotificationCandidateStatus) = NotificationCandidate(
        id = id,
        packageName = "com.example.pay",
        postedAt = 1_760_000_000_000L,
        status = status,
    )

    private fun outboxEntry(index: Int) = DraftOutboxEntry(
        id = "entry-$index",
        sourceType = "APP_FORM",
        sourceRef = "android-ocr-request-$index",
        origin = DraftOutboxOrigin.MANUAL_TEXT,
        summary = "早餐 18 元",
        intent = AccountingIntentDto(sourceType = "APP_FORM", sourceRef = "android-ocr-request-$index"),
        createdAt = index.toLong(),
    )
}
