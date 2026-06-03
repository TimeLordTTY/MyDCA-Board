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
/**
 * 业务注释规范化: UserController 控制器，负责接收前端请求、读取用户上下文，并将业务处理委托给服务层。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class UserController {

    /**
     * 业务注释规范化: userService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final UserService userService;

    /**
     * 业务注释规范化: 处理 UserController 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param userService userService 业务字段，承载该对象在后端流程中的核心属性。
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    /**
     * 业务注释规范化: 查询 getCurrentUser 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<AuthResponse.UserInfo> getCurrentUser() {
        AuthResponse.UserInfo userInfo = userService.getCurrentUser();
        return ResponseEntity.ok(userInfo);
    }

    /**
     * 更新当前用户资料
     * PUT /api/v2/users/me
     */
    @PutMapping("/me")
    /**
     * 业务注释规范化: 更新 updateProfile 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param req req 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<AuthResponse.UserInfo> updateProfile(@RequestBody UpdateProfileRequest req) {
        AuthResponse.UserInfo userInfo = userService.updateCurrentUserProfile(req.getNickname(), req.getEmail(), req.getPhone());
        return ResponseEntity.ok(userInfo);
    }

    /**
     * 修改密码
     * POST /api/v2/users/change-password
     */
    @PostMapping("/change-password")
    /**
     * 业务注释规范化: 处理 changePassword 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param req req 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody ChangePasswordRequest req) {
        userService.changePassword(req.getOldPassword(), req.getNewPassword());
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        return ResponseEntity.ok(resp);
    }

    public static class UpdateProfileRequest {
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
         * 业务注释规范化: 查询 getNickname 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getNickname() { return nickname; }
        /**
         * 业务注释规范化: 处理 setNickname 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param nickname nickname 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setNickname(String nickname) { this.nickname = nickname; }
        /**
         * 业务注释规范化: 查询 getEmail 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getEmail() { return email; }
        /**
         * 业务注释规范化: 处理 setEmail 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param email email 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setEmail(String email) { this.email = email; }
        /**
         * 业务注释规范化: 查询 getPhone 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getPhone() { return phone; }
        /**
         * 业务注释规范化: 处理 setPhone 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param phone phone 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setPhone(String phone) { this.phone = phone; }
    }

    public static class ChangePasswordRequest {
        /**
         * 业务注释规范化: oldPassword 业务字段，承载该对象在后端流程中的核心属性。
         */
        private String oldPassword;
        /**
         * 业务注释规范化: newPassword 业务字段，承载该对象在后端流程中的核心属性。
         */
        private String newPassword;

        /**
         * 业务注释规范化: 查询 getOldPassword 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getOldPassword() { return oldPassword; }
        /**
         * 业务注释规范化: 处理 setOldPassword 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param oldPassword oldPassword 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
        /**
         * 业务注释规范化: 查询 getNewPassword 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public String getNewPassword() { return newPassword; }
        /**
         * 业务注释规范化: 处理 setNewPassword 相关业务，保持现有接口路径、请求和响应字段不变。
         *
         * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
         * @param newPassword newPassword 业务字段，承载该对象在后端流程中的核心属性。
         * @return 处理后的业务结果，具体结构保持现有契约不变。
         */
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
}

