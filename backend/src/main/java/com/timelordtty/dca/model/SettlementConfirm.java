package com.timelordtty.dca.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 结算确认表实体（settlement_confirm表）
 * 
 * 对应数据库表：settlement_confirm
 * 
 * 设计说明：记录订单最终结算结果（到账金额/份额/手续费/确认日期等），是订单流水到会计分录的桥梁
 * - 订单记录"意图"，结算确认记录"结果"
 * - 结算确认后，更新订单状态为 CONFIRMED，并生成对应的 ledger_txn 和 ledger_posting
 * - 支持人工覆盖 confirm_date、nav_date、confirm_nav 等字段（以用户核对结果为准）
 * 
 * 字段说明：
 * - id: 结算确认ID，主键，自增
 * - orderId: 关联订单ID，外键关联orders.order_id，唯一约束（一个订单只能有一个结算确认）
 * - confirmDate: 实际确认日期，实际到账的日期，可人工覆盖
 * - confirmDatetime: 实际确认时间，实际到账的时间，精确到秒
 * - navDate: 实际使用的净值日期，实际计算份额时用的净值日期，可人工覆盖
 * - confirmNav: 实际确认净值，实际使用的净值，可人工覆盖
 * - confirmShares: 实际确认份额，实际到账的份额，买入/申购时使用
 * - confirmAmount: 实际确认金额，实际到账的金额，卖出/赎回时使用
 * - confirmFee: 实际手续费，实际支付的手续费
 * - isManualOverride: 是否人工覆盖，用户是否手动修正了系统计算的值，用于标记用户手动修正
 * - confirmedByUserId: 确认人用户ID，操作人
 * - confirmedAt: 确认时间，操作时间
 * - note: 备注，用户自定义说明
 * - createdAt: 创建时间
 * - updatedAt: 更新时间，自动更新
 * 
 * 业务规则：
 * 1. 结算确认后应生成对应的 ledger_txn 与 ledger_posting 分录，反映实际资金与份额变动
 * 2. 对于特殊产品（如逆回购），确认流程可能只调整 reserved_amount 并生成利息分录
 * 3. 支持手动覆盖（isManualOverride=true），允许用户修改confirmNav、confirmShares等
 * 4. 买入/申购时：使用confirmShares（份额）和confirmNav（净值）计算成本
 * 5. 卖出/赎回时：使用confirmAmount（金额）和confirmNav（净值）计算份额
 * 
 * 逆回购订单特殊处理：
 * - 下单处理：创建REPO订单（PENDING），仅增加source_account.reserved_amount（锁定本金），不生成ledger_txn和ledger_posting
 * - 到期处理：确认订单（CONFIRMED），必须按顺序：1) 校验订单到期；2) 释放占用（accounts.reserved_amount -= principal）；3) 生成利息ledger_txn（CASH DEBIT(interest) + INCOME CREDIT(interest)，记到同一个发起子账户）
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Data
public class SettlementConfirm {
    /** 结算确认ID，主键，自增 */
    private Long id;
    
    /** 关联订单ID，外键关联orders.order_id，唯一约束 */
    private String orderId;
    
    /** 实际确认日期，实际到账的日期，可人工覆盖 */
    private LocalDate confirmDate;
    
    /** 实际确认时间，实际到账的时间，精确到秒 */
    private LocalDateTime confirmDatetime;
    
    /** 实际使用的净值日期，实际计算份额时用的净值日期，可人工覆盖 */
    private LocalDate navDate;
    
    /** 实际确认净值，实际使用的净值，可人工覆盖 */
    private BigDecimal confirmNav;
    
    /** 实际确认份额，实际到账的份额，买入/申购时使用 */
    private BigDecimal confirmShares;
    
    /** 实际确认金额，实际到账的金额，卖出/赎回时使用 */
    private BigDecimal confirmAmount;
    
    /** 实际手续费，实际支付的手续费 */
    private BigDecimal confirmFee;
    
    /** 是否人工覆盖，用户是否手动修正了系统计算的值，用于标记用户手动修正 */
    private Boolean isManualOverride;
    
    /** 确认人用户ID，操作人 */
    private Long confirmedByUserId;
    
    /** 确认时间，操作时间 */
    private LocalDateTime confirmedAt;
    
    /** 备注，用户自定义说明 */
    private String note;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间，自动更新 */
    private LocalDateTime updatedAt;
}
