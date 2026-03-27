package com.timelordtty.dca.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 净资产快照表实体（net_worth_snapshot）
 */
public class NetWorthSnapshot {
    private Long id;
    private Long userId;
    private Long familyId;
    private LocalDate snapshotDate;
    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;
    private BigDecimal netWorth;
    private BigDecimal cashBalance;
    private BigDecimal positionValue;
    private BigDecimal realizedPnl;
    private BigDecimal unrealizedPnl;
    private BigDecimal incomePnl;
    private LocalDate fetchDate;
    private Boolean isDirty;
    private LocalDate dirtyFromDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public LocalDate getSnapshotDate() { return snapshotDate; }
    public void setSnapshotDate(LocalDate snapshotDate) { this.snapshotDate = snapshotDate; }
    public BigDecimal getTotalAssets() { return totalAssets; }
    public void setTotalAssets(BigDecimal totalAssets) { this.totalAssets = totalAssets; }
    public BigDecimal getTotalLiabilities() { return totalLiabilities; }
    public void setTotalLiabilities(BigDecimal totalLiabilities) { this.totalLiabilities = totalLiabilities; }
    public BigDecimal getNetWorth() { return netWorth; }
    public void setNetWorth(BigDecimal netWorth) { this.netWorth = netWorth; }
    public BigDecimal getCashBalance() { return cashBalance; }
    public void setCashBalance(BigDecimal cashBalance) { this.cashBalance = cashBalance; }
    public BigDecimal getPositionValue() { return positionValue; }
    public void setPositionValue(BigDecimal positionValue) { this.positionValue = positionValue; }
    public BigDecimal getRealizedPnl() { return realizedPnl; }
    public void setRealizedPnl(BigDecimal realizedPnl) { this.realizedPnl = realizedPnl; }
    public BigDecimal getUnrealizedPnl() { return unrealizedPnl; }
    public void setUnrealizedPnl(BigDecimal unrealizedPnl) { this.unrealizedPnl = unrealizedPnl; }
    public BigDecimal getIncomePnl() { return incomePnl; }
    public void setIncomePnl(BigDecimal incomePnl) { this.incomePnl = incomePnl; }
    public LocalDate getFetchDate() { return fetchDate; }
    public void setFetchDate(LocalDate fetchDate) { this.fetchDate = fetchDate; }
    public Boolean getIsDirty() { return isDirty; }
    public void setIsDirty(Boolean isDirty) { this.isDirty = isDirty; }
    public LocalDate getDirtyFromDate() { return dirtyFromDate; }
    public void setDirtyFromDate(LocalDate dirtyFromDate) { this.dirtyFromDate = dirtyFromDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

