package com.timelordtty.dca.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单表实体（orders表）
 * 
 * 对应数据库表：orders
 * 
 * 设计说明：订单记录"意图"，结算确认记录"结果"
 * - 订单代表用户的交易意图（下单信息），订单与结算确认分离
 * - 订单状态机：PENDING → CONFIRMED/CANCELLED/FAILED
 * - 实际发生资产/资金变动以结算确认（SettlementConfirm）和 ledger_txn 为准
 * 
 * 字段说明：
 * - id: 订单ID，主键，自增
 * - orderId: 系统生成的订单ID（业务单号），格式：ORD-YYYYMMDD-6位随机字符，唯一标识
 * - userId: 发起用户ID，外键关联users表
 * - productId: 目标产品ID，外键关联product_master表
 * - orderType: 订单类型：BUY=买入（场内买入），SELL=卖出（场内卖出），SUBSCRIPTION=申购（场外基金买入），REDEMPTION=赎回（场外基金卖出）
 * - amount: 下单金额（买入/申购时使用），针对现金类下单
 * - shares: 下单份额（卖出/赎回时使用），针对按份额申购/赎回
 * - requestedAt: 发起时间，用户下单的时间
 * - tradeDate: 交易归属日，由requestedAt+cutoff+交易日历推导
 * - expectedNavDate: 预期净值日期，预计用哪天的净值计算，用于计算预期份额/金额
 * - expectedConfirmDate: 预期确认日期，预计哪天确认到账，计算公式：trade_date + T+N，N由产品配置决定
 * - status: 订单状态：PENDING=待确认（等待结算确认），CONFIRMED=已确认（订单已确认），CANCELLED=已取消（订单被取消），FAILED=失败（订单失败）
 * - feeEstimate: 预估手续费，预计的手续费
 * - note: 备注，用户自定义说明
 * - createdAt: 创建时间
 * - updatedAt: 更新时间，自动更新
 * 
 * 业务规则：
 * 1. 下单时通常会锁定账户的 reserved_amount（占用资金），直到结算确认或取消时释放
 * 2. 订单只是交易意图，实际发生资产/资金变动以结算确认（SettlementConfirm）和 ledger_txn 为准
 * 3. 支持组合支付：订单可以由多个账户共同出资（通过order_funding_line表记录）
 * 4. 待结算清单：查询status='PENDING'的订单，按expectedConfirmDate排序
 * 
 * 组合支付说明：
 * - 订单的资金来源通过order_funding_line表记录
 * - 创建订单时：写入order_funding_line记录，分别增加各account.reserved_amount
 * - 取消订单时：逐条释放各account.reserved_amount，删除order_funding_line记录
 * - 确认结算时：按order_funding_line生成多条CASH CREDIT分录，并清零对应account.reserved_amount
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Data
public class Order {
    /** 订单ID，主键，自增 */
    private Long id;
    
    /** 系统生成的订单ID（业务单号），格式：ORD-YYYYMMDD-6位随机字符，唯一标识 */
    private String orderId;
    
    /** 发起用户ID，外键关联users表 */
    private Long userId;
    
    /** 目标产品ID，外键关联product_master表 */
    private Long productId;
    
    /** 订单类型：BUY=买入（场内买入），SELL=卖出（场内卖出），SUBSCRIPTION=申购（场外基金买入），REDEMPTION=赎回（场外基金卖出） */
    private String orderType;
    
    /** 下单金额（买入/申购时使用），针对现金类下单 */
    private BigDecimal amount;
    
    /** 下单份额（卖出/赎回时使用），针对按份额申购/赎回 */
    private BigDecimal shares;
    
    /** 发起时间，用户下单的时间 */
    private LocalDateTime requestedAt;
    
    /** 交易归属日，由requestedAt+cutoff+交易日历推导 */
    private LocalDate tradeDate;
    
    /** 预期净值日期，预计用哪天的净值计算，用于计算预期份额/金额 */
    private LocalDate expectedNavDate;
    
    /** 预期确认日期，预计哪天确认到账，计算公式：trade_date + T+N，N由产品配置决定 */
    private LocalDate expectedConfirmDate;
    
    /** 订单状态：PENDING=待确认，CONFIRMED=已确认，CANCELLED=已取消，FAILED=失败 */
    private String status;
    
    /** 预估手续费，预计的手续费 */
    private BigDecimal feeEstimate;
    
    /** 备注，用户自定义说明 */
    private String note;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间，自动更新 */
    private LocalDateTime updatedAt;
}
