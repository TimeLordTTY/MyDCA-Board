 package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.LedgerPosting;
import com.timelordtty.dca.model.LedgerTxn;
import com.timelordtty.dca.service.AccountService;
import com.timelordtty.dca.service.LedgerService;
import com.timelordtty.dca.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final UserService userService;
    private final AccountService accountService;

    public LedgerController(LedgerService ledgerService, UserService userService, AccountService accountService) {
        this.ledgerService = ledgerService;
        this.userService = userService;
        this.accountService = accountService;
    }

    @GetMapping("/txns")
    public ResponseEntity<Map<String, Object>> getTransactions(
            @RequestParam(required = false) String txnType,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        List<LedgerTxn> txns = ledgerService.getTransactions(
                currentUser.getId(), txnType, startDate, endDate, productId, accountId, page, pageSize);
        int total = ledgerService.countTransactions(
                currentUser.getId(), txnType, startDate, endDate, productId, accountId);
        
        // 为每个交易计算摘要信息（金额、主要账户等）
        List<Map<String, Object>> result = new ArrayList<>();
        for (LedgerTxn txn : txns) {
            // 获取 postings
            List<LedgerPosting> postings = ledgerService.getPostingsByTxnId(txn.getTxnId());
            
            // 对于转账交易，生成两条记录（转出和转入）
            if (("TRANSFER_OUT".equals(txn.getTxnType()) || "TRANSFER_IN".equals(txn.getTxnType())) && postings.size() >= 2) {
                // 找到转出（CREDIT）和转入（DEBIT）的 REAL 账户分录
                LedgerPosting creditPosting = null;  // 转出
                LedgerPosting debitPosting = null;   // 转入
                
                for (LedgerPosting posting : postings) {
                    Account acc = accountService.getAccount(posting.getAccountId());
                    if (acc != null && "REAL".equals(acc.getAccountKind())) {
                        if ("CREDIT".equals(posting.getPostingType()) && creditPosting == null) {
                            creditPosting = posting;
                        } else if ("DEBIT".equals(posting.getPostingType()) && debitPosting == null) {
                            debitPosting = posting;
                        }
                    }
                }
                
                // 生成转出记录
                if (creditPosting != null) {
                    Map<String, Object> outMap = buildTxnMap(txn, creditPosting, "TRANSFER_OUT", true);
                    result.add(outMap);
                }
                
                // 生成转入记录
                if (debitPosting != null) {
                    Map<String, Object> inMap = buildTxnMap(txn, debitPosting, "TRANSFER_IN", false);
                    result.add(inMap);
                }
            } else {
                // 非转账交易，正常处理
                Map<String, Object> txnMap = new HashMap<>();
                txnMap.put("id", txn.getId());
                txnMap.put("txnId", txn.getTxnId());
                txnMap.put("userId", txn.getUserId());
                txnMap.put("familyId", txn.getFamilyId());
                txnMap.put("txnType", txn.getTxnType());
                txnMap.put("bizGroupKey", txn.getBizGroupKey());
                txnMap.put("productId", txn.getProductId());
                txnMap.put("orderId", txn.getOrderId());
                txnMap.put("relatedTxnId", txn.getRelatedTxnId());
                txnMap.put("relatedOrderId", txn.getRelatedOrderId());
                txnMap.put("relationType", txn.getRelationType());
                txnMap.put("requestedAt", txn.getRequestedAt());
                txnMap.put("tradeDate", txn.getTradeDate());
                txnMap.put("navDate", txn.getNavDate());
                txnMap.put("confirmDate", txn.getConfirmDate());
                txnMap.put("fetchDate", txn.getFetchDate());
                txnMap.put("status", txn.getStatus());
                txnMap.put("note", txn.getNote());
                txnMap.put("categoryId", txn.getCategoryId());
                txnMap.put("isReimbursable", txn.getIsReimbursable());
                txnMap.put("isReimbursed", txn.getIsReimbursed());
                txnMap.put("isReversed", txn.getIsReversed());
                txnMap.put("reversedByTxnId", txn.getReversedByTxnId());
                txnMap.put("createdAt", txn.getCreatedAt());
                txnMap.put("updatedAt", txn.getUpdatedAt());
                
                if (!postings.isEmpty()) {
                    // 计算主要金额（对于收入/支出，取 CASH 账户的金额）
                    BigDecimal summaryAmount = BigDecimal.ZERO;
                    Long mainAccountId = null;
                    
                    // 优先查找 CASH 账户（真实账户）
                    for (LedgerPosting posting : postings) {
                        if ("CASH".equals(posting.getAccountType())) {
                            Account acc = accountService.getAccount(posting.getAccountId());
                            if (acc != null && "REAL".equals(acc.getAccountKind())) {
                                // 对于BUY/SUBSCRIPTION交易，CASH CREDIT表示现金减少，应该显示为负数
                                if (("BUY".equals(txn.getTxnType()) || "SUBSCRIPTION".equals(txn.getTxnType())) 
                                    && "CREDIT".equals(posting.getPostingType())) {
                                    summaryAmount = posting.getAmount().negate();
                                } else {
                                    summaryAmount = posting.getAmount();
                                }
                                mainAccountId = posting.getAccountId();
                                break;
                            }
                        }
                    }
                    
                    // 如果没有找到 CASH 账户，查找其他 REAL 账户
                    if (mainAccountId == null) {
                        for (LedgerPosting posting : postings) {
                            Account acc = accountService.getAccount(posting.getAccountId());
                            if (acc != null && "REAL".equals(acc.getAccountKind())) {
                                summaryAmount = posting.getAmount();
                                mainAccountId = posting.getAccountId();
                                break;
                            }
                        }
                    }
                    
                    // 如果还是没有找到，取第一个 postings 的金额（可能是虚拟账户）
                    if (mainAccountId == null && !postings.isEmpty()) {
                        summaryAmount = postings.get(0).getAmount();
                        mainAccountId = postings.get(0).getAccountId();
                    }
                    
                    txnMap.put("summaryAmount", summaryAmount);
                    txnMap.put("mainAccountId", mainAccountId);
                    
                    // 获取账户信息
                    populateAccountInfo(txnMap, mainAccountId, postings);
                } else {
                    txnMap.put("summaryAmount", BigDecimal.ZERO);
                    txnMap.put("mainAccountId", null);
                    txnMap.put("leafAccountName", null);
                    txnMap.put("leafAccountBalance", null);
                    txnMap.put("parentAccountBalance", null);
                }
                
                result.add(txnMap);
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("list", result);
        response.put("total", total);
        response.put("page", page);
        response.put("pageSize", pageSize);
        response.put("totalPages", (int) Math.ceil((double) total / pageSize));
        
        return ResponseEntity.ok(response);
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
        result.put("categoryId", txn.getCategoryId());
        result.put("isReimbursable", txn.getIsReimbursable());
        result.put("isReimbursed", txn.getIsReimbursed());
        result.put("isReversed", txn.getIsReversed());
        result.put("reversedByTxnId", txn.getReversedByTxnId());
        result.put("createdAt", txn.getCreatedAt());
        result.put("updatedAt", txn.getUpdatedAt());
        result.put("postings", postings);
        
        // 计算 summaryAmount（与列表接口逻辑一致）
        if (!postings.isEmpty()) {
            BigDecimal summaryAmount = BigDecimal.ZERO;
            Long mainAccountId = null;
            
            // 优先查找 CASH 账户（真实账户）
            for (LedgerPosting posting : postings) {
                if ("CASH".equals(posting.getAccountType())) {
                    Account acc = accountService.getAccount(posting.getAccountId());
                    if (acc != null && "REAL".equals(acc.getAccountKind())) {
                        // 对于BUY/SUBSCRIPTION交易，CASH CREDIT表示现金减少，应该显示为负数
                        if (("BUY".equals(txn.getTxnType()) || "SUBSCRIPTION".equals(txn.getTxnType())) 
                            && "CREDIT".equals(posting.getPostingType())) {
                            summaryAmount = posting.getAmount().negate();
                        } else {
                            summaryAmount = posting.getAmount();
                        }
                        mainAccountId = posting.getAccountId();
                        break;
                    }
                }
            }
            
            // 如果没有找到 CASH 账户，查找其他 REAL 账户
            if (mainAccountId == null) {
                for (LedgerPosting posting : postings) {
                    Account acc = accountService.getAccount(posting.getAccountId());
                    if (acc != null && "REAL".equals(acc.getAccountKind())) {
                        summaryAmount = posting.getAmount();
                        mainAccountId = posting.getAccountId();
                        break;
                    }
                }
            }
            
            // 如果还是没有找到，取第一个 postings 的金额（可能是虚拟账户）
            if (mainAccountId == null && !postings.isEmpty()) {
                summaryAmount = postings.get(0).getAmount();
                mainAccountId = postings.get(0).getAccountId();
            }
            
            result.put("summaryAmount", summaryAmount);
            result.put("mainAccountId", mainAccountId);
        } else {
            result.put("summaryAmount", BigDecimal.ZERO);
            result.put("mainAccountId", null);
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
        Long productId = request.containsKey("productId") ? Long.valueOf(request.get("productId").toString()) : null;
        
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
                currentUser.getId(), currentUser.getFamilyId(), txnType, bizGroupKey, postings, note, requestedAtStr, categoryId, isReimbursable, productId);
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

        // 使用统一记账接口，支持更多参数
        List<LedgerPosting> postings = new java.util.ArrayList<>();
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new RuntimeException("账户不存在");
        }
        
        if ("EXPENSE".equals(type)) {
            // CASH CREDIT + EXPENSE DEBIT
            LedgerPosting cashPosting = new LedgerPosting();
            cashPosting.setPostingType("CREDIT");
            cashPosting.setAccountId(accountId);
            cashPosting.setAccountType("CASH");
            cashPosting.setAmount(amount);
            cashPosting.setCurrency(account.getCurrency());
            postings.add(cashPosting);
            
            String ownerType = account.getOwnerType() != null ? account.getOwnerType() : "PERSONAL";
            Account expenseAccount = accountService.getOrCreateVirtualAccount(
                "EXPENSE", "EXPENSE", ownerType, account.getOwnerUserId(), account.getOwnerFamilyId(), null, null);
            LedgerPosting expensePosting = new LedgerPosting();
            expensePosting.setPostingType("DEBIT");
            expensePosting.setAccountId(expenseAccount.getId());
            expensePosting.setAccountType("EXPENSE");
            expensePosting.setAmount(amount);
            expensePosting.setCurrency(account.getCurrency());
            postings.add(expensePosting);
        } else {
            // CASH DEBIT + INCOME CREDIT
            LedgerPosting cashPosting = new LedgerPosting();
            cashPosting.setPostingType("DEBIT");
            cashPosting.setAccountId(accountId);
            cashPosting.setAccountType("CASH");
            cashPosting.setAmount(amount);
            cashPosting.setCurrency(account.getCurrency());
            postings.add(cashPosting);
            
            String ownerType = account.getOwnerType() != null ? account.getOwnerType() : "PERSONAL";
            Account incomeAccount = accountService.getOrCreateVirtualAccount(
                "INCOME", "INCOME", ownerType, account.getOwnerUserId(), account.getOwnerFamilyId(), null, null);
            LedgerPosting incomePosting = new LedgerPosting();
            incomePosting.setPostingType("CREDIT");
            incomePosting.setAccountId(incomeAccount.getId());
            incomePosting.setAccountType("INCOME");
            incomePosting.setAmount(amount);
            incomePosting.setCurrency(account.getCurrency());
            postings.add(incomePosting);
        }
        
        LedgerTxn txn = ledgerService.createTransaction(
            currentUser.getId(), currentUser.getFamilyId(), type, null, postings, note, occurredAt, categoryId, isReimbursable);
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
        LocalDateTime requestedAt = request.containsKey("occurredAt") && request.get("occurredAt") != null
            ? LocalDateTime.parse(request.get("occurredAt").toString().replace(" ", "T"))
            : null;

        LedgerTxn refundTxn = ledgerService.createRefund(
            currentUser.getId(), currentUser.getFamilyId(), txnId, refundAmount, accountId, note, requestedAt);
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
        LocalDateTime requestedAt = request.containsKey("occurredAt") && request.get("occurredAt") != null
            ? LocalDateTime.parse(request.get("occurredAt").toString().replace(" ", "T"))
            : null;

        LedgerTxn reimburseTxn = ledgerService.createReimburse(
            currentUser.getId(), currentUser.getFamilyId(), txnId, reimburseAmount, accountId, note, requestedAt);
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
        // 支持 transferPrice 或 transferOutPrice 参数名
        BigDecimal transferPrice = request.containsKey("transferPrice") 
            ? new BigDecimal(request.get("transferPrice").toString()) 
            : (request.containsKey("transferOutPrice") 
                ? new BigDecimal(request.get("transferOutPrice").toString()) 
                : BigDecimal.ZERO);
        String transferDateStr = request.get("transferDate").toString();
        String note = request.containsKey("note") ? request.get("note").toString() : null;

        LocalDate transferDate = LocalDate.parse(transferDateStr);

        LedgerTxn transferTxn = ledgerService.createCustodyTransfer(
            currentUser.getId(), currentUser.getFamilyId(), productId, transferShares, transferPrice, transferDate, note);
        return ResponseEntity.ok(transferTxn);
    }
    
    /**
     * 为转账交易构建显示记录
     * @param txn 原始交易
     * @param posting 分录（转出或转入）
     * @param displayTxnType 显示的交易类型（TRANSFER_OUT 或 TRANSFER_IN）
     * @param isOut 是否为转出（用于金额正负号）
     * @return 构建的Map
     */
    private Map<String, Object> buildTxnMap(LedgerTxn txn, LedgerPosting posting, String displayTxnType, boolean isOut) {
        Map<String, Object> txnMap = new HashMap<>();
        // 使用 txnId + 后缀来区分转出和转入记录
        txnMap.put("id", txn.getId());
        txnMap.put("txnId", txn.getTxnId() + (isOut ? "_OUT" : "_IN"));
        txnMap.put("originalTxnId", txn.getTxnId());  // 保留原始txnId用于查看详情
        txnMap.put("userId", txn.getUserId());
        txnMap.put("familyId", txn.getFamilyId());
        txnMap.put("txnType", displayTxnType);
        txnMap.put("bizGroupKey", txn.getBizGroupKey());
        txnMap.put("productId", txn.getProductId());
        txnMap.put("orderId", txn.getOrderId());
        txnMap.put("relatedTxnId", txn.getRelatedTxnId());
        txnMap.put("relatedOrderId", txn.getRelatedOrderId());
        txnMap.put("relationType", txn.getRelationType());
        txnMap.put("requestedAt", txn.getRequestedAt());
        txnMap.put("tradeDate", txn.getTradeDate());
        txnMap.put("navDate", txn.getNavDate());
        txnMap.put("confirmDate", txn.getConfirmDate());
        txnMap.put("fetchDate", txn.getFetchDate());
        txnMap.put("status", txn.getStatus());
        txnMap.put("note", txn.getNote());
        txnMap.put("categoryId", txn.getCategoryId());
        txnMap.put("isReimbursable", txn.getIsReimbursable());
        txnMap.put("isReimbursed", txn.getIsReimbursed());
        txnMap.put("isReversed", txn.getIsReversed());
        txnMap.put("reversedByTxnId", txn.getReversedByTxnId());
        txnMap.put("createdAt", txn.getCreatedAt());
        txnMap.put("updatedAt", txn.getUpdatedAt());
        
        // 金额：转出为正（显示时会加负号），转入为正
        txnMap.put("summaryAmount", posting.getAmount());
        txnMap.put("mainAccountId", posting.getAccountId());
        
        // 获取账户信息
        Account account = accountService.getAccount(posting.getAccountId());
        if (account != null) {
            String leafAccountName = account.getAccountName();
            if (account.getParentAccountId() != null) {
                Account parentAccount = accountService.getAccount(account.getParentAccountId());
                if (parentAccount != null) {
                    leafAccountName = parentAccount.getAccountName() + "-" + account.getAccountName();
                }
            }
            txnMap.put("leafAccountName", leafAccountName);
            
            if ("REAL".equals(account.getAccountKind())) {
                BigDecimal leafBalance = posting.getAccountBalanceAfter();
                if (leafBalance == null) {
                    leafBalance = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
                }
                txnMap.put("leafAccountBalance", leafBalance);
                
                BigDecimal parentBalance = posting.getParentAccountBalanceAfter();
                if (account.getParentAccountId() != null) {
                    Account parentAccount = accountService.getAccount(account.getParentAccountId());
                    if (parentAccount != null) {
                        if (parentBalance == null) {
                            List<Account> children = accountService.getAccountChildren(account.getParentAccountId());
                            parentBalance = children.stream()
                                .map(Account::getBalance)
                                .filter(b -> b != null)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        }
                        txnMap.put("parentAccountBalance", parentBalance);
                        txnMap.put("parentAccountName", parentAccount.getAccountName());
                    } else {
                        txnMap.put("parentAccountBalance", BigDecimal.ZERO);
                        txnMap.put("parentAccountName", null);
                    }
                } else {
                    txnMap.put("parentAccountBalance", null);
                    txnMap.put("parentAccountName", null);
                }
            } else {
                txnMap.put("leafAccountBalance", null);
                txnMap.put("parentAccountBalance", null);
            }
        } else {
            txnMap.put("leafAccountName", null);
            txnMap.put("leafAccountBalance", null);
            txnMap.put("parentAccountBalance", null);
        }
        
        return txnMap;
    }
    
    /**
     * 填充账户信息到 txnMap
     */
    private void populateAccountInfo(Map<String, Object> txnMap, Long mainAccountId, List<LedgerPosting> postings) {
        if (mainAccountId != null) {
            Account account = accountService.getAccount(mainAccountId);
            if (account != null) {
                String leafAccountName = account.getAccountName();
                if (account.getParentAccountId() != null) {
                    Account parentAccount = accountService.getAccount(account.getParentAccountId());
                    if (parentAccount != null) {
                        leafAccountName = parentAccount.getAccountName() + "-" + account.getAccountName();
                    }
                }
                txnMap.put("leafAccountName", leafAccountName);
                
                if ("REAL".equals(account.getAccountKind())) {
                    BigDecimal leafBalance = null;
                    BigDecimal parentBalance = null;
                    for (LedgerPosting posting : postings) {
                        if (posting.getAccountId().equals(mainAccountId) && posting.getAccountBalanceAfter() != null) {
                            leafBalance = posting.getAccountBalanceAfter();
                            parentBalance = posting.getParentAccountBalanceAfter();
                            break;
                        }
                    }
                    
                    if (leafBalance == null) {
                        leafBalance = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
                    }
                    txnMap.put("leafAccountBalance", leafBalance);
                    
                    if (account.getParentAccountId() != null) {
                        Account parentAccount = accountService.getAccount(account.getParentAccountId());
                        if (parentAccount != null) {
                            if (parentBalance != null) {
                                txnMap.put("parentAccountBalance", parentBalance);
                            } else {
                                List<Account> children = accountService.getAccountChildren(account.getParentAccountId());
                                BigDecimal calculatedParentBalance = children.stream()
                                    .map(Account::getBalance)
                                    .filter(b -> b != null)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                                txnMap.put("parentAccountBalance", calculatedParentBalance);
                            }
                            txnMap.put("parentAccountName", parentAccount.getAccountName());
                        } else {
                            txnMap.put("parentAccountBalance", BigDecimal.ZERO);
                            txnMap.put("parentAccountName", null);
                        }
                    } else {
                        txnMap.put("parentAccountBalance", null);
                        txnMap.put("parentAccountName", null);
                    }
                } else {
                    txnMap.put("leafAccountBalance", null);
                    txnMap.put("parentAccountBalance", null);
                }
            } else {
                txnMap.put("leafAccountName", null);
                txnMap.put("leafAccountBalance", null);
                txnMap.put("parentAccountBalance", null);
            }
        } else {
            txnMap.put("leafAccountName", null);
            txnMap.put("leafAccountBalance", null);
            txnMap.put("parentAccountBalance", null);
        }
    }
}

