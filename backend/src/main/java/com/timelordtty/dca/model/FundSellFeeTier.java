package com.timelordtty.dca.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 场外基金卖出费率分段表实体（fund_sell_fee_tier表）
 * 
 * 对应数据库表：fund_sell_fee_tier
 * 
 * 设计说明：存储场外基金的卖出费率分段配置，按持有天数分段
 * 
 * 费率分段规则：
 * - 持有天数使用左闭右开区间（如0-7表示[0, 7)，7-30表示[7, 30)）
 * - 最后一个分段可以使用NULL表示"以上"（如180以上）
 * - 按sort_order排序，数字越小越靠前
 * 
 * 示例：
 * - 持有0-7天：费率1.5%
 * - 持有7-30天：费率0.75%
 * - 持有30-180天：费率0.5%
 * - 持有180天以上：费率0%
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Data
/**
 * 业务注释规范化: FundSellFeeTier 实体模型，对应后端数据库中的核心业务记录。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class FundSellFeeTier {
    /** 费率分段ID，主键，自增 */
    /**
     * 业务注释规范化: 主键 ID，用于在后端内部唯一定位该业务记录。
     */
    private Long id;
    
    /** 产品ID（外键product_master.id） */
    /**
     * 业务注释规范化: 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     */
    private Long productId;
    
    /** 最小持有天数（包含，如0表示持有0天及以上） */
    /**
     * 业务注释规范化: minDays 业务字段，承载该对象在后端流程中的核心属性。
     */
    private Integer minDays;
    
    /** 最大持有天数（不包含，如7表示持有7天以下，NULL表示无上限） */
    /**
     * 业务注释规范化: maxDays 业务字段，承载该对象在后端流程中的核心属性。
     */
    private Integer maxDays;
    
    /** 卖出费率（如0.0015表示0.15%） */
    /**
     * 业务注释规范化: sellFeeRate 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    private BigDecimal sellFeeRate;
    
    /** 排序顺序（数字越小越靠前，用于确定分段优先级） */
    /**
     * 业务注释规范化: sortOrder 业务字段，承载该对象在后端流程中的核心属性。
     */
    private Integer sortOrder;
    
    /** 是否启用，true=启用，false=禁用，默认true */
    /**
     * 业务注释规范化: isActive 布尔标记，用于控制该记录在业务流程中的特殊状态。
     */
    private Boolean isActive;
    
    /** 备注（如"持有0-7天"） */
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
