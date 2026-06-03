package com.timelordtty.dca.model;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 家庭表实体（families表）
 * 
 * 对应数据库表：families
 * 
 * 字段说明：
 * - id: 家庭ID，主键，自增
 * - familyCode: 家庭代码，唯一标识，格式如：FAM-20240101-001
 * - familyName: 家庭名称，用户自定义名称
 * - adminUserId: 管理员用户ID，外键关联users表，家庭创建者/管理员
 * - isActive: 是否启用，true=启用，false=禁用（软删除），默认true
 * - createdAt: 创建时间，记录家庭创建时间
 * - updatedAt: 更新时间，记录最后修改时间，自动更新
 * 
 * 业务规则：
 * 1. familyCode必须唯一，创建时自动生成
 * 2. 家庭管理员（adminUserId）拥有家庭的所有权限
 * 3. 家庭成员通过user_family_roles表关联
 * 4. 家庭账户（ownerType=FAMILY）属于家庭共同所有
 * 
 * 家庭成员管理：
 * - 通过UserFamilyRole表关联用户和家庭
 * - 角色类型：ADMIN=管理员，MEMBER=普通成员
 * - 只有家庭成员才能访问家庭数据
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Data
public class Family {
    /** 家庭ID，主键，自增 */
    private Long id;
    
    /** 家庭代码，唯一标识，格式如：FAM-20240101-001 */
    private String familyCode;
    
    /** 家庭名称，用户自定义名称 */
    private String familyName;
    
    /** 管理员用户ID，外键关联users表，家庭创建者/管理员 */
    private Long adminUserId;
    
    /** 是否启用，true=启用，false=禁用（软删除），默认true */
    private Boolean isActive;
    
    /** 创建时间，记录家庭创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间，记录最后修改时间，自动更新 */
    private LocalDateTime updatedAt;
}

