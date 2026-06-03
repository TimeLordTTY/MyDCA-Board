package com.timelordtty.dca.dto;

import lombok.Data;

/**
 * 认证响应DTO
 */
@Data
public class AuthResponse {
    /**
     * 认证相关字段，仅用于当次请求或安全校验，不应在日志中明文输出。
     */
    private String token;
    /**
     * 请求或响应字段，用于前后端传递该场景的业务信息。
     */
    private UserInfo user;

    @Data
    public static class UserInfo {
        /**
         * 主键 ID，用于数据库内部唯一定位记录。
         */
        private Long id;
        /**
         * 展示或唯一标识字段，用于人工识别和业务查找。
         */
        private String username;
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
        /**
         * 所属家庭 ID，用于家庭视角聚合。
         */
        private Long familyId;
    }
}

