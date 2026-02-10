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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LedgerService.class);

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
        return createTransaction(userId, familyId, txnType, bizGroupKey, postings, note, requestedAtStr, categoryId, isReimbursable, productId, null);
    }

    /**
     * 创建交易记录（完整版，支持 orderId 关联）
     */
    @Transactional
    public LedgerTxn createTransaction(Long userId, Long familyId, String txnType, String bizGroupKey,
                                      List<LedgerPosting> postings, String note, String requestedAtStr, 
                                      Long categoryId, Boolean isReimbursable, Long productId, String orderId) {
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
        fixVirtualAccountIds(postings, productId);

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
        txn.setOrderId(orderId); // 关联订单ID（可为空）
        txn.setIsReimbursable(isReimbursable != null ? isReimbursable : false);
        txn.setIsReimbursed(false);
        txn.setIsReversed(false);

        ledgerTxnMapper.insert(txn);

        // 收集涉及的账户ID
        Set<Long> accountIds = postings.stream()
            .map(LedgerPosting::getAccountId)
            .collect(Collectors.toSet());

        // 创建分录并更新账户余额，同时记录历史余额
        insertPostingsWithBalance(txnId, postings);

        // 性能优化：只有当交易时间是历史时间（插入到中间）时才重算历史余额
        // 如果是最新时间的交易，insertPostingsWithBalance 已经正确计算了余额
        if (needsHistoryRecalculation(accountIds, txn.getRequestedAt())) {
            recalculateAccountBalanceHistoryForAccounts(accountIds);
        }

        return txn;
    }
    
    /**
     * 判断是否需要重算历史余额
     * 
     * 优化策略：只有当新交易的时间早于相关账户（含兄弟账户）的最新交易时间时，才需要重算。
     * 
     * 关键点：父账户余额 = 所有子账户余额之和，因此在计算父账户历史余额时，
     * 需要考虑兄弟账户的历史变化。如果兄弟账户在新交易时间之后有更多posting，
     * 则 insertPostingsWithBalance 用的"当前DB余额"计算的父余额是错误的，
     * 必须触发 recalculateAccountBalanceHistoryForAccounts 来用历史running balance重算。
     * 
     * @param accountIds 涉及的账户ID集合
     * @param newTxnTime 新交易的时间
     * @return true 如果需要重算历史余额
     */
    private boolean needsHistoryRecalculation(Set<Long> accountIds, LocalDateTime newTxnTime) {
        if (accountIds == null || accountIds.isEmpty() || newTxnTime == null) {
            return false;
        }
        
        // 扩展检查范围：包含所有兄弟账户
        // 因为父账户余额依赖于所有子账户的历史状态，
        // 如果任何兄弟账户在新交易时间之后有posting，父余额就需要用历史值重算
        Set<Long> allRelatedIds = new HashSet<>(accountIds);
        for (Long accountId : accountIds) {
            Account account = accountMapper.selectById(accountId);
            if (account != null && account.getParentAccountId() != null) {
                List<Account> siblings = accountService.getAccountChildren(account.getParentAccountId());
                for (Account sibling : siblings) {
                    allRelatedIds.add(sibling.getId());
                }
            }
        }
        
        // 查询所有相关账户（含兄弟）的最新交易时间
        LocalDateTime latestTxnTime = ledgerPostingMapper.selectLatestTxnTimeByAccountIds(
            new ArrayList<>(allRelatedIds));
        
        // 如果没有历史交易，不需要重算
        if (latestTxnTime == null) {
            return false;
        }
        
        // 如果新交易时间早于最新交易时间，说明是插入到历史中间，需要重算
        return newTxnTime.isBefore(latestTxnTime);
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
     * 修正虚拟账户的 accountId
     * 
     * 如果 posting 的 accountType 是虚拟账户类型（INCOME/EXPENSE/FEE/POSITION），但 accountId 指向的是 REAL 账户，
     * 需要自动获取或创建对应的虚拟账户并修正 accountId。
     * 
     * 这个方法在 createTransaction 和 updateTransaction 中都会调用，确保虚拟账户 ID 正确。
     * 
     * @param postings 分录列表
     * @param productId 产品ID（仅POSITION账户需要）
     */
    private void fixVirtualAccountIds(List<LedgerPosting> postings, Long productId) {
        for (LedgerPosting posting : postings) {
            String accountType = posting.getAccountType();
            // 如果是虚拟账户类型，需要确保 accountId 指向的是虚拟账户
            if ("INCOME".equals(accountType) || "EXPENSE".equals(accountType) || "FEE".equals(accountType) || "POSITION".equals(accountType)) {
                Account account = accountMapper.selectById(posting.getAccountId());
                // 调试日志：记录虚拟账户修正过程
                log.info("检查虚拟账户: posting.accountType={}, posting.accountId={}, 账户存在={}, 账户性质={}, 虚拟子类型={}", 
                    accountType, posting.getAccountId(), account != null, 
                    account != null ? account.getAccountKind() : "null", 
                    account != null ? account.getVirtualSubtype() : "null");
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
                    log.info("修正虚拟账户: 原accountId={}, 新accountId={}, 虚拟账户类型={}, ownerType={}", 
                        posting.getAccountId(), virtualAccount.getId(), accountType, ownerType);
                    posting.setAccountId(virtualAccount.getId());
                }
            }
        }
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
                // 父账户余额 = Σ 子账户余额（只排除信贷账户），与账户页保持一致
                BigDecimal parentBalance = children.stream()
                    .filter(child -> {
                        // 只排除信贷类账户，其他类型（包括 accountType 为 null 的）都计入
                        String accountType = child.getAccountType();
                        return !"CREDIT_CARD".equals(accountType) &&
                               !"HUABEI".equals(accountType) &&
                               !"BAITIAO".equals(accountType) &&
                               !"LOAN".equals(accountType);
                    })
                    .map(Account::getBalance)
                    .filter(b -> b != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                posting.setParentAccountBalanceAfter(parentBalance);
                
                // 如果父账户关联了产品，需要同步更新父账户的余额和份额
                Account parentAccount = accountMapper.selectById(accountAfter.getParentAccountId());
                if (parentAccount != null && parentAccount.getLinkedProductId() != null) {
                    // 父账户是关联产品的账户（如货币基金账户）
                    // 当子账户有现金流变化时，需要同步更新父账户的余额和份额
                    // 注意：使用已计算的 parentBalance（子账户余额之和），而不是数据库中可能过时的值
                    if ("CASH".equals(posting.getAccountType())) {
                        // 获取父账户在数据库中记录的旧份额（用于日志）
                        BigDecimal parentOldShares = parentAccount.getInitialShares() != null ? parentAccount.getInitialShares() : BigDecimal.ZERO;
                        
                        // 父账户的新余额 = 子账户余额之和（已在上面计算为 parentBalance）
                        // 注意：parentBalance 已经是更新后的子账户余额之和
                        BigDecimal parentNewBalance = parentBalance;
                        if (parentNewBalance.compareTo(BigDecimal.ZERO) < 0) {
                            parentNewBalance = BigDecimal.ZERO;
                        }
                        
                        // 更新父账户余额
                        accountMapper.updateBalance(parentAccount.getId(), parentNewBalance);
                        
                        // 更新父账户份额
                        // 对于货币基金账户，份额 ≈ 余额（净值通常接近1.0）
                        BigDecimal parentNewShares = parentNewBalance;
                        accountMapper.updateInitialShares(parentAccount.getId(), parentNewShares);
                        
                        log.info("同步更新关联产品账户: 账户ID={}, 账户名={}, 旧份额={}, 新余额={}, 新份额={}", 
                            parentAccount.getId(), parentAccount.getAccountName(), 
                            parentOldShares, parentNewBalance, parentNewShares);
                    }
                }
            } else {
                posting.setParentAccountBalanceAfter(null);
            }
            
            // 插入分录（包含历史余额）
            ledgerPostingMapper.insert(posting);
        }
        
        // === 二次修正：统一同一交易内所有同父posting的parent_account_balance_after ===
        // 因为一笔交易是原子的（如转账：A-4000 + B+4000），所有posting应使用交易完成后的父账户余额
        // 上面逐条处理时，前面的posting看不到后面posting的变化，会导致中间态
        Map<Long, BigDecimal> finalParentBalances = new HashMap<>();
        List<LedgerPosting> parentBalanceUpdates = new ArrayList<>();
        for (LedgerPosting posting : postings) {
            Account acct = accountMapper.selectById(posting.getAccountId());
            if (acct != null && acct.getParentAccountId() != null) {
                Long parentId = acct.getParentAccountId();
                BigDecimal finalBal = finalParentBalances.get(parentId);
                if (finalBal == null) {
                    // 计算最终父账户余额（所有子账户的当前余额之和，此时所有posting已生效）
                    List<Account> children = accountService.getAccountChildren(parentId);
                    finalBal = children.stream()
                        .filter(child -> {
                            String at = child.getAccountType();
                            return !"CREDIT_CARD".equals(at) && !"HUABEI".equals(at) && 
                                   !"BAITIAO".equals(at) && !"LOAN".equals(at);
                        })
                        .map(Account::getBalance)
                        .filter(b -> b != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                    finalParentBalances.put(parentId, finalBal);
                }
                // 如果不一致，修正
                if (posting.getParentAccountBalanceAfter() == null ||
                    posting.getParentAccountBalanceAfter().compareTo(finalBal) != 0) {
                    posting.setParentAccountBalanceAfter(finalBal);
                    parentBalanceUpdates.add(posting);
                }
            }
        }
        if (!parentBalanceUpdates.isEmpty()) {
            ledgerPostingMapper.batchUpdateBalanceAfter(parentBalanceUpdates);
        }
    }

    public List<LedgerTxn> getTransactions(Long userId, String txnType, LocalDate startDate, 
                                          LocalDate endDate, Long productId, Long parentAccountId, Long accountId, String note, Integer page, Integer pageSize) {
        Integer offset = null;
        Integer limit = null;
        if (page != null && pageSize != null && page > 0 && pageSize > 0) {
            offset = (page - 1) * pageSize;
            limit = pageSize;
        }
        
        // 如果指定了父账户但没有指定子账户，获取该父账户下所有子账户的ID
        List<Long> childAccountIds = null;
        if (parentAccountId != null && accountId == null) {
            List<Account> children = accountService.getAccountChildren(parentAccountId);
            if (children != null && !children.isEmpty()) {
                childAccountIds = children.stream().map(Account::getId).collect(java.util.stream.Collectors.toList());
            } else {
                // 如果指定了父账户但没有子账户，返回空列表（不应该匹配任何记录）
                return java.util.Collections.emptyList();
            }
        }
        
        return ledgerTxnMapper.selectByCondition(userId, txnType, startDate, endDate, productId, accountId, childAccountIds, note, offset, limit);
    }
    
    public int countTransactions(Long userId, String txnType, LocalDate startDate, 
                                LocalDate endDate, Long productId, Long parentAccountId, Long accountId, String note) {
        // 如果指定了父账户但没有指定子账户，获取该父账户下所有子账户的ID
        List<Long> childAccountIds = null;
        if (parentAccountId != null && accountId == null) {
            List<Account> children = accountService.getAccountChildren(parentAccountId);
            if (children != null && !children.isEmpty()) {
                childAccountIds = children.stream().map(Account::getId).collect(java.util.stream.Collectors.toList());
            } else {
                // 如果指定了父账户但没有子账户，返回0（不应该匹配任何记录）
                return 0;
            }
        }
        
        return ledgerTxnMapper.countByCondition(userId, txnType, startDate, endDate, productId, accountId, childAccountIds, note);
    }

    public LedgerTxn getTransactionDetail(String txnId) {
        return ledgerTxnMapper.selectByTxnId(txnId);
    }

    public List<LedgerPosting> getPostingsByTxnId(String txnId) {
        return ledgerPostingMapper.selectByTxnId(txnId);
    }

    /**
     * 批量查询多个交易的postings
     * @param txnIds 交易ID列表
     * @return 所有交易的postings列表
     */
    public List<LedgerPosting> getPostingsByTxnIds(List<String> txnIds) {
        if (txnIds == null || txnIds.isEmpty()) {
            return new ArrayList<>();
        }
        return ledgerPostingMapper.selectByTxnIds(txnIds);
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
        
        // 成本处理说明（与常见券商持仓口径对齐）：
        // - 场外转出成本 = 场外平均成本 × 转出份额（按平均成本法）
        // - 场内转入成本 = 场外转出成本（完全沿用原持仓成本，不因当日价格波动而改变）
        // 这样转托管只是“搬仓”，不会改变整体持仓成本和平均成本，便于与券商对账
        
        // 计算场外转出成本（按平均成本法）
        BigDecimal avgCost = totalShares.compareTo(BigDecimal.ZERO) > 0 
            ? totalCost.divide(totalShares, 6, java.math.RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        BigDecimal outCost = avgCost.multiply(transferShares);
        
        // 场内转入成本沿用场外成本，确保转托管不改变整体平均成本
        // 注意：transferPrice 仅用于记录展示，不进入成本计算
        BigDecimal inCost = outCost;
        
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
     * 根据账户类型计算余额变化方向（公共方法，供重算和余额更新共用）
     * 
     * - 资产类（CASH/POSITION/MMF/BROKER等）：DEBIT=加，CREDIT=减
     * - 负债类（LIABILITY/CREDIT_CARD/HUABEI/BAITIAO/LOAN）：DEBIT=减，CREDIT=加
     * - 收入类（INCOME）：DEBIT=减，CREDIT=加
     * - 支出/手续费类（EXPENSE/FEE）：DEBIT=加，CREDIT=减
     * 
     * @param account 账户对象
     * @param postingType DEBIT 或 CREDIT
     * @param amount 金额
     * @return 余额变化量（正数表示增加，负数表示减少）
     */
    private BigDecimal calculateBalanceDelta(Account account, String postingType, BigDecimal amount) {
        // 对于虚拟账户，使用 virtual_subtype 作为账户类型
        // 对于 REAL 账户，使用 account_type
        String accountType;
        if ("VIRTUAL".equals(account.getAccountKind())) {
            accountType = account.getVirtualSubtype();
        } else {
            accountType = account.getAccountType();
            if (accountType == null || accountType.isEmpty()) {
                accountType = account.getFundUsage();
            }
        }
        
        if (accountType == null) {
            // 默认按资产类处理
            return "DEBIT".equals(postingType) ? amount : amount.negate();
        }
        
        // 负债类/收入类：DEBIT减少余额，CREDIT增加余额
        if ("LIABILITY".equals(accountType) || accountType.contains("CREDIT") || 
            accountType.contains("HUABEI") || accountType.contains("BAITIAO") ||
            accountType.contains("LOAN") || "INCOME".equals(accountType)) {
            return "DEBIT".equals(postingType) ? amount.negate() : amount;
        }
        
        // 资产类/支出类/手续费类：DEBIT增加余额，CREDIT减少余额
        return "DEBIT".equals(postingType) ? amount : amount.negate();
    }

    /**
     * 重算账户的历史余额
     * 当插入/修改交易时调用，确保历史余额按交易时间顺序正确
     * 
     * 算法说明：
     * 1. 获取账户当前余额
     * 2. 查询该账户所有分录（按交易的 requested_at 时间排序）
     * 3. 计算起始余额 = initial_balance
     * 4. 按时间顺序、根据账户类型重新计算每条分录的 accountBalanceAfter
     * 5. 批量更新有变化的分录
     * 
     * @param accountId 需要重算的账户ID
     */
    public void recalculateAccountBalanceHistory(Long accountId) {
        // 1. 获取账户当前余额
        Account account = accountMapper.selectById(accountId);
        if (account == null) return;
        
        // 2. 查询该账户所有分录（按交易时间排序）
        List<LedgerPosting> postings = ledgerPostingMapper.selectByAccountIdOrderByTxnTime(accountId);
        if (postings.isEmpty()) return;
        
        // 3. 起始余额：使用账户 initial_balance，从第一条分录开始完整回放
        BigDecimal startBalance = account.getInitialBalance() != null ? account.getInitialBalance() : BigDecimal.ZERO;
        
        // 4. 按时间顺序计算每条分录的余额（根据账户类型区分方向）
        List<LedgerPosting> updates = new ArrayList<>();
        BigDecimal runningBalance = startBalance;
        
        // 获取父账户ID（用于计算父账户余额）
        Long parentAccountId = account.getParentAccountId();
        
        for (LedgerPosting p : postings) {
            runningBalance = runningBalance.add(calculateBalanceDelta(account, p.getPostingType(), p.getAmount()));
            
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
        
        // 5. 批量更新分录余额（如果有变化）
        if (!updates.isEmpty()) {
            ledgerPostingMapper.batchUpdateBalanceAfter(updates);
        }

        // 6. 同步 accounts 表中的余额为最后一条分录后的余额，确保账户当前余额与历史分录一致
        accountMapper.updateBalance(accountId, runningBalance);
    }

    /**
     * 统一重算多个账户的历史余额（按时间顺序，确保父账户余额使用历史时刻的余额）
     * 
     * 算法说明：
     * 1. 收集所有受影响账户及其父账户的所有子账户
     * 2. 按时间顺序获取所有相关分录
     * 3. 按时间顺序计算每个账户的历史余额
     * 4. 在计算每个分录时，同时计算该时刻的父账户余额（使用历史时刻的兄弟账户余额）
     * 
     * @param accountIds 需要重算的账户ID集合
     */
    public void recalculateAccountBalanceHistoryForAccounts(Set<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) return;
        
        log.info("[重算历史余额] 开始，输入账户IDs: {}", accountIds);
        
        // 1. 收集所有需要重算的账户（包括受影响账户及其父账户的所有子账户）
        Set<Long> allAccountIds = new HashSet<>(accountIds);
        Set<Long> parentAccountIds = new HashSet<>();
        
        // 获取所有受影响账户的父账户ID
        for (Long accountId : accountIds) {
            Account account = accountMapper.selectById(accountId);
            if (account != null && account.getParentAccountId() != null) {
                parentAccountIds.add(account.getParentAccountId());
                log.info("[重算历史余额] 账户 {} ({}) 的父账户ID: {}", accountId, account.getAccountName(), account.getParentAccountId());
            }
        }
        
        // 获取所有父账户的所有子账户（用于计算父账户余额）
        for (Long parentId : parentAccountIds) {
            List<Account> children = accountService.getAccountChildren(parentId);
            log.info("[重算历史余额] 父账户 {} 的子账户数量: {}", parentId, children.size());
            for (Account child : children) {
                allAccountIds.add(child.getId());
                log.info("[重算历史余额]   - 子账户: {} ({})", child.getId(), child.getAccountName());
            }
        }
        
        if (allAccountIds.isEmpty()) return;
        
        log.info("[重算历史余额] 最终需要重算的账户IDs: {}", allAccountIds);
        
        // 2. 查询所有相关账户的分录（按交易时间排序）
        List<LedgerPosting> allPostings = ledgerPostingMapper.selectByAccountIdsOrderByTxnTime(new ArrayList<>(allAccountIds));
        log.info("[重算历史余额] 查询到的分录数量: {}", allPostings.size());
        if (allPostings.isEmpty()) return;
        
        // 3. 初始化每个账户的起始余额和运行余额
        // 同时确保所有父账户的所有子账户都在 accountRunningBalances 中（即使它们没有分录）
        Map<Long, BigDecimal> accountRunningBalances = new HashMap<>();
        for (Long accountId : allAccountIds) {
            Account account = accountMapper.selectById(accountId);
            BigDecimal initialBalance = account != null && account.getInitialBalance() != null 
                ? account.getInitialBalance() : BigDecimal.ZERO;
            accountRunningBalances.put(accountId, initialBalance);
        }
        
        // 确保所有父账户的所有子账户都在 accountRunningBalances 中
        for (Long parentId : parentAccountIds) {
            List<Account> children = accountService.getAccountChildren(parentId);
            for (Account child : children) {
                if (!accountRunningBalances.containsKey(child.getId())) {
                    BigDecimal initialBalance = child.getInitialBalance() != null 
                        ? child.getInitialBalance() : BigDecimal.ZERO;
                    accountRunningBalances.put(child.getId(), initialBalance);
                }
            }
        }
        
        // 4. 按时间顺序处理所有分录
        // 收集所有有父账户的分录，用于后续更新父账户余额
        Map<Long, List<Account>> siblingsByParentId = new HashMap<>();
        List<LedgerPosting> updates = new ArrayList<>();
        
        // 缓存账户对象，避免在循环中重复查询
        Map<Long, Account> accountCache = new HashMap<>();
        for (Long accountId : allAccountIds) {
            Account acct = accountMapper.selectById(accountId);
            if (acct != null) {
                accountCache.put(accountId, acct);
            }
        }
        
        for (LedgerPosting p : allPostings) {
            Long accountId = p.getAccountId();
            BigDecimal currentBalance = accountRunningBalances.get(accountId);
            
            // 根据账户类型计算余额变化方向（修复：不再统一 DEBIT=加/CREDIT=减）
            Account acct = accountCache.get(accountId);
            if (acct == null) {
                acct = accountMapper.selectById(accountId);
                if (acct != null) accountCache.put(accountId, acct);
            }
            if (acct != null) {
                currentBalance = currentBalance.add(calculateBalanceDelta(acct, p.getPostingType(), p.getAmount()));
            } else {
                // 兜底：找不到账户时按资产类处理
                if ("DEBIT".equals(p.getPostingType())) {
                    currentBalance = currentBalance.add(p.getAmount());
                } else {
                    currentBalance = currentBalance.subtract(p.getAmount());
                }
            }
            accountRunningBalances.put(accountId, currentBalance);
            
            boolean needUpdate = false;
            
            // 更新分录的账户余额
            if (p.getAccountBalanceAfter() == null || 
                p.getAccountBalanceAfter().compareTo(currentBalance) != 0) {
                p.setAccountBalanceAfter(currentBalance);
                needUpdate = true;
            }
            
            // 计算父账户余额（如果有父账户，使用历史时刻的所有兄弟账户余额）
            Account account = accountCache.get(accountId);
            if (account == null) {
                account = accountMapper.selectById(accountId);
                if (account != null) accountCache.put(accountId, account);
            }
            if (account != null && account.getParentAccountId() != null) {
                Long parentId = account.getParentAccountId();
                
                // 获取该父账户的所有子账户（缓存，避免重复查询）
                List<Account> siblings = siblingsByParentId.get(parentId);
                if (siblings == null) {
                    siblings = accountService.getAccountChildren(parentId);
                    siblingsByParentId.put(parentId, siblings);
                }
                
                // 计算该时间点的父账户余额（所有子账户余额之和）
                BigDecimal parentBalance = BigDecimal.ZERO;
                StringBuilder debugInfo = new StringBuilder();
                debugInfo.append("[父账户余额计算] 分录ID=").append(p.getId())
                        .append(", 账户=").append(account.getAccountName())
                        .append(", 时间=").append(p.getTxnId()).append(": ");
                for (Account sibling : siblings) {
                    // 只排除信贷类账户，其他类型（包括 accountType 为 null 的）都计入
                    String accountType = sibling.getAccountType();
                    boolean isCreditAccount = "CREDIT_CARD".equals(accountType) || 
                                              "HUABEI".equals(accountType) || 
                                              "BAITIAO".equals(accountType) || 
                                              "LOAN".equals(accountType);
                    if (!isCreditAccount) {
                        // 使用历史时刻的余额（从 runningBalances 中获取）
                        // 如果该账户在 runningBalances 中，使用计算中的余额；否则使用 initialBalance
                        BigDecimal siblingBalance = accountRunningBalances.get(sibling.getId());
                        if (siblingBalance == null) {
                            // 如果该账户不在 runningBalances 中（没有分录），使用 initialBalance
                            siblingBalance = sibling.getInitialBalance() != null 
                                ? sibling.getInitialBalance() : BigDecimal.ZERO;
                            debugInfo.append(sibling.getAccountName()).append("(初始)=").append(siblingBalance).append(" + ");
                        } else {
                            debugInfo.append(sibling.getAccountName()).append("=").append(siblingBalance).append(" + ");
                        }
                        parentBalance = parentBalance.add(siblingBalance);
                    }
                }
                debugInfo.append(" => 父账户余额=").append(parentBalance);
                log.info(debugInfo.toString());
                
                // 更新该分录的父账户余额
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
        
        // 5. 修正同一交易内同父账户posting的parent_account_balance_after
        // 同一笔交易是原子的，同一父账户下的所有posting应使用该交易最后一条posting计算出的父余额
        // 按txn_id分组，找到每个txn_id+parentId组合的最终父余额
        Map<String, Map<Long, BigDecimal>> txnParentFinalBal = new LinkedHashMap<>();
        for (LedgerPosting p : allPostings) {
            Account acct = accountCache.get(p.getAccountId());
            if (acct != null && acct.getParentAccountId() != null && p.getParentAccountBalanceAfter() != null) {
                txnParentFinalBal
                    .computeIfAbsent(p.getTxnId(), k -> new HashMap<>())
                    .put(acct.getParentAccountId(), p.getParentAccountBalanceAfter());
            }
        }
        // 将同一交易同一父账户的所有posting统一为最终值
        for (LedgerPosting p : allPostings) {
            Account acct = accountCache.get(p.getAccountId());
            if (acct != null && acct.getParentAccountId() != null) {
                Map<Long, BigDecimal> parentMap = txnParentFinalBal.get(p.getTxnId());
                if (parentMap != null) {
                    BigDecimal finalBal = parentMap.get(acct.getParentAccountId());
                    if (finalBal != null && (p.getParentAccountBalanceAfter() == null ||
                        p.getParentAccountBalanceAfter().compareTo(finalBal) != 0)) {
                        p.setParentAccountBalanceAfter(finalBal);
                        if (!updates.contains(p)) {
                            updates.add(p);
                        }
                    }
                }
            }
        }
        
        // 6. 批量更新分录余额（如果有变化）
        if (!updates.isEmpty()) {
            ledgerPostingMapper.batchUpdateBalanceAfter(updates);
        }
        
        // 7. 同步 accounts 表中的余额为最后一条分录后的余额
        // 更新所有有分录的账户的余额（不仅仅是传入的 accountIds）
        for (Long accountId : allAccountIds) {
            BigDecimal finalBalance = accountRunningBalances.get(accountId);
            if (finalBalance != null) {
                accountMapper.updateBalance(accountId, finalBalance);
            }
        }
    }

    /**
     * 重算所有账户的历史余额
     * 
     * 用于修复历史数据中可能存在的父账户余额计算错误。
     * 这个方法会：
     * 1. 查询所有有分录的账户ID
     * 2. 调用 recalculateAccountBalanceHistoryForAccounts 重新计算
     * 
     * 注意：这个操作可能比较耗时，建议在低峰期执行
     */
    @Transactional
    public void recalculateAllAccountBalanceHistory() {
        // 查询所有有分录的账户ID
        List<Long> allAccountIds = ledgerPostingMapper.selectDistinctAccountIds();
        if (allAccountIds == null || allAccountIds.isEmpty()) {
            return;
        }
        
        // 调用统一重算方法
        recalculateAccountBalanceHistoryForAccounts(new java.util.HashSet<>(allAccountIds));
    }

    /**
     * 删除一笔交易及其所有分录，并回滚相关账户余额。
     *
     * 业务规则：
     * - 目前仅用于手工流水（orderId 为空的交易）；
     *   与订单、报销等业务关联的交易不允许直接删除。
     */
    @Transactional
    public void deleteTransaction(String txnId) {
        LedgerTxn txn = ledgerTxnMapper.selectByTxnId(txnId);
        if (txn == null) {
            throw new RuntimeException("交易不存在: " + txnId);
        }
        if (txn.getOrderId() != null) {
            throw new RuntimeException("禁止直接删除与订单关联的流水，请通过订单模块处理");
        }

        List<LedgerPosting> postings = ledgerPostingMapper.selectByTxnId(txnId);
        java.util.Set<Long> affectedAccountIds = new java.util.HashSet<>();

        // 回滚账户余额：对每条分录执行反向记账
        for (LedgerPosting posting : postings) {
            String reversedType = "DEBIT".equals(posting.getPostingType()) ? "CREDIT" : "DEBIT";
            updateAccountBalance(posting.getAccountId(), reversedType, posting.getAmount());
            affectedAccountIds.add(posting.getAccountId());
        }

        // 删除分录与交易
        ledgerPostingMapper.deleteByTxnId(txnId);
        ledgerTxnMapper.deleteByTxnId(txnId);

        // 重算受影响账户的历史余额（统一重算所有受影响账户及其父账户）
        recalculateAccountBalanceHistoryForAccounts(affectedAccountIds);
    }

    /**
     * 更新一笔交易：真正的UPDATE操作，保留created_at。
     *
     * 业务规则：
     * - 仅允许更新当前用户自己的手工流水（orderId 为空）。
     * - 保留原始的created_at，只更新updated_at。
     * - 先回滚旧分录的账户余额，再应用新分录的账户余额。
     * - 重算所有受影响账户（旧账户+新账户）的历史余额。
     *
     * @param oldTxnId 原交易ID
     * @param txnType 新的交易类型
     * @param bizGroupKey 新的业务分组键
     * @param postings 新的分录列表
     * @param note 新的备注
     * @param requestedAtStr 新的请求时间（格式：yyyy-MM-dd HH:mm:ss）
     * @param categoryId 新的分类ID
     * @param isReimbursable 是否可报销
     * @param productId 新的产品ID
     * @return 更新后的交易记录
     */
    @Transactional
    public LedgerTxn updateTransaction(String oldTxnId,
                                       String txnType,
                                       String bizGroupKey,
                                       List<LedgerPosting> postings,
                                       String note,
                                       String requestedAtStr,
                                       Long categoryId,
                                       Boolean isReimbursable,
                                       Long productId) {
        LedgerTxn existing = ledgerTxnMapper.selectByTxnId(oldTxnId);
        if (existing == null) {
            throw new RuntimeException("交易不存在: " + oldTxnId);
        }
        if (existing.getOrderId() != null) {
            throw new RuntimeException("禁止直接修改与订单关联的流水，请通过订单模块处理");
        }

        // 验证借贷平衡
        BigDecimal totalDebit = postings.stream()
                .filter(p -> "DEBIT".equals(p.getPostingType()))
                .map(LedgerPosting::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = postings.stream()
                .filter(p -> "CREDIT".equals(p.getPostingType()))
                .map(LedgerPosting::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal difference = totalDebit.subtract(totalCredit).abs();
        BigDecimal tolerance = new BigDecimal("0.0000000001");
        if (difference.compareTo(tolerance) > 0) {
            throw new RuntimeException(String.format("借贷不平衡：DEBIT总额=%s，CREDIT总额=%s，差额=%s", 
                totalDebit, totalCredit, difference));
        }

        // 验证所有账户都是叶子账户
        for (LedgerPosting posting : postings) {
            if (!accountService.isLeafAccount(posting.getAccountId())) {
                throw new RuntimeException(
                    String.format("账户ID %d 不是叶子账户，禁止对父账户记账", posting.getAccountId())
                );
            }
        }

        // 获取旧分录，用于回滚余额
        List<LedgerPosting> oldPostings = ledgerPostingMapper.selectByTxnId(oldTxnId);
        Set<Long> oldAccountIds = oldPostings.stream()
                .map(LedgerPosting::getAccountId)
                .collect(Collectors.toSet());
        
        // 收集新分录涉及的账户ID
        Set<Long> newAccountIds = postings.stream()
                .map(LedgerPosting::getAccountId)
                .collect(Collectors.toSet());
        
        // 合并所有受影响的账户ID（用于后续重算历史余额）
        Set<Long> allAffectedAccountIds = new HashSet<>(oldAccountIds);
        allAffectedAccountIds.addAll(newAccountIds);

        // 1. 回滚旧分录的账户余额
        for (LedgerPosting oldPosting : oldPostings) {
            String reversedType = "DEBIT".equals(oldPosting.getPostingType()) ? "CREDIT" : "DEBIT";
            updateAccountBalance(oldPosting.getAccountId(), reversedType, oldPosting.getAmount());
        }

        // 2. 删除旧分录
        ledgerPostingMapper.deleteByTxnId(oldTxnId);

        // 3. 更新交易记录（保留created_at）
        // 处理requestedAt
        LocalDateTime requestedAt = existing.getRequestedAt(); // 默认使用原时间
        if (requestedAtStr != null && !requestedAtStr.isEmpty()) {
            try {
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                requestedAt = LocalDateTime.parse(requestedAtStr, formatter);
            } catch (Exception e) {
                // 解析失败，使用原时间
            }
        }
        
        existing.setTxnType(txnType);
        existing.setBizGroupKey(bizGroupKey != null ? bizGroupKey : existing.getTxnId());
        existing.setRequestedAt(requestedAt);
        existing.setTradeDate(requestedAt.toLocalDate());
        existing.setNote(note);
        existing.setCategoryId(categoryId);
        existing.setIsReimbursable(isReimbursable != null ? isReimbursable : false);
        existing.setProductId(productId);
        // created_at保持不变，updated_at会自动更新
        
        ledgerTxnMapper.update(existing);

        // 4. 验证并修正虚拟账户的 accountId（与 createTransaction 相同的逻辑）
        // 这是关键步骤：确保 EXPENSE/INCOME 等虚拟账户类型的 posting 使用正确的虚拟账户 ID
        fixVirtualAccountIds(postings, productId);
        
        // 5. 插入新分录并更新账户余额
        for (LedgerPosting posting : postings) {
            posting.setTxnId(oldTxnId); // 使用原交易ID
        }
        insertPostingsWithBalance(oldTxnId, postings);

        // 6. 性能优化：判断是否需要重算历史余额
        // 修改操作比较复杂，需要考虑：
        // - 旧分录被删除后，旧账户的后续余额会变化
        // - 新分录插入后，新账户的后续余额可能会变化
        // 因此，只有当所有涉及的交易时间都是各自账户的最新时间时，才不需要重算
        boolean needsRecalc = needsHistoryRecalculationForUpdate(
            oldAccountIds, existing.getRequestedAt(),  // 旧账户和旧时间（已删除）
            newAccountIds, requestedAt                  // 新账户和新时间
        );
        
        log.info("[updateTransaction] 旧账户IDs: {}, 新账户IDs: {}", oldAccountIds, newAccountIds);
        log.info("[updateTransaction] 需要重算历史余额: {}, 所有受影响账户: {}", needsRecalc, allAffectedAccountIds);
        
        if (needsRecalc) {
            recalculateAccountBalanceHistoryForAccounts(allAffectedAccountIds);
        } else {
            log.info("[updateTransaction] 跳过历史余额重算");
        }

        // 返回更新后的交易记录
        return ledgerTxnMapper.selectByTxnId(oldTxnId);
    }
    
    /**
     * 判断修改操作是否需要重算历史余额
     * 
     * 修改操作涉及删除旧分录和插入新分录：
     * 1. 如果账户发生变更（旧账户和新账户不完全相同），总是需要重算
     *    - 因为新账户可能已经有历史分录，新插入的分录需要基于历史余额计算
     * 2. 如果旧分录不是旧账户的最新分录，删除后会影响后续余额，需要重算
     * 3. 如果新分录不是新账户的最新分录，插入后会影响后续余额，需要重算
     * 
     * @param oldAccountIds 旧账户ID集合
     * @param oldTxnTime 旧交易时间
     * @param newAccountIds 新账户ID集合
     * @param newTxnTime 新交易时间
     * @return true 如果需要重算
     */
    private boolean needsHistoryRecalculationForUpdate(
            Set<Long> oldAccountIds, LocalDateTime oldTxnTime,
            Set<Long> newAccountIds, LocalDateTime newTxnTime) {
        
        log.info("[needsHistoryRecalculationForUpdate] 检查是否需要重算: oldAccountIds={}, newAccountIds={}", oldAccountIds, newAccountIds);
        
        // 情况1：如果账户发生变更（旧账户和新账户不完全相同），总是需要重算
        // 这是因为新账户可能已经有历史分录，需要正确计算历史余额
        if (oldAccountIds != null && newAccountIds != null && !oldAccountIds.equals(newAccountIds)) {
            log.info("[needsHistoryRecalculationForUpdate] 账户变更，需要重算");
            return true;
        }
        
        // 检查旧账户：删除的分录是否是最新的
        // 注意：此时旧分录已经被删除，所以查询的最新时间不包括旧分录
        if (oldAccountIds != null && !oldAccountIds.isEmpty() && oldTxnTime != null) {
            LocalDateTime latestOldTime = ledgerPostingMapper.selectLatestTxnTimeByAccountIds(
                new ArrayList<>(oldAccountIds));
            // 如果旧账户还有分录，且有分录的时间晚于被删除的分录时间，需要重算
            if (latestOldTime != null && latestOldTime.isAfter(oldTxnTime)) {
                return true;
            }
        }
        
        // 检查新账户：插入的分录是否是最新的
        // 注意：此时新分录已经被插入，所以查询的最新时间包括新分录
        if (newAccountIds != null && !newAccountIds.isEmpty() && newTxnTime != null) {
            LocalDateTime latestNewTime = ledgerPostingMapper.selectLatestTxnTimeByAccountIds(
                new ArrayList<>(newAccountIds));
            // 如果有比新分录更晚的分录，需要重算
            if (latestNewTime != null && latestNewTime.isAfter(newTxnTime)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 快速购买货币基金（N+0，无需订单和结算）
     * 
     * 适用于场外货币基金（MMF），有关联账户的产品。
     * 直接创建交易流水并更新持仓份额，无需创建订单和结算记录。
     * 
     * 业务逻辑：
     * 1. 检查产品是否是MMF且有关联账户
     * 2. 计算份额（金额 / 净值，货币基金净值通常为1.0）
     * 3. 更新关联账户的 initial_shares
     * 4. 创建交易流水：CASH CREDIT（出金账户减少）+ CASH DEBIT（入金账户增加）
     * 
     * @param userId 用户ID
     * @param familyId 家庭ID，可为空
     * @param productId 产品ID（必须是MMF类型且有关联账户）
     * @param sourceAccountId 出金账户ID（必须是叶子账户）
     * @param targetAccountId 入金账户ID（关联账户的子账户，可为空则使用关联账户的第一个子账户）
     * @param amount 购买金额
     * @param nav 净值（通常为1.0，可为空则默认1.0）
     * @param note 备注（可选）
     * @param requestedAt 交易时间（可为空则使用当前时间）
     * @return 创建的交易记录
     */
    public LedgerTxn quickBuyMoneyMarketFund(Long userId, Long familyId, Long productId, 
                                            Long sourceAccountId, Long targetAccountId,
                                            BigDecimal amount, BigDecimal nav, String note, 
                                            LocalDateTime requestedAt) {
        // 1. 验证产品
        ProductMaster product = productMasterMapper.selectById(productId);
        if (product == null) {
            throw new RuntimeException("产品不存在: productId=" + productId);
        }
        if (!"MMF".equals(product.getAssetType())) {
            throw new RuntimeException("该产品不是货币基金，请使用普通购买流程: productId=" + productId);
        }
        
        // 2. 检查是否有关联账户
        Account linkedAccount = accountMapper.selectByLinkedProductId(productId);
        if (linkedAccount == null) {
            throw new RuntimeException("该产品没有关联账户，请使用普通购买流程: productId=" + productId);
        }
        
        // 3. 验证出金账户
        Account sourceAccount = accountMapper.selectById(sourceAccountId);
        if (sourceAccount == null) {
            throw new RuntimeException("出金账户不存在: accountId=" + sourceAccountId);
        }
        if (sourceAccount.getParentAccountId() == null) {
            throw new RuntimeException("出金账户必须是叶子账户: accountId=" + sourceAccountId);
        }
        
        // 4. 确定入金账户
        Account targetAccount = null;
        if (targetAccountId != null) {
            targetAccount = accountMapper.selectById(targetAccountId);
            if (targetAccount == null) {
                throw new RuntimeException("入金账户不存在: accountId=" + targetAccountId);
            }
            // 验证入金账户是关联账户的子账户
            if (targetAccount.getParentAccountId() == null || 
                !targetAccount.getParentAccountId().equals(linkedAccount.getId())) {
                throw new RuntimeException("入金账户必须是关联账户的子账户: accountId=" + targetAccountId);
            }
        } else {
            // 如果没有指定，使用关联账户的第一个子账户
            List<Account> children = accountService.getAccountChildren(linkedAccount.getId());
            if (children == null || children.isEmpty()) {
                throw new RuntimeException("关联账户没有子账户，无法入金: linkedAccountId=" + linkedAccount.getId());
            }
            targetAccount = children.get(0);
        }
        
        // 5. 计算份额（金额 / 净值）
        BigDecimal finalNav = nav != null && nav.compareTo(BigDecimal.ZERO) > 0 ? nav : BigDecimal.ONE;
        BigDecimal shares = amount.divide(finalNav, 6, RoundingMode.HALF_UP);
        
        // 6. 更新关联账户的 initial_shares
        BigDecimal currentShares = linkedAccount.getInitialShares();
        if (currentShares == null) {
            currentShares = BigDecimal.ZERO;
        }
        BigDecimal newShares = currentShares.add(shares);
        accountMapper.updateInitialShares(linkedAccount.getId(), newShares);
        
        // 7. 创建交易流水
        String ownerType = familyId != null ? "FAMILY" : "PERSONAL";
        List<LedgerPosting> postings = new ArrayList<>();
        
        // CASH CREDIT：出金账户减少
        LedgerPosting sourcePosting = new LedgerPosting();
        sourcePosting.setPostingType("CREDIT");
        sourcePosting.setAccountId(sourceAccountId);
        sourcePosting.setAccountType("CASH");
        sourcePosting.setAmount(amount);
        sourcePosting.setCurrency(sourceAccount.getCurrency() != null ? sourceAccount.getCurrency() : "CNY");
        postings.add(sourcePosting);
        
        // CASH DEBIT：入金账户增加
        LedgerPosting targetPosting = new LedgerPosting();
        targetPosting.setPostingType("DEBIT");
        targetPosting.setAccountId(targetAccount.getId());
        targetPosting.setAccountType("CASH");
        targetPosting.setAmount(amount);
        targetPosting.setShares(shares); // 记录份额，用于持仓计算
        targetPosting.setCurrency(targetAccount.getCurrency() != null ? targetAccount.getCurrency() : "CNY");
        postings.add(targetPosting);
        
        // 8. 创建交易
        String finalNote = note != null ? note : 
            String.format("快速购买: %s %s份，金额%s元", product.getProductName(), shares, amount);
        
        // 将 LocalDateTime 转换为字符串格式（yyyy-MM-dd HH:mm:ss）
        String requestedAtStr = null;
        if (requestedAt != null) {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            requestedAtStr = requestedAt.format(formatter);
        }
        
        LedgerTxn txn = createTransaction(userId, familyId, "SUBSCRIPTION", null, postings, finalNote, requestedAtStr, null, false);
        
        // 9. 设置产品ID（用于持仓计算）
        txn.setProductId(productId);
        ledgerTxnMapper.update(txn);
        
        log.info("快速购买货币基金成功: productId={}, shares={}, amount={}, linkedAccountId={}", 
            productId, shares, amount, linkedAccount.getId());
        
        return txn;
    }
}

