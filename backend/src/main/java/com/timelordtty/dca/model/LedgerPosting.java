package com.timelordtty.dca.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分录表实体（ledger_posting表）
 * 
 * 对应数据库表：ledger_posting
 * 
 * 字段说明：
 * - id: 分录ID，主键，自增
 * - txnId: 交易ID，外键关联ledger_txn.txn_id
 * - postingType: 借贷方向：DEBIT=借方，CREDIT=贷方
 * - accountId: 账户ID，外键关联accounts.id，必须是叶子账户（应用层校验）
 * - accountType: 账户类型：CASH/POSITION/FEE/INCOME/EXPENSE/LIABILITY/RECEIVABLE
 * - amount: 金额，永远为正数，方向由postingType决定
 * - shares: 份额（持仓类分录），永远为正数（NULL或>=0），方向由postingType决定
 * - currency: 货币，CNY/USD/HKD，默认CNY
 * - note: 备注，用户自定义说明
 * - createdAt: 创建时间
 * 
 * 业务规则：
 * 1. amount和shares永远为正数，方向由postingType决定
 * 2. accountId必须是叶子账户（禁止对父账户记账）
 * 3. 每笔交易必须至少包含2个分录（1个DEBIT + 1个CREDIT），且借贷金额必须相等
 * 4. 持仓类分录（accountType=POSITION）需要shares字段
 * 
 * 借贷方向规则：
 * - 资产类账户（CASH/POSITION/RECEIVABLE）：DEBIT增加余额，CREDIT减少余额
 * - 负债类账户（LIABILITY）：DEBIT减少余额，CREDIT增加余额
 * - 收入类账户（INCOME）：DEBIT减少余额，CREDIT增加余额
 * - 支出类账户（EXPENSE/FEE）：DEBIT增加余额，CREDIT减少余额
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Data
/**
 * 业务注释规范化: LedgerPosting 实体模型，对应后端数据库中的核心业务记录。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class LedgerPosting {
    /** 分录ID，主键，自增 */
    /**
     * 业务注释规范化: 主键 ID，用于在后端内部唯一定位该业务记录。
     */
    private Long id;
    
    /** 交易ID，外键关联ledger_txn.txn_id */
    /**
     * 业务注释规范化: 流水业务 ID，用于聚合一笔复式记账交易下的所有分录。
     */
    private String txnId;
    
    /** 借贷方向：DEBIT=借方，CREDIT=贷方 */
    /**
     * 业务注释规范化: 分录方向，DEBIT/CREDIT 决定账户余额在复式记账中的增减方向。
     */
    private String postingType;
    
    /** 账户ID，外键关联accounts.id，必须是叶子账户 */
    /**
     * 业务注释规范化: 关联账户 ID，指向承载资金、持仓或虚拟科目的账户。
     */
    private Long accountId;
    
    /** 账户类型：CASH/POSITION/FEE/INCOME/EXPENSE/LIABILITY/RECEIVABLE */
    /**
     * 业务注释规范化: accountType 类型字段，用于区分不同业务分类并驱动处理分支。
     */
    private String accountType;
    
    /** 金额，永远为正数，方向由postingType决定 */
    /**
     * 业务注释规范化: 业务金额，通常以账户币种计价，正负含义由交易类型和分录方向决定。
     */
    private BigDecimal amount;
    
    /** 份额（持仓类分录），永远为正数（NULL或>=0），方向由postingType决定 */
    /**
     * 业务注释规范化: 产品份额，适用于基金、ETF、货币基金等按份额管理的资产。
     */
    private BigDecimal shares;
    
    /** 货币，CNY/USD/HKD，默认CNY */
    /**
     * 业务注释规范化: currency 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String currency;
    
    /** 备注，用户自定义说明 */
    /**
     * 业务注释规范化: note 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String note;
    
    /** 该分录发生后的账户余额（用于显示历史余额） */
    /**
     * 业务注释规范化: accountBalanceAfter 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    private BigDecimal accountBalanceAfter;
    
    /** 该分录发生后的父账户余额（用于显示历史余额） */
    /**
     * 业务注释规范化: parentAccountBalanceAfter 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    private BigDecimal parentAccountBalanceAfter;
    
    /** 创建时间 */
    /**
     * 业务注释规范化: 记录创建时间，用于审计和排序。
     */
    private LocalDateTime createdAt;
}

