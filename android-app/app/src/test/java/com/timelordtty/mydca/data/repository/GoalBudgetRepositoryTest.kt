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

class GoalBudgetRepositoryTest {
    private val goal = """{"id":"g","config":{"name":"储蓄","targetValue":1000.01,"targetDate":"2026-12-31","currency":"CNY","scope":"PERSONAL","measure":"CASH","state":"ACTIVE"}}"""
    private val progress = """{"goalId":"g","quality":"OK","reason":"只读","currentValue":250.01,"knownValue":250.01,"completionRate":0.25,"completed":false,"asOfDate":"2026-10-02","daysRemaining":90,"overdue":false}"""
    private val budget = """{"id":"b","config":{"name":"本月","month":"2026-10","currency":"CNY","scope":"PERSONAL","items":[]}}"""
    private val comparison = """{"budgetId":"b","quality":"PARTIAL","plannedIncome":5000,"plannedExpenses":1000,"plannedReserve":100,"plannedSurplus":3900,"actualIncome":null,"actualExpenses":null,"actualSurplus":null,"remainingBudget":null,"overspent":null,"unmatchedPostings":1,"items":[],"warnings":["部分数据"]}"""

    @Test fun contractPrecisionPaginationAndAuthenticatedGetOnly() = runTest {
        val server = MockWebServer(); server.start()
        try {
            listOf("[$goal]", progress, "[$budget]", comparison).forEach { server.enqueue(MockResponse().setBody(it)) }
            val repository = GoalBudgetRepository(NetworkModule.createApi(ApiConfig(server.url("/").toString()), InMemoryAuthTokenProvider("test")))
            val g = (repository.goals(1) as NetworkResult.Success).data.single()
            val p = (repository.progress("g") as NetworkResult.Success).data
            val budgets = (repository.budgets(2) as NetworkResult.Success).data
            val c = (repository.comparison("b") as NetworkResult.Success).data
            assertEquals(BigDecimal("1000.01"), g.config.targetValue)
            assertEquals("25.00%", goalRate(p))
            assertEquals("750.00 CNY", goalRemaining(g, p))
            assertEquals("未知", observedMoney(c.actualExpenses, c.quality, "CNY"))
            assertEquals("超支状态未知", overspendStatus(c.overspent, c.quality))
            assertEquals(1, currentMonthBudgets(budgets, "2026-10").size)
            assertTrue(currentMonthBudgets(budgets, "2026-11").isEmpty())
            listOf("/api/v2/goals?page=1&size=20", "/api/v2/goals/g/progress", "/api/v2/monthly-budgets?page=2&size=20", "/api/v2/monthly-budgets/b/comparison").forEach {
                val request = server.takeRequest()
                assertEquals(it, request.path); assertEquals("GET", request.method)
                assertEquals(0L, request.body.size); assertEquals("Bearer test", request.getHeader("Authorization"))
            }
            assertEquals(4, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun composeStateEmptyErrorsPermissionsRecoveryAndUnknown() = runTest {
        val server = MockWebServer(); server.start()
        try {
            var expired = false
            val api = NetworkModule.createServices(ApiConfig(server.url("/").toString()), InMemoryAuthTokenProvider("test"), UnauthorizedHandler { expired = true }).wealthHubApi
            val repository = GoalBudgetRepository(api)
            var state = ResearchReadState<List<GoalDto>>()
            assertTrue(state.loading)
            server.enqueue(MockResponse().setBody("[]"))
            state = state.loaded(repository.goals(0)); assertTrue(state.data!!.isEmpty())
            for ((code, message) in listOf(401 to "登录已失效，请重新登录", 403 to "当前账号无权访问该数据", 503 to "服务暂时不可用，请稍后重试")) {
                server.enqueue(MockResponse().setResponseCode(code))
                state = state.loaded(repository.goals(0))
                assertNull(state.data); assertFalse(state.loading); assertEquals(message, state.error)
            }
            assertTrue(expired)
            server.enqueue(MockResponse().setBody("not-json"))
            state = state.loaded(repository.goals(0)); assertNotNull(state.error); assertNull(state.data)
            server.enqueue(MockResponse().setBody("[$goal]"))
            state = state.loaded(repository.goals(0)); assertEquals(1, state.data!!.size); assertNull(state.error)
            val p = GoalProgressDto("g", "PARTIAL", null, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, true, null, 0, false)
            assertEquals("未知", goalRate(p)); assertEquals("未知", goalRemaining(state.data!!.single(), p))
            for (quality in listOf("PARTIAL", "UNKNOWN", null, "FUTURE")) {
                assertEquals("未知", observedMoney(BigDecimal.ZERO, quality, "CNY"))
                assertEquals("超支状态未知", overspendStatus(false, quality))
            }
            assertEquals("已超支", overspendStatus(true, "OK"))
            assertEquals("未超支", overspendStatus(false, "OK"))
            assertEquals("未知", goalRate(p.copy(quality = "OK", completionRate = null)))
        } finally { server.shutdown() }
    }
}
