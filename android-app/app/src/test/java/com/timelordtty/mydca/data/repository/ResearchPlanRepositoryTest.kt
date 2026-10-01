package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.*
import com.timelordtty.mydca.data.dto.*
import com.timelordtty.mydca.ui.state.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ResearchPlanRepositoryTest {
    private val id = "12345678-1234-1234-1234-123456789abc"
    private val plan = """{"id":"$id","name":"定投研究","status":"ACTIVE","strategy":"dca","strategyVersion":"1",
        "sourceRunIds":["source"],"canonicalParamsSnapshot":{"amount":200},"paramsDraft":{"amount":300},
        "datasetHashes":["hash"],"warnings":["来源数据集已变更"],
        "evidenceSnapshot":{"runs":[{"run_id":"source","dataset_hash":"hash","engine_version":"1.0.0","metrics":{"annualized_return":null}}]}}""".trimIndent()

    @Test fun listDetailAndHistoryMapBackendEvidenceAndOnlySendAuthenticatedGet() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("[$plan]"))
            server.enqueue(MockResponse().setBody(plan))
            server.enqueue(MockResponse().setBody("""[
                {"historyRunId":"failed","researchPlanId":"$id","status":"FAILED","startedAt":"2026-10-01T10:00:00Z","failureCode":"ENGINE_ERROR","metrics":null},
                {"historyRunId":"ok","researchPlanId":"$id","status":"SUCCESS","startedAt":"2026-09-30T10:00:00Z","metrics":{"total_return":0.1,"annualized_return":null,"max_drawdown":0.2}},
                {"historyRunId":"other","researchPlanId":"other-plan","status":"SUCCESS"}]
            """.trimIndent()))
            val repository = ResearchPlanRepository(NetworkModule.createApi(ApiConfig(server.url("/").toString()), InMemoryAuthTokenProvider("test-token")))
            val list = (repository.plans(1) as NetworkResult.Success).data
            val detail = (repository.detail(id) as NetworkResult.Success).data
            val runs = (repository.runs(2) as NetworkResult.Success).data
            assertEquals(list.single(), detail)
            assertEquals("来源数据集已变更", detail.warnings.single())
            assertEquals(200.0, detail.canonicalParamsSnapshot["amount"])
            assertEquals(300.0, detail.paramsDraft["amount"])
            assertEquals("1.0.0", detail.evidenceSnapshot!!.runs.single().engine_version)
            assertNull(detail.evidenceSnapshot!!.runs.single().metrics["annualized_return"])
            assertEquals(listOf("failed", "ok"), planRuns(detail, runs).map { it.historyRunId })
            assertNull(runs.first().metrics)
            assertNull(runs[1].metrics!!["annualized_return"])
            listOf("/api/v2/research-plans?page=1&size=20", "/api/v2/research-plans/$id", "/api/v2/backtest-lab/runs?page=2&size=50").forEach { path ->
                val request = server.takeRequest()
                assertEquals(path, request.path)
                assertEquals("GET", request.method)
                assertEquals(0L, request.body.size)
                assertEquals("Bearer test-token", request.getHeader("Authorization"))
            }
            assertEquals(3, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun authPermissionMissingAndServerFailuresAreExplicitAnd401ExpiresSession() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            var expired = false
            val api = NetworkModule.createServices(ApiConfig(server.url("/").toString()), InMemoryAuthTokenProvider("test-token"), UnauthorizedHandler { expired = true }).wealthHubApi
            val repository = ResearchPlanRepository(api)
            for ((code, message) in listOf(401 to "登录已失效，请重新登录", 403 to "当前账号无权访问该数据", 404 to "接口请求失败(404)", 503 to "服务暂时不可用，请稍后重试")) {
                server.enqueue(MockResponse().setResponseCode(code))
                val result = repository.detail(id) as NetworkResult.Failure
                assertEquals(message, result.message)
                val state = ResearchReadState(data = ResearchPlanDto(id, "old", "ACTIVE", "dca", "1")).loaded(result)
                assertNull(state.data)
                assertFalse(state.loading)
                assertEquals(message, state.error)
            }
            assertTrue(expired)
            assertEquals(4, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun composeStateDistinguishesLoadingEmptyFailureAndRecovery() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val repository = ResearchPlanRepository(NetworkModule.createApi(ApiConfig(server.url("/").toString())))
            var state = ResearchReadState<List<ResearchPlanDto>>()
            assertTrue(state.loading)
            assertNull(state.data)
            server.enqueue(MockResponse().setBody("[]"))
            state = state.loaded(repository.plans(0))
            assertTrue(state.data!!.isEmpty())
            assertNull(state.error)
            server.enqueue(MockResponse().setResponseCode(500))
            state = state.loaded(repository.plans(0))
            assertNull(state.data)
            assertNotNull(state.error)
            server.enqueue(MockResponse().setBody("[$plan]"))
            state = state.loaded(repository.plans(0))
            assertEquals(1, state.data!!.size)
            assertNull(state.error)
            val active = state.data!!.single()
            assertEquals(2, visibleResearchPlans(listOf(active, active.copy(status = "DRAFT"), active.copy(status = "ARCHIVED"))).size)
            assertEquals("失败 FAILED", researchStatus("FAILED"))
            assertEquals("已读取历史中无关联运行", researchStatus(null))
        } finally { server.shutdown() }
    }

    @Test fun invalidResponseIsChineseFailureRatherThanEmptyData() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("not-json"))
            val repository = ResearchPlanRepository(NetworkModule.createApi(ApiConfig(server.url("/").toString())))
            val state = ResearchReadState<List<ResearchPlanDto>>().loaded(repository.plans(0))
            assertNull(state.data)
            assertFalse(state.loading)
            assertEquals("网络连接失败或研究数据不可读取，请重试", state.error)
            assertEquals("GET", server.takeRequest().method)
        } finally { server.shutdown() }
    }
}
