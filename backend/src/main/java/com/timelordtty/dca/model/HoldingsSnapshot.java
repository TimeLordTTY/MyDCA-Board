package com.timelordtty.dca.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 持仓快照表实体（holdings_snapshot）
 */
public class HoldingsSnapshot {
    private Long id;
    private Long userId;
    private Long productId;
    private LocalDate snapshotDate;
    private BigDecimal shares;
    private BigDecimal cost;
    private String costMethod; // AVERAGE/FIFO
    private BigDecimal nav;
    private LocalDate navDate;
    private BigDecimal marketValue;
    private BigDecimal unrealizedPnl;
    private BigDecimal returnRate;
    private LocalDate fetchDate;
    private Boolean isDirty;
    private LocalDate dirtyFromDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public LocalDate getSnapshotDate() { return snapshotDate; }
    public void setSnapshotDate(LocalDate snapshotDate) { this.snapshotDate = snapshotDate; }
    public BigDecimal getShares() { return shares; }
    public void setShares(BigDecimal shares) { this.shares = shares; }
    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }
    public String getCostMethod() { return costMethod; }
    public void setCostMethod(String costMethod) { this.costMethod = costMethod; }
    public BigDecimal getNav() { return nav; }
    public void setNav(BigDecimal nav) { this.nav = nav; }
    public LocalDate getNavDate() { return navDate; }
    public void setNavDate(LocalDate navDate) { this.navDate = navDate; }
    public BigDecimal getMarketValue() { return marketValue; }
    public void setMarketValue(BigDecimal marketValue) { this.marketValue = marketValue; }
    public BigDecimal getUnrealizedPnl() { return unrealizedPnl; }
    public void setUnrealizedPnl(BigDecimal unrealizedPnl) { this.unrealizedPnl = unrealizedPnl; }
    public BigDecimal getReturnRate() { return returnRate; }
    public void setReturnRate(BigDecimal returnRate) { this.returnRate = returnRate; }
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

