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
/**
 * 业务注释规范化: LedgerTxn 实体模型，对应后端数据库中的核心业务记录。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class LedgerTxn {
    /** 主键，自增 */
    /**
     * 业务注释规范化: 主键 ID，用于在后端内部唯一定位该业务记录。
     */
    private Long id;
    
    /** 交易唯一标识，格式如：TXN-16位大写字母数字 */
    /**
     * 业务注释规范化: 流水业务 ID，用于聚合一笔复式记账交易下的所有分录。
     */
    private String txnId;
    
    /** 用户ID，交易发起人 */
    /**
     * 业务注释规范化: 所属用户 ID，用于限定个人数据权限和查询范围。
     */
    private Long userId;
    
    /** 家庭ID，可为空（个人交易） */
    /**
     * 业务注释规范化: 所属家庭 ID，用于家庭视角下的数据隔离。
     */
    private Long familyId;
    
    /** 交易类型：BUY/SELL/SUBSCRIPTION/REDEMPTION/DIVIDEND_CASH/DIVIDEND_REINVEST/FEE/TAX/TRANSFER_OUT/TRANSFER_IN/EXPENSE/INCOME/ADJUST/REIMBURSE_IN/REIMBURSE_OUT/DEFER */
    /**
     * 业务注释规范化: 流水类型，决定该笔交易在收入、支出、投资、转账等统计口径中的归类。
     */
    private String txnType;
    
    /** 业务分组键，用于关联同一笔业务的多笔交易（如转账的转出和转入），可为空（默认使用txnId） */
    /**
     * 业务注释规范化: bizGroupKey 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String bizGroupKey;
    
    /** 产品ID，关联产品主数据，可为空（非产品相关交易） */
    /**
     * 业务注释规范化: 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     */
    private Long productId;
    
    /** 关联订单ID，关联orders.order_id，可为空（非订单相关交易） */
    /**
     * 业务注释规范化: 关联订单 ID，用于串联订单创建、资金冻结、结算确认和流水入账。
     */
    private String orderId;
    
    /** 关联的原交易txn_id（退款/报销/撤销等，指向原交易），可为空 */
    /**
     * 业务注释规范化: relatedTxnId 关联 ID，用于连接对应业务对象并保持数据引用关系。
     */
    private String relatedTxnId;
    
    /** 关联的原订单号（可选，用于订单级退款/撤单），可为空 */
    /**
     * 业务注释规范化: relatedOrderId 关联 ID，用于连接对应业务对象并保持数据引用关系。
     */
    private String relatedOrderId;
    
    /** 关联类型：NONE=无关联，TRANSFER_PAIR=转账成对，REFUND=退款，REIMBURSE=报销，REVERSAL=撤销 */
    /**
     * 业务注释规范化: relationType 类型字段，用于区分不同业务分类并驱动处理分支。
     */
    private String relationType;
    
    /** 请求时间，交易发起时间 */
    /**
     * 业务注释规范化: requestedAt 时间字段，用于记录业务动作发生或审计时间。
     */
    private LocalDateTime requestedAt;
    
    /** 交易归属日，由requestedAt+cutoff+交易日历推导 */
    /**
     * 业务注释规范化: tradeDate 日期字段，用于交易、确认、净值或统计周期口径。
     */
    private LocalDate tradeDate;
    
    /** 净值日期，净值类产品使用的净值日期 */
    /**
     * 业务注释规范化: navDate 日期字段，用于交易、确认、净值或统计周期口径。
     */
    private LocalDate navDate;
    
    /** 确认日期，交易确认到账的日期 */
    /**
     * 业务注释规范化: confirmDate 日期字段，用于交易、确认、净值或统计周期口径。
     */
    private LocalDate confirmDate;
    
    /** 采集日，用于看板"今日资产"的日期 */
    /**
     * 业务注释规范化: fetchDate 日期字段，用于交易、确认、净值或统计周期口径。
     */
    private LocalDate fetchDate;
    
    /** 交易状态：PENDING=待确认，CONFIRMED=已确认，CANCELLED=已取消，REVERSED=已撤销 */
    /**
     * 业务注释规范化: 业务状态，表示记录当前所处的创建、确认、取消或完成阶段。
     */
    private String status;
    
    /** 备注，用户自定义说明 */
    /**
     * 业务注释规范化: note 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String note;
    
    /** 分类ID（外键categories.id，用于收入/支出分类） */
    /**
     * 业务注释规范化: categoryId 关联 ID，用于连接对应业务对象并保持数据引用关系。
     */
    private Long categoryId;
    
    /** 是否可报销（仅用于EXPENSE类型交易） */
    /**
     * 业务注释规范化: isReimbursable 布尔标记，用于控制该记录在业务流程中的特殊状态。
     */
    private Boolean isReimbursable;
    
    /** 是否已报销（仅用于EXPENSE类型交易） */
    /**
     * 业务注释规范化: isReimbursed 布尔标记，用于控制该记录在业务流程中的特殊状态。
     */
    private Boolean isReimbursed;
    
    /** 是否已撤销，true=已撤销，false=未撤销 */
    /**
     * 业务注释规范化: isReversed 布尔标记，用于控制该记录在业务流程中的特殊状态。
     */
    private Boolean isReversed;
    
    /** 撤销此交易的交易ID，指向撤销交易 */
    /**
     * 业务注释规范化: reversedByTxnId 关联 ID，用于连接对应业务对象并保持数据引用关系。
     */
    private String reversedByTxnId;
    
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


