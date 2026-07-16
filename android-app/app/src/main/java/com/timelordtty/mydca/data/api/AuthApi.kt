package com.timelordtty.mydca.data.api

import com.timelordtty.mydca.data.dto.AuthRequestDto
import com.timelordtty.mydca.data.dto.AuthResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

/** 后端现有 JWT 登录与登出契约。 */
interface AuthApi {
    @POST("api/v2/auth/login")
    suspend fun login(@Body request: AuthRequestDto): AuthResponseDto

    @POST("api/v2/auth/logout")
    suspend fun logout()
}
