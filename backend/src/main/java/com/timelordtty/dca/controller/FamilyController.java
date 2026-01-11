package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.model.Family;
import com.timelordtty.dca.service.FamilyService;
import com.timelordtty.dca.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 家庭控制器
 */
@RestController
@RequestMapping("/api/v2/families")
public class FamilyController {

    private final FamilyService familyService;
    private final UserService userService;

    public FamilyController(FamilyService familyService, UserService userService) {
        this.familyService = familyService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<Family> getFamily() {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        if (currentUser.getFamilyId() == null) {
            return ResponseEntity.notFound().build();
        }
        Family family = familyService.getFamily(currentUser.getFamilyId());
        return ResponseEntity.ok(family);
    }

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

    @PostMapping("/members")
    public ResponseEntity<Void> addMember(@RequestBody Map<String, Object> request) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        Long familyId = currentUser.getFamilyId();
        if (familyId == null) {
            throw new RuntimeException("用户不属于任何家庭");
        }

        Long userId = Long.valueOf(request.get("userId").toString());
        String role = request.getOrDefault("role", "MEMBER").toString();

        familyService.addMember(familyId, userId, role);
        return ResponseEntity.ok().build();
    }
}

