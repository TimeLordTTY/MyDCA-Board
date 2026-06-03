package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.model.Order;
import com.timelordtty.dca.service.DashboardService;
import com.timelordtty.dca.service.FamilyService;
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

    /**
     * 依赖的 DashboardService 服务，用于复用该领域的业务校验和事务逻辑。
     */
    private final DashboardService dashboardService;
    /**
     * 依赖的 UserService 服务，用于复用该领域的业务校验和事务逻辑。
     */
    private final UserService userService;
    /**
     * 依赖的 FamilyService 服务，用于复用该领域的业务校验和事务逻辑。
     */
    private final FamilyService familyService;

    /**
     * 处理写入类 API，将请求参数校验后委托给 Service 层。
     * 是否产生账本、账户或订单变更由对应 Service 事务边界决定。
     */
    public DashboardController(DashboardService dashboardService, UserService userService, FamilyService familyService) {
        this.dashboardService = dashboardService;
        this.userService = userService;
        this.familyService = familyService;
    }

    /**
     * 返回当前场景的业务数据，用于前后端传递或服务层计算。
     */
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
        if ("FAMILY".equals(normalizedViewType)) {
            if (currentUser.getFamilyId() == null) {
                return ResponseEntity.badRequest().build();
            }
            // 只有家庭管理员才能查看家庭总览
            familyService.assertAdmin(currentUser.getId(), currentUser.getFamilyId());
        }
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

