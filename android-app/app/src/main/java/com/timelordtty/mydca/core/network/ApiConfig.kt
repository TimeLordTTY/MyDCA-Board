package com.timelordtty.mydca.core.network

/**
 * Android 侧 API 基础配置。
 * 默认地址仅指向 Android 模拟器访问本机开发服务的常见地址，不包含任何生产域名或密钥。
 */
data class ApiConfig(
    val baseUrl: String = DEFAULT_LOCAL_BASE_URL,
) {
    val normalizedBaseUrl: String = normalizeBaseUrl(baseUrl)

    companion object {
        const val DEFAULT_LOCAL_BASE_URL = "http://10.0.2.2:8080/"

        fun normalizeBaseUrl(value: String): String {
            val trimmed = value.trim()
            require(trimmed.isNotBlank()) { "BaseUrl 不能为空" }
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }
    }
}
