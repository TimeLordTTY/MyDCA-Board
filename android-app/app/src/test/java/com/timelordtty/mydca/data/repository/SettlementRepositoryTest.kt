package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.ApiConfig
import com.timelordtty.mydca.core.network.NetworkModule
import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.dto.SettlementPreviewRequestDto
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.13.0 人工结算网络边界端到端验证（真实 Retrofit + MockWebServer）。
 *
 * 覆盖：待结算列表加载、preview 只读请求体、confirm 必须携带 freshPreviewToken、失败不显示假成功。
 */
class SettlementRepositoryTest {
    private fun repository(server: MockWebServer): SettlementRepository =
        SettlementRepository(NetworkModule.createApi(ApiConfig(server.url("/").toString())))

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun drainRequests(server: MockWebServer): List<RecordedRequest> =
        generateSequence { server.takeRequest(1, TimeUnit.SECONDS) }.toList()

    @Test
    fun listPendingSettlementsParsesBackendOrders() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                jsonResponse(
                    """
                    [
                      {
                        "id": 11,
                        "orderId": "ORD-20260928-AAA111",
                        "userId": 3,
                        "productId": 5,
                        "orderType": "BUY",
                        "amount": 1000.00,
                        "shares": null,
                        "requestedAt": "2026-09-28T10:00:00",
                        "tradeDate": "2026-09-28",
                        "expectedNavDate": "2026-09-28",
                        "expectedConfirmDate": "2026-09-29",
                        "status": "PENDING",
                        "feeEstimate": 5.00,
                        "note": "定投",
                        "createdAt": "2026-09-28T10:00:00",
                        "updatedAt": "2026-09-28T10:00:00"
                      },
                      {
                        "id": 12,
                        "orderId": "ORD-20260928-BBB222",
                        "productId": 6,
                        "orderType": "REDEMPTION",
                        "shares": 100.5,
                        "status": "PENDING",
                        "expectedConfirmDate": "2026-09-30"
                      }
                    ]
                    """.trimIndent(),
                ),
            )

            val result = repository(server).listPendingSettlements()

            assertTrue(result is NetworkResult.Success)
            val orders = (result as NetworkResult.Success).data
            assertEquals(2, orders.size)
            assertEquals("ORD-20260928-AAA111", orders[0].orderId)
            assertEquals("BUY", orders[0].orderType)
            assertEquals(1000.0, orders[0].amount)
            assertEquals("PENDING", orders[0].status)
            assertEquals("2026-09-29", orders[0].expectedConfirmDate)
            assertEquals(5.0, orders[0].feeEstimate)
            assertEquals("REDEMPTION", orders[1].orderType)
            assertEquals(100.5, orders[1].shares)
            assertNull(orders[1].amount)

            val request = drainRequests(server).single()
            assertEquals("GET", request.method)
            assertEquals("/api/v2/settlements/pending", request.path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun previewSettlementSendsReadOnlyRequestWithoutToken() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                jsonResponse(
                    """
                    {
                      "orderId": "ORD-20260928-AAA111",
                      "orderType": "BUY",
                      "orderTypeLabel": "买入 BUY",
                      "orderStatus": "PENDING",
                      "productId": 5,
                      "productName": "沪深300ETF",
                      "productCode": "510300",
                      "currency": "CNY",
                      "confirmDate": "2026-09-29",
                      "navDate": "2026-09-28",
                      "confirmNav": 1.2345,
                      "confirmShares": null,
                      "confirmAmount": null,
                      "confirmFee": 0.00,
                      "computedShares": 806.00,
                      "computedAmount": null,
                      "totalFundingAmount": 1000.00,
                      "warnings": ["实际份额留空，已按金额与净值计算"],
                      "confirmSupported": true,
                      "blockingReasons": [],
                      "postingsPreview": [
                        {
                          "accountId": 21,
                          "accountName": "券商持仓账户",
                          "accountType": "POSITION",
                          "postingType": "DEBIT",
                          "amount": null,
                          "shares": 806.00,
                          "currency": "CNY",
                          "description": "持仓账户：+806.00 份"
                        },
                        {
                          "accountId": null,
                          "accountName": "手续费账户",
                          "accountType": "FEE",
                          "postingType": "DEBIT",
                          "amount": 0.00,
                          "currency": "CNY",
                          "description": "手续费：0.00 元"
                        }
                      ],
                      "summaryLines": ["持仓账户：+806.00 份", "手续费：0.00 元"],
                      "willCreateSettlementConfirm": true,
                      "willCreateLedgerTxn": true,
                      "willChangeHolding": true,
                      "willChangeCash": false,
                      "freshPreviewToken": "fingerprint-abc",
                      "previewFingerprint": "fingerprint-abc"
                    }
                    """.trimIndent(),
                ),
            )

            val result = repository(server).previewSettlement(
                SettlementPreviewRequestDto(
                    orderId = "ORD-20260928-AAA111",
                    confirmDate = "2026-09-29",
                    navDate = "2026-09-28",
                    confirmNav = 1.2345,
                ),
            )

            assertTrue(result is NetworkResult.Success)
            val preview = (result as NetworkResult.Success).data
            assertTrue(preview.confirmSupported)
            assertEquals("fingerprint-abc", preview.freshPreviewToken)
            assertEquals(806.0, preview.computedShares)
            assertEquals(2, preview.postingsPreview?.size)
            assertEquals("券商持仓账户", preview.postingsPreview?.get(0)?.accountName)
            assertNull(preview.postingsPreview?.get(1)?.accountId)
            assertTrue(preview.willChangeHolding)
            assertFalse(preview.willChangeCash)

            val request = drainRequests(server).single()
            assertEquals("POST", request.method)
            assertEquals("/api/v2/settlements/preview", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("ORD-20260928-AAA111"))
            assertTrue(body.contains("confirmNav"))
            // 只读预览绝不携带 fresh preview 令牌。
            assertFalse(body.contains("freshPreviewToken"))
            // 留空的份额 / 金额不能变成 0。
            assertFalse(body.contains("confirmShares"))
            assertFalse(body.contains("confirmAmount"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun confirmSettlementCarriesFreshPreviewToken() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                jsonResponse(
                    """
                    {
                      "id": 31,
                      "orderId": "ORD-20260928-AAA111",
                      "confirmDate": "2026-09-29",
                      "navDate": "2026-09-28",
                      "confirmNav": 1.2345,
                      "confirmShares": 806.00,
                      "confirmAmount": null,
                      "confirmFee": 0.00,
                      "confirmedAt": "2026-09-29T20:00:00",
                      "note": null
                    }
                    """.trimIndent(),
                ),
            )

            val result = repository(server).confirmSettlement(
                SettlementPreviewRequestDto(
                    orderId = "ORD-20260928-AAA111",
                    confirmDate = "2026-09-29",
                    navDate = "2026-09-28",
                    confirmNav = 1.2345,
                    freshPreviewToken = "fingerprint-abc",
                ),
            )

            assertTrue(result is NetworkResult.Success)
            assertEquals(31L, (result as NetworkResult.Success).data.id)
            assertEquals(806.0, result.data.confirmShares)

            val request = drainRequests(server).single()
            assertEquals("POST", request.method)
            assertEquals("/api/v2/settlements/confirm", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("freshPreviewToken"))
            assertTrue(body.contains("fingerprint-abc"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun previewFailureIsReportedAsFailureNotFakeSuccess() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

            val result = repository(server).previewSettlement(
                SettlementPreviewRequestDto(orderId = "ORD-1", confirmNav = 1.0),
            )

            assertTrue(result is NetworkResult.Failure)
            assertEquals("服务暂时不可用，请稍后重试", (result as NetworkResult.Failure).message)
            assertEquals(1, drainRequests(server).size)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun confirmFailureNeverReportsSuccess() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))

            val result = repository(server).confirmSettlement(
                SettlementPreviewRequestDto(orderId = "ORD-1", freshPreviewToken = "fingerprint-abc"),
            )

            assertTrue(result is NetworkResult.Failure)
            assertEquals("登录已失效，请重新登录", (result as NetworkResult.Failure).message)
            assertEquals(1, drainRequests(server).size)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun previewOnlyCallsPreviewEndpoint() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(jsonResponse("""{"orderId":"ORD-1","confirmSupported":false,"blockingReasons":["订单不存在：ORD-1"]}"""))

            repository(server).previewSettlement(SettlementPreviewRequestDto(orderId = "ORD-1"))

            val requests = drainRequests(server)
            assertEquals(1, requests.size)
            assertEquals("/api/v2/settlements/preview", requests.single().path)
            assertFalse(requests.any { it.path.orEmpty().contains("/confirm") })
        } finally {
            server.shutdown()
        }
    }
}