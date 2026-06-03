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
public class Account {
    /** 账户ID，主键，自增 */
    private Long id;
    
    /** 账户代码，唯一标识，格式如：ACC-20240101-001 */
    private String accountCode;
    
    /** 账户名称，用户自定义名称，如"华宝证券-房租子账户" */
    private String accountName;
    
    /** 账户性质：REAL=现实账户，VIRTUAL=虚拟科目 */
    private String accountKind;
    
    /** 账户类型：BANK/PAYMENT/BROKER/MMF/CASH/CREDIT_CARD/HUABEI/BAITIAO/LOAN/OTHER */
    private String accountType;
    
    /** 账户子类型，用于信贷账户等特殊场景 */
    private String accountSubtype;
    
    /** 虚拟科目子类型（仅VIRTUAL使用）：POSITION/FEE/INCOME/EXPENSE/RECEIVABLE/LIABILITY */
    private String virtualSubtype;
    
    /** 归属类型：PERSONAL=个人，FAMILY=家庭 */
    private String ownerType;
    
    /** 归属用户ID（个人账户），外键关联users表 */
    private Long ownerUserId;
    
    /** 归属家庭ID（家庭账户），外键关联families表 */
    private Long ownerFamilyId;
    
    /** 货币：CNY/USD/HKD，默认CNY */
    private String currency;
    
    /** 父账户ID，用于现实账户的资金分区/子账户，外键关联accounts表 */
    private Long parentAccountId;
    
    /**
     * 关联产品ID（可选）
     *
     * 使用场景：
     * - 稳利宝、小荷包等账户需要与具体理财/基金产品绑定，便于初始化持仓和后续对账
     * - 为空表示该账户不直接绑定产品
     */
    private Long linkedProductId;
    
    /**
     * 初始份额（仅MMF平台账户使用）
     * 
     * 用于货币基金类型平台的份额管理：
     * - 总金额 = 初始份额 × 最新净值
     * - 子账户可分配份额
     */
    private BigDecimal initialShares;
    
    /**
     * 是否固定金额子账户（仅MMF子账户使用）
     * 
     * 如房租预备金，金额固定为4000，不随净值变化
     */
    private Boolean isFixedAmount;
    
    /**
     * 固定金额值（仅is_fixed_amount=true时有效）
     */
    private BigDecimal fixedAmount;
    
    /** 资金用途（仅对REAL CASH叶子账户生效）：SPENDABLE/RESERVED/INVESTABLE */
    private String fundUsage;
    
    /** 账面余额，由流水推导：initial_balance + Σ(DEBIT) - Σ(CREDIT)（资产类账户） */
    private BigDecimal balance;
    
    /** 占用/冻结金额，下单时暂时冻结的资金还未实际扣款，结算确认或取消时释放 */
    private BigDecimal reservedAmount;
    
    /** 初始余额，账户创建时的余额 */
    private BigDecimal initialBalance;
    
    /** 是否启用，true=启用，false=禁用（软删除），默认true */
    private Boolean isActive;
    
    /** 备注，用户自定义说明 */
    private String note;
    
    /** 创建时间，记录账户创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间，记录最后修改时间，自动更新 */
    private LocalDateTime updatedAt;
    
    /** 子账户列表（用于树形结构展示），非数据库字段 */
    private List<Account> children;
}

