package com.timelordtty.dca.dto;

/**
 * 家庭成员 DTO（用于成员列表展示）
 */
public class FamilyMemberDto {
    /**
     * 所属用户 ID，用于个人视角数据隔离。
     */
    private Long userId;
    /**
     * 展示或唯一标识字段，用于人工识别和业务查找。
     */
    private String username;
    /**
     * 展示或唯一标识字段，用于人工识别和业务查找。
     */
    private String nickname;
    /**
     * 用户联系方式，用于账户资料展示或后续通知扩展。
     */
    private String email;
    /**
     * 用户联系方式，用于账户资料展示或后续通知扩展。
     */
    private String phone;
    /**
     * 家庭成员角色，通常为 ADMIN 或 MEMBER，用于决定成员管理和家庭资产查看权限。
     */
    private String role;

    /**
     * 返回所属用户 ID，用于个人视角数据隔离。
     */
    public Long getUserId() { return userId; }
    /**
     * 设置所属用户 ID，用于个人视角数据隔离。
     */
    public void setUserId(Long userId) { this.userId = userId; }
    /**
     * 返回展示或唯一标识字段，用于人工识别和业务查找。
     */
    public String getUsername() { return username; }
    /**
     * 设置展示或唯一标识字段，用于人工识别和业务查找。
     */
    public void setUsername(String username) { this.username = username; }
    /**
     * 返回展示或唯一标识字段，用于人工识别和业务查找。
     */
    public String getNickname() { return nickname; }
    /**
     * 设置展示或唯一标识字段，用于人工识别和业务查找。
     */
    public void setNickname(String nickname) { this.nickname = nickname; }
    /**
     * 返回用户联系方式，用于账户资料展示或后续通知扩展。
     */
    public String getEmail() { return email; }
    /**
     * 设置用户联系方式，用于账户资料展示或后续通知扩展。
     */
    public void setEmail(String email) { this.email = email; }
    /**
     * 返回用户联系方式，用于账户资料展示或后续通知扩展。
     */
    public String getPhone() { return phone; }
    /**
     * 设置用户联系方式，用于账户资料展示或后续通知扩展。
     */
    public void setPhone(String phone) { this.phone = phone; }
    /**
     * 读取家庭成员角色，供前端展示 ADMIN/MEMBER 权限。
     */
    public String getRole() { return role; }
    /**
     * 设置家庭成员角色，保存成员在家庭中的权限级别。
     */
    public void setRole(String role) { this.role = role; }
}

