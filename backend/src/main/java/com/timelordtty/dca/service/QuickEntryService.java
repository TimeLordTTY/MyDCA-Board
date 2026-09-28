package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.LedgerPosting;
import com.timelordtty.dca.model.LedgerTxn;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 快速录入服务（QuickEntryService）
 *
 * 职责：提供便捷的收入/支出快速录入入口，内部使用 LedgerService 创建对应的会计分录
 *
 * 说明：快速录入为简化场景，示例中对收入/支出只生成基础的现金与收入/支出分录，实际系统应使用专用虚拟账户并保证审计链完整
 */
@Service
public class QuickEntryService {

    /**
     * 账本服务入口，负责流水事务、分录、余额影响和快速记账编排。
     */
    private final LedgerService ledgerService;
    /**
     * 账户持久化 Mapper，负责账户主表的查询、插入、更新和账户树读取。
     */
    private final AccountMapper accountMapper;
    /**
     * 账户服务入口，负责账户树查询、账户归属校验、账户创建更新以及余额调整编排。
     */
    private final AccountService accountService;

    /**
     * 装配账本、账户和用户组件，用于把快速记账表单转换为标准账本事务。
     */
    public QuickEntryService(LedgerService ledgerService, AccountMapper accountMapper, AccountService accountService) {
        this.ledgerService = ledgerService;
        this.accountMapper = accountMapper;
        this.accountService = accountService;
    }

    /**
     * 快速记录支出（生成 CASH CREDIT + EXPENSE DEBIT），并调用统一记账服务创建交易
     *
     * 注意：会校验账户的 fund_usage（专款不得用于日常支出）
     *
     * @param userId 发起用户ID
     * @param accountId 现金账户ID
     * @param amount 金额
     * @param note 备注
     * @return 创建的 LedgerTxn
     */
    @Transactional
    public LedgerTxn quickExpense(Long userId, Long accountId, BigDecimal amount, String note) {
        Account account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new RuntimeException("账户不存在");
        }

        // 校验账户fund_usage必须是SPENDABLE
        if ("REAL".equals(account.getAccountKind()) && "CASH".equals(account.getAccountType()) 
            && accountService.isLeafAccount(accountId) && !"SPENDABLE".equals(account.getFundUsage())) {
            throw new RuntimeException("专款账户禁止日常支出");
        }

        // 生成双分录：CASH CREDIT + EXPENSE DEBIT
        List<LedgerPosting> postings = new ArrayList<>();

        // CASH账户：CREDIT（减少）
        LedgerPosting cashPosting = new LedgerPosting();
        cashPosting.setPostingType("CREDIT");
        cashPosting.setAccountId(accountId);
        cashPosting.setAccountType("CASH");
        cashPosting.setAmount(amount);
        cashPosting.setCurrency(account.getCurrency());
        postings.add(cashPosting);

        // EXPENSE账户：DEBIT（增加）
        // 获取或创建虚拟EXPENSE账户
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

        return ledgerService.createTransaction(userId, null, "EXPENSE", null, postings, note);
    }

    /**
     * 快速记录收入（生成 CASH DEBIT + INCOME CREDIT），并调用统一记账服务创建交易
     *
     * @param userId 发起用户ID
     * @param accountId 现金账户ID
     * @param amount 金额
     * @param note 备注
     * @return 创建的 LedgerTxn
     */
    @Transactional
    public LedgerTxn quickIncome(Long userId, Long accountId, BigDecimal amount, String note) {
        Account account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new RuntimeException("账户不存在");
        }

        // 生成双分录：CASH DEBIT + INCOME CREDIT
        List<LedgerPosting> postings = new ArrayList<>();

        // CASH账户：DEBIT（增加）
        LedgerPosting cashPosting = new LedgerPosting();
        cashPosting.setPostingType("DEBIT");
        cashPosting.setAccountId(accountId);
        cashPosting.setAccountType("CASH");
        cashPosting.setAmount(amount);
        cashPosting.setCurrency(account.getCurrency());
        postings.add(cashPosting);

        // INCOME账户：CREDIT（增加）
        // 获取或创建虚拟INCOME账户
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

        return ledgerService.createTransaction(userId, null, "INCOME", null, postings, note);
    }

    /**
     * 快速记录一笔真实账户之间的转账（转出账户 CREDIT + 转入账户 DEBIT），并调用统一记账服务创建交易。
     *
     * <p>转账入口不复用“查到 ID 就能记账”的逻辑：两个账户都必须当前 user/family 可见、为启用的 REAL 叶子账户，
     * 且转出账户 != 转入账户、币种一致、金额大于 0；任何校验不通过都直接拒绝，由事务整体回滚。</p>
     *
     * <p>一笔转账只生成一条业务交易，流水页会按转出 / 转入展示为两条视图，但底层不会重复记账，
     * 也不计入收入 / 支出净现金流。</p>
     *
     * @param userId 发起用户ID
     * @param familyId 家庭ID，可为空
     * @param sourceAccountId 转出账户ID
     * @param targetAccountId 转入账户ID
     * @param amount 转账金额，必须大于 0
     * @param note 备注
     * @return 创建的 LedgerTxn
     */
    @Transactional
    public LedgerTxn quickTransfer(Long userId, Long familyId, Long sourceAccountId, Long targetAccountId,
                                   BigDecimal amount, String note) {
        if (sourceAccountId == null || targetAccountId == null) {
            throw new RuntimeException("转账必须同时指定转出账户与转入账户");
        }
        if (sourceAccountId.equals(targetAccountId)) {
            throw new RuntimeException("转出账户与转入账户不能相同");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("转账金额必须大于 0");
        }

        Account sourceAccount = accountMapper.selectVisibleRealById(sourceAccountId, userId, familyId);
        if (sourceAccount == null) {
            throw new RuntimeException("转出账户不存在、已停用或当前用户/家庭不可见");
        }
        Account targetAccount = accountMapper.selectVisibleRealById(targetAccountId, userId, familyId);
        if (targetAccount == null) {
            throw new RuntimeException("转入账户不存在、已停用或当前用户/家庭不可见");
        }
        if (!accountService.isLeafAccount(sourceAccountId) || !accountService.isLeafAccount(targetAccountId)) {
            throw new RuntimeException("父账户仅用于聚合展示，不能作为转账账户；请选择叶子账户");
        }
        if (!currencyEquals(sourceAccount.getCurrency(), targetAccount.getCurrency())) {
            throw new RuntimeException("转出账户与转入账户币种必须一致；跨币种转账暂不支持");
        }

        // 一笔转账使用现有转账流水语义：转出账户 CREDIT，转入账户 DEBIT
        List<LedgerPosting> postings = new ArrayList<>();

        LedgerPosting creditPosting = new LedgerPosting();
        creditPosting.setPostingType("CREDIT");
        creditPosting.setAccountId(sourceAccountId);
        creditPosting.setAccountType("CASH");
        creditPosting.setAmount(amount);
        creditPosting.setCurrency(sourceAccount.getCurrency());
        postings.add(creditPosting);

        LedgerPosting debitPosting = new LedgerPosting();
        debitPosting.setPostingType("DEBIT");
        debitPosting.setAccountId(targetAccountId);
        debitPosting.setAccountType("CASH");
        debitPosting.setAmount(amount);
        debitPosting.setCurrency(targetAccount.getCurrency());
        postings.add(debitPosting);

        return ledgerService.createTransaction(userId, familyId, "TRANSFER_OUT", null, postings, note);
    }

    /** 比较两个账户币种是否一致；双方都为空时视为一致，避免因历史空值误阻断。 */
    private boolean currencyEquals(String left, String right) {
        if (left == null || left.isBlank()) {
            return right == null || right.isBlank();
        }
        return left.equalsIgnoreCase(right);
    }
}

