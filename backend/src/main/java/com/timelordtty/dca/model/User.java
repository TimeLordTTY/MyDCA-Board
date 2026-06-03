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
public class User {
    /** 用户ID，主键，自增 */
    private Long id;
    
    /** 用户名，唯一标识，用于登录，不能为空 */
    private String username;
    
    /** 密码哈希值，使用BCrypt加密存储，不存储明文密码 */
    private String passwordHash;
    
    /** 昵称，用户显示名称，可选 */
    private String nickname;
    
    /** 邮箱地址，可选，用于找回密码等 */
    private String email;
    
    /** 手机号，可选，用于验证和通知 */
    private String phone;
    
    /** 所属家庭ID，外键关联families表，可为空（个人用户） */
    private Long familyId;
    
    /** 是否启用，true=启用，false=禁用（软删除），默认true */
    private Boolean isActive;
    
    /** 最后登录时间，用于统计和安全性检查 */
    private LocalDateTime lastLoginAt;
    
    /** 创建时间，记录用户注册时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间，记录最后修改时间，自动更新 */
    private LocalDateTime updatedAt;
}

