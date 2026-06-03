package com.timelordtty.dca.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 持仓快照表实体（holdings_snapshot）
 */
public class HoldingsSnapshot {
    /**
     * 主键 ID，用于数据库内部唯一定位记录。
     */
    private Long id;
    /**
     * 所属用户 ID，用于个人视角数据隔离。
     */
    private Long userId;
    /**
     * 关联产品 ID，指向基金、ETF 或其他投资产品。
     */
    private Long productId;
    /**
     * 业务日期，用于交易归属、确认或统计周期判定。
     */
    private LocalDate snapshotDate;
    /**
     * 持有份额，用于基金、ETF 或货币基金持仓计算。
     */
    private BigDecimal shares;
    /**
     * 持仓成本，金额口径由持仓计算流程决定。
     */
    private BigDecimal cost;
    /**
     * 金额类字段，用于资金、费用、盈亏或统计结果表达。
     */
    private String costMethod; // AVERAGE/FIFO
    /**
     * 单位净值，用于持仓市值和盈亏计算。
     */
    private BigDecimal nav;
    /**
     * 业务日期，用于交易归属、确认或统计周期判定。
     */
    private LocalDate navDate;
    /**
     * 持仓市值，通常等于份额乘以最新净值或价格。
     */
    private BigDecimal marketValue;
    /**
     * 未实现盈亏，用于衡量当前市值与成本的差额。
     */
    private BigDecimal unrealizedPnl;
    /**
     * 收益率，按当前持仓成本和市值口径计算。
     */
    private BigDecimal returnRate;
    /**
     * 业务日期，用于交易归属、确认或统计周期判定。
     */
    private LocalDate fetchDate;
    /**
     * 布尔标记，用于表示该记录在业务流程中的开关状态。
     */
    private Boolean isDirty;
    /**
     * 业务日期，用于交易归属、确认或统计周期判定。
     */
    private LocalDate dirtyFromDate;
    /**
     * 记录创建时间，用于审计和排序。
     */
    private LocalDateTime createdAt;
    /**
     * 记录最后更新时间，用于审计和增量同步。
     */
    private LocalDateTime updatedAt;

    /**
     * 返回主键 ID，用于数据库内部唯一定位记录。
     */
    public Long getId() { return id; }
    /**
     * 设置主键 ID，用于数据库内部唯一定位记录。
     */
    public void setId(Long id) { this.id = id; }
    /**
     * 返回所属用户 ID，用于个人视角数据隔离。
     */
    public Long getUserId() { return userId; }
    /**
     * 设置所属用户 ID，用于个人视角数据隔离。
     */
    public void setUserId(Long userId) { this.userId = userId; }
    /**
     * 返回关联产品 ID，指向基金、ETF 或其他投资产品。
     */
    public Long getProductId() { return productId; }
    /**
     * 设置关联产品 ID，指向基金、ETF 或其他投资产品。
     */
    public void setProductId(Long productId) { this.productId = productId; }
    /**
     * 返回业务日期，用于交易归属、确认或统计周期判定。
     */
    public LocalDate getSnapshotDate() { return snapshotDate; }
    /**
     * 设置业务日期，用于交易归属、确认或统计周期判定。
     */
    public void setSnapshotDate(LocalDate snapshotDate) { this.snapshotDate = snapshotDate; }
    /**
     * 返回持有份额，用于基金、ETF 或货币基金持仓计算。
     */
    public BigDecimal getShares() { return shares; }
    /**
     * 设置持有份额，用于基金、ETF 或货币基金持仓计算。
     */
    public void setShares(BigDecimal shares) { this.shares = shares; }
    /**
     * 返回持仓成本，金额口径由持仓计算流程决定。
     */
    public BigDecimal getCost() { return cost; }
    /**
     * 设置持仓成本，金额口径由持仓计算流程决定。
     */
    public void setCost(BigDecimal cost) { this.cost = cost; }
    /**
     * 返回金额类字段，用于资金、费用、盈亏或统计结果表达。
     */
    public String getCostMethod() { return costMethod; }
    /**
     * 设置金额类字段，用于资金、费用、盈亏或统计结果表达。
     */
    public void setCostMethod(String costMethod) { this.costMethod = costMethod; }
    /**
     * 返回单位净值，用于持仓市值和盈亏计算。
     */
    public BigDecimal getNav() { return nav; }
    /**
     * 设置单位净值，用于持仓市值和盈亏计算。
     */
    public void setNav(BigDecimal nav) { this.nav = nav; }
    /**
     * 返回业务日期，用于交易归属、确认或统计周期判定。
     */
    public LocalDate getNavDate() { return navDate; }
    /**
     * 设置业务日期，用于交易归属、确认或统计周期判定。
     */
    public void setNavDate(LocalDate navDate) { this.navDate = navDate; }
    /**
     * 返回持仓市值，通常等于份额乘以最新净值或价格。
     */
    public BigDecimal getMarketValue() { return marketValue; }
    /**
     * 设置持仓市值，通常等于份额乘以最新净值或价格。
     */
    public void setMarketValue(BigDecimal marketValue) { this.marketValue = marketValue; }
    /**
     * 返回未实现盈亏，用于衡量当前市值与成本的差额。
     */
    public BigDecimal getUnrealizedPnl() { return unrealizedPnl; }
    /**
     * 设置未实现盈亏，用于衡量当前市值与成本的差额。
     */
    public void setUnrealizedPnl(BigDecimal unrealizedPnl) { this.unrealizedPnl = unrealizedPnl; }
    /**
     * 返回收益率，按当前持仓成本和市值口径计算。
     */
    public BigDecimal getReturnRate() { return returnRate; }
    /**
     * 设置收益率，按当前持仓成本和市值口径计算。
     */
    public void setReturnRate(BigDecimal returnRate) { this.returnRate = returnRate; }
    /**
     * 返回业务日期，用于交易归属、确认或统计周期判定。
     */
    public LocalDate getFetchDate() { return fetchDate; }
    /**
     * 设置业务日期，用于交易归属、确认或统计周期判定。
     */
    public void setFetchDate(LocalDate fetchDate) { this.fetchDate = fetchDate; }
    /**
     * 返回布尔标记，用于表示该记录在业务流程中的开关状态。
     */
    public Boolean getIsDirty() { return isDirty; }
    /**
     * 设置布尔标记，用于表示该记录在业务流程中的开关状态。
     */
    public void setIsDirty(Boolean isDirty) { this.isDirty = isDirty; }
    /**
     * 返回业务日期，用于交易归属、确认或统计周期判定。
     */
    public LocalDate getDirtyFromDate() { return dirtyFromDate; }
    /**
     * 设置业务日期，用于交易归属、确认或统计周期判定。
     */
    public void setDirtyFromDate(LocalDate dirtyFromDate) { this.dirtyFromDate = dirtyFromDate; }
    /**
     * 返回记录创建时间，用于审计和排序。
     */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /**
     * 设置记录创建时间，用于审计和排序。
     */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    /**
     * 返回记录最后更新时间，用于审计和增量同步。
     */
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    /**
     * 设置记录最后更新时间，用于审计和增量同步。
     */
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

