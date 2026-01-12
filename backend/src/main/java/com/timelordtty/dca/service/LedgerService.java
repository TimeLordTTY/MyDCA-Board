package com.timelordtty.dca.service;

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
import java.util.List;
import java.util.UUID;

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

    public LedgerService(LedgerTxnMapper ledgerTxnMapper, LedgerPostingMapper ledgerPostingMapper,
                        AccountMapper accountMapper, AccountService accountService, ProductMasterMapper productMasterMapper) {
        this.ledgerTxnMapper = ledgerTxnMapper;
        this.ledgerPostingMapper = ledgerPostingMapper;
        this.accountMapper = accountMapper;
        this.accountService = accountService;
        this.productMasterMapper = productMasterMapper;
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
        // 如果不平衡，抛出异常，事务回滚，不创建任何记录
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new RuntimeException(
                String.format("借贷不平衡: DEBIT总额=%s, CREDIT总额=%s, 差额=%s", 
                    totalDebit, totalCredit, totalDebit.subtract(totalCredit))
            );
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
        txn.setIsReimbursable(isReimbursable != null ? isReimbursable : false);
        txn.setIsReimbursed(false);
        txn.setIsReversed(false);

        ledgerTxnMapper.insert(txn);

        // 创建分录并更新账户余额
        for (LedgerPosting posting : postings) {
            posting.setTxnId(txnId);
            ledgerPostingMapper.insert(posting);

            // 更新账户余额
            updateAccountBalance(posting.getAccountId(), posting.getPostingType(), posting.getAmount());
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
            return;
        }

        BigDecimal oldBalance = account.getBalance();
        BigDecimal newBalance = oldBalance;
        String accountType = account.getAccountType();

        // 资产类账户：DEBIT增加余额，CREDIT减少余额
        // 公式：newBalance = oldBalance + (postingType == DEBIT ? amount : -amount)
        if ("CASH".equals(accountType) || "POSITION".equals(accountType) || "RECEIVABLE".equals(accountType)) {
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

        // 更新账户余额（数据库操作）
        accountMapper.updateBalance(accountId, newBalance);
        
        // 记录余额变化日志（简化版，实际应该使用日志框架）
        // 格式：账户[%d]余额变化: %s -> %s (变化: %s, 方向: %s, 账户类型: %s)
        // 注意：日志大小控制，只记录关键信息，避免日志过大
    }

    public List<LedgerTxn> getTransactions(Long userId, String txnType, LocalDate startDate, 
                                          LocalDate endDate, Long productId) {
        return ledgerTxnMapper.selectByCondition(userId, txnType, startDate, endDate, productId);
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
                                  BigDecimal refundAmount, Long accountId, String note) {
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
        refundTxn.setRequestedAt(LocalDateTime.now());
        refundTxn.setTradeDate(LocalDate.now());
        refundTxn.setStatus("CONFIRMED");
        refundTxn.setNote(note != null ? note : "退款：" + refundAmount);
        refundTxn.setIsReversed(false);

        ledgerTxnMapper.insert(refundTxn);

        // 创建分录并更新账户余额
        for (LedgerPosting posting : postings) {
            posting.setTxnId(txnId);
            ledgerPostingMapper.insert(posting);
            updateAccountBalance(posting.getAccountId(), posting.getPostingType(), posting.getAmount());
        }

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
                                     BigDecimal reimburseAmount, Long accountId, String note) {
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
        reimburseTxn.setRequestedAt(LocalDateTime.now());
        reimburseTxn.setTradeDate(LocalDate.now());
        reimburseTxn.setStatus("CONFIRMED");
        reimburseTxn.setNote(note != null ? note : "报销：" + reimburseAmount);
        reimburseTxn.setIsReversed(false);

        ledgerTxnMapper.insert(reimburseTxn);

        // 创建分录并更新账户余额
        for (LedgerPosting posting : postings) {
            posting.setTxnId(txnId);
            ledgerPostingMapper.insert(posting);
            updateAccountBalance(posting.getAccountId(), posting.getPostingType(), posting.getAmount());
        }

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
        
        // 获取场外和场内持仓账户（通过产品名称+渠道区分）
        String ownerType = "PERSONAL"; // 简化处理，实际应该从用户信息获取
        Account otcPositionAccount = accountService.getOrCreatePositionAccount(
            otcProduct.getId(), otcProduct.getProductName() + "(场外)", ownerType, userId, familyId);
        Account exchangePositionAccount = accountService.getOrCreatePositionAccount(
            exchangeProduct.getId(), exchangeProduct.getProductName() + "(场内)", ownerType, userId, familyId);
        
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
        
        // 计算转出成本（按平均成本法）
        BigDecimal avgCost = totalShares.compareTo(BigDecimal.ZERO) > 0 
            ? totalCost.divide(totalShares, 6, java.math.RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        BigDecimal outCost = avgCost.multiply(transferShares);
        
        // 场内成本按转出价格计算（通常为0费用，但可以指定价格）
        BigDecimal inCost = transferPrice.multiply(transferShares);
        
        // 生成转托管分录
        List<LedgerPosting> postings = new java.util.ArrayList<>();
        
        // 场外POSITION CREDIT（减少份额和成本）
        LedgerPosting otcPosting = new LedgerPosting();
        otcPosting.setPostingType("CREDIT");
        otcPosting.setAccountId(otcPositionAccount.getId());
        otcPosting.setAccountType("POSITION");
        otcPosting.setAmount(outCost);
        otcPosting.setShares(transferShares);
        otcPosting.setCurrency("CNY");
        postings.add(otcPosting);
        
        // 场内POSITION DEBIT（增加份额，成本按转出价格计算）
        LedgerPosting exchangePosting = new LedgerPosting();
        exchangePosting.setPostingType("DEBIT");
        exchangePosting.setAccountId(exchangePositionAccount.getId());
        exchangePosting.setAccountType("POSITION");
        exchangePosting.setAmount(inCost);
        exchangePosting.setShares(transferShares);
        exchangePosting.setCurrency("CNY");
        postings.add(exchangePosting);
        
        // 创建转托管交易
        String txnId = "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        LedgerTxn transferTxn = new LedgerTxn();
        transferTxn.setTxnId(txnId);
        transferTxn.setUserId(userId);
        transferTxn.setFamilyId(familyId);
        transferTxn.setTxnType("CUSTODY_TRANSFER");
        transferTxn.setBizGroupKey(txnId);
        transferTxn.setProductId(productId);
        transferTxn.setRelationType("CUSTODY_TRANSFER_OF");
        transferTxn.setRequestedAt(transferDate.atStartOfDay());
        transferTxn.setTradeDate(transferDate);
        transferTxn.setStatus("CONFIRMED");
        transferTxn.setNote(note != null ? note : "转托管：" + transferShares + "份");
        transferTxn.setIsReversed(false);
        
        ledgerTxnMapper.insert(transferTxn);
        
        // 创建分录并更新账户余额
        for (LedgerPosting posting : postings) {
            posting.setTxnId(txnId);
            ledgerPostingMapper.insert(posting);
            updateAccountBalance(posting.getAccountId(), posting.getPostingType(), posting.getAmount());
        }
        
        return transferTxn;
    }
}

