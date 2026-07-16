package com.timelordtty.mydca.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthInterceptorTest {
    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun loginRequestDoesNotCarryStaleAuthorization() {
        server.enqueue(MockResponse().setResponseCode(200))
        val client = client(token = "stale-token")

        client.newCall(Request.Builder().url(server.url("/api/v2/auth/login")).build()).execute().close()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun protectedRequestCarriesBearerToken() {
        server.enqueue(MockResponse().setResponseCode(200))
        val client = client(token = "fixture-token")

        client.newCall(Request.Builder().url(server.url("/api/v2/drafts")).build()).execute().close()

        assertEquals("Bearer fixture-token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun unauthorizedResponseInvalidatesOnceWithoutRetry() {
        server.enqueue(MockResponse().setResponseCode(401))
        var unauthorizedCount = 0
        val client = client(token = "fixture-token") { unauthorizedCount += 1 }

        client.newCall(Request.Builder().url(server.url("/api/v2/drafts")).build()).execute().close()

        assertEquals(1, unauthorizedCount)
        assertEquals(1, server.requestCount)
    }

    private fun client(token: String, onUnauthorized: () -> Unit = {}): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                AuthInterceptor(
                    tokenProvider = InMemoryAuthTokenProvider(token),
                    apiBaseUrl = server.url("/"),
                    unauthorizedHandler = UnauthorizedHandler(onUnauthorized),
                ),
            )
            .build()
    }
}
