package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.AuthRequest;
import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.mapper.UserMapper;
import com.timelordtty.dca.model.User;
import com.timelordtty.dca.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 认证服务（AuthService）
 *
 * 职责：处理用户注册、登录认证并颁发 JWT Token
 *
 * 实现要点：
 * - 密码使用 `PasswordEncoder`（如 BCrypt）进行哈希存储，服务层不存储明文密码
 * - 登录成功后更新用户最后登录时间并返回包含 JWT 的 `AuthResponse`
 * - 注册时需保证 username 唯一性并为用户设置默认元数据
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AccountService accountService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider, AccountService accountService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.accountService = accountService;
    }

    @Transactional
    /**
     * 注册新用户并返回认证信息（含 JWT）
     *
     * 流程：检查用户名唯一性 -> 按需填充用户信息 -> 持久化 -> 生成 token
     *
     * @param request 注册请求（包含 username/password 等）
     * @return 包含 JWT 与用户基础信息的响应
     */
    public AuthResponse register(AuthRequest request) {
        // 检查用户名是否已存在
        User existingUser = userMapper.selectByUsername(request.getUsername());
        if (existingUser != null) {
            throw new RuntimeException("用户名已存在");
        }

        // 创建新用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setIsActive(true);

        userMapper.insert(user);

        // 为新用户创建所有虚拟账户（INCOME、EXPENSE、FEE、RECEIVABLE、LIABILITY）
        // POSITION 账户会在买入/申购时按产品自动创建
        String[] virtualSubtypes = {"INCOME", "EXPENSE", "FEE", "RECEIVABLE", "LIABILITY"};
        String ownerType = "PERSONAL";
        Long ownerFamilyId = user.getFamilyId(); // 如果用户有家庭，也设置 familyId
        
        for (String virtualSubtype : virtualSubtypes) {
            accountService.getOrCreateVirtualAccount(
                virtualSubtype, virtualSubtype, ownerType, user.getId(), ownerFamilyId, null, null);
        }

        // 生成JWT token
        String token = jwtTokenProvider.generateToken(user.getUsername());

        // 构建响应
        AuthResponse response = new AuthResponse();
        response.setToken(token);
        AuthResponse.UserInfo userInfo = new AuthResponse.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setNickname(user.getNickname());
        userInfo.setEmail(user.getEmail());
        userInfo.setPhone(user.getPhone());
        userInfo.setFamilyId(user.getFamilyId());
        response.setUser(userInfo);

        return response;
    }

    /**
     * 登录并生成 JWT
     *
     * 流程：校验用户存在且启用 -> 验证密码 -> 更新最后登录时间 -> 返回 token
     *
     * @param request 登录请求（username/password）
     * @return 包含 JWT 与用户基础信息的响应
     */
    public AuthResponse login(AuthRequest request) {
        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null || !user.getIsActive()) {
            throw new RuntimeException("用户名或密码错误");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 更新最后登录时间
        userMapper.updateLastLoginAt(user.getId(), LocalDateTime.now());

        // 生成JWT token
        String token = jwtTokenProvider.generateToken(user.getUsername());

        // 构建响应
        AuthResponse response = new AuthResponse();
        response.setToken(token);
        AuthResponse.UserInfo userInfo = new AuthResponse.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setNickname(user.getNickname());
        userInfo.setEmail(user.getEmail());
        userInfo.setPhone(user.getPhone());
        userInfo.setFamilyId(user.getFamilyId());
        response.setUser(userInfo);

        return response;
    }
}

