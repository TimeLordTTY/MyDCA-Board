package com.timelordtty.mydca.core.network

import okhttp3.Interceptor
import okhttp3.HttpUrl
import okhttp3.Response

/**
 * 统一添加 Authorization Header 的网络拦截器。
 *
 * 没有 token 时保持请求不带认证头，避免把占位值误发送到服务端。
 */
class AuthInterceptor(
    private val tokenProvider: AuthTokenProvider,
    private val apiBaseUrl: HttpUrl,
    private val unauthorizedHandler: UnauthorizedHandler,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val isApiHost = original.url.scheme == apiBaseUrl.scheme &&
            original.url.host == apiBaseUrl.host &&
            original.url.port == apiBaseUrl.port
        val isAuthRequest = original.url.encodedPath.contains(AUTH_PATH_SEGMENT)
        val token = tokenProvider.token()
        val request = if (!isApiHost || isAuthRequest || token == null) {
            original
        } else {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request).also { response ->
            if (isApiHost && !isAuthRequest && response.code == 401) {
                unauthorizedHandler.onUnauthorized()
            }
        }
    }

    private companion object {
        const val AUTH_PATH_SEGMENT = "/api/v2/auth/"
    }
}
