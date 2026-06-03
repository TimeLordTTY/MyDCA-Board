package com.timelordtty.dca.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 券商费率配置表实体（broker_fee_config表）
 * 
 * 对应数据库表：broker_fee_config
 * 
 * 设计说明：存储券商账户的费率配置，费率与券商账户绑定，而不是与产品绑定
 * 
 * 费率规则类型（fee_rule_type）：
 * - STOCK: A股
 * - ETF: ETF
 * - LOF: LOF场内交易
 * - LOF_SUBSCRIPTION: LOF场内申购（支持折扣率）
 * - CONVERTIBLE_BOND_SH: 上海可转债
 * - CONVERTIBLE_BOND_SZ: 深圳可转债
 * - BOND_REPO: 逆回购
 * - FUND_OTC: 场外基金
 * - DEFAULT: 默认规则
 * 
 * 费率计算逻辑：
 * - 买入手续费 = max(交易金额 × buy_fee_rate, buy_min_fee)
 * - 卖出手续费 = max(交易金额 × sell_fee_rate, sell_min_fee)
 * - LOF场内申购：手续费 = max(交易金额 × 产品申购费率 × subscription_discount_rate, buy_min_fee)
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Data
/**
 * 业务注释规范化: BrokerFeeConfig 实体模型，对应后端数据库中的核心业务记录。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class BrokerFeeConfig {
    /** 费率配置ID，主键，自增 */
    /**
     * 业务注释规范化: 主键 ID，用于在后端内部唯一定位该业务记录。
     */
    private Long id;
    
    /** 券商账户ID（外键accounts.id，account_type必须为BROKER） */
    /**
     * 业务注释规范化: 关联账户 ID，指向承载资金、持仓或虚拟科目的账户。
     */
    private Long accountId;
    
    /** 费率规则类型：STOCK/ETF/LOF/LOF_SUBSCRIPTION/CONVERTIBLE_BOND_SH/CONVERTIBLE_BOND_SZ/BOND_REPO/FUND_OTC/DEFAULT */
    /**
     * 业务注释规范化: feeRuleType 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    private String feeRuleType;
    
    /** 买入费率（如0.0001154表示万1.154） */
    /**
     * 业务注释规范化: buyFeeRate 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    private BigDecimal buyFeeRate;
    
    /** 卖出费率 */
    /**
     * 业务注释规范化: sellFeeRate 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    private BigDecimal sellFeeRate;
    
    /** 买入最低手续费（起收金额，如2.00表示2元起收） */
    /**
     * 业务注释规范化: buyMinFee 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    private BigDecimal buyMinFee;
    
    /** 卖出最低手续费 */
    /**
     * 业务注释规范化: sellMinFee 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    private BigDecimal sellMinFee;
    
    /** 申购折扣率（如0.1表示一折，仅用于LOF_SUBSCRIPTION） */
    /**
     * 业务注释规范化: subscriptionDiscountRate 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal subscriptionDiscountRate;
    
    /** 是否启用，true=启用，false=禁用，默认true */
    /**
     * 业务注释规范化: isActive 布尔标记，用于控制该记录在业务流程中的特殊状态。
     */
    private Boolean isActive;
    
    /** 备注 */
    /**
     * 业务注释规范化: note 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String note;
    
    /** 创建时间 */
    /**
     * 业务注释规范化: 记录创建时间，用于审计和排序。
     */
    private LocalDateTime createdAt;
    
    /** 更新时间，自动更新 */
    /**
     * 业务注释规范化: 记录最后更新时间，用于审计和增量同步。
     */
    private LocalDateTime updatedAt;
}
