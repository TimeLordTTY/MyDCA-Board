package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.dto.FinanceRadarDTO;
import com.timelordtty.dca.service.FamilyService;
import com.timelordtty.dca.service.FinanceRadarService;
import com.timelordtty.dca.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/finance-radar")
public class FinanceRadarController {
    private final FinanceRadarService radar;
    private final UserService users;
    private final FamilyService families;

    public FinanceRadarController(FinanceRadarService radar, UserService users, FamilyService families) {
        this.radar = radar;
        this.users = users;
        this.families = families;
    }

    @GetMapping
    public ResponseEntity<FinanceRadarDTO> getRadar(@RequestParam(defaultValue = "PERSONAL") String scope) {
        if (!"PERSONAL".equals(scope) && !"FAMILY".equals(scope)) return ResponseEntity.badRequest().build();
        AuthResponse.UserInfo user = users.getCurrentUser();
        if ("FAMILY".equals(scope)) {
            if (user.getFamilyId() == null) return ResponseEntity.badRequest().build();
            families.assertAdmin(user.getId(), user.getFamilyId());
        }
        return ResponseEntity.ok(radar.getRadar(user.getId(), user.getFamilyId(), scope));
    }
}
