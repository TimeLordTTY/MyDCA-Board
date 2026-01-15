package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.mapper.LedgerPostingMapper;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.LedgerPosting;
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
    private final LedgerPostingMapper ledgerPostingMapper;

    public AccountService(AccountMapper accountMapper, LedgerPostingMapper ledgerPostingMapper) {
        this.accountMapper = accountMapper;
        this.ledgerPostingMapper = ledgerPostingMapper;
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
        
        // 如果创建的是父账户（平台账户），自动创建"待分配"子账户
        if (account.getParentAccountId() == null && 
            "REAL".equals(account.getAccountKind()) && 
            "CASH".equals(account.getAccountType())) {
            // 创建默认的"待分配"子账户
            Account defaultEnvelope = new Account();
            defaultEnvelope.setAccountCode(account.getAccountCode() + "-待分配");
            defaultEnvelope.setAccountName("待分配");
            defaultEnvelope.setAccountKind("REAL");
            defaultEnvelope.setAccountType("CASH");
            defaultEnvelope.setParentAccountId(account.getId());
            defaultEnvelope.setFundUsage("SPENDABLE");
            defaultEnvelope.setCurrency(account.getCurrency());
            defaultEnvelope.setBalance(BigDecimal.ZERO);
            defaultEnvelope.setReservedAmount(BigDecimal.ZERO);
            defaultEnvelope.setInitialBalance(BigDecimal.ZERO);
            defaultEnvelope.setIsActive(true);
            defaultEnvelope.setOwnerType(account.getOwnerType());
            defaultEnvelope.setOwnerUserId(account.getOwnerUserId());
            defaultEnvelope.setOwnerFamilyId(account.getOwnerFamilyId());
            defaultEnvelope.setNote("系统自动创建的待分配账户");
            
            // 直接调用insert，跳过校验（因为已经通过父账户校验）
            accountMapper.insert(defaultEnvelope);
        }
        
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
     * 更新账户信息
     * 
     * @param account 待更新的账户实体（必须包含id）
     * @return 更新后的账户实体
     */
    @Transactional
    public Account updateAccount(Account account) {
        if (account.getId() == null) {
            throw new RuntimeException("账户ID不能为空");
        }
        
        // 校验账户是否存在
        Account existing = accountMapper.selectById(account.getId());
        if (existing == null) {
            throw new RuntimeException("账户不存在: " + account.getId());
        }
        
        // 处理初始余额和余额的关系
        if (account.getInitialBalance() != null) {
            // 如果设置了初始余额，检查是否需要同步余额
            // 如果账户没有其他交易（balance == initial_balance），则同步余额
            if (existing.getBalance() != null && existing.getBalance().compareTo(existing.getInitialBalance()) == 0) {
                // 余额等于初始余额，说明没有其他交易，可以直接同步
                account.setBalance(account.getInitialBalance());
            } else if (account.getBalance() == null) {
                // 如果没有提供新余额，保持原余额不变
                account.setBalance(existing.getBalance());
            }
        } else if (account.getBalance() != null) {
            // 如果只提供了余额，使用余额作为初始余额
            account.setInitialBalance(account.getBalance());
        }
        
        // 信贷账户（信用卡、花呗、白条、贷款）不需要资金用途
        if (isCreditAccount(account.getAccountType())) {
            account.setFundUsage(null);
        }
        
        accountMapper.update(account);
        return accountMapper.selectById(account.getId());
    }

    /**
     * 判断是否为信贷账户
     */
    private boolean isCreditAccount(String accountType) {
        return "CREDIT_CARD".equals(accountType) || 
               "HUABEI".equals(accountType) || 
               "BAITIAO".equals(accountType) || 
               "LOAN".equals(accountType);
    }

    /**
     * 获取账户树（父子结构），并对父账户计算聚合余额（子账户之和）
     *
     * @param ownerUserId 归属用户ID（个人视图），可为空
     * @param ownerFamilyId 归属家庭ID（家庭视图），可为空
     * @return 账户列表（包含父账户及其子账户），父账户的 balance 为子账户总和
     */
    public List<Account> getAccountTree(Long ownerUserId, Long ownerFamilyId) {
        List<Account> allAccounts = accountMapper.selectByOwner(ownerUserId, ownerFamilyId);
        
        // 分离父账户和子账户，只返回父账户
        List<Account> parentAccounts = new java.util.ArrayList<>();
        for (Account account : allAccounts) {
            if (account.getParentAccountId() == null) {
                parentAccounts.add(account);
            }
        }
        
        // 构建树形结构：为每个父账户计算聚合余额并设置children属性
        for (Account parentAccount : parentAccounts) {
            // 查询子账户
            List<Account> children = accountMapper.selectChildren(parentAccount.getId());
            
            // 计算子账户余额总和
            BigDecimal totalBalance = children.stream()
                    .map(Account::getBalance)
                    .filter(b -> b != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            parentAccount.setBalance(totalBalance);
            
            // 计算子账户占用总和
            BigDecimal totalReserved = children.stream()
                    .map(Account::getReservedAmount)
                    .filter(b -> b != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            parentAccount.setReservedAmount(totalReserved);
            
            // 设置children属性（用于前端树形展示）
            parentAccount.setChildren(children);
        }
        
        return parentAccounts;
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
     * 获取账户的子账户列表
     * @param parentAccountId 父账户ID
     * @return 子账户列表
     */
    public List<Account> getAccountChildren(Long parentAccountId) {
        return accountMapper.selectChildren(parentAccountId);
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
        } else if ("RECEIVABLE".equals(virtualSubtype)) {
            accountName = "应收账户";
        } else if ("LIABILITY".equals(virtualSubtype)) {
            accountName = "负债账户";
        } else {
            accountName = virtualSubtype + "账户";
        }

        // 查询是否已存在虚拟账户
        List<Account> existingAccounts = accountMapper.selectByOwner(ownerUserId, ownerFamilyId);
        Account existingAccount = existingAccounts.stream()
                .filter(a -> "VIRTUAL".equals(a.getAccountKind()))
                .filter(a -> virtualSubtype.equals(a.getVirtualSubtype()))
                // 必须匹配 ownerType，确保个人账户使用个人虚拟账户，家庭账户使用家庭虚拟账户
                .filter(a -> ownerType.equals(a.getOwnerType()))
                // 虚拟账户的 account_type 都是 "OTHER"，所以不需要比较 accountType
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
        // 虚拟账户的 account_type 使用 "OTHER"（因为 ENUM 只包含 REAL 账户类型）
        // 真正的类型信息存储在 virtual_subtype 中
        virtualAccount.setAccountType("OTHER");
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
     * 获取或创建券商账户的持仓市值汇总账户
     * 
     * 为每个BROKER类型的平台账户创建一个虚拟叶子账户，用于汇总该账户下所有场内产品的持仓市值
     * 这个账户作为该券商账户的子账户，余额 = 该券商账户下所有场内产品的持仓市值总和
     * 
     * @param brokerAccountId 券商平台账户ID（BROKER类型的父账户）
     * @return 持仓市值汇总账户实体
     */
    @Transactional
    public Account getOrCreateBrokerPositionValueAccount(Long brokerAccountId) {
        Account brokerAccount = accountMapper.selectById(brokerAccountId);
        if (brokerAccount == null) {
            throw new RuntimeException("券商账户不存在: " + brokerAccountId);
        }
        if (!"BROKER".equals(brokerAccount.getAccountType())) {
            throw new RuntimeException("账户不是券商账户: " + brokerAccountId);
        }
        if (brokerAccount.getParentAccountId() != null) {
            throw new RuntimeException("账户不是平台账户（父账户）: " + brokerAccountId);
        }

        // 查找是否已存在持仓市值汇总账户
        // 账户代码格式：BROKER-POS-VALUE-{brokerAccountId}
        String accountCode = "BROKER-POS-VALUE-" + brokerAccountId;
        Account existing = accountMapper.selectByCode(accountCode);
        if (existing != null) {
            return existing;
        }

        // 创建新的持仓市值汇总账户
        Account positionValueAccount = new Account();
        positionValueAccount.setAccountCode(accountCode);
        positionValueAccount.setAccountName(brokerAccount.getAccountName() + "-持仓市值");
        positionValueAccount.setAccountKind("VIRTUAL");
        positionValueAccount.setAccountType("CASH"); // 使用CASH类型，表示资产
        positionValueAccount.setVirtualSubtype("POSITION_VALUE"); // 虚拟子类型：持仓市值汇总
        positionValueAccount.setOwnerType(brokerAccount.getOwnerType());
        positionValueAccount.setOwnerUserId(brokerAccount.getOwnerUserId());
        positionValueAccount.setOwnerFamilyId(brokerAccount.getOwnerFamilyId());
        positionValueAccount.setCurrency(brokerAccount.getCurrency() != null ? brokerAccount.getCurrency() : "CNY");
        positionValueAccount.setParentAccountId(brokerAccountId); // 作为券商账户的子账户
        positionValueAccount.setInitialBalance(BigDecimal.ZERO);
        positionValueAccount.setBalance(BigDecimal.ZERO);
        positionValueAccount.setReservedAmount(BigDecimal.ZERO);
        positionValueAccount.setIsActive(true);
        positionValueAccount.setNote("券商账户持仓市值汇总账户（自动创建）");

        return createAccount(positionValueAccount);
    }

    /**
     * 更新券商账户的持仓市值汇总账户余额
     * 
     * 这个方法用于更新券商账户下虚拟持仓市值汇总账户的余额
     * 余额应该等于该券商账户下所有场内产品的持仓市值总和
     * 
     * @param brokerAccountId 券商平台账户ID
     * @param totalMarketValue 持仓市值总和
     */
    @Transactional
    public void updateBrokerPositionValue(Long brokerAccountId, BigDecimal totalMarketValue) {
        Account positionValueAccount = getOrCreateBrokerPositionValueAccount(brokerAccountId);
        // 直接更新余额（虚拟账户的余额更新不需要生成流水，因为这是汇总值）
        accountMapper.updateBalance(positionValueAccount.getId(), totalMarketValue);
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

    /**
     * 重新计算账户余额（从分录中计算）
     * 
     * 这个方法会从 ledger_posting 表中重新计算指定账户的余额
     * 适用于：修复历史数据、数据迁移、余额不一致等情况
     * 
     * 计算公式：
     * - 资产类账户（CASH/POSITION/RECEIVABLE）：balance = initial_balance + Σ(DEBIT) - Σ(CREDIT)
     * - 负债类账户（LIABILITY/CREDIT_CARD/HUABEI/BAITIAO/LOAN）：balance = initial_balance - Σ(DEBIT) + Σ(CREDIT)
     * - 收入类账户（INCOME）：balance = initial_balance - Σ(DEBIT) + Σ(CREDIT)
     * - 支出类账户（EXPENSE）：balance = initial_balance + Σ(DEBIT) - Σ(CREDIT)
     * - 手续费类账户（FEE）：balance = initial_balance + Σ(DEBIT) - Σ(CREDIT)
     * 
     * @param accountId 账户ID
     * @return 重新计算后的余额
     */
    @Transactional
    public BigDecimal recalculateBalance(Long accountId) {
        Account account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new RuntimeException("账户不存在: " + accountId);
        }

        // 获取该账户的所有分录
        List<LedgerPosting> postings = ledgerPostingMapper.selectByAccountId(accountId);
        
        // 确定账户类型（虚拟账户使用 virtual_subtype，REAL账户使用 account_type）
        String accountType = "VIRTUAL".equals(account.getAccountKind()) 
            ? account.getVirtualSubtype() 
            : account.getAccountType();
        
        // 从初始余额开始计算
        BigDecimal balance = account.getInitialBalance() != null ? account.getInitialBalance() : BigDecimal.ZERO;
        
        // 根据账户类型和借贷方向计算余额
        for (LedgerPosting posting : postings) {
            if ("CASH".equals(accountType) || "POSITION".equals(accountType) || "RECEIVABLE".equals(accountType)) {
                // 资产类账户：DEBIT增加，CREDIT减少
                if ("DEBIT".equals(posting.getPostingType())) {
                    balance = balance.add(posting.getAmount());
                } else {
                    balance = balance.subtract(posting.getAmount());
                }
            } else if ("LIABILITY".equals(accountType) || accountType.contains("CREDIT") || 
                      accountType.contains("HUABEI") || accountType.contains("BAITIAO") ||
                      accountType.contains("LOAN")) {
                // 负债类账户：DEBIT减少，CREDIT增加
                if ("DEBIT".equals(posting.getPostingType())) {
                    balance = balance.subtract(posting.getAmount());
                } else {
                    balance = balance.add(posting.getAmount());
                }
            } else if ("INCOME".equals(accountType)) {
                // 收入类账户：DEBIT减少，CREDIT增加
                if ("DEBIT".equals(posting.getPostingType())) {
                    balance = balance.subtract(posting.getAmount());
                } else {
                    balance = balance.add(posting.getAmount());
                }
            } else if ("EXPENSE".equals(accountType) || "FEE".equals(accountType)) {
                // 支出类账户和手续费账户：DEBIT增加，CREDIT减少
                if ("DEBIT".equals(posting.getPostingType())) {
                    balance = balance.add(posting.getAmount());
                } else {
                    balance = balance.subtract(posting.getAmount());
                }
            }
        }
        
        // 更新账户余额
        accountMapper.updateBalance(accountId, balance);
        
        return balance;
    }

    /**
     * 重新计算所有账户的余额（从分录中计算）
     * 
     * 这个方法会遍历所有账户，从 ledger_posting 表中重新计算每个账户的余额
     * 适用于：修复历史数据、数据迁移、余额不一致等情况
     * 
     * @return 重新计算的账户数量
     */
    @Transactional
    public int recalculateAllBalances() {
        // 获取所有账户
        List<Account> allAccounts = accountMapper.selectByOwner(null, null);
        int count = 0;
        
        for (Account account : allAccounts) {
            try {
                recalculateBalance(account.getId());
                count++;
            } catch (Exception e) {
                // 记录错误但继续处理其他账户
                System.err.println("重新计算账户余额失败: " + account.getId() + ", 错误: " + e.getMessage());
            }
        }
        
        return count;
    }
}

