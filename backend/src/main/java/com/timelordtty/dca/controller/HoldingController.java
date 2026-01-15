package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.service.HoldingService;
import com.timelordtty.dca.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 持仓控制器
 */
@RestController
@RequestMapping("/api/v2/holdings")
public class HoldingController {

    private final HoldingService holdingService;
    private final UserService userService;

    public HoldingController(HoldingService holdingService, UserService userService) {
        this.holdingService = holdingService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<Map<Long, HoldingService.HoldingInfo>> getHoldings() {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        Map<Long, HoldingService.HoldingInfo> holdings = holdingService.calculateHoldings(currentUser.getId());
        return ResponseEntity.ok(holdings);
    }

    /**
     * 导入初始持仓
     */
    @PostMapping("/import-initial")
    public ResponseEntity<Void> importInitialHoldings(@RequestBody List<HoldingService.InitialHoldingImport> holdings) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        holdingService.importInitialHoldings(currentUser.getId(), currentUser.getFamilyId(), holdings);
        return ResponseEntity.ok().build();
    }
}

