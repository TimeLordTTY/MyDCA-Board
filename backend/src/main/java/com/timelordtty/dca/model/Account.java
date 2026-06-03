package com.timelordtty.dca.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 账户表实体（accounts表）
 * 
 * 对应数据库表：accounts
 * 
 * 账户体系说明：
 * 1. 账户性质（account_kind）：
 *    - REAL：现实账户，真实存在的账户如银行卡、支付宝、券商账户等，允许手工余额调整
 *    - VIRTUAL：虚拟科目，用于记账的虚拟账户如费用、收入、持仓账户等，禁止手工改余额，只能通过分录更新
 * 
 * 2. 账户类型（account_type）：
 *    - BANK：银行账户
 *    - PAYMENT：支付账户（支付宝、微信等）
 *    - BROKER：券商账户（股票账户）
 *    - MMF：货币基金账户
 *    - CASH：现金账户
 *    - CREDIT_CARD：信用卡
 *    - HUABEI：花呗
 *    - BAITIAO：白条
 *    - LOAN：贷款
 *    - OTHER：其他
 * 
 * 3. 父子账户关系（parent_account_id）：
 *    - 父账户：平台容器/分组节点，用于组织管理，不参与任何记账分录，balance字段不作为真实余额来源
 *    - 子账户：真实信封余额，参与记账分录，是记账的最小单位
 *    - 规则：只有REAL类型的账户允许形成父子层级
 *    - 父账户展示余额 = Σ(子账户叶子余额)（仅展示层聚合）
 *    - ledger_posting.account_id 只允许引用叶子账户，父账户禁止出现在任何记账分录中
 * 
 * 4. 资金用途（fund_usage，仅对REAL CASH叶子账户生效）：
 *    - SPENDABLE：可支出，允许日常支出/生活消费
 *    - RESERVED：专款，房租/项目/安全金等，禁止日常支出和默认禁止投资（但允许逆回购）
 *    - INVESTABLE：可投资，可用于投资如ETF/逆回购等，默认不用于日常支出
 * 
 * 5. 余额计算：
 *    - balance：账面余额 = initial_balance + Σ(DEBIT金额) - Σ(CREDIT金额)（资产类账户）
 *    - reserved_amount：占用金额（下单时增加，结算确认或取消时减少）
 *    - available_for_trade：可用购买力 = balance - reserved_amount
 * 
 * 业务规则：
 * 1. VIRTUAL账户不允许设置parent_account_id
 * 2. 子账户必须是REAL
 * 3. REAL类型的账户允许形成父子层级（用于资金分区/信封系统）
 * 4. ledger_posting.account_id只允许引用叶子账户（禁止对父账户记账）
 * 5. REAL账户允许手工余额调整（需生成ADJUST流水）
 * 6. VIRTUAL账户禁止手工改余额，只能通过分录更新
 * 7. 父账户不参与任何记账分录，禁止余额编辑
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Data
/**
 * 业务注释规范化: Account 实体模型，对应后端数据库中的核心业务记录。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class Account {
    /** 账户ID，主键，自增 */
    /**
     * 业务注释规范化: 主键 ID，用于在后端内部唯一定位该业务记录。
     */
    private Long id;
    
    /** 账户代码，唯一标识，格式如：ACC-20240101-001 */
    /**
     * 业务注释规范化: accountCode 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String accountCode;
    
    /** 账户名称，用户自定义名称，如"华宝证券-房租子账户" */
    /**
     * 业务注释规范化: accountName 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String accountName;
    
    /** 账户性质：REAL=现实账户，VIRTUAL=虚拟科目 */
    /**
     * 业务注释规范化: accountKind 类型字段，用于区分不同业务分类并驱动处理分支。
     */
    private String accountKind;
    
    /** 账户类型：BANK/PAYMENT/BROKER/MMF/CASH/CREDIT_CARD/HUABEI/BAITIAO/LOAN/OTHER */
    /**
     * 业务注释规范化: accountType 类型字段，用于区分不同业务分类并驱动处理分支。
     */
    private String accountType;
    
    /** 账户子类型，用于信贷账户等特殊场景 */
    /**
     * 业务注释规范化: accountSubtype 类型字段，用于区分不同业务分类并驱动处理分支。
     */
    private String accountSubtype;
    
    /** 虚拟科目子类型（仅VIRTUAL使用）：POSITION/FEE/INCOME/EXPENSE/RECEIVABLE/LIABILITY */
    /**
     * 业务注释规范化: virtualSubtype 类型字段，用于区分不同业务分类并驱动处理分支。
     */
    private String virtualSubtype;
    
    /** 归属类型：PERSONAL=个人，FAMILY=家庭 */
    /**
     * 业务注释规范化: ownerType 类型字段，用于区分不同业务分类并驱动处理分支。
     */
    private String ownerType;
    
    /** 归属用户ID（个人账户），外键关联users表 */
    /**
     * 业务注释规范化: ownerUserId 关联 ID，用于连接对应业务对象并保持数据引用关系。
     */
    private Long ownerUserId;
    
    /** 归属家庭ID（家庭账户），外键关联families表 */
    /**
     * 业务注释规范化: ownerFamilyId 关联 ID，用于连接对应业务对象并保持数据引用关系。
     */
    private Long ownerFamilyId;
    
    /** 货币：CNY/USD/HKD，默认CNY */
    /**
     * 业务注释规范化: currency 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String currency;
    
    /** 父账户ID，用于现实账户的资金分区/子账户，外键关联accounts表 */
    /**
     * 业务注释规范化: 父级账户 ID，用于表达平台账户与资金分区的层级关系。
     */
    private Long parentAccountId;
    
    /**
     * 关联产品ID（可选）
     *
     * 使用场景：
     * - 稳利宝、小荷包等账户需要与具体理财/基金产品绑定，便于初始化持仓和后续对账
     * - 为空表示该账户不直接绑定产品
     */
    /**
     * 业务注释规范化: linkedProductId 关联 ID，用于连接对应业务对象并保持数据引用关系。
     */
    private Long linkedProductId;
    
    /**
     * 初始份额（仅MMF平台账户使用）
     * 
     * 用于货币基金类型平台的份额管理：
     * - 总金额 = 初始份额 × 最新净值
     * - 子账户可分配份额
     */
    /**
     * 业务注释规范化: initialShares 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal initialShares;
    
    /**
     * 是否固定金额子账户（仅MMF子账户使用）
     * 
     * 如房租预备金，金额固定为4000，不随净值变化
     */
    /**
     * 业务注释规范化: isFixedAmount 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    private Boolean isFixedAmount;
    
    /**
     * 固定金额值（仅is_fixed_amount=true时有效）
     */
    /**
     * 业务注释规范化: fixedAmount 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    private BigDecimal fixedAmount;
    
    /** 资金用途（仅对REAL CASH叶子账户生效）：SPENDABLE/RESERVED/INVESTABLE */
    /**
     * 业务注释规范化: fundUsage 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String fundUsage;
    
    /** 账面余额，由流水推导：initial_balance + Σ(DEBIT) - Σ(CREDIT)（资产类账户） */
    /**
     * 业务注释规范化: 账面余额，由初始余额和已确认分录推导，不应绕过记账规则直接修改。
     */
    private BigDecimal balance;
    
    /** 占用/冻结金额，下单时暂时冻结的资金还未实际扣款，结算确认或取消时释放 */
    /**
     * 业务注释规范化: 冻结或占用金额，通常来源于未结算订单，结算或取消时释放。
     */
    private BigDecimal reservedAmount;
    
    /** 初始余额，账户创建时的余额 */
    /**
     * 业务注释规范化: initialBalance 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    private BigDecimal initialBalance;
    
    /** 是否启用，true=启用，false=禁用（软删除），默认true */
    /**
     * 业务注释规范化: isActive 布尔标记，用于控制该记录在业务流程中的特殊状态。
     */
    private Boolean isActive;
    
    /** 备注，用户自定义说明 */
    /**
     * 业务注释规范化: note 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String note;
    
    /** 创建时间，记录账户创建时间 */
    /**
     * 业务注释规范化: 记录创建时间，用于审计和排序。
     */
    private LocalDateTime createdAt;
    
    /** 更新时间，记录最后修改时间，自动更新 */
    /**
     * 业务注释规范化: 记录最后更新时间，用于审计和增量同步。
     */
    private LocalDateTime updatedAt;
    
    /** 子账户列表（用于树形结构展示），非数据库字段 */
    /**
     * 业务注释规范化: children 业务字段，承载该对象在后端流程中的核心属性。
     */
    private List<Account> children;
}

