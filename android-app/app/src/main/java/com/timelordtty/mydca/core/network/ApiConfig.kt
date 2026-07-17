package com.timelordtty.mydca.core.network

/**
 * Android 侧 API 基础配置。
 * 默认地址使用已验证的 HTTPS 服务入口；开发联调时仍可在界面中临时改为模拟器本机地址。
 */
data class ApiConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
) {
    val normalizedBaseUrl: String = normalizeBaseUrl(baseUrl)

    companion object {
        const val DEFAULT_BASE_URL = "https://www.timelordtty.cn/"

        fun normalizeBaseUrl(value: String): String {
            val trimmed = value.trim()
            require(trimmed.isNotBlank()) { "BaseUrl 不能为空" }
            require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                "BaseUrl 必须以 http:// 或 https:// 开头"
            }
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }
    }
}
