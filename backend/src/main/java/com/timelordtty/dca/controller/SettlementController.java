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
     * 依赖的 OrderService 服务，用于复用该领域的业务校验和事务逻辑。
     */
    private final OrderService orderService;
    /**
     * 依赖的 SettlementService 服务，用于复用该领域的业务校验和事务逻辑。
     */
    private final SettlementService settlementService;

    /**
     * 处理写入类 API，将请求参数校验后委托给 Service 层。
     * 是否产生账本、账户或订单变更由对应 Service 事务边界决定。
     */
    public SettlementController(OrderService orderService, SettlementService settlementService) {
        this.orderService = orderService;
        this.settlementService = settlementService;
    }

    /**
     * 返回当前场景的业务数据，用于前后端传递或服务层计算。
     */
    @GetMapping("/pending")
    public ResponseEntity<List<Order>> getPendingSettlements() {
        List<Order> orders = orderService.getPendingOrders();
        return ResponseEntity.ok(orders);
    }

    /**
     * 处理写入类 API，将请求参数校验后委托给 Service 层。
     * 是否产生账本、账户或订单变更由对应 Service 事务边界决定。
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

