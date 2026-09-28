package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.core.network.ApiConfig
import com.timelordtty.mydca.core.network.NetworkModule
import com.timelordtty.mydca.data.dto.PendingSettlementOrderDto
import com.timelordtty.mydca.data.repository.SettlementRepository
import com.timelordtty.mydca.data.repository.WealthRepository
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 待结算页状态装配测试：首次加载、产品名称解析、刷新失败保留上次成功数据。
 *
 * 状态装配只读取待结算订单与产品名称，不做任何 preview / confirm。
 */
class SettlementStateHolderTest {
    private class PathDispatcher(private val handler: (String) -> MockResponse) : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = handler(request.path.orEmpty())
    }

    private fun ok(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private val pendingOrdersJson = """
        [
          {
            "id": 11,
            "orderId": "ORD-20260928-AAA111",
            "productId": 5,
            "orderType": "BUY",
            "amount": 1000.00,
            "status": "PENDING",
            "expectedConfirmDate": "2026-09-29"
          },
          {
            "id": 12,
            "orderId": "ORD-20260928-BBB222",
            "productId": 6,
            "orderType": "SELL",
            "shares": 100.00,
            "status": "PENDING"
          }
        ]
    """.trimIndent()

    private val productsJson = """[{"id":5,"productCode":"510300","productName":"沪深300ETF","assetType":"ETF","currency":"CNY","isActive":true}]"""

    @Test
    fun loadPendingParsesOrdersAndResolvesProductNames() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.dispatcher = PathDispatcher { path ->
                if (path.startsWith("/api/v2/products")) ok(productsJson) else ok(pendingOrdersJson)
            }
            val api = NetworkModule.createApi(ApiConfig(server.url("/").toString()))
            val holder = SettlementStateHolder(
                repository = SettlementRepository(api),
                wealthRepository = WealthRepository(api),
                clock = { 1234L },
            )

            val state = holder.loadPending()

            assertEquals(2, state.orders.size)
            assertEquals("ORD-20260928-AAA111", state.orders[0].orderId)
            assertEquals(2, SettlementEditState.pendingCount(state.orders))
            assertEquals("沪深300ETF", state.productNames[5L])
            assertEquals(1234L, state.lastUpdatedAt)
            assertNull(state.errorMessage)
            assertEquals(false, state.isLoading)
            assertEquals(false, state.isRefreshing)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun loadPendingKeepsPreviousOrdersWhenRefreshFails() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.dispatcher = PathDispatcher { path ->
                if (path.startsWith("/api/v2/products")) {
                    ok(productsJson)
                } else {
                    MockResponse().setResponseCode(500).setBody("boom")
                }
            }
            val api = NetworkModule.createApi(ApiConfig(server.url("/").toString()))
            val holder = SettlementStateHolder(
                repository = SettlementRepository(api),
                wealthRepository = WealthRepository(api),
                clock = { 9999L },
            )
            val previous = PendingSettlementUiState(
                isLoading = false,
                isRefreshing = true,
                orders = listOf(PendingSettlementOrderDto(orderId = "ORD-KEPT", orderType = "BUY", status = "PENDING")),
                lastUpdatedAt = 555L,
            )

            val state = holder.loadPending(previous)

            assertEquals(1, state.orders.size)
            assertEquals("ORD-KEPT", state.orders[0].orderId)
            assertEquals("服务暂时不可用，请稍后重试", state.errorMessage)
            // 刷新失败不伪造新的刷新时间。
            assertEquals(555L, state.lastUpdatedAt)
            assertEquals(false, state.isRefreshing)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun productListFailureDoesNotHidePendingOrders() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.dispatcher = PathDispatcher { path ->
                if (path.startsWith("/api/v2/products")) {
                    MockResponse().setResponseCode(500).setBody("boom")
                } else {
                    ok(pendingOrdersJson)
                }
            }
            val api = NetworkModule.createApi(ApiConfig(server.url("/").toString()))
            val holder = SettlementStateHolder(
                repository = SettlementRepository(api),
                wealthRepository = WealthRepository(api),
                clock = { 1L },
            )

            val state = holder.loadPending()

            assertEquals(2, state.orders.size)
            assertTrue(state.productNames.isEmpty())
            assertNull(state.errorMessage)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun loadPendingWithoutProductRepositoryStillReturnsOrders() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.dispatcher = PathDispatcher { ok(pendingOrdersJson) }
            val api = NetworkModule.createApi(ApiConfig(server.url("/").toString()))
            val holder = SettlementStateHolder(repository = SettlementRepository(api), wealthRepository = null)

            val state = holder.loadPending()

            assertEquals(2, state.orders.size)
            assertTrue(state.productNames.isEmpty())
        } finally {
            server.shutdown()
        }
    }
}