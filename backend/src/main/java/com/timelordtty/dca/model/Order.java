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
/**
 * 业务注释规范化: Order 实体模型，对应后端数据库中的核心业务记录。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class Order {
    /** 订单ID，主键，自增 */
    /**
     * 业务注释规范化: 主键 ID，用于在后端内部唯一定位该业务记录。
     */
    private Long id;
    
    /** 系统生成的订单ID（业务单号），格式：ORD-YYYYMMDD-6位随机字符，唯一标识 */
    /**
     * 业务注释规范化: 关联订单 ID，用于串联订单创建、资金冻结、结算确认和流水入账。
     */
    private String orderId;
    
    /** 发起用户ID，外键关联users表 */
    /**
     * 业务注释规范化: 所属用户 ID，用于限定个人数据权限和查询范围。
     */
    private Long userId;
    
    /** 目标产品ID，外键关联product_master表 */
    /**
     * 业务注释规范化: 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     */
    private Long productId;
    
    /** 订单类型：BUY=买入（场内买入），SELL=卖出（场内卖出），SUBSCRIPTION=申购（场外基金买入），REDEMPTION=赎回（场外基金卖出） */
    /**
     * 业务注释规范化: orderType 类型字段，用于区分不同业务分类并驱动处理分支。
     */
    private String orderType;
    
    /** 下单金额（买入/申购时使用），针对现金类下单 */
    /**
     * 业务注释规范化: 业务金额，通常以账户币种计价，正负含义由交易类型和分录方向决定。
     */
    private BigDecimal amount;
    
    /** 下单份额（卖出/赎回时使用），针对按份额申购/赎回 */
    /**
     * 业务注释规范化: 产品份额，适用于基金、ETF、货币基金等按份额管理的资产。
     */
    private BigDecimal shares;
    
    /** 发起时间，用户下单的时间 */
    /**
     * 业务注释规范化: requestedAt 时间字段，用于记录业务动作发生或审计时间。
     */
    private LocalDateTime requestedAt;
    
    /** 交易归属日，由requestedAt+cutoff+交易日历推导 */
    /**
     * 业务注释规范化: tradeDate 日期字段，用于交易、确认、净值或统计周期口径。
     */
    private LocalDate tradeDate;
    
    /** 预期净值日期，预计用哪天的净值计算，用于计算预期份额/金额 */
    /**
     * 业务注释规范化: expectedNavDate 日期字段，用于交易、确认、净值或统计周期口径。
     */
    private LocalDate expectedNavDate;
    
    /** 预期确认日期，预计哪天确认到账，计算公式：trade_date + T+N，N由产品配置决定 */
    /**
     * 业务注释规范化: expectedConfirmDate 日期字段，用于交易、确认、净值或统计周期口径。
     */
    private LocalDate expectedConfirmDate;
    
    /** 订单状态：PENDING=待确认，CONFIRMED=已确认，CANCELLED=已取消，FAILED=失败 */
    /**
     * 业务注释规范化: 业务状态，表示记录当前所处的创建、确认、取消或完成阶段。
     */
    private String status;
    
    /** 预估手续费，预计的手续费 */
    /**
     * 业务注释规范化: feeEstimate 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    private BigDecimal feeEstimate;
    
    /** 备注，用户自定义说明 */
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
