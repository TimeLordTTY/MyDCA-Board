package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.model.Order;
import com.timelordtty.dca.service.OrderService;
import com.timelordtty.dca.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 订单控制器
 */
@RestController
@RequestMapping("/api/v2/orders")
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;

    public OrderController(OrderService orderService, UserService userService) {
        this.orderService = orderService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<Order>> getOrders(@RequestParam(required = false) String status) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        if ("PENDING".equals(status)) {
            return ResponseEntity.ok(orderService.getPendingOrders());
        }
        // 其他查询逻辑
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        // 实现获取订单详情
        return ResponseEntity.ok().build();
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Map<String, Object> request) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        Long productId = Long.valueOf(request.get("productId").toString());
        String orderType = request.get("orderType").toString();
        BigDecimal amount = request.containsKey("amount") ? 
            new BigDecimal(request.get("amount").toString()) : null;
        BigDecimal shares = request.containsKey("shares") ? 
            new BigDecimal(request.get("shares").toString()) : null;
        Long accountId = Long.valueOf(request.get("accountId").toString());

        Order order = orderService.createOrder(currentUser.getId(), productId, orderType, amount, shares, accountId);
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(@PathVariable String orderId) {
        orderService.cancelOrder(orderId);
        return ResponseEntity.ok().build();
    }
}

