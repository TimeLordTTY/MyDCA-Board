package com.timelordtty.dca.dto;

import lombok.Data;

/**
 * 认证响应DTO
 */
@Data
/**
 * 业务注释规范化: AuthResponse DTO 数据传输对象，用于承载请求参数或响应结果，属于前后端契约。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class AuthResponse {
    /**
     * 业务注释规范化: token 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String token;
    /**
     * 业务注释规范化: user 业务字段，承载该对象在后端流程中的核心属性。
     */
    private UserInfo user;

    @Data
    public static class UserInfo {
        /**
         * 业务注释规范化: 主键 ID，用于在后端内部唯一定位该业务记录。
         */
        private Long id;
        /**
         * 业务注释规范化: username 业务字段，承载该对象在后端流程中的核心属性。
         */
        private String username;
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
        /**
         * 业务注释规范化: 所属家庭 ID，用于家庭视角下的数据隔离。
         */
        private Long familyId;
    }
}

