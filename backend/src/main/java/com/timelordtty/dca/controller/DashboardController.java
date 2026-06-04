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
     * 总览服务入口，负责聚合订单、持仓、账户等数据形成首页看板指标。
     */
    private final DashboardService dashboardService;
    /**
     * 用户身份服务，用于根据当前登录名定位用户、家庭和角色权限上下文。
     */
    private final UserService userService;
    /**
     * 家庭服务入口，用于校验家庭归属、管理员权限和成员范围。
     */
    private final FamilyService familyService;

    /**
     * 装配看板、用户和家庭服务，按当前登录上下文返回首页聚合数据。
     */
    public DashboardController(DashboardService dashboardService, UserService userService, FamilyService familyService) {
        this.dashboardService = dashboardService;
        this.userService = userService;
        this.familyService = familyService;
    }

    /**
     * 查询待结算订单列表，供首页或结算页提醒需要确认成交的订单。
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

