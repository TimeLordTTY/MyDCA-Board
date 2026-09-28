package com.timelordtty.mydca.widget

import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.repository.AiAccountingRepository
import com.timelordtty.mydca.data.repository.DraftRepository
import com.timelordtty.mydca.ui.AppRoute
import com.timelordtty.mydca.ui.QuickCaptureAction
import com.timelordtty.mydca.ui.QuickCaptureFocus
import com.timelordtty.mydca.ui.QuickCaptureHub
import com.timelordtty.mydca.ui.screens.OcrEntryMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面入口 → 既有页面：只做导航映射，四个目标都停在既有采集 / 草稿边界之内。
 */
class WidgetNavigationHubTest {

    @Test
    fun quickCaptureOpensTheExistingQuickCaptureHubWithoutChangingPage() {
        val decision = WidgetNavigationHub.decide(WidgetNavigationTarget.QuickCapture)

        assertTrue(decision.openQuickCapturePanel)
        assertNull("记一笔只展开面板，不强行跳页", decision.route)
        assertNull(decision.entryMode)
        assertEquals(QuickCaptureFocus.NONE, decision.focus)
    }

    @Test
    fun manualTextEntryReusesTheExistingManualTextFlow() {
        val widget = WidgetNavigationHub.decide(WidgetNavigationTarget.ManualText)
        val quickPanel = QuickCaptureHub.decide(QuickCaptureAction.ManualText)

        assertEquals(AppRoute.Drafts, widget.route)
        assertEquals(OcrEntryMode.ManualText, widget.entryMode)
        assertEquals(quickPanel.route, widget.route)
        assertEquals(quickPanel.entryMode, widget.entryMode)
        assertFalse(widget.openQuickCapturePanel)
    }

    @Test
    fun imageOcrEntryReusesTheExistingOcrFlowWithoutStartingRecognition() {
        val widget = WidgetNavigationHub.decide(WidgetNavigationTarget.ImageOcr)
        val quickPanel = QuickCaptureHub.decide(QuickCaptureAction.ImageOcr)

        assertEquals(AppRoute.Drafts, widget.route)
        assertEquals(OcrEntryMode.Image, widget.entryMode)
        assertEquals(quickPanel.entryMode, widget.entryMode)
        assertEquals(quickPanel.focus, widget.focus)
        assertFalse("图片入口只进入既有采集页，不自动开始识别", widget.openQuickCapturePanel)
    }

    @Test
    fun draftsEntryOpensTheDraftInboxWithoutAnyEntryMode() {
        val decision = WidgetNavigationHub.decide(WidgetNavigationTarget.Drafts)

        assertEquals(AppRoute.Drafts, decision.route)
        assertNull("草稿箱入口不进入采集子页面", decision.entryMode)
        assertFalse(decision.openQuickCapturePanel)
        assertEquals(QuickCaptureFocus.NONE, decision.focus)
    }

    @Test
    fun everyTargetClearsStaleDraftAndCandidateSelection() {
        WidgetNavigationTarget.entries.forEach { target ->
            val decision = WidgetNavigationHub.decide(target)
            assertTrue("$target 必须清空旧 draftId", decision.clearSelectedDraftId)
            assertTrue("$target 必须清空旧 candidateId", decision.clearSelectedCandidateId)
        }
    }

    @Test
    fun hubExposesNoParseDraftPreviewConfirmCapability() {
        val forbiddenNameTokens = listOf(
            "preview",
            "confirm",
            "ignore",
            "quickentry",
            "createdraft",
            "draftfromintent",
            "parsetext",
            "execute",
            "upload",
            "notify",
        )
        val forbiddenTypes = setOf(
            AiAccountingRepository::class.java,
            DraftRepository::class.java,
            WealthHubApi::class.java,
        )
        val surfaces = listOf(
            WidgetNavigationHub::class.java,
            WidgetNavigationTarget::class.java,
            WidgetNavigationDecision::class.java,
            WidgetNavigationResolver::class.java,
            WidgetNavigationPendingStore::class.java,
            WidgetNavigationRequest::class.java,
            WidgetNavigationArbiter::class.java,
            WidgetEntryPoints::class.java,
            WidgetSizePolicy::class.java,
        )

        surfaces.forEach { surface ->
            surface.declaredMethods.forEach { method ->
                val name = method.name.lowercase()
                forbiddenNameTokens.forEach { token ->
                    assertFalse("$surface 暴露了越过草稿边界的入口：${method.name}", name.contains(token))
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
}
