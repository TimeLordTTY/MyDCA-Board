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

    private final LedgerService ledgerService;
    private final AccountMapper accountMapper;
    private final AccountService accountService;

    public QuickEntryService(LedgerService ledgerService, AccountMapper accountMapper, AccountService accountService) {
        this.ledgerService = ledgerService;
        this.accountMapper = accountMapper;
        this.accountService = accountService;
    }

    @Transactional
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
    public LedgerTxn quickExpense(Long userId, Long accountId, BigDecimal amount, String note) {
        return quickExpense(userId, accountId, amount, note, null, null, false);
    }

    public LedgerTxn quickExpense(Long userId, Long accountId, BigDecimal amount, String note, String occurredAt) {
        return quickExpense(userId, accountId, amount, note, occurredAt, null, false);
    }

    public LedgerTxn quickExpense(Long userId, Long accountId, BigDecimal amount, String note, String occurredAt, Long categoryId, Boolean isReimbursable) {
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

        return ledgerService.createTransaction(userId, null, "EXPENSE", null, postings, note, occurredAt, categoryId, isReimbursable);
    }

    @Transactional
    /**
     * 快速记录收入（生成 CASH DEBIT + INCOME CREDIT），并调用统一记账服务创建交易
     *
     * @param userId 发起用户ID
     * @param accountId 现金账户ID
     * @param amount 金额
     * @param note 备注
     * @return 创建的 LedgerTxn
     */
    public LedgerTxn quickIncome(Long userId, Long accountId, BigDecimal amount, String note) {
        return quickIncome(userId, accountId, amount, note, null, null);
    }

    public LedgerTxn quickIncome(Long userId, Long accountId, BigDecimal amount, String note, String occurredAt) {
        return quickIncome(userId, accountId, amount, note, occurredAt, null);
    }

    public LedgerTxn quickIncome(Long userId, Long accountId, BigDecimal amount, String note, String occurredAt, Long categoryId) {
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

        return ledgerService.createTransaction(userId, null, "INCOME", null, postings, note, occurredAt, categoryId, false);
    }
}

