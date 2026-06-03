package com.timelordtty.dca.controller;

import com.timelordtty.dca.model.Order;
import com.timelordtty.dca.model.SettlementConfirm;
import com.timelordtty.dca.service.OrderService;
import com.timelordtty.dca.service.SettlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 结算控制器
 */
@RestController
@RequestMapping("/api/v2/settlements")
/**
 * 业务注释规范化: SettlementController 控制器，负责接收前端请求、读取用户上下文，并将业务处理委托给服务层。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class SettlementController {

    /**
     * 业务注释规范化: orderService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final OrderService orderService;
    /**
     * 业务注释规范化: settlementService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final SettlementService settlementService;

    /**
     * 业务注释规范化: 处理 SettlementController 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param orderService orderService 业务字段，承载该对象在后端流程中的核心属性。
     * @param settlementService settlementService 业务字段，承载该对象在后端流程中的核心属性。
     */
    public SettlementController(OrderService orderService, SettlementService settlementService) {
        this.orderService = orderService;
        this.settlementService = settlementService;
    }

    @GetMapping("/pending")
    /**
     * 业务注释规范化: 查询 getPendingSettlements 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<List<Order>> getPendingSettlements() {
        List<Order> orders = orderService.getPendingOrders();
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/confirm")
    /**
     * 业务注释规范化: 处理 confirmSettlement 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param request request 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<SettlementConfirm> confirmSettlement(@RequestBody Map<String, Object> request) {
        String orderId = request.get("orderId").toString();
        LocalDate confirmDate = LocalDate.parse(request.get("confirmDate").toString());
        LocalDate navDate = LocalDate.parse(request.get("navDate").toString());
        BigDecimal confirmNav = new BigDecimal(request.get("confirmNav").toString());
        BigDecimal confirmShares = request.containsKey("confirmShares") ? 
            new BigDecimal(request.get("confirmShares").toString()) : null;
        BigDecimal confirmAmount = request.containsKey("confirmAmount") ? 
            new BigDecimal(request.get("confirmAmount").toString()) : null;
        BigDecimal confirmFee = request.containsKey("confirmFee") ? 
            new BigDecimal(request.get("confirmFee").toString()) : BigDecimal.ZERO;

        SettlementConfirm settlement = settlementService.confirmSettlement(
                orderId, confirmDate, navDate, confirmNav, confirmShares, confirmAmount, confirmFee);
        return ResponseEntity.ok(settlement);
    }
}

