package com.timelordtty.dca.dto.mobile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class MobileWealthOverviewDto {
    private BigDecimal totalAssets;
    private BigDecimal cashBalance;
    private BigDecimal positionValue;
    private BigDecimal totalLiabilities;
    private BigDecimal netWorth;
    private int accountCount;
    private int draftCount;
    private int settlementCount;
    private int suggestionCount;
    private int totalTodoCount;
    private LocalDateTime lastUpdatedAt;
    private List<MobileActivityItemDto> recentActivities;

    public BigDecimal getTotalAssets() {
        return totalAssets;
    }

    public void setTotalAssets(BigDecimal totalAssets) {
        this.totalAssets = totalAssets;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public void setCashBalance(BigDecimal cashBalance) {
        this.cashBalance = cashBalance;
    }

    public BigDecimal getPositionValue() {
        return positionValue;
    }

    public void setPositionValue(BigDecimal positionValue) {
        this.positionValue = positionValue;
    }

    public BigDecimal getTotalLiabilities() {
        return totalLiabilities;
    }

    public void setTotalLiabilities(BigDecimal totalLiabilities) {
        this.totalLiabilities = totalLiabilities;
    }

    public BigDecimal getNetWorth() {
        return netWorth;
    }

    public void setNetWorth(BigDecimal netWorth) {
        this.netWorth = netWorth;
    }

    public int getAccountCount() {
        return accountCount;
    }

    public void setAccountCount(int accountCount) {
        this.accountCount = accountCount;
    }

    public int getDraftCount() {
        return draftCount;
    }

    public void setDraftCount(int draftCount) {
        this.draftCount = draftCount;
    }

    public int getSettlementCount() {
        return settlementCount;
    }

    public void setSettlementCount(int settlementCount) {
        this.settlementCount = settlementCount;
    }

    public int getSuggestionCount() {
        return suggestionCount;
    }

    public void setSuggestionCount(int suggestionCount) {
        this.suggestionCount = suggestionCount;
    }

    public int getTotalTodoCount() {
        return totalTodoCount;
    }

    public void setTotalTodoCount(int totalTodoCount) {
        this.totalTodoCount = totalTodoCount;
    }

    public LocalDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public List<MobileActivityItemDto> getRecentActivities() {
        return recentActivities;
    }

    public void setRecentActivities(List<MobileActivityItemDto> recentActivities) {
        this.recentActivities = recentActivities;
    }
}
