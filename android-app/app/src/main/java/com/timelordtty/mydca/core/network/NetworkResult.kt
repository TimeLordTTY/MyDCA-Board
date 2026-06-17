package com.timelordtty.mydca.core.network

/**
 * 移动端统一网络结果结构，便于后续把 Retrofit 异常转换成可恢复的 UI 状态。
 */
sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>
    data class Failure(val message: String, val cause: Throwable? = null) : NetworkResult<Nothing>
}
