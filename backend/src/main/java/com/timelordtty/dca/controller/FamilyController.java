package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.dto.FamilyMemberDto;
import com.timelordtty.dca.model.Family;
import com.timelordtty.dca.service.FamilyService;
import com.timelordtty.dca.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * 家庭控制器
 */
@RestController
@RequestMapping("/api/v2/families")
public class FamilyController {

    /**
     * 家庭服务入口，用于校验家庭归属、管理员权限和成员范围。
     */
    private final FamilyService familyService;
    /**
     * 用户身份服务，用于根据当前登录名定位用户、家庭和角色权限上下文。
     */
    private final UserService userService;

    /**
     * 装配家庭和用户服务，处理家庭资料、成员和角色管理接口。
     */
    public FamilyController(FamilyService familyService, UserService userService) {
        this.familyService = familyService;
        this.userService = userService;
    }

    /**
     * 读取当前用户所属家庭资料，用于家庭页初始化和权限展示。
     */
    @GetMapping
    public ResponseEntity<Family> getFamily() {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        if (currentUser.getFamilyId() == null) {
            return ResponseEntity.notFound().build();
        }
        Family family = familyService.getFamily(currentUser.getFamilyId());
        return ResponseEntity.ok(family);
    }

    /**
     * 为当前用户创建家庭并建立管理员成员关系，供家庭资产视图聚合使用。
     */
    @PostMapping
    public ResponseEntity<Family> createFamily(@RequestBody Map<String, String> request) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        String familyName = request.get("familyName");
        if (familyName == null || familyName.trim().isEmpty()) {
            throw new RuntimeException("家庭名称不能为空");
        }
        Family family = familyService.createFamily(currentUser.getId(), familyName);
        return ResponseEntity.ok(family);
    }

    /**
     * 将指定用户加入当前家庭，并写入成员角色；调用前需要校验当前用户管理权限。
     */
    @PostMapping("/members")
    public ResponseEntity<Void> addMember(@RequestBody Map<String, Object> request) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        Long familyId = currentUser.getFamilyId();
        if (familyId == null) {
            throw new RuntimeException("用户不属于任何家庭");
        }

        familyService.assertAdmin(currentUser.getId(), familyId);

        Object userIdObj = request.get("userId");
        Object usernameObj = request.get("username");
        Long userId;
        if (userIdObj != null) {
            userId = Long.valueOf(userIdObj.toString());
        } else if (usernameObj != null) {
            String username = usernameObj.toString();
            userId = familyService.findUserIdByUsername(username);
        } else {
            throw new RuntimeException("缺少 userId 或 username");
        }

        String role = request.getOrDefault("role", "MEMBER").toString();

        familyService.addMember(familyId, userId, role);
        return ResponseEntity.ok().build();
    }

    /**
     * 查询当前家庭成员列表及角色信息，用于家庭成员管理页面展示。
     */
    @GetMapping("/members")
    public ResponseEntity<List<FamilyMemberDto>> getMembers() {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        Long familyId = currentUser.getFamilyId();
        if (familyId == null) {
            throw new RuntimeException("用户不属于任何家庭");
        }
        List<FamilyMemberDto> members = familyService.getMembers(familyId);
        return ResponseEntity.ok(members);
    }

    /**
     * 从当前家庭移除指定成员，返回受影响账户或权限处理结果。
     */
    @DeleteMapping("/members/{userId}")
    public ResponseEntity<Map<String, Object>> removeMember(@PathVariable Long userId) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        Long familyId = currentUser.getFamilyId();
        if (familyId == null) {
            throw new RuntimeException("用户不属于任何家庭");
        }
        familyService.assertAdmin(currentUser.getId(), familyId);
        familyService.removeMember(familyId, userId);
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        return ResponseEntity.ok(resp);
    }

    /**
     * 更新家庭成员角色，用于管理员授权或降级成员权限。
     */
    @PutMapping("/members/{userId}/role")
    public ResponseEntity<Map<String, Object>> updateMemberRole(@PathVariable Long userId, @RequestBody Map<String, Object> request) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        Long familyId = currentUser.getFamilyId();
        if (familyId == null) {
            throw new RuntimeException("用户不属于任何家庭");
        }
        familyService.assertAdmin(currentUser.getId(), familyId);
        String role = request.get("role") != null ? request.get("role").toString() : null;
        familyService.updateMemberRole(familyId, userId, role);
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        return ResponseEntity.ok(resp);
    }
}

