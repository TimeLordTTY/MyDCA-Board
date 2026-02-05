package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.model.Order;
import com.timelordtty.dca.model.LedgerTxn;
import com.timelordtty.dca.model.LedgerPosting;
import com.timelordtty.dca.model.ProductMaster;
import com.timelordtty.dca.service.OrderService;
import com.timelordtty.dca.service.SettlementService;
import com.timelordtty.dca.service.UserService;
import com.timelordtty.dca.service.BrokerFeeService;
import com.timelordtty.dca.mapper.LedgerTxnMapper;
import com.timelordtty.dca.mapper.LedgerPostingMapper;
import com.timelordtty.dca.mapper.ProductMasterMapper;
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
    private final BrokerFeeService brokerFeeService;
    private final ProductMasterMapper productMasterMapper;

    public OrderController(OrderService orderService, UserService userService, SettlementService settlementService,
                          LedgerTxnMapper ledgerTxnMapper, LedgerPostingMapper ledgerPostingMapper,
                          BrokerFeeService brokerFeeService, ProductMasterMapper productMasterMapper) {
        this.orderService = orderService;
        this.userService = userService;
        this.settlementService = settlementService;
        this.ledgerTxnMapper = ledgerTxnMapper;
        this.ledgerPostingMapper = ledgerPostingMapper;
        this.brokerFeeService = brokerFeeService;
        this.productMasterMapper = productMasterMapper;
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
            // 如果没有结算确认，尝试从ledger_posting中查询（使用批量查询优化）
            List<LedgerTxn> txns = ledgerTxnMapper.selectByOrderId(orderId);
            if (!txns.isEmpty()) {
                // 批量查询所有交易的postings
                List<String> txnIds = txns.stream().map(LedgerTxn::getTxnId).collect(java.util.stream.Collectors.toList());
                List<LedgerPosting> allPostings = ledgerPostingMapper.selectByTxnIds(txnIds);
                // 查找FEE类型的posting
                for (LedgerPosting posting : allPostings) {
                    if ("FEE".equals(posting.getAccountType()) && posting.getAmount() != null) {
                        actualFee = posting.getAmount();
                        break;
                    }
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
                // 解析 lineType：SOURCE（出金来源）或 TARGET（到账目标）
                if (fl.containsKey("lineType") && fl.get("lineType") != null) {
                    fundingLine.setLineType(fl.get("lineType").toString());
                }
                fundingLines.add(fundingLine);
            }
        }

        // 解析预期日期
        LocalDate expectedNavDate = request.containsKey("expectedNavDate") && request.get("expectedNavDate") != null
            ? LocalDate.parse(request.get("expectedNavDate").toString()) : null;
        LocalDate expectedConfirmDate = request.containsKey("expectedConfirmDate") && request.get("expectedConfirmDate") != null
            ? LocalDate.parse(request.get("expectedConfirmDate").toString()) : null;

        // 解析发起时间（如果提供，使用用户指定的时间；否则使用系统当前时间）
        java.time.LocalDateTime requestedAt = null;
        if (request.containsKey("requestedAt") && request.get("requestedAt") != null) {
            try {
                // 解析格式：YYYY-MM-DD HH:mm:ss
                String requestedAtStr = request.get("requestedAt").toString();
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                requestedAt = java.time.LocalDateTime.parse(requestedAtStr, formatter);
            } catch (Exception e) {
                // 解析失败，使用当前时间
                requestedAt = java.time.LocalDateTime.now();
            }
        } else {
            requestedAt = java.time.LocalDateTime.now();
        }

        // 解析手续费（如果提供）
        BigDecimal feeEstimate = request.containsKey("feeEstimate") && request.get("feeEstimate") != null
            ? new BigDecimal(request.get("feeEstimate").toString()) : null;

        Order order = orderService.createOrder(currentUser.getId(), productId, orderType, amount, shares, accountId, fundingLines, expectedNavDate, expectedConfirmDate, requestedAt, feeEstimate);
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
        // 如果前端传递了confirmFee（包括0），使用传递的值；如果未传递，传递null让后端自动计算
        BigDecimal confirmFee = request.containsKey("confirmFee") && request.get("confirmFee") != null
            ? new BigDecimal(request.get("confirmFee").toString()) : null;

        com.timelordtty.dca.model.SettlementConfirm settlement = settlementService.confirmSettlement(
            orderId, confirmDate, navDate, confirmNav, confirmShares, confirmAmount, confirmFee
        );

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("success", true);
        result.put("settlement", settlement);
        return ResponseEntity.ok(result);
    }

    /**
     * 计算场内交易手续费
     * 
     * @param request 包含 productId, accountId, orderType, amount
     * @return 计算后的手续费
     */
    @PostMapping("/calculate-fee")
    public ResponseEntity<Map<String, Object>> calculateFee(@RequestBody Map<String, Object> request) {
        Long productId = Long.valueOf(request.get("productId").toString());
        Long accountId = request.containsKey("accountId") && request.get("accountId") != null 
            ? Long.valueOf(request.get("accountId").toString()) 
            : null;
        String orderType = request.get("orderType").toString();
        BigDecimal amount = new BigDecimal(request.get("amount").toString());

        // 获取产品信息
        ProductMaster product = productMasterMapper.selectById(productId);
        if (product == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "产品不存在"));
        }

        // 从账户ID中查找券商账户ID
        Long brokerAccountId = null;
        if (accountId != null) {
            brokerAccountId = brokerFeeService.findBrokerAccountId(List.of(accountId));
        }

        // 计算手续费
        BigDecimal fee = brokerFeeService.calculateFee(brokerAccountId, product, orderType, amount);

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("fee", fee);
        result.put("productId", productId);
        result.put("orderType", orderType);
        result.put("amount", amount);
        return ResponseEntity.ok(result);
    }
}

