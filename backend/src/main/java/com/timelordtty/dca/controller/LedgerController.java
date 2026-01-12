package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.model.LedgerPosting;
import com.timelordtty.dca.model.LedgerTxn;
import com.timelordtty.dca.service.LedgerService;
import com.timelordtty.dca.service.QuickEntryService;
import com.timelordtty.dca.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 记账控制器（LedgerController）
 * 
 * 职责：提供流水查询、创建、退款、报销等REST API接口
 * 
 * API接口：
 * - GET /api/v2/ledger/txns - 获取流水列表（支持筛选）
 * - GET /api/v2/ledger/txns/{txnId} - 获取流水详情（包含所有postings）
 * - POST /api/v2/ledger/txns - 创建交易流水（双分录，支持组合支付）
 * - POST /api/v2/ledger/quick-entry - 快速录入（消费/收入）
 * - POST /api/v2/ledger/txns/{txnId}/refund - 创建退款交易
 * - POST /api/v2/ledger/txns/{txnId}/reimburse - 创建报销交易
 * 
 * 交易关联说明：
 * - 退款：使用relatedTxnId关联原消费交易，relationType='REFUND'
 * - 报销：使用relatedTxnId关联原消费交易，relationType='REIMBURSE'
 * - 转账：使用bizGroupKey关联转出和转入交易
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v2/ledger")
public class LedgerController {

    private final LedgerService ledgerService;
    private final QuickEntryService quickEntryService;
    private final UserService userService;

    public LedgerController(LedgerService ledgerService, QuickEntryService quickEntryService, UserService userService) {
        this.ledgerService = ledgerService;
        this.quickEntryService = quickEntryService;
        this.userService = userService;
    }

    @GetMapping("/txns")
    public ResponseEntity<List<LedgerTxn>> getTransactions(
            @RequestParam(required = false) String txnType,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) Long productId) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        List<LedgerTxn> txns = ledgerService.getTransactions(
                currentUser.getId(), txnType, startDate, endDate, productId);
        return ResponseEntity.ok(txns);
    }

    @GetMapping("/txns/{txnId}")
    public ResponseEntity<Map<String, Object>> getTransactionDetail(@PathVariable String txnId) {
        LedgerTxn txn = ledgerService.getTransactionDetail(txnId);
        List<LedgerPosting> postings = ledgerService.getPostingsByTxnId(txnId);
        // 将postings附加到响应中
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("id", txn.getId());
        result.put("txnId", txn.getTxnId());
        result.put("userId", txn.getUserId());
        result.put("familyId", txn.getFamilyId());
        result.put("txnType", txn.getTxnType());
        result.put("bizGroupKey", txn.getBizGroupKey());
        result.put("productId", txn.getProductId());
        result.put("orderId", txn.getOrderId());
        result.put("relatedTxnId", txn.getRelatedTxnId());
        result.put("relatedOrderId", txn.getRelatedOrderId());
        result.put("relationType", txn.getRelationType());
        result.put("requestedAt", txn.getRequestedAt());
        result.put("tradeDate", txn.getTradeDate());
        result.put("navDate", txn.getNavDate());
        result.put("confirmDate", txn.getConfirmDate());
        result.put("fetchDate", txn.getFetchDate());
        result.put("status", txn.getStatus());
        result.put("note", txn.getNote());
        result.put("isReversed", txn.getIsReversed());
        result.put("reversedByTxnId", txn.getReversedByTxnId());
        result.put("createdAt", txn.getCreatedAt());
        result.put("updatedAt", txn.getUpdatedAt());
        result.put("postings", postings);
        // 从note中解析isReimbursable（如果存在）
        if (txn.getNote() != null && txn.getNote().contains("isReimbursable:true")) {
            result.put("isReimbursable", true);
        } else {
            result.put("isReimbursable", false);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/txns")
    public ResponseEntity<LedgerTxn> createTransaction(@RequestBody Map<String, Object> request) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        String txnType = request.get("txnType").toString();
        String bizGroupKey = request.containsKey("bizGroupKey") ? request.get("bizGroupKey").toString() : null;
        String note = request.containsKey("note") ? request.get("note").toString() : null;
        
        // 处理新字段
        String requestedAtStr = request.containsKey("requestedAt") ? request.get("requestedAt").toString() : null;
        Long categoryId = request.containsKey("categoryId") ? Long.valueOf(request.get("categoryId").toString()) : null;
        Boolean isReimbursable = request.containsKey("isReimbursable") ? Boolean.valueOf(request.get("isReimbursable").toString()) : false;
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> postingsData = (List<Map<String, Object>>) request.get("postings");
        List<LedgerPosting> postings = new java.util.ArrayList<>();
        for (Map<String, Object> p : postingsData) {
            LedgerPosting posting = new LedgerPosting();
            posting.setPostingType(p.get("postingType").toString());
            posting.setAccountId(Long.valueOf(p.get("accountId").toString()));
            posting.setAccountType(p.get("accountType").toString());
            posting.setAmount(new BigDecimal(p.get("amount").toString()));
            if (p.containsKey("shares")) {
                posting.setShares(new BigDecimal(p.get("shares").toString()));
            }
            posting.setCurrency(p.getOrDefault("currency", "CNY").toString());
            postings.add(posting);
        }

        LedgerTxn txn = ledgerService.createTransaction(
                currentUser.getId(), currentUser.getFamilyId(), txnType, bizGroupKey, postings, note, requestedAtStr);
        return ResponseEntity.ok(txn);
    }

    @PostMapping("/quick-entry")
    public ResponseEntity<LedgerTxn> quickEntry(@RequestBody Map<String, Object> request) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        String type = request.get("type").toString(); // EXPENSE or INCOME
        Long accountId = Long.valueOf(request.get("accountId").toString());
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        String note = request.containsKey("note") ? request.get("note").toString() : null;
        String occurredAt = request.containsKey("occurredAt") ? request.get("occurredAt").toString() : null;
        Long categoryId = request.containsKey("categoryId") ? Long.valueOf(request.get("categoryId").toString()) : null;
        Boolean isReimbursable = request.containsKey("isReimbursable") ? Boolean.valueOf(request.get("isReimbursable").toString()) : false;

        // 将categoryId信息附加到note中
        if (categoryId != null) {
            note = (note != null ? note : "") + " [categoryId:" + categoryId + "]";
        }

        LedgerTxn txn;
        if ("EXPENSE".equals(type)) {
            txn = quickEntryService.quickExpense(currentUser.getId(), accountId, amount, note, occurredAt, categoryId, isReimbursable);
        } else {
            txn = quickEntryService.quickIncome(currentUser.getId(), accountId, amount, note, occurredAt, categoryId);
        }
        return ResponseEntity.ok(txn);
    }

    /**
     * 创建退款交易
     * 
     * 业务场景：用户消费后收到退款，需要冲减原消费交易
     * 
     * 请求参数：
     * - refundAmount: 退款金额
     * - accountId: 退款到账账户ID（必须是叶子账户）
     * - note: 备注（可选）
     * 
     * 返回：创建的退款交易记录
     * 
     * @param txnId 原交易ID（被退款的消费交易）
     * @param request 请求体，包含refundAmount、accountId、note
     * @return 退款交易记录
     */
    @PostMapping("/txns/{txnId}/refund")
    public ResponseEntity<LedgerTxn> createRefund(@PathVariable String txnId, 
                                                   @RequestBody Map<String, Object> request) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        BigDecimal refundAmount = new BigDecimal(request.get("refundAmount").toString());
        Long accountId = Long.valueOf(request.get("accountId").toString());
        String note = request.containsKey("note") ? request.get("note").toString() : null;

        LedgerTxn refundTxn = ledgerService.createRefund(
            currentUser.getId(), currentUser.getFamilyId(), txnId, refundAmount, accountId, note);
        return ResponseEntity.ok(refundTxn);
    }

    /**
     * 创建报销交易
     * 
     * 业务场景：用户消费后可报销，收到报销款
     * 
     * 请求参数：
     * - reimburseAmount: 报销金额
     * - accountId: 报销到账账户ID（必须是叶子账户）
     * - note: 备注（可选）
     * 
     * 返回：创建的报销交易记录
     * 
     * @param txnId 原交易ID（被报销的消费交易）
     * @param request 请求体，包含reimburseAmount、accountId、note
     * @return 报销交易记录
     */
    @PostMapping("/txns/{txnId}/reimburse")
    public ResponseEntity<LedgerTxn> createReimburse(@PathVariable String txnId, 
                                                     @RequestBody Map<String, Object> request) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        BigDecimal reimburseAmount = new BigDecimal(request.get("reimburseAmount").toString());
        Long accountId = Long.valueOf(request.get("accountId").toString());
        String note = request.containsKey("note") ? request.get("note").toString() : null;

        LedgerTxn reimburseTxn = ledgerService.createReimburse(
            currentUser.getId(), currentUser.getFamilyId(), txnId, reimburseAmount, accountId, note);
        return ResponseEntity.ok(reimburseTxn);
    }

    /**
     * 创建转托管交易
     * 
     * 请求参数：
     * - productId: 产品ID
     * - transferShares: 转出份额
     * - transferPrice: 转出价格（通常为0费用）
     * - transferDate: 转出日期
     * - note: 备注（可选）
     * 
     * @param request 请求体
     * @return 转托管交易记录
     */
    @PostMapping("/txns/custody-transfer")
    public ResponseEntity<LedgerTxn> createCustodyTransfer(@RequestBody Map<String, Object> request) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        Long productId = Long.valueOf(request.get("productId").toString());
        BigDecimal transferShares = new BigDecimal(request.get("shares").toString());
        BigDecimal transferPrice = request.containsKey("transferPrice") 
            ? new BigDecimal(request.get("transferPrice").toString()) 
            : BigDecimal.ZERO;
        String transferDateStr = request.get("transferDate").toString();
        String note = request.containsKey("note") ? request.get("note").toString() : null;

        LocalDate transferDate = LocalDate.parse(transferDateStr);

        LedgerTxn transferTxn = ledgerService.createCustodyTransfer(
            currentUser.getId(), currentUser.getFamilyId(), productId, transferShares, transferPrice, transferDate, note);
        return ResponseEntity.ok(transferTxn);
    }
}

