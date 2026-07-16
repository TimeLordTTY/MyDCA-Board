package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.auth.AuthSession
import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.api.AuthApi
import com.timelordtty.mydca.data.dto.AuthRequestDto

/** 串联后端真实登录契约与本地安全会话。 */
class AuthRepository(
    private val api: AuthApi,
    private val session: AuthSession,
) {
    suspend fun login(username: String, password: String): NetworkResult<Unit> {
        session.beginAuthentication()
        return try {
            val response = api.login(AuthRequestDto(username.trim(), password))
            val displayName = response.user?.nickname ?: response.user?.username
            session.authenticate(response.token, displayName)
            NetworkResult.Success(Unit)
        } catch (error: Throwable) {
            val message = "登录失败，请检查用户名、密码和服务地址"
            session.failAuthentication(message)
            NetworkResult.Failure(message, error)
        }
    }

    suspend fun logout() {
        try {
            api.logout()
        } catch (_: Throwable) {
            // 无状态 JWT 以本地凭据清理为准，远端失败不阻断退出。
        } finally {
            session.logout()
        }
    }
}
