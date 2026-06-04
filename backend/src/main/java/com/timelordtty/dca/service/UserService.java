package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.mapper.UserMapper;
import com.timelordtty.dca.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户服务（UserService）
 *
 * 职责：提供当前登录用户信息查询等用户相关便捷方法
 *
 * 说明：本服务依赖 Spring Security 的上下文来获取当前认证信息，并根据用户名查询用户实体
 */
@Service
public class UserService {

    /**
     * 用户持久化 Mapper，负责登录用户名、密码哈希和用户基础资料的读写。
     */
    private final UserMapper userMapper;
    /**
     * 展示或唯一标识字段，用于人工识别和业务查找。
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * 装配用户 Mapper 和密码编码器，用于当前用户查询、用户创建和密码修改。
     */
    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 获取当前认证用户的基础信息（用于前端展示/个人中心）
     *
     * @return 包含 id/username/nickname/email/phone/familyId 的用户信息对象
     */
    public AuthResponse.UserInfo getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        AuthResponse.UserInfo userInfo = new AuthResponse.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setNickname(user.getNickname());
        userInfo.setEmail(user.getEmail());
        userInfo.setPhone(user.getPhone());
        userInfo.setFamilyId(user.getFamilyId());

        return userInfo;
    }

    /**
     * 更新 UserService 领域记录，保持历史流水和已结算数据的可追溯性。
     */
    public AuthResponse.UserInfo updateCurrentUserProfile(String nickname, String email, String phone) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        String safeNickname = nickname != null ? nickname.trim() : null;
        String safeEmail = email != null ? email.trim() : null;
        String safePhone = phone != null ? phone.trim() : null;

        userMapper.updateProfile(user.getId(), safeNickname, safeEmail, safePhone);
        return getCurrentUser();
    }

    /**
     * 校验当前登录用户旧密码后写入新密码哈希，用于账号安全设置，不影响账户资产数据。
     */
    public void changePassword(String oldPassword, String newPassword) {
        if (oldPassword == null || newPassword == null) {
            throw new IllegalArgumentException("密码不能为空");
        }
        String newPw = newPassword.trim();
        if (newPw.length() < 8) {
            throw new IllegalArgumentException("新密码长度至少 8 位");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (user.getPasswordHash() == null || !passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("旧密码不正确");
        }

        String encoded = passwordEncoder.encode(newPw);
        userMapper.updatePasswordHash(user.getId(), encoded);
    }
}

