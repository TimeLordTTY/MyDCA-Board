package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.FamilyMemberDto;
import com.timelordtty.dca.mapper.FamilyMapper;
import com.timelordtty.dca.mapper.UserFamilyRoleMapper;
import com.timelordtty.dca.mapper.UserMapper;
import com.timelordtty.dca.model.Family;
import com.timelordtty.dca.model.User;
import com.timelordtty.dca.model.UserFamilyRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 家庭服务（FamilyService）
 *
 * 职责：家庭实体管理、家庭成员角色分配以及家庭相关元数据维护
 *
 * 实现要点：
 * - 创建家庭时自动生成 familyCode，并为管理员创建 UserFamilyRole
 * - 家庭成员管理通过 user_family_roles 表维护，角色由应用层约束权限
 */
@Service
public class FamilyService {

    private final FamilyMapper familyMapper;
    private final UserFamilyRoleMapper userFamilyRoleMapper;
    private final UserMapper userMapper;

    public FamilyService(FamilyMapper familyMapper, UserFamilyRoleMapper userFamilyRoleMapper, UserMapper userMapper) {
        this.familyMapper = familyMapper;
        this.userFamilyRoleMapper = userFamilyRoleMapper;
        this.userMapper = userMapper;
    }

    @Transactional
    /**
     * 创建家庭并为管理员分配角色
     *
     * @param adminUserId 管理员用户ID
     * @param familyName 家庭名称
     * @return 创建的 Family 实体
     */
    public Family createFamily(Long adminUserId, String familyName) {
        // 生成唯一家庭代码
        String familyCode = "FAM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // 创建家庭
        Family family = new Family();
        family.setFamilyCode(familyCode);
        family.setFamilyName(familyName);
        family.setAdminUserId(adminUserId);
        family.setIsActive(true);

        familyMapper.insert(family);

        // 添加管理员角色
        UserFamilyRole role = new UserFamilyRole();
        role.setUserId(adminUserId);
        role.setFamilyId(family.getId());
        role.setRole("ADMIN");
        userFamilyRoleMapper.insert(role);

        // 更新用户的family_id
        User user = userMapper.selectById(adminUserId);
        if (user != null) {
            user.setFamilyId(family.getId());
            userMapper.update(user);
        }

        return family;
    }

    /**
     * 查询家庭信息
     * @param familyId 家庭ID
     * @return Family 实体或 null
     */
    public Family getFamily(Long familyId) {
        return familyMapper.selectById(familyId);
    }

    public List<FamilyMemberDto> getMembers(Long familyId) {
        List<UserFamilyRole> roles = userFamilyRoleMapper.selectByFamilyId(familyId);
        List<FamilyMemberDto> result = new ArrayList<>();
        for (UserFamilyRole r : roles) {
            User u = userMapper.selectById(r.getUserId());
            if (u == null) {
                continue;
            }
            FamilyMemberDto dto = new FamilyMemberDto();
            dto.setUserId(u.getId());
            dto.setUsername(u.getUsername());
            dto.setNickname(u.getNickname());
            dto.setEmail(u.getEmail());
            dto.setPhone(u.getPhone());
            dto.setRole(r.getRole());
            result.add(dto);
        }
        return result;
    }

    public void assertAdmin(Long operatorUserId, Long familyId) {
        String role = userFamilyRoleMapper.selectRole(operatorUserId, familyId);
        if (!"ADMIN".equals(role)) {
            throw new RuntimeException("无权限：仅家庭管理员可操作");
        }
    }

    public Long findUserIdByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new RuntimeException("用户名不能为空");
        }
        User user = userMapper.selectByUsername(username.trim());
        if (user == null) {
            throw new RuntimeException("用户不存在: " + username);
        }
        return user.getId();
    }

    /**
     * 向家庭添加成员并分配角色（如 MEMBER/ADMIN）
     *
     * @param familyId 家庭ID
     * @param userId 用户ID
     * @param role 角色，若为空则默认为 MEMBER
     */
    @Transactional
    public void addMember(Long familyId, Long userId, String role) {
        // 检查是否已经是家庭成员
        UserFamilyRole existing = userFamilyRoleMapper.selectByUserId(userId).stream()
                .filter(r -> r.getFamilyId().equals(familyId))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            throw new RuntimeException("用户已经是该家庭成员");
        }

        // 添加成员
        UserFamilyRole userFamilyRole = new UserFamilyRole();
        userFamilyRole.setUserId(userId);
        userFamilyRole.setFamilyId(familyId);
        userFamilyRole.setRole(role != null ? role : "MEMBER");
        userFamilyRoleMapper.insert(userFamilyRole);

        // 更新用户的family_id（便于前端快速判断）
        User user = userMapper.selectById(userId);
        if (user != null) {
            user.setFamilyId(familyId);
            userMapper.update(user);
        }
    }

    @Transactional
    public void removeMember(Long familyId, Long userId) {
        // 不能移除最后一个 ADMIN
        String targetRole = userFamilyRoleMapper.selectRole(userId, familyId);
        if ("ADMIN".equals(targetRole)) {
            int adminCount = userFamilyRoleMapper.countAdmins(familyId);
            if (adminCount <= 1) {
                throw new RuntimeException("不能移除最后一个管理员");
            }
        }

        userFamilyRoleMapper.delete(userId, familyId);

        // 如果用户当前 family_id 指向该家庭，则清空
        User user = userMapper.selectById(userId);
        if (user != null && familyId.equals(user.getFamilyId())) {
            user.setFamilyId(null);
            userMapper.update(user);
        }
    }

    @Transactional
    public void updateMemberRole(Long familyId, Long userId, String role) {
        if (role == null || role.trim().isEmpty()) {
            throw new RuntimeException("角色不能为空");
        }
        String normalizedRole = role.trim().toUpperCase();
        if (!"ADMIN".equals(normalizedRole) && !"MEMBER".equals(normalizedRole)) {
            throw new RuntimeException("不支持的角色: " + role);
        }

        // 不能把最后一个 ADMIN 降级
        String currentRole = userFamilyRoleMapper.selectRole(userId, familyId);
        if ("ADMIN".equals(currentRole) && !"ADMIN".equals(normalizedRole)) {
            int adminCount = userFamilyRoleMapper.countAdmins(familyId);
            if (adminCount <= 1) {
                throw new RuntimeException("不能降级最后一个管理员");
            }
        }

        int updated = userFamilyRoleMapper.updateRole(userId, familyId, normalizedRole);
        if (updated <= 0) {
            throw new RuntimeException("成员不存在或更新失败");
        }
    }
}

