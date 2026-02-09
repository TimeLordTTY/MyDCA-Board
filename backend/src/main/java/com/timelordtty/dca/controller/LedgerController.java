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
import java.util.Set;

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
            @RequestParam(required = false) Long parentAccountId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String note,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        List<LedgerTxn> txns = ledgerService.getTransactions(
                currentUser.getId(), txnType, startDate, endDate, productId, parentAccountId, accountId, note, page, pageSize);
        int total = ledgerService.countTransactions(
                currentUser.getId(), txnType, startDate, endDate, productId, parentAccountId, accountId, note);
        
        // 计算允许的账户ID集合（用于后续过滤展示记录）
        Set<Long> allowedAccountIds = null;
        if (accountId != null) {
            // 如果指定了具体账户，只允许该账户
            allowedAccountIds = new java.util.HashSet<>();
            allowedAccountIds.add(accountId);
        } else if (parentAccountId != null) {
            // 如果指定了父账户，获取其所有子账户
            List<Account> children = accountService.getAccountChildren(parentAccountId);
            if (children != null && !children.isEmpty()) {
                allowedAccountIds = children.stream().map(Account::getId).collect(java.util.stream.Collectors.toSet());
            }
        }
        
        // 批量查询优化：一次性查询所有交易的postings
        List<String> txnIds = txns.stream().map(LedgerTxn::getTxnId).collect(java.util.stream.Collectors.toList());
        List<LedgerPosting> allPostings = ledgerService.getPostingsByTxnIds(txnIds);
        
        // 按txnId分组postings
        Map<String, List<LedgerPosting>> postingsByTxnId = new HashMap<>();
        for (LedgerPosting posting : allPostings) {
            postingsByTxnId.computeIfAbsent(posting.getTxnId(), k -> new ArrayList<>()).add(posting);
        }
        
        // 收集所有需要的accountId，批量查询账户
        Set<Long> accountIds = new java.util.HashSet<>();
        for (LedgerPosting posting : allPostings) {
            accountIds.add(posting.getAccountId());
        }
        // 还需要收集父账户ID
        Map<Long, Account> accountMap = accountService.getAccountsByIds(new ArrayList<>(accountIds));
        Set<Long> parentAccountIds = new java.util.HashSet<>();
        for (Account acc : accountMap.values()) {
            if (acc.getParentAccountId() != null) {
                parentAccountIds.add(acc.getParentAccountId());
            }
        }
        if (!parentAccountIds.isEmpty()) {
            Map<Long, Account> parentAccountMap = accountService.getAccountsByIds(new ArrayList<>(parentAccountIds));
            accountMap.putAll(parentAccountMap);
        }
        
        // 为每个交易计算摘要信息（金额、主要账户等）
        List<Map<String, Object>> result = new ArrayList<>();
        for (LedgerTxn txn : txns) {
            // 从Map中获取 postings（批量查询的结果）
            List<LedgerPosting> postings = postingsByTxnId.getOrDefault(txn.getTxnId(), new ArrayList<>());
            
            // 对于转账交易，生成两条记录（转出和转入）
            if (("TRANSFER_OUT".equals(txn.getTxnType()) || "TRANSFER_IN".equals(txn.getTxnType())) && postings.size() >= 2) {
                // 找到转出（CREDIT）和转入（DEBIT）的 REAL 账户分录
                LedgerPosting creditPosting = null;  // 转出
                LedgerPosting debitPosting = null;   // 转入
                
                for (LedgerPosting posting : postings) {
                    Account acc = accountMap.get(posting.getAccountId());
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
                    Map<String, Object> outMap = buildTxnMap(txn, creditPosting, "TRANSFER_OUT", true, accountMap);
                    result.add(outMap);
                }
                
                // 生成转入记录
                if (debitPosting != null) {
                    Map<String, Object> inMap = buildTxnMap(txn, debitPosting, "TRANSFER_IN", false, accountMap);
                    result.add(inMap);
                }
            } 
            // 对于赎回/卖出交易，如果存在出金（CREDIT）和入金（DEBIT）的 REAL CASH 账户，也生成对应记录
            else if (("SELL".equals(txn.getTxnType()) || "REDEMPTION".equals(txn.getTxnType())) && postings.size() >= 2) {
                // 收集所有出金（CREDIT）的 REAL CASH 账户分录，以及第一个入金（DEBIT）分录
                List<LedgerPosting> creditPostings = new ArrayList<>();  // 出金（产品关联账户减少），可能多个账户
                LedgerPosting debitPosting = null;   // 入金（赎回款到账）
                
                for (LedgerPosting posting : postings) {
                    if ("CASH".equals(posting.getAccountType())) {
                        Account acc = accountMap.get(posting.getAccountId());
                        if (acc != null && "REAL".equals(acc.getAccountKind())) {
                            if ("CREDIT".equals(posting.getPostingType())) {
                                creditPostings.add(posting);
                            } else if ("DEBIT".equals(posting.getPostingType()) && debitPosting == null) {
                                debitPosting = posting;
                            }
                        }
                    }
                }
                
                // 如果同时有出金和入金账户（说明是有关联账户的赎回），生成记录
                if (!creditPostings.isEmpty() && debitPosting != null) {
                    // 对于每个出金账户，生成一条出金记录（产品账户减少）
                    for (LedgerPosting creditPosting : creditPostings) {
                        Map<String, Object> outMap = buildTxnMap(txn, creditPosting, "REDEMPTION_OUT", true, accountMap);
                        outMap.put("note", txn.getNote() + " [出金]");
                        result.add(outMap);
                    }
                    
                    // 生成入金记录（赎回款到账）
                    Map<String, Object> inMap = buildTxnMap(txn, debitPosting, "REDEMPTION_IN", false, accountMap);
                    inMap.put("note", txn.getNote() + " [入金]");
                    result.add(inMap);
                } else {
                    // 没有同时存在出金和入金，正常处理
                    addNormalTxnMap(result, txn, postings, accountMap);
                }
            }
            // 对于支出交易，如果有多个 CASH CREDIT 分录（组合支付），为每个分录生成一条记录
            else if ("EXPENSE".equals(txn.getTxnType()) && postings.size() >= 2) {
                // 收集所有 CASH CREDIT 的 REAL 账户分录
                List<LedgerPosting> cashCreditPostings = new ArrayList<>();
                for (LedgerPosting posting : postings) {
                    if ("CASH".equals(posting.getAccountType()) && "CREDIT".equals(posting.getPostingType())) {
                        Account acc = accountMap.get(posting.getAccountId());
                        if (acc != null && "REAL".equals(acc.getAccountKind())) {
                            cashCreditPostings.add(posting);
                        }
                    }
                }
                
                // 如果有多个付款账户（组合支付），为每个账户生成一条记录
                if (cashCreditPostings.size() > 1) {
                    for (LedgerPosting posting : cashCreditPostings) {
                        Map<String, Object> expenseMap = buildTxnMap(txn, posting, "EXPENSE", true, accountMap);
                        result.add(expenseMap);
                    }
                } else {
                    // 单账户支付，正常处理
                    addNormalTxnMap(result, txn, postings, accountMap);
                }
            } else {
                // 非转账、非组合支付交易，正常处理
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
                    String currentTxnType = txn.getTxnType();
                    
                    // 对于转账类型，根据交易类型选择正确的分录
                    // TRANSFER_OUT: 显示转出账户（CREDIT），TRANSFER_IN: 显示转入账户（DEBIT）
                    if ("TRANSFER_OUT".equals(currentTxnType) || "TRANSFER_IN".equals(currentTxnType)) {
                        String targetPostingType = "TRANSFER_OUT".equals(currentTxnType) ? "CREDIT" : "DEBIT";
                        for (LedgerPosting posting : postings) {
                            if ("CASH".equals(posting.getAccountType()) && targetPostingType.equals(posting.getPostingType())) {
                                Account acc = accountMap.get(posting.getAccountId());
                                if (acc != null && "REAL".equals(acc.getAccountKind())) {
                                    // 转账金额：转出显示为负数，转入显示为正数
                                    summaryAmount = "CREDIT".equals(posting.getPostingType()) 
                                        ? posting.getAmount().negate() 
                                        : posting.getAmount();
                                    mainAccountId = posting.getAccountId();
                                    break;
                                }
                            }
                        }
                    }
                    
                    // 如果不是转账类型或没找到，优先查找 CASH 账户（真实账户）
                    if (mainAccountId == null) {
                        for (LedgerPosting posting : postings) {
                            if ("CASH".equals(posting.getAccountType())) {
                                Account acc = accountMap.get(posting.getAccountId());
                                if (acc != null && "REAL".equals(acc.getAccountKind())) {
                                    // 对于BUY/SUBSCRIPTION交易，CASH CREDIT表示现金减少，应该显示为负数
                                    if (("BUY".equals(currentTxnType) || "SUBSCRIPTION".equals(currentTxnType)) 
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
                    }
                    
                    // 如果没有找到 CASH 账户，查找其他 REAL 账户
                    if (mainAccountId == null) {
                        for (LedgerPosting posting : postings) {
                            Account acc = accountMap.get(posting.getAccountId());
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
                    
                    // 计算 summaryShares（优先从 POSITION 账户获取，否则从任何有 shares 字段的分录获取）
                    BigDecimal summaryShares = BigDecimal.ZERO;
                    for (LedgerPosting posting : postings) {
                        if ("POSITION".equals(posting.getAccountType()) && posting.getShares() != null) {
                            summaryShares = posting.getShares();
                            break;
                        }
                    }
                    // 如果 POSITION 账户没有 shares，尝试从其他分录获取
                    if (summaryShares.compareTo(BigDecimal.ZERO) == 0) {
                        for (LedgerPosting posting : postings) {
                            if (posting.getShares() != null && posting.getShares().compareTo(BigDecimal.ZERO) > 0) {
                                summaryShares = posting.getShares();
                                break;
                            }
                        }
                    }
                    txnMap.put("summaryShares", summaryShares);
                    
                    // 获取账户信息
                    populateAccountInfo(txnMap, mainAccountId, postings, accountMap);
                } else {
                    txnMap.put("summaryAmount", BigDecimal.ZERO);
                    txnMap.put("summaryShares", BigDecimal.ZERO);
                    txnMap.put("mainAccountId", null);
                    txnMap.put("leafAccountName", null);
                    txnMap.put("leafAccountBalance", null);
                    txnMap.put("parentAccountBalance", null);
                }
                
                result.add(txnMap);
            }
        }
        
        // 如果有账户过滤条件，过滤掉不匹配的展示记录
        // 这是因为一个交易可能涉及多个账户，会生成多条展示记录，
        // 我们只保留 mainAccountId 在允许列表中的记录
        List<Map<String, Object>> filteredResult = result;
        if (allowedAccountIds != null && !allowedAccountIds.isEmpty()) {
            final Set<Long> finalAllowedIds = allowedAccountIds;
            filteredResult = result.stream()
                .filter(map -> {
                    Object mainAccIdObj = map.get("mainAccountId");
                    if (mainAccIdObj == null) {
                        return false;
                    }
                    Long mainAccId = mainAccIdObj instanceof Long ? (Long) mainAccIdObj : Long.valueOf(mainAccIdObj.toString());
                    return finalAllowedIds.contains(mainAccId);
                })
                .collect(java.util.stream.Collectors.toList());
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("list", filteredResult);
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
        
        // 批量查询账户信息（优化性能）
        Set<Long> accountIds = new java.util.HashSet<>();
        for (LedgerPosting posting : postings) {
            accountIds.add(posting.getAccountId());
        }
        Map<Long, Account> accountMap = accountService.getAccountsByIds(new ArrayList<>(accountIds));
        // 收集父账户ID
        Set<Long> parentAccountIds = new java.util.HashSet<>();
        for (Account acc : accountMap.values()) {
            if (acc.getParentAccountId() != null) {
                parentAccountIds.add(acc.getParentAccountId());
            }
        }
        if (!parentAccountIds.isEmpty()) {
            Map<Long, Account> parentAccountMap = accountService.getAccountsByIds(new ArrayList<>(parentAccountIds));
            accountMap.putAll(parentAccountMap);
        }
        
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
            String txnType = txn.getTxnType();
            
            // 对于转账类型，根据交易类型选择正确的分录
            // TRANSFER_OUT: 显示转出账户（CREDIT），TRANSFER_IN: 显示转入账户（DEBIT）
            if ("TRANSFER_OUT".equals(txnType) || "TRANSFER_IN".equals(txnType)) {
                String targetPostingType = "TRANSFER_OUT".equals(txnType) ? "CREDIT" : "DEBIT";
                for (LedgerPosting posting : postings) {
                    if ("CASH".equals(posting.getAccountType()) && targetPostingType.equals(posting.getPostingType())) {
                        Account acc = accountMap.get(posting.getAccountId());
                        if (acc != null && "REAL".equals(acc.getAccountKind())) {
                            // 转账金额：转出显示为负数，转入显示为正数
                            summaryAmount = "CREDIT".equals(posting.getPostingType()) 
                                ? posting.getAmount().negate() 
                                : posting.getAmount();
                            mainAccountId = posting.getAccountId();
                            break;
                        }
                    }
                }
            }
            
            // 如果不是转账类型或没找到，优先查找 CASH 账户（真实账户）
            if (mainAccountId == null) {
                for (LedgerPosting posting : postings) {
                    if ("CASH".equals(posting.getAccountType())) {
                        Account acc = accountMap.get(posting.getAccountId());
                        if (acc != null && "REAL".equals(acc.getAccountKind())) {
                            // 对于BUY/SUBSCRIPTION交易，CASH CREDIT表示现金减少，应该显示为负数
                            if (("BUY".equals(txnType) || "SUBSCRIPTION".equals(txnType)) 
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
            }
            
            // 如果没有找到 CASH 账户，查找其他 REAL 账户
            if (mainAccountId == null) {
                for (LedgerPosting posting : postings) {
                    Account acc = accountMap.get(posting.getAccountId());
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
            
            // 计算 summaryShares（优先从 POSITION 账户获取，否则从任何有 shares 字段的分录获取）
            BigDecimal summaryShares = BigDecimal.ZERO;
            for (LedgerPosting posting : postings) {
                if ("POSITION".equals(posting.getAccountType()) && posting.getShares() != null) {
                    summaryShares = posting.getShares();
                    break;
                }
            }
            // 如果 POSITION 账户没有 shares，尝试从其他分录获取
            if (summaryShares.compareTo(BigDecimal.ZERO) == 0) {
                for (LedgerPosting posting : postings) {
                    if (posting.getShares() != null && posting.getShares().compareTo(BigDecimal.ZERO) > 0) {
                        summaryShares = posting.getShares();
                        break;
                    }
                }
            }
            result.put("summaryShares", summaryShares);
        } else {
            result.put("summaryAmount", BigDecimal.ZERO);
            result.put("summaryShares", BigDecimal.ZERO);
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

    /**
     * 更新一笔流水交易（先删除原交易，再按新数据重建）。
     * 
     * 用途：个人纠错，例如修改付款账户、金额、备注等。
     */
    @PutMapping("/txns/{txnId}")
    public ResponseEntity<LedgerTxn> updateTransaction(@PathVariable String txnId,
                                                       @RequestBody Map<String, Object> request) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        LedgerTxn existing = ledgerService.getTransactionDetail(txnId);
        if (existing == null || !existing.getUserId().equals(currentUser.getId())) {
            return ResponseEntity.notFound().build();
        }

        String txnType = request.containsKey("txnType")
                ? request.get("txnType").toString()
                : existing.getTxnType();
        String bizGroupKey = request.containsKey("bizGroupKey")
                ? request.get("bizGroupKey").toString()
                : existing.getBizGroupKey();
        String note = request.containsKey("note")
                ? request.get("note").toString()
                : existing.getNote();

        // 处理 requestedAt（如果未提供，则沿用原值）
        String requestedAtStr;
        if (request.containsKey("requestedAt") && request.get("requestedAt") != null) {
            requestedAtStr = request.get("requestedAt").toString();
        } else if (existing.getRequestedAt() != null) {
            java.time.format.DateTimeFormatter formatter =
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            requestedAtStr = existing.getRequestedAt().format(formatter);
        } else {
            requestedAtStr = null;
        }

        Long categoryId = request.containsKey("categoryId")
                ? Long.valueOf(request.get("categoryId").toString())
                : existing.getCategoryId();
        Boolean isReimbursable = request.containsKey("isReimbursable")
                ? Boolean.valueOf(request.get("isReimbursable").toString())
                : existing.getIsReimbursable();
        Long productId = request.containsKey("productId")
                ? Long.valueOf(request.get("productId").toString())
                : existing.getProductId();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> postingsData =
                (List<Map<String, Object>>) request.get("postings");
        if (postingsData == null || postingsData.isEmpty()) {
            throw new RuntimeException("postings 不能为空");
        }
        List<LedgerPosting> postings = new java.util.ArrayList<>();
        for (Map<String, Object> p : postingsData) {
            LedgerPosting posting = new LedgerPosting();
            posting.setPostingType(p.get("postingType").toString());
            posting.setAccountId(Long.valueOf(p.get("accountId").toString()));
            posting.setAccountType(p.get("accountType").toString());
            posting.setAmount(new BigDecimal(p.get("amount").toString()));
            if (p.containsKey("shares") && p.get("shares") != null) {
                posting.setShares(new BigDecimal(p.get("shares").toString()));
            }
            posting.setCurrency(p.getOrDefault("currency", "CNY").toString());
            postings.add(posting);
        }

        LedgerTxn updated = ledgerService.updateTransaction(
                txnId,
                txnType,
                bizGroupKey,
                postings,
                note,
                requestedAtStr,
                categoryId,
                isReimbursable,
                productId);
        return ResponseEntity.ok(updated);
    }

    /**
     * 删除一笔流水交易。
     */
    @DeleteMapping("/txns/{txnId}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable String txnId) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        LedgerTxn existing = ledgerService.getTransactionDetail(txnId);
        if (existing == null || !existing.getUserId().equals(currentUser.getId())) {
            return ResponseEntity.notFound().build();
        }
        ledgerService.deleteTransaction(txnId);
        return ResponseEntity.ok().build();
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
     * @param accountMap 账户Map（批量查询的结果，避免N+1查询）
     * @return 构建的Map
     */
    private Map<String, Object> buildTxnMap(LedgerTxn txn, LedgerPosting posting, String displayTxnType, boolean isOut, Map<Long, Account> accountMap) {
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
        
        // 份额：从分录中获取
        txnMap.put("summaryShares", posting.getShares() != null ? posting.getShares() : BigDecimal.ZERO);
        
        // 获取账户信息（从批量查询的Map中获取，避免N+1查询）
        Account account = accountMap.get(posting.getAccountId());
        if (account != null) {
            String leafAccountName = account.getAccountName();
            if (account.getParentAccountId() != null) {
                Account parentAccount = accountMap.get(account.getParentAccountId());
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
                    Account parentAccount = accountMap.get(account.getParentAccountId());
                    if (parentAccount != null) {
                        if (parentBalance == null) {
                            // 如果parentBalance为null，尝试从posting中获取，否则计算
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
     * @param accountMap 账户Map（批量查询的结果，避免N+1查询）
     */
    private void populateAccountInfo(Map<String, Object> txnMap, Long mainAccountId, List<LedgerPosting> postings, Map<Long, Account> accountMap) {
        if (mainAccountId != null) {
            Account account = accountMap.get(mainAccountId);
            if (account != null) {
                String leafAccountName = account.getAccountName();
                if (account.getParentAccountId() != null) {
                    Account parentAccount = accountMap.get(account.getParentAccountId());
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
                        Account parentAccount = accountMap.get(account.getParentAccountId());
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
    
    /**
     * 添加普通交易记录到结果列表（用于非转账、非赎回的交易）
     * @param accountMap 账户Map（批量查询的结果，避免N+1查询）
     */
    private void addNormalTxnMap(List<Map<String, Object>> result, LedgerTxn txn, List<LedgerPosting> postings, Map<Long, Account> accountMap) {
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
                    Account acc = accountMap.get(posting.getAccountId());
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
                    Account acc = accountMap.get(posting.getAccountId());
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
            
            // 计算 summaryShares（优先从 POSITION 账户获取，否则从任何有 shares 字段的分录获取）
            BigDecimal summaryShares = BigDecimal.ZERO;
            for (LedgerPosting posting : postings) {
                if ("POSITION".equals(posting.getAccountType()) && posting.getShares() != null) {
                    summaryShares = posting.getShares();
                    break;
                }
            }
            // 如果 POSITION 账户没有 shares，尝试从其他分录获取
            if (summaryShares.compareTo(BigDecimal.ZERO) == 0) {
                for (LedgerPosting posting : postings) {
                    if (posting.getShares() != null && posting.getShares().compareTo(BigDecimal.ZERO) > 0) {
                        summaryShares = posting.getShares();
                        break;
                    }
                }
            }
            txnMap.put("summaryShares", summaryShares);
            
            // 获取账户信息
            populateAccountInfo(txnMap, mainAccountId, postings, accountMap);
        } else {
            txnMap.put("summaryAmount", BigDecimal.ZERO);
            txnMap.put("summaryShares", BigDecimal.ZERO);
            txnMap.put("mainAccountId", null);
            txnMap.put("leafAccountName", null);
            txnMap.put("leafAccountBalance", null);
            txnMap.put("parentAccountBalance", null);
        }
        
        result.add(txnMap);
    }
    
    /**
     * 重算所有账户的历史余额
     * 
     * POST /api/v2/ledger/recalculate-all-balance-history
     * 
     * 用于修复历史数据中可能存在的父账户余额计算错误。
     * 注意：这个操作可能比较耗时，建议在低峰期执行。
     * 
     * @return 操作结果
     */
    @PostMapping("/recalculate-all-balance-history")
    public ResponseEntity<Map<String, Object>> recalculateAllBalanceHistory() {
        long startTime = System.currentTimeMillis();
        ledgerService.recalculateAllAccountBalanceHistory();
        long duration = System.currentTimeMillis() - startTime;
        
        Map<String, Object> result = new HashMap<>();
        result.put("message", "所有账户的历史余额已重新计算");
        result.put("durationMs", duration);
        return ResponseEntity.ok(result);
    }
}

