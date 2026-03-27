package com.timelordtty.dca.dto;

/**
 * 家庭成员 DTO（用于成员列表展示）
 */
public class FamilyMemberDto {
    private Long userId;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String role;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}

