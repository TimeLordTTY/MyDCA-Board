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

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

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
        private String nickname;
        private String email;
        private String phone;

        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
    }

    public static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;

        public String getOldPassword() { return oldPassword; }
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
}

