package com.timelordtty.dca.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 净资产快照表实体（net_worth_snapshot）
 */
public class NetWorthSnapshot {
    /**
     * 主键 ID，用于数据库内部唯一定位记录。
     */
    private Long id;
    /**
     * 所属用户 ID，用于个人视角数据隔离。
     */
    private Long userId;
    /**
     * 所属家庭 ID，用于家庭视角聚合。
     */
    private Long familyId;
    /**
     * 业务日期，用于交易归属、确认或统计周期判定。
     */
    private LocalDate snapshotDate;
    /**
     * 资产总额，汇总现金、持仓市值和其他资产项目。
     */
    private BigDecimal totalAssets;
    /**
     * 负债总额，用于从总资产中扣除后计算净资产。
     */
    private BigDecimal totalLiabilities;
    /**
     * 净资产，表示总资产扣除负债后的家庭或个人资产结果。
     */
    private BigDecimal netWorth;
    /**
     * 现金余额，表示统计时点账户或总览口径下可用现金资产金额。
     */
    private BigDecimal cashBalance;
    /**
     * 持仓市值，表示统计时点基金、股票或其他持仓按当前价格折算后的资产金额。
     */
    private BigDecimal positionValue;
    /**
     * 已实现盈亏，表示卖出、结算或分红等已落账事项形成的盈亏金额。
     */
    private BigDecimal realizedPnl;
    /**
     * 未实现盈亏，用于衡量当前市值与成本的差额。
     */
    private BigDecimal unrealizedPnl;
    /**
     * 收益类盈亏，表示利息、分红或货币基金收益等收入项目形成的金额。
     */
    private BigDecimal incomePnl;
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
     * 返回所属家庭 ID，用于家庭视角聚合。
     */
    public Long getFamilyId() { return familyId; }
    /**
     * 设置所属家庭 ID，用于家庭视角聚合。
     */
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    /**
     * 返回业务日期，用于交易归属、确认或统计周期判定。
     */
    public LocalDate getSnapshotDate() { return snapshotDate; }
    /**
     * 设置业务日期，用于交易归属、确认或统计周期判定。
     */
    public void setSnapshotDate(LocalDate snapshotDate) { this.snapshotDate = snapshotDate; }
    /**
     * 返回金额字段，按所属账户、持仓或统计口径计量。
     */
    public BigDecimal getTotalAssets() { return totalAssets; }
    /**
     * 设置金额字段，按所属账户、持仓或统计口径计量。
     */
    public void setTotalAssets(BigDecimal totalAssets) { this.totalAssets = totalAssets; }
    /**
     * 读取总负债金额，用于计算家庭或个人净资产。
     */
    public BigDecimal getTotalLiabilities() { return totalLiabilities; }
    /**
     * 设置总负债金额，用于净资产快照计算。
     */
    public void setTotalLiabilities(BigDecimal totalLiabilities) { this.totalLiabilities = totalLiabilities; }
    /**
     * 读取净资产金额，等于资产扣除负债后的统计结果。
     */
    public BigDecimal getNetWorth() { return netWorth; }
    /**
     * 设置净资产金额，保存总资产扣除负债后的结果。
     */
    public void setNetWorth(BigDecimal netWorth) { this.netWorth = netWorth; }
    /**
     * 读取现金余额，表示统计时点可用现金资产。
     */
    public BigDecimal getCashBalance() { return cashBalance; }
    /**
     * 设置现金余额，用于保存净值快照或首页资产汇总结果。
     */
    public void setCashBalance(BigDecimal cashBalance) { this.cashBalance = cashBalance; }
    /**
     * 读取持仓市值，表示统计时点证券或基金持仓折算金额。
     */
    public BigDecimal getPositionValue() { return positionValue; }
    /**
     * 设置持仓市值，用于保存按最新价格计算出的资产金额。
     */
    public void setPositionValue(BigDecimal positionValue) { this.positionValue = positionValue; }
    /**
     * 读取已实现盈亏，表示已卖出或已结算事项带来的收益结果。
     */
    public BigDecimal getRealizedPnl() { return realizedPnl; }
    /**
     * 设置已实现盈亏，用于快照或看板保存已落账收益。
     */
    public void setRealizedPnl(BigDecimal realizedPnl) { this.realizedPnl = realizedPnl; }
    /**
     * 返回未实现盈亏，用于衡量当前市值与成本的差额。
     */
    public BigDecimal getUnrealizedPnl() { return unrealizedPnl; }
    /**
     * 设置未实现盈亏，用于衡量当前市值与成本的差额。
     */
    public void setUnrealizedPnl(BigDecimal unrealizedPnl) { this.unrealizedPnl = unrealizedPnl; }
    /**
     * 读取收入类盈亏，表示利息、分红或货币基金收益金额。
     */
    public BigDecimal getIncomePnl() { return incomePnl; }
    /**
     * 设置收入类盈亏，用于净值快照保留非交易收益。
     */
    public void setIncomePnl(BigDecimal incomePnl) { this.incomePnl = incomePnl; }
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

