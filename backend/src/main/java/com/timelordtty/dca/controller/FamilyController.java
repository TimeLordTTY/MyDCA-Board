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
/**
 * 业务注释规范化: FamilyController 控制器，负责接收前端请求、读取用户上下文，并将业务处理委托给服务层。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class FamilyController {

    /**
     * 业务注释规范化: familyService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final FamilyService familyService;
    /**
     * 业务注释规范化: userService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final UserService userService;

    /**
     * 业务注释规范化: 处理 FamilyController 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param familyService familyService 业务字段，承载该对象在后端流程中的核心属性。
     * @param userService userService 业务字段，承载该对象在后端流程中的核心属性。
     */
    public FamilyController(FamilyService familyService, UserService userService) {
        this.familyService = familyService;
        this.userService = userService;
    }

    @GetMapping
    /**
     * 业务注释规范化: 查询 getFamily 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<Family> getFamily() {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        if (currentUser.getFamilyId() == null) {
            return ResponseEntity.notFound().build();
        }
        Family family = familyService.getFamily(currentUser.getFamilyId());
        return ResponseEntity.ok(family);
    }

    @PostMapping
    /**
     * 业务注释规范化: 创建 createFamily 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param request request 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<Family> createFamily(@RequestBody Map<String, String> request) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        String familyName = request.get("familyName");
        if (familyName == null || familyName.trim().isEmpty()) {
            throw new RuntimeException("家庭名称不能为空");
        }
        Family family = familyService.createFamily(currentUser.getId(), familyName);
        return ResponseEntity.ok(family);
    }

    @PostMapping("/members")
    /**
     * 业务注释规范化: 追加 addMember 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param request request 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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

    @GetMapping("/members")
    /**
     * 业务注释规范化: 查询 getMembers 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<List<FamilyMemberDto>> getMembers() {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        Long familyId = currentUser.getFamilyId();
        if (familyId == null) {
            throw new RuntimeException("用户不属于任何家庭");
        }
        List<FamilyMemberDto> members = familyService.getMembers(familyId);
        return ResponseEntity.ok(members);
    }

    @DeleteMapping("/members/{userId}")
    /**
     * 业务注释规范化: 处理 removeMember 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param userId 所属用户 ID，用于限定个人数据权限和查询范围。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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

    @PutMapping("/members/{userId}/role")
    /**
     * 业务注释规范化: 更新 updateMemberRole 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param userId 所属用户 ID，用于限定个人数据权限和查询范围。
     * @param request request 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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

