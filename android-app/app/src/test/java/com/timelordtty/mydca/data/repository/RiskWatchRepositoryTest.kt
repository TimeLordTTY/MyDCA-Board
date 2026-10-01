package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.*
import com.timelordtty.mydca.data.dto.*
import com.timelordtty.mydca.ui.state.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RiskWatchRepositoryTest {
    private val snapshot = """{"id":"s","ruleId":"r","sourceDataTimestamp":"2026-09-01","status":"UNKNOWN","matched":false,"severity":"CRITICAL","reason":"数据缺失","observedValue":null,"threshold":0.3}"""

    @Test fun readsBackendEvidenceWithOnlyAuthenticatedGet() = runTest {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setBody("""[{"id":"r","config":{"scope":"PERSONAL","type":"CONCENTRATION","threshold":0.3,"severity":"CRITICAL"}}]"""))
            server.enqueue(MockResponse().setBody("[$snapshot]"))
            server.enqueue(MockResponse().setBody("""[{"fingerprint":"f","evidence":$snapshot,"state":"OPEN","visible":true}]"""))
            val repository = RiskWatchRepository(NetworkModule.createApi(ApiConfig(server.url("/").toString()), InMemoryAuthTokenProvider("test-token")))
            assertEquals("r", (repository.rules(1) as NetworkResult.Success).data.single().id)
            val evidence = (repository.snapshots("r", 2) as NetworkResult.Success).data.single()
            assertNull(evidence.observedValue)
            assertEquals("数据缺失", evidence.reason)
            val events = (repository.events("r", 3) as NetworkResult.Success).data
            assertEquals(evidence, events.single().evidence)
            listOf("/api/v2/risk-watch-rules?page=1&size=20", "/api/v2/risk-watch-rules/r/snapshots?page=2&size=20", "/api/v2/risk-watch-rules/r/events?page=3&size=20").forEach { path ->
                val request = server.takeRequest()
                assertEquals(path, request.path); assertEquals("GET", request.method)
                assertEquals(0L, request.body.size)
                assertEquals("Bearer test-token", request.getHeader("Authorization"))
            }
            assertEquals(3, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun authPermissionServerAndEmptyStatesClearOldComposeData() = runTest {
        val server = MockWebServer(); server.start()
        try {
            var expired = false
            val api = NetworkModule.createServices(ApiConfig(server.url("/").toString()), InMemoryAuthTokenProvider("test-token"), UnauthorizedHandler { expired = true }).wealthHubApi
            val repository = RiskWatchRepository(api)
            for ((code, message) in listOf(401 to "登录已失效，请重新登录", 403 to "当前账号无权访问该数据", 503 to "服务暂时不可用，请稍后重试")) {
                server.enqueue(MockResponse().setResponseCode(code))
                val result = repository.events("r", 0) as NetworkResult.Failure
                assertEquals(message, result.message)
                val state = ResearchReadState(data = listOf(event("OPEN"))).loaded(result)
                assertNull(state.data); assertFalse(state.loading); assertEquals(message, state.error)
            }
            assertTrue(expired)
            server.enqueue(MockResponse().setBody("[]"))
            val state = ResearchReadState<List<RiskEventDto>>().loaded(repository.events("r", 0))
            assertTrue(state.data!!.isEmpty()); assertNull(state.error); assertFalse(state.loading)
            server.enqueue(MockResponse().setBody("invalid-json"))
            assertTrue(repository.rules(0) is NetworkResult.Failure)
        } finally { server.shutdown() }
    }

    @Test fun networkFailureIsVisibleAndCancellationPropagates() = runTest {
        fun failing(error: RuntimeException): com.timelordtty.mydca.data.api.WealthHubApi = java.lang.reflect.Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(com.timelordtty.mydca.data.api.WealthHubApi::class.java)
        ) { _, _, _ -> throw error } as com.timelordtty.mydca.data.api.WealthHubApi
        val repository = RiskWatchRepository(failing(java.io.UncheckedIOException(java.io.IOException("offline"))))
        val state = ResearchReadState<List<RiskRuleDto>>().loaded(repository.rules(0))
        assertNull(state.data); assertFalse(state.loading)
        assertEquals("风险数据读取失败，请检查网络后重试", state.error)
        val cancelled = RiskWatchRepository(failing(kotlinx.coroutines.CancellationException("left screen")))
        try { cancelled.rules(0); fail("Cancellation must propagate") }
        catch (_: kotlinx.coroutines.CancellationException) { }
    }

    private fun event(state: String, severity: String = "CRITICAL") = RiskEventDto(state, RiskSnapshotDto("s", "r", "2026-09-01", "OK", true, severity, "命中", 0.4, 0.3, null), state, state == "OPEN")

    @Test fun composeFiltersGroupingAndUnknownStaleDataRemainExplicit() {
        val events = listOf(event("RESOLVED"), event("OPEN", "INFO"), event("OPEN"), event("MUTED"), event("ACKNOWLEDGED"))
        assertEquals(listOf("CRITICAL", "INFO"), riskGroups(events, "OPEN").keys.toList())
        assertEquals(1, riskGroups(events, "RESOLVED").values.flatten().size)
        assertEquals(5, riskGroups(events, "ALL").values.flatten().size)
        val today = LocalDate.parse("2026-10-02")
        val evidence = events.first().evidence
        assertTrue(riskDataWarning(evidence, today).contains("陈旧"))
        assertTrue(riskDataWarning(evidence.copy(status = "UNKNOWN", observedValue = null), today).contains("不能视为"))
        assertTrue(riskDataWarning(evidence.copy(sourceDataTimestamp = null), today).contains("时间未知"))
        assertTrue(riskDataWarning(evidence.copy(sourceDataTimestamp = "2026-10-03"), today).contains("异常"))
        assertTrue(riskDataWarning(evidence.copy(sourceDataTimestamp = "2026-09-29"), today).contains("3 天内"))
        assertEquals("仅供观察，不构成交易建议", RISK_DISCLAIMER)
    }
}
