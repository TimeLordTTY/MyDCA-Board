package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.model.Account;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 账户服务（AccountService）
 *
 * 职责：账户实体的 CRUD、账户树构建、叶子账户判定及余额辅助操作
 *
 * 重要规则：
 * - 仅叶子账户允许出现在记账分录中（ledger_posting.account_id）
 * - VIRTUAL 账户不允许设置父账户
 * - 子账户必须为 REAL 类型，且只有 REAL/CASH 允许形成父子层级
 *
 * 该服务提供的操作会结合数据库查询与应用层校验；余额的正式变更应通过记账流程完成，手工调整应记录 ADJUST 类型流水。
 */
@Service
public class AccountService {

    private final AccountMapper accountMapper;

    public AccountService(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    @Transactional
    /**
     * 创建账户并设置默认值
     *
     * 流程：
     * 1. 应用层校验账户合法性（如父子关系约束）
     * 2. 生成唯一账户代码（如未提供）
     * 3. 设置默认余额/占用/初始值并持久化
     *
     * 注意：余额调整应走记账或生成 ADJUST 流程。此方法仅在创建时设定初始余额与元数据。
     *
     * @param account 待创建的账户实体
     * @return 创建后的账户实体（包含自增ID）
     */
    public Account createAccount(Account account) {
        // 应用层校验规则
        validateAccountCreation(account);

        // 生成账户代码
        if (account.getAccountCode() == null || account.getAccountCode().isEmpty()) {
            account.setAccountCode("ACC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }

        // 检查账户代码唯一性
        Account existing = accountMapper.selectByCode(account.getAccountCode());
        if (existing != null) {
            throw new RuntimeException("账户代码已存在");
        }

        // 设置默认值
        if (account.getBalance() == null) {
            account.setBalance(BigDecimal.ZERO);
        }
        if (account.getReservedAmount() == null) {
            account.setReservedAmount(BigDecimal.ZERO);
        }
        if (account.getInitialBalance() == null) {
            account.setInitialBalance(account.getBalance());
        }
        if (account.getIsActive() == null) {
            account.setIsActive(true);
        }

        accountMapper.insert(account);
        return account;
    }

    /**
     * 校验账户创建规则（应用层）
     *
     * 规则包括：VIRTUAL 不允许 parent、子账户必须为 REAL、只有 REAL/CASH 允许父子层级等
     */
    private void validateAccountCreation(Account account) {
        // 规则1: VIRTUAL账户不允许设置parent_account_id
        if ("VIRTUAL".equals(account.getAccountKind()) && account.getParentAccountId() != null) {
            throw new RuntimeException("VIRTUAL账户不允许设置父账户");
        }

        // 规则2: 子账户必须是REAL
        if (account.getParentAccountId() != null && !"REAL".equals(account.getAccountKind())) {
            throw new RuntimeException("子账户必须是REAL类型");
        }

        // 规则3: 只有CASH类型的REAL账户允许形成父子层级
        if (account.getParentAccountId() != null && !"CASH".equals(account.getAccountType())) {
            throw new RuntimeException("只有CASH类型的REAL账户允许形成父子层级");
        }
    }

    /**
     * 查询单个账户
     * @param accountId 账户ID
     * @return Account 实体或 null
     */
    public Account getAccount(Long accountId) {
        return accountMapper.selectById(accountId);
    }

    /**
     * 获取账户树（父子结构），并对父账户计算聚合余额（子账户之和）
     *
     * @param ownerUserId 归属用户ID（个人视图），可为空
     * @param ownerFamilyId 归属家庭ID（家庭视图），可为空
     * @return 账户列表（包含父账户及其子账户），父账户的 balance 为子账户总和
     */
    public List<Account> getAccountTree(Long ownerUserId, Long ownerFamilyId) {
        List<Account> accounts = accountMapper.selectByOwner(ownerUserId, ownerFamilyId);
        // 构建树形结构
        for (Account account : accounts) {
            if (account.getParentAccountId() == null) {
                // 父账户，计算子账户余额
                List<Account> children = accountMapper.selectChildren(account.getId());
                BigDecimal totalBalance = children.stream()
                        .map(Account::getBalance)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                account.setBalance(totalBalance);
            }
        }
        return accounts;
    }

    /**
     * 判断账户是否为叶子账户（没有子账户）
     * 叶子账户是唯一允许出现在记账分录中的账户
     * @param accountId 账户ID
     * @return true 如果没有子账户
     */
    public boolean isLeafAccount(Long accountId) {
        List<Account> children = accountMapper.selectChildren(accountId);
        return children.isEmpty();
    }

    /**
     * 获取或创建虚拟账户（EXPENSE/INCOME/FEE/POSITION）
     * 
     * 业务规则：
     * - 虚拟账户用于记账的虚拟科目，禁止手工改余额，只能通过分录更新
     * - 每个用户/家庭应该有独立的虚拟账户（按ownerType和ownerUserId/ownerFamilyId区分）
     * - 如果账户不存在，自动创建
     * 
     * 账户命名规则：
     * - EXPENSE账户：账户名称="费用账户"
     * - INCOME账户：账户名称="收入账户"
     * - FEE账户：账户名称="手续费账户"
     * - POSITION账户：账户名称="持仓账户-{产品名称}"（每个产品一个持仓账户）
     * 
     * @param virtualSubtype 虚拟账户子类型：EXPENSE/INCOME/FEE/POSITION
     * @param accountType 账户类型：EXPENSE/INCOME/FEE/POSITION
     * @param ownerType 归属类型：PERSONAL/FAMILY
     * @param ownerUserId 归属用户ID（个人账户），可为空
     * @param ownerFamilyId 归属家庭ID（家庭账户），可为空
     * @param productId 产品ID（仅POSITION账户需要），可为空
     * @param productName 产品名称（仅POSITION账户需要），可为空
     * @return 虚拟账户实体
     */
    @Transactional
    public Account getOrCreateVirtualAccount(String virtualSubtype, String accountType, 
                                              String ownerType, Long ownerUserId, Long ownerFamilyId,
                                              Long productId, String productName) {
        // 构建账户名称
        String accountName;
        if ("POSITION".equals(virtualSubtype) && productName != null) {
            accountName = "持仓账户-" + productName;
        } else if ("EXPENSE".equals(virtualSubtype)) {
            accountName = "费用账户";
        } else if ("INCOME".equals(virtualSubtype)) {
            accountName = "收入账户";
        } else if ("FEE".equals(virtualSubtype)) {
            accountName = "手续费账户";
        } else {
            accountName = virtualSubtype + "账户";
        }

        // 查询是否已存在虚拟账户
        List<Account> existingAccounts = accountMapper.selectByOwner(ownerUserId, ownerFamilyId);
        Account existingAccount = existingAccounts.stream()
                .filter(a -> "VIRTUAL".equals(a.getAccountKind()))
                .filter(a -> virtualSubtype.equals(a.getVirtualSubtype()))
                .filter(a -> accountType.equals(a.getAccountType()))
                .filter(a -> {
                    // POSITION账户需要匹配productId（通过账户名称或额外字段）
                    if ("POSITION".equals(virtualSubtype)) {
                        return accountName.equals(a.getAccountName());
                    }
                    return true;
                })
                .findFirst()
                .orElse(null);

        if (existingAccount != null) {
            return existingAccount;
        }

        // 创建新的虚拟账户
        Account virtualAccount = new Account();
        virtualAccount.setAccountKind("VIRTUAL");
        virtualAccount.setAccountType(accountType);
        virtualAccount.setVirtualSubtype(virtualSubtype);
        virtualAccount.setOwnerType(ownerType);
        virtualAccount.setOwnerUserId(ownerUserId);
        virtualAccount.setOwnerFamilyId(ownerFamilyId);
        virtualAccount.setAccountName(accountName);
        virtualAccount.setCurrency("CNY");
        virtualAccount.setBalance(BigDecimal.ZERO);
        virtualAccount.setReservedAmount(BigDecimal.ZERO);
        virtualAccount.setInitialBalance(BigDecimal.ZERO);
        virtualAccount.setIsActive(true);
        virtualAccount.setNote("系统自动创建的虚拟账户");

        return createAccount(virtualAccount);
    }

    /**
     * 获取或创建产品对应的持仓账户（POSITION账户）
     * 
     * 每个产品应该有一个独立的持仓账户，用于记录该产品的持仓
     * 
     * @param productId 产品ID
     * @param productName 产品名称
     * @param ownerType 归属类型：PERSONAL/FAMILY
     * @param ownerUserId 归属用户ID（个人账户），可为空
     * @param ownerFamilyId 归属家庭ID（家庭账户），可为空
     * @return 持仓账户实体
     */
    @Transactional
    public Account getOrCreatePositionAccount(Long productId, String productName,
                                             String ownerType, Long ownerUserId, Long ownerFamilyId) {
        return getOrCreateVirtualAccount("POSITION", "POSITION", ownerType, ownerUserId, ownerFamilyId, 
                                        productId, productName);
    }

    /**
     * 手工调整账户余额（仅限 REAL 账户）
     * 注意：实际系统中应同时生成 ADJUST 类型的 ledger_txn/ledger_posting 以保证审计链完整
     *
     * @param accountId 账户ID
     * @param newBalance 调整后的余额
     */
    @Transactional
    public void adjustBalance(Long accountId, BigDecimal newBalance) {
        Account account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new RuntimeException("账户不存在");
        }
        if (!"REAL".equals(account.getAccountKind())) {
            throw new RuntimeException("只有REAL账户允许调整余额");
        }
        accountMapper.updateBalance(accountId, newBalance);
        // 注意：余额调整需要生成ADJUST类型的流水，这将在LedgerService中实现
    }
}

