package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.*
import com.timelordtty.mydca.data.dto.*
import com.timelordtty.mydca.ui.state.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class GoalForecastRepositoryTest {
    private val goal = GoalDto("g", GoalConfigDto("目标", BigDecimal.TEN, "2027-12-31", "CNY", "PERSONAL", "CASH", "ACTIVE"))
    private val progress = """{"goalId":"g","quality":"OK","currentValue":1,"asOfDate":"2026-10-08","daysRemaining":1,"overdue":false}"""
    private val result = """{"goalId":"g","currency":"CNY","asOfDate":"2026-10-08","actualProgress":$progress,"baseline":{"annualRate":0,"quality":"PARTIAL","outcome":"UNKNOWN","months":[]}}"""
    @Test fun baselineOnlyReadSimulationAndNoCoverageAssertion() = runTest {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setBody(progress))
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody(result))
            val repository = GoalBudgetRepository(NetworkModule.createApi(ApiConfig(server.url("/").toString()), InMemoryAuthTokenProvider("test")))
            val state = ResearchReadState<GoalForecastDto>().loaded(repository.forecast(goal))
            assertFalse(state.loading); assertNull(state.error)
            assertTrue(state.data!!.baseline.months.isEmpty())
            assertTrue(forecastOutcome(state.data!!.baseline).contains("无法确定"))
            assertEquals("GET", server.takeRequest().method)
            assertEquals("GET", server.takeRequest().method)
            val request = server.takeRequest()
            assertEquals("/api/v2/goals/g/forecast", request.path)
            assertEquals("POST", request.method)
            assertEquals("Bearer test", request.getHeader("Authorization"))
            val body = request.body.readUtf8()
            assertTrue(body.contains("2026-11")); assertTrue(body.contains("2027-10"))
            assertTrue(body.contains("\"mode\":\"PLANNED\""))
            assertFalse(body.contains("annualRate")); assertFalse(body.contains("true"))
            assertTrue(body.contains("\"monthlyExtraSavings\":0"))
            assertEquals(3, server.requestCount)
        } finally { server.shutdown() }
    }
    @Test fun permissionsErrorsRetryClearOldData() = runTest {
        val server = MockWebServer(); server.start()
        try {
            var expired = false
            val api = NetworkModule.createServices(ApiConfig(server.url("/").toString()), InMemoryAuthTokenProvider("test"), UnauthorizedHandler { expired = true }).wealthHubApi
            val repository = GoalBudgetRepository(api)
            var state = ResearchReadState<GoalForecastDto>()
            assertTrue(state.loading)
            for (code in listOf(401, 403, 503)) {
                server.enqueue(MockResponse().setBody(progress)); server.enqueue(MockResponse().setBody("[]"))
                server.enqueue(MockResponse().setResponseCode(code))
                state = state.loaded(repository.forecast(goal))
                assertNull(state.data); assertNotNull(state.error); assertFalse(state.loading)
                if (code == 403) assertEquals("当前账号无权访问该数据", state.error)
            }
            assertTrue(expired)
            server.enqueue(MockResponse().setBody(progress)); server.enqueue(MockResponse().setBody("[]")); server.enqueue(MockResponse().setBody(result))
            state = state.loaded(repository.forecast(goal)); assertNotNull(state.data)
            state = state.loaded(NetworkResult.Failure("断网，请重试", java.io.IOException()))
            assertNull(state.data)
            server.enqueue(MockResponse().setBody("invalid"))
            assertNotNull((repository.forecast(goal) as NetworkResult.Failure).message)
        } finally { server.shutdown() }
    }
    @Test fun paginatedBudgetsAmbiguityAndScopeAreNotGuessed() = runTest {
        val server = MockWebServer(); server.start()
        fun budget(id: String, month: String, scope: String = "PERSONAL") =
            """{"id":"$id","config":{"name":"预算","month":"$month","currency":"CNY","scope":"$scope","items":[]}}"""
        try {
            server.enqueue(MockResponse().setBody(progress))
            val first = (0..17).map { budget("old$it", "2026-09") } + budget("a", "2026-11") + budget("family", "2026-12", "FAMILY")
            server.enqueue(MockResponse().setBody(first.joinToString(",", "[", "]")))
            server.enqueue(MockResponse().setBody("[${budget("b", "2026-11")},${budget("c", "2026-12")} ]"))
            server.enqueue(MockResponse().setBody(result))
            val repository = GoalBudgetRepository(NetworkModule.createApi(ApiConfig(server.url("/").toString()), InMemoryAuthTokenProvider("test")))
            assertTrue(repository.forecast(goal) is NetworkResult.Success)
            server.takeRequest(); server.takeRequest()
            assertEquals("/api/v2/monthly-budgets?page=1&size=20", server.takeRequest().path)
            val body = server.takeRequest().body.readUtf8()
            assertFalse(body.contains("\"budgetId\":\"a\"")); assertFalse(body.contains("\"budgetId\":\"b\""))
            assertFalse(body.contains("family")); assertTrue(body.contains("\"budgetId\":\"c\""))
        } finally { server.shutdown() }
    }
    @Test fun unknownPartialAndStaleNeverClaimAchievement() {
        for (quality in listOf("UNKNOWN", "PARTIAL", null)) {
            val scenario = ForecastScenarioDto(BigDecimal.ZERO, null, quality, "ACHIEVED", "2026-11", emptyList())
            assertTrue(forecastOutcome(scenario).contains("无法确定"))
            assertEquals("未知", observedMoney(BigDecimal.ZERO, quality, "CNY"))
        }
        assertTrue(forecastFreshness("2026-10-07", LocalDate.of(2026,10,8)).contains("过期"))
        assertTrue(forecastFreshness(null).contains("未知"))
    }
}
