package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.dto.DataReadinessDto
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/** Only owner-scoped GET; no local storage or operational endpoints. */
class DataReadinessRepository(private val api: WealthHubApi) {
    suspend fun read(month: String): NetworkResult<DataReadinessDto> = try {
        val data = api.dataReadiness("PERSONAL", month)
        require(data.scope == "PERSONAL" && data.month == month)
        NetworkResult.Success(data)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        NetworkResult.Failure(when (error) {
            is HttpException, is SocketTimeoutException -> error.toUserMessage()
            is IOException -> "网络连接失败，请检查网络后重试"
            else -> "数据就绪诊断不可读取，请稍后重试"
        }, error)
    }
}
