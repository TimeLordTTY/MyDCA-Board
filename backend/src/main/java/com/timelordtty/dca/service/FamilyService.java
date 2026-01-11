package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.FamilyMapper;
import com.timelordtty.dca.mapper.UserFamilyRoleMapper;
import com.timelordtty.dca.mapper.UserMapper;
import com.timelordtty.dca.model.Family;
import com.timelordtty.dca.model.User;
import com.timelordtty.dca.model.UserFamilyRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    }
}

