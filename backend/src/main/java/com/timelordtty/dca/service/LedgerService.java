package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.mapper.LedgerPostingMapper;
import com.timelordtty.dca.mapper.LedgerTxnMapper;
import com.timelordtty.dca.mapper.ProductMasterMapper;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.LedgerPosting;
import com.timelordtty.dca.model.LedgerTxn;
import com.timelordtty.dca.model.ProductMaster;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 统一记账服务（LedgerService）
 * 
 * 核心功能：实现复式记账（双分录），确保借贷平衡，自动更新账户余额
 * 
 * 记账原理：
 * 1. 复式记账：每笔交易必须至少包含2个分录（DEBIT和CREDIT），且借贷金额必须相等
 * 2. 借贷方向：
 *    - 资产类账户（CASH/POSITION/RECEIVABLE）：DEBIT增加余额，CREDIT减少余额
 *    - 负债类账户（LIABILITY/CREDIT_CARD/HUABEI/BAITIAO/LOAN）：DEBIT减少余额，CREDIT增加余额
 *    - 收入类账户（INCOME）：CREDIT增加余额，DEBIT减少余额
 *    - 支出类账户（EXPENSE）：DEBIT增加余额，CREDIT减少余额
 * 
 * 余额计算公式：
 * - 资产类账户：balance = initial_balance + Σ(DEBIT金额) - Σ(CREDIT金额)
 * - 负债类账户：balance = initial_balance - Σ(DEBIT金额) + Σ(CREDIT金额)
 * 
 * 业务规则：
 * 1. 借贷必须平衡：Σ(DEBIT金额) = Σ(CREDIT金额)，否则抛出异常
 * 2. 只能对叶子账户记账：ledger_posting.account_id必须引用叶子账户，禁止对父账户记账
 * 3. 账户余额自动更新：创建分录后自动更新对应账户的balance字段
 * 4. 事务保证：整个记账过程在事务中执行，确保数据一致性
 * 
 * 交易类型（txnType）：
 * - BUY：买入（CASH CREDIT + POSITION DEBIT）
 * - SELL：卖出（POSITION CREDIT + CASH DEBIT）
 * - EXPENSE：支出（CASH CREDIT + EXPENSE DEBIT）
 * - INCOME：收入（CASH DEBIT + INCOME CREDIT）
 * - TRANSFER_OUT：转出（源账户 CREDIT + 目标账户 DEBIT）
 * - TRANSFER_IN：转入（源账户 CREDIT + 目标账户 DEBIT）
 * - ADJUST：调整（用于手工调整余额，CASH DEBIT/CREDIT + ADJUST CREDIT/DEBIT）
 * 
 * 日志记录：
 * - 记录交易ID、用户ID、交易类型、借贷总额等关键信息
 * - 记录账户余额变化（变化前余额、变化后余额、变化金额）
 * - 日志级别：INFO（正常记账），WARN（借贷不平衡等异常），ERROR（系统错误）
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Service
public class LedgerService {

    private final LedgerTxnMapper ledgerTxnMapper;
    private final LedgerPostingMapper ledgerPostingMapper;
    private final AccountMapper accountMapper;
    private final AccountService accountService;
    private final ProductMasterMapper productMasterMapper;
    private final UserService userService;

    public LedgerService(LedgerTxnMapper ledgerTxnMapper, LedgerPostingMapper ledgerPostingMapper,
                        AccountMapper accountMapper, AccountService accountService, ProductMasterMapper productMasterMapper,
                        UserService userService) {
        this.ledgerTxnMapper = ledgerTxnMapper;
        this.ledgerPostingMapper = ledgerPostingMapper;
        this.accountMapper = accountMapper;
        this.accountService = accountService;
        this.productMasterMapper = productMasterMapper;
        this.userService = userService;
    }

    /**
     * 创建交易记录（复式记账核心方法）
     * 
     * 流程说明：
     * 1. 生成唯一交易ID（格式：TXN-16位大写字母数字）
     * 2. 验证借贷平衡：Σ(DEBIT金额) = Σ(CREDIT金额)
     * 3. 验证所有账户都是叶子账户（禁止对父账户记账）
     * 4. 创建交易记录（ledger_txn表）
     * 5. 创建所有分录（ledger_posting表）
     * 6. 自动更新账户余额（accounts.balance字段）
     * 
     * 公式验证：
     * - 借贷平衡公式：totalDebit = totalCredit
     * - 如果不平衡，抛出异常，事务回滚
     * 
     * @param userId 用户ID，交易发起人
     * @param familyId 家庭ID，可为空（个人交易）
     * @param txnType 交易类型：BUY/SELL/EXPENSE/INCOME/TRANSFER_OUT/TRANSFER_IN/ADJUST
     * @param bizGroupKey 业务分组键，用于关联同一笔业务的多笔交易，可为空（默认使用txnId）
     * @param postings 分录列表，至少包含2个分录（1个DEBIT + 1个CREDIT）
     * @param note 备注，用户自定义说明
     * @return 创建的交易记录
     * @throws RuntimeException 如果借贷不平衡或账户不是叶子账户
     */
    @Transactional
    public LedgerTxn createTransaction(Long userId, Long familyId, String txnType, String bizGroupKey,
                                      List<LedgerPosting> postings, String note) {
        return createTransaction(userId, familyId, txnType, bizGroupKey, postings, note, null, null, false);
    }

    @Transactional
    public LedgerTxn createTransaction(Long userId, Long familyId, String txnType, String bizGroupKey,
                                      List<LedgerPosting> postings, String note, String requestedAtStr) {
        return createTransaction(userId, familyId, txnType, bizGroupKey, postings, note, requestedAtStr, null, false);
    }

    @Transactional
    public LedgerTxn createTransaction(Long userId, Long familyId, String txnType, String bizGroupKey,
                                      List<LedgerPosting> postings, String note, String requestedAtStr, Long categoryId, Boolean isReimbursable) {
        return createTransaction(userId, familyId, txnType, bizGroupKey, postings, note, requestedAtStr, categoryId, isReimbursable, null);
    }

    @Transactional
    public LedgerTxn createTransaction(Long userId, Long familyId, String txnType, String bizGroupKey,
                                      List<LedgerPosting> postings, String note, String requestedAtStr, Long categoryId, Boolean isReimbursable, Long productId) {
        // 生成唯一交易ID：格式为TXN-16位大写字母数字
        // 示例：TXN-A1B2C3D4E5F6G7H8
        String txnId = "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        // 验证借贷平衡：计算所有DEBIT和CREDIT的总额
        // 公式：totalDebit = Σ(所有DEBIT分录的amount)
        BigDecimal totalDebit = postings.stream()
                .filter(p -> "DEBIT".equals(p.getPostingType()))
                .map(LedgerPosting::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 公式：totalCredit = Σ(所有CREDIT分录的amount)
        BigDecimal totalCredit = postings.stream()
                .filter(p -> "CREDIT".equals(p.getPostingType()))
                .map(LedgerPosting::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 借贷平衡验证：totalDebit必须等于totalCredit
        // 允许极小的精度误差（1E-10），因为浮点数计算可能产生微小误差
        // 如果不平衡，抛出异常，事务回滚，不创建任何记录
        BigDecimal difference = totalDebit.subtract(totalCredit).abs();
        BigDecimal tolerance = new BigDecimal("0.0000000001"); // 1E-10 精度容差
        if (difference.compareTo(tolerance) > 0) {
            throw new RuntimeException(
                String.format("借贷不平衡: DEBIT总额=%s, CREDIT总额=%s, 差额=%s", 
                    totalDebit, totalCredit, totalDebit.subtract(totalCredit))
            );
        }

        // 验证并修正虚拟账户的 accountId
        // 如果 posting 的 accountType 是虚拟账户类型（INCOME/EXPENSE/FEE/POSITION），但 accountId 指向的是 REAL 账户，
        // 需要自动获取或创建对应的虚拟账户
        for (LedgerPosting posting : postings) {
            String accountType = posting.getAccountType();
            // 如果是虚拟账户类型，需要确保 accountId 指向的是虚拟账户
            if ("INCOME".equals(accountType) || "EXPENSE".equals(accountType) || "FEE".equals(accountType) || "POSITION".equals(accountType)) {
                Account account = accountMapper.selectById(posting.getAccountId());
                // 调试日志：记录虚拟账户修正过程
                System.out.println(String.format("检查虚拟账户: posting.accountType=%s, posting.accountId=%d, 账户存在=%s, 账户性质=%s, 虚拟子类型=%s", 
                    accountType, posting.getAccountId(), account != null, 
                    account != null ? account.getAccountKind() : "null", 
                    account != null ? account.getVirtualSubtype() : "null"));
                // 如果账户不存在，或者不是虚拟账户，或者虚拟账户的 virtual_subtype 不匹配，需要获取或创建虚拟账户
                if (account == null || !"VIRTUAL".equals(account.getAccountKind()) || 
                    !accountType.equals(account.getVirtualSubtype())) {
                    // 需要从其他 REAL 账户获取 owner 信息
                    Account realAccount = null;
                    for (LedgerPosting p : postings) {
                        if (!"INCOME".equals(p.getAccountType()) && !"EXPENSE".equals(p.getAccountType()) && 
                            !"FEE".equals(p.getAccountType()) && !"POSITION".equals(p.getAccountType())) {
                            Account acc = accountMapper.selectById(p.getAccountId());
                            if (acc != null && "REAL".equals(acc.getAccountKind())) {
                                realAccount = acc;
                                break;
                            }
                        }
                    }
                    if (realAccount == null) {
                        throw new RuntimeException("无法确定虚拟账户的归属信息，请确保至少有一个 REAL 账户的分录");
                    }
                    // 获取或创建虚拟账户
                    String ownerType = realAccount.getOwnerType() != null ? realAccount.getOwnerType() : "PERSONAL";
                    
                    // 业务规则：个人账户记账时，同时更新个人和家庭虚拟账户
                    // 1. 如果 realAccount 是 PERSONAL 类型，使用个人虚拟账户
                    // 2. 如果用户属于家庭，同时创建额外的分录来更新家庭虚拟账户
                    Account virtualAccount;
                    if ("POSITION".equals(accountType)) {
                        // POSITION 账户需要产品信息，如果提供了 productId，自动创建或获取 POSITION 账户
                        if (productId != null) {
                            ProductMaster product = productMasterMapper.selectById(productId);
                            if (product == null) {
                                throw new RuntimeException("产品不存在: productId=" + productId);
                            }
                            virtualAccount = accountService.getOrCreatePositionAccount(
                                productId, product.getProductName(), ownerType, 
                                realAccount.getOwnerUserId(), realAccount.getOwnerFamilyId());
                        } else {
                            // 如果没有提供 productId，抛出异常提示
                            throw new RuntimeException("POSITION 账户必须通过 getOrCreatePositionAccount 方法创建，需要提供 productId 和 productName");
                        }
                    } else {
                        if ("PERSONAL".equals(ownerType)) {
                            // 个人账户，使用个人虚拟账户
                            virtualAccount = accountService.getOrCreateVirtualAccount(
                                accountType, accountType, "PERSONAL", realAccount.getOwnerUserId(), null, null, null);
                            
                            // 如果用户属于家庭，标记需要同步更新家庭虚拟账户
                            // 注意：不创建额外的分录（会导致借贷不平衡），而是在更新余额时直接同步更新家庭虚拟账户
                            if (realAccount.getOwnerFamilyId() != null) {
                                // 在 posting 的 note 中标记家庭ID，用于后续同步更新
                                String originalNote = posting.getNote();
                                posting.setNote((originalNote != null ? originalNote + " | " : "") + 
                                    "FAMILY_SYNC:" + realAccount.getOwnerFamilyId());
                            }
                        } else {
                            // FAMILY 类型，只使用家庭虚拟账户
                            virtualAccount = accountService.getOrCreateVirtualAccount(
                                accountType, accountType, "FAMILY", null, realAccount.getOwnerFamilyId(), null, null);
                        }
                    }
                    // 修正 posting 的 accountId
                    System.out.println(String.format("修正虚拟账户: 原accountId=%d, 新accountId=%d, 虚拟账户类型=%s, ownerType=%s", 
                        posting.getAccountId(), virtualAccount.getId(), accountType, ownerType));
                    posting.setAccountId(virtualAccount.getId());
                }
            }
        }

        // 验证所有账户都是叶子账户（业务规则：禁止对父账户记账）
        // 父账户只用于组织管理，不参与实际记账
        for (LedgerPosting posting : postings) {
            if (!accountService.isLeafAccount(posting.getAccountId())) {
                throw new RuntimeException(
                    String.format("账户ID %d 不是叶子账户，禁止对父账户记账", posting.getAccountId())
                );
            }
        }

        // 创建交易记录
        LedgerTxn txn = new LedgerTxn();
        txn.setTxnId(txnId);
        txn.setUserId(userId);
        txn.setFamilyId(familyId);
        txn.setTxnType(txnType);
        txn.setBizGroupKey(bizGroupKey != null ? bizGroupKey : txnId);
        txn.setRelationType("NONE"); // 默认无关联
        // 处理requestedAt
        if (requestedAtStr != null && !requestedAtStr.isEmpty()) {
            try {
                // 解析格式：YYYY-MM-DD HH:mm:ss
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                txn.setRequestedAt(LocalDateTime.parse(requestedAtStr, formatter));
            } catch (Exception e) {
                // 解析失败，使用当前时间
                txn.setRequestedAt(LocalDateTime.now());
            }
        } else {
            txn.setRequestedAt(LocalDateTime.now());
        }
        txn.setTradeDate(txn.getRequestedAt().toLocalDate());
        txn.setStatus("CONFIRMED");
        txn.setNote(note);
        txn.setCategoryId(categoryId);
        txn.setProductId(productId);
        txn.setIsReimbursable(isReimbursable != null ? isReimbursable : false);
        txn.setIsReimbursed(false);
        txn.setIsReversed(false);

        ledgerTxnMapper.insert(txn);

        // 创建分录并更新账户余额，同时记录历史余额
        insertPostingsWithBalance(txnId, postings);

        // 重算涉及账户的历史余额（确保按交易时间顺序正确）
        Set<Long> accountIds = postings.stream()
            .map(LedgerPosting::getAccountId)
            .collect(Collectors.toSet());
        for (Long accountId : accountIds) {
            recalculateAccountBalanceHistory(accountId);
        }

        return txn;
    }

    /**
     * 更新账户余额（根据账户类型和借贷方向自动计算）
     * 
     * 余额计算公式：
     * 1. 资产类账户（CASH/POSITION/RECEIVABLE）：
     *    - DEBIT：newBalance = oldBalance + amount（增加）
     *    - CREDIT：newBalance = oldBalance - amount（减少）
     * 
     * 2. 负债类账户（LIABILITY/CREDIT_CARD/HUABEI/BAITIAO/LOAN）：
     *    - DEBIT：newBalance = oldBalance - amount（减少）
     *    - CREDIT：newBalance = oldBalance + amount（增加）
     * 
     * 3. 收入类账户（INCOME）：
     *    - DEBIT：newBalance = oldBalance - amount（减少）
     *    - CREDIT：newBalance = oldBalance + amount（增加）
     * 
     * 4. 支出类账户（EXPENSE）：
     *    - DEBIT：newBalance = oldBalance + amount（增加）
     *    - CREDIT：newBalance = oldBalance - amount（减少）
     * 
     * 5. 手续费类账户（FEE）：
     *    - DEBIT：newBalance = oldBalance + amount（增加）
     *    - CREDIT：newBalance = oldBalance - amount（减少）
     * 
     * 日志记录：
     * - 记录账户ID、账户类型、借贷方向、变化前余额、变化后余额、变化金额
     * - 日志格式：账户[%d]余额变化: %s -> %s (变化: %s, 方向: %s)
     * 
     * @param accountId 账户ID
     * @param postingType 借贷方向：DEBIT或CREDIT
     * @param amount 金额，必须大于0
     */
    private void updateAccountBalance(Long accountId, String postingType, BigDecimal amount) {
        Account account = accountMapper.selectById(accountId);
        if (account == null) {
            // 账户不存在，记录警告日志但不抛出异常（可能已被删除）
            System.err.println(String.format("警告：账户不存在 (账户ID: %d)，跳过余额更新", accountId));
            return;
        }

        BigDecimal oldBalance = account.getBalance();
        BigDecimal newBalance = oldBalance;
        // 对于虚拟账户，使用 virtual_subtype 作为账户类型
        // 对于 REAL 账户，优先使用 account_type，如果为空则使用 fund_usage
        String accountType;
        if ("VIRTUAL".equals(account.getAccountKind())) {
            accountType = account.getVirtualSubtype();
        } else {
            accountType = account.getAccountType();
            // 如果 account_type 为空或不是预期的类型，使用 fund_usage 作为备选
            if (accountType == null || accountType.isEmpty()) {
                accountType = account.getFundUsage();
            }
        }
        
        // 调试日志：记录账户信息（安全地处理可能为 null 的值）
        System.out.println(String.format("更新账户余额: 账户ID=%d, 账户性质=%s, 账户类型=%s, 虚拟子类型=%s, 借贷方向=%s, 金额=%s, 当前余额=%s", 
            accountId, 
            account.getAccountKind() != null ? account.getAccountKind() : "null",
            account.getAccountType() != null ? account.getAccountType() : "null",
            account.getVirtualSubtype() != null ? account.getVirtualSubtype() : "null",
            postingType, amount, oldBalance));
        
        // 如果 accountType 是 null，记录警告并返回
        if (accountType == null || accountType.isEmpty()) {
            System.err.println(String.format("错误：账户类型为空 (账户ID: %d, 账户性质: %s, 账户类型: %s, 虚拟子类型: %s)，跳过余额更新", 
                accountId, account.getAccountKind(), account.getAccountType(), account.getVirtualSubtype()));
            return;
        }

        // 资产类账户：DEBIT增加余额，CREDIT减少余额
        // 公式：newBalance = oldBalance + (postingType == DEBIT ? amount : -amount)
        // 包括：CASH（现金）、POSITION（持仓）、RECEIVABLE（应收）、MMF（货币基金）、
        //       BANK_WM_NAV（银行理财净值型）、BANK_WM_BOX（银行理财封闭型）、ETF、LOF、FUND 等
        //       BROKER（券商账户）、INVESTABLE（可投资）、SPENDABLE（可支出）- 这些是券商子账户的资金用途类型
        //       PAYMENT（支付账户，如支付宝余额宝）、BANK（银行账户）、OTHER（其他现金类账户）
        if ("CASH".equals(accountType) || "POSITION".equals(accountType) || "RECEIVABLE".equals(accountType) ||
            "MMF".equals(accountType) || "BANK_WM_NAV".equals(accountType) || "BANK_WM_BOX".equals(accountType) ||
            "ETF".equals(accountType) || "LOF".equals(accountType) || "FUND".equals(accountType) ||
            "STOCK".equals(accountType) || "BOND".equals(accountType) || "OPTION".equals(accountType) ||
            "BROKER".equals(accountType) || "INVESTABLE".equals(accountType) || "SPENDABLE".equals(accountType) || "RESERVED".equals(accountType) ||
            "PAYMENT".equals(accountType) || "BANK".equals(accountType) || "OTHER".equals(accountType)) {
            if ("DEBIT".equals(postingType)) {
                newBalance = oldBalance.add(amount);
            } else {
                newBalance = oldBalance.subtract(amount);
            }
        }
        // 负债类账户：DEBIT减少余额，CREDIT增加余额
        // 公式：newBalance = oldBalance - (postingType == DEBIT ? amount : -amount)
        else if ("LIABILITY".equals(accountType) || accountType.contains("CREDIT") || 
                 accountType.contains("HUABEI") || accountType.contains("BAITIAO") ||
                 accountType.contains("LOAN")) {
            if ("DEBIT".equals(postingType)) {
                newBalance = oldBalance.subtract(amount);
            } else {
                newBalance = oldBalance.add(amount);
            }
        }
        // 收入类账户：DEBIT减少余额，CREDIT增加余额
        else if ("INCOME".equals(accountType)) {
            if ("DEBIT".equals(postingType)) {
                newBalance = oldBalance.subtract(amount);
            } else {
                newBalance = oldBalance.add(amount);
            }
        }
        // 支出类账户：DEBIT增加余额，CREDIT减少余额
        else if ("EXPENSE".equals(accountType)) {
            if ("DEBIT".equals(postingType)) {
                newBalance = oldBalance.add(amount);
            } else {
                newBalance = oldBalance.subtract(amount);
            }
        }
        // 手续费类账户：DEBIT增加余额，CREDIT减少余额（与EXPENSE相同）
        else if ("FEE".equals(accountType)) {
            if ("DEBIT".equals(postingType)) {
                newBalance = oldBalance.add(amount);
            } else {
                newBalance = oldBalance.subtract(amount);
            }
        }
        // 如果账户类型不匹配任何已知类型，记录警告但不更新余额
        else {
            // 对于未知的账户类型，不更新余额，但记录警告
            System.err.println(String.format("警告：未知的账户类型 %s (账户ID: %d, 账户性质: %s, 虚拟子类型: %s)，跳过余额更新", 
                accountType, accountId, account.getAccountKind(), account.getVirtualSubtype()));
            return; // 不更新余额，直接返回
        }

        // 更新账户余额（数据库操作）
        accountMapper.updateBalance(accountId, newBalance);
        
        // 记录余额变化日志（简化版，实际应该使用日志框架）
        // 格式：账户[%d]余额变化: %s -> %s (变化: %s, 方向: %s, 账户类型: %s)
        // 注意：日志大小控制，只记录关键信息，避免日志过大
    }

    /**
     * 插入分录并记录历史余额（辅助方法）
     * 
     * 这个方法会：
     * 1. 更新账户余额
     * 2. 记录该分录发生后的账户余额
     * 3. 如果账户有父账户，计算并记录父账户余额
     * 4. 插入分录到数据库
     * 
     * @param txnId 交易ID
     * @param postings 分录列表
     */
    private void insertPostingsWithBalance(String txnId, List<LedgerPosting> postings) {
        for (LedgerPosting posting : postings) {
            posting.setTxnId(txnId);
            
            // 检查是否需要同步更新家庭虚拟账户
            String note = posting.getNote();
            Long familyIdToSync = null;
            if (note != null && note.contains("FAMILY_SYNC:")) {
                // 提取家庭ID
                String[] parts = note.split("FAMILY_SYNC:");
                if (parts.length > 1) {
                    try {
                        familyIdToSync = Long.parseLong(parts[1].split("\\|")[0].trim());
                        // 从 note 中移除同步标记（保留原始备注）
                        note = parts[0].replaceAll("\\s*\\|\\s*$", "").trim();
                        if (note.isEmpty()) {
                            note = null;
                        }
                        posting.setNote(note);
                    } catch (NumberFormatException e) {
                        // 解析失败，忽略
                    }
                }
            }
            
            // 更新账户余额
            updateAccountBalance(posting.getAccountId(), posting.getPostingType(), posting.getAmount());
            
            // 如果个人虚拟账户记账且用户属于家庭，同时更新家庭虚拟账户余额
            if (familyIdToSync != null && posting.getAccountType() != null) {
                Account personalAccount = accountMapper.selectById(posting.getAccountId());
                if (personalAccount != null && "VIRTUAL".equals(personalAccount.getAccountKind()) && 
                    "PERSONAL".equals(personalAccount.getOwnerType())) {
                    // 获取或创建家庭虚拟账户
                    Account familyVirtualAccount = accountService.getOrCreateVirtualAccount(
                        posting.getAccountType(), posting.getAccountType(), "FAMILY", null, familyIdToSync, null, null);
                    
                    // 同步更新家庭虚拟账户余额（相同的借贷方向和金额）
                    // 注意：这里不创建分录，只更新余额，因为家庭虚拟账户余额 = 所有家庭成员的个人虚拟账户余额之和
                    updateAccountBalance(familyVirtualAccount.getId(), posting.getPostingType(), posting.getAmount());
                    
                    System.out.println(String.format("同步更新家庭虚拟账户: 家庭ID=%d, 账户类型=%s, 借贷方向=%s, 金额=%s", 
                        familyIdToSync, posting.getAccountType(), posting.getPostingType(), posting.getAmount()));
                }
            }
            
            // 获取更新后的余额（作为"变化后余额"）
            Account accountAfter = accountMapper.selectById(posting.getAccountId());
            BigDecimal balanceAfter = accountAfter != null && accountAfter.getBalance() != null ? accountAfter.getBalance() : BigDecimal.ZERO;
            posting.setAccountBalanceAfter(balanceAfter);
            
            // 如果账户有父账户，计算父账户余额
            if (accountAfter != null && accountAfter.getParentAccountId() != null) {
                List<Account> children = accountService.getAccountChildren(accountAfter.getParentAccountId());
                BigDecimal parentBalance = children.stream()
                    .map(Account::getBalance)
                    .filter(b -> b != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                posting.setParentAccountBalanceAfter(parentBalance);
                
                // 如果父账户关联了产品，需要同步更新父账户的余额和份额
                Account parentAccount = accountMapper.selectById(accountAfter.getParentAccountId());
                if (parentAccount != null && parentAccount.getLinkedProductId() != null) {
                    // 父账户是关联产品的账户（如货币基金账户）
                    // 当子账户有现金流出（CREDIT）时，需要减少父账户的余额和份额
                    if ("CASH".equals(posting.getAccountType()) && "CREDIT".equals(posting.getPostingType())) {
                        // 减少父账户余额
                        BigDecimal parentOldBalance = parentAccount.getBalance() != null ? parentAccount.getBalance() : BigDecimal.ZERO;
                        BigDecimal parentNewBalance = parentOldBalance.subtract(posting.getAmount());
                        if (parentNewBalance.compareTo(BigDecimal.ZERO) < 0) {
                            parentNewBalance = BigDecimal.ZERO;
                        }
                        accountMapper.updateBalance(parentAccount.getId(), parentNewBalance);
                        
                        // 减少父账户份额
                        // 对于关联产品的账户，份额应该等于余额（货币基金净值通常是1.0）
                        // 如果账户类型是MMF（货币基金），直接使用余额作为份额
                        BigDecimal parentNewShares = parentNewBalance;
                        
                        // 如果不是MMF类型，尝试获取产品净值来计算份额
                        if (!"MMF".equals(parentAccount.getAccountType())) {
                            try {
                                com.timelordtty.dca.model.ProductMaster product = productMasterMapper.selectById(parentAccount.getLinkedProductId());
                                if (product != null) {
                                    // 通过AccountService获取最新净值（如果有的话）
                                    // 这里简化处理：对于非MMF类型，也使用余额作为份额（净值通常接近1.0）
                                    // 如果需要精确计算，可以通过AccountService或NavService获取净值
                                    parentNewShares = parentNewBalance;
                                }
                            } catch (Exception e) {
                                // 如果获取产品信息失败，使用余额作为份额
                                parentNewShares = parentNewBalance;
                            }
                        }
                        accountMapper.updateInitialShares(parentAccount.getId(), parentNewShares);
                        
                        System.out.println(String.format("同步更新关联产品账户: 账户ID=%d, 余额 %s -> %s, 份额 -> %s", 
                            parentAccount.getId(), parentOldBalance, parentNewBalance, parentNewShares));
                    }
                }
            } else {
                posting.setParentAccountBalanceAfter(null);
            }
            
            // 插入分录（包含历史余额）
            ledgerPostingMapper.insert(posting);
        }
    }

    public List<LedgerTxn> getTransactions(Long userId, String txnType, LocalDate startDate, 
                                          LocalDate endDate, Long productId, Long accountId, Integer page, Integer pageSize) {
        Integer offset = null;
        Integer limit = null;
        if (page != null && pageSize != null && page > 0 && pageSize > 0) {
            offset = (page - 1) * pageSize;
            limit = pageSize;
        }
        return ledgerTxnMapper.selectByCondition(userId, txnType, startDate, endDate, productId, accountId, offset, limit);
    }
    
    public int countTransactions(Long userId, String txnType, LocalDate startDate, 
                                LocalDate endDate, Long productId, Long accountId) {
        return ledgerTxnMapper.countByCondition(userId, txnType, startDate, endDate, productId, accountId);
    }

    public LedgerTxn getTransactionDetail(String txnId) {
        return ledgerTxnMapper.selectByTxnId(txnId);
    }

    public List<LedgerPosting> getPostingsByTxnId(String txnId) {
        return ledgerPostingMapper.selectByTxnId(txnId);
    }

    /**
     * 创建退款交易
     * 
     * 业务场景：用户消费后收到退款，需要冲减原消费交易
     * 
     * 流程说明：
     * 1. 查询原交易（relatedTxnId对应的交易）
     * 2. 验证原交易类型为EXPENSE（只有消费交易可以退款）
     * 3. 生成退款分录：
     *    - CASH DEBIT（现金增加，退款到账）
     *    - EXPENSE CREDIT（费用减少，冲减原消费）
     * 4. 设置关联关系：relatedTxnId指向原交易，relationType='REFUND'
     * 
     * 分录模板：
     * - DEBIT CASH accountId refundAmount（现金增加）
     * - CREDIT EXPENSE [原交易的EXPENSE账户] refundAmount（费用冲减）
     * 
     * 应用层约束：
     * - relatedTxnId必须非空
     * - relationType='REFUND'时，relatedTxnId必须非空
     * - refundAmount不能超过原交易金额
     * 
     * @param userId 用户ID
     * @param familyId 家庭ID，可为空
     * @param relatedTxnId 原交易ID（被退款的消费交易）
     * @param refundAmount 退款金额
     * @param accountId 退款到账账户ID（必须是叶子账户）
     * @param note 备注
     * @return 创建的退款交易记录
     */
    @Transactional
    public LedgerTxn createRefund(Long userId, Long familyId, String relatedTxnId, 
                                  BigDecimal refundAmount, Long accountId, String note, LocalDateTime requestedAt) {
        // 验证原交易存在且为EXPENSE类型
        LedgerTxn originalTxn = ledgerTxnMapper.selectByTxnId(relatedTxnId);
        if (originalTxn == null) {
            throw new RuntimeException("原交易不存在: " + relatedTxnId);
        }
        if (!"EXPENSE".equals(originalTxn.getTxnType())) {
            throw new RuntimeException("只能对消费交易进行退款");
        }

        // 查询原交易的分录，找到EXPENSE账户
        List<LedgerPosting> originalPostings = ledgerPostingMapper.selectByTxnId(relatedTxnId);
        LedgerPosting expensePosting = originalPostings.stream()
                .filter(p -> "DEBIT".equals(p.getPostingType()) && "EXPENSE".equals(p.getAccountType()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("原交易没有EXPENSE分录"));

        // 验证退款金额不超过原交易金额
        if (refundAmount.compareTo(expensePosting.getAmount()) > 0) {
            throw new RuntimeException("退款金额不能超过原交易金额");
        }

        // 验证账户是叶子账户
        if (!accountService.isLeafAccount(accountId)) {
            throw new RuntimeException("账户必须是叶子账户");
        }

        // 生成退款分录
        List<LedgerPosting> postings = new java.util.ArrayList<>();
        
        // CASH DEBIT（现金增加，退款到账）
        LedgerPosting cashPosting = new LedgerPosting();
        cashPosting.setPostingType("DEBIT");
        cashPosting.setAccountId(accountId);
        cashPosting.setAccountType("CASH");
        cashPosting.setAmount(refundAmount);
        cashPosting.setCurrency("CNY");
        postings.add(cashPosting);

        // EXPENSE CREDIT（费用减少，冲减原消费）
        LedgerPosting expenseCreditPosting = new LedgerPosting();
        expenseCreditPosting.setPostingType("CREDIT");
        expenseCreditPosting.setAccountId(expensePosting.getAccountId());
        expenseCreditPosting.setAccountType("EXPENSE");
        expenseCreditPosting.setAmount(refundAmount);
        expenseCreditPosting.setCurrency("CNY");
        postings.add(expenseCreditPosting);

        // 创建退款交易
        String txnId = "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        LedgerTxn refundTxn = new LedgerTxn();
        refundTxn.setTxnId(txnId);
        refundTxn.setUserId(userId);
        refundTxn.setFamilyId(familyId);
        refundTxn.setTxnType("REIMBURSE_IN"); // 退款收入
        refundTxn.setBizGroupKey(txnId);
        refundTxn.setRelatedTxnId(relatedTxnId);
        refundTxn.setRelationType("REFUND");
        refundTxn.setRequestedAt(requestedAt != null ? requestedAt : LocalDateTime.now());
        refundTxn.setTradeDate(requestedAt != null ? requestedAt.toLocalDate() : LocalDate.now());
        refundTxn.setStatus("CONFIRMED");
        refundTxn.setNote(note != null ? note : "退款：" + refundAmount);
        refundTxn.setCategoryId(57L); // 退款分类（收入分类中的"退款"，ID=57）
        refundTxn.setIsReimbursable(false); // 退款交易本身不可报销
        refundTxn.setIsReimbursed(false); // 退款交易本身不可报销
        refundTxn.setIsReversed(false);

        ledgerTxnMapper.insert(refundTxn);

        // 创建分录并更新账户余额，同时记录历史余额
        insertPostingsWithBalance(txnId, postings);

        return refundTxn;
    }
    
    /**
     * 更新原交易的报销状态（在note中添加isReimbursed标记）
     */
    private void markTransactionAsReimbursed(String txnId) {
        LedgerTxn txn = ledgerTxnMapper.selectByTxnId(txnId);
        if (txn != null) {
            String note = txn.getNote() != null ? txn.getNote() : "";
            if (!note.contains("isReimbursed:true")) {
                note += " [isReimbursed:true]";
                txn.setNote(note);
                ledgerTxnMapper.update(txn);
            }
        }
    }

    /**
     * 创建报销交易
     * 
     * 业务场景：用户消费后可报销，收到报销款
     * 
     * 流程说明：
     * 1. 查询原交易（relatedTxnId对应的交易）
     * 2. 验证原交易类型为EXPENSE（只有消费交易可以报销）
     * 3. 生成报销分录：
     *    - CASH DEBIT（现金增加，报销到账）
     *    - EXPENSE CREDIT（费用减少，冲减原消费）
     * 4. 设置关联关系：relatedTxnId指向原交易，relationType='REIMBURSE'
     * 
     * 分录模板：
     * - DEBIT CASH accountId reimburseAmount（现金增加）
     * - CREDIT EXPENSE [原交易的EXPENSE账户] reimburseAmount（费用冲减）
     * 
     * 应用层约束：
     * - relatedTxnId必须非空
     * - relationType='REIMBURSE'时，relatedTxnId必须非空
     * - reimburseAmount不能超过原交易金额
     * 
     * @param userId 用户ID
     * @param familyId 家庭ID，可为空
     * @param relatedTxnId 原交易ID（被报销的消费交易）
     * @param reimburseAmount 报销金额
     * @param accountId 报销到账账户ID（必须是叶子账户）
     * @param note 备注
     * @return 创建的报销交易记录
     */
    @Transactional
    public LedgerTxn createReimburse(Long userId, Long familyId, String relatedTxnId, 
                                     BigDecimal reimburseAmount, Long accountId, String note, LocalDateTime requestedAt) {
        // 验证原交易存在且为EXPENSE类型
        LedgerTxn originalTxn = ledgerTxnMapper.selectByTxnId(relatedTxnId);
        if (originalTxn == null) {
            throw new RuntimeException("原交易不存在: " + relatedTxnId);
        }
        if (!"EXPENSE".equals(originalTxn.getTxnType())) {
            throw new RuntimeException("只能对消费交易进行报销");
        }

        // 查询原交易的分录，找到EXPENSE账户
        List<LedgerPosting> originalPostings = ledgerPostingMapper.selectByTxnId(relatedTxnId);
        LedgerPosting expensePosting = originalPostings.stream()
                .filter(p -> "DEBIT".equals(p.getPostingType()) && "EXPENSE".equals(p.getAccountType()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("原交易没有EXPENSE分录"));

        // 验证报销金额不超过原交易金额
        if (reimburseAmount.compareTo(expensePosting.getAmount()) > 0) {
            throw new RuntimeException("报销金额不能超过原交易金额");
        }

        // 验证账户是叶子账户
        if (!accountService.isLeafAccount(accountId)) {
            throw new RuntimeException("账户必须是叶子账户");
        }

        // 生成报销分录
        List<LedgerPosting> postings = new java.util.ArrayList<>();
        
        // CASH DEBIT（现金增加，报销到账）
        LedgerPosting cashPosting = new LedgerPosting();
        cashPosting.setPostingType("DEBIT");
        cashPosting.setAccountId(accountId);
        cashPosting.setAccountType("CASH");
        cashPosting.setAmount(reimburseAmount);
        cashPosting.setCurrency("CNY");
        postings.add(cashPosting);

        // EXPENSE CREDIT（费用减少，冲减原消费）
        LedgerPosting expenseCreditPosting = new LedgerPosting();
        expenseCreditPosting.setPostingType("CREDIT");
        expenseCreditPosting.setAccountId(expensePosting.getAccountId());
        expenseCreditPosting.setAccountType("EXPENSE");
        expenseCreditPosting.setAmount(reimburseAmount);
        expenseCreditPosting.setCurrency("CNY");
        postings.add(expenseCreditPosting);

        // 创建报销交易
        String txnId = "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        LedgerTxn reimburseTxn = new LedgerTxn();
        reimburseTxn.setTxnId(txnId);
        reimburseTxn.setUserId(userId);
        reimburseTxn.setFamilyId(familyId);
        reimburseTxn.setTxnType("REIMBURSE_IN"); // 报销收入
        reimburseTxn.setBizGroupKey(txnId);
        reimburseTxn.setRelatedTxnId(relatedTxnId);
        reimburseTxn.setRelationType("REIMBURSE");
        reimburseTxn.setRequestedAt(requestedAt != null ? requestedAt : LocalDateTime.now());
        reimburseTxn.setTradeDate(requestedAt != null ? requestedAt.toLocalDate() : LocalDate.now());
        reimburseTxn.setStatus("CONFIRMED");
        reimburseTxn.setNote(note != null ? note : "报销：" + reimburseAmount);
        reimburseTxn.setCategoryId(null); // 报销交易不需要分类
        reimburseTxn.setIsReimbursable(false); // 报销交易本身不可报销
        reimburseTxn.setIsReimbursed(false); // 报销交易本身不可报销
        reimburseTxn.setIsReversed(false);

        ledgerTxnMapper.insert(reimburseTxn);

        // 创建分录并更新账户余额，同时记录历史余额
        insertPostingsWithBalance(txnId, postings);

        // 更新原支出的报销状态
        markTransactionAsReimbursed(relatedTxnId);

        return reimburseTxn;
    }

    /**
     * 创建转托管交易
     * 
     * 业务场景：将场外产品持仓份额在某一天以某个价格（通常0费用）的方式直接转入指定份额到同一个产品id的场内产品持仓中
     * 
     * 流程说明：
     * 1. 验证产品存在，且同时有场外和场内版本
     * 2. 查询场外持仓账户（OTC渠道）
     * 3. 查询场内持仓账户（EXCHANGE渠道）
     * 4. 验证场外持仓有足够份额
     * 5. 计算转出成本（按平均成本法）
     * 6. 生成转托管分录：
     *    - 场外POSITION CREDIT（减少份额和成本）
     *    - 场内POSITION DEBIT（增加份额，成本按转出价格计算）
     * 7. 设置关联关系：relationType='CUSTODY_TRANSFER_OF'
     * 
     * 分录模板：
     * - CREDIT POSITION [场外持仓账户] shares=transferShares, amount=outCost（按平均成本计算）
     * - DEBIT POSITION [场内持仓账户] shares=transferShares, amount=inCost（按转出价格计算）
     * 
     * @param userId 用户ID
     * @param familyId 家庭ID，可为空
     * @param productId 产品ID（场外和场内必须是同一个产品）
     * @param transferShares 转出份额
     * @param transferPrice 转出价格（通常为0费用，用于计算场内成本）
     * @param transferDate 转出日期
     * @param note 备注
     * @return 创建的转托管交易记录
     */
    @Transactional
    public LedgerTxn createCustodyTransfer(Long userId, Long familyId, Long productId,
                                           BigDecimal transferShares, BigDecimal transferPrice,
                                           LocalDate transferDate, String note) {
        // 验证产品存在（通过ProductService获取）
        // 简化处理：假设传入的productId是场外产品，需要找到对应的场内产品
        // 实际应该通过产品代码查找同一产品的场外和场内版本
        ProductMaster product = productMasterMapper.selectById(productId);
        if (product == null) {
            throw new RuntimeException("产品不存在: " + productId);
        }
        
        // 查找场外和场内产品（同一个产品代码，不同渠道）
        // 通过selectByCondition查找所有产品，然后过滤
        List<ProductMaster> allProducts = productMasterMapper.selectByCondition(null, null, null);
        ProductMaster otcProduct = allProducts.stream()
            .filter(p -> product.getProductCode().equals(p.getProductCode()) && "OTC".equals(p.getChannel()))
            .findFirst()
            .orElse(product); // 如果找不到，使用原产品
        ProductMaster exchangeProduct = allProducts.stream()
            .filter(p -> product.getProductCode().equals(p.getProductCode()) && "EXCHANGE".equals(p.getChannel()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("找不到场内产品: " + product.getProductCode()));
        
        // 获取场外和场内持仓账户
        // 注意：持仓账户通过 accountCode 匹配（基于 productId），而不是账户名称
        // accountCode 格式：VIRTUAL-POSITION-{ownerType}-{ownerKey}-{productId}
        // 所以场外和场内产品如果 productId 不同，会有不同的持仓账户
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        String ownerType = currentUser.getFamilyId() != null ? "FAMILY" : "PERSONAL";
        Long ownerUserId = currentUser.getId();
        Long ownerFamilyId = currentUser.getFamilyId();
        
        // 使用实际的产品名称（不带渠道后缀），因为持仓账户名称格式是"持仓账户-{产品名称}"
        Account otcPositionAccount = accountService.getOrCreatePositionAccount(
            otcProduct.getId(), otcProduct.getProductName(), ownerType, ownerUserId, ownerFamilyId);
        Account exchangePositionAccount = accountService.getOrCreatePositionAccount(
            exchangeProduct.getId(), exchangeProduct.getProductName(), ownerType, ownerUserId, ownerFamilyId);
        
        // 验证场外持仓有足够份额并计算平均成本
        // 查询场外持仓账户的所有分录，计算当前持仓
        List<LedgerPosting> otcPostings = ledgerPostingMapper.selectByAccountId(otcPositionAccount.getId());
        BigDecimal totalShares = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (LedgerPosting p : otcPostings) {
            if ("POSITION".equals(p.getAccountType())) {
                if ("DEBIT".equals(p.getPostingType())) {
                    if (p.getShares() != null) {
                        totalShares = totalShares.add(p.getShares());
                    }
                    totalCost = totalCost.add(p.getAmount());
                } else if ("CREDIT".equals(p.getPostingType())) {
                    if (p.getShares() != null) {
                        totalShares = totalShares.subtract(p.getShares());
                    }
                    totalCost = totalCost.subtract(p.getAmount());
                }
            }
        }
        
        // 验证有足够份额
        if (totalShares.compareTo(transferShares) < 0) {
            throw new RuntimeException("场外持仓份额不足，当前持仓: " + totalShares + "，转出份额: " + transferShares);
        }
        
        // 摊薄成本法（同花顺方式）：
        // - 场外转出成本 = 场外平均成本 × 转出份额（按平均成本法）
        // - 场内转入成本 = 场内价格 × 转入份额（按实际转入金额）
        // 这样场内的成本计算与直接购买一致，便于用户理解
        
        // 计算场外转出成本（按平均成本法）
        BigDecimal avgCost = totalShares.compareTo(BigDecimal.ZERO) > 0 
            ? totalCost.divide(totalShares, 6, java.math.RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        BigDecimal outCost = avgCost.multiply(transferShares);
        
        // 场内转入成本 = 场内价格 × 份额（与同花顺一致）
        BigDecimal inCost = transferPrice.multiply(transferShares);
        
        // 生成转托管分录
        // 场外POSITION CREDIT（减少份额和成本）
        LedgerPosting otcPosting = new LedgerPosting();
        otcPosting.setPostingType("CREDIT");
        otcPosting.setAccountId(otcPositionAccount.getId());
        otcPosting.setAccountType("POSITION");
        otcPosting.setAmount(outCost);
        otcPosting.setShares(transferShares);
        otcPosting.setCurrency("CNY");
        otcPosting.setNote(note != null ? note + String.format(" [转出，场内价格%s]", transferPrice) : 
            String.format("转托管转出：%s份，场内价格%s", transferShares, transferPrice));
        
        // 场内POSITION DEBIT（增加份额，成本等于场外转出成本）
        LedgerPosting exchangePosting = new LedgerPosting();
        exchangePosting.setPostingType("DEBIT");
        exchangePosting.setAccountId(exchangePositionAccount.getId());
        exchangePosting.setAccountType("POSITION");
        exchangePosting.setAmount(inCost);
        exchangePosting.setShares(transferShares);
        exchangePosting.setCurrency("CNY");
        exchangePosting.setNote(note != null ? note + String.format(" [转入，场内价格%s]", transferPrice) : 
            String.format("转托管转入：%s份，场内价格%s", transferShares, transferPrice));
        
        // 创建转托管交易记录
        // 转托管涉及两个产品（场外和场内），需要创建两条交易记录，以便在两个产品的交易记录中都能看到
        String baseTxnId = "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        String bizGroupKey = baseTxnId; // 使用同一个 bizGroupKey 关联两条交易记录
        
        // 创建场外产品的转托管交易记录
        LedgerTxn otcTransferTxn = new LedgerTxn();
        otcTransferTxn.setTxnId(baseTxnId + "-OTC");
        otcTransferTxn.setUserId(userId);
        otcTransferTxn.setFamilyId(familyId);
        otcTransferTxn.setTxnType("CUSTODY_TRANSFER");
        otcTransferTxn.setBizGroupKey(bizGroupKey);
        otcTransferTxn.setProductId(otcProduct.getId()); // 关联场外产品
        otcTransferTxn.setRelationType("CUSTODY_TRANSFER_OF");
        otcTransferTxn.setRequestedAt(transferDate.atStartOfDay());
        otcTransferTxn.setTradeDate(transferDate);
        otcTransferTxn.setStatus("CONFIRMED");
        otcTransferTxn.setNote(note != null ? note + String.format(" [转出，场内价格%s]", transferPrice) : 
            String.format("转托管转出：%s份，场内价格%s", transferShares, transferPrice));
        otcTransferTxn.setCategoryId(null);
        otcTransferTxn.setIsReimbursable(false);
        otcTransferTxn.setIsReimbursed(false);
        otcTransferTxn.setIsReversed(false);
        ledgerTxnMapper.insert(otcTransferTxn);
        
        // 创建场内产品的转托管交易记录
        LedgerTxn exchangeTransferTxn = new LedgerTxn();
        exchangeTransferTxn.setTxnId(baseTxnId + "-EXCHANGE");
        exchangeTransferTxn.setUserId(userId);
        exchangeTransferTxn.setFamilyId(familyId);
        exchangeTransferTxn.setTxnType("CUSTODY_TRANSFER");
        exchangeTransferTxn.setBizGroupKey(bizGroupKey);
        exchangeTransferTxn.setProductId(exchangeProduct.getId()); // 关联场内产品
        exchangeTransferTxn.setRelationType("CUSTODY_TRANSFER_OF");
        exchangeTransferTxn.setRequestedAt(transferDate.atStartOfDay());
        exchangeTransferTxn.setTradeDate(transferDate);
        exchangeTransferTxn.setStatus("CONFIRMED");
        exchangeTransferTxn.setNote(note != null ? note + String.format(" [转入，场内价格%s]", transferPrice) : 
            String.format("转托管转入：%s份，场内价格%s", transferShares, transferPrice));
        exchangeTransferTxn.setCategoryId(null);
        exchangeTransferTxn.setIsReimbursable(false);
        exchangeTransferTxn.setIsReimbursed(false);
        exchangeTransferTxn.setIsReversed(false);
        ledgerTxnMapper.insert(exchangeTransferTxn);
        
        // 创建分录并更新账户余额，同时记录历史余额
        // 场外分录关联场外交易记录
        insertPostingsWithBalance(otcTransferTxn.getTxnId(), List.of(otcPosting));
        // 场内分录关联场内交易记录
        insertPostingsWithBalance(exchangeTransferTxn.getTxnId(), List.of(exchangePosting));
        
        // 返回场内交易记录（作为主记录）
        return exchangeTransferTxn;
    }

    /**
     * 重算账户的历史余额
     * 当插入/修改交易时调用，确保历史余额按交易时间顺序正确
     * 
     * 算法说明：
     * 1. 获取账户当前余额
     * 2. 查询该账户所有分录（按交易的 requested_at 时间排序）
     * 3. 计算起始余额 = 当前余额 - 所有分录的净变化量
     * 4. 按时间顺序重新计算每条分录的 accountBalanceAfter
     * 5. 批量更新有变化的分录
     * 
     * @param accountId 需要重算的账户ID
     */
    public void recalculateAccountBalanceHistory(Long accountId) {
        // 1. 获取账户当前余额
        Account account = accountMapper.selectById(accountId);
        if (account == null) return;
        
        BigDecimal currentBalance = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
        
        // 2. 查询该账户所有分录（按交易时间排序）
        List<LedgerPosting> postings = ledgerPostingMapper.selectByAccountIdOrderByTxnTime(accountId);
        if (postings.isEmpty()) return;
        
        // 3. 计算起始余额 = 当前余额 - 所有分录的净变化量
        // 净变化量 = DEBIT总额 - CREDIT总额（对于资产类账户）
        BigDecimal netChange = BigDecimal.ZERO;
        for (LedgerPosting p : postings) {
            if ("DEBIT".equals(p.getPostingType())) {
                netChange = netChange.add(p.getAmount());
            } else {
                netChange = netChange.subtract(p.getAmount());
            }
        }
        BigDecimal startBalance = currentBalance.subtract(netChange);
        
        // 4. 按时间顺序计算每条分录的余额
        List<LedgerPosting> updates = new ArrayList<>();
        BigDecimal runningBalance = startBalance;
        
        // 获取父账户ID（用于计算父账户余额）
        Long parentAccountId = account.getParentAccountId();
        
        for (LedgerPosting p : postings) {
            if ("DEBIT".equals(p.getPostingType())) {
                runningBalance = runningBalance.add(p.getAmount());
            } else {
                runningBalance = runningBalance.subtract(p.getAmount());
            }
            
            // 只有余额变化时才加入更新列表
            boolean needUpdate = false;
            if (p.getAccountBalanceAfter() == null || 
                p.getAccountBalanceAfter().compareTo(runningBalance) != 0) {
                p.setAccountBalanceAfter(runningBalance);
                needUpdate = true;
            }
            
            // 计算父账户余额（如果有父账户）
            if (parentAccountId != null) {
                List<Account> siblings = accountService.getAccountChildren(parentAccountId);
                BigDecimal parentBalance = BigDecimal.ZERO;
                for (Account sibling : siblings) {
                    if (sibling.getId().equals(accountId)) {
                        // 当前账户使用计算中的余额
                        parentBalance = parentBalance.add(runningBalance);
                    } else if (sibling.getBalance() != null) {
                        parentBalance = parentBalance.add(sibling.getBalance());
                    }
                }
                if (p.getParentAccountBalanceAfter() == null ||
                    p.getParentAccountBalanceAfter().compareTo(parentBalance) != 0) {
                    p.setParentAccountBalanceAfter(parentBalance);
                    needUpdate = true;
                }
            }
            
            if (needUpdate) {
                updates.add(p);
            }
        }
        
        // 5. 批量更新（如果有变化）
        if (!updates.isEmpty()) {
            ledgerPostingMapper.batchUpdateBalanceAfter(updates);
        }
    }
}

