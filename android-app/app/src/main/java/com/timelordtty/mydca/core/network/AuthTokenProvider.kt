package com.timelordtty.mydca.core.network

/**
 * Token 提供边界。
 *
 * 首版不实现真实登录和持久化，后续应接入安全存储，不得硬编码真实 token。
 */
interface AuthTokenProvider {
    fun token(): String?
}

class InMemoryAuthTokenProvider(
    private var currentToken: String? = null,
) : AuthTokenProvider {
    override fun token(): String? = currentToken?.takeIf { it.isNotBlank() }

    fun updateToken(token: String?) {
        currentToken = token
    }
}
