package com.timelordtty.dca.dto;

/**
 * 家庭成员 DTO（用于成员列表展示）
 */
/**
 * 业务注释规范化: FamilyMemberDto DTO 数据传输对象，用于承载请求参数或响应结果，属于前后端契约。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class FamilyMemberDto {
    /**
     * 业务注释规范化: 所属用户 ID，用于限定个人数据权限和查询范围。
     */
    private Long userId;
    /**
     * 业务注释规范化: username 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String username;
    /**
     * 业务注释规范化: nickname 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String nickname;
    /**
     * 业务注释规范化: email 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String email;
    /**
     * 业务注释规范化: phone 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String phone;
    /**
     * 业务注释规范化: role 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String role;

    /**
     * 业务注释规范化: 查询 getUserId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public Long getUserId() { return userId; }
    /**
     * 业务注释规范化: 处理 setUserId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param userId 所属用户 ID，用于限定个人数据权限和查询范围。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setUserId(Long userId) { this.userId = userId; }
    /**
     * 业务注释规范化: 查询 getUsername 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public String getUsername() { return username; }
    /**
     * 业务注释规范化: 处理 setUsername 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param username username 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setUsername(String username) { this.username = username; }
    /**
     * 业务注释规范化: 查询 getNickname 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public String getNickname() { return nickname; }
    /**
     * 业务注释规范化: 处理 setNickname 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param nickname nickname 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setNickname(String nickname) { this.nickname = nickname; }
    /**
     * 业务注释规范化: 查询 getEmail 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public String getEmail() { return email; }
    /**
     * 业务注释规范化: 处理 setEmail 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param email email 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setEmail(String email) { this.email = email; }
    /**
     * 业务注释规范化: 查询 getPhone 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public String getPhone() { return phone; }
    /**
     * 业务注释规范化: 处理 setPhone 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param phone phone 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setPhone(String phone) { this.phone = phone; }
    /**
     * 业务注释规范化: 查询 getRole 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public String getRole() { return role; }
    /**
     * 业务注释规范化: 处理 setRole 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param role role 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setRole(String role) { this.role = role; }
}

