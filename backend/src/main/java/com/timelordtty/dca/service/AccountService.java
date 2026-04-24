package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.mapper.LedgerPostingMapper;
import com.timelordtty.dca.mapper.ProductMasterMapper;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.LedgerPosting;
import com.timelordtty.dca.model.Nav;
import com.timelordtty.dca.model.ProductMaster;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
    private final ProductMasterMapper productMasterMapper;
    private final NavService navService;
    private final LedgerService ledgerService;
    private final UserService userService;

    public AccountService(AccountMapper accountMapper, LedgerPostingMapper ledgerPostingMapper,
                         ProductMasterMapper productMasterMapper, NavService navService,
                         @Lazy LedgerService ledgerService, UserService userService) {
        this.accountMapper = accountMapper;
        this.ledgerPostingMapper = ledgerPostingMapper;
        this.productMasterMapper = productMasterMapper;
        this.navService = navService;
        this.ledgerService = ledgerService;
        this.userService = userService;
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
        //
        // 重要：余额初始化只允许通过 initialBalance 控制。
        // - 新建账户时（account.id 为空）忽略入参里的 balance，避免“表单残留/误传 balance”
        //   导致新建账户出现非预期余额（看起来像“从别的平台同步过来”）。
        if (account.getId() == null) {
            BigDecimal init = account.getInitialBalance();
            account.setBalance(init != null ? init : BigDecimal.ZERO);
        } else if (account.getBalance() == null) {
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
        // 设置 is_fixed_amount 默认值（数据库字段不允许为null）
        if (account.getIsFixedAmount() == null) {
            account.setIsFixedAmount(false);
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
            defaultEnvelope.setIsFixedAmount(false); // 设置默认值
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
     * 规则包括：
     * - VIRTUAL 不允许 parent
     * - 子账户必须为 REAL
     * - 父账户可以是 BANK/PAYMENT/BROKER/CASH 等平台容器，子账户类型可独立选择（如支付宝下的花呗、余额宝等）
     */
    private void validateAccountCreation(Account account) {
        // 券商平台下允许的虚拟子账户类型（用于持仓隔离/汇总）
        boolean isAllowedBrokerVirtualChild =
            "VIRTUAL".equals(account.getAccountKind())
                && account.getParentAccountId() != null
                && ("POSITION".equals(account.getVirtualSubtype()) || "POSITION_VALUE".equals(account.getVirtualSubtype()));

        boolean parentIsBrokerPlatform = false;
        if (isAllowedBrokerVirtualChild) {
            Account parent = accountMapper.selectById(account.getParentAccountId());
            parentIsBrokerPlatform = parent != null
                && "BROKER".equals(parent.getAccountType())
                && parent.getParentAccountId() == null;
        }

        // 规则1: 默认情况下 VIRTUAL 账户不允许设置 parent_account_id
        // 但“券商维度持仓账户”（VIRTUAL + virtual_subtype=POSITION 且 parent 为 BROKER 平台）是特例，允许挂载在券商平台下。
        if ("VIRTUAL".equals(account.getAccountKind()) && account.getParentAccountId() != null) {
            if (!(isAllowedBrokerVirtualChild && parentIsBrokerPlatform)) {
                throw new RuntimeException("VIRTUAL账户不允许设置父账户");
            }
        }

        // 规则2: 子账户必须是REAL
        if (account.getParentAccountId() != null && !"REAL".equals(account.getAccountKind())) {
            // 放行：券商平台下的持仓类虚拟子账户
            if (!(isAllowedBrokerVirtualChild && parentIsBrokerPlatform)) {
                throw new RuntimeException("子账户必须是REAL类型");
            }
        }
        // 不再限制子账户 accountType 必须为 CASH：
        // - 允许在同一平台下挂载 CASH/MMF/BANK/PAYMENT 等资产账户
        // - 也允许挂载 CREDIT_CARD/HUABEI/BAITIAO/LOAN 等信贷账户，后续在统计时单独作为负债处理
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
     * 批量查询账户
     * @param accountIds 账户ID列表
     * @return 账户Map，key为accountId，value为Account实体
     */
    public java.util.Map<Long, Account> getAccountsByIds(List<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return new java.util.HashMap<>();
        }
        List<Account> accounts = accountMapper.selectByIds(accountIds);
        java.util.Map<Long, Account> accountMap = new java.util.HashMap<>();
        for (Account account : accounts) {
            accountMap.put(account.getId(), account);
        }
        return accountMap;
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
            BigDecimal existingInitial = existing.getInitialBalance();
            BigDecimal existingBalance = existing.getBalance();
            
            // 判断账户是否还没有发生过交易（初始状态）
            // 条件：1) 原初始余额为 null 或 0  2) 原余额等于原初始余额
            boolean isInitialState = (existingInitial == null || existingInitial.compareTo(BigDecimal.ZERO) == 0)
                || (existingBalance != null && existingInitial != null && existingBalance.compareTo(existingInitial) == 0);
            
            if (isInitialState) {
                // 账户处于初始状态，设置初始余额时同步余额
                account.setBalance(account.getInitialBalance());
            } else if (account.getBalance() == null) {
                // 如果没有提供新余额，保持原余额不变
                account.setBalance(existingBalance);
            }
        } else if (account.getBalance() != null) {
            // 如果只提供了余额，使用余额作为初始余额
            account.setInitialBalance(account.getBalance());
        }
        
        // 信贷账户（信用卡、花呗、白条、贷款）不需要资金用途
        if (isCreditAccount(account.getAccountType())) {
            account.setFundUsage(null);
        }
        
        // MMF 账户的初始份额同步到余额
        // 当 MMF 账户设置 initialShares 时，需要计算并同步 balance = initialShares * NAV
        if ("MMF".equals(account.getAccountType()) || "MMF".equals(existing.getAccountType())) {
            BigDecimal initialShares = account.getInitialShares();
            if (initialShares == null) {
                initialShares = existing.getInitialShares();
            }
            Long linkedProductId = account.getLinkedProductId();
            if (linkedProductId == null) {
                linkedProductId = existing.getLinkedProductId();
            }
            
            if (initialShares != null && initialShares.compareTo(BigDecimal.ZERO) > 0) {
                // 获取 NAV，货币基金 NAV 通常为 1.0000 或略大
                BigDecimal nav = BigDecimal.ONE;
                if (linkedProductId != null) {
                    // 尝试获取产品最新净值
                    Nav latestNav = navService.getLatestNav(linkedProductId);
                    if (latestNav != null && latestNav.getNav() != null) {
                        nav = latestNav.getNav();
                    }
                }
                
                // 计算余额 = 份额 * 净值
                BigDecimal calculatedBalance = initialShares.multiply(nav).setScale(2, java.math.RoundingMode.HALF_UP);
                
                // 只有当现有余额为0或null时才同步，避免覆盖已有交易后的余额
                if (existing.getBalance() == null || existing.getBalance().compareTo(BigDecimal.ZERO) == 0) {
                    account.setBalance(calculatedBalance);
                    account.setInitialBalance(calculatedBalance);
                    System.out.println(String.format("MMF账户[%d]同步余额: initialShares=%s, NAV=%s, balance=%s", 
                        account.getId(), initialShares, nav, calculatedBalance));
                }
            }
        }
        
        // 如果账户关联了产品且设置了初始余额，自动生成初始持仓
        if (account.getLinkedProductId() != null && account.getInitialBalance() != null 
            && account.getInitialBalance().compareTo(BigDecimal.ZERO) > 0) {
            syncInitialHoldingFromAccount(account.getId(), account.getLinkedProductId(), account.getInitialBalance());
        }
        
        accountMapper.update(account);
        return accountMapper.selectById(account.getId());
    }

    /**
     * 为关联产品的账户同步初始持仓
     * 
     * 流程：
     * 1. 获取产品信息
     * 2. 获取最新净值（或指定日期的净值）
     * 3. 计算份额 = 初始余额 / 净值
     * 4. 生成初始持仓分录：POSITION DEBIT + CASH CREDIT（从账户扣款）
     * 
     * @param accountId 账户ID
     * @param productId 产品ID
     * @param initialBalance 初始余额
     */
    @Transactional
    private void syncInitialHoldingFromAccount(Long accountId, Long productId, BigDecimal initialBalance) {
        try {
            Account account = accountMapper.selectById(accountId);
            if (account == null) {
                return;
            }
            
            ProductMaster product = productMasterMapper.selectById(productId);
            if (product == null) {
                return;
            }
            
            // 获取最新净值
            Nav latestNav = navService.getLatestNav(productId);
            if (latestNav == null || latestNav.getNav() == null || latestNav.getNav().compareTo(BigDecimal.ZERO) <= 0) {
                // 如果没有净值，跳过（可能是新产品，后续有净值后再同步）
                return;
            }
            
            BigDecimal nav = latestNav.getNav();
            BigDecimal shares = initialBalance.divide(nav, 6, RoundingMode.HALF_UP);
            
            // 检查是否已有初始持仓（避免重复生成）
            // 通过查询该账户是否有对应的持仓分录来判断
            List<LedgerPosting> existingPostings = ledgerPostingMapper.selectByAccountId(accountId);
            boolean hasHolding = existingPostings.stream()
                .anyMatch(p -> "POSITION".equals(p.getAccountType()) && "DEBIT".equals(p.getPostingType()));
            
            if (hasHolding) {
                // 已有持仓，跳过（避免重复生成）
                return;
            }
            
            // 获取或创建持仓账户
            Account positionAccount = getOrCreatePositionAccount(
                productId, product.getProductName(),
                account.getOwnerType(), account.getOwnerUserId(), account.getOwnerFamilyId()
            );
            
            // 生成初始持仓分录
            List<LedgerPosting> postings = new ArrayList<>();
            
            // POSITION DEBIT：增加持仓
            LedgerPosting positionDebit = new LedgerPosting();
            positionDebit.setAccountId(positionAccount.getId());
            positionDebit.setAccountType("POSITION");
            positionDebit.setPostingType("DEBIT");
            positionDebit.setAmount(initialBalance); // 成本金额
            positionDebit.setShares(shares);
            postings.add(positionDebit);
            
            // CASH CREDIT：从账户扣款
            LedgerPosting cashCredit = new LedgerPosting();
            cashCredit.setAccountId(accountId);
            cashCredit.setAccountType(account.getAccountType());
            cashCredit.setPostingType("CREDIT");
            cashCredit.setAmount(initialBalance);
            postings.add(cashCredit);
            
            // 获取用户信息
            AuthResponse.UserInfo currentUser = userService.getCurrentUser();
            
            // 创建交易记录
            ledgerService.createTransaction(
                currentUser.getId(),
                currentUser.getFamilyId() != null ? currentUser.getFamilyId() : null,
                "ADJUST", // 使用ADJUST类型，表示初始持仓调整
                null,
                postings,
                String.format("账户[%s]初始持仓同步，产品[%s]，金额=%s，份额=%s", 
                    account.getAccountName(), product.getProductName(), initialBalance, shares)
            );
        } catch (Exception e) {
            // 记录错误但不影响账户更新
            System.err.println("同步初始持仓失败: " + e.getMessage());
            e.printStackTrace();
        }
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
            // UI 账户树只展示 REAL 子账户；系统自动创建的 VIRTUAL（如券商维度持仓账户）不应暴露给用户
            children = children.stream()
                    .filter(c -> "REAL".equals(c.getAccountKind()))
                    .collect(java.util.stream.Collectors.toList());
            
            // 计算子账户余额总和（排除信贷账户）
            // 信贷账户（CREDIT_CARD/HUABEI/BAITIAO/LOAN）不计入余额，因为它们的余额是欠款，不是资产
            BigDecimal totalBalance = children.stream()
                    .filter(child -> {
                        String accountType = child.getAccountType();
                        return accountType != null && 
                               !"CREDIT_CARD".equals(accountType) && 
                               !"HUABEI".equals(accountType) && 
                               !"BAITIAO".equals(accountType) && 
                               !"LOAN".equals(accountType);
                    })
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
     * 查询绑定指定产品的账户列表
     * @param productId 产品ID
     * @param ownerUserId 用户ID，可为空
     * @param ownerFamilyId 家庭ID，可为空
     * @return 绑定该产品的账户列表
     */
    public List<Account> getAccountsByLinkedProduct(Long productId, Long ownerUserId, Long ownerFamilyId) {
        return accountMapper.selectByLinkedProduct(productId, ownerUserId, ownerFamilyId);
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
        // 先用稳定的 accountCode 做幂等（尤其是 POSITION：同一产品应复用同一账户）
        // 这样即便历史上存在重复账户，也能保证后续不再继续新增
        String ownerKey = "PERSONAL".equals(ownerType) ? String.valueOf(ownerUserId) : String.valueOf(ownerFamilyId);
        String accountCode;
        if ("POSITION".equals(virtualSubtype)) {
            accountCode = String.format("VIRTUAL-POSITION-%s-%s-%s", ownerType, ownerKey, String.valueOf(productId));
        } else {
            accountCode = String.format("VIRTUAL-%s-%s-%s", virtualSubtype, ownerType, ownerKey);
        }

        Account byCode = accountMapper.selectByCode(accountCode);
        if (byCode != null) {
            return byCode;
        }

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
        // 注意：必须查 VIRTUAL 账户；selectByOwner 只查 REAL，会导致永远找不到从而重复创建
        List<Account> existingAccounts = accountMapper.selectVirtualAccountsByOwner(ownerUserId, ownerFamilyId, virtualSubtype);
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
        virtualAccount.setAccountCode(accountCode);
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
        virtualAccount.setIsFixedAmount(false); // 设置默认值
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
     * 获取或创建“券商维度”的产品持仓账户（POSITION账户）。
     *
     * 目标：同一产品在不同券商账户下应当拥有各自独立的持仓成本与份额。
     *
     * 账户约定：
     * - account_kind = VIRTUAL
     * - virtual_subtype = POSITION
     * - parent_account_id = brokerAccountId（券商平台账户，BROKER 类型父账户）
     * - linked_product_id = productId
     * - account_code = BROKER-POS-{brokerAccountId}-{productId}（唯一）
     */
    @Transactional
    public Account getOrCreateBrokerPositionAccount(Long brokerAccountId,
                                                    Long productId,
                                                    String productName,
                                                    String ownerType,
                                                    Long ownerUserId,
                                                    Long ownerFamilyId) {
        if (brokerAccountId == null) {
            throw new RuntimeException("brokerAccountId 不能为空");
        }
        if (productId == null) {
            throw new RuntimeException("productId 不能为空");
        }

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

        String accountCode = "BROKER-POS-" + brokerAccountId + "-" + productId;
        Account existing = accountMapper.selectByCode(accountCode);
        if (existing != null) {
            return existing;
        }

        String resolvedProductName = productName != null ? productName : ("产品" + productId);
        Account positionAccount = new Account();
        positionAccount.setAccountCode(accountCode);
        positionAccount.setAccountName(brokerAccount.getAccountName() + "-持仓账户-" + resolvedProductName);
        positionAccount.setAccountKind("VIRTUAL");
        positionAccount.setAccountType("OTHER"); // 虚拟账户 account_type 统一用 OTHER，真实类型在 virtual_subtype
        positionAccount.setVirtualSubtype("POSITION");
        positionAccount.setOwnerType(ownerType != null ? ownerType : brokerAccount.getOwnerType());
        positionAccount.setOwnerUserId(ownerUserId != null ? ownerUserId : brokerAccount.getOwnerUserId());
        positionAccount.setOwnerFamilyId(ownerFamilyId != null ? ownerFamilyId : brokerAccount.getOwnerFamilyId());
        positionAccount.setCurrency(brokerAccount.getCurrency() != null ? brokerAccount.getCurrency() : "CNY");
        positionAccount.setParentAccountId(brokerAccountId);
        positionAccount.setLinkedProductId(productId);
        positionAccount.setInitialBalance(BigDecimal.ZERO);
        positionAccount.setBalance(BigDecimal.ZERO);
        positionAccount.setReservedAmount(BigDecimal.ZERO);
        positionAccount.setIsActive(true);
        positionAccount.setIsFixedAmount(false);
        positionAccount.setNote("券商维度持仓账户（自动创建）");

        return createAccount(positionAccount);
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
        positionValueAccount.setIsFixedAmount(false); // 设置默认值
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
        
        // 确定账户类型
        // 虚拟账户使用 virtual_subtype，REAL账户优先使用 account_type，为空则使用 fund_usage
        String accountType;
        if ("VIRTUAL".equals(account.getAccountKind())) {
            accountType = account.getVirtualSubtype();
        } else {
            accountType = account.getAccountType();
            if (accountType == null || accountType.isEmpty()) {
                accountType = account.getFundUsage();
            }
        }
        
        // 从初始余额开始计算
        BigDecimal balance = account.getInitialBalance() != null ? account.getInitialBalance() : BigDecimal.ZERO;
        
        // 根据账户类型和借贷方向计算余额
        for (LedgerPosting posting : postings) {
            if ("CASH".equals(accountType) || "POSITION".equals(accountType) || "RECEIVABLE".equals(accountType) ||
                "MMF".equals(accountType) || "BANK_WM_NAV".equals(accountType) || "BANK_WM_BOX".equals(accountType) ||
                "ETF".equals(accountType) || "LOF".equals(accountType) || "FUND".equals(accountType) ||
                "STOCK".equals(accountType) || "BOND".equals(accountType) || "OPTION".equals(accountType) ||
                "BROKER".equals(accountType) || "INVESTABLE".equals(accountType) || "SPENDABLE".equals(accountType) || "RESERVED".equals(accountType) ||
                "PAYMENT".equals(accountType) || "BANK".equals(accountType) || "OTHER".equals(accountType)) {
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

    /**
     * 按净值更新关联产品的账户子账户余额
     * 
     * 对于关联了产品的账户（linked_product_id不为空），根据最新净值更新子账户余额
     * 余额计算公式：子账户余额 = 子账户份额 × 最新净值
     * 子账户份额 = 父账户initial_shares × (子账户当前余额 / 子账户总余额)
     * 
     * 执行时机：每日净值更新后（如18:00后）
     * 
     * @return 更新的账户数量
     */
    @Transactional
    public int updateLinkedAccountBalancesByNav() {
        // 查找所有关联了产品的账户
        List<Account> linkedAccounts = accountMapper.selectAllLinkedAccounts();
        int updateCount = 0;
        
        for (Account linkedAccount : linkedAccounts) {
            if (linkedAccount.getLinkedProductId() == null || 
                linkedAccount.getInitialShares() == null ||
                linkedAccount.getInitialShares().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            
            // 获取产品最新净值
            Nav latestNav = navService.getLatestNav(linkedAccount.getLinkedProductId());
            if (latestNav == null || latestNav.getNav() == null || 
                latestNav.getNav().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            
            BigDecimal nav = latestNav.getNav();
            BigDecimal totalShares = linkedAccount.getInitialShares();
            
            // 获取子账户
            List<Account> children = accountMapper.selectChildren(linkedAccount.getId());
            if (children.isEmpty()) {
                // 没有子账户，更新父账户余额
                BigDecimal newBalance = totalShares.multiply(nav).setScale(2, RoundingMode.HALF_UP);
                accountMapper.updateBalance(linkedAccount.getId(), newBalance);
                updateCount++;
            } else {
                // 计算子账户总余额（用于按比例分配份额）
                BigDecimal totalBalance = children.stream()
                    .filter(c -> c.getBalance() != null && c.getBalance().compareTo(BigDecimal.ZERO) > 0)
                    .map(Account::getBalance)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                if (totalBalance.compareTo(BigDecimal.ZERO) > 0) {
                    // 按比例分配份额并更新余额
                    for (Account child : children) {
                        BigDecimal childBalance = child.getBalance();
                        if (childBalance == null || childBalance.compareTo(BigDecimal.ZERO) <= 0) {
                            continue;
                        }
                        
                        // 子账户份额 = 子账户金额 / 总金额 × 总份额
                        BigDecimal childShares = childBalance
                            .divide(totalBalance, 10, RoundingMode.HALF_UP)
                            .multiply(totalShares);
                        
                        // 新余额 = 子账户份额 × 最新净值
                        BigDecimal newBalance = childShares.multiply(nav).setScale(2, RoundingMode.HALF_UP);
                        accountMapper.updateBalance(child.getId(), newBalance);
                        updateCount++;
                    }
                } else {
                    // 如果所有子账户余额都为0，按份额平均分配
                    BigDecimal sharesPerChild = totalShares.divide(
                        new BigDecimal(children.size()), 6, RoundingMode.HALF_UP);
                    for (Account child : children) {
                        BigDecimal newBalance = sharesPerChild.multiply(nav).setScale(2, RoundingMode.HALF_UP);
                        accountMapper.updateBalance(child.getId(), newBalance);
                        updateCount++;
                    }
                }
            }
        }
        
        return updateCount;
    }
}

