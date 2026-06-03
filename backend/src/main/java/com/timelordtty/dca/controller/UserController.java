package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/api/v2/users")
public class UserController {

    /**
     * 依赖的 UserService 服务，用于复用该领域的业务校验和事务逻辑。
     */
    private final UserService userService;

    /**
     * 处理写入类 API，将请求参数校验后委托给 Service 层。
     * 是否产生账本、账户或订单变更由对应 Service 事务边界决定。
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 返回当前场景的业务数据，用于前后端传递或服务层计算。
     */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse.UserInfo> getCurrentUser() {
        AuthResponse.UserInfo userInfo = userService.getCurrentUser();
        return ResponseEntity.ok(userInfo);
    }

    /**
     * 更新当前用户资料
     * PUT /api/v2/users/me
     */
    @PutMapping("/me")
    public ResponseEntity<AuthResponse.UserInfo> updateProfile(@RequestBody UpdateProfileRequest req) {
        AuthResponse.UserInfo userInfo = userService.updateCurrentUserProfile(req.getNickname(), req.getEmail(), req.getPhone());
        return ResponseEntity.ok(userInfo);
    }

    /**
     * 修改密码
     * POST /api/v2/users/change-password
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody ChangePasswordRequest req) {
        userService.changePassword(req.getOldPassword(), req.getNewPassword());
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        return ResponseEntity.ok(resp);
    }

    public static class UpdateProfileRequest {
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
         * 返回展示或唯一标识字段，用于人工识别和业务查找。
         */
        public String getNickname() { return nickname; }
        /**
         * 设置展示或唯一标识字段，用于人工识别和业务查找。
         */
        public void setNickname(String nickname) { this.nickname = nickname; }
        /**
         * 返回用户联系方式，用于账户资料展示或后续通知扩展。
         */
        public String getEmail() { return email; }
        /**
         * 设置用户联系方式，用于账户资料展示或后续通知扩展。
         */
        public void setEmail(String email) { this.email = email; }
        /**
         * 返回用户联系方式，用于账户资料展示或后续通知扩展。
         */
        public String getPhone() { return phone; }
        /**
         * 设置用户联系方式，用于账户资料展示或后续通知扩展。
         */
        public void setPhone(String phone) { this.phone = phone; }
    }

    public static class ChangePasswordRequest {
        /**
         * 认证相关字段，仅用于当次请求或安全校验，不应在日志中明文输出。
         */
        private String oldPassword;
        /**
         * 认证相关字段，仅用于当次请求或安全校验，不应在日志中明文输出。
         */
        private String newPassword;

        /**
         * 返回认证相关字段，仅用于当次请求或安全校验，不应在日志中明文输出。
         */
        public String getOldPassword() { return oldPassword; }
        /**
         * 设置认证相关字段，仅用于当次请求或安全校验，不应在日志中明文输出。
         */
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
        /**
         * 返回认证相关字段，仅用于当次请求或安全校验，不应在日志中明文输出。
         */
        public String getNewPassword() { return newPassword; }
        /**
         * 设置认证相关字段，仅用于当次请求或安全校验，不应在日志中明文输出。
         */
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
}

