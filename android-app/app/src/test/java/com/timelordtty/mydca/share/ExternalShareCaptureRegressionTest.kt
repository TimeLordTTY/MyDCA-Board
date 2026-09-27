package com.timelordtty.mydca.share

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.dto.AccountingIntentDto
import com.timelordtty.mydca.data.dto.DraftFromIntentResponseDto
import com.timelordtty.mydca.data.dto.DraftLedgerEntryDto
import com.timelordtty.mydca.data.repository.AiAccountingRepository
import com.timelordtty.mydca.data.repository.DraftRepository
import com.timelordtty.mydca.ocr.OcrDraftGateway
import com.timelordtty.mydca.outbox.DraftOutboxOrigin
import com.timelordtty.mydca.outbox.DraftOutboxQueue
import com.timelordtty.mydca.outbox.FIXED_NOW
import com.timelordtty.mydca.outbox.InMemoryDraftOutboxStorage
import com.timelordtty.mydca.ui.AppRoute
import com.timelordtty.mydca.ui.ExternalShareCaptureSession
import com.timelordtty.mydca.ui.ExternalShareHub
import com.timelordtty.mydca.ui.QuickCaptureAction
import com.timelordtty.mydca.ui.QuickCaptureHub
import com.timelordtty.mydca.ui.screens.OcrEntryMode
import com.timelordtty.mydca.ui.state.OcrDraftCoordinator
import com.timelordtty.mydca.ui.state.OcrDraftStage
import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.8.0 外部分享采集：把“只预填、只到 DRAFT”的安全边界固定成可重复执行的证据，
 * 并回归 v0.7 快速采集中心与 v0.6 Outbox 的既有语义。
 */
class ExternalShareCaptureRegressionTest {
    private val shareText = "早餐 18 元 微信支付"
    private val shareTextSourceRef = "android-share-text-aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    private val shareImageSourceRef = "android-share-image-11111111-2222-3333-4444-555555555555"

    @Test
    fun sharedTextPrefillNeverParsesAndNeverDrafts() = runBlocking {
        val coordinator = OcrDraftCoordinator()
        val gateway = RecordingGateway()

        coordinator.startTextEntry("share-text-request", shareText, shareTextSourceRef)

        assertEquals(OcrDraftStage.Recognized, coordinator.state.value.stage)
        assertEquals(shareText, coordinator.state.value.recognizedText)
        assertEquals(shareTextSourceRef, coordinator.state.value.sourceRef)
        assertEquals("分享预填不能自动解析", 0, gateway.parseCalls)
        assertEquals("分享预填不能自动创建草稿", 0, gateway.createCalls)
        assertNull(coordinator.state.value.intent)
        assertNull(coordinator.state.value.draftId)
    }

    @Test
    fun sharedTextReachesDraftOnlyThroughTwoExplicitUserActions() = runBlocking {
        val coordinator = OcrDraftCoordinator()
        val gateway = RecordingGateway()
        coordinator.startTextEntry("share-text-request", shareText, shareTextSourceRef)

        coordinator.parseIntent(gateway)

        assertEquals(1, gateway.parseCalls)
        assertEquals("解析之后仍不能自动创建草稿", 0, gateway.createCalls)
        assertEquals(OcrDraftStage.IntentReady, coordinator.state.value.stage)

        coordinator.createDraft(gateway, null, DraftOutboxOrigin.MANUAL_TEXT)

        assertEquals(1, gateway.createCalls)
        assertEquals(42L, coordinator.state.value.draftId)
        assertEquals(OcrDraftStage.DraftCreated, coordinator.state.value.stage)
    }

    @Test
    fun onlyUserEditedTextAndTheShareSourceRefReachTheBackend() = runBlocking {
        val coordinator = OcrDraftCoordinator()
        val gateway = RecordingGateway()
        coordinator.startTextEntry("share-text-request", shareText, shareTextSourceRef)

        coordinator.editText("用户删除敏感字段后 早餐 18")
        coordinator.parseIntent(gateway)

        assertEquals("用户删除敏感字段后 早餐 18", gateway.lastParsedText)
        assertEquals(shareTextSourceRef, gateway.lastSourceRef)
        assertEquals(shareTextSourceRef, coordinator.state.value.intent?.sourceRef)
        assertEquals("APP_FORM", coordinator.state.value.intent?.sourceType)
        assertNull("候选不能携带原始分享 payload", coordinator.state.value.intent?.parsedPayloadJson)
    }

    @Test
    fun userCanClearThePrefilledShareTextWithoutCreatingAnything() = runBlocking {
        val coordinator = OcrDraftCoordinator()
        val gateway = RecordingGateway()
        coordinator.startTextEntry("share-text-request", shareText, shareTextSourceRef)

        coordinator.editText("")

        assertEquals(OcrDraftStage.Recognized, coordinator.state.value.stage)
        assertEquals("", coordinator.state.value.recognizedText)
        coordinator.parseIntent(gateway)
        assertEquals("清空后不允许解析", 0, gateway.parseCalls)
        assertEquals(0, gateway.createCalls)
    }

    @Test
    fun sharedTextDraftKeepsAStableSourceRefAcrossOutboxRetry() = runBlocking {
        val coordinator = OcrDraftCoordinator()
        val failingGateway = RecordingGateway(draftFailure = true)
        val outbox = DraftOutboxQueue(InMemoryDraftOutboxStorage(), clock = { FIXED_NOW })
        coordinator.startTextEntry("share-text-request", shareText, shareTextSourceRef)
        coordinator.parseIntent(failingGateway)
        coordinator.createDraft(failingGateway, outbox, DraftOutboxOrigin.MANUAL_TEXT)

        val queued = outbox.entries.value.single()
        assertEquals(shareTextSourceRef, queued.sourceRef)
        assertFalse("sourceRef 不能包含分享原文", queued.sourceRef.contains("早餐"))
        assertFalse(queued.sourceRef.contains("18"))

        val retryGateway = RecordingGateway()
        assertTrue(outbox.retryEntry(queued.id, retryGateway))

        assertEquals("Outbox 重试必须复用同一次分享的 sourceRef", shareTextSourceRef, retryGateway.createdIntents.single().sourceRef)
        assertEquals("Outbox 只重试创建草稿，不重新解析", 0, retryGateway.parseCalls)
        assertTrue(outbox.entries.value.isEmpty())
    }

    @Test
    fun leavingTheCaptureFlowClearsTheActiveShare() {
        val capture = ExternalShareCapture(
            token = 1L,
            payload = ExternalSharePayload.Text(shareText),
            sourceRef = shareTextSourceRef,
        )
        val clearingPaths = mapOf(
            "返回 / 取消" to { session: ExternalShareCaptureSession -> session.onCaptureExit() },
            "主动切换底部导航" to { session: ExternalShareCaptureSession -> session.onManualNavigation() },
            "成功生成 DRAFT" to { session: ExternalShareCaptureSession -> session.onDraftCreated() },
        )

        clearingPaths.forEach { (label, clear) ->
            val session = ExternalShareCaptureSession()
            session.adopt(capture)
            assertEquals(capture, session.active.value)

            clear(session)

            assertNull("$label 后必须清掉 share target", session.active.value)
        }
    }

    @Test
    fun adoptingNothingKeepsTheCurrentShareInsteadOfDroppingIt() {
        val session = ExternalShareCaptureSession()
        val capture = ExternalShareCapture(
            token = 2L,
            payload = ExternalSharePayload.Image("content://com.example.provider/picked/1"),
            sourceRef = shareImageSourceRef,
        )
        session.adopt(capture)

        session.adopt(null)

        assertNotNull("没有新的 pending 时不应清掉当前分享", session.active.value)
        assertEquals(capture, session.active.value)
    }

    @Test
    fun shareDestinationReusesTheExistingManualAndOcrFlows() {
        val textDestination = ExternalShareHub.destinationFor(ExternalSharePayload.Text(shareText))
        val imageDestination = ExternalShareHub.destinationFor(
            ExternalSharePayload.Image("content://com.example.provider/picked/1"),
        )

        assertEquals(AppRoute.Drafts, textDestination.route)
        assertEquals(OcrEntryMode.ManualText, textDestination.entryMode)
        assertEquals(AppRoute.Drafts, imageDestination.route)
        assertEquals(OcrEntryMode.Image, imageDestination.entryMode)
    }

    @Test
    fun quickCaptureHubStillOwnsTheFourV07Entries() {
        assertEquals(4, QuickCaptureAction.entries.size)
        assertEquals(OcrEntryMode.ManualText, QuickCaptureHub.decide(QuickCaptureAction.ManualText).entryMode)
        assertEquals(OcrEntryMode.Image, QuickCaptureHub.decide(QuickCaptureAction.ImageOcr).entryMode)
        assertTrue(QuickCaptureHub.decide(QuickCaptureAction.ManualText).clearSelectedDraftId)
        assertTrue(QuickCaptureHub.decide(QuickCaptureAction.ManualText).clearSelectedCandidateId)
        assertEquals(
            ExternalShareHub.destinationFor(ExternalSharePayload.Text(shareText)).entryMode,
            QuickCaptureHub.decide(QuickCaptureAction.ManualText).entryMode,
        )
    }

    @Test
    fun shareCopyAnnouncesPrefillOnlyAndStaysUserFacing() {
        assertEquals("来自系统分享，尚未解析/未生成草稿", ExternalShareHub.PREFILL_HINT)

        val forbiddenWords = listOf(
            "outbox",
            "draft",
            "ocr",
            "preview",
            "confirm",
            "quickentry",
            "sourceref",
            "external",
        )
        listOf(
            ExternalSharePayload.Text(shareText),
            ExternalSharePayload.Image("content://com.example.provider/picked/1"),
        ).forEach { payload ->
            val banner = ExternalShareHub.bannerFor(payload)
            assertTrue("分享提示不能为空", banner.isNotBlank())
            forbiddenWords.forEach { word ->
                assertFalse("提示不应暴露内部名称 $word：$banner", banner.lowercase().contains(word))
            }
        }
        assertTrue(
            ExternalShareHub.bannerFor(ExternalSharePayload.Text(shareText)).contains("只有你点击"),
        )
        assertTrue(
            ExternalShareHub.bannerFor(ExternalSharePayload.Image("content://com.example.provider/picked/1"))
                .contains("图片不会上传"),
        )
    }

    @Test
    fun rejectedShareNeverProducesAShareTarget() {
        val store = ExternalSharePendingStore()

        assertNull(store.publish(ExternalShareResolution.Rejected(ExternalShareRejection.UntrustedImageSource)))

        assertNull("被拒绝的分享不能留下可导航的 payload", store.pending.value)
        assertNotNull(store.consumeNotice())
    }

    @Test
    fun externalShareLayerExposesNoParsePreviewConfirmCapability() {
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
            ExternalShareResolver::class.java,
            ExternalShareSourceRef::class.java,
            ExternalSharePendingStore::class.java,
            SharedImageOcrGate::class.java,
            ExternalShareHub::class.java,
            ExternalShareCaptureSession::class.java,
        )

        surfaces.forEach { surface ->
            surface.declaredMethods.forEach { method ->
                val name = method.name.lowercase()
                forbiddenNameTokens.forEach { token ->
                    assertFalse("$surface 暴露了越过草稿边界的入口：${method.name}", name.contains(token))
                }
                assertFalse("$surface 直接依赖了网络写入门面：${method.name}", method.returnType in forbiddenTypes)
                method.parameterTypes.forEach { parameter ->
                    assertFalse("$surface 直接依赖了网络写入门面：${method.name}", parameter in forbiddenTypes)
                }
            }
        }
    }

    /** 只实现“解析候选 / 创建 DRAFT”的网关测试替身；类型上没有任何 preview / confirm 入口。 */
    private class RecordingGateway(private val draftFailure: Boolean = false) : OcrDraftGateway {
        var parseCalls = 0
        var createCalls = 0
        var lastParsedText: String? = null
        var lastSourceRef: String? = null
        val createdIntents = mutableListOf<AccountingIntentDto>()

        override suspend fun parseText(text: String, sourceRef: String): NetworkResult<AccountingIntentDto> {
            parseCalls += 1
            lastParsedText = text
            lastSourceRef = sourceRef
            return NetworkResult.Success(
                AccountingIntentDto(
                    sourceType = "APP_FORM",
                    sourceRef = sourceRef,
                    rawInput = text,
                    txnType = "EXPENSE",
                    amount = 18.0,
                ),
            )
        }

        override suspend fun createDraft(intent: AccountingIntentDto): NetworkResult<DraftFromIntentResponseDto> {
            createCalls += 1
            createdIntents += intent
            if (draftFailure) {
                return NetworkResult.Failure("fixture draft failure", SocketTimeoutException("timeout"))
            }
            return NetworkResult.Success(
                DraftFromIntentResponseDto(
                    intent = intent,
                    draft = DraftLedgerEntryDto(id = 42L, status = "DRAFT"),
                ),
            )
        }
    }
}
