package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.AuthRequest;
import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/v2/auth")
public class AuthController {

    /**
     * 认证服务入口，负责注册、登录、登出和当前用户信息组装。
     */
    private final AuthService authService;

    /**
     * 装配认证服务，统一处理注册、登录、登出和当前用户读取接口。
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 注册新用户账号，创建登录凭据并返回 JWT 与用户信息；注册流程可能初始化默认账户。
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 校验用户名和密码，认证成功后返回新的 JWT 和当前用户资料。
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 处理前端登出请求；当前 JWT 为无状态令牌，后端只返回成功响应。
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // JWT无状态，客户端删除token即可
        return ResponseEntity.ok().build();
    }
}

