package com.timelordtty.mydca.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证 Android API 基础配置不会吞掉路径分隔符。
 */
class ApiConfigTest {
    @Test
    fun normalizeBaseUrlAppendsTrailingSlash() {
        assertEquals("http://10.0.2.2:8080/", ApiConfig.normalizeBaseUrl("http://10.0.2.2:8080"))
    }

    @Test
    fun normalizeBaseUrlKeepsExistingTrailingSlash() {
        assertEquals("http://10.0.2.2:8080/", ApiConfig.normalizeBaseUrl("http://10.0.2.2:8080/"))
    }
}
