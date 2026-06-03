package com.timelordtty.dca.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单资金来源拆分表实体（order_funding_line表）
 * 
 * 对应数据库表：order_funding_line
 * 
 * 设计目标：支持订单由多个账户共同出资（组合支付），在PENDING状态下准确跟踪各来源账户的预占用，
 * 确保取消/确认结算时能正确释放/扣减。
 * 
 * 字段说明：
 * - id: 资金来源行ID，主键，自增
 * - orderId: 订单ID，外键关联orders.order_id，ON DELETE CASCADE（订单删除时自动删除资金来源行）
 * - lineNo: 行号，同一订单内从1开始递增，与orderId组成唯一约束
 * - accountId: 资金来源账户ID，外键关联accounts.id，必须是叶子账户（应用层校验）
 * - amount: 出资金额，该账户为此订单出资的金额
 * - currency: 货币，CNY/USD/HKD，默认CNY
 * - createdAt: 创建时间
 * - updatedAt: 更新时间，自动更新
 * 
 * 业务规则：
 * 1. 每个订单可以有多个资金来源行（line_no从1开始递增）
 * 2. account_id必须是叶子账户（应用层校验，禁止父账户）
 * 3. 创建订单时：写入order_funding_line记录，并分别增加各account.reserved_amount
 * 4. 取消订单时：逐条释放各account.reserved_amount，删除order_funding_line记录（CASCADE自动删除）
 * 5. 确认结算时：按order_funding_line生成多条CASH CREDIT分录，并清零对应account.reserved_amount
 * 6. 组合支付总额必须等于订单金额：Σ(order_funding_line.amount) = orders.amount（应用层校验）
 * 7. 每个资金来源账户的可用余额必须足够：account.balance - account.reserved_amount >= funding_line.amount（应用层校验）
 * 
 * 组合支付示例：
 * 场景：用户买入ETF 5000元，使用生活费账户3000元 + 理财金账户2000元
 * - order_funding_line:
 *   - line_no=1: account_id=生活费账户, amount=3000.00
 *   - line_no=2: account_id=理财金账户, amount=2000.00
 * - accounts:
 *   - 生活费账户.reserved_amount += 3000.00
 *   - 理财金账户.reserved_amount += 2000.00
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Data
/**
 * 业务注释规范化: OrderFundingLine 实体模型，对应后端数据库中的核心业务记录。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class OrderFundingLine {
    /** 资金来源行ID，主键，自增 */
    /**
     * 业务注释规范化: 主键 ID，用于在后端内部唯一定位该业务记录。
     */
    private Long id;
    
    /** 订单ID，外键关联orders.order_id，ON DELETE CASCADE */
    /**
     * 业务注释规范化: 关联订单 ID，用于串联订单创建、资金冻结、结算确认和流水入账。
     */
    private String orderId;
    
    /** 行号，同一订单内从1开始递增，与orderId组成唯一约束 */
    /**
     * 业务注释规范化: lineNo 业务字段，承载该对象在后端流程中的核心属性。
     */
    private Integer lineNo;
    
    /** 资金来源账户ID，外键关联accounts.id，必须是叶子账户 */
    /**
     * 业务注释规范化: 关联账户 ID，指向承载资金、持仓或虚拟科目的账户。
     */
    private Long accountId;
    
    /** 出资金额，该账户为此订单出资的金额 */
    /**
     * 业务注释规范化: 业务金额，通常以账户币种计价，正负含义由交易类型和分录方向决定。
     */
    private BigDecimal amount;
    
    /** 卖出份额（卖出/赎回时使用，买入/申购时为NULL） */
    /**
     * 业务注释规范化: 产品份额，适用于基金、ETF、货币基金等按份额管理的资产。
     */
    private BigDecimal shares;
    
    /** 货币，CNY/USD/HKD，默认CNY */
    /**
     * 业务注释规范化: currency 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String currency;
    
    /** 行类型：SOURCE=出金来源（买入扣款/卖出份额来源），TARGET=到账目标（卖出/赎回资金到账账户） */
    /**
     * 业务注释规范化: lineType 类型字段，用于区分不同业务分类并驱动处理分支。
     */
    private String lineType;
    
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
