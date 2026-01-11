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

    private final OrderService orderService;
    private final SettlementService settlementService;

    public SettlementController(OrderService orderService, SettlementService settlementService) {
        this.orderService = orderService;
        this.settlementService = settlementService;
    }

    @GetMapping("/pending")
    public ResponseEntity<List<Order>> getPendingSettlements() {
        List<Order> orders = orderService.getPendingOrders();
        return ResponseEntity.ok(orders);
    }

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

