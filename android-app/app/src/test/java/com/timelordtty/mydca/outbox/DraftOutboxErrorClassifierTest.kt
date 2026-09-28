package com.timelordtty.mydca.outbox

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class DraftOutboxErrorClassifierTest {
    @Test
    fun networkInterruptionsAndTimeoutsAreRetryable() {
        assertEquals(
            DraftOutboxErrorCategory.RETRYABLE,
            DraftOutboxErrorClassifier.classify(SocketTimeoutException("timeout")),
        )
        assertEquals(
            DraftOutboxErrorCategory.RETRYABLE,
            DraftOutboxErrorClassifier.classify(UnknownHostException("offline")),
        )
        assertEquals(
            DraftOutboxErrorCategory.RETRYABLE,
            DraftOutboxErrorClassifier.classify(IOException("connection reset")),
        )
    }

    @Test
    fun serverErrorsAreRetryable() {
        assertEquals(DraftOutboxErrorCategory.RETRYABLE, DraftOutboxErrorClassifier.classify(httpError(500)))
        assertEquals(DraftOutboxErrorCategory.RETRYABLE, DraftOutboxErrorClassifier.classify(httpError(503)))
        assertTrue(DraftOutboxErrorClassifier.isAutoRetryEligible(DraftOutboxErrorCategory.RETRYABLE))
    }

    @Test
    fun unauthorizedFailuresPauseForLogin() {
        assertEquals(DraftOutboxErrorCategory.AUTH_REQUIRED, DraftOutboxErrorClassifier.classify(httpError(401)))
        assertEquals(DraftOutboxErrorCategory.AUTH_REQUIRED, DraftOutboxErrorClassifier.classify(httpError(403)))
        assertFalse(DraftOutboxErrorClassifier.isAutoRetryEligible(DraftOutboxErrorCategory.AUTH_REQUIRED))
    }

    @Test
    fun businessErrorsAreNotAutoRetryable() {
        listOf(400, 409, 422).forEach { code ->
            assertEquals(DraftOutboxErrorCategory.BUSINESS, DraftOutboxErrorClassifier.classify(httpError(code)))
        }
        assertFalse(DraftOutboxErrorClassifier.isAutoRetryEligible(DraftOutboxErrorCategory.BUSINESS))
    }

    @Test
    fun unknownFailuresAreNotAutoRetryable() {
        assertEquals(DraftOutboxErrorCategory.UNKNOWN, DraftOutboxErrorClassifier.classify(IllegalStateException("bug")))
        assertEquals(DraftOutboxErrorCategory.UNKNOWN, DraftOutboxErrorClassifier.classify(null))
        assertFalse(DraftOutboxErrorClassifier.isAutoRetryEligible(DraftOutboxErrorCategory.UNKNOWN))
    }

    @Test
    fun messageDoesNotKeepServerText() {
        assertEquals(
            DraftOutboxErrorCategory.AUTH_REQUIRED.label,
            DraftOutboxErrorClassifier.messageFor(DraftOutboxErrorCategory.AUTH_REQUIRED, "卡号 123456"),
        )
        assertEquals(
            DraftOutboxErrorCategory.RETRYABLE.label,
            DraftOutboxErrorClassifier.messageFor(DraftOutboxErrorCategory.RETRYABLE, "  "),
        )
    }

    private fun httpError(code: Int): Throwable =
        HttpException(Response.error<Any>(code, "".toResponseBody("text/plain".toMediaType())))
}
