package com.timelordtty.mydca.data.dto

/** 登录请求只在当前网络调用中持有密码。 */
data class AuthRequestDto(
    val username: String,
    val password: String,
)

/** 后端当前只返回 access token，不存在 refresh token。 */
data class AuthResponseDto(
    val token: String,
    val user: AuthUserDto? = null,
)

/** 登录响应中的非敏感用户摘要。 */
data class AuthUserDto(
    val id: Long? = null,
    val username: String? = null,
    val nickname: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val familyId: Long? = null,
)
