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
/**
 * 业务注释规范化: QuickEntryService 服务类，负责业务规则、账户、账本流水、订单或持仓数据的组合处理。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class QuickEntryService {

    /**
     * 业务注释规范化: ledgerService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final LedgerService ledgerService;
    /**
     * 业务注释规范化: accountMapper 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final AccountMapper accountMapper;
    /**
     * 业务注释规范化: accountService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final AccountService accountService;

    /**
     * 业务注释规范化: 处理 QuickEntryService 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param ledgerService ledgerService 业务字段，承载该对象在后端流程中的核心属性。
     * @param accountMapper accountMapper 业务字段，承载该对象在后端流程中的核心属性。
     * @param accountService accountService 业务字段，承载该对象在后端流程中的核心属性。
     */
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
    /**
     * 业务注释规范化: 处理 quickExpense 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param userId 所属用户 ID，用于限定个人数据权限和查询范围。
     * @param accountId 关联账户 ID，指向承载资金、持仓或虚拟科目的账户。
     * @param amount 业务金额，通常以账户币种计价，正负含义由交易类型和分录方向决定。
     * @param note note 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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
    /**
     * 业务注释规范化: 处理 quickIncome 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param userId 所属用户 ID，用于限定个人数据权限和查询范围。
     * @param accountId 关联账户 ID，指向承载资金、持仓或虚拟科目的账户。
     * @param amount 业务金额，通常以账户币种计价，正负含义由交易类型和分录方向决定。
     * @param note note 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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
}

