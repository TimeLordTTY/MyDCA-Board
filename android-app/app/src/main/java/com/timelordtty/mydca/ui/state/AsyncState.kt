package com.timelordtty.mydca.ui.state

/**
 * Android 页面通用异步状态。
 * 首版只区分加载、成功和可恢复错误，避免把网络异常误认为真实空数据。
 */
sealed interface AsyncState<out T> {
    data object Loading : AsyncState<Nothing>
    data class Success<T>(val data: T) : AsyncState<T>
    data class Error(val message: String) : AsyncState<Nothing>
}
