package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.model.Order;
import com.timelordtty.dca.model.LedgerTxn;
import com.timelordtty.dca.model.LedgerPosting;
import com.timelordtty.dca.service.OrderService;
import com.timelordtty.dca.service.SettlementService;
import com.timelordtty.dca.service.UserService;
import com.timelordtty.dca.mapper.LedgerTxnMapper;
import com.timelordtty.dca.mapper.LedgerPostingMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final SettlementService settlementService;
    private final LedgerTxnMapper ledgerTxnMapper;
    private final LedgerPostingMapper ledgerPostingMapper;

    public OrderController(OrderService orderService, UserService userService, SettlementService settlementService,
                          LedgerTxnMapper ledgerTxnMapper, LedgerPostingMapper ledgerPostingMapper) {
        this.orderService = orderService;
        this.userService = userService;
        this.settlementService = settlementService;
        this.ledgerTxnMapper = ledgerTxnMapper;
        this.ledgerPostingMapper = ledgerPostingMapper;
    }

    @GetMapping
    public ResponseEntity<List<Order>> getOrders(@RequestParam(required = false) String status) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        if (status != null && !status.isEmpty()) {
            // 如果指定了status，按status查询
            return ResponseEntity.ok(orderService.getOrdersByStatus(status));
        }
        // 如果没有指定status，默认返回当前用户的所有订单
        return ResponseEntity.ok(orderService.getOrdersByUserId(currentUser.getId()));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrder(@PathVariable String orderId) {
        Order order = orderService.getOrderByOrderId(orderId);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        
        // 查询资金来源行
        List<com.timelordtty.dca.model.OrderFundingLine> fundingLines = 
            orderService.getOrderFundingLines(orderId);
        
        // 查询结算确认信息
        com.timelordtty.dca.model.SettlementConfirm settlement = 
            orderService.getSettlementByOrderId(orderId);
        
        // 查询实际费用（从ledger_posting中查询FEE类型的记录）
        BigDecimal actualFee = order.getFeeEstimate();
        if (settlement != null && settlement.getConfirmFee() != null) {
            actualFee = settlement.getConfirmFee();
        } else {
            // 如果没有结算确认，尝试从ledger_posting中查询
            List<LedgerTxn> txns = ledgerTxnMapper.selectByOrderId(orderId);
            for (LedgerTxn txn : txns) {
                List<LedgerPosting> postings = ledgerPostingMapper.selectByTxnId(txn.getTxnId());
                for (LedgerPosting posting : postings) {
                    if ("FEE".equals(posting.getAccountType()) && posting.getAmount() != null) {
                        actualFee = posting.getAmount();
                        break;
                    }
                }
                if (actualFee != null && actualFee.compareTo(BigDecimal.ZERO) > 0) {
                    break;
                }
            }
        }
        
        // 构建返回对象
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("id", order.getId());
        result.put("orderId", order.getOrderId());
        result.put("userId", order.getUserId());
        result.put("productId", order.getProductId());
        result.put("orderType", order.getOrderType());
        result.put("amount", order.getAmount());
        result.put("shares", order.getShares());
        result.put("requestedAt", order.getRequestedAt());
        result.put("tradeDate", order.getTradeDate());
        result.put("expectedNavDate", order.getExpectedNavDate());
        result.put("expectedConfirmDate", order.getExpectedConfirmDate());
        result.put("status", order.getStatus());
        result.put("feeEstimate", actualFee);  // 使用实际费用
        result.put("note", order.getNote());
        result.put("createdAt", order.getCreatedAt());
        result.put("updatedAt", order.getUpdatedAt());
        result.put("fundingLines", fundingLines);
        if (settlement != null) {
            result.put("settlement", settlement);
        }
        
        return ResponseEntity.ok(result);
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
        Long accountId = request.containsKey("accountId") ? 
            Long.valueOf(request.get("accountId").toString()) : null;

        // 支持组合支付（fundingLines）
        List<com.timelordtty.dca.model.OrderFundingLine> fundingLines = null;
        if (request.containsKey("fundingLines")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fundingLinesData = (List<Map<String, Object>>) request.get("fundingLines");
            fundingLines = new java.util.ArrayList<>();
            for (Map<String, Object> fl : fundingLinesData) {
                com.timelordtty.dca.model.OrderFundingLine fundingLine = new com.timelordtty.dca.model.OrderFundingLine();
                fundingLine.setAccountId(Long.valueOf(fl.get("accountId").toString()));
                // 买入时使用amount，卖出时使用shares
                if (fl.containsKey("amount") && fl.get("amount") != null) {
                    fundingLine.setAmount(new BigDecimal(fl.get("amount").toString()));
                }
                if (fl.containsKey("shares") && fl.get("shares") != null) {
                    fundingLine.setShares(new BigDecimal(fl.get("shares").toString()));
                }
                fundingLines.add(fundingLine);
            }
        }

        Order order = orderService.createOrder(currentUser.getId(), productId, orderType, amount, shares, accountId, fundingLines);
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(@PathVariable String orderId) {
        orderService.cancelOrder(orderId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{orderId}/settle")
    public ResponseEntity<Map<String, Object>> settleOrder(@PathVariable String orderId, @RequestBody Map<String, Object> request) {
        LocalDate confirmDate = LocalDate.parse(request.get("confirmDate").toString());
        LocalDate navDate = LocalDate.parse(request.get("navDate").toString());
        BigDecimal confirmNav = new BigDecimal(request.get("confirmNav").toString());
        BigDecimal confirmShares = request.containsKey("confirmShares") && request.get("confirmShares") != null
            ? new BigDecimal(request.get("confirmShares").toString()) : null;
        BigDecimal confirmAmount = request.containsKey("confirmAmount") && request.get("confirmAmount") != null
            ? new BigDecimal(request.get("confirmAmount").toString()) : null;
        BigDecimal confirmFee = request.containsKey("confirmFee") && request.get("confirmFee") != null
            ? new BigDecimal(request.get("confirmFee").toString()) : BigDecimal.ZERO;

        com.timelordtty.dca.model.SettlementConfirm settlement = settlementService.confirmSettlement(
            orderId, confirmDate, navDate, confirmNav, confirmShares, confirmAmount, confirmFee
        );

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("success", true);
        result.put("settlement", settlement);
        return ResponseEntity.ok(result);
    }
}

