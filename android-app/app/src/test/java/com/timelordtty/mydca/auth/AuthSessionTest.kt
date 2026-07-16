package com.timelordtty.mydca.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AuthSessionTest {
    @Test
    fun restoreExistingTokenAuthenticatesSession() {
        val session = AuthSession(InMemoryTokenStore("stored-token"))

        session.restore()

        assertEquals(AuthState.Authenticated(), session.state.value)
        assertEquals("stored-token", session.token())
    }

    @Test
    fun corruptTokenClearsStoreAndFallsBackToUnauthenticated() {
        val store = ThrowingTokenStore()
        val session = AuthSession(store)

        session.restore()

        assertEquals(AuthState.Unauthenticated, session.state.value)
        assertEquals(1, store.clearCount)
        assertNull(session.token())
    }

    @Test
    fun logoutAndRepeatedUnauthorizedAreIdempotent() {
        val store = InMemoryTokenStore("stored-token")
        val session = AuthSession(store)
        session.restore()

        session.onUnauthorized()
        session.onUnauthorized()

        assertEquals(AuthState.Expired, session.state.value)
        assertNull(store.storedToken)
        session.logout()
        session.logout()
        assertEquals(AuthState.Unauthenticated, session.state.value)
    }

    @Test
    fun concurrentUnauthorizedResponsesConvergeOnOneExpiredSession() {
        val store = InMemoryTokenStore("stored-token")
        val session = AuthSession(store).apply { restore() }
        val executor = Executors.newFixedThreadPool(4)

        repeat(12) { executor.submit { session.onUnauthorized() } }
        executor.shutdown()

        executor.awaitTermination(2, TimeUnit.SECONDS)
        assertEquals(AuthState.Expired, session.state.value)
        assertNull(session.token())
        assertNull(store.storedToken)
    }

    private class ThrowingTokenStore : TokenStore {
        var clearCount = 0
        override fun read(): String? = error("corrupt encrypted payload")
        override fun write(token: String) = Unit
        override fun clear() { clearCount += 1 }
    }
}
