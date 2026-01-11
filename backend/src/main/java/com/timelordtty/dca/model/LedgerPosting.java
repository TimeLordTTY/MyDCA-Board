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
public class LedgerPosting {
    /** 分录ID，主键，自增 */
    private Long id;
    
    /** 交易ID，外键关联ledger_txn.txn_id */
    private String txnId;
    
    /** 借贷方向：DEBIT=借方，CREDIT=贷方 */
    private String postingType;
    
    /** 账户ID，外键关联accounts.id，必须是叶子账户 */
    private Long accountId;
    
    /** 账户类型：CASH/POSITION/FEE/INCOME/EXPENSE/LIABILITY/RECEIVABLE */
    private String accountType;
    
    /** 金额，永远为正数，方向由postingType决定 */
    private BigDecimal amount;
    
    /** 份额（持仓类分录），永远为正数（NULL或>=0），方向由postingType决定 */
    private BigDecimal shares;
    
    /** 货币，CNY/USD/HKD，默认CNY */
    private String currency;
    
    /** 备注，用户自定义说明 */
    private String note;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
}

