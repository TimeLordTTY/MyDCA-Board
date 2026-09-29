package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.ApiConfig
import com.timelordtty.mydca.core.network.NetworkModule
import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.ui.state.FinanceRadarState
import com.timelordtty.mydca.ui.state.FinanceRadarStateHolder
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class FinanceRadarRepositoryTest {
    private fun repository(server: MockWebServer) =
        FinanceRadarRepository(NetworkModule.createApi(ApiConfig(server.url("/").toString())))

    private val response = """
        {
          "date":"2026-09-29","scope":"PERSONAL",
          "assets":{"status":"UNKNOWN","cashBalance":120.50,"investmentCost":null,
            "positionValue":null,"liabilities":0,"totalAssets":null,"netWorth":null},
          "counts":{"drafts":2,"outbox":null,"pendingOrders":1,"awaitingSettlement":1,
            "reconciliationWarning":3,"reconciliationBroken":1},
          "markets":[{"productId":7,"status":"WARNING","priceDate":"2026-09-25",
            "valuationDate":null,"indicatorStatus":"UNKNOWN","indicatorDate":null}],
          "warnings":[{"code":"MARKET_WARNING","status":"WARNING","message":"行情过旧"}]
        }
    """.trimIndent()

    @Test fun mapsNullableBackendFactsAndOnlySendsGet() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(response))
            val result = repository(server).load()
            assertTrue(result is NetworkResult.Success)
            val radar = (result as NetworkResult.Success).data
            assertEquals("120.50", radar.assets?.cashBalance)
            assertNull(radar.assets?.totalAssets)
            assertNull(radar.counts?.outbox)
            assertEquals(2, radar.counts?.drafts)
            assertEquals(1, radar.counts?.reconciliationBroken)
            assertEquals("UNKNOWN", radar.markets.single().indicatorStatus)
            assertEquals("行情过旧", radar.warnings.single().message)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/v2/finance-radar", request.path)
            assertEquals(0L, request.body.size)
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test fun failureKeepsOldSnapshotStaleUntilManualRetrySucceeds() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody(response))
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(MockResponse().setBody(response))
            val holder = FinanceRadarStateHolder(repository(server)) { 1234L }
            val first = holder.refresh(FinanceRadarState())
            assertEquals(1234L, first.lastSuccessAt)
            val failed = holder.refresh(first)
            assertEquals("服务暂时不可用，请稍后重试", failed.error)
            assertEquals(first.snapshot, failed.snapshot)
            assertEquals(first.lastSuccessAt, failed.lastSuccessAt)
            assertTrue(failed.isStale)
            assertTrue(failed.copy(loading = true, error = null).isStale)
            val retried = holder.refresh(failed)
            assertNull(retried.error)
            assertFalse(retried.isStale)
            assertEquals(3, server.requestCount)
            // A restored process starts from a fresh state, never from the old snapshot.
            assertNull(FinanceRadarState().snapshot)
            assertNull(FinanceRadarState().lastSuccessAt)
        } finally {
            server.shutdown()
        }
    }

    @Test fun loginExpiryAndConnectionFailuresHaveChineseRetryStates() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(401))
            val result = repository(server).load()
            assertTrue(result is NetworkResult.Failure)
            assertEquals("登录已失效，请重新登录", (result as NetworkResult.Failure).message)
            assertEquals("网络连接失败，请检查网络后重试", UnknownHostException().toUserMessage())
            assertEquals("请求超时，请稍后重试", SocketTimeoutException().toUserMessage())
        } finally {
            server.shutdown()
        }
    }
}
