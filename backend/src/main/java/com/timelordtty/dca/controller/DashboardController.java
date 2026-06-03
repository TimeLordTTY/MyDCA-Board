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
/**
 * 业务注释规范化: DashboardController 控制器，负责接收前端请求、读取用户上下文，并将业务处理委托给服务层。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class DashboardController {

    /**
     * 业务注释规范化: dashboardService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final DashboardService dashboardService;
    /**
     * 业务注释规范化: userService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final UserService userService;
    /**
     * 业务注释规范化: familyService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final FamilyService familyService;

    /**
     * 业务注释规范化: 处理 DashboardController 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param dashboardService dashboardService 业务字段，承载该对象在后端流程中的核心属性。
     * @param userService userService 业务字段，承载该对象在后端流程中的核心属性。
     * @param familyService familyService 业务字段，承载该对象在后端流程中的核心属性。
     */
    public DashboardController(DashboardService dashboardService, UserService userService, FamilyService familyService) {
        this.dashboardService = dashboardService;
        this.userService = userService;
        this.familyService = familyService;
    }

    @GetMapping("/pending-settlements")
    /**
     * 业务注释规范化: 查询 getPendingSettlements 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<List<Order>> getPendingSettlements() {
        // 只返回当前用户的待结算订单
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        List<Order> orders = dashboardService.getPendingSettlements().stream()
                .filter(o -> o != null && o.getUserId() != null && o.getUserId().equals(currentUser.getId()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/asset-overview")
    /**
     * 业务注释规范化: 查询 getAssetOverview 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param viewType viewType 类型字段，用于区分不同业务分类并驱动处理分支。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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
    /**
     * 业务注释规范化: 查询 getTodayActions 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<List<DashboardService.TodayAction>> getTodayActions() {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        List<DashboardService.TodayAction> actions = dashboardService.getTodayActions(currentUser.getId());
        return ResponseEntity.ok(actions);
    }
}

