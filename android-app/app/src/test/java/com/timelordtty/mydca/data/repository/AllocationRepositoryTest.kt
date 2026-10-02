package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.*
import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.dto.*
import com.timelordtty.mydca.ui.state.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AllocationRepositoryTest {
    private val policy = """{"id":"p","config":{"scope":"FAMILY","productId":1,"assetType":null,"target":0.6,"lowerBound":0.5,"upperBound":0.7,"returnThreshold":0.2,"takeProfitThresholds":[0.1,0.2],"enabled":true,"note":"主人配置"},"createdAt":null}"""
    private val evaluation = """{"policyId":"p","status":"TAKE_PROFIT_WATCH","reason":"仅供观察","allocation":0.8,"returnRate":0.2,"reachedTakeProfitThresholds":[0.1,0.2],"evaluatedAt":"2026-10-02T00:00:00Z"}"""
    private val previewJson = """{"policyId":"p","status":"UNKNOWN","description":"行情陈旧","totalAssets":null,"currentWeight":null,"targetWeight":0.6,"lowerBound":0.5,"upperBound":0.7,"deviationPercentagePoints":null,"targetScenario":null,"bandScenario":null,"dataDate":"2026-09-01","prices":[{"productId":1,"status":"STALE","priceDate":"2026-09-01","valuationDate":null,"priceSource":"UNKNOWN"}],"warnings":[{"code":"FEES","status":"NOT_MODELED","message":"未建模"}]}"""

    @Test fun authenticatedReadsUseOnlyObservationContractsAndPreserveUnknown() = runTest {
        val server = MockWebServer(); server.start()
        try {
            listOf("[$policy]", policy, evaluation, previewJson).forEach { server.enqueue(MockResponse().setBody(it)) }
            val repo = AllocationRepository(NetworkModule.createApi(ApiConfig(server.url("/").toString()), InMemoryAuthTokenProvider("test-token")))
            assertEquals("FAMILY", (repo.policies(2) as NetworkResult.Success).data.single().config.scope)
            assertEquals("60%", allocationPercent((repo.detail("p") as NetworkResult.Success).data.config.target))
            val observation = (repo.observation("p") as NetworkResult.Success).data
            assertEquals(listOf("0.1", "0.2"), observation.reachedTakeProfitThresholds)
            assertEquals("已触发（仅观察）", allocationReturnWatch("0.2", observation.returnRate))
            val preview = (repo.preview("p") as NetworkResult.Success).data
            assertNull(preview.currentWeight); assertNull(preview.targetScenario)
            assertEquals("STALE", preview.prices.single().status)
            assertEquals("NOT_MODELED", preview.warnings.single().status)
            listOf("GET" to "/api/v2/allocation-policies?page=2&size=20", "GET" to "/api/v2/allocation-policies/p", "POST" to "/api/v2/allocation-policies/p/evaluate", "GET" to "/api/v2/allocation-policies/p/preview").forEach { (method, path) ->
                val request = server.takeRequest()
                assertEquals(method, request.method); assertEquals(path, request.path)
                assertEquals(0L, request.body.size); assertEquals("Bearer test-token", request.getHeader("Authorization"))
            }
            // POST is the backend's read-only evaluation, never create/edit/confirm/execute.
            assertEquals(4, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun permissionErrorsEmptyAndMalformedResultsClearComposeState() = runTest {
        val server = MockWebServer(); server.start()
        try {
            var expired = false
            val repo = AllocationRepository(NetworkModule.createServices(ApiConfig(server.url("/").toString()), InMemoryAuthTokenProvider("test-token"), UnauthorizedHandler { expired = true }).wealthHubApi)
            for ((code, message) in listOf(401 to "登录已失效，请重新登录", 403 to "当前账号无权访问该数据", 503 to "服务暂时不可用，请稍后重试")) {
                server.enqueue(MockResponse().setResponseCode(code))
                val state = ResearchReadState(data = listOf("old"))
                val failure = repo.policies(0) as NetworkResult.Failure
                assertEquals(message, failure.message)
                val cleared = state.loaded(failure)
                assertNull(cleared.data); assertFalse(cleared.loading); assertEquals(message, cleared.error)
            }
            assertTrue(expired)
            server.enqueue(MockResponse().setBody("[]"))
            val empty = ResearchReadState<List<AllocationPolicyDto>>().loaded(repo.policies(0))
            assertTrue(empty.data!!.isEmpty()); assertFalse(empty.loading); assertNull(empty.error)
            server.enqueue(MockResponse().setBody("invalid-json"))
            assertTrue(repo.preview("p") is NetworkResult.Failure)
        } finally { server.shutdown() }
    }

    @Test fun networkFailureAndCancellation() = runTest {
        fun failing(error: RuntimeException) = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(WealthHubApi::class.java)) { _, _, _ -> throw error } as WealthHubApi
        val offline = AllocationRepository(failing(java.io.UncheckedIOException(java.io.IOException("offline"))))
        val state = ResearchReadState<AllocationEvaluationDto>().loaded(offline.observation("p"))
        assertNull(state.data); assertFalse(state.loading); assertTrue(state.error!!.contains("网络"))
        val cancelled = AllocationRepository(failing(kotlinx.coroutines.CancellationException("left")))
        try { cancelled.preview("p"); fail("must propagate cancellation") } catch (_: kotlinx.coroutines.CancellationException) { }
    }

    @Test fun composeLabelsDatesAndThresholdBoundaries() {
        val preview = AllocationPreviewDto("p", "IN_RANGE", null, "100", "0.6", "0.6", "0.5", "0.7", "0", null, null, "2026-10-02")
        val today = LocalDate.parse("2026-10-02")
        assertTrue(allocationFreshness(preview, today).contains("不代表实时"))
        assertTrue(allocationFreshness(preview.copy(dataDate = "2026-09-01"), today).contains("陈旧"))
        assertTrue(allocationFreshness(preview.copy(dataDate = null), today).contains("UNKNOWN"))
        assertTrue(allocationFreshness(preview.copy(dataDate = "2026-10-03"), today).contains("异常"))
        assertTrue(allocationFreshness(preview.copy(status = "UNKNOWN"), today).contains("不可确定"))
        assertEquals("未知 UNKNOWN", allocationPercent(null)); assertEquals("0%", allocationPercent("0"))
        assertEquals("未知 UNKNOWN", allocationReturnWatch("0.2", null))
        assertEquals("未知 UNKNOWN", allocationReturnWatch("invalid", "0.2"))
        assertEquals("未触发", allocationReturnWatch("0.2", "0.199999999999"))
        assertEquals("已触发（仅观察）", allocationReturnWatch("0.2", "0.2"))
        assertEquals("未配置", allocationReturnWatch(null, null))
        listOf("IN_RANGE", "BELOW_BAND", "ABOVE_BAND", "TAKE_PROFIT_WATCH", "UNKNOWN", "STALE", "NOT_MODELED").forEach { assertTrue(allocationLabel(it).contains(it)) }
        assertEquals("规则来自主人配置；情景预览不构成交易建议", ALLOCATION_DISCLAIMER)
    }
}
