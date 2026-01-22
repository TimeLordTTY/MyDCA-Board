package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.model.Order;
import com.timelordtty.dca.service.DashboardService;
import com.timelordtty.dca.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 看板控制器
 */
@RestController
@RequestMapping("/api/v2/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserService userService;

    public DashboardController(DashboardService dashboardService, UserService userService) {
        this.dashboardService = dashboardService;
        this.userService = userService;
    }

    @GetMapping("/pending-settlements")
    public ResponseEntity<List<Order>> getPendingSettlements() {
        List<Order> orders = dashboardService.getPendingSettlements();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/asset-overview")
    public ResponseEntity<DashboardService.AssetOverview> getAssetOverview(
            @RequestParam(defaultValue = "personal") String viewType) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        // 统一转换为大写进行比较
        String normalizedViewType = viewType != null ? viewType.toUpperCase() : "PERSONAL";
        DashboardService.AssetOverview overview = dashboardService.getAssetOverview(
                currentUser.getId(), currentUser.getFamilyId(), normalizedViewType);
        return ResponseEntity.ok(overview);
    }

    /**
     * 获取今日建议清单
     * 
     * 注意：Phase 1阶段，建议功能在Phase 3实现，这里返回空列表
     * 
     * @return 今日建议列表（Phase 1返回空列表）
     */
    @GetMapping("/today-actions")
    public ResponseEntity<List<DashboardService.TodayAction>> getTodayActions() {
        // Phase 1阶段，建议功能未实现，返回空列表
        // Phase 3会实现策略引擎和建议生成
        List<DashboardService.TodayAction> emptyList = new java.util.ArrayList<>();
        return ResponseEntity.ok(emptyList);
    }
}

