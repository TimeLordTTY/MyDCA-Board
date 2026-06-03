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
public class FundSellFeeTier {
    /** 费率分段ID，主键，自增 */
    private Long id;
    
    /** 产品ID（外键product_master.id） */
    private Long productId;
    
    /** 最小持有天数（包含，如0表示持有0天及以上） */
    private Integer minDays;
    
    /** 最大持有天数（不包含，如7表示持有7天以下，NULL表示无上限） */
    private Integer maxDays;
    
    /** 卖出费率（如0.0015表示0.15%） */
    private BigDecimal sellFeeRate;
    
    /** 排序顺序（数字越小越靠前，用于确定分段优先级） */
    private Integer sortOrder;
    
    /** 是否启用，true=启用，false=禁用，默认true */
    private Boolean isActive;
    
    /** 备注（如"持有0-7天"） */
    private String note;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间，自动更新 */
    private LocalDateTime updatedAt;
}
