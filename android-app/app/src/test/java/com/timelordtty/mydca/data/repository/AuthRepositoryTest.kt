package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.auth.AuthSession
import com.timelordtty.mydca.auth.AuthState
import com.timelordtty.mydca.auth.InMemoryTokenStore
import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.api.AuthApi
import com.timelordtty.mydca.data.dto.AuthRequestDto
import com.timelordtty.mydca.data.dto.AuthResponseDto
import com.timelordtty.mydca.data.dto.AuthUserDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {
    @Test
    fun successfulLoginStoresTokenAndAuthenticates() = runBlocking {
        val store = InMemoryTokenStore()
        val session = AuthSession(store).apply { restore() }
        val repository = AuthRepository(FakeAuthApi(), session)

        val result = repository.login(" owner ", "test-password")

        assertTrue(result is NetworkResult.Success)
        assertEquals("fixture-token", store.storedToken)
        assertEquals(AuthState.Authenticated("主人"), session.state.value)
    }

    @Test
    fun failedLoginDoesNotStoreTokenOrExposeServerDetails() = runBlocking {
        val store = InMemoryTokenStore()
        val session = AuthSession(store).apply { restore() }
        val repository = AuthRepository(FakeAuthApi(loginError = IllegalStateException("token=secret-value")), session)

        val result = repository.login("owner", "wrong-password")

        assertTrue(result is NetworkResult.Failure)
        assertNull(store.storedToken)
        val message = (result as NetworkResult.Failure).message
        assertFalse(message.contains("secret-value"))
        assertEquals(AuthState.AuthFailed(message), session.state.value)
    }

    @Test
    fun logoutClearsLocalTokenEvenWhenRemoteFails() = runBlocking {
        val store = InMemoryTokenStore("fixture-token")
        val session = AuthSession(store).apply { restore() }
        val repository = AuthRepository(FakeAuthApi(logoutError = IllegalStateException("offline")), session)

        repository.logout()

        assertNull(store.storedToken)
        assertEquals(AuthState.Unauthenticated, session.state.value)
    }

    private class FakeAuthApi(
        private val loginError: Throwable? = null,
        private val logoutError: Throwable? = null,
    ) : AuthApi {
        override suspend fun login(request: AuthRequestDto): AuthResponseDto {
            loginError?.let { throw it }
            assertEquals("owner", request.username)
            return AuthResponseDto("fixture-token", AuthUserDto(username = "owner", nickname = "主人"))
        }

        override suspend fun logout() {
            logoutError?.let { throw it }
        }
    }
}
