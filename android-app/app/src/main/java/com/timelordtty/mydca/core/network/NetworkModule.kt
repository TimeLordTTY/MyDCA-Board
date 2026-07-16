package com.timelordtty.mydca.core.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.timelordtty.mydca.data.api.AuthApi
import com.timelordtty.mydca.data.api.WealthHubApi
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Android 网络客户端装配入口。
 * 只装配后端 API 边界，不连接数据库，也不会在客户端记录 Token 明文。
 */
object NetworkModule {
    data class ApiServices(
        val authApi: AuthApi,
        val wealthHubApi: WealthHubApi,
    )

    fun createServices(
        config: ApiConfig = ApiConfig(),
        tokenProvider: AuthTokenProvider = InMemoryAuthTokenProvider(),
        unauthorizedHandler: UnauthorizedHandler = UnauthorizedHandler {},
    ): ApiServices {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val client = OkHttpClient.Builder()
            .addInterceptor(
                AuthInterceptor(
                    tokenProvider = tokenProvider,
                    apiBaseUrl = config.normalizedBaseUrl.toHttpUrl(),
                    unauthorizedHandler = unauthorizedHandler,
                ),
            )
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(config.normalizedBaseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return ApiServices(
            authApi = retrofit.create(AuthApi::class.java),
            wealthHubApi = retrofit.create(WealthHubApi::class.java),
        )
    }

    fun createApi(
        config: ApiConfig = ApiConfig(),
        tokenProvider: AuthTokenProvider = InMemoryAuthTokenProvider(),
    ): WealthHubApi = createServices(config, tokenProvider).wealthHubApi
}
