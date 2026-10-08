package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.*
import com.timelordtty.mydca.data.dto.*
import com.timelordtty.mydca.ui.state.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DataReadinessRepositoryTest {
    private val body = """{"scope":"PERSONAL","month":"2026-10","checkedAt":"2026-10-08T01:00:00Z","evidence":[{"area":"BUDGETS","state":"PARTIAL","reason":"部分流水","source":"月度预算只读对比","dataTime":"2026-10","nextStep":"人工核对"},{"area":"SCHEMA","state":"UNKNOWN","reason":"未核实","source":"授权业务读取","dataTime":null,"nextStep":"需人工核验"},{"area":"MARKET","state":"UNAVAILABLE"}]}"""

    @Test fun authenticatedGetPermissionsAndRecoveryRetainWarnings() = runTest {
        val server = MockWebServer(); server.start()
        try {
            var expired = false
            val api = NetworkModule.createServices(ApiConfig(server.url("/").toString()), InMemoryAuthTokenProvider("test"), UnauthorizedHandler { expired = true }).wealthHubApi
            val repository = DataReadinessRepository(api)
            server.enqueue(MockResponse().setBody(body))
            var state = DataReadinessState().loaded(repository.read("2026-10"))
            val original = state.data
            assertEquals(listOf("PARTIAL", "UNKNOWN", "UNAVAILABLE"), original!!.evidence.map { it.state })
            assertNull(original.evidence[1].dataTime)
            for ((code, message) in listOf(401 to "登录已失效，请重新登录", 403 to "当前账号无权访问该数据", 503 to "服务暂时不可用，请稍后重试")) {
                server.enqueue(MockResponse().setResponseCode(code))
                state = state.copy(loading = true).loaded(repository.read("2026-10"))
                assertSame(original, state.data); assertEquals(message, state.error); assertFalse(state.loading)
            }
            assertTrue(expired)
            server.enqueue(MockResponse().setBody("not-json"))
            state = state.loaded(repository.read("2026-10")); assertNotNull(state.error); assertSame(original, state.data)
            server.enqueue(MockResponse().setBody(body.replace("PERSONAL", "FAMILY")))
            state = state.loaded(repository.read("2026-10")); assertNotNull(state.error); assertSame(original, state.data)
            server.enqueue(MockResponse().setBody("""{"scope":"PERSONAL","month":"2026-10","evidence":[]}"""))
            state = state.loaded(repository.read("2026-10")); assertTrue(state.data!!.evidence.isEmpty()); assertNull(state.error)
            repeat(server.requestCount) {
                val request = server.takeRequest()
                assertEquals("/api/v2/data-readiness?scope=PERSONAL&month=2026-10", request.path)
                assertEquals("GET", request.method); assertEquals(0L, request.body.size)
                assertEquals("Bearer test", request.getHeader("Authorization"))
            }
        } finally { server.shutdown() }
    }

    @Test fun offlineRequestIsFailure() = runTest {
        val server = MockWebServer(); server.start()
        val url = server.url("/").toString(); server.shutdown()
        val repository = DataReadinessRepository(NetworkModule.createApi(ApiConfig(url), InMemoryAuthTokenProvider("test")))
        val old = DataReadinessDto(evidence = listOf(DataReadinessEvidenceDto(state = "UNKNOWN")))
        val state = DataReadinessState(data = old).loaded(repository.read("2026-10"))
        assertEquals("网络连接失败，请检查网络后重试", state.error)
        assertSame(old, state.data)
    }

    @Test fun composeStateStalenessAndUnknownLabels() {
        val now = Instant.parse("2026-10-08T02:00:00Z")
        assertTrue(DataReadinessState().stale(now))
        val fresh = DataReadinessState(data = DataReadinessDto(checkedAt = "2026-10-08T01:00:00Z"))
        assertFalse(fresh.stale(now))
        for (time in listOf(null, "invalid", "2026-10-06T01:00:00Z", "2026-10-09T01:00:00Z")) {
            assertTrue(fresh.copy(data = fresh.data!!.copy(checkedAt = time)).stale(now))
        }
        assertEquals("可读取 READY", readinessLabel("READY"))
        assertEquals("部分可用 PARTIAL", readinessLabel("PARTIAL"))
        assertEquals("不可用 UNAVAILABLE", readinessLabel("UNAVAILABLE"))
        for (value in listOf(null, "UNKNOWN", "FUTURE")) assertEquals("未知 UNKNOWN", readinessLabel(value))
        assertEquals("数据库部署", readinessArea("SCHEMA"))
    }
}
