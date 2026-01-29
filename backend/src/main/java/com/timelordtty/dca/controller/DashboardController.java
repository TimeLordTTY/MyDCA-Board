package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.model.Order;
import com.timelordtty.dca.service.DashboardService;
import com.timelordtty.dca.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

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
        // 只返回当前用户的待结算订单
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        List<Order> orders = dashboardService.getPendingSettlements().stream()
                .filter(o -> o != null && o.getUserId() != null && o.getUserId().equals(currentUser.getId()))
                .collect(Collectors.toList());
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
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        List<DashboardService.TodayAction> actions = dashboardService.getTodayActions(currentUser.getId());
        return ResponseEntity.ok(actions);
    }
}

