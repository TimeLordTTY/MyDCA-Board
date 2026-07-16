package com.timelordtty.mydca.core.network

/** 内存认证头读取边界；持久化由独立 TokenStore 负责。 */
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

/** 受保护请求返回 401 时的会话失效边界。 */
fun interface UnauthorizedHandler {
    fun onUnauthorized()
}
