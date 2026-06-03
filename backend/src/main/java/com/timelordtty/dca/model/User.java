package com.timelordtty.dca.model;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户表实体（users表）
 * 
 * 对应数据库表：users
 * 
 * 字段说明：
 * - id: 用户ID，主键，自增
 * - username: 用户名，唯一标识，用于登录
 * - passwordHash: 密码哈希值，使用BCrypt加密存储，不存储明文密码
 * - nickname: 昵称，用户显示名称，可选
 * - email: 邮箱地址，可选，用于找回密码等
 * - phone: 手机号，可选，用于验证和通知
 * - familyId: 所属家庭ID，外键关联families表，可为空（个人用户）
 * - isActive: 是否启用，true=启用，false=禁用（软删除）
 * - lastLoginAt: 最后登录时间，用于统计和安全性检查
 * - createdAt: 创建时间，记录用户注册时间
 * - updatedAt: 更新时间，记录最后修改时间
 * 
 * 业务规则：
 * 1. username必须唯一，注册时校验
 * 2. passwordHash使用BCrypt加密，强度因子为10
 * 3. 用户可以被分配到家庭（familyId），也可以独立存在
 * 4. 禁用用户（isActive=false）无法登录，但数据保留
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Data
/**
 * 业务注释规范化: User 实体模型，对应后端数据库中的核心业务记录。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class User {
    /** 用户ID，主键，自增 */
    /**
     * 业务注释规范化: 主键 ID，用于在后端内部唯一定位该业务记录。
     */
    private Long id;
    
    /** 用户名，唯一标识，用于登录，不能为空 */
    /**
     * 业务注释规范化: username 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String username;
    
    /** 密码哈希值，使用BCrypt加密存储，不存储明文密码 */
    /**
     * 业务注释规范化: passwordHash 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String passwordHash;
    
    /** 昵称，用户显示名称，可选 */
    /**
     * 业务注释规范化: nickname 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String nickname;
    
    /** 邮箱地址，可选，用于找回密码等 */
    /**
     * 业务注释规范化: email 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String email;
    
    /** 手机号，可选，用于验证和通知 */
    /**
     * 业务注释规范化: phone 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String phone;
    
    /** 所属家庭ID，外键关联families表，可为空（个人用户） */
    /**
     * 业务注释规范化: 所属家庭 ID，用于家庭视角下的数据隔离。
     */
    private Long familyId;
    
    /** 是否启用，true=启用，false=禁用（软删除），默认true */
    /**
     * 业务注释规范化: isActive 布尔标记，用于控制该记录在业务流程中的特殊状态。
     */
    private Boolean isActive;
    
    /** 最后登录时间，用于统计和安全性检查 */
    /**
     * 业务注释规范化: lastLoginAt 时间字段，用于记录业务动作发生或审计时间。
     */
    private LocalDateTime lastLoginAt;
    
    /** 创建时间，记录用户注册时间 */
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

