package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.NetworkResult
import okhttp3.internal.http2.ConnectionShutdownException
import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal suspend fun <T> safeNetworkCall(block: suspend () -> T): NetworkResult<T> {
    return try {
        NetworkResult.Success(block())
    } catch (error: Throwable) {
        NetworkResult.Failure(error.toUserMessage(), error)
    }
}

internal fun Throwable.toUserMessage(): String {
    return when (this) {
        is HttpException -> when (code()) {
            401 -> "登录已失效，请重新登录"
            403 -> "当前账号无权访问该数据"
            in 500..599 -> "服务暂时不可用，请稍后重试"
            else -> "接口请求失败(${code()})"
        }
        is SocketTimeoutException -> "请求超时，请稍后重试"
        is UnknownHostException, is ConnectionShutdownException -> "网络连接失败，请检查网络后重试"
        else -> message ?: "移动端接口调用失败"
    }
}
