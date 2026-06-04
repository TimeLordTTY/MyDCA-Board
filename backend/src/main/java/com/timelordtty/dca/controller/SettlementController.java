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
public class SettlementController {

    /**
     * 订单服务入口，负责订单查询、创建、取消以及与结算流程的衔接。
     */
    private final OrderService orderService;
    /**
     * 结算服务入口，负责订单成交确认、费用拆分和账本落账编排。
     */
    private final SettlementService settlementService;

    /**
     * 装配订单与结算服务，处理待结算订单查询和成交确认入口。
     */
    public SettlementController(OrderService orderService, SettlementService settlementService) {
        this.orderService = orderService;
        this.settlementService = settlementService;
    }

    /**
     * 查询待结算订单列表，供首页或结算页提醒需要确认成交的订单。
     */
    @GetMapping("/pending")
    public ResponseEntity<List<Order>> getPendingSettlements() {
        List<Order> orders = orderService.getPendingOrders();
        return ResponseEntity.ok(orders);
    }

    /**
     * 确认订单成交结算，写入结算明细并交由服务层生成账本影响。
     */
    @PostMapping("/confirm")
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

