package com.timelordtty.mydca.auth

import com.timelordtty.mydca.core.network.AuthTokenProvider
import com.timelordtty.mydca.core.network.UnauthorizedHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Android 认证状态，不在状态对象中保存密码或完整 Token。 */
sealed interface AuthState {
    data object Initializing : AuthState
    data object Unauthenticated : AuthState
    data object Authenticating : AuthState
    data class Authenticated(val displayName: String? = null) : AuthState
    data class AuthFailed(val message: String) : AuthState
    data object Expired : AuthState
}

/** 统一维护内存 Token、持久化恢复和 401 幂等失效。 */
class AuthSession(
    private val tokenStore: TokenStore,
) : AuthTokenProvider, UnauthorizedHandler {
    private val mutableState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val state: StateFlow<AuthState> = mutableState.asStateFlow()
    private var accessToken: String? = null

    @Synchronized
    fun restore() {
        try {
            accessToken = tokenStore.read()?.takeIf { it.isNotBlank() }
            mutableState.value = if (accessToken == null) AuthState.Unauthenticated else AuthState.Authenticated()
        } catch (_: Throwable) {
            accessToken = null
            runCatching { tokenStore.clear() }
            mutableState.value = AuthState.Unauthenticated
        }
    }

    @Synchronized
    fun beginAuthentication() {
        mutableState.value = AuthState.Authenticating
    }

    @Synchronized
    fun authenticate(token: String, displayName: String?) {
        require(token.isNotBlank()) { "登录响应缺少 Token" }
        tokenStore.write(token)
        accessToken = token
        mutableState.value = AuthState.Authenticated(displayName)
    }

    @Synchronized
    fun failAuthentication(message: String) {
        accessToken = null
        runCatching { tokenStore.clear() }
        mutableState.value = AuthState.AuthFailed(message)
    }

    @Synchronized
    fun logout() {
        accessToken = null
        runCatching { tokenStore.clear() }
        mutableState.value = AuthState.Unauthenticated
    }

    @Synchronized
    override fun onUnauthorized() {
        if (mutableState.value == AuthState.Expired || mutableState.value == AuthState.Unauthenticated) return
        accessToken = null
        runCatching { tokenStore.clear() }
        mutableState.value = AuthState.Expired
    }

    @Synchronized
    override fun token(): String? = accessToken
}
