package com.timelordtty.mydca.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 统一添加 Authorization Header 的网络拦截器。
 *
 * 没有 token 时保持请求不带认证头，避免把占位值误发送到服务端。
 */
class AuthInterceptor(
    private val tokenProvider: AuthTokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenProvider.token()
        val request = if (token == null) {
            original
        } else {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}
