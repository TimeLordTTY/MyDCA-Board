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
/**
 * 业务注释规范化: AuthController 控制器，负责接收前端请求、读取用户上下文，并将业务处理委托给服务层。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class AuthController {

    /**
     * 业务注释规范化: authService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final AuthService authService;

    /**
     * 业务注释规范化: 处理 AuthController 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param authService authService 业务字段，承载该对象在后端流程中的核心属性。
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    /**
     * 业务注释规范化: 处理 register 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param request request 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    /**
     * 业务注释规范化: 处理 login 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param request request 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    /**
     * 业务注释规范化: 处理 logout 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<Void> logout() {
        // JWT无状态，客户端删除token即可
        return ResponseEntity.ok().build();
    }
}

