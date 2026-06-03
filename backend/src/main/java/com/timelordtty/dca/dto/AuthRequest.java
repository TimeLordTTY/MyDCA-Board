package com.timelordtty.dca.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 认证请求DTO
 */
@Data
public class AuthRequest {
    @NotBlank(message = "用户名不能为空")
    /**
     * 展示或唯一标识字段，用于人工识别和业务查找。
     */
    private String username;

    @NotBlank(message = "密码不能为空")
    /**
     * 认证相关字段，仅用于当次请求或安全校验，不应在日志中明文输出。
     */
    private String password;

    /**
     * 展示或唯一标识字段，用于人工识别和业务查找。
     */
    private String nickname;
    /**
     * 用户联系方式，用于账户资料展示或后续通知扩展。
     */
    private String email;
    /**
     * 用户联系方式，用于账户资料展示或后续通知扩展。
     */
    private String phone;
}

