package com.timelordtty.dca.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 认证请求DTO
 */
@Data
/**
 * 业务注释规范化: AuthRequest DTO 数据传输对象，用于承载请求参数或响应结果，属于前后端契约。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class AuthRequest {
    @NotBlank(message = "用户名不能为空")
    /**
     * 业务注释规范化: username 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String username;

    @NotBlank(message = "密码不能为空")
    /**
     * 业务注释规范化: password 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String password;

    /**
     * 业务注释规范化: nickname 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String nickname;
    /**
     * 业务注释规范化: email 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String email;
    /**
     * 业务注释规范化: phone 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String phone;
}

