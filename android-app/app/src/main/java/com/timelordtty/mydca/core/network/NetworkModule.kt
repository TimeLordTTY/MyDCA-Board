package com.timelordtty.mydca.core.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.timelordtty.mydca.data.api.WealthHubApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Android 网络客户端装配入口。
 *
 * 首版只声明 API 边界；UI 默认可以使用空状态，不强制连接真实服务。
 */
object NetworkModule {
    fun createApi(
        config: ApiConfig = ApiConfig(),
        tokenProvider: AuthTokenProvider = InMemoryAuthTokenProvider(),
    ): WealthHubApi {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenProvider))
            .build()

        return Retrofit.Builder()
            .baseUrl(config.normalizedBaseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WealthHubApi::class.java)
    }
}
