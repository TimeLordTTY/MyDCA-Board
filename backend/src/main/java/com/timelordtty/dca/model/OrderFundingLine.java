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
public class OrderFundingLine {
    /** 资金来源行ID，主键，自增 */
    private Long id;
    
    /** 订单ID，外键关联orders.order_id，ON DELETE CASCADE */
    private String orderId;
    
    /** 行号，同一订单内从1开始递增，与orderId组成唯一约束 */
    private Integer lineNo;
    
    /** 资金来源账户ID，外键关联accounts.id，必须是叶子账户 */
    private Long accountId;
    
    /** 出资金额，该账户为此订单出资的金额 */
    private BigDecimal amount;
    
    /** 货币，CNY/USD/HKD，默认CNY */
    private String currency;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间，自动更新 */
    private LocalDateTime updatedAt;
}
