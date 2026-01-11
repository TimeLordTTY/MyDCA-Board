package com.timelordtty.dca.model;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交易事件表实体（ledger_txn表）
 * 
 * 对应数据库表：ledger_txn
 * 
 * 字段说明：
 * - id: 主键，自增
 * - txnId: 交易唯一标识，格式如：TXN-16位大写字母数字
 * - userId: 用户ID，交易发起人
 * - familyId: 家庭ID，可为空（个人交易）
 * - txnType: 交易类型：BUY/SELL/SUBSCRIPTION/REDEMPTION/DIVIDEND_CASH/DIVIDEND_REINVEST/FEE/TAX/TRANSFER_OUT/TRANSFER_IN/EXPENSE/INCOME/ADJUST/REIMBURSE_IN/REIMBURSE_OUT/DEFER
 * - bizGroupKey: 业务分组键，用于关联同一笔业务的多笔交易（如转账的转出和转入），可为空（默认使用txnId）
 * - productId: 产品ID，关联产品主数据，可为空（非产品相关交易）
 * - orderId: 关联订单ID，关联orders.order_id，可为空（非订单相关交易）
 * - relatedTxnId: 关联的原交易txn_id（退款/报销/撤销等，指向原交易），可为空
 * - relatedOrderId: 关联的原订单号（可选，用于订单级退款/撤单），可为空
 * - relationType: 关联类型：NONE=无关联，TRANSFER_PAIR=转账成对，REFUND=退款，REIMBURSE=报销，REVERSAL=撤销
 * - requestedAt: 请求时间，交易发起时间
 * - tradeDate: 交易归属日，由requestedAt+cutoff+交易日历推导
 * - navDate: 净值日期，净值类产品使用的净值日期
 * - confirmDate: 确认日期，交易确认到账的日期
 * - fetchDate: 采集日，用于看板"今日资产"的日期
 * - status: 交易状态：PENDING=待确认，CONFIRMED=已确认，CANCELLED=已取消，REVERSED=已撤销
 * - note: 备注，用户自定义说明
 * - isReversed: 是否已撤销，true=已撤销，false=未撤销
 * - reversedByTxnId: 撤销此交易的交易ID，指向撤销交易
 * - createdAt: 创建时间
 * - updatedAt: 更新时间，自动更新
 * 
 * 交易关联关系说明：
 * 1. 转账关联：使用bizGroupKey关联，同一笔转账的转出和转入交易共享相同的bizGroupKey
 * 2. 退款关联：使用relatedTxnId关联，退款交易指向原消费交易，relationType='REFUND'
 * 3. 报销关联：使用relatedTxnId关联，报销交易指向原消费交易，relationType='REIMBURSE'
 * 4. 撤销关联：使用relatedTxnId关联，撤销交易指向被撤销的交易，relationType='REVERSAL'
 * 
 * 应用层约束：
 * 1. related_txn_id不能等于txn_id（防止自引用）
 * 2. relation_type='REFUND'或'REIMBURSE'时，related_txn_id必须非空
 * 3. relation_type='TRANSFER_PAIR'时，使用biz_group_key关联（保持现有逻辑）
 * 4. relation_type='REVERSAL'时，related_txn_id指向被撤销的交易
 * 
 * 查询示例（计算退款总额、报销总额、剩余金额）：
 * - refunded_total: SELECT SUM(amount) FROM ledger_posting WHERE txn_id IN (SELECT txn_id FROM ledger_txn WHERE related_txn_id = ? AND relation_type = 'REFUND')
 * - reimbursed_total: SELECT SUM(amount) FROM ledger_posting WHERE txn_id IN (SELECT txn_id FROM ledger_txn WHERE related_txn_id = ? AND relation_type = 'REIMBURSE')
 * - remaining: 原交易金额 - refunded_total - reimbursed_total
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Data
public class LedgerTxn {
    /** 主键，自增 */
    private Long id;
    
    /** 交易唯一标识，格式如：TXN-16位大写字母数字 */
    private String txnId;
    
    /** 用户ID，交易发起人 */
    private Long userId;
    
    /** 家庭ID，可为空（个人交易） */
    private Long familyId;
    
    /** 交易类型：BUY/SELL/SUBSCRIPTION/REDEMPTION/DIVIDEND_CASH/DIVIDEND_REINVEST/FEE/TAX/TRANSFER_OUT/TRANSFER_IN/EXPENSE/INCOME/ADJUST/REIMBURSE_IN/REIMBURSE_OUT/DEFER */
    private String txnType;
    
    /** 业务分组键，用于关联同一笔业务的多笔交易（如转账的转出和转入），可为空（默认使用txnId） */
    private String bizGroupKey;
    
    /** 产品ID，关联产品主数据，可为空（非产品相关交易） */
    private Long productId;
    
    /** 关联订单ID，关联orders.order_id，可为空（非订单相关交易） */
    private String orderId;
    
    /** 关联的原交易txn_id（退款/报销/撤销等，指向原交易），可为空 */
    private String relatedTxnId;
    
    /** 关联的原订单号（可选，用于订单级退款/撤单），可为空 */
    private String relatedOrderId;
    
    /** 关联类型：NONE=无关联，TRANSFER_PAIR=转账成对，REFUND=退款，REIMBURSE=报销，REVERSAL=撤销 */
    private String relationType;
    
    /** 请求时间，交易发起时间 */
    private LocalDateTime requestedAt;
    
    /** 交易归属日，由requestedAt+cutoff+交易日历推导 */
    private LocalDate tradeDate;
    
    /** 净值日期，净值类产品使用的净值日期 */
    private LocalDate navDate;
    
    /** 确认日期，交易确认到账的日期 */
    private LocalDate confirmDate;
    
    /** 采集日，用于看板"今日资产"的日期 */
    private LocalDate fetchDate;
    
    /** 交易状态：PENDING=待确认，CONFIRMED=已确认，CANCELLED=已取消，REVERSED=已撤销 */
    private String status;
    
    /** 备注，用户自定义说明 */
    private String note;
    
    /** 是否已撤销，true=已撤销，false=未撤销 */
    private Boolean isReversed;
    
    /** 撤销此交易的交易ID，指向撤销交易 */
    private String reversedByTxnId;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间，自动更新 */
    private LocalDateTime updatedAt;
}


