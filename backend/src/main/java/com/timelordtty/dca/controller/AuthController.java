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
     * 依赖的 AuthService 服务，用于复用该领域的业务校验和事务逻辑。
     */
    private final AuthService authService;

    /**
     * 处理写入类 API，将请求参数校验后委托给 Service 层。
     * 是否产生账本、账户或订单变更由对应 Service 事务边界决定。
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 处理写入类 API，将请求参数校验后委托给 Service 层。
     * 是否产生账本、账户或订单变更由对应 Service 事务边界决定。
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 处理写入类 API，将请求参数校验后委托给 Service 层。
     * 是否产生账本、账户或订单变更由对应 Service 事务边界决定。
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 处理写入类 API，将请求参数校验后委托给 Service 层。
     * 是否产生账本、账户或订单变更由对应 Service 事务边界决定。
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // JWT无状态，客户端删除token即可
        return ResponseEntity.ok().build();
    }
}

