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
/**
 * 业务注释规范化: UserFamilyRole 实体模型，对应后端数据库中的核心业务记录。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class UserFamilyRole {
    /** 关联ID，主键，自增 */
    /**
     * 业务注释规范化: 主键 ID，用于在后端内部唯一定位该业务记录。
     */
    private Long id;
    
    /** 用户ID，外键关联users表 */
    /**
     * 业务注释规范化: 所属用户 ID，用于限定个人数据权限和查询范围。
     */
    private Long userId;
    
    /** 家庭ID，外键关联families表 */
    /**
     * 业务注释规范化: 所属家庭 ID，用于家庭视角下的数据隔离。
     */
    private Long familyId;
    
    /** 角色类型：ADMIN=管理员，MEMBER=普通成员 */
    /**
     * 业务注释规范化: role 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String role;
    
    /** 创建时间，记录用户加入家庭的时间 */
    /**
     * 业务注释规范化: 记录创建时间，用于审计和排序。
     */
    private LocalDateTime createdAt;
    
    /** 更新时间，记录最后修改时间，自动更新 */
    /**
     * 业务注释规范化: 记录最后更新时间，用于审计和增量同步。
     */
    private LocalDateTime updatedAt;
}

