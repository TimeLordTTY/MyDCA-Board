package com.timelordtty.dca.model;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户家庭角色关联表实体（user_family_roles表）
 * 
 * 对应数据库表：user_family_roles
 * 
 * 字段说明：
 * - id: 关联ID，主键，自增
 * - userId: 用户ID，外键关联users表
 * - familyId: 家庭ID，外键关联families表
 * - role: 角色类型：ADMIN=管理员（拥有家庭的所有权限），MEMBER=普通成员（可以查看和操作家庭数据）
 * - createdAt: 创建时间，记录用户加入家庭的时间
 * - updatedAt: 更新时间，记录最后修改时间，自动更新
 * 
 * 业务规则：
 * 1. 一个用户可以在多个家庭中，一个家庭可以有多个成员
 * 2. 每个用户在家庭中的角色唯一（userId + familyId 唯一约束）
 * 3. 家庭管理员（adminUserId）自动拥有ADMIN角色
 * 4. 只有家庭成员才能访问家庭数据（权限校验）
 * 
 * 权限说明：
 * - ADMIN：拥有家庭的所有权限（创建账户、查看所有数据、管理成员等）
 * - MEMBER：可以查看和操作家庭数据，但不能管理成员
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Data
public class UserFamilyRole {
    /** 关联ID，主键，自增 */
    private Long id;
    
    /** 用户ID，外键关联users表 */
    private Long userId;
    
    /** 家庭ID，外键关联families表 */
    private Long familyId;
    
    /** 角色类型：ADMIN=管理员，MEMBER=普通成员 */
    private String role;
    
    /** 创建时间，记录用户加入家庭的时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间，记录最后修改时间，自动更新 */
    private LocalDateTime updatedAt;
}

